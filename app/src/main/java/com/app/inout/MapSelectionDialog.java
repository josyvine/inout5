package com.inout.app;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import org.osmdroid.config.Configuration;
import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Full Screen Dialog for selecting a location on a map.
 * Uses OpenStreetMap (osmdroid).
 * The user drags the map, and the center fixed pointer determines the selection.
 */
public class MapSelectionDialog extends DialogFragment {

    private MapView mapView;
    private EditText etSearch;
    private CardView cvSearchBar;
    private OnLocationSelectedListener listener;

    // Interface to pass data back to the Fragment
    public interface OnLocationSelectedListener {
        void onLocationSelected(double lat, double lng, String addressName);
    }

    public void setOnLocationSelectedListener(OnLocationSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Important: Initialize OSMDroid configuration
        Context ctx = requireContext().getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        
        // Set full screen style
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_map_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind Views
        mapView = view.findViewById(R.id.map_view);
        etSearch = view.findViewById(R.id.et_map_search);
        cvSearchBar = view.findViewById(R.id.cv_search_bar);
        
        ImageButton btnClose = view.findViewById(R.id.btn_close_map);
        ImageButton btnToggleSearch = view.findViewById(R.id.btn_toggle_search);
        Button btnSearchGo = view.findViewById(R.id.btn_map_search_go);
        ImageButton btnZoomIn = view.findViewById(R.id.btn_zoom_in);
        ImageButton btnZoomOut = view.findViewById(R.id.btn_zoom_out);
        Button btnSave = view.findViewById(R.id.btn_confirm_selection);

        // Setup Map
        setupMap();
        
        // Try to center on user's current location initially
        centerOnCurrentLocation();

        // Listeners
        btnClose.setOnClickListener(v -> dismiss());

        btnToggleSearch.setOnClickListener(v -> {
            if (cvSearchBar.getVisibility() == View.VISIBLE) {
                cvSearchBar.setVisibility(View.GONE);
                hideKeyboard();
            } else {
                cvSearchBar.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
                showKeyboard();
            }
        });

        btnSearchGo.setOnClickListener(v -> performSearch());
        
        // Handle keyboard "Search" action
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        btnZoomIn.setOnClickListener(v -> mapView.getController().zoomIn());
        btnZoomOut.setOnClickListener(v -> mapView.getController().zoomOut());

        btnSave.setOnClickListener(v -> confirmSelection());
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK); // Standard Free OSM Map
        mapView.setMultiTouchControls(true); // Enable Pinch to Zoom
        
        IMapController mapController = mapView.getController();
        mapController.setZoom(15.0); // Default Zoom Level
    }

    private void centerOnCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED) {
            
            LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            Location lastLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            
            if (lastLocation == null) {
                lastLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (lastLocation != null) {
                GeoPoint startPoint = new GeoPoint(lastLocation.getLatitude(), lastLocation.getLongitude());
                mapView.getController().setCenter(startPoint);
            } else {
                // Default to a generic location (e.g., 0,0) if no GPS found
                mapView.getController().setCenter(new GeoPoint(0.0, 0.0));
                Toast.makeText(getContext(), "GPS not available. Please search or scroll.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void performSearch() {
        String query = etSearch.getText().toString().trim();
        if (TextUtils.isEmpty(query)) return;

        hideKeyboard();
        
        // Use Android Geocoder to find location from text
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(query, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address result = addresses.get(0);
                GeoPoint target = new GeoPoint(result.getLatitude(), result.getLongitude());
                
                // Animate map to result
                mapView.getController().animateTo(target);
                mapView.getController().setZoom(17.0); // Zoom in on result
            } else {
                Toast.makeText(getContext(), "Location not found.", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Search error. Check internet.", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmSelection() {
        // Get the coordinates at the center of the screen (under the pin)
        GeoPoint centerPoint = (GeoPoint) mapView.getMapCenter();
        double lat = centerPoint.getLatitude();
        double lng = centerPoint.getLongitude();

        // Try to find a human-readable name for this spot
        String addressName = "Map Selected Location";
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                
                // Construct a meaningful name
                if (address.getFeatureName() != null) {
                    addressName = address.getFeatureName();
                } else if (address.getThoroughfare() != null) {
                    addressName = address.getThoroughfare();
                } else if (address.getLocality() != null) {
                    addressName = address.getLocality();
                }
                
                // If it's just a number, append street
                if (addressName.matches("\\d+") && address.getThoroughfare() != null) {
                    addressName = addressName + " " + address.getThoroughfare();
                }
            }
        } catch (IOException e) {
            // Failed to get name, stick with default
        }

        // Send data back
        if (listener != null) {
            listener.onLocationSelected(lat, lng, addressName);
        }
        dismiss();
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void hideKeyboard() {
        View view = getView();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }
}