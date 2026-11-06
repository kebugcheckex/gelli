package com.dkanada.gramophone.adapter.album;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dkanada.gramophone.helper.HorizontalAdapterHelper;
import com.dkanada.gramophone.model.Album;
import com.dkanada.gramophone.util.MusicUtil;

import java.util.List;

public class HorizontalAlbumAdapter extends AlbumAdapter {
    public HorizontalAlbumAdapter(@NonNull AppCompatActivity activity, List<Album> dataSet) {
        super(activity, dataSet, HorizontalAdapterHelper.LAYOUT_RES);
    }

    @Override
    protected ViewHolder createViewHolder(View view, int viewType) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        HorizontalAdapterHelper.applyMarginToLayoutParams(activity, params, viewType);

        return new ViewHolder(view);
    }

    @Override
    protected String getAlbumText(Album album) {
        return MusicUtil.getYearString(album.year);
    }

    @Override
    public int getItemViewType(int position) {
        return HorizontalAdapterHelper.getItemViewType(position, getItemCount());
    }
}
