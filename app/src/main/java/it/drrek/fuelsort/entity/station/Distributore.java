package it.drrek.fuelsort.entity.station;

import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.util.ArrayList;

import it.drrek.fuelsort.entity.settings.SearchParams;

/**
 * Da riempire
 * Created by luca on 08/10/17.
 */

public class Distributore implements Comparable, Serializable{

    private static final long serialVersionUID = 1L;

    private int id;
    private String gestore;
    private String bandiera;
    private String tipoImpianto;
    private String nome;
    private String indirizzo;
    private String comune;
    private String provincia;
    private double lat;
    private double lon;
    private ArrayList<Pompa> pompe;
    private float computedPrice;

    public Distributore(int id, String gestore, String bandiera, String tipoImpianto, String nome, String indirizzo, String comune, String provincia, double lat, double lon){
        this.id = id;
        this.gestore = gestore;
        this.bandiera = bandiera;
        this.tipoImpianto = tipoImpianto;
        this.nome = nome;
        this.indirizzo = indirizzo;
        this.comune = comune;
        this.provincia = provincia;
        if(lat<=1 && lat>=-1 && lon<=1 && lon>=-1){
            this.lat = Math.toDegrees(lat);
            this.lon = Math.toDegrees(lon);
        }else {
            this.lat = lat;
            this.lon = lon;
        }

        this.pompe = null;
    }

    public int getId(){return id;}
    public String getGestore(){return gestore;}
    public String getBandiera(){return bandiera;}
    public String getTipoImpianto(){return tipoImpianto;}
    public String getNome(){return nome;}
    public String getIndirizzo(){return indirizzo;}
    public String getComune(){return comune;}
    public String getProvincia(){return provincia;}
    public double getLat(){return lat;}
    public double getLon(){return lon;}

    public void setPompe(ArrayList<Pompa> pompe){
        this.pompe = pompe;
    }

    public ArrayList<Pompa> getPompe() {
        return pompe;
    }

    @Override
    public int compareTo(@NonNull Object o) {
        if(((Distributore)o).getId()>this.id){
            return -1;
        }else if(((Distributore)o).getId()<this.id){
            return 1;
        }
        return 0;
    }

    public float setPriceByParams(SearchParams params){
        computedPrice = Float.MAX_VALUE;
        for(Pompa p : pompe){
            float prezzo = p.getPrezzo();
            if(params.checkCarburante(p.getCarburante()) && (params.isSelf() || (!params.isSelf() && !p.isSelf())) && prezzo<computedPrice) {
                if(p.getPrezzo()>=0.1) {
                    computedPrice = prezzo;
                }
            }
        }
        return computedPrice;
    }

    void setPrice(float price){
        this.computedPrice = price;
    }

    public float getBestPriceUsingSearchParams(){
        return computedPrice;
    }

    public String toString(){
        StringBuilder toString = new StringBuilder("\n" +
                "Id: " + id + "\n" +
                "Gestore: " + gestore + "\n" +
                "Bandiera: " + bandiera + "\n" +
                "TipoImpianto: " + tipoImpianto + "\n" +
                "Nome: " + nome + "\n" +
                "Indirizzo: " + indirizzo + "\n" +
                "Comune: " + comune + "\n" +
                "Provincia: " + provincia + "\n" +
                "Lat Lng: " + lat + " " + lon + "\n");
        for (Pompa p : pompe){
            toString.append("Pompa:").append(p.toString()).append("\n");
        }
        return toString.toString();
    }

    public LatLng getPosizione(){
        return new LatLng(lat,lon);
    }
}
