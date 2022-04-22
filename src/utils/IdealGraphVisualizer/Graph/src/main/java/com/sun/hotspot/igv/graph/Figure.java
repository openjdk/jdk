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

import com.sun.hotspot.igv.data.InputBlock;
import com.sun.hotspot.igv.data.InputGraph;
import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.data.Source;
import com.sun.hotspot.igv.layout.Cluster;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.*;
import java.util.List;
import java.util.*;

public class Figure extends Properties.Entity implements Source.Provider, Vertex {

    public static final int INSET = 8;
    public static final int SLOT_WIDTH = 10;
    public static final int OVERLAPPING = 6;
    public static final int SLOT_START = 4;
    public static final int SLOT_OFFSET = 8;
    public static final int TOP_CFG_HEIGHT = 7;
    public static final int BOTTOM_CFG_HEIGHT = 6;
    protected List<InputSlot> inputSlots;
    protected List<OutputSlot> outputSlots;
    private Source source;
    private Diagram diagram;
    private Point position;
    private List<Figure> predecessors;
    private List<Figure> successors;
    private List<InputGraph> subgraphs;
    private Color color;
    private int id;
    private String idString;
    private String[] lines;
    private int heightCash = -1;
    private int widthCash = -1;
    private InputBlock block;
    private final FontMetrics metrics;

    public int getHeight() {
        if (heightCash == -1) {
            updateHeight();
        }
        return heightCash;
    }

    private void updateHeight() {
        String nodeText = diagram.getNodeText();
        int lines = nodeText.split("\n").length;
        if (hasInputList() && lines > 1) {
            lines++;
        }
        heightCash = lines * metrics.getHeight() + INSET;
        if (diagram.isCFG()) {
            if (hasNamedInputSlot()) {
                heightCash += TOP_CFG_HEIGHT;
            }
            if (hasNamedOutputSlot()) {
                heightCash += BOTTOM_CFG_HEIGHT;
            }
        }
    }

    public static <T> List<T> getAllBefore(List<T> inputList, T tIn) {
        List<T> result = new ArrayList<>();
        for(T t : inputList) {
            if(t.equals(tIn)) {
                break;
            }
            result.add(t);
        }
        return result;
    }

    public static int getSlotsWidth(Collection<? extends Slot> slots) {
        int result = Figure.SLOT_OFFSET;
        for(Slot s : slots) {
            result += s.getWidth() + Figure.SLOT_OFFSET;
        }
        return result;
    }

    public int getWidth() {
        if (widthCash == -1) {
            updateWidth();
        }
        return widthCash;
    }

    public void setWidth(int width) {
        widthCash = width;
    }

    private void updateWidth() {
            int max = 0;
            for (String s : getLines()) {
                int cur = metrics.stringWidth(s);
                if (cur > max) {
                    max = cur;
                }
            }
            widthCash = max + INSET;
            widthCash = Math.max(widthCash, Figure.getSlotsWidth(inputSlots));
            widthCash = Math.max(widthCash, Figure.getSlotsWidth(outputSlots));
    }

    protected Figure(Diagram diagram, int id) {
        this.diagram = diagram;
        this.source = new Source();
        inputSlots = new ArrayList<>(5);
        outputSlots = new ArrayList<>(1);
        predecessors = new ArrayList<>(6);
        successors = new ArrayList<>(6);
        this.id = id;
        idString = Integer.toString(id);

        this.position = new Point(0, 0);
        this.color = Color.WHITE;
        Canvas canvas = new Canvas();
        metrics = canvas.getFontMetrics(diagram.getFont().deriveFont(Font.BOLD));
    }

