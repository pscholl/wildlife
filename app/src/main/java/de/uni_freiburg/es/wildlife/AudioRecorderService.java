package de.uni_freiburg.es.wildlife;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaRouter;
import android.media.MediaScannerConnection;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javaFlacEncoder.FLACEncoder;
import javaFlacEncoder.FLACFileOutputStream;
import javaFlacEncoder.StreamConfiguration;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p/>
 * helper methods.
 */
public class AudioRecorderService extends IntentService {
    public static final String PREF_WINDOW_SIZE = "window size_in_seconds";
    public static final int PREF_WINDOW_SIZE_DEFAULT = 1 * 60; // in seconds
    public static final String PREF_SLEEP_TIME = "sleep_time_in_seconds";
    public static final int PREF_SLEEP_TIME_DEFAULT = 9 * 60;
    public static final String PREF_STORAGE_PATH = "storage_path";
    public static final String PREF_STORAGE_PATH_DEFAULT =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString();
    public static final String PREF_RECORD_ON = "recording_on";
    public static final boolean PREF_RECORD_ON_DEFAULT = false;
    private static final String ACTION_RECORD = "de.uni_freiburg.es.wildlife.action.RECORD";
    public static final String IS_RECORDING = "is_recording";
    private final String TAG = this.getClass().getSimpleName();
    private SharedPreferences mPreferences;
    private DateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH.mm.ss'Z'");

    private static PendingIntent mNextSchedule;
    private boolean mIsRecording;

    private static class MyChangeListener implements
            SharedPreferences.OnSharedPreferenceChangeListener {

        private AudioRecorderService myRecorderService;
        private static MyChangeListener instance = null;

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(PREF_RECORD_ON) ||
                    key.equals(PREF_STORAGE_PATH) ||
                    key.equals(PREF_SLEEP_TIME) ||
                    key.equals(PREF_WINDOW_SIZE)) {
                /* this will restart an ongoing recording session or start a new one, if any
                 * of the above listed settings has changed. */
                myRecorderService.mIsRecording = false;
                startActionRecord(myRecorderService);
            }
        }

        public static MyChangeListener newInstance(AudioRecorderService s) {
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
        Intent intent = new Intent(context, AudioRecorderService.class);
        intent.setAction(ACTION_RECORD);
        context.startService(intent);
    }

    public AudioRecorderService() {
        super("DataRecorderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_RECORD.equals(action)) {
                boolean isRecordingEnabled = mPreferences.getBoolean(
                        PREF_RECORD_ON,
                        PREF_RECORD_ON_DEFAULT);

                if (!isRecordingEnabled)
                {
                    AlarmManager aman = (AlarmManager) getSystemService(ALARM_SERVICE);
                    aman.cancel(mNextSchedule);
                    mPreferences.edit().putBoolean(IS_RECORDING, false).commit();
                    return;
                }

                handleActionRecordAudio();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Intent i = new Intent(this, AudioRecorderService.class);
        i.setAction(ACTION_RECORD);
        mNextSchedule = PendingIntent.getService(this, 0, i, 0);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mIsRecording = false;
        mPreferences.registerOnSharedPreferenceChangeListener(MyChangeListener.newInstance(this));
    }

    private void handleActionRecordAudio() {
        File storagePath = new File(mPreferences.getString(
                PREF_STORAGE_PATH,
                PREF_STORAGE_PATH_DEFAULT));
        String filename = new File(storagePath,
                mDateFormat.format(new Date())+".flac").toString();
        long sample_time = (long) mPreferences.getInt(
                PREF_WINDOW_SIZE,
                PREF_WINDOW_SIZE_DEFAULT) * 1000, // from seconds to millis
             sleep_time = (long) mPreferences.getInt(
                PREF_SLEEP_TIME,
                PREF_SLEEP_TIME_DEFAULT) * 1000;

        Log.d(TAG, "starting ");

        if (!storagePath.canWrite())
            return;

        /* do the recording */
        doAudioRecording(filename, sample_time);

        /* schedule repetition, this whole process will be stopped when the next Record
         * Intent is triggered, and the recording is not enabled! */
        AlarmManager aman = (AlarmManager) getSystemService(ALARM_SERVICE);
        aman.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                 SystemClock.elapsedRealtime() + SystemClock.elapsedRealtime() % sleep_time,
                 mNextSchedule);
    }

    private void setIsRecording(boolean state) {
        mPreferences.edit().putBoolean(IS_RECORDING, state).commit();
        mIsRecording = state;
    }

    private void doAudioRecording(String filename, long sample_time_in_ms) {
        /* prepare the recorder */
        int samplerate = getMaxSampleRate();
        int BUFSIZE = AudioRecord.getMinBufferSize(samplerate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        int flacbuf[] = new int[BUFSIZE/2];
        short recbuf[] = new short[BUFSIZE/2];

        AudioRecord rec = new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT, samplerate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                BUFSIZE);

        //AudioManager audiom = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
        //audiom.setSpeakerphoneOn(true);

        StreamConfiguration flac = new StreamConfiguration();
        flac.setChannelCount(1);
        flac.setBitsPerSample(16);
        flac.setSampleRate(samplerate);

        try {
            for (Date beg = new Date();
                 new Date().getTime() - beg.getTime() < 2000 &&
                       rec.getState()!=rec.STATE_INITIALIZED;
                 Thread.sleep(100))
                ; // wait for init
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        setIsRecording(true);
        rec.startRecording();

        Log.e(TAG, "writing file to: " + filename);
        try {
            FLACEncoder fec = new FLACEncoder();
            FLACFileOutputStream fos = new FLACFileOutputStream(filename);
            fec.setStreamConfiguration(flac);
            fec.setOutputStream(fos);
            fec.openFLACStream();

            Date beg = new Date();

            while (mIsRecording && (new Date().getTime() - beg.getTime()) < sample_time_in_ms)
            {
                // additionally stop recording when mIsRecording changes to false, but only
                // after a full block has been encoded,
                int len = rec.read(recbuf, 0, recbuf.length);
                if (len < 0) {
                    Log.e(TAG, "failed to read from recorder: " + len);
                    break;
                }

                // transfer to int buffer
                for (int i=0; i<len; i++)
                    flacbuf[i] = recbuf[i];

                //fec.addSamples(flacbuf, len/2);
                fec.addSamples(flacbuf, len);

                while (fec.fullBlockSamplesAvailableToEncode() > 0)
                    fec.t_encodeSamples(fec.fullBlockSamplesAvailableToEncode(), false);
            }

            fec.encodeSamples(0, true);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "file not found: ", e);
        } catch (IOException e) {
            Log.e(TAG, "io problem: ", e);
        }

        setIsRecording(false);
        rec.stop();
        rec = null;
        Log.e(TAG, "recording finished");

        //audiom.setSpeakerphoneOn(false);
        MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{filename}, new String[]{"audio/flac"}, null);
    }

    public int getMaxSampleRate() {
        for (int rate : new int[] {96000, 48000, 44100, 22050, 11025, 8000})
            try {
                int BUFSIZE =
                        AudioRecord.getMinBufferSize(rate,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT);
                AudioRecord r = new AudioRecord(
                        MediaRecorder.AudioSource.CAMCORDER, rate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        BUFSIZE);
                return rate;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, String.format("check rate %d", rate));
            }

        return 0;
    }
}
