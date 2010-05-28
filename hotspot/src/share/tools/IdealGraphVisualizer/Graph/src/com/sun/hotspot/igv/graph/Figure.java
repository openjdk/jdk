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

import com.sun.hotspot.igv.layout.Cluster;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.data.Properties;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Thomas Wuerthinger
 */
public class Figure extends Properties.Entity implements Source.Provider, Vertex {

    public static final int INSET = 6;
    public static final int SLOT_WIDTH = 10;
    public static final int SLOT_START = 3;
    public static final boolean VERTICAL_LAYOUT = true;
    protected List<InputSlot> inputSlots;
    protected List<OutputSlot> outputSlots;
    private Source source;
    private Diagram diagram;
    private Point position;
    private List<Figure> predecessors;
    private List<Figure> successors;
    private Color color;
    private int id;
    private String idString;
    private String[] lines;
    private int heightCash = -1;
    private int widthCash = -1;

    public int getHeight() {
        if (heightCash == -1) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(diagram.getFont());
            FontMetrics metrics = g.getFontMetrics();
            String nodeText = diagram.getNodeText();
            heightCash = nodeText.split("\n").length * metrics.getHeight() + INSET;
        }
        return heightCash;
    }

    public int getWidth() {
        if (widthCash == -1) {
            int max = 0;
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(diagram.getFont());
            FontMetrics metrics = g.getFontMetrics();
            for (String s : lines) {
                int cur = metrics.stringWidth(s);
                if (cur > max) {
                    max = cur;
                }
            }
            widthCash = max + INSET;
        }
        return widthCash;
    }

    protected Figure(Diagram diagram, int id) {
        this.diagram = diagram;
        this.source = new Source();
        inputSlots = new ArrayList<InputSlot>(5);
        outputSlots = new ArrayList<OutputSlot>(1);
        predecessors = new ArrayList<Figure>(6);
        successors = new ArrayList<Figure>(6);
        this.id = id;
        idString = Integer.toString(id);

        this.position = new Point(0, 0);
        this.color = Color.WHITE;
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

    public List<Figure> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Set<Figure> getPredecessorSet() {
        Set<Figure> result = new HashSet<Figure>();
        for (Figure f : getPredecessors()) {
            result.add(f);
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Figure> getSuccessorSet() {
        Set<Figure> result = new HashSet<Figure>();
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

    public void setPosition(Point p) {
        this.position = p;
    }

    public Point getPosition() {
        return position;
    }

    public Diagram getDiagram() {
        return diagram;
    }

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
        Collections.sort(inputSlots, Slot.slotIndexComparator);
        return slot;
    }

    public void removeSlot(Slot s) {

        assert inputSlots.contains(s) || outputSlots.contains(s);

        List<Connection> connections = new ArrayList<Connection>(s.getConnections());
        for (Connection c : connections) {
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
        Collections.sort(outputSlots, Slot.slotIndexComparator);
        return slot;
    }

    public List<InputSlot> getInputSlots() {
        return Collections.unmodifiableList(inputSlots);
    }

    public List<OutputSlot> getOutputSlots() {
        return Collections.unmodifiableList(outputSlots);
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
        String[] result = new String[strings.length];

        for (int i = 0; i < strings.length; i++) {
            result[i] = resolveString(strings[i]);
        }

        lines = result;
    }

    private String resolveString(String string) {

        StringBuilder sb = new StringBuilder();
        boolean inBrackets = false;
        StringBuilder curIdent = new StringBuilder();

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (inBrackets) {
                if (c == ']') {
                    String value = getProperties().get(curIdent.toString());
                    if (value == null) {
                        value = "";
                    }
                    sb.append(value);
                    inBrackets = false;
                } else {
                    curIdent.append(c);
                }
            } else {
                if (c == '[') {
                    inBrackets = true;
                    curIdent = new StringBuilder();
                } else {
                    sb.append(c);
                }
            }
        }

        return sb.toString();
    }

    public Dimension getSize() {
        if (VERTICAL_LAYOUT) {
            int width = Math.max(getWidth(), Figure.SLOT_WIDTH * (Math.max(inputSlots.size(), outputSlots.size()) + 1));
            int height = getHeight() + 2 * Figure.SLOT_WIDTH;
            return new Dimension(width, height);
        } else {
            int width = getWidth() + 2 * Figure.SLOT_WIDTH;
            int height = Figure.SLOT_WIDTH * (Math.max(inputSlots.size(), outputSlots.size()) + 1);
            return new Dimension(width, height);
        }
    }

    @Override
    public String toString() {
        return idString;
    }

    public Cluster getCluster() {
        if (getSource().getSourceNodes().size() == 0) {
            assert false : "Should never reach here, every figure must have at least one source node!";
            return null;
        } else {
            Cluster result = diagram.getBlock(diagram.getGraph().getBlock(getSource().getSourceNodes().get(0)));
            assert result != null;
            return result;
        }
    }

    public boolean isRoot() {
        if (source.getSourceNodes().size() > 0 && source.getSourceNodes().get(0).getProperties().get("name").equals("Root")) {
            return true;
        } else {
            return false;
        }
    }

    public int compareTo(Vertex f) {
        return toString().compareTo(f.toString());
    }
}
