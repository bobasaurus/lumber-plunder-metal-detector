package com.example.lumberplundermetaldetector;

import java.util.LinkedList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RollingDataForPlot implements ISampleCollector {
    private LinkedList<Double> queueData;
    private int windowSize;
    private Lock lock = new ReentrantLock();

    public RollingDataForPlot(int windowSize) {
        this.windowSize = windowSize;

        queueData = new LinkedList<Double>(); //new Queue<double>(windowSize + 1);
    }

    public void AddSample(double dataPoint) {
        lock.lock();
        try
        {
            queueData.offer(dataPoint);
            if (queueData.size() > windowSize) queueData.poll();
        }
        finally {
            lock.unlock();
        }
    }

    public Double[] CopyQueueToArray() {
        lock.lock();
        try
        {
            return queueData.toArray(new Double[queueData.size()]);
        }
        finally {
            lock.unlock();
        }
    }

}
