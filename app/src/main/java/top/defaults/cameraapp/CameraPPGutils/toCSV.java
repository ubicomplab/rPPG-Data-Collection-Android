package top.defaults.cameraapp.CameraPPGutils;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

// Save data to .csv file
class toCSV {
    // need to create a new folder and save the CSV file into it
    toCSV(Activity activity, String[] data, String fileName) throws IOException {
        String folderName = "Record";
        String baseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        String folderPath = baseDir + File.separator + folderName;
        String filePath = folderPath + File.separator + fileName;
        File folder = new File(folderPath);
        File file = new File(filePath);
        CSVWriter writer;
        boolean success = true;

        // Create Folder
        if(!folder.exists()) {
            success = folder.mkdirs();
        }
        if (success) {
            // Save File
            if(file.exists()&&!file.isDirectory())
            {
                FileWriter mFileWriter = new FileWriter(filePath, true);
                writer = new CSVWriter(mFileWriter);
            } else {
                writer = new CSVWriter(new FileWriter(filePath));
            }
            writer.writeNext(data);
            Log.println(Log.VERBOSE, "Save", filePath);
            writer.close();
        } else {
            // create new folder failed
            Log.println(Log.ERROR, "Save", folderPath + " folder not exist and make folder failed!");
            activity.runOnUiThread(() -> Toast.makeText(activity, "Error: Folder not exist and make folder failed!", Toast.LENGTH_SHORT).show());
        }
    }
}
