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
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.Features2d;
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
                    //findContours();
                    detectObject();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }

    }


    private void detectObject()
    {

        try
        {
            Mat img_object = Utils.loadResource(MainActivity.this, R.drawable.cardobj1, Highgui.CV_LOAD_IMAGE_GRAYSCALE);
            Mat img_scene = Utils.loadResource(MainActivity.this, R.drawable.cardscene, Highgui.CV_LOAD_IMAGE_GRAYSCALE);
            MatOfKeyPoint keypoints_object = new MatOfKeyPoint();
            MatOfKeyPoint keypoints_scene = new MatOfKeyPoint();


            //-- Step 1: Detect the keypoints using SURF Detector
            int minHessian = 400;
            FeatureDetector featureDetector = FeatureDetector.create(FeatureDetector.FAST);


            try
            {
                featureDetector.detect(img_scene, keypoints_scene);
                featureDetector.detect(img_object, keypoints_object);

            }
            catch (Exception ex)
            {
                Log.e("", "");
            }

            //-- Step 2: Calculate descriptors (feature vectors)
            DescriptorExtractor extractor = DescriptorExtractor.create(DescriptorExtractor.FREAK);

            Mat descriptors_object = new Mat();
            Mat descriptors_scene = new Mat();

            extractor.compute(img_object, keypoints_object, descriptors_object);
            extractor.compute(img_scene, keypoints_scene, descriptors_scene);

            //-- Step 3: Matching descriptor vectors using FLANN matcher
            DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_SL2);
            MatOfDMatch matches = new MatOfDMatch();
            matcher.match(descriptors_object, descriptors_scene, matches);

            double max_dist = 0;
            double min_dist = 100;

            //-- Quick calculation of max and min distances between keypoints
            for (int i = 0; i < descriptors_object.rows(); i++)
            {
                double dist = matches.toList().get(i).distance;
                if (dist < min_dist) min_dist = dist;
                if (dist > max_dist) max_dist = dist;
            }

            //-- Draw only "good" matches (i.e. whose distance is less than 3*min_dist )
            MatOfDMatch good_matches = new MatOfDMatch();

            for (int i = 0; i < descriptors_object.rows(); i++)
            {
                float distance = matches.toList().get(i).distance;

                float threeMinDist = (float) (3 * min_dist);

                if (matches.toList().get(i).distance < threeMinDist)
                {
                    good_matches.push_back(matches.col(i));
                }
            }

            Mat img_matches = new Mat();
            MatOfByte matOfByte = new MatOfByte();
            Features2d.drawMatches(img_object, keypoints_object, img_scene, keypoints_scene, matches, img_matches, Scalar.all(-1), Scalar.all(-1), matOfByte, Features2d.NOT_DRAW_SINGLE_POINTS);


            Point pointObj[] = new Point[matches.toList().size()];
            Point pointScene[] = new Point[matches.toList().size()];

            for (int i = 0; i < matches.toList().size(); i++)
            {
                //-- Get the keypoints from the good matches

                pointObj[i] = keypoints_object.toList().get(matches.toList().get(i).queryIdx).pt;
                pointScene[i] = keypoints_scene.toList().get(matches.toList().get(i).trainIdx).pt;

                // obj.push_back(keypoints_object.toList().get(matches.toList().get(i).queryIdx).pt);
                // scene.fromArray(keypoints_object.toList().get(matches.toList().get(i).trainIdx).pt);
            }

            MatOfPoint2f obj = new MatOfPoint2f();
            obj.fromArray(pointObj);

            MatOfPoint2f scene = new MatOfPoint2f(pointScene);


            Mat H = new Mat();

            try
            {

                H = Calib3d.findHomography(obj, scene, Calib3d.RANSAC, 5);
            }
            catch (Exception ex)
            {
                Log.e("", "");
            }


            MatOfPoint2f obj_corners = new MatOfPoint2f();
            Point pointObjConners[] = new Point[4];
            pointObjConners[0] = new Point(0, 0);
            pointObjConners[1] = new Point(img_object.cols(), 0);
            pointObjConners[2] = new Point(img_object.cols(), img_object.rows());
            pointObjConners[3] = new Point(0, img_object.rows());


            obj_corners.fromArray(pointObjConners);


            List<Point> pointList1 = obj_corners.toList();


            MatOfPoint2f scene_corners = new MatOfPoint2f();
            Core.perspectiveTransform(obj_corners, scene_corners, H);


            //-- Draw lines between the corners (the mapped object in the scene - image_2 )
            List<Point> pointList = scene_corners.toList();


            Point p0 = new Point(scene_corners.toList().get(0).x + img_object.cols(), scene_corners.toList().get(0).y + 0);
            Point p1 = new Point(scene_corners.toList().get(1).x + img_object.cols(), scene_corners.toList().get(1).y + 0);
            Point p2 = new Point(scene_corners.toList().get(2).x + img_object.cols(), scene_corners.toList().get(2).y + 0);
            Point p3 = new Point(scene_corners.toList().get(3).x + img_object.cols(), scene_corners.toList().get(3).y + 0);


            Scalar scalar = new Scalar(0, 255, 0);

            Core.line(img_matches, p0, p1, scalar, 4);
            Core.line(img_matches, p1, p2, scalar, 4);
            Core.line(img_matches, p2, p3, scalar, 4);
            Core.line(img_matches, p3, p0, scalar, 4);


            Bitmap bitmap = matToBitmap(img_matches);

            imageViewOriginal.setImageBitmap(bitmap);

        }
        catch (Exception ex)
        {
            Log.e("", "");

        }


    }

    private Bitmap matToBitmap(Mat mat)
    {
        // convert mat to bitmap for display
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);
        return bitmap;
    }


    private void findContours()
    {
        try
        {
            int thresh = 100;
            int max_thresh = 255;
            // variable
            Mat source;
            Mat source_grey = new Mat();


            // load image
            source = Utils.loadResource(MainActivity.this, R.drawable.kkc);

            // convert image to gray
            Imgproc.cvtColor(source, source_grey, Imgproc.COLOR_BGR2GRAY);


            // blur the image for better result
            Imgproc.GaussianBlur(source_grey, source_grey, new Size(3, 3), 2, 2);


            Mat canny_output = new Mat();
            List<MatOfPoint> matOfPointList = new ArrayList<>();
            Mat hierarchy = new Mat();

            Imgproc.Canny(source_grey, canny_output, thresh, thresh * 2, 3, false);
            Imgproc.findContours(canny_output, matOfPointList, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE, new Point(0, 0));


            Mat draw_mat = Mat.zeros(canny_output.size(), CvType.CV_8UC3);


            for (int i = 0; i < matOfPointList.size(); i++)
            {
                Scalar color = new Scalar(123, 0, 70);
                Imgproc.drawContours(draw_mat, matOfPointList, i, color, 2, 8, hierarchy, 0, new Point());

            }


            // convert mat to bitmap for display
            Bitmap bitmap = Bitmap.createBitmap(draw_mat.cols(), draw_mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(draw_mat, bitmap);

            imageViewOriginal.setImageBitmap(bitmap);

        }
        catch (Exception ex)
        {

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
