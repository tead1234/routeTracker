package com.ppsoln.obslib.dataType;

import androidx.annotation.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ObsData {
    private double gs;
    private int prn;
    private double snr;
    private double doppler;
    private double pseudoRange;
    private double carrierPhase;
    private int satType;

    public ObsData(double _gs, int _prn,
                   double _snr, double _doppler,
                   double _pseudoRange, double _carrierPhase,
                   int _satType){

        this.gs = _gs;
        this.prn = _prn;
        this.snr = new BigDecimal(_snr).setScale(3, RoundingMode.HALF_EVEN).doubleValue();
        this.doppler = new BigDecimal(_doppler).setScale(3, RoundingMode.HALF_EVEN).doubleValue();
        this.pseudoRange = new BigDecimal(_pseudoRange).setScale(3, RoundingMode.HALF_EVEN).doubleValue();
        this.carrierPhase =  new BigDecimal(_carrierPhase).setScale(3, RoundingMode.HALF_EVEN).doubleValue();
        this.satType = _satType;
    }

    public void setGs(double gs) {
        this.gs = gs;
    }

    public double getGs() {
        return gs;
    }

    public double getCarrierPhase() {
        return carrierPhase;
    }

    public double getDoppler() {
        return doppler;
    }

    public void setPrn(int prn) {
        this.prn = prn;
    }

    public int getPrn() {
        return prn;
    }

    public double getSnr() {
        return snr;
    }

    public void setPseudoRange(double pseudoRange) {
        this.pseudoRange = pseudoRange;
    }

    public double getPseudoRange() {
        return pseudoRange;
    }

    public int getSatType() {
        return satType;
    }

    @NonNull
    @Override
    public String toString() {
        return  gs + "\t" + prn + "\t" + (satType + 20) + "\t" + BigDecimal.valueOf(pseudoRange).toString() + "\n" +
                gs + "\t" + prn + "\t" + (satType + 11) + "\t" + carrierPhase + "\n" +
                gs + "\t" + prn + "\t" + (satType + 41) + "\t" + snr + "\n" +
                gs + "\t" + prn + "\t" + (satType + 31) + "\t" + doppler + "\n";
    }

    public String toViewString(){
        StringBuilder builder = new StringBuilder("GnssMeasurement:\n");
        final String format = "   %-4s = %s\n";

        builder.append((String.format(format, "GS", gs)));
        builder.append(String.format(format, "PRN", prn));
        builder.append(String.format(format, "SNR", snr));
        builder.append(String.format(format, "Doppler", doppler));
        builder.append(String.format(format, "Pseudorange", BigDecimal.valueOf(pseudoRange).toString()));
        builder.append(String.format(format, "carrierPhase", carrierPhase));

        return builder.toString();
    }
}
