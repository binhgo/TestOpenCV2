/*
 * Copyright (c) 2010, Sony Ericsson Mobile Communication AB. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright notice, this 
 *      list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright notice,
 *      this list of conditions and the following disclaimer in the documentation
 *      and/or other materials provided with the distribution.
 *    * Neither the name of the Sony Ericsson Mobile Communication AB nor the names
 *      of its contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.em.testopencv2;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import android.graphics.Bitmap;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author Erik Hellman <erik.hellman@sonymobile.com>
 */
public class OpenCVWorker implements Runnable {
    public static final String TAG = "OpenCVWorker";

    public static final int FIRST_CAMERA = 0;
    public static final int SECOND_CAMERA = 1;

    public static final int RESULT_MATRIX_BUFFER_SIZE = 3;

    /**
     * Constant used to calculate FPS value (see measureFps())
     */
    public static final int FPS_STEPS = 20;

    // The threshold value for the lower and upper color limits
    public static final double THRESHOLD_LOW = 35;
    public static final double THRESHOLD_HIGH = 35;

    /**
     * Boolean
     */
    private boolean mDoProcess;
    private int mCameraId = SECOND_CAMERA;
    private Size mPreviewSize;
    private VideoCapture mCamera;
    private Set<ResultCallback> mResultCallbacks = Collections.synchronizedSet(new HashSet<ResultCallback>());
    private ConcurrentLinkedQueue<Bitmap> mResultBitmaps = new ConcurrentLinkedQueue<Bitmap>();

    /**
     * Matrices used to hold the actual image data for each processing step
     */
    private Mat mCurrentFrame;
    private Mat mFilteredFrame;
    private Mat mInRangeResult;
    private Mat mCurrentFrameHsv;

    private int mFpsCounter;
    private double mFpsFrequency;
    private long mPrevFrameTime;
    private double mPreviousFps;

    private Point mSelectedPoint = null;

    private Scalar mLowerColorLimit;
    private Scalar mUpperColorLimit;

    public OpenCVWorker(int cameraId) {
        mCameraId = cameraId;
        // Default preview size
        mPreviewSize = new Size(480, 320);
    }

    public void releaseResultBitmap(Bitmap bitmap) {
        mResultBitmaps.offer(bitmap);
    }

    public void addResultCallback(ResultCallback resultCallback) {
        mResultCallbacks.add(resultCallback);
    }

    public void removeResultCallback(ResultCallback resultCallback) {
        mResultCallbacks.remove(resultCallback);
    }

    public void stopProcessing() {
        mDoProcess = false;
    }

