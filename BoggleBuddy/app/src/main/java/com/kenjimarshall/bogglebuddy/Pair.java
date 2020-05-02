package com.kenjimarshall.bogglebuddy;

import androidx.annotation.Nullable;

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

    public boolean equals(Pair obj) {

        return this.contour.equals(obj.contour);
    }


}
