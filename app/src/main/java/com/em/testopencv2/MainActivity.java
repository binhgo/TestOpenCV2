package com.em.testopencv2;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
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
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;


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
            //Mat displayImg = new Mat();


            Mat result = new Mat();
            Mat source = Utils.loadResource(MainActivity.this, R.drawable.girls);
            Mat template = Utils.loadResource(this, R.drawable.girltemp);

            Bitmap bmpTmp = Bitmap.createBitmap(template.cols(), template.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(template, bmpTmp);

            imageViewTemplate.setImageBitmap(bmpTmp);

            //source.copyTo(displayImg);


            int result_cols = source.cols() - template.cols() + 1;
            int result_rows = source.rows() - template.rows() + 1;
            result.create(result_rows, result_cols, CvType.CV_32FC1);


            // "Method: \n 0: SQDIFF \n 1: SQDIFF NORMED \n 2: TM CCORR \n 3: TM CCORR NORMED \n 4: TM COEFF \n 5: TM COEFF NORMED"
            Imgproc.matchTemplate(source, template, result, 0 /*SQDIFF*/);
            Core.normalize(result, result, 0, 1, Core.NORM_MINMAX, -1, new Mat());

            Point matchLoc;

            Core.MinMaxLocResult mmResult = Core.minMaxLoc(result);

            matchLoc = mmResult.minLoc;

            Core.rectangle(source, matchLoc, new Point(matchLoc.x + template.cols(), matchLoc.y + template.rows()), Scalar.all(0.0), 2, 8, 0);


            Bitmap bmp = Bitmap.createBitmap(source.cols(), source.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(source, bmp);

            imageView.setImageBitmap(bmp);

            Log.e("", "");

        }
        catch (Exception ex)
        {
            Log.e("", "");
        }
    }


    ImageView imageView;
    ImageView imageViewTemplate;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, new OpenCVLoaderCallback(this));

        imageView = (ImageView) findViewById(R.id.imageView);
        imageViewTemplate = (ImageView) findViewById(R.id.imageView1);

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
