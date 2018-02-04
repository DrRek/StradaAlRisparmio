package it.drrek.fuelsort.control.route;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import it.drrek.fuelsort.entity.route.Distance;
import it.drrek.fuelsort.entity.route.Duration;
import it.drrek.fuelsort.entity.route.Route;
import it.drrek.fuelsort.entity.route.Region;

public class DirectionFinderAsync extends DirectionFinder {
    private DirectionFinderListener listener;

    public DirectionFinderAsync(String origin, String destination, LatLng waypoint, DirectionFinderListener listener) {
        super(origin, destination, waypoint);
        this.listener = listener;
    }

    public void execute() throws UnsupportedEncodingException {
        if(listener!=null) listener.onDirectionFinderStart();
        String url = DIRECTION_URL_API + createUrl();
        new DownloadRawData().execute(url);
    }

    private String createUrl() {
        try {
            String urlOrigin = URLEncoder.encode(origin, "utf-8");
            String urlDestination = URLEncoder.encode(destination, "utf-8");
            if (waypoint == null)
                return "origin=" + urlOrigin + "&destination=" + urlDestination + "&key=" + GOOGLE_API_KEY;
            else
                return "origin=" + urlOrigin + "&destination=" + urlDestination + "&waypoints=" + waypoint + "&key=" + GOOGLE_API_KEY;
        } catch (UnsupportedEncodingException e) {
            Log.e("ERROR", "Impossible to create url for path request");
            e.printStackTrace();
            return "";
        }
    }

    private class DownloadRawData extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String link = params[0];
            try {
                URL url = new URL(link);
                System.out.println(link);
                InputStream is = url.openConnection().getInputStream();
                StringBuilder buffer = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line).append("\n");
                }
                return buffer.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String res) {
            try {
                parseJSon(res);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void parseJSon(String data) throws JSONException {
        if (data == null)
            return;
        List<Route> routes = new ArrayList<>();
        JSONObject jsonData = new JSONObject(data);
        JSONArray jsonRoutes = jsonData.getJSONArray("routes");
        for (int i = 0; i < jsonRoutes.length(); i++) {
            JSONObject jsonRoute = jsonRoutes.getJSONObject(i);
            Route route = new Route();

            JSONObject overview_polylineJson = jsonRoute.getJSONObject("overview_polyline");
            JSONArray jsonLegs = jsonRoute.getJSONArray("legs");
            JSONObject jsonLegStart = jsonLegs.getJSONObject(0);
            JSONObject jsonLegEnd = jsonLegs.getJSONObject(jsonLegs.length()-1);

            JSONObject jsonBounds = jsonRoute.getJSONObject("bounds");
            JSONObject jsonNE = jsonBounds.getJSONObject("northeast");
            JSONObject jsonSO = jsonBounds.getJSONObject("southwest");

            JSONObject jsonEndLocation = jsonLegEnd.getJSONObject("end_location");
            JSONObject jsonStartLocation = jsonLegStart.getJSONObject("start_location");

            Distance distance = new Distance("No string data for path with waypoint, just waypoint", 0);
            Duration duration = new Duration("No string data for path with waypoint, just waypoint", 0);
            for (int y=0; y<jsonLegs.length();y++){
                JSONObject jsO = (JSONObject) jsonLegs.get(y);
                JSONObject jsODuration = jsO.getJSONObject("duration");
                JSONObject jsODistance = jsO.getJSONObject("distance");
                duration.setValue(duration.getValue() + jsODuration.getInt("value"));
                distance.setValue(distance.getValue() + jsODistance.getInt("value"));
                route.addRegions(calculateRegions(jsO.getJSONArray("steps")));
            }
            route.setDistance(distance);
            route.setDuration(duration);

            route.setNortheast(new LatLng(jsonNE.getDouble("lat"), jsonNE.getDouble("lng")));
            route.setSouthwest(new LatLng(jsonSO.getDouble("lat"), jsonSO.getDouble("lng")));
            route.setEndAddress(jsonLegEnd.getString("end_address"));
            route.setStartAddress(jsonLegStart.getString("start_address"));
            route.setStartLocation(new LatLng(jsonStartLocation.getDouble("lat"), jsonStartLocation.getDouble("lng")));
            route.setEndLocation(new LatLng(jsonEndLocation.getDouble("lat"), jsonEndLocation.getDouble("lng")));
            route.setPoints(decodePolyLine(overview_polylineJson.getString("points")));
            route.setParameters(createUrl());

            routes.add(route);
        }

        if(listener!=null)listener.onDirectionFinderSuccess(routes);
    }

    /**
     * Questo è la nuova funzione per il calcolo delle region, per ogni region verrà poi lanciato un thread
     * Le regioni sono divise in "a pagamento" e "non a pagamento" ed in base a questo parametro si evitano ricerche che necessitano l'uscita dall'autostrada.
     * @param steps
     * @return
     * @throws JSONException
     */
    private List<Region> calculateRegions(JSONArray steps) throws JSONException{
        List<Region> regions = new ArrayList<>();
        for(int i = 0; i < steps.length(); i++) {
            boolean isToll = steps.getJSONObject(i).getString("html_instructions").toLowerCase().contains("toll");
            int distance = steps.getJSONObject(i).getJSONObject("distance").getInt("value");
            List<LatLng> currentStepPolilyne = decodePolyLine(steps.getJSONObject(i).getJSONObject("polyline").getString("points"));

            double SOBoundLat = currentStepPolilyne.get(0).latitude;
            double SOBoundLng = currentStepPolilyne.get(0).longitude;
            double NEBoundLat = currentStepPolilyne.get(0).latitude;
            double NEBoundLng = currentStepPolilyne.get(0).longitude;
            for (int y = 1; y < currentStepPolilyne.size(); y++) {
                SOBoundLat = Math.min(SOBoundLat, currentStepPolilyne.get(y).latitude);
                SOBoundLng = Math.min(SOBoundLng, currentStepPolilyne.get(y).longitude);
                NEBoundLat = Math.max(NEBoundLat, currentStepPolilyne.get(y).latitude);
                NEBoundLng = Math.max(NEBoundLng, currentStepPolilyne.get(y).longitude);
            }
            Region currentRegion = new Region(new LatLng(SOBoundLat, SOBoundLng), new LatLng(NEBoundLat, NEBoundLng), distance, isToll);
            if(     !regions.isEmpty() &&
                    regions.get(regions.size()-1).isToll() == isToll &&
                    regions.get(regions.size()-1).getDistance() + distance <= Route.SUGGESTED_REGION_SIZE){
                Region oldRegion = regions.get(regions.size()-1);
                oldRegion.merge(currentRegion);
            } else {
                regions.add(currentRegion);
            }
        }

        for(Region s : regions){
            if(s.isToll()) {
                s.addMargin(Route.BOUNDS_FOR_TOLLS_REGIONS);
            } else {
                s.addMargin(Route.BOUNDS_FOR_NORMAL_REGIONS);
            }
        }

        return regions;
    }

    private List<LatLng> decodePolyLine(final String poly) {
        int len = poly.length();
        int index = 0;
        List<LatLng> decoded = new ArrayList<>();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int b;
            int shift = 0;
            int result = 0;
            do {
                b = poly.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = poly.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            decoded.add(new LatLng(
                    lat / 100000d, lng / 100000d
            ));
        }

        return decoded;
    }
}