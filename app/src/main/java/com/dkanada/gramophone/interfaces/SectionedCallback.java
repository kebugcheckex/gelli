package com.dkanada.gramophone.interfaces;

import com.dkanada.gramophone.util.SectionedList;

public interface SectionedCallback<T> {
    void onLoadMedia(SectionedList<T> media);
}
