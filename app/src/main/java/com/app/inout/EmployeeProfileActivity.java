package com.inout.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.inout.app.databinding.ActivityEmployeeProfileBinding;
import com.inout.app.models.User;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity for Employees to set up their Profile (Name, Phone).
 * ZERO BILLING SOLUTION:
 * - No Firebase Storage used.
 * - Profile Photo is retrieved directly from the Google Account photoURL.
 * - Only the URL string is stored in Firestore.
 */
public class EmployeeProfileActivity extends AppCompatActivity {

    private ActivityEmployeeProfileBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmployeeProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // 1. Load data if user already exists
        loadCurrentUserData();

        // 2. Disable photo selection button as it's now synced with Google
        binding.btnSelectPhoto.setVisibility(View.GONE);
        
        // 3. Save Button Listener
        binding.btnSaveProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndSave();
            }
        });
    }

    private void loadCurrentUserData() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) return;

        // Display current Google info as hint/default
        if (firebaseUser.getDisplayName() != null) {
            binding.etName.setText(firebaseUser.getDisplayName());
        }

        // Fetch the user's profile from Firestore to see if phone is already saved
        db.collection("users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            if (user.getName() != null) binding.etName.setText(user.getName());
                            if (user.getPhone() != null) binding.etPhone.setText(user.getPhone());
                            
                            // Note: To display the image from the URL string, 
                            // you would normally use a library like Glide or Picasso.
                            // e.g., Glide.with(this).load(user.getPhotoUrl()).into(binding.ivProfilePhoto);
                        }
                    }
                });
    }

    private void validateAndSave() {
        String name = binding.etName.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.etName.setError("Name required");
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            binding.etPhone.setError("Phone required");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSaveProfile.setEnabled(false);

        // Directly proceed to save data using Google's photo URL
        saveFirestoreData(name, phone);
    }

    private void saveFirestoreData(String name, String phone) {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = firebaseUser.getUid();

        // **ZERO BILLING DESIGN**
        // We pull the URL directly from the Google Auth object provided by the system.
        String googlePhotoUrl = "";
        if (firebaseUser.getPhotoUrl() != null) {
            googlePhotoUrl = firebaseUser.getPhotoUrl().toString();
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", phone);
        updates.put("photoUrl", googlePhotoUrl); // Saving the Google-hosted link

        db.collection("users").document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(EmployeeProfileActivity.this, "Profile Updated via Google Sync", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnSaveProfile.setEnabled(true);
                        Toast.makeText(EmployeeProfileActivity.this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}