package com.kenjimarshall.bogglebuddy;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class Boggle {

    private static String WORDLIST = "nwl2018.txt";
    private HashSet<String> wordDict;
    private HashSet<String> threeDict;
    private int size;
    private ArrayList<Node> adjList;
    private Context mContext;

    public Boggle(ArrayList<String> symbols, Context mContext) {
        this.size = 4;
        this.mContext = mContext;
        this.adjList = buildAdjList(symbols);
        ArrayList<HashSet<String>> dicts = loadWordDict();
        this.wordDict = dicts.get(0);
        this.threeDict = dicts.get(1);
    }

    private ArrayList<HashSet<String>> loadWordDict() {
        HashSet<String> wordDict = new HashSet<>();
        HashSet<String> threeDict = new HashSet<>();
        try {
            InputStream is = mContext.getAssets().open(Boggle.WORDLIST);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) !=  null) {
                wordDict.add(line);
                if (line.length() >= 3) {
                    threeDict.add(line.substring(0, 3));
                }
            }

            reader.close();

        }
        catch (FileNotFoundException ignored) {}
        catch (IOException ignored) {}

        ArrayList<HashSet<String>> dicts = new ArrayList<>();
        dicts.add(wordDict);
        dicts.add(threeDict);
        return dicts;
    }

    private ArrayList<Node> buildAdjList(ArrayList<String> symbols) {

        ArrayList<Node> adjList = new ArrayList<Node>();

        for (int i = 0; i < symbols.size(); i++) {
            int xPosition = i / this.size;
            int yPosition = i % this.size;
            ArrayList<Node> edges = new ArrayList<Node>();

            Node newNode = new Node(xPosition, yPosition, edges, symbols.get(i).toLowerCase());
            adjList.add(newNode);
            for (Node node : adjList) {
                if (Math.abs(xPosition - node.xPosition) <= 1 && Math.abs(yPosition - node.yPosition) <= 1) {
                    newNode.edges.add(node);
                    node.edges.add(newNode);
                }
            }
        }

        return adjList;
    }

    public boolean validateWord(String word) {
        return this.wordDict.contains(word);
    }

    public HashMap<Integer, String[]> findWords() {
        HashMap<Integer, HashSet<String>> validWords = new HashMap<>();
        for (int i = 3; i <= 10; i++) {
            validWords.put(Integer.valueOf(i), new HashSet<String>());
        }

        for (Node node: this.adjList) {
            ArrayList<Node> starterList = new ArrayList<>();
            starterList.add(node);
            this.extendAndCheckWords(node.symbol, starterList, validWords);
        }

        HashMap<Integer, String[]> sortedValidWords = new HashMap<>();
        for (Integer key: validWords.keySet()){
            String[] words = new String[validWords.get(key).size()];
            validWords.get(key).toArray(words);
            Arrays.sort(words);
            sortedValidWords.put(key, words);
        }

        return sortedValidWords;
    }

    private void extendAndCheckWords(String wordToExtend, ArrayList<Node> nodeList, HashMap<Integer, HashSet<String>> validWords) {
        boolean good_starter = true;
        if (wordToExtend.length() == 3) {
            good_starter = this.threeDict.contains(wordToExtend);
        }

        if (good_starter) {
            if (wordToExtend.length() >= 3) {
                if (this.validateWord(wordToExtend)){
                    validWords.get(Integer.valueOf(wordToExtend.length())).add(wordToExtend);
                }
            }

            if (wordToExtend.length() < 10) {
                for (Node neighbor: nodeList.get(nodeList.size()-1).edges) {
                    if (!nodeList.contains(neighbor)) {
                        ArrayList<Node> newNodeList = new ArrayList<>(nodeList);
                        newNodeList.add(neighbor);
                        this.extendAndCheckWords(wordToExtend.concat(neighbor.symbol), newNodeList, validWords);
                    }
                }
            }
        }
    }


    private class Node {

        private int xPosition;
        private int yPosition;
        private ArrayList<Node> edges;
        private String symbol;


        private Node(int xPosition, int yPosition, ArrayList<Node> edges, String symbol) {
            this.xPosition = xPosition;
            this.yPosition = yPosition;
            this.edges = edges;
            this.symbol = symbol;
        }
    }
}
