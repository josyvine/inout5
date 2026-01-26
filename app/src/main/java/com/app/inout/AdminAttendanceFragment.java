package com.inout.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.FragmentAdminAttendanceBinding;
import com.inout.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin view for Attendance selection.
 * Updated Logic:
 * 1. Select employee from dropdown.
 * 2. Launches AttendanceProfileDialog (the pop-up window) with CV-style header.
 */
public class AdminAttendanceFragment extends Fragment {

    private FragmentAdminAttendanceBinding binding;
    private FirebaseFirestore db;
    private List<User> employees;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAdminAttendanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        employees = new ArrayList<>();

        loadEmployeeList();
    }

    /**
     * Fetches all approved employees to populate the selection spinner.
     */
    private void loadEmployeeList() {
        binding.progressBar.setVisibility(View.VISIBLE);
        db.collection("users")
                .whereEqualTo("role", "employee")
                .whereEqualTo("approved", true)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    binding.progressBar.setVisibility(View.GONE);
                    employees.clear();
                    List<String> employeeNames = new ArrayList<>();
                    employeeNames.add("Select an Employee to view Profile");

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            user.setUid(doc.getId());
                            employees.add(user);
                            employeeNames.add(user.getName() + " (" + user.getEmployeeId() + ")");
                        }
                    }

                    setupSpinner(employeeNames);
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Error loading employees", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupSpinner(List<String> names) {
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), 
                android.R.layout.simple_spinner_item, names);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerEmployees.setAdapter(spinnerAdapter);

        binding.spinnerEmployees.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    // Position 0 is the hint "Select an Employee..."
                    User selectedUser = employees.get(position - 1);
                    
                    // NEW LOGIC: Launch the pop-up window instead of showing a list here
                    openAttendanceProfileDialog(selectedUser);
                    
                    // Reset spinner selection so it can be clicked again for the same person if needed
                    binding.spinnerEmployees.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Creates and shows the new Professional Attendance Profile pop-up.
     */
    private void openAttendanceProfileDialog(User user) {
        AttendanceProfileDialog dialog = AttendanceProfileDialog.newInstance(user);
        dialog.show(getChildFragmentManager(), "AttendanceProfileDialog");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}