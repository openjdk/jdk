/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
import javax.swing.text.*;
import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.beans.PropertyChangeEvent;

import sun.swing.plaf.synth.SynthUI;

/**
 * Basis of a look and feel for a JTextField in the Synth
 * look and feel.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans<sup><font size="-2">TM</font></sup>
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @author  Shannon Hickey
 */
class SynthTextFieldUI
    extends BasicTextFieldUI
    implements SynthUI, FocusListener
{
    private SynthStyle style;

    /**
     * Creates a UI for a JTextField.
     *
     * @param c the text field
     * @return the UI
     */
    public static ComponentUI createUI(JComponent c) {
        return new SynthTextFieldUI();
    }

    public SynthTextFieldUI() {
        super();
    }

    private void updateStyle(JTextComponent comp) {
        SynthContext context = getContext(comp, ENABLED);
        SynthStyle oldStyle = style;

        style = SynthLookAndFeel.updateStyle(context, this);

        if (style != oldStyle) {
            SynthTextFieldUI.updateStyle(comp, context, getPropertyPrefix());

            if (oldStyle != null) {
                uninstallKeyboardActions();
                installKeyboardActions();
            }
        }
        context.dispose();
    }

    static void updateStyle(JTextComponent comp, SynthContext context,
            String prefix) {
        SynthStyle style = context.getStyle();

        Color color = comp.getCaretColor();
        if (color == null || color instanceof UIResource) {
            comp.setCaretColor(
                (Color)style.get(context, prefix + ".caretForeground"));
        }

        Color fg = comp.getForeground();
        if (fg == null || fg instanceof UIResource) {
            fg = style.getColorForState(context, ColorType.TEXT_FOREGROUND);
            if (fg != null) {
                comp.setForeground(fg);
            }
        }

        Object ar = style.get(context, prefix + ".caretAspectRatio");
        if (ar instanceof Number) {
            comp.putClientProperty("caretAspectRatio", ar);
        }

        context.setComponentState(SELECTED | FOCUSED);

        Color s = comp.getSelectionColor();
        if (s == null || s instanceof UIResource) {
            comp.setSelectionColor(
                style.getColor(context, ColorType.TEXT_BACKGROUND));
        }

        Color sfg = comp.getSelectedTextColor();
        if (sfg == null || sfg instanceof UIResource) {
            comp.setSelectedTextColor(
                style.getColor(context, ColorType.TEXT_FOREGROUND));
        }

        context.setComponentState(DISABLED);

        Color dfg = comp.getDisabledTextColor();
        if (dfg == null || dfg instanceof UIResource) {
            comp.setDisabledTextColor(
                style.getColor(context, ColorType.TEXT_FOREGROUND));
        }

        Insets margin = comp.getMargin();
        if (margin == null || margin instanceof UIResource) {
            margin = (Insets)style.get(context, prefix + ".margin");

            if (margin == null) {
                // Some places assume margins are non-null.
                margin = SynthLookAndFeel.EMPTY_UIRESOURCE_INSETS;
            }
            comp.setMargin(margin);
        }

        Caret caret = comp.getCaret();
        if (caret instanceof UIResource) {
            Object o = style.get(context, prefix + ".caretBlinkRate");
            if (o != null && o instanceof Integer) {
                Integer rate = (Integer)o;
                caret.setBlinkRate(rate.intValue());
            }
        }
    }

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

    public void update(Graphics g, JComponent c) {
        SynthContext context = getContext(c);

        SynthLookAndFeel.update(context, g);
        paintBackground(context, g, c);
        paint(context, g);
        context.dispose();
    }

    /**
     * Paints the interface.  This is routed to the
     * paintSafely method under the guarantee that
     * the model won't change from the view of this thread
     * while it's rendering (if the associated model is
     * derived from AbstractDocument).  This enables the
     * model to potentially be updated asynchronously.
     */
    protected void paint(SynthContext context, Graphics g) {
        super.paint(g, getComponent());
    }

    void paintBackground(SynthContext context, Graphics g, JComponent c) {
        context.getPainter().paintTextFieldBackground(context, g, 0, 0,
                                                c.getWidth(), c.getHeight());
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintTextFieldBorder(context, g, x, y, w, h);
    }

    protected void paintBackground(Graphics g) {
        // Overriden to do nothing, all our painting is done from update/paint.
    }

    /**
     * This method gets called when a bound property is changed
     * on the associated JTextComponent.  This is a hook
     * which UI implementations may change to reflect how the
     * UI displays bound properties of JTextComponent subclasses.
     * This is implemented to do nothing (i.e. the response to
     * properties in JTextComponent itself are handled prior
     * to calling this method).
     *
     * @param evt the property change event
     */
    protected void propertyChange(PropertyChangeEvent evt) {
        if (SynthLookAndFeel.shouldUpdateStyle(evt)) {
            updateStyle((JTextComponent)evt.getSource());
        }
        super.propertyChange(evt);
    }

    public void focusGained(FocusEvent e) {
        getComponent().repaint();
    }

    public void focusLost(FocusEvent e) {
        getComponent().repaint();
    }

    protected void installDefaults() {
        // Installs the text cursor on the component
        super.installDefaults();
        updateStyle(getComponent());
        getComponent().addFocusListener(this);
    }

    protected void uninstallDefaults() {
        SynthContext context = getContext(getComponent(), ENABLED);

        getComponent().putClientProperty("caretAspectRatio", null);
        getComponent().removeFocusListener(this);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;
        super.uninstallDefaults();
    }

    public void installUI(JComponent c) {
        super.installUI(c);
    }
}
