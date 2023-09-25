/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.hotspot.igv.data.InputNode;
import com.sun.hotspot.igv.data.Properties;
import com.sun.hotspot.igv.layout.Cluster;
import com.sun.hotspot.igv.layout.Vertex;
import java.awt.*;
import java.util.List;
import java.util.*;

public class Figure extends Properties.Entity implements Vertex {

    public static final int INSET = 8;
    public static final int SLOT_WIDTH = 10;
    public static final int OVERLAPPING = 6;
    public static final int SLOT_START = 4;
    public static final int SLOT_OFFSET = 8;
    public static final int TOP_CFG_HEIGHT = 7;
    public static final int BOTTOM_CFG_HEIGHT = 6;
    public static final int WARNING_WIDTH = 16;
    public static final double BOLD_LINE_FACTOR = 1.06;
    protected List<InputSlot> inputSlots;
    protected List<OutputSlot> outputSlots;
    private final InputNode inputNode;
    private final Diagram diagram;
    private Point position;
    private final List<Figure> predecessors;
    private final List<Figure> successors;
    private Color color;
    private String warning;
    private final int id;
    private final String idString;
    private String[] lines;
    private int heightCash = -1;
    private int widthCash = -1;
    private Block block;
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
        if (getProperties().get("extra_label") != null) {
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
            widthCash = (int)(max * BOLD_LINE_FACTOR) + INSET;
            if (getWarning() != null) {
                widthCash += WARNING_WIDTH;
            }
            widthCash = Math.max(widthCash, Figure.getSlotsWidth(inputSlots));
            widthCash = Math.max(widthCash, Figure.getSlotsWidth(outputSlots));
    }

    protected Figure(Diagram diagram, int id, InputNode node) {
        this.diagram = diagram;
        this.inputNode = node;
        this.inputSlots = new ArrayList<>(5);
        this.outputSlots = new ArrayList<>(1);
        this.predecessors = new ArrayList<>(6);
        this.successors = new ArrayList<>(6);
        this.id = id;
        this.idString = Integer.toString(id);
        this.position = new Point(0, 0);
        this.color = Color.WHITE;
        Canvas canvas = new Canvas();
        this.metrics = canvas.getFontMetrics(Diagram.FONT.deriveFont(Font.BOLD));
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

    public void setWarning(String warning) {
        this.warning = getProperties().resolveString(warning);
    }

    public String getWarning() {
        return warning;
    }

    public boolean hasInputList() {
        return diagram.isCFG() && !getInputSlots().isEmpty();
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    public List<Figure> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Set<Figure> getPredecessorSet() {
        return Collections.unmodifiableSet(new HashSet<>(getPredecessors()));
    }

    public Set<Figure> getSuccessorSet() {
        return Collections.unmodifiableSet(new HashSet<>(getSuccessors()));
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

    public InputNode getInputNode() {
        return inputNode;
    }

    public InputSlot createInputSlot() {
        InputSlot slot = new InputSlot(this, -1);
        inputSlots.add(slot);
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
        } else outputSlots.remove(s);
    }

    public void createOutputSlot() {
        OutputSlot slot = new OutputSlot(this, -1);
        outputSlots.add(slot);
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

        for (String string : strings) {
            result.add(getProperties().resolveString(string));
        }

        if (hasInputList()) {
            String inputList = " ← ";
            List<String> inputs = new ArrayList<>(getPredecessors().size());
            for (InputSlot is : getInputSlots()) {
                String inputLabel = null;
                if (is.getConnections().isEmpty()) {
                    if (is.hasSourceNodes() && is.shouldShowName()) {
                        inputLabel = "[" + is.getShortName() + "]";
                    } else {
                        inputLabel = "_";
                    }
                } else {
                    OutputSlot os = is.getConnections().get(0).getOutputSlot();
                    Figure f = os.getFigure();
                    String nodeTinyLabel = f.getProperties().resolveString(diagram.getTinyNodeText());
                    if (os.hasSourceNodes() && os.shouldShowName()) {
                        nodeTinyLabel += ":" + os.getShortName();
                    }
                    inputLabel = nodeTinyLabel;
                }
                assert(inputLabel != null);
                int gapSize = is.gapSize();
                if (gapSize == 1) {
                    inputs.add("_");
                } else if (gapSize > 1) {
                    inputs.add("…");
                }
                inputs.add(inputLabel);
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

        String extraLabel = getProperties().get("extra_label");
        if (extraLabel != null) {
            result.add(extraLabel);
        }

        lines = result.toArray(new String[0]);
        // Set the "label" property of the input node, so that by default
        // search is done on the node label (without line breaks). See also
        // class NodeQuickSearch in the View module.
        String label = inputNode.getProperties().resolveString(diagram.getNodeText());
        inputNode.getProperties().setProperty("label", label.replaceAll("\\R", " "));

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
    public boolean equals(Object o) {
        if (!(o instanceof Figure)) {
            return false;
        }
        return getInputNode().equals(((Figure) o).getInputNode());
    }

    @Override
    public int hashCode() {
        return getInputNode().hashCode();
    }

    @Override
    public String toString() {
        return idString;
    }

    public static int getVerticalOffset() {
        return Figure.SLOT_WIDTH - Figure.OVERLAPPING;
    }

    public Cluster getCluster() {
        return block;
    }

    @Override
    public boolean isRoot() {
        if (inputNode != null && inputNode.getProperties().get("name") != null) {
            return inputNode.getProperties().get("name").equals("Root");
        } else {
            return false;
        }
    }

    @Override
    public int compareTo(Vertex f) {
        return toString().compareTo(f.toString());
    }
}
