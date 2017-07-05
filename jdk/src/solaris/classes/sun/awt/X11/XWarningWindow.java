/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package sun.awt.X11;

import java.awt.*;

class XWarningWindow extends XWindow {
    final static int defaultHeight = 27;

    Window ownerWindow;
    XWarningWindow(Window ownerWindow, long parentWindow) {
        super(ownerWindow, parentWindow);
        this.ownerWindow = ownerWindow;
        xSetVisible(true);
        toFront();
    }

    protected String getWMName() {
        return "Warning window";
    }

    public Graphics getGraphics() {
        if ((surfaceData == null) || (ownerWindow == null)) return null;
        return getGraphics(surfaceData,
                                 getColor(),
                                 getBackground(),
                                 getFont());
    }
    void paint(Graphics g, int x, int y, int width, int height) {
        String warningString = getWarningString();
        Rectangle bounds = getBounds();
        bounds.x = 0;
        bounds.y = 0;
        Rectangle updateRect = new Rectangle(x, y, width, height);
        if (updateRect.intersects(bounds)) {
            Rectangle updateArea = updateRect.intersection(bounds);
            g.setClip(updateArea);
            g.setColor(getBackground());
            g.fillRect(updateArea.x, updateArea.y, updateArea.width, updateArea.height);
            g.setColor(getColor());
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            int warningWidth = fm.stringWidth(warningString);
            int w_x = (bounds.width - warningWidth)/2;
            int w_y = (bounds.height + fm.getMaxAscent() - fm.getMaxDescent())/2;
            g.drawString(warningString, w_x, w_y);
            g.drawLine(bounds.x, bounds.y+bounds.height-1, bounds.x+bounds.width-1, bounds.y+bounds.height-1);
        }
    }

    String getWarningString() {
        return ownerWindow.getWarningString();
    }

    int getHeight() {
        return defaultHeight; // should implement depending on Font
    }

    Color getBackground() {
        return SystemColor.window;
    }
    Color getColor() {
        return Color.black;
    }
    Font getFont () {
        return ownerWindow.getFont();
    }
    public void repaint() {
        Rectangle bounds = getBounds();
        Graphics g = getGraphics();
        try {
            paint(g, 0, 0, bounds.width, bounds.height);
        } finally {
            g.dispose();
        }
    }

    public void handleExposeEvent(XEvent xev) {
        super.handleExposeEvent(xev);

        XExposeEvent xe = xev.get_xexpose();
        final int x = xe.get_x();
        final int y = xe.get_y();
        final int width = xe.get_width();
        final int height = xe.get_height();
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                Graphics g = getGraphics();
                try {
                    paint(g, x, y, width, height);
                } finally {
                    g.dispose();
                }
            }
        });
    }
    protected boolean isEventDisabled(XEvent e) {
        return true;
    }
}
