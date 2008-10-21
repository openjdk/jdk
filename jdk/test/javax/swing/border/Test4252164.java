/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 4252164
 * @summary Tests rounded LineBorder for components
 * @author Sergey Malenkov
 * @run applet/manual=yesno Test4252164.html
 */

import java.awt.Color;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.JApplet;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.LineBorder;

public class Test4252164 extends JApplet implements MouseWheelListener {
    private int thickness;
    private JLabel rounded;
    private JLabel straight;

    public void mouseWheelMoved(MouseWheelEvent event) {
        update(event.getWheelRotation());
    }

    public void init() {
        add(createUI());
        addMouseWheelListener(this);
    }

    private JPanel createUI() {
        this.rounded = new JLabel("ROUNDED"); // NON-NLS: the label for rounded border
        this.straight = new JLabel("STRAIGHT"); // NON-NLS: the label for straight border

        JPanel panel = new JPanel();
        panel.add(this.rounded);
        panel.add(this.straight);

        update(10);

        return panel;
    }

    private void update(int thickness) {
        this.thickness += thickness;

        this.rounded.setBorder(new LineBorder(Color.RED, this.thickness, true));
        this.straight.setBorder(new LineBorder(Color.RED, this.thickness, false));
    }
}
