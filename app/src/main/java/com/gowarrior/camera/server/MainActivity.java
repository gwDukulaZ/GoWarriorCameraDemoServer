/*
 * Copyright 2010-2014 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.gowarrior.camera.server;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.gowarrior.GPIO;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.gowarrior.camera.server.models.TransferModel;
import com.gowarrior.camera.server.utils.CloudTool;
import com.gowarrior.cloudq.CWSBucket.ICWSBucketAidlInterface;
import com.gowarrior.cloudq.CWSPipe.CWSPipeCallback;
import com.gowarrior.cloudq.CWSPipe.CWSPipeClient;
import com.gowarrior.cloudq.CWSPipe.CWSPipeMessage;

import org.eclipse.paho.client.mqttv3.ICWSDeliveryToken;
import org.eclipse.paho.client.mqttv3.ICWSPipeToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/* 
 * Activity where the user can see the history of transfers, go to the downloads
 * page, or upload images/videos.
 *
 * The reason we separate image and videos is for compatibility with Android versions
 * that don't support multiple MIME types. We only allow videos and images because
 * they are nice for demonstration
 */
public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2,
        SurfaceHolder.Callback {
    private static final String TAG = "GoWarriorCameraServer";
    private static final int REFRESH_DELAY = 500;
    private static final int AUTOUPLOAD_DELAY = 500;
    private static final int AUTOSNAPSHOT_DELAY = 60000;
    private static final int PIPECHECK_DELAY = 3000;

    private Timer mTimer;
    private LinearLayout mLayout;
    private TransferModel[] mModels = new TransferModel[0];

    private MyPreview myCameraPreview = null;

    private boolean bdeleteobj = false;
    private int objnum = 0;
    private int totalnum = 30;
    private int lastdeleteidx = 0;
    private boolean deletetaskrun = false;
    private int delnumatime = 0;
    private int uploadnumsincelastsync = 0;

    private static final String WEB_CAMERA_PROFILE = "web_camera_profile";
    private static final String AUTO_RUN_KEY = "auto_run";
    private static final String AUTO_SNAPSHOT_KEY = "auto_snapshot";
    private static final String FACE_DETECT_KEY = "face_detect";
    private static final String PHOTO_UPLOAD_KEY = "photo_upload";
    private static final int mSensorGPIO = 66;
    private static final int mUserKey1 = 15;
    private static final int mUserKey2 = 28;
    private static final int GPIO_HIGH = 1;
    private static final int GPIO_LOW = 0;
    private int mSensorLastState = GPIO_LOW;
    private int mUserKey1State = GPIO_LOW;
    private int mUserKey2State = GPIO_LOW;
    private SharedPreferences mSP;
    private SharedPreferences.Editor mSPEditor;
    private boolean mAutoRun = false;
    private boolean mAutoSnapShot = false;
    private boolean mFaceDetect = false;
    private boolean mPhotoUpload = false;
    private Handler mSnapshotHandle = null;
    private boolean allReady = false;
    private CWSPipeClient mCWSPipeClient;
    private boolean mUserKey1Run = false;
    private boolean mUserKey2Run = false;

    private Mat mRgba;
    private Mat mGray;
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final int JAVA_DETECTOR = 0;
    public static final int NATIVE_DETECTOR = 1;
    private float mRelativeFaceSize = 0.2f;
    private int mAbsoluteFaceSize = 0;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetector;
    private DetectionBasedTracker mNativeDetector;
    private int mDetectorType = NATIVE_DETECTOR;
    private float mScale = 0;
    private int mLastPersonDetected = 0;
    private Bitmap mCacheBitmap;
    private SurfaceView mSurfaceView = null;
    private SurfaceHolder mSurfaceHolder = null;
    private PeripheralModel pwmDev = null;
    private boolean connected = false;
    private Date lastConnectTime;


    public static CloudTool cloudTool;
    private ICWSBucketAidlInterface myBucket;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mSP = getSharedPreferences(WEB_CAMERA_PROFILE, Activity.MODE_PRIVATE);
        mAutoRun = mSP.getBoolean(AUTO_RUN_KEY, false);
        mAutoSnapShot = mSP.getBoolean(AUTO_SNAPSHOT_KEY, false);
        mFaceDetect = mSP.getBoolean(FACE_DETECT_KEY, false);
        mPhotoUpload = mSP.getBoolean(PHOTO_UPLOAD_KEY, false);

        Intent intent = getIntent();
        boolean mBroadcast = intent.getBooleanExtra("ByBroadcast", false);
        Log.i(TAG, "ByBroadcast:" + mBroadcast + ", mAutoRun:" + mAutoRun + "!\n");
        if (mBroadcast && (!mAutoRun)) {
            System.exit(0);
        }

        setContentView(com.gowarrior.camera.server.R.layout.activity_main);

        cloudServiceBind();

        pwmDev = new PeripheralModel();
        mLayout = (LinearLayout) findViewById(com.gowarrior.camera.server.R.id.transfers_layout);

        /* init camera preview */
        Log.v(TAG, "myCameraPreview new ...");
        myCameraPreview = (MyPreview) findViewById(com.gowarrior.camera.server.R.id.camera_preview);
        Log.v(TAG, "myCameraPreview new success");
        checkDir(Constants.UPLOAD_FROM);
        checkDir(Constants.UPLOAD_FROM + "/new");
        checkDir(Constants.UPLOAD_FROM + "/upload");
        checkDir(Constants.DOWNLOAD_TO);

        Button mRefreshButton = (Button) findViewById(R.id.setautorun);
        if (mAutoRun) {
            mRefreshButton.setText(R.string.disablesetautorun);
        } else {
            mRefreshButton.setText(R.string.enablesetautorun);
        }
        mRefreshButton = (Button) findViewById(R.id.setautosnap);
        if (mAutoSnapShot) {
            mRefreshButton.setText(R.string.disablesetautosnap);
        } else {
            mRefreshButton.setText(R.string.enablesetautosnap);
        }
        mRefreshButton = (Button) findViewById(R.id.setfacedetect);
        if (mFaceDetect) {
            mRefreshButton.setText(R.string.disablefacedetect);
        } else {
            mRefreshButton.setText(R.string.enablefacedetect);
        }
        mRefreshButton = (Button) findViewById(R.id.setphotoupload);
        if (mPhotoUpload) {
            mRefreshButton.setText(R.string.disablephotoupload);
        } else {
            mRefreshButton.setText(R.string.enablephotoupload);
        }
        myCameraPreview.setPhotoUpload(mPhotoUpload);

        findViewById(R.id.autodownload).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                findViewById(R.id.autodownload).setEnabled(false);
                new AutoDownload().execute();
            }
        });

        findViewById(R.id.snapshot).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                /* to take snapshot automatically */
                if (mSnapshotHandle != null) {
                    Date curT = new Date();
                    Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_Active, 10000, 0, curT);
                    mSnapshotHandle.sendMessage(msg);
                    Log.v(TAG, "send msg to take Active snapshot at " + curT.toString());
                }
            }
        });

        findViewById(R.id.setautorun).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Log.v(TAG, "current mode is AUTO RUN " + (mAutoRun ? "ENABLE" : "DISABLE") + "!\n");
                Log.v(TAG, "MODE will be changed, AUTO RUN from " + (mAutoRun ? "ENABLE" : "DISABLE")
                        + " to " + (!mAutoRun ? "ENABLE" : "DISABLE") + "!\n");
                if (mAutoRun) {
                    mAutoRun = false;
                    ((Button) findViewById(R.id.setautorun)).setText(R.string.enablesetautorun);
                } else {
                    mAutoRun = true;
                    ((Button) findViewById(R.id.setautorun)).setText(R.string.disablesetautorun);
                }
                mSPEditor = mSP.edit();
                mSPEditor.putBoolean(AUTO_RUN_KEY, mAutoRun);
                mSPEditor.commit();
            }
        });

        findViewById(R.id.setautosnap).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "current mode is AUTO SNAPSHOT " + (mAutoSnapShot ? "ENABLE" : "DISABLE") + "!\n");
                Log.v(TAG, "MODE will be changed, AUTO SNAPSHOT from " + (mAutoSnapShot ? "ENABLE" : "DISABLE")
                        + " to " + (!mAutoSnapShot ? "ENABLE" : "DISABLE") + "!\n");
                if (mAutoSnapShot) {
                    mAutoSnapShot = false;
                    ((Button) findViewById(R.id.setautosnap)).setText(R.string.enablesetautosnap);
                } else {
                    mAutoSnapShot = true;
                    ((Button) findViewById(R.id.setautosnap)).setText(R.string.disablesetautosnap);
                }
                mSPEditor = mSP.edit();
                mSPEditor.putBoolean(AUTO_SNAPSHOT_KEY, mAutoSnapShot);
                mSPEditor.commit();
            }
        });

        findViewById(R.id.setfacedetect).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "current mode is FACE DETECT " + (mFaceDetect ? "ENABLE" : "DISABLE") + "!\n");
                Log.v(TAG, "MODE will be changed, FACE DETECT from " + (mFaceDetect ? "ENABLE" : "DISABLE")
                        + " to " + (!mFaceDetect ? "ENABLE" : "DISABLE") + "!\n");
                if (mFaceDetect) {
                    mFaceDetect = false;
                    ((Button) findViewById(R.id.setfacedetect)).setText(R.string.enablefacedetect);
                    mSurfaceView.setVisibility(View.INVISIBLE);
                    mSurfaceView.setZOrderOnTop(false);
                    myCameraPreview.changeCameraDisplayMode("display");
                } else {
                    mFaceDetect = true;
                    ((Button) findViewById(R.id.setfacedetect)).setText(R.string.disablefacedetect);
                    mSurfaceView.setVisibility(View.VISIBLE);
                    mSurfaceView.setZOrderOnTop(true);
                    myCameraPreview.changeCameraDisplayMode("texture");
                }
                mSPEditor = mSP.edit();
                mSPEditor.putBoolean(FACE_DETECT_KEY, mFaceDetect);
                mSPEditor.commit();
            }
        });

        findViewById(R.id.setphotoupload).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "current mode is PHOTO UPLOAD " + (mPhotoUpload ? "ENABLE" : "DISABLE") + "!\n");
                Log.v(TAG, "MODE will be changed, PHOTO UPLOAD from " + (mPhotoUpload ? "ENABLE" : "DISABLE")
                        + " to " + (!mPhotoUpload ? "ENABLE" : "DISABLE") + "!\n");
                if (mPhotoUpload) {
                    mPhotoUpload = false;
                    ((Button) findViewById(R.id.setphotoupload)).setText(R.string.enablephotoupload);
                } else {
                    mPhotoUpload = true;
                    ((Button) findViewById(R.id.setphotoupload)).setText(R.string.disablephotoupload);
                }

                myCameraPreview.setPhotoUpload(mPhotoUpload);
                mSPEditor = mSP.edit();
                mSPEditor.putBoolean(PHOTO_UPLOAD_KEY, mPhotoUpload);
                mSPEditor.commit();
            }
        });

        findViewById(R.id.downloadphoneapp).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v(TAG, "show QR code for phone app download");
                Intent intent = new Intent(MainActivity.this, QCCode.class);
                startActivity(intent);
            }
        });
        ((Button) findViewById(R.id.downloadphoneapp)).setText("Download Phone APP");


        // make timer that will refresh all the transfer views
        mTimer = new Timer();

        //TODO
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        syncModels();
                        for (int i = 0; i < mLayout.getChildCount(); i++) {
                            ((TransferView) mLayout.getChildAt(i)).refresh();
                            ((TransferView) mLayout.getChildAt(i)).setHandle(mSnapshotHandle);
                        }
                    }
                });
            }
        };
        mTimer.schedule(task, 0, REFRESH_DELAY);

        TimerTask mIRSensorTask = new TimerTask() {
            @Override
            public void run() {
                //check IR sensor GPIO
                int mCurrentState = GPIO_LOW;
                GPIO gpio = new GPIO();
                gpio.setmode(GPIO.BCM);

                gpio.setup(mSensorGPIO, GPIO.INPUT);
                mCurrentState = gpio.input(mSensorGPIO);
                if (GPIO_HIGH == mCurrentState && mSensorLastState != mCurrentState) {
                    Log.i(TAG, "PIR detected");
                    if (null != myCameraPreview) {
                        if (mSnapshotHandle != null) {
                            Date curT = new Date();
                            Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_PIR, 500, 0, curT);
                            mSnapshotHandle.sendMessage(msg);
                            Log.v(TAG, "send msg to take PIR snapshot at " + curT.toString());
                        }
                    }
                    mSensorLastState = mCurrentState;
                } else if (GPIO_LOW == mCurrentState && mSensorLastState != mCurrentState) {
                    mSensorLastState = mCurrentState;
                }

                gpio.setup(mUserKey1, GPIO.INPUT);
                mCurrentState = gpio.input(mUserKey1);
                if (mUserKey1State != mCurrentState) {
                    if (GPIO_LOW == mCurrentState) {
                        Log.i(TAG, "UserKey1 detected !!!");
                        if (mSnapshotHandle != null) {
                            Date curT = new Date();
                            Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_UserKey1, 0, 0, curT);
                            mSnapshotHandle.sendMessage(msg);
                            Log.v(TAG, "send msg to do remote control Alarm at " + curT.toString());
                        }
                    }
                    mUserKey1State = mCurrentState;
                }

                gpio.setup(mUserKey2, GPIO.INPUT);
                mCurrentState = gpio.input(mUserKey2);
                if (mUserKey2State != mCurrentState) {
                    if (GPIO_LOW == mCurrentState) {
                        Log.i(TAG, "UserKey2 detected !!!");
                        if (mSnapshotHandle != null) {
                            Date curT = new Date();
                            Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_UserKey2, 0, 0, curT);
                            mSnapshotHandle.sendMessage(msg);
                            Log.v(TAG, "send msg to do remote control Alarm at " + curT.toString());
                        }
                    }
                    mUserKey2State = mCurrentState;
                }
            }
        };
        mTimer.schedule(mIRSensorTask, 0, REFRESH_DELAY / 2);

        TimerTask mAutoSnapShotTask = new TimerTask() {
            @Override
            public void run() {
                //Log.v(TAG,"mAutoSnapShotTask run, mAutoSnapShot=" + mAutoSnapShot);
                if (null != myCameraPreview && mAutoSnapShot == true) {
                    if (mSnapshotHandle != null) {
                        Date curT = new Date();
                        Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_Auto, AUTOSNAPSHOT_DELAY, 0, curT);
                        mSnapshotHandle.sendMessage(msg);
                        Log.v(TAG, "send msg to take Auto snapshot at " + curT.toString());
                    }
                }
            }
        };
        mTimer.schedule(mAutoSnapShotTask, 0, AUTOSNAPSHOT_DELAY);

        new TakeSnapshot().start();

        mSurfaceView = (SurfaceView) findViewById(R.id.face_preview);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setFormat(PixelFormat.TRANSPARENT);
        mSurfaceView.setZOrderOnTop(true);
        mCacheBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        myCameraPreview.setCvCameraViewListener(this);

        //TODO
        new Thread(new Runnable() {
            @Override
            public void run() {
                createCWSPipe();
            }
        }).start();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("detection_based_tracker");

                    try {
                        // load cascade file from application resources
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetector = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(), 0);

                        cascadeDir.delete();

                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    //mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    private void UserKey1Action() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int degree = -90; //-90,Left turn; 0, Middle; 90, Right turn
                mUserKey1Run = true;

                Log.d(TAG, " from -50 to 90");

                pwmDev.directStart();
                for( degree = -50; degree <= 90; degree+=10 ){
                    pwmDev.directControl(degree);

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                pwmDev.directStop();
                mUserKey1Run = false;

//                int degree = 90; //-90,Left turn; 0, Middle; 90, Right turn
//                for( int i = 0; i < 10; i++ ){
//                    pwmDev.directStart();
//                    pwmDev.directControl(degree);
//
//                    try {
//                        Thread.sleep(10);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    pwmDev.directStop();
//
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//
//                degree = -90; //-90,Left turn; 0, Middle; 90, Right turn
//                for( int i = 0; i < 10; i++ ){
//                    pwmDev.directStart();
//                    pwmDev.directControl(degree);
//
//                    try {
//                        Thread.sleep(10);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                    pwmDev.directStop();
//
//                    try {
//                        Thread.sleep(500);
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
            }
        }).start();
    }

    private void UserKey2Action() { // in middle, open door
        new Thread(new Runnable() {
            @Override
            public void run() {
                int degree = 90; //-90,Left turn; 0, Middle; 90, Right turn
                mUserKey2Run = true;

                Log.d(TAG, " from 90 to -40");

                pwmDev.directStart();
                for( degree = 90; degree >= -40; degree-=10 ){
                    pwmDev.directControl(degree);

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                pwmDev.directStop();
                mUserKey2Run = false;
            }
        }).start();
    }

    private void testPWM() { // open door then close it
        new Thread(new Runnable() {
            @Override
            public void run() {
                int degree = 90; //-90,Left turn; 0, Middle; 90, Right turn

                pwmDev.directStart();
                for( degree = 90; degree >= -40; degree-=10 ){
                    pwmDev.directControl(degree);

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                pwmDev.directStop();

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                pwmDev.directStart();
                for( degree = -50; degree <= 90; degree+=10 ){
                    pwmDev.directControl(degree);

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                pwmDev.directStop();

                replyRemoter("alarm done");
            }
        }).start();

    }

    private void testLed() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int flag = 0;
                int timeout = 0;
                while (timeout <= 5000) {
                    // LED flash
                    if (0 == flag % 2)
                        pwmDev.ledOn();
                    else
                        pwmDev.ledOff();

                    flag++;

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    timeout += 200;
                }
            }
        }).start();
    }

    private void createCWSPipe() {
        ICWSPipeToken token;
        //TODO
//        Log.v(TAG, "Pipe Client, server=" + Constants.PIPE_SERVER + " id=" + Constants.Prefix + PIPE_ID);
        mCWSPipeClient = new CWSPipeClient(this);

        CWSPipeCallback cb = new CWSPipeCallback() {

            @Override
            public void pipeConnectionLost(Throwable throwable) {
                Log.d(TAG, "connectionLost");
                if (mSnapshotHandle != null) {
                    Date curT = new Date();
                    Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_RemoteConnect, 0, 0, curT);
                    mSnapshotHandle.sendMessage(msg);
                    Log.v(TAG, "send msg to do remote connect at " + curT.toString());
                }
            }

            @Override
            public void pipeMessageArrived(String s, byte[] payload) {
                String msgR = new String(payload);
                Log.d(TAG, "messageArrived:" + s + " payload:" + msgR);
                if (msgR.equals("photo")) {
                    if (mSnapshotHandle != null) {
                        Date curT = new Date();
                        Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_Remote, 1000, 0, curT);
                        mSnapshotHandle.sendMessage(msg);
                        Log.v(TAG, "send msg to take Remote snapshot at " + curT.toString());
                    }
                } else if (msgR.equals("alarm")) {
                    if (mSnapshotHandle != null) {
                        Date curT = new Date();
                        Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_RemoteControl, 0, 0, curT);
                        mSnapshotHandle.sendMessage(msg);
                        Log.v(TAG, "send msg to do remote control Alarm at " + curT.toString());
                    }
                } else {
                    Log.v(TAG, "ERROR: can't deal with this remote msg: " + msgR);
                }
            }

            @Override
            public void pipeDeliveryComplete(ICWSDeliveryToken token) {
                Log.d(TAG, "deliveryComplete, token=" + token.toString());
            }
        };

        mCWSPipeClient.setCallback(cb);

        while (mSnapshotHandle == null) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (mSnapshotHandle != null) {
            myCameraPreview.setHandle(mSnapshotHandle);
            Date curT = new Date();
            Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_RemoteConnect, 0, 0, curT);
            mSnapshotHandle.sendMessage(msg);
            Log.v(TAG, "send msg to do remote connect at " + curT.toString());
        }
    }

    private void subPipeTopic() {
        mCWSPipeClient.subscribe();
    }

    private void pubPipeTopic(String payload) {
        CWSPipeMessage msg = new CWSPipeMessage(payload.getBytes());
        msg.setQos(2);
        mCWSPipeClient.publish(msg);
    }

    private void checkDir(String path) {
        File Dir = new File(path);
        if (!Dir.exists()) {
            boolean ret = false;
            ret = Dir.mkdirs();
            Log.v(TAG, "mkdir " + path + " ret=" + ret);
        }
    }

    /*
     * When we get a Uri back from the gallery, upload the associated
     * image/video
     */
    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        if (resCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                Log.v(TAG, "upload file " + uri.toString());
                cloudTool.uploadFile(uri);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
        myCameraPreview.disableView();
        Log.v(TAG, "do remote server close");
        mCWSPipeClient.close();
        //TODO
        cloudTool.cloudServiceFinish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* open camera */
        if (myCameraPreview != null)
            myCameraPreview.enableView();

        syncModels();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        /* clear camera */
        if (myCameraPreview != null)
            myCameraPreview.disableView();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mTimer.purge();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
//        mCacheBitmap = Bitmap.createBitmap(myCameraPreview.getmCameraFrameWidth(),
//                myCameraPreview.getmCameraFrameHeight(), Bitmap.Config.ARGB_8888);
        if (mFaceDetect) {
            myCameraPreview.changeCameraDisplayMode("texture");
        }
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }

        MatOfRect faces = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null)
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2, // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        } else if (mDetectorType == NATIVE_DETECTOR) {
            if (mNativeDetector != null)
                mNativeDetector.detect(mGray, faces);
        } else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] facesArray = faces.toArray();
        Log.i(TAG, "facesArray.length" + facesArray.length);
        for (int i = 0; i < facesArray.length; i++)
            Imgproc.rectangle(mRgba, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);

        boolean bmpValid = true;
        if (mRgba != null) {
            try {
                Utils.matToBitmap(mRgba, mCacheBitmap);
            } catch (Exception e) {
                Log.e(TAG, "Mat type: " + mRgba);
                Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
                Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
                bmpValid = false;
            }
        }

        if (bmpValid && mCacheBitmap != null) {
            Canvas canvas = mSurfaceHolder.lockCanvas();
            if (canvas != null) {
                if (mScale != myCameraPreview.getFrameScale())
                    mScale = myCameraPreview.getFrameScale();
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                Log.d(TAG, "mStretch value: " + mScale);

                if (mScale != 0) {
                    canvas.drawBitmap(mCacheBitmap, new android.graphics.Rect(0, 0, mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
                            new android.graphics.Rect((int) ((canvas.getWidth() - mScale * mCacheBitmap.getWidth()) / 2),
                                    (int) ((canvas.getHeight() - mScale * mCacheBitmap.getHeight()) / 2),
                                    (int) ((canvas.getWidth() - mScale * mCacheBitmap.getWidth()) / 2 + mScale * mCacheBitmap.getWidth()),
                                    (int) ((canvas.getHeight() - mScale * mCacheBitmap.getHeight()) / 2 + mScale * mCacheBitmap.getHeight())), null);
                } else {
                    canvas.drawBitmap(mCacheBitmap, new android.graphics.Rect(0, 0, mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
                            new android.graphics.Rect((canvas.getWidth() - mCacheBitmap.getWidth()) / 2,
                                    (canvas.getHeight() - mCacheBitmap.getHeight()) / 2,
                                    (canvas.getWidth() - mCacheBitmap.getWidth()) / 2 + mCacheBitmap.getWidth(),
                                    (canvas.getHeight() - mCacheBitmap.getHeight()) / 2 + mCacheBitmap.getHeight()), null);
                }
                mSurfaceHolder.unlockCanvasAndPost(canvas);
            }
        }

        if (0 != facesArray.length) {
            if (mLastPersonDetected != facesArray.length) {
                mLastPersonDetected = facesArray.length;
                if (mSnapshotHandle != null) {
                    Date curT = new Date();
                    Message msg = mSnapshotHandle.obtainMessage(Constants.Snapshot_FaceDetect, 500, 0, curT);
                    mSnapshotHandle.sendMessage(msg);
                    Log.v(TAG, "send msg to take FACE snapshot at " + curT.toString());
                }
            }
        } else {
            mLastPersonDetected = 0;
        }

        return mRgba;
    }

    /* makes sure that we are up to date on the transfers */
    private void syncModels() {
        TransferModel[] models = TransferModel.getAllTransfers();
        if ((models != null) && (mModels.length != models.length)) {
            if (models.length > 20) {
                mLayout.removeViewsInLayout(mLayout.getChildCount() - (models.length - mModels.length),
                        models.length - mModels.length);
            }
            // add the transfers we haven't seen yet
            for (int i = mModels.length; i < models.length; i++) {
                mLayout.addView(new TransferView(this, TransferModel.getTransferModel(i + 1)), 0);
            }
            mModels = models;
        }
    }

    class TakeSnapshot extends Thread {
        private Message lastsnapshotmsg = null;

        @Override
        public void run() {
            super.run();
            Looper.prepare();

            mSnapshotHandle = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    boolean doit = false;

                    //testPWM();
                    //testLed();

                    super.handleMessage(msg);
                    switch (msg.what) {
                        case Constants.Snapshot_Remote:
                            // do reply to remoter before take snapshot
                            replyRemoter("snapshot in process");
                        case Constants.Snapshot_Auto:
                        case Constants.Snapshot_Active:
                        case Constants.Snapshot_PIR:
                        case Constants.Snapshot_FaceDetect:
                            if (!allReady) {
                                Toast.makeText(getApplicationContext(), "Cloud not ready for snapshot", Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }
                            if (lastsnapshotmsg == null) {
                                lastsnapshotmsg = new Message();
                                lastsnapshotmsg.copyFrom(msg);
                                doit = true;
                            }
                            Date msgT = (Date) msg.obj;
                            String snapshottype = (msg.what == Constants.Snapshot_Auto) ? "Auto" :
                                    ((msg.what == Constants.Snapshot_Active) ? "Active" :
                                            ((msg.what == Constants.Snapshot_PIR) ? "PIR" :
                                                    ((msg.what == Constants.Snapshot_FaceDetect) ? "FaceDetect" :
                                                            ((msg.what == Constants.Snapshot_Remote) ? "Remote" : "Unknown"))));
                            Log.d(TAG, "TakeSnapshot recv msg at " + msgT.toString() + " Type=" + snapshottype);
                            Date msgL = (Date) lastsnapshotmsg.obj;
                            long passedT = msgT.getTime() - msgL.getTime() + 1000;
                            if (passedT >= msg.arg1) {
                                doit = true;
                            }
                            Log.v(TAG, "last snapshot passed " + passedT + " The threshold is " + msg.arg1 + " -> doit=" + doit);

                            if (doit) {
                                lastsnapshotmsg.copyFrom(msg);
                                lastsnapshotmsg.obj = new Date();
                                Log.v(TAG, "now to take " + snapshottype + " snapshot");
                                myCameraPreview.setCurrentSnapshotMode(snapshottype);
                                myCameraPreview.doTakePicture();
                            }
                            doit = false;
                            break;
                        case Constants.Snapshot_UploadStart:
                            String fpath = (String) msg.obj;
                            Log.v(TAG, "file " + fpath + " upload start");
                            if (fpath.indexOf("Remote") != -1) {
                                replyRemoter("snapshot uploading");
                            }
                            break;
                        case Constants.Snapshot_UploadDone:
                            fpath = (String) msg.obj;
                            if (fpath.contains("Remote")) {
                                replyRemoter("snapshot done-" + Util.getFileName(fpath));
                            }
                            File fp = new File(fpath); // file:///storage/emu
                            fp.delete();
                            delnumatime++;
                            uploadnumsincelastsync++;
                            Log.v(TAG, "file " + fpath + " upload complete, Now to delete the local file, delnumatime=" + delnumatime
                                    + " uploadnumsincelastsync=" + uploadnumsincelastsync);
                            if (!deletetaskrun) {
                                new DeleteObject().execute();
                            }
                            break;
                        case Constants.Snapshot_RemoteControl:
                            replyRemoter("alarm in process");
                            testPWM();
                            testLed();
                            break;
                        case Constants.Snapshot_UserKey1:
                            if (!mUserKey1Run) {
                                UserKey1Action();
                            }
                            break;
                        case Constants.Snapshot_UserKey2:
                            if (!mUserKey2Run) {
                                UserKey2Action();
                            }
                            break;
                        case Constants.Snapshot_RemoteConnect:
                            Log.v(TAG, "do remote server connect");
                            ICWSPipeToken token;
                            long connectduration = 0;
                            if (connected) {
                                Date cur = new Date();
                                connectduration = cur.getTime() - lastConnectTime.getTime();
                                /*
                                if (connectduration <= 3000) {
                                    // connect lost within 1s, just ignore it??
                                    Log.v(TAG,"ERROR ------------------ now ignore connect lost within 3s, what will happen?");
                                    break;
                                }*/
                                Log.v(TAG, "server mqtt connection lost, duration=" + connectduration);
                            }
                            token = mCWSPipeClient.connect();
                            try {
                                token.waitForCompletion();
                                if (!connected) {
                                    connected = true;
                                }
                                lastConnectTime = new Date();
                            } catch (MqttException e) {
                                e.printStackTrace();
                            }
                            //TODO
                            subPipeTopic();
                            break;
                        default:
                            break;
                    }
                }
            };

            Looper.loop();
        }
    }


    //TODO
    private class DeleteObject extends AsyncTask<Object, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            deletetaskrun = true;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            String object;
            int num = 0;

            //check and delete old snapshots
            List<String> list = cloudTool.getCloudFileList();
            num = list.size();
            Log.v(TAG,"now " + num + " snapshots,number limit "+ totalnum);
            if (num > totalnum) {
                num -= totalnum;
                Log.v(TAG,"need delete " + num + " files since number limit "+ totalnum);
                for (int i = 0; i < list.size(); i++) {
                    object = list.get(i);
                    if (object.endsWith("-snap.jpg")) {
                        Log.v(TAG, "now delete "+ object);
                        cloudTool.deleteFile(object);
                        num--;
                        if (num <= 0) {
                            break;
                        }
                    }
                }
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            deletetaskrun = false;
        }
    }

    public int downloadFile(String path) {
        int downloadsize = 0;

        int id;
        String mpath;
        String object;
        //path
        if (null == path)
            //default download path
            mpath = Util.getDownloadPath();
        else
            mpath = path;

        //file
        List<String> list = cloudTool.getCloudFileList();

        for (int i = 0; i < list.size(); i++) {
            object = list.get(i);
            Log.v(TAG,"download file check: "+object);
            File tmpfile = new File(mpath, object);
            if (tmpfile.exists()) {
                if (tmpfile.length() == cloudTool.getFileSize(object)) {
                    continue;
                } else {
                    id = cloudTool.downloadFile(object, mpath);
                    if (id > -1) {
                        myCameraPreview.download(Uri.fromFile(tmpfile));
                        downloadsize++;
                    }
                }

            } else {
                id = cloudTool.downloadFile(object, mpath);
                if (id > -1) {
                    myCameraPreview.download(Uri.fromFile(tmpfile));
                    downloadsize++;
                }
            }
        }

        return downloadsize;
    }

    private class AutoDownload extends AsyncTask<Object, Void, Integer> {

        @Override
        protected Integer doInBackground(Object... params) {
            //TODO
            return downloadFile(Constants.DOWNLOAD_TO);
//            return  cloudTool.downloadFile(null);
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == 0) {
                Log.v(TAG, "hint No new files need download");
                Toast.makeText(getApplicationContext(), "No new files need download", Toast.LENGTH_SHORT)
                        .show();
            } else {
                String hint = String.valueOf(result) + " files downloaded";
                Log.v(TAG, "hint " + hint);
                Toast.makeText(getApplicationContext(), hint, Toast.LENGTH_SHORT)
                        .show();
            }
            findViewById(R.id.autodownload).setEnabled(true);
        }
    }

    private void replyRemoter(String msg) {
        Log.v(TAG, "reply to remoter: " + msg);
        //TODO
        pubPipeTopic(msg);
    }

    private ServiceConnection bucketSC = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            myBucket = ICWSBucketAidlInterface.Stub.asInterface(service);
            //TODO
            cloudTool = new CloudTool();
            cloudTool.setCloudService(myBucket);

            int i =cloudTool.cloudServiceInit();
            cloudTool.setListener(TransferModel.mCloudToolListener);
            Log.d(TAG,"the value of i is "+ i);
            if (i > 0){
                allReady = true;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

            cloudTool.cloudServiceFinish();
            Log.d(TAG, "onServiceDisconnected");
        }
    };

    private void cloudServiceBind(){
        Intent mIntent = new Intent();
        mIntent.setAction(ICWSBucketAidlInterface.class.getName());
        Intent serverIntent = getExplicitIntent(getApplicationContext(), mIntent);
        if(null != serverIntent) {
            Intent intent = new Intent(serverIntent);
            intent.setPackage(getPackageName());
            Log.d(TAG, "bindService");
            bindService(intent, bucketSC, Context.BIND_AUTO_CREATE);
        }else{
            Log.e(TAG, "[bind] Not find cloud service!!");
        }
    }

    private void cloudServiceUnbind(){
        Intent mIntent = new Intent();
        mIntent.setAction(ICWSBucketAidlInterface.class.getName());
        Intent serverIntent = getExplicitIntent(getApplicationContext(), mIntent);
        if(null != serverIntent) {
            Intent intent = new Intent(serverIntent);
            intent.setPackage(getPackageName());
            Log.d(TAG, "unbindService");
            unbindService(bucketSC);
        }else{
            Log.e(TAG, "[unbind] Not find cloud service!!");
        }
    }

    public static Intent getExplicitIntent(Context context, Intent implicitIntent) {
        // Retrieve all services that can match the given intent
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfo = pm.queryIntentServices(implicitIntent, 0);
        // Make sure only one match was found
        if (resolveInfo == null || resolveInfo.size() != 1) {
            return null;
        }
        // Get component info and create ComponentName
        ResolveInfo serviceInfo = resolveInfo.get(0);
        String packageName = serviceInfo.serviceInfo.packageName;
        String className = serviceInfo.serviceInfo.name;
        ComponentName component = new ComponentName(packageName, className);
        // Create a new intent. Use the old one for extras and such reuse
        Intent explicitIntent = new Intent(implicitIntent);
        // Set the component to be explicit
        explicitIntent.setComponent(component);
        return explicitIntent;
    }



















}
