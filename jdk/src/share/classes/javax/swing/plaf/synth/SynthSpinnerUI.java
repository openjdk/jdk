/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.event.*;

import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicSpinnerUI;

import java.beans.*;
import sun.swing.plaf.synth.SynthUI;

/**
 * Synth's SpinnerUI.
 *
 * @author Hans Muller
 * @author Joshua Outwater
 */
class SynthSpinnerUI extends BasicSpinnerUI implements PropertyChangeListener,
        SynthUI {
    private SynthStyle style;
    /**
     * A FocusListener implementation which causes the entire spinner to be
     * repainted whenever the editor component (typically a text field) becomes
     * focused, or loses focus. This is necessary because since SynthSpinnerUI
     * is composed of an editor and two buttons, it is necessary that all three
     * components indicate that they are "focused" so that they can be drawn
     * appropriately. The repaint is used to ensure that the buttons are drawn
     * in the new focused or unfocused state, mirroring that of the editor.
     */
    private EditorFocusHandler editorFocusHandler = new EditorFocusHandler();

    /**
     * Returns a new instance of SynthSpinnerUI.
     *
     * @param c the JSpinner (not used)
     * @see ComponentUI#createUI
     * @return a new SynthSpinnerUI object
     */
    public static ComponentUI createUI(JComponent c) {
        return new SynthSpinnerUI();
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        spinner.addPropertyChangeListener(this);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor)editor).getTextField();
            if (tf != null) {
                tf.addFocusListener(editorFocusHandler);
            }
        }
    }

    /**
     * Removes the <code>propertyChangeListener</code> added
     * by installListeners.
     * <p>
     * This method is called by <code>uninstallUI</code>.
     *
     * @see #installListeners
     */
    @Override
    protected void uninstallListeners() {
        super.uninstallListeners();
        spinner.removePropertyChangeListener(this);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor)editor).getTextField();
            if (tf != null) {
                tf.removeFocusListener(editorFocusHandler);
            }
        }
    }

    /**
     * Initialize the <code>JSpinner</code> <code>border</code>,
     * <code>foreground</code>, and <code>background</code>, properties
     * based on the corresponding "Spinner.*" properties from defaults table.
     * The <code>JSpinners</code> layout is set to the value returned by
     * <code>createLayout</code>.  This method is called by <code>installUI</code>.
     *
     * @see #uninstallDefaults
     * @see #installUI
     * @see #createLayout
     * @see LookAndFeel#installBorder
     * @see LookAndFeel#installColors
     */
    protected void installDefaults() {
        LayoutManager layout = spinner.getLayout();

        if (layout == null || layout instanceof UIResource) {
            spinner.setLayout(createLayout());
        }
        updateStyle(spinner);
    }


    private void updateStyle(JSpinner c) {
        SynthContext context = getContext(c, ENABLED);
        SynthStyle oldStyle = style;
        style = SynthLookAndFeel.updateStyle(context, this);
        if (style != oldStyle) {
            if (oldStyle != null) {
                // Only call installKeyboardActions as uninstall is not
                // public.
                installKeyboardActions();
            }
        }
        context.dispose();
    }


    /**
     * Sets the <code>JSpinner's</code> layout manager to null.  This
     * method is called by <code>uninstallUI</code>.
     *
     * @see #installDefaults
     * @see #uninstallUI
     */
    protected void uninstallDefaults() {
        if (spinner.getLayout() instanceof UIResource) {
            spinner.setLayout(null);
        }

        SynthContext context = getContext(spinner, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;
    }


    protected LayoutManager createLayout() {
        return new SpinnerLayout();
    }


    /**
     * Create a component that will replace the spinner models value
     * with the object returned by <code>spinner.getPreviousValue</code>.
     * By default the <code>previousButton</code> is a JButton
     * who's <code>ActionListener</code> updates it's <code>JSpinner</code>
     * ancestors model.  If a previousButton isn't needed (in a subclass)
     * then override this method to return null.
     *
     * @return a component that will replace the spinners model with the
     *     next value in the sequence, or null
     * @see #installUI
     * @see #createNextButton
     */
    protected Component createPreviousButton() {
        JButton b = new SynthArrowButton(SwingConstants.SOUTH);
        b.setName("Spinner.previousButton");
        installPreviousButtonListeners(b);
        return b;
    }


    /**
     * Create a component that will replace the spinner models value
     * with the object returned by <code>spinner.getNextValue</code>.
     * By default the <code>nextButton</code> is a JButton
     * who's <code>ActionListener</code> updates it's <code>JSpinner</code>
     * ancestors model.  If a nextButton isn't needed (in a subclass)
     * then override this method to return null.
     *
     * @return a component that will replace the spinners model with the
     *     next value in the sequence, or null
     * @see #installUI
     * @see #createPreviousButton
     */
    protected Component createNextButton() {
        JButton b = new SynthArrowButton(SwingConstants.NORTH);
        b.setName("Spinner.nextButton");
        installNextButtonListeners(b);
        return b;
    }


    /**
     * This method is called by installUI to get the editor component
     * of the <code>JSpinner</code>.  By default it just returns
     * <code>JSpinner.getEditor()</code>.  Subclasses can override
     * <code>createEditor</code> to return a component that contains
     * the spinner's editor or null, if they're going to handle adding
     * the editor to the <code>JSpinner</code> in an
     * <code>installUI</code> override.
     * <p>
     * Typically this method would be overridden to wrap the editor
     * with a container with a custom border, since one can't assume
     * that the editors border can be set directly.
     * <p>
     * The <code>replaceEditor</code> method is called when the spinners
     * editor is changed with <code>JSpinner.setEditor</code>.  If you've
     * overriden this method, then you'll probably want to override
     * <code>replaceEditor</code> as well.
     *
     * @return the JSpinners editor JComponent, spinner.getEditor() by default
     * @see #installUI
     * @see #replaceEditor
     * @see JSpinner#getEditor
     */
    protected JComponent createEditor() {
        JComponent editor = spinner.getEditor();
        editor.setName("Spinner.editor");
        updateEditorAlignment(editor);
        return editor;
    }


    /**
     * Called by the <code>PropertyChangeListener</code> when the
     * <code>JSpinner</code> editor property changes.  It's the responsibility
     * of this method to remove the old editor and add the new one.  By
     * default this operation is just:
     * <pre>
     * spinner.remove(oldEditor);
     * spinner.add(newEditor, "Editor");
     * </pre>
     * The implementation of <code>replaceEditor</code> should be coordinated
     * with the <code>createEditor</code> method.
     *
     * @see #createEditor
     * @see #createPropertyChangeListener
     */
    protected void replaceEditor(JComponent oldEditor, JComponent newEditor) {
        spinner.remove(oldEditor);
        spinner.add(newEditor, "Editor");
        if (oldEditor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor)oldEditor).getTextField();
            if (tf != null) {
                tf.removeFocusListener(editorFocusHandler);
            }
        }
        if (newEditor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor)newEditor).getTextField();
            if (tf != null) {
                tf.addFocusListener(editorFocusHandler);
            }
        }
    }

    private void updateEditorAlignment(JComponent editor) {
        if (editor instanceof JSpinner.DefaultEditor) {
            SynthContext context = getContext(spinner);
            Integer alignment = (Integer)context.getStyle().get(
                    context, "Spinner.editorAlignment");
            JTextField text = ((JSpinner.DefaultEditor)editor).getTextField();
            if (alignment != null) {
                text.setHorizontalAlignment(alignment);

            }
            // copy across the sizeVariant property to the editor
            text.putClientProperty("JComponent.sizeVariant",
                    spinner.getClientProperty("JComponent.sizeVariant"));
        }
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
        context.getPainter().paintSpinnerBackground(context,
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
    }

    public void paintBorder(SynthContext context, Graphics g, int x,
                            int y, int w, int h) {
        context.getPainter().paintSpinnerBorder(context, g, x, y, w, h);
    }

    /**
     * A simple layout manager for the editor and the next/previous buttons.
     * See the SynthSpinnerUI javadoc for more information about exactly
     * how the components are arranged.
     */
    private static class SpinnerLayout implements LayoutManager, UIResource
    {
        private Component nextButton = null;
        private Component previousButton = null;
        private Component editor = null;

        public void addLayoutComponent(String name, Component c) {
            if ("Next".equals(name)) {
                nextButton = c;
            }
            else if ("Previous".equals(name)) {
                previousButton = c;
            }
            else if ("Editor".equals(name)) {
                editor = c;
            }
        }

        public void removeLayoutComponent(Component c) {
            if (c == nextButton) {
                nextButton = null;
            }
            else if (c == previousButton) {
                previousButton = null;
            }
            else if (c == editor) {
                editor = null;
            }
        }

        private Dimension preferredSize(Component c) {
            return (c == null) ? new Dimension(0, 0) : c.getPreferredSize();
        }

        public Dimension preferredLayoutSize(Container parent) {
            Dimension nextD = preferredSize(nextButton);
            Dimension previousD = preferredSize(previousButton);
            Dimension editorD = preferredSize(editor);

            /* Force the editors height to be a multiple of 2
             */
            editorD.height = ((editorD.height + 1) / 2) * 2;

            Dimension size = new Dimension(editorD.width, editorD.height);
            size.width += Math.max(nextD.width, previousD.width);
            Insets insets = parent.getInsets();
            size.width += insets.left + insets.right;
            size.height += insets.top + insets.bottom;
            return size;
        }

        public Dimension minimumLayoutSize(Container parent) {
            return preferredLayoutSize(parent);
        }

        private void setBounds(Component c, int x, int y, int width, int height) {
            if (c != null) {
                c.setBounds(x, y, width, height);
            }
        }

        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int availWidth = parent.getWidth() - (insets.left + insets.right);
            int availHeight = parent.getHeight() - (insets.top + insets.bottom);
            Dimension nextD = preferredSize(nextButton);
            Dimension previousD = preferredSize(previousButton);
            int nextHeight = availHeight / 2;
            int previousHeight = availHeight - nextHeight;
            int buttonsWidth = Math.max(nextD.width, previousD.width);
            int editorWidth = availWidth - buttonsWidth;

            /* Deal with the spinners componentOrientation property.
             */
            int editorX, buttonsX;
            if (parent.getComponentOrientation().isLeftToRight()) {
                editorX = insets.left;
                buttonsX = editorX + editorWidth;
            }
            else {
                buttonsX = insets.left;
                editorX = buttonsX + buttonsWidth;
            }

            int previousY = insets.top + nextHeight;
            setBounds(editor, editorX, insets.top, editorWidth, availHeight);
            setBounds(nextButton, buttonsX, insets.top, buttonsWidth, nextHeight);
            setBounds(previousButton, buttonsX, previousY, buttonsWidth, previousHeight);
        }
    }


    public void propertyChange(PropertyChangeEvent e) {
        String propertyName = e.getPropertyName();
        JSpinner spinner = (JSpinner)(e.getSource());
        SpinnerUI spinnerUI = spinner.getUI();

        if (spinnerUI instanceof SynthSpinnerUI) {
            SynthSpinnerUI ui = (SynthSpinnerUI)spinnerUI;

            if (SynthLookAndFeel.shouldUpdateStyle(e)) {
                ui.updateStyle(spinner);
            }
        }
    }

    /** Listen to editor text field focus changes and repaint whole spinner */
    private class EditorFocusHandler implements FocusListener{
        /** Invoked when a editor text field gains the keyboard focus. */
        public void focusGained(FocusEvent e) {
            spinner.repaint();
        }

        /** Invoked when a editor text field loses the keyboard focus. */
        public void focusLost(FocusEvent e) {
            spinner.repaint();
        }
    }

    /** Override the arrowbuttons focus handling to follow the text fields focus */
    private class SpinnerArrowButton extends SynthArrowButton{
        public SpinnerArrowButton(int direction) {
            super(direction);
        }

        @Override
        public boolean isFocusOwner() {
            if (spinner == null){
                return super.isFocusOwner();
            } else if (spinner.getEditor() instanceof JSpinner.DefaultEditor){
                return ((JSpinner.DefaultEditor)spinner.getEditor())
                        .getTextField().isFocusOwner();
            } else if (spinner.getEditor()!= null) {
                return spinner.getEditor().isFocusOwner();
            } else {
                return super.isFocusOwner();
            }
        }
    }
}
