package com.roomarchive.server;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * 物体识别 · 实时本地 AI 推理（TensorFlow Lite + COCO 80 类）
 * 完全离线：摄像头取帧 -> 内置模型检测人物/车辆/动物/日常物品
 * 叠加识别框 + 类别 + 置信度 + 估算距离/尺寸（单目估算，仅供娱乐参考）
 */
public class ObjectDetectActivity extends Activity implements TextureView.SurfaceTextureListener, Camera.PreviewCallback {

    static final int INPUT_SIZE = 320;           // EfficientDet-Lite0 输入 320x320（替换 MobileNetSSD 300）
    static final int MAX_DETECT = 25;            // EfficientDet-Lite0 最大输出 25 个检测框
    static final float THRESHOLD = 0.60f;        // 置信度阈值：0.6 高准确率，减少误报
    static final float FOV_DEG = 62f;               // 手机相机近似视场角（估算用）
    static final int MSG_RESULT = 1;
    static final int MSG_DETECT = 2;

    Interpreter tfLite;
    Interpreter clsLite;              // 1000 类整图分类模型
    List<String> labels = new ArrayList<>();
    List<String> clsLabels = new ArrayList<>();
    int clsTick = 0;                  // 分类节流计数

    // COCO 90 类中文标签（EfficientDet-Lite0：1-80 标准 COCO，81-90 附加类；索引与模型输出一致）
    private static final String[] CN_LABELS = {
            "未知", "人", "自行车", "汽车", "摩托车", "飞机", "公交车", "火车", "卡车", "船",
            "红绿灯", "消防栓", "停止标志", "停车计时器", "长椅", "鸟", "猫", "狗", "马", "羊",
            "牛", "大象", "熊", "斑马", "长颈鹿", "背包", "雨伞", "手提包", "领带", "行李箱",
            "飞盘", "滑雪板", "滑雪单板", "运动球", "风筝", "棒球棒", "棒球手套", "滑板", "冲浪板", "网球拍",
            "瓶子", "红酒杯", "杯子", "叉子", "刀", "勺子", "碗", "香蕉", "苹果", "三明治",
            "橙子", "西兰花", "胡萝卜", "热狗", "披萨", "甜甜圈", "蛋糕", "椅子", "沙发", "盆栽",
            "床", "餐桌", "马桶", "电视", "笔记本电脑", "鼠标", "遥控器", "键盘", "手机", "微波炉",
            "烤箱", "烤面包机", "水槽", "冰箱", "书", "时钟", "花瓶", "剪刀", "泰迪熊", "吹风机",
            "牙刷", "横幅", "毯子", "树枝", "桥", "建筑物", "公交车", "汽车", "猫", "椅子", "牛"};
    String overallText = "";          // 最近一次整图识别结果
    Camera camera;
    int cameraId = 0;
    TextureView texture;
    OverlayView overlay;
    TextView infoView;
    TextView stateView;
    boolean detecting = true;
    boolean firstFrame = true;
    int previewW = 0, previewH = 0;   // 预览帧尺寸（旋转校正后）
    int viewW = 0, viewH = 0;         // 屏幕视图尺寸
    HandlerThread inferThread;
    Handler inferHandler;
    Handler uiHandler = new Handler();
    byte[] lastFrame;                 // 最近一帧 NV21（供推理）

