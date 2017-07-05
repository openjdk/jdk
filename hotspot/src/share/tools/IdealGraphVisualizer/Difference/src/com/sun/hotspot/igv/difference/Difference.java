/*
 * Copyright (c) 1998, 2008, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */
package com.sun.hotspot.igv.difference;

import com.sun.hotspot.igv.data.Group;
import com.sun.hotspot.igv.data.InputEdge;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Property;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Difference {

    public static final String PROPERTY_STATE = "state";
    public static final String VALUE_NEW = "new";
    public static final String VALUE_CHANGED = "changed";
    public static final String VALUE_SAME = "same";
    public static final String VALUE_DELETED = "deleted";
    public static final String OLD_PREFIX = "OLD_";
    public static final String MAIN_PROPERTY = "name";
    public static final double LIMIT = 100.0;
    public static final String[] IGNORE_PROPERTIES = new String[]{"idx", "debug_idx"};

    public static InputGraph createDiffGraph(InputGraph a, InputGraph b) {
        if (a.getGroup() == b.getGroup()) {
            return createDiffSameGroup(a, b);
        } else {
            return createDiff(a, b);
        }
    }

    private static InputGraph createDiffSameGroup(InputGraph a, InputGraph b) {
        Map<Integer, InputNode> keyMapB = new HashMap<Integer, InputNode>();
        for (InputNode n : b.getNodes()) {
            Integer key = n.getId();
            assert !keyMapB.containsKey(key);
            keyMapB.put(key, n);
        }

        Set<Pair> pairs = new HashSet<Pair>();

        for (InputNode n : a.getNodes()) {
            Integer key = n.getId();


            if (keyMapB.containsKey(key)) {
                InputNode nB = keyMapB.get(key);
                pairs.add(new Pair(n, nB));
            }
        }

        return createDiff(a, b, pairs);
    }

    private static InputGraph createDiff(InputGraph a, InputGraph b, Set<Pair> pairs) {
        Group g = new Group();
        g.setMethod(a.getGroup().getMethod());
        g.setAssembly(a.getGroup().getAssembly());
        g.getProperties().setProperty("name", "Difference");
        InputGraph graph = new InputGraph(g, null);
        graph.setName(a.getName() + ", " + b.getName());
        graph.setIsDifferenceGraph(true);

        Set<InputNode> nodesA = new HashSet<InputNode>(a.getNodes());
        Set<InputNode> nodesB = new HashSet<InputNode>(b.getNodes());

        Map<InputNode, InputNode> inputNodeMap = new HashMap<InputNode, InputNode>();
        for (Pair p : pairs) {
            InputNode n = p.getN1();
            assert nodesA.contains(n);
            InputNode nB = p.getN2();
            assert nodesB.contains(nB);

            nodesA.remove(n);
            nodesB.remove(nB);
            InputNode n2 = new InputNode(n);
            inputNodeMap.put(n, n2);
            inputNodeMap.put(nB, n2);
            graph.addNode(n2);
            markAsChanged(n2, n, nB);
        }

        for (InputNode n : nodesA) {
            InputNode n2 = new InputNode(n);
            graph.addNode(n2);
            markAsNew(n2);
            inputNodeMap.put(n, n2);
        }

        for (InputNode n : nodesB) {
            InputNode n2 = new InputNode(n);
            n2.setId(-n2.getId());
            graph.addNode(n2);
            markAsDeleted(n2);
            inputNodeMap.put(n, n2);
        }

        Collection<InputEdge> edgesA = a.getEdges();
        Collection<InputEdge> edgesB = b.getEdges();

        Set<InputEdge> newEdges = new HashSet<InputEdge>();

        for (InputEdge e : edgesA) {
            int from = e.getFrom();
            int to = e.getTo();
            InputNode nodeFrom = inputNodeMap.get(a.getNode(from));
            InputNode nodeTo = inputNodeMap.get(a.getNode(to));
            char index = e.getToIndex();

            InputEdge newEdge = new InputEdge(index, nodeFrom.getId(), nodeTo.getId());
            if (!newEdges.contains(newEdge)) {
                markAsNew(newEdge);
                newEdges.add(newEdge);
                graph.addEdge(newEdge);
            }
        }

        for (InputEdge e : edgesB) {
            int from = e.getFrom();
            int to = e.getTo();
            InputNode nodeFrom = inputNodeMap.get(b.getNode(from));
            InputNode nodeTo = inputNodeMap.get(b.getNode(to));
            char index = e.getToIndex();

            InputEdge newEdge = new InputEdge(index, nodeFrom.getId(), nodeTo.getId());
            if (!newEdges.contains(newEdge)) {
                markAsDeleted(newEdge);
                newEdges.add(newEdge);
                graph.addEdge(newEdge);
            } else {
                newEdges.remove(newEdge);
                graph.removeEdge(newEdge);
                markAsSame(newEdge);
                newEdges.add(newEdge);
                graph.addEdge(newEdge);
            }
        }

        g.addGraph(graph);
        return graph;
    }

    private static class Pair {

        private InputNode n1;
        private InputNode n2;

        public Pair(InputNode n1, InputNode n2) {
            this.n1 = n1;
            this.n2 = n2;
        }

        public double getValue() {

            double result = 0.0;
            for (Property p : n1.getProperties()) {
                double faktor = 1.0;
                for (String forbidden : IGNORE_PROPERTIES) {
                    if (p.getName().equals(forbidden)) {
                        faktor = 0.1;
                        break;
                    }
                }
                String p2 = n2.getProperties().get(p.getName());
                result += evaluate(p.getValue(), p2) * faktor;
            }

            return result;
        }

        private double evaluate(String p, String p2) {
            if (p2 == null) {
                return 1.0;
            }
            if (p.equals(p2)) {
                return 0.0;
            } else {
                return (double) (Math.abs(p.length() - p2.length())) / p.length() + 0.5;
            }
        }

        public InputNode getN1() {
            return n1;
        }

        public InputNode getN2() {
            return n2;
        }
    }

    private static InputGraph createDiff(InputGraph a, InputGraph b) {

        Set<InputNode> matched = new HashSet<InputNode>();

        Set<Pair> pairs = new HashSet<Pair>();
        for (InputNode n : a.getNodes()) {
            String s = n.getProperties().get(MAIN_PROPERTY);
            if (s == null) {
                s = "";
            }
            for (InputNode n2 : b.getNodes()) {
                String s2 = n2.getProperties().get(MAIN_PROPERTY);
                if (s2 == null) {
                    s2 = "";
                }

                if (s.equals(s2)) {
                    Pair p = new Pair(n, n2);
                    pairs.add(p);
                }
            }
        }

        Set<Pair> selectedPairs = new HashSet<Pair>();
        while (pairs.size() > 0) {

            double min = Double.MAX_VALUE;
            Pair minPair = null;
            for (Pair p : pairs) {
                double cur = p.getValue();
                if (cur < min) {
                    minPair = p;
                    min = cur;
                }
            }

            if (min > LIMIT) {
                break;
            } else {
                selectedPairs.add(minPair);

                Set<Pair> toRemove = new HashSet<Pair>();
                for (Pair p : pairs) {
                    if (p.getN1() == minPair.getN1() || p.getN2() == minPair.getN2()) {
                        toRemove.add(p);
                    }
                }
                pairs.removeAll(toRemove);
            }
        }

        return createDiff(a, b, selectedPairs);
    }

    private static void markAsNew(InputEdge e) {
        e.setState(InputEdge.State.NEW);
    }

    private static void markAsDeleted(InputEdge e) {
        e.setState(InputEdge.State.DELETED);

    }

    private static void markAsSame(InputEdge e) {
        e.setState(InputEdge.State.SAME);
    }

    private static void markAsChanged(InputNode n, InputNode firstNode, InputNode otherNode) {

        boolean difference = false;
        for (Property p : otherNode.getProperties()) {
            String s = firstNode.getProperties().get(p.getName());
            if (!p.getValue().equals(s)) {
                difference = true;
                n.getProperties().setProperty(OLD_PREFIX + p.getName(), p.getValue());
            }
        }

        for (Property p : firstNode.getProperties()) {
            String s = otherNode.getProperties().get(p.getName());
            if (s == null && p.getValue().length() > 0) {
                difference = true;
                n.getProperties().setProperty(OLD_PREFIX + p.getName(), "");
            }
        }

        if (difference) {
            n.getProperties().setProperty(PROPERTY_STATE, VALUE_CHANGED);
        } else {
            n.getProperties().setProperty(PROPERTY_STATE, VALUE_SAME);
        }
    }

    private static void markAsDeleted(InputNode n) {
        n.getProperties().setProperty(PROPERTY_STATE, VALUE_DELETED);
    }

    private static void markAsNew(InputNode n) {
        n.getProperties().setProperty(PROPERTY_STATE, VALUE_NEW);
    }
}
