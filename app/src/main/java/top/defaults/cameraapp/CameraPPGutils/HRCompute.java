package top.defaults.cameraapp.CameraPPGutils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.annotation.RequiresApi;
import android.view.TextureView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import top.defaults.cameraapp.PhotographerActivity;
import top.defaults.cameraapp.R;

public class HRCompute {
    private final Activity activity;
    private final Plot plot;
    private final CopyOnWriteArrayList<Long> valleys = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Measurement<Integer>> measurements = new CopyOnWriteArrayList<>();
    private CountDownTimer timer;

    private int detectedValleys = 0; // number of detected valleys
    private int ticksPassed = 0; // count of time passed
    private int minimum = 2147483647; // 2^31-1
    private int maximum = -2147483648; // -2^31

    private final int measurementInterval = 50; // time interval between two measurements (unit: millisecond)
    private final int clipLength = 0; // for the beginning, time to be clipped
    private final int measurementLength = 33000; // total measurement length time (including clipLength)
    private toCSV Save;

    public HRCompute(PhotographerActivity activity, TextureView TVPlot) {
        this.activity = activity;
        this.plot = new Plot(TVPlot); // for waveform plot
    }

    // Add one measurement and update
    private void add(int measurement) {
        Measurement<Integer> measurementWithDate = new Measurement<>(new Date(), measurement);

        measurements.add(measurementWithDate);
        // Update the minimum and maximum of measurement
        if (measurement < minimum) minimum = measurement;
        if (measurement > maximum) maximum = measurement;
    }

    // Calculate the rolling average and normalize the results (for plot, not for data save)
    private CopyOnWriteArrayList<Measurement<Float>> getStdValues() {
        CopyOnWriteArrayList<Measurement<Float>> stdValues = new CopyOnWriteArrayList<>();

        for (int i = 0; i < measurements.size(); i++) {
            // for every measurement value, calculate the sum of itself and the data before
            int sum = 0;
            int rollingAverageSize = 3;
            for (int rollingAverageCounter = 0; rollingAverageCounter < rollingAverageSize; rollingAverageCounter++) {
                sum += measurements.get(Math.max(0, i - rollingAverageCounter)).measurement;
            }
            // get the rolling average and do the Min-Max Normalization
            Measurement<Float> stdValue =
                    new Measurement<>(
                            measurements.get(i).timestamp,
                            ((float)sum / rollingAverageSize - minimum ) / (maximum - minimum));
            stdValues.add(stdValue);
        }

        return stdValues;
    }

    // Get last measurement stdValues (length: count)
    private CopyOnWriteArrayList<Measurement<Integer>> getLastStdValues(int count) {
        if (count < measurements.size()) {
            return  new CopyOnWriteArrayList<>(measurements.subList(measurements.size() - 1 - count, measurements.size() - 1));
        } else {
            return measurements;
        }
    }

    // Get last measurement's timestamp
    private Date getLastTimestamp() {
        return measurements.get(measurements.size() - 1).timestamp;
    }

    // Detect if there is a valley in the measurement sequence
    private boolean detectValley() {
        // Get last 14 stdValues
        final float valleyDetectionWindowSize = 14;
        CopyOnWriteArrayList<Measurement<Integer>> subList = getLastStdValues((int)valleyDetectionWindowSize);

        // not enough last measurements
        if (subList.size() < valleyDetectionWindowSize) {
            return false;
        } else {
            // Set a referenceValue
            Integer referenceValue = subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement;

            // only if all the measurements >= referenceValue, decide there is a valley
            for (Measurement<Integer> measurement : subList) {
                if (measurement.measurement < referenceValue) return false;
            }

            // Filter out consecutive measurements due to too high measurement rate
            return (!subList.get((int) Math.ceil(valleyDetectionWindowSize / 2)).measurement.equals(
                    subList.get((int) Math.ceil(valleyDetectionWindowSize / 2) - 1).measurement));
        }
    }

    // Get the amount of red on the picture
    // Detect local minimums, calculate pulse
    public void measurePulse(TextureView textureView, PhotographerActivity main, String fileName) {
        detectedValleys = 0;

        timer = new CountDownTimer(measurementLength, measurementInterval) {
//            TextView textView = activity.findViewById(R.id.TextView1);

            @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            @Override
            public void onTick(long millisUntilFinished) {
                // Skip the first measurements, which are broken by exposure metering
                if (clipLength > (++ticksPassed * measurementInterval)) return;

                // Get time stamps and get Bitmap values from the textureView
                Thread thread;
                thread = new Thread(() -> {
                    Bitmap currentBitmap = textureView.getBitmap();

                    int width = textureView.getWidth();
                    int height = textureView.getHeight();
                    int[] pixels = new int[width * height];

                    // Get pixels from the bitmap
                    // Starting at (x,y) = (0, 0), total rows: width, total columns: height
                    currentBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

                    // Get timestamp
                    // Create Calendar instance
                    Calendar calendar = Calendar.getInstance();
                    // Return current time in millis
                    long timeMilli = calendar.getTimeInMillis();

                    // Extract the red component
                    // https://developer.android.com/reference/android/graphics/Color.html#decoding
                    int red = 0;
                    for (int pixelIndex = 0; pixelIndex < width * height; pixelIndex++) {
                        int redValue = (pixels[pixelIndex] >> 16) & 0xff;
                        red += redValue;
                    }
                    add(red); // add one measurement

                    // Save red data to .csv file
                    // from left to right: timestamp, redData
                    String[] data = {String.valueOf(timeMilli), Integer.toString(red)};
                    try {
                        Save = new toCSV(activity, data, fileName);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    activity.runOnUiThread(() -> {
                        if (detectValley()) {
                            detectedValleys = detectedValleys + 1;
                            valleys.add(getLastTimestamp().getTime());

                            String currentValue = String.format(
                                    Locale.getDefault(),
                                    activity.getResources().getQuantityString(R.plurals.measurement_output, detectedValleys),
                                    // calculate valleys in one minute
                                    (valleys.size() == 1)
                                            ? (60f * (detectedValleys) / (Math.max(1, (measurementLength - millisUntilFinished - clipLength) / 1000f)))
                                            // the number of intervals is the number of detectedValleys - 1
                                            : ((60f * (detectedValleys - 1) / (Math.max(1, (valleys.get(valleys.size() - 1) - valleys.get(0)) / 1000f))) + 1f),
                                    detectedValleys, (valleys.size() == 1)
                                            // the time used
                                            ? (1f * (measurementLength - millisUntilFinished - clipLength) / 1000f)
                                            : (valleys.get(valleys.size() - 1) - valleys.get(0)) / 1000f);
//                            textView.setText(currentValue);
                        }
                    });

                    // Plot the waveform on a separate thread
                    Thread PlotThread = new Thread(() -> plot.draw(getStdValues()));
                    PlotThread.start();
                });
                thread.start();
            }

            @Override
            public void onFinish() {
                String currentValue = String.format(
                        Locale.getDefault(),
                        activity.getResources().getQuantityString(R.plurals.measurement_result, detectedValleys),
                        // the number of intervals is the number of detectedValleys - 1
                        (60f * (detectedValleys - 1) / (Math.max(1, (valleys.get(valleys.size() - 1) - valleys.get(0)) / 1000f)) + 1f),
                        detectedValleys,
                        // the time used
                        1f * (valleys.get(valleys.size() - 1) - valleys.get(0)) / 1000f);
//                textView.setText(currentValue);
//                main.CameraStop();
                main.setIsRecordingVideo(false);
            }
        };
        timer.start();
    }

    // stop
    public void stop() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
