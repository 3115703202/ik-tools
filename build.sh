#!/bin/bash
# IK工具箱 · 安卓 APK 构建脚本（命令行工具链，第三方依赖随仓库 libs/ext 提供）
# 前置：JDK17 在 ~/.jdk17/jdk17，Android SDK 在 ~/.android-sdk（build-tools/34.0.0 + platforms/android-34）
set -e
cd "$(dirname "$0")"

SDK=/home/user/.android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
JDK=/home/user/.jdk17/jdk17
JAVA=$JDK/bin/java
JAVAC=$JDK/bin/javac
KEYTOOL=$JDK/bin/keytool

# 仓库内第三方依赖
SHIZUKU_API=libs/ext/shizuku/classes.jar
SHIZUKU_AIDL=libs/ext/aidl/classes.jar
SHIZUKU_SHARED=libs/ext/shared/classes.jar
ZXING=libs/ext/zxing-core-3.5.3.jar
LG_CLASSES=libs/liquidglass/ex/cls
LG_JAR=libs/liquidglass/ex/classes.jar
KOTLIN=libs/ext/kotlin-stdlib-1.9.24.jar
TF_JAR=libs/ext/tflite/classes.jar
TF_API_JAR=libs/ext/tflite-api/classes.jar
JSCH=libs/jsch-0.1.55.jar
LG_SO=libs/liquidglass/ex/jni
TF_SO=libs/ext/tflite/jni

rm -rf build && mkdir -p build/gen build/obj build/dex build/assets build/apk build/lib/arm64-v8a build/lib/armeabi-v7a

# assets（网页 / 模型 / 图片 / 号段库）
for f in index.html fps3d.html three.min.js phone.dat detect.tflite labelmap.txt \
         mobilenet_v2.tflite imagenet_labels.txt \
         gun.png impact.png soldier_orange.png soldier_blue.png soldier_purple.png soldier_green.png \
         llama-cli admin.html; do
  [ -f "assets/$f" ] && cp "assets/$f" "build/assets/$f"
done
# 穿透内核（cloudflared/frpc）按需下载，不打进 APK，APK 保持瘦身
echo "  （穿透内核按需下载，不打包进 APK）"

# 原生库：液态玻璃 + TensorFlow Lite（arm64-v8a / armeabi-v7a）
cp $LG_SO/arm64-v8a/libnativegauss.so build/lib/arm64-v8a/libnativegauss.so
cp $LG_SO/armeabi-v7a/libnativegauss.so build/lib/armeabi-v7a/libnativegauss.so
cp $TF_SO/arm64-v8a/libtensorflowlite_jni.so build/lib/arm64-v8a/libtensorflowlite_jni.so
cp $TF_SO/armeabi-v7a/libtensorflowlite_jni.so build/lib/armeabi-v7a/libtensorflowlite_jni.so
echo "  原生库就位（liquidglass + tflite）"

echo "[1/7] aapt2 compile 资源..."
$BT/aapt2 compile --dir res -o build/res.zip

echo "[2/7] aapt2 link 生成 apk 骨架（含 assets）..."
$BT/aapt2 link \
  -o build/apk/app-unsigned.apk \
  -I $PLATFORM \
  -A build/assets \
  --manifest AndroidManifest.xml \
  --auto-add-overlay \
  build/res.zip

echo "[3/7] javac 编译 Java 源码..."
CP="$PLATFORM:$SHIZUKU_API:$SHIZUKU_AIDL:$SHIZUKU_SHARED:$ZXING:$LG_CLASSES:$LG_JAR:$KOTLIN:$TF_JAR:$TF_API_JAR:$JSCH"
if ! $JAVAC -source 8 -target 8 -encoding UTF-8 \
  -classpath "$CP" \
  -d build/obj \
  $(find src src_extra -name '*.java') 2>/tmp/javac.err; then
  echo "❌ javac 编译失败（构建中止，防止产出坏包）："
  grep -v "bootstrap class path" /tmp/javac.err || true
  exit 1
fi
echo "  Java 编译通过"

# 合并第三方类进 dex
unzip -o -q "$SHIZUKU_API" -d build/obj
unzip -o -q "$SHIZUKU_AIDL" -d build/obj
unzip -o -q "$SHIZUKU_SHARED" -d build/obj
unzip -o -q "$ZXING" -d build/obj
unzip -o -q "$LG_JAR" -d build/obj
unzip -o -q "$KOTLIN" -d build/obj -x "module-info.class" "META-INF/versions/*"
unzip -o -q "$TF_JAR" -d build/obj
unzip -o -q "$TF_API_JAR" -d build/obj
unzip -o -q "$JSCH" -d build/obj -x "META-INF/*"
find build/obj -name "module-info.class" -delete 2>/dev/null || true
echo "  第三方类已合并"

echo "[4/7] d8 转 dex..."
$BT/d8 --release --lib $PLATFORM --min-api 24 \
  --output build/dex \
  $(find build/obj -name '*.class')

echo "[5/7] 打包 classes.dex + 原生库..."
python3 - <<'PY'
import zipfile, shutil, os
src = "build/apk/app-unsigned.apk"
tmp = "build/apk/app-with-dex.apk"
with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write("build/dex/classes.dex", "classes.dex")
    for abi in ("arm64-v8a", "armeabi-v7a"):
        for so in ("libnativegauss.so", "libtensorflowlite_jni.so"):
            p = f"build/lib/{abi}/{so}"
            if os.path.exists(p):
                zout.write(p, f"lib/{abi}/{so}", zipfile.ZIP_DEFLATED)
                print(f"  {so} -> {abi}")
shutil.move(tmp, src)
print("  classes.dex 已写入")
PY

echo "[6/7] zipalign 对齐..."
$BT/zipalign -f 4 build/apk/app-unsigned.apk build/apk/app-aligned.apk

echo "[7/7] 签名（固定密钥：keystore/debug.keystore）..."
mkdir -p keystore
if [ ! -f keystore/debug.keystore ]; then
  $KEYTOOL -genkeypair -v \
    -keystore keystore/debug.keystore -alias androiddebugkey \
    -storepass android -keypass android \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=CN" >/dev/null 2>&1
fi
$BT/apksigner sign \
  --ks keystore/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --out output/IK工具箱-v4.5.20.apk \
  build/apk/app-aligned.apk

echo ""
echo "✅ APK 生成：$(pwd)/output/IK工具箱-v4.5.20.apk"
ls -la output/
