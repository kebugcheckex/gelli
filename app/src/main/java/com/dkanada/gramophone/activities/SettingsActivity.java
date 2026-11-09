package com.dkanada.gramophone.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.TwoStatePreference;

import com.dkanada.gramophone.databinding.ActivitySettingsBinding;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.views.shortcuts.DynamicShortcutManager;
import com.dkanada.gramophone.dialogs.preferences.CategoryPreferenceDialog;
import com.dkanada.gramophone.dialogs.preferences.ColorPresetDialog;
import com.dkanada.gramophone.dialogs.preferences.ListPreferenceDialog;
import com.dkanada.gramophone.activities.base.AbsBaseActivity;
import com.dkanada.gramophone.service.SaveTrigger;
import com.dkanada.gramophone.service.SaveWorker;
import com.dkanada.gramophone.util.NetworkUtil;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.dkanada.gramophone.util.ViewUtil;

public class SettingsActivity extends AbsBaseActivity {
    private ActivitySettingsBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, new SettingsFragment()).commit();
        } else {
            SettingsFragment fragment = (SettingsFragment) getSupportFragmentManager().findFragmentById(R.id.content_frame);
            if (fragment != null) fragment.invalidateSettings();
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(R.xml.pref_library);
            addPreferencesFromResource(R.xml.pref_interface);
            addPreferencesFromResource(R.xml.pref_notification);
            addPreferencesFromResource(R.xml.pref_lock_screen);
            addPreferencesFromResource(R.xml.pref_playback);
            addPreferencesFromResource(R.xml.pref_network);
            addPreferencesFromResource(R.xml.pref_local_library);
            addPreferencesFromResource(R.xml.pref_cache);
        }

        @Override
        public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            getListView().setPadding(0, 0, 0, 0);
            ViewUtil.capContentWidth(getListView());
            setDivider(null);
            invalidateSettings();
            PreferenceUtil.getInstance(getActivity()).registerOnSharedPreferenceChangedListener(this);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onDisplayPreferenceDialog(@NonNull Preference preference) {
            if (!(preference instanceof ListPreference)) {
                super.onDisplayPreferenceDialog(preference);
                return;
            }

            // a second tap can arrive before the dialog is on screen
            if (getParentFragmentManager().findFragmentByTag(ListPreferenceDialog.TAG) != null) {
                return;
            }

            // the library finds the preference through the target fragment
            ListPreferenceDialog dialog = ListPreferenceDialog.create(preference.getKey());
            dialog.setTargetFragment(this, 0);
            dialog.show(getParentFragmentManager(), ListPreferenceDialog.TAG);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            PreferenceUtil.getInstance(getActivity()).unregisterOnSharedPreferenceChangedListener(this);
        }

        @SuppressWarnings("ConstantConditions")
        private void invalidateSettings() {
            findPreference("downloads_storage").setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), DownloadsActivity.class));
                return true;
            });
            final TwoStatePreference classicNotification = findPreference(PreferenceUtil.CLASSIC_NOTIFICATION);
            final TwoStatePreference coloredNotification = findPreference(PreferenceUtil.COLORED_NOTIFICATION);
            final Preference categoryPreference = findPreference(PreferenceUtil.CATEGORIES);
            final Preference colorPresetPreference = findPreference(PreferenceUtil.COLOR_PRESET);
            final Preference showAlbumCoverPreference = findPreference(PreferenceUtil.SHOW_ALBUM_COVER);

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                // stock Android 11 removed the album cover on lock screens
                // supported on LineageOS so we might want to add a check at some point
                showAlbumCoverPreference.setEnabled(false);
            }

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                // custom notification layouts were removed entirely in Android 12
                classicNotification.setEnabled(false);
                coloredNotification.setEnabled(false);
            }

            categoryPreference.setOnPreferenceClickListener(preference -> {
                CategoryPreferenceDialog.create().show(getParentFragmentManager(), CategoryPreferenceDialog.TAG);
                return false;
            });

            colorPresetPreference.setSummary(PreferenceUtil.getInstance(requireContext()).getColorPreset().title);
            colorPresetPreference.setOnPreferenceClickListener(preference -> {
                ColorPresetDialog.create().show(getParentFragmentManager(), ColorPresetDialog.TAG);
                return true;
            });

            // use this to set default state for notification style and save trigger
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());

            onSharedPreferenceChanged(preferences, PreferenceUtil.CLASSIC_NOTIFICATION);
            onSharedPreferenceChanged(preferences, PreferenceUtil.LIBRARY_SAVE_TRIGGER);
        }

        @Override
        @SuppressWarnings("ConstantConditions")
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case PreferenceUtil.COLORED_SHORTCUTS:
                    new DynamicShortcutManager(requireContext()).updateDynamicShortcuts();
                    break;
                case PreferenceUtil.COLOR_PRESET:
                case PreferenceUtil.GENERAL_THEME:
                    new DynamicShortcutManager(requireContext()).updateDynamicShortcuts();

                    requireActivity().recreate();
                    break;
                case PreferenceUtil.CLASSIC_NOTIFICATION:
                    TwoStatePreference colorNotification = findPreference(PreferenceUtil.COLORED_NOTIFICATION);

                    colorNotification.setEnabled(sharedPreferences.getBoolean(key, false));
                    colorNotification.setChecked(colorNotification.isEnabled());
                    break;
                case PreferenceUtil.LIBRARY_SAVE_TRIGGER:
                    Preference saveTriggerSeconds = findPreference(PreferenceUtil.LIBRARY_SAVE_TRIGGER_SECONDS);

                    saveTriggerSeconds.setEnabled(PreferenceUtil.getInstance(getActivity()).getLibrarySaveTrigger() == SaveTrigger.Mode.DELAY);
                    break;
                case PreferenceUtil.OFFLINE_MODE:
                    NetworkUtil.notifyChanged();
                    SaveWorker.restart(requireContext());
                    break;
                case PreferenceUtil.NETWORK_STREAMING:
                    NetworkUtil.notifyChanged();
                    break;
                case PreferenceUtil.NETWORK_SAVING:
                    SaveWorker.restart(requireContext());
                    break;
            }
        }
    }
}
