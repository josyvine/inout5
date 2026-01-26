package com.inout.app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.inout.app.adapters.AttendanceAdapter;
import com.inout.app.databinding.FragmentEmployeeHistoryBinding;
import com.inout.app.models.AttendanceRecord;
import com.inout.app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for Employees to view their own personal attendance history.
 * Data is presented in a horizontal monthly table format (CSV-style).
 * FIXED: Resolved ViewBinding visibility error for included table header.
 */
public class EmployeeHistoryFragment extends Fragment {

    private static final String TAG = "EmployeeHistoryFrag";
    private FragmentEmployeeHistoryBinding binding;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    
    private List<AttendanceRecord> historyLogs;
    private AttendanceAdapter adapter;
    private String employeeId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEmployeeHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        historyLogs = new ArrayList<>();

        setupRecyclerView();
        fetchEmployeeIdAndLoadLogs();
    }

    private void setupRecyclerView() {
        binding.rvHistoryTable.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AttendanceAdapter(historyLogs);
        binding.rvHistoryTable.setAdapter(adapter);
    }

    /**
     * First, we must get the employeeId (e.g., EMP001) from the user profile,
     * then we can query the attendance logs.
     */
    private void fetchEmployeeIdAndLoadLogs() {
        if (mAuth.getCurrentUser() == null) return;
        
        String uid = mAuth.getCurrentUser().getUid();
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getEmployeeId() != null) {
                            this.employeeId = user.getEmployeeId();
                            
                            // Update header info if views exist in this layout variant
                            if (binding.tvHistoryId != null) {
                                binding.tvHistoryId.setText("ID: " + this.employeeId);
                            }
                            
                            loadMyLogs();
                        } else {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.tvNoData.setText("Employee ID not assigned yet.");
                            binding.tvNoData.setVisibility(View.VISIBLE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Failed to load profile.", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Queries the 'attendance' collection for records belonging to this employee.
     */
    private void loadMyLogs() {
        db.collection("attendance")
                .whereEqualTo("employeeId", employeeId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    binding.progressBar.setVisibility(View.GONE);
                    
                    if (error != null) {
                        Log.e(TAG, "Error listening for history logs", error);
                        Toast.makeText(getContext(), "Error syncing logs.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null) {
                        historyLogs.clear();
                        for (DocumentSnapshot doc : value) {
                            AttendanceRecord record = doc.toObject(AttendanceRecord.class);
                            if (record != null) {
                                historyLogs.add(record);
                            }
                        }
                        
                        adapter.notifyDataSetChanged();
                        
                        if (historyLogs.isEmpty()) {
                            binding.tvNoData.setVisibility(View.VISIBLE);
                            // FIXED: Use getRoot() to set visibility on the included layout
                            binding.tableHeader.getRoot().setVisibility(View.GONE);
                        } else {
                            binding.tvNoData.setVisibility(View.GONE);
                            // FIXED: Use getRoot() to set visibility on the included layout
                            binding.tableHeader.getRoot().setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}