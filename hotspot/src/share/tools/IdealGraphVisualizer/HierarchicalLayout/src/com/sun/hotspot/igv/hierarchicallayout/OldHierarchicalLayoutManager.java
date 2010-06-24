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

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.sun.hotspot.igv.layout.LayoutGraph;
import com.sun.hotspot.igv.layout.LayoutManager;
import com.sun.hotspot.igv.layout.Link;
import com.sun.hotspot.igv.layout.Port;
import com.sun.hotspot.igv.layout.Vertex;

/**
 *
 * @author Thomas Wuerthinger
 */
public class OldHierarchicalLayoutManager implements LayoutManager {

    public static final int DUMMY_WIDTH = 0;
    public static final int DUMMY_HEIGHT = 0;
    public static final int LAYER_OFFSET = 50;
    public static final int OFFSET = 8;
    public static final boolean VERTICAL_LAYOUT = true;
    public static final boolean ASSERT = false;
    public static final boolean TRACE = false;
    public static final Timing initTiming = new Timing("init");
    public static final Timing removeCyclesTiming = new Timing("removeCycles");
    public static final Timing reversedEdgesTiming = new Timing("reversedEdges");
    public static final Timing assignLayersTiming = new Timing("assignLayers");
    public static final Timing dummyNodesTiming = new Timing("dummyNodes");
    public static final Timing crossingReductionTiming = new Timing("crossingReduction");
    public static final Timing assignCoordinatesTiming = new Timing("assignCoordinates");
    public static final Timing assignRealTiming = new Timing("assignReal");
    public static final Timing rootVertexTiming = new Timing("rootVertex");
    public static final Timing createEdgesTiming = new Timing("createEdges");
    public static final Timing optimizeMedianTiming = new Timing("optimizeMedian");
    private Combine combine;

    public enum Combine {

        NONE,
        SAME_INPUTS,
        SAME_OUTPUTS
    }

    private class NodeData {

        private Map<Port, Integer> reversePositions;
        private Vertex node;
        private Link edge;
        private int layer;
        private int x;
        private int y;
        private int width;

        public NodeData(Vertex node) {
            reversePositions = new HashMap<Port, Integer>();
            layer = -1;
            this.node = node;
            assert node != null;

            if (VERTICAL_LAYOUT) {
                width = node.getSize().width;
            } else {
                width = node.getSize().height;
            }
        }

        public NodeData(Link edge) {
            layer = -1;
            this.edge = edge;
            assert edge != null;

            if (VERTICAL_LAYOUT) {
                width = DUMMY_WIDTH;
            } else {
                width = DUMMY_HEIGHT;
            }
        }

        public Vertex getNode() {
            return node;
        }

        public Link getEdge() {
            return edge;
        }

        public int getCoordinate() {
            return x;
        }

        public void setCoordinate(int x) {
            this.x = x;
        }

        public int getX() {
            if (VERTICAL_LAYOUT) {
                return x;
            } else {
                return y;
            }
        }

        public int getY() {
            if (VERTICAL_LAYOUT) {
                return y;
            } else {
                return x;
            }
        }

        public void setLayerCoordinate(int y) {
            this.y = y;
        }

        public void setLayer(int x) {
            layer = x;
        }

        public int getLayer() {
            return layer;
        }

        public boolean isDummy() {
            return edge != null;
        }

        public int getWidth() {
            return width;
        }

        public void addReversedStartEdge(Edge<NodeData, EdgeData> e) {
            assert e.getData().isReversed();
            Port port = e.getData().getEdge().getTo();
            int pos = addReversedPort(port);
            Point start = e.getData().getRelativeStart();
            e.getData().addStartPoint(start);
            int yCoord = node.getSize().height + width - node.getSize().width;
            e.getData().addStartPoint(new Point(start.x, yCoord));
            e.getData().addStartPoint(new Point(pos, yCoord));
            e.getData().setRelativeStart(new Point(pos, 0));
        }

        private int addReversedPort(Port p) {
            if (reversePositions.containsKey(p)) {
                return reversePositions.get(p);
            } else {
                width += OFFSET;
                reversePositions.put(p, width);
                return width;
            }
        }

        public void addReversedEndEdge(Edge<NodeData, EdgeData> e) {
            assert e.getData().isReversed();
            int pos = addReversedPort(e.getData().getEdge().getFrom());
            Point end = e.getData().getRelativeEnd();
            e.getData().setRelativeEnd(new Point(pos, node.getSize().height));
            int yCoord = 0 - width + node.getSize().width;
            e.getData().addEndPoint(new Point(pos, yCoord));
            e.getData().addEndPoint(new Point(end.x, yCoord));
            e.getData().addEndPoint(end);
        }

        public int getHeight() {
            if (isDummy()) {
                if (VERTICAL_LAYOUT) {
                    return DUMMY_HEIGHT;
                } else {
                    return DUMMY_WIDTH;
                }

            } else {
                if (VERTICAL_LAYOUT) {
                    return node.getSize().height;
                } else {
                    return node.getSize().width;
                }
            }
        }

