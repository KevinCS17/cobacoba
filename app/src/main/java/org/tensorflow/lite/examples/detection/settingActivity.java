package org.tensorflow.lite.examples.detection;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class settingActivity extends AppCompatActivity {

    public static boolean detectPerson = true;
    public static boolean detectCar = true;
    public static boolean detectBicycle = true;
    public static boolean detectMotorCycle = true;
    public static boolean detectStopSign = true;
    public static boolean volumeCheckbox = false;
    public static int volumeLevel= 0;

    public static float confScore = 0.5f;
    private int ScoreNow = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        CheckBox checkPerson = findViewById(R.id.checkPerson);
        CheckBox checkCar = findViewById(R.id.checkCar);
        CheckBox checkBicycle = findViewById(R.id.checkBicycle);
        CheckBox checkMotorcycle = findViewById(R.id.checkMotorcycle);
        CheckBox checkStopSign = findViewById(R.id.checkStopSign);

        EditText editScore = findViewById(R.id.editScore);
        SeekBar seekScore = findViewById(R.id.seekScore);
        confScore = DetectorActivity.MINIMUM_CONFIDENCE_TF_OD_API * 100;
        ScoreNow = (int)confScore;
        seekScore.setProgress(ScoreNow);
        editScore.setText(ScoreNow + "%");

        CheckBox LouderCheckbox = findViewById(R.id.LouderCheckbox);
        CheckBox TimerCheckbox = findViewById(R.id.TimerCheckbox);
        View warningView = findViewById(R.id.WarningView);
        RelativeLayout relativeLayout = findViewById(R.id.LevelLinear);
        if(LouderCheckbox.isChecked()){
            warningView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            relativeLayout.setVisibility(View.VISIBLE);
        }else{
            warningView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            relativeLayout.setVisibility(View.GONE);
        }

        if(volumeCheckbox == true) {
            LouderCheckbox.setChecked(true);
            warningView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            relativeLayout.setVisibility(View.VISIBLE);
            TextView textLevel = findViewById(R.id.textLevel);
            String setText = Integer.toString(volumeLevel);
            textLevel.setText(setText);
        }
        else{
            LouderCheckbox.setChecked(false);
            warningView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            relativeLayout.setVisibility(View.GONE);
            TextView textLevel = findViewById(R.id.textLevel);
            String setText = Integer.toString(volumeLevel);
            textLevel.setText(setText);
        }
        seekScore.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ScoreNow = progress;
                String setText = ScoreNow + "%";
                editScore.setText(setText);
                confScore = (float)ScoreNow;
                changeConfScore(ScoreNow);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        if (!detectPerson) {
            checkPerson.setChecked(false);
        }
        if (!detectCar) {
            checkCar.setChecked(false);
        }
        if (!detectBicycle) {
            checkBicycle.setChecked(false);
        }
        if (!detectMotorCycle) {
            checkMotorcycle.setChecked(false);
        }
        if (!detectStopSign) {
            checkStopSign.setChecked(false);
        }


        ((EditText) findViewById(R.id.editScore)).setOnEditorActionListener(
                new EditText.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                                actionId == EditorInfo.IME_ACTION_DONE ||
                                event != null &&
                                        event.getAction() == KeyEvent.ACTION_DOWN &&
                                        event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                            if (event == null || !event.isShiftPressed()) {
                                // the user is done typing.
                                String value = editScore.getText().toString();
                                if(value.equals("") || value.equals("%")){
                                    String setText = ScoreNow + "%";
                                    editScore.setText(setText);
                                }
                                else{
                                    changeSeekbar(value);
                                }
                                return true; // consume.
                            }
                        }
                        return false; // pass on to other listeners.
                    }
                }
        );


    }
    public void changeConfScore(int score){
        float floatScore = (float)score;
        floatScore = floatScore/100;
        DetectorActivity.MINIMUM_CONFIDENCE_TF_OD_API = floatScore;
    }
    public void changeSeekbar(String value) {
        String hasil;
        if (value.toLowerCase().contains("%")) {
            hasil = value.replace("%", "");
        } else {
            hasil = value;
        }
        int inthasil = Integer.valueOf(hasil);
        if (inthasil <= 100 && inthasil >= 0) {
                ScoreNow = inthasil;
                SeekBar seekBar = findViewById(R.id.seekScore);
                seekBar.setProgress(ScoreNow);

                EditText editscore = findViewById(R.id.editScore);
                String setText = ScoreNow + "%";
                editscore.setText(setText);
                changeConfScore(ScoreNow);
            } else {
                EditText editscore = findViewById(R.id.editScore);
                String setText = ScoreNow + "%";
                editscore.setText(setText);
                changeConfScore(ScoreNow);
            }
        }
    public void plusOnClick(View view) {
        if(volumeLevel < 5) {
            volumeLevel = volumeLevel + 1;
            TextView textLevel = findViewById(R.id.textLevel);
            String setText = Integer.toString(volumeLevel);
            textLevel.setText(setText);
        }
    }
    public void minusOnClick(View view){
        if(volumeLevel > 0){
            volumeLevel = volumeLevel -1;
            TextView textLevel = findViewById(R.id.textLevel);
            String setText = Integer.toString(volumeLevel);
            textLevel.setText(setText);
        }
    }
    public void onVolumeLoud(View view){
        View warningView = findViewById(R.id.WarningView);
        RelativeLayout levelLinear = findViewById(R.id.LevelLinear);
        boolean checked = ((CheckBox) view).isChecked();
        if(checked){
            warningView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            levelLinear.setVisibility(View.VISIBLE);
            volumeCheckbox = true;
        } else {
            warningView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            levelLinear.setVisibility(View.GONE);
            volumeCheckbox = false;
        }
    }

    public void onClassTimer(View view){

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