package com.dkanada.gramophone.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.activities.base.AbsBaseActivity;
import com.dkanada.gramophone.adapter.SelectAdapter;
import com.dkanada.gramophone.databinding.ActivitySelectBinding;
import com.dkanada.gramophone.model.User;
import com.dkanada.gramophone.util.PreferenceUtil;

import java.util.List;

public class SelectActivity extends AbsBaseActivity {
    private ActivitySelectBinding binding;
    private SelectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySelectBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new SelectAdapter(this, new java.util.ArrayList<>());

        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));

        binding.add.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));

        int primaryColor = PreferenceUtil.getInstance(this).getPrimaryColor();

        binding.add.setBackgroundColor(primaryColor);
        binding.toolbar.setBackgroundColor(primaryColor);
        setSupportActionBar(binding.toolbar);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.updateUsers(App.getDatabase().userDao().getUsers());
    }

    @Override
    public void onPause() {
        super.onPause();
        overridePendingTransition(0, R.anim.fade_quick);
    }
}
