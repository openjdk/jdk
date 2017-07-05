/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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
import java.beans.*;

/**
 * The <code>Action</code> interface provides a useful extension to the
 * <code>ActionListener</code>
 * interface in cases where the same functionality may be accessed by
 * several controls.
 * <p>
 * In addition to the <code>actionPerformed</code> method defined by the
 * <code>ActionListener</code> interface, this interface allows the
 * application to define, in a single place:
 * <ul>
 * <li>One or more text strings that describe the function. These strings
 *     can be used, for example, to display the flyover text for a button
 *     or to set the text in a menu item.
 * <li>One or more icons that depict the function. These icons can be used
 *     for the images in a menu control, or for composite entries in a more
 *     sophisticated user interface.
 * <li>The enabled/disabled state of the functionality. Instead of having
 *     to separately disable the menu item and the toolbar button, the
 *     application can disable the function that implements this interface.
 *     All components which are registered as listeners for the state change
 *     then know to disable event generation for that item and to modify the
 *     display accordingly.
 * </ul>
 * <p>
 * This interface can be added to an existing class or used to create an
 * adapter (typically, by subclassing <code>AbstractAction</code>).
 * The <code>Action</code> object
 * can then be added to multiple <code>Action</code>-aware containers
 * and connected to <code>Action</code>-capable
 * components. The GUI controls can then be activated or
 * deactivated all at once by invoking the <code>Action</code> object's
 * <code>setEnabled</code> method.
 * <p>
 * Note that <code>Action</code> implementations tend to be more expensive
 * in terms of storage than a typical <code>ActionListener</code>,
 * which does not offer the benefits of centralized control of
 * functionality and broadcast of property changes.  For this reason,
 * you should take care to only use <code>Action</code>s where their benefits
 * are desired, and use simple <code>ActionListener</code>s elsewhere.
 * <p>
 *
 * <h4><a name="buttonActions"></a>Swing Components Supporting <code>Action</code></h4>
 * <p>
 * Many of Swing's components have an <code>Action</code> property.  When
 * an <code>Action</code> is set on a component, the following things
 * happen:
 * <ul>
 * <li>The <code>Action</code> is added as an <code>ActionListener</code> to
 *     the component.
 * <li>The component configures some of its properties to match the
 *      <code>Action</code>.
 * <li>The component installs a <code>PropertyChangeListener</code> on the
 *     <code>Action</code> so that the component can change its properties
 *     to reflect changes in the <code>Action</code>'s properties.
 * </ul>
 * <p>
 * The following table describes the properties used by
 * <code>Swing</code> components that support <code>Actions</code>.
 * In the table, <em>button</em> refers to any
 * <code>AbstractButton</code> subclass, which includes not only
 * <code>JButton</code> but also classes such as
 * <code>JMenuItem</code>. Unless otherwise stated, a
 * <code>null</code> property value in an <code>Action</code> (or a
 * <code>Action</code> that is <code>null</code>) results in the
 * button's corresponding property being set to <code>null</code>.
 * <p>
 * <table border="1" cellpadding="1" cellspacing="0"
 *         summary="Supported Action properties"
 *         valign="top" >
 *  <tr valign="top"  align="left">
 *    <th style="background-color:#CCCCFF" align="left">Component Property
 *    <th style="background-color:#CCCCFF" align="left">Components
 *    <th style="background-color:#CCCCFF" align="left">Action Key
 *    <th style="background-color:#CCCCFF" align="left">Notes
 *  <tr valign="top"  align="left">
 *      <td><b><code>enabled</code></b>
 *      <td>All
 *      <td>The <code>isEnabled</code> method
 *      <td>&nbsp;
 *  <tr valign="top"  align="left">
 *      <td><b><code>toolTipText</code></b>
 *      <td>All
 *      <td><code>SHORT_DESCRIPTION</code>
 *      <td>&nbsp;
 *  <tr valign="top"  align="left">
 *      <td><b><code>actionCommand</code></b>
 *      <td>All
 *      <td><code>ACTION_COMMAND_KEY</code>
 *      <td>&nbsp;
 *  <tr valign="top"  align="left">
 *      <td><b><code>mnemonic</code></b>
 *      <td>All buttons
 *      <td><code>MNEMONIC_KEY</code>
 *      <td>A <code>null</code> value or <code>Action</code> results in the
 *          button's <code>mnemonic</code> property being set to
 *          <code>'\0'</code>.
 *  <tr valign="top"  align="left">
 *      <td><b><code>text</code></b>
 *      <td>All buttons
 *      <td><code>NAME</code>
 *      <td>If you do not want the text of the button to mirror that
 *          of the <code>Action</code>, set the property
 *          <code>hideActionText</code> to <code>true</code>.  If
 *          <code>hideActionText</code> is <code>true</code>, setting the
 *          <code>Action</code> changes the text of the button to
 *          <code>null</code> and any changes to <code>NAME</code>
 *          are ignored.  <code>hideActionText</code> is useful for
 *          tool bar buttons that typically only show an <code>Icon</code>.
 *          <code>JToolBar.add(Action)</code> sets the property to
 *          <code>true</code> if the <code>Action</code> has a
 *          non-<code>null</code> value for <code>LARGE_ICON_KEY</code> or
 *          <code>SMALL_ICON</code>.
 *  <tr valign="top"  align="left">
 *      <td><b><code>displayedMnemonicIndex</code></b>
 *      <td>All buttons
 *      <td><code>DISPLAYED_MNEMONIC_INDEX_KEY</code>
 *      <td>If the value of <code>DISPLAYED_MNEMONIC_INDEX_KEY</code> is
 *          beyond the bounds of the text, it is ignored.  When
 *          <code>setAction</code> is called, if the value from the
 *          <code>Action</code> is <code>null</code>, the displayed
 *          mnemonic index is not updated.  In any subsequent changes to
 *          <code>DISPLAYED_MNEMONIC_INDEX_KEY</code>, <code>null</code>
 *          is treated as -1.
 *  <tr valign="top"  align="left">
 *      <td><b><code>icon</code></b>
 *      <td>All buttons except of <code>JCheckBox</code>,
 *      <code>JToggleButton</code> and <code>JRadioButton</code>.
 *      <td>either <code>LARGE_ICON_KEY</code> or
 *          <code>SMALL_ICON</code>
 *     <td>The <code>JMenuItem</code> subclasses only use
 *         <code>SMALL_ICON</code>.  All other buttons will use
 *         <code>LARGE_ICON_KEY</code>; if the value is <code>null</code> they
 *         use <code>SMALL_ICON</code>.
 *  <tr valign="top"  align="left">
 *      <td><b><code>accelerator</code></b>
 *      <td>All <code>JMenuItem</code> subclasses, with the exception of
 *          <code>JMenu</code>.
 *      <td><code>ACCELERATOR_KEY</code>
 *      <td>&nbsp;
 *  <tr valign="top"  align="left">
 *      <td><b><code>selected</code></b>
 *      <td><code>JToggleButton</code>, <code>JCheckBox</code>,
 *          <code>JRadioButton</code>, <code>JCheckBoxMenuItem</code> and
 *          <code>JRadioButtonMenuItem</code>
 *      <td><code>SELECTED_KEY</code>
 *      <td>Components that honor this property only use
 *          the value if it is {@code non-null}. For example, if
 *          you set an {@code Action} that has a {@code null}
 *          value for {@code SELECTED_KEY} on a {@code JToggleButton}, the
 *          {@code JToggleButton} will not update it's selected state in
 *          any way. Similarly, any time the {@code JToggleButton}'s
 *          selected state changes it will only set the value back on
 *          the {@code Action} if the {@code Action} has a {@code non-null}
 *          value for {@code SELECTED_KEY}.
 *          <br>
 *          Components that honor this property keep their selected state
 *          in sync with this property. When the same {@code Action} is used
 *          with multiple components, all the components keep their selected
 *          state in sync with this property. Mutually exclusive
 *          buttons, such as {@code JToggleButton}s in a {@code ButtonGroup},
 *          force only one of the buttons to be selected. As such, do not
 *          use the same {@code Action} that defines a value for the
 *          {@code SELECTED_KEY} property with multiple mutually
 *          exclusive buttons.
 * </table>
 * <p>
 * <code>JPopupMenu</code>, <code>JToolBar</code> and <code>JMenu</code>
 * all provide convenience methods for creating a component and setting the
 * <code>Action</code> on the corresponding component.  Refer to each of
 * these classes for more information.
 * <p>
 * <code>Action</code> uses <code>PropertyChangeListener</code> to
 * inform listeners the <code>Action</code> has changed.  The beans
 * specification indicates that a <code>null</code> property name can
 * be used to indicate multiple values have changed.  By default Swing
 * components that take an <code>Action</code> do not handle such a
 * change.  To indicate that Swing should treat <code>null</code>
 * according to the beans specification set the system property
 * <code>swing.actions.reconfigureOnNull</code> to the <code>String</code>
 * value <code>true</code>.
 *
 * @author Georges Saab
 * @see AbstractAction
 */
