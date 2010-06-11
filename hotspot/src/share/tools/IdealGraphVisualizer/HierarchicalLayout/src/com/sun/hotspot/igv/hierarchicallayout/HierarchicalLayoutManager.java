/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */
package com.sun.hotspot.igv.hierarchicallayout;

import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;

/**
 *
 * @author Thomas Wuerthinger
 */
public class HierarchicalLayoutManager implements LayoutManager {

    public static final boolean TRACE = false;
    public static final boolean CHECK = false;
    public static final int SWEEP_ITERATIONS = 1;
    public static final int CROSSING_ITERATIONS = 2;
    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;
    public static final int X_OFFSET = 9;
    public static final int LAYER_OFFSET = 30;
    public static final int MAX_LAYER_LENGTH = -1;
    public static final int MIN_LAYER_DIFFERENCE = 1;

    public enum Combine {

        NONE,
        SAME_INPUTS,
        SAME_OUTPUTS
    }
    // Options
    private Combine combine;
    private int dummyWidth;
    private int dummyHeight;
    private int xOffset;
    private int layerOffset;
    private int maxLayerLength;
    private int minLayerDifference;
    // Algorithm global datastructures
    private Set<Link> reversedLinks;
    private List<LayoutNode> nodes;
    private HashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private HashMap<Link, List<Point>> reversedLinkStartPoints;
    private HashMap<Link, List<Point>> reversedLinkEndPoints;
    private HashMap<LayoutEdge, LayoutEdge> bottomEdgeHash;
    private HashMap<Link, List<Point>> splitStartPoints;
    private HashMap<Link, List<Point>> splitEndPoints;
    private LayoutGraph graph;
    private List<LayoutNode>[] layers;
    private int layerCount;
    private Set<? extends Vertex> firstLayerHint;
    private Set<? extends Vertex> lastLayerHint;
    private Set<? extends Link> importantLinks;
    private Set<Link> linksToFollow;

    private class LayoutNode {

        public int x;
        public int y;
        public int width;
        public int height;
        public int layer = -1;
        public int xOffset;
        public int yOffset;
        public int bottomYOffset;
        public Vertex vertex; // Only used for non-dummy nodes, otherwise null
        public List<LayoutEdge> preds = new ArrayList<LayoutEdge>();
        public List<LayoutEdge> succs = new ArrayList<LayoutEdge>();
        public HashMap<Integer, Integer> outOffsets = new HashMap<Integer, Integer>();
        public HashMap<Integer, Integer> inOffsets = new HashMap<Integer, Integer>();
        public int pos = -1; // Position within layer
        public int crossingNumber;

        @Override
        public String toString() {
            return "Node " + vertex;
        }
    }

    private class LayoutEdge {

        public LayoutNode from;
        public LayoutNode to;
        public int relativeFrom;
        public int relativeTo;
        public Link link;
    }

    private abstract class AlgorithmPart {

        public void start() {
            if (CHECK) {
                preCheck();
            }

            long start = 0;
            if (TRACE) {
                System.out.println("##################################################");
                System.out.println("Starting part " + this.getClass().getName());
                start = System.currentTimeMillis();
            }
            run();
            if (TRACE) {
                System.out.println("Timing for " + this.getClass().getName() + " is " + (System.currentTimeMillis() - start));
                printStatistics();
            }

            if (CHECK) {
                postCheck();
            }
        }

        protected abstract void run();

        protected void printStatistics() {
        }

        protected void postCheck() {
        }

        protected void preCheck() {
        }
    }

    public HierarchicalLayoutManager() {
        this(Combine.NONE);
    }

    public HierarchicalLayoutManager(Combine b) {
        this.combine = b;
        this.dummyWidth = DUMMY_WIDTH;
        this.dummyHeight = DUMMY_HEIGHT;
        this.xOffset = X_OFFSET;
        this.layerOffset = LAYER_OFFSET;
        this.maxLayerLength = MAX_LAYER_LENGTH;
        this.minLayerDifference = MIN_LAYER_DIFFERENCE;
        this.linksToFollow = new HashSet<Link>();
    }

    public int getMaxLayerLength() {
        return maxLayerLength;
    }

    public void setMaxLayerLength(int v) {
        maxLayerLength = v;
    }

    public void setMinLayerDifference(int v) {
        minLayerDifference = v;
    }

    public void doLayout(LayoutGraph graph) {
        doLayout(graph, new HashSet<Vertex>(), new HashSet<Vertex>(), new HashSet<Link>());

    }

    public void doLayout(LayoutGraph graph, Set<? extends Vertex> firstLayerHint, Set<? extends Vertex> lastLayerHint, Set<? extends Link> importantLinks) {

        this.importantLinks = importantLinks;
        this.graph = graph;
        this.firstLayerHint = firstLayerHint;
        this.lastLayerHint = lastLayerHint;

        vertexToLayoutNode = new HashMap<Vertex, LayoutNode>();
        reversedLinks = new HashSet<Link>();
        reversedLinkStartPoints = new HashMap<Link, List<Point>>();
        reversedLinkEndPoints = new HashMap<Link, List<Point>>();
        bottomEdgeHash = new HashMap<LayoutEdge, LayoutEdge>();
        nodes = new ArrayList<LayoutNode>();
        splitStartPoints = new HashMap<Link, List<Point>>();
        splitEndPoints = new HashMap<Link, List<Point>>();

        // #############################################################
        // Step 1: Build up data structure
        new BuildDatastructure().start();

        // #############################################################
        // STEP 2: Reverse edges, handle backedges
        new ReverseEdges().start();

        for (LayoutNode n : nodes) {
            ArrayList<LayoutEdge> tmpArr = new ArrayList<LayoutEdge>();
            for (LayoutEdge e : n.succs) {
                if (importantLinks.contains(e.link)) {
                    tmpArr.add(e);
                }
            }

            for (LayoutEdge e : tmpArr) {
                //System.out.println("Removed " + e);
                e.from.succs.remove(e);
                e.to.preds.remove(e);
            }
        }

        // #############################################################
        // STEP 3: Assign layers
        new AssignLayers().start();

        // #############################################################
        // STEP 4: Create dummy nodes
        new CreateDummyNodes().start();

        // #############################################################
        // STEP 5: Crossing Reduction
        new CrossingReduction().start();

        // #############################################################
        // STEP 7: Assign X coordinates
        //new AssignXCoordinates().start();
        new AssignXCoordinates2().start();

        // #############################################################
        // STEP 6: Assign Y coordinates
        new AssignYCoordinates().start();

        // #############################################################
        // STEP 8: Write back to interface
        new WriteResult().start();
    }

    private class WriteResult extends AlgorithmPart {

        private int pointCount;

        protected void run() {

            HashMap<Vertex, Point> vertexPositions = new HashMap<Vertex, Point>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<Link, List<Point>>();
            for (Vertex v : graph.getVertices()) {
                LayoutNode n = vertexToLayoutNode.get(v);
                assert !vertexPositions.containsKey(v);
                vertexPositions.put(v, new Point(n.x + n.xOffset, n.y + n.yOffset));
            }

            for (LayoutNode n : nodes) {

                for (LayoutEdge e : n.preds) {
                    if (e.link != null) {
                        ArrayList<Point> points = new ArrayList<Point>();

                        Point p = new Point(e.to.x + e.relativeTo, e.to.y + e.to.yOffset);
                        points.add(p);
                        if (e.to.inOffsets.containsKey(e.relativeTo)) {
                            points.add(new Point(p.x, p.y + e.to.inOffsets.get(e.relativeTo)));
                        }

                        LayoutNode cur = e.from;
                        LayoutNode other = e.to;
                        LayoutEdge curEdge = e;
                        while (cur.vertex == null && cur.preds.size() != 0) {
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y + cur.height));
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y));
                            assert cur.preds.size() == 1;
                            curEdge = cur.preds.get(0);
                            cur = curEdge.from;
                        }

