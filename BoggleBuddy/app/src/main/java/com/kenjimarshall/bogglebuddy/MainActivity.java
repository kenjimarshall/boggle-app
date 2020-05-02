package com.kenjimarshall.bogglebuddy;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.theartofdev.edmodo.cropper.CropImage;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity {


    // Codes for corresponding activity resolutions
    final int CAMERA_CAPTURE = 1024;
    final HashSet<String> VALID_CHARS = new HashSet<>(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n",
            "o", "p", "qu", "r", "u", "s", "t", "v", "w", "x", "y", "z"));

    final String[] VALID_CHARS_PRETTY = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L",
            "M", "N", "O", "P", "Qu", "U", "R", "S", "T", "W", "X", "Y", "Z"};

    private Button randomBtn, solveBtn;
    private ImageButton cameraBtn;

    private Uri mPhotoUri, mCroppedUri;

    private String ASSET_TESS_DIR = "tessdata";
    private String TESS_DATA = "/tessdata";
    private String DATA_PATH;


    private TessBaseAPI tessAPI;

    private ImageView testImage; // REMOVE FOR PRODUCTION

    private boolean OpenCVSetup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPermission(); // READ/WRITE to external storage

        testImage = findViewById(R.id.testImage); // REMOVE FOR PRODUCTION

        DATA_PATH = this.getExternalFilesDir(null) + "/Tess"; // need to store tesseract model file here

        randomBtn = (Button) findViewById(R.id.randomButton);
        solveBtn = (Button) findViewById(R.id.solveButton);
        cameraBtn = (ImageButton) findViewById(R.id.camera);


        // Randomize board
        randomBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                EditText[] tiles = getTiles();
                Random r = new Random();

                // Make random assignments for each tile
                for (EditText tile : tiles) {
                    int random_index = r.nextInt(VALID_CHARS_PRETTY.length);
                    tile.setText(VALID_CHARS_PRETTY[random_index]);
                }
            }
        });

        // Solve board
        solveBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                EditText[] tiles = getTiles();
                if (validateTiles(tiles)) {
                    ArrayList<String> symbols = new ArrayList<>();
                    for (EditText tile : tiles) {
                        symbols.add(tile.getText().toString());
                    }

                    Context context = v.getContext();
                    Boggle board = new Boggle(symbols, context);
                    HashMap<Integer, String[]> validWordsSorted = board.findWords();
                    ArrayList<SpannableString> solutionListing = new ArrayList<>();
                    for (Integer key : validWordsSorted.keySet()) {
                        String str = "";
                        for (String sol : validWordsSorted.get(key)) {
                            str += sol + " ";
                        }
                        SpannableString strSpannable = new SpannableString(str);
                        solutionListing.add(strSpannable);
                    }
                    updateSolutions(solutionListing);
                }
            }

        });


        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPhotoIntent();
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();

        // STATIC INITIALIZATION of OPEN CV
        if (! this.OpenCVSetup) {
            if (!OpenCVLoader.initDebug()) {
                // Then it goes to install the Open CV APK
                Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
            } else {
                this.OpenCVSetup = true; // We don't have to do this again.
                Log.d("OpenCV", "Open CV package found within library.");
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            }
        }



    }

    // Loading OpenCV
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i("Open CV", "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public void updateSolutions(ArrayList<SpannableString> solutionListing) {
        ArrayAdapter adapter = new ArrayAdapter<SpannableString>(this, R.layout.solution_list, solutionListing);
        ListView listView = (ListView) findViewById(R.id.solutionsListView);
        listView.setAdapter(adapter);
    }

    private EditText[] getTiles() {
        EditText[] tiles = {
                (EditText) findViewById(R.id.tile_0_0),
                (EditText) findViewById(R.id.tile_0_1),
                (EditText) findViewById(R.id.tile_0_2),
                (EditText) findViewById(R.id.tile_0_3),
                (EditText) findViewById(R.id.tile_1_0),
                (EditText) findViewById(R.id.tile_1_1),
                (EditText) findViewById(R.id.tile_1_2),
                (EditText) findViewById(R.id.tile_1_3),
                (EditText) findViewById(R.id.tile_2_0),
                (EditText) findViewById(R.id.tile_2_1),
                (EditText) findViewById(R.id.tile_2_2),
                (EditText) findViewById(R.id.tile_2_3),
                (EditText) findViewById(R.id.tile_3_0),
                (EditText) findViewById(R.id.tile_3_1),
                (EditText) findViewById(R.id.tile_3_2),
                (EditText) findViewById(R.id.tile_3_3)};

        return tiles;
    }

    private boolean validateTiles(EditText[] tiles) {
        boolean valid = true;

        for (EditText tile: tiles) {
            if (!VALID_CHARS.contains(tile.getText().toString().toLowerCase())) {
                valid = false;
                ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.InvalidEntry));
                ViewCompat.setBackgroundTintList(tile, colorStateList);
            }
            else {
                ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.colorPrimary));
                ViewCompat.setBackgroundTintList(tile, colorStateList);
            }
        }
        return valid;
    }

    // Permission to read and write to external storage.
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 120);
        }
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 121);
        }
    }

    private void sendPhotoIntent() {
        // Intent to take an image
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.d("Camera", "Failed to create Photo File");
                // Error occurred while creating the File
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                // File creation successful.
                this.mPhotoUri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".provider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, this.mPhotoUri);
                startActivityForResult(takePictureIntent, this.CAMERA_CAPTURE);

            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        return image;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == this.CAMERA_CAPTURE) { // Activity result from photograph
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(getApplicationContext(), "Camera Success!", Toast.LENGTH_SHORT).show();
                CropImage.activity(this.mPhotoUri)
                        .start(this);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "Camera capture canceled.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "Photograph failed.", Toast.LENGTH_SHORT).show();
            }
        }
        else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == Activity.RESULT_OK) {
                this.mCroppedUri = result.getUri();
                Toast.makeText(getApplicationContext(), "Crop Success!", Toast.LENGTH_SHORT).show();
                Mat img = convertBitmapToMat();
                Mat processedImg = preprocessImage(img);
                try {
                    ArrayList<Mat> letterContours = fitToGrid(processedImg);
                    Toast.makeText(this, "Board found! Estimating characters...", Toast.LENGTH_SHORT).show();
                    prepareTesseract();
                    ArrayList<String> symbols = getBoardCharacters(letterContours);
                    Log.d("Tesseract", "CHARACTERS: " + symbols.toString());
                } catch (BoardRecognitionError boardRecognitionError) {
                    Toast.makeText(this, "Couldn't find grid... :( Try again or enter manually.", Toast.LENGTH_LONG).show();
                    boardRecognitionError.printStackTrace();
                }
            }
            else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(getApplicationContext(), "Crop failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }



    private Mat convertBitmapToMat()  {
        InputStream ims = null;
        try {
            ims = getContentResolver().openInputStream(this.mCroppedUri);
        } catch (FileNotFoundException e) {
            Log.d("Photo", "FILE NOT FOUD");
        }
        Bitmap bitmap = BitmapFactory.decodeStream(ims);
        Mat img = new Mat();
        Utils.bitmapToMat(bitmap, img);
        return img;
    }

    private void downsizeMat(Mat mat, double w, double h) {
        double width = mat.size().width;
        double height = mat.size().height;
        double scaleFactor = 0.9;
        Mat matToResize = mat.clone();
        while (width > w || height > h) {
            Mat resizedMat = new Mat();
            Size newSize = new Size(width * 0.9, height * 0.9);
            Log.d("Open CV", matToResize.size().toString());
            Imgproc.resize(matToResize, resizedMat, newSize, 0, 0, Imgproc.INTER_CUBIC);
            matToResize = resizedMat.clone();
            width = matToResize.size().width;
            height = matToResize.size().height;
        }

    }

    private Mat preprocessImage(Mat img) {
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(img, img, 150, 255, Imgproc.THRESH_BINARY_INV);
        Mat kernelOne = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS,new Size(3, 3));
        Mat kernelTwo = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(5, 5));


        Imgproc.morphologyEx(img, img, Imgproc.MORPH_CLOSE, kernelOne, new Point(-1, -1), 2);

        return img;
    }

    private ArrayList<Mat> fitToGrid(Mat img) throws BoardRecognitionError {
        double imgHeight = img.size().height;
        double imgWidth = img.size().width;
        Log.d("Open CV", "Cropped image size: " + String.valueOf(imgHeight) + " x " + String.valueOf(imgWidth));

        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        // Find all contours on board
        Imgproc.findContours(img, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
        Log.d("Open CV", String.valueOf(contours.size()) + " contours found.");




        ArrayList<Mat> letterContours = new ArrayList<>();
        IntervalNode rootY = null;
        IntervalNode rootX = null;
        for (MatOfPoint contour: contours) {
            Rect contourRect = Imgproc.boundingRect(contour); // Bounding rectangle around contour
            double contourWidth = contourRect.width;
            double contourHeight = contourRect.height;
            double contourX = contourRect.x;
            double contourY = contourRect.y;



            // Boggle letters should take between 1/5 to 1/12 along any dimension of the board

            if (contourRect.area() < (imgHeight * imgWidth) * 1/450) { // small enough to capture I contours
                continue;
            }

            else if (contourRect.area() > (imgHeight * imgWidth * 1/60)){
                continue;
            }

            else if (contourWidth < imgWidth * 1 / 50 || contourHeight < imgHeight * 1/ 50) {
                continue;
            }

            else if (contourWidth < imgWidth * 1 / 15 && contourHeight < imgHeight * 1/15) {
                continue;
            }

            else if (contourX <= 5 || contourY <= 5 || contourX + contourWidth >= (imgWidth - 6) || contourY + contourHeight >= (imgHeight - 6)) {
                continue;
            }

            Mat letterContour = new Mat(img, contourRect).clone(); // contourRect is ROI of original MAT
            letterContours.add(letterContour); // Identified as a contour of appropriate size

            Log.d("Open CV", "Contour of size " + String.valueOf(contourHeight) + " x " + String.valueOf(contourWidth) + " ACCEPTED.");
            Log.d("Open CV", "Relative size by area of " + String.valueOf(contourRect.area() / (imgHeight * imgWidth)));


            Imgproc.rectangle(img, new Point(contourRect.x,contourRect.y), new Point(contourRect.x+contourRect.width,contourRect.y+contourRect.height), new Scalar(200), 15);




            IntervalNode<Pair> yInterval = new IntervalNode<>(contourY,
                    contourY + contourHeight, new Pair(contourX, letterContour));
            // BST of intervals organized by y-coordinate. Stores x-coordinate for grid formulation later.
            IntervalNode<Pair> xInterval = new IntervalNode<>(contourX,
                    contourX + contourWidth, new Pair(contourY, letterContour));
            // BST of intervals organized by x-coordinate

            if (rootY == null) {
                rootY = yInterval; // Initialize tree
            }
            else {
                rootY.add(yInterval);
            }

            if (rootX == null) {
                rootX = xInterval;
            }
            else {
                rootX.add(xInterval);
            }
        }

        Bitmap contourBitmap = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, contourBitmap);
        testImage.setImageBitmap(contourBitmap);

        Log.d("Open CV", String.valueOf(letterContours.size()) + " letter-sized contours found.");

        if (letterContours.size() == 0) { // No letters found.
            throw new BoardRecognitionError("Failed to localize Boggle board");
            // Protects against NullPointerException in merge.
        }
        ArrayList<IntervalNode<ArrayList<Pair>>> mergedY = rootY.merge();
        // Collapse to all exclusive intervals contained in tree.
        ArrayList<IntervalNode<ArrayList<Pair>>> mergedX = rootX.merge();

        boolean outliersExist = true;
        Log.d("Open CV", "Grid before pruning: " + String.valueOf(mergedY.size()) + ", " + String.valueOf(mergedX.size()));
        while (outliersExist) {

            boolean reconstructed = false;

            if (mergedY.size() == 0) {
                Log.d("Open CV", "No rows left.");
                throw new BoardRecognitionError("Failed to find board!");
            }
            for (int i = 0; i < mergedY.size(); i++) {
                IntervalNode<ArrayList<Pair>> y_interval = mergedY.get(i);
                Log.d("Open CV", String.valueOf(y_interval.data.size()) + " elements in row.");
                if (y_interval.data.size() == 1) {
                    Log.d("Open CV", "Outlier row removed. Reconstructing grid estimation...");
                    rootY.removeByData(y_interval.data.get(0));
                    rootX.removeByData(y_interval.data.get(0));
                    mergedY = rootY.merge();
                    mergedX = rootX.merge();
                    Log.d("Open CV", "Grid after reconstruction: " + String.valueOf(mergedY.size()) + ", " + String.valueOf(mergedX.size()));
                    reconstructed = true;
                    break;
                }
            }


            if (mergedX.size() == 0) {
                Log.d("Open CV", "No columns left.");
                throw new BoardRecognitionError("Failed to find board!");
            }

            for (int i = 0; i < mergedX.size(); i++) {
                IntervalNode<ArrayList<Pair>> x_interval = mergedX.get(i);
                Log.d("Open CV", String.valueOf(x_interval.data.size()) + " elements in column.");
                if (x_interval.data.size() == 1) { // outlier merged group with one element
                    Log.d("Open CV", "Outlier column removed. Reconstructing grid estimation...");
                    rootY.removeByData(x_interval.data.get(0));
                    rootX.removeByData(x_interval.data.get(0));
                    mergedY = rootY.merge();
                    mergedX = rootX.merge();
                    Log.d("Open CV", "Grid after reconstruction: " + String.valueOf(mergedY.size()) + ", " + String.valueOf(mergedX.size()));
                    reconstructed = true;
                    break;
                }
            }
            outliersExist = reconstructed;
        }

        ArrayList<Mat> orderedLetterContours = new ArrayList<>();

        Log.d("Open CV", "Pruned grid of size: " + String.valueOf(mergedY.size()) + ", " + String.valueOf(mergedX.size()));

        if (mergedY.size() != 4 || mergedX.size() != 4) { // Expectation of four rows and four columns
            throw new BoardRecognitionError("Failed to localize Boggle board.");
        }

        else {

            Log.d("Open CV", "Board localized. Fitting to grid...");
            Collections.sort(mergedY); // Sort by y-coordinate (4 rows)
            Collections.sort(mergedX); // Sort by x-coordiante (4 columns)
            Mat grid = Mat.zeros(new Size(4, 4), CvType.CV_8UC1);

            for (int i = 0; i < mergedY.size(); i++) { // Over each row
                Log.d("Open CV", "Fitting over row " + String.valueOf(i) + " starting at position " + String.valueOf(mergedY.get(i).start));
                IntervalNode row = mergedY.get(i);
                ArrayList<Pair> rowData = (ArrayList<Pair>) row.data; // Contains x-coordinate and contour pair
                Collections.sort(rowData); // Sort by x-coordinate (i.e gives contours in 1 row, from left to right)
                for (Pair rowDatum: rowData){
                    for (int j = 0; j < mergedX.size(); j++) { // Over each column
                        Log.d("Open CV", "Checking column " + String.valueOf(j));
                        IntervalNode col = mergedX.get(j);
                        if (col.overlapsPoint(rowDatum.pos)){ // Grid position found
                            Log.d("Open CV", "Element matched to column " + String.valueOf(j));
                            if (grid.get(i, j)[0] == 1) {
                                Log.d("Open CV", "Position already occupied. Ignoring.");
                            }
                            else {
                                Log.d("Open CV", "Populating grid position at " + String.valueOf(i) + " " + String.valueOf(j));
                                grid.put(i, j, 1);
                                orderedLetterContours.add(rowDatum.contour);
                            }
                            break;
                        }
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (grid.get(i, j)[0] == 0) {
                        Log.d("Open CV", "Grid position at " + String.valueOf(i) + ", " +
                                String.valueOf(j) + "unoccupied. Filling with blank.");
                        Mat blank = Mat.zeros(new Size(100, 100), CvType.CV_8UC1);
                        orderedLetterContours.add(i*4+j, blank); // unrecognized grid component
                    }
                }
            }
        }
        return orderedLetterContours;
    }

    private void prepareDirectory(String path) {

        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.d("Tesseract", "ERROR: Creation of directory " + path + " failed, check does Android Manifest have permission to write to external storage.");
            }
        } else {
            Log.d("Tesseract", "Created directory " + path);
        }
    }

    private void prepareTesseract() {
        Log.d("Tesseract", "Preparing directory at " + DATA_PATH + TESS_DATA);
        prepareDirectory(DATA_PATH + TESS_DATA);
        copyTessDataFiles();
    }

    private void copyTessDataFiles() {
        try {

            String fileList[] = getAssets().list(ASSET_TESS_DIR); // eng.traineddata stored in assets/TESS_DATA
            Log.d("Tesseract", "Files in assets" + TESS_DATA + ": " + fileList);

            for (String fileName : fileList) {

                Log.d("Tesseract", "Copying file " + fileName);

                // open file within the assets foldera and if it is not already there, copy it to the SD card
                String pathToDataFile = DATA_PATH + TESS_DATA + "/" + fileName; // external_storage/tessdata/eng.traineddata

                if (!(new File(pathToDataFile)).exists()) {

                    InputStream in = getAssets().open(ASSET_TESS_DIR + "/" + fileName);

                    OutputStream out = new FileOutputStream(pathToDataFile);

                    // Transfer bytes from in to out
                    byte[] buf = new byte[1024];
                    int len;

                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }

                    in.close();
                    out.close();

                    Log.d("Tesseract", "Copied " + fileName + " to SD card.");
                }
            }
        } catch (IOException e) {
            Log.e("Tesseract", "Unable to copy files to tessdata " + e.toString());
        }
    }

    private ArrayList<String> getBoardCharacters(ArrayList<Mat> letterContours) {
        ArrayList<String> symbols = new ArrayList<>();

        tessAPI = new TessBaseAPI();
        if (tessAPI == null) {
            Log.e("Tesseract", "TessBaseAPI failed ot initialize.");
        }
        else {
            tessAPI.init(DATA_PATH, "eng");
            tessAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR); // Individual characters
            tessAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHIJKLMNOPQWRSTUVWXYZ");

            Log.d("Tesseract", "Tesseract API loaded.");
            for (Mat letterContour : letterContours) {

                if (letterContour.width() < letterContour.height() * 1/3) {
                    Log.d("Tesseract", "Identified I geometrically!");
                    symbols.add("I");
                    continue;
                }

                Mat borderedLetterContour = new Mat();
                Core.copyMakeBorder(letterContour, borderedLetterContour, 200, 200, 200, 200, Core.BORDER_CONSTANT, new Scalar(0, 0, 0));
                Mat borderedLetterContourFlipped = borderedLetterContour.clone();
                Mat borderedLetterContourRotCW = borderedLetterContour.clone();
                Mat borderedLetterContourRotCCW = borderedLetterContour.clone();

                rotateMAT(borderedLetterContourFlipped, 3); // 180 deg
                rotateMAT(borderedLetterContourRotCW, 1); // 90 CW
                rotateMAT(borderedLetterContourRotCCW, 2); // 90 CCW


                Bitmap letterBmp = Bitmap.createBitmap(borderedLetterContour.cols(), borderedLetterContour.rows(),
                        Bitmap.Config.ARGB_8888);

                Bitmap letterBmpFlipped = Bitmap.createBitmap(borderedLetterContourFlipped.cols(),
                        borderedLetterContourFlipped.rows(), Bitmap.Config.ARGB_8888);

                Bitmap letterBmpCW = Bitmap.createBitmap(borderedLetterContourRotCW.cols(),
                        borderedLetterContourRotCW.rows(), Bitmap.Config.ARGB_8888);

                Bitmap letterBmpCCW = Bitmap.createBitmap(borderedLetterContourRotCCW.cols(),
                        borderedLetterContourRotCCW.rows(), Bitmap.Config.ARGB_8888);

                Utils.matToBitmap(borderedLetterContour, letterBmp);
                Utils.matToBitmap(borderedLetterContourFlipped, letterBmpFlipped);
                Utils.matToBitmap(borderedLetterContourRotCW, letterBmpCW);
                Utils.matToBitmap(borderedLetterContourRotCCW, letterBmpCCW);


                tessAPI.setImage(letterBmp);
                String predictedChar = tessAPI.getUTF8Text();
                int confidence = tessAPI.meanConfidence();

                tessAPI.setImage(letterBmpFlipped);
                String predictedCharFlipped = tessAPI.getUTF8Text();
                int confidenceFlipped = tessAPI.meanConfidence();

                tessAPI.setImage(letterBmpCW);
                String predictedCharCW = tessAPI.getUTF8Text();
                int confidenceCW = tessAPI.meanConfidence();

                tessAPI.setImage(letterBmpCCW);
                String predictedCharCCW = tessAPI.getUTF8Text();
                int confidenceCCW = tessAPI.meanConfidence();

                String finalPred;
                int finalPredConfidence;

                if (predictedChar.equals(" ") && predictedCharFlipped.equals(" ")) {
                    finalPred = ""; // Empty prediction
                    finalPredConfidence = 0;
                }
                else if (predictedChar.equals(" ")) {
                    finalPred = predictedCharFlipped;
                    finalPredConfidence = confidenceFlipped;
                }
                else if (predictedCharFlipped.equals(" ")) {
                    finalPred = predictedChar;
                    finalPredConfidence = confidence;
                }
                else {
                    if (confidence >= confidenceFlipped) {
                        finalPred = predictedChar;
                        finalPredConfidence = confidence;
                    }
                    else {
                        finalPred = predictedCharFlipped;
                        finalPredConfidence = confidenceFlipped;
                    }
                }

                String finalPredRot;
                int finalPredRotConfidence;

                if (predictedCharCW.equals(" ") && predictedCharCCW.equals(" ")) {
                    finalPredRot = ""; // Empty prediction
                    finalPredRotConfidence = 0;
                }
                else if (predictedCharCW.equals(" ")) {
                    finalPredRot = predictedCharCCW;
                    finalPredRotConfidence = confidenceCCW;

                }
                else if (predictedCharCCW.equals(" ")) {
                    finalPredRot = predictedCharCW;
                    finalPredRotConfidence = confidenceCW;
                }
                else {
                    if (confidenceCW >= confidenceCCW) {
                        finalPredRot = predictedCharCW;
                        finalPredRotConfidence = confidenceCW;

                    }
                    else {
                        finalPredRot = predictedCharCCW;
                        finalPredRotConfidence = confidenceCCW;
                    }
                }

                if (finalPred.equals("I")) { // I is detected geometrically instead. Confusion between R and I should be mitigated.
                    Log.d("Tesseract", "Predicted I is invalid. Using rotated prediction.");
                    finalPred = finalPredRot;
                }
                else if (!finalPredRot.equals("I") && finalPredConfidence < finalPredRotConfidence) {
                    finalPred = finalPredRot;
                }

                Log.d("Tesseract", "Prediction: " + predictedChar + " with confidence " + String.valueOf(confidence));
                Log.d("Tesseract", "Flipped Prediction: " + predictedCharFlipped + " with confidence " + String.valueOf(confidenceFlipped));
                Log.d("Tesseract", "Prediction (CW): " + predictedCharCW + " with confidence " + String.valueOf(confidenceCW));
                Log.d("Tesseract", "Prediction (CCW): " + predictedCharCCW + " with confidence " + String.valueOf(confidenceCCW));
                Log.d("Tesseract", "Final Prediction: " + finalPred);
                symbols.add(finalPred);
            }
        }

        EditText[] tiles = getTiles();

        for (int i = 0; i < tiles.length; i++) {
            if (symbols.get(i).equals("Q")) {
                tiles[i].setText("Qu");
            } else {
                tiles[i].setText(symbols.get(i));
            }
        }

        return symbols;
    }

    private void rotateMAT(Mat toRotate, int rotateCode) {
        // rotateCode: 1 -> 90 CW
        //             2 -> 90 CCW
        //             3 -> 180
        if (rotateCode == 1) {
            Core.transpose(toRotate, toRotate);
            Core.flip(toRotate, toRotate, 1); // flip on y-axis
        }
        else if (rotateCode == 2) {
            Core.transpose(toRotate, toRotate);
            Core.flip(toRotate, toRotate, 0); // x-axis
        }
        else if (rotateCode == 3) {
            Core.flip(toRotate, toRotate, -1); // both axes
        }
    }


    private class BoardRecognitionError extends Exception
    {
        public BoardRecognitionError(String s)
        {
            // Call constructor of parent Exception
            super(s);
        }
    }




}
