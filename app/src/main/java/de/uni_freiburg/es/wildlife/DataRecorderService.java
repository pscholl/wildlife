package de.uni_freiburg.es.wildlife;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * helper methods.
 */
public class DataRecorderService extends IntentService {
    // TODO: Rename actions, choose action names that describe tasks that this
    // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
    private static final String ACTION_RECORD = "de.uni_freiburg.es.wildlife.action.RECORD";
    public static final String IS_RECORDING = "is_recording";
    private final String TAG = this.getClass().getSimpleName();
    private SharedPreferences mPreferences;
    private DateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH.mm.ss'Z'");

    private static PendingIntent mNextSchedule;
    private boolean mIsRecording;

    private static class MyChangeListener implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private DataRecorderService myRecorderService;
        private static MyChangeListener instance = null;

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(SettingsFragment.PREF_RECORD_ON) ||
                    key.equals(SettingsFragment.PREF_STORAGE_PATH) ||
                    key.equals(SettingsFragment.PREF_SLEEP_TIME) ||
                    key.equals(SettingsFragment.PREF_WINDOW_SIZE)) {
                myRecorderService.mIsRecording = false;
                startActionRecord(myRecorderService);
            }
        }

        public static MyChangeListener newInstance(DataRecorderService s) {
            if (instance == null)
                instance = new MyChangeListener();
            instance.myRecorderService = s;
            return instance;
        }
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    // TODO: Customize helper method
    public static void startActionRecord(Context context) {
        Intent intent = new Intent(context, DataRecorderService.class);
        intent.setAction(ACTION_RECORD);
        context.startService(intent);
    }

    public DataRecorderService() {
        super("DataRecorderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_RECORD.equals(action))
                handleActionRecord();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Intent i = new Intent(this, DataRecorderService.class);
        i.setAction(ACTION_RECORD);
        mNextSchedule = PendingIntent.getService(this, 0, i, 0);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mIsRecording = false;
        mPreferences.registerOnSharedPreferenceChangeListener(MyChangeListener.newInstance(this));
    }

    private void handleActionRecord() {
        File storagePath = new File(mPreferences.getString(
                SettingsFragment.PREF_STORAGE_PATH,
                SettingsFragment.PREF_STORAGE_PATH_DEFAULT));
        String filename = new File(storagePath, mDateFormat.format(new Date())+".aac").toString();
        long sample_time = (long) mPreferences.getInt(
                SettingsFragment.PREF_WINDOW_SIZE,
                SettingsFragment.PREF_WINDOW_SIZE_DEFAULT) * 1000, // from seconds to millis
             sleep_time = (long) mPreferences.getInt(
                 SettingsFragment.PREF_SLEEP_TIME,
                 SettingsFragment.PREF_SLEEP_TIME_DEFAULT) * 1000;

        Log.d(TAG, "starting ");

        boolean isRecordingEnabled = mPreferences.getBoolean(
                SettingsFragment.PREF_RECORD_ON,
                SettingsFragment.PREF_RECORD_ON_DEFAULT);

        if (!isRecordingEnabled)
        {
            AlarmManager aman = (AlarmManager) getSystemService(ALARM_SERVICE);
            aman.cancel(mNextSchedule);
            mPreferences.edit().putBoolean(IS_RECORDING, false).commit();
            return;
        }

        if (!storagePath.canWrite())
            throw new UnsupportedOperationException(
                    "storage path is not writable: " + storagePath.toString());

        /* prepare the recorder */
        MediaRecorder rec = new MediaRecorder();
        rec.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        rec.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        rec.setAudioSamplingRate(getMaxSampleRate());
        rec.setOutputFile(filename);

        Log.e(TAG, "writing file to: " + filename);

        try { /* do the recording */
            rec.prepare();
            rec.start();
            mPreferences.edit().putBoolean(IS_RECORDING, true).commit();

            mIsRecording = true;
            Date beginning = new Date();
            while (mIsRecording &&
                   new Date().getTime() - beginning.getTime() < sample_time)
                Thread.sleep(500);

            rec.stop();
            rec.release();
            rec = null;
        } catch (IOException e) {
            Log.e(TAG, "prepare() failed: ", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "sleep disturbed: ", e);
        }

        Log.e(TAG, "recording finished");
        mPreferences.edit().putBoolean(IS_RECORDING, false).commit();

        isRecordingEnabled = mPreferences.getBoolean(
                SettingsFragment.PREF_RECORD_ON,
                SettingsFragment.PREF_RECORD_ON_DEFAULT);

        /* schedule repetition */
        if (isRecordingEnabled)
        {
            AlarmManager aman = (AlarmManager) getSystemService(ALARM_SERVICE);
            aman.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                     SystemClock.elapsedRealtime() + sleep_time,
                     mNextSchedule);
        }

        enableLowPowerMode();
    }

    private void enableLowPowerMode() {
        boolean isLowPowerEnabled = mPreferences.getBoolean(
                SettingsFragment.PREF_LOW_POWERMODE,
                SettingsFragment.PREF_LOW_POWERMODE_DEFAULT);

        if (isLowPowerEnabled) {
            // needs system privs
            //Settings.Global.putInt(getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 1);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", true);
            sendBroadcast(intent);
        }
    }

    public int getMaxSampleRate() {
        for (int rate : new int[] {96000, 48000, 44100, 22050, 11025, 8000})
            if (AudioRecord.getMinBufferSize(rate,
                    AudioFormat.CHANNEL_IN_DEFAULT,
                    AudioFormat.ENCODING_PCM_16BIT) > 0)
                return rate;

        return 0;
    }
}
