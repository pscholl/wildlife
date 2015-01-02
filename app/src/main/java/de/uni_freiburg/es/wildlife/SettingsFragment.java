package de.uni_freiburg.es.wildlife;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import wildlife.es.uni_freiburg.de.wildlifemonitor.R;

/**
 * Created by phil on 12/26/14.
 */
public class SettingsFragment extends Fragment {
    private static final String TAG = SettingsFragment.class.getSimpleName();

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
            if (key.equals(AudioRecorderService.PREF_RECORD_ON)) {
                Switch rsw = (Switch) rootView.findViewById(R.id.recording_enabled_switch);
                rsw.setOnCheckedChangeListener(null);
                rsw.setChecked(mPreferences.getBoolean(
                        AudioRecorderService.PREF_RECORD_ON,
                        AudioRecorderService.PREF_RECORD_ON_DEFAULT));
                rsw.setOnCheckedChangeListener(new MySwitchListener(
                        AudioRecorderService.PREF_RECORD_ON));
            }
        }
    };
    private TimeTransformer mWsvListener, mSsvListener, mLspListener;

    public static SettingsFragment newInstance() {
        SettingsFragment f = new SettingsFragment();
        return f;
    }

    public SettingsFragment() {
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EditText wsv = (EditText) rootView.findViewById(R.id.window_size_value);
        wsv.removeTextChangedListener(mWsvListener);
        wsv.setOnFocusChangeListener(null);
        wsv.setOnKeyListener(null);
        wsv.setOnFocusChangeListener(null);

        EditText ssv = (EditText) rootView.findViewById(R.id.sleep_time);
        ssv.removeTextChangedListener(mSsvListener);
        ssv.setOnEditorActionListener(null);
        ssv.setOnKeyListener(null);
        ssv.setOnFocusChangeListener(null);

        EditText lsp = (EditText) rootView.findViewById(R.id.life_sign);
        lsp.removeTextChangedListener(mLspListener);
        lsp.setOnEditorActionListener(null);
        lsp.setOnKeyListener(null);
        lsp.setOnFocusChangeListener(null);
    }

    @Override
    public void onDetach() {
        super.onDetach();
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

        EditText wsv = (EditText) rootView.findViewById(R.id.window_size_value);
        mWsvListener = new TimeTransformer(wsv, AudioRecorderService.PREF_WINDOW_SIZE,
                                                AudioRecorderService.PREF_WINDOW_SIZE_DEFAULT);
        wsv.addTextChangedListener(mWsvListener);
        wsv.setOnEditorActionListener(mWsvListener);
        wsv.setOnKeyListener(mWsvListener);
        wsv.setOnFocusChangeListener(mWsvListener);

        EditText ssv = (EditText) rootView.findViewById(R.id.sleep_time);
        mSsvListener = new TimeTransformer(ssv, AudioRecorderService.PREF_SLEEP_TIME,
                                                AudioRecorderService.PREF_SLEEP_TIME_DEFAULT);
        ssv.addTextChangedListener(mSsvListener);
        ssv.setOnEditorActionListener(mSsvListener); // pushed enter
        ssv.setOnKeyListener(mSsvListener);          // pushed back
        ssv.setOnFocusChangeListener(mSsvListener);  // changed focus

        EditText lsp = (EditText) rootView.findViewById(R.id.life_sign);
        mLspListener = new TimeTransformer(lsp, LifeSignService.PREF_PERIOD,
                                                LifeSignService.PREF_PERIOD_DEFAULT);
        lsp.addTextChangedListener(mLspListener);
        lsp.setOnEditorActionListener(mLspListener);
        lsp.setOnKeyListener(mLspListener);
        lsp.setOnFocusChangeListener(mLspListener);

        EditText url = (EditText) rootView.findViewById(R.id.life_sign_uri);
        url.setText(LifeSignService.getServerURL(getActivity()));

        boolean recordon = mPreferences.getBoolean(AudioRecorderService.PREF_RECORD_ON,
                                           AudioRecorderService.PREF_RECORD_ON_DEFAULT);
        Switch rsw = (Switch) rootView.findViewById(R.id.recording_enabled_switch);
        rsw.setChecked(recordon);
        rsw.setOnCheckedChangeListener(new MySwitchListener(AudioRecorderService.PREF_RECORD_ON));

        boolean lowpow = LifeSignService.getPowerSavingMode(getActivity());
        Switch lpw = (Switch) rootView.findViewById(R.id.low_power_mode_switch);
        lpw.setChecked(lowpow);
        lpw.setOnCheckedChangeListener(new MySwitchListener(LifeSignService.PREF_POWERSAVE));

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

    private class TimeTransformer extends EditTextStorage implements TextWatcher {
        public static final String FORMAT = "%02dh%02dm%02ds";
        private int mHour;
        private int mMinutes;
        private int mSeconds;
        private CharSequence mCurrentText;

        public TimeTransformer(TextView tv, String key, int def) {
            super(tv, key);
            int seconds = mPreferences.getInt(key, def);
            mSeconds = (int) seconds % 60;
            mMinutes = (int) (seconds % (60 * 60)) / 60;
            mHour = (int) seconds / (60 * 60);
            mTv.setText(String.format(FORMAT, mHour, mMinutes, mSeconds));
        }

        public void parse(CharSequence s, int gap) {
            mHour = Integer.parseInt((String) s.subSequence(0, 2));
            mMinutes = Integer.parseInt((String) s.subSequence(2 + gap, 4 + gap));
            gap += gap;
            mSeconds = Integer.parseInt((String) s.subSequence(4 + gap, 6 + gap));
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            mCurrentText = s;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (count != 1)
                return;

            /* shift in the current input */
            char c = s.charAt(start);

            try {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%02d%02d%02d", mHour, mMinutes, mSeconds), 1, 6);
                sb.append(c);
                parse(sb, 0);
            } catch (NumberFormatException e) {
                ;
            }

            mTv.removeTextChangedListener(this);
            mTv.setText(String.format(FORMAT, mHour, mMinutes, mSeconds));
            mTv.addTextChangedListener(this);
        }

        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void store() {
            if (mSeconds > 59) {
                mMinutes += 1;
                mSeconds %= 60;
            }

            if (mMinutes > 59) {
                mHour += 1;
                mMinutes %= 60;
            }

            if (mHour > 99)
                mHour = 99;

            mTv.removeTextChangedListener(this);
            mTv.setText(String.format(FORMAT, mHour, mMinutes, mSeconds));
            mTv.addTextChangedListener(this);

            if (mEditor == null)
                mEditor = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();

            mEditor.putInt(mKey, mHour * 3600 + mMinutes * 60 + mSeconds);
            mHandler.removeCallbacks(mCommitEditor);
            mHandler.postDelayed(mCommitEditor, 10 * 1000);
        }
    }

    public class EditTextStorage  implements
                                    TextView.OnEditorActionListener,
                                    View.OnKeyListener,
                                    View.OnFocusChangeListener {
        final TextView mTv;
        final String mKey;

        public EditTextStorage(TextView tv, String preference_key) {
            if (mEditor == null)
                mEditor = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();

            mKey = preference_key;
            mTv = tv;
        }

        public void store() {
            if (mEditor == null)
                mEditor = PreferenceManager.getDefaultSharedPreferences(getActivity()).edit();

            mEditor.putString(mKey, mTv.getText().toString());
            mHandler.removeCallbacks(mCommitEditor);
            mHandler.postDelayed(mCommitEditor, 10 * 1000);
        }

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                store();
                return true;
            }

            return false;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                store();
                return true;
            }

            return false;
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus)
                store();
        }
    }
}
