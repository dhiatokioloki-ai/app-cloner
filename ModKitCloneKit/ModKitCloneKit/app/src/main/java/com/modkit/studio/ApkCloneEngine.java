package com.modkit.studio;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.android.apksig.ApkSigner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.security.auth.x500.X500Principal;

import brut.androlib.ApkBuilder;
import brut.androlib.ApkDecoder;
import brut.androlib.Config;
import brut.androlib.android.ApktoolAndroid;

/**
 * Rebuilds one standalone APK with a new application ID, label and optional launch orientation.
 * The APK is signed with a persistent, device-local Android Keystore key so future builds from
 * the same installation use the same signer. This is not a guarantee that every APK can rebuild.
 */
public final class ApkCloneEngine {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String SIGNING_ALIAS = "modkit-clone-signer-v1";

    public interface Progress {
        void onStage(String message);
    }

    public static final class Options {
        public String packageId;
        public String displayName;
        /** landscape, reverseLandscape, portrait, or keep */
        public String orientation = "keep";
        public boolean removeInternetPermission;
    }

    private ApkCloneEngine() {}

    public static File build(Context context, File sourceApk, File outputDirectory,
                             Options options, Progress progress) throws Exception {
        if (sourceApk == null || !sourceApk.isFile() || sourceApk.length() == 0) {
            throw new IOException("The selected source APK could not be read.");
        }
        if (!isValidPackageId(options.packageId)) {
            throw new IllegalArgumentException("That package ID is not valid.");
        }
        if (options.displayName == null || options.displayName.trim().isEmpty()) {
            options.displayName = options.packageId.substring(options.packageId.lastIndexOf('.') + 1);
        }

        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IOException("Could not create the output folder.");
        }
        File work = new File(context.getCacheDir(), "modkit-build-" + System.currentTimeMillis());
        File decoded = new File(work, "decoded");
        File unsignedApk = new File(work, "rebuilt-unsigned.apk");
        File signedApk = new File(outputDirectory, safeFileName(options.packageId) + ".apk");
        if (!work.mkdirs()) throw new IOException("Could not create the temporary build folder.");

