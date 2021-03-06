package de.uni_freiburg.es.wildlife;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.io.File;
import java.util.Date;

import wildlife.es.uni_freiburg.de.wildlifemonitor.R;

/**
 * Created by phil on 12/26/14.
 */
public class StatusFragment extends Fragment {
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";
    private View mRootView;
    private SharedPreferences mPreferences;
    private Handler mHandler;
    private Runnable mPostUpdate = new Runnable() {
        @Override
        public void run() {
            StatusFragment.this.updateStatus();
        }
    };
    private Date mRecordingDate = null;
    private SharedPreferences.OnSharedPreferenceChangeListener mChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (mRootView == null)
                return;

            ProgressBar progressBar = (ProgressBar) mRootView.findViewById(R.id.progressBar);
            boolean isRecording = mPreferences.getBoolean(
                    AudioRecorderService.IS_RECORDING,
                    false);

            progressBar.setProgressDrawable(isRecording ?
                    getResources().getDrawable(R.drawable.circular_progress) :
                    getResources().getDrawable(R.drawable.circular_progress_red));
            progressBar.setMax(isRecording ?
                    mPreferences.getInt(AudioRecorderService.PREF_WINDOW_SIZE, AudioRecorderService.PREF_WINDOW_SIZE_DEFAULT) * 1000:
                    mPreferences.getInt(AudioRecorderService.PREF_SLEEP_TIME, AudioRecorderService.PREF_SLEEP_TIME_DEFAULT) * 1000);
            progressBar.setProgress(1);
            mRecordingDate = new Date();
        }
    };

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static StatusFragment newInstance() {
        StatusFragment fragment = new StatusFragment();
        /*
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        */
        return fragment;
    }

    public StatusFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_wild_life_monitor, container, false);
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mHandler = new Handler();
        /**
         * make a large button for rec starting and stopping
         */
        View v = mRootView.findViewById(R.id.progress_layout);
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                final boolean isRecordingEnabled = mPreferences.getBoolean(
                        AudioRecorderService.PREF_RECORD_ON,
                        AudioRecorderService.PREF_RECORD_ON_DEFAULT);

                mPreferences.edit().putBoolean(AudioRecorderService.PREF_RECORD_ON,
                        !isRecordingEnabled).commit();
                return true;
            }
        });

        TextView devid = (TextView) mRootView.findViewById(R.id.deviceidtext);
        devid.setText(AndroidUniqueDeviceID.getDeviceId(getActivity()));

        mChangeListener.onSharedPreferenceChanged(mPreferences, null); // initialize
        mPreferences.registerOnSharedPreferenceChangeListener(mChangeListener);
        updateStatus();

        return mRootView;
    }

    @Override
    public void onDestroyView() {
        mHandler = null;
        mPreferences.unregisterOnSharedPreferenceChangeListener(mChangeListener);
        super.onDestroyView();
    }

    public void updateStatus() {
        if (mRootView == null)
            return;

        if (getActivity() == null)
            return;

        /**
         *  modify storage status line
         */
        String storagePath = mPreferences.getString(AudioRecorderService.PREF_STORAGE_PATH,
                                            AudioRecorderService.PREF_STORAGE_PATH_DEFAULT);
        File storageDir = new File(storagePath);
        TextView event_status = (TextView) mRootView.findViewById(R.id.numevents);

        if (storageDir.canWrite()) {
            event_status.setText(String.format("%.1fGB of %.1fGB left",
                    storageDir.getUsableSpace() / (1024 * 1024 * 1024.),
                    storageDir.getTotalSpace() / (1024 * 1024 * 1024.)));
        } else {
            event_status.setText("unable to write " + storagePath);
        }

        /**
         * modify progress bar and status line
         */
        final boolean isRecordingEnabled = mPreferences.getBoolean(
                    AudioRecorderService.PREF_RECORD_ON,
                    AudioRecorderService.PREF_RECORD_ON_DEFAULT),
                      isRecording = mPreferences.getBoolean(
                        AudioRecorderService.IS_RECORDING, false);

        TextView status = (TextView) mRootView.findViewById(R.id.status);
        TextView next = (TextView) mRootView.findViewById(R.id.nexttimer);
        ProgressBar progressBar = (ProgressBar) mRootView.findViewById(R.id.progressBar);

        if (!isRecordingEnabled) {
            progressBar.setProgress(0);
            status.setText(getResources().getString(R.string.not_enabled));
            mRecordingDate = null;
            next.setText("");
        } else {
            if (mRecordingDate == null)
                mRecordingDate = new Date();

            Date now = new Date();
            long diff = now.getTime() - mRecordingDate.getTime();
            progressBar.setProgress((int) diff);

            status.setText(isRecording ? getResources().getString(R.string.is_recording) : getResources().getString(R.string.is_sleeping));
            next.setText(SettingsFragment.toTimeString((1000 + progressBar.getMax() - diff)/1000, "0 sec") + " left");
        }

        mHandler.postDelayed(mPostUpdate, 20);
    }
}