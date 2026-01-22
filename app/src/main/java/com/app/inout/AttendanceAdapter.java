package com.inout.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inout.app.R;
import com.inout.app.models.AttendanceRecord;

import java.util.List;

/**
 * Adapter for displaying Attendance Records in a CSV-style table.
 * Used by AdminAttendanceFragment and EmployeeHistoryFragment.
 */
public class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder> {

    private final List<AttendanceRecord> attendanceList;

    public AttendanceAdapter(List<AttendanceRecord> attendanceList) {
        this.attendanceList = attendanceList;
    }

    @NonNull
    @Override
    public AttendanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the row layout which represents one day of attendance
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_row, parent, false);
        return new AttendanceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AttendanceViewHolder holder, int position) {
        AttendanceRecord record = attendanceList.get(position);

        // Bind data to the table columns
        holder.tvDate.setText(record.getDate());
        
        holder.tvIn.setText(record.getCheckInTime() != null ? record.getCheckInTime() : "--:--");
        
        holder.tvOut.setText(record.getCheckOutTime() != null ? record.getCheckOutTime() : "--:--");
        
        holder.tvTotalHours.setText(record.getTotalHours() != null ? record.getTotalHours() : "0h 00m");

        // Optional: Verification indicator logic
        // If the record was verified by both Biometrics and GPS, we can style it
        if (record.isFingerprintVerified() && record.isLocationVerified()) {
            holder.tvDate.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_verified_small, 0);
        } else {
            holder.tvDate.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    @Override
    public int getItemCount() {
        return attendanceList.size();
    }

    /**
     * ViewHolder for a single row in the attendance table.
     */
    static class AttendanceViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvIn, tvOut, tvTotalHours;

        public AttendanceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_col_date);
            tvIn = itemView.findViewById(R.id.tv_col_in);
            tvOut = itemView.findViewById(R.id.tv_col_out);
            tvTotalHours = itemView.findViewById(R.id.tv_col_hours);
        }
    }
}