        @Override
        public String toString() {
            if (isDummy()) {
                return edge.toString() + "(layer=" + layer + ")";
            } else {
                return node.toString() + "(layer=" + layer + ")";
            }
        }
    }

    private class EdgeData {

        private Point relativeEnd;
        private Point relativeStart;
        private List<Point> startPoints;
        private List<Point> endPoints;
        private boolean important;
        private boolean reversed;
        private Link edge;

        public EdgeData(Link edge) {
            this(edge, false);
        }

        public EdgeData(Link edge, boolean rev) {
            this.edge = edge;
            reversed = rev;
            relativeStart = edge.getFrom().getRelativePosition();
            relativeEnd = edge.getTo().getRelativePosition();
            assert relativeStart.x >= 0 && relativeStart.x <= edge.getFrom().getVertex().getSize().width;
            assert relativeStart.y >= 0 && relativeStart.y <= edge.getFrom().getVertex().getSize().height;
            assert relativeEnd.x >= 0 && relativeEnd.x <= edge.getTo().getVertex().getSize().width;
            assert relativeEnd.y >= 0 && relativeEnd.y <= edge.getTo().getVertex().getSize().height;
            startPoints = new ArrayList<Point>();
            endPoints = new ArrayList<Point>();
            this.important = true;
        }

        public boolean isImportant() {
            return important;
        }

        public void setImportant(boolean b) {
            this.important = b;
        }

        public List<Point> getStartPoints() {
            return startPoints;
        }

        public List<Point> getEndPoints() {
            return endPoints;
        }

        public List<Point> getAbsoluteEndPoints() {
            if (endPoints.size() == 0) {
                return endPoints;
            }

            List<Point> result = new ArrayList<Point>();
            Point point = edge.getTo().getVertex().getPosition();
            for (Point p : endPoints) {
                Point p2 = new Point(p.x + point.x, p.y + point.y);
                result.add(p2);
            }

            return result;
        }

        public List<Point> getAbsoluteStartPoints() {
            if (startPoints.size() == 0) {
                return startPoints;
            }

            List<Point> result = new ArrayList<Point>();
            Point point = edge.getFrom().getVertex().getPosition();
            for (Point p : startPoints) {
                Point p2 = new Point(p.x + point.x, p.y + point.y);
                result.add(p2);
            }

            return result;
        }

        public void addEndPoint(Point p) {
            endPoints.add(p);
        }

        public void addStartPoint(Point p) {
            startPoints.add(p);
        }

        public Link getEdge() {
            return edge;
        }

        public void setRelativeEnd(Point p) {
            relativeEnd = p;
        }

        public void setRelativeStart(Point p) {
            relativeStart = p;
        }

        public Point getRelativeEnd() {
            return relativeEnd;
        }

        public Point getRelativeStart() {
            return relativeStart;
        }

        public boolean isReversed() {
            return reversed;
        }

        public void setReversed(boolean b) {
            reversed = b;
        }

        @Override
        public String toString() {
            return "EdgeData[reversed=" + reversed + "]";
        }
    }
    private Graph<NodeData, EdgeData> graph;
    private Map<Vertex, Node<NodeData, EdgeData>> nodeMap;
    private int layerOffset;

    /** Creates a new instance of HierarchicalPositionManager */
    public OldHierarchicalLayoutManager(Combine combine) {
        this(combine, LAYER_OFFSET);
    }

    public OldHierarchicalLayoutManager(Combine combine, int layerOffset) {
        this.combine = combine;
        this.layerOffset = layerOffset;
    }

    public void doRouting(LayoutGraph graph) {
    }

    //public void setPositions(PositionedNode rootNode, List<? extends PositionedNode> nodes, List<? extends PositionedEdge> edges) {
    public void doLayout(LayoutGraph layoutGraph) {
        doLayout(layoutGraph, new HashSet<Vertex>(), new HashSet<Vertex>());
    }

    public void doLayout(LayoutGraph layoutGraph, Set<? extends Vertex> firstLayerHint, Set<? extends Vertex> lastLayerHint) {
        doLayout(layoutGraph, firstLayerHint, lastLayerHint, new HashSet<Link>());
    }

