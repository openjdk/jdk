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
import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import sun.swing.DefaultLookup;
import sun.swing.plaf.synth.SynthUI;

/**
 * Synth's OptionPaneUI.
 *
 * @author James Gosling
 * @author Scott Violet
 * @author Amy Fowler
 */
class SynthOptionPaneUI extends BasicOptionPaneUI implements
                                PropertyChangeListener, SynthUI {
    private SynthStyle style;

    /**
      * Creates a new BasicOptionPaneUI instance.
      */
    public static ComponentUI createUI(JComponent x) {
        return new SynthOptionPaneUI();
    }

    protected void installDefaults() {
        updateStyle(optionPane);
    }

    protected void installListeners() {
        super.installListeners();
        optionPane.addPropertyChangeListener(this);
    }

    private void updateStyle(JComponent c) {
        SynthContext context = getContext(c, ENABLED);
        SynthStyle oldStyle = style;

        style = SynthLookAndFeel.updateStyle(context, this);
        if (style != oldStyle) {
            minimumSize = (Dimension)style.get(context,
                                               "OptionPane.minimumSize");
            if (minimumSize == null) {
                minimumSize = new Dimension(262, 90);
            }
            if (oldStyle != null) {
                uninstallKeyboardActions();
                installKeyboardActions();
            }
        }
        context.dispose();
    }

    protected void uninstallDefaults() {
        SynthContext context = getContext(optionPane, ENABLED);

        style.uninstallDefaults(context);
        context.dispose();
        style = null;
    }

    protected void uninstallListeners() {
        super.uninstallListeners();
        optionPane.removePropertyChangeListener(this);
    }

    protected void installComponents() {
        optionPane.add(createMessageArea());

        Container separator = createSeparator();
        if (separator != null) {
            optionPane.add(separator);
            SynthContext context = getContext(optionPane, ENABLED);
            optionPane.add(Box.createVerticalStrut(context.getStyle().
                       getInt(context, "OptionPane.separatorPadding", 6)));
            context.dispose();
        }
        optionPane.add(createButtonArea());
        optionPane.applyComponentOrientation(optionPane.getComponentOrientation());
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
        context.getPainter().paintOptionPaneBackground(context,
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
        context.getPainter().paintOptionPaneBorder(context, g, x, y, w, h);
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (SynthLookAndFeel.shouldUpdateStyle(e)) {
            updateStyle((JOptionPane)e.getSource());
        }
    }

    protected boolean getSizeButtonsToSameWidth() {
        return DefaultLookup.getBoolean(optionPane, this,
                                        "OptionPane.sameSizeButtons", true);
    }

    /**
     * Messaged from installComponents to create a Container containing the
     * body of the message. The icon is the created by calling
     * <code>addIcon</code>.
     */
    protected Container createMessageArea() {
        JPanel top = new JPanel();
        top.setName("OptionPane.messageArea");
        top.setLayout(new BorderLayout());

        /* Fill the body. */
        Container          body = new JPanel(new GridBagLayout());
        Container          realBody = new JPanel(new BorderLayout());

        body.setName("OptionPane.body");
        realBody.setName("OptionPane.realBody");

        if (getIcon() != null) {
            JPanel sep = new JPanel();
            sep.setName("OptionPane.separator");
            sep.setPreferredSize(new Dimension(15, 1));
            realBody.add(sep, BorderLayout.BEFORE_LINE_BEGINS);
        }
        realBody.add(body, BorderLayout.CENTER);

        GridBagConstraints cons = new GridBagConstraints();
        cons.gridx = cons.gridy = 0;
        cons.gridwidth = GridBagConstraints.REMAINDER;
        cons.gridheight = 1;

        SynthContext context = getContext(optionPane, ENABLED);
        cons.anchor = context.getStyle().getInt(context,
                      "OptionPane.messageAnchor", GridBagConstraints.CENTER);
        context.dispose();

        cons.insets = new Insets(0,0,3,0);

        addMessageComponents(body, cons, getMessage(),
                          getMaxCharactersPerLineCount(), false);
        top.add(realBody, BorderLayout.CENTER);

        addIcon(top);
        return top;
    }

    protected Container createSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);

        separator.setName("OptionPane.separator");
        return separator;
    }
}
