package dev.kebugcheck.gelli.fragments.main;

import androidx.fragment.app.Fragment;

import dev.kebugcheck.gelli.activities.MainActivity;

public abstract class AbsMainActivityFragment extends Fragment {
    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }
}
