package uni.dcloud.io.uniplugin_richalert;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.FaceDetector;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import uni.dcloud.io.uniplugin_richalert.face.RecordVideoInterface;
import uni.dcloud.io.uniplugin_richalert.face.RecordVideoSurfaceView;
import uni.dcloud.io.uniplugin_richalert.face.ShowToast;
import uni.dcloud.io.uniplugin_richalert.face.StaticArguments;
import uni.dcloud.io.uniplugin_richalert.permission.CheckPermissionUtils;
import uni.dcloud.io.uniplugin_richalert.widget.HomeKeyListener;

/**
 * Name: FaceActivity
 * Author: Administrator
 * Email:
 * Comment: //TODO
 * Date: 2019-07-18 14:37
 */
public class FaceActivity extends Activity implements View.OnClickListener{
    private Button ivBack;
    private TextView tvTips;
    private String shoot_tips_1, shoot_tips_2, shoot_tips_3, shoot_tips_4;
    // 预览surFaceViewPreview
    private RecordVideoSurfaceView surFaceViewPreview;
    public Chronometer chronometer;
    // Camera nv21格式预览帧的尺寸，默认设置640*480
    private int PREVIEW_WIDTH = 640;
    private int PREVIEW_HEIGHT = 480;
    // 预览帧数据存储数组和缓存数组
    private byte[] nv21;
    private byte[] buffer;

    private boolean mStopTrack;
    private int isAlign = 1;
    private static int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

    // 进行人脸检测及活体检测的子线程
    private Thread mThread;
    private Context mContext;
    private Button btnSubmit;
    private Button btnTakeAgain;
    // 是否录像
    private boolean isRecord = false;
    private int mRecordMaxTime = 7;// 一次拍摄最长时间
    private int mTimeCount = 0;// 时间计数
    private Timer mTimer;// 计时器
    private int width;
    private int height;
    private DisplayMetrics metrics;


