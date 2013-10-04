/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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
package javax.swing;

import java.awt.*;
import java.awt.event.*;

import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.accessibility.*;

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;


/**
 * An implementation of a two-state button.
 * The <code>JRadioButton</code> and <code>JCheckBox</code> classes
 * are subclasses of this class.
 * For information on using them see
 * <a
 href="http://docs.oracle.com/javase/tutorial/uiswing/components/button.html">How to Use Buttons, Check Boxes, and Radio Buttons</a>,
 * a section in <em>The Java Tutorial</em>.
 * <p>
 * Buttons can be configured, and to some degree controlled, by
 * <code><a href="Action.html">Action</a></code>s.  Using an
 * <code>Action</code> with a button has many benefits beyond directly
 * configuring a button.  Refer to <a href="Action.html#buttonActions">
 * Swing Components Supporting <code>Action</code></a> for more
 * details, and you can find more information in <a
 * href="http://docs.oracle.com/javase/tutorial/uiswing/misc/action.html">How
 * to Use Actions</a>, a section in <em>The Java Tutorial</em>.
 * <p>
 * <strong>Warning:</strong> Swing is not thread safe. For more
 * information see <a
 * href="package-summary.html#threading">Swing's Threading
 * Policy</a>.
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
 * @beaninfo
 *   attribute: isContainer false
 * description: An implementation of a two-state button.
 *
 * @see JRadioButton
 * @see JCheckBox
 * @author Jeff Dinkins
 */
public class JToggleButton extends AbstractButton implements Accessible {

    /**
     * @see #getUIClassID
     * @see #readObject
     */
    private static final String uiClassID = "ToggleButtonUI";

    /**
     * Creates an initially unselected toggle button
     * without setting the text or image.
     */
    public JToggleButton () {
        this(null, null, false);
    }

    /**
     * Creates an initially unselected toggle button
     * with the specified image but no text.
     *
     * @param icon  the image that the button should display
     */
    public JToggleButton(Icon icon) {
        this(null, icon, false);
    }

    /**
     * Creates a toggle button with the specified image
     * and selection state, but no text.
     *
     * @param icon  the image that the button should display
     * @param selected  if true, the button is initially selected;
     *                  otherwise, the button is initially unselected
     */
    public JToggleButton(Icon icon, boolean selected) {
        this(null, icon, selected);
    }

    /**
     * Creates an unselected toggle button with the specified text.
     *
     * @param text  the string displayed on the toggle button
     */
    public JToggleButton (String text) {
        this(text, null, false);
    }

    /**
     * Creates a toggle button with the specified text
     * and selection state.
     *
     * @param text  the string displayed on the toggle button
     * @param selected  if true, the button is initially selected;
     *                  otherwise, the button is initially unselected
     */
    public JToggleButton (String text, boolean selected) {
        this(text, null, selected);
    }

    /**
     * Creates a toggle button where properties are taken from the
     * Action supplied.
     *
     * @since 1.3
     */
    public JToggleButton(Action a) {
        this();
        setAction(a);
    }

    /**
     * Creates a toggle button that has the specified text and image,
     * and that is initially unselected.
     *
     * @param text the string displayed on the button
     * @param icon  the image that the button should display
     */
    public JToggleButton(String text, Icon icon) {
        this(text, icon, false);
    }

    /**
     * Creates a toggle button with the specified text, image, and
     * selection state.
     *
     * @param text the text of the toggle button
     * @param icon  the image that the button should display
     * @param selected  if true, the button is initially selected;
     *                  otherwise, the button is initially unselected
     */
    public JToggleButton (String text, Icon icon, boolean selected) {
        // Create the model
        setModel(new ToggleButtonModel());

        model.setSelected(selected);

        // initialize
        init(text, icon);
    }

    /**
     * Resets the UI property to a value from the current look and feel.
     *
     * @see JComponent#updateUI
     */
    public void updateUI() {
        setUI((ButtonUI)UIManager.getUI(this));
    }

    /**
     * Returns a string that specifies the name of the l&amp;f class
     * that renders this component.
     *
     * @return String "ToggleButtonUI"
     * @see JComponent#getUIClassID
     * @see UIDefaults#getUI
     * @beaninfo
     *  description: A string that specifies the name of the L&amp;F class
     */
    public String getUIClassID() {
        return uiClassID;
    }


    /**
     * Overriden to return true, JToggleButton supports
     * the selected state.
     */
    boolean shouldUpdateSelectedStateFromAction() {
        return true;
    }

    // *********************************************************************

    /**
     * The ToggleButton model
     * <p>
     * <strong>Warning:</strong>
     * Serialized objects of this class will not be compatible with
     * future Swing releases. The current serialization support is
     * appropriate for short term storage or RMI between applications running
     * the same version of Swing.  As of 1.4, support for long term storage
     * of all JavaBeans<sup><font size="-2">TM</font></sup>
     * has been added to the <code>java.beans</code> package.
     * Please see {@link java.beans.XMLEncoder}.
     */
    public static class ToggleButtonModel extends DefaultButtonModel {

