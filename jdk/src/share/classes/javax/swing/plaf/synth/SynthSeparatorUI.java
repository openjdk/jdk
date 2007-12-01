/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.beans.*;
import javax.swing.*;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.SeparatorUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.DimensionUIResource;
import sun.swing.plaf.synth.SynthUI;

/**
 * A Synth L&F implementation of SeparatorUI.  This implementation
 * is a "combined" view/controller.
 *
 * @author Shannon Hickey
 * @author Joshua Outwater
 */
class SynthSeparatorUI extends SeparatorUI implements PropertyChangeListener,
                                    SynthUI {
    private SynthStyle style;

    public static ComponentUI createUI(JComponent c) {
        return new SynthSeparatorUI();
    }

    public void installUI(JComponent c) {
        installDefaults((JSeparator)c);
        installListeners((JSeparator)c);
    }

    public void uninstallDefaults(JComponent c) {
        uninstallListeners((JSeparator)c);
        uninstallDefaults((JSeparator)c);
    }

    public void installDefaults(JSeparator c) {
        updateStyle(c);
    }

    private void updateStyle(JSeparator sep) {
        SynthContext context = getContext(sep, ENABLED);
        SynthStyle oldStyle = style;

        style = SynthLookAndFeel.updateStyle(context, this);

        if (style != oldStyle) {
            if (sep instanceof JToolBar.Separator) {
                Dimension size = ((JToolBar.Separator)sep).getSeparatorSize();
                if (size == null || size instanceof UIResource) {
                    size = (DimensionUIResource)style.get(
                                      context, "ToolBar.separatorSize");
                    if (size == null) {
                        size = new DimensionUIResource(10, 10);
                    }
                    ((JToolBar.Separator)sep).setSeparatorSize(size);
                }
            }
        }

        context.dispose();
    }

    public void uninstallDefaults(JSeparator c) {
        SynthContext context = getContext(c, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;
    }

    public void installListeners(JSeparator c) {
        c.addPropertyChangeListener(this);
    }

    public void uninstallListeners(JSeparator c) {
        c.removePropertyChangeListener(this);
    }

    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        JSeparator separator = (JSeparator)context.getComponent();
        SynthLookAndFeel.update(context, g);
        context.getPainter().paintSeparatorBackground(context,
                          g, 0, 0, c.getWidth(), c.getHeight(),
                          separator.getOrientation());
        paint(context, g);
        context.dispose();
    }

    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    protected void paint(SynthContext context, Graphics g) {
        JSeparator separator = (JSeparator)context.getComponent();
        context.getPainter().paintSeparatorForeground(context, g, 0, 0,
                             separator.getWidth(), separator.getHeight(),
                             separator.getOrientation());
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        JSeparator separator = (JSeparator)context.getComponent();
        context.getPainter().paintSeparatorBorder(context, g, x, y, w, h,
                                                  separator.getOrientation());
    }

    public Dimension getPreferredSize(JComponent c) {
        SynthContext context = getContext(c);

        int thickness = style.getInt(context, "Separator.thickness", 2);
        Insets insets = c.getInsets();
        Dimension size;

        if (((JSeparator)c).getOrientation() == JSeparator.VERTICAL) {
            size = new Dimension(insets.left + insets.right + thickness,
                                 insets.top + insets.bottom);
        } else {
            size = new Dimension(insets.left + insets.right,
                                 insets.top + insets.bottom + thickness);
        }
        context.dispose();
        return size;
    }

    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    public Dimension getMaximumSize(JComponent c) {
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
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

    public void propertyChange(PropertyChangeEvent evt) {
        if (SynthLookAndFeel.shouldUpdateStyle(evt)) {
            updateStyle((JSeparator)evt.getSource());
        }
    }
}
