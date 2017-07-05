/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 */

import java.awt.*;

/**
 * A simple bar chart demo
 * @author Sami Shaio
 * @modified 06/21/00 Daniel Peek : refactored, comments
 */
public class BarChart extends java.applet.Applet {
    private static final int VERTICAL = 0;
    private static final int HORIZONTAL = 1;

    private static final int SOLID = 0;
    private static final int STRIPED = 1;

    private int orientation;
    private String title;
    private Font font;
    private FontMetrics metrics;
    private int fontHeight = 15;
    private int columns;
    private int values[];
    private Color colors[];
    private String labels[];
    private int styles[];
    private int scale = 10;
    private int maxLabelWidth = 0;
    private int barSpacing = 10;
    private int maxValue = 0;

    public void init() {

        getSettings();

        values = new int[columns];
        labels = new String[columns];
        styles = new int[columns];
        colors = new Color[columns];

        for (int i=0; i < columns; i++) {
            parseValue(i);
            parseLabel(i);
            parseStyle(i);
            parseColor(i);
        }
    }

    private void getSettings() {
        font = new java.awt.Font("Monospaced", Font.BOLD, 12);
        metrics = getFontMetrics(font);

        title = getParameter("title");
        if (title == null) {
            title = "Chart";
        }

        String temp = getParameter("columns");
        if (temp == null) {
            columns = 5;
        } else {
            columns = Integer.parseInt(temp);
        }

        temp = getParameter("scale");
        if (temp == null) {
            scale = 10;
        } else {
            scale = Integer.parseInt(temp);
        }

        temp = getParameter("orientation");
        if (temp == null) {
            orientation = VERTICAL;
        } else if (temp.equalsIgnoreCase("horizontal")) {
            orientation = HORIZONTAL;
        } else {
            orientation = VERTICAL;
        }
    }

    private void parseValue(int i) {
        String temp = getParameter("C" + (i+1));
        try {
            values[i] = Integer.parseInt(temp);
        } catch (NumberFormatException e) {
            values[i] = 0;
        } catch (NullPointerException e) {
            values[i] = 0;
        }
        maxValue = Math.max(maxValue, values[i]);
    }

    private void parseLabel(int i) {
        String temp = getParameter("C" + (i+1) + "_label");
        if (temp==null) {
            labels[i] = "";
        } else {
            labels[i] = temp;
        }
        maxLabelWidth = Math.max(metrics.stringWidth
                                 ((String) (labels[i])), maxLabelWidth);
    }

    private void parseStyle(int i) {
        String temp = getParameter("C" + (i+1) + "_style");
        if (temp == null || temp.equalsIgnoreCase("solid")) {
            styles[i] = SOLID;
        } else if (temp.equalsIgnoreCase("striped")) {
            styles[i] = STRIPED;
        } else {
            styles[i] = SOLID;
        }
    }

    private void parseColor(int i) {
        String temp = getParameter("C" + (i+1) + "_color");
        if (temp != null) {
            temp = temp.toLowerCase();
            if (temp.equals("red")) {
                colors[i] = Color.red;
            } else if (temp.equals("green")) {
                colors[i] = Color.green;
            } else if (temp.equals("blue")) {
                colors[i] = Color.blue;
            } else if (temp.equals("pink")) {
                colors[i] = Color.pink;
            } else if (temp.equals("orange")) {
                colors[i] = Color.orange;
            } else if (temp.equals("magenta")) {
                colors[i] = Color.magenta;
            } else if (temp.equals("cyan")) {
                colors[i] = Color.cyan;
            } else if (temp.equals("white")) {
                colors[i] = Color.white;
            } else if (temp.equals("yellow")) {
                colors[i] = Color.yellow;
            } else if (temp.equals("gray")) {
                colors[i] = Color.gray;
            } else if (temp.equals("darkgray")) {
                colors[i] = Color.darkGray;
            } else {
                colors[i] = Color.gray;
            }
        } else {
            colors[i] = Color.gray;
        }
    }

    public void paint(Graphics g) {
        // draw the title centered at the bottom of the bar graph
        g.setColor(Color.black);
        g.setFont(font);

        g.drawRect(0, 0, getSize().width - 1, getSize().height - 1);

        int titleWidth = metrics.stringWidth(title);
        int cx = Math.max((getSize().width - titleWidth) / 2, 0);
        int cy = getSize().height - metrics.getDescent();
        g.drawString(title, cx, cy);

        // draw the bars and their titles
        if(orientation == HORIZONTAL) {
            paintHorizontal(g);
        } else {  // VERTICAL
            paintVertical(g);
        }
    }

