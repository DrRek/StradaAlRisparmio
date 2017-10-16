package it.unisa.luca.stradaalrisparmio.stazioni;

import android.util.Log;

/**
 * Created by luca on 08/10/17.
 */

public class Pompa {
    private int id;
    private String carburante;
    private float prezzo;
    private boolean isSelf;
    private String latestUpdate;

    public Pompa(int id, String carburante, float prezzo, boolean isSelf, String latestUpdate){
        this.id = id;
        this.carburante = carburante;
        this.prezzo = prezzo;
        Log.d("Debug prezzo", id+" "+prezzo);
        this.isSelf = isSelf;
        this.latestUpdate = latestUpdate;
    }

    public int getId(){return id;}
    public String getCarburante(){return carburante;}
    public float getPrezzo(){return prezzo;}
    public boolean isSelf(){return isSelf;}
    public String getLatestUpdate(){return latestUpdate;}
}
