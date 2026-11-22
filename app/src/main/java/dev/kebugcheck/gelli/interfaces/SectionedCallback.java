package dev.kebugcheck.gelli.interfaces;

import dev.kebugcheck.gelli.util.SectionedList;

public interface SectionedCallback<T> {
    void onLoadMedia(SectionedList<T> media);
}