    private void paintHorizontal(Graphics g) {
        // x and y coordinates to draw/write to
        int cx, cy;
        int barHeight = metrics.getHeight();

        for (int i = 0; i < columns; i++) {

            // set the X coordinate for this bar and label and center it
            int widthOfItems = maxLabelWidth + 3 + (maxValue * scale) + 5
                + metrics.stringWidth(Integer.toString(maxValue));
            cx = Math.max((getSize().width - widthOfItems) / 2, 0);

            // set the Y coordinate for this bar and label
            cy = getSize().height - metrics.getDescent() - metrics.getHeight()
                - barSpacing - ((columns - i - 1) * (barSpacing + barHeight));

            // draw the label
            g.setColor(Color.black);
            g.drawString(labels[i], cx, cy);
            cx += maxLabelWidth + 3;


            // draw the shadow
            g.fillRect(cx + 4, cy - barHeight + 4,
                       (values[i] * scale), barHeight);

            // draw the bar
            g.setColor(colors[i]);
            if (styles[i] == STRIPED) {
                for (int k = 0; k <= values[i] * scale; k += 2) {
                    g.drawLine(cx + k, cy - barHeight, cx + k, cy);
                }
            } else {      // SOLID
                g.fillRect(cx, cy - barHeight,
                           (values[i] * scale) + 1, barHeight + 1);
            }
            cx += (values[i] * scale) + 4;

            // draw the value at the end of the bar
            g.setColor(g.getColor().darker());
            g.drawString(Integer.toString(values[i]), cx, cy);
        }
    }

    private void paintVertical(Graphics g) {
        int barWidth = maxLabelWidth;

        for (int i = 0; i < columns; i++) {

            // X coordinate for this label and bar (centered)
            int widthOfItems = (barWidth + barSpacing) * columns - barSpacing;
            int cx = Math.max((getSize().width - widthOfItems) / 2, 0);
            cx += (maxLabelWidth + barSpacing) * i;

            // Y coordinate for this label and bar
            int cy = getSize().height - metrics.getHeight()
                - metrics.getDescent() - 4;

            // draw the label
            g.setColor(Color.black);
            g.drawString(labels[i], cx, cy);
            cy -= metrics.getHeight() - 3;

            // draw the shadow
            g.fillRect(cx + 4, cy - (values[i] * scale) - 4,
                       barWidth, (values[i] * scale));

            // draw the bar
            g.setColor(colors[i]);
            if (styles[i] == STRIPED) {
                for (int k=0; k <= values[i] * scale; k+=2) {
                    g.drawLine(cx, cy - k,
                               cx + barWidth, cy - k);
                }
            } else {
                g.fillRect(cx, cy - (values[i] * scale),
                           barWidth + 1, (values[i] * scale) + 1);
            }
            cy -= (values[i] * scale) + 5;

            // draw the value on top of the bar
            g.setColor(g.getColor().darker());
            g.drawString(Integer.toString(values[i]), cx, cy);
        }
    }

    public String getAppletInfo() {
        return "Title: Bar Chart \n"
            + "Author: Sami Shaio \n"
            + "A simple bar chart demo.";
    }

    public String[][] getParameterInfo() {
        String[][] info = {
            {"title", "string", "The title of bar graph.  Default is 'Chart'"},
            {"scale", "int", "The scale of the bar graph.  Default is 10."},
            {"columns", "int", "The number of columns/rows.  Default is 5."},
            {"orientation", "{VERTICAL, HORIZONTAL}",
             "The orienation of the bar graph.  Default is VERTICAL."},
            {"c#", "int", "Subsitute a number for #.  "
             + "The value/size of bar #.  Default is 0."},
            {"c#_label", "string", "The label for bar #.  "
             + "Default is an empty label."},
            {"c#_style", "{SOLID, STRIPED}", "The style of bar #.  "
             + "Default is SOLID."},
            {"c#_color", "{RED, GREEN, BLUE, PINK, ORANGE, MAGENTA, CYAN, "
             + "WHITE, YELLOW, GRAY, DARKGRAY}",
             "The color of bar #.  Default is GRAY."}
        };
        return info;
    }
}
