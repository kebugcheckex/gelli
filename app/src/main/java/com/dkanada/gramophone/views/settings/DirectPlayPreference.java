package com.dkanada.gramophone.views.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dkanada.gramophone.R;
import com.dkanada.gramophone.adapter.DirectPlayCodecAdapter;
import com.dkanada.gramophone.util.PreferenceUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DirectPlayPreference extends Preference implements View.OnClickListener {
    public DirectPlayPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setOnClickListener(this);
    }

    @Override
    @SuppressLint("InflateParams")
    public void onClick(View v) {
        Activity activity = (Activity) getContext();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        View view = LayoutInflater.from(builder.getContext()).inflate(R.layout.preference_dialog_direct_play_codecs, null);
        DirectPlayCodecAdapter adapter = new DirectPlayCodecAdapter(PreferenceUtil.getInstance(getContext()).getDirectPlayCodecs());
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);

        builder
            .setTitle(R.string.pref_title_direct_play_codecs)
            .setView(view)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> PreferenceUtil.getInstance(getContext()).setDirectPlayCodecs(adapter.getCodecs()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
