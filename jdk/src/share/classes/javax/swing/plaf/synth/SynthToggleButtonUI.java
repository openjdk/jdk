/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing.plaf.synth;

import java.awt.Graphics;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;

/**
 * Synth's ToggleButtonUI.
 * <p>
 * @author Jeff Dinkins
 */
class SynthToggleButtonUI extends SynthButtonUI {
    // ********************************
    //          Create PLAF
    // ********************************
    public static ComponentUI createUI(JComponent b) {
        return new SynthToggleButtonUI();
    }

    @Override
    protected String getPropertyPrefix() {
        return "ToggleButton.";
    }

    @Override
    void paintBackground(SynthContext context, Graphics g, JComponent c) {
        if (((AbstractButton) c).isContentAreaFilled()) {
            int x = 0, y = 0, w = c.getWidth(), h = c.getHeight();
            SynthPainter painter = context.getPainter();
            painter.paintToggleButtonBackground(context, g, x, y, w, h);
        }
    }

    @Override
    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintToggleButtonBorder(context, g, x, y, w, h);
    }
}
