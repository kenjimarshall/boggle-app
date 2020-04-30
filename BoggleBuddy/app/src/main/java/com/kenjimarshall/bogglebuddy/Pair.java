package com.kenjimarshall.bogglebuddy;

import org.opencv.core.Mat;

public class Pair implements Comparable<Pair> {
    double pos;
    Mat contour;


    public Pair(double pos, Mat contour) {
        this.pos = pos;
        this.contour = contour;
    }

    @Override
    public int compareTo(Pair o) {
        return Double.compare(this.pos, o.pos);
    }
}
