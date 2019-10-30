/*
 * Copyright (c) 1997, 2019, Oracle and/or its affiliates. All rights reserved.
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

package javax.swing.plaf.basic;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.text.View;
import sun.swing.SwingUtilities2;
import sun.awt.AppContext;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * RadioButtonUI implementation for BasicRadioButtonUI
 *
 * @author Jeff Dinkins
 */
public class BasicRadioButtonUI extends BasicToggleButtonUI
{
    private static final Object BASIC_RADIO_BUTTON_UI_KEY = new Object();

    /**
     * The icon.
     */
    protected Icon icon;

    private boolean defaults_initialized = false;

    private static final String propertyPrefix = "RadioButton" + ".";

    private KeyListener keyListener = null;

    // ********************************
    //        Create PLAF
    // ********************************

    /**
     * Returns an instance of {@code BasicRadioButtonUI}.
     *
     * @param b a component
     * @return an instance of {@code BasicRadioButtonUI}
     */
    public static ComponentUI createUI(JComponent b) {
        AppContext appContext = AppContext.getAppContext();
        BasicRadioButtonUI radioButtonUI =
                (BasicRadioButtonUI) appContext.get(BASIC_RADIO_BUTTON_UI_KEY);
        if (radioButtonUI == null) {
            radioButtonUI = new BasicRadioButtonUI();
            appContext.put(BASIC_RADIO_BUTTON_UI_KEY, radioButtonUI);
        }
        return radioButtonUI;
    }

    @Override
    protected String getPropertyPrefix() {
        return propertyPrefix;
    }

