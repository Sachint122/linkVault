package com.linkvault.app.ui.onboarding;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.linkvault.app.App;
import com.linkvault.app.R;
import com.linkvault.app.data.saf.StoragePermissionManager;

/** First-run storage folder selection (FR-07). */
public class OnboardingFragment extends Fragment {

    private TextView statusText;
    private MaterialButton pickButton;
    private StoragePermissionManager permissionManager;

    private final ActivityResultLauncher<Uri> folderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return; // user cancelled
                boolean ok = permissionManager.onTreePicked(uri);
                if (!ok) {
                    statusText.setText(R.string.onboarding_pick_error);
                    return;
                }
                ((App) requireActivity().getApplication()).getContainer().onStorageFolderChanged();
                refreshStatus();
                // Pop onboarding off backstack so back press from Home exits the app
                Navigation.findNavController(requireView()).navigate(
                        R.id.homeFragment,
                        null,
                        new androidx.navigation.NavOptions.Builder()
                                .setPopUpTo(R.id.onboardingFragment, true)
                                .build());
                if (getActivity() instanceof com.linkvault.app.ui.MainActivity) {
                    ((com.linkvault.app.ui.MainActivity) getActivity()).consumePendingShare();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_onboarding, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        statusText = view.findViewById(R.id.statusText);
        pickButton = view.findViewById(R.id.pickButton);
        permissionManager = ((App) requireActivity().getApplication())
                .getContainer().permissionManager();

        pickButton.setOnClickListener(v -> folderPicker.launch(null));

        refreshStatus();
    }

    private void refreshStatus() {
        StoragePermissionManager.AccessState state = permissionManager.getAccessState();
        int res = switch (state) {
            case GRANTED -> R.string.onboarding_status_granted;
            case REVOKED -> R.string.onboarding_status_revoked;
            case NO_FOLDER -> R.string.onboarding_status_none;
        };
        statusText.setText(res);
        if (state == StoragePermissionManager.AccessState.GRANTED) {
            pickButton.setText(R.string.storage_reselect);
        }
    }
}