public interface Action extends ActionListener {
    /**
     * Useful constants that can be used as the storage-retrieval key
     * when setting or getting one of this object's properties (text
     * or icon).
     */
    /**
     * Not currently used.
     */
    public static final String DEFAULT = "Default";
    /**
     * The key used for storing the <code>String</code> name
     * for the action, used for a menu or button.
     */
    public static final String NAME = "Name";
    /**
     * The key used for storing a short <code>String</code>
     * description for the action, used for tooltip text.
     */
    public static final String SHORT_DESCRIPTION = "ShortDescription";
    /**
     * The key used for storing a longer <code>String</code>
     * description for the action, could be used for context-sensitive help.
     */
    public static final String LONG_DESCRIPTION = "LongDescription";
    /**
     * The key used for storing a small <code>Icon</code>, such
     * as <code>ImageIcon</code>.  This is typically used with
     * menus such as <code>JMenuItem</code>.
     * <p>
     * If the same <code>Action</code> is used with menus and buttons you'll
     * typically specify both a <code>SMALL_ICON</code> and a
     * <code>LARGE_ICON_KEY</code>.  The menu will use the
     * <code>SMALL_ICON</code> and the button will use the
     * <code>LARGE_ICON_KEY</code>.
     */
    public static final String SMALL_ICON = "SmallIcon";

