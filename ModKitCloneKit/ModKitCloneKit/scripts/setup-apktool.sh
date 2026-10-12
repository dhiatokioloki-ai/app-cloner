#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DEPENDENCY="$ROOT/third_party/apktool-android"

if [ ! -f "$DEPENDENCY/settings.gradle.kts" ]; then
  mkdir -p "$ROOT/third_party"
  if [ -e "$DEPENDENCY" ]; then rm -rf "$DEPENDENCY"; fi
  git clone --depth 1 https://github.com/anirudhmalik/apktool-android.git "$DEPENDENCY"
fi

mkdir -p "$ROOT/gradle/wrapper"
cp "$DEPENDENCY/gradlew" "$ROOT/gradlew"
cp "$DEPENDENCY/gradlew.bat" "$ROOT/gradlew.bat"
cp "$DEPENDENCY/gradle/wrapper/gradle-wrapper.jar" "$ROOT/gradle/wrapper/gradle-wrapper.jar"
# AGP 8.7.0 in this project requires Gradle 8.9. Keep the project wrapper
# aligned even if the dependency repository updates its own wrapper later.
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
printf '\nModKit dependency is set up. Close/reopen the project in your Android IDE and run the debug build.\n'
