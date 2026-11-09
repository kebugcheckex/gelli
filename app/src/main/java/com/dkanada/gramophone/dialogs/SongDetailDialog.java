package com.dkanada.gramophone.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.databinding.DialogFileDetailsBinding;
import com.dkanada.gramophone.model.Song;
import com.dkanada.gramophone.util.MusicUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SongDetailDialog extends DialogFragment {
    public static final String TAG = SongDetailDialog.class.getSimpleName();

    @NonNull
    public static SongDetailDialog create(Song song) {
        SongDetailDialog dialog = new SongDetailDialog();
        Bundle args = new Bundle();
        args.putParcelable("song", song);
        dialog.setArguments(args);
        return dialog;
    }

    private static Spanned makeTextWithTitle(@NonNull Context context, int titleResId, String text) {
        return Html.fromHtml("<b>" + context.getResources().getString(titleResId) + ": " + "</b>" + text);
    }

    private static String getFileSizeString(long sizeInBytes) {
        long fileSizeInKB = sizeInBytes / 1024;
        long fileSizeInMB = fileSizeInKB / 1024;
        return fileSizeInMB + " MB";
    }

    private static String getSampleRateString(int sampleRate) {
        return sampleRate + " Hz";
    }

    private static String getBitRateString(int bitRate) {
        int bitRateInKB = bitRate / 1000;
        return bitRateInKB + " kb/s";
    }

    private static String getBitDepthString(int bitDepth) {
        return bitDepth + "-bit";
    }

    private static String getFormatString(@NonNull Song song) {
        if (song.container.equals(song.codec)) return song.container.toUpperCase();
        return song.container.toUpperCase() + ":" + song.codec.toUpperCase();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity context = requireActivity();
        final Song song = getArguments().getParcelable("song");

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        DialogFileDetailsBinding binding = DialogFileDetailsBinding.inflate(LayoutInflater.from(builder.getContext()));

        final TextView path = binding.filePath;
        final TextView name = binding.fileName;
        final TextView size = binding.fileSize;
        final TextView format = binding.fileFormat;
        final TextView length = binding.trackLength;
        final TextView sampleRate = binding.sampleRate;
        final TextView bitRate = binding.bitRate;
        final TextView bitDepth = binding.bitDepth;
        final TextView channels = binding.channels;

        path.setText(makeTextWithTitle(context, R.string.label_file_path, "-"));
        name.setText(makeTextWithTitle(context, R.string.label_file_name, "-"));
        size.setText(makeTextWithTitle(context, R.string.label_file_size, "-"));
        format.setText(makeTextWithTitle(context, R.string.label_file_format, "-"));
        length.setText(makeTextWithTitle(context, R.string.label_track_length, "-"));
        sampleRate.setText(makeTextWithTitle(context, R.string.label_sample_rate, "-"));
        bitRate.setText(makeTextWithTitle(context, R.string.label_bit_rate, "-"));
        bitDepth.setText(makeTextWithTitle(context, R.string.label_bit_depth, "-"));
        channels.setText(makeTextWithTitle(context, R.string.label_channels, "-"));

        if (song != null) {
            path.setText(makeTextWithTitle(context, R.string.label_file_path, song.path));
            name.setText(makeTextWithTitle(context, R.string.label_file_name, song.title));
            size.setText(makeTextWithTitle(context, R.string.label_file_size, getFileSizeString(song.size)));

            format.setText(makeTextWithTitle(context, R.string.label_file_format, getFormatString(song)));

            length.setText(makeTextWithTitle(context, R.string.label_track_length, MusicUtil.getReadableDurationString(song.duration)));
            sampleRate.setText(makeTextWithTitle(context, R.string.label_sample_rate, getSampleRateString(song.sampleRate)));
            bitRate.setText(makeTextWithTitle(context, R.string.label_bit_rate, getBitRateString(song.bitRate)));
            bitDepth.setText(makeTextWithTitle(context, R.string.label_bit_depth, getBitDepthString(song.bitDepth)));
            channels.setText(makeTextWithTitle(context, R.string.label_channels, Integer.toString(song.channels)));

            if (song.bitDepth == 0) {
                bitDepth.setVisibility(View.GONE);
            }
        }

        return builder
                .setTitle(R.string.label_details)
                .setView(binding.getRoot())
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
