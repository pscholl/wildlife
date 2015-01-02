package de.uni_freiburg.es.wildlife;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.AudioRecord;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by phil on 1/1/15.
 */
public class LifeSignService extends IntentService {
    private static final String ACTION_SENDLIFESIGN = "de.es.unifreiburg.sendlifesign";
    public static final String PREF_SERVERURL = "LifeSignServer";
    public static final String PREF_PERIOD = "LifeSignPeriodInSeconds";
    public static final String PREF_POWERSAVE = "LifeSignPowerSaving";
    public static final String TAG = LifeSignService.class.toString();
    public static final String PREF_SERVERURL_DEFAULT = "http://es.informatik.uni-freiburg.de:81/wildlifesign";
    public static final int PREF_PERIOD_DEFAULT = 12 * 60 * 60;
    private static MySharedPreferencesListener mSharedPrefsListener;
    private PendingIntent mNextSchedule;
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private boolean mGoogleApiConnected = false;
    private int NETWORK_TIMEOUT_IN_MS  = 30 * 1000;

    public LifeSignService() {
        super(LifeSignService.class.toString());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Intent i = new Intent(this, LifeSignService.class);
        i.setAction(ACTION_SENDLIFESIGN);
        mNextSchedule = PendingIntent.getService(this, 0, i, 0);
    }