                        p = new Point(cur.x + curEdge.relativeFrom, cur.y + cur.height - cur.bottomYOffset);
                        if (curEdge.from.outOffsets.containsKey(curEdge.relativeFrom)) {
                            points.add(new Point(p.x, p.y + curEdge.from.outOffsets.get(curEdge.relativeFrom)));
                        }
                        points.add(p);

                        Collections.reverse(points);



                        if (cur.vertex == null && cur.preds.size() == 0) {

                            if (reversedLinkEndPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                    points.add(new Point(p1.x + e.to.x, p1.y + e.to.y));
                                }
                            }

                            if (splitStartPoints.containsKey(e.link)) {
                                points.add(0, null);
                                points.addAll(0, splitStartPoints.get(e.link));

                                //checkPoints(points);
                                if (reversedLinks.contains(e.link)) {
                                    Collections.reverse(points);
                                }
                                assert !linkPositions.containsKey(e.link);
                                linkPositions.put(e.link, points);
                            } else {
                                splitEndPoints.put(e.link, points);
                            }

                        } else {
                            if (reversedLinks.contains(e.link)) {
                                Collections.reverse(points);
                            }
                            if (reversedLinkStartPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                    points.add(new Point(p1.x + cur.x, p1.y + cur.y));
                                }
                            }

