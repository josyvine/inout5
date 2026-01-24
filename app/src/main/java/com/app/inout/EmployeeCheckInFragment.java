package com.inout.app;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.FragmentEmployeeCheckinBinding;
import com.inout.app.models.AttendanceRecord;
import com.inout.app.models.CompanyConfig;
import com.inout.app.models.User;
import com.inout.app.utils.BiometricHelper;
import com.inout.app.utils.LocationHelper;
import com.inout.app.utils.TimeUtils;

/**
 * Fragment where employees perform Check-In and Check-Out.
 * FIXED: Displays real user data and links to the assigned office location.
 */
public class EmployeeCheckInFragment extends Fragment {

    private static final String TAG = "CheckInFrag";
    private FragmentEmployeeCheckinBinding binding;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LocationHelper locationHelper;
    
    private User currentUser;
    private CompanyConfig assignedLocation;
    private AttendanceRecord todayRecord;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEmployeeCheckinBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        locationHelper = new LocationHelper(requireContext());

        // Initial UI State
        binding.btnCheckIn.setEnabled(false);
        binding.btnCheckOut.setEnabled(false);

        loadUserDataAndStatus();

        binding.btnCheckIn.setOnClickListener(v -> initiateAction(true));
        binding.btnCheckOut.setOnClickListener(v -> initiateAction(false));
    }

    /**
     * Fetches user profile to display real Name/ID and retrieve the office assignment.
     */
    private void loadUserDataAndStatus() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();
        
        // Listen for profile changes (Approval, Name updates, Location assignment)
        db.collection("users").document(uid).addSnapshotListener((doc, error) -> {
            if (error != null || doc == null || !doc.exists()) return;

            currentUser = doc.toObject(User.class);
            if (currentUser != null) {
                // FIXED: Replace XML placeholders with live database data
                binding.tvEmployeeName.setText(currentUser.getName() != null ? currentUser.getName() : "Unknown User");
                binding.tvEmployeeId.setText(currentUser.getEmployeeId() != null ? currentUser.getEmployeeId() : "Pending ID");

                // Check if Admin has assigned a location ID (e.g., the ID for Canara Bank)
                if (currentUser.getAssignedLocationId() != null && !currentUser.getAssignedLocationId().isEmpty()) {
                    fetchAssignedLocationDetails(currentUser.getAssignedLocationId());
                } else {
                    binding.tvStatus.setText("Status: Waiting for Admin to assign an office location.");
                }
                
                // Also load today's attendance state
                loadTodayAttendance();
            }
        });
    }

    /**
     * Retrieves the Lat/Lng and name of the specific office assigned to this employee.
     */
    private void fetchAssignedLocationDetails(String locId) {
        db.collection("locations").document(locId).get().addOnSuccessListener(doc -> {
            assignedLocation = doc.toObject(CompanyConfig.class);
            if (assignedLocation != null) {
                Log.d(TAG, "Office Assigned: " + assignedLocation.getName());
                updateUIBasedOnStatus();
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to fetch location info", e));
    }

    private void loadTodayAttendance() {
        if (currentUser == null || currentUser.getEmployeeId() == null) return;
        
        String dateId = TimeUtils.getCurrentDateId();
        String recordId = currentUser.getEmployeeId() + "_" + dateId;

        db.collection("attendance").document(recordId).addSnapshotListener((snapshot, e) -> {
            if (snapshot != null && snapshot.exists()) {
                todayRecord = snapshot.toObject(AttendanceRecord.class);
            } else {
                todayRecord = null;
            }
            updateUIBasedOnStatus();
        });
    }

    private void updateUIBasedOnStatus() {
        if (currentUser == null) return;

        // If location isn't fetched yet, keep buttons disabled
        if (assignedLocation == null) {
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckOut.setEnabled(false);
            return;
        }

        if (todayRecord == null) {
            binding.btnCheckIn.setEnabled(true);
            binding.btnCheckOut.setEnabled(false);
            binding.tvStatus.setText("Status: Not Checked In (" + assignedLocation.getName() + ")");
        } else if (todayRecord.getCheckOutTime() == null || todayRecord.getCheckOutTime().isEmpty()) {
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckOut.setEnabled(true);
            binding.tvStatus.setText("Status: Checked In at " + todayRecord.getCheckInTime());
        } else {
            binding.btnCheckIn.setEnabled(false);
            binding.btnCheckOut.setEnabled(false);
            binding.tvStatus.setText("Status: Day Completed (" + todayRecord.getTotalHours() + ")");
        }
    }

    private void initiateAction(boolean isCheckIn) {
        if (assignedLocation == null) {
            Toast.makeText(getContext(), "Error: No office location assigned to you.", Toast.LENGTH_LONG).show();
            return;
        }

        BiometricHelper.authenticate(requireActivity(), new BiometricHelper.BiometricCallback() {
            @Override
            public void onAuthenticationSuccess() {
                verifyLocationAndProceed(isCheckIn);
            }

            @Override
            public void onAuthenticationError(String errorMsg) {
                Toast.makeText(getContext(), "Auth Error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(getContext(), "Fingerprint not recognized.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void verifyLocationAndProceed(boolean isCheckIn) {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        locationHelper.getCurrentLocation(new LocationHelper.LocationResultCallback() {
            @Override
            public void onLocationResult(Location location) {
                binding.progressBar.setVisibility(View.GONE);
                
                if (location != null) {
                    boolean inRange = LocationHelper.isWithinRadius(
                            location.getLatitude(), location.getLongitude(),
                            assignedLocation.getLatitude(), assignedLocation.getLongitude(),
                            assignedLocation.getRadius());

                    if (inRange) {
                        if (isCheckIn) performCheckIn(location);
                        else performCheckOut(location);
                    } else {
                        String msg = "Denied: You are not within the 100m radius of " + assignedLocation.getName();
                        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onError(String errorMsg) {
                binding.progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "GPS Error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performCheckIn(Location loc) {
        String dateId = TimeUtils.getCurrentDateId();
        String recordId = currentUser.getEmployeeId() + "_" + dateId;

        AttendanceRecord record = new AttendanceRecord(
                currentUser.getEmployeeId(), 
                currentUser.getName(), 
                dateId, 
                TimeUtils.getCurrentTimestamp());

        record.setRecordId(recordId);
        record.setCheckInTime(TimeUtils.getCurrentTime());
        record.setCheckInLat(loc.getLatitude());
        record.setCheckInLng(loc.getLongitude());
        record.setFingerprintVerified(true);
        record.setLocationVerified(true);
        
        // Save UID to satisfy security rules (resource.data.uid)
        record.setRecordId(recordId); 

        db.collection("attendance").document(recordId).set(record)
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-In Success!", Toast.LENGTH_SHORT).show());
    }

    private void performCheckOut(Location loc) {
        if (todayRecord == null) return;

        String checkOutTime = TimeUtils.getCurrentTime();
        String totalHrs = TimeUtils.calculateDuration(todayRecord.getCheckInTime(), checkOutTime);

        db.collection("attendance").document(todayRecord.getRecordId())
                .update(
                        "checkOutTime", checkOutTime,
                        "checkOutLat", loc.getLatitude(),
                        "checkOutLng", loc.getLongitude(),
                        "totalHours", totalHrs
                )
                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Check-Out Success!", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}