    public static String getServerURL(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c).getString(
                PREF_SERVERURL, PREF_SERVERURL_DEFAULT);
    }

    public boolean setServerURL(Context c, String url) {
        boolean success = PreferenceManager.getDefaultSharedPreferences(c).edit().putString(
                PREF_SERVERURL, url).commit();
        return success;
    }

    public static Integer getPeriodInSeconds(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c).getInt(
                PREF_PERIOD, PREF_PERIOD_DEFAULT);
    }

    public static boolean setPeriodInSeconds(Context c, int period) {
        boolean success = PreferenceManager.getDefaultSharedPreferences(c).edit().putInt(
                PREF_PERIOD, period).commit();
        return success;
    }

    public static boolean getPowerSavingMode(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c).getBoolean(PREF_POWERSAVE,
                false);
    }

    public static boolean setPowerSavingMode(Context c, boolean save_power) {
        boolean success = PreferenceManager.getDefaultSharedPreferences(c).edit().putBoolean(
                PREF_POWERSAVE, save_power).commit();
        return success;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(ACTION_SENDLIFESIGN))
            handleSendLifeSign();
    }

    public static void startLifesigning(Context ctx) {
        Intent i = new Intent(ctx, LifeSignService.class);
        i.setAction(ACTION_SENDLIFESIGN);
        ctx.startService(i);
    }

    /** This function is called whenever one of the preferences is changed through
     * the methods outlined above. This is acheived with an observer on the shared preferences
     * which needs to be instantiated, so one call to startLifesigning is neccesary. */
    private void handleSendLifeSign() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isEnabled   = prefs.getBoolean(AudioRecorderService.PREF_RECORD_ON,
                                               AudioRecorderService.PREF_RECORD_ON_DEFAULT),
                isSavePower = prefs.getBoolean(PREF_POWERSAVE, false);
        long period_ms      = prefs.getInt(PREF_PERIOD, PREF_PERIOD_DEFAULT) * 1000l;
        String server_url   = prefs.getString(PREF_SERVERURL,
                                              PREF_SERVERURL_DEFAULT);
        AlarmManager mgr = (AlarmManager) getSystemService(ALARM_SERVICE);

        Log.d(TAG, "starting lifesigning");

        /* make sure that we're listening to changes on the sharedpreferences */
        if (mSharedPrefsListener == null) {
            mSharedPrefsListener = new MySharedPreferencesListener();
            prefs.registerOnSharedPreferenceChangeListener(mSharedPrefsListener);
        }

        /* cancel all pending schedules of this alarm */
        mgr.cancel(mNextSchedule);

        /* do nothing if not enabled */
        if (!isEnabled) return;

        /* disable power saving */
        disablePowerSaving();

        /* wait for connection */
        if (waitForInetConnection(NETWORK_TIMEOUT_IN_MS))
        {
            try {
                OkHttpClient c = new OkHttpClient();
                Request req = new Request.Builder()
                    .url(server_url)
                    .post(RequestBody.create(JSON, getLifeSignJSON()))
                    .build();
                Response rsp = c.newCall(req).execute();
                Log.d(TAG, "response to lifesign: " + rsp.toString());
            } catch (IOException e) {
                Log.d(TAG, "unable to post request", e);
            }
        }

        /* prime for next run */
        if (isSavePower) enablePowerSaving();
        mgr.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + period_ms,
                mNextSchedule);
    }

    private boolean waitForInetConnection(int timeout) {
        final ConnectivityManager cm =(ConnectivityManager)
                LifeSignService.this.getSystemService(Context.CONNECTIVITY_SERVICE);
        final boolean[] isConnected = new boolean[] {false};

        BroadcastReceiver recv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                isConnected[0] = activeNetwork != null &&
                                 activeNetwork.isConnectedOrConnecting();
            }
        };

        IntentFilter ifilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(recv, ifilter);

        int SLEEPTIME = 500;
        for (int i=0; i < timeout / SLEEPTIME && !isConnected[0]; i++) {
            SystemClock.sleep(SLEEPTIME);
        }

        unregisterReceiver(recv);
        return isConnected[0];
    }

    private String getLifeSignJSON() {
        StringBuilder msg = new StringBuilder();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String deviceid = AndroidUniqueDeviceID.getDeviceId(this);
        Location position = getLocation();
        float[] temperature = new OneShotSensorReader(Sensor.TYPE_AMBIENT_TEMPERATURE).get();
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);
        int batteryCapacity = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, 1),
            batteryLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 1);
        float batteryTemperature = (float)
                (batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.),
              batteryPercentage = (float) batteryLevel / batteryCapacity * 100.f;

        if (position != null)
            msg.append(String.format("position: { %f, %f }", position.getLatitude(), position.getLongitude()));
        if (temperature.length > 0)
            msg.append(String.format("temperature: %.2f\n", temperature));

        String storagePath = prefs.getString(AudioRecorderService.PREF_STORAGE_PATH,
                                    AudioRecorderService.PREF_STORAGE_PATH_DEFAULT);
        File storageDir = new File(storagePath);
        float spaceLeft = (float) (storageDir.getUsableSpace() / (1024 * 1024 * 1024.));

        msg.append(String.format("deviceid: %s\n", deviceid));
        msg.append(String.format("battery temperature: %.2f\n",batteryTemperature ));
        msg.append(String.format("battery charge: %.1f\n", batteryPercentage));
        msg.append(String.format("battery capacity: %d\n", batteryCapacity));
        msg.append(String.format("space left: %.4fGb\n", spaceLeft));
        msg.append(String.format("sample interval: %d\n",
                prefs.getInt(AudioRecorderService.PREF_SLEEP_TIME, 0)));
        msg.append(String.format("sample time: %d\n",
                prefs.getInt(AudioRecorderService.PREF_WINDOW_SIZE, 0)));
        msg.append(String.format("lifesign interval: %d\n",
                prefs.getInt(PREF_PERIOD, 0)));

        return msg.toString();
    }

    private void enablePowerSaving() {
        exec("settings put global airplane_mode_on 1");
        exec("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true");
    }

    private void disablePowerSaving() {
        exec("settings put global airplane_mode_on 0");
        exec("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false");
    }

    private void exec(String cmd) {
        try {
            Runtime r = Runtime.getRuntime();
            int result = r.exec(new String[] {"su", "-c", cmd}).waitFor();
            if (result != 0)
                Log.d(TAG, "executing " + cmd + " failed with " + Integer.toString(result));
        } catch (Exception e) {
            Log.d(TAG, "executing " + cmd + " failed", e);
        }
    }


    private Location getLocation() {
        GoogleApiClient g = new GoogleApiClient.Builder(LifeSignService.this)
                .addApi(LocationServices.API)
                .build();

        ConnectionResult res = g.blockingConnect(1, TimeUnit.SECONDS);
        Location position = null;

        if (!res.isSuccess())
            Log.d(TAG, "unable to connect " + res.toString());
        else
            position = LocationServices.FusedLocationApi.getLastLocation(g);

        return position;
    }

    private class MySharedPreferencesListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(PREF_SERVERURL) || key.equals(PREF_PERIOD) || key.equals(PREF_POWERSAVE) ||
                key.equals(AudioRecorderService.PREF_RECORD_ON))
                startLifesigning(LifeSignService.this);
        }
    }

    private class OneShotSensorReader implements SensorEventListener {
        private final Sensor mSensor;
        private final SensorManager mSensorMgr;
        private float[] mValues = null;

        public OneShotSensorReader(Integer sensor) {
            mSensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
            mSensor = mSensorMgr.getDefaultSensor(sensor);

            if (mSensor == null)
                mValues = new float[] {};
            else
                mSensorMgr.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            mValues = event.values;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public float[] get() {
            while (mValues == null)
                ;

            mSensorMgr.unregisterListener(this);
            return mValues;
        }
    }
}
