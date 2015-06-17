package com.em.testopencv2;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MainActivity extends ActionBarActivity
{

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
                    //templateMatching();
                    detectCircle();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }

    }

    private void remapImage()
    {
        
    }

    private void detectCircle()
    {
        try
        {

            // variable
            Mat source;
            Mat source_grey = new Mat();


            // to store circle that the function found
            MatOfPoint3f matOfPoint3f = new MatOfPoint3f();

            // load image
            source = Utils.loadResource(MainActivity.this, R.drawable.circle);

            // convert image to gray
            Imgproc.cvtColor(source, source_grey, Imgproc.COLOR_BGR2GRAY);


            // blur the image for better result
            Imgproc.GaussianBlur(source_grey, source_grey, new Size(9, 9), 2, 2);


            // find circle
            Imgproc.HoughCircles(source_grey, matOfPoint3f, Imgproc.CV_HOUGH_GRADIENT, 3, source_grey.rows() / 8, 200, 100, 0, 0);


            // convert mat 3 point to list
            List<Point3> point3List = new ArrayList<>();
            point3List = matOfPoint3f.toList();


            // loop and draw circle that are found
            for (int i = 0; i < point3List.size(); i++)
            {

                Point center = new Point(point3List.get(i).x, point3List.get(i).y);

                int radius = (int) point3List.get(i).z;

                Scalar scalar = new Scalar(96, 128, 15);
                Core.circle(source_grey, center, 3, scalar, -1, 8, 0);

                Core.circle(source_grey, center, radius, scalar, 5, 8, 0);

                // This si code C++ sample
               /* Point center (cvRound(circles[i][0]), cvRound(circles[i][1]));
                int radius = cvRound(circles[i][2]);
                // circle center
                circle(src, center, 3, Scalar(0, 255, 0), -1, 8, 0);
                // circle outline
                circle(src, center, radius, Scalar(0, 0, 255), 3, 8, 0);*/

            }


            // convert to bitmap and display
            Bitmap bmpTmp = Bitmap.createBitmap(source_grey.cols(), source_grey.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(source_grey, bmpTmp);

            imageViewOriginal.setImageBitmap(bmpTmp);

        }
        catch (Exception ex)
        {

        }
    }

    private void templateMatching()
    {
        try
        {
            // set 1st image for view purpose
            imageViewOriginal.setImageResource(R.drawable.girls);

            // create new MAT. Mat is same as Bitmap in android
            Mat displayImg = new Mat();
            Mat displayImgBoder = new Mat();
            Mat result = new Mat();

            // load image from drawable
            Mat source = Utils.loadResource(MainActivity.this, R.drawable.girls, Highgui.CV_LOAD_IMAGE_COLOR);
            Imgproc.cvtColor(source, displayImg, Imgproc.COLOR_RGB2BGRA);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String currentDateandTime = sdf.format(new Date());
            String fileName = Environment.getExternalStorageDirectory().getPath() +
                    "/sample_picture_" + currentDateandTime + ".jpg";

            // save image to sd card
            Highgui.imwrite(fileName, source);


            // load template image for comparing
            Mat template = Utils.loadResource(this, R.drawable.girltemp, Highgui.CV_LOAD_IMAGE_COLOR);
            // convert to get the right color of image
            Mat templateDisplay = new Mat();
            Mat templateDisplayBorder = new Mat();
            Imgproc.cvtColor(template, templateDisplay, Imgproc.COLOR_RGB2BGRA);
            // convert to get the right color of image


            // convert Mat to bitmap for display to se
            Bitmap bmpTmp = Bitmap.createBitmap(templateDisplay.cols(), templateDisplay.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(templateDisplay, bmpTmp);
            imageViewTemplate.setImageBitmap(bmpTmp);


            // create emplty Mat result for store result
            int result_cols = source.cols() - template.cols() + 1;
            int result_rows = source.rows() - template.rows() + 1;
            result.create(result_rows, result_cols, CvType.CV_32FC1);


            // compare source and template image
            // "Method: \n 0: SQDIFF \n 1: SQDIFF NORMED \n 2: TM CCORR \n 3: TM CCORR NORMED \n 4: TM COEFF \n 5: TM COEFF NORMED"
            Imgproc.matchTemplate(source, template, result, 0 /*SQDIFF*/);
            Core.normalize(result, result, 0, 1, Core.NORM_MINMAX, -1, new Mat());


            Point matchLoc;

            // matched position
            Core.MinMaxLocResult mmResult = Core.minMaxLoc(result);


            // min loc is the matched point
            matchLoc = mmResult.minLoc;


            // scalar is color
            Scalar scalar = new Scalar(0, 0, 255);


            Core.rectangle(displayImg, matchLoc, new Point(matchLoc.x + template.cols(), matchLoc.y + template.rows()), scalar, 3, 8, 0);


            // draw boder for an image
            /*int top = (int) (0.05 * displayImg.rows());

            Scalar scalar1 = new Scalar(96, 128, 15);
            Imgproc.copyMakeBorder(displayImg, displayImgBoder, top, top, top, top, Imgproc.BORDER_CONSTANT, scalar1);*/


            Bitmap bmp = Bitmap.createBitmap(displayImgBoder.cols(), displayImgBoder.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(displayImgBoder, bmp);

            imageView.setImageBitmap(bmp);

        }
        catch (Exception ex)
        {
            Log.e("", "");
        }
    }


    ImageView imageView;
    ImageView imageViewTemplate;
    ImageView imageViewOriginal;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, new OpenCVLoaderCallback(this));

        imageView = (ImageView) findViewById(R.id.imageView);
        imageViewTemplate = (ImageView) findViewById(R.id.imageView1);
        imageViewOriginal = (ImageView) findViewById(R.id.imageOgri);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