    // ********************************
    //        Install PLAF
    // ********************************
    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        if(!defaults_initialized) {
            icon = UIManager.getIcon(getPropertyPrefix() + "icon");
            defaults_initialized = true;
        }
    }

    // ********************************
    //        Uninstall PLAF
    // ********************************
    @Override
    protected void uninstallDefaults(AbstractButton b) {
        super.uninstallDefaults(b);
        defaults_initialized = false;
    }

    /**
     * Returns the default icon.
     *
     * @return the default icon
     */
    public Icon getDefaultIcon() {
        return icon;
    }

    // ********************************
    //        Install Listeners
    // ********************************
    @Override
    protected void installListeners(AbstractButton button) {
        super.installListeners(button);

        // Only for JRadioButton
        if (!(button instanceof JRadioButton))
            return;

        keyListener = createKeyListener();
        button.addKeyListener(keyListener);

        // Need to get traversal key event
        button.setFocusTraversalKeysEnabled(false);

        // Map actions to the arrow keys
        button.getActionMap().put("Previous", new SelectPreviousBtn());
        button.getActionMap().put("Next", new SelectNextBtn());

        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            put(KeyStroke.getKeyStroke("UP"), "Previous");
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            put(KeyStroke.getKeyStroke("DOWN"), "Next");
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            put(KeyStroke.getKeyStroke("LEFT"), "Previous");
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            put(KeyStroke.getKeyStroke("RIGHT"), "Next");
    }

    // ********************************
    //        UnInstall Listeners
    // ********************************
    @Override
    protected void uninstallListeners(AbstractButton button) {
        super.uninstallListeners(button);

        // Only for JRadioButton
        if (!(button instanceof JRadioButton))
            return;

        // Unmap actions from the arrow keys
        button.getActionMap().remove("Previous");
        button.getActionMap().remove("Next");
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .remove(KeyStroke.getKeyStroke("UP"));
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .remove(KeyStroke.getKeyStroke("DOWN"));
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .remove(KeyStroke.getKeyStroke("LEFT"));
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .remove(KeyStroke.getKeyStroke("RIGHT"));

        if (keyListener != null) {
            button.removeKeyListener(keyListener);
            keyListener = null;
        }
    }

    /* These Dimensions/Rectangles are allocated once for all
     * RadioButtonUI.paint() calls.  Re-using rectangles
     * rather than allocating them in each paint call substantially
     * reduced the time it took paint to run.  Obviously, this
     * method can't be re-entered.
     */
    private static Dimension size = new Dimension();
    private static Rectangle viewRect = new Rectangle();
    private static Rectangle iconRect = new Rectangle();
    private static Rectangle textRect = new Rectangle();

    /**
     * paint the radio button
     */
    @Override
    public synchronized void paint(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        ButtonModel model = b.getModel();

        Font f = c.getFont();
        g.setFont(f);
        FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, f);

        Insets i = c.getInsets();
        size = b.getSize(size);
        viewRect.x = i.left;
        viewRect.y = i.top;
        viewRect.width = size.width - (i.right + viewRect.x);
        viewRect.height = size.height - (i.bottom + viewRect.y);
        iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;
        textRect.x = textRect.y = textRect.width = textRect.height = 0;

        Icon altIcon = b.getIcon();
        Icon selectedIcon = null;
        Icon disabledIcon = null;

        String text = SwingUtilities.layoutCompoundLabel(
            c, fm, b.getText(), altIcon != null ? altIcon : getDefaultIcon(),
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            viewRect, iconRect, textRect,
            b.getText() == null ? 0 : b.getIconTextGap());

        // fill background
        if(c.isOpaque()) {
            g.setColor(b.getBackground());
            g.fillRect(0,0, size.width, size.height);
        }


        // Paint the radio button
        if(altIcon != null) {

            if(!model.isEnabled()) {
                if(model.isSelected()) {
                   altIcon = b.getDisabledSelectedIcon();
                } else {
                   altIcon = b.getDisabledIcon();
                }
            } else if(model.isPressed() && model.isArmed()) {
                altIcon = b.getPressedIcon();
                if(altIcon == null) {
                    // Use selected icon
                    altIcon = b.getSelectedIcon();
                }
            } else if(model.isSelected()) {
                if(b.isRolloverEnabled() && model.isRollover()) {
                        altIcon = b.getRolloverSelectedIcon();
                        if (altIcon == null) {
                                altIcon = b.getSelectedIcon();
                        }
                } else {
                        altIcon = b.getSelectedIcon();
                }
            } else if(b.isRolloverEnabled() && model.isRollover()) {
                altIcon = b.getRolloverIcon();
            }

            if(altIcon == null) {
                altIcon = b.getIcon();
            }

            altIcon.paintIcon(c, g, iconRect.x, iconRect.y);

        } else {
            getDefaultIcon().paintIcon(c, g, iconRect.x, iconRect.y);
        }


        // Draw the Text
        if(text != null) {
            View v = (View) c.getClientProperty(BasicHTML.propertyKey);
            if (v != null) {
                v.paint(g, textRect);
            } else {
                paintText(g, b, textRect, text);
            }
            if(b.hasFocus() && b.isFocusPainted() &&
               textRect.width > 0 && textRect.height > 0 ) {
                paintFocus(g, textRect, size);
            }
        }
    }

    /**
     * Paints focused radio button.
     *
     * @param g an instance of {@code Graphics}
     * @param textRect bounds
     * @param size the size of radio button
     */
    protected void paintFocus(Graphics g, Rectangle textRect, Dimension size) {
    }


    /* These Insets/Rectangles are allocated once for all
     * RadioButtonUI.getPreferredSize() calls.  Re-using rectangles
     * rather than allocating them in each call substantially
     * reduced the time it took getPreferredSize() to run.  Obviously,
     * this method can't be re-entered.
     */
    private static Rectangle prefViewRect = new Rectangle();
    private static Rectangle prefIconRect = new Rectangle();
    private static Rectangle prefTextRect = new Rectangle();
    private static Insets prefInsets = new Insets(0, 0, 0, 0);

    /**
     * The preferred size of the radio button
     */
    @Override
    public Dimension getPreferredSize(JComponent c) {
        if(c.getComponentCount() > 0) {
            return null;
        }

        AbstractButton b = (AbstractButton) c;

        String text = b.getText();

        Icon buttonIcon = b.getIcon();
        if(buttonIcon == null) {
            buttonIcon = getDefaultIcon();
        }

        Font font = b.getFont();
        FontMetrics fm = b.getFontMetrics(font);

        prefViewRect.x = prefViewRect.y = 0;
        prefViewRect.width = Short.MAX_VALUE;
        prefViewRect.height = Short.MAX_VALUE;
        prefIconRect.x = prefIconRect.y = prefIconRect.width = prefIconRect.height = 0;
        prefTextRect.x = prefTextRect.y = prefTextRect.width = prefTextRect.height = 0;

        SwingUtilities.layoutCompoundLabel(
            c, fm, text, buttonIcon,
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            prefViewRect, prefIconRect, prefTextRect,
            text == null ? 0 : b.getIconTextGap());

        // find the union of the icon and text rects (from Rectangle.java)
        int x1 = Math.min(prefIconRect.x, prefTextRect.x);
        int x2 = Math.max(prefIconRect.x + prefIconRect.width,
                          prefTextRect.x + prefTextRect.width);
        int y1 = Math.min(prefIconRect.y, prefTextRect.y);
        int y2 = Math.max(prefIconRect.y + prefIconRect.height,
                          prefTextRect.y + prefTextRect.height);
        int width = x2 - x1;
        int height = y2 - y1;

        prefInsets = b.getInsets(prefInsets);
        width += prefInsets.left + prefInsets.right;
        height += prefInsets.top + prefInsets.bottom;
        return new Dimension(width, height);
    }

    /////////////////////////// Private functions ////////////////////////
    /**
     * Creates the key listener to handle tab navigation in JRadioButton Group.
     */
    private KeyListener createKeyListener() {
         if (keyListener == null) {
            keyListener = new KeyHandler();
        }
        return keyListener;
    }


    private boolean isValidRadioButtonObj(Object obj) {
        return ((obj instanceof JRadioButton) &&
                    ((JRadioButton) obj).isVisible() &&
                    ((JRadioButton) obj).isEnabled());
    }

    /**
     * Select radio button based on "Previous" or "Next" operation
     *
     * @param event, the event object.
     * @param next, indicate if it's next one
     */
    private void selectRadioButton(ActionEvent event, boolean next) {
        // Get the source of the event.
        Object eventSrc = event.getSource();

        // Check whether the source is JRadioButton, it so, whether it is visible
        if (!isValidRadioButtonObj(eventSrc))
            return;

        ButtonGroupInfo btnGroupInfo = new ButtonGroupInfo((JRadioButton)eventSrc);
        btnGroupInfo.selectNewButton(next);
    }

    /////////////////////////// Inner Classes ////////////////////////
    @SuppressWarnings("serial")
    private class SelectPreviousBtn extends AbstractAction {
        public SelectPreviousBtn() {
            super("Previous");
        }

        public void actionPerformed(ActionEvent e) {
           BasicRadioButtonUI.this.selectRadioButton(e, false);
        }
    }

    @SuppressWarnings("serial")
    private class SelectNextBtn extends AbstractAction{
        public SelectNextBtn() {
            super("Next");
        }

        public void actionPerformed(ActionEvent e) {
            BasicRadioButtonUI.this.selectRadioButton(e, true);
        }
    }

    /**
     * ButtonGroupInfo, used to get related info in button group
     * for given radio button
     */
    private class ButtonGroupInfo {

        JRadioButton activeBtn = null;

        JRadioButton firstBtn = null;
        JRadioButton lastBtn = null;

        JRadioButton previousBtn = null;
        JRadioButton nextBtn = null;

        HashSet<JRadioButton> btnsInGroup = null;

        boolean srcFound = false;
        public ButtonGroupInfo(JRadioButton btn) {
            activeBtn = btn;
            btnsInGroup = new HashSet<JRadioButton>();
        }

        // Check if given object is in the button group
        boolean containsInGroup(Object obj){
           return btnsInGroup.contains(obj);
        }

        // Check if the next object to gain focus belongs
        // to the button group or not
        Component getFocusTransferBaseComponent(boolean next){
            return firstBtn;
        }

        boolean getButtonGroupInfo() {
            if (activeBtn == null)
                return false;

            btnsInGroup.clear();

            // Get the button model from the source.
            ButtonModel model = activeBtn.getModel();
            if (!(model instanceof DefaultButtonModel))
                return false;

            // If the button model is DefaultButtonModel, and use it, otherwise return.
            DefaultButtonModel bm = (DefaultButtonModel) model;

            // get the ButtonGroup of the button from the button model
            ButtonGroup group = bm.getGroup();
            if (group == null)
                return false;

            // Get all the buttons in the group
            Enumeration<AbstractButton> e = group.getElements();
            if (e == null)
                return false;

            while (e.hasMoreElements()) {
                AbstractButton curElement = e.nextElement();
                if (!isValidRadioButtonObj(curElement))
                    continue;

                btnsInGroup.add((JRadioButton) curElement);

                // If firstBtn is not set yet, curElement is that first button
                if (null == firstBtn)
                    firstBtn = (JRadioButton) curElement;

                if (activeBtn == curElement)
                    srcFound = true;
                else if (!srcFound) {
                    // The source has not been yet found and the current element
                    // is the last previousBtn
                    previousBtn = (JRadioButton) curElement;
                } else if (nextBtn == null) {
                    // The source has been found and the current element
                    // is the next valid button of the list
                    nextBtn = (JRadioButton) curElement;
                }

                // Set new last "valid" JRadioButton of the list
                lastBtn = (JRadioButton) curElement;
            }

            return true;
        }

        /**
          * Find the new radio button that focus needs to be
          * moved to in the group, select the button
          *
          * @param next, indicate if it's arrow up/left or down/right
          */
        void selectNewButton(boolean next) {
            if (!getButtonGroupInfo())
                return;

            if (srcFound) {
                JRadioButton newSelectedBtn = null;
                if (next) {
                    // Select Next button. Cycle to the first button if the source
                    // button is the last of the group.
                    newSelectedBtn = (null == nextBtn) ? firstBtn : nextBtn;
                } else {
                    // Select previous button. Cycle to the last button if the source
                    // button is the first button of the group.
                    newSelectedBtn = (null == previousBtn) ? lastBtn : previousBtn;
                }
                if (newSelectedBtn != null &&
                    (newSelectedBtn != activeBtn)) {
                    ButtonModel btnModel = newSelectedBtn.getModel();
                    btnModel.setPressed(true);
                    btnModel.setArmed(true);
                    newSelectedBtn.requestFocusInWindow();
                    newSelectedBtn.setSelected(true);
                    btnModel.setPressed(false);
                    btnModel.setArmed(false);
                }
            }
        }

        /**
          * Find the button group the passed in JRadioButton belongs to, and
          * move focus to next component of the last button in the group
          * or previous component of first button
          *
          * @param next, indicate if jump to next component or previous
          */
        void jumpToNextComponent(boolean next) {
            if (!getButtonGroupInfo()){
                // In case the button does not belong to any group, it needs
                // to be treated as a component
                if (activeBtn != null){
                    lastBtn = activeBtn;
                    firstBtn = activeBtn;
                }
                else
                    return;
            }

            // Update the component we will use as base to transfer
            // focus from
            JComponent compTransferFocusFrom = activeBtn;

            // If next component in the parent window is not in
            // the button group, current active button will be
            // base, otherwise, the base will be first or last
            // button in the button group
            Component focusBase = getFocusTransferBaseComponent(next);
            if (focusBase != null){
                if (next) {
                    KeyboardFocusManager.
                        getCurrentKeyboardFocusManager().focusNextComponent(focusBase);
                } else {
                    KeyboardFocusManager.
                        getCurrentKeyboardFocusManager().focusPreviousComponent(focusBase);
                }
            }
        }
    }

    /**
     * Radiobutton KeyListener
     */
    private class KeyHandler implements KeyListener {

        // This listener checks if the key event is a focus traversal key event
        // on a radio button, consume the event if so and move the focus
        // to next/previous component
        public void keyPressed(KeyEvent e) {
            AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStrokeForEvent(e);
            if (stroke != null && e.getSource() instanceof JRadioButton) {
                JRadioButton source = (JRadioButton) e.getSource();
                boolean next = isFocusTraversalKey(source,
                        KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                        stroke);
                if (next || isFocusTraversalKey(source,
                        KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                        stroke)) {
                    e.consume();
                    ButtonGroupInfo btnGroupInfo = new ButtonGroupInfo(source);
                    btnGroupInfo.jumpToNextComponent(next);
                }
            }
        }

        private boolean isFocusTraversalKey(JComponent c, int id,
                                            AWTKeyStroke stroke) {
            Set<AWTKeyStroke> keys = c.getFocusTraversalKeys(id);
            return keys != null && keys.contains(stroke);
        }

        public void keyReleased(KeyEvent e) {
        }

        public void keyTyped(KeyEvent e) {
        }
    }
}
