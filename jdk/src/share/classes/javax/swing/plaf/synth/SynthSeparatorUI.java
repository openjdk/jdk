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

/**
 * Provides the Synth L&F UI delegate for
 * {@link javax.swing.JSeparator}.
 *
 * @author Shannon Hickey
 * @author Joshua Outwater
 * @since 1.7
 */
public class SynthSeparatorUI extends SeparatorUI
                              implements PropertyChangeListener, SynthUI {
    private SynthStyle style;

    /**
     * Creates a new UI object for the given component.
     *
     * @param c component to create UI object for
     * @return the UI object
     */
    public static ComponentUI createUI(JComponent c) {
        return new SynthSeparatorUI();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void installUI(JComponent c) {
        installDefaults((JSeparator)c);
        installListeners((JSeparator)c);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void uninstallUI(JComponent c) {
        uninstallListeners((JSeparator)c);
        uninstallDefaults((JSeparator)c);
    }

    /**
     * Installs default setting. This method is called when a
     * {@code LookAndFeel} is installed.
     */
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

    /**
     * Uninstalls default setting. This method is called when a
     * {@code LookAndFeel} is uninstalled.
     */
    public void uninstallDefaults(JSeparator c) {
        SynthContext context = getContext(c, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;
    }

    /**
     * Installs listeners. This method is called when a
     * {@code LookAndFeel} is installed.
     */
    public void installListeners(JSeparator c) {
        c.addPropertyChangeListener(this);
    }

    /**
     * Uninstalls listeners. This method is called when a
     * {@code LookAndFeel} is uninstalled.
     */
    public void uninstallListeners(JSeparator c) {
        c.removePropertyChangeListener(this);
    }

    /**
     * @inheritDoc
     */
    @Override
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

    /**
     * @inheritDoc
     */
    @Override
    public void paint(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        paint(context, g);
        context.dispose();
    }

    /**
     * Paints the specified component.
     *
     * @param context context for the component being painted
     * @param g {@code Graphics} object used for painting
     */
    protected void paint(SynthContext context, Graphics g) {
        JSeparator separator = (JSeparator)context.getComponent();
        context.getPainter().paintSeparatorForeground(context, g, 0, 0,
                             separator.getWidth(), separator.getHeight(),
                             separator.getOrientation());
    }

    /**
     * @inheritDoc
     */
    @Override
    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        JSeparator separator = (JSeparator)context.getComponent();
        context.getPainter().paintSeparatorBorder(context, g, x, y, w, h,
                                                  separator.getOrientation());
    }

    /**
     * @inheritDoc
     */
    @Override
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

    /**
     * @inheritDoc
     */
    @Override
    public Dimension getMinimumSize(JComponent c) {
        return getPreferredSize(c);
    }

    /**
     * @inheritDoc
     */
    @Override
    public Dimension getMaximumSize(JComponent c) {
        return new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    }

    /**
     * @inheritDoc
     */
    @Override
    public SynthContext getContext(JComponent c) {
        return getContext(c, SynthLookAndFeel.getComponentState(c));
    }

    private SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                    SynthLookAndFeel.getRegion(c), style, state);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (SynthLookAndFeel.shouldUpdateStyle(evt)) {
            updateStyle((JSeparator)evt.getSource());
        }
    }
}
