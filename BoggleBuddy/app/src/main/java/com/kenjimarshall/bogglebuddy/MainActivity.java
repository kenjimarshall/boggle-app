package com.kenjimarshall.bogglebuddy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.os.Bundle;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.EditText;

import android.widget.SearchView;
import android.widget.Toast;
import android.widget.TextView;

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
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Open CV
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

//Dependencies for Merriam Webster API
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import android.os.AsyncTask;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// Tesseract API
import com.googlecode.tesseract.android.TessBaseAPI;

//CropImage API
import com.theartofdev.edmodo.cropper.CropImage;


/**
 * Boggle Buddy
 *
 * Android application to serve as a companion to the Boggle board game.
 * Functionality:
 *      - Generate random boards
 *      - Use the device camera to take a photo of the board and recognize all characters using Open CV
 *        image processing and the Tesseract OCR engine
 *      - Given a valid grid, finds all possible words. Words can be tapped to query the Merriam-Webster
 *        Collegiate Dictionary API for their definition
 *      - Word validation using the North American Scrabble Players Association official 2018 Word List
 *
 * @author  Kenji Marshall
 * @version 1.0
 * @since   2020-05-04
 */


public class MainActivity extends AppCompatActivity {

    //region Attributes


    final int CAMERA_CAPTURE = 1024; // Code for activity resolution
    final int BOARD_SIZE = 4;

    final HashSet<String> VALID_CHARS = new HashSet<>(Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n",
            "o", "p", "qu", "r", "u", "s", "t", "v", "w", "x", "y", "z"));
    final String[] VALID_CHARS_PRETTY = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L",
            "M", "N", "O", "P", "Qu", "U", "R", "S", "T", "W", "X", "Y", "Z"}; // more handsome

    // Views the class interacts with
    private Button randomBtn, solveBtn, populateBtn, clearBtn;
    private ImageButton cameraBtn;
    private EditText easyEntry;
    private ListView solutionsListView;
    private TextView maxScore;
//    private ImageView testImage;

    // Working with camera capture
    private Uri mPhotoUri, mCroppedUri;


    private String ASSET_TESS_DIR = "tessdata"; // where tesseract training data is store in assets
    private String TESS_DATA = "/tessdata"; // folder in external storage to copy tesseract training data
    private String DATA_PATH; // path to external storage
    private String EXTERNAL_DIR;

    private TessBaseAPI tessAPI; // OCR engine
    private Boggle validator; // Used to validate words

    private boolean OpenCVSetup = false; // Set to true once OpenCV has been setup

    //endregion

    //region On Create

    /**
     * Initializes View attributes, sets listeners for all the buttons.
     *
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // Setting attributes

        validator = new Boggle(this); // Empty graph. Used only for word validator feature

        DATA_PATH = this.getExternalFilesDir(null) + "/Tess"; // where we store tesseract training file
        EXTERNAL_DIR = this.getExternalFilesDir(null).toString();
        Log.d("Main", EXTERNAL_DIR);

        randomBtn = findViewById(R.id.randomButton);
        solveBtn = findViewById(R.id.solveButton);
        populateBtn = findViewById(R.id.populateBtn);
        clearBtn = findViewById(R.id.clearBtn);
        maxScore = findViewById(R.id.maxScore);
        easyEntry = findViewById(R.id.easyEntry);
        cameraBtn = findViewById(R.id.camera);

//        testImage = findViewById(R.id.testImage);

        solutionsListView = findViewById(R.id.solutionsListView);




        // Populate Listener
        // Flexible parses entry into EasyEntry field. If valid, uses it to populate the grid.
        populateBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                String entry = easyEntry.getText().toString().trim();
                ArrayList<String> characters = new ArrayList<>(); // builds array of all valid characters it comes across

                // used to modify color tiles where entry was invalid
                ColorStateList colorStateListInvalid = ColorStateList.valueOf(getColor(R.color.invalidEntry));
                ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.colorPrimaryDark));

                boolean validEntry = true;

                for (int i = 0; i < entry.length(); i++) {

                    String character = String.valueOf(entry.charAt(i)).toLowerCase();
                    if (!Character.isLetter(character.charAt(0))) {
                        continue; // ignore
                    }

                    if (character.equals("q")) { // force q to qu
                        // avoids IndexOutOfBounds
                        if (i != entry.length() - 1 && String.valueOf(entry.charAt(i+1)).toLowerCase().equals("u")) {
                            if (entry.length() == BOARD_SIZE * BOARD_SIZE) { // then u is likely a separate character
                                characters.add(character.toUpperCase().concat("u"));
                            }
                            else { // then assume u is coupled to q
                                characters.add(character.toUpperCase().concat("u"));
                                i++;
                            }
                        }
                        else {
                            characters.add(character.toUpperCase().concat("u"));
                        }
                    }
                    else {
                        if (VALID_CHARS.contains(character)) { // good
                            characters.add(character.toUpperCase());
                        }
                        else { // shouldn't be reached but included for safety
                            validEntry = false;
                            break;
                        }
                    }
                }

                if (characters.size() != BOARD_SIZE * BOARD_SIZE) {
                    validEntry = false;
                }

                if (validEntry) { // reset color and update the tiles
                    ViewCompat.setBackgroundTintList(easyEntry, colorStateList);
                    EditText[] tiles = getTiles();
                    for (int i = 0; i < characters.size(); i++) {
                        tiles[i].setText(characters.get(i));
                    }
                }

                else {
                    // invalid entry color
                    ViewCompat.setBackgroundTintList(easyEntry, colorStateListInvalid);
                }
            }
        });

        // Clear Listener
        // Clears all input fields, solutions, and max score. Resets color of tiles.

        clearBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                clearBoard();
            }
        });



        // Random Listener
        // Puts a random string from VALID_CHARS_PRETTY into each tile
        randomBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                clearFields();

                EditText[] tiles = getTiles();
                Random r = new Random();

                for (EditText tile : tiles) {
                    int random_index = r.nextInt(VALID_CHARS_PRETTY.length);
                    tile.setText(VALID_CHARS_PRETTY[random_index]);
                }
            }
        });

        // Solve Listener
        // Validates tile inputs and then finds and displays all possible solutions.
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
                    setMaxScore(board.getScore());

                    ArrayList<SpannableString> solutionListing = new ArrayList<>();
                    for (Integer key : validWordsSorted.keySet()) {
                        StringBuilder str = new StringBuilder();
                        for (String sol : validWordsSorted.get(key)) {
                            str.append(sol).append(" "); // one space between each word
                        }
                        SpannableString strSpannable = new SpannableString(str.toString());
                        solutionListing.add(strSpannable);
                    }
                    updateSolutions(solutionListing);
                }
            }

        });


        // Camera listener
        // Starts process of take image, crop image, process iamge, apply OCR

        cameraBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                checkPermission();
                sendPhotoIntent();
            }
        });
    }


    /**
     * Check permissions to READ/WRITE to external storage
     */