        try {
            stage(progress, "Preparing APK rebuild tools…");
            Config config = ApktoolAndroid.configure(context.getApplicationContext());
            config.setForced(true);
            config.setVerbose(false);
            config.setJobs(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors())));

            stage(progress, "Decoding the APK and its resources…");
            new ApkDecoder(sourceApk, config).decode(decoded);

            File manifest = new File(decoded, "AndroidManifest.xml");
            if (!manifest.isFile()) throw new IOException("The APK manifest could not be decoded.");
            Document before = parseXml(manifest);
            String originalPackage = before.getDocumentElement().getAttribute("package").trim();
            if (originalPackage.isEmpty()) throw new IOException("The source APK has no readable package ID.");
            if (originalPackage.equals(options.packageId)) {
                throw new IllegalArgumentException("Choose a new package ID different from the original.");
            }

            stage(progress, "Updating package references and app settings…");
            replaceTextReferences(decoded.toPath(), originalPackage, options.packageId, manifest.toPath());
            Document document = parseXml(manifest);
            updateManifest(document, options, originalPackage);
            writeXml(document, manifest);

            stage(progress, "Rebuilding the modified APK…");
            new ApkBuilder(decoded, config).build(unsignedApk);
            if (!unsignedApk.isFile() || unsignedApk.length() == 0) {
                throw new IOException("The APK rebuild finished without creating an output file.");
            }

            stage(progress, "Signing the clone with your local device key…");
            sign(context, unsignedApk, signedApk);
            if (!signedApk.isFile() || signedApk.length() == 0) {
                throw new IOException("The APK signer did not create the output file.");
            }
            return signedApk;
        } finally {
            deleteRecursively(work);
        }
    }

    private static void updateManifest(Document document, Options options, String originalPackage) throws Exception {
        Element root = document.getDocumentElement();
        root.setAttribute("package", options.packageId);
        // A cloned package cannot safely continue claiming another package's shared UID.
        root.removeAttributeNS(ANDROID_NS, "sharedUserId");
        root.removeAttributeNS(ANDROID_NS, "sharedUserMaxSdkVersion");

        NodeList applications = document.getElementsByTagName("application");
        if (applications.getLength() > 0 && applications.item(0) instanceof Element) {
            Element application = (Element) applications.item(0);
            application.setAttributeNS(ANDROID_NS, "android:label", options.displayName);
        }

        if (options.orientation != null && !"keep".equalsIgnoreCase(options.orientation)) {
            Element launcher = findLauncherActivity(document);
            if (launcher == null) {
                throw new IOException("No launcher activity was found, so orientation could not be changed.");
            }
            String orientation = options.orientation;
            if (!Arrays.asList("landscape", "reverseLandscape", "portrait").contains(orientation)) {
                throw new IllegalArgumentException("Unsupported orientation option.");
            }
            launcher.setAttributeNS(ANDROID_NS, "android:screenOrientation", orientation);
        }

        if (options.removeInternetPermission) removeInternetPermission(root);
    }

    private static Element findLauncherActivity(Document document) {
        NodeList activities = document.getElementsByTagName("activity");
        Element first = activities.getLength() > 0 && activities.item(0) instanceof Element
                ? (Element) activities.item(0) : null;
        String manifestPackage = document.getDocumentElement().getAttribute("package");
        NodeList aliases = document.getElementsByTagName("activity-alias");

        // Normal launch activities can carry screenOrientation directly.
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (hasMainLauncherFilter(activity)) return activity;
        }
        // Some apps expose an activity-alias as their launcher entry. Resolve it to
        // its actual target activity because activity-alias does not support this flag.
        for (int i = 0; i < aliases.getLength(); i++) {
            Element alias = (Element) aliases.item(i);
            if (!hasMainLauncherFilter(alias)) continue;
            String target = alias.getAttributeNS(ANDROID_NS, "targetActivity");
            Element resolved = findActivityByName(activities, target, manifestPackage);
            if (resolved != null) return resolved;
        }
        return first;
    }

    private static boolean hasMainLauncherFilter(Element activity) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int j = 0; j < filters.getLength(); j++) {
            Element filter = (Element) filters.item(j);
            boolean main = false;
            boolean launcher = false;
            NodeList actions = filter.getElementsByTagName("action");
            for (int k = 0; k < actions.getLength(); k++) {
                Element action = (Element) actions.item(k);
                if ("android.intent.action.MAIN".equals(action.getAttributeNS(ANDROID_NS, "name"))) main = true;
            }
            NodeList categories = filter.getElementsByTagName("category");
            for (int k = 0; k < categories.getLength(); k++) {
                Element category = (Element) categories.item(k);
                if ("android.intent.category.LAUNCHER".equals(category.getAttributeNS(ANDROID_NS, "name"))) launcher = true;
            }
            if (main && launcher) return true;
        }
        return false;
    }

    private static Element findActivityByName(NodeList activities, String target, String manifestPackage) {
        if (target == null || target.trim().isEmpty()) return null;
        String normalized = target;
        if (normalized.startsWith(".")) normalized = manifestPackage + normalized;
        else if (!normalized.contains(".")) normalized = manifestPackage + "." + normalized;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (normalized.equals(activity.getAttributeNS(ANDROID_NS, "name"))) return activity;
        }
        return null;
    }

    private static void removeInternetPermission(Element root) {
        List<Node> remove = new ArrayList<>();
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element)) continue;
            String tag = node.getNodeName();
            if (!tag.equals("uses-permission") && !tag.equals("uses-permission-sdk-23")
                    && !tag.equals("uses-permission-sdk-m")) continue;
            Element permission = (Element) node;
            if ("android.permission.INTERNET".equals(permission.getAttributeNS(ANDROID_NS, "name"))) {
                remove.add(node);
            }
        }
        for (Node node : remove) root.removeChild(node);
    }

    private static void replaceTextReferences(Path root, String oldPackage, String newPackage,
                                              Path manifest) throws IOException {
        List<String> textExtensions = Arrays.asList(".xml", ".smali", ".txt", ".yml", ".yaml",
                ".json", ".properties", ".java", ".kt", ".cfg", ".pro");
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            java.util.Iterator<Path> iterator = paths.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                boolean text = false;
                for (String ext : textExtensions) if (name.endsWith(ext)) { text = true; break; }
                if (!text) continue;
                // AndroidManifest.xml is parsed and saved separately immediately afterwards.
                byte[] bytes = Files.readAllBytes(path);
                String content = new String(bytes, StandardCharsets.UTF_8);
                if (!content.contains(oldPackage)) continue;
                Files.write(path, content.replace(oldPackage, newPackage).getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
        catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-general-entities", false); }
        catch (Exception ignored) {}
        try { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
        catch (Exception ignored) {}
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(file);
    }

    private static void writeXml(Document document, File file) throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        try { factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); }
        catch (Exception ignored) {}
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(file));
    }

    private static void sign(Context context, File input, File output) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(SIGNING_ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            long now = System.currentTimeMillis();
            generator.initialize(new KeyGenParameterSpec.Builder(SIGNING_ALIAS,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(new X500Principal("CN=ModKit Local APK Signer"))
                    .setCertificateSerialNumber(BigInteger.valueOf(1L))
                    .setCertificateNotBefore(new Date(now - 24L * 60L * 60L * 1000L))
                    .setCertificateNotAfter(new Date(now + 3650L * 24L * 60L * 60L * 1000L))
                    .build());
            generator.generateKeyPair();
            keyStore.load(null);
        }
        PrivateKey privateKey = (PrivateKey) keyStore.getKey(SIGNING_ALIAS, null);
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(SIGNING_ALIAS);
        if (privateKey == null || certificate == null) throw new IOException("Could not load the local APK signing key.");

        ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
                "ModKit", privateKey, Collections.singletonList(certificate)).build();
        ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(input)
                .setOutputApk(output)
                .setMinSdkVersion(26)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .build();
        signer.sign();
    }

    private static void stage(Progress progress, String message) {
        if (progress != null) progress.onStage(message);
    }

    private static String safeFileName(String packageId) {
        return packageId.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public static boolean isValidPackageId(String id) {
        return id != null && id.length() >= 3 && id.length() <= 220
                && id.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    private static void deleteRecursively(File path) {
        if (path == null || !path.exists()) return;
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        path.delete();
    }
}
