package com.cycling_advocacy.bumpy.pending_trips;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.cycling_advocacy.bumpy.entities.Trip;

import java.util.List;

public class PendingTripsViewModel extends AndroidViewModel {

    private PendingTripsRepository repository;
    public LiveData<List<PendingTrip>> pendingTripsLiveData;

    public PendingTripsViewModel(Application application) {
        super(application);

        this.repository = new PendingTripsRepository(application);
        this.pendingTripsLiveData = this.repository.getPendingTrips();
    }

    public LiveData<PendingTrip> getPendingTripByTripUUID(String tripUUID){
        return this.repository.getPendingTripByTripUUID(tripUUID);
    }

    public boolean insert(Trip trip) {
        PendingTrip pendingTrip = PendingTripsManager.convertToPendingTrip(trip);
        if (pendingTrip != null) {
            this.repository.insertAsync(pendingTrip);
            return true;
        } else {
            return false;
        }
    }

    public void delete(PendingTrip pendingTrip) {
        this.repository.deleteAsync(pendingTrip);
    }

    public void deleteByTripUUID(String tripUUID) {
        this.repository.deleteByTripUUIDAsync(tripUUID);
    }
}
