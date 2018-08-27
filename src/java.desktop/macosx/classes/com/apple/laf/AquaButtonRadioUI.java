/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.apple.laf;

import javax.swing.JComponent;
import javax.swing.ImageIcon;
import javax.swing.JRadioButton;
import javax.swing.Icon;
import javax.swing.AbstractButton;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import javax.swing.DefaultButtonModel;
import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.plaf.ComponentUI;

import java.awt.Component;
import java.awt.AWTKeyStroke;
import java.awt.KeyboardFocusManager;

import java.awt.event.ActionEvent;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;

import apple.laf.JRSUIConstants.Widget;
import com.apple.laf.AquaUtilControlSize.SizeVariant;
import com.apple.laf.AquaUtilControlSize.SizeDescriptor;
import com.apple.laf.AquaUtils.RecyclableSingleton;
import com.apple.laf.AquaUtils.RecyclableSingletonFromDefaultConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.Enumeration;

public class AquaButtonRadioUI extends AquaButtonLabeledUI {
    private KeyListener keyListener = null;

    @SuppressWarnings("serial")
    private class SelectPreviousBtn extends AbstractAction {
        public SelectPreviousBtn() {
            super("Previous");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            AquaButtonRadioUI.this.selectRadioButton(e, false);
        }
    }

    @SuppressWarnings("serial")
    private class SelectNextBtn extends AbstractAction {
        public SelectNextBtn() {
            super("Next");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            AquaButtonRadioUI.this.selectRadioButton(e, true);
        }
    }

    private static final RecyclableSingleton<AquaButtonRadioUI> instance = new RecyclableSingletonFromDefaultConstructor<AquaButtonRadioUI>(AquaButtonRadioUI.class);
    private static final RecyclableSingleton<ImageIcon> sizingIcon = new RecyclableSingleton<ImageIcon>() {
        protected ImageIcon getInstance() {
            return new ImageIcon(AquaNativeResources.getRadioButtonSizerImage());
        }
    };

    public static ComponentUI createUI(final JComponent b) {
        return instance.get();
    }

    public static Icon getSizingRadioButtonIcon() {
        return sizingIcon.get();
    }

    protected String getPropertyPrefix() {
        return "RadioButton" + ".";
    }

    protected AquaButtonBorder getPainter() {
        return new RadioButtonBorder();
    }

    public static class RadioButtonBorder extends LabeledButtonBorder {
        public RadioButtonBorder() {
            super(new SizeDescriptor(new SizeVariant().replaceMargins("RadioButton.margin")));
            painter.state.set(Widget.BUTTON_RADIO);
        }

        public RadioButtonBorder(final RadioButtonBorder other) {
            super(other);
        }
    }

    private KeyListener createKeyListener() {
        if (keyListener == null) {
            keyListener = new KeyHandler();
        }

        return keyListener;
    }

    private boolean isValidRadioButtonObj(Object obj) {
        return ((obj instanceof JRadioButton) &&
                    ((JRadioButton)obj).isVisible() &&
                    ((JRadioButton)obj).isEnabled());
    }

