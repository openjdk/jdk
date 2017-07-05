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
import java.beans.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import java.awt.*;
import java.awt.event.*;
import sun.swing.plaf.synth.SynthUI;


/**
 * Synth's ViewportUI.
 *
 */
class SynthViewportUI extends ViewportUI implements
           PropertyChangeListener, SynthUI {
    private SynthStyle style;

    public static ComponentUI createUI(JComponent c) {
        return new SynthViewportUI();
    }

    public void installUI(JComponent c) {
        super.installUI(c);
        installDefaults(c);
        installListeners(c);
    }

    public void uninstallUI(JComponent c) {
        super.uninstallUI(c);
        uninstallListeners(c);
        uninstallDefaults(c);
    }

    protected void installDefaults(JComponent c) {
        updateStyle(c);
    }

    private void updateStyle(JComponent c) {
        SynthContext context = getContext(c, ENABLED);

        // Note: JViewport is special cased as it does not allow for
        // a border to be set. JViewport.setBorder is overriden to throw
        // an IllegalArgumentException. Refer to SynthScrollPaneUI for
        // details of this.
        SynthStyle newStyle = SynthLookAndFeel.getStyle(context.getComponent(),
                                                        context.getRegion());
        SynthStyle oldStyle = context.getStyle();

        if (newStyle != oldStyle) {
            if (oldStyle != null) {
                oldStyle.uninstallDefaults(context);
            }
            context.setStyle(newStyle);
            newStyle.installDefaults(context);
        }
        this.style = newStyle;
        context.dispose();
    }

    protected void installListeners(JComponent c) {
        c.addPropertyChangeListener(this);
    }

    protected void uninstallListeners(JComponent c) {
        c.removePropertyChangeListener(this);
    }

    protected void uninstallDefaults(JComponent c) {
        SynthContext context = getContext(c, ENABLED);
        style.uninstallDefaults(context);
        context.dispose();
        style = null;
    }

    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    private SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                                       getRegion(c), style, state);
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
        context.getPainter().paintViewportBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight());
        paint(context, g);
        context.dispose();
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        // This does nothing on purpose, JViewport doesn't allow a border
        // and therefor this will NEVER be called.
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    protected void paint(SynthContext context, Graphics g) {
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle((JComponent)e.getSource());
        }
    }
}
