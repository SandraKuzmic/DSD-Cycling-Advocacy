package com.skuzmic.gpstracker_sampleapp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.skuzmic.gpstracker_sampleapp.entities.ApiResponse;
import com.skuzmic.gpstracker_sampleapp.entities.GnssData;
import com.skuzmic.gpstracker_sampleapp.entities.Motion;
import com.skuzmic.gpstracker_sampleapp.entities.Trip;
import com.skuzmic.gpstracker_sampleapp.retrofit.RetrofitServiceGenerator;
import com.skuzmic.gpstracker_sampleapp.retrofit.service.BumpyService;
import com.skuzmic.gpstracker_sampleapp.utils.AccumulatedVibrationsManager;
import com.skuzmic.gpstracker_sampleapp.utils.CsvUtils;
import com.skuzmic.gpstracker_sampleapp.utils.PermissionUtils;
import com.skuzmic.gpstracker_sampleapp.utils.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, SensorEventListener, BackgroundLocationService.BackgroundLocationChangesListener {

    private static final long UPDATE_INTERVAL = 3000, FASTEST_INTERVAL = 3000; // = 3 seconds

    private TextView tvLocation;
    private Button btnStart;
    private Button btnStop;
    private TextView tvVibrations;

    private GoogleApiClient googleApiClient;
//    private FusedLocationProviderClient locationProviderClient;
//    private LocationCallback locationCallback;

    private BackgroundLocationService locationService;

    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer, gyroscope;

    // lists for permissions
    private ArrayList<String> permissionsToRequest;
    private ArrayList<String> permissionsRejected = new ArrayList<>();
    private ArrayList<String> permissions = new ArrayList<>();

    // integer for permissions results request
    private static final int ALL_PERMISSIONS_RESULT_REQ_CODE = 1997;

    private AccumulatedVibrationsManager accumulatedVibrationsManager;

    private Trip trip;

    private FileWriter fileWriter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvLocation = findViewById(R.id.textView);
        btnStart = findViewById(R.id.btnStart);
        tvVibrations = findViewById(R.id.tvVibrations);

        btnStart.setEnabled(false);

        btnStop = findViewById(R.id.btnStop);
        btnStop.setEnabled(false);

        // we add permissions we need to request from the users
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        permissionsToRequest = PermissionUtils.permissionsToRequest(this, permissions);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size() > 0) {
                requestPermissions(
                        permissionsToRequest.toArray(new String[permissionsToRequest.size()]),
                        ALL_PERMISSIONS_RESULT_REQ_CODE
                );
            }
        }

        // we build google api client
        googleApiClient = new GoogleApiClient.Builder(this).
                addApi(LocationServices.API).
                addConnectionCallbacks(this).
                addOnConnectionFailedListener(this)
                .build();

        /*
        locationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                // todo background problems in Oreo and Pie
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d("loc", "Update");

                        GnssData gnssData = new GnssData(location);
                        trip.addGpsData(gnssData);

                        tvLocation.append("\n\n" + gnssData.toString());
                        tvLocation.append("\nDistance(km): " + trip.getDistance());
                    }
                }
            }
        };
         */

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // todo check for nulls if the device does not have that sensor
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvLocation.append("\n***UnPaused***\n");
        if (!Utils.checkPlayServices(this)) {
            tvLocation.setText("You need to install Google Play Services to use the App properly");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        tvLocation.append("\n***Paused***\n");
    }

    @Override
    protected void onDestroy() {
        locationService.stopTracking();
        super.onDestroy();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // start service
        Intent intent = new Intent(this, BackgroundLocationService.class);
        startService(intent);
//        startForegroundService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        tvLocation.setText("GPS Connected successfully");
//        btnStart.setEnabled(true);
    }

    @Override
    public void onConnectionSuspended(int i) {
        // todo disable the button
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // todo disable the button
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == ALL_PERMISSIONS_RESULT_REQ_CODE) {
            for (String perm : permissionsToRequest) {
                if (!PermissionUtils.hasPermission(this, perm)) {
                    permissionsRejected.add(perm);
                }
            }

            if (permissionsRejected.size() > 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (shouldShowRequestPermissionRationale(permissionsRejected.get(0))) {
                        new AlertDialog.Builder(MainActivity.this).
                                setMessage("These permissions are mandatory to get your location. You need to allow them.").
                                setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            requestPermissions(permissionsRejected.
                                                    toArray(new String[permissionsRejected.size()]), ALL_PERMISSIONS_RESULT_REQ_CODE);
                                        }
                                    }
                                }).setNegativeButton("Cancel", null).create().show();
                    }
                }
            } else {
                if (googleApiClient != null) {
                    googleApiClient.connect();
                }
            }
        }
    }

    public void startTracking(View view) {
        tvLocation.setText("");
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);

        accumulatedVibrationsManager = new AccumulatedVibrationsManager();

        locationService.startTracking();

        //TODO: The device UUID hard-coded and given here is for the purposes of the alpha-prototype only an will be stored/managed elsewhere in the 'real' app
        trip = new Trip("5efa0f9f-ee0a-45c9-ac20-ac4bb76dc83f");
        trip.start();

