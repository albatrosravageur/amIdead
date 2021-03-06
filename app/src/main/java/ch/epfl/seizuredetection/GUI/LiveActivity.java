package ch.epfl.seizuredetection.GUI;

import android.Manifest;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.google.android.gms.common.util.ArrayUtils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.reactivestreams.Publisher;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.common.ops.QuantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.ops.Rot90Op;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.lang.reflect.Array;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import ch.epfl.seizuredetection.Bluetooth.BluetoothLeService;
import ch.epfl.seizuredetection.Bluetooth.SampleGattAttributes;
import ch.epfl.seizuredetection.Data.AppDatabase;
import ch.epfl.seizuredetection.Data.Constant;
import ch.epfl.seizuredetection.R;
import ch.epfl.seizuredetection.ml.CompressionNn0;

import static android.graphics.Color.RED;
import static android.graphics.Color.TRANSPARENT;

import ch.epfl.seizuredetection.ml.CompressionNn1;
import ch.epfl.seizuredetection.ml.CompressionNn2;
import ch.epfl.seizuredetection.signalProcessing.LinearRegression;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import polar.com.sdk.api.PolarBleApi;
import polar.com.sdk.api.PolarBleApiCallback;
import polar.com.sdk.api.PolarBleApiDefaultImpl;
import polar.com.sdk.api.errors.PolarInvalidArgument;
import polar.com.sdk.api.model.PolarAccelerometerData;
import polar.com.sdk.api.model.PolarDeviceInfo;
import polar.com.sdk.api.model.PolarEcgData;
import polar.com.sdk.api.model.PolarExerciseData;
import polar.com.sdk.api.model.PolarExerciseEntry;
import polar.com.sdk.api.model.PolarHrBroadcastData;
import polar.com.sdk.api.model.PolarHrData;
import polar.com.sdk.api.model.PolarOhrPPGData;
import polar.com.sdk.api.model.PolarOhrPPIData;
import polar.com.sdk.api.model.PolarSensorSetting;


public class LiveActivity extends AppCompatActivity {

    // Fields related to the Bluetooth connexion
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_DEVICE_ID = "DEVICE_ID";
    private static int THREE_SEC_SIGNAL_LEN = 768;
    private BluetoothLeService mBluetoothLeService;
    //private ServiceConnection mServiceConnection;
    private String mDeviceName; // Name of the device
    private String mDeviceAddress; // Address of the device
    private boolean mConnected; // True if device connected
    private ArrayList<Integer> hrArray = new ArrayList();
    // plot attributes
    private long startTime;
    private final static String TAG = LiveActivity.class.getSimpleName();
    // Firebase
    private DatabaseReference recordingRef;
    private String userID;
    private String recID;

    // Polar API
    private PolarBleApi api;
    private Disposable ecgDisposable = null;
    private String deviceId;

    // Signal
    private ArrayList<Integer> ecg = new ArrayList();
    public static String SIGNAL = "im signal";
    private String EXTRAS_COMPRESSION_RATE = "COMPRESSION_RATE";
    private int mCompressionRate = 2;

    //HR Plot
    private static XYPlot heartRatePlot;
    private static final int MIN_HR = 40; //Minimal heart rate value to display on the graph
    private static final int MAX_HR = 200; //Maximum heart rate value to display on the graph
    private static final int NUMBER_OF_POINTS = 50; //Number of data points to be displayed on the graph
    private XYplotSeriesList xyPlotSeriesList;
    public static final String HR_PLOT = "HR Polar H10";

    //SQlite
    AppDatabase db;
    private List<Integer> hrList = new ArrayList<Integer>();
    private int sizeListToSave = 10;

