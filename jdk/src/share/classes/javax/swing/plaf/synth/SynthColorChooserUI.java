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


import javax.swing.*;
import javax.swing.colorchooser.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicColorChooserUI;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;


/**
 * Provides the Synth L&F UI delegate for
 * {@link javax.swing.JColorChooser}.
 *
 * @author Tom Santos
 * @author Steve Wilson
 * @since 1.7
 */
public class SynthColorChooserUI extends BasicColorChooserUI implements
        PropertyChangeListener, SynthUI {
    private SynthStyle style;

    /**
     * Creates a new UI object for the given component.
     *
     * @param c component to create UI object for
     * @return the UI object
     */
    public static ComponentUI createUI(JComponent c) {
        return new SynthColorChooserUI();
    }

    /**
     * @inheritDoc
     */
    @Override
    protected AbstractColorChooserPanel[] createDefaultChoosers() {
        SynthContext context = getContext(chooser, ENABLED);
        AbstractColorChooserPanel[] panels = (AbstractColorChooserPanel[])
                     context.getStyle().get(context, "ColorChooser.panels");
        context.dispose();

        if (panels == null) {
            panels = ColorChooserComponentFactory.getDefaultChooserPanels();
        }
        return panels;
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void installDefaults() {
        super.installDefaults();
        updateStyle(chooser);
    }

    private void updateStyle(JComponent c) {
        SynthContext context = getContext(c, ENABLED);
        style = SynthLookAndFeel.updateStyle(context, this);
        context.dispose();
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void uninstallDefaults() {
        SynthContext context = getContext(chooser, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;
        super.uninstallDefaults();
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void installListeners() {
        super.installListeners();
        chooser.addPropertyChangeListener(this);
    }

    /**
     * @inheritDoc
     */
    @Override
    protected void uninstallListeners() {
        chooser.removePropertyChangeListener(this);
        super.uninstallListeners();
    }

    /**
     * @inheritDoc
     */
    @Override
    public SynthContext getContext(JComponent c) {
        return getContext(c, getComponentState(c));
    }

    private SynthContext getContext(JComponent c, int state) {
        return SynthContext.getContext(SynthContext.class, c,
                    SynthLookAndFeel.getRegion(c), style, state);
    }

    private int getComponentState(JComponent c) {
        return SynthLookAndFeel.getComponentState(c);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        context.getPainter().paintColorChooserBackground(context, g, 0, 0,
                                                  c.getWidth(), c.getHeight());
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
     * This implementation does not perform any actions.
     *
     * @param context context for the component being painted
     * @param g {@code Graphics} object used for painting
     */
    protected void paint(SynthContext context, Graphics g) {
    }

    /**
     * @inheritDoc
     */
    @Override
    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintColorChooserBorder(context, g, x, y,w,h);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle((JColorChooser)e.getSource());
        }
    }
}
