package no.aardal.kompisleague.views;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import no.aardal.kompisleague.R;
import no.aardal.kompisleague.controllers.RiotAPI;
import no.aardal.kompisleague.models.League;
import no.aardal.kompisleague.models.Summoner;
import no.aardal.kompisleague.utils.Config;
import no.aardal.kompisleague.utils.TinyDB;
import no.aardal.kompisleague.views.adapters.MainRecyclerViewAdapter;
import retrofit.Call;
import retrofit.Callback;
import retrofit.GsonConverterFactory;
import retrofit.Response;
import retrofit.Retrofit;

public class MainActivity extends AppCompatActivity implements AddFriendDialog.NoticeDialogListener {

    private TextView text;
    private ArrayList<Summoner> summoners;
    private Context self = this;
    private int MY_PERMISSIONS_SEND_SMS;

    TinyDB tinyDB;
    ArrayList<String> staticSummoners = new ArrayList<>();

    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipreRefresh;
    private MainRecyclerViewAdapter recyclerAdapter;
    private RecyclerView.LayoutManager recyclerViewLayoutManager;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tinyDB = new TinyDB(this);

        checkPermission();

        if (firstTimeLaunching()) {
            storeSummonerListToDb();
        } else {
            getSummonerListFromDb();
        }



        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initViews();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AddFriendDialog addFriend = new AddFriendDialog();
                addFriend.show(getSupportFragmentManager(), "addFriend");
            }
        });


    }

    private void initViews() {
        text = (TextView) findViewById(R.id.text);
        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerViewLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(recyclerViewLayoutManager);
        swipreRefresh = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
        swipreRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipreRefresh.setRefreshing(true);
                requestData();
            }
        });

        requestData();

    }

    private boolean firstTimeLaunching() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (settings.getBoolean("first_time", true)) {
            settings.edit().putBoolean("first_time", false).apply();
            return true;
        } else {
            return false;
        }
    }

    private void storeSummonerListToDb() {
        for(String summoner : Config.summonernames) {
            staticSummoners.add(summoner);
        }
        tinyDB.putListString("summoners", staticSummoners);
    }

    private void getSummonerListFromDb() {
        if (tinyDB.getListString("summoners") != null) {
            staticSummoners = tinyDB.getListString("summoners");
        } else {
            storeSummonerListToDb();
        }
    }


    private void requestData() {

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(Config.baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        final RiotAPI riot = retrofit.create(RiotAPI.class);




        Call<Map> call = riot.getSummoner(getQueryParamsFromArray(staticSummoners), Config.urlParamKey);
        call.enqueue(new Callback<Map>() {
            @Override
            public void onResponse(Response<Map> response, Retrofit retrofit) {
                final Integer res = response.code();
                Log.d("RESPONSE MESSAGE: ", response.message());
                Log.d("RESPONSE RAW: ", response.raw().toString());
                Log.d("RESPONSE CODE: ", res.toString());


                HashMap<String, Double> summonerIdMap = new HashMap<>();
                summoners = new ArrayList<>();
                for (String name : staticSummoners) {
                    Map item = (Map) response.body().get(name);
                    Summoner summoner = new Summoner().build(item);
                    summonerIdMap.put(summoner.name, summoner.id);
                    summoners.add(summoner);
                }


                final ArrayList<Double> summonerIdsList = new ArrayList<>(summonerIdMap.values());


                Call<Map> mapCall = riot.getRanked("euw", getSummoneridsFromList(summonerIdsList), Config.urlParamKey);
                mapCall.enqueue(new Callback<Map>() {
                    @Override
                    public void onResponse(Response<Map> response, Retrofit retrofit) {
                        Integer resp = response.code();
                        Log.d("RESPONSE MESSAGE: ", response.message());
                        Log.d("RESPONSE RAW: ", response.raw().toString());
                        Log.d("RESPONSE CODE: ", resp.toString());


                        for (Summoner summoner : summoners) {
                            ArrayList<Map> item = (ArrayList<Map>) response.body().get("" + summoner.id.intValue());
                            Log.d("SUMMONER ID: ", "" + summoner.id.intValue());
                            League league = new League().build(item.get(0));
                            summoner.league = league;
                        }


                        recyclerAdapter = new MainRecyclerViewAdapter(summoners, self, getSupportFragmentManager());
                        recyclerView.setAdapter(recyclerAdapter);

                        if (swipreRefresh.isRefreshing()) {
                            swipreRefresh.setRefreshing(false);
                        }


                    }

                    @Override
                    public void onFailure(Throwable t) {

                    }
                });


            }

            @Override
            public void onFailure(Throwable t) {
                Log.d("FAILED", "FAILED");

            }
        });
    }

    private String getQueryParamsFromArray(ArrayList<String> summoners) {
        StringBuilder build = new StringBuilder();
        for (String summoner : summoners) {
            build.append(summoner + ",");
        }
        return build.length() > 0 ? build.substring(0, build.length() - 1): "";
    }

    private String getSummoneridsFromList(ArrayList<Double> summonerIds) {
        StringBuilder builder = new StringBuilder();
        String param;
            for (Double id : summonerIds) {
                builder.append(id.longValue() + ",");
            }
        param = builder.toString();
        Log.d("PARAMS:", param);
        return param;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    public void checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.SEND_SMS)) {

                Toast.makeText(this, "Show explaination", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS}, MY_PERMISSIONS_SEND_SMS);
            }
        }
    }

    @Override
    public void onPositiveDialogClick(AppCompatDialogFragment dialog, String summoner, String phoneNumber) {
        staticSummoners.add(summoner);
        tinyDB.putListString("summoners", staticSummoners);
    }

    @Override
    public void onNegativeDialogClick(AppCompatDialogFragment dialog) {
        Toast.makeText(this, "HELLO IT WORKS NEGATIVE", Toast.LENGTH_SHORT).show();
    }
}