        /**
         * Creates a new ToggleButton Model
         */
        public ToggleButtonModel () {
        }

        /**
         * Checks if the button is selected.
         */
        public boolean isSelected() {
//              if(getGroup() != null) {
//                  return getGroup().isSelected(this);
//              } else {
                return (stateMask & SELECTED) != 0;
//              }
        }


        /**
         * Sets the selected state of the button.
         * @param b true selects the toggle button,
         *          false deselects the toggle button.
         */
        public void setSelected(boolean b) {
            ButtonGroup group = getGroup();
            if (group != null) {
                // use the group model instead
                group.setSelected(this, b);
                b = group.isSelected(this);
            }

            if (isSelected() == b) {
                return;
            }

            if (b) {
                stateMask |= SELECTED;
            } else {
                stateMask &= ~SELECTED;
            }

            // Send ChangeEvent
            fireStateChanged();

            // Send ItemEvent
            fireItemStateChanged(
                    new ItemEvent(this,
                                  ItemEvent.ITEM_STATE_CHANGED,
                                  this,
                                  this.isSelected() ?  ItemEvent.SELECTED : ItemEvent.DESELECTED));

        }

        /**
         * Sets the pressed state of the toggle button.
         */
        public void setPressed(boolean b) {
            if ((isPressed() == b) || !isEnabled()) {
                return;
            }

            if (b == false && isArmed()) {
                setSelected(!this.isSelected());
            }

            if (b) {
                stateMask |= PRESSED;
            } else {
                stateMask &= ~PRESSED;
            }

            fireStateChanged();

            if(!isPressed() && isArmed()) {
                int modifiers = 0;
                AWTEvent currentEvent = EventQueue.getCurrentEvent();
                if (currentEvent instanceof InputEvent) {
                    modifiers = ((InputEvent)currentEvent).getModifiers();
                } else if (currentEvent instanceof ActionEvent) {
                    modifiers = ((ActionEvent)currentEvent).getModifiers();
                }
                fireActionPerformed(
                    new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                                    getActionCommand(),
                                    EventQueue.getMostRecentEventTime(),
                                    modifiers));
            }

        }
    }


    /**
     * See readObject() and writeObject() in JComponent for more
     * information about serialization in Swing.
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        s.defaultWriteObject();
        if (getUIClassID().equals(uiClassID)) {
            byte count = JComponent.getWriteObjCounter(this);
            JComponent.setWriteObjCounter(this, --count);
            if (count == 0 && ui != null) {
                ui.installUI(this);
            }
        }
    }


    /**
     * Returns a string representation of this JToggleButton. This method
     * is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not
     * be <code>null</code>.
     *
     * @return  a string representation of this JToggleButton.
     */
    protected String paramString() {
        return super.paramString();
    }


/////////////////
// Accessibility support
////////////////

    /**
     * Gets the AccessibleContext associated with this JToggleButton.
     * For toggle buttons, the AccessibleContext takes the form of an
     * AccessibleJToggleButton.
     * A new AccessibleJToggleButton instance is created if necessary.
     *
     * @return an AccessibleJToggleButton that serves as the
     *         AccessibleContext of this JToggleButton
     * @beaninfo
     *       expert: true
     *  description: The AccessibleContext associated with this ToggleButton.
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJToggleButton();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>JToggleButton</code> class.  It provides an implementation of the
     * Java Accessibility API appropriate to toggle button user-interface
     * elements.
     * <p>
     * <strong>Warning:</strong>
     * Serialized objects of this class will not be compatible with
     * future Swing releases. The current serialization support is
     * appropriate for short term storage or RMI between applications running
     * the same version of Swing.  As of 1.4, support for long term storage
     * of all JavaBeans<sup><font size="-2">TM</font></sup>
     * has been added to the <code>java.beans</code> package.
     * Please see {@link java.beans.XMLEncoder}.
     */
    protected class AccessibleJToggleButton extends AccessibleAbstractButton
            implements ItemListener {

        public AccessibleJToggleButton() {
            super();
            JToggleButton.this.addItemListener(this);
        }

        /**
         * Fire accessible property change events when the state of the
         * toggle button changes.
         */
        public void itemStateChanged(ItemEvent e) {
            JToggleButton tb = (JToggleButton) e.getSource();
            if (JToggleButton.this.accessibleContext != null) {
                if (tb.isSelected()) {
                    JToggleButton.this.accessibleContext.firePropertyChange(
                            AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                            null, AccessibleState.CHECKED);
                } else {
                    JToggleButton.this.accessibleContext.firePropertyChange(
                            AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                            AccessibleState.CHECKED, null);
                }
            }
        }

        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of the
         * object
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.TOGGLE_BUTTON;
        }
    } // inner class AccessibleJToggleButton
}
