package com.example.lumberplundermetaldetector;

import android.graphics.Canvas;

import com.androidplot.Plot;
import com.androidplot.PlotListener;
import com.androidplot.xy.AdvancedLineAndPointRenderer;
import com.androidplot.xy.FastLineAndPointRenderer;
import com.androidplot.xy.RectRegion;
import com.androidplot.xy.XYSeries;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

//NOTE: could use FastXYSeries instead: http://halfhp.github.io/androidplot/docs/advanced_xy_plot.html but this would require figuring out an efficient rolling min/max value algorithm which is hard
public class RollingXYSeries implements XYSeries, PlotListener {
    public ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    private double minY = Double.NaN;
    private double maxY = Double.NaN;
    private ArrayList<Number> data;
    private int maxCapacity;

    //not really sure what this does
    //private AdvancedLineAndPointRenderer renderer;
    private WeakReference<FastLineAndPointRenderer> rendererRef;

    RollingXYSeries(int maxCapacity, final WeakReference<FastLineAndPointRenderer> renderer) {
        this.rendererRef = renderer;
        this.maxCapacity = maxCapacity;
        data = new ArrayList<>();
    }

    public void addDataThreadSafe(Number yValue)
    {
        try {
            if (lock.writeLock().tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    data.add(yValue);
                    while (data.size() > maxCapacity) {
                        data.remove(0);
                    }

                    //if (rendererRef.get() != null)
                    //    rendererRef.get().setLatestIndex(data.size() - 1);
                } finally {
                    lock.writeLock().unlock();
                }
            }
        }
        catch (Exception ex){
            //todo: handle this?
        }
    }

    @Override
    public void onBeforeDraw(Plot source, Canvas canvas) {
        lock.readLock().lock();
    }

    @Override
    public void onAfterDraw(Plot source, Canvas canvas) {
        lock.readLock().unlock();
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public Number getX(int index) {
        return index;
    }

    @Override
    public Number getY(int index) {
        return data.get(index);
    }

    @Override
    public String getTitle() {
        return "";
    }

}