    /**
     * The key used to determine the command <code>String</code> for the
     * <code>ActionEvent</code> that will be created when an
     * <code>Action</code> is going to be notified as the result of
     * residing in a <code>Keymap</code> associated with a
     * <code>JComponent</code>.
     */
    public static final String ACTION_COMMAND_KEY = "ActionCommandKey";

    /**
     * The key used for storing a <code>KeyStroke</code> to be used as the
     * accelerator for the action.
     *
     * @since 1.3
     */
    public static final String ACCELERATOR_KEY="AcceleratorKey";

    /**
     * The key used for storing an <code>Integer</code> that corresponds to
     * one of the <code>KeyEvent</code> key codes.  The value is
     * commonly used to specify a mnemonic.  For example:
     * <code>myAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_A)</code>
     * sets the mnemonic of <code>myAction</code> to 'a', while
     * <code>myAction.putValue(Action.MNEMONIC_KEY, KeyEvent.getExtendedKeyCodeForChar('\u0444'))</code>
     * sets the mnemonic of <code>myAction</code> to Cyrillic letter "Ef".
     *
     * @since 1.3
     */
    public static final String MNEMONIC_KEY="MnemonicKey";

    /**
     * The key used for storing a <code>Boolean</code> that corresponds
     * to the selected state.  This is typically used only for components
     * that have a meaningful selection state.  For example,
     * <code>JRadioButton</code> and <code>JCheckBox</code> make use of
     * this but instances of <code>JMenu</code> don't.
     * <p>
     * This property differs from the others in that it is both read
     * by the component and set by the component.  For example,
     * if an <code>Action</code> is attached to a <code>JCheckBox</code>
     * the selected state of the <code>JCheckBox</code> will be set from
     * that of the <code>Action</code>.  If the user clicks on the
     * <code>JCheckBox</code> the selected state of the <code>JCheckBox</code>
     * <b>and</b> the <code>Action</code> will <b>both</b> be updated.
     * <p>
     * Note: the value of this field is prefixed with 'Swing' to
     * avoid possible collisions with existing <code>Actions</code>.
     *
     * @since 1.6
     */
    public static final String SELECTED_KEY = "SwingSelectedKey";

