package com.example.lumberplundermetaldetector;

public class Statistics {
    public static double Average(double[] array){
        double sum = 0;
        for (double value : array) sum += value;
        return sum / array.length;
    }
}
