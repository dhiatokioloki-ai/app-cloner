
#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DEPENDENCY="$ROOT/third_party/apktool-android"

if [ ! -f "$DEPENDENCY/settings.gradle.kts" ]; then
  mkdir -p "$ROOT/third_party"
  if [ -e "$DEPENDENCY" ]; then
    rm -rf "$DEPENDENCY"
  fi
  git clone --depth 1 \
    https://github.com/anirudhmalik/apktool-android.git \
    "$DEPENDENCY"
fi

# Apply Java library plugins to the imported Java modules.
# The original upstream build applies these centrally, but
# ModKit imports the modules into a different Gradle project.
patch_java_module() {
  MODULE_DIR="$1"
  BUILD_FILE="$MODULE_DIR/build.gradle.kts"

  mkdir -p "$MODULE_DIR"
  if [ ! -f "$BUILD_FILE" ]; then
    : > "$BUILD_FILE"
  fi

  if ! grep -Fq '`java-library`' "$BUILD_FILE"; then
    TMP_FILE="${BUILD_FILE}.modkit-tmp"

    {
      printf 'plugins {\n    `java-library`\n}\n\n'
      printf 'java {\n'
      printf '    sourceCompatibility = JavaVersion.VERSION_1_8\n'
      printf '    targetCompatibility = JavaVersion.VERSION_1_8\n'
      printf '}\n\n'
      cat "$BUILD_FILE"
    } > "$TMP_FILE"

    mv "$TMP_FILE" "$BUILD_FILE"
  fi
}

patch_java_module "$DEPENDENCY/brut.j.common"
patch_java_module "$DEPENDENCY/brut.j.util"
patch_java_module "$DEPENDENCY/brut.j.dir"
patch_java_module "$DEPENDENCY/brut.j.xml"
patch_java_module "$DEPENDENCY/brut.j.yaml"
patch_java_module "$DEPENDENCY/brut.apktool/apktool-lib"

mkdir -p "$ROOT/gradle/wrapper"
cp "$DEPENDENCY/gradlew" "$ROOT/gradlew"
cp "$DEPENDENCY/gradlew.bat" "$ROOT/gradlew.bat"
cp "$DEPENDENCY/gradle/wrapper/gradle-wrapper.jar" \
  "$ROOT/gradle/wrapper/gradle-wrapper.jar"

cat > "$ROOT/gradle/wrapper/gradle-wrapper.properties" <<'PROPERTIES'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
PROPERTIES

chmod +x "$ROOT/gradlew"

printf '\nModKit dependency setup completed.\n'
