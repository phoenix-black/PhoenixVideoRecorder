package com.blackphoenix.phoenixvideorecorder;

/**
 * Created by shree on 2/17/2018.
 */
public class VideoRecorderException extends Exception {
    public VideoRecorderException(String message){
        super("VideoRecorderException: "+message);
    }
}
