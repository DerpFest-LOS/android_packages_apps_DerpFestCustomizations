/*
 * Copyright (C) 2019 AospExtended ROM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.derpfest.customizations.fragment;

import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.widget.EditText;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;

import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.Date;
import java.util.List;

@SearchIndexable
public class ClockSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String STATUSBAR_CLOCK_AM_PM_STYLE = "statusbar_clock_am_pm_style";
    private static final String STATUSBAR_CLOCK_DATE_DISPLAY = "statusbar_clock_date_display";
    private static final String STATUSBAR_CLOCK_DATE_STYLE = "statusbar_clock_date_style";
    private static final String STATUSBAR_CLOCK_DATE_FORMAT = "statusbar_clock_date_format";
    private static final String STATUSBAR_CLOCK_DATE_POSITION = "statusbar_clock_date_position";
    private static final String STATUSBAR_CLOCK = "statusbar_clock";
    private static final String STATUSBAR_CLOCK_STYLE = "statusbar_clock_style";
    private static final String STATUSBAR_CLOCK_SECONDS = "statusbar_clock_seconds";
    private static final String STATUSBAR_CLOCK_AUTO_HIDE = "statusbar_clock_auto_hide";
    private static final String STATUSBAR_CLOCK_AUTO_HIDE_HDURATION = "statusbar_clock_auto_hide_hduration";
    private static final String STATUSBAR_CLOCK_AUTO_HIDE_SDURATION = "statusbar_clock_auto_hide_sduration";

    private static final int AM_PM_STYLE_GONE = 2;

    private static final int CLOCK_DATE_DISPLAY_GONE = 0;

    private static final int CLOCK_DATE_STYLE_NORMAL = 0;
    private static final int CLOCK_DATE_STYLE_LOWERCASE = 1;
    private static final int CLOCK_DATE_STYLE_UPPERCASE = 2;

    private static final int CLOCK_STYLE_LEFT = 0;

    private static final int DEFAULT_CLOCK_DATE_FORMAT_INDEX = 9;
    private static final int CUSTOM_CLOCK_DATE_FORMAT_INDEX = 18;

    private ListPreference mStatusBarAmPm;
    private ListPreference mClockDateDisplay;
    private ListPreference mClockDateStyle;
    private ListPreference mClockDateFormat;
    private ListPreference mClockDatePosition;
    private ListPreference mClockStyle;
    private Preference mClockSeconds;
    private Preference mClockAutoHide;
    private Preference mClockAutoHideHDuration;
    private Preference mClockAutoHideSDuration;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.statusbar_clock);

        final ContentResolver resolver = getActivity().getContentResolver();

        mStatusBarAmPm = (ListPreference) findPreference(STATUSBAR_CLOCK_AM_PM_STYLE);
        mClockDateDisplay = (ListPreference) findPreference(STATUSBAR_CLOCK_DATE_DISPLAY);
        mClockDateStyle = (ListPreference) findPreference(STATUSBAR_CLOCK_DATE_STYLE);
        mClockDateFormat = (ListPreference) findPreference(STATUSBAR_CLOCK_DATE_FORMAT);
        mClockDatePosition = (ListPreference) findPreference(STATUSBAR_CLOCK_DATE_POSITION);
        mClockStyle = (ListPreference) findPreference(STATUSBAR_CLOCK_STYLE);
        mClockSeconds = findPreference(STATUSBAR_CLOCK_SECONDS);
        mClockAutoHide = findPreference(STATUSBAR_CLOCK_AUTO_HIDE);
        mClockAutoHideHDuration = findPreference(STATUSBAR_CLOCK_AUTO_HIDE_HDURATION);
        mClockAutoHideSDuration = findPreference(STATUSBAR_CLOCK_AUTO_HIDE_SDURATION);

        if (DateFormat.is24HourFormat(getActivity())) {
            mStatusBarAmPm.setEnabled(false);
            mStatusBarAmPm.setSummary(R.string.status_bar_am_pm_info);
        }

        mClockDateDisplay.setOnPreferenceChangeListener(this);
        mClockDateStyle.setOnPreferenceChangeListener(this);
        mClockStyle.setOnPreferenceChangeListener(this);

        String clockFormat = Settings.System.getStringForUser(resolver,
                 Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, UserHandle.USER_CURRENT);
        if (TextUtils.isEmpty(clockFormat)) {
            clockFormat = "EEE";
        }
        final int index = mClockDateFormat.findIndexOfValue((String) clockFormat);
        if (index == -1 || index == CUSTOM_CLOCK_DATE_FORMAT_INDEX) {
            mClockDateFormat.setValueIndex(CUSTOM_CLOCK_DATE_FORMAT_INDEX);
        } else {
            mClockDateFormat.setValue(clockFormat);
        }
        mClockDateFormat.setSummary(clockFormat);
        mClockDateFormat.setOnPreferenceChangeListener(this);

        parseClockDateFormats(Settings.System.getIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_STYLE, CLOCK_DATE_STYLE_NORMAL,
                UserHandle.USER_CURRENT));
        setDateOptions(Settings.System.getIntForUser(resolver,
                Settings.System.STATUSBAR_CLOCK_DATE_DISPLAY, CLOCK_DATE_DISPLAY_GONE,
                UserHandle.USER_CURRENT) != CLOCK_DATE_DISPLAY_GONE);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mClockDateDisplay) {
            setDateOptions(Integer.parseInt((String) newValue) != CLOCK_DATE_DISPLAY_GONE);
            return true;
        }
        if (preference == mClockDateStyle) {
            parseClockDateFormats(Integer.parseInt((String) newValue));
            return true;
        }
        if (preference == mClockStyle) {
            return true;
        }
        if (preference == mClockDateFormat) {
            final int index = mClockDateFormat.findIndexOfValue((String) newValue);
            if (index == CUSTOM_CLOCK_DATE_FORMAT_INDEX) {
                final EditText input = new EditText(getActivity());
                final String oldText = Settings.System.getStringForUser(
                        getActivity().getContentResolver(),
                        Settings.System.STATUSBAR_CLOCK_DATE_FORMAT,
                        UserHandle.USER_CURRENT);
                if (oldText != null) {
                    input.setText(oldText);
                }

                new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.clock_date_string_edittext_title)
                    .setMessage(R.string.clock_date_string_edittext_summary)
                    .setView(input)
                    .setPositiveButton(R.string.menu_save, (dialog, which) -> {
                        String clockFormat = input.getText().toString();
                        if (TextUtils.isEmpty(clockFormat)) {
                            clockFormat = "EEE";
                        }
                        Settings.System.putStringForUser(getActivity().getContentResolver(),
                                Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, clockFormat,
                                UserHandle.USER_CURRENT);
                        mClockDateFormat.setSummary(clockFormat);
                    })
                    .setNegativeButton(R.string.menu_cancel, (dialog, which) -> {
                        String clockFormat = Settings.System.getStringForUser(
                                getActivity().getContentResolver(),
                                Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, UserHandle.USER_CURRENT);
                        if (TextUtils.isEmpty(clockFormat)) {
                            clockFormat = "EEE";
                        }
                        mClockDateFormat.setSummary(clockFormat);
                    })
                    .create()
                    .show();
            } else {
                Settings.System.putStringForUser(getActivity().getContentResolver(),
                        Settings.System.STATUSBAR_CLOCK_DATE_FORMAT, (String) newValue,
                        UserHandle.USER_CURRENT);
            }
            mClockDateFormat.setSummary((String) newValue);
            return true;
        }
        return false;
    }

    private void parseClockDateFormats(int dateStyle) {
        final String[] dateEntries = getResources().getStringArray(R.array.clock_date_format_entries_values);
        final CharSequence parsedDateEntries[] = new CharSequence[dateEntries.length];
        for (int i = 0; i < dateEntries.length; i++) {
            if (i == dateEntries.length - 1) {
                parsedDateEntries[i] = dateEntries[i];
            } else {
                final CharSequence dateString = DateFormat.format(dateEntries[i], new Date());
                final String newDate;
                if (dateStyle == CLOCK_DATE_STYLE_LOWERCASE) {
                    newDate = dateString.toString().toLowerCase();
                } else if (dateStyle == CLOCK_DATE_STYLE_UPPERCASE) {
                    newDate = dateString.toString().toUpperCase();
                } else {
                    newDate = dateString.toString();
                }
                parsedDateEntries[i] = newDate;
            }
        }
        mClockDateFormat.setEntries(parsedDateEntries);
    }

    private void setDateOptions(boolean showDate) {
        mClockDateStyle.setEnabled(showDate);
        mClockDateFormat.setEnabled(showDate);
        mClockDatePosition.setEnabled(showDate);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.DERPFEST;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.statusbar_clock) {

                @Override
                public List<String> getNonIndexableKeys(Context context) {
                    List<String> keys = super.getNonIndexableKeys(context);

                    return keys;
                }
            };
}
