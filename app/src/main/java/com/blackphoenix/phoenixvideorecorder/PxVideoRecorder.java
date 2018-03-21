package com.blackphoenix.phoenixvideorecorder;

import android.content.Context;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import com.blackphoenix.phoenixwidgets.ILog;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by w on 07-11-2016.
 */

public class PxVideoRecorder implements SurfaceHolder.Callback {

    Context mContext;
    private SurfaceHolder surfaceHolder;
    public MediaRecorder mediaRecorder;
    public VideoSurfaceView surfaceView;
    private Camera mCamera;
    private FrameLayout mVideoFrame;

   // public Semaphore semRecording  = new Semaphore(1,true);

    //private File deviceHomeDir = Environment.getExternalStorageDirectory();
  /*  private String appDir = "/hoyo/";*/
    private final int MIN_RECORD_TIME = 5000;
    private String mVideoFolderName;
    private File mRecordedVideoFile;
    /*    private String videoRecordPath;*/
    //private int videoRecordTime = 10000; // 10 seconds
    public boolean mRecordStatus = false;
    public boolean mediaRecorderStatus = false;
    private boolean cameraStatus = false;

    private PxCameraListener mVideoRecordingListener;

    public class Parameters {
        int width;
        int height;
        int folderName;
    }

    public PxVideoRecorder(Context context){
        this(context,null);
    }

    public PxVideoRecorder(Context context, FrameLayout frameLayout) {
        this.mContext = context;
        this.mRecordStatus = false;
        this.mVideoFrame = frameLayout;
    }

    public void setCameraListener(PxCameraListener pxCameraListener){
        this.mVideoRecordingListener = pxCameraListener;
    }

    public void setVideoFrame(FrameLayout frame){
        this.mVideoFrame = frame;
    }

    public void setVideoFolderName(String name){
        this.mVideoFolderName = name;
    }

    private void setupCamera(){
        surfaceView = new VideoSurfaceView(mContext);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        if(mVideoFrame!=null)
        {
            // ToDO remove view once recording is done
            mVideoFrame.addView(surfaceView);
        }
        ILog.print("VRec","Camera Setup Done");
    }

    /*
     * Setup Media Recorder
     */

    private MediaRecorder setupMediaRecorder(){

        mRecordedVideoFile = null;

        if(mCamera == null){
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingError("Camera Initialization Failed");
        }

        MediaRecorder localMediaRecorder = new MediaRecorder();
        mCamera.unlock();

        localMediaRecorder.setCamera(mCamera);
        localMediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
        localMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        localMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        localMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        localMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        localMediaRecorder.setAudioChannels(1);
        localMediaRecorder.setAudioSamplingRate(48000);
        localMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        localMediaRecorder.setVideoFrameRate(20);
        localMediaRecorder.setVideoSize(640,480);

        try {
            mRecordedVideoFile = prepareVideoFile();
        } catch (VideoRecorderException e) {
            e.printStackTrace();
            mCamera.lock();

            if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }

            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("Unable to Create Video File!\n"+e.toString());
            return null;
        }

        localMediaRecorder.setOutputFile(mRecordedVideoFile.getAbsolutePath());

        ILog.print("VRec","Recorder Configuration Done");

