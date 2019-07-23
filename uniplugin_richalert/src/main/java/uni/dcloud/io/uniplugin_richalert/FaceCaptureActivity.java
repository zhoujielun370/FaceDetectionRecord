package uni.dcloud.io.uniplugin_richalert;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import uni.dcloud.io.uniplugin_richalert.capture.CameraPreview;
import uni.dcloud.io.uniplugin_richalert.capture.CircleCameraLayout;
import uni.dcloud.io.uniplugin_richalert.capture.FaceHelper;
import uni.dcloud.io.uniplugin_richalert.capture.ToolsFile;
import uni.dcloud.io.uniplugin_richalert.capture.Util;
import uni.dcloud.io.uniplugin_richalert.face.RecordVideoInterface;
import uni.dcloud.io.uniplugin_richalert.face.StaticArguments;

public class FaceCaptureActivity extends Activity implements CameraPreview.OnPreviewFrameListener, View.OnClickListener {
    private static final int PERMISSION_REQUEST_CODE = 10;
    private String tempImagePath;
    private String[] mPermissions = {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};
    private CameraPreview cameraPreview;
    private boolean hasPermissions;
    private boolean resume = false;//解决home键黑屏问题
    private Dialog mSuccessDialog;
    private Dialog mFailDialog;
    private CircleCameraLayout mCircleCameraLayout;
    private ImageView activity_camera_title_back_view;
    private Chronometer chronometer;

    private MediaRecorder mMediaRecorder;
    private TextView tvTips;
    private int mRecordMaxTime = 4;// 一次拍摄最长时间

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//竖屏
        setContentView(R.layout.activity_face_capture);
        mCircleCameraLayout = findViewById(R.id.activity_camera_layout);
        activity_camera_title_back_view = (ImageView)findViewById(R.id.activity_camera_title_back_view);
        this.chronometer = (Chronometer) findViewById(R.id.chronometer);
        this.tvTips = (TextView) findViewById(R.id.tvTips);