    // Setup the camera
    private void setupCamera() {
        if (mCamera != null) {
            VideoCapture camera = mCamera;
            mCamera = null; // Make it null before releasing...
            camera.release();
        }

        mCamera = new VideoCapture(mCameraId);

        // Figure out the most appropriate preview size that this camera supports.
        // We always need to do this as each device support different preview sizes for their cameras
        List<Size> previewSizes = mCamera.getSupportedPreviewSizes();
        double largestPreviewSize = 1280 * 720; // We should be smaller than this...
        double smallestWidth = 480; // Let's not get a smaller width than this...
        for (Size previewSize : previewSizes) {
            if (previewSize.area() < largestPreviewSize && previewSize.width >= smallestWidth) {
                mPreviewSize = previewSize;
            }
        }

        mCamera.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, mPreviewSize.width);
        mCamera.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, mPreviewSize.height);
    }

    /**
     * Initialize the matrices and the bitmaps we will use to draw the result
     */
    private void initMatrices() {
        mCurrentFrame = new Mat();
        mCurrentFrameHsv = new Mat();
        mFilteredFrame = new Mat();
        mInRangeResult = new Mat();

        // Since drawing to screen occurs on a different thread than the processing,
        // we use a queue to handle the bitmaps we will draw to screen
        mResultBitmaps.clear();
        for (int i = 0; i < RESULT_MATRIX_BUFFER_SIZE; i++) {
            Bitmap resultBitmap = Bitmap.createBitmap((int) mPreviewSize.width, (int) mPreviewSize.height,
                    Bitmap.Config.ARGB_8888);
            mResultBitmaps.offer(resultBitmap);
        }
    }

    /**
     * The thread used to grab and process frames
     */
    @Override
    public void run() {
        mDoProcess = true;
        Rect previewRect = new Rect(0, 0, (int) mPreviewSize.width, (int) mPreviewSize.height);
        double fps;
        mFpsFrequency = Core.getTickFrequency();
        mPrevFrameTime = Core.getTickCount();

        setupCamera();

        initMatrices();

        while (mDoProcess && mCamera != null) {
            boolean grabbed = mCamera.grab();
            if (grabbed) {
                // Retrieve the next frame from the camera in RGB format
                mCamera.retrieve(mCurrentFrame, Highgui.CV_CAP_ANDROID_COLOR_FRAME_RGB);

                // Convert the RGB frame to HSV as it is a more appropriate format when calling Core.inRange
                Imgproc.cvtColor(mCurrentFrame, mCurrentFrameHsv, Imgproc.COLOR_RGB2HSV);

                // If we have selected a new point, get the color range and decide the color range
                if (mLowerColorLimit == null && mUpperColorLimit == null && mSelectedPoint != null) {
                    double[] selectedColor = mCurrentFrameHsv.get((int) mSelectedPoint.x, (int) mSelectedPoint.y);

                    // We check the colors in a 5x5 pixels square (Region Of Interest) and get the average from that
                    if (mSelectedPoint.x < 2) {
                        mSelectedPoint.x = 2;
                    } else if (mSelectedPoint.x >= (previewRect.width - 2)) {
                        mSelectedPoint.x = previewRect.width - 2;
                    }
                    if (mSelectedPoint.y < 2) {
                        mSelectedPoint.y = 2;
                    } else if (mSelectedPoint.y >= (previewRect.height - 2)) {
                        mSelectedPoint.y = previewRect.height - 2;
                    }

                    // ROI (Region Of Interest) is used to find the average value around the point we clicked.
                    // This will reduce the risk of getting "freak" values if the pixel where we clicked has an unexpected value
                    Rect roiRect = new Rect((int) (mSelectedPoint.x - 2), (int) (mSelectedPoint.y - 2), 5, 5);
                    // Get the Matrix representing the ROI
                    Mat roi = mCurrentFrameHsv.submat(roiRect);
                    // Calculate the mean value of the the ROI matrix
                    Scalar sumColor = Core.mean(roi);
                    double[] sumColorValues = sumColor.val;

                    // Decide on the color range based on the mean value from the ROI
                    if (selectedColor != null) {
                        mLowerColorLimit = new Scalar(sumColorValues[0] - THRESHOLD_LOW * 3,
                                sumColorValues[1] - THRESHOLD_LOW,
                                sumColorValues[2] - THRESHOLD_LOW);
                        mUpperColorLimit = new Scalar(sumColorValues[0] + THRESHOLD_HIGH * 3,
                                sumColorValues[1] + THRESHOLD_HIGH,
                                sumColorValues[2] + THRESHOLD_HIGH);
                    }
                }

                // If we have selected color, process the current frame using inRange function
                if (mLowerColorLimit != null && mUpperColorLimit != null) {
                    // Using the color limits to generate a mask (mInRangeResult)
                    Core.inRange(mCurrentFrameHsv, mLowerColorLimit, mUpperColorLimit, mInRangeResult);
                    // Clear (set to black) the filtered image frame
                    mFilteredFrame.setTo(new Scalar(0, 0, 0));
                    // Copy the current frame in RGB to the filtered frame using the mask.
                    // Only the pixels in the mask will be copied.
                    mCurrentFrame.copyTo(mFilteredFrame, mInRangeResult);

                    notifyResultCallback(mFilteredFrame);
                } else {
                    notifyResultCallback(mCurrentFrame);
                }

                fps = measureFps();
                notifyFpsResult(fps);
            }
        }

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }

    }

    public double measureFps() {
        mFpsCounter++;
        if (mFpsCounter % FPS_STEPS == 0) {
            long time = Core.getTickCount();
            double fps = FPS_STEPS * mFpsFrequency / (time - mPrevFrameTime);
            mPrevFrameTime = time;
            mPreviousFps = fps;
        }
        return mPreviousFps;
    }


    private void notifyFpsResult(double fps) {
        for (ResultCallback resultCallback : mResultCallbacks) {
            resultCallback.onFpsUpdate(fps);
        }
    }

    private void notifyResultCallback(Mat result) {
        Bitmap resultBitmap = mResultBitmaps.poll();
        if (resultBitmap != null) {
            Utils.matToBitmap(result, resultBitmap, true);
            for (ResultCallback resultCallback : mResultCallbacks) {
                resultCallback.onResultMatrixReady(resultBitmap);
            }
        }
    }

    public void setSelectedPoint(double x, double y) {
        mLowerColorLimit = null;
        mUpperColorLimit = null;
        mSelectedPoint = new Point(x, y);
    }

    public void clearSelectedColor() {
        mLowerColorLimit = null;
        mUpperColorLimit = null;
        mSelectedPoint = null;
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }

    public interface ResultCallback {
        void onResultMatrixReady(Bitmap mat);

        void onFpsUpdate(double fps);
    }
}