        try {
            localMediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
        } catch (RuntimeException e){
            mCamera.lock();

            if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }

            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("RunTimeException : "+e.toString());
        }

        try {
            localMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            localMediaRecorder.release();
            mCamera.lock();

            if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }

            e.printStackTrace();
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("Exception Occurred "+e.toString());
            return null;
        } catch (IOException e) {
            localMediaRecorder.release();
            mCamera.lock();

            if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }

            e.printStackTrace();
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("Exception Occurred "+e.toString());
            return null;
        }
        ILog.print("VRec","Media Recorder Setup Done");

        return localMediaRecorder;
    }

    /*
     * Prepare Output file for video
     */

    private File prepareVideoFile() throws VideoRecorderException {

        File appHomeDir = mContext.getFilesDir();
        String videoRecordPath = appHomeDir.getPath();

        if(mVideoFolderName !=null){
            videoRecordPath +="/"+mVideoFolderName+"/Video";
        } else {
            videoRecordPath +="/Video";
        }

        File f = new File(videoRecordPath);

        if(f.isDirectory()) {
            ILog.print("VRec","Is Directory "+videoRecordPath);
        } else {
            if(!f.exists()) {
                if(!f.mkdirs()) {
                    throw new VideoRecorderException("Unable to Create Video Directory : "+f.getAbsolutePath());
                }
            }
        }

        File tempVideoFile = new File(videoRecordPath+"/"+getCurrentDataTimeString()+".mp4");
        try {
            if(tempVideoFile.isFile()) {
                if(!tempVideoFile.createNewFile()){
                    throw new VideoRecorderException("Unable to Create Video File : "+tempVideoFile);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new VideoRecorderException("Unable to Create Video Directory"+tempVideoFile.getAbsolutePath() +"ERROR: "+e.toString());
        }

        ILog.print("VRec","Video File Prepared");

        return tempVideoFile;
    }

    public String getCurrentDataTimeString(){
        SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat("MMddyyhhmss", Locale.getDefault());
        return simpleDateFormat.format(new Date());
    }

    /*
     * Runnable for Stop Recording
     */

    private Runnable stopRecordingRunnable = new Runnable() {
        @Override
        public void run() {
            ILog.print("VRec","Recording Finished");
            mRecordStatus = true;
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.setOutputFile(mRecordedVideoFile);
            StopRecording();
        }
    };

    /*
     * Start Video Recording
     */


    public void startRecording(int mSeconds) {
        startRecording(mSeconds,mVideoFolderName);
    }

    public void startRecording(int mSeconds, String folderName){

        this.setVideoFolderName(folderName);

        if(mSeconds<=MIN_RECORD_TIME){
            mSeconds = MIN_RECORD_TIME;
        }

        this.setupCamera();

        if(mCamera == null) {
            try {
                mCamera = getCameraInstance();
            } catch (VideoRecorderException e){
                ILog.print("VRec_E","No Camera Instance Available "+e.toString());
                if(mVideoRecordingListener!=null){
                    mVideoRecordingListener.recordingError(e.toString());
                }

                if(mVideoFrame!=null){
                    mVideoFrame.removeView(surfaceView);
                }

                return;
            }
        }

        mediaRecorder = this.setupMediaRecorder();

        if(mediaRecorder == null){

            if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }

            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("MediaRecorder setup initialize Failed");
            return;
        }

        mediaRecorder.start();
        mediaRecorderStatus = true;

        ILog.print("VRec","Record Started");

        if(mVideoRecordingListener!=null)
            mVideoRecordingListener.recordingStarted();

        /*
         * Set Timer based Stop Function
         */

        Handler handler = new Handler();
        handler.postDelayed(stopRecordingRunnable,mSeconds);

        ILog.print("VRec","Recorder Started");
    }

    /*
    * Stop Recording
     */

    private void StopRecording() {
        ILog.print("VRec","Recording Stopped");
        if(mediaRecorder != null) {

            if(mediaRecorderStatus) {
                mediaRecorder.stop();
            }
            mediaRecorder.release();
            mediaRecorder = null;
            mediaRecorderStatus = false;
            ILog.print("VRec","mediaRecorder stopped");
        }

        if(mVideoFrame!=null){
            mVideoFrame.removeView(surfaceView);
        }

        if(mVideoRecordingListener!=null)
            mVideoRecordingListener.setRecordingFinished();
    }

     /*
    * Release Camera
     */

    private void ReleaseCamera() {
        if (mCamera != null) {
            mCamera.lock();
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            surfaceHolder.removeCallback(this);
            mCamera.release();
            mCamera = null;
            ILog.print("VRec", "Camera stopped");

        }
    }

     /*
    * Destroy Recorder
     */

    private void Destroy() {
        ILog.print("VRec","Recording Destroy");
        if(mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }


    private Camera getCameraInstance() throws VideoRecorderException {
        Camera c = null;
        try {
            return Camera.open();
        } catch (Exception e) {
            e.printStackTrace();
            ILog.print("VRec", "Error Exception" + e);
            throw new VideoRecorderException("UYnable to Open Camera!\n"+e.toString());
        }
    }




    /*
     * Auto Generated **********************************************************************
     */


    private class VideoSurfaceView extends SurfaceView {
        public VideoSurfaceView(Context context) {
            super(context);
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        ILog.print("VRec","SurFace Created");
        if(mCamera == null) {
            try {
                mCamera = getCameraInstance();
            } catch (VideoRecorderException e){
                if(mVideoRecordingListener!=null){
                    mVideoRecordingListener.recordingError(e.toString());
                }
                return;
            }
        }

        try {
            mCamera.setPreviewDisplay(surfaceHolder);
            //camera.setPreviewCallback();
        } catch (IOException e) {
            e.printStackTrace();
            if(mVideoRecordingListener!=null){
                mVideoRecordingListener.recordingError(e.toString());
            }
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
        ILog.print("VRec","SurFace Changed");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        ILog.print("VRec","Surface Destroyed");

        this.surfaceHolder.removeCallback(this);

        if(mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            mCamera.lock();
        }

        if(mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }
}

/*

 */
