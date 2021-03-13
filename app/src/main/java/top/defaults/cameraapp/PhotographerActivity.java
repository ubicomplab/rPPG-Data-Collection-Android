package top.defaults.cameraapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;


import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;
import top.defaults.camera.CameraView;
import top.defaults.camera.CanvasDrawer;
import top.defaults.camera.Error;
import top.defaults.camera.Photographer;
import top.defaults.camera.PhotographerFactory;
import top.defaults.camera.PhotographerHelper;
import top.defaults.camera.SimpleOnEventListener;

import top.defaults.camera.Utils;
import top.defaults.camera.Values;

import top.defaults.cameraapp.CameraPPGutils.HRCompute;
import top.defaults.cameraapp.options.Commons;


public class PhotographerActivity extends AppCompatActivity implements SensorEventListener {


    Photographer photographer;
    PhotographerHelper photographerHelper;
    private boolean isRecordingVideo;

    boolean isRecording = false; //is recording  audio or not
    AudioRecord audioRecord = null;

    File recordingFile;//file that stores recorded audio
    File parent = null; //file directory

    //audio format
    int bufferSize = 0;
    int sampleRateInHz = 11025;
    int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO; //mono-channel
    int audioFormat = AudioFormat.ENCODING_PCM_16BIT; //16-bit

    //log Tag
    private static final String TAG = "MainActivity";
    String mAudioTAG = "AudioRecord";

    //prefix of filename
    String nameAgeGender = MainActivity.nameAgeGender;

    //SensorManager
    private SensorManager mSensorManager;

    //Sensors: IMU + ambient light
    private Sensor mAccelerometer, mGyroscope, mMagnetometer, mAmbientlight;

    private HRCompute HeartRate;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession previewSession;
    private CaptureRequest.Builder previewCaptureRequestBuilder;

    private LineChart mChart;
    private boolean plotData = true;
    private Thread plotThread;

    @BindView(R.id.preview)
    CameraView preview;
    @BindView(R.id.preview2)
    CameraView preview2;

    @BindView(R.id.status)
    TextView statusTextView;

    @BindView(R.id.action)
    ImageButton actionButton;

    @BindView(R.id.zoomValue)
    TextView zoomValueTextView;

    @BindView(R.id.ambien_light)
    TextView ambientlight;

    private int currentFlash = Values.FLASH_AUTO;

