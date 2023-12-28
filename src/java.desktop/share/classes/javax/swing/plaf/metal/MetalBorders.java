/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package javax.swing.plaf.metal;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicBorders;
import javax.swing.text.JTextComponent;

import com.sun.java.swing.SwingUtilities3;
import sun.swing.StringUIClientPropertyKey;
import sun.swing.SwingUtilities2;

import static sun.java2d.pipe.Region.clipRound;

/**
 * Factory object that can vend Borders appropriate for the metal L &amp; F.
 * @author Steve Wilson
 */
public class MetalBorders {

    /**
     * Client property indicating the button shouldn't provide a rollover
     * indicator. Only used with the Ocean theme.
     */
    static Object NO_BUTTON_ROLLOVER =
        new StringUIClientPropertyKey("NoButtonRollover");

    /**
     * Constructs a {@code MetalBorders}.
     */
    public MetalBorders() {}

    /**
     * The class represents the 3D border.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class Flush3DBorder extends AbstractBorder implements UIResource{
        /**
         * Constructs a {@code Flush3DBorder}.
         */
        public Flush3DBorder() {}

        public void paintBorder(Component c, Graphics g, int x, int y,
                          int w, int h) {
            if (c.isEnabled()) {
                MetalUtils.drawFlush3DBorder(g, x, y, w, h);
            } else {
                MetalUtils.drawDisabledBorder(g, x, y, w, h);
            }
        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            newInsets.set(2, 2, 2, 2);
            return newInsets;
        }
    }

    /**
     * The class represents the border of a {@code JButton}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class ButtonBorder extends AbstractBorder implements UIResource {

        /**
         * The border insets.
         */
        protected static Insets borderInsets = new Insets( 3, 3, 3, 3 );

        /**
         * Constructs a {@code ButtonBorder}.
         */
        public ButtonBorder() {}

        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            if (!(c instanceof AbstractButton)) {
                return;
            }
            if (MetalLookAndFeel.usingOcean()) {
                paintOceanBorder(c, g, x, y, w, h);
                return;
            }
            AbstractButton button = (AbstractButton)c;
            ButtonModel model = button.getModel();

            if ( model.isEnabled() ) {
                boolean isPressed = model.isPressed() && model.isArmed();
                boolean isDefault = (button instanceof JButton && ((JButton)button).isDefaultButton());

                if (isPressed && isDefault) {
                    MetalUtils.drawDefaultButtonPressedBorder(g, x, y, w, h);
                } else if (isPressed) {
                    MetalUtils.drawPressed3DBorder( g, x, y, w, h );
                } else if (isDefault) {
                    MetalUtils.drawDefaultButtonBorder( g, x, y, w, h, false);
                } else {
                    MetalUtils.drawButtonBorder( g, x, y, w, h, false);
                }
            } else { // disabled state
                MetalUtils.drawDisabledBorder( g, x, y, w-1, h-1 );
            }
        }

        private void paintOceanBorder(Component c, Graphics g, int x, int y,
                                      int w, int h) {
            AbstractButton button = (AbstractButton)c;
            ButtonModel model = ((AbstractButton)c).getModel();

            g.translate(x, y);
            if (MetalUtils.isToolBarButton(button)) {
                if (model.isEnabled()) {
                    if (model.isPressed()) {
                        g.setColor(MetalLookAndFeel.getWhite());
                        g.fillRect(1, h - 1, w - 1, 1);
                        g.fillRect(w - 1, 1, 1, h - 1);
                        g.setColor(MetalLookAndFeel.getControlDarkShadow());
                        g.drawRect(0, 0, w - 2, h - 2);
                        g.fillRect(1, 1, w - 3, 1);
                    }
                    else if (model.isSelected() || model.isRollover()) {
                        g.setColor(MetalLookAndFeel.getWhite());
                        g.fillRect(1, h - 1, w - 1, 1);
                        g.fillRect(w - 1, 1, 1, h - 1);
                        g.setColor(MetalLookAndFeel.getControlDarkShadow());
                        g.drawRect(0, 0, w - 2, h - 2);
                    }
                    else {
                        g.setColor(MetalLookAndFeel.getWhite());
                        g.drawRect(1, 1, w - 2, h - 2);
                        g.setColor(UIManager.getColor(
                                "Button.toolBarBorderBackground"));
                        g.drawRect(0, 0, w - 2, h - 2);
                    }
                }
                else {
                   g.setColor(UIManager.getColor(
                           "Button.disabledToolBarBorderBackground"));
                   g.drawRect(0, 0, w - 2, h - 2);
                }
            }
            else if (model.isEnabled()) {
                boolean pressed = model.isPressed();
                boolean armed = model.isArmed();

                if ((c instanceof JButton) && ((JButton)c).isDefaultButton()) {
                    g.setColor(MetalLookAndFeel.getControlDarkShadow());
                    g.drawRect(0, 0, w - 1, h - 1);
                    g.drawRect(1, 1, w - 3, h - 3);
                }
                else if (pressed) {
                    g.setColor(MetalLookAndFeel.getControlDarkShadow());
                    g.fillRect(0, 0, w, 2);
                    g.fillRect(0, 2, 2, h - 2);
                    g.fillRect(w - 1, 1, 1, h - 1);
                    g.fillRect(1, h - 1, w - 2, 1);
                }
                else if (model.isRollover() && button.getClientProperty(
                               NO_BUTTON_ROLLOVER) == null) {
                    g.setColor(MetalLookAndFeel.getPrimaryControl());
                    g.drawRect(0, 0, w - 1, h - 1);
                    g.drawRect(2, 2, w - 5, h - 5);
                    g.setColor(MetalLookAndFeel.getControlDarkShadow());
                    g.drawRect(1, 1, w - 3, h - 3);
                }
                else {
                    g.setColor(MetalLookAndFeel.getControlDarkShadow());
                    g.drawRect(0, 0, w - 1, h - 1);
                }
            }
            else {
                g.setColor(MetalLookAndFeel.getInactiveControlTextColor());
                g.drawRect(0, 0, w - 1, h - 1);
                if ((c instanceof JButton) && ((JButton)c).isDefaultButton()) {
                    g.drawRect(1, 1, w - 3, h - 3);
                }
            }
        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            newInsets.set(3, 3, 3, 3);
            return newInsets;
        }
    }

    @SuppressWarnings("serial")
    private abstract static sealed class AbstractMetalWindowBorder
            extends AbstractBorder
            implements UIResource
            permits FrameBorder, DialogBorder, InternalFrameBorderImpl {

        protected Color background;
        protected Color highlight;
        protected Color shadow;

        private static final int CORNER = 14;

        @Override
        public final void paintBorder(Component c, Graphics g,
                                      int x, int y, int w, int h) {
            SwingUtilities3.paintBorder(c, g,
                                        x, y, w, h,
                                        this::paintUnscaledBorder);
        }

        protected abstract boolean isActive(Component c);

        protected abstract boolean isResizable(Component c);

        protected void updateColors(Component c) {
            if (isActive(c)) {
                background = MetalLookAndFeel.getPrimaryControlDarkShadow();
                highlight = MetalLookAndFeel.getPrimaryControlShadow();
                shadow = MetalLookAndFeel.getPrimaryControlInfo();
            } else {
                background = MetalLookAndFeel.getControlDarkShadow();
                highlight = MetalLookAndFeel.getControlShadow();
                shadow = MetalLookAndFeel.getControlInfo();
            }
        }

        private void paintUnscaledBorder(Component c, Graphics g,
                                         int width, int height,
                                         double scaleFactor) {
            updateColors(c);

            // scaled thickness
            int thickness = (int) Math.ceil(4 * scaleFactor);
            g.setColor(background);
            // Draw the bulk of the border
            for (int i = 0; i <= thickness; i++) {
                g.drawRect(i, i, width - (i * 2), height - (i * 2));
            }

            if (isResizable(c)) {
                //midpoint at which highlight & shadow lines
                //are positioned on the border
                int midPoint = thickness / 2;
                int strokeWidth = clipRound(scaleFactor);
                int offset = (((scaleFactor - strokeWidth) >= 0)
                              && ((strokeWidth % 2) != 0)) ? 1 : 0;

                int loc1 = (thickness % 2 == 0)
                           ? midPoint + strokeWidth / 2 - strokeWidth
                           : midPoint;
                int loc2 = (thickness % 2 == 0)
                           ? midPoint + strokeWidth / 2
                           : midPoint + strokeWidth;

                // scaled corner
                int corner = (int) Math.round(CORNER * scaleFactor);

                if (g instanceof Graphics2D) {
                    ((Graphics2D) g).setStroke(new BasicStroke((float) strokeWidth));
                }

                // Draw the Long highlight lines
                g.setColor(highlight);
                g.drawLine(corner + 1, loc2, width - corner, loc2); //top
                g.drawLine(loc2, corner + 1, loc2, height - corner); //left
                g.drawLine((width - offset) - loc1, corner + 1,
                        (width - offset) - loc1, height - corner); //right
                g.drawLine(corner + 1, (height - offset) - loc1,
                        width - corner, (height - offset) - loc1); //bottom

                // Draw the Long shadow lines
                g.setColor(shadow);
                g.drawLine(corner, loc1, width - corner - 1, loc1);
                g.drawLine(loc1, corner, loc1, height - corner - 1);
                g.drawLine((width - offset) - loc2, corner,
                        (width - offset) - loc2, height - corner - 1);
                g.drawLine(corner, (height - offset) - loc2,
                        width - corner - 1, (height - offset) - loc2);
            }
        }

        @Override
        public final Insets getBorderInsets(Component c, Insets newInsets) {
            newInsets.set(4, 4, 4, 4);
            return newInsets;
        }
    }

    @SuppressWarnings("serial")
    private static final class InternalFrameBorderImpl extends AbstractMetalWindowBorder {

        @Override
        protected boolean isActive(Component c) {
            return (c instanceof JInternalFrame
                    && ((JInternalFrame)c).isSelected());
        }

        @Override
        protected boolean isResizable(Component c) {
            return ((c instanceof JInternalFrame
                    && ((JInternalFrame) c).isResizable()));
        }
    }

    /**
     * The class represents the border of a {@code JInternalFrame}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class InternalFrameBorder extends AbstractBorder implements UIResource {

        private final InternalFrameBorderImpl border;

        /**
         * Constructs a {@code InternalFrameBorder}.
         */
        public InternalFrameBorder() {
            border = new InternalFrameBorderImpl();
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y,
                                int w, int h) {
            border.paintBorder(c, g, x, y, w, h);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets newInsets) {
            return border.getBorderInsets(c, newInsets);
        }
    }

    /**
     * Border for a Frame.
     * @since 1.4
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    static final class FrameBorder extends AbstractMetalWindowBorder implements UIResource {

        @Override
        protected boolean isActive(Component c) {
            Window window = SwingUtilities.getWindowAncestor(c);
            return (window != null && window.isActive());
        }

        @Override
        protected boolean isResizable(Component c) {
            Window window = SwingUtilities.getWindowAncestor(c);
            return ((window instanceof Frame)
                    && ((Frame) window).isResizable());
        }
    }

    /**
     * Border for a Frame.
     * @since 1.4
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    static sealed class DialogBorder
            extends AbstractMetalWindowBorder
            implements UIResource
            permits ErrorDialogBorder, QuestionDialogBorder, WarningDialogBorder {

        protected Color getActiveBackground() {
            return MetalLookAndFeel.getPrimaryControlDarkShadow();
        }

        protected final Color getActiveHighlight() {
            return MetalLookAndFeel.getPrimaryControlShadow();
        }

        protected final Color getActiveShadow() {
            return MetalLookAndFeel.getPrimaryControlInfo();
        }

        protected final Color getInactiveBackground() {
            return MetalLookAndFeel.getControlDarkShadow();
        }

        protected final Color getInactiveHighlight() {
            return MetalLookAndFeel.getControlShadow();
        }

        protected final Color getInactiveShadow() {
            return MetalLookAndFeel.getControlInfo();
        }

        @Override
        protected final void updateColors(Component c) {
            if (isActive(c)) {
                background = getActiveBackground();
                highlight = getActiveHighlight();
                shadow = getActiveShadow();
            } else {
                background = getInactiveBackground();
                highlight = getInactiveHighlight();
                shadow = getInactiveShadow();
            }
        }

        @Override
        protected final boolean isActive(Component c) {
            Window window = SwingUtilities.getWindowAncestor(c);
            return (window != null && window.isActive());
        }

        @Override
        protected final boolean isResizable(Component c) {
            Window window = SwingUtilities.getWindowAncestor(c);
            return ((window instanceof Dialog)
                    && ((Dialog) window).isResizable());
        }
    }

    /**
     * Border for an Error Dialog.
     * @since 1.4
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    static final class ErrorDialogBorder extends DialogBorder implements UIResource
    {
        protected Color getActiveBackground() {
            return UIManager.getColor("OptionPane.errorDialog.border.background");
        }
    }


    /**
     * Border for a QuestionDialog.  Also used for a JFileChooser and a
     * JColorChooser..
     * @since 1.4
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    static final class QuestionDialogBorder extends DialogBorder implements UIResource
    {
        protected Color getActiveBackground() {
            return UIManager.getColor("OptionPane.questionDialog.border.background");
        }
    }


    /**
     * Border for a Warning Dialog.
     * @since 1.4
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    static final class WarningDialogBorder extends DialogBorder implements UIResource
    {
        protected Color getActiveBackground() {
            return UIManager.getColor("OptionPane.warningDialog.border.background");
        }
    }


    /**
     * Border for a Palette.
     * @since 1.3
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class PaletteBorder extends AbstractBorder implements UIResource {
        int titleHeight = 0;

        /**
         * Constructs a {@code PaletteBorder}.
         */
        public PaletteBorder() {}

        public void paintBorder( Component c, Graphics g, int x, int y, int w, int h ) {

            g.translate(x,y);
            g.setColor(MetalLookAndFeel.getPrimaryControlDarkShadow());
            g.drawLine(0, 1, 0, h-2);
            g.drawLine(1, h-1, w-2, h-1);
            g.drawLine(w-1,  1, w-1, h-2);
            g.drawLine( 1, 0, w-2, 0);
            g.drawRect(1,1, w-3, h-3);
            g.translate(-x,-y);

        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            newInsets.set(1, 1, 1, 1);
            return newInsets;
        }
    }

    /**
     * The class represents the border of an option dialog.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class OptionDialogBorder extends AbstractBorder implements UIResource {
        int titleHeight = 0;

        /**
         * Constructs a {@code OptionDialogBorder}.
         */
        public OptionDialogBorder() {}

        public void paintBorder( Component c, Graphics g, int x, int y, int w, int h ) {

            g.translate(x,y);

            int messageType = JOptionPane.PLAIN_MESSAGE;
            if (c instanceof JInternalFrame) {
                Object obj = ((JInternalFrame) c).getClientProperty(
                              "JInternalFrame.messageType");
                if (obj instanceof Integer) {
                    messageType = (Integer) obj;
                }
            }

            Color borderColor;

            switch (messageType) {
            case(JOptionPane.ERROR_MESSAGE):
                borderColor = UIManager.getColor(
                    "OptionPane.errorDialog.border.background");
                break;
            case(JOptionPane.QUESTION_MESSAGE):
                borderColor = UIManager.getColor(
                    "OptionPane.questionDialog.border.background");
                break;
            case(JOptionPane.WARNING_MESSAGE):
                borderColor = UIManager.getColor(
                    "OptionPane.warningDialog.border.background");
                break;
            case(JOptionPane.INFORMATION_MESSAGE):
            case(JOptionPane.PLAIN_MESSAGE):
            default:
                borderColor = MetalLookAndFeel.getPrimaryControlDarkShadow();
                break;
            }

            g.setColor(borderColor);

              // Draw outermost lines
              g.drawLine( 1, 0, w-2, 0);
              g.drawLine( 0, 1, 0, h-2);
              g.drawLine( w-1, 1, w-1, h-2);
              g.drawLine( 1, h-1, w-2, h-1);

              // Draw the bulk of the border
              for (int i = 1; i < 3; i++) {
                  g.drawRect(i, i, w-(i*2)-1, h-(i*2)-1);
              }

            g.translate(-x,-y);

        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            newInsets.set(3, 3, 3, 3);
            return newInsets;
        }
    }

    /**
     * The class represents the border of a {@code JMenuBar}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class MenuBarBorder extends AbstractBorder implements UIResource {

        /**
         * The border insets.
         */
        protected static Insets borderInsets = new Insets( 1, 0, 1, 0 );

        /**
         * Constructs a {@code MenuBarBorder}.
         */
        public MenuBarBorder() {}

        public void paintBorder( Component c, Graphics g, int x, int y, int w, int h ) {
            g.translate(x, y);

            if (MetalLookAndFeel.usingOcean()) {
                // Only paint a border if we're not next to a horizontal toolbar
                if (c instanceof JMenuBar
                        && !MetalToolBarUI.doesMenuBarBorderToolBar((JMenuBar)c)) {
                    g.setColor(MetalLookAndFeel.getControl());
                    SwingUtilities2.drawHLine(g, 0, w - 1, h - 2);
                    g.setColor(UIManager.getColor("MenuBar.borderColor"));
                    SwingUtilities2.drawHLine(g, 0, w - 1, h - 1);
                }
            } else {
                g.setColor(MetalLookAndFeel.getControlShadow());
                SwingUtilities2.drawHLine(g, 0, w - 1, h - 1);
            }
            g.translate(-x, -y);
        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            if (MetalLookAndFeel.usingOcean()) {
                newInsets.set(0, 0, 2, 0);
            }
            else {
                newInsets.set(1, 0, 1, 0);
            }
            return newInsets;
        }
    }

    /**
     * The class represents the border of a {@code JMenuItem}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class MenuItemBorder extends AbstractBorder implements UIResource {

        /**
         * The border insets.
         */
        protected static Insets borderInsets = new Insets( 2, 2, 2, 2 );

        /**
         * Constructs a {@code MenuItemBorder}.
         */
        public MenuItemBorder() {}

        public void paintBorder( Component c, Graphics g, int x, int y, int w, int h ) {
            if (!(c instanceof JMenuItem)) {
                return;
            }
            JMenuItem b = (JMenuItem) c;
            ButtonModel model = b.getModel();

            g.translate( x, y );

            if ( c.getParent() instanceof JMenuBar ) {
                if ( model.isArmed() || model.isSelected() ) {
                    g.setColor( MetalLookAndFeel.getControlDarkShadow() );
                    g.drawLine( 0, 0, w - 2, 0 );
                    g.drawLine( 0, 0, 0, h - 1 );
                    g.drawLine( w - 2, 2, w - 2, h - 1 );

                    g.setColor( MetalLookAndFeel.getPrimaryControlHighlight() );
                    g.drawLine( w - 1, 1, w - 1, h - 1 );

                    g.setColor( MetalLookAndFeel.getMenuBackground() );
                    g.drawLine( w - 1, 0, w - 1, 0 );
                }
            } else {
                if (  model.isArmed() || ( c instanceof JMenu && model.isSelected() ) ) {
                    g.setColor( MetalLookAndFeel.getPrimaryControlDarkShadow() );
                    g.drawLine( 0, 0, w - 1, 0 );

                    g.setColor( MetalLookAndFeel.getPrimaryControlHighlight() );
                    g.drawLine( 0, h - 1, w - 1, h - 1 );
                } else {
                    g.setColor( MetalLookAndFeel.getPrimaryControlHighlight() );
                    g.drawLine( 0, 0, 0, h - 1 );
                }
            }

            g.translate( -x, -y );
        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            newInsets.set(2, 2, 2, 2);
            return newInsets;
        }
    }

    /**
     * The class represents the border of a {@code JPopupMenu}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class PopupMenuBorder extends AbstractBorder implements UIResource {

        /**
         * The border insets.
         */
        protected static Insets borderInsets = new Insets( 3, 1, 2, 1 );

        /**
         * Constructs a {@code PopupMenuBorder}.
         */
        public PopupMenuBorder() {}

        public void paintBorder( Component c, Graphics g, int x, int y, int w, int h ) {
            g.translate( x, y );

            g.setColor( MetalLookAndFeel.getPrimaryControlDarkShadow() );
            g.drawRect( 0, 0, w - 1, h - 1 );

            g.setColor( MetalLookAndFeel.getPrimaryControlHighlight() );
            g.drawLine( 1, 1, w - 2, 1 );
            g.drawLine( 1, 2, 1, 2 );
            g.drawLine( 1, h - 2, 1, h - 2 );

            g.translate( -x, -y );

        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            newInsets.set(3, 1, 2, 1);
            return newInsets;
        }
    }

    /**
     * The class represents the border of a rollover {@code Button}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class RolloverButtonBorder extends ButtonBorder {

        /**
         * Constructs a {@code RolloverButtonBorder}.
         */
        public RolloverButtonBorder() {}

        public void paintBorder( Component c, Graphics g, int x, int y, int w, int h ) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();

            if ( model.isRollover() && !( model.isPressed() && !model.isArmed() ) ) {
                super.paintBorder( c, g, x, y, w, h );
            }
        }

    }

    /**
     * A border which is like a Margin border but it will only honor the margin
     * if the margin has been explicitly set by the developer.
     *
     * Note: This is identical to the package private class
     * BasicBorders.RolloverMarginBorder and should probably be consolidated.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    static class RolloverMarginBorder extends EmptyBorder {

        public RolloverMarginBorder() {
            super(3,3,3,3); // hardcoded margin for JLF requirements.
        }

        public Insets getBorderInsets(Component c, Insets insets) {
            Insets margin = null;

            if (c instanceof AbstractButton) {
                margin = ((AbstractButton)c).getMargin();
            }
            if (margin == null || margin instanceof UIResource) {
                // default margin so replace
                insets.left = left;
                insets.top = top;
                insets.right = right;
                insets.bottom = bottom;
            } else {
                // Margin which has been explicitly set by the user.
                insets.left = margin.left;
                insets.top = margin.top;
                insets.right = margin.right;
                insets.bottom = margin.bottom;
            }
            return insets;
        }
    }

    /**
     * The class represents the border of a {@code JToolBar}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class ToolBarBorder extends AbstractBorder implements UIResource, SwingConstants
    {
        /**
         * The instance of {@code MetalBumps}.
         */
        private MetalBumps bumps = new MetalBumps( 10, 10,
                                      MetalLookAndFeel.getControlHighlight(),
                                      MetalLookAndFeel.getControlDarkShadow(),
                                     UIManager.getColor("ToolBar.background"));

        /**
         * Constructs a {@code ToolBarBorder}.
         */
        public ToolBarBorder() {}

        public void paintBorder( Component c, Graphics g, int x, int y, int w, int h )
        {
            if (!(c instanceof JToolBar)) {
                return;
            }
            g.translate( x, y );

            if ( ((JToolBar) c).isFloatable() )
            {
                if ( ((JToolBar) c).getOrientation() == HORIZONTAL )
                {
                    int shift = MetalLookAndFeel.usingOcean() ? -1 : 0;
                    bumps.setBumpArea( 10, h - 4 );
                    if( MetalUtils.isLeftToRight(c) ) {
                        bumps.paintIcon( c, g, 2, 2 + shift );
                    } else {
                        bumps.paintIcon( c, g, w-12,
                                         2 + shift );
                    }
                }
                else // vertical
                {
                    bumps.setBumpArea( w - 4, 10 );
                    bumps.paintIcon( c, g, 2, 2 );
                }

            }

            if (((JToolBar) c).getOrientation() == HORIZONTAL &&
                               MetalLookAndFeel.usingOcean()) {
                g.setColor(MetalLookAndFeel.getControl());
                g.drawLine(0, h - 2, w, h - 2);
                g.setColor(UIManager.getColor("ToolBar.borderColor"));
                g.drawLine(0, h - 1, w, h - 1);
            }

            g.translate( -x, -y );
        }

        public Insets getBorderInsets(Component c, Insets newInsets) {
            if (MetalLookAndFeel.usingOcean()) {
                newInsets.set(1, 2, 3, 2);
            }
            else {
                newInsets.top = newInsets.left = newInsets.bottom = newInsets.right = 2;
            }

            if (!(c instanceof JToolBar)) {
                return newInsets;
            }
            if ( ((JToolBar) c).isFloatable() ) {
                if ( ((JToolBar) c).getOrientation() == HORIZONTAL ) {
                    if (c.getComponentOrientation().isLeftToRight()) {
                        newInsets.left = 16;
                    } else {
                        newInsets.right = 16;
                    }
                } else {// vertical
                    newInsets.top = 16;
                }
            }

            Insets margin = ((JToolBar) c).getMargin();

            if ( margin != null ) {
                newInsets.left   += margin.left;
                newInsets.top    += margin.top;
                newInsets.right  += margin.right;
                newInsets.bottom += margin.bottom;
            }

            return newInsets;
        }
    }

    private static Border buttonBorder;

    /**
     * Returns a border instance for a {@code JButton}.
     *
     * @return a border instance for a {@code JButton}
     * @since 1.3
     */
    public static Border getButtonBorder() {
        if (buttonBorder == null) {
            buttonBorder = new BorderUIResource.CompoundBorderUIResource(
                                                   new MetalBorders.ButtonBorder(),
                                                   new BasicBorders.MarginBorder());
        }
        return buttonBorder;
    }

    private static Border textBorder;

    /**
     * Returns a border instance for a text component.
     *
     * @return a border instance for a text component
     * @since 1.3
     */
    public static Border getTextBorder() {
        if (textBorder == null) {
            textBorder = new BorderUIResource.CompoundBorderUIResource(
                                                   new MetalBorders.Flush3DBorder(),
                                                   new BasicBorders.MarginBorder());
        }
        return textBorder;
    }

    private static Border textFieldBorder;

    /**
     * Returns a border instance for a {@code JTextField}.
     *
     * @return a border instance for a {@code JTextField}
     * @since 1.3
     */
    public static Border getTextFieldBorder() {
        if (textFieldBorder == null) {
            textFieldBorder = new BorderUIResource.CompoundBorderUIResource(
                                                   new MetalBorders.TextFieldBorder(),
                                                   new BasicBorders.MarginBorder());
        }
        return textFieldBorder;
    }

    /**
     * Border for a {@code JTextField}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class TextFieldBorder extends Flush3DBorder {

        /**
         * Constructs a {@code TextFieldBorder}.
         */
        public TextFieldBorder() {}

        public void paintBorder(Component c, Graphics g, int x, int y,
                                int w, int h) {

          if (!(c instanceof JTextComponent)) {
                // special case for non-text components (bug ID 4144840)
                if (c.isEnabled()) {
                    MetalUtils.drawFlush3DBorder(g, x, y, w, h);
                } else {
                    MetalUtils.drawDisabledBorder(g, x, y, w, h);
                }
                return;
            }

            if (c.isEnabled() && ((JTextComponent)c).isEditable()) {
                MetalUtils.drawFlush3DBorder(g, x, y, w, h);
            } else {
                MetalUtils.drawDisabledBorder(g, x, y, w, h);
            }

        }
    }

    /**
     * The class represents the border of a {@code JScrollPane}.
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class ScrollPaneBorder extends AbstractBorder implements UIResource {
        /**
         * Constructs a {@code ScrollPaneBorder}.
         */
        public ScrollPaneBorder() {}

        public void paintBorder(Component c, Graphics g, int x, int y,
                          int w, int h) {

            if (!(c instanceof JScrollPane)) {
                return;
            }
            JScrollPane scroll = (JScrollPane)c;
            JComponent colHeader = scroll.getColumnHeader();
            int colHeaderHeight = 0;
            if (colHeader != null)
               colHeaderHeight = colHeader.getHeight();

            JComponent rowHeader = scroll.getRowHeader();
            int rowHeaderWidth = 0;
            if (rowHeader != null)
               rowHeaderWidth = rowHeader.getWidth();


            g.translate( x, y);

            g.setColor( MetalLookAndFeel.getControlDarkShadow() );
            g.drawRect( 0, 0, w-2, h-2 );
            g.setColor( MetalLookAndFeel.getControlHighlight() );

            g.drawLine( w-1, 1, w-1, h-1);
            g.drawLine( 1, h-1, w-1, h-1);

            g.setColor( MetalLookAndFeel.getControl() );
            g.drawLine( w-2, 2+colHeaderHeight, w-2, 2+colHeaderHeight );
            g.drawLine( 1+rowHeaderWidth, h-2, 1+rowHeaderWidth, h-2 );

            g.translate( -x, -y);

        }

        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(1, 1, 2, 2);
            return insets;
        }
    }

    private static Border toggleButtonBorder;

    /**
     * Returns a border instance for a {@code JToggleButton}.
     *
     * @return a border instance for a {@code JToggleButton}
     * @since 1.3
     */
    public static Border getToggleButtonBorder() {
        if (toggleButtonBorder == null) {
            toggleButtonBorder = new BorderUIResource.CompoundBorderUIResource(
                                                   new MetalBorders.ToggleButtonBorder(),
                                                   new BasicBorders.MarginBorder());
        }
        return toggleButtonBorder;
    }

    /**
     * Border for a {@code JToggleButton}.
     *
     * @since 1.3
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class ToggleButtonBorder extends ButtonBorder {
        /**
         * Constructs a {@code ToggleButtonBorder}.
         */
        public ToggleButtonBorder() {}

        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            AbstractButton button = (AbstractButton)c;
            ButtonModel model = button.getModel();
            if (MetalLookAndFeel.usingOcean()) {
                if(model.isArmed() || !button.isEnabled()) {
                    super.paintBorder(c, g, x, y, w, h);
                }
                else {
                 g.setColor(MetalLookAndFeel.getControlDarkShadow());
                 g.drawRect(0, 0, w - 1, h - 1);
            }
            return;
        }
            if (! c.isEnabled() ) {
                MetalUtils.drawDisabledBorder( g, x, y, w-1, h-1 );
            } else {
                if ( model.isPressed() && model.isArmed() ) {
                   MetalUtils.drawPressed3DBorder( g, x, y, w, h );
                } else if ( model.isSelected() ) {
                    MetalUtils.drawDark3DBorder( g, x, y, w, h );
                } else {
                    MetalUtils.drawFlush3DBorder( g, x, y, w, h );
                }
            }
        }
    }

    /**
     * Border for a Table Header
     * @since 1.3
     */
    @SuppressWarnings("serial") // Superclass is not serializable across versions
    public static class TableHeaderBorder extends javax.swing.border.AbstractBorder {

        /**
         * Constructs a {@code TableHeaderBorder}.
         */
        public TableHeaderBorder() {}

        /**
         * The border insets.
         */
        protected Insets editorBorderInsets = new Insets( 2, 2, 2, 0 );

        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            g.translate( x, y );

            g.setColor( MetalLookAndFeel.getControlDarkShadow() );
            SwingUtilities2.drawVLine(g, w-1, 0, h-1);
            SwingUtilities2.drawHLine(g, 1, w-1, h-1);
            g.setColor( MetalLookAndFeel.getControlHighlight() );
            SwingUtilities2.drawHLine(g, 0, w-2, 0);
            SwingUtilities2.drawVLine(g, 0, 0, h-2);

            g.translate( -x, -y );
        }

        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(2, 2, 2, 2);
            return insets;
        }
    }

    /**
     * Returns a border instance for a Desktop Icon.
     *
     * @return a border instance for a Desktop Icon
     * @since 1.3
     */
    public static Border getDesktopIconBorder() {
        return new BorderUIResource.CompoundBorderUIResource(
                                          new LineBorder(MetalLookAndFeel.getControlDarkShadow(), 1),
                                          new MatteBorder (2,2,1,2, MetalLookAndFeel.getControl()));
    }

    static Border getToolBarRolloverBorder() {
        if (MetalLookAndFeel.usingOcean()) {
            return new CompoundBorder(
                new MetalBorders.ButtonBorder(),
                new MetalBorders.RolloverMarginBorder());
        }
        return new CompoundBorder(new MetalBorders.RolloverButtonBorder(),
                                  new MetalBorders.RolloverMarginBorder());
    }

    static Border getToolBarNonrolloverBorder() {
        if (MetalLookAndFeel.usingOcean()) {
            new CompoundBorder(
                new MetalBorders.ButtonBorder(),
                new MetalBorders.RolloverMarginBorder());
        }
        return new CompoundBorder(new MetalBorders.ButtonBorder(),
                                  new MetalBorders.RolloverMarginBorder());
    }
}
