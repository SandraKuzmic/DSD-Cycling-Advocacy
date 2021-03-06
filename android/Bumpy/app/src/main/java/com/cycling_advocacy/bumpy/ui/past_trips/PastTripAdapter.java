package com.cycling_advocacy.bumpy.ui.past_trips;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.cycling_advocacy.bumpy.R;
import com.cycling_advocacy.bumpy.entities.PastTrip;
import com.cycling_advocacy.bumpy.ui.trip_stats.PastTripStatisticsActivity;
import com.cycling_advocacy.bumpy.utils.GeneralUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PastTripAdapter extends RecyclerView.Adapter<PastTripAdapter.ViewHolder> {

    private List<PastTrip> pastTripList = new ArrayList<>();
    private List<PastTrip> apiData = new ArrayList<>();
    private List<PastTrip> dbData = new ArrayList<>();
    private Context context;
    private PastTripClickedListener listener;

    PastTripAdapter(Context context, PastTripClickedListener listener) {
        this.context = context;
        this.listener = listener;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, detail;
        ImageButton imageUpload;

        ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.tv_past_trips_title);
            detail = view.findViewById(R.id.tv_past_trips_details);
            imageUpload = view.findViewById(R.id.image_uploaded);
        }
    }

    @NotNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_past_trips, parent, false);

        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        PastTrip pastTrip = pastTripList.get(position);

        holder.title.setText(GeneralUtil.formatTimestampLocale(pastTrip.getStartTime()));

        String duration = GeneralUtil.formatDuration(pastTrip.getStartTime(), pastTrip.getEndTime());
        holder.detail.setText(
                context.getString(R.string.past_trip_description_display, duration, pastTrip.getDistance())
        );

        if (!pastTrip.isUploaded()) {
            holder.imageUpload.setVisibility(View.VISIBLE);
        } else {
            holder.imageUpload.setVisibility(View.INVISIBLE);
        }

        holder.imageUpload.setOnClickListener(view -> listener.onSyncClick(pastTrip));

        holder.itemView.setOnClickListener(view -> {
            PastTrip selectedTrip = pastTripList.get(position);

            if (selectedTrip.isUploaded()) {
                Intent tripStatisticsIntent = new Intent(context, PastTripStatisticsActivity.class);
                tripStatisticsIntent.putExtra(PastTripStatisticsActivity.EXTRA_TRIP_UUID, selectedTrip.getTripUUID());

                context.startActivity(tripStatisticsIntent);
            } else {
                Toast.makeText(context, R.string.upload_trip_for_stats, Toast.LENGTH_LONG).show();
            }
        });

        holder.itemView.setOnLongClickListener(view -> {
            listener.onPastTripLongClick(pastTrip);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return pastTripList.size();
    }

    void addDbData(List<PastTrip> pastTripList) {
        this.dbData = pastTripList;
        Collections.sort(this.dbData);
        updateList();
    }

    void addApiData(List<PastTrip> pastTripList) {
        this.apiData = pastTripList;
        updateList();
    }

    private void updateList() {
        this.pastTripList = new ArrayList<>();
        pastTripList.addAll(dbData);
        pastTripList.addAll(apiData);

        notifyDataSetChanged();
    }
}