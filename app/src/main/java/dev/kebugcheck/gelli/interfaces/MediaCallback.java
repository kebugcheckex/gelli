package dev.kebugcheck.gelli.interfaces;

import java.util.List;

public interface MediaCallback<T> {
    void onLoadMedia(List<T> media);
}