    private static String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO

    };
    List<String> mPermissionList = new ArrayList<>();
    private static final int MY_PERMISSIONS_REQUEST_CALL_CAMERA = 2;
    private static final int NOT_NOTICE = 3;//如果勾选了不再询问



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//竖屏
        initPermission();
        Log.i("liyangzi","onCreate");
        setContentView(R.layout.record_video_layout);
        this.mContext = this;
       this.init();
    }
    private void initPermission()
    {
        mPermissionList.clear();
        for (int i = 0; i < permissions.length; i++) {
            if (ContextCompat.checkSelfPermission(FaceActivity.this, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                mPermissionList.add(permissions[i]);
            }
        }
        if (mPermissionList.isEmpty()) {//未授予的权限为空，表示都授予了
            Toast.makeText(FaceActivity.this,"已经授权",Toast.LENGTH_LONG).show();

        } else {//请求权限方法
            String[] permissions = mPermissionList.toArray(new String[mPermissionList.size()]);//将List转为数组
            ActivityCompat.requestPermissions(FaceActivity.this, permissions, MY_PERMISSIONS_REQUEST_CALL_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        if (requestCode == MY_PERMISSIONS_REQUEST_CALL_CAMERA){
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    //始终不允许
                    //判断是否勾选禁止后不再询问
                    boolean showRequestPermission = ActivityCompat.shouldShowRequestPermissionRationale(FaceActivity.this, permissions[i]);
                    if (!showRequestPermission) {//用户选择了禁止不再询问

                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);//注意就是"package",不用改成自己的包名
                        intent.setData(uri);
                        startActivityForResult(intent, NOT_NOTICE);

                    }
                    else
                    {
                        Toast.makeText(FaceActivity.this,"权限未申请",Toast.LENGTH_LONG).show();
                    }
                }
                else
                {//始终允许
                    Toast.makeText(this, "" + "权限" + permissions[i] + "申请成功", Toast.LENGTH_SHORT).show();
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void init() {
        this.initModule();
        this.addListener();
        this.stop();
        this.prepareRecord();
        RecordVideoInterface.getInstance(mContext).doOpenCamera(surFaceViewPreview.getSurfaceHolder(), Camera.CameraInfo.CAMERA_FACING_FRONT);
        RecordVideoInterface.getInstance(mContext).doStartPreview(surFaceViewPreview.getSurfaceHolder(), 1.333f);
        this.isRecord = false;
        this.mStopTrack = false;


    }

    private void initModule() {
        this.ivBack = (Button) findViewById(R.id.ivBack);
        this.tvTips = (TextView) findViewById(R.id.tvTips);
        this.surFaceViewPreview = (RecordVideoSurfaceView) findViewById(R.id.movieRecordSurfaceView);
        this.shoot_tips_1 = getResources().getString(R.string.shoot_tips_1);
        this.shoot_tips_2 = getResources().getString(R.string.shoot_tips_2);
        this.shoot_tips_3 = getResources().getString(R.string.shoot_tips_3);
        this.shoot_tips_4 = getResources().getString(R.string.shoot_tips_4);
        this.chronometer = (Chronometer) findViewById(R.id.chronometer);
        this.btnSubmit = (Button) findViewById(R.id.btnSubmit);
        this.btnTakeAgain = (Button) findViewById(R.id.btnTakeAgain);
        // 设置顶部tips
        this.tvTips.setText(shoot_tips_1);
        this.setSurfaceSize();
        // 创建录像存储路径
        RecordVideoInterface.getInstance(mContext).createRecordDir();

    }

    private void addListener() {
        this.ivBack.setOnClickListener(this);
        this.btnSubmit.setOnClickListener(this);
        this.btnTakeAgain.setOnClickListener(this);
    }

    /**
     * 设置SurfaceSize
     */
    private void setSurfaceSize() {
        this.metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        this.width = metrics.widthPixels;
        this.height = (int) (width * PREVIEW_WIDTH / (float) PREVIEW_HEIGHT);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, height);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        this.surFaceViewPreview.setLayoutParams(params);
    }

    /**
     * 设置计时器
     */
    public void initData() {
        this.chronometer.setBase(SystemClock.elapsedRealtime());//计时器清零
        this.chronometer.setFormat("%s"); // 00：00
    }

    /**
     * 准备录音
     */
    public void prepareRecord() {
        //预览
        btnTakeAgain.setVisibility(View.VISIBLE);
        ivBack.setVisibility(View.VISIBLE);
        // 计时器清零
        this.initData();
    }

    private void start()
    {
        this.surFaceViewPreview.setVisibility(View.VISIBLE);
        RecordVideoInterface.getInstance(mContext).delFile();
        this.stop();
        this.tvTips.setText(shoot_tips_1);
        this.prepareRecord();
        RecordVideoInterface.getInstance(mContext).doOpenCamera(surFaceViewPreview.getSurfaceHolder(), Camera.CameraInfo.CAMERA_FACING_FRONT);
        RecordVideoInterface.getInstance(mContext).doStartPreview(surFaceViewPreview.getSurfaceHolder(), 1.333f);

        this.isRecord = false;
        this.mStopTrack = false;
        RecordVideoInterface.getInstance(mContext).createRecordDir();
        this.setFaceDetection();

    }
    @Override
    public void onResume() {
        super.onResume();
        Log.i("liyangzi","onResume");
        //start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("liyangzi","onStop");
    }

    /**
     * 人脸检测
     */

    private void setFaceDetection() {
        this.mTimeCount = 0;
        this.mThread = new Thread(new Runnable() {
            @Override
            public void run() {

                if (isRecord) {
                    return;
                }
                mHandler.sendEmptyMessage(StaticArguments.START_RECORD);

            }

        });
        mThread.start();
    }

    /**
     * 录制完成
     */
    private void recordComplete() {
        this.tvTips.setText(shoot_tips_4);
        this.chronometer.stop();
        this.initData();
        this.stop();
    }

    /**
     * 设置view是否显示隐藏
     *
     * @param isShow
     */

    /**
     * 停止拍摄
     */
    public void stop() {
        Log.d("liyangzi","stop");
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
        RecordVideoInterface.getInstance(mContext).stopRecord();
        RecordVideoInterface.getInstance(mContext).releaseRecord();
        RecordVideoInterface.getInstance(mContext).doDestroyCamera();
        RecordVideoInterface.getInstance(mContext).doDestroyCameraInterface();
    }

    /**
     * 开始录制视频
     *
     * @param
     * @param
     */
    public void startRecord() {
        isRecord = true;//表示已经录制了
        mStopTrack = true;
        this.initData();
        try {
            mTimeCount = 0;
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    // 开始计时器
                    mHandler.sendEmptyMessage(StaticArguments.START_CHRONOMETER);
                    if (mTimeCount == mRecordMaxTime) {// 达到指定时间，停止拍摄
                        // 显示发送，重拍按钮
                        mHandler.sendEmptyMessage(StaticArguments.RECORD_COMPLETE);
                    } else if (mTimeCount == 4) {//2s
                        // 显示左右摇头
                        mHandler.sendEmptyMessage(StaticArguments.SHOW_SHAKEHEAD);
                    } else if (mTimeCount == 6) { //4s
                        // 显示点头
                        mHandler.sendEmptyMessage(StaticArguments.SHOW_NODHEAD);
                    }
                    mTimeCount++;
                }
            }, 0, 1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        Log.i("liyangzi","onPause");
        RecordVideoInterface.getInstance(mContext).delFile();
        RecordVideoInterface.getInstance(mContext).doDestroyCamera();
        RecordVideoInterface.getInstance(mContext).doDestroyCameraInterface();
        this.mStopTrack = true;
        stop();
        isRecord = false;
        recordComplete();
        this.destroyThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("liyangzi","onDestroy");
        RecordVideoInterface.getInstance(mContext).doDestroyCamera();
        this.mStopTrack = true;
        // 销毁对象
        // 取消广播监听
      /*  if (homeKeyListener != null) {
            homeKeyListener.stopHomeListener(); //关闭监听
        }*/
        isRecord = false;
        recordComplete();
        RecordVideoInterface.getInstance(mContext).delFile();
        RecordVideoInterface.getInstance(mContext).stopRecord();
        RecordVideoInterface.getInstance(mContext).releaseRecord();
        RecordVideoInterface.getInstance(mContext).doDestroyCameraInterface();
        RecordVideoInterface.getInstance(mContext).doDestroyCamera();
        this.destroyThread();

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.ivBack) {
            RecordVideoInterface.getInstance(mContext).delFile();
            isRecord = false;
            recordComplete();
            this.finish();
        } else if (id == R.id.btnTakeAgain) {
            this.surFaceViewPreview.setVisibility(View.VISIBLE);
            RecordVideoInterface.getInstance(mContext).delFile();
            this.stop();
            this.tvTips.setText(shoot_tips_1);
            this.prepareRecord();
            RecordVideoInterface.getInstance(mContext).doOpenCamera(surFaceViewPreview.getSurfaceHolder(), Camera.CameraInfo.CAMERA_FACING_FRONT);
            RecordVideoInterface.getInstance(mContext).doStartPreview(surFaceViewPreview.getSurfaceHolder(), 1.333f);

            this.isRecord = false;
            this.mStopTrack = false;

            // 人脸识别
            this.setFaceDetection();
        } else if (id == R.id.btnSubmit) {
            this.isRecord = false;
            this.mStopTrack = false;
            uploadVedio();
        }
    }

    /**
     * 上传视频文件
     */
    public void uploadVedio() {
        //LoadingDialog.showDialog(mContext, "文件上传中,请稍后...", false);
        TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
//        String android_imsi = tm.getSubscriberId();//获取手机IMSI号
        String imei = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
        String params = "";
        String secrect = "";
        final Map<String, Object> map = new HashMap<>();
        map.put("appver", 1);
        map.put("appName", "vsimAndroid");
        map.put("imsi", imei);
        map.put("ip", getLocalIpAddress());
        map.put("number", "13051499351");
        map.put("type", 4);
        map.put("token", "63e28f89-02ff-4765-8034-6c5ad9b83d1e");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
            params += entry.getKey() + "=" + entry.getValue() + "&";
        }
        params = params.substring(0, params.length() - 1);

        //secrect = MD5Util.GetMD5Code(params);
        map.put("secrect", secrect);
        String url = RecordVideoInterface.getInstance(mContext).getmVecordFile().getAbsolutePath();
        String[] uploadFile = new String[]{url};
        final HashMap<String, String[]> fileMap = new HashMap<String, String[]>();
        fileMap.put("userFile", uploadFile);
        new Thread() {
            @Override
            public void run() {
                //把网络访问的代码放在这里
                //  HttpUploadUtils.formUpload(new UrlApi().url, map, fileMap, mHandler);
            }
        }.start();

    }

    /**
     * 获取手机ip
     *
     * @return
     */
    public String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
        }
        return null;
    }

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            try {
                switch (msg.what) {
                    case StaticArguments.START_RECORD: //扫描到人脸，开始录制

                        try {
                            Camera mCamera = RecordVideoInterface.getInstance(mContext).getCameraInstance();
                            if (mCamera != null) {
                                mCamera.unlock();
                            }
                            // 准备录制视频
                            RecordVideoInterface.getInstance(mContext).initRecord(surFaceViewPreview);
                            startRecord(); // 开始录制视频
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;

                    case StaticArguments.START_CHRONOMETER: //开启计时器
                        // 开始启动计时器
                        chronometer.start();
                        break;

                    case StaticArguments.RECORD_COMPLETE: //录制完成

                        recordComplete();
                        break;
                    case StaticArguments.SHOW_SHAKEHEAD: //显示左右摇头

                        tvTips.setText(shoot_tips_2);
                        break;
                    case StaticArguments.SHOW_NODHEAD: //显示点头
                        tvTips.setText(shoot_tips_3);
                        break;
                    case StaticArguments.UPLOAD_SUCCESS://上传成功
               /*         try {
                            LoadingDialog.closeDialog();
                            if (null != msg.obj) {
                                String data = (String) msg.obj;
                                if (!TextUtils.isEmpty(data)) {
                                    BaseResponse baseResponse = JsonUtil.parseObject(data, BaseResponse.class);
                                    if (baseResponse.getResult().equals("1")) {
                                        Toast.makeText(mContext, "上传成功!", Toast.LENGTH_LONG).show();
                                        RecordVideoInterface.getInstance(mContext).delFile();
                                        stop();
                                        if (null != mAcceler) {
                                            mAcceler.stop();
                                            mAcceler = null;
                                        }
                                        if (null != mFaceDetector) {
                                            mFaceDetector.destroy();
                                            mFaceDetector = null;
                                        }
                                        startActivityForResult(new Intent(mContext, IdentifyVerifyActivity.class), 0);
                                    } else {
                                        Toast.makeText(mContext, "上传失败!" + baseResponse.getMessage(), Toast.LENGTH_LONG).show();
                                        finish();
                                    }
                                } else {
                                    Toast.makeText(mContext, "服务器异常!", Toast.LENGTH_LONG).show();
                                }

                            } else {
                                Toast.makeText(mContext, "服务器异常!", Toast.LENGTH_LONG).show();
                            }

                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Toast.makeText(mContext, "服务器异常!", Toast.LENGTH_LONG).show();
                        }*/

                        break;
                    case StaticArguments.UPLOAD_FAIL://上传失败
                      /*  try {
                            LoadingDialog.closeDialog();
                            String errorData = "";
                            if (null != msg.obj) {
                                errorData = (String) msg.obj;
                            }
                            Toast.makeText(mContext, "上传失败!" + errorData, Toast.LENGTH_LONG).show();
                            finish();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                            Toast.makeText(mContext, "服务器异常!", Toast.LENGTH_LONG).show();
                        }*/
                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    };


    /**
     * 销毁线程方法
     */
    private void destroyThread() {
        try {
            if (null != mThread && Thread.State.RUNNABLE == mThread.getState()) {
                try {
                    Thread.sleep(500);
                    mThread.interrupt();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mThread = null;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 根据上面发送过去的请求吗来区别
        switch (requestCode) {
            case 0:
                this.tvTips.setText(shoot_tips_1);
                this.prepareRecord();
                this.isRecord = false;
                this.mStopTrack = false;
                RecordVideoInterface.getInstance(mContext).doOpenCamera(surFaceViewPreview.getSurfaceHolder(), Camera.CameraInfo.CAMERA_FACING_FRONT);
                RecordVideoInterface.getInstance(mContext).doStartPreview(surFaceViewPreview.getSurfaceHolder(), 1.333f);
                // 人脸识别
                this.setFaceDetection();
                break;

            case 3:
                initPermission();//由于不知道是否选择了允许所以需要再次判断
                break;
            default:
                break;
        }
    }

}
