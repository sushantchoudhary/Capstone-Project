package com.appdev.schoudhary.wittylife.database;


import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.appdev.schoudhary.wittylife.model.CostRanking;
import com.appdev.schoudhary.wittylife.model.TrafficRanking;

import java.util.List;

@Dao
public interface TrafficDao {

    @Query("SELECT * FROM trafficranking ORDER BY trafficIndex DESC")
    LiveData<List<TrafficRanking>> loadTrafficRank();

    @Query("DELETE FROM trafficranking")
    void deleteAllRows();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTraffic(TrafficRanking trafficrecord);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertTrafficList(List<TrafficRanking> trafficRecords);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void updateTraffic(TrafficRanking trafficRanking);

    @Delete
    void deleteTraffic(TrafficRanking trafficRecord);

    @Query("SELECT * FROM trafficranking WHERE city_id = :id")
    LiveData<CostRanking> loadTrafficById(int id);

    @Query("SELECT count(*) FROM trafficranking")
    int getRowCount();

}