    // TODO: editar esta función para que haga display de los datos
    private void displayData(int intExtra) {

        TextView txtBpm = findViewById(R.id.bpm);
        txtBpm.setText(String.valueOf(intExtra) + " bpm");
        float time = System.currentTimeMillis() / 1000 - startTime;
        TextView txtSeconds = findViewById(R.id.time);
        txtSeconds.setText(String.valueOf(time) + " s");
    }

/*        HRseriesBelt.addLast(time, intExtra);
        while (HRseriesBelt.size() > 0 && (time - HRseriesBelt.getX(0).longValue()) > NUMBER_OF_SECONDS) {
            HRseriesBelt.removeFirst();
            heartRatePlot.setDomainBoundaries(0, 0, BoundaryMode.AUTO);
        }
        heartRatePlot.redraw();

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);

        //Remove going back button from toolbar
        View backButton = findViewById(R.id.backButton);
        ViewGroup parent = (ViewGroup) backButton.getParent();
        parent.removeView(backButton);

        //Remove profile button from toolbar
        View profileButton = findViewById(R.id.profile);
        ViewGroup parent2 = (ViewGroup) profileButton.getParent();
        parent2.removeView(profileButton);

        //SQLite
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, Constant.BD_NAME)
                .allowMainThreadQueries()
                .build();



        final Intent intent = getIntent();
        //mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        //mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        deviceId = intent.getStringExtra(EXTRAS_DEVICE_ID);
        mCompressionRate = intent.getIntExtra(EXTRAS_COMPRESSION_RATE, 2);
        //deviceId = "E78BAE13";

        if (Build.VERSION.SDK_INT >= 23) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }


        api = PolarBleApiDefaultImpl.defaultImplementation(this,
                PolarBleApi.FEATURE_POLAR_SENSOR_STREAMING |
                        PolarBleApi.FEATURE_BATTERY_INFO |
                        PolarBleApi.FEATURE_DEVICE_INFO |
                        PolarBleApi.FEATURE_HR);

        api.setApiCallback(new PolarBleApiCallback() {
            @Override
            public void blePowerStateChanged(boolean b) {
                Log.d(TAG, "BluetoothStateChanged " + b);
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device connected " + s.deviceId);
                startTime = System.currentTimeMillis() / 1000;
                TextView txtModel = findViewById(R.id.Model);
                txtModel.setText("Polar H10 ID:" + s.deviceId);
                Toast.makeText(LiveActivity.this, R.string.connected, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {

            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo s) {
                Log.d(TAG, "Device disconnected " + s);
            }

            @Override
            public void ecgFeatureReady(@NonNull String s) {
                Log.d(TAG, "ECG Feature ready " + s);
                streamECG();
            }

            @Override
            public void accelerometerFeatureReady(@NonNull String s) {
                Log.d(TAG, "ACC Feature ready " + s);
            }

            @Override
            public void ppgFeatureReady(@NonNull String s) {
                Log.d(TAG, "PPG Feature ready " + s);
            }

            @Override
            public void ppiFeatureReady(@NonNull String s) {
                Log.d(TAG, "PPI Feature ready " + s);
            }

            @Override
            public void biozFeatureReady(@NonNull String s) {

            }

            @Override
            public void hrFeatureReady(@NonNull String s) {
                Log.d(TAG, "HR Feature ready " + s);

            }

            @Override
            public void disInformationReceived(@NonNull String s, @NonNull UUID u, @NonNull String s1) {
                if (u.equals(UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb"))) {
                    String msg = "Firmware: " + s1.trim();
                    Log.d(TAG, "Firmware: " + s + " " + s1.trim());
                    //textViewFW.append(msg + "\n");
                }
            }

            @Override
            public void batteryLevelReceived(@NonNull String s, int i) {
                String msg = "ID: " + s + "\nBattery level: " + i;
                Log.d(TAG, "Battery level " + s + " " + i);
                Toast.makeText(LiveActivity.this, msg, Toast.LENGTH_LONG).show();
                //textViewFW.append(msg + "\n");
            }

            @Override
            public void hrNotificationReceived(@NonNull String s, @NonNull PolarHrData polarHrData) {
                Log.d(TAG, "HR " + polarHrData.hr);
                displayData(polarHrData.hr);
                hrArray.add(polarHrData.hr);

                // Update PLOT
                xyPlotSeriesList.updateSeries(HR_PLOT, polarHrData.hr);
                XYSeries hrSeries = new SimpleXYSeries(xyPlotSeriesList.getSeriesFromList
                        (HR_PLOT), SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, HR_PLOT);
                LineAndPointFormatter formatter = xyPlotSeriesList.getFormatterFromList
                        (HR_PLOT);

                heartRatePlot.clear();
                heartRatePlot.addSeries(hrSeries, formatter);
                heartRatePlot.redraw();
                //textViewHR.setText(String.valueOf(polarHrData.hr));
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
                Log.d(TAG, "Polar FTP ready " + s);
            }
        });

        try {
            api.connectToDevice(deviceId);
        } catch (Exception e) {
            e.printStackTrace();
        }


        Button stopRecording = findViewById(R.id.stopRecording);
        stopRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ecg.toArray().length < 767) {
                    Toast.makeText(LiveActivity.this, "Too short signal: " + String.valueOf(ecg.toArray().length), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(LiveActivity.this, "Recording stopped", Toast.LENGTH_SHORT).show();

                    // Get recording information from Firebase
                    FirebaseDatabase database = FirebaseDatabase.getInstance();
                    DatabaseReference profileGetRef = database.getReference("profiles");
                    recordingRef = profileGetRef.child(userID).child("recordings").child(recID);

                    recordingRef.child("hr_data").setValue(hrArray);
                    recordingRef.child("ecg_data").setValue("hola");
                    // Divide the signal
                    int i = 1;
                    while (ecg.toArray().length > THREE_SEC_SIGNAL_LEN * i) {
                        // Compress the signal
                        float[] compressedSignal = compressor(ecg.subList(THREE_SEC_SIGNAL_LEN * (i - 1), THREE_SEC_SIGNAL_LEN * i));
                        i++;
                        // Upload everything in Firebase
                        if (compressedSignal != null) {
                            ArrayList<Float> result = new ArrayList<Float>(compressedSignal.length);
                            for (float f : compressedSignal) {
                                result.add(Float.valueOf(f));
                            }
                         recordingRef.child("hr_compressed_data ").setValue(result);
                        }
                    }
                    Intent intent = new Intent(LiveActivity.this, ResultsActivity.class);
                    if (ecg.toArray().length > 768) {
                        intent.putExtra(SIGNAL, preprocessSignal(ecg.subList(0, 768)));
                    }
                    startActivity(intent);
                }
            }
        });

        Intent intentFromRec = getIntent();
        userID = intentFromRec.getStringExtra(EditProfileActivity.USER_ID);
        recID = intentFromRec.getStringExtra(MainActivity.RECORDING_ID);
/*
        getSupportActionBar().setTitle(mDeviceName);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);*/
//        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
//        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && (checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_DENIED || checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION") == PackageManager.PERMISSION_DENIED || checkSelfPermission("android.permission.INTERNET") == PackageManager.PERMISSION_DENIED)) {
//            requestPermissions(new String[]{"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.INTERNET"}, 0);
//        }


