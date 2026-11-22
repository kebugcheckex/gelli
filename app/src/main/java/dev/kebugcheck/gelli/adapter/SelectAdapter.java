package dev.kebugcheck.gelli.adapter;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import dev.kebugcheck.gelli.App;
import dev.kebugcheck.gelli.R;
import dev.kebugcheck.gelli.activities.AddressesActivity;
import dev.kebugcheck.gelli.activities.SplashActivity;
import dev.kebugcheck.gelli.model.User;
import dev.kebugcheck.gelli.util.PreferenceUtil;
import dev.kebugcheck.gelli.views.IconImageView;

import java.util.List;

public class SelectAdapter extends RecyclerView.Adapter<SelectAdapter.ViewHolder> {
    private final AppCompatActivity activity;
    private final List<User> users;

    public SelectAdapter(@NonNull AppCompatActivity activity, List<User> users) {
        this.activity = activity;
        this.users = users;
    }

    public void updateUsers(List<User> newUsers) {
        users.clear();
        users.addAll(newUsers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(activity).inflate(R.layout.card_server, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final User user = users.get(position);

        holder.name.setText(user.name);
        // one account per server, with its addresses in the order they are tried
        holder.url.setText(String.join("\n", user.addresses));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView url;

        IconImageView addresses;
        IconImageView delete;
        IconImageView select;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.name);
            url = itemView.findViewById(R.id.url);
            addresses = itemView.findViewById(R.id.addresses);
            delete = itemView.findViewById(R.id.delete);
            select = itemView.findViewById(R.id.select);

            addresses.setOnClickListener(this::onAddresses);
            delete.setOnClickListener(this::onDelete);
            select.setOnClickListener(this::onSelect);
        }

        public void onSelect(View v) {
            final User user = users.get(getBindingAdapterPosition());

            PreferenceUtil.getInstance(activity).setUser(user.id);

            activity.startActivity(new Intent(activity, SplashActivity.class));
        }

        public void onAddresses(View v) {
            final User user = users.get(getBindingAdapterPosition());

            AddressesActivity.start(activity, user.id);
        }

        public void onDelete(View v) {
            final User user = users.get(getBindingAdapterPosition());

            App.getDatabase().userDao().deleteUser(user);
            users.remove(user);
            notifyDataSetChanged();
        }
    }
}
