#!/bin/bash
# 房型库共享服务器 · 安卓 APK 构建脚本（零外部依赖，纯命令行工具链）
set -e
cd "$(dirname "$0")"

SDK=/home/user/.android-sdk
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
JAVA=/home/user/.jdk17/jdk17/bin/java
JAVAC=/home/user/.jdk17/jdk17/bin/javac
KEYTOOL=/home/user/.jdk17/jdk17/bin/keytool

rm -rf build && mkdir -p build/gen build/obj build/dex build/assets build/apk build/lib/arm64-v8a build/lib/armeabi-v7a
cp assets/index.html build/assets/index.html
cp assets/gun.png build/assets/gun.png
cp assets/impact.png build/assets/impact.png
cp assets/soldier_orange.png build/assets/soldier_orange.png
cp assets/soldier_blue.png build/assets/soldier_blue.png
cp assets/soldier_purple.png build/assets/soldier_purple.png
cp assets/soldier_green.png build/assets/soldier_green.png
# 穿透内核（cloudflared/frpc）已改为按需下载：不再打进 APK，APK 大幅瘦身（30MB→9MB）
echo "  （穿透内核已按需下载化，不打包进 APK）"
# 液态玻璃开源库原生渲染（Liquid-Glass-Android / MIT）
LG_LIB=libs/liquidglass/ex/jni
cp $LG_LIB/arm64-v8a/libnativegauss.so build/lib/arm64-v8a/libnativegauss.so
cp $LG_LIB/armeabi-v7a/libnativegauss.so build/lib/armeabi-v7a/libnativegauss.so
# 物体识别引擎 TensorFlow Lite + COCO 模型（本地离线推理）
TF_LIB=/home/user/Doubao/chats/21887200059138/libs/tflite/tflite2_ex/jni
cp $TF_LIB/arm64-v8a/libtensorflowlite_jni.so build/lib/arm64-v8a/libtensorflowlite_jni.so
cp $TF_LIB/armeabi-v7a/libtensorflowlite_jni.so build/lib/armeabi-v7a/libtensorflowlite_jni.so
cp /home/user/Doubao/chats/21887200059138/libs/tflite/new_model/ed0.tflite build/assets/detect.tflite
cp /home/user/Doubao/chats/21887200059138/libs/tflite/new_model/labelmap_90.txt build/assets/labelmap.txt
cp /home/user/Doubao/chats/21887200059138/libs/tflite/new_model/mv2_quant.tflite build/assets/mobilenet_v2.tflite
cp /home/user/Doubao/chats/21887200059138/libs/tflite/imagenet_labels.txt build/assets/imagenet_labels.txt
echo "  TFLite 引擎 + EfficientDet-Lite0 检测模型 + MobileNetV2 分类模型已就位"

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
SHIZUKU_JAR=/home/user/Doubao/chats/21887200059138/libs/shizuku_new/shizuku-api-13.1.5.jar
ZXING_JAR=/home/user/Doubao/chats/21887200059138/libs/zxing-core-3.5.3.jar
LG_CLASSES=/home/user/Doubao/chats/21887200059138/房型库共享服务器/android/libs/liquidglass/ex/cls
KOTLIN_STDLIB=/home/user/Doubao/chats/21887200059138/libs/kotlin-stdlib-1.9.24.jar
LG_JAR=/home/user/Doubao/chats/21887200059138/房型库共享服务器/android/libs/liquidglass/ex/classes.jar
TF_JAR=/home/user/Doubao/chats/21887200059138/libs/tflite/tflite2_ex/classes.jar
TF_API_JAR=/home/user/Doubao/chats/21887200059138/libs/tflite/tflite_api_ex/classes.jar
JSCH_JAR=/home/user/Doubao/chats/21887200059138/房型库共享服务器/android/libs/jsch-0.1.55.jar
if ! $JAVAC -source 8 -target 8 -encoding UTF-8 \
  -classpath "$PLATFORM:$SHIZUKU_JAR:$ZXING_JAR:$LG_CLASSES:$KOTLIN_STDLIB:$TF_JAR:$TF_API_JAR:$JSCH_JAR" \
  -d build/obj \
  $(find src src_extra -name '*.java') 2>/tmp/javac.err; then
  echo "❌ javac 编译失败（构建中止，防止产出坏包）："
  grep -v "bootstrap class path" /tmp/javac.err || true
  exit 1
