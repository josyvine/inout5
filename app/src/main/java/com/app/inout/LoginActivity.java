package com.inout.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.inout.app.databinding.ActivityLoginBinding;
import com.inout.app.models.User;
import com.inout.app.utils.EncryptionHelper;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private ActivityLoginBinding binding;
    
    private GoogleSignInClient mGoogleSignInClient;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    
    private String expectedRole;

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        }
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        updateUI(null);
                        Toast.makeText(this, "Google Sign-In Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    updateUI(null);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        EncryptionHelper encryptionHelper = EncryptionHelper.getInstance(this);
        expectedRole = encryptionHelper.getUserRole();
        
        // =====================================================================
        // CRITICAL CHANGE: DYNAMICALLY GET WEB CLIENT ID FROM STORED JSON
        // =====================================================================
        String webClientId = encryptionHelper.getWebClientId();

        if (webClientId == null) {
            Toast.makeText(this, "FATAL ERROR: Google Client ID not found in configuration. Please re-setup.", Toast.LENGTH_LONG).show();
            binding.btnGoogleSignIn.setEnabled(false);
            return; // Stop initialization if the ID is missing
        }
        // =====================================================================

        // Configure Google Sign-In with the DYNAMIC ID
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId) // Use the ID extracted from the JSON
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        String companyName = encryptionHelper.getCompanyName();
        binding.tvLoginTitle.setText("Login to " + companyName);
        binding.tvLoginSubtitle.setText("Role: " + (expectedRole != null ? expectedRole.toUpperCase() : "UNKNOWN"));

        binding.btnGoogleSignIn.setOnClickListener(v -> signIn());
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            checkUserInFirestore(currentUser);
        }
    }

    private void signIn() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGoogleSignIn.setVisibility(View.INVISIBLE);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            checkUserInFirestore(user);
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    }
                });
    }

    private void checkUserInFirestore(FirebaseUser firebaseUser) {
        if (firebaseUser == null) return;

        DocumentReference userRef = db.collection("users").document(firebaseUser.getUid());

        userRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                User user = documentSnapshot.toObject(User.class);
                if (user != null && user.getRole().equals(expectedRole)) {
                    proceedToDashboard(user);
                } else {
                    Toast.makeText(LoginActivity.this, "Error: Account role mismatch.", Toast.LENGTH_LONG).show();
                    mAuth.signOut();
                    updateUI(null);
                }
            } else {
                createUserProfile(firebaseUser, userRef);
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching user", e);
            Toast.makeText(LoginActivity.this, "Network Error. Please try again.", Toast.LENGTH_SHORT).show();
            updateUI(null);
        });
    }

    private void createUserProfile(FirebaseUser firebaseUser, DocumentReference userRef) {
        User newUser = new User(firebaseUser.getUid(), firebaseUser.getEmail(), expectedRole);
        
        if (firebaseUser.getDisplayName() != null) {
            newUser.setName(firebaseUser.getDisplayName());
        }

        if ("admin".equals(expectedRole)) {
            newUser.setApproved(true);
        } else {
            newUser.setApproved(false);
        }

        userRef.set(newUser)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(LoginActivity.this, "Account Created.", Toast.LENGTH_SHORT).show();
                    proceedToDashboard(newUser);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating profile", e);
                    Toast.makeText(LoginActivity.this, "Failed to create database record.", Toast.LENGTH_SHORT).show();
                    mAuth.signOut();
                    updateUI(null);
                });
    }

    private void proceedToDashboard(User user) {
        Intent intent;
        if ("admin".equals(user.getRole())) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else {
            intent = new Intent(this, EmployeeDashboardActivity.class);
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void updateUI(FirebaseUser user) {
        binding.progressBar.setVisibility(View.GONE);
        binding.btnGoogleSignIn.setVisibility(View.VISIBLE);
    }
}