package top.defaults.cameraapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import com.jakewharton.rxbinding2.view.RxView;
import com.tbruyelle.rxpermissions2.RxPermissions;


import butterknife.ButterKnife;
import top.defaults.camera.Camera2Photographer;
import top.defaults.view.TextButton;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private View prepareToRecord;
    private Bundle bundle;
    private EditText nameText;
    private EditText ageText;
    private RadioButton male;
    private RadioButton female;
    public static String nameAgeGender = "";



    @Override
    public void onClick(View v) {
        // default method for handling onClick Events..
        bundle = new Bundle();

        nameText = findViewById(R.id.nameText);
        ageText = findViewById(R.id.ageText);

        male = findViewById(R.id.radioM);
        female = findViewById(R.id.radioF);

        RxPermissions rxPermissions = new RxPermissions(this);

        switch (v.getId()) {

            case R.id.open_camera_RGB:
                prepareToRecord = findViewById(R.id.open_camera_RGB);
                setPrepareToRecord(rxPermissions, "1");
                break;

            default:
                break;
        }


    }

    @SuppressLint("CheckResult")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        TextButton rgb = findViewById(R.id.open_camera_RGB);
        rgb.setOnClickListener(this);
    }

    private void startVideoRecordActivity() {
        Intent intent = new Intent(this, PhotographerActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    public void setPrepareToRecord(RxPermissions rxPermissions, String cameraID) {

        RxView.clicks(prepareToRecord)
                .compose(rxPermissions.ensure(Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE))
                .subscribe(granted -> {
                    if (granted) {
                        String name = nameText.getText().toString();
                        String age = ageText.getText().toString();
                        String gender = "";

                        if (male.isChecked()) {
                            gender = "male";
                        } else if (female.isChecked()) {
                            gender = "female";
                        }
                        nameAgeGender = name + "_" + age + "_" + gender;

                        Camera2Photographer camera2Photographer = new Camera2Photographer();
                        camera2Photographer.setFileName(name + "_" + age + "_" + gender + "_Front");
                        camera2Photographer.setFileName2(name + "_" + age + "_" + gender + "_Rear");
                        camera2Photographer.setCameraID("5");
                        camera2Photographer.setCameraID2("1");
                        startVideoRecordActivity();

                    } else {
                        Snackbar.make(prepareToRecord, getString(R.string.no_enough_permission), Snackbar.LENGTH_SHORT).setAction("Confirm", null).show();
                    }
                });
    }
}