fi
# 合并 Shizuku API 13.1.5（rikka.shizuku.* + moe.shizuku.*）进 dex
unzip -o -q "$SHIZUKU_JAR" -d build/obj
echo "  Shizuku API 13.1.5 类已合并"
# 合并 ZXing（二维码生成）类进 dex
unzip -o -q "$ZXING_JAR" -d build/obj
echo "  ZXing 类已合并"
# 合并液态玻璃开源库（Liquid-Glass-Android）+ Kotlin 运行时进 dex
unzip -o -q "$LG_JAR" -d build/obj
echo "  LiquidGlass 类已合并"
unzip -o -q "$KOTLIN_STDLIB" -d build/obj -x "module-info.class" "META-INF/versions/*"
find build/obj -name "module-info.class" -delete 2>/dev/null || true
echo "  Kotlin stdlib 已合并（排除 module-info / 多版本目录）"
unzip -o -q "$TF_JAR" -d build/obj
unzip -o -q "$TF_API_JAR" -d build/obj
echo "  TensorFlow Lite 类已合并（API + 实现）"
unzip -o -q "$JSCH_JAR" -d build/obj -x "META-INF/*"
echo "  JSch（SSH 隧道）类已合并"

echo "[4/7] d8 转 dex..."
$BT/d8 --release --lib $PLATFORM --min-api 24 \
  --output build/dex \
  $(find build/obj -name '*.class')

echo "[5/7] 打包 classes.dex + 原生库 进 apk（Python zipfile）..."
python3 - <<'PY'
import zipfile, shutil, os
src = "build/apk/app-unsigned.apk"
tmp = "build/apk/app-with-dex.apk"
with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write("build/dex/classes.dex", "classes.dex")
    # 原生库存入 lib/arm64-v8a/（manifest 已设 extractNativeLibs=true，安装时系统解压）
    # 穿透内核不再打包进 APK（按需下载）
    if os.path.exists("build/lib/arm64-v8a/libnativegauss.so"):
        zout.write("build/lib/arm64-v8a/libnativegauss.so", "lib/arm64-v8a/libnativegauss.so", zipfile.ZIP_DEFLATED)
        print("  libnativegauss.so 已写入 lib/arm64-v8a/")
    if os.path.exists("build/lib/armeabi-v7a/libnativegauss.so"):
        zout.write("build/lib/armeabi-v7a/libnativegauss.so", "lib/armeabi-v7a/libnativegauss.so", zipfile.ZIP_DEFLATED)
        print("  libnativegauss.so 已写入 lib/armeabi-v7a/")
    if os.path.exists("build/lib/arm64-v8a/libtensorflowlite_jni.so"):
        zout.write("build/lib/arm64-v8a/libtensorflowlite_jni.so", "lib/arm64-v8a/libtensorflowlite_jni.so", zipfile.ZIP_DEFLATED)
        print("  libtensorflowlite_jni.so 已写入 lib/arm64-v8a/")
    if os.path.exists("build/lib/armeabi-v7a/libtensorflowlite_jni.so"):
        zout.write("build/lib/armeabi-v7a/libtensorflowlite_jni.so", "lib/armeabi-v7a/libtensorflowlite_jni.so", zipfile.ZIP_DEFLATED)
        print("  libtensorflowlite_jni.so 已写入 lib/armeabi-v7a/")
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
  --out output/房型库共享服务器.apk \
  build/apk/app-aligned.apk

echo ""
echo "✅ APK 生成：$(pwd)/output/房型库共享服务器.apk"
ls -la output/