    // 常见类别真实高度近似值（米，用于单目距离估算；索引=COCO 类别号）
    static final float DEFAULT_H = 0.6f;
    float[] realH = new float[91];
    {
        for (int i = 0; i < realH.length; i++) realH[i] = DEFAULT_H;
        realH[1] = 1.7f;   // person
        realH[2] = 1.0f;   // bicycle
        realH[3] = 1.5f;   // car
        realH[4] = 1.2f;   // motorcycle
        realH[5] = 1.5f;   // airplane
        realH[6] = 2.5f;   // bus
        realH[7] = 2.8f;   // train
        realH[8] = 2.2f;   // truck
        realH[9] = 0.7f;   // boat
        realH[10] = 0.5f;  // traffic light
        realH[16] = 0.5f;  // cat
        realH[17] = 0.4f;  // dog
        realH[18] = 1.5f;  // horse
        realH[19] = 0.6f;  // sheep
        realH[20] = 0.6f;  // cow
        realH[21] = 3.0f;  // elephant
        realH[22] = 1.5f;  // bear
        realH[23] = 1.3f;  // zebra
        realH[24] = 4.0f;  // giraffe
        realH[25] = 0.4f;  // backpack
        realH[26] = 0.3f;  // umbrella
        realH[28] = 0.9f;  // tie
        realH[29] = 0.9f;  // suitcase
        realH[30] = 0.4f;  // frisbee
        realH[31] = 0.5f;  // skis
        realH[33] = 0.8f;  // sports ball
        realH[37] = 1.0f;  // skateboard
        realH[38] = 0.8f;  // surfboard
        realH[40] = 0.9f;  // bottle
        realH[41] = 0.6f;  // wine glass
        realH[42] = 0.3f;  // cup
        realH[43] = 0.2f;  // fork
        realH[44] = 0.2f;  // knife
        realH[45] = 0.2f;  // spoon
        realH[46] = 0.25f; // bowl
        realH[47] = 0.3f;  // banana
        realH[48] = 0.2f;  // apple
        realH[57] = 0.5f;  // chair
        realH[58] = 0.5f;  // couch
        realH[59] = 0.8f;  // potted plant
        realH[60] = 0.9f;  // bed
        realH[61] = 0.4f;  // dining table
        realH[63] = 0.6f;  // tv
        realH[64] = 0.6f;  // laptop
        realH[65] = 0.5f;  // mouse
        realH[66] = 0.3f;  // remote
        realH[67] = 1.0f;  // keyboard
        realH[68] = 0.2f;  // cell phone
        realH[69] = 0.5f;  // microwave
        realH[70] = 0.5f;  // oven
        realH[71] = 0.7f;  // toaster
        realH[72] = 0.7f;  // sink
        realH[73] = 0.6f;  // refrigerator
        realH[74] = 0.5f;  // book
        realH[75] = 0.3f;  // clock
        realH[76] = 0.3f;  // vase
        realH[77] = 1.0f;  // scissors
        realH[78] = 1.0f;  // teddy bear
        realH[79] = 0.5f;  // hair drier
        realH[80] = 0.3f;  // toothbrush
        realH[86] = 2.5f;  // bus (附加类)
        realH[87] = 1.5f;  // car
        realH[88] = 0.5f;  // cat
        realH[89] = 0.5f;  // chair
        realH[90] = 0.6f;  // cow
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.applyStatusBar(this);
        try {
            loadModel();
            loadClsModel();
            loadLabels();
            loadClsLabels();
        } catch (Exception e) {
            Toast.makeText(this, "模型加载失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 运行时相机权限（Android 6.0+）
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 100);
        }

        buildUI();

        inferThread = new HandlerThread("infer");
        inferThread.start();
        inferHandler = new Handler(inferThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == MSG_DETECT) {
                    detectOnThread((byte[]) msg.obj);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "需要相机权限才能识别物体", Toast.LENGTH_LONG).show();
            finish();
        } else {
            openCamera(cameraId);
        }
    }

    void buildUI() {
        FrameLayout root = new FrameLayout(this);

        texture = new TextureView(this);
        texture.setSurfaceTextureListener(this);
        root.addView(texture, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        overlay = new OverlayView(this);
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶部标题
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(10));
        TextView title = new TextView(this);
        title.setText("物体识别 · 实时");
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setShadowLayer(dp(2), 0, dp(1), 0x99000000);
        top.addView(title);
        stateView = new TextView(this);
        stateView.setText("识别中");
        stateView.setTextSize(11);
        stateView.setTextColor(0xFF7CFF9B);
        stateView.setPadding(dp(8), dp(2), dp(8), dp(2));
        top.addView(stateView);
        root.addView(top, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        // 右侧数据面板（检测结果流）
        infoView = new TextView(this);
        infoView.setTextSize(11);
        infoView.setTextColor(Color.WHITE);
        infoView.setBackgroundColor(0x66000000);
        infoView.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(infoView, new FrameLayout.LayoutParams(
                dp(210), FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.RIGHT | Gravity.TOP));

        // 底部控制栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(0x99000000);
        bar.setPadding(dp(8), dp(8), dp(8), dp(8));

        TextView btnFlip = new TextView(this);
        btnFlip.setText("翻转");
        btnFlip.setTextSize(13);
        btnFlip.setTextColor(Color.WHITE);
        btnFlip.setGravity(Gravity.CENTER);
        btnFlip.setBackground(Tools.liquidGlassCard(this, 12));
        btnFlip.setPadding(dp(14), dp(8), dp(14), dp(8));
        btnFlip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraId = (cameraId == 0) ? findBackCamera() : 0;
                openCamera(cameraId);
            }
        });
        bar.addView(btnFlip);

        View gap = new View(this);
        gap.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
        bar.addView(gap);

        TextView btnPause = new TextView(this);
        btnPause.setText("暂停");
        btnPause.setTextSize(13);
        btnPause.setTextColor(Color.WHITE);
        btnPause.setGravity(Gravity.CENTER);
        btnPause.setBackground(Tools.liquidGlassCard(this, 12));
        btnPause.setPadding(dp(14), dp(8), dp(14), dp(8));
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                detecting = !detecting;
                ((TextView) v).setText(detecting ? "暂停" : "继续");
                stateView.setText(detecting ? "识别中" : "已暂停");
            }
        });
        bar.addView(btnPause);

        View gap2 = new View(this);
        gap2.setLayoutParams(new LinearLayout.LayoutParams(dp(12), 1));
        bar.addView(gap2);

        TextView btnClose = new TextView(this);
        btnClose.setText("关闭");
        btnClose.setTextSize(13);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setBackground(Tools.liquidGlassCard(this, 12));
        btnClose.setPadding(dp(14), dp(8), dp(14), dp(8));
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(btnClose);

        root.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        setContentView(root);
    }

    int findBackCamera() {
        int n = Camera.getNumberOfCameras();
        for (int i = 0; i < n; i++) {
            Camera.CameraInfo ci = new Camera.CameraInfo();
            Camera.getCameraInfo(i, ci);
            if (ci.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return 0;
    }

    void openCamera(int id) {
        try {
            if (camera != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
                camera = null;
            }
            camera = Camera.open(id);
            Camera.Parameters p = camera.getParameters();
            Camera.Size sz = p.getSupportedPreviewSizes().get(0);
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                if (s.width >= 640 && s.width <= 1280) { sz = s; break; }
            }
            p.setPreviewSize(sz.width, sz.height);
            p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            try { p.setRecordingHint(true); } catch (Exception ignored) {}
            camera.setParameters(p);
            camera.setDisplayOrientation(calcOrientation(id));
            // 关键：先读回相机实际生效的预览尺寸，再开预览，避免首帧回调拿到 0 尺寸
            Camera.Size real = camera.getParameters().getPreviewSize();
            if (real != null) {
                previewW = real.width;
                previewH = real.height;
            } else {
                previewW = sz.width;
                previewH = sz.height;
            }
            SurfaceTexture st = texture.getSurfaceTexture();
            if (st != null) {
                camera.setPreviewTexture(st);
            }
            camera.setPreviewCallbackWithBuffer(this);
            int bufSize = previewW * previewH * 3 / 2;
            camera.addCallbackBuffer(new byte[bufSize]);
            camera.addCallbackBuffer(new byte[bufSize]);
            camera.addCallbackBuffer(new byte[bufSize]);
            camera.startPreview();
        } catch (Exception e) {
            Toast.makeText(this, "打开相机失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    int calcOrientation(int id) {
        Camera.CameraInfo ci = new Camera.CameraInfo();
        Camera.getCameraInfo(id, ci);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        int result;
        if (ci.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (ci.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (ci.orientation - degrees + 360) % 360;
        }
        return result;
    }

    // ===== TextureView 生命周期 =====
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        viewW = w; viewH = h;
        openCamera(cameraId);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        viewW = w; viewH = h;
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }

    // ===== 相机帧回调 =====
    @Override
    public void onPreviewFrame(byte[] data, Camera c) {
        if (camera == null) return;
        camera.addCallbackBuffer(data); // 归还 buffer 复用
        if (!detecting) return;
        if (firstFrame && previewW > 0 && previewH > 0) {
            firstFrame = false;
            byte[] frame = data.clone();
            lastFrame = frame;
            inferHandler.removeMessages(MSG_DETECT);
            inferHandler.obtainMessage(MSG_DETECT, frame).sendToTarget();
        } else if (!firstFrame) {
            // 节流：约 5 帧处理 1 帧（~150ms 间隔）
            synchronized (this) {
                if (lastFrame == null || inferHandler.hasMessages(MSG_DETECT)) return;
                lastFrame = data.clone();
                inferHandler.obtainMessage(MSG_DETECT, lastFrame).sendToTarget();
            }
        }
    }

    // ===== 推理（工作线程）=====
    void detectOnThread(byte[] nv21) {
        try {
            detectInner(nv21);
        } catch (Throwable e) {
            // 任何异常都不能杀死推理线程；把错误显示出来便于排查
            final String err = e.toString();
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    infoView.setText("推理异常: " + err);
                }
            });
        }
    }

    void detectInner(byte[] nv21) {
        if (previewW <= 0 || previewH <= 0) return;
        // NV21 -> 300x300 RGB
        int[] rgb = nv21ToRgbScaled(nv21, previewW, previewH, INPUT_SIZE);
        ByteBuffer input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3).order(ByteOrder.nativeOrder());
        for (int v : rgb) {
            input.put((byte) (v & 0xFF));
        }
        input.rewind();
        // 关键修复：输出统一用一维数组（按元素总数拍平），TFLite 引擎对多维 Java 数组解析兼容性差
        // （曾报 "DataType error: cannot resolve DataType of [Ljava.lang.Object;"）
        Object[] outputs = null;
        try {
            // 输出数组维度必须与引擎 tensor shape 完全一致（该引擎 copyTo 逐维校验）：
            // EfficientDet-Lite0: detection_boxes [1,25,4] / classes [1,25] / scores [1,25] / num [1]
            outputs = new Object[4];
            outputs[0] = new float[1][25][4];
            outputs[1] = new float[1][25];
            outputs[2] = new float[1][25];
            outputs[3] = new float[1];
            // 关键：必须用多输出 API。run(Object,Object) 会把整个输出数组当单个输出，报
            // "DataType error: cannot resolve DataType of [Ljava.lang.Object;"（v2.2.2 起一直如此）
            java.util.Map<Integer, Object> outs = new java.util.HashMap<>();
            for (int i = 0; i < 4; i++) outs.put(i, outputs[i]);
            tfLite.runForMultipleInputsOutputs(new Object[]{input}, outs);
        } catch (Exception e) {
            final String err = e.toString();
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    infoView.setText("推理错误: " + err);
                }
            });
            return;
        }
        float[][][] boxesF = (float[][][]) outputs[0]; // [1,25,4]
        float[][] classesF = (float[][]) outputs[1];    // [1,25]
        float[][] scoresF = (float[][]) outputs[2];     // [1,25]
        float[] numF = (float[]) outputs[3];            // [1]
        int n = (int) numF[0];
        if (n > MAX_DETECT) n = MAX_DETECT;
        final List<Detected> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (scoresF[0][i] >= THRESHOLD) {
                int cls = (int) classesF[0][i];
                // 过滤背景类（class=0，标签为"???"），避免显示无意义框
                if (cls <= 0 || cls >= labels.size()) continue;
                Detected d = new Detected();
                d.cls = cls;
                d.score = scoresF[0][i];
                // 模型输出：y1, x1, y2, x2（0-1 归一化）
                d.rect = new float[]{boxesF[0][i][0], boxesF[0][i][1], boxesF[0][i][2], boxesF[0][i][3]};
                // 显示中文标签；超出中文表范围时回退英文原标签
                d.label = (cls < CN_LABELS.length) ? CN_LABELS[cls] : labels.get(cls);
                // 估算距离/尺寸（单目近似）
                float boxH = (d.rect[2] - d.rect[0]) * INPUT_SIZE;
                float boxW = (d.rect[3] - d.rect[1]) * INPUT_SIZE;
                if (boxH > 4f) {
                    float focal = (INPUT_SIZE / 2f) / (float) Math.tan(Math.toRadians(FOV_DEG / 2f));
                    float h = (cls < realH.length) ? realH[cls] : DEFAULT_H;
                    d.distM = h * focal / boxH;
                    d.widthM = d.distM * 2f * (float) Math.tan(Math.toRadians(FOV_DEG / 2f)) * boxW / INPUT_SIZE;
                } else {
                    d.distM = 0; d.widthM = 0;
                }
                d.color = labelColor(d.cls);
                list.add(d);
            }
        }
        // 同类高重叠框去重：模型 NMS 后仍会留下多个重叠的同类框（如多个"笔记本电脑"），只保留置信度最高的
        java.util.List<Detected> dedup = new ArrayList<>();
        for (Detected d : list) {
            boolean dup = false;
            for (Detected k : dedup) {
                if (k.cls == d.cls && iou(k.rect, d.rect) > 0.45f) {
                    dup = true;
                    break;
                }
            }
            if (!dup) dedup.add(d);
        }
        list.clear();
        list.addAll(dedup);
        // 按距离近->远排序
        java.util.Collections.sort(list, new java.util.Comparator<Detected>() {
            @Override
            public int compare(Detected a, Detected b) {
                return Float.compare(a.distM, b.distM);
            }
        });

        // 每 6 帧做一次整图 1000 类分类（约 1 秒一次），覆盖"几乎所有东西都能大致识别"
        clsTick++;
        if (clsTick % 6 == 0) {
            try {
                classifyScene(nv21);
            } catch (Throwable ignored) {
            }
        }
        final String overall = overallText;

        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                overlay.setResults(list);
                StringBuilder sb = new StringBuilder();
                if (overall != null && !overall.isEmpty()) {
                    sb.append("整体: ").append(overall).append("\n");
                }
                if (list.isEmpty()) {
                    sb.append("画面中物体: 未检测到\n");
                } else {
                    for (int i = 0; i < Math.min(list.size(), 6); i++) {
                        Detected d = list.get(i);
                        sb.append(String.format("#%s %s %.0f%%\n  距 %.1fm 约%.2fm×%.2fm\n",
                                Integer.toHexString(d.color & 0xFFFFFF), d.label, d.score * 100,
                                d.distM, d.widthM, d.widthM));
                    }
                }
                infoView.setText(sb.toString());
            }
        });
    }

    int labelColor(int cls) {
        int[] palette = {0xFFFF4444, 0xFF44AAFF, 0xFFFFAA00, 0xFF44FF88,
                0xFFAA44FF, 0xFFFF66CC, 0xFF55CCCC, 0xFFFFCC00, 0xFF66FF66, 0xFFFF8844};
        return palette[cls % palette.length];
    }

    // 两个框的交并比（0-1 归一化坐标）
    float iou(float[] a, float[] b) {
        float ax1 = a[1], ay1 = a[0], ax2 = a[3], ay2 = a[2];
        float bx1 = b[1], by1 = b[0], bx2 = b[3], by2 = b[2];
        float ix1 = Math.max(ax1, bx1), iy1 = Math.max(ay1, by1);
        float ix2 = Math.min(ax2, bx2), iy2 = Math.min(ay2, by2);
        float iw = Math.max(0f, ix2 - ix1), ih = Math.max(0f, iy2 - iy1);
        float inter = iw * ih;
        float ua = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter;
        return ua <= 0f ? 0f : inter / ua;
    }

    // NV21 -> 300x300 RGB（双线性近似，取中心缩放）
    int[] nv21ToRgbScaled(byte[] nv21, int srcW, int srcH, int dst) {
        int[] out = new int[dst * dst * 3];
        int idx = 0;
        for (int y = 0; y < dst; y++) {
            int sy = y * srcH / dst;
            for (int x = 0; x < dst; x++) {
                int sx = x * srcW / dst;
                int yOff = sy * srcW + sx;
                int yv = nv21[yOff] & 0xFF;
                int uvOff = srcW * srcH + (sy / 2) * srcW + (sx / 2) * 2;
                int v = (nv21[uvOff] & 0xFF) - 128;      // NV21: V 在前
                int u = (nv21[uvOff + 1] & 0xFF) - 128;  // U 在后
                int r = yv + (int) (1.402f * v);
                int g = yv - (int) (0.344f * u) - (int) (0.714f * v);
                int b = yv + (int) (1.772f * u);
                if (r < 0) r = 0; else if (r > 255) r = 255;
                if (g < 0) g = 0; else if (g > 255) g = 255;
                if (b < 0) b = 0; else if (b > 255) b = 255;
                out[idx++] = r;
                out[idx++] = g;
                out[idx++] = b;
            }
        }
        return out;
    }

    // 整图分类：MobileNet 1000 类，top-3 标签
    void classifyScene(byte[] nv21) {
        if (clsLite == null || clsLabels.isEmpty()) return;
        int[] rgb = nv21ToRgbScaled(nv21, previewW, previewH, 224);
        ByteBuffer input = ByteBuffer.allocateDirect(224 * 224 * 3).order(ByteOrder.nativeOrder());
        for (int v : rgb) input.put((byte) (v & 0xFF));
        input.rewind();
        // MobileNet 输出固定 [1,1001]
        Object[] outputs = new Object[1];
        outputs[0] = new float[1][1001];
        java.util.Map<Integer, Object> outs = new java.util.HashMap<>();
        outs.put(0, outputs[0]);
        clsLite.runForMultipleInputsOutputs(new Object[]{input}, outs);
        float[] probs = ((float[][]) outputs[0])[0];
        int[] top = new int[3];
        float[] topv = new float[]{-1, -1, -1};
        for (int i = 1; i < probs.length && i < clsLabels.size(); i++) {
            float v = probs[i];
            for (int k = 0; k < 3; k++) {
                if (v > topv[k]) {
                    for (int j = 2; j > k; j--) { topv[j] = topv[j - 1]; top[j] = top[j - 1]; }
                    topv[k] = v;
                    top[k] = i;
                    break;
                }
            }
        }
        if (topv[0] < 0.30f) {
            // 整图分类置信度不足 30%：大概率是画面没有该类别，不强行报
            overallText = "";
        } else {
            // 只报最高置信的一个类别，避免把画面乱猜成奇怪的东西
            overallText = String.format("%s %.0f%%",
                    cnImageLabel(clsLabels.get(top[0])), topv[0] * 100);
        }
    }

    // ImageNet 常见类别中英对照（未收录的保持英文原样）
    private static final java.util.Map<String, String> CN_IMAGE = new java.util.HashMap<>();
    static {
        CN_IMAGE.put("golden retriever", "金毛犬"); CN_IMAGE.put("labrador retriever", "拉布拉多犬");
        CN_IMAGE.put("german shepherd", "德国牧羊犬"); CN_IMAGE.put("poodle", "贵宾犬");
        CN_IMAGE.put("husky", "哈士奇"); CN_IMAGE.put("bulldog", "斗牛犬");
        CN_IMAGE.put("pug", "巴哥犬"); CN_IMAGE.put("chihuahua", "吉娃娃");
        CN_IMAGE.put("beagle", "比格犬"); CN_IMAGE.put("samoyed", "萨摩耶");
        CN_IMAGE.put("corgi", "柯基犬"); CN_IMAGE.put("rottweiler", "罗威纳犬");
        CN_IMAGE.put("dachshund", "腊肠犬"); CN_IMAGE.put("tabby", "虎斑猫");
        CN_IMAGE.put("siamese cat", "暹罗猫"); CN_IMAGE.put("persian cat", "波斯猫");
        CN_IMAGE.put("egyptian cat", "埃及猫"); CN_IMAGE.put("tiger cat", "狸花猫");
        CN_IMAGE.put("lion", "狮子"); CN_IMAGE.put("tiger", "老虎");
        CN_IMAGE.put("leopard", "豹"); CN_IMAGE.put("cheetah", "猎豹");
        CN_IMAGE.put("jaguar", "美洲豹"); CN_IMAGE.put("snow leopard", "雪豹");
        CN_IMAGE.put("elephant", "大象"); CN_IMAGE.put("bear", "熊");
        CN_IMAGE.put("zebra", "斑马"); CN_IMAGE.put("giraffe", "长颈鹿");
        CN_IMAGE.put("camel", "骆驼"); CN_IMAGE.put("horse", "马");
        CN_IMAGE.put("cow", "牛"); CN_IMAGE.put("pig", "猪");
        CN_IMAGE.put("sheep", "羊"); CN_IMAGE.put("goat", "山羊");
        CN_IMAGE.put("chicken", "鸡"); CN_IMAGE.put("rooster", "公鸡");
        CN_IMAGE.put("duck", "鸭"); CN_IMAGE.put("goose", "鹅");
        CN_IMAGE.put("swan", "天鹅"); CN_IMAGE.put("owl", "猫头鹰");
        CN_IMAGE.put("parrot", "鹦鹉"); CN_IMAGE.put("eagle", "鹰");
        CN_IMAGE.put("pigeon", "鸽子"); CN_IMAGE.put("sparrow", "麻雀");
        CN_IMAGE.put("robin", "知更鸟"); CN_IMAGE.put("flamingo", "火烈鸟");
        CN_IMAGE.put("peacock", "孔雀"); CN_IMAGE.put("penguin", "企鹅");
        CN_IMAGE.put("whale", "鲸鱼"); CN_IMAGE.put("dolphin", "海豚");
        CN_IMAGE.put("shark", "鲨鱼"); CN_IMAGE.put("goldfish", "金鱼");
        CN_IMAGE.put("frog", "青蛙"); CN_IMAGE.put("turtle", "乌龟");
        CN_IMAGE.put("snake", "蛇"); CN_IMAGE.put("lizard", "蜥蜴");
        CN_IMAGE.put("butterfly", "蝴蝶"); CN_IMAGE.put("bee", "蜜蜂");
        CN_IMAGE.put("ant", "蚂蚁"); CN_IMAGE.put("spider", "蜘蛛");
        CN_IMAGE.put("ladybug", "瓢虫");
        CN_IMAGE.put("car", "汽车"); CN_IMAGE.put("sports car", "跑车");
        CN_IMAGE.put("limousine", "豪华轿车"); CN_IMAGE.put("taxi", "出租车");
        CN_IMAGE.put("ambulance", "救护车"); CN_IMAGE.put("fire engine", "消防车");
        CN_IMAGE.put("police van", "警车"); CN_IMAGE.put("school bus", "校车");
        CN_IMAGE.put("bus", "公交车"); CN_IMAGE.put("trolleybus", "无轨电车");
        CN_IMAGE.put("train", "火车"); CN_IMAGE.put("bullet train", "高铁");
        CN_IMAGE.put("airplane", "飞机"); CN_IMAGE.put("airliner", "客机");
        CN_IMAGE.put("helicopter", "直升机"); CN_IMAGE.put("bicycle", "自行车");
        CN_IMAGE.put("mountain bike", "山地车"); CN_IMAGE.put("motorcycle", "摩托车");
        CN_IMAGE.put("scooter", "踏板车"); CN_IMAGE.put("truck", "卡车");
        CN_IMAGE.put("pickup truck", "皮卡"); CN_IMAGE.put("forklift", "叉车");
        CN_IMAGE.put("tractor", "拖拉机"); CN_IMAGE.put("ship", "轮船");
        CN_IMAGE.put("yacht", "游艇"); CN_IMAGE.put("canoe", "独木舟");
        CN_IMAGE.put("rowboat", "划艇");
        CN_IMAGE.put("laptop", "笔记本电脑"); CN_IMAGE.put("notebook", "笔记本电脑");
        CN_IMAGE.put("desktop computer", "台式电脑"); CN_IMAGE.put("computer keyboard", "键盘");
        CN_IMAGE.put("mouse", "鼠标"); CN_IMAGE.put("monitor", "显示器");
        CN_IMAGE.put("television", "电视"); CN_IMAGE.put("smartphone", "智能手机");
        CN_IMAGE.put("cell phone", "手机"); CN_IMAGE.put("tablet", "平板电脑");
        CN_IMAGE.put("camera", "相机"); CN_IMAGE.put("digital camera", "数码相机");
        CN_IMAGE.put("webcam", "摄像头"); CN_IMAGE.put("microphone", "麦克风");
        CN_IMAGE.put("printer", "打印机"); CN_IMAGE.put("scanner", "扫描仪");
        CN_IMAGE.put("router", "路由器"); CN_IMAGE.put("hard disk", "硬盘");
        CN_IMAGE.put("remote control", "遥控器"); CN_IMAGE.put("gamepad", "游戏手柄");
        CN_IMAGE.put("joystick", "操纵杆"); CN_IMAGE.put("headphone", "耳机");
        CN_IMAGE.put("earphone", "耳机"); CN_IMAGE.put("speaker", "音箱");
        CN_IMAGE.put("projector", "投影仪"); CN_IMAGE.put("calculator", "计算器");
        CN_IMAGE.put("watch", "手表"); CN_IMAGE.put("clock", "时钟");
        CN_IMAGE.put("alarm clock", "闹钟");
        CN_IMAGE.put("chair", "椅子"); CN_IMAGE.put("armchair", "扶手椅");
        CN_IMAGE.put("sofa", "沙发"); CN_IMAGE.put("couch", "沙发");
        CN_IMAGE.put("table", "桌子"); CN_IMAGE.put("dining table", "餐桌");
        CN_IMAGE.put("desk", "书桌"); CN_IMAGE.put("bed", "床");
        CN_IMAGE.put("wardrobe", "衣柜"); CN_IMAGE.put("dresser", "梳妆台");
        CN_IMAGE.put("bookshelf", "书架"); CN_IMAGE.put("bookcase", "书柜");
        CN_IMAGE.put("lamp", "台灯"); CN_IMAGE.put("floor lamp", "落地灯");
        CN_IMAGE.put("ceiling fan", "吊扇"); CN_IMAGE.put("refrigerator", "冰箱");
        CN_IMAGE.put("freezer", "冰柜"); CN_IMAGE.put("dishwasher", "洗碗机");
        CN_IMAGE.put("microwave", "微波炉"); CN_IMAGE.put("toaster", "烤面包机");
        CN_IMAGE.put("oven", "烤箱"); CN_IMAGE.put("stove", "炉灶");
        CN_IMAGE.put("washing machine", "洗衣机"); CN_IMAGE.put("dryer", "烘干机");
        CN_IMAGE.put("vacuum cleaner", "吸尘器"); CN_IMAGE.put("iron", "熨斗");
        CN_IMAGE.put("coffee maker", "咖啡机"); CN_IMAGE.put("kettle", "水壶");
        CN_IMAGE.put("sink", "水槽"); CN_IMAGE.put("bathtub", "浴缸");
        CN_IMAGE.put("toilet", "马桶"); CN_IMAGE.put("shower", "淋浴");
        CN_IMAGE.put("mirror", "镜子"); CN_IMAGE.put("window", "窗户");
        CN_IMAGE.put("door", "门"); CN_IMAGE.put("curtain", "窗帘");
        CN_IMAGE.put("rug", "地毯"); CN_IMAGE.put("pillow", "枕头");
        CN_IMAGE.put("blanket", "毯子"); CN_IMAGE.put("towel", "毛巾");
        CN_IMAGE.put("banana", "香蕉"); CN_IMAGE.put("apple", "苹果");
        CN_IMAGE.put("orange", "橙子"); CN_IMAGE.put("lemon", "柠檬");
        CN_IMAGE.put("lime", "青柠"); CN_IMAGE.put("strawberry", "草莓");
        CN_IMAGE.put("blueberry", "蓝莓"); CN_IMAGE.put("pineapple", "菠萝");
        CN_IMAGE.put("watermelon", "西瓜"); CN_IMAGE.put("grape", "葡萄");
        CN_IMAGE.put("peach", "桃子"); CN_IMAGE.put("pear", "梨");
        CN_IMAGE.put("cherry", "樱桃"); CN_IMAGE.put("mango", "芒果");
        CN_IMAGE.put("avocado", "牛油果"); CN_IMAGE.put("tomato", "番茄");
        CN_IMAGE.put("cucumber", "黄瓜"); CN_IMAGE.put("carrot", "胡萝卜");
        CN_IMAGE.put("potato", "土豆"); CN_IMAGE.put("broccoli", "西兰花");
        CN_IMAGE.put("cauliflower", "菜花"); CN_IMAGE.put("mushroom", "蘑菇");
        CN_IMAGE.put("onion", "洋葱"); CN_IMAGE.put("garlic", "大蒜");
        CN_IMAGE.put("corn", "玉米"); CN_IMAGE.put("rice", "米饭");
        CN_IMAGE.put("noodle", "面条"); CN_IMAGE.put("bread", "面包");
        CN_IMAGE.put("bagel", "贝果"); CN_IMAGE.put("croissant", "羊角面包");
        CN_IMAGE.put("pretzel", "椒盐脆饼"); CN_IMAGE.put("pizza", "披萨");
        CN_IMAGE.put("hamburger", "汉堡"); CN_IMAGE.put("hotdog", "热狗");
        CN_IMAGE.put("sandwich", "三明治"); CN_IMAGE.put("taco", "墨西哥卷饼");
        CN_IMAGE.put("burrito", "墨西哥卷"); CN_IMAGE.put("french fries", "薯条");
        CN_IMAGE.put("donut", "甜甜圈"); CN_IMAGE.put("cake", "蛋糕");
        CN_IMAGE.put("cupcake", "纸杯蛋糕"); CN_IMAGE.put("cookie", "曲奇");
        CN_IMAGE.put("ice cream", "冰淇淋"); CN_IMAGE.put("popsicle", "冰棒");
        CN_IMAGE.put("chocolate", "巧克力"); CN_IMAGE.put("candy", "糖果");
        CN_IMAGE.put("popcorn", "爆米花"); CN_IMAGE.put("cheese", "奶酪");
        CN_IMAGE.put("egg", "鸡蛋"); CN_IMAGE.put("fried egg", "煎蛋");
        CN_IMAGE.put("butter", "黄油"); CN_IMAGE.put("yogurt", "酸奶");
        CN_IMAGE.put("milk", "牛奶"); CN_IMAGE.put("coffee", "咖啡");
        CN_IMAGE.put("espresso", "浓缩咖啡"); CN_IMAGE.put("tea", "茶");
        CN_IMAGE.put("beer", "啤酒"); CN_IMAGE.put("wine", "葡萄酒");
        CN_IMAGE.put("champagne", "香槟"); CN_IMAGE.put("juice", "果汁");
        CN_IMAGE.put("soda", "汽水"); CN_IMAGE.put("water bottle", "水瓶");
        CN_IMAGE.put("tennis ball", "网球"); CN_IMAGE.put("baseball", "棒球");
        CN_IMAGE.put("basketball", "篮球"); CN_IMAGE.put("football", "橄榄球");
        CN_IMAGE.put("soccer ball", "足球"); CN_IMAGE.put("volleyball", "排球");
        CN_IMAGE.put("golf ball", "高尔夫球"); CN_IMAGE.put("golf club", "高尔夫球杆");
        CN_IMAGE.put("tennis racket", "网球拍"); CN_IMAGE.put("baseball bat", "棒球棒");
        CN_IMAGE.put("hockey puck", "冰球"); CN_IMAGE.put("skis", "滑雪板");
        CN_IMAGE.put("snowboard", "滑雪单板"); CN_IMAGE.put("surfboard", "冲浪板");
        CN_IMAGE.put("kite", "风筝"); CN_IMAGE.put("frisbee", "飞盘");
        CN_IMAGE.put("skateboard", "滑板"); CN_IMAGE.put("roller skates", "旱冰鞋");
        CN_IMAGE.put("ice skate", "冰鞋"); CN_IMAGE.put("boxing glove", "拳击手套");
        CN_IMAGE.put("dumbbell", "哑铃"); CN_IMAGE.put("barbell", "杠铃");
        CN_IMAGE.put("bow", "弓"); CN_IMAGE.put("arrow", "箭");
        CN_IMAGE.put("sword", "剑"); CN_IMAGE.put("shield", "盾牌");
        CN_IMAGE.put("book", "书"); CN_IMAGE.put("magazine", "杂志");
        CN_IMAGE.put("newspaper", "报纸"); CN_IMAGE.put("envelope", "信封");
        CN_IMAGE.put("pen", "钢笔"); CN_IMAGE.put("pencil", "铅笔");
        CN_IMAGE.put("marker", "马克笔"); CN_IMAGE.put("crayon", "蜡笔");
        CN_IMAGE.put("scissors", "剪刀"); CN_IMAGE.put("knife", "刀");
        CN_IMAGE.put("spoon", "勺子"); CN_IMAGE.put("fork", "叉子");
        CN_IMAGE.put("plate", "盘子"); CN_IMAGE.put("bowl", "碗");
        CN_IMAGE.put("cup", "杯子"); CN_IMAGE.put("mug", "马克杯");
        CN_IMAGE.put("bottle", "瓶子"); CN_IMAGE.put("wine bottle", "酒瓶");
        CN_IMAGE.put("beer bottle", "啤酒瓶"); CN_IMAGE.put("bucket", "水桶");
        CN_IMAGE.put("trash can", "垃圾桶"); CN_IMAGE.put("recycling bin", "回收箱");
        CN_IMAGE.put("umbrella", "雨伞"); CN_IMAGE.put("backpack", "背包");
        CN_IMAGE.put("handbag", "手提包"); CN_IMAGE.put("wallet", "钱包");
        CN_IMAGE.put("purse", "钱包"); CN_IMAGE.put("suitcase", "行李箱");
        CN_IMAGE.put("briefcase", "公文包"); CN_IMAGE.put("luggage", "行李");
        CN_IMAGE.put("tie", "领带"); CN_IMAGE.put("hat", "帽子");
        CN_IMAGE.put("baseball cap", "棒球帽"); CN_IMAGE.put("helmet", "头盔");
        CN_IMAGE.put("crown", "皇冠"); CN_IMAGE.put("sunglasses", "太阳镜");
        CN_IMAGE.put("glasses", "眼镜"); CN_IMAGE.put("necklace", "项链");
        CN_IMAGE.put("ring", "戒指"); CN_IMAGE.put("bracelet", "手链");
        CN_IMAGE.put("earring", "耳环"); CN_IMAGE.put("shoe", "鞋子");
        CN_IMAGE.put("sneaker", "运动鞋"); CN_IMAGE.put("boot", "靴子");
        CN_IMAGE.put("sandal", "凉鞋"); CN_IMAGE.put("slipper", "拖鞋");
        CN_IMAGE.put("high-heeled shoe", "高跟鞋"); CN_IMAGE.put("sock", "袜子");
        CN_IMAGE.put("glove", "手套"); CN_IMAGE.put("scarf", "围巾");
        CN_IMAGE.put("belt", "腰带"); CN_IMAGE.put("button", "纽扣");
        CN_IMAGE.put("zipper", "拉链"); CN_IMAGE.put("teddy bear", "泰迪熊");
        CN_IMAGE.put("doll", "娃娃"); CN_IMAGE.put("ball", "球");
        CN_IMAGE.put("puzzle", "拼图"); CN_IMAGE.put("card", "卡片");
        CN_IMAGE.put("dice", "骰子"); CN_IMAGE.put("domino", "多米诺骨牌");
        CN_IMAGE.put("chess piece", "棋子"); CN_IMAGE.put("balloon", "气球");
        CN_IMAGE.put("flag", "旗帜"); CN_IMAGE.put("statue", "雕像");
        CN_IMAGE.put("monument", "纪念碑"); CN_IMAGE.put("fountain", "喷泉");
        CN_IMAGE.put("bridge", "桥"); CN_IMAGE.put("castle", "城堡");
        CN_IMAGE.put("church", "教堂"); CN_IMAGE.put("temple", "寺庙");
        CN_IMAGE.put("pagoda", "塔"); CN_IMAGE.put("lighthouse", "灯塔");
        CN_IMAGE.put("windmill", "风车"); CN_IMAGE.put("traffic light", "红绿灯");
        CN_IMAGE.put("street sign", "路牌"); CN_IMAGE.put("mailbox", "邮箱");
        CN_IMAGE.put("fire hydrant", "消防栓"); CN_IMAGE.put("bench", "长椅");
        CN_IMAGE.put("parking meter", "停车计时器"); CN_IMAGE.put("hammock", "吊床");
        CN_IMAGE.put("tent", "帐篷"); CN_IMAGE.put("sleeping bag", "睡袋");
        CN_IMAGE.put("flower", "花"); CN_IMAGE.put("tree", "树");
        CN_IMAGE.put("grass", "草"); CN_IMAGE.put("leaf", "叶子");
        CN_IMAGE.put("mountain", "山"); CN_IMAGE.put("ocean", "海洋");
        CN_IMAGE.put("beach", "海滩"); CN_IMAGE.put("sand", "沙子");
        CN_IMAGE.put("rock", "岩石"); CN_IMAGE.put("stone", "石头");
        CN_IMAGE.put("rainbow", "彩虹"); CN_IMAGE.put("sun", "太阳");
        CN_IMAGE.put("moon", "月亮"); CN_IMAGE.put("star", "星星");
        CN_IMAGE.put("cloud", "云"); CN_IMAGE.put("snow", "雪");
        CN_IMAGE.put("rain", "雨"); CN_IMAGE.put("fire", "火");
    }

    private String cnImageLabel(String en) {
        String c = CN_IMAGE.get(en);
        return c != null ? c : en;
    }

    Class<?> dtypeClass(DataType dt) {
        switch (dt) {
            case INT32: return int.class;
            case UINT8: return byte.class;
            case INT64: return long.class;
            case BOOL: return boolean.class;
            default: return float.class;
        }
    }

    void loadClsModel() throws Exception {
        InputStream is = getAssets().open("mobilenet_v2.tflite");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int r;
        while ((r = is.read(chunk)) > 0) bos.write(chunk, 0, r);
        is.close();
        byte[] buf = bos.toByteArray();
        ByteBuffer bb = ByteBuffer.allocateDirect(buf.length).order(ByteOrder.nativeOrder());
        bb.put(buf);
        bb.rewind();
        clsLite = new Interpreter(bb);
    }

    void loadClsLabels() throws Exception {
        InputStream is = getAssets().open("imagenet_labels.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) clsLabels.add(line);
        }
        br.close();
    }

    void loadModel() throws Exception {
        InputStream is = getAssets().open("detect.tflite");
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int r;
        while ((r = is.read(chunk)) > 0) {
            bos.write(chunk, 0, r);
        }
        is.close();
        byte[] buf = bos.toByteArray();
        ByteBuffer bb = ByteBuffer.allocateDirect(buf.length).order(ByteOrder.nativeOrder());
        bb.put(buf);
        bb.rewind();
        tfLite = new Interpreter(bb);
    }

    void loadLabels() throws Exception {
        InputStream is = getAssets().open("labelmap.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty()) labels.add(line);
        }
        br.close();
        // labelmap 索引 0 是 background(???)，模型输出类别号从 1 开始，直接按索引取标签
        if (labels.size() < 91) {
            throw new Exception("标签文件异常（应含 90 类）");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        if (inferThread != null) {
            inferThread.quitSafely();
        }
        if (tfLite != null) tfLite.close();
        if (clsLite != null) clsLite.close();
    }

    int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 检测结果 */
    static class Detected {
        int cls;
        float score;
        float[] rect;   // y1,x1,y2,x2 归一化
        String label;
        float distM;
        float widthM;
        int color;
    }

    /** 识别框叠加层 */
    class OverlayView extends View {
        List<Detected> results = new ArrayList<>();
        Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        OverlayView(android.content.Context c) {
            super(c);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(dp(2.5f));
            textPaint.setTextSize(dp(13));
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        void setResults(List<Detected> list) {
            results = list;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (viewW <= 0 || viewH <= 0 || previewW <= 0 || previewH <= 0) return;
            // 预览旋转后宽高可能对调
            boolean rotated = (previewH > previewW) != (viewH > viewW);
            int dispW = rotated ? previewH : previewW;
            int dispH = rotated ? previewW : previewH;
            float scale = Math.min((float) viewW / dispW, (float) viewH / dispH);
            float drawW = dispW * scale;
            float drawH = dispH * scale;
            float offX = (viewW - drawW) / 2f;
            float offY = (viewH - drawH) / 2f;

            for (Detected d : results) {
                float y1 = d.rect[0], x1 = d.rect[1], y2 = d.rect[2], x2 = d.rect[3];
                float l = x1 * dispW, t = y1 * dispH, r = x2 * dispW, b = y2 * dispH;
                boxPaint.setColor(d.color);
                boxPaint.setStrokeWidth(dp(2.5f));
                canvas.drawRect(offX + l * scale, offY + t * scale,
                        offX + r * scale, offY + b * scale, boxPaint);
                // 标签底
                String tag = String.format("%s %.0f%%", d.label, d.score * 100);
                if (d.distM > 0) {
                    tag += String.format(" %.1fm", d.distM);
                }
                Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                fill.setColor(d.color);
                float tw = textPaint.measureText(tag);
                float ty = offY + t * scale;
                canvas.drawRect(offX + l * scale, ty - dp(20), offX + l * scale + tw + dp(8), ty, fill);
                textPaint.setColor(Color.WHITE);
                canvas.drawText(tag, offX + l * scale + dp(4), ty - dp(6), textPaint);
            }
        }
    }
}
