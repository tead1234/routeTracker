package com.ppsoln.obslib;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssStatus;

public class ObsCalculater {
    public double fullBiasNanos;

    public ObsCalculater() {
        fullBiasNanos = 0;
    }

    public int staType(GnssMeasurement measurement){
        int type;
        switch (measurement.getConstellationType()){
            case GnssStatus.CONSTELLATION_GPS:
                type = 100;
                break;
            case GnssStatus.CONSTELLATION_SBAS:
                type = 600;
                break;
            case GnssStatus.CONSTELLATION_GLONASS:
                type = 300;
                break;
            case GnssStatus.CONSTELLATION_QZSS:
                type = 500;
                break;
            case GnssStatus.CONSTELLATION_BEIDOU:
                type = 200;
                break;
            case GnssStatus.CONSTELLATION_GALILEO:
                type = 400;
                break;
            default:
                type = 700;
                break;
        }
        return type;
    }

    public double calGS(GnssMeasurement measurement, GnssClock gnssClock){
        double weekSecond = 604800;

        double weekNumber = Math.floor(-(double)fullBiasNanos*1e-9/weekSecond);
        double WeekNumberNanos = (double)weekNumber * (double)(weekSecond * 1e9);
        double tRxNanos = (gnssClock.getTimeNanos() + measurement.getTimeOffsetNanos())
                - (fullBiasNanos + gnssClock.getBiasNanos())
                - WeekNumberNanos;
        double tRxSeconds = tRxNanos * 1e-9;

        return tRxSeconds;
    }

    public double calPseudoRange(GnssMeasurement measurement, GnssClock gnssClock){
        double weekSecond = 604800;
        double CCC = 299792458;

        double weekNumber = Math.floor(-(double)fullBiasNanos*1e-9/weekSecond);
        double  WeekNumberNanos = (double)weekNumber * (double)(weekSecond * 1e9);
        double tRxNanos = (gnssClock.getTimeNanos() + measurement.getTimeOffsetNanos())
                - (fullBiasNanos + gnssClock.getBiasNanos())
                - WeekNumberNanos;
        double tRxSeconds = (double)tRxNanos * 1e-9;
        double PseudoRangeSecond = tRxSeconds - (double)measurement.getReceivedSvTimeNanos() * 1e-9;

        // Week Rollover 문제 해결
        if (PseudoRangeSecond > weekSecond/2) {
            double prS = PseudoRangeSecond;
            double delS = Math.round(prS/weekSecond)*weekSecond;
            prS = prS - delS;
            if (prS > 10){
                return 0;
            }else {
                PseudoRangeSecond = prS;
            }
        }

        double PseudoRange;
        if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU){
            PseudoRange = (PseudoRangeSecond-14) * CCC;
        }else{
            PseudoRange = PseudoRangeSecond * CCC;
        }

        return PseudoRange;
    }

    private double calWL(GnssMeasurement measurement) {
        double CCC = 299792458;
        double f;
        double WL;

        if(measurement.getConstellationType() == GnssStatus.CONSTELLATION_BEIDOU){
            f = 1561097980;
        }else {
            f = 1575420030;
        }

        WL = (double) CCC/f;

        return WL;
    }

    public double calDoppler(GnssMeasurement measurement){
        return (double)(-measurement.getPseudorangeRateMetersPerSecond() / calWL(measurement));
    }

    public double calCarrierPhase(GnssMeasurement measurement){
        return (double)(measurement.getAccumulatedDeltaRangeMeters() / calWL(measurement));
    }
}

