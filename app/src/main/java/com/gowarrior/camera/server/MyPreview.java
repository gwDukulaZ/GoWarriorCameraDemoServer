package com.gowarrior.camera.server;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.ViewGroup;

import com.gowarrior.camera.server.models.DownloadModel;
import com.gowarrior.camera.server.models.UploadModel;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by dukula.zhu on 2015/7/25.
 */
public class MyPreview extends CameraBridgeViewBase implements Camera.PreviewCallback {
    private static final String TAG = "GoWarriorCameraServer";
    private Camera mCamera;
    private boolean isPreviewing = true;
    private SurfaceHolder mHolder;
    private String mCurrentPhotoPath;
    private String curSnapshotMode = "Active";
    private Integer newPicture = 0;
    private boolean bPhotoUpload = false;

    private Thread mThread;
    private boolean mStopThread;
    private int mChainIdx = 0;
    private Mat[] mFrameChain;
    protected JavaCameraFrame[] mCameraFrame;
    private byte mBuffer[];
    private static final int MAGIC_TEXTURE_ID = 10;
    private SurfaceTexture mSurfaceTexture;
    private String mCameraMode = "Unknow";
    private int mCameraFrameWidth = 0;
    private int mCameraFrameHeight = 0;
    private Handler mHandle = null;


    private static ExecutorService mExecutorService = Executors.newFixedThreadPool(10);


    public static class JavaCameraSizeAccessor implements ListItemAccessor {

        @Override
        public int getWidth(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.width;
        }

        @Override
        public int getHeight(Object obj) {
            Camera.Size size = (Camera.Size) obj;
            return size.height;
        }
    }

    private class JavaCameraFrame implements CvCameraViewFrame {
        @Override
        public Mat gray() {
            return mYuvFrameData.submat(0, mHeight, 0, mWidth);
        }

        @Override
        public Mat rgba() {
            Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4);
            return mRgba;
        }

        public JavaCameraFrame(Mat Yuv420sp, int width, int height) {
            super();
            mWidth = width;
            mHeight = height;
            mYuvFrameData = Yuv420sp;
            mRgba = new Mat();
        }

        public void release() {
            mRgba.release();
        }

