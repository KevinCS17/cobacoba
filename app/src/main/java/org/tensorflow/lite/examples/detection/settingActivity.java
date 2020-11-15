package org.tensorflow.lite.examples.detection;

import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.app.AppCompatActivity;

public class settingActivity extends AppCompatActivity {

    public static boolean detectPerson = true;
    public static boolean detectCar = true;
    public static boolean detectBicycle = true;
    public static boolean detectMotorCycle = true;
    public static boolean detectStopSign = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        CheckBox checkPerson = findViewById(R.id.checkPerson);
        CheckBox checkCar = findViewById(R.id.checkCar);
        CheckBox checkBicycle = findViewById(R.id.checkBicycle);
        CheckBox checkMotorcycle = findViewById(R.id.checkMotorcycle);
        CheckBox checkStopSign = findViewById(R.id.checkStopSign);

        if(!detectPerson){
            checkPerson.setChecked(false);
        }
        if(!detectCar){
            checkCar.setChecked(false);
        }
        if(!detectBicycle){
            checkBicycle.setChecked(false);
        }
        if(!detectMotorCycle){
            checkMotorcycle.setChecked(false);
        }
        if(!detectStopSign){
            checkStopSign.setChecked(false);
        }

    }

    public void onCheckPerson(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        if(checked) {
            detectPerson = true;
        }
        else {
            detectPerson = false;
        }
    }

    public void onCheckCar(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        if(checked) {
            detectCar = true;
        }
        else {
            detectCar = false;
        }
    }

    public void onCheckBicycle(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        if(checked) {
            detectBicycle = true;
        }
        else {
            detectBicycle = false;
        }
    }
    public void onCheckMotorcycle(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        if(checked) {
            detectMotorCycle = true;
        }
        else {
            detectMotorCycle = false;
        }
    }

    public void onCheckStopSign(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        if(checked) {
            detectStopSign = true;
        }
        else {
            detectStopSign = false;
        }
    }

    public void btnSaveOnClick(View view) {
        finish();
    }
}