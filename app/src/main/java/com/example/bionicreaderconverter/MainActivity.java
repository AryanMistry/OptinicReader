package com.example.bionicreaderconverter;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;


import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.Random;
import android.graphics.drawable.AnimationDrawable;
import android.widget.LinearLayout;

public class MainActivity extends AppCompatActivity {

    private MaterialButton inputImageBtn;
    private MaterialButton recognizeTextBtn;
    private MaterialButton copyToClipboardBtn;
    private MaterialButton clearBtn;
    private ShapeableImageView imageIv;
    private EditText recognizedTextEt;


    private static final String TAG = "MAIN_TAG";

    private Uri imageUri = null;

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int STORAGE_REQUEST_CODE = 101;

    private String[] cameraPermissions;
    private String[] storagePermissions;

    private ProgressDialog progressDialog;

    private TextRecognizer textRecognizer;
    private ActivityResultLauncher<String> galleryLauncher;

    private LinearLayout rootLayout;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.root_layout);

        AnimationDrawable animationDrawable = (AnimationDrawable) rootLayout.getBackground();
        animationDrawable.setEnterFadeDuration(10);
        animationDrawable.setExitFadeDuration(5000);
        animationDrawable.start();


        inputImageBtn = findViewById(R.id.inputImageBtn);
        recognizeTextBtn = findViewById(R.id.recognizeTextBtn);
        copyToClipboardBtn = findViewById(R.id.copyToClipboardBtn);
        clearBtn = findViewById(R.id.clearBtn);
        imageIv = findViewById(R.id.imageIv);
        recognizedTextEt = findViewById(R.id.recognizedTextEt);

        cameraPermissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Please Wait");
        progressDialog.setCanceledOnTouchOutside(false);

        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        copyToClipboardBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyTextToClipboard(recognizedTextEt.getText().toString());
            }
        });
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearAllData();
            }
        });

        galleryLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                imageUri = uri;
                imageIv.setImageURI(imageUri);
            } else {
                Toast.makeText(MainActivity.this, "Error selecting image", Toast.LENGTH_SHORT).show();
            }
        });

        inputImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInputImageDialogue();
            }
        });

        recognizeTextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (imageUri == null){
                    Toast.makeText(MainActivity.this, "Pick an image first", Toast.LENGTH_SHORT).show();
                }
                else{
                    recognizeTextFromImage();
                }
            }
        });

    }

    private Bitmap getDownsampledBitmap(Uri imageUri, int targetWidth, int targetHeight) {
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri), null, options);

            options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri), null, options);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }


    private void copyTextToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Recognized Text", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(MainActivity.this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "Failed to copy text", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAllData() {
        recognizedTextEt.setText("");

        // Clear the displayed image
        imageIv.setImageResource(0);

        Toast.makeText(MainActivity.this, "Data cleared", Toast.LENGTH_SHORT).show();
    }

    private String applyBionicReading(String recognizedText) {
        // Apply the bionic reading algorithm
        // Split the recognized text into words or tokens
        String[] words = recognizedText.split(" ");

        // Initialize a StringBuilder to build the formatted text
        StringBuilder formattedText = new StringBuilder();

        // Create a Random object to generate random numbers
        Random rand = new Random();

        // Apply the algorithm: Randomly bold 1-3 starting characters of each word
        for (String word : words) {
            int boldCharsCount;
            if (word.length() <= 3) {
                boldCharsCount = 1; // Bold only the first character for words with 3 or fewer characters
            } else {
                boldCharsCount = 1 + rand.nextInt(3); // Randomly choose between 1 to 3 for words with more than 3 characters
            }
            String formattedWord = "<b>" + word.substring(0, boldCharsCount) + "</b>" + word.substring(boldCharsCount) + " ";
            formattedText.append(formattedWord);
        }

        return formattedText.toString();
    }


    private void recognizeTextFromImage() {


        try {
            InputImage inputImage = InputImage.fromFilePath(this, imageUri);

            progressDialog.setMessage("Recognizing text...");

            Task<Text> textTaskResult = textRecognizer.process(inputImage)
                    .addOnSuccessListener(new OnSuccessListener<Text>() {
                        @RequiresApi(api = Build.VERSION_CODES.N)
                        @Override
                        public void onSuccess(Text text) {

                            progressDialog.dismiss();
                            String recognizedText = text.getText();
                            String bionicText = applyBionicReading(recognizedText);
                            recognizedTextEt.setText(Html.fromHtml(bionicText, Html.FROM_HTML_MODE_COMPACT));
                            recognizedTextEt.setTextColor(getResources().getColor(R.color.black)); // Set text color
                            recognizedTextEt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18); // Set text size
                            recognizedTextEt.setLineSpacing(10f, 1.2f); // Adjust line spacing
                            recognizedTextEt.setPadding(16, 16, 16, 16); // Apply padding
                            recognizedTextEt.setBackgroundColor(getResources().getColor(R.color.white)); // Set background color
                            recognizedTextEt.setTextAlignment(View.TEXT_ALIGNMENT_CENTER); // Align text center

                            clearBtn.setVisibility(View.VISIBLE);
                            copyToClipboardBtn.setVisibility(View.VISIBLE);

                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {

                            progressDialog.dismiss();
                            Log.e(TAG,"onFailure: ", e);
                            Toast.makeText(MainActivity.this,"Failed recognizing text due to "+e.getMessage(), Toast.LENGTH_SHORT).show();


                        }
                    });
        } catch (Exception e) {
            progressDialog.dismiss();
            Log.e(TAG, "recognizeTextFromImage: " , e);
            Toast.makeText(this, "Failed preparing image due to "+e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }


    private void showInputImageDialogue() {
        PopupMenu popupMenu = new PopupMenu(this, inputImageBtn);

        popupMenu.getMenu().add(Menu.NONE, 1, 1, "CAMERA");
        popupMenu.getMenu().add(Menu.NONE, 2, 2, "GALLERY");

        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {

                int id = menuItem.getItemId();
                if (id==1){

                    Log.d(TAG, "onMenuItemClick: Camera Clicked...");

                    if (checkCameraPermissions()){
                        pickImageCamera();
                    }
                    else{
                        requestCameraPermissions();
                    }
                }
                else if (id==2){
                    Log.d(TAG,"onMenuItemClick: Gallery opened...");
                    if (checkStoragePermission()){
                        pickImageGallery();
                    }
                    else{
                        requestStoragePermissions();
                    }
                }
                return true;
            }
        });
    }


    private void pickImageGallery() {
        galleryLauncher.launch("image/*");
    }

    private ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK){
                        Intent data = result.getData();
                        imageUri = result.getData().getData();
                        Bitmap downsampledBitmap = getDownsampledBitmap(imageUri, imageIv.getWidth(), imageIv.getHeight());
                        if (downsampledBitmap != null) {
                            imageIv.setImageBitmap(downsampledBitmap);
                        } else {
                            Toast.makeText(MainActivity.this, "Error processing image", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else{
                        Toast.makeText(MainActivity.this, "Cancelled...", Toast.LENGTH_SHORT).show();
                    }

                }
            }

    );

    private void pickImageCamera(){

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Sample Title");
        values.put(MediaStore.Images.Media.DESCRIPTION,"Sample Description");

        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraActivityResultLauncher.launch(intent);

    }

    private ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK){
                        Bitmap downsampledBitmap = getDownsampledBitmap(imageUri, imageIv.getWidth(), imageIv.getHeight());
                        if (downsampledBitmap != null) {
                            imageIv.setImageBitmap(downsampledBitmap);
                        } else {
                            Toast.makeText(MainActivity.this, "Error processing image", Toast.LENGTH_SHORT).show();
                        }
                    }
                    else{

                        Toast.makeText(MainActivity.this, "Cancelled...", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private boolean checkStoragePermission(){

        boolean result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);

        return result;
    }

    private void requestStoragePermissions(){
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQUEST_CODE);
    }

    private boolean checkCameraPermissions(){

        boolean cameraResult = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED);
        boolean storageResult = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);

        return cameraResult && storageResult;
    }

    private void requestCameraPermissions(){
        ActivityCompat.requestPermissions(this, cameraPermissions, CAMERA_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode){
            case CAMERA_REQUEST_CODE:{

                if (grantResults.length>0){
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if (cameraAccepted && storageAccepted){
                        pickImageCamera();
                    }
                    else {
                        pickImageCamera();

                    }
                }
                else {
                    Toast.makeText(this, "Cancelled...", Toast.LENGTH_SHORT).show();
                }
            }
            break;
            case STORAGE_REQUEST_CODE:{

                if (grantResults.length>0){

                    boolean storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if (storageAccepted){
                        pickImageGallery();
                    }
                    else{
                        pickImageGallery();
                    }
                }
            }
            break;
        }
    }
}