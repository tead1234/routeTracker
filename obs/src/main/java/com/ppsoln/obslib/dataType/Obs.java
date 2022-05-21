package com.ppsoln.obslib.dataType;


import android.location.GnssClock;

import androidx.annotation.NonNull;

public class Obs  implements Cloneable {
    private final ObsData[] obsData;
    private double gs;
    public double gw;
    private int satNum;

    public Obs(ObsData[] _obsData) {
        this.obsData = _obsData;
        this.satNum = this.obsData.length;

        try{
            this.gs = _obsData[0].getGs();
        }catch (Exception e){
            this.gs = 0;
            //e.printStackTrace();
        }
    }

    public int getSatNum() {
        return satNum;
    }

    public double getGs() {
        return gs;
    }

    public ObsData[] getObsData() {
        return obsData;
    }

    public double getGw() {
        return gw;
    }

    public void setGw(GnssClock gnssClock) {
        long weekSecond = 604800;
        this.gw = Math.floor(-(double)gnssClock.getFullBiasNanos()*1e-9/weekSecond);
    }

    public void setGs() {
        this.gs = obsData[0].getGs();
    }

    public void setObsCount(){
        int count = 0 ;
        for(ObsData o : obsData){
            if(o == null){
                break;
            }
            count++;
        }
        satNum = count;
    }

    @NonNull
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}