        if (Util.checkPermissionAllGranted(this, mPermissions)) {
            hasPermissions = true;
        } else {
            ActivityCompat.requestPermissions(this, mPermissions, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * 设置计时器
     */
    public void initData() {
        this.chronometer.setBase(SystemClock.elapsedRealtime());//计时器清零
        this.chronometer.setFormat("%s"); // 00：00
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPermissions) {
            startCamera();
            resume = true;
        }
    }

    private void startCamera() {
        if (null != cameraPreview) cameraPreview.releaseCamera();
        cameraPreview = new CameraPreview(this, this);
        mCircleCameraLayout.removeAllViews();
        mCircleCameraLayout.setCameraPreview(cameraPreview);
        if (!hasPermissions || resume) {
            mCircleCameraLayout.startView();
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                cameraPreview.startPreview();
            }
        }, 200);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.activity_camera_title_back_view) {
            finish();
        }
        else if(i == R.id.btn_start_record)
        {
            //开始录制
            if (!Environment.getExternalStorageState().equals(
                    android.os.Environment.MEDIA_MOUNTED))
            {
                Toast.makeText(FaceCaptureActivity.this
                        , "SD卡不存在，请插入SD卡！"
                        , Toast.LENGTH_SHORT).show();
                return;
            }
            startRecord();

        }
        else if(i == R.id.btn_start_cancel)
        {
            //取消录制
            cancelRecord();

        }
    }
    private String time;
    private static File outFile;
    private SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");// 用于格式化日
    //文件路径
    private static final String PATH = Environment.getExternalStorageDirectory().getPath() + File.separator + "FaceVideo";
    private static final String FILE_NAME = "record";
    private static final String FILE_NAME_SUFEIX = ".mp4";
    private boolean isReording = false;
    private long sizePicture = 0;
    private int mTimeCount = 0;// 时间计数
    private Timer mTimer;// 计时器


   private void startRecord()
   {
       initData();
       if(cameraPreview!=null){
           cameraPreview.getmCamera().unlock();

       this.mMediaRecorder = new MediaRecorder();
       this.mMediaRecorder.reset();
       this.mMediaRecorder.setCamera( cameraPreview.getmCamera());
       this.mMediaRecorder.setPreviewDisplay(cameraPreview.getSurfaceHolder().getSurface());
       this.mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);// 视频源
       this.mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);// 音频源
       this.mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);// 视频输出格式
       this.mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);// 音频格式
       this.mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);// 视频录制格式
           System.out.println("H:"+cameraPreview.getHigh()* 2 / 3);
           System.out.println("W:"+cameraPreview.getW()* 2 / 3);
       this.mMediaRecorder.setVideoSize(cameraPreview.getHigh()* 2 / 3, cameraPreview.getW()* 2 / 3);// 设置分辨率：
           this.mMediaRecorder.setVideoFrameRate(30);
           List<Camera.Size> supportedPictureSizes = cameraPreview.getParameters().getSupportedPictureSizes();
           for (Camera.Size size : supportedPictureSizes) {
               sizePicture = (size.height * size.width) > sizePicture ? size.height * size.width : sizePicture;
           }
           if (sizePicture< 3000000) {//这里设置可以调整清晰度
               this.mMediaRecorder.setVideoEncodingBitRate(3 * 1024 * 512);
           } else if (sizePicture <= 5000000) {
               this.mMediaRecorder.setVideoEncodingBitRate(2 * 1024 * 512);
           } else {
               this.mMediaRecorder.setVideoEncodingBitRate(1 * 1024 * 512);
           }
       this.mMediaRecorder.setOrientationHint(270);// 输出旋转270度，保持竖屏录制
         //  mMediaRecorder.setMaxDuration(4000);
       this.time = format.format(new Date());
           try {
               this.outFile = new File(Environment
                       .getExternalStorageDirectory()
                       .getCanonicalFile() + "/"+time+".mp4");
               if (!outFile.exists()) {
                   outFile.mkdir();
               }
           } catch (IOException e) {
               e.printStackTrace();
           }
           this.mMediaRecorder.setOutputFile(outFile.getAbsolutePath());
       System.out.println("存储地址:"+outFile.getAbsolutePath());
       try {
           this.mMediaRecorder.prepare();
           this.mMediaRecorder.start();
           Toast.makeText(this,"开始录制视频",Toast.LENGTH_LONG).show();
           System.out.println("---recording---");
           isReording = true;
       } catch (IllegalStateException e) {
           e.printStackTrace();
       } catch (RuntimeException e) {
           e.printStackTrace();
       } catch (Exception e) {
           e.printStackTrace();
       }
       updateUi();
       }
   }

   private void updateUi(){
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
                   } else if (mTimeCount == 1) {//2s
                       // 显示左右摇头
                       mHandler.sendEmptyMessage(StaticArguments.SHOW_SHAKEHEAD);
                   } else if (mTimeCount == 2) { //4s
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


    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            try {
                switch (msg.what) {
                    case StaticArguments.START_CHRONOMETER: //开启计时器
                        // 开始启动计时器
                        chronometer.start();
                        break;

                    case StaticArguments.RECORD_COMPLETE: //录制完成
                        tvTips.setText("录制完成");
                        cancelRecord();
                        chronometer.stop();
                        initData();
                        System.out.println("录制完成path="+outFile.getAbsolutePath());
                        Intent intent = new Intent();
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.setAction("com.scott.sayhi");
                        intent.putExtra("result",outFile.getAbsolutePath());
                        FaceCaptureActivity.this.sendBroadcast(intent);
                        break;
                    case StaticArguments.SHOW_SHAKEHEAD: //显示左右摇头
                        tvTips.setText("左右摇头");
                        break;
                    case StaticArguments.SHOW_NODHEAD: //显示点头
                        tvTips.setText("点头");
                        break;
                    case StaticArguments.UPLOAD_SUCCESS://上传成功

                        break;
                    case StaticArguments.UPLOAD_FAIL://上传失败

                        break;
                    default:
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    };

   private void cancelRecord()
   {
       if (mTimer != null) {
           mTimer.cancel();
           mTimer = null;
       }
           if(mMediaRecorder !=null)
           {
           // 停止录
               this.mMediaRecorder.setOnErrorListener(null);
               this.mMediaRecorder.setOnErrorListener(null);
               this.mMediaRecorder.setOnInfoListener(null);
               this.mMediaRecorder.setPreviewDisplay(null);
               try {
                  mMediaRecorder.stop();
               } catch (IllegalStateException e) {
                   e.printStackTrace();
                   mMediaRecorder = null;
                   mMediaRecorder = new MediaRecorder();
               } catch (RuntimeException e) {
                   e.printStackTrace();
               } catch (Exception e) {
                   e.printStackTrace();
               }
           // 释放资源
           mMediaRecorder.release();
           mMediaRecorder = null;
           Toast.makeText(this,"停止录制视频",Toast.LENGTH_LONG).show();
               isReording = false;
           }
   }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != cameraPreview) {
            cameraPreview.releaseCamera();
        }
        mCircleCameraLayout.release();
    }

    /**
     * 申请权限结果返回处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean isAllGranted = true;
            for (int grant : grantResults) {  // 判断是否所有的权限都已经授予了
                if (grant != PackageManager.PERMISSION_GRANTED) {
                    isAllGranted = false;
                    break;
                }
            }
            if (isAllGranted) { // 所有的权限都授予了
                startCamera();
            } else {// 提示需要权限的原因
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("拍照需要允许权限, 是否再次开启?")
                        .setTitle("提示")
                        .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(FaceCaptureActivity.this, mPermissions, PERMISSION_REQUEST_CODE);
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                finish();
                            }
                        });
                builder.create().show();
            }
        }
    }

    @Override
    public void onPreviewFrame(Bitmap bitmap) {
        Log.d("onPreviewFrame", "bitmap:" + bitmap);
        File tempImageFile = null;
        Bitmap faceBitmap = null;
        try {
            tempImageFile = ToolsFile.createTempImageFile(this);
            tempImagePath = tempImageFile.getPath();
            Log.d("tempImagePath", "tempImagePath:" + tempImagePath);
            faceBitmap = FaceHelper.genFaceBitmap(bitmap);
            saveBitmap(faceBitmap);
            compressPic();
            Log.d("FaceHelper", "bitmap1:" + faceBitmap + ",Width：" + (faceBitmap == null ? "0" : faceBitmap.getWidth()));
        } catch (Exception e) {
            faceBitmap = null;
        }
        //如果截取的图片宽小于350，高小于400则重新获取。
        if (faceBitmap == null || faceBitmap.getHeight() < 400) {
            return;
        }
      /*  if (null != cameraPreview) {
            cameraPreview.releaseCamera();
        }
        mCircleCameraLayout.release();*/
        //showAuthenticationSuccessDialog(faceBitmap);
        Toast.makeText(this,"检测到人脸",Toast.LENGTH_LONG).show();
    }

    private void compressPic() {
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inSampleSize = 1; // 这个数字越大,图片就越小.图片就越不清晰
        Bitmap pic = null;
        pic = BitmapFactory.decodeFile(tempImagePath, op);  //先从本地读照片，然后利用op参数对图片进行处理
        FileOutputStream b = null;
        try {
            b = new FileOutputStream(tempImagePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        if (pic != null) {
            pic.compress(Bitmap.CompressFormat.JPEG, 50, b);
        }
    }

    public void saveBitmap(Bitmap bm) {
        File f = new File(tempImagePath);
        if (f.exists()) {
            f.delete();
        }
        try {
            FileOutputStream out = new FileOutputStream(f);
            bm.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();
            Log.d("OnFaceCollected", "保存成功：" + tempImagePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

/*    private void showAuthenticationSuccessDialog(final Bitmap faceBitmap) {
        View view = LayoutInflater.from(FaceCaptureActivity.this).inflate(R.layout.dialog_authentication_success, null);
        TextView authenticationView = view.findViewById(R.id.dialog_know_tv);
        mSuccessDialog = new Dialog(FaceCaptureActivity.this, R.style.custom_noActionbar_window_style);
        mSuccessDialog.show();
        mSuccessDialog.setContentView(view);
        mSuccessDialog.setCanceledOnTouchOutside(false);
        mSuccessDialog.setCancelable(false);
        Window win = mSuccessDialog.getWindow();
        WindowManager.LayoutParams lp = win.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        win.setAttributes(lp);
        authenticationView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle b = new Bundle();
                b.putParcelable("bitmap", faceBitmap);
                Intent intent = getIntent();
                intent.putExtras(b);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }*/
}
