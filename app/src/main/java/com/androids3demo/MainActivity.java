package com.androids3demo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.s3.transfermanager.TransferManager;
import com.amazonaws.mobileconnectors.s3.transfermanager.Upload;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;

import java.io.File;
import java.util.List;


public class MainActivity extends Activity {
    ImageView image;
    String imageFolder = "", imageName = "";
    Button selectButton, uploadButton;
    Context context;
    ProgressDialog progressDialog;
    Spinner buckets;
    ArrayAdapter<String> bucketAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = this;
        image = (ImageView)findViewById(R.id.imageView);
        selectButton = (Button)findViewById(R.id.selectButton);
        uploadButton = (Button)findViewById(R.id.uploadButton);
        buckets = (Spinner) findViewById(R.id.bucketSpinner);

        //Initializing the progressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Uploading file!");
        progressDialog.setIndeterminate(true);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);

        //Initializing the Adapter which will populate the dropdown list for bucket names
        bucketAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        bucketAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        bucketAdapter.add("Create New Bucket");

        buckets.setEnabled(false);

        /*
         * OnItemSelectedListener for spinner. Every time "Create New Button" option is selected
         * the newBucketLayout is shown.
         */
        buckets.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String)buckets.getItemAtPosition(position);
                LinearLayout newBucketLayout = ((LinearLayout)findViewById(R.id.newBucketLayout));
                if(selected.equalsIgnoreCase("Create New Bucket")){
                    newBucketLayout.setVisibility(LinearLayout.VISIBLE);
                }
                else{
                    newBucketLayout.setVisibility(LinearLayout.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    //OnClick method which handles onClick for all the buttons
    public void onClick(View v){
        Button button = (Button)v;
        try{
            switch(button.getId()){

                //If select button is pressed the selectImage() method is called
                case R.id.selectButton:
                    selectImage();
                    break;

                /*
                 * If upload button is pressed the selected bucket name is retrieved. If the user
                 * wants to create new bucket then they must enter some text in the text box.
                 */
                case R.id.uploadButton:
                    String bucket = (String)buckets.getSelectedItem();
                    if(bucket.equalsIgnoreCase("Create New Bucket")){
                        bucket = ((TextView)findViewById(R.id.newBucketEdit)).getText().toString();

                        if(bucket.trim().isEmpty() || bucket.trim().length() <= 6 || bucket.trim().contains(" ")){
                            Toast.makeText(this, "Invalid bucket name", Toast.LENGTH_LONG).show();
                            return;
                        }
                        new S3UploadTask().execute(bucket, "new");
                    }else
                        new S3UploadTask().execute(bucket);
                    break;
                default:
                    Toast.makeText(this, "OnClick not implemented for this button", Toast.LENGTH_LONG).show();
                    break;
            }
        }
        catch(Exception e){
            Toast.makeText(this, "Error in onView : " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    /*
     * This method is method is called when the select image button is pressed. It starts the
     * gallery activity for result. Which means once the activity being started is finished it will
     * return back some sort of data back to the current activity.
     */
    private void selectImage(){
        try{
            Intent intent = new Intent(Intent.ACTION_PICK,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 0);
        }catch(Exception e){
            Toast.makeText(this, "Error in selectImage : " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    /*
     * Method which is run when the control returns back to this activity. Once the image is selected
     * from gallery the intent data is returned back to this method.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try{
            if (resultCode == RESULT_OK) {
                Uri targetUri = data.getData();

                String imagePath = getRealPathFromURI(this, targetUri);

                //separate image name and image folder
                imageName = imagePath.substring(imagePath.lastIndexOf("/")+1);
                imageFolder = imagePath.substring(0, imagePath.lastIndexOf("/"));

                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(targetUri));
                image.setImageBitmap(bitmap);
                new PopulateBucketListTask().execute();
            }
        }catch(Exception e) {
            Toast.makeText(this, "Error in onActivityResult : " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    //Method to convert MediaStore Uri to AbsolutePath for file
    // Method Source - Post 1 : http://stackoverflow.com/questions/3401579/get-filename-and-path-from-uri-from-mediastore
    public String getRealPathFromURI(Context context, Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(contentUri,  proj, null, null, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(column_index);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    //**********************************************************************************************
    /**
     * Asynctask for populating the list of buckets
     */
    public class PopulateBucketListTask extends AsyncTask<Void, Void, Void>{
        @Override
        protected Void doInBackground(Void... params) {
            try{
                //Create AWS S3 client
                AmazonS3Client s3client = new AmazonS3Client(AWSUtility.credentialsProvider);

                //Get the list of buckets
                List<Bucket> bucketLists = s3client.listBuckets();
                boolean flag = false;
                for(Bucket b : bucketLists)
                {
                    //Add all the bucket names to the adapter object
                    bucketAdapter.add(b.getName());
                }
            }catch(Exception e) {
                Log.i("Error DOINBACKGROUND : ", e.toString());
            }
            return null;
        }

        /*
         * Once all bucket names have been retrieved, notify that the dataset has been changed,
         * and set this adapter to the spinner object and then enable the spinner. Also enable the
         * uploadButton.
         */
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            bucketAdapter.notifyDataSetChanged();
            buckets.setAdapter(bucketAdapter);
            buckets.setEnabled(true);
            uploadButton.setEnabled(true);
        }
    }
    //**********************************************************************************************

    //**********************************************************************************************
    /**
     * Asynctask for uploading the selected image file
     */
    public class S3UploadTask extends AsyncTask<String, Double, String>{
        @Override
        protected String doInBackground(String... params) {
            try{
                File image = new File(imageFolder, imageName);
                AmazonS3Client s3client = new AmazonS3Client(AWSUtility.credentialsProvider);
                TransferManager transferManager = new TransferManager(s3client);

                String bucket = params[0];
                boolean isNewBucket = false;
                try{
                    String isnew = params[1];
                    if(isnew!=null && isnew.equalsIgnoreCase("new"))isNewBucket=true;
                }catch(Exception e){isNewBucket=true;}

                if(isNewBucket)
                    s3client.createBucket(bucket);

                Upload upload = transferManager.upload(bucket, imageName, image);

                while(!upload.isDone()){
                    publishProgress(upload.getProgress().getPercentTransferred());
                }
            }catch(Exception e) {
                Log.i("Error DOINBACKGROUND : ", e.toString());
                return e.getMessage();
            }
            return null;
        }

        //Show progress dialog when upload starts
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        //Show progress as file gets uploaded
        @Override
        protected void onProgressUpdate(Double... progress) {
            super.onProgressUpdate(progress);
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);
            progressDialog.setProgress(progress[0].intValue());
        }

        //Dismiss progress dialog once upload is completed. If any errors returned, toast them!
        // result : Errors returned from doInBackground
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            progressDialog.dismiss();
            if(result!=null)
            {
                Toast.makeText(context, "Error : "+result, Toast.LENGTH_LONG).show();
            }
            else{
                Toast.makeText(context, "Upload Successful!", Toast.LENGTH_LONG).show();
            }
        }
    }
    //**********************************************************************************************
}
