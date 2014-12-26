package de.uni_freiburg.es.wildlife;

import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.Set;

import wildlife.es.uni_freiburg.de.wildlifemonitor.R;

/**
 * Created by phil on 12/26/14.
 */
public class SettingsFragment extends Fragment {
    public static final String PREF_WINDOW_SIZE = "window size_in_seconds";
    public static final int PREF_WINDOW_SIZE_DEFAULT = 1 * 60; // in seconds
    public static final int PREF_WINDOW_SIZE_MAX = 2 * 60 * 60;
    public static final String PREF_SLEEP_TIME = "sleep_time_in_seconds";
    public static final int PREF_SLEEP_TIME_DEFAULT = 10*60;
    public static final String PREF_STORAGE_PATH = "storage_path";
    public static final String PREF_STORAGE_PATH_DEFAULT =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).toString();
    public static final String PREF_RECORD_ON = "recording_on";
    public static final boolean PREF_RECORD_ON_DEFAULT = false;
    public static final String PREF_LOW_POWERMODE = "lowpowerenabled";
    public static final boolean PREF_LOW_POWERMODE_DEFAULT = false;

    private SharedPreferences mPreferences;
    private View rootView;
    private SharedPreferences.Editor mEditor;
    private Handler mHandler;
    private java.lang.Runnable mCommitEditor = new Runnable() {
        @Override
        public void run() {
            if (mEditor==null)
                return;
            mEditor.commit();
            mEditor = null;
            return;
        }
    };
    private SharedPreferences.OnSharedPreferenceChangeListener mListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(PREF_RECORD_ON)) {
                Switch rsw = (Switch) rootView.findViewById(R.id.recording_enabled_switch);
                rsw.setOnCheckedChangeListener(null);
                rsw.setChecked(mPreferences.getBoolean(PREF_RECORD_ON, PREF_RECORD_ON_DEFAULT));
                rsw.setOnCheckedChangeListener(new MySwitchListener(PREF_RECORD_ON));
            }
        }
    };

    public static SettingsFragment newInstance() {
        SettingsFragment f = new SettingsFragment();
        return f;
    }

    public SettingsFragment() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPreferences.unregisterOnSharedPreferenceChangeListener(mListener);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_wild_life_settings, container, false);
        long ws = (long) mPreferences.getInt(PREF_WINDOW_SIZE, PREF_WINDOW_SIZE_DEFAULT);
        TextView wsv = (TextView) rootView.findViewById(R.id.window_size_value);
        wsv.setText( toTimeString(ws));
        SeekBar wsb = (SeekBar) rootView.findViewById(R.id.windows_size_bar);
        wsb.setMax(PREF_WINDOW_SIZE_MAX);
        wsb.setProgress((int) ws);
        wsb.setOnSeekBarChangeListener(new MySeekBarListener(wsv, PREF_WINDOW_SIZE));

        long sv = (long) mPreferences.getInt(PREF_SLEEP_TIME, PREF_SLEEP_TIME_DEFAULT);
        TextView ssv = (TextView) rootView.findViewById(R.id.sleep_time);
        ssv.setText( toTimeString(sv) );
        SeekBar ssb = (SeekBar) rootView.findViewById(R.id.sleep_time_bar);
        ssb.setMax(PREF_WINDOW_SIZE_MAX);
        ssb.setProgress((int) sv);
        ssb.setOnSeekBarChangeListener(new MySeekBarListener(ssv, PREF_SLEEP_TIME));

        boolean recordon = mPreferences.getBoolean(PREF_RECORD_ON, PREF_RECORD_ON_DEFAULT);
        Switch rsw = (Switch) rootView.findViewById(R.id.recording_enabled_switch);
        rsw.setChecked(recordon);
        rsw.setOnCheckedChangeListener(new MySwitchListener(PREF_RECORD_ON));

        boolean lowpow = mPreferences.getBoolean(PREF_LOW_POWERMODE, PREF_LOW_POWERMODE_DEFAULT);
        Switch lpw = (Switch) rootView.findViewById(R.id.low_power_mode_switch);
        lpw.setChecked(lowpow);
        lpw.setOnCheckedChangeListener(new MySwitchListener(PREF_LOW_POWERMODE));

        mPreferences.registerOnSharedPreferenceChangeListener(mListener);

        mHandler = new Handler();
        return rootView;
    }

    public static String toTimeString(long aLong) {
        return toTimeString(aLong, "disabled");
    }

    public static String toTimeString(long aLong, String lastString) {
        long  DAY = 24*60*60,
              HOUR = DAY/24,
              MINUTE = HOUR/60;

        if (aLong > DAY)
            return String.format("%d day%s", aLong/DAY, aLong/DAY>1 ? "s":"");
        else if (aLong > HOUR)
            return String.format("%d hour%s", aLong/HOUR, aLong/HOUR>1 ? "s":"");
        else if (aLong > MINUTE)
            return String.format("%d min%s", aLong/MINUTE, aLong/MINUTE>1 ? "s":"");
        else if (aLong > 0)
            return String.format("%d sec%s", aLong, aLong>1 ? "s":"");
        else
            return lastString;
    }

    private class MySeekBarListener implements SeekBar.OnSeekBarChangeListener {
        private final TextView mTv;
        private final String mPref;

        public MySeekBarListener(TextView textView, String pref) {
            mTv = textView;
            mPref = pref;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mTv.setText(toTimeString(progress));
            if (mEditor==null) mEditor = mPreferences.edit();
            else mHandler.removeCallbacks(mCommitEditor);
            mEditor.putInt(mPref, progress);
            mHandler.postDelayed(mCommitEditor, 1000);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    }

    private class MySwitchListener implements CompoundButton.OnCheckedChangeListener {
        private final String mPref;

        public MySwitchListener(String pref) {
            mPref = pref;
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (mEditor==null) mEditor = mPreferences.edit();
            else mHandler.removeCallbacks(mCommitEditor);
            mEditor.putBoolean(mPref, isChecked);
            mHandler.postDelayed(mCommitEditor, 1000);
        }
    }
}
