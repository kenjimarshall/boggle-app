package com.kenjimarshall.bogglebuddy;

import android.util.Log;

import androidx.annotation.Nullable;

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

    public void remove(IntervalNode<T> node) { // lazy removal. sets data to null.
        if (node.data == null ) {
            return;
        }

        if (this.data != null) {
            if (this.equals(node)) { // same data
                this.data = null;
            }
        }

        else if (node.start <= this.start) {
            if (this.lChild == null) {
                return;
            }
            this.lChild.remove(node);
        }
        else {
            if (this.rChild == null) {
                return;
            }
            this.rChild.remove(node);
        }
    }

    public void removeByData(T datum) { // preorder traversal


        if (datum == null) {
            Log.d("Open CV", "NULL data to remove");
            return;
        }

        Stack<IntervalNode> stack = new Stack<>();
        IntervalNode<T> curr = this;

        stack.push(curr);

        while (!stack.empty()) {

            curr = stack.pop();
            if (curr.data != null) { // hasn't been removed
                if (curr.data.equals(datum)) { // found it
                    Log.d("Open CV", "Found node to remove. Nullifying...");
                    curr.data = null;
                    return;
                }
            }

            if (curr.lChild != null) {
                stack.push(curr.lChild);
            }
            if (curr.rChild != null) {
                stack.push(curr.rChild);
            }
        }

        Log.d("Open CV", "Data couldn't be found for removal...");


    }


    public boolean equals(IntervalNode<T> node) {
        return this.data.equals(node.data);
    }

    public ArrayList<IntervalNode<ArrayList<T>>> merge() {
        Stack<IntervalNode> stack = new Stack<>();
        ArrayList<IntervalNode<ArrayList<T>>> merged = new ArrayList<>();

        IntervalNode<T> curr = this;
        while (curr != null || !stack.empty()) {
            while (curr != null) {
                stack.push(curr);
                curr = curr.lChild;
            }

            // Gone all the way down L subtree so curr is null
            curr = stack.pop();

            if (curr.data != null) {
                // has not been removed, so add to merged list

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
                        lastEl.end = Math.max(lastEl.end, curr.end);
                    } else { // Create new element
                        ArrayList<T> newData = new ArrayList<>();
                        newData.add(curr.data);
                        IntervalNode<ArrayList<T>> newInterval = new IntervalNode<>(curr.start, curr.end, newData);
                        merged.add(newInterval);
                    }
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