//        startLocationUpdates();
        startSensorUpdates();
    }

    public void stopTracking(View view) {
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);

        accumulatedVibrationsManager = null;
        tvVibrations.setText("Vibrations: -%");

        locationService.stopTracking();

        trip.stop();

        // todo remove some of this
//        stopLocationUpdates();
        stopSensorUpdates();

        sendData();
    }

    private void sendData() {
        // If there is a parsing exception I assume we shouldn't send potentially 'broken' data, so duration = 0 should prevent that
        // TODO: Note that this calculation is a bit rough, in the future we should rely on a separate, more precise stopwatch that measures the duration of a trip
        Date startTS = trip.getStartTs();
        Date stopTS = trip.getStopTs();
        long duration = Utils.getDurationInSeconds(startTS, stopTS);

        double distance = trip.getDistance();

        if (duration >= Trip.MIN_TRIP_DURATION && distance >= Trip.MIN_TRIP_DISTANCE) {
            // Saves the trip general + location data as a txt file on the device
            trip.exportToTxt(this);

            sendLocationData(this);
            sendMotionData(this);
        } else {
            // The motion file is constructed during the trip so we need to delete it
            Utils.deleteMotionData(MainActivity.this, trip.getTripUUID());
            Log.d("Trip end", "Trip duration or distance too short for the trip to be considered.");
            Log.d("Trip end", "Trip duration is " + duration + " while minimum is " + Trip.MIN_TRIP_DURATION);
            Log.d("Trip end", "Trip distance is " + distance + " while minimum is " + Trip.MIN_TRIP_DISTANCE);
            Toast.makeText(this, "Trip duration or distance too short for the trip to be considered", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendLocationData(final Context context) {
        Log.d("Location data", "Uploading location data for trip " + trip.getTripUUID() + " to the server");

        BumpyService bumpyService = RetrofitServiceGenerator.createService(BumpyService.class);
        bumpyService.insertNewTrip(trip)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<Response<ApiResponse>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //Do nothing
                    }

                    @Override
                    public void onSuccess(Response<ApiResponse> response) {
                        Log.d("Location data", "Location data upload response for trip " + trip.getTripUUID() + ": " + response.message());
                        if (response.isSuccessful()) {
                            Toast.makeText(context, "Location data successfully uploaded!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(context, "Location data upload not successful!: " + response.message(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("Location data", "Failed to upload location data for trip " + trip.getTripUUID() + " to the server: " + e.getMessage());
                    }
                });
    }

    private void sendMotionData(final Context context) {
        Log.d("Motion data", "Uploading motion data for trip " + trip.getTripUUID() + " to the server");

        File motionDataFile = CsvUtils.getMotionDataFile(this, trip.getTripUUID());

        //TODO: This should look like 'RequestBody.create(MediaType.parse(getContentResolver().getType(Uri.fromFile(motionDataFile))), motionDataFile);' but something with the URI doesn't work
        RequestBody motionDataFileRB = RequestBody.create(MediaType.parse("text/csv"), motionDataFile);
        MultipartBody.Part motionDataFilePart = MultipartBody.Part.createFormData("file", motionDataFile.getName(), motionDataFileRB);

        BumpyService bumpyService = RetrofitServiceGenerator.createService(BumpyService.class);
        bumpyService.uploadMotionData(trip.getTripUUID(), motionDataFilePart)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<Response<ApiResponse>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //Do nothing
                    }

                    @Override
                    public void onSuccess(Response<ApiResponse> response) {
                        Log.d("Motion data", "Motion data upload response for trip " + trip.getTripUUID() + ": " + response.message());
                        if (response.isSuccessful()) {
                            Toast.makeText(context, "Motion data successfully uploaded!", Toast.LENGTH_SHORT).show();
                            Utils.deleteMotionData(MainActivity.this, trip.getTripUUID());
                        } else {
                            Toast.makeText(context, "Motion data upload not successful!: " + response.message(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.d("Motion data", "Failed to upload motion data for trip " + trip.getTripUUID() + " to the server: " + e.getMessage());
                    }
                });
    }

    private void startLocationUpdates() {
        final LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You need to enable permissions to display location !", Toast.LENGTH_SHORT).show();
        }

//        locationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
    }

    private void stopLocationUpdates() {
//        locationProviderClient.removeLocationUpdates(locationCallback);
    }

    private float[] accelerometerData = null;
    private float[] magnetometerData = null;
    private float[] gyroscopeData = null;

    private void startSensorUpdates() {
        try {
            fileWriter = CsvUtils.initFileWriter(this, trip.getTripUUID());
        } catch (IOException e) {
            e.printStackTrace();
        }

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);  // 50hz
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
    }

    private void stopSensorUpdates() {
        try {
            CsvUtils.finish(fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
        }

        sensorManager.unregisterListener(this, accelerometer);
        sensorManager.unregisterListener(this, magnetometer);
        sensorManager.unregisterListener(this, gyroscope);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // sensors are working in background as well
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerData = sensorEvent.values.clone();
        }
        if (sensorEvent.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magnetometerData = sensorEvent.values.clone();
        }
        if (sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeData = sensorEvent.values.clone();
        }

        if (accelerometerData != null && magnetometerData != null && gyroscopeData != null) {
            Motion motion = new Motion(accelerometerData, magnetometerData, gyroscopeData);
            try {
                CsvUtils.writeLine(fileWriter, motion.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }

            accumulatedVibrationsManager.addAccelerometerMeasurement(accelerometerData);
            tvVibrations.setText("Vibrations: " + (int) accumulatedVibrationsManager.getBumpPercentage() + "%");

            // clean
            accelerometerData = null;
            magnetometerData = null;
            gyroscopeData = null;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            String name = componentName.getClassName();
            // it is full name with package id
            if (name.endsWith("BackgroundLocationService")) {
                locationService = ((BackgroundLocationService.BackgroundLocationServiceBinder) iBinder).getService();
                locationService.setListener(MainActivity.this);
                tvLocation.append("SERVICE Connected successfully");
                btnStart.setEnabled(true);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            if (componentName.getClassName().endsWith("BackgroundLocationService")) {
                locationService = null;
            }
        }
    };

    @Override
    public void onLocationChanged(Location location) {
        GnssData gnssData = new GnssData(location);
        trip.addGpsData(gnssData);

        tvLocation.append("\n\n" + gnssData.toString());
        tvLocation.append("\nDistance(km): " + trip.getDistance());
    }
}