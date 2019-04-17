package com.appdev.schoudhary.wittylife.ui.main;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ShareCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.appdev.schoudhary.wittylife.BuildConfig;
import com.appdev.schoudhary.wittylife.R;
import com.appdev.schoudhary.wittylife.SuggestionProvider;
import com.appdev.schoudhary.wittylife.database.AppDatabase;
import com.appdev.schoudhary.wittylife.model.CityIndices;
import com.appdev.schoudhary.wittylife.model.ClimateData;
import com.appdev.schoudhary.wittylife.model.CrimeData;
import com.appdev.schoudhary.wittylife.model.HealthCareData;
import com.appdev.schoudhary.wittylife.model.QOLRanking;
import com.appdev.schoudhary.wittylife.network.ApiService;
import com.appdev.schoudhary.wittylife.network.RetroClient;
import com.appdev.schoudhary.wittylife.utils.AppExecutors;
import com.appdev.schoudhary.wittylife.viewmodel.MainViewModel;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import at.grabner.circleprogress.CircleProgressView;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DetailsActivity extends AppCompatActivity {

    private static final String TAG = DetailsActivity.class.getSimpleName();
    private static final String DESTINATION_URL = "https://www.numbeo.com/quality-of-life/in/";
    private TextView mDetailHeader;
    private TextView mPPIValue;
    private TextView mSafetyValue;
    private TextView mHealthValue;
    private TextView mClimateValue;
    private TextView mMinContribValue;
    private TextView mMaxContribValue;

    private TextView mErrorMessageDisplay;
    private ProgressBar mLoadingIndicator;

    private ConstraintLayout detailsLayout;

    private TextView mMinContribText;
    private TextView mMaxContribText;

    private Pair<Integer, Integer> contribData;

    private FloatingActionButton floatingActionButton;


    private static AppDatabase mDB;


    private QOLRanking rankingData;
    private CircleProgressView mPPiCircleView;
    private CircleProgressView mSafetyCircleView;
    private CircleProgressView mHealthCircleView;
    private CircleProgressView mClimateCircleView;

    private CompositeDisposable disposables = new CompositeDisposable();
    private ShareActionProvider shareActionProvider;

    private static String searchResultCityName;
    private ShimmerFrameLayout mShimmerMinContainer;
    private ShimmerFrameLayout mShimmerMaxContainer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        mShimmerMinContainer = findViewById(R.id.shimmer_min_container);
        mShimmerMaxContainer = findViewById(R.id.shimmer_max_container);

        detailsLayout = findViewById(R.id.details_layout);

        mDetailHeader = findViewById(R.id.detail_header);
        mPPIValue = findViewById(R.id.ppi_value);
        mSafetyValue = findViewById(R.id.safety_value);
        mHealthValue = findViewById(R.id.healthcare_value);
        mClimateValue = findViewById(R.id.climate_value);
        mMinContribValue = findViewById(R.id.destination_min_contrib);
        mMaxContribValue = findViewById(R.id.destination_max_contrib);

        mMinContribText = findViewById(R.id.min_contrib_text);
        mMaxContribText = findViewById(R.id.max_contrib_text);

        floatingActionButton = findViewById(R.id.compare_fab);


        mPPiCircleView = findViewById(R.id.ppiCircleView);
        mSafetyCircleView = findViewById(R.id.safetyCircleView);
        mHealthCircleView = findViewById(R.id.healthCircleView);
        mClimateCircleView = findViewById(R.id.climateCircleView);

        mDB = AppDatabase.getsInstance(getApplicationContext());

        mLoadingIndicator = findViewById(R.id.pb_loading_indicator);
        mErrorMessageDisplay = findViewById(R.id.tv_error_message_display);


        // Check for existing state after configuration change and restore the layout
        if (savedInstanceState != null) {
            // Restore value of members from saved state
            rankingData = savedInstanceState.getParcelable("rankingData");
            searchResultCityName = savedInstanceState.getString("searchResultCityName");
            contribData = new Pair<>(savedInstanceState.getInt("maxContribData"),
                    savedInstanceState.getInt("minContribData"));

            /**
             * Updating UI from view model
             */
            if (searchResultCityName != null) {
                bindSearchUI(searchResultCityName);
            } else {
                setupRankingFromViewModel(rankingData);
            }

        } else {
            Intent intentFromHome = getIntent();
            if (intentFromHome != null) {
                if (intentFromHome.hasExtra(Intent.EXTRA_TEXT)) {
                    rankingData = intentFromHome.getParcelableExtra(Intent.EXTRA_TEXT);
//                    setupMovieDetailFromViewModel(movieRecord);
                    /**
                     * Fetching destination ranking from API on destination details loading
                     */
                    populateUI(rankingData);
                }
                // Handle ACTION_SEARCH intent
                handleIntent(intentFromHome);
            }
        }

        //Hide FAB so that context data is saved before user can navigate to comparison activity.
        floatingActionButton.hide();

        floatingActionButton.setOnClickListener(view -> {
            Class destinationClass = ComparisonActivity.class;
            Intent intentToStartComparisonActivity = new Intent(DetailsActivity.this, destinationClass);
            String name = (searchResultCityName != null) ? searchResultCityName : rankingData.getCityName();
            intentToStartComparisonActivity.putExtra(Intent.EXTRA_TEXT, name);
            startActivity(intentToStartComparisonActivity);
        });
    }

    @SuppressLint("DefaultLocale")
    private void populateUI(QOLRanking rankingData) {
        if (rankingData == null) {
            showErrorMessage();
        }

        Double QOLindex = Math.max(0, 100 + rankingData.getPurchasingPowerInclRentIndex() / 2.5 - (rankingData.getHousePriceToIncomeRatio() * 1.0) - rankingData.getCpiIndex() / 10 + rankingData.getSafetyIndex() / 2.0 + rankingData.getHealthcareIndex() / 2.5 - rankingData.getTrafficTimeIndex() / 2.0 - rankingData.getPollutionIndex() * 2.0 / 3.0 + rankingData.getClimateIndex() / 3.0);

        mPPiCircleView.setMaxValue(QOLindex.floatValue());
        mSafetyCircleView.setMaxValue(100);
        mHealthCircleView.setMaxValue(100);
        mClimateCircleView.setMaxValue(100);

        mPPIValue.setText(String.format("%.2f", Objects.requireNonNull(rankingData).getPurchasingPowerInclRentIndex()));
        mSafetyValue.setText(String.format("%.2f", Objects.requireNonNull(rankingData).getSafetyIndex()));
        mHealthValue.setText(String.format("%.2f", Objects.requireNonNull(rankingData).getHealthcareIndex()));
        mClimateValue.setText(String.format("%.2f", Objects.requireNonNull(rankingData).getClimateIndex()));

        mPPiCircleView.setValueAnimated(rankingData.getPurchasingPowerInclRentIndex().floatValue());
        mSafetyCircleView.setValueAnimated(rankingData.getSafetyIndex().floatValue());
        mHealthCircleView.setValueAnimated(rankingData.getHealthcareIndex().floatValue());
        mClimateCircleView.setValueAnimated(rankingData.getClimateIndex().floatValue());

        setContributorsData(rankingData.getCityName());

        this.setTitle(rankingData.getCityName());

    }

    @SuppressLint("DefaultLocale")
    private void populateUIFromSearch(@Nonnull Float purchasingPowerInclRentIndex,
                                      @Nonnull Float propertyPriceToIncomeRatio,
                                      @Nonnull Float cpiIndex,
                                      @Nonnull Float safetyIndex,
                                      @Nonnull Float healthcareIndex,
                                      @Nonnull Float trafficTimeIndex,
                                      @Nonnull Float pollutionIndex,
                                      @Nonnull Float climateIndex) {

        Double QOLindex = Math.max(0, 100 + purchasingPowerInclRentIndex / 2.5
                - (propertyPriceToIncomeRatio * 1.0)
                - cpiIndex / 10 + safetyIndex / 2.0
                + healthcareIndex / 2.5
                - trafficTimeIndex / 2.0
                - pollutionIndex * 2.0 / 3.0
                + climateIndex / 3.0);

        mPPiCircleView.setMaxValue(QOLindex.floatValue());
        mSafetyCircleView.setMaxValue(100);
        mHealthCircleView.setMaxValue(100);
        mClimateCircleView.setMaxValue(100);


        mPPIValue.setText(String.format("%.2f", purchasingPowerInclRentIndex));
        mSafetyValue.setText(String.format("%.2f", safetyIndex));
        mHealthValue.setText(String.format("%.2f", healthcareIndex));
        mClimateValue.setText(String.format("%.2f", climateIndex));

        mPPiCircleView.setValueAnimated(purchasingPowerInclRentIndex);
        mSafetyCircleView.setValueAnimated(safetyIndex);
        mHealthCircleView.setValueAnimated(healthcareIndex);
        mClimateCircleView.setValueAnimated(climateIndex);

        setContributorsData(searchResultCityName);

        this.setTitle(searchResultCityName);

    }

    //FIXME This call takes a long time hence rendering of contribution data is delayed
    @SuppressLint("SetTextI18n")
    private void setContributorsData(String cityName) {

        if (contribData != null) {
            mMinContribText.setVisibility(View.VISIBLE);
            mMinContribValue.setVisibility(View.VISIBLE);

            mMinContribValue.setText(contribData.second.toString());

            mShimmerMinContainer.stopShimmer();
            mShimmerMinContainer.setVisibility(View.GONE);

            mMaxContribValue.setVisibility(View.VISIBLE);
            mMaxContribText.setVisibility(View.VISIBLE);

            mMaxContribValue.setText(contribData.first.toString());

            mShimmerMaxContainer.stopShimmer();
            mShimmerMaxContainer.setVisibility(View.GONE);

            floatingActionButton.show();

        } else {
            ApiService apiService = RetroClient.getApiService();

            Single<CrimeData> crimeDataCall = apiService.getDestinationCrimeData(BuildConfig.ApiKey, cityName);
            Single<HealthCareData> healthDataCall = apiService.getDestinationHealthData(BuildConfig.ApiKey, cityName);
            Single<ClimateData> climateDataCall = apiService.getDestinationClimateData(BuildConfig.ApiKey, cityName);

            @SuppressLint("SetTextI18n") Disposable disposable = Single.zip(crimeDataCall, healthDataCall, climateDataCall, (crimeData, healthCareData, climateData) -> {
                List<Integer> data = Arrays.asList(crimeData.getContributors(), healthCareData.getContributors(), climateData.getContributors());
                Integer maxValue = data.stream().mapToInt(v -> v).max().getAsInt();
                Integer minValue = data.stream().mapToInt(v -> v).min().getAsInt();

                return new Pair<>(maxValue, minValue);
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(contributors -> {

                        contribData = contributors;

                        mMinContribText.setVisibility(View.VISIBLE);
                        mMinContribValue.setVisibility(View.VISIBLE);

                        mMinContribValue.setText(contributors.second.toString());

                        mShimmerMinContainer.stopShimmer();
                        mShimmerMinContainer.setVisibility(View.GONE);

                        mMaxContribValue.setVisibility(View.VISIBLE);
                        mMaxContribText.setVisibility(View.VISIBLE);

                        mMaxContribValue.setText(contributors.first.toString());

                        mShimmerMaxContainer.stopShimmer();
                        mShimmerMaxContainer.setVisibility(View.GONE);

                        floatingActionButton.show();

                    }, throwable -> showErrorMessage());

            disposables.add(disposable);

        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            searchResultCityName = intent.getStringExtra(SearchManager.QUERY);
            SearchRecentSuggestions recentSuggestions = new SearchRecentSuggestions(
                    this, SuggestionProvider.AUTHORITY, SuggestionProvider.MODE);
//            recentSuggestions.clearHistory();
            //TODO : Fix suggestion popup background and enable it again
            recentSuggestions.saveRecentQuery(searchResultCityName, null);
            //use the query to search your data somehow

            bindSearchUI(searchResultCityName);


        }
    }

    private void bindSearchUI(String cityName) {

        mDB.cityIndicesDao().loadCityByName("%" + cityName + "%").observe(this, new Observer<CityIndices>() {
            @Override
            public void onChanged(@Nullable CityIndices cityIndices) {

                if (cityIndices != null) {
                    populateUIFromSearch(cityIndices.getPurchasingPowerInclRentIndex(),
                            cityIndices.getPropertyPriceToIncomeRatio(),
                            cityIndices.getCpiAndRentIndex(),
                            cityIndices.getSafetyIndex(),
                            cityIndices.getHealthCareIndex(),
                            cityIndices.getTrafficTimeIndex(),
                            cityIndices.getPollutionIndex(),
                            cityIndices.getClimateIndex());
                } else {
                    fetchAndUpdateIndicesFromAPI(cityName);
                }
            }
        });
    }

    private void fetchAndUpdateIndicesFromAPI(String cityName) {
        Single<CityIndices> callCityIndices;
        ApiService apiService = RetroClient.getApiService();
        callCityIndices = apiService.getCityIndices(BuildConfig.ApiKey, cityName);

//        detailsLayout.setVisibility(View.GONE);

        //FIXME progress bar color is not consistent across app
        mLoadingIndicator.setVisibility(View.VISIBLE);

        Disposable disposable = callCityIndices.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(cityIndices -> {
                            mLoadingIndicator.setVisibility(View.INVISIBLE);
//                             detailsLayout.setVisibility(View.VISIBLE);

                            AppExecutors.getInstance().diskIO().execute(() -> {
                                //FiXME Primary key could be null, filter before insert
                                mDB.cityIndicesDao().insertIndices(cityIndices);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //FIXME Null check required
                                        populateUIFromSearch(cityIndices.getPurchasingPowerInclRentIndex(),
                                                cityIndices.getPropertyPriceToIncomeRatio(),
                                                cityIndices.getCpiAndRentIndex(),
                                                cityIndices.getSafetyIndex(),
                                                cityIndices.getHealthCareIndex(),
                                                cityIndices.getTrafficTimeIndex(),
                                                cityIndices.getPollutionIndex(),
                                                cityIndices.getClimateIndex());
                                    }
                                });
                            });

                        }, throwable -> {
                            mLoadingIndicator.setVisibility(View.INVISIBLE);
                            showErrorMessage();
                        }
                );


        disposables.add(disposable);
    }


    private void setupRankingFromViewModel(QOLRanking rankingData) {
        final MainViewModel viewModel = ViewModelProviders.of(this).get(MainViewModel.class);
        viewModel.getRankingForCityId(rankingData.getCityId()).observe(this, new Observer<QOLRanking>() {
            @Override
            public void onChanged(@Nullable QOLRanking ranking) {
                viewModel.getRankingForCityId(rankingData.getCityId()
                ).removeObserver(this);
                Log.d(TAG, "Receiving database update from LiveData");
                populateUI(ranking);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelable("rankingData", rankingData);
        outState.putString("searchResultCityName", searchResultCityName);
        outState.putInt("minContribData", contribData.second);
        outState.putInt("maxContribData", contribData.first);

        super.onSaveInstanceState(outState);
        Log.d(TAG, "Saving rankingData in bundle during orientation change");

    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        rankingData = savedInstanceState.getParcelable("rankingData");
        searchResultCityName = savedInstanceState.getString("searchResultCityName");
        contribData = new Pair<>(
                savedInstanceState.getInt("maxContribData"),
                savedInstanceState.getInt("minContribData"));
        Log.d(TAG, "Restoring rankingData from bundle during orientation change");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.destination_detail, menu);
        // Fetch and store ShareActionProvider
//        shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
//        setShareIntent(createShareRankingIntent());
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        /* Share menu item clicked */
        if (itemId == R.id.action_share) {
            Intent shareIntent = createShareRankingIntent();
            startActivity(shareIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Call to update the share intent
//        private void setShareIntent(Intent shareInte          nt) {
//            if (shareActionProvider != null) {
//                shareActionProvider.setShareIntent(shareIntent);
//            }
//        }
    private Intent createShareRankingIntent() {

        String cityName = (searchResultCityName != null) ? searchResultCityName : rankingData.getCityName();

        return Intent.createChooser(ShareCompat.IntentBuilder.from(this)
                .setChooserTitle("Share ranking data")
                .setType("text/plain")
                .setText(DESTINATION_URL + cityName)
                .getIntent(), getString(R.string.action_share));
    }

    private void showErrorMessage() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.network_error)
                .setMessage(R.string.network_error_msg)
                .setNegativeButton(R.string.error_dismiss_button, (dialog, which) -> dialog.dismiss()).create().show();
    }


    @Override
    protected void onRestart() {
        super.onRestart();
        if (rankingData != null) {
            setupRankingFromViewModel(rankingData);
        } else {
            bindSearchUI(searchResultCityName);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mShimmerMinContainer.startShimmer();
        mShimmerMaxContainer.startShimmer();

    }

    @Override
    protected void onPause() {
        disposables.dispose();
        mShimmerMinContainer.stopShimmer();
        mShimmerMaxContainer.stopShimmer();
        super.onPause();
    }

}
