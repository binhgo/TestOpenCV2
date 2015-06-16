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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * @author Erik Hellman <erik.hellman@sonymobile.com>
 */
public class OpenCVTutorial extends Activity implements OpenCVWorker.ResultCallback, SurfaceHolder.Callback, View.OnTouchListener, GestureDetector.OnDoubleTapListener
{
    public static final int DRAW_RESULT_BITMAP = 10;
    private Handler mUiHandler;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Rect mSurfaceSize;
    private OpenCVWorker mWorker;
    private double mFpsResult;
    private Paint mFpsPaint;
    private GestureDetector mGestureDetector;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mGestureDetector = new GestureDetector(new MyOnGestureListener());
        mGestureDetector.setOnDoubleTapListener(this);
        mGestureDetector.setIsLongpressEnabled(false);

        mFpsPaint = new Paint();
        mFpsPaint.setColor(Color.GREEN);
        mFpsPaint.setDither(true);
        mFpsPaint.setFlags(Paint.SUBPIXEL_TEXT_FLAG);
        mFpsPaint.setTextSize(48);
        mFpsPaint.setTypeface(Typeface.SANS_SERIF);

        mSurfaceView = new SurfaceView(this);
        mSurfaceView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mSurfaceHolder = mSurfaceView.getHolder();

        // Create a Handler that we can post messages to so we avoid having to use anonymous Runnables
        // and runOnUiThread() instead
        mUiHandler = new Handler(getMainLooper(), new UiCallback());


    }

    @Override
    protected void onResume()
    {
        super.onResume();

        mSurfaceHolder.addCallback(this);
        mSurfaceView.setOnTouchListener(this);
        setContentView(mSurfaceView);
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        mWorker.stopProcessing();
        mWorker.removeResultCallback(this);

        if (mSurfaceHolder != null)
        {
            mSurfaceHolder.removeCallback(this);
        }
    }

    @Override
    public void onResultMatrixReady(Bitmap resultBitmap)
    {
        mUiHandler.obtainMessage(DRAW_RESULT_BITMAP, resultBitmap).sendToTarget();
    }

    @Override
    public void onFpsUpdate(double fps)
    {
        mFpsResult = fps;
    }

    private void initCameraView()
    {
        mWorker = new OpenCVWorker(OpenCVWorker.FIRST_CAMERA);
        mWorker.addResultCallback(this);
        new Thread(mWorker).start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder)
    {
        // Initializing OpenCV is done asynchronously. We do this after our SurfaceView is ready.
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, new OpenCVLoaderCallback(this));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        mSurfaceSize = new Rect(0, 0, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder)
    {
    }

    @Override
    public boolean onTouch(View view, MotionEvent event)
    {
        return mGestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event)
    {
        pickColorFromTap(event);
        return true;
    }

    private void pickColorFromTap(MotionEvent event)
    {
        // Calculate the point in the preview frame from the tap point on the screen
        Size previewSize = mWorker.getPreviewSize();
        double xFactor = previewSize.width / mSurfaceView.getWidth();
        double yFactor = previewSize.height / mSurfaceView.getHeight();
        mWorker.setSelectedPoint((int) (event.getX() * xFactor), (int) (event.getY() * yFactor));
    }

    @Override
    public boolean onDoubleTap(MotionEvent event)
    {
        mWorker.clearSelectedColor();
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event)
    {
        return false;
    }

    /**
     * This class will receive a callback once the OpenCV library is loaded.
     */
    private class OpenCVLoaderCallback extends BaseLoaderCallback
    {
        private Context mContext;

        public OpenCVLoaderCallback(Context context)
        {
            super(context);
            mContext = context;
        }

        @Override
        public void onManagerConnected(int status)
        {
            switch (status)
            {
                case LoaderCallbackInterface.SUCCESS:
                    ((OpenCVTutorial) mContext).initCameraView();
                    load();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }

    }

    private void load()
    {
        try
        {
            Mat result = new Mat();
            Mat source = Utils.loadResource(OpenCVTutorial.this, R.drawable.sourceimg);
            Mat template = Utils.loadResource(this, R.drawable.tampimg);


            int result_cols = source.cols() - template.cols() + 1;
            int result_rows = source.rows() - template.rows() + 1;
            result.create(result_rows, result_cols, CvType.CV_32FC1);


            Imgproc.matchTemplate(source, template, result, 0);
            Core.normalize(result, result, 0, 1, Core.NORM_MINMAX, -1, new Mat());

            double minVal;
            double maxVal;
            Point minLoc;
            Point maxLoc;
            Point matchLoc;

            Core.MinMaxLocResult mmResult = Core.minMaxLoc(result);

            matchLoc = mmResult.minLoc;

            Log.e("", "");

        }
        catch (Exception ex)
        {
            Log.e("", "");
        }
    }

    /**
     * This Handler callback is used to draw a bitmap to our SurfaceView.
     */
    private class UiCallback implements Handler.Callback
    {
        @Override
        public boolean handleMessage(Message message)
        {
            if (message.what == DRAW_RESULT_BITMAP)
            {
                Bitmap resultBitmap = (Bitmap) message.obj;
                Canvas canvas = null;
                try
                {
                    canvas = mSurfaceHolder.lockCanvas();
                    canvas.drawBitmap(resultBitmap, null, mSurfaceSize, null);
                    canvas.drawText(String.format("FPS: %.2f", mFpsResult), 35, 45, mFpsPaint);
                    String msg = "Single tap to select color. Double-tap to clear selection.";
                    float width = mFpsPaint.measureText(msg);
                    canvas.drawText(msg, mSurfaceView.getWidth() / 2 - width / 2, mSurfaceView.getHeight() - 30, mFpsPaint);
                }
                finally
                {
                    if (canvas != null)
                    {
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                    // Tell the worker that the bitmap is ready to be reused
                    mWorker.releaseResultBitmap(resultBitmap);
                }
            }
            return true;
        }
    }

    private class MyOnGestureListener extends GestureDetector.SimpleOnGestureListener
    {
        @Override
        public boolean onDown(MotionEvent event)
        {
            return true;
        }
    }

}
