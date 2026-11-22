package dev.kebugcheck.gelli.fragments.library;

import dev.kebugcheck.gelli.fragments.AbsMusicServiceFragment;
import dev.kebugcheck.gelli.fragments.main.LibraryFragment;

public class AbsLibraryPagerFragment extends AbsMusicServiceFragment {
    public LibraryFragment getLibraryFragment() {
        return (LibraryFragment) getParentFragment();
    }
}
