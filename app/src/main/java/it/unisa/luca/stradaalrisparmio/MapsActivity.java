package it.unisa.luca.stradaalrisparmio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unisa.luca.stradaalrisparmio.api.strada.DirectionFinder;
import it.unisa.luca.stradaalrisparmio.api.strada.DirectionFinderListener;
import it.unisa.luca.stradaalrisparmio.api.strada.Route;
import it.unisa.luca.stradaalrisparmio.stazioni.Distributore;
import it.unisa.luca.stradaalrisparmio.stazioni.database.DBmanager;
import it.unisa.luca.stradaalrisparmio.support.BitmapCreator;
import it.unisa.luca.stradaalrisparmio.support.LoadingShow;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static Double SCREEN_DIMENSION_FOR_DATA = 0.15;

    private boolean loadStationOnPosition;
    private GoogleMap mMap;
    private EditText to, from;
    private DBmanager manager;
    private LoadingShow loaderView;
    private volatile HashMap<Marker, Distributore> distributoriMarker;
    Double lastMinLat, lastMaxLat, lastMinLng, lastMaxLng;
    LoadStationInScreen old;

    private DBmanager.SearchParams params;

    private Context context;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("test","on create wtf: " + (mMap==null));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        context = getApplicationContext();

        loaderView = LoadingShow.getLoader(this);
        loaderView.add("Starting app...");

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        manager = new DBmanager(this);
        manager.start();

        //Roba delle mappe
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Roba dei campi d'input
        from = (EditText) findViewById(R.id.from);
        to = (EditText) findViewById(R.id.to);

        to.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                Toast.makeText(getApplicationContext(), "Inizio la ricerca!", Toast.LENGTH_SHORT).show();
                return findForPath();
            }
        });

        //To avoid searching once i move into the map, use the button to undo
        loadStationOnPosition = false;

        loaderView.remove("Starting app...");
    }


    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences pref = getSharedPreferences("it.unisa.luca.stradaalrisparmio.pref", MODE_PRIVATE);
        String prefCarburante = pref.getString("carburante", "diesel");
        boolean prefSelf = pref.getBoolean("self", true);
        int prefKmxl = pref.getInt("kmxl", 20);
        params = new DBmanager.SearchParams(prefCarburante, prefSelf, prefKmxl);

        old = null; //necessario per aggiornare bene lo schermo quando si ritorna da un'altra attività
        removeAllStationFoundInScreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences pref = getSharedPreferences("it.unisa.luca.stradaalrisparmio.pref", MODE_PRIVATE);
        SharedPreferences.Editor edit = pref.edit();
        edit.putLong("lat", Double.doubleToRawLongBits(mMap.getCameraPosition().target.latitude));
        edit.putLong("lng", Double.doubleToRawLongBits(mMap.getCameraPosition().target.longitude));
        edit.putFloat("zoom", mMap.getCameraPosition().zoom);
        edit.apply();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                // User chose the "Settings" item, show the app settings UI...
                openSettings();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        SharedPreferences pref = getSharedPreferences("it.unisa.luca.stradaalrisparmio.pref", MODE_PRIVATE);

        if(!pref.contains("zoom")) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(40.769817, 14.7900013), 15.0f));
        }else{
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(Double.longBitsToDouble(pref.getLong("lat", Double.doubleToRawLongBits(40.769817))),Double.longBitsToDouble(pref.getLong("lng", Double.doubleToRawLongBits(14.7900013)))), pref.getFloat("zoom", 15.0f)));
        }
        old = null;
        mMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
            @Override
            public void onCameraIdle() {
                setMarkersBasedOnPosition();
            }
        });
       /* mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                setInfoWindowBasedOnStation(distributoriMarker.get(marker));
                return false;
            }
        });*/
    }

    /**
     * THIS SECTION CONTAIN SEARCH FOR PERFECT ROUTE
     **/

    private boolean findForPath() {
        try {
            new DirectionFinder(from.getText().toString(), to.getText().toString(), null, new DirectionFinderListener() {
                @Override
                public void onDirectionFinderStart() {
                    loadStationOnPosition=false;
                    removeAllStationFoundInScreen();
                    mMap.clear();
                    loaderView.add("Searching path");
                }

                @Override
                /*
                 * Levare la classe Distance
                 * Inserire variabile globale al posto di 2000
                 */
                public void onDirectionFinderSuccess(List<Route> routes) {
                    Log.d("DirectionFinderSuccess", "Success");
                    if (!routes.isEmpty()) {
                        Route r = routes.get(0);
                        /*mMap.addMarker(new MarkerOptions().title("Start").position(r.startLocation));
                        mMap.addMarker(new MarkerOptions().title("End").position(r.endLocation));
                        PolylineOptions plo = new PolylineOptions();
                        plo.geodesic(true);
                        plo.color(Color.BLUE);
                        plo.width(10);
                        for (int i = 0; i < r.points.size(); i++) {
                            plo.add(r.points.get(i));
                        }*/
                        new LoadStationForRoute().execute(r);
                    }
                }
            }).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private class LoadStationForRoute extends AsyncTask<Route, Integer, List<Distributore>> {
        @Override
        protected List<Distributore> doInBackground(Route... r) {
            return manager.getZoneStation(r[0], params);
        }

        @Override
        protected void onPostExecute(List<Distributore> results) {
            if (results != null) {
                final Distributore d = results.get(0);
                try {
                    new DirectionFinder(from.getText().toString(), to.getText().toString(), d.getPosizione(), new DirectionFinderListener() {
                        @Override
                        public void onDirectionFinderStart() {
                        }

                        @Override
                        /*
                            * Levare la classe Distance
                            */
                        public void onDirectionFinderSuccess(List<Route> routes) {
                            Log.d("DirectionFinderSuccess", "Success");
                            if (!routes.isEmpty()) {
                                Route r = routes.get(0);
                                mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(BitmapCreator.getStartBitmap(context))).title("Start").position(r.startLocation));
                                mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(BitmapCreator.getFinishBitmap(context))).title("End").position(r.endLocation));
                                mMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(BitmapCreator.getBitmap(context, Color.RED, d.getLowestPrice(params), d.getBandiera()))).title(d.getId() + "").draggable(false).visible(true).alpha(0.95f).position(d.getPosizione()));
                                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(r.getLatLngBounds(), 100)); //100 is just some padding
                                PolylineOptions plo = new PolylineOptions();
                                plo.geodesic(true);
                                plo.color(Color.BLUE);
                                plo.width(10);
                                for (int i = 0; i < r.points.size(); i++) {
                                    plo.add(r.points.get(i));
                                }
                                mMap.addPolyline(plo);
                            }
                        }
                    }).execute();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d("Found", "name: " + d.getId() + " price: " + d.getLowestPrice(params));

            }
            loaderView.remove("Searching path");
        }
    }


    /**
     * THIS SECTION CONTAIN SEARCH BASED ON SCREEN
     **/

    void setMarkersBasedOnPosition() {
        if(loadStationOnPosition) {
            if (old != null) {
                old.cancel(true);
            } else {
                this.lastMinLat = 90.0;
                this.lastMaxLat = -90.0;
                this.lastMinLng = 180.0;
                this.lastMaxLng = -180.0;
            }
            (old = new LoadStationInScreen()).execute();
        }
    }

    void removeAllStationFoundInScreen(){
        if (distributoriMarker != null) {
            Set<Marker> markers = distributoriMarker.keySet();
            for (Marker m : markers) {
                m.remove();
            }
        }
        this.lastMinLat = 90.0;
        this.lastMaxLat = -90.0;
        this.lastMinLng = 180.0;
        this.lastMaxLng = -180.0;
        distributoriMarker = new HashMap<>();
    }

    /**
     * Async task usato per la ricerca dei distributori all'interno dello schermo.
     * Se SCREEN_DIMENSION_FOR_DATA > delle dimensioni dello schermo non viene creato alcun thread
     */
    private class LoadStationInScreen extends AsyncTask<Void, Integer, ArrayList<Distributore>> {
        private Double minLat, maxLat, minLng, maxLng;
        private Double minLatC, maxLatC, minLngC, maxLngC;

        @Override
        protected void onPreExecute() {
            Log.d("Ricerca distributori", "Inizio a cercare distributori.");
            LatLngBounds view = mMap.getProjection().getVisibleRegion().latLngBounds;
            minLat = view.southwest.latitude;
            minLng = view.southwest.longitude;
            maxLat = view.northeast.latitude;
            maxLng = view.northeast.longitude;
            if (mMap.getCameraPosition().zoom<=15f) {
                cancel(true);
                return;
            }else{
                loaderView.add("Cerco distributori nella zona");
            }
            if (view.northeast.latitude <= view.southwest.latitude) {
                minLat = view.northeast.latitude;
                maxLat = view.southwest.latitude;
            }
            if (view.northeast.longitude <= view.southwest.longitude) {
                minLat = view.northeast.longitude;
                maxLat = view.southwest.longitude;
            }
            minLatC = Math.max(minLat, lastMinLat);
            maxLatC = Math.min(maxLat, lastMaxLat);
            minLngC = Math.max(minLng, lastMinLng);
            maxLngC = Math.min(maxLng, lastMaxLng);

            //Scorre tutti i marker già presenti, se non entrano nei bordi dello schermo li rimuove
            LatLng tempPosition;
            Marker temp;
            synchronized (LoadStationInScreen.class) {
                Iterator<Map.Entry<Marker, Distributore>> iter = distributoriMarker.entrySet().iterator();
                while (iter.hasNext()) {
                    temp = iter.next().getKey();
                    tempPosition = temp.getPosition();
                    if (tempPosition.latitude < minLatC || tempPosition.latitude > maxLatC || tempPosition.longitude < minLngC || tempPosition.longitude > maxLngC) {
                        temp.remove();
                        iter.remove();
                    }
                }
            }
        }

        /**
         * Uno schermo è composto da al più 5 rettangoli (in caso di zoom out), il rettangolo "Centrale" l'ho già calcolato dalla precedente ricerca,
         * Evito di ricalcolarlo ed eventualmente ricalcolo solo i rettangoli che rappresentano le aree nuove.
         */
        @Override
        protected synchronized ArrayList<Distributore> doInBackground(Void... voids) {
            if (isCancelled()) {
                lastMinLat = minLatC;
                lastMaxLat = maxLatC;
                lastMinLng = minLngC;
                lastMaxLng = maxLngC;
                return null;
            }
            ArrayList<Distributore> nuovi = new ArrayList<>();
            if (minLat < lastMinLat) {
                nuovi.addAll(manager.getDistributoriInRange(minLat, lastMinLat, minLng, maxLng, params));
                if (isCancelled()) {
                    lastMinLat = minLat;
                    lastMaxLat = maxLatC;
                    lastMinLng = minLngC;
                    lastMaxLng = maxLngC;
                    return null;
                }
            }
            if (maxLat > lastMaxLat) {
                nuovi.addAll(manager.getDistributoriInRange(lastMaxLat, maxLat, minLng, maxLng, params));
                if (isCancelled()) {
                    lastMaxLat = maxLat;
                    lastMinLng = minLngC;
                    lastMaxLng = maxLngC;
                    return null;
                }
            }
            if (minLng < lastMinLng) {
                //Double neededMinLat = Math.max(minLat, lastMinLat);
                // Double neededMaxLat = Math.min(maxLat, lastMaxLat);
                nuovi.addAll(manager.getDistributoriInRange(minLatC, maxLatC, minLng, lastMinLng, params));
                if (isCancelled()) {
                    lastMinLng = minLng;
                    lastMaxLng = maxLngC;
                    return null;
                }
            }
            if (maxLng > lastMaxLng) {
                //Double neededMinLat = Math.max(minLat, lastMinLat);
                //Double neededMaxLat = Math.min(maxLat, lastMaxLat);
                nuovi.addAll(manager.getDistributoriInRange(minLatC, maxLatC, lastMaxLng, maxLng, params));
                if (isCancelled()) {
                    lastMaxLng = maxLng;
                    return null;
                }
            }
            return nuovi;
        }

        protected void onPostExecute(ArrayList<Distributore> nuovi) {/*
            HashMap<Marker, Distributore> tempHashMap = new HashMap<>();
            Bitmap tempBitmap;
            for (Distributore dist : nuovi) {
                tempBitmap = BitmapCreator.getBitmap(context, Color.GRAY, dist.getLowestPrice(params), dist.getBandiera());
                Marker temp = mMap.addMarker(
                        new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(tempBitmap)).title(dist.getId() + "").draggable(false).visible(true).alpha(0.95f).position(dist.getPosizione())
                );
                tempHashMap.put(temp, dist);
            }
            synchronized (LoadStationInScreen.class) {
                distributoriMarker.putAll(tempHashMap);
            }
            lastMinLat = minLat;
            lastMaxLat = maxLat;
            lastMinLng = minLng;
            lastMaxLng = maxLng;
            Log.d("Ricerca distributori", "Ricerca terminata con successo.");*/

            loaderView.remove("Cerco distributori nella zona");

            synchronized (LoadStationInScreen.class) {
                Set<Marker> markers = distributoriMarker.keySet();
                for (Marker m : markers) {
                    nuovi.add(distributoriMarker.get(m));
                    m.remove();
                }
                distributoriMarker = new HashMap<>();
                Collections.sort(nuovi, new Comparator<Distributore>() {
                    @Override
                    public int compare(Distributore distributore, Distributore t1) {
                        return Math.round(distributore.getLowestPrice(params) - t1.getLowestPrice(params));
                    }
                });
                int numeroColoriDaUsare = nuovi.size();
                int green = ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null);
                int red = ResourcesCompat.getColor(getResources(), R.color.red, null);
                float rDifference = Math.abs(Color.red(green) - Color.red(red));
                float gDifference = Math.abs(Color.green(green) - Color.green(red));
                float bDifference = Math.abs(Color.red(green) - Color.blue(red));
                float rIncrement = rDifference/(numeroColoriDaUsare-1);
                float gIncrement = gDifference/(numeroColoriDaUsare-1);
                float bIncrement = bDifference/(numeroColoriDaUsare-1);
                for(int i=0; i<numeroColoriDaUsare; i++){
                    Distributore tempDist = nuovi.get(i);
                    Log.d("color", Color.rgb(Math.round(Color.red(green)+rIncrement*i),Math.round(Color.green(green)+gIncrement*i),Math.round(Color.blue(green)+bIncrement*i))+"");
                    Bitmap tempBitmap = BitmapCreator.getBitmap(context, Color.rgb(Math.round(Color.red(green)+(rIncrement*i)),Math.round(Color.green(green)+(gIncrement*i)),Math.round((Color.blue(green)+bIncrement*i))), tempDist.getLowestPrice(params), tempDist.getBandiera());
                    Marker tempMark = mMap.addMarker(
                            new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(tempBitmap)).title(tempDist.getId() + "").draggable(false).visible(true).alpha(0.95f).position(tempDist.getPosizione())
                    );
                    distributoriMarker.put(tempMark, tempDist);
                }
            }
        }

        protected void onCancelled(ArrayList<Distributore> nuovi) {
            Log.d("Ricerca distributori", "Ricerca cancellata.");
            loaderView.remove("Cerco distributori nella zona");
        }
    }



    /**THIS IS FOR SETTINGS**/

    public void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        //startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out);
    }



    /**THIS IS TO HIDE/VIEW STATION IN SCREEN**/

    public void onChangeStationScreenLoad(View v){
        if(loadStationOnPosition){
            final Toast toast = Toast.makeText(getApplicationContext(), "Rimuovo i distributori nello schermo", Toast.LENGTH_SHORT);
            toast.show();

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    toast.cancel();
                }
            }, 350);
            //Toast.makeText(getApplicationContext(), "Rimuovo i distributori nello schermo", Toast.LENGTH_SHORT).show();
            removeAllStationFoundInScreen();
            loadStationOnPosition = false;
        } else {
            final Toast toast = Toast.makeText(getApplicationContext(), "Aggiungo i distributori nello schermo", Toast.LENGTH_SHORT);
            toast.show();

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    toast.cancel();
                }
            }, 150); //Il delay in questo caso è più corto perché il sistema sarà già in buona parte rallentato dalla ricerca di distributori
            //Toast.makeText(getApplicationContext(), "Aggiungo i distributori nello schermo", Toast.LENGTH_SHORT).show();
            loadStationOnPosition = true;
            setMarkersBasedOnPosition();
        }
    }
}