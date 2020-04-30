package com.kenjimarshall.bogglebuddy;

import java.util.ArrayList;
import java.util.Stack;

public class IntervalNode<T> implements Comparable<IntervalNode> {

    double start;
    double end;
    T data;
    private IntervalNode<T> lChild;
    private IntervalNode<T> rChild;
    private IntervalNode<T> parent;


    public IntervalNode(double start, double end, T data) {
        this.start = start;
        this.end = end;

        this.data = data;

        this.parent = null;
        this.lChild = null;
        this.rChild = null;
    }

    public boolean contains(IntervalNode node) {
        if (node.start >= this.start && node.end <= this.end) {
            return true;
        }
        return false;
    }

    public boolean overlaps(IntervalNode node) {
        if (node.end < this.start || node.start > this.end) {
            return false;
        }
        return true;
    }

    public boolean overlapsPoint(double point) {
        if (point >= this.start && point <= this.end) {
            return true;
        }
        return false;
    }

    public void add(IntervalNode node) {

        if (node.start <= this.start) {
            if (this.lChild == null) {
                this.lChild = node;
                node.parent = this;
            }
            else {
                this.lChild.add(node);
            }
        }

        else {
            if (this.rChild == null) {
                this.rChild = node;
                node.parent = this;
            }
            else {
                this.rChild.add(node);
            }
        }
    }


    public ArrayList<IntervalNode<ArrayList<T>>> merge() {
        Stack<IntervalNode> stack = new Stack<>();
        ArrayList<IntervalNode<ArrayList<T>>> merged = new ArrayList<>();

        IntervalNode<T> curr = this;
        while (curr != null || stack.empty() == false) {
            while (curr != null) {
                stack.push(curr);
                curr = curr.lChild;
            }

            // Gone all the way down L subtree so curr is null
            curr = stack.pop();

            if (merged.size() == 0) { // Create new element
                ArrayList<T> newData = new ArrayList<>();
                newData.add(curr.data);
                IntervalNode<ArrayList<T>> newInterval = new IntervalNode<>(curr.start, curr.end, newData);
                merged.add(newInterval);
            } else {
                IntervalNode<ArrayList<T>> lastEl = merged.get(merged.size() - 1);
                if (lastEl.overlaps(curr)) { // Expand last element
                    lastEl.data.add(curr.data);
                    lastEl.start = Math.min(lastEl.start, curr.start);
                    lastEl.end = Math.min(lastEl.end, curr.end);
                } else { // Create new element
                    ArrayList<T> newData = new ArrayList<>();
                    newData.add(curr.data);
                    IntervalNode<ArrayList<T>> newInterval = new IntervalNode<>(curr.start, curr.end, newData);
                    merged.add(newInterval);
                }
            }


            // Now visit right subtree
            curr = curr.rChild;

        }

        return merged;
    }

    @Override
    public int compareTo(IntervalNode o) {
        return Double.compare(this.start, o.start);
    }
}