    private static final int[] FLASH_OPTIONS = {
            Values.FLASH_AUTO,
            Values.FLASH_OFF,
            Values.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private static final int[] FLASH_TITLES = {
            R.string.flash_auto,
            R.string.flash_off,
            R.string.flash_on,
    };


    @OnClick(R.id.action)
    void action() {
        int mode = photographer.getMode();
        if (mode == Values.MODE_VIDEO) {
            if (isRecordingVideo) {
                stopRecording();//stop audio recording
                finishRecordingIfNeeded();
            } else {
                record();//record audio
                isRecordingVideo = true;

                photographer.startRecording(null);

                photographer.startRecording2(null);

                actionButton.setEnabled(false);
//                actionButton.setVisibility(View.INVISIBLE);
            }
        } else if (mode == Values.MODE_IMAGE) {

            photographer.takePicture();

            photographer.takePicture2();
        }
    }

    @OnClick(R.id.flash_torch)
    void toggleFlashTorch() {
        int flash = photographer.getFlash();
        if (flash == Values.FLASH_TORCH) {
            photographer.setFlash(currentFlash);
            photographer.setFlash2(currentFlash);
        } else {
            photographer.setFlash(Values.FLASH_TORCH);
            photographer.setFlash2(Values.FLASH_TORCH);
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PhotographerActivity.this.setTitle("Sensor Fusion");
        setContentView(R.layout.activity_video_record);
        //initialize and register motion sensors and ambient light sensor
        initializeSensors();
        initializeChart();

        startPlot();
        //calculate buffersize
        bufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        //Initialize AudioRecord
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateInHz, channelConfig, audioFormat, bufferSize);
        //parent directory for file storage
        parent = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Record");
        if (!parent.exists())
            parent.mkdirs();//mkdir if not exist

        // Request camera permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        // Request saving permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        ButterKnife.bind(this);

        preview.setFocusIndicatorDrawer(new CanvasDrawer() {
            private static final int SIZE = 300;
            private static final int LINE_LENGTH = 50;

            @Override
            public Paint[] initPaints() {
                Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                focusPaint.setStyle(Paint.Style.STROKE);
                focusPaint.setStrokeWidth(2);
                focusPaint.setColor(Color.WHITE);
                return new Paint[]{focusPaint};
            }

            @Override
            public void draw(Canvas canvas, Point point, Paint[] paints) {
                if (paints == null || paints.length == 0) return;

                int left = point.x - (SIZE / 2);
                int top = point.y - (SIZE / 2);
                int right = point.x + (SIZE / 2);
                int bottom = point.y + (SIZE / 2);

                Paint paint = paints[0];

                canvas.drawLine(left, top + LINE_LENGTH, left, top, paint);
                canvas.drawLine(left, top, left + LINE_LENGTH, top, paint);

                canvas.drawLine(right - LINE_LENGTH, top, right, top, paint);
                canvas.drawLine(right, top, right, top + LINE_LENGTH, paint);

                canvas.drawLine(right, bottom - LINE_LENGTH, right, bottom, paint);
                canvas.drawLine(right, bottom, right - LINE_LENGTH, bottom, paint);

                canvas.drawLine(left + LINE_LENGTH, bottom, left, bottom, paint);
                canvas.drawLine(left, bottom, left, bottom - LINE_LENGTH, paint);
            }
        });

        preview2.setFocusIndicatorDrawer(new CanvasDrawer() {
            private static final int SIZE = 300;
            private static final int LINE_LENGTH = 50;

            @Override
            public Paint[] initPaints() {
                Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                focusPaint.setStyle(Paint.Style.STROKE);
                focusPaint.setStrokeWidth(2);
                focusPaint.setColor(Color.WHITE);
                return new Paint[]{focusPaint};
            }

            @Override
            public void draw(Canvas canvas, Point point, Paint[] paints) {
                if (paints == null || paints.length == 0) return;

                int left = point.x - (SIZE / 2);
                int top = point.y - (SIZE / 2);
                int right = point.x + (SIZE / 2);
                int bottom = point.y + (SIZE / 2);

                Paint paint = paints[0];

                canvas.drawLine(left, top + LINE_LENGTH, left, top, paint);
                canvas.drawLine(left, top, left + LINE_LENGTH, top, paint);

                canvas.drawLine(right - LINE_LENGTH, top, right, top, paint);
                canvas.drawLine(right, top, right, top + LINE_LENGTH, paint);

                canvas.drawLine(right, bottom - LINE_LENGTH, right, bottom, paint);
                canvas.drawLine(right, bottom, right - LINE_LENGTH, bottom, paint);

                canvas.drawLine(left + LINE_LENGTH, bottom, left, bottom, paint);
                canvas.drawLine(left, bottom, left, bottom - LINE_LENGTH, paint);
            }
        });

        photographer = PhotographerFactory.createPhotographerWithCamera2(this, preview, preview2);
        photographerHelper = new PhotographerHelper(photographer);
        photographerHelper.setFileDir(Commons.MEDIA_DIR);
        photographer.setOnEventListener(new SimpleOnEventListener() {
            @Override
            public void onDeviceConfigured() {
                if (photographer.getMode() == Values.MODE_VIDEO) {
                    actionButton.setImageResource(R.drawable.record);
                } else {
                    actionButton.setImageResource(R.drawable.ic_camera);
                }
            }

            @Override
            public void onZoomChanged(float zoom) {
                zoomValueTextView.setText(String.format(Locale.getDefault(), "%.1fX", zoom));
            }

            @Override
            public void onStartRecording() {
                actionButton.setEnabled(true);
                actionButton.setImageResource(R.drawable.stop);
                statusTextView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinishRecording(String filePath) {
                announcingNewFile(filePath);
            }

            @Override
            public void onShotFinished(String filePath) {
                announcingNewFile(filePath);
            }

            @Override
            public void onError(Error error) {
                Timber.e("Error happens: %s", error.getMessage());
            }
        });

        photographer.setOnEventListener2(new SimpleOnEventListener() {
            @Override
            public void onDeviceConfigured() {
                if (photographer.getMode() == Values.MODE_VIDEO) {
                    actionButton.setImageResource(R.drawable.record);
                } else {
                    actionButton.setImageResource(R.drawable.ic_camera);
                }
            }

            @Override
            public void onZoomChanged(float zoom) {
                zoomValueTextView.setText(String.format(Locale.getDefault(), "%.1fX", zoom));
            }

            @Override
            public void onStartRecording() {
                actionButton.setEnabled(true);
                actionButton.setImageResource(R.drawable.stop);
                statusTextView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFinishRecording(String filePath) {
                announcingNewFile(filePath);
            }

            @Override
            public void onShotFinished(String filePath) {
                announcingNewFile(filePath);
            }

            @Override
            public void onError(Error error) {
                Timber.e("Error happens: %s", error.getMessage());
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
//        enterFullscreen();
        photographer.startPreview();
        photographer.startPreview2();

    }

    @Override
    protected void onPause() {
        finishRecordingIfNeeded();
        photographer.stopPreview();
        photographer.stopPreview2();
        super.onPause();
    }

    private void enterFullscreen() {
        View decorView = getWindow().getDecorView();
        decorView.setBackgroundColor(Color.BLACK);
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
    }

    private void finishRecordingIfNeeded() {
        if (isRecordingVideo) {
            isRecordingVideo = false;
            photographer.finishRecording();

            photographer.finishRecording2();
            statusTextView.setVisibility(View.INVISIBLE);
            actionButton.setEnabled(true);
            actionButton.setImageResource(R.drawable.record);
        }
    }

    private void announcingNewFile(String filePath) {
        Toast.makeText(PhotographerActivity.this, "File: " + filePath, Toast.LENGTH_SHORT).show();
        Utils.addMediaToGallery(PhotographerActivity.this, filePath);
    }

    private void addEntry(SensorEvent event) {
        LineData data = mChart.getData();
        if (data != null) {
            ILineDataSet set = data.getDataSetByIndex(0);

            if (set == null) {
                set = createSet();
                data.addDataSet(set);
            }

            data.addEntry(new Entry(set.getEntryCount(), event.values[0] + 5), 0);
            data.notifyDataChanged();

            // let the chart know it's data has changed
            mChart.notifyDataSetChanged();
            // limit the number of visible entries
            mChart.setMaxVisibleValueCount(150);


            // move to the latest entry
            mChart.moveViewToX(data.getEntryCount());
        }
    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "Dynamic Data");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(1f);
        set.setColor(Color.RED);
        set.setHighlightEnabled(false);
        set.setDrawValues(false);
        set.setDrawCircles(false);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);

        return set;
    }

    private void startPlot() {
        if (plotThread != null) {
            plotThread.interrupt();
        }

        plotThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    plotData = true;
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        plotThread.start();
    }

    //load and store sensor parameters while recording
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        Sensor sensor = sensorEvent.sensor;
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            //plotData
            if (plotData) {
                addEntry(sensorEvent);
                plotData = false;
            }

            if (isRecordingVideo) {
                //write csv
                try {
                    String accCsvFileName = nameAgeGender + "_ACCELEROMETER.csv";
                    File accFile = new File(parent, accCsvFileName);
                    if (!accFile.exists()) {
                        accFile.createNewFile();
                    }
                    FileWriter accFw = new FileWriter(accFile.getAbsoluteFile(), true);
                    BufferedWriter accBw = new BufferedWriter(accFw);
                    //creating Calendar instance
                    Calendar calendar = Calendar.getInstance();
                    //Returns current time in millis
                    long timeMilli = calendar.getTimeInMillis();
                    //from left to right: timestamp, x, y, z
                    String content = timeMilli + "," + String.valueOf(sensorEvent.values[0]) + "," + String.valueOf(sensorEvent.values[1]) + "," + String.valueOf(sensorEvent.values[2]);

                    accBw.write(content);
                    accBw.newLine();
                    accBw.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {


            String gyroCsvFileName = nameAgeGender + "_GYRO.csv";
            if (isRecordingVideo) {
                try {

                    File gyroFile = new File(parent, gyroCsvFileName);
                    // if file doesnt exists, then create it

                    if (!gyroFile.exists()) {
                        gyroFile.createNewFile();
                    }
                    FileWriter gyroFw = new FileWriter(gyroFile.getAbsoluteFile(), true);
                    BufferedWriter gyroBw = new BufferedWriter(gyroFw);
                    //creating Calendar instance
                    Calendar calendar = Calendar.getInstance();
                    //Returns current time in millis
                    long timeMilli = calendar.getTimeInMillis();
                    //from left to right: timestamp, x, y, z
                    String content = timeMilli + "," + String.valueOf(sensorEvent.values[0]) + "," + String.valueOf(sensorEvent.values[1]) + "," + String.valueOf(sensorEvent.values[2]);

                    gyroBw.write(content);
                    gyroBw.newLine();
                    gyroBw.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {

            String magnetCsvFileName = nameAgeGender + "_MAGNET.csv";
            if (isRecordingVideo) {
                try {
                    File magnetFile = new File(parent, magnetCsvFileName);
                    // if file doesnt exists, then create it
                    if (!magnetFile.exists()) {
                        magnetFile.createNewFile();
                    }
                    FileWriter magnetFw = new FileWriter(magnetFile.getAbsoluteFile(), true);
                    BufferedWriter magnetBw = new BufferedWriter(magnetFw);
                    //creating Calendar instance
                    Calendar calendar = Calendar.getInstance();
                    //Returns current time in millis
                    long timeMilli = calendar.getTimeInMillis();
                    //from left to right: timestamp, x, y, z
                    String content = timeMilli + "," + String.valueOf(sensorEvent.values[0]) + "," + String.valueOf(sensorEvent.values[1]) + "," + String.valueOf(sensorEvent.values[2]);

                    magnetBw.write(content);
                    magnetBw.newLine();
                    magnetBw.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (sensor.getType() == Sensor.TYPE_LIGHT) {

            ambientlight.setText("Intensity: " + sensorEvent.values[0]);

            String lightCsvFileName = nameAgeGender + "_LIGHT.csv";
            if (isRecordingVideo) {
                try {
                    File lightFile = new File(parent, lightCsvFileName);
                    // if file doesnt exists, then create it
                    if (!lightFile.exists()) {
                        lightFile.createNewFile();
                    }
                    FileWriter lightFw = new FileWriter(lightFile.getAbsoluteFile(), true);
                    BufferedWriter lightBw = new BufferedWriter(lightFw);
                    //creating Calendar instance
                    Calendar calendar = Calendar.getInstance();
                    //Returns current time in millis
                    long timeMilli = calendar.getTimeInMillis();
                    //from left to right: timestamp, light intensity
                    String content = timeMilli + "," + String.valueOf(sensorEvent.values[0]);
                    lightBw.write(content);
                    lightBw.newLine();
                    lightBw.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    //Record & store audio
    private void record() {
        isRecording = true;

        Log.d(mAudioTAG, "Recording!");
        //new thread for audio recording
        new Thread(() -> {
            isRecording = true;
            //'name_age_gender.pcm'
            recordingFile = new File(parent, nameAgeGender + ".pcm");

            if (recordingFile.exists()) {
                recordingFile.delete();
            }
            //create file
            try {
                recordingFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error occurred when storing audio!");
            }
            //record and write pcm file
            try {
                DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(recordingFile)));
                byte[] buffer = new byte[bufferSize];
                audioRecord.startRecording();//start recording

                while (isRecording) {
                    int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                    for (int i = 0; i < bufferReadResult; i++) {
                        dos.write(buffer[i]);
                    }
                }
                audioRecord.stop();
                dos.close();
            } catch (Throwable t) {
                Log.e(TAG, "Recording Failed");
            }
        }).start();
    }

    //Stop
    private void stopRecording() {
        isRecording = false;
    }

    private void initializeSensors() {
        //Initialize sensormanager & IMU sensors
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAmbientlight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        //register sensorlisteners
        if (mAccelerometer != null) {
            //register listener
            mSensorManager.registerListener(this, mAccelerometer, 10000);
        } else {
            Log.d(TAG, "no access to Accelerometer");
        }

        if (mGyroscope != null) {
            //register listener
            mSensorManager.registerListener(this, mGyroscope, 10000);
        } else {
            Log.d(TAG, "no access to Gyroscope");
        }

        if (mMagnetometer != null) {
            //register listener
            mSensorManager.registerListener(this, mMagnetometer, 10000);
            ;
        } else {
            Log.d(TAG, "no access to Magnetometer");
        }

        if (mAmbientlight != null) {
            //register listener
            mSensorManager.registerListener(this, mAmbientlight, 10000);
            ;
        } else {
            Log.d(TAG, "no access to Magnetometer");
        }
    }

    //initialize chart
    public void initializeChart() {
        mChart = (LineChart) findViewById(R.id.chart1);
        mChart.getDescription().setEnabled(true);
        mChart.getDescription().setText("Real Time Accelerometer Data Plot");

        // disable scaling and dragging
        mChart.setTouchEnabled(false);
        mChart.setDragEnabled(false);
        mChart.setScaleEnabled(false);
        mChart.setDrawGridBackground(false);
        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(false);
        // set an alternative background color
        mChart.setBackgroundColor(Color.WHITE);

        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
        // add empty data
        mChart.setData(data);

        // get the legend (only possible after setting data)
        Legend l = mChart.getLegend();

        // modify the legend ...
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.WHITE);

        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(true);
        xl.setAvoidFirstLastClipping(true);
        xl.setEnabled(true);

        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(false);
        leftAxis.setAxisMaximum(10f);
        leftAxis.setAxisMinimum(0f);


        YAxis rightAxis = mChart.getAxisRight();
        rightAxis.setEnabled(false);

        mChart.getAxisLeft().setDrawGridLines(false);
        mChart.getXAxis().setDrawGridLines(false);
        mChart.setDrawBorders(false);


    }

    public void setIsRecordingVideo(boolean t) {

        stopRecording();//stop audio recording
        finishRecordingIfNeeded();
        isRecordingVideo = t;
    }
}