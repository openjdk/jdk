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

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicPanelUI;
import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import sun.swing.plaf.synth.SynthUI;

/**
 * Synth's PanelUI.
 *
 * @author Steve Wilson
 */
class SynthPanelUI extends BasicPanelUI implements PropertyChangeListener,
        SynthUI {
    private SynthStyle style;

    public static ComponentUI createUI(JComponent c) {
        return new SynthPanelUI();
    }

    public void installUI(JComponent c) {
        JPanel p = (JPanel)c;

        super.installUI(c);
        installListeners(p);
    }

    public void uninstallUI(JComponent c) {
        JPanel p = (JPanel)c;

        uninstallListeners(p);
        super.uninstallUI(c);
    }

    protected void installListeners(JPanel p) {
        p.addPropertyChangeListener(this);
    }

    protected void uninstallListeners(JPanel p) {
        p.removePropertyChangeListener(this);
    }

    protected void installDefaults(JPanel p) {
        updateStyle(p);
    }

    protected void uninstallDefaults(JPanel p) {
        SynthContext context = getContext(p, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;
    }

    private void updateStyle(JPanel c) {
        SynthContext context = getContext(c, ENABLED);
        style = SynthLookAndFeel.updateStyle(context, this);
        context.dispose();
    }

    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    private SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                    SynthLookAndFeel.getRegion(c), style, state);
    }

    private Region getRegion(JComponent c) {
        return SynthLookAndFeel.getRegion(c);
    }

    private int getComponentState(JComponent c) {
        return SynthLookAndFeel.getComponentState(c);
    }

    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        context.getPainter().paintPanelBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight());
        paint(context, g);
        context.dispose();
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    protected void paint(SynthContext context, Graphics g) {
        // do actual painting
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintPanelBorder(context, g, x, y, w, h);
    }

    public void propertyChange(PropertyChangeEvent pce) {
        if (SynthLookAndFeel.shouldUpdateStyle(pce)) {
            updateStyle((JPanel)pce.getSource());
        }
    }
}
