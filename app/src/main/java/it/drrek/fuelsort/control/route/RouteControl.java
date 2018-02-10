package it.drrek.fuelsort.control.route;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.EditText;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import it.drrek.fuelsort.entity.settings.SearchParams;
import it.drrek.fuelsort.entity.station.Distributore;
import it.drrek.fuelsort.entity.route.Region;
import it.drrek.fuelsort.entity.route.Route;
import it.drrek.fuelsort.R;
import it.drrek.fuelsort.model.DistributoriManager;
import it.drrek.fuelsort.model.SearchParamsModel;

/**
 * Route manager is the class delegated to search for the best path.
 * Created by Luca on 09/12/2017.
 */

public class RouteControl {

    private Context activityContext;
    private RouteControlListener routeControlListener;
    private String from, to;

    public RouteControl(Context context) {
        activityContext = context;
    }

    /**
     * Questo metodo viene eseguito quando si deve iniziare la ricerca di un percorso
     */
    public void findRoute() {
        EditText toET = ((Activity)activityContext).findViewById(R.id.to);
        EditText fromET = ((Activity)activityContext).findViewById(R.id.from);
        from = fromET.getText().toString();
        to = toET.getText().toString();

        /* Cerco il percorso normale.
         */
        try {
            new DirectionFinderAsync(from, to, null, new DirectionFinderListener() {
                @Override
                public void onDirectionFinderStart() {}

                /* Trovato il percorso normale cerco i distributori
                 */
                @Override
                public void onDirectionFinderSuccess(List<Route> routes) {
                    Log.d("DirectionFinderSuccess", "Success");
                    if (!routes.isEmpty()) {
                        Route r = routes.get(0);
                        new LoadStationForRoute().execute(r);
                    }
                }

                @Override
                public void directionFinderException(Exception e) {
                    routeControlListener.exceptionSearchingForRoute(e);
                }
            }).execute();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setListener(RouteControlListener rml){
        this.routeControlListener = rml;
    }

    private class LoadStationForRoute extends AsyncTask<Route, Integer, LoadStationForRoute.Result> {
        private Route defaultRoute;
        class Result
        {
            Route strada;
            Distributore distributore;

            Result(Route s, Distributore d){strada=s; distributore=d;}
        };

        @Override
        protected Result doInBackground(final Route... r) {
            abstract class ComputeSearchOnSingleRegion implements Runnable{
                private Map<Distributore, Integer> risultati;
                private Region s;
                ComputeSearchOnSingleRegion(Region s){
                    this.s=s;
                }
                Region getStep(){return s;}
                private Map<Distributore, Integer> getRisultati() {
                    return risultati;
                }
                void setRisultati(Map<Distributore, Integer> risultati) {
                    this.risultati = risultati;
                }
            }

            final DistributoriManager distManager = new DistributoriManager(activityContext);
            final SearchParams searchParams = SearchParamsModel.getSearchParams(activityContext);

            final Map<Thread, ComputeSearchOnSingleRegion> threads  = new HashMap<>();
            List<Distributore> distributoriTrovatiAllaFine;
            this.defaultRoute =r[0];
            for (final Region s : this.defaultRoute.getRegions()){
                ComputeSearchOnSingleRegion runnableTemp = new ComputeSearchOnSingleRegion(s){
                    @Override
                    public void run() {
                        List<Distributore>trovatiInRegion = distManager.getStationsInBound(this.getStep().getSOBound().latitude,this.getStep().getNEBound().latitude,this.getStep().getSOBound().longitude,this.getStep().getNEBound().longitude, searchParams, true, s.isToll());

                        Map<Distributore, Integer> risultati = new HashMap<>();
                        List<LatLng> puntiInRegion = s.getPoints();
                        int lunghezzaPerOra = s.getDistance();
                        for(int i=0; i<puntiInRegion.size()-1; i++) {

                            final int R = 6371; // Radius of the earth
                            double lat1=puntiInRegion.get(i).latitude;
                            double lon1=puntiInRegion.get(i).longitude;
                            double lat2=puntiInRegion.get(i+1).latitude;
                            double lon2=puntiInRegion.get(i+1).longitude;

                            double latDistance = Math.toRadians(lat2 - lat1);
                            double lonDistance = Math.toRadians(lon2 - lon1);
                            double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                                    * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
                            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                            double distance = R * c * 1000; // convert to meters

                            lunghezzaPerOra += distance;

                            for (Distributore distributoreCorrente : trovatiInRegion){
                                double lat3=distributoreCorrente.getLat();
                                double lon3=distributoreCorrente.getLon();
                                double EARTH_RADIUS_KM=6371;

                                double y=Math.sin(lon3-lon1)*Math.cos(lat3);
                                double x = Math.cos(lat1) * Math.sin(lat3) - Math.sin(lat1) * Math.cos(lat3) * Math.cos(lat3 - lat1);
                                double bearing1=Math.toDegrees(Math.atan2(y,x));
                                bearing1=360-(bearing1+360%360);

                                double y2 = Math.sin(lon2 - lon1) * Math.cos(lat2);
                                double x2 = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lat2 - lat1);
                                double bearing2 = Math.toDegrees(Math.atan2(y2, x2));
                                bearing2 = 360 - (bearing2 + 360 % 360);

                                double lat1Rads = Math.toRadians(lat1);
                                double lat3Rads = Math.toRadians(lat3);
                                double dLon = Math.toRadians(lon3 - lon1);

                                double distanceAC = Math.acos(Math.sin(lat1Rads) * Math.sin(lat3Rads)+Math.cos(lat1Rads)*Math.cos(lat3Rads)*Math.cos(dLon)) * EARTH_RADIUS_KM;
                                double risultato = Math.abs(Math.asin(Math.sin(distanceAC/EARTH_RADIUS_KM)*Math.sin(Math.toRadians(bearing1)-Math.toRadians(bearing2))) * EARTH_RADIUS_KM)*1000;
                                if(risultato<50){
                                    int distanzaDistributoreDaStart = (int) (lunghezzaPerOra+risultato);
                                    if(risultati.containsKey(distributoreCorrente)){
                                        if(risultati.get(distributoreCorrente) > distanzaDistributoreDaStart){
                                            risultati.put(distributoreCorrente, distanzaDistributoreDaStart);
                                        }
                                    }else {
                                        risultati.put(distributoreCorrente, distanzaDistributoreDaStart);
                                    }
                                }
                            }
                        }
                        setRisultati(risultati);
                    }
                };
                Thread threadTemp = new Thread(runnableTemp);
                threadTemp.start();
                threads.put(threadTemp, runnableTemp);
            }

            for(Thread t : threads.keySet()){
                try{
                    t.join();
                    distributoriTrovatiConDistanza.putAll(threads.get(t).getRisultati());
                } catch (Exception e){
                    Log.d("Severe warning", "Compute search on a region did something wrong\n"+ Arrays.toString(e.getStackTrace()));
                }
            }

            distributoriTrovati = new ArrayList<>(distributoriTrovatiConDistanza.keySet());
            Collections.sort(distributoriTrovati, new Comparator<Distributore>() {
                @Override
                public int compare(Distributore fruit1, Distributore fruit2)
                {
                    if(distributoriTrovatiConDistanza.get(fruit1)>distributoriTrovatiConDistanza.get(fruit2)){
                        return 1;
                    }
                    return -1;
                }
            });

            /*Distributore fine = new Distributore(-1,null, null, null, null, null, null, null, defaultRoute.getEndLocation().latitude ,defaultRoute.getEndLocation().longitude);
            int distanza = defaultRoute.getDistance().getValue();
            if(distanza <= distributoriTrovatiConDistanza.get(distributoriTrovati.get(distributoriTrovati.size()-1))){
                distanza = distributoriTrovatiConDistanza.get(distributoriTrovati.get(distributoriTrovati.size()-1)) + 1;
            }
            distributoriTrovatiConDistanza.put(fine, distanza);
            distributoriTrovati.add(distributoriTrovati.size(), fine);*/


            System.out.println("Distanza percorso: "+defaultRoute.getDistance().getValue());
            System.out.println("Massima autonomia: "+autonomiaInMetri);
            System.out.println("Divisione: "+(int)Math.ceil(defaultRoute.getDistance().getValue()/(double)autonomiaInMetri));
            System.out.println("Distributori presenti (compreso fine): "+distributoriTrovati.size());
            memoized = new double[distributoriTrovati.size()];

            for(int y = 0; y<memoized.length; y++){
                memoized[y] = Double.MAX_VALUE;
            }

            opt(0, (int)Math.ceil(defaultRoute.getDistance().getValue()/(double)autonomiaInMetri));

            DecimalFormat df = new DecimalFormat("#.####");
            System.out.print("\t");
            for(double d : memoized){
                System.out.print(df.format(d)+"\t");
            }
            System.out.println();

            distributoriTrovatiAllaFine = new ArrayList<>();
            findSolution(distributoriTrovati.size()-1, (int)Math.ceil(defaultRoute.getDistance().getValue()/(double)autonomiaInMetri));


            for(Distributore d : distributoriTrovatiAllaFine){
                System.out.println(d);
            }

            return null;
            /*
            Collections.sort(results, new Comparator<Distributore>() {
                @Override
                public int compare(Distributore fruit2, Distributore fruit1)
                {
                    double f1Price = fruit1.getBestPriceUsingSearchParams(), f2Price = fruit2.getBestPriceUsingSearchParams();
                    if(f1Price<f2Price) return 1;
                    else if(f1Price==f2Price) return 0;
                    return -1;
                }
            });

            Distributore bestDistrTillNow = null;
            float bestPrezzoTillNow = 9999.99f;
            Route bestRouteTillNow = null;
            for(Distributore d : results){

                //Condizione d'uscita principale
                if(bestPrezzoTillNow < d.getBestPriceUsingSearchParams()){
                    return new Result(bestRouteTillNow, bestDistrTillNow);
                }

                try {
                    List<Route> resultList = new DirectionFinderSync(from, to, d.getPosizione()).execute();
                    Route result = resultList.get(0);
                    Log.d("RouteControl","Analizzo il primo risultato: ");
                    Log.d("RouteControl", "Lat:"+d.getLat()+"  Lng:"+d.getLon());
                    Log.d("RouteControl", "Lunghezza nuovo: "+result.getDistance().getValue()+"m Lunghezza vecchio:"+ defaultRoute.getDistance().getValue()+"m");
                    Log.d("RouteControl", "Autostrade nuovo: "+result.getNumeroDiPagamenti()+"  Autostrade vecchio:"+ defaultRoute.getNumeroDiPagamenti()+" ");
                    Log.d("RouteControl", "Durata nuovo: "+result.getDuration().getValue()+"m Durata vecchio:"+ defaultRoute.getDuration().getValue()+"m");
                    if(
                            result.getDistance().getValue() - defaultRoute.getDistance().getValue() <= Route.DISTANZA_MASSIMA_AGGIUNTA_AL_PERCORSO &&
                            result.getNumeroDiPagamenti() <= defaultRoute.getNumeroDiPagamenti() &&
                            result.getDuration().getValue() - defaultRoute.getDuration().getValue() <= Route.TEMPO_MASSIMO_AGGIUNTO_AL_PERCORSO){

                        //Questo controllo serve per controllare tutti i distributori con lo stesso prezzo.
                        float tempPrezzo = d.getBestPriceUsingSearchParams();
                        if((bestPrezzoTillNow == tempPrezzo && (bestRouteTillNow == null || bestRouteTillNow.getDuration().getValue()>result.getDuration().getValue())) || (tempPrezzo < bestPrezzoTillNow)){
                            bestRouteTillNow = result;
                            bestDistrTillNow = d;
                            bestPrezzoTillNow = tempPrezzo;
                        }
                    } else{
                        Log.d("RouteControl", "Risultato NON accettabile, passo al prossimo. \n");
                    }
                } catch (UnsupportedEncodingException | JSONException e) {
                    e.printStackTrace();
                }
            }

            //In questo caso non sono stati trovati risultati.
            //TODO: CERCARE DI EVITARE QUESTA SITUAZIONE!
            return null;
            */
        }

        int capienzaSerbatoio = 15;
        int kmxl = 20;
        int autonomiaInMetri = capienzaSerbatoio * kmxl * 1000;
        List<Distributore> distributoriTrovati;
        final Map<Distributore, Integer> distributoriTrovatiConDistanza = new HashMap<>();
        double memoized[];
        private double opt(int indice, int quantiDistributoriMancano) {

            if (indice == distributoriTrovati.size() && quantiDistributoriMancano == 0) {
                System.out.println("arrivato a caso base");
                return 0;
            } else if(indice>=distributoriTrovati.size() || quantiDistributoriMancano <= 0){
                return Double.MAX_VALUE / 2;
            }

            if (memoized[indice] != Double.MAX_VALUE) {
                System.out.println("Sono stato già calcolato " +indice+" "+memoized[indice]);
                return memoized[indice];
            }

            if(indice < distributoriTrovati.size()){
                Distributore d = distributoriTrovati.get(indice);
                int distanzaDiD = distributoriTrovatiConDistanza.get(distributoriTrovati.get(indice));
                memoized[indice] = Double.MAX_VALUE;
                int y = indice +1;
                for (; y < distributoriTrovati.size() &&  distributoriTrovatiConDistanza.get(distributoriTrovati.get(y)) + distanzaDiD <= autonomiaInMetri; y++) {
                    memoized[indice] = Math.min(memoized[indice], (Math.abs(distributoriTrovatiConDistanza.get(distributoriTrovati.get(y)) - distanzaDiD) * d.getBestPriceUsingSearchParams() / 1000) + opt(y, quantiDistributoriMancano - 1));
                }
                if(y==distributoriTrovati.size()){
                    memoized[indice] = Math.min(memoized[indice], (Math.abs(defaultRoute.getDistance().getValue() - distanzaDiD) * d.getBestPriceUsingSearchParams() / 1000)+opt(y, quantiDistributoriMancano-1));
                }
                double temp =opt(indice + 1, quantiDistributoriMancano);
                memoized[indice] = Math.min(memoized[indice], temp);
                System.out.println("Per "+indice+" alla fine ho scelto " + memoized[indice]);
                return memoized[indice];
            }

            System.out.println("Qui non sarei dovuto arrivare, indice " + indice +"  quanti mancano"+quantiDistributoriMancano);

            return Double.MAX_VALUE / 2;
        }

        private void findSolution(int indice, int quantiDistributoriMancano){

        }


        @Override
        protected void onPostExecute(Result r) {
            routeControlListener.routeFound(r.strada, r.distributore);
        }
    }
}