    public int getId() {
        return id;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public boolean hasInputList() {
        return diagram.isCFG() && !getPredecessors().isEmpty();
    }

    public void setBlock(InputBlock block) {
        this.block = block;
    }

    public InputBlock getBlock() {
        return block;
    }

    public List<Figure> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Set<Figure> getPredecessorSet() {
        Set<Figure> result = new HashSet<>();
        for (Figure f : getPredecessors()) {
            result.add(f);
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Figure> getSuccessorSet() {
        Set<Figure> result = new HashSet<>();
        for (Figure f : getSuccessors()) {
            result.add(f);
        }
        return Collections.unmodifiableSet(result);
    }

    public List<Figure> getSuccessors() {
        return Collections.unmodifiableList(successors);
    }

    protected void addPredecessor(Figure f) {
        this.predecessors.add(f);
    }

    protected void addSuccessor(Figure f) {
        this.successors.add(f);
    }

    protected void removePredecessor(Figure f) {
        assert predecessors.contains(f);
        predecessors.remove(f);
    }

    protected void removeSuccessor(Figure f) {
        assert successors.contains(f);
        successors.remove(f);
    }

    public List<InputGraph> getSubgraphs() {
        return subgraphs;
    }

    public void setSubgraphs(List<InputGraph> subgraphs) {
        this.subgraphs = subgraphs;
    }

    @Override
    public void setPosition(Point p) {
        this.position = p;
    }

    @Override
    public Point getPosition() {
        return position;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    @Override
    public Source getSource() {
        return source;
    }

    public InputSlot createInputSlot() {
        InputSlot slot = new InputSlot(this, -1);
        inputSlots.add(slot);
        return slot;
    }

    public InputSlot createInputSlot(int index) {
        InputSlot slot = new InputSlot(this, index);
        inputSlots.add(slot);
        inputSlots.sort(Slot.slotIndexComparator);
        return slot;
    }

    public void removeSlot(Slot s) {

        assert inputSlots.contains(s) || outputSlots.contains(s);

        List<FigureConnection> connections = new ArrayList<>(s.getConnections());
        for (FigureConnection c : connections) {
            c.remove();
        }

        if (inputSlots.contains(s)) {
            inputSlots.remove(s);
        } else if (outputSlots.contains(s)) {
            outputSlots.remove(s);
        }
    }

    public OutputSlot createOutputSlot() {
        OutputSlot slot = new OutputSlot(this, -1);
        outputSlots.add(slot);
        return slot;
    }

    public OutputSlot createOutputSlot(int index) {
        OutputSlot slot = new OutputSlot(this, index);
        outputSlots.add(slot);
        outputSlots.sort(Slot.slotIndexComparator);
        return slot;
    }

    public List<InputSlot> getInputSlots() {
        return Collections.unmodifiableList(inputSlots);
    }

    public Set<Slot> getSlots() {
        Set<Slot> result = new HashSet<>();
        result.addAll(getInputSlots());
        result.addAll(getOutputSlots());
        return result;
    }

    public List<OutputSlot> getOutputSlots() {
        return Collections.unmodifiableList(outputSlots);
    }

    public boolean hasNamedInputSlot() {
        for (InputSlot is : getInputSlots()) {
            if (is.hasSourceNodes() && is.shouldShowName()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasNamedOutputSlot() {
        for (OutputSlot os : getOutputSlots()) {
            if (os.hasSourceNodes() && os.shouldShowName()) {
                return true;
            }
        }
        return false;
    }

    void removeInputSlot(InputSlot s) {
        s.removeAllConnections();
        inputSlots.remove(s);
    }

    void removeOutputSlot(OutputSlot s) {
        s.removeAllConnections();
        outputSlots.remove(s);
    }

    public String[] getLines() {
        if (lines == null) {
            updateLines();
        }
        return lines;
    }

    public void updateLines() {
        String[] strings = diagram.getNodeText().split("\n");
        List<String> result = new ArrayList<>(strings.length + 1);

        for (int i = 0; i < strings.length; i++) {
            result.add(getProperties().resolveString(strings[i]));
        }

        if (hasInputList()) {
            String inputList = " ← ";
            List<String> inputs = new ArrayList<String>(getPredecessors().size());
            for (Figure p : getPredecessors()) {
                inputs.add(p.getProperties().resolveString(diagram.getTinyNodeText()));
            }
            inputList += String.join("  ", inputs);
            if (result.size() == 1) {
                // Single-line node, append input list to line.
                result.set(0, result.get(0) + inputList);
            } else {
                // Multi-line node, add yet another line for input list.
                result.add(inputList);
            }
        }

        lines = result.toArray(new String[0]);
        // Set the "label" property of each input node, so that by default
        // search is done on the node label (without line breaks). See also
        // class NodeQuickSearch in the View module.
        for (InputNode n : getSource().getSourceNodes()) {
            String label = n.getProperties().resolveString(diagram.getNodeText());
            n.getProperties().setProperty("label", label.replaceAll("\\R", " "));
        }
        // Update figure dimensions, as these are affected by the node text.
        updateWidth();
        updateHeight();
    }

    @Override
    public Dimension getSize() {
        int width = Math.max(getWidth(), Figure.SLOT_WIDTH * (Math.max(inputSlots.size(), outputSlots.size()) + 1));
        int height = getHeight() + (diagram.isCFG() ? 0 : 2 * Figure.SLOT_WIDTH - 2 * Figure.OVERLAPPING);
        return new Dimension(width, height);
    }

    @Override
    public String toString() {
        return idString;
    }

    public InputNode getFirstSourceNode() {
        return getSource().getSourceNodes().get(0);
    }

    public static int getVerticalOffset() {
        return Figure.SLOT_WIDTH - Figure.OVERLAPPING;
    }

    public Cluster getCluster() {
        if (getSource().getSourceNodes().size() == 0) {
            assert false : "Should never reach here, every figure must have at least one source node!";
            return null;
        } else {
            final InputBlock inputBlock = diagram.getGraph().getBlock(getFirstSourceNode());
            assert inputBlock != null;
            Cluster result = diagram.getBlock(inputBlock);
            assert result != null;
            return result;
        }
    }

    @Override
    public boolean isRoot() {

        List<InputNode> sourceNodes = source.getSourceNodes();
        if (sourceNodes.size() > 0 && getFirstSourceNode().getProperties().get("name") != null) {
            return getFirstSourceNode().getProperties().get("name").equals("Root");
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(Vertex f) {
        return toString().compareTo(f.toString());
    }

    public Rectangle getBounds() {
        return new Rectangle(this.getPosition(), new Dimension(this.getWidth(), this.getHeight()));
    }
}
