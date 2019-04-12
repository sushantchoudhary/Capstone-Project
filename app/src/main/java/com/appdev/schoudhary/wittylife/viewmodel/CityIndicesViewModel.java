package com.appdev.schoudhary.wittylife.viewmodel;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.util.Log;

import com.appdev.schoudhary.wittylife.database.AppDatabase;
import com.appdev.schoudhary.wittylife.model.CityIndices;

public class CityIndicesViewModel extends ViewModel {
    private static final String TAG = CityIndicesViewModel.class.getSimpleName();
    private LiveData<CityIndices> cityIndicesLiveData;
    private CityIndices cityIndices;

    public CityIndicesViewModel(AppDatabase appDatabase, String cityName) {
        Log.d(TAG, "Actively retrieving city indices from database");
        cityIndicesLiveData = appDatabase.cityIndicesDao().loadCityByName(cityName);
        cityIndices = appDatabase.cityIndicesDao().loadCityByNameRaw(cityName);

    }

    public LiveData<CityIndices> getCityIndices() {
        return cityIndicesLiveData;
    }

    public CityIndices getCityRawIndices() {
        return cityIndices;
    }
}