    public void doLayout(LayoutGraph layoutGraph, Set<? extends Vertex> firstLayerHint, Set<? extends Vertex> lastLayerHint, Set<? extends Link> importantLinksHint) {

        if (TRACE) {
            System.out.println("HierarchicalPositionManager.doLayout called");
            System.out.println("Vertex count = " + layoutGraph.getVertices().size() + " Link count = " + layoutGraph.getLinks().size());
        }

        // Nothing to do => quit immediately
        if (layoutGraph.getVertices().size() == 0) {
            return;
        }

        initTiming.start();

        // Mapping vertex to Node in graph
        nodeMap = new HashMap<Vertex, Node<NodeData, EdgeData>>();

        graph = new Graph<NodeData, EdgeData>();

        Set<Node<NodeData, EdgeData>> rootNodes = new HashSet<Node<NodeData, EdgeData>>();
        Set<Vertex> startRootVertices = new HashSet<Vertex>();

        for (Vertex v : layoutGraph.getVertices()) {
            if (v.isRoot()) {
                startRootVertices.add(v);
            }
        }

        rootVertexTiming.start();
        Set<Vertex> rootVertices = layoutGraph.findRootVertices(startRootVertices);
        rootVertexTiming.stop();


        for (Vertex node : layoutGraph.getVertices()) {

            NodeData data = new NodeData(node);
            Node<NodeData, EdgeData> n = graph.createNode(data, node);
            nodeMap.put(node, n);

            if (rootVertices.contains(node)) {
                rootNodes.add(n);
            }
        }

        Set<? extends Link> links = layoutGraph.getLinks();
        Link[] linkArr = new Link[links.size()];
        links.toArray(linkArr);

        List<Link> linkList = new ArrayList<Link>();
        for (Link l : linkArr) {
            linkList.add(l);
        }

        createEdgesTiming.start();
        Collections.sort(linkList, new Comparator<Link>() {

            public int compare(Link o1, Link o2) {
                int result = o1.getFrom().getVertex().compareTo(o2.getFrom().getVertex());
                if (result == 0) {
                    return o1.getTo().getVertex().compareTo(o2.getTo().getVertex());
                } else {
                    return result;
                }
            }
        });

        for (Link edge : linkList) {
            EdgeData data = new EdgeData(edge);
            graph.createEdge(graph.getNode(edge.getFrom().getVertex()), graph.getNode(edge.getTo().getVertex()), data, data);
            if (importantLinksHint.size() > 0 && !importantLinksHint.contains(edge)) {
                data.setImportant(false);
            }
        }
        createEdgesTiming.stop();

        initTiming.stop();

        removeCyclesTiming.start();

        // STEP 1: Remove cycles!
        removeCycles(rootNodes);
        if (ASSERT) {
            assert checkRemoveCycles();
        }

        removeCyclesTiming.stop();

        reversedEdgesTiming.start();

        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            List<Edge<NodeData, EdgeData>> edges = new ArrayList<Edge<NodeData, EdgeData>>(n.getOutEdges());
            Collections.sort(edges, new Comparator<Edge<NodeData, EdgeData>>() {

                public int compare(Edge<NodeData, EdgeData> o1, Edge<NodeData, EdgeData> o2) {
                    return o2.getData().getRelativeEnd().x - o1.getData().getRelativeEnd().x;
                }
            });


            for (Edge<NodeData, EdgeData> e : edges) {

                if (e.getData().isReversed()) {
                    e.getSource().getData().addReversedEndEdge(e);
                }
            }
        }

        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            List<Edge<NodeData, EdgeData>> edges = new ArrayList<Edge<NodeData, EdgeData>>(n.getInEdges());
            Collections.sort(edges, new Comparator<Edge<NodeData, EdgeData>>() {

                public int compare(Edge<NodeData, EdgeData> o1, Edge<NodeData, EdgeData> o2) {
                    return o2.getData().getRelativeStart().x - o1.getData().getRelativeStart().x;
                }
            });