        private Mat mYuvFrameData;
        private Mat mRgba;
        private int mWidth;
        private int mHeight;
    }

    public MyPreview(Context context, AttributeSet attrs) {
        super(context,attrs);
        setCameraIndex(CAMERA_ID_BACK);
        mHolder = this.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setHandle(Handler hd) {mHandle = hd;};

    /*@Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "SurfaceView surfaceCreated called");
        Integer num = Camera.getNumberOfCameras();
        Log.v(TAG, "There are " + num + " cameras");
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i=0;i<num;i++) {
            Camera.getCameraInfo(i, info);
            Log.v(TAG, "camera " + i + " id = " + info.toString());
            Log.v(TAG, "Now safeCameraOpen camera " + info.facing + "...");
            boolean ret = safeCameraOpen(info.facing);
            Log.v(TAG, "safeCameraOpen done, ret = " + ret);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        Log.v(TAG,"SurfaceView surfaceChanged called");
        // Now that the size is known, set up the camera parameters and begin
        // the preview.
        if(null == mCamera)
            return;
        Camera.Parameters parameters = mCamera.getParameters();
        Log.v(TAG,"setPreviewSize w=" + w + " h=" + h);
        parameters.setPreviewSize(w,h);
        parameters.setRecordingHint(false);
        requestLayout();
        Log.v(TAG, "call getSupportedPictureSizes");
        List<Camera.Size> vSizeList = parameters.getSupportedPictureSizes();
        for(int num = 0; num < vSizeList.size(); num++) {
            Camera.Size vSize = vSizeList.get(num);
            Log.v(TAG, "Support Size " + num + " " + vSize.height + " * " + vSize.width);
            if (vSize.height<h && vSize.width<w) {
                parameters.setPreviewSize(vSize.width, vSize.height);
                Log.v(TAG, "Try to set preview w=" + vSize.width + " h=" + vSize.height);
                break;
            }
        }
        if(this.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
            parameters.set("orientation", "portrait");
        } else {
            parameters.set("orientation", "landscape");
        }
        Log.v(TAG, "call setParameters");
        mCamera.setParameters(parameters);
        try {
            Log.v(TAG, "call setPreviewDisplay");
            mCamera.setPreviewDisplay(holder);
        } catch (IOException exception) {
            mCamera.release();
            mCamera = null;
            exception.printStackTrace();
        }
        // Important: Call startPreview() to start updating the preview surface.
        // Preview must be started before you can take a picture.
        Log.v(TAG, "call startPreview");
        mCamera.startPreview();
        Log.v(TAG,"SurfaceView surfaceChanged done");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "SurfaceView surfaceDestroyed called");
        stopPreviewAndFreeCamera();
    }

    private void stopPreviewAndFreeCamera() {
        if (mCamera != null) {
            // Call stopPreview() to stop updating the preview surface.
            Log.v(TAG, "call stopPreview");
            mCamera.stopPreview();
            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            Log.v(TAG, "call release");
            mCamera.release();
            mCamera = null;
        }
    }

    private boolean safeCameraOpen(int id) {
        boolean qOpened = false;
        try {
            Log.v(TAG,"Now stopPreviewAndFreeCamera ...");
            stopPreviewAndFreeCamera();
            Log.v(TAG, "stopPreviewAndFreeCamera done");
            Log.v(TAG,"Now Camera.open " + id + "...");
            mCamera = Camera.open(id);
            qOpened = (mCamera != null);
            Log.v(TAG,"Camera.open ret = " + qOpened);
        } catch (Exception e) {
            Log.e(TAG, "failed to open Camera");
            e.printStackTrace();
        }
        return qOpened;
    }*/

    protected boolean initializeCamera(int width, int height) {
        Log.d(TAG, "Initialize java camera");
        boolean result = true;
        synchronized (this) {
            mCamera = null;

            if (mCameraIndex == CAMERA_ID_ANY) {
                Log.d(TAG, "Trying to open camera with old open()");
                try {
                    mCamera = Camera.open();
                }
                catch (Exception e){
                    Log.e(TAG, "Camera is not available (in use or does not exist): " + e.getLocalizedMessage());
                }

                if(mCamera == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    boolean connected = false;
                    for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                        Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(camIdx) + ")");
                        try {
                            mCamera = Camera.open(camIdx);
                            connected = true;
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Camera #" + camIdx + "failed to open: " + e.getLocalizedMessage());
                        }
                        if (connected) break;
                    }
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    int localCameraIndex = mCameraIndex;
                    if (mCameraIndex == CAMERA_ID_BACK) {
                        Log.i(TAG, "Trying to open back camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo( camIdx, cameraInfo );
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    } else if (mCameraIndex == CAMERA_ID_FRONT) {
                        Log.i(TAG, "Trying to open front camera");
                        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                        for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); ++camIdx) {
                            Camera.getCameraInfo( camIdx, cameraInfo );
                            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                localCameraIndex = camIdx;
                                break;
                            }
                        }
                    }
                    if (localCameraIndex == CAMERA_ID_BACK) {
                        Log.e(TAG, "Back camera not found!");
                    } else if (localCameraIndex == CAMERA_ID_FRONT) {
                        Log.e(TAG, "Front camera not found!");
                    } else {
                        Log.d(TAG, "Trying to open camera with new open(" + Integer.valueOf(localCameraIndex) + ")");
                        try {
                            mCamera = Camera.open(localCameraIndex);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Camera #" + localCameraIndex + "failed to open: " + e.getLocalizedMessage());
                        }
                    }
                }
            }

            if (mCamera == null)
                return false;

            /* Now set camera parameters */
            try {
                Camera.Parameters params = mCamera.getParameters();
                Log.d(TAG, "getSupportedPreviewSizes():" + width + ":" +height);
                List<android.hardware.Camera.Size> sizes = params.getSupportedPreviewSizes();

                if (sizes != null) {
                    /* Select the size that fits surface considering maximum size allowed */
                    Size frameSize = calculateCameraFrameSize(sizes, new JavaCameraSizeAccessor(), width, height);
                    frameSize.width = 640;
                    frameSize.height = 480;
                    mCameraFrameWidth = (int)frameSize.width;
                    mCameraFrameHeight = (int)frameSize.height;

                    params.setPreviewFormat(ImageFormat.NV21);
                    Log.d(TAG, "Set preview size to " + Integer.valueOf((int) frameSize.width) + "x" + Integer.valueOf((int) frameSize.height));
                    params.setPreviewSize((int)frameSize.width, (int)frameSize.height);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && !android.os.Build.MODEL.equals("GT-I9100"))
                        params.setRecordingHint(false);

                    List<String> FocusModes = params.getSupportedFocusModes();
                    if (FocusModes != null && FocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                    {
                        params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    }

                    mCamera.setParameters(params);
                    params = mCamera.getParameters();

                    mFrameWidth = params.getPreviewSize().width;
                    mFrameHeight = params.getPreviewSize().height;

                    if ((getLayoutParams().width == ViewGroup.LayoutParams.MATCH_PARENT) && (getLayoutParams().height == ViewGroup.LayoutParams.MATCH_PARENT))
                        mScale = Math.min(((float)height)/mFrameHeight, ((float)width)/mFrameWidth);
                    else
                        mScale = 0;

                    if (mFpsMeter != null) {
                        mFpsMeter.setResolution(mFrameWidth, mFrameHeight);
                    }

                    int size = mFrameWidth * mFrameHeight;
                    size  = size * (ImageFormat.getBitsPerPixel(params.getPreviewFormat()) + 4)/ 8;
                    mBuffer = new byte[size];

//                    mCamera.addCallbackBuffer(mBuffer);
//                    mCamera.setPreviewCallbackWithBuffer(this);

                    mFrameChain = new Mat[2];
                    mFrameChain[0] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);
                    mFrameChain[1] = new Mat(mFrameHeight + (mFrameHeight/2), mFrameWidth, CvType.CV_8UC1);

//                    AllocateCache();

                    mCameraFrame = new JavaCameraFrame[2];
                    mCameraFrame[0] = new JavaCameraFrame(mFrameChain[0], mFrameWidth, mFrameHeight);
                    mCameraFrame[1] = new JavaCameraFrame(mFrameChain[1], mFrameWidth, mFrameHeight);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mSurfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
                        mCamera.setPreviewDisplay(this.getHolder());
                    } else
                        mCamera.setPreviewDisplay(null);

                    /* Finally we are ready to start the preview */
                    Log.d(TAG, "startPreview");
                    mCamera.startPreview();
                }
                else
                    result = false;
            } catch (Exception e) {
                result = false;
                e.printStackTrace();
            }
        }

        return result;
    }

    public int getmCameraFrameWidth() {
        return mCameraFrameWidth;
    }

    public int getmCameraFrameHeight() {
        return mCameraFrameHeight;
    }

    protected void releaseCamera() {
        synchronized (this) {
            if (mCamera != null) {
                mCamera.stopPreview();
                mCamera.setPreviewCallback(null);

                mCamera.release();
            }
            mCamera = null;
            if (mFrameChain != null) {
                mFrameChain[0].release();
                mFrameChain[1].release();
            }
            if (mCameraFrame != null) {
                mCameraFrame[0].release();
                mCameraFrame[1].release();
            }
        }
    }

    public boolean changeCameraDisplayMode(String mModeString) {
        boolean mRet = false;
        if (null != mCamera) {
            mCamera.stopPreview();
            if(mModeString.toLowerCase().equals("texture")) {
                mCamera.addCallbackBuffer(mBuffer);
                mCamera.setPreviewCallbackWithBuffer(this);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mCamera.setPreviewTexture(mSurfaceTexture);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mCameraMode = "texture";
                mRet = true;
            } else if(mModeString.toLowerCase().equals("display")) {
                mCamera.setPreviewCallback(null);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                        mCamera.setPreviewDisplay(this.getHolder());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mCameraMode = "display";
                mRet = true;
            } else {
                Log.e(TAG, "Mode is not support!");
            }
            mCamera.startPreview();
        }
        return mRet;
    }

    public float getFrameScale() {
        return mScale;
    }

    private boolean mCameraFrameReady = false;

    @Override
    protected boolean connectCamera(int width, int height) {

        /* 1. We need to instantiate camera
         * 2. We need to start thread which will be getting frames
         */
        /* First step - initialize camera connection */
        Log.d(TAG, "Connecting to camera");
        if (!initializeCamera(width, height))
            return false;

        mCameraFrameReady = false;

        /* now we can start update thread */
        Log.d(TAG, "Starting processing thread");
        mStopThread = false;
        mThread = new Thread(new CameraWorker());
        mThread.start();

        return true;
    }

    @Override
    protected void disconnectCamera() {
        /* 1. We need to stop thread which updating the frames
         * 2. Stop camera and release it
         */
        Log.d(TAG, "Disconnecting from camera");
        try {
            mStopThread = true;
            Log.d(TAG, "Notify thread");
            synchronized (this) {
                this.notify();
            }
            Log.d(TAG, "Wating for thread");
            if (mThread != null)
                mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mThread =  null;
        }

        /* Now release camera */
        releaseCamera();

        mCameraFrameReady = false;
    }

    @Override
    public void onPreviewFrame(byte[] frame, Camera arg1) {
        Log.d(TAG, "Preview Frame received. Frame size: " + frame.length);
        synchronized (this) {
            mFrameChain[mChainIdx].put(0, 0, frame);
            mCameraFrameReady = true;
            this.notify();
        }
        if (mCamera != null)
            mCamera.addCallbackBuffer(mBuffer);
    }

    private class CameraWorker implements Runnable {

        @Override
        public void run() {
            do {
                synchronized (MyPreview.this) {
                    try {
                        while (!mCameraFrameReady && !mStopThread) {
                            MyPreview.this.wait();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (mCameraFrameReady)
                        mChainIdx = 1 - mChainIdx;
                }

                if (!mStopThread && mCameraFrameReady) {
                    mCameraFrameReady = false;
                    if (!mFrameChain[1 - mChainIdx].empty()) {
                        deliverAndDrawFrame(mCameraFrame[1 - mChainIdx]);
                    }
                }
            } while (!mStopThread);
            Log.d(TAG, "Finish processing thread");
        }
    }

    public void doTakePicture(){
        Log.v(TAG,"doTakePicture isPreviewing = " + isPreviewing);
        if(isPreviewing && (mCamera != null)){
            Log.v(TAG, "Now takePicture ...");
            isPreviewing = false;
            mCamera.stopPreview();
            mCamera.takePicture(mShutterCallback, null, mJpegPictureCallback);
            Log.v(TAG, "takePicture done");
        }
    }

    private Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback()
    {
        public void onShutter() {
            Log.v(TAG, "myShutterCallback:onShutter...");
        }
    };

    private Camera.PictureCallback mJpegPictureCallback = new Camera.PictureCallback()
    {
        public void onPictureTaken(byte[] data, Camera camera) {
            File photoFile;
            Log.v(TAG, "myJpegCallback:onPictureTaken...");
            if(null != data){
                Log.v(TAG, "call stopPreview");
                mCamera.stopPreview();
                isPreviewing = false;
                Camera.Parameters ps = camera.getParameters();
                if(ps.getPictureFormat() == PixelFormat.JPEG){
                    /* save snapshot */
                    Log.v(TAG, "Now save snapshot");
                    try {
                        photoFile = createImageFile(curSnapshotMode);
                        if (!photoFile.exists()) {
                            photoFile.createNewFile();
                        }
                        FileOutputStream fos = new FileOutputStream(photoFile);
                        fos.write(data);
                        fos.close();
                        newPicture++;

                        Log.d(TAG,"curSnapshotMode is "+ curSnapshotMode);
                        Log.d(TAG,"bphotoupload is "+ bPhotoUpload);

                        if(curSnapshotMode.equalsIgnoreCase("Remote") || bPhotoUpload) {
                            String mvPath = photoFile.getPath().replaceFirst("new", "upload");
                            File newfile = new File(mvPath);
                            photoFile.renameTo(newfile);
                            Log.v(TAG, "rename " + photoFile.getPath() + " to " + mvPath);
                            Log.v(TAG, "Now to upload file: " + newfile.toString());
                            Uri fileUri = Uri.fromFile(newfile);

                            //jerry upload
                            //int id = MainActivity.cloudTool.uploadFile(fileUri);
                            upload(fileUri);

                            if (mHandle != null) {
                                Message msg = mHandle.obtainMessage(Constants.Snapshot_UploadStart, 0, 0, mvPath);
                                mHandle.sendMessage(msg);
                                Log.v(TAG, "send msg to notify upload " + newfile + " start");
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
            Log.v(TAG, "call startPreview");
            mCamera.startPreview();
            if (mCameraMode.equals("texture")) {
                mCamera.addCallbackBuffer(mBuffer);
                mCamera.setPreviewCallbackWithBuffer(MyPreview.this);
            }
            isPreviewing = true;
        }
    };

    public File createImageFile(String surfix) throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Log.i(TAG, "Time Stamp " + timeStamp + "!!\n");
        String imageFilePath = Constants.UPLOAD_FROM + "/new/" + timeStamp + "_" + surfix + ".jpg";
//        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = new File(imageFilePath);

        // Save a file: path for use with ACTION_VIEW intents
        //mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        mCurrentPhotoPath = image.getAbsolutePath();
        Log.v(TAG,"take photo to " + image.toString());
        Log.v(TAG,"mCurrentPhotoPath = " + mCurrentPhotoPath);
        return image;
    }

    public String getCurrentPhotoPath() {
        return mCurrentPhotoPath;
    }

    public int getPictureNum() {
        return newPicture;
    }

    public void uploadPictureDone() {
        if (newPicture <= 0) {
            Log.v(TAG,"Pictrue num error, now it is " + newPicture);
            return;
        }
        newPicture--;
    }

    public void setCurrentSnapshotMode(String mode) {
        curSnapshotMode = mode;
    }

    public String getCurrentSnapshotMode() {
        return curSnapshotMode;
    }

    public void setPhotoUpload(boolean bUpload) {
        bPhotoUpload = bUpload;
    }

    public void upload(Uri uri) {
        UploadModel model = new UploadModel(getContext(), uri);
        if(null != mExecutorService)
            mExecutorService.execute(model.getUploadRunnable());
    }
    public void download(Uri uri) {
        DownloadModel model = new DownloadModel(getContext(), uri);
    }
}


