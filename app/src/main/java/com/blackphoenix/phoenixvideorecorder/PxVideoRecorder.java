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
 * Created by Praba on 07-11-2016.
 *  ToDo : Add the message as String Resources and Add the Language Support
 *
 */

public class PxVideoRecorder implements SurfaceHolder.Callback {

    private static String LOG_TITLE = PxVideoRecorder.class.getSimpleName();

    public static String E_MEDIA_RECORDER_CAMERA_INIT = "E0511";
    public static String E_PREPARE_VIDEO_FILE_FAILED = "E0512";
    public static String E_RECORDER_SET_PREVIEW_DISP_FAILED = "E0513";
    public static String E_PREPARE_M_RECORDER_STAE_ERROR = "E0514";
    public static String E_PREPARE_M_RECORDER_IO_ERROR = "E0515";
    public static String E_INIT_CAMERA_INSTANCE_FAILED = "E0516";
    public static String E_CAMERA_SET_PREVIEW_DISP_FAILED = "E0517";

    private Context mContext;
    private SurfaceHolder surfaceHolder;
    private MediaRecorder mediaRecorder;
    private VideoSurfaceView surfaceView;
    private Camera mCamera;
    private FrameLayout mVideoFrame;

    private final int MIN_RECORD_TIME = 5000;
    private String mVideoFolderName;
    private File mRecordedVideoFile;

    private boolean mRecordStatus = false;
    private boolean mediaRecorderStatus = false;
    private boolean isDebug = false;

    private PxCameraListener mVideoRecordingListener;
    private PxVideoRecorderDebugListener mDebugListener;

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

    public void addDebugListener(PxVideoRecorderDebugListener listener){
        this.mDebugListener = listener;
    }

    public void setVideoFrame(FrameLayout frame){
        this.mVideoFrame = frame;
    }

    public void setVideoFolderName(String name){
        this.mVideoFolderName = name;
    }

    public void setDebugEnabled(boolean status){
        this.isDebug = status;
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
        debugLog("Camera Setup Done");
    }

    /*
     * Setup Media Recorder
     */

    private MediaRecorder setupMediaRecorder(){

        mRecordedVideoFile = null;

        if(mCamera == null){
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("Internal Error has Occurred","Media Recorder Setup Failed! Camera object is Null",E_MEDIA_RECORDER_CAMERA_INIT);

            return null;
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
                mVideoRecordingListener.recordingInterrupted("Internal Error has Occurred","Unable to Create Video File!\n"+e.toString(),E_PREPARE_VIDEO_FILE_FAILED);
            return null;
        }

        localMediaRecorder.setOutputFile(mRecordedVideoFile.getAbsolutePath());

        debugLog("Recorder Configuration Done");

        try {
            localMediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
        } catch (RuntimeException e){
            mCamera.lock();

            if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }

            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("Internal Error has Occurred","RunTimeException : "+e.toString(), E_RECORDER_SET_PREVIEW_DISP_FAILED);

            return null;
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
                mVideoRecordingListener.recordingInterrupted("Internal Error has Occurred","Exception Occurred "+e.toString(), E_PREPARE_M_RECORDER_STAE_ERROR);
            return null;
        } catch (IOException e) {
            localMediaRecorder.release();
            mCamera.lock();

            if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }

            e.printStackTrace();
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("Internal Error has Occurred","Exception Occurred "+e.toString(),E_PREPARE_M_RECORDER_IO_ERROR);
            return null;
        }
        debugLog("Media Recorder Setup Done");

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
            debugLog("Is Directory "+videoRecordPath);
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

        debugLog("Video File Prepared");

        return tempVideoFile;
    }

    private String getCurrentDataTimeString(){
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
            debugLog("Recording Finished");
            mRecordStatus = true;
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.setOutputFile(mRecordedVideoFile);
            finishRecording();
        }
    };

    /*
     * Start Video Recording
     */


    public void startRecording(int mSeconds) {
        startRecording(mSeconds,mVideoFolderName);
    }

    public void startRecording(int mSeconds, String folderName){

        this.mVideoFolderName =folderName;

        if(mSeconds<=MIN_RECORD_TIME){
            mSeconds = MIN_RECORD_TIME;
        }

        this.setupCamera();
    }

    private void start(int mSeconds){
        mediaRecorder = this.setupMediaRecorder();

        if(mediaRecorder == null){

            // ToDo Commented this for experimentation
            /*if(mVideoFrame!=null){
                mVideoFrame.removeView(surfaceView);
            }*/

            /* Already Handled
            if(mVideoRecordingListener!=null)
                mVideoRecordingListener.recordingInterrupted("Internal Error has Occcurred","MediaRecorder setup initialize Failed");*/
            return;
        }

        mediaRecorder.start();
        mediaRecorderStatus = true;

        debugLog("Record Started");

        if(mVideoRecordingListener!=null)
            mVideoRecordingListener.recordingStarted();

        /*
         * Set Timer based Stop Function
         */

        Handler handler = new Handler();
        handler.postDelayed(stopRecordingRunnable,mSeconds);

        debugLog("Recorder Started");
    }

    /*
    * Stop Recording
     */

    private void finishRecording() {
        debugLog("Recording Stopped");
        if(mediaRecorder != null) {

            if(mediaRecorderStatus) {
                mediaRecorder.stop();
            }
            mediaRecorder.release();
            mediaRecorder = null;
            mediaRecorderStatus = false;
            debugLog("mediaRecorder stopped");
        }

        if(mVideoFrame!=null){
            mVideoFrame.removeView(surfaceView);
        }

        if(mVideoRecordingListener!=null)
            mVideoRecordingListener.setRecordingFinished();
    }

    private void stopRecordingOnError(String errorMessage){

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
            debugLog( "Camera stopped");

        }
    }

     /*
    * Destroy Recorder
     */

    private void Destroy() {
        debugLog("Recording Destroy");
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
            debugLog("Error Exception" + e);
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
        debugLog("SurFace Created");
        if(mCamera == null) {
            try {
                mCamera = getCameraInstance();
            } catch (VideoRecorderException e){
                if(mVideoRecordingListener!=null){
                    mVideoRecordingListener.recordingError("Internal Error has Occurred",e.toString(),E_INIT_CAMERA_INSTANCE_FAILED);
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
                mVideoRecordingListener.recordingError("Internal Error has Occurred",e.toString(),E_CAMERA_SET_PREVIEW_DISP_FAILED);
            }
        }

        start(MIN_RECORD_TIME);

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
        debugLog("SurFace Changed");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        debugLog("Surface Destroyed");

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

    private void debugLog(String message){
        if(isDebug) {
            ILog.print(LOG_TITLE, message);
        }

        if(mDebugListener!=null){
            mDebugListener.debugLog(LOG_TITLE,""+message);
        }

    }
}

/*

 */