        //Configure HR Plot
        heartRatePlot = findViewById(R.id.HRplot);
        configurePlot();

        //Initialize plot
        xyPlotSeriesList = new XYplotSeriesList();
        LineAndPointFormatter formatter = new LineAndPointFormatter(RED, TRANSPARENT,
                TRANSPARENT, null);
        formatter.getLinePaint().setStrokeWidth(8);
        xyPlotSeriesList.initializeSeriesAndAddToList(HR_PLOT, MIN_HR, NUMBER_OF_POINTS,
                formatter);
        XYSeries HRseries = new SimpleXYSeries(xyPlotSeriesList.getSeriesFromList(HR_PLOT),
                SimpleXYSeries.ArrayFormat.XY_VALS_INTERLEAVED, HR_PLOT);
        heartRatePlot.clear();
        heartRatePlot.addSeries(HRseries, formatter);
        heartRatePlot.redraw();
    }

    @Override
    protected void onResume() {
        super.onResume();
    /*    registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }*/
    }

    @Override
    protected void onPause() {
        super.onPause();
/*
        unregisterReceiver(mGattUpdateReceiver);
*/
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        api.shutDown();
    }

    private float[] preprocessSignal(List<Integer> input_sig) {

        float[] input_signal = new float[input_sig.size()];
        float[] x = new float[input_sig.size()];
        long sum = 0;
        long variance = 0;
        TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 768}, DataType.FLOAT32);
        inputFeature0.loadArray(input_signal);
        // Runs model inference and gets resul
        int i = 0;
        Iterator<Integer> it = input_sig.iterator();
        while (it.hasNext()) {
            Integer val = it.next();
            input_signal[i] = (float) val;
            sum += val;
            variance += Math.pow(val, 2);
            x[i] = i;
            i++;
        }
        float mean = sum / input_signal.length;
        float std = (float) Math.sqrt(variance);
        for (int j = 0; j < input_signal.length; j++) {
            input_signal[j] = (input_signal[j] - mean) / std; // signal standardization
        }

        // Detrend
        input_signal = detrend(x, input_signal);

        return input_signal;
    }

    private float[] compressor(List<Integer> input_sig) {

        float[] input_signal = preprocessSignal(input_sig);
        Context context = getApplicationContext();
        TensorBuffer outputFeature0 = null;
        switch (mCompressionRate) {
            case 4:
                try {
                    CompressionNn1 model = CompressionNn1.newInstance(context);

                    // Creates inputs for reference.
                    TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 768}, DataType.FLOAT32);
                    inputFeature0.loadArray(input_signal);

                    // Runs model inference and gets result.
                    CompressionNn1.Outputs outputs = model.process(inputFeature0);
                    outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

                    // Releases model resources if no longer used.
                    model.close();
                } catch (IOException e) {
                    // TODO Handle the exception
                }

                break;
            case 6:
                try {
                    CompressionNn0 model = CompressionNn0.newInstance(context);

                    // Creates inputs for reference.
                    TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 768}, DataType.FLOAT32);
                    inputFeature0.loadArray(input_signal);

                    // Runs model inference and gets result.
                    CompressionNn0.Outputs outputs = model.process(inputFeature0);
                    outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

                    // Releases model resources if no longer used.
                    model.close();
                } catch (IOException e) {
                    // TODO Handle the exception
                }
                break;
            case 2:
                try {
                    CompressionNn2 model = CompressionNn2.newInstance(context);

                    // Creates inputs for reference.
                    TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 768}, DataType.FLOAT32);
                    inputFeature0.loadArray(input_signal);

                    // Runs model inference and gets result.
                    CompressionNn2.Outputs outputs = model.process(inputFeature0);
                    outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

                    // Releases model resources if no longer used.
                    model.close();
                } catch (IOException e) {
                    // TODO Handle the exception
                }
                break;
        }

        return outputFeature0.getFloatArray();
    }

    public static float[] detrend(float[] x, float[] y) {

        if (x.length != y.length)
            throw new IllegalArgumentException("The x and y data elements needs to be of the same length");

        LinearRegression regression = new LinearRegression(x, y);

        double slope = regression.slope();
        double intercept = regression.intercept();

        for (int i = 0; i < x.length; i++) {
            //y -= intercept + slope * x
            y[i] -= intercept + (x[i] * slope);
        }
        return y;
    }


    private void configurePlot() {
        // Get background color from Theme
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.windowBackground, typedValue, true);
        int backgroundColor = typedValue.data;
        // Set background colors
        heartRatePlot.setPlotMargins(0, 0, 0, 0);
        heartRatePlot.getBorderPaint().setColor(backgroundColor);
        heartRatePlot.getBackgroundPaint().setColor(backgroundColor);
        heartRatePlot.getGraph().getBackgroundPaint().setColor(backgroundColor);
        heartRatePlot.getGraph().getGridBackgroundPaint().setColor(backgroundColor);
        // Set the grid color
        heartRatePlot.getGraph().getRangeGridLinePaint().setColor(Color.DKGRAY);
        heartRatePlot.getGraph().getDomainGridLinePaint().setColor(Color.DKGRAY);
        // Set the origin axes colors
        heartRatePlot.getGraph().getRangeOriginLinePaint().setColor(Color.DKGRAY);
        heartRatePlot.getGraph().getDomainOriginLinePaint().setColor(Color.DKGRAY);
        // Set the XY axis boundaries and step values
        heartRatePlot.setRangeBoundaries(MIN_HR, MAX_HR, BoundaryMode.FIXED);
        heartRatePlot.setDomainBoundaries(0, NUMBER_OF_POINTS - 1, BoundaryMode.FIXED);
        heartRatePlot.setRangeStepValue(9); // 9 values 40 60 ... 200
        heartRatePlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).setFormat(new
                DecimalFormat("#")); // Force the Axis to be integer
        heartRatePlot.setRangeLabel(getString(R.string.heart_rate));

        // Get recording information from Firebase
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference profileGetRef = database.getReference("profiles");
        recordingRef = profileGetRef.child(userID).child("recordings").child(recID);

        recordingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                /*TextView exerciseDatetime = findViewById(R.id.exerciseDateTimeLive);
                Long datetime = Long.parseLong(dataSnapshot.child("datetime").getValue().toString());
                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss", Locale.getDefault());
                exerciseDatetime.setText(formatter.format(new Date(datetime)));*/
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }
/*
    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void registerHeartRateService(
            List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic
                    gattCharacteristic : gattCharacteristics) {
                uuid = gattCharacteristic.getUuid().toString();
                // Find heart rate measurement (0x2A37)
                if (SampleGattAttributes.lookup(uuid, "unknown")
                        .equals("Heart Rate Measurement")) {
                    Log.i(TAG, "Registering for HR measurement");
                    mBluetoothLeService.setCharacteristicNotification(
                            gattCharacteristic, true);
                }
            }
        }
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    public void stopRecording(View view) {


    }*/



    public void streamECG() {
        if (ecgDisposable == null) {
            ecgDisposable =
                    api.requestEcgSettings(deviceId)
                            .toFlowable()
                            .flatMap((Function<PolarSensorSetting, Publisher<PolarEcgData>>) sensorSetting -> api.startEcgStreaming(deviceId, sensorSetting.maxSettings()))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    polarEcgData -> {
                                        Log.d(TAG, "ecg update, len is " + String.valueOf(ecg.toArray().length));
                                        for (Integer data : polarEcgData.samples) {
                                            //plotter.sendSingleSample((float) ((float) data / 1000.0));
                                            ecg.add(data);
                                            Toast.makeText(LiveActivity.this, "sendSignSample", Toast.LENGTH_SHORT);
                                        }
                                    },
                                    throwable -> {
                                        Log.e(TAG,
                                                "" + throwable.getLocalizedMessage());
                                        ecgDisposable = null;
                                    },
                                    () -> Log.d(TAG, "complete")
                            );
        } else {
            // NOTE stops streaming if it is "running"
            ecgDisposable.dispose();
            ecgDisposable = null;
        }
    }
}

