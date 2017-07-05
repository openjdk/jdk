/*
 * Copyright 1995-1996 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.motif;

import java.awt.*;
import java.awt.peer.*;

class MLabelPeer extends MComponentPeer implements LabelPeer {
    native void create(MComponentPeer parent);

    public void initialize() {
        Label   l = (Label)target;
        String  txt;
        int     align;

        if ((txt = l.getText()) != null) {
            setText(l.getText());
        }
        if ((align = l.getAlignment()) != Label.LEFT) {
            setAlignment(align);
        }
        super.initialize();
    }

    MLabelPeer(Label target) {
        super(target);
    }

    public Dimension getMinimumSize() {
        FontMetrics fm = getFontMetrics(target.getFont());
        String label = ((Label)target).getText();
        if (label == null) label = "";
        return new Dimension(fm.stringWidth(label) + 14,
                             fm.getHeight() + 8);
    }

    public native void setText(String label);
    public native void setAlignment(int alignment);

    /*
     * Print the native component by rendering the Motif look ourselves.
     */
    public void print(Graphics g) {
        Label l = (Label)target;
        Dimension d = l.size();
        Color bg = l.getBackground();
        Color fg = l.getForeground();

        g.setColor(bg);
        g.fillRect(1, 1, d.width - 2, d.height - 2);

        g.setColor(fg);
        g.setFont(l.getFont());
        FontMetrics fm = g.getFontMetrics();
        String lbl = l.getText();

        switch (l.getAlignment()) {
          case Label.LEFT:
            g.drawString(lbl, 2,
                         (d.height + fm.getMaxAscent() - fm.getMaxDescent()) / 2);
            break;
          case Label.RIGHT:
            g.drawString(lbl, d.width - (fm.stringWidth(lbl) + 2),
                         (d.height + fm.getMaxAscent() - fm.getMaxDescent()) / 2);
            break;
          case Label.CENTER:
            g.drawString(lbl, (d.width - fm.stringWidth(lbl)) / 2,
                         (d.height + fm.getMaxAscent() - fm.getMaxDescent()) / 2);
            break;
        }

        target.print(g);
    }

    /**
     * DEPRECATED
     */
    public Dimension minimumSize() {
            return getMinimumSize();
    }

}