    @Override
    protected void installListeners(AbstractButton button) {
        super.installListeners(button);

        //Only for JRadioButton
        if (!(button instanceof JRadioButton))
            return;

        keyListener = createKeyListener();
        button.addKeyListener(keyListener);

        button.setFocusTraversalKeysEnabled(false);

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

    @Override
    protected void uninstallListeners(AbstractButton button) {
        super.uninstallListeners(button);

        //Only for JRadioButton
        if (!(button instanceof JRadioButton))
            return;

        //Unmap actions from the arrow keys.
        button.getActionMap().remove("Previous");
        button.getActionMap().remove("Next");

        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            remove(KeyStroke.getKeyStroke("UP"));
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            remove(KeyStroke.getKeyStroke("DOWN"));
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            remove(KeyStroke.getKeyStroke("LEFT"));
        button.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            remove(KeyStroke.getKeyStroke("RIGHT"));

        if (keyListener != null ) {
            button.removeKeyListener(keyListener);
            keyListener = null;
        }
    }

    /**
     * Select radio button based on "Previous" or "Next" operation
     *
     * @param event, the event object.
     * @param next, indicate if it's next one
     */
    private void selectRadioButton(ActionEvent event, boolean next) {
        Object eventSrc = event.getSource();

        //Check whether the source is JRadioButton, if so, whether it is visible
        if (!isValidRadioButtonObj(eventSrc))
            return;

        ButtonGroupInfo btnGroupInfo = new ButtonGroupInfo((JRadioButton)eventSrc);
        btnGroupInfo.selectNewButton(next);
    }

    /**
     * ButtonGroupInfo, used to get related info in button group
     * for given radio button.
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

        //Check if given object is in the button group
        boolean containsInGroup(Object obj) {
            return btnsInGroup.contains(obj);
        }

        //Check if the next object to gain focus belongs
        //to the button group or not
        Component getFocusTransferBaseComponent(boolean next) {
            return firstBtn;
        }

        boolean getButtonGroupInfo() {
            if (activeBtn == null)
                return false;

            btnsInGroup.clear();

            //Get the button model from ths source.
            ButtonModel model = activeBtn.getModel();
            if (!(model instanceof DefaultButtonModel))
                return false;

            // If the button model is DefaultButtonModel, and use it, otherwise return.
            DefaultButtonModel bm = (DefaultButtonModel) model;

            //get the ButtonGroup of the button from the button model
            ButtonGroup group = bm.getGroup();
            if (group == null)
                return false;

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
                    firstBtn = (JRadioButton)curElement;

                if (activeBtn == curElement)
                    srcFound = true;
                else if (!srcFound) {
                    //The source has not been yet found and the current element
                    // is the last previousBtn
                    previousBtn = (JRadioButton) curElement;
                } else if (nextBtn == null) {
                    //The source has been found and the current element
                    //is the next valid button of the list
                    nextBtn = (JRadioButton) curElement;
                }

                //Set new last "valid" JRadioButton of the list
                lastBtn = (JRadioButton)curElement;
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
                    //Select Next button. Cycle to the first button if the source
                    //button is the last of the group.
                    newSelectedBtn = (null == nextBtn) ? firstBtn : nextBtn;
                } else {
                    //Select previous button. Cycle to the last button if the source
                    //button is the first button of the group.
                    newSelectedBtn = (null == previousBtn) ? lastBtn: previousBtn;
                }
                if (newSelectedBtn != null && newSelectedBtn != activeBtn) {
                    newSelectedBtn.requestFocusInWindow();
                    newSelectedBtn.setSelected(true);
                }
            }
        }

        /**
         * Find the button group the passed in JRadioButton belongs to, and
         * move focus to next component of the last button in the group
         * or previous compoennt of first button
         *
         * @param next, indicate if jump to next component or previous
         */
        void jumpToNextComponent(boolean next) {
            if (!getButtonGroupInfo()) {
                //In case the button does not belong to any group, it needs
                //to be treated as a component
                if (activeBtn != null) {
                    lastBtn = activeBtn;
                    firstBtn = activeBtn;
                } else
                    return;
            }

            //If next component in the parent window is not in the button
            //group, current active button will be base, otherwise, the base
            // will be first or last button in the button group
            Component focusBase = getFocusTransferBaseComponent(next);
            if (focusBase != null) {
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
        //This listener checks if the key event is a focus traversal key event
        // on a radio button, consume the event if so and move the focus
        // to next/previous component
        @Override
        public void keyPressed(KeyEvent e) {
            AWTKeyStroke stroke = AWTKeyStroke.getAWTKeyStrokeForEvent(e);
            if (stroke != null && e.getSource() instanceof JRadioButton) {
                JRadioButton source = (JRadioButton) e.getSource();
                boolean next = isFocusTraversalKey(source,
                                KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, stroke);
                if (next || isFocusTraversalKey(source,
                                KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, stroke)) {
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

        @Override public void keyReleased(KeyEvent e) {}

        @Override public void keyTyped(KeyEvent e) {}
    }
}
