/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.hotspot.igv.data.Source;
import com.sun.hotspot.igv.layout.Port;
import com.sun.hotspot.igv.layout.Vertex;
import com.sun.hotspot.igv.util.StringUtils;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 *
 * @author Thomas Wuerthinger
 */
public abstract class Slot implements Port, Source.Provider, Properties.Provider {

    private final int wantedIndex;
    private final Source source;
    protected List<FigureConnection> connections;
    private Color color;
    private String text;
    private String shortName;
    private final Figure figure;

    protected Slot(Figure figure, int wantedIndex) {
        this.figure = figure;
        connections = new ArrayList<>(2);
        source = new Source();
        this.wantedIndex = wantedIndex;
        text = "";
        shortName = "";
        assert figure != null;
    }

    @Override
    public Properties getProperties() {
        Properties p = new Properties();
        if (hasSourceNodes()) {
            for (InputNode n : source.getSourceNodes()) {
                p.add(n.getProperties());
            }
        } else {
            p.setProperty("name", "Slot");
            p.setProperty("figure", figure.getProperties().get("name"));
            p.setProperty("connectionCount", Integer.toString(connections.size()));
        }
        return p;
    }
    public static final Comparator<Slot> slotIndexComparator = Comparator.comparingInt(o -> o.wantedIndex);

    public int getWidth() {
        assert shortName != null;
        if (shortName.isEmpty()) {
            return 0;
        } else {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(Diagram.SLOT_FONT.deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            return Math.max(Figure.SLOT_WIDTH, metrics.stringWidth(shortName) + 6);
        }
    }

    public int getHeight() {
        return Figure.SLOT_HEIGHT;
    }

    @Override
    public Source getSource() {
        return source;
    }

    public String getText() {
        return text;
    }

    public void setShortName(String s) {
        assert s != null;
        this.shortName = s;

    }

    public String getShortName() {
        return shortName;
    }

    public String getToolTipText() {
        StringBuilder sb = new StringBuilder();
        String shortNodeText = figure.getDiagram().getShortNodeText();
        if (!text.isEmpty()) {
            sb.append(text);
            if (!shortNodeText.isEmpty()) {
                sb.append(": ");
            }
        }

        for (InputNode n : getSource().getSourceNodes()) {
            sb.append(StringUtils.escapeHTML(n.getProperties().resolveString(shortNodeText)));
        }

        return sb.toString();
    }

    public boolean shouldShowName() {
        return getShortName() != null && !getShortName().isEmpty();
    }

    public boolean hasSourceNodes() {
        return !getSource().getSourceNodes().isEmpty();
    }

    public void setText(String s) {
        if (s == null) {
            s = "";
        }
        this.text = s;
    }

    public Figure getFigure() {
        return figure;
    }

    public Color getColor() {
        return this.color;
    }

    public void setColor(Color c) {
        color = c;
    }

    public List<FigureConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public void removeAllConnections() {
        List<FigureConnection> connectionsCopy = new ArrayList<>(this.connections);
        for (FigureConnection c : connectionsCopy) {
            c.remove();
        }
    }

    @Override
    public Vertex getVertex() {
        return figure;
    }

    public abstract int getPosition();

    public abstract void setPosition(int position);
}

