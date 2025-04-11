/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.*;
import java.awt.Font;
import java.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Diagram {

    private final Map<InputNode, Figure> figures;
    private final Hashtable<Integer, Figure> figureHash;
    private final Map<InputBlock, Block> blocks;
    private List<LiveRangeSegment> liveRangeSegments;
    private final InputGraph inputGraph;
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
        this.figures = new LinkedHashMap<>();
        this.figureHash = new Hashtable<>();
        this.blocks = new LinkedHashMap<>(8);
        this.liveRangeSegments = new ArrayList<>();
        this.blockConnections = new HashSet<>();
        this.inputGraph = graph;
        this.cfg = false;
        int curId = 0;

        for (InputBlock b : graph.getBlocks()) {
            blocks.put(b, new Block(b, this));
        }

        Collection<InputNode> nodes = graph.getNodes();
        for (InputNode n : nodes) {
            Figure f = new Figure(this, curId, n);
            curId++;
            f.getProperties().add(n.getProperties());
            f.setBlock(blocks.get(graph.getBlock(n)));
            figureHash.put(n.getId(), f);
            this.figures.put(n, f);
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

        for (Figure f : figures.values()) {
            int i = 0;
            for (InputSlot inputSlot : f.getInputSlots()) {
                inputSlot.setOriginalIndex(i);
                i++;
            }
        }

        for (InputBlockEdge e : graph.getBlockEdges()) {
            Block p = getBlock(e.getFrom());
            Block s = getBlock(e.getTo());
            blockConnections.add(new BlockConnection(p, s, e.getLabel()));
        }

        Hashtable<Integer, InputLiveRange> liveRangeHash = new Hashtable<>();
        for (InputLiveRange lrg : graph.getLiveRanges()) {
            liveRangeHash.put(lrg.getId(), lrg);
        }

        // Pre-compute live ranges joined by each block.
        Map<InputBlock, Set<Integer>> blockJoined = new HashMap<>();
        for (InputBlock b : graph.getBlocks()) {
            blockJoined.put(b, new HashSet<>());
            for (InputNode n : b.getNodes()) {
                LivenessInfo l = graph.getLivenessInfoForNode(n);
                if (l != null && l.join != null) {
                    blockJoined.get(b).addAll(l.join);
                }
            }
        }

        for (InputBlock b : graph.getBlocks()) {
            if (b.getNodes().isEmpty()) {
                continue;
            }
            Map<Integer, InputNode> active = new HashMap<>();
            Set<Integer> instant = new HashSet<>();
            Set<Integer> opening = new HashSet<>();
            InputNode header = b.getNodes().get(0);
            if (graph.getLivenessInfoForNode(header) == null) {
                // No liveness information available, skip.
                continue;
            }
            Set<Integer> joined = new HashSet<>();
            if (b.getSuccessors().size() == 1) {
                // We assume the live-out ranges in this block might only be
                // joined if there is exactly one successor block (i.e. the CFG
                // does not contain critical edges).
                joined.addAll(b.getLiveOut());
                InputBlock succ = b.getSuccessors().iterator().next();
                joined.retainAll(blockJoined.get(succ));
            }
            for (int liveRangeId : graph.getLivenessInfoForNode(header).livein) {
                active.put(liveRangeId, null);
            }
            for (InputNode n : b.getNodes()) {
                LivenessInfo l = graph.getLivenessInfoForNode(n);
                // Commit segments killed by n.
                if (l.kill != null) {
                    for (int liveRangeId : l.kill) {
                        InputNode startNode = active.get(liveRangeId);
                        Figure start = startNode == null ? null : figureHash.get(startNode.getId());
                        InputNode endNode = n;
                        Figure end = figureHash.get(endNode.getId());
                        LiveRangeSegment s = new LiveRangeSegment(liveRangeHash.get(liveRangeId), getBlock(b), start, end);
                        if (opening.contains(liveRangeId)) {
                            s.setOpening(true);
                        }
                        s.setClosing(true);
                        liveRangeSegments.add(s);
                        active.remove(liveRangeId);
                    }
                }
                // Activate new segments.
                if (l.def != null && !active.containsKey(l.def)) {
                    InputNode startNode = n;
                    if (l.join != null && !l.join.isEmpty()) {
                        // Start of a "joined" live range. These start always at
                        // the beginning of the basic block.
                        startNode = null;
                    }
                    active.put(l.def, startNode);
                    opening.add(l.def);
                    if (!l.liveout.contains(l.def)) {
                        instant.add(l.def);
                    }
                }
            }
            // Commit segments live out the block.
            for (Integer liveRangeId : active.keySet()) {
                InputNode startNode = active.get(liveRangeId);
                Figure start = startNode == null ? null : figureHash.get(startNode.getId());
                LiveRangeSegment s = new LiveRangeSegment(liveRangeHash.get(liveRangeId), getBlock(b), start, null);
                if (instant.contains(liveRangeId)) {
                    s.setInstantaneous(true);
                }
                if (opening.contains(liveRangeId)) {
                    s.setOpening(true);
                }
                if (instant.contains(liveRangeId) || joined.contains(liveRangeId)) {
                    s.setClosing(true);
                }
                liveRangeSegments.add(s);
            }
        }
        liveRangeSegments.sort(Comparator.comparingInt(s -> s.getLiveRange().getId()));
        for (InputBlock inputBlock : graph.getBlocks()) {
            // This loop could be sped up by fusing it with the above one.
            List<Integer> liveRangeSegmentIds = new ArrayList<>();
            int lastAddedLiveRangeId = -1;
            for (LiveRangeSegment s : getLiveRangeSegments()) {
                if (s.getCluster().getInputBlock().getName().equals(inputBlock.getName())) {
                    int thisLiveRangeId = s.getLiveRange().getId();
                    if (thisLiveRangeId != lastAddedLiveRangeId) {
                        liveRangeSegmentIds.add(thisLiveRangeId);
                        lastAddedLiveRangeId = thisLiveRangeId;
                    }
                }
            }
            blocks.get(inputBlock).setLiveRangeIds(liveRangeSegmentIds);
        }
    }

    public InputGraph getInputGraph() {
        return inputGraph;
    }

    public Block getBlock(InputBlock b) {
        assert blocks.containsKey(b);
        return blocks.get(b);
    }

    public boolean hasFigure(InputNode n) {
        return figures.containsKey(n);
    }

    public Figure getFigure(InputNode n) {
        assert figures.containsKey(n);
        return figures.get(n);
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
        return Collections.unmodifiableList(new ArrayList<>(figures.values()));
    }

    public List<LiveRangeSegment> getLiveRangeSegments() {
        return Collections.unmodifiableList(liveRangeSegments);
    }

    public FigureConnection createConnection(InputSlot inputSlot, OutputSlot outputSlot, String label) {
        assert inputSlot.getFigure().getDiagram() == this;
        assert outputSlot.getFigure().getDiagram() == this;
        return new FigureConnection(inputSlot, outputSlot, label);
    }

    public void removeAllBlocks(Collection<Block> blocksToRemove) {
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

    public void removeAllFigures(Collection<Figure> figuresToRemove) {
        for (Figure f : figuresToRemove) {
            freeFigure(f);
            figures.remove(f.getInputNode());

        }
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

    public void removeFigure(Figure figure) {
        freeFigure(figure);
        this.figures.remove(figure.getInputNode());
    }

    public Set<FigureConnection> getConnections() {
        Set<FigureConnection> connections = new HashSet<>();
        for (Figure f : figures.values()) {
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

        Collection<Figure> tmpFigures = getFigures();
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

    public Figure getRootFigure() {
        Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<>(figures.values());
        Figure root = selector.selectSingle(new Properties.StringPropertyMatcher("name", "Root"));
        if (root == null) {
            root = selector.selectSingle(new Properties.StringPropertyMatcher("name", "Start"));
        }
        return root;
    }

}