            for (Edge<NodeData, EdgeData> e : edges) {
                if (e.getData().isReversed()) {
                    e.getDest().getData().addReversedStartEdge(e);
                }
            }
        }

        reversedEdgesTiming.stop();

        assignLayersTiming.start();
        // STEP 2: Assign layers!
        int maxLayer = assignLayers(rootNodes, firstLayerHint, lastLayerHint);
        if (ASSERT) {
            assert checkAssignLayers();
        }

        // Put into layer array
        //int maxLayer = 0;
        //for(Node<NodeData, EdgeData> n : graph.getNodes()) {
        //    maxLayer = Math.max(maxLayer, n.getData().getLayer());
        //}


        ArrayList<Node<NodeData, EdgeData>> layers[] = new ArrayList[maxLayer + 1];
        int layerSizes[] = new int[maxLayer + 1];
        for (int i = 0; i < maxLayer + 1; i++) {
            layers[i] = new ArrayList<Node<NodeData, EdgeData>>();
        }

        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            int curLayer = n.getData().getLayer();
            layers[curLayer].add(n);
        }

        assignLayersTiming.stop();

        // STEP 3: Insert dummy nodes!
        dummyNodesTiming.start();
        insertDummyNodes(layers);
        if (ASSERT) {
            assert checkDummyNodes();
        }
        dummyNodesTiming.stop();

        crossingReductionTiming.start();
        // STEP 4: Assign Y coordinates
        assignLayerCoordinates(layers, layerSizes);

        // STEP 5: Crossing reduction
        crossingReduction(layers);
        crossingReductionTiming.stop();

        // STEP 6: Assign Y coordinates
        assignCoordinatesTiming.start();
        assignCoordinates(layers);
        assignCoordinatesTiming.stop();

        assignRealTiming.start();

        // Assign coordinates of nodes to real objects
        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            if (!n.getData().isDummy()) {

                Vertex node = n.getData().getNode();
                node.setPosition(new Point(n.getData().getX(), n.getData().getY()));
            }
        }

        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            if (!n.getData().isDummy()) {

                Vertex node = n.getData().getNode();

                List<Edge<NodeData, EdgeData>> outEdges = n.getOutEdges();
                for (Edge<NodeData, EdgeData> e : outEdges) {
                    Node<NodeData, EdgeData> succ = e.getDest();
                    if (succ.getData().isDummy()) {
                        //PositionedEdge edge = succ.getData().getEdge();
                        List<Point> points = new ArrayList<Point>();
                        assignToRealObjects(layerSizes, succ, points);
                    } else {
                        List<Point> points = new ArrayList<Point>();

                        EdgeData otherEdgeData = e.getData();
                        points.addAll(otherEdgeData.getAbsoluteStartPoints());
                        Link otherEdge = otherEdgeData.getEdge();
                        Point relFrom = new Point(otherEdgeData.getRelativeStart());
                        Point from = otherEdge.getFrom().getVertex().getPosition();
                        relFrom.move(relFrom.x + from.x, relFrom.y + from.y);
                        points.add(relFrom);

                        Point relTo = new Point(otherEdgeData.getRelativeEnd());
                        Point to = otherEdge.getTo().getVertex().getPosition();
                        relTo.move(relTo.x + to.x, relTo.y + to.y);
                        assert from != null;
                        assert to != null;
                        points.add(relTo);
                        points.addAll(otherEdgeData.getAbsoluteEndPoints());
                        e.getData().getEdge().setControlPoints(points);
                    }
                }
            }
        }

        assignRealTiming.stop();

        initTiming.print();
        removeCyclesTiming.print();
        reversedEdgesTiming.print();
        assignLayersTiming.print();
        dummyNodesTiming.print();
        crossingReductionTiming.print();
        assignCoordinatesTiming.print();
        assignRealTiming.print();
        rootVertexTiming.print();
        createEdgesTiming.print();
        optimizeMedianTiming.print();
    }

    public boolean onOneLine(Point p1, Point p2, Point p3) {
        int xoff1 = p1.x - p2.x;
        int yoff1 = p1.y - p2.y;
        int xoff2 = p3.x - p2.x;
        int yoff2 = p3.y - p2.x;

        return (xoff1 * yoff2 - yoff1 * xoff2 == 0);
    }

    private void assignToRealObjects(int layerSizes[], Node<NodeData, EdgeData> cur, List<Point> points) {
        assert cur.getData().isDummy();

        ArrayList<Point> otherPoints = new ArrayList<Point>(points);

        int size = layerSizes[cur.getData().getLayer()];
        otherPoints.add(new Point(cur.getData().getX(), cur.getData().getY() - size / 2));
        if (otherPoints.size() >= 3 && onOneLine(otherPoints.get(otherPoints.size() - 1), otherPoints.get(otherPoints.size() - 2), otherPoints.get(otherPoints.size() - 3))) {
            otherPoints.remove(otherPoints.size() - 2);
        }
        otherPoints.add(new Point(cur.getData().getX(), cur.getData().getY() + size / 2));
        if (otherPoints.size() >= 3 && onOneLine(otherPoints.get(otherPoints.size() - 1), otherPoints.get(otherPoints.size() - 2), otherPoints.get(otherPoints.size() - 3))) {
            otherPoints.remove(otherPoints.size() - 2);
        }

        for (int i = 0; i < cur.getOutEdges().size(); i++) {
            Node<NodeData, EdgeData> otherSucc = cur.getOutEdges().get(i).getDest();

            if (otherSucc.getData().isDummy()) {
                assignToRealObjects(layerSizes, otherSucc, otherPoints);
            } else {
                EdgeData otherEdgeData = cur.getOutEdges().get(i).getData();
                Link otherEdge = otherEdgeData.getEdge();

                List<Point> middlePoints = new ArrayList<Point>(otherPoints);
                if (cur.getOutEdges().get(i).getData().isReversed()) {
                    Collections.reverse(middlePoints);
                }

                ArrayList<Point> copy = new ArrayList<Point>();
                Point relFrom = new Point(otherEdgeData.getRelativeStart());
                Point from = otherEdge.getFrom().getVertex().getPosition();
                //int moveUp = (size - otherEdge.getFrom().getVertex().getSize().height) / 2;
                relFrom.move(relFrom.x + from.x, relFrom.y + from.y);
                copy.addAll(otherEdgeData.getAbsoluteStartPoints());
                copy.add(relFrom);
                copy.addAll(middlePoints);

                Point relTo = new Point(otherEdgeData.getRelativeEnd());
                Point to = otherEdge.getTo().getVertex().getPosition();
                relTo.move(relTo.x + to.x, relTo.y + to.y);
                copy.add(relTo);

                copy.addAll(otherEdgeData.getAbsoluteEndPoints());


                otherEdge.setControlPoints(copy);
            }
        }
    }

    private boolean checkDummyNodes() {
        for (Edge<NodeData, EdgeData> e : graph.getEdges()) {
            if (e.getSource().getData().getLayer() != e.getDest().getData().getLayer() - 1) {
                return false;
            }
        }

        return true;
    }

    private void insertDummyNodes(ArrayList<Node<NodeData, EdgeData>> layers[]) {

        int sum = 0;
        List<Node<NodeData, EdgeData>> nodes = new ArrayList<Node<NodeData, EdgeData>>(graph.getNodes());
        int edgeCount = 0;
        int innerMostLoop = 0;

        for (Node<NodeData, EdgeData> n : nodes) {
            List<Edge<NodeData, EdgeData>> edges = new ArrayList<Edge<NodeData, EdgeData>>(n.getOutEdges());
            for (Edge<NodeData, EdgeData> e : edges) {

                edgeCount++;
                Link edge = e.getData().getEdge();
                Node<NodeData, EdgeData> destNode = e.getDest();
                Node<NodeData, EdgeData> lastNode = n;
                Edge<NodeData, EdgeData> lastEdge = e;

                boolean searchForNode = (combine != Combine.NONE);
                for (int i = n.getData().getLayer() + 1; i < destNode.getData().getLayer(); i++) {

                    Node<NodeData, EdgeData> foundNode = null;
                    if (searchForNode) {
                        for (Node<NodeData, EdgeData> sameLayerNode : layers[i]) {
                            innerMostLoop++;

                            if (combine == Combine.SAME_OUTPUTS) {
                                if (sameLayerNode.getData().isDummy() && sameLayerNode.getData().getEdge().getFrom() == edge.getFrom()) {
                                    foundNode = sameLayerNode;
                                    break;
                                }
                            } else if (combine == Combine.SAME_INPUTS) {
                                if (sameLayerNode.getData().isDummy() && sameLayerNode.getData().getEdge().getTo() == edge.getTo()) {
                                    foundNode = sameLayerNode;
                                    break;
                                }
                            }
                        }
                    }

                    if (foundNode == null) {
                        searchForNode = false;
                        NodeData intermediateData = new NodeData(edge);
                        Node<NodeData, EdgeData> curNode = graph.createNode(intermediateData, null);
                        curNode.getData().setLayer(i);
                        layers[i].add(0, curNode);
                        sum++;
                        lastEdge.remove();
                        graph.createEdge(lastNode, curNode, e.getData(), null);
                        assert lastNode.getData().getLayer() == curNode.getData().getLayer() - 1;
                        lastEdge = graph.createEdge(curNode, destNode, e.getData(), null);
                        lastNode = curNode;
                    } else {
                        lastEdge.remove();
                        lastEdge = graph.createEdge(foundNode, destNode, e.getData(), null);
                        lastNode = foundNode;
                    }

                }
            }
        }

        if (TRACE) {
            System.out.println("Number of edges: " + edgeCount);
        }
        if (TRACE) {
            System.out.println("Dummy nodes inserted: " + sum);
        }
    }

    private void assignLayerCoordinates(ArrayList<Node<NodeData, EdgeData>> layers[], int layerSizes[]) {
        int cur = 0;
        for (int i = 0; i < layers.length; i++) {
            int maxHeight = 0;
            for (Node<NodeData, EdgeData> n : layers[i]) {
                maxHeight = Math.max(maxHeight, n.getData().getHeight());
            }

            layerSizes[i] = maxHeight;
            for (Node<NodeData, EdgeData> n : layers[i]) {
                int curCoordinate = cur + (maxHeight - n.getData().getHeight()) / 2;
                n.getData().setLayerCoordinate(curCoordinate);
            }
            cur += maxHeight + layerOffset;

        }
    }

    private void assignCoordinates(ArrayList<Node<NodeData, EdgeData>> layers[]) {

        // TODO: change this
        for (int i = 0; i < layers.length; i++) {
            ArrayList<Node<NodeData, EdgeData>> curArray = layers[i];
            int curY = 0;
            for (Node<NodeData, EdgeData> n : curArray) {

                n.getData().setCoordinate(curY);
                if (!n.getData().isDummy()) {
                    curY += n.getData().getWidth();
                }
                curY += OFFSET;

            }
        }

        int curSol = evaluateSolution();
        if (TRACE) {
            System.out.println("First coordinate solution found: " + curSol);
        }

        // Sort to correct order
        for (int i = 0; i < layers.length; i++) {
            Collections.sort(layers[i], new Comparator<Node<NodeData, EdgeData>>() {

                public int compare(Node<NodeData, EdgeData> o1, Node<NodeData, EdgeData> o2) {
                    if (o2.getData().isDummy()) {
                        return 1;
                    } else if (o1.getData().isDummy()) {
                        return -1;
                    }
                    return o2.getInEdges().size() + o2.getOutEdges().size() - o1.getInEdges().size() - o1.getOutEdges().size();
                }
            });
        }


        optimizeMedianTiming.start();
        for (int i = 0; i < 2; i++) {
            optimizeMedian(layers);
            curSol = evaluateSolution();
            if (TRACE) {
                System.out.println("Current coordinate solution found: " + curSol);
            }
        }
        optimizeMedianTiming.stop();
        normalizeCoordinate();

    }

    private void normalizeCoordinate() {

        int min = Integer.MAX_VALUE;
        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            min = Math.min(min, n.getData().getCoordinate());
        }

        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            n.getData().setCoordinate(n.getData().getCoordinate() - min);
        }

    }

    private void optimizeMedian(ArrayList<Node<NodeData, EdgeData>> layers[]) {

        // Downsweep
        for (int i = 1; i < layers.length; i++) {

            ArrayList<Node<NodeData, EdgeData>> processingList = layers[i];
            ArrayList<Node<NodeData, EdgeData>> alreadyAssigned = new ArrayList<Node<NodeData, EdgeData>>();
            for (Node<NodeData, EdgeData> n : processingList) {


                ArrayList<Node<NodeData, EdgeData>> preds = new ArrayList<Node<NodeData, EdgeData>>(n.getPredecessors());
                int pos = n.getData().getCoordinate();
                if (preds.size() > 0) {

                    Collections.sort(preds, new Comparator<Node<NodeData, EdgeData>>() {

                        public int compare(Node<NodeData, EdgeData> o1, Node<NodeData, EdgeData> o2) {
                            return o1.getData().getCoordinate() - o2.getData().getCoordinate();
                        }
                    });

                    if (preds.size() % 2 == 0) {
                        assert preds.size() >= 2;
                        pos = (preds.get(preds.size() / 2).getData().getCoordinate() - calcRelativeCoordinate(preds.get(preds.size() / 2), n) + preds.get(preds.size() / 2 - 1).getData().getCoordinate() - calcRelativeCoordinate(preds.get(preds.size() / 2 - 1), n)) / 2;
                    } else {
                        assert preds.size() >= 1;
                        pos = preds.get(preds.size() / 2).getData().getCoordinate() - calcRelativeCoordinate(preds.get(preds.size() / 2), n);
                    }
                }

                tryAdding(alreadyAssigned, n, pos);
            }
        }
        // Upsweep
        for (int i = layers.length - 2; i >= 0; i--) {
            ArrayList<Node<NodeData, EdgeData>> processingList = layers[i];
            ArrayList<Node<NodeData, EdgeData>> alreadyAssigned = new ArrayList<Node<NodeData, EdgeData>>();
            for (Node<NodeData, EdgeData> n : processingList) {

                ArrayList<Node<NodeData, EdgeData>> succs = new ArrayList<Node<NodeData, EdgeData>>(n.getSuccessors());
                int pos = n.getData().getCoordinate();
                if (succs.size() > 0) {

                    Collections.sort(succs, new Comparator<Node<NodeData, EdgeData>>() {

                        public int compare(Node<NodeData, EdgeData> o1, Node<NodeData, EdgeData> o2) {
                            return o1.getData().getCoordinate() - o2.getData().getCoordinate();
                        }
                    });

                    if (succs.size() % 2 == 0) {
                        assert succs.size() >= 2;
                        pos = (succs.get(succs.size() / 2).getData().getCoordinate() - calcRelativeCoordinate(n, succs.get(succs.size() / 2)) + succs.get(succs.size() / 2 - 1).getData().getCoordinate() - calcRelativeCoordinate(n, succs.get(succs.size() / 2 - 1))) / 2;
                    } else {
                        assert succs.size() >= 1;
                        pos = succs.get(succs.size() / 2).getData().getCoordinate() - calcRelativeCoordinate(n, succs.get(succs.size() / 2));
                    }
                }

                tryAdding(alreadyAssigned, n, pos);
            }
        }
    }

    private int median(ArrayList<Integer> arr) {
        assert arr.size() > 0;
        Collections.sort(arr);
        if (arr.size() % 2 == 0) {
            return (arr.get(arr.size() / 2) + arr.get(arr.size() / 2 - 1)) / 2;
        } else {
            return arr.get(arr.size() / 2);
        }
    }

    private int calcRelativeCoordinate(Node<NodeData, EdgeData> n, Node<NodeData, EdgeData> succ) {

        if (n.getData().isDummy() && succ.getData().isDummy()) {
            return 0;
        }

        int pos = 0;
        int pos2 = 0;
        ArrayList<Integer> coords2 = new ArrayList<Integer>();
        ArrayList<Integer> coords = new ArrayList<Integer>();
        /*if(!n.getData().isDummy())*/ {
            for (Edge<NodeData, EdgeData> e : n.getOutEdges()) {

                //System.out.println("reversed: " + e.getData().isReversed());
                if (e.getDest() == succ) {

                    if (e.getData().isReversed()) {
                        if (!n.getData().isDummy()) {
                            coords.add(e.getData().getRelativeEnd().x);
                        }

                        if (!succ.getData().isDummy()) {
                            coords2.add(e.getData().getRelativeStart().x);
                        }
                    } else {
                        if (!n.getData().isDummy()) {
                            coords.add(e.getData().getRelativeStart().x);
                        }

                        if (!succ.getData().isDummy()) {
                            coords2.add(e.getData().getRelativeEnd().x);
                        }
                    }
                }
            }

            // assert coords.size() > 0;
            if (!n.getData().isDummy()) {
                pos = median(coords);
            }

            if (!succ.getData().isDummy()) {
                pos2 = median(coords2);
            }
        }
        //System.out.println("coords=" + coords);
        //System.out.println("coords2=" + coords2);

        return pos - pos2;
    }

    private boolean intersect(int v1, int w1, int v2, int w2) {
        if (v1 >= v2 && v1 < v2 + w2) {
            return true;
        }
        if (v1 + w1 > v2 && v1 + w1 < v2 + w2) {
            return true;
        }
        if (v1 < v2 && v1 + w1 > v2) {
            return true;
        }
        return false;
    }

    private boolean intersect(Node<NodeData, EdgeData> n1, Node<NodeData, EdgeData> n2) {
        return intersect(n1.getData().getCoordinate(), n1.getData().getWidth() + OFFSET, n2.getData().getCoordinate(), n2.getData().getWidth() + OFFSET);
    }

    private void tryAdding(List<Node<NodeData, EdgeData>> alreadyAssigned, Node<NodeData, EdgeData> node, int pos) {

        boolean doesIntersect = false;
        node.getData().setCoordinate(pos);
        for (Node<NodeData, EdgeData> n : alreadyAssigned) {
            if (n.getData().getCoordinate() + n.getData().getWidth() < pos) {
                break;
            } else if (intersect(node, n)) {
                doesIntersect = true;
                break;
            }

        }

        if (!doesIntersect) {

            // Everything fine, just place the node
            int z = 0;
            for (Node<NodeData, EdgeData> n : alreadyAssigned) {
                if (pos > n.getData().getCoordinate()) {
                    break;
                }
                z++;
            }

            if (z == -1) {
                z = alreadyAssigned.size();
            }


            if (ASSERT) {
                assert !findOverlap(alreadyAssigned, node);
            }
            alreadyAssigned.add(z, node);

        } else {

            assert alreadyAssigned.size() > 0;

            // Search for alternative location
            int minOffset = Integer.MAX_VALUE;
            int minIndex = -1;
            int minPos = 0;
            int w = node.getData().getWidth() + OFFSET;

            // Try top-most
            minIndex = 0;
            minPos = alreadyAssigned.get(0).getData().getCoordinate() + alreadyAssigned.get(0).getData().getWidth() + OFFSET;
            minOffset = Math.abs(minPos - pos);

            // Try bottom-most
            Node<NodeData, EdgeData> lastNode = alreadyAssigned.get(alreadyAssigned.size() - 1);
            int lastPos = lastNode.getData().getCoordinate() - w;
            int lastOffset = Math.abs(lastPos - pos);
            if (lastOffset < minOffset) {
                minPos = lastPos;
                minOffset = lastOffset;
                minIndex = alreadyAssigned.size();
            }

            // Try between
            for (int i = 0; i < alreadyAssigned.size() - 1; i++) {
                Node<NodeData, EdgeData> curNode = alreadyAssigned.get(i);
                Node<NodeData, EdgeData> nextNode = alreadyAssigned.get(i + 1);

                int start = nextNode.getData().getCoordinate() + nextNode.getData().getWidth() + OFFSET;
                int end = curNode.getData().getCoordinate() - OFFSET;

                int bestPoss = end - node.getData().getWidth();
                if (bestPoss < pos && pos - bestPoss > minOffset) {
                    // No better solution possible => break
                    break;
                }

                if (end - start >= node.getData().getWidth()) {
                    // Node could fit here
                    int cand1 = start;
                    int cand2 = end - node.getData().getWidth();
                    int off1 = Math.abs(cand1 - pos);
                    int off2 = Math.abs(cand2 - pos);
                    if (off1 < minOffset) {
                        minPos = cand1;
                        minOffset = off1;
                        minIndex = i + 1;
                    }

                    if (off2 < minOffset) {
                        minPos = cand2;
                        minOffset = off2;
                        minIndex = i + 1;
                    }
                }
            }

            assert minIndex != -1;
            node.getData().setCoordinate(minPos);
            if (ASSERT) {
                assert !findOverlap(alreadyAssigned, node);
            }
            alreadyAssigned.add(minIndex, node);
        }

    }

    private boolean findOverlap(List<Node<NodeData, EdgeData>> nodes, Node<NodeData, EdgeData> node) {

        for (Node<NodeData, EdgeData> n1 : nodes) {
            if (intersect(n1, node)) {
                return true;
            }
        }

        return false;
    }

    private int evaluateSolution() {

        int sum = 0;
        for (Edge<NodeData, EdgeData> e : graph.getEdges()) {
            Node<NodeData, EdgeData> source = e.getSource();
            Node<NodeData, EdgeData> dest = e.getDest();
            int offset = 0;
            offset = Math.abs(source.getData().getCoordinate() - dest.getData().getCoordinate());
            sum += offset;
        }

        return sum;
    }

    private void crossingReduction(ArrayList<Node<NodeData, EdgeData>> layers[]) {

        for (int i = 0; i < layers.length - 1; i++) {

            ArrayList<Node<NodeData, EdgeData>> curNodes = layers[i];
            ArrayList<Node<NodeData, EdgeData>> nextNodes = layers[i + 1];
            for (Node<NodeData, EdgeData> n : curNodes) {
                for (Node<NodeData, EdgeData> succ : n.getSuccessors()) {
                    if (ASSERT) {
                        assert nextNodes.contains(succ);
                    }
                    nextNodes.remove(succ);
                    nextNodes.add(succ);
                }
            }

        }

    }

    private void removeCycles(Set<Node<NodeData, EdgeData>> rootNodes) {
        final List<Edge<NodeData, EdgeData>> reversedEdges = new ArrayList<Edge<NodeData, EdgeData>>();


        int removedCount = 0;
        int reversedCount = 0;

        Graph.DFSTraversalVisitor visitor = graph.new DFSTraversalVisitor() {

            @Override
            public boolean visitEdge(Edge<NodeData, EdgeData> e, boolean backEdge) {
                if (backEdge) {
                    if (ASSERT) {
                        assert !reversedEdges.contains(e);
                    }
                    reversedEdges.add(e);
                    e.getData().setReversed(!e.getData().isReversed());
                }

                return e.getData().isImportant();
            }
        };
        Set<Node<NodeData, EdgeData>> nodes = new HashSet<Node<NodeData, EdgeData>>();
        nodes.addAll(rootNodes);

        assert nodes.size() > 0;

        this.graph.traverseDFS(nodes, visitor);

        for (Edge<NodeData, EdgeData> e : reversedEdges) {
            if (e.isSelfLoop()) {
                e.remove();
                removedCount++;
            } else {
                e.reverse();
                reversedCount++;
            }
        }
    }

    private boolean checkRemoveCycles() {
        return !graph.hasCycles();
    }
    // Only used by assignLayers
    private int maxLayerTemp;

    private int assignLayers(Set<Node<NodeData, EdgeData>> rootNodes, Set<? extends Vertex> firstLayerHints,
            Set<? extends Vertex> lastLayerHints) {
        this.maxLayerTemp = -1;
        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            n.getData().setLayer(-1);
        }

        Graph.BFSTraversalVisitor traverser = graph.new BFSTraversalVisitor() {

            @Override
            public void visitNode(Node<NodeData, EdgeData> n, int depth) {
                if (depth > n.getData().getLayer()) {
                    n.getData().setLayer(depth);
                    maxLayerTemp = Math.max(maxLayerTemp, depth);
                }
            }
        };

        for (Node<NodeData, EdgeData> n : rootNodes) {
            if (n.getData().getLayer() == -1) {
                this.graph.traverseBFS(n, traverser, true);
            }
        }

        for (Vertex v : firstLayerHints) {
            assert nodeMap.containsKey(v);
            nodeMap.get(v).getData().setLayer(0);
        }

        for (Vertex v : lastLayerHints) {
            assert nodeMap.containsKey(v);
            nodeMap.get(v).getData().setLayer(maxLayerTemp);
        }

        return maxLayerTemp;
    }

    private boolean checkAssignLayers() {

        for (Edge<NodeData, EdgeData> e : graph.getEdges()) {
            Node<NodeData, EdgeData> source = e.getSource();
            Node<NodeData, EdgeData> dest = e.getDest();


            if (source.getData().getLayer() >= dest.getData().getLayer()) {
                return false;
            }
        }
        int maxLayer = 0;
        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            assert n.getData().getLayer() >= 0;
            if (n.getData().getLayer() > maxLayer) {
                maxLayer = n.getData().getLayer();
            }
        }

        int countPerLayer[] = new int[maxLayer + 1];
        for (Node<NodeData, EdgeData> n : graph.getNodes()) {
            countPerLayer[n.getData().getLayer()]++;
        }

        if (TRACE) {
            System.out.println("Number of layers: " + maxLayer);
        }
        return true;
    }
}
