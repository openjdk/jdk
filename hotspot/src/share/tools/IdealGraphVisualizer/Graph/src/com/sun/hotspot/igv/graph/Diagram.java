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
package com.sun.hotspot.igv.graph;

import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputEdge;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Properties;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Diagram {

    private List<Figure> figures;
    private Map<InputBlock, Block> blocks;
    private InputGraph graph;
    private int curId;
    private String nodeText;
    private Font font;

    public Font getFont() {
        return font;
    }

    private Diagram() {
        figures = new ArrayList<Figure>();
        blocks = new HashMap<InputBlock, Block>();
        this.nodeText = "";
        this.font = new Font("Serif", Font.PLAIN, 14);
    }

    public Block getBlock(InputBlock b) {
        return blocks.get(b);
    }

    public String getNodeText() {
        return nodeText;
    }

    public void schedule(Collection<InputBlock> newBlocks) {
        graph.schedule(newBlocks);
        updateBlocks();
    }

    private void updateBlocks() {
        blocks.clear();
        for (InputBlock b : graph.getBlocks()) {
            Block curBlock = new Block(b, this);
            blocks.put(b, curBlock);
        }
    }

    public Diagram getNext() {
        return Diagram.createDiagram(graph.getNext(), nodeText);
    }

    public Collection<Block> getBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public Diagram getPrev() {
        return Diagram.createDiagram(graph.getPrev(), nodeText);
    }

    public List<Figure> getFigures() {
        return Collections.unmodifiableList(figures);
    }

    public Figure createFigure() {
        Figure f = new Figure(this, curId);
        curId++;
        this.figures.add(f);
        return f;
    }

    public Connection createConnection(InputSlot inputSlot, OutputSlot outputSlot) {
        assert inputSlot.getFigure().getDiagram() == this;
        assert outputSlot.getFigure().getDiagram() == this;
        return new Connection(inputSlot, outputSlot);
    }

    public static Diagram createDiagram(InputGraph graph, String nodeText) {
        if (graph == null) {
            return null;
        }

        Diagram d = new Diagram();
        d.graph = graph;
        d.nodeText = nodeText;

        d.updateBlocks();

        Collection<InputNode> nodes = graph.getNodes();
        HashMap<Integer, Figure> figureHash = new HashMap<Integer, Figure>();
        for (InputNode n : nodes) {
            Figure f = d.createFigure();
            f.getSource().addSourceNode(n);
            f.getProperties().add(n.getProperties());
            figureHash.put(n.getId(), f);
        }

        for (InputEdge e : graph.getEdges()) {

            int from = e.getFrom();
            int to = e.getTo();
            Figure fromFigure = figureHash.get(from);
            Figure toFigure = figureHash.get(to);
            assert fromFigure != null && toFigure != null;

            int toIndex = e.getToIndex();

            while (fromFigure.getOutputSlots().size() <= 0) {
                fromFigure.createOutputSlot();
            }

            OutputSlot outputSlot = fromFigure.getOutputSlots().get(0);

            while (toFigure.getInputSlots().size() <= toIndex) {
                toFigure.createInputSlot();
            }

            InputSlot inputSlot = toFigure.getInputSlots().get(toIndex);

            Connection c = d.createConnection(inputSlot, outputSlot);

            if (e.getState() == InputEdge.State.NEW) {
                c.setStyle(Connection.ConnectionStyle.BOLD);
            } else if (e.getState() == InputEdge.State.DELETED) {
                c.setStyle(Connection.ConnectionStyle.DASHED);
            }
        }


        return d;
    }

    public void removeAllFigures(Set<Figure> figuresToRemove) {
        for (Figure f : figuresToRemove) {
            freeFigure(f);
        }

        ArrayList<Figure> newFigures = new ArrayList<Figure>();
        for (Figure f : this.figures) {
            if (!figuresToRemove.contains(f)) {
                newFigures.add(f);
            }
        }
        figures = newFigures;
    }

    private void freeFigure(Figure succ) {

        List<InputSlot> inputSlots = new ArrayList<InputSlot>(succ.getInputSlots());
        for (InputSlot s : inputSlots) {
            succ.removeInputSlot(s);
        }

        List<OutputSlot> outputSlots = new ArrayList<OutputSlot>(succ.getOutputSlots());
        for (OutputSlot s : outputSlots) {
            succ.removeOutputSlot(s);
        }

        assert succ.getInputSlots().size() == 0;
        assert succ.getOutputSlots().size() == 0;
        assert succ.getPredecessors().size() == 0;
        assert succ.getSuccessors().size() == 0;

    }

    public void removeFigure(Figure succ) {

        assert this.figures.contains(succ);
        freeFigure(succ);
        this.figures.remove(succ);
    }

    public String getName() {
        return graph.getName();
    }

    public InputGraph getGraph() {
        return graph;
    }

    public Set<Connection> getConnections() {

        Set<Connection> connections = new HashSet<Connection>();
        for (Figure f : figures) {

            for (InputSlot s : f.getInputSlots()) {
                connections.addAll(s.getConnections());
            }
        }

        return connections;
    }

    public Figure getRootFigure() {
        Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<Figure>(figures);
        Figure root = selector.selectSingle("name", "Root");
        if (root == null) {
            root = selector.selectSingle("name", "Start");
        }
        if (root == null) {
            List<Figure> rootFigures = getRootFigures();
            if (rootFigures.size() > 0) {
                root = rootFigures.get(0);
            } else if (figures.size() > 0) {
                root = figures.get(0);
            }
        }

        return root;
    }

    public void printStatistics() {
        System.out.println("=============================================================");
        System.out.println("Diagram statistics");

        List<Figure> tmpFigures = getFigures();
        Set<Connection> connections = getConnections();

        System.out.println("Number of figures: " + tmpFigures.size());
        System.out.println("Number of connections: " + connections.size());

        List<Figure> figuresSorted = new ArrayList<Figure>(tmpFigures);
        Collections.sort(figuresSorted, new Comparator<Figure>() {

            public int compare(Figure a, Figure b) {
                return b.getPredecessors().size() + b.getSuccessors().size() - a.getPredecessors().size() - a.getSuccessors().size();
            }
        });

        final int COUNT = 10;
        int z = 0;
        for (Figure f : figuresSorted) {

            z++;
            int sum = f.getPredecessors().size() + f.getSuccessors().size();
            System.out.println("#" + z + ": " + f + ", predCount=" + f.getPredecessors().size() + " succCount=" + f.getSuccessors().size());
            if (sum < COUNT) {
                break;
            }

        }

        System.out.println("=============================================================");
    }

    public List<Figure> getRootFigures() {
        ArrayList<Figure> rootFigures = new ArrayList<Figure>();
        for (Figure f : figures) {
            if (f.getPredecessors().size() == 0) {
                rootFigures.add(f);
            }
        }
        return rootFigures;
    }
}