                            if (reversedLinkEndPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                    points.add(0, new Point(p1.x + other.x, p1.y + other.y));
                                }
                            }

                            assert !linkPositions.containsKey(e.link);
                            linkPositions.put(e.link, points);
                        }
                        pointCount += points.size();

                        // No longer needed!
                        e.link = null;
                    }
                }

                for (LayoutEdge e : n.succs) {
                    if (e.link != null) {
                        ArrayList<Point> points = new ArrayList<Point>();
                        Point p = new Point(e.from.x + e.relativeFrom, e.from.y + e.from.height - e.from.bottomYOffset);
                        points.add(p);
                        if (e.from.outOffsets.containsKey(e.relativeFrom)) {
                            points.add(new Point(p.x, p.y + e.from.outOffsets.get(e.relativeFrom)));
                        }

                        LayoutNode cur = e.to;
                        LayoutNode other = e.from;
                        LayoutEdge curEdge = e;
                        while (cur.vertex == null && cur.succs.size() != 0) {
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y));
                            if (points.size() > 1 && points.get(points.size() - 1).x == cur.x + cur.width / 2 && points.get(points.size() - 2).x == cur.x + cur.width / 2) {
                                points.remove(points.size() - 1);
                            }
                            points.add(new Point(cur.x + cur.width / 2, cur.y + cur.height));
                            if (cur.succs.size() == 0) {
                                break;
                            }
                            assert cur.succs.size() == 1;
                            curEdge = cur.succs.get(0);
                            cur = curEdge.to;
                        }


                        p = new Point(cur.x + curEdge.relativeTo, cur.y + cur.yOffset);
                        points.add(p);
                        if (curEdge.to.inOffsets.containsKey(curEdge.relativeTo)) {
                            points.add(new Point(p.x, p.y + curEdge.to.inOffsets.get(curEdge.relativeTo)));
                        }


                        if (cur.succs.size() == 0 && cur.vertex == null) {
                            if (reversedLinkStartPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                    points.add(0, new Point(p1.x + other.x, p1.y + other.y));
                                }
                            }

                            if (splitEndPoints.containsKey(e.link)) {
                                points.add(null);
                                points.addAll(splitEndPoints.get(e.link));

                                //checkPoints(points);
                                if (reversedLinks.contains(e.link)) {
                                    Collections.reverse(points);
                                }
                                assert !linkPositions.containsKey(e.link);
                                linkPositions.put(e.link, points);
                            } else {
                                splitStartPoints.put(e.link, points);
                            }
                        } else {

                            if (reversedLinkStartPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                    points.add(0, new Point(p1.x + other.x, p1.y + other.y));
                                }
                            }
                            if (reversedLinkEndPoints.containsKey(e.link)) {
                                for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                    points.add(new Point(p1.x + cur.x, p1.y + cur.y));
                                }
                            }
                            if (reversedLinks.contains(e.link)) {
                                Collections.reverse(points);
                            }
                            //checkPoints(points);
                            assert !linkPositions.containsKey(e.link);
                            linkPositions.put(e.link, points);
                        }

                        pointCount += points.size();
                        e.link = null;
                    }
                }
            }

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            for (Vertex v : vertexPositions.keySet()) {
                Point p = vertexPositions.get(v);
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
            }

            for (Link l : linkPositions.keySet()) {
                List<Point> points = linkPositions.get(l);
                for (Point p : points) {
                    if (p != null) {
                        minX = Math.min(minX, p.x);
                        minY = Math.min(minY, p.y);
                    }
                }

            }

            for (Vertex v : vertexPositions.keySet()) {
                Point p = vertexPositions.get(v);
                p.x -= minX;
                p.y -= minY;
                v.setPosition(p);
            }

            for (Link l : linkPositions.keySet()) {
                List<Point> points = linkPositions.get(l);
                for (Point p : points) {
                    if (p != null) {
                        p.x -= minX;
                        p.y -= minY;
                    }
                }
                l.setControlPoints(points);

            }
        }

        @Override
        protected void printStatistics() {
            System.out.println("Number of nodes: " + nodes.size());
            int edgeCount = 0;
            for (LayoutNode n : nodes) {
                edgeCount += n.succs.size();
            }
            System.out.println("Number of edges: " + edgeCount);
            System.out.println("Number of points: " + pointCount);
        }
    }

    private static class Segment {

        public float d;
        public int orderNumber = -1;
        public ArrayList<LayoutNode> nodes = new ArrayList<LayoutNode>();
        public HashSet<Segment> succs = new HashSet<Segment>();
        public HashSet<Segment> preds = new HashSet<Segment>();
        public Region region;
    }
    private static final Comparator<Segment> segmentComparator = new Comparator<Segment>() {

        public int compare(Segment s1, Segment s2) {
            return s1.orderNumber - s2.orderNumber;
        }
    };

    private static class Region {

        public float d;
        public int minOrderNumber;
        public SortedSet<Segment> segments = new TreeSet<Segment>(segmentComparator);
        public HashSet<Region> succs = new HashSet<Region>(4);
        public HashSet<Region> preds = new HashSet<Region>(4);
    }
    private static final Comparator<Region> regionComparator = new Comparator<Region>() {

        public int compare(Region r1, Region r2) {
            return r1.minOrderNumber - r2.minOrderNumber;
        }
    };
    private static final Comparator<LayoutNode> nodePositionComparator = new Comparator<LayoutNode>() {

        public int compare(LayoutNode n1, LayoutNode n2) {
            return n1.pos - n2.pos;
        }
    };
    private static final Comparator<LayoutNode> nodeProcessingDownComparator = new Comparator<LayoutNode>() {

        public int compare(LayoutNode n1, LayoutNode n2) {
            if (n1.vertex == null) {
                return -1;
            }
            if (n2.vertex == null) {
                return 1;
            }
            return n1.preds.size() - n2.preds.size();
        }
    };
    private static final Comparator<LayoutNode> nodeProcessingUpComparator = new Comparator<LayoutNode>() {

        public int compare(LayoutNode n1, LayoutNode n2) {
            if (n1.vertex == null) {
                return -1;
            }
            if (n2.vertex == null) {
                return 1;
            }
            return n1.succs.size() - n2.succs.size();
        }
    };

    private class AssignXCoordinates2 extends AlgorithmPart {

        private ArrayList<Integer>[] space;
        private ArrayList<LayoutNode>[] downProcessingOrder;
        private ArrayList<LayoutNode>[] upProcessingOrder;

        private void initialPositions() {
            for (LayoutNode n : nodes) {
                n.x = space[n.layer].get(n.pos);
            }
        }

        protected void run() {

            space = new ArrayList[layers.length];
            downProcessingOrder = new ArrayList[layers.length];
            upProcessingOrder = new ArrayList[layers.length];

            for (int i = 0; i < layers.length; i++) {
                space[i] = new ArrayList<Integer>();
                downProcessingOrder[i] = new ArrayList<LayoutNode>();
                upProcessingOrder[i] = new ArrayList<LayoutNode>();

                int curX = 0;
                for (LayoutNode n : layers[i]) {
                    space[i].add(curX);
                    curX += n.width + xOffset;
                    downProcessingOrder[i].add(n);
                    upProcessingOrder[i].add(n);
                }

                Collections.sort(downProcessingOrder[i], nodeProcessingDownComparator);
                Collections.sort(upProcessingOrder[i], nodeProcessingUpComparator);
            }

            initialPositions();
            for (int i = 0; i < SWEEP_ITERATIONS; i++) {
                sweepDown();
                sweepUp();
            }

            for (int i = 0; i < SWEEP_ITERATIONS; i++) {
                doubleSweep();
            }
        }

        private int calculateOptimalDown(LayoutNode n) {

            List<Integer> values = new ArrayList<Integer>();
            if (n.preds.size() == 0) {
                return n.x;
            }
            for (LayoutEdge e : n.preds) {
                int cur = e.from.x + e.relativeFrom - e.relativeTo;
                values.add(cur);
            }
            return median(values);
        }

        private int calculateOptimalBoth(LayoutNode n) {

            List<Integer> values = new ArrayList<Integer>();
            if (n.preds.size() == 0 + n.succs.size()) {
                return n.x;
            }
            for (LayoutEdge e : n.preds) {
                int cur = e.from.x + e.relativeFrom - e.relativeTo;
                values.add(cur);
            }

            for (LayoutEdge e : n.succs) {
                int cur = e.to.x + e.relativeTo - e.relativeFrom;
                values.add(cur);
            }

            return median(values);
        }

        private int calculateOptimalUp(LayoutNode n) {

            //List<Integer> values = new ArrayList<Integer>();
            int size = n.succs.size();
            if (size == 0) {
                return n.x;
            } else {
                int result = 0;
                for (LayoutEdge e : n.succs) {
                    int cur = e.to.x + e.relativeTo - e.relativeFrom;
                    result += cur;
                }
                return result / size; //median(values);
            }
        }

        private int median(List<Integer> values) {
            Collections.sort(values);
            if (values.size() % 2 == 0) {
                return (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2;
            } else {
                return values.get(values.size() / 2);
            }
        }

        private void sweepUp() {
            for (int i = layers.length - 1; i >= 0; i--) {
                NodeRow r = new NodeRow(space[i]);
                for (LayoutNode n : upProcessingOrder[i]) {
                    int optimal = calculateOptimalUp(n);
                    r.insert(n, optimal);
                }
            }
        /*
        for(int i=0; i<layers.length; i++) {
        NodeRow r = new NodeRow(space[i]);
        for(LayoutNode n : upProcessingOrder[i]) {
        int optimal = calculateOptimalUp(n);
        r.insert(n, optimal);
        }
        }*/
        }

        private void doubleSweep() {
            for (int i = layers.length - 2; i >= 0; i--) {
                NodeRow r = new NodeRow(space[i]);
                for (LayoutNode n : upProcessingOrder[i]) {
                    int optimal = calculateOptimalBoth(n);
                    r.insert(n, optimal);
                }
            }
        }

        private void sweepDown() {
            for (int i = 1; i < layers.length; i++) {
                NodeRow r = new NodeRow(space[i]);
                for (LayoutNode n : downProcessingOrder[i]) {
                    int optimal = calculateOptimalDown(n);
                    r.insert(n, optimal);
                }
            }
        }
    }

    private static class NodeRow {

        private TreeSet<LayoutNode> treeSet;
        private ArrayList<Integer> space;

        public NodeRow(ArrayList<Integer> space) {
            treeSet = new TreeSet<LayoutNode>(nodePositionComparator);
            this.space = space;
        }

        public int offset(LayoutNode n1, LayoutNode n2) {
            int v1 = space.get(n1.pos) + n1.width;
            int v2 = space.get(n2.pos);
            return v2 - v1;
        }

        public void insert(LayoutNode n, int pos) {

            SortedSet<LayoutNode> headSet = treeSet.headSet(n);

            LayoutNode leftNeighbor = null;
            int minX = Integer.MIN_VALUE;
            if (!headSet.isEmpty()) {
                leftNeighbor = headSet.last();
                minX = leftNeighbor.x + leftNeighbor.width + offset(leftNeighbor, n);
            }

            if (pos < minX) {
                n.x = minX;
            } else {

                LayoutNode rightNeighbor = null;
                SortedSet<LayoutNode> tailSet = treeSet.tailSet(n);
                int maxX = Integer.MAX_VALUE;
                if (!tailSet.isEmpty()) {
                    rightNeighbor = tailSet.first();
                    maxX = rightNeighbor.x - offset(n, rightNeighbor) - n.width;
                }

                if (pos > maxX) {
                    n.x = maxX;
                } else {
                    n.x = pos;
                }

                assert minX <= maxX;
            }

            treeSet.add(n);
        }
    }

    private class AssignXCoordinates extends AlgorithmPart {

        HashMap<LayoutNode, Segment> hashMap = new HashMap<LayoutNode, Segment>();
        ArrayList<Segment> segments = new ArrayList<Segment>();

        private void generateSegments() {

            for (int i = 0; i < layerCount; i++) {
                for (LayoutNode n : layers[i]) {
                    if (!hashMap.containsKey(n)) {
                        Segment s = new Segment();
                        segments.add(s);
                        LayoutNode curNode = n;

                        int maxLength = 0;
                        while (curNode.succs.size() == 1 && curNode.preds.size() == 1) {
                            s.nodes.add(curNode);
                            assert !hashMap.containsKey(curNode);
                            hashMap.put(curNode, s);
                            curNode = curNode.succs.get(0).to;
                            maxLength++;
                        //if(maxLength > 10) break;
                        }

                        if (s.nodes.size() > 0 && curNode.preds.size() == 1 && curNode.vertex == null) {
                            s.nodes.add(curNode);
                            assert !hashMap.containsKey(curNode);
                            hashMap.put(curNode, s);
                        }

                        if (s.nodes.size() == 0) {
                            // Simple segment with a single node
                            s.nodes.add(n);
                            hashMap.put(n, s);
                        }
                    }
                }
            }
        }

        private void addEdges() {

            for (int i = 0; i < layerCount; i++) {
                LayoutNode prev = null;
                for (LayoutNode n : layers[i]) {

                    if (prev != null) {
                        Segment s1 = hashMap.get(prev);
                        Segment s2 = hashMap.get(n);

                        if (s1 != s2) {
                            s1.succs.add(s2);
                            s2.preds.add(s1);
                        }
                    }
                    prev = n;

                }
            }
        }

        private void topologicalSorting() {

            Queue<Segment> queue = new LinkedList<Segment>();

            int index = 0;
            ArrayList<Segment> newList = new ArrayList<Segment>();
            for (Segment s : segments) {
                if (s.preds.size() == 0) {
                    s.orderNumber = index;
                    newList.add(s);
                    index++;
                    queue.add(s);
                }
            }

            while (!queue.isEmpty()) {
                Segment s = queue.remove();

                for (Segment succ : s.succs) {
                    succ.preds.remove(s);
                    if (succ.preds.size() == 0) {
                        queue.add(succ);
                        succ.orderNumber = index;
                        newList.add(succ);
                        index++;
                    }
                }
            }

            segments = newList;
        }

        private void initialPositions() {

            int[] minPos = new int[layers.length];

            for (Segment s : segments) {
                int max = 0;
                for (LayoutNode n : s.nodes) {
                    int x = minPos[n.layer];
                    if (x > max) {
                        max = x;
                    }
                }

                for (LayoutNode n : s.nodes) {
                    minPos[n.layer] = max + n.width + xOffset;
                    n.x = max;
                }
            }
        }

        private int predSum(LayoutNode n) {
            int sum = 0;
            for (LayoutEdge e : n.preds) {
                assert e.to == n;
                //sum += (e.from.x + e.relativeFrom + (int)hashMap.get(e.from).d) - (e.to.x + e.relativeTo + (int)hashMap.get(e.to).d);
                sum += (e.from.x + e.relativeFrom) - (e.to.x + e.relativeTo);
            }

            return sum;
        }

        private int succSum(LayoutNode n) {
            int sum = 0;
            for (LayoutEdge e : n.succs) {

                assert e.from == n;
                sum += (e.to.x + e.relativeTo) - (e.from.x + e.relativeFrom);
            //sum += (e.to.x + e.relativeTo + (int)hashMap.get(e.to).d) - (e.from.x + e.relativeFrom + (int)hashMap.get(e.from).d);
            }

            return sum;

        }

        private void downValues() {

            for (Segment s : segments) {
                downValues(s);

            }

        }

        private void downValues(Segment s) {
            LayoutNode n = s.nodes.get(0); // Only first node needed, all other have same coordinate

            if (n.preds.size() == 0) {
                // Value is 0.0;
                if (n.succs.size() == 0) {
                    s.d = 0.0f;
                } else {
                    s.d = (((float) succSum(n) / (float) n.succs.size())) / 2;
                }
            } else {
                s.d = (float) predSum(n) / (float) n.preds.size();
            }
        }

        private void upValues() {
            for (Segment s : segments) {
                upValues(s);
            }
        }

        private void upValues(Segment s) {
            LayoutNode n = s.nodes.get(0); // Only first node needed, all other have same coordinate

            if (n.succs.size() == 0) {
                // Value is 0.0;
                if (n.preds.size() == 0) {
                    s.d = 0.0f;
                } else {
                    s.d = (float) predSum(n) / (float) n.preds.size();
                }
            } else {
                s.d = ((float) succSum(n) / (float) n.succs.size()) / 2;
            }
        }

        private void sweep(boolean down) {

            if (down) {
                downValues();
            } else {
                upValues();
            }

            SortedSet<Region> regions = new TreeSet<Region>(regionComparator);
            for (Segment s : segments) {
                s.region = new Region();
                s.region.minOrderNumber = s.orderNumber;
                s.region.segments.add(s);
                s.region.d = s.d;
                regions.add(s.region);
            }

            for (Segment s : segments) {
                for (LayoutNode n : s.nodes) {
                    if (n.pos != 0) {
                        LayoutNode prevNode = layers[n.layer].get(n.pos - 1);
                        if (prevNode.x + prevNode.width + xOffset == n.x) {
                            Segment other = hashMap.get(prevNode);
                            Region r1 = s.region;
                            Region r2 = other.region;
                            // They are close together
                            if (r1 != r2 && r2.d >= r1.d) {

                                if (r2.segments.size() < r1.segments.size()) {

                                    r1.d = (r1.d * r1.segments.size() + r2.d * r2.segments.size()) / (r1.segments.size() + r2.segments.size());

                                    for (Segment tempS : r2.segments) {
                                        r1.segments.add(tempS);
                                        tempS.region = r1;
                                        r1.minOrderNumber = Math.min(r1.minOrderNumber, tempS.orderNumber);
                                    }

                                    regions.remove(r2);
                                } else {

                                    r2.d = (r1.d * r1.segments.size() + r2.d * r2.segments.size()) / (r1.segments.size() + r2.segments.size());

                                    for (Segment tempS : r1.segments) {
                                        r2.segments.add(tempS);
                                        tempS.region = r2;
                                        r2.minOrderNumber = Math.min(r2.minOrderNumber, tempS.orderNumber);
                                    }

                                    regions.remove(r1);
                                }
                            }
                        }
                    }
                }
            }



            ArrayList<Region> reversedRegions = new ArrayList<Region>();
            for (Region r : regions) {
                if (r.d < 0) {
                    processRegion(r, down);
                } else {
                    reversedRegions.add(0, r);
                }
            }

            for (Region r : reversedRegions) {
                processRegion(r, down);
            }

        }

        private void processRegion(Region r, boolean down) {

            // Do not move
            if ((int) r.d == 0) {
                return;
            }

            ArrayList<Segment> arr = new ArrayList<Segment>();
            for (Segment s : r.segments) {
                arr.add(s);
            }

            if (r.d > 0) {
                Collections.reverse(arr);
            }

            for (Segment s : arr) {


                int min = (int) r.d;
                if (min < 0) {
                    min = -min;
                }

                for (LayoutNode n : s.nodes) {

                    int layer = n.layer;
                    int pos = n.pos;


                    if (r.d > 0) {

                        if (pos != layers[layer].size() - 1) {

                            int off = layers[layer].get(pos + 1).x - n.x - xOffset - n.width;
                            assert off >= 0;
                            if (off < min) {
                                min = off;
                            }
                        }

                    } else {

                        if (pos != 0) {

                            int off = n.x - xOffset - layers[layer].get(pos - 1).x - layers[layer].get(pos - 1).width;
                            assert off >= 0;
                            if (off < min) {
                                min = off;
                            }
                        }
                    }
                }

                assert min >= 0;
                if (min != 0) {
                    for (LayoutNode n : s.nodes) {
                        if (r.d > 0) {
                            n.x += min;
                        } else {
                            n.x -= min;
                        }

                    }
                }
            }
        }

        protected void run() {

            generateSegments();
            addEdges();
            topologicalSorting();
            initialPositions();
            for (int i = 0; i < SWEEP_ITERATIONS; i++) {

                sweep(true);
                sweep(true);
                sweep(false);
                sweep(false);
            }

            sweep(true);
            sweep(true);
        }
    }
    private static Comparator<LayoutNode> crossingNodeComparator = new Comparator<LayoutNode>() {

        public int compare(LayoutNode n1, LayoutNode n2) {
            return n1.crossingNumber - n2.crossingNumber;
        }
    };

    private class CrossingReduction extends AlgorithmPart {

        @Override
        public void preCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer < layerCount;
            }
        }

        protected void run() {

            layers = new List[layerCount];

            for (int i = 0; i < layerCount; i++) {
                layers[i] = new ArrayList<LayoutNode>();
            }


            // Generate initial ordering
            HashSet<LayoutNode> visited = new HashSet<LayoutNode>();
            for (LayoutNode n : nodes) {
                if (n.layer == 0) {
                    layers[0].add(n);
                    visited.add(n);
                } else if (n.preds.size() == 0) {
                    layers[n.layer].add(n);
                    visited.add(n);
                }
            }

            for (int i = 0; i < layers.length - 1; i++) {
                for (LayoutNode n : layers[i]) {
                    for (LayoutEdge e : n.succs) {
                        if (!visited.contains(e.to)) {
                            visited.add(e.to);
                            layers[i + 1].add(e.to);
                        }
                    }
                }
            }


            updatePositions();

            initX();

            // Optimize
            for (int i = 0; i < CROSSING_ITERATIONS; i++) {
                downSweep();
                upSweep();
            }

        /*for(int i=0; i<CROSSING_ITERATIONS; i++) {
        doubleSweep();
        }*/
        }

        private void initX() {

            for (int i = 0; i < layers.length; i++) {
                updateXOfLayer(i);
            }
        }

        private void updateXOfLayer(int index) {
            int x = 0;

            for (LayoutNode n : layers[index]) {
                n.x = x;
                x += n.width + X_OFFSET;
            }
        }

        private void updatePositions() {

            for (int i = 0; i < layers.length; i++) {
                int z = 0;
                for (LayoutNode n : layers[i]) {
                    n.pos = z;
                    z++;
                }
            }
        }

        private void downSweep() {

            // Downsweep
            for (int i = 1; i < layerCount; i++) {

                for (LayoutNode n : layers[i]) {
                    n.crossingNumber = 0;
                }

                for (LayoutNode n : layers[i]) {

                    int sum = 0;
                    for (LayoutEdge e : n.preds) {
                        int cur = e.from.x + e.relativeFrom;

                        /*pos;
                        if(e.from.width != 0 && e.relativeFrom != 0) {
                        cur += (float)e.relativeFrom / (float)(e.from.width);
                        }*/

                        sum += cur;
                    }

                    if (n.preds.size() > 0) {
                        sum /= n.preds.size();
                        n.crossingNumber = sum;
                    //if(n.vertex == null) n.crossingNumber += layers[i].size();
                    }
                }


                updateCrossingNumbers(i, true);
                Collections.sort(layers[i], crossingNodeComparator);
                updateXOfLayer(i);

                int z = 0;
                for (LayoutNode n : layers[i]) {
                    n.pos = z;
                    z++;
                }
            }
        }

        private void updateCrossingNumbers(int index, boolean down) {
            for (int i = 0; i < layers[index].size(); i++) {
                LayoutNode n = layers[index].get(i);
                LayoutNode prev = null;
                if (i > 0) {
                    prev = layers[index].get(i - 1);
                }
                LayoutNode next = null;
                if (i < layers[index].size() - 1) {
                    next = layers[index].get(i + 1);
                }

                boolean cond = (n.succs.size() == 0);
                if (down) {
                    cond = (n.preds.size() == 0);
                }

                if (cond) {

                    if (prev != null && next != null) {
                        n.crossingNumber = (prev.crossingNumber + next.crossingNumber) / 2;
                    } else if (prev != null) {
                        n.crossingNumber = prev.crossingNumber;
                    } else if (next != null) {
                        n.crossingNumber = next.crossingNumber;
                    }
                }
            }
        }
        /*
        private void doubleSweep() {
        // Downsweep
        for(int i=0; i<layerCount*2; i++) {
        int index = i;
        if(index >= layerCount) {
        index = 2*layerCount - i - 1;
        }
        for(LayoutNode n : layers[index]) {
        float sum = 0.0f;
        for(LayoutEdge e : n.preds) {
        float cur = e.from.pos;
        if(e.from.width != 0 && e.relativeFrom != 0) {
        cur += (float)e.relativeFrom / (float)(e.from.width);
        }
        sum += cur;
        }
        for(LayoutEdge e : n.succs) {
        float cur = e.to.pos;
        if(e.to.width != 0 && e.relativeTo != 0) {
        cur += (float)e.relativeTo / (float)(e.to.width);
        }
        sum += cur;
        }
        if(n.preds.size() + n.succs.size() > 0) {
        sum /= n.preds.size() + n.succs.size();
        n.crossingNumber = sum;
        }
        }
        Collections.sort(layers[index], crossingNodeComparator);
        updateXOfLayer(index);
        int z = 0;
        for(LayoutNode n : layers[index]) {
        n.pos = z;
        z++;
        }
        }
        }*/

        private void upSweep() {
            // Upsweep
            for (int i = layerCount - 2; i >= 0; i--) {

                for (LayoutNode n : layers[i]) {
                    n.crossingNumber = 0;
                }

                for (LayoutNode n : layers[i]) {

                    int sum = 0;
                    for (LayoutEdge e : n.succs) {
                        int cur = e.to.x + e.relativeTo;//pos;
                                                /*
                        if(e.to.width != 0 && e.relativeTo != 0) {
                        cur += (float)e.relativeTo / (float)(e.to.width);
                        }*/

                        sum += cur;
                    }

                    if (n.succs.size() > 0) {
                        sum /= n.succs.size();
                        n.crossingNumber = sum;
                    //if(n.vertex == null) n.crossingNumber += layers[i].size();
                    }

                }

                updateCrossingNumbers(i, false);
                Collections.sort(layers[i], crossingNodeComparator);
                updateXOfLayer(i);

                int z = 0;
                for (LayoutNode n : layers[i]) {
                    n.pos = z;
                    z++;
                }
            }
        }

        private int evaluate() {
            // TODO: Implement efficient evaluate / crossing min
            return 0;
        }

        @Override
        public void postCheck() {

            HashSet<LayoutNode> visited = new HashSet<LayoutNode>();
            for (int i = 0; i < layers.length; i++) {
                for (LayoutNode n : layers[i]) {
                    assert !visited.contains(n);
                    assert n.layer == i;
                    visited.add(n);
                }
            }

        }
    }

    private class AssignYCoordinates extends AlgorithmPart {

        protected void run() {
            int curY = 0;
            //maxLayerHeight = new int[layers.length];
            for (int i = 0; i < layers.length; i++) {
                int maxHeight = 0;
                int baseLine = 0;
                int bottomBaseLine = 0;
                for (LayoutNode n : layers[i]) {
                    maxHeight = Math.max(maxHeight, n.height - n.yOffset - n.bottomYOffset);
                    baseLine = Math.max(baseLine, n.yOffset);
                    bottomBaseLine = Math.max(bottomBaseLine, n.bottomYOffset);
                }

                int maxXOffset = 0;
                for (LayoutNode n : layers[i]) {
                    if (n.vertex == null) {
                        // Dummy node
                        n.y = curY;
                        n.height = maxHeight + baseLine + bottomBaseLine;

                    } else {
                        n.y = curY + baseLine + (maxHeight - (n.height - n.yOffset - n.bottomYOffset)) / 2 - n.yOffset;
                    }

                    for (LayoutEdge e : n.succs) {
                        int curXOffset = Math.abs(n.x - e.to.x);
                        maxXOffset = Math.max(curXOffset, maxXOffset);
                    }
                }

                //maxLayerHeight[i] = maxHeight + baseLine + bottomBaseLine;

                curY += maxHeight + baseLine + bottomBaseLine;
                curY += layerOffset + (int) Math.sqrt(maxXOffset);
            }
        }
    }

    private class CreateDummyNodes extends AlgorithmPart {

        private int oldNodeCount;

        @Override
        protected void preCheck() {
            for (LayoutNode n : nodes) {
                for (LayoutEdge e : n.succs) {
                    assert e.from != null;
                    assert e.from == n;
                    assert e.from.layer < e.to.layer;
                }

                for (LayoutEdge e : n.preds) {
                    assert e.to != null;
                    assert e.to == n;
                }
            }
        }

        protected void run() {
            oldNodeCount = nodes.size();


            if (combine == Combine.SAME_OUTPUTS) {

                Comparator<LayoutEdge> comparator = new Comparator<LayoutEdge>() {

                    public int compare(LayoutEdge e1, LayoutEdge e2) {
                        return e1.to.layer - e2.to.layer;
                    }
                };
                HashMap<Integer, List<LayoutEdge>> portHash = new HashMap<Integer, List<LayoutEdge>>();
                ArrayList<LayoutNode> currentNodes = new ArrayList<LayoutNode>(nodes);
                for (LayoutNode n : currentNodes) {
                    portHash.clear();

                    ArrayList<LayoutEdge> succs = new ArrayList<LayoutEdge>(n.succs);
                    HashMap<Integer, LayoutNode> topNodeHash = new HashMap<Integer, LayoutNode>();
                    HashMap<Integer, HashMap<Integer, LayoutNode>> bottomNodeHash = new HashMap<Integer, HashMap<Integer, LayoutNode>>();
                    for (LayoutEdge e : succs) {
                        assert e.from.layer < e.to.layer;
                        if (e.from.layer != e.to.layer - 1) {
                            if (maxLayerLength != -1 && e.to.layer - e.from.layer > maxLayerLength/* && e.to.preds.size() > 1 && e.from.succs.size() > 1*/) {
                                assert maxLayerLength > 2;
                                e.to.preds.remove(e);
                                e.from.succs.remove(e);

                                LayoutEdge topEdge = null;

                                if (combine == Combine.SAME_OUTPUTS && topNodeHash.containsKey(e.relativeFrom)) {
                                    LayoutNode topNode = topNodeHash.get(e.relativeFrom);
                                    topEdge = new LayoutEdge();
                                    topEdge.relativeFrom = e.relativeFrom;
                                    topEdge.from = e.from;
                                    topEdge.relativeTo = topNode.width / 2;
                                    topEdge.to = topNode;
                                    topEdge.link = e.link;
                                    e.from.succs.add(topEdge);
                                    topNode.preds.add(topEdge);
                                } else {

                                    LayoutNode topNode = new LayoutNode();
                                    topNode.layer = e.from.layer + 1;
                                    topNode.width = DUMMY_WIDTH;
                                    topNode.height = DUMMY_HEIGHT;
                                    nodes.add(topNode);
                                    topEdge = new LayoutEdge();
                                    topEdge.relativeFrom = e.relativeFrom;
                                    topEdge.from = e.from;
                                    topEdge.relativeTo = topNode.width / 2;
                                    topEdge.to = topNode;
                                    topEdge.link = e.link;
                                    e.from.succs.add(topEdge);
                                    topNode.preds.add(topEdge);
                                    topNodeHash.put(e.relativeFrom, topNode);
                                    bottomNodeHash.put(e.relativeFrom, new HashMap<Integer, LayoutNode>());
                                }

                                HashMap<Integer, LayoutNode> hash = bottomNodeHash.get(e.relativeFrom);

                                LayoutNode bottomNode = null;
                                if (hash.containsKey(e.to.layer)) {
                                    bottomNode = hash.get(e.to.layer);
                                } else {

                                    bottomNode = new LayoutNode();
                                    bottomNode.layer = e.to.layer - 1;
                                    bottomNode.width = DUMMY_WIDTH;
                                    bottomNode.height = DUMMY_HEIGHT;
                                    nodes.add(bottomNode);
                                    hash.put(e.to.layer, bottomNode);
                                }

                                LayoutEdge bottomEdge = new LayoutEdge();
                                bottomEdge.relativeTo = e.relativeTo;
                                bottomEdge.to = e.to;
                                bottomEdge.relativeFrom = bottomNode.width / 2;
                                bottomEdge.from = bottomNode;
                                bottomEdge.link = e.link;
                                e.to.preds.add(bottomEdge);
                                bottomEdgeHash.put(topEdge, bottomEdge);
                                bottomNode.succs.add(bottomEdge);

                            } else {
                                Integer i = e.relativeFrom;
                                if (!portHash.containsKey(i)) {
                                    portHash.put(i, new ArrayList<LayoutEdge>());
                                }

                                if (n.vertex.toString().equals("1012 CastPP")) {
                                    int x = 0;
                                }

                                portHash.get(i).add(e);
                            }
                        }
                    }

                    succs = new ArrayList<LayoutEdge>(n.succs);
                    for (LayoutEdge e : succs) {

                        Integer i = e.relativeFrom;
                        if (portHash.containsKey(i)) {

                            List<LayoutEdge> list = portHash.get(i);
                            Collections.sort(list, comparator);

                            if (list.size() == 1) {
                                processSingleEdge(list.get(0));
                            } else {

                                int maxLayer = list.get(0).to.layer;
                                for (LayoutEdge curEdge : list) {
                                    maxLayer = Math.max(maxLayer, curEdge.to.layer);
                                }


                                int cnt = maxLayer - n.layer - 1;
                                LayoutEdge[] edges = new LayoutEdge[cnt];
                                LayoutNode[] nodes = new LayoutNode[cnt];
                                edges[0] = new LayoutEdge();
                                edges[0].from = n;
                                edges[0].relativeFrom = i;
                                n.succs.add(edges[0]);

                                nodes[0] = new LayoutNode();
                                nodes[0].width = dummyWidth;
                                nodes[0].height = dummyHeight;
                                nodes[0].layer = n.layer + 1;
                                nodes[0].preds.add(edges[0]);
                                edges[0].to = nodes[0];
                                edges[0].relativeTo = nodes[0].width / 2;
                                for (int j = 1; j < cnt; j++) {
                                    edges[j] = new LayoutEdge();
                                    edges[j].from = nodes[j - 1];
                                    edges[j].relativeFrom = nodes[j - 1].width / 2;
                                    nodes[j - 1].succs.add(edges[j]);
                                    nodes[j] = new LayoutNode();
                                    nodes[j].width = dummyWidth;
                                    nodes[j].height = dummyHeight;
                                    nodes[j].layer = n.layer + j + 1;
                                    nodes[j].preds.add(edges[j]);
                                    edges[j].to = nodes[j];
                                    edges[j].relativeTo = nodes[j].width / 2;
                                }

                                for (LayoutEdge curEdge : list) {
                                    assert curEdge.to.layer - n.layer - 2 >= 0;
                                    assert curEdge.to.layer - n.layer - 2 < cnt;
                                    LayoutNode anchor = nodes[curEdge.to.layer - n.layer - 2];
                                    anchor.succs.add(curEdge);
                                    curEdge.from = anchor;
                                    curEdge.relativeFrom = anchor.width / 2;
                                    n.succs.remove(curEdge);
                                }

                            }

                            portHash.remove(i);
                        }
                    }
                }
            } else if (combine == Combine.SAME_INPUTS) {
                throw new UnsupportedOperationException("Currently not supported");
            } else {
                ArrayList<LayoutNode> currentNodes = new ArrayList<LayoutNode>(nodes);
                for (LayoutNode n : currentNodes) {
                    for (LayoutEdge e : n.succs) {
                        processSingleEdge(e);
                    }
                }
            }
        }

        private void processSingleEdge(LayoutEdge e) {
            LayoutNode n = e.from;
            if (e.to.layer > n.layer + 1) {
                LayoutEdge last = e;
                for (int i = n.layer + 1; i < last.to.layer; i++) {
                    last = addBetween(last, i);
                }
            }
        }

        private LayoutEdge addBetween(LayoutEdge e, int layer) {
            LayoutNode n = new LayoutNode();
            n.width = dummyWidth;
            n.height = dummyHeight;
            n.layer = layer;
            n.preds.add(e);
            nodes.add(n);
            LayoutEdge result = new LayoutEdge();
            n.succs.add(result);
            result.from = n;
            result.relativeFrom = n.width / 2;
            result.to = e.to;
            result.relativeTo = e.relativeTo;
            e.relativeTo = n.width / 2;
            e.to.preds.remove(e);
            e.to.preds.add(result);
            e.to = n;
            return result;
        }

        @Override
        public void printStatistics() {
            System.out.println("Dummy nodes created: " + (nodes.size() - oldNodeCount));
        }

        @Override
        public void postCheck() {
            ArrayList<LayoutNode> currentNodes = new ArrayList<LayoutNode>(nodes);
            for (LayoutNode n : currentNodes) {
                for (LayoutEdge e : n.succs) {
                    assert e.from.layer == e.to.layer - 1;
                }
            }

            for (int i = 0; i < layers.length; i++) {
                assert layers[i].size() > 0;
                for (LayoutNode n : layers[i]) {
                    assert n.layer == i;
                }
            }
        }
    }

    private class AssignLayers extends AlgorithmPart {

        @Override
        public void preCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer == -1;
            }
        }

        protected void run() {
            HashSet<LayoutNode> set = new HashSet<LayoutNode>();
            for (LayoutNode n : nodes) {
                if (n.preds.size() == 0) {
                    set.add(n);
                    n.layer = 0;
                }
            }

            int z = minLayerDifference;
            HashSet<LayoutNode> newSet = new HashSet<LayoutNode>();
            HashSet<LayoutNode> failed = new HashSet<LayoutNode>();
            while (!set.isEmpty()) {

                newSet.clear();
                failed.clear();

                for (LayoutNode n : set) {

                    for (LayoutEdge se : n.succs) {
                        LayoutNode s = se.to;
                        if (!newSet.contains(s) && !failed.contains(s)) {
                            boolean ok = true;
                            for (LayoutEdge pe : s.preds) {
                                LayoutNode p = pe.from;
                                if (p.layer == -1) {
                                    ok = false;
                                    break;
                                }
                            }

                            if (ok) {
                                newSet.add(s);
                            } else {
                                failed.add(s);
                            }
                        }
                    }

                }

                for (LayoutNode n : newSet) {
                    n.layer = z;
                }

                // Swap sets
                HashSet<LayoutNode> tmp = set;
                set = newSet;
                newSet = tmp;
                z += minLayerDifference;
            }

            optimize(set);

            layerCount = z - minLayerDifference;

            for (Vertex v : lastLayerHint) {

                LayoutNode n = vertexToLayoutNode.get(v);
                assert n.succs.size() == 0;
                n.layer = layerCount - 1;
            }

            for (Vertex v : firstLayerHint) {
                LayoutNode n = vertexToLayoutNode.get(v);
                assert n.preds.size() == 0;
                assert n.layer == 0;
            }
        }

        public void optimize(HashSet<LayoutNode> set) {

            for (LayoutNode n : set) {
                if (n.preds.size() == 0 && n.succs.size() > 0) {
                    int minLayer = n.succs.get(0).to.layer;
                    for (LayoutEdge e : n.succs) {
                        minLayer = Math.min(minLayer, e.to.layer);
                    }

                    n.layer = minLayer - 1;
                }
            }

        }

        @Override
        public void postCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer >= 0;
                assert n.layer < layerCount;
                for (LayoutEdge e : n.succs) {
                    assert e.from.layer < e.to.layer;
                }
            }
        }
    }

    private class ReverseEdges extends AlgorithmPart {

        private HashSet<LayoutNode> visited;
        private HashSet<LayoutNode> active;

        protected void run() {

            // Remove self-edges, TODO: Special treatment
            for (LayoutNode node : nodes) {
                ArrayList<LayoutEdge> succs = new ArrayList<LayoutEdge>(node.succs);
                for (LayoutEdge e : succs) {
                    assert e.from == node;
                    if (e.to == node) {
                        node.succs.remove(e);
                        node.preds.remove(e);
                    }
                }
            }

            // Reverse inputs of roots
            for (LayoutNode node : nodes) {
                if (node.vertex.isRoot()) {
                    boolean ok = true;
                    for (LayoutEdge e : node.preds) {
                        if (e.from.vertex.isRoot()) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        reverseAllInputs(node);
                    }
                }
            }


            // Start DFS and reverse back edges
            visited = new HashSet<LayoutNode>();
            active = new HashSet<LayoutNode>();
            for (LayoutNode node : nodes) {
                DFS(node);
            }


            for (LayoutNode node : nodes) {

                SortedSet<Integer> reversedDown = new TreeSet<Integer>();

                for (LayoutEdge e : node.succs) {
                    if (reversedLinks.contains(e.link)) {
                        reversedDown.add(e.relativeFrom);
                    }
                }


                SortedSet<Integer> reversedUp = null;
                if (reversedDown.size() == 0) {
                    reversedUp = new TreeSet<Integer>(Collections.reverseOrder());
                } else {
                    reversedUp = new TreeSet<Integer>();
                }

                for (LayoutEdge e : node.preds) {
                    if (reversedLinks.contains(e.link)) {
                        reversedUp.add(e.relativeTo);
                    }
                }

                final int offset = X_OFFSET + DUMMY_WIDTH;

                int curX = 0;
                int curWidth = node.width + reversedDown.size() * offset;
                for (int pos : reversedDown) {
                    ArrayList<LayoutEdge> reversedSuccs = new ArrayList<LayoutEdge>();
                    for (LayoutEdge e : node.succs) {
                        if (e.relativeFrom == pos && reversedLinks.contains(e.link)) {
                            reversedSuccs.add(e);
                            e.relativeFrom = curWidth;
                        }
                    }

                    ArrayList<Point> startPoints = new ArrayList<Point>();
                    startPoints.add(new Point(curWidth, curX));
                    startPoints.add(new Point(pos, curX));
                    startPoints.add(new Point(pos, reversedDown.size() * offset));
                    for (LayoutEdge e : reversedSuccs) {
                        reversedLinkStartPoints.put(e.link, startPoints);
                    }

                    node.inOffsets.put(pos, -curX);
                    curX += offset;
                    node.height += offset;
                    node.yOffset += offset;
                    curWidth -= offset;
                }
                node.width += reversedDown.size() * offset;

                if (reversedDown.size() == 0) {
                    curX = offset;
                } else {
                    curX = -offset;
                }

                curX = 0;
                int minX = 0;
                if (reversedDown.size() != 0) {
                    minX = -offset * reversedUp.size();
                }

                int oldNodeHeight = node.height;
                for (int pos : reversedUp) {
                    ArrayList<LayoutEdge> reversedPreds = new ArrayList<LayoutEdge>();
                    for (LayoutEdge e : node.preds) {
                        if (e.relativeTo == pos && reversedLinks.contains(e.link)) {
                            if (reversedDown.size() == 0) {
                                e.relativeTo = node.width + offset;
                            } else {
                                e.relativeTo = curX - offset;
                            }

                            reversedPreds.add(e);
                        }
                    }
                    node.height += offset;
                    ArrayList<Point> endPoints = new ArrayList<Point>();

                    if (reversedDown.size() == 0) {

                        curX += offset;
                        node.width += offset;
                        endPoints.add(new Point(node.width, node.height));

                    } else {
                        curX -= offset;
                        node.width += offset;
                        endPoints.add(new Point(curX, node.height));
                    }

                    node.outOffsets.put(pos - minX, curX);
                    curX += offset;
                    node.bottomYOffset += offset;


                    endPoints.add(new Point(pos, node.height));
                    endPoints.add(new Point(pos, oldNodeHeight));
                    for (LayoutEdge e : reversedPreds) {
                        reversedLinkEndPoints.put(e.link, endPoints);
                    }
                }


                if (minX < 0) {
                    for (LayoutEdge e : node.preds) {
                        e.relativeTo -= minX;
                    }

                    for (LayoutEdge e : node.succs) {
                        e.relativeFrom -= minX;
                    }

                    node.xOffset = -minX;
                    node.width += -minX;
                }
            }

        }

        private void DFS(LayoutNode startNode) {
            if (visited.contains(startNode)) {
                return;
            }

            Stack<LayoutNode> stack = new Stack<LayoutNode>();
            stack.push(startNode);

            while (!stack.empty()) {
                LayoutNode node = stack.pop();

                if (visited.contains(node)) {
                    // Node no longer active
                    active.remove(node);
                    continue;
                }

                // Repush immediately to know when no longer active
                stack.push(node);
                visited.add(node);
                active.add(node);

                ArrayList<LayoutEdge> succs = new ArrayList<LayoutEdge>(node.succs);
                for (LayoutEdge e : succs) {
                    if (active.contains(e.to)) {
                        assert visited.contains(e.to);
                        // Encountered back edge
                        reverseEdge(e);
                    } else if (!visited.contains(e.to) && (linksToFollow.size() == 0 || linksToFollow.contains(e.link))) {
                        stack.push(e.to);
                    }
                }
            }
        }

        private void reverseAllInputs(LayoutNode node) {
            for (LayoutEdge e : node.preds) {
                assert !reversedLinks.contains(e.link);
                reversedLinks.add(e.link);
                node.succs.add(e);
                e.from.preds.add(e);
                e.from.succs.remove(e);
                int oldRelativeFrom = e.relativeFrom;
                int oldRelativeTo = e.relativeTo;
                e.to = e.from;
                e.from = node;
                e.relativeFrom = oldRelativeTo;
                e.relativeTo = oldRelativeFrom;
            }
            node.preds.clear();
        }

        private void reverseEdge(LayoutEdge e) {
            assert !reversedLinks.contains(e.link);
            reversedLinks.add(e.link);

            LayoutNode oldFrom = e.from;
            LayoutNode oldTo = e.to;
            int oldRelativeFrom = e.relativeFrom;
            int oldRelativeTo = e.relativeTo;

            e.from = oldTo;
            e.to = oldFrom;
            e.relativeFrom = oldRelativeTo;
            e.relativeTo = oldRelativeFrom;

            oldFrom.succs.remove(e);
            oldFrom.preds.add(e);
            oldTo.preds.remove(e);
            oldTo.succs.add(e);
        }

        @Override
        public void postCheck() {

            for (LayoutNode n : nodes) {

                HashSet<LayoutNode> curVisited = new HashSet<LayoutNode>();
                Queue<LayoutNode> queue = new LinkedList<LayoutNode>();
                for (LayoutEdge e : n.succs) {
                    LayoutNode s = e.to;
                    queue.add(s);
                    curVisited.add(s);
                }

                while (!queue.isEmpty()) {
                    LayoutNode curNode = queue.remove();

                    for (LayoutEdge e : curNode.succs) {
                        assert e.to != n;
                        if (!curVisited.contains(e.to)) {
                            queue.add(e.to);
                            curVisited.add(e.to);
                        }
                    }
                }
            }
        }
    }
    private Comparator<Link> linkComparator = new Comparator<Link>() {

        public int compare(Link l1, Link l2) {

            int result = l1.getFrom().getVertex().compareTo(l2.getFrom().getVertex());
            if (result != 0) {
                return result;
            }
            result = l1.getTo().getVertex().compareTo(l2.getTo().getVertex());
            if (result != 0) {
                return result;
            }
            result = l1.getFrom().getRelativePosition().x - l2.getFrom().getRelativePosition().x;
            if (result != 0) {
                return result;
            }
            result = l1.getTo().getRelativePosition().x - l2.getTo().getRelativePosition().x;
            return result;
        }
    };

    private class BuildDatastructure extends AlgorithmPart {

        protected void run() {
            // Set up nodes
            List<Vertex> vertices = new ArrayList<Vertex>(graph.getVertices());
            Collections.sort(vertices);

            for (Vertex v : vertices) {
                LayoutNode node = new LayoutNode();
                Dimension size = v.getSize();
                node.width = (int) size.getWidth();
                node.height = (int) size.getHeight();
                node.vertex = v;
                nodes.add(node);
                vertexToLayoutNode.put(v, node);
            }

            // Set up edges
            List<Link> links = new ArrayList<Link>(graph.getLinks());
            Collections.sort(links, linkComparator);
            for (Link l : links) {
                LayoutEdge edge = new LayoutEdge();
                assert vertexToLayoutNode.containsKey(l.getFrom().getVertex());
                assert vertexToLayoutNode.containsKey(l.getTo().getVertex());
                edge.from = vertexToLayoutNode.get(l.getFrom().getVertex());
                edge.to = vertexToLayoutNode.get(l.getTo().getVertex());
                edge.relativeFrom = l.getFrom().getRelativePosition().x;
                edge.relativeTo = l.getTo().getRelativePosition().x;
                edge.link = l;
                edge.from.succs.add(edge);
                edge.to.preds.add(edge);
            //assert edge.from != edge.to; // No self-loops allowed
            }

            for (Link l : importantLinks) {
                if (!vertexToLayoutNode.containsKey(l.getFrom().getVertex()) ||
                        vertexToLayoutNode.containsKey(l.getTo().getVertex())) {
                    continue;
                }
                LayoutNode from = vertexToLayoutNode.get(l.getFrom().getVertex());
                LayoutNode to = vertexToLayoutNode.get(l.getTo().getVertex());
                for (LayoutEdge e : from.succs) {
                    if (e.to == to) {
                        linksToFollow.add(e.link);
                    }
                }
            }
        }

        @Override
        public void postCheck() {

            assert vertexToLayoutNode.keySet().size() == nodes.size();
            assert nodes.size() == graph.getVertices().size();

            for (Vertex v : graph.getVertices()) {

                LayoutNode node = vertexToLayoutNode.get(v);
                assert node != null;

                for (LayoutEdge e : node.succs) {
                    assert e.from == node;
                }

                for (LayoutEdge e : node.preds) {
                    assert e.to == node;
                }

            }
        }
    }

    public void doRouting(LayoutGraph graph) {
    // Do nothing for now
    }
}