    /**
     * The key used for storing an <code>Integer</code> that corresponds
     * to the index in the text (identified by the <code>NAME</code>
     * property) that the decoration for a mnemonic should be rendered at.  If
     * the value of this property is greater than or equal to the length of
     * the text, it will treated as -1.
     * <p>
     * Note: the value of this field is prefixed with 'Swing' to
     * avoid possible collisions with existing <code>Actions</code>.
     *
     * @see AbstractButton#setDisplayedMnemonicIndex
     * @since 1.6
     */
    public static final String DISPLAYED_MNEMONIC_INDEX_KEY =
                                 "SwingDisplayedMnemonicIndexKey";

    /**
     * The key used for storing an <code>Icon</code>.  This is typically
     * used by buttons, such as <code>JButton</code> and
     * <code>JToggleButton</code>.
     * <p>
     * If the same <code>Action</code> is used with menus and buttons you'll
     * typically specify both a <code>SMALL_ICON</code> and a
     * <code>LARGE_ICON_KEY</code>.  The menu will use the
     * <code>SMALL_ICON</code> and the button the <code>LARGE_ICON_KEY</code>.
     * <p>
     * Note: the value of this field is prefixed with 'Swing' to
     * avoid possible collisions with existing <code>Actions</code>.
     *
     * @since 1.6
     */
    public static final String LARGE_ICON_KEY = "SwingLargeIconKey";

    /**
     * Gets one of this object's properties
     * using the associated key.
     * @see #putValue
     */
    public Object getValue(String key);
    /**
     * Sets one of this object's properties
     * using the associated key. If the value has
     * changed, a <code>PropertyChangeEvent</code> is sent
     * to listeners.
     *
     * @param key    a <code>String</code> containing the key
     * @param value  an <code>Object</code> value
     */
    public void putValue(String key, Object value);

    /**
     * Sets the enabled state of the <code>Action</code>.  When enabled,
     * any component associated with this object is active and
     * able to fire this object's <code>actionPerformed</code> method.
     * If the value has changed, a <code>PropertyChangeEvent</code> is sent
     * to listeners.
     *
     * @param  b true to enable this <code>Action</code>, false to disable it
     */
    public void setEnabled(boolean b);
    /**
     * Returns the enabled state of the <code>Action</code>. When enabled,
     * any component associated with this object is active and
     * able to fire this object's <code>actionPerformed</code> method.
     *
     * @return true if this <code>Action</code> is enabled
     */
    public boolean isEnabled();

    /**
     * Adds a <code>PropertyChange</code> listener. Containers and attached
     * components use these methods to register interest in this
     * <code>Action</code> object. When its enabled state or other property
     * changes, the registered listeners are informed of the change.
     *
     * @param listener  a <code>PropertyChangeListener</code> object
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);
    /**
     * Removes a <code>PropertyChange</code> listener.
     *
     * @param listener  a <code>PropertyChangeListener</code> object
     * @see #addPropertyChangeListener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);

}
