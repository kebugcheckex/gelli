package dev.kebugcheck.gelli.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;

import dev.kebugcheck.gelli.App;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.activities.base.AbsBaseActivity;
import dev.kebugcheck.gelli.adapter.SelectAdapter;
import dev.kebugcheck.gelli.databinding.ActivitySelectBinding;
import dev.kebugcheck.gelli.model.User;

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