//    private void checkPermission() {
//        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            Log.d("Main", "Missing read");
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
//                generateDialog("External Storage?", "The camera feature needs access to your external storage so it can find the photo after it's taken and store some data for the character recognition tool. Don't hesitate to reach out if you have any more questions.");
//            }
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 120);
//
//        }
//        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            Log.d("Main", "Missing write");
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
//                generateDialog("External Storage?", "The camera feature needs access to your external storage so it can find the photo after it's taken and store some data for the character recognition tool. Don't hesitate to reach out if you have any more questions.");
//            }
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 121);
//        }
//    }

    //endregion


    //region On Resume

    /**
     * Ensures that the OpenCV library is accessible.
     */
    @Override
    public void onResume()
    {
        super.onResume();

        // STATIC INITIALIZATION of OPEN CV
        if (! this.OpenCVSetup) {
            if (!OpenCVLoader.initDebug()) {
                Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
            } else {
                this.OpenCVSetup = true; // We don't have to do this until app is created again.
                Log.d("OpenCV", "Open CV package found within library.");
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            }
        }
    }

    //endregion

    //region Private Helper Methods


    private void clearFields() {

        EditText[] tiles = getTiles();
        ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.colorPrimaryDark));

        ViewCompat.setBackgroundTintList(easyEntry, colorStateList);
        easyEntry.setText("");

        for (EditText tile: tiles) {
            ViewCompat.setBackgroundTintList(tile, colorStateList);
            tile.setText("");
        }

    }

    public void deleteFiles(String path) {
        File file = new File(path);

        if (file.exists()) {
            String deleteCmd = "rm -r " + path;
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec(deleteCmd);
            } catch (IOException e) {

            }
        }

    }

    private void clearSol(){

        ArrayList<SpannableString> solutions = new ArrayList<>(Arrays.asList(
                new SpannableString(""),
                new SpannableString(""),
                new SpannableString(""),
                new SpannableString(""),
                new SpannableString(""),
                new SpannableString(""),
                new SpannableString(""),
                new SpannableString(""),
                new SpannableString("")));
        updateSolutions(solutions);

        resetScore();

    }

    private void clearBoard() {

        clearFields();
        clearSol();

    }

    /**
     * Get IDs of all of the Boggle tiles.

     * @return Array of tile IDs, left to right and top to bottom.
     */
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

    /**
     * Update the solutions displayed in the solution ListView

     * @param solutionListing ArrayList of SpannableStrings containing solutions of 3, 4,..,10 letters
     *                        as space-separated words.
     */

    private void updateSolutions(ArrayList<SpannableString> solutionListing) {

        for (int i = 0; i < solutionListing.size(); i++) {
            int wordLength = i + 3; // 3, 4, 5, ..., 10
            int space_between = 1; // space between words in each string
            int wordBlockLength = wordLength + space_between;

            SpannableString words = solutionListing.get(i);

            int numWords = words.length() / wordBlockLength;
            // recall that the last word in each string is also followed by a space to make it a complete block


            // iterate over each individual word in the string
            for (int j = 0; j < numWords; j++) {


                /**
                 * Action upon clicking any individual word in the String.
                 *
                 * Makes a query to the Merriam-Webster API which in turn generates a dialog with
                 * the definition (if it can find one)
                 */
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View textView) {
                        TextView wordList = (TextView) textView; // Parent view of the string

                        // Isolating the clicked span
                        Spanned wordListSpanned = (Spanned) wordList.getText();
                        int start = wordListSpanned.getSpanStart(this);
                        int end = wordListSpanned.getSpanEnd(this);
                        String word = wordListSpanned.subSequence(start, end).toString();
                        word = word.toLowerCase();

                        // API call
                        new CallbackTask().execute(makeMerriamQuery(word));
                    }
                };

                // Applying the clickableSpan action
                words.setSpan(clickableSpan, j * wordBlockLength, (j * wordBlockLength  + wordBlockLength -1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }


        // Update the values displayed in the ListView
        ArrayAdapter adapter = new ArrayAdapter<SpannableString>(this, R.layout.solution_list, solutionListing) {

            @NonNull
            @Override
            // Override to modify attributes of TextView within the ListView
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View listItemView = convertView;

                if(listItemView == null) {
                    listItemView = LayoutInflater.from(getContext()).inflate(
                            R.layout.solution_list, parent, false);
                }

                // Find the TextView in the solution_list.xml layout
                TextView labelTextView = (TextView) listItemView.findViewById(R.id.label);
                // Gets the corresponding value in the solution listing
                labelTextView.setText(getItem(position));

                // set attributes of TextView
                labelTextView.setMovementMethod(LinkMovementMethod.getInstance()); // so span links work
                labelTextView.setFocusable(false);
                labelTextView.setFocusableInTouchMode(false);
                return listItemView;

            }
        };

        solutionsListView.setAdapter(adapter);
    }


    /**
     *
     * @param tiles Array of IDs to all the tiles on the board
     * @return true if all tiles contain valid entries. false otherwise.
     */

    private boolean validateTiles(EditText[] tiles) {
        boolean valid = true;

        for (EditText tile: tiles) {
            if (tile.getText().toString().toLowerCase().equals("q")) { // special case
                tile.setText(tile.getText().toString().concat("u"));
            }
            else if (!VALID_CHARS.contains(tile.getText().toString().toLowerCase())) {
                valid = false;
                ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.invalidEntry));
                ViewCompat.setBackgroundTintList(tile, colorStateList);
            }
            else {
                ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.colorPrimaryDark));
                ViewCompat.setBackgroundTintList(tile, colorStateList);
            }
        }
        return valid;
    }

    /**
     * Update max score TextView with a specified score.
     * @param score score to display
     */

    private void setMaxScore(int score) {
        String scoreString = "Max Score: " + String.valueOf(score);
        maxScore.setText(scoreString);
    }

    /**
     * Clear score field
     */

    private void resetScore() {
        maxScore.setText("Max Score:");
    }

    /**
     * Generate and show an alert dialog with an "Ok" button.
     * @param title Title of dialog
     * @param message Body text of dialog
     */
    private void generateDialog(String title, CharSequence message) {
        // 1. Instantiate an <code><a href="/reference/android/app/AlertDialog.Builder.html">AlertDialog.Builder</a></code> with its constructor
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

        // 2. Chain together various setter methods to set the dialog characteristics
        builder.setMessage(message)
                .setTitle(title);

        builder.setPositiveButton("Ok", null);

        // 3. Get the <code><a href="/reference/android/app/AlertDialog.html">AlertDialog</a></code> from <code><a href="/reference/android/app/AlertDialog.Builder.html#create()">create()</a></code>
        AlertDialog dialog = builder.create();
        Log.d("Main", "Showing Dialog.");
        dialog.show();

    }

    /**
     *
     * Generates dialogs where message can have a clickable span.
     *
     * @param title title of dialog
     * @param message body of dialog
     */
    private void generateClickableDialog(String title, CharSequence message) {
        final AlertDialog d = new AlertDialog.Builder(this)
                .setPositiveButton("Ok", null)
                .setMessage(message)
                .setTitle(title)
                .create();

        d.show();

        // Make the textview clickable. Must be called after show()
        ((TextView)d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
    }

    //endregion


    //region Merriam-Webster API

    /**
     * Assemble URL as a string for making a Merriam-Webster API query
     *
     * @param word word to query
     * @return API url as a string
     */
    private String makeMerriamQuery(String word) {
        word = word.toLowerCase();
        return "https://dictionaryapi.com/api/v3/references/collegiate/json/" + word + "?" + "key=" + "e228a052-a672-438e-9c1d-bfa66d939ee5"+"|"+word;
    }


    /**
     * Make asynchronous network request to the Merriam-Webster API. Can't do network requests on main thread.
     */
    private class CallbackTask extends AsyncTask<String, Integer, String> {

        /**
         *
         * @param params URL as a string stored at param[0]
         * @return
         */
        @Override
        protected String doInBackground(String... params) {

            try {
                String sURL = params[0];
                String[] sURLSplitted = sURL.split("\\|");
                String word = sURLSplitted[1];
                URL url = new URL(sURLSplitted[0]);

                HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Accept", "application/json");

                // read the output from the server
                BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder stringBuilder = new StringBuilder();

                String line = null;
                while ((line = reader.readLine()) != null) {
                    stringBuilder.append(line + "\n");
                }

                return stringBuilder.toString()+"@@@"+word;

            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Couldn't reach dictionary.", Toast.LENGTH_SHORT).show();
                return "";
            }

        }

        /**
         * Parse JSON string from Merriam-Webster
         * @param result JSON string from request
         */
        @Override
        protected void onPostExecute(String result) {

            super.onPostExecute(result);

            if (result.equals("")){ // dictionary couldn't be reached.
                return;
            }

            Log.d("Merriam", result);
            String[] splitAtWord = result.split("@@@");
            Log.d("Merriam", splitAtWord.toString());
            String word = splitAtWord[1];
            String jsonString = splitAtWord[0];
            String message;
            String title = word.toUpperCase();

            try {
                Log.d("Merriam", "Trying to parse JSON");
                ArrayList<Map<String, Object>> retMap = new Gson().fromJson(
                        jsonString, new TypeToken<ArrayList<HashMap<String, Object>>>() {
                        }.getType() // Gives an ArrayList containing the JSON
                );

                Map<String, Object> JSON =  retMap.get(0);

                Map<String, Object> meta = (Map<String, Object>) JSON.get("meta");
                Log.d("Merriam", meta.toString());

//                String word = ((Map<String, String>) JSON.get("meta")).get("id");
//                title = word.split(":")[0].toUpperCase(); // word comes as APPLE:1

                String definitionText;

                if (JSON.containsKey("cxs")) { // Cognate Cross Reference
                    // Word is a less common spelling of another word.
                    // Parse the other word.

                    Map<String, Object> cxs = ((ArrayList<Map<String, Object>>) JSON.get("cxs")).get(0);
                    definitionText = "";
                    definitionText += cxs.get("cxl") + " ";
                    String presTense = ((ArrayList<Map<String, String>>) cxs.get("cxtis")).get(0).get("cxt");
                    definitionText += presTense;

                } else {
                    Log.d("Merriam", retMap.get(0).get("def").toString());

                    Map<String, Object> definitions = ((ArrayList<Map<String, Object>>) JSON.get("def")).get(0);
                    Log.d("Merriam", definitions.toString());


                    ArrayList<Object> senseOrPseq = (ArrayList<Object>) ((ArrayList<ArrayList<Object>>) definitions.get("sseq")).get(0).get(0);
                    Log.d("Merriam", senseOrPseq.toString());


                    if (((String) senseOrPseq.get(0)).equals("sense")) {
                        // Then there is NOT a sequence of sense objects. Also, we don't have binding substitutes.
                        Map<String, Object> sense = (Map<String, Object>) senseOrPseq.get(1);
                        Log.d("Merriam", sense.toString());

                        ArrayList<Object> textOrUns = ((ArrayList<ArrayList<Object>>) sense.get("dt")).get(0);
                        Log.d("Merriam", textOrUns.toString());
                        if (textOrUns.get(0).equals("text")) { // definition can be retrieved directly
                            definitionText = (String) textOrUns.get(1);
                        } else {
                            // Usage notes pattern
                            Log.d("Merriam", "UNS");
                            definitionText = ((ArrayList<ArrayList<ArrayList<String>>>) textOrUns.get(1)).get(0).get(0).get(1);
                        }
                        Log.d("Merriam", definitionText);
                    } else if (((String) senseOrPseq.get(0)).equals("bs")) {
                        // binding substitutes pattern
                        Map<String, Object> sense = ((Map<String, Map<String, Object>>) senseOrPseq.get(1)).get("sense");
                        Log.d("Merriam", sense.toString());
                        definitionText = ((ArrayList<ArrayList<String>>) sense.get("dt")).get(0).get(1);
                        Log.d("Merriam", definitionText);
                    }

                    else {
                        // We have an array of sense objects
                        Log.d("Merriam", "PSEQ");
                        ArrayList<Object> senseOrBs = ((ArrayList<ArrayList<Object>>)
                                senseOrPseq.get(1)).get(0);
                        Log.d("Merriam", senseOrBs.toString());
                        if (((String) senseOrBs.get(0)).equals("bs")) {
                            // binding subtitutes pattern
                            definitionText = ((Map<String, Map<String, ArrayList<ArrayList<String>>>>) senseOrBs.get(1)).get("sense").get("dt").get(0).get(1);
                        } else {
                            definitionText = ((Map<String, ArrayList<ArrayList<String>>>) senseOrBs.get(1)).get("dt").get(0).get(1);
                        }
                        Log.d("Merriam", definitionText);

                    }
                }

                definitionText = definitionText.split(": such as")[0];
                // some definitions come as ...: such as, with examples stored in other JSON fields
                // don't parse and display examples. just provide simple definition.
                definitionText = definitionText.split("\\{dx\\}")[0];
                // directional cross references list a bunch of other words as reference examples
                // after the main definition. Ignore those.

                Log.d("Merriam", definitionText);


                // Look for synonymous cross references and extract the reference words
                // If they exists, message contains ONLY the cross references.
                ArrayList<String> synCrossRefs = new ArrayList<String>();
                Matcher m = Pattern.compile("\\{(sx\\|[^}|]*\\|\\|[^}]*)\\}")
                        .matcher(definitionText);

                while (m.find()) {
                    synCrossRefs.add(m.group());
                }

                Log.d("Merriam", synCrossRefs.toString());

                if (synCrossRefs.size() > 0) {
                    message = "";
                    for (String crossRef : synCrossRefs) {
                        String[] refSplitByBar = crossRef.split("\\|");
                        Log.d("Merriam", refSplitByBar[0]);
                        message += refSplitByBar[1];
                        message += ", ";
                    }

                    message = message.trim().substring(0, message.length() - 2);
                    Log.d("Merriam", message);
                } else {
                    // no cross-references. parse out other tags and join text.


                    String[] definitionTextSplit = definitionText.split("\\{([^}]*)\\}");
                    Log.d("Merriam", definitionTextSplit.toString());
                    String definitionClean = TextUtils.join("", definitionTextSplit);
                    Log.d("Merriam", definitionClean);
                    message = definitionClean;
                }

                generateDialog(title, message);

            } catch (Exception e) {
                SpannableString clickableMessage = new SpannableString("Definition couldn't be found :( \n\nTry looking here");
                clickableMessage.setSpan(new URLSpan("https://www.merriam-webster.com/dictionary/" + word), 46, 50, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                generateClickableDialog(title, clickableMessage);
            }
        }
    }

    //endregion


    //region Action Menu Interaction


    /**
     * Inflate menu and set properties of search operation.
     * @param menu menu
     * @return true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);


        // Associate searchable configuration with the SearchView
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final MenuItem searchItem = menu.findItem(R.id.search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getComponentName()));

        searchView.setQueryHint(HtmlCompat.fromHtml("<small>" +
                getResources().getString(R.string.search_hint) + "</small>",
                HtmlCompat.FROM_HTML_MODE_LEGACY)); // set hint
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                searchView.setQuery("", false);
                searchItem.collapseActionView();

                query = query.trim().toLowerCase();
                if (validator.validateWord(query)) { // make API call
                    Toast.makeText(MainActivity.this, "Valid! Getting definition...", Toast.LENGTH_SHORT).show();
                    new CallbackTask().execute(makeMerriamQuery(query));
                }
                else {
                    generateDialog(query.toUpperCase(), "Invalid Word");
                }
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false; // do nothing
            }
        });
        return true;
    }

    /**
     * Set custom actions when help and about buttons are pressed
     * @param item menu item selected
     * @return true if handled here
     */

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.help: {
                // HTML formatting
                String message = "<br>" + getString(R.string.populate_help) + "<br><br>" + getString(R.string.search_help)
                        + "<br><br>"  + getString(R.string.camera_help) + "<br><br>"  + getString(R.string.solve_help) + "<br>";
                Spanned messageStyled = HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY);

                generateDialog(getString(R.string.help_dialog_title), messageStyled);

                return true;
            }


            case R.id.about: {

                generateDialog(getString(R.string.about_dialog_title),
                        getString(R.string.version) + "\n\n" + getString(R.string.author_credit) +
                        "\n\n" + getString(R.string.email));

                return true;
            }

            case R.id.faq: {

                ImageView image = new ImageView(this);
                image.setImageResource(R.drawable.ic_mw_logo);
                String faqMessage = getString(R.string.q1) + "<br>" + getString(R.string.a1) + "<br><br>" +
                        getString(R.string.q2) + "<br>" + getString(R.string.a2) + "<br><br>" +
                        getString(R.string.q3) + "<br>" + getString(R.string.a3) + "<br><br>" +
                        getString(R.string.q4) + "<br>" + getString(R.string.a4);
                Spanned faqMessageStyled = HtmlCompat.fromHtml(faqMessage, HtmlCompat.FROM_HTML_MODE_LEGACY);



                AlertDialog.Builder builder =
                        new AlertDialog.Builder(this).
                                setTitle(R.string.faq_dialog_title).
                                setMessage(faqMessageStyled).
                                setPositiveButton("OK", null).
                                setView(image);
                builder.create().show();
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //endregion


    //region Camera Usage, Image Processing, and OCR

    /**
     * Loading Open CV
     */
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


    /**
     * Generate intent to take an image, try to generate a file to store the image.
     */
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

    /**
     * Make image file to store camera capture
     * @return image file
     * @throws IOException failed to create file
     */

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        Log.d("Camera", storageDir.toString());
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        return image;
    }

    /**
     * Handle activity completions of crop or camera capture
     *
     * @param requestCode identifier of activity
     * @param resultCode identifier of activity success
     * @param data intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == this.CAMERA_CAPTURE) { // Activity result from photograph
            if (resultCode == Activity.RESULT_OK) {
                CropImage.activity(this.mPhotoUri) // crop it!
                        .start(this);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                // Capture cancelled
            } else {
                Toast.makeText(getApplicationContext(), "Photograph failed...", Toast.LENGTH_LONG).show();
            }
        }
        else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == Activity.RESULT_OK) {
                this.mCroppedUri = result.getUri();
                Mat img = null;
                try {
                    img = convertBitmapToMat();
                } catch (FileNotFoundException e) {
                    Toast.makeText(getApplicationContext(), "Couldn't find photo file", Toast.LENGTH_SHORT).show();
                }
                Mat processedImg = preprocessImage(img);
                deleteFiles(EXTERNAL_DIR); // delete the photo files

                try {
                    ArrayList<Mat> letterContours = fitToGrid(processedImg);
                    Toast.makeText(this, "Board found! Estimating characters...", Toast.LENGTH_SHORT).show();
                    prepareTesseract();

                    clearBoard();
                    ArrayList<String> symbols = getBoardCharacters(letterContours);
                    deleteFiles(EXTERNAL_DIR); // delete the tess files

                    Log.d("Tesseract", "CHARACTERS: " + symbols.toString());
                } catch (BoardRecognitionError boardRecognitionError) {
                    Toast.makeText(this, "Couldn't find grid... :( Try again or enter manually.", Toast.LENGTH_LONG).show();
                    boardRecognitionError.printStackTrace();
                }
            }
            else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(getApplicationContext(), "Crop failed...", Toast.LENGTH_SHORT).show();
            }
        }
    }


    /**
     * From an image URI, decode the stream and form a MAT
     * @return
     */
    private Mat convertBitmapToMat() throws FileNotFoundException {
        InputStream ims = null;
        ims = getContentResolver().openInputStream(this.mCroppedUri);
        Bitmap bitmap = BitmapFactory.decodeStream(ims);
        Mat img = new Mat();
        Utils.bitmapToMat(bitmap, img);
        return img;
    }

    /**
     * Downsize a Mat object to a specified size.
     *
     * @param mat mat to downsize
     * @param w highest allowed width (pixels)
     * @param h highest allowed height (pixels)
     */
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


    /**
     * Preprocess image to prepare for contour recognition. Expects tile letters to be dark on a
     * light background (as in the game)
     * @param img image to process
     * @return processed image
     */
    private Mat preprocessImage(Mat img) {
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(img, img, 180, 255, Imgproc.THRESH_BINARY_INV);
        Mat kernelOne = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS,new Size(3, 3));
//        Imgproc.erode(img, img, kernelOne, new Point(-1, -1), 1);
        Imgproc.morphologyEx(img, img, Imgproc.MORPH_CLOSE, kernelOne, new Point(-1, -1), 2);

        return img;
    }


    /**
     * Make a flexible attempt to extract a 4x4 grid structure from contours.
     *
     * @param img preprocessed image
     * @return BOARD_SIZE * BOARD_SIZE length array of contours from left to right, top to bottom.
     *         Whenever a grid position isn't identified, provides a blank image.
     *
     * @throws BoardRecognitionError grid structure can't be found.
     */

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

        // used to build BSTs based on y-coordinate of contour start, and x-coordinate respectively
        IntervalNode rootY = null;
        IntervalNode rootX = null;
        for (MatOfPoint contour: contours) {
            Rect contourRect = Imgproc.boundingRect(contour); // Bounding rectangle around contour
            double contourWidth = contourRect.width;
            double contourHeight = contourRect.height;
            double contourX = contourRect.x;
            double contourY = contourRect.y;




            if (contourRect.area() < (imgHeight * imgWidth) * 1/550) { // small enough to capture I contours
                continue;
            }

            else if (contourRect.area() > (imgHeight * imgWidth * 1/60)){ // big enough to capture all letters, but not complete tiles.
                continue;
            }

            else if (contourWidth < imgWidth * 1 / 50 || contourHeight < imgHeight * 1/ 50) { // extremely skinny contours are not okay
                continue;
            }

            else if (contourWidth < imgWidth * 1 / 15 && contourHeight < imgHeight * 1/15) { // both dimensions cannot be small
                continue;
            }

            else if (contourX <= 5 || contourY <= 5 || contourX + contourWidth >= (imgWidth - 6) || contourY + contourHeight >= (imgHeight - 6)) {
                continue; // contour not at very edge of image
            }

            Mat letterContour = new Mat(img, contourRect).clone(); // contourRect is ROI of original MAT
            letterContours.add(letterContour); // Identified as a contour of appropriate size

            Log.d("Open CV", "Contour of size " + contourHeight + " x " + contourWidth + " ACCEPTED.");
            Log.d("Open CV", "Relative size by area of " + contourRect.area() / (imgHeight * imgWidth));


            // draw rectangle on originla image (for testing)
            Imgproc.rectangle(img, new Point(contourRect.x,contourRect.y), new Point(contourRect.x+contourRect.width,
                    contourRect.y+contourRect.height), new Scalar(200), 15);



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

//        testImage.setImageBitmap(contourBitmap);


        Log.d("Open CV", letterContours.size() + " letter-sized contours found.");

        if (letterContours.size() == 0) { // No letters found.
            throw new BoardRecognitionError("Failed to localize Boggle board");
            // Protects against NullPointerException in merge.
        }

        // Collapse to all exclusive intervals contained in tree.
        ArrayList<IntervalNode<ArrayList<Pair>>> mergedY = rootY.merge();
        ArrayList<IntervalNode<ArrayList<Pair>>> mergedX = rootX.merge();

        boolean outliersExist = true;
        Log.d("Open CV", "Grid before pruning: " + mergedY.size() + ", " + mergedX.size());

        while (outliersExist) { // outliers are any contours which don't overlap with any other contours in at least one dimension

            outliersExist = false; //  assume good until proved wrong

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
                    rootX.removeByData(y_interval.data.get(0)); // remove outlier from both trees
                    mergedY = rootY.merge(); // reconstruct intervals
                    mergedX = rootX.merge();
                    Log.d("Open CV", "Grid after reconstruction: " + mergedY.size() + ", " + mergedX.size());
                    outliersExist = true; // we found an outlier
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
                    outliersExist = true;
                    break;
                }
            }
        }




        ArrayList<Mat> orderedLetterContours = new ArrayList<>();

        Log.d("Open CV", "Pruned grid of size: " + String.valueOf(mergedY.size()) + ", " + String.valueOf(mergedX.size()));

        if (mergedY.size() != 4 || mergedX.size() != 4) { // Expectation of four rows and four columns after pruning
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

    /**
     * Make a directory in external storage
     * @param path directory to create
     */

    private void prepareDirectory(String path) {

        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.d("Tesseract", "ERROR: Creation of directory " + path + " failed.");
            }
        } else {
            Log.d("Tesseract", "Created directory " + path);
        }
    }

    /**
     * Prepare Tesseract directory and move over training file.
     */
    private void prepareTesseract() {
        Log.d("Tesseract", "Preparing directory at " + DATA_PATH + TESS_DATA);
        prepareDirectory(DATA_PATH + TESS_DATA);
        copyTessDataFiles();
    }


    /**
     * Copy eng.traineddata file from assets to device storage
     */
    private void copyTessDataFiles() {
        try {

            String fileList[] = getAssets().list(ASSET_TESS_DIR); // eng.traineddata stored in assets/TESS_DATA
            Log.d("Tesseract", "Files in assets" + TESS_DATA + ": " + fileList);

            for (String fileName : fileList) {

                Log.d("Tesseract", "Copying file " + fileName);

                // open file within the assets folder and if it is not already there, copy it to the SD card
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
            Log.d("Tesseract", "Unable to copy files to tessdata " + e.toString());
        }
    }

    /**
     * Given an ordered list of the contour Mats (L to R, top to bottom), use OCR to try and
     * recognize each character. Try all four orientations and use prediction with highest confidence.
     *
     * @param letterContours ordered letter contours Mat objects
     * @return predictions from L to R, top to bottom
     */
    private ArrayList<String> getBoardCharacters(ArrayList<Mat> letterContours) {
        ArrayList<String> symbols = new ArrayList<>();

        tessAPI = new TessBaseAPI();
        if (tessAPI == null) {
            Log.e("Tesseract", "TessBaseAPI failed ot initialize.");
        }
        else {
            tessAPI.init(DATA_PATH, "eng");
            tessAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_CHAR); // Individual characters
            tessAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "ABCDEFGHJKLMNOPQWRSTUVWXYZ");
            // only single uppercase letters can be returned
            // NOTE: I is excluded as it's recognized geometrically

            Log.d("Tesseract", "Tesseract API loaded.");
            for (Mat letterContour : letterContours) {

                if (letterContour.width() < letterContour.height() * 1/2.5 || letterContour.height() < letterContour.width() * 1/2.5) {
                    // has trouble with I so pinpoint geometrically
                    Log.d("Tesseract", "Identified I geometrically!");
                    symbols.add("I");
                    continue;
                }

                double rotConfFactor;
                double confFactor;
                if (letterContour.width() < letterContour.height()) {
                    rotConfFactor = 1;
                    confFactor = 1.05; // usually the letter orientation has the longest side as the height
                    // inflate the confidence of those predictions
                }
                else {
                    rotConfFactor = 1.05;
                    confFactor = 1;
                }

                // provide black border around contour
                Mat borderedLetterContour = new Mat();
                Core.copyMakeBorder(letterContour, borderedLetterContour, 200, 200, 200, 200, Core.BORDER_CONSTANT, new Scalar(0, 0, 0));
                Mat borderedLetterContourFlipped = borderedLetterContour.clone();
                Mat borderedLetterContourRotCW = borderedLetterContour.clone();
                Mat borderedLetterContourRotCCW = borderedLetterContour.clone();

                // rotations to all other orientations
                rotateMAT(borderedLetterContourFlipped, 3); // 180 deg
                rotateMAT(borderedLetterContourRotCW, 1); // 90 CW
                rotateMAT(borderedLetterContourRotCCW, 2); // 90 CCW


                // convert Mats to Bitmaps
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



                // make four predictions with confidence
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



                // best prediction for upright orientations
                String finalPred;
                int finalPredConfidence;

                Log.d("Tesseract", predictedChar + "|" + predictedCharFlipped + "|" + predictedCharCW + "|" +  predictedCharCCW);

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

                // best prediction for sideways orientation
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

                Log.d("Tesseract", "Prediction: " + predictedChar + " with confidence " + String.valueOf(confidence));
                Log.d("Tesseract", "Flipped Prediction: " + predictedCharFlipped + " with confidence " + String.valueOf(confidenceFlipped));
                Log.d("Tesseract", "Prediction (CW): " + predictedCharCW + " with confidence " + String.valueOf(confidenceCW));
                Log.d("Tesseract", "Prediction (CCW): " + predictedCharCCW + " with confidence " + String.valueOf(confidenceCCW));


                // apply weighting to confidence
                finalPredConfidence = (int) Math.round(finalPredConfidence * confFactor);
                finalPredRotConfidence = (int) Math.round(finalPredRotConfidence * rotConfFactor);

                Log.d("Tesseract" ,"After rotation factor, confidence of: " + String.valueOf(finalPredConfidence) + " for " + finalPred);
                Log.d("Tesseract" , "After rotation factor, confidence of: " + String.valueOf(finalPredRotConfidence) + " for " + finalPredRot);



                if (finalPredConfidence < finalPredRotConfidence) {
                    finalPred = finalPredRot;
                }

                Log.d("Tesseract", "Final Prediction: " + finalPred);
                symbols.add(finalPred);
            }
        }

        EditText[] tiles = getTiles(); // update tiles

        ColorStateList colorStateListInvalid = ColorStateList.valueOf(getColor(R.color.invalidEntry));
        ColorStateList colorStateList = ColorStateList.valueOf(getColor(R.color.colorPrimaryDark));

        for (int i = 0; i < tiles.length; i++) {
            if (symbols.get(i).equals("Q")) {
                tiles[i].setText("Qu");
                ViewCompat.setBackgroundTintList(tiles[i], colorStateList);
            }
            else if (symbols.get(i).equals("")) {
                ViewCompat.setBackgroundTintList(tiles[i], colorStateListInvalid);
            }
            else {
                tiles[i].setText(symbols.get(i));
                ViewCompat.setBackgroundTintList(tiles[i], colorStateList);
            }
        }

        return symbols; // return symbols
    }

    /**
     * Efficient rotation of Mat 90, 180, or 270 degrees
     * @param toRotate Mat to rotate
     * @param rotateCode 1 if 90 CW, 2, if 90 CCW, 3 if 270.
     */
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


    /**
     * Failed to recognize 4x4 letter grid
     */

    private class BoardRecognitionError extends Exception
    {
        public BoardRecognitionError(String s)
        {
            // Call constructor of parent Exception
            super(s);
        }
    }

    //endregion


}
