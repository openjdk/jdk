/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.*;
import com.sun.hotspot.igv.data.Properties;
import java.awt.Font;
import java.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Diagram {

    private List<Figure> figures;
    private final Map<InputBlock, Block> blocks;
    private final String nodeText;
    private final String shortNodeText;
    private final String tinyNodeText;
    public static final Font FONT = new Font("Arial", Font.PLAIN, 12);
    public static final Font SLOT_FONT = new Font("Arial", Font.PLAIN, 10);
    public static final Font BOLD_FONT = FONT.deriveFont(Font.BOLD);

    // Whether widgets derived from this diagram should be adapted for the
    // control-flow graph view.
    private boolean cfg;
    private final Set<BlockConnection> blockConnections;

    public boolean isCFG() {
        return cfg;
    }

    public void setCFG(boolean cfg) {
        this.cfg = cfg;
    }

    public Diagram(InputGraph graph, String nodeText, String shortNodeText,
                   String tinyNodeText) {
        assert graph != null;

        this.nodeText = nodeText;
        this.shortNodeText = shortNodeText;
        this.tinyNodeText = tinyNodeText;
        this.figures = new ArrayList<>();
        this.blocks = new LinkedHashMap<>(8);
        this.blockConnections = new HashSet<>();
        this.cfg = false;
        int curId = 0;

        for (InputBlock b : graph.getBlocks()) {
            blocks.put(b, new Block(b, this));
        }

        Collection<InputNode> nodes = graph.getNodes();
        Hashtable<Integer, Figure> figureHash = new Hashtable<>();
        for (InputNode n : nodes) {
            Figure f = new Figure(this, curId, n);
            curId++;
            f.getProperties().add(n.getProperties());
            f.setBlock(blocks.get(graph.getBlock(n)));
            figureHash.put(n.getId(), f);
            this.figures.add(f);
        }

        for (InputEdge e : graph.getEdges()) {
            int from = e.getFrom();
            int to = e.getTo();
            Figure fromFigure = figureHash.get(from);
            Figure toFigure = figureHash.get(to);

            if(fromFigure == null || toFigure == null) continue;

            int fromIndex = e.getFromIndex();
            while (fromFigure.getOutputSlots().size() <= fromIndex) {
                fromFigure.createOutputSlot();
            }
            OutputSlot outputSlot = fromFigure.getOutputSlots().get(fromIndex);

            int toIndex = e.getToIndex();
            while (toFigure.getInputSlots().size() <= toIndex) {
                toFigure.createInputSlot();
            }
            InputSlot inputSlot = toFigure.getInputSlots().get(toIndex);

            FigureConnection c = createConnection(inputSlot, outputSlot, e.getLabel());

            if (e.getState() == InputEdge.State.NEW) {
                c.setStyle(Connection.ConnectionStyle.BOLD);
            } else if (e.getState() == InputEdge.State.DELETED) {
                c.setStyle(Connection.ConnectionStyle.DASHED);
            }
        }

        for (InputBlockEdge e : graph.getBlockEdges()) {
            Block p = getBlock(e.getFrom());
            Block s = getBlock(e.getTo());
            blockConnections.add(new BlockConnection(p, s, e.getLabel()));
        }
    }

    public Block getBlock(InputBlock b) {
        assert blocks.containsKey(b);
        return blocks.get(b);
    }

    public boolean hasBlock(InputBlock b) {
        return blocks.containsKey(b);
    }

    public String getNodeText() {
        return nodeText;
    }

    public String getShortNodeText() {
        return shortNodeText;
    }

    public String getTinyNodeText() {
        return tinyNodeText;
    }

    public Collection<Block> getBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public Collection<InputBlock> getInputBlocks() {
        return Collections.unmodifiableCollection(blocks.keySet());
    }

    public List<Figure> getFigures() {
        return Collections.unmodifiableList(figures);
    }

    public FigureConnection createConnection(InputSlot inputSlot, OutputSlot outputSlot, String label) {
        assert inputSlot.getFigure().getDiagram() == this;
        assert outputSlot.getFigure().getDiagram() == this;
        return new FigureConnection(inputSlot, outputSlot, label);
    }

    public void removeAllBlocks(Set<Block> blocksToRemove) {
        Set<Figure> figuresToRemove = new HashSet<>();
        for (Block b : blocksToRemove) {
            for (Figure f : getFigures()) {
                if (f.getBlock() == b) {
                    figuresToRemove.add(f);
                }
            }
        }
        removeAllFigures(figuresToRemove);
        for (Block b : blocksToRemove) {
            blocks.remove(b.getInputBlock());
        }
    }

    public void removeAllFigures(Set<Figure> figuresToRemove) {
        for (Figure f : figuresToRemove) {
            freeFigure(f);
        }

        ArrayList<Figure> newFigures = new ArrayList<>();
        for (Figure f : this.figures) {
            if (!figuresToRemove.contains(f)) {
                newFigures.add(f);
            }
        }
        figures = newFigures;
    }

    private void freeFigure(Figure succ) {

        List<InputSlot> inputSlots = new ArrayList<>(succ.getInputSlots());
        for (InputSlot s : inputSlots) {
            succ.removeInputSlot(s);
        }

        List<OutputSlot> outputSlots = new ArrayList<>(succ.getOutputSlots());
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

    public Set<FigureConnection> getConnections() {
        Set<FigureConnection> connections = new HashSet<>();
        for (Figure f : figures) {
            for (InputSlot s : f.getInputSlots()) {
                connections.addAll(s.getConnections());
            }
        }
        return connections;
    }

    public Set<BlockConnection> getBlockConnections() {
        Set<BlockConnection> connections = new HashSet<>();
        for (BlockConnection bc : blockConnections) {
            if (blocks.containsKey(bc.getFromCluster().getInputBlock()) &&
                blocks.containsKey(bc.getToCluster().getInputBlock())) {
                connections.add(bc);
            }
        }
        return connections;
    }

    public void printStatistics() {
        System.out.println("=============================================================");
        System.out.println("Diagram statistics");

        List<Figure> tmpFigures = getFigures();
        Set<FigureConnection> connections = getConnections();

        System.out.println("Number of figures: " + tmpFigures.size());
        System.out.println("Number of connections: " + connections.size());

        List<Figure> figuresSorted = new ArrayList<>(tmpFigures);
        figuresSorted.sort(new Comparator<Figure>() {

            @Override
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
}
