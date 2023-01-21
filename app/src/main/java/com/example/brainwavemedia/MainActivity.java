package com.example.brainwavemedia;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.androidplot.xy.XYPlot;
import com.neurosky.AlgoSdk.NskAlgoDataType;
import com.neurosky.AlgoSdk.NskAlgoSdk;
import com.neurosky.AlgoSdk.NskAlgoState;
import com.neurosky.AlgoSdk.NskAlgoType;
import com.neurosky.connection.ConnectionStates;
import com.neurosky.connection.DataType.MindDataType;
import com.neurosky.connection.TgStreamHandler;
import com.neurosky.connection.TgStreamReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {
    private NskAlgoSdk nskAlgoSdk;
    private BluetoothAdapter bluetoothAdapter;
    private TgStreamReader tgStreamReader;
    InputStream badavg,goodavg;

    private short raw_data[] = {0};
    private int raw_data_index = 0;
    List goodrange = new ArrayList();
    List badrange = new ArrayList();
    private Button hatebtn,lovebtn;
    private VideoView videoView;
    private TextView connectionStatus;

    private int attention = 0;
    float RawData;
    double realtimerange,goodmin,goodmax,badmin,badmax;
    String[] goodrage;
    String[] badrage;
    double sum = 0D;
    XYPlot plot;
    double rawdata1 = 0;
    double rawCount = 0;
    ImageView image =findViewById(R.id.image);
    List<Double> realtimedata = new ArrayList<>();

    private boolean record = true;


    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        goodavg=getResources().openRawResource(R.raw.good);
        BufferedReader reader = new BufferedReader(new InputStreamReader(goodavg));
        try {
            String csvline;
            while ((csvline=reader.readLine())!=null){
                goodrage=csvline.split(",");
                try {
                    //double[] doubleArray = Arrays.stream(goodrage).mapToDouble(Double::parseDouble).toArray();
                    goodrange.add(goodrage);
                    Collections.sort(goodrange);
                    goodmin= (double) goodrange.get(0);
                    goodmax=(double) (goodrange.size()-1);



                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        badavg = getResources().openRawResource(R.raw.bad);
        BufferedReader reader1 = new BufferedReader(new InputStreamReader(badavg));
        try {
            String csvline1;
            while ((csvline1=reader.readLine())!=null){
                badrage=csvline1.split(",");
                try {
                    //double[] doubleArray = Arrays.stream(goodrage).mapToDouble(Double::parseDouble).toArray();
                    badrange.add(badrage);
                    Collections.sort(badrange);
                    badmin= (double) badrange.get(0);
                    badmax=(double) (badrange.size()-1);



                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        hatebtn=findViewById(R.id.hatebtn);
        lovebtn=findViewById(R.id.lovebtn);



        try {
            // (1) Make sure that the device supports Bluetooth and Bluetooth is on
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
                Toast.makeText(
                        this,
                        "Please enable your Bluetooth and re-run this program !",
                        Toast.LENGTH_LONG).show();
                //finish();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("TAG", "error:" + e.getMessage());
            return;
        }

        init();
    }

    @Override
    protected void onResume() {
        connect();
        super.onResume();
    }

    private void init() {
        nskAlgoSdk = new NskAlgoSdk();
        plot =findViewById(R.id.graph);

        nskAlgoSdk.setOnStateChangeListener((state, reason) -> {
            String stateStr = "";
            String reasonStr = "";
            for (NskAlgoState s : NskAlgoState.values()) {
                if (s.value == state) {
                    stateStr = s.toString();
                }
            }
            for (NskAlgoState r : NskAlgoState.values()) {
                if (r.value == reason) {
                    reasonStr = r.toString();
                }
            }
            Log.e("TAG", "NskAlgoSdkStateChangeListener: state: " + stateStr + ", reason: " + reasonStr);
            final String finalStateStr = stateStr + " | " + reasonStr;
            final int[] finalState = {state};
            runOnUiThread(() -> {
                // change UI elements here
                if (finalState[0] == NskAlgoState.NSK_ALGO_STATE_RUNNING.value || finalState[0] == NskAlgoState.NSK_ALGO_STATE_COLLECTING_BASELINE_DATA.value) {
                    connectionStatus.setText("running");
                } else if (finalState[0] == NskAlgoState.NSK_ALGO_STATE_STOP.value) {
                    raw_data = null;
                    raw_data_index = 0;
                    connectionStatus.setText("Stopped");
                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {

                        // Prepare for connecting
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    System.gc();
                } else if (finalState[0] == NskAlgoState.NSK_ALGO_STATE_PAUSE.value) {
                    connectionStatus.setText("paused");

                } else if (finalState[0] == NskAlgoState.NSK_ALGO_STATE_ANALYSING_BULK_DATA.value) {
                    connectionStatus.setText("analyzing");
                } else if (finalState[0] == NskAlgoState.NSK_ALGO_STATE_INITED.value || finalState[0] == NskAlgoState.NSK_ALGO_STATE_UNINTIED.value) {
                    connectionStatus.setText("inited");
                }
            });
        });
        hatebtn.setOnClickListener(view -> {
            receivedata(1);
            image.setImageResource(R.drawable.cat);



        });
        lovebtn.setOnClickListener(view ->{
            image.setImageResource(R.drawable.cockroge);
            receivedata(2);

                }
        );


    }

    private void connect() {
        raw_data = new short[512];
        raw_data_index = 0;

        tgStreamReader = new TgStreamReader(bluetoothAdapter, callback);

        if (tgStreamReader != null && tgStreamReader.isBTConnected()) {

            // Prepare for connecting
            tgStreamReader.stop();
            tgStreamReader.close();
        }

        // (4) Demo of  using connect() and start() to replace connectAndStart(),
        // please call start() when the state is changed to STATE_CONNECTED
        tgStreamReader.connect();
    }

    private void receivedata(int index) {
        raw_data_index = 0;
        rawdata1=0;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    realtimedata.add(rawdata1);
                    Collections.sort(realtimedata);
                    realtimerange=realtimedata.get(0)-realtimedata.get(realtimedata.size()-1);
                    showToast("Avg:" + realtimerange, Toast.LENGTH_LONG);

                    switch (index) {
                        case 1:
                            if((realtimerange<=goodmax)&&((goodmin)<realtimerange ) ){
                                showToast("you like the picture",Toast.LENGTH_LONG);
                            }


                            break;
                        case 2:
                            if((realtimerange<=badmax)&&((badmin)<realtimerange ) ){
                                showToast("you dontlikethe picture",Toast.LENGTH_LONG);
                            }

                            break;

                    }

                });
            }
        }, 1000 * 5);


    }

   /* private void DirectionData() {
        showToast("Recognizing data", Toast.LENGTH_LONG);
        rawdata1 = 0;
        raw_data_index=0;
        while(true){
            sum = 0;

            for(int i =0;i<10;i++){
                 sum += rawdata1;

                double AvgD = sum/10;
                if (AvgD > avgE-2 && AvgD < 2 + avgE) {
                    Log.e("TAG","East : "+AvgD);
                    showToast("Direction:East", Toast.LENGTH_LONG);
                } else if (AvgD >avgW-2 && AvgD < 2 + avgW) {
                    Log.e("TAG","West : "+AvgD);
                    showToast("Direction:West", Toast.LENGTH_LONG);
                } else if (AvgD > avgN-2 && AvgD< 2 + avgN) {
                    Log.e("TAG","North : "+AvgD);
                    showToast("Direction:North", Toast.LENGTH_LONG);
                } else if (AvgD> avgS-2 && AvgD < 2 + avgS) {
                    Log.e("TAG","South :" +AvgD);
                    showToast("Direction:East", Toast.LENGTH_LONG);
                } else {
                    showToast("Error", Toast.LENGTH_LONG);
                }
            }
        }

    }
*/



    private TgStreamHandler callback = new TgStreamHandler() {

        @Override
        public void onStatesChanged(int connectionStates) {
            // TODO Auto-generated method stub
            Log.d("TAG", "connectionStates change to: " + connectionStates);
            switch (connectionStates) {
                case ConnectionStates.STATE_CONNECTING:
                    showToast("Connecting", Toast.LENGTH_SHORT);
                    // Do something when connecting
                    break;
                case ConnectionStates.STATE_CONNECTED:
                    // Do something when connected
                    tgStreamReader.start();
                    showToast("Connected", Toast.LENGTH_SHORT);
                    int algoTypes = NskAlgoType.NSK_ALGO_TYPE_ATT.value;

                    int ret = nskAlgoSdk.NskAlgoInit(algoTypes, getFilesDir().getAbsolutePath());
                    if (ret == 0) {
                        showToast("Receiving data ", Toast.LENGTH_LONG);
                        nskAlgoSdk.NskAlgoStart(false);
                    }
                    break;
                case ConnectionStates.STATE_WORKING:
                    // Do something when working

                    //(9) demo of recording raw data , stop() will call stopRecordRawData,
                    //or you can add a button to control it.
                    //You can change the save path by calling setRecordStreamFilePath(String filePath) before startRecordRawData
                    //tgStreamReader.startRecordRawData();

                    MainActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            showToast("click to record EastData", Toast.LENGTH_LONG);


                            //receivedata();

                        }

                    });

                    break;
                case ConnectionStates.STATE_GET_DATA_TIME_OUT:
                    // Do something when getting data timeout

                    //(9) demo of recording raw data, exception handling
                    //tgStreamReader.stopRecordRawData();

                    showToast("Get data time out!", Toast.LENGTH_SHORT);

                    if (tgStreamReader != null && tgStreamReader.isBTConnected()) {
                        tgStreamReader.stop();
                        tgStreamReader.close();
                    }

                    break;
                case ConnectionStates.STATE_STOPPED:
                    // Do something when stopped
                    // We have to call tgStreamReader.stop() and tgStreamReader.close() much more than
                    // tgStreamReader.connectAndstart(), because we have to prepare for that
                    showToast("stopped", Toast.LENGTH_LONG);
                    break;
                case ConnectionStates.STATE_DISCONNECTED:
                    // Do something when disconnected
                    Log.e("TAG", "disconnected");

                    showToast("Disconnected", Toast.LENGTH_LONG);

                    break;
                case ConnectionStates.STATE_ERROR:
                    // Do something when you get error message
                    showToast("error", Toast.LENGTH_LONG);
                    break;
                case ConnectionStates.STATE_FAILED:
                    // Do something when you get failed message
                    // It always happens when open the BluetoothSocket error or timeout
                    // Maybe the device is not working normal.
                    // Maybe you have to try again
                    Log.e("TAG", "failed");
                    break;
            }
        }

        @Override
        public void onRecordFail(int flag) {
            // You can handle the record error message here
            Log.e("TAG", "onRecordFail: " + flag);

        }

        @Override
        public void onChecksumFail(byte[] payload, int length, int checksum) {
            // You can handle the bad packets here.
        }

        @Override
        public void onDataReceived(int datatype, int data, Object obj) {
            // You can handle the received data here
            // You can feed the raw data to algo sdk here if necessary.
            //Log.i(TAG,"onDataReceived");
            //Log.e("TAG","DATATYPE"+datatype);
            //Log.e("TAG","Data"+data);
            switch (datatype) {
                case MindDataType.CODE_ATTENTION:
                    short attValue[] = {(short) data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_ATT.value, attValue, 1);
                    break;
                case MindDataType.CODE_MEDITATION:
                    short medValue[] = {(short) data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_MED.value, medValue, 1);
                    break;
                case MindDataType.CODE_POOR_SIGNAL:
                    short pqValue[] = {(short) data};
                    nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_PQ.value, pqValue, 1);
                    break;
                case MindDataType.CODE_RAW:
                    raw_data[raw_data_index++] = (short) data;
                    if (raw_data_index == 100) {
                        nskAlgoSdk.NskAlgoDataStream(NskAlgoDataType.NSK_ALGO_DATA_TYPE_EEG.value, raw_data, raw_data_index);
                        raw_data_index = 0;
                        Log.e("TAG","RawData : "+raw_data[raw_data_index]);
                        rawdata1 = raw_data[raw_data_index];
                    }
                    break;
                default:
                    break;
            }
        }

    };

    public void showToast(final String msg, final int timeStyle) {
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), msg, timeStyle).show();
            }
        });
    }


}