/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uic.sandeep.streetsmart.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.text.Spanned;
import android.text.SpannedString;
import android.util.Log;

import com.uic.sandeep.streetsmart.Constants;
import com.uic.sandeep.streetsmart.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Utility class that handles writing, reading and cleaning up files where we
 * log the activities that where detected by the activity detection service.
 */
public class LogManager {

    // Store a context for handling files
    private final Context mContext;

    // Store an object that can "print" to a file
    private PrintWriter mActivityWriter;

    // Store a File handle
    private HashMap<String, File> mLogFiles;

    // Store the shared preferences repository handle
    private SharedPreferences mPrefs;

    // Store the current file number and name
    private HashMap<String, Integer> mLogFileSequenceNumber;
    private String mFileName;

    // Store an sLogFileInstance of the log file
    private static LogManager sLogFileInstance = null;

    /**
     * Singleton that ensures that only one sLogFileInstance of the LogFile exists at any time
     *
     * @param context A Context for the current app
     */
    private LogManager(Context context) {

        // Get the context from the caller
        mContext = context;

        // initilize the fields
        mLogFiles=new HashMap<String, File>();
        mLogFileSequenceNumber=new HashMap<String, Integer>();
        
        // Open the shared preferences repository
        mPrefs = context.getSharedPreferences(Constants.SHARED_PREFERENCES,
                Context.MODE_PRIVATE);

        // for each type of log, initialize the file
        for(String logFileType: Constants.LOG_FILE_TYPE){
        	
        	// If this is a new log file type, set the file number to 1
            if (!mPrefs.contains(logFileType)) {
                mLogFileSequenceNumber.put(logFileType,  1);
            // Otherwise, get the last-used file number and increment it.
            } else {
                int fileNum = mPrefs.getInt(logFileType, 0);
                mLogFileSequenceNumber.put(logFileType,  mLogFileSequenceNumber.get(logFileType) + 1);
            }

            // Get a repository editor sLogFileInstance
            Editor editor = mPrefs.edit();

            // Put the file number in the repository
            editor.putInt(logFileType+Constants.PREFERENCE_KEY_TYPE[0], mLogFileSequenceNumber.get(logFileType));

            // Create a timestamp
            String dateString = new SimpleDateFormat("yyyy_MM_dd", Locale.US).format(new Date());

            // Create the file name by sprintf'ing its parts into the filename string.
            mFileName = context.getString(
                    R.string.log_filename, // a match pattern
                    logFileType+"_",
                    dateString,
                    mLogFileSequenceNumber.get(logFileType),
                    ".log");
            
            // Save the filename
            editor.putString(logFileType+Constants.PREFERENCE_KEY_TYPE[1], mFileName);
            editor.commit();

            // Commit the updates
            // Create the log file
            mLogFiles.put(logFileType, createLogFile(mFileName) );
        }
    }

    /**
     * Create an sLogFileInstance of log file, or return the current sLogFileInstance
     *
     * @param context A Context for the current app
     *
     * @return An sLogFileInstance of this class
     */
    public static LogManager getInstance(Context context) {

        if (sLogFileInstance == null) {
            sLogFileInstance = new LogManager(context);
        }
        return sLogFileInstance;
    }

    /**
     * Log a message to the log file
     */
    public void log(String message, String logFileType) {

        // Start a print writer for the log file
        initLogWriter(logFileType);

        // Print a log message
        mActivityWriter.println(message);

        // Flush buffers
        mActivityWriter.flush();
    }

    /**
     * Loads data from the log file.
     */
    public List<Spanned> loadLogFile() throws IOException {

        // Get a new List of spanned strings
        List<Spanned> content = new ArrayList<Spanned>();

        // If no log file exists yet, return the empty List
        if (!mLogFiles.get(Constants.LOG_FILE_TYPE[1]).exists()) {
            return content;
        }

        // Create a new buffered file reader based on the log file
        BufferedReader reader = new BufferedReader(new FileReader(mLogFiles.get(Constants.LOG_FILE_TYPE[1])));

        // Get a String instance to hold input from the log file
        String line;

        /*
         * Read until end-of-file from the log file, and store the input line as a
         * spanned string in the List
         */
        while ((line = reader.readLine()) != null) {
            content.add(new SpannedString(line));
        }

        // Close the file
        reader.close();

        // Return the data from the log file
        return content;
    }

    /**
     * Creates an object that writes human-readable lines of text to a file
     * @msgType: error_report/senor_log/
     */
    private void initLogWriter(String logFileType) {

        // Catch IO exceptions
        try {

            // If the writer is still open, close it
            if (mActivityWriter != null) {
                mActivityWriter.close();
            }

            // Create a new writer for the log file
            mActivityWriter = new PrintWriter(new FileWriter(mLogFiles.get(logFileType), true));
           
            

        // If an IO exception occurs, print a stack trace
        } catch (IOException e) {
        		System.out.println(logFileType+ mLogFiles.keySet());
                e.printStackTrace();
        }
    }

    /**
     * Delete log files
     */
    public boolean removeLogFiles() {
        // Start with the "all files removed" flag set to true
        boolean removed = true;

        // Iterate through all the files in the app's file directory
        //for (File file : mContext.getFilesDir().listFiles()) {
        for (File file : mContext.getExternalFilesDir(null).listFiles()) {
            // If unable to delete the file
            if (!file.delete()) {

                // Log the file's name
                Log.e(Constants.APP_NAME, file.getAbsolutePath() + " : " + file.getName());

                // Note that not all files were removed
                removed = false;
            }
        }

        // Return true if all files were removed, otherwise false
        return removed;
    }

    /**
     * Returns a new file object for the specified filename.
     *
     * @return A File for the given file name
     */
    private File createLogFile(String filename) {

        // Create a new file in the app's directory
        //File newFile = new File(mContext.getFilesDir(), filename);
    	File newFile = new File(mContext.getExternalFilesDir(null), filename);
        
        // Log the file name
        Log.d(Constants.APP_NAME, newFile.getAbsolutePath() + " : " + newFile.getName());

        // return the new file handle
        return newFile;

    }
}
