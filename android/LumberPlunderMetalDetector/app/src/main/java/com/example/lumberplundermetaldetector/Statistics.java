package com.example.lumberplundermetaldetector;

import java.util.List;

public class Statistics {
    public static double Average(double[] array){
        double sum = 0;
        for (double value : array) sum += value;
        return sum / array.length;
    }

    public static double Average(List<Short> list){
        double sum = 0;
        for (double value : list) sum += value;
        return sum / list.size();
    }
    public static double Max(List<Short> list){
        double max = Double.MIN_VALUE;
        for (double value : list)
            if (value > max) max = value;
        return max;
    }
}
