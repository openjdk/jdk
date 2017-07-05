/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.awt;

import java.awt.peer.ScrollbarPeer;
import java.awt.event.*;
import java.util.EventListener;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import javax.accessibility.*;


/**
 * The <code>Scrollbar</code> class embodies a scroll bar, a
 * familiar user-interface object. A scroll bar provides a
 * convenient means for allowing a user to select from a
 * range of values. The following three vertical
 * scroll bars could be used as slider controls to pick
 * the red, green, and blue components of a color:
 * <p>
 * <img src="doc-files/Scrollbar-1.gif" alt="Image shows 3 vertical sliders, side-by-side."
 * style="float:center; margin: 7px 10px;">
 * <p>
 * Each scroll bar in this example could be created with
 * code similar to the following:
 *
 * <hr><blockquote><pre>
 * redSlider=new Scrollbar(Scrollbar.VERTICAL, 0, 1, 0, 255);
 * add(redSlider);
 * </pre></blockquote><hr>
 * <p>
 * Alternatively, a scroll bar can represent a range of values. For
 * example, if a scroll bar is used for scrolling through text, the
 * width of the "bubble" (also called the "thumb" or "scroll box")
 * can be used to represent the amount of text that is visible.
 * Here is an example of a scroll bar that represents a range:
 * <p>
 * <img src="doc-files/Scrollbar-2.gif"
 * alt="Image shows horizontal slider with starting range of 0 and ending range of 300. The slider thumb is labeled 60."
 * style="float:center; margin: 7px 10px;">
 * <p>
 * The value range represented by the bubble in this example
 * is the <em>visible amount</em>. The horizontal scroll bar
 * in this example could be created with code like the following:
 *
 * <hr><blockquote><pre>
 * ranger = new Scrollbar(Scrollbar.HORIZONTAL, 0, 60, 0, 300);
 * add(ranger);
 * </pre></blockquote><hr>
 * <p>
 * Note that the actual maximum value of the scroll bar is the
 * <code>maximum</code> minus the <code>visible amount</code>.
 * In the previous example, because the <code>maximum</code> is
 * 300 and the <code>visible amount</code> is 60, the actual maximum
 * value is 240.  The range of the scrollbar track is 0 - 300.
 * The left side of the bubble indicates the value of the
 * scroll bar.
 * <p>
 * Normally, the user changes the value of the scroll bar by
 * making a gesture with the mouse. For example, the user can
 * drag the scroll bar's bubble up and down, or click in the
 * scroll bar's unit increment or block increment areas. Keyboard
 * gestures can also be mapped to the scroll bar. By convention,
 * the <b>Page&nbsp;Up</b> and <b>Page&nbsp;Down</b>
 * keys are equivalent to clicking in the scroll bar's block
 * increment and block decrement areas.
 * <p>
 * When the user changes the value of the scroll bar, the scroll bar
 * receives an instance of <code>AdjustmentEvent</code>.
 * The scroll bar processes this event, passing it along to
 * any registered listeners.
 * <p>
 * Any object that wishes to be notified of changes to the
 * scroll bar's value should implement
 * <code>AdjustmentListener</code>, an interface defined in
 * the package <code>java.awt.event</code>.
 * Listeners can be added and removed dynamically by calling
 * the methods <code>addAdjustmentListener</code> and
 * <code>removeAdjustmentListener</code>.
 * <p>
 * The <code>AdjustmentEvent</code> class defines five types
 * of adjustment event, listed here:
 *
 * <ul>
 * <li><code>AdjustmentEvent.TRACK</code> is sent out when the
 * user drags the scroll bar's bubble.
 * <li><code>AdjustmentEvent.UNIT_INCREMENT</code> is sent out
 * when the user clicks in the left arrow of a horizontal scroll
 * bar, or the top arrow of a vertical scroll bar, or makes the
 * equivalent gesture from the keyboard.
 * <li><code>AdjustmentEvent.UNIT_DECREMENT</code> is sent out
 * when the user clicks in the right arrow of a horizontal scroll
 * bar, or the bottom arrow of a vertical scroll bar, or makes the
 * equivalent gesture from the keyboard.
 * <li><code>AdjustmentEvent.BLOCK_INCREMENT</code> is sent out
 * when the user clicks in the track, to the left of the bubble
 * on a horizontal scroll bar, or above the bubble on a vertical
 * scroll bar. By convention, the <b>Page&nbsp;Up</b>
 * key is equivalent, if the user is using a keyboard that
 * defines a <b>Page&nbsp;Up</b> key.
 * <li><code>AdjustmentEvent.BLOCK_DECREMENT</code> is sent out
 * when the user clicks in the track, to the right of the bubble
 * on a horizontal scroll bar, or below the bubble on a vertical
 * scroll bar. By convention, the <b>Page&nbsp;Down</b>
 * key is equivalent, if the user is using a keyboard that
 * defines a <b>Page&nbsp;Down</b> key.
 * </ul>
 * <p>
 * The JDK&nbsp;1.0 event system is supported for backwards
 * compatibility, but its use with newer versions of the platform is
 * discouraged. The five types of adjustment events introduced
 * with JDK&nbsp;1.1 correspond to the five event types
 * that are associated with scroll bars in previous platform versions.
 * The following list gives the adjustment event type,
 * and the corresponding JDK&nbsp;1.0 event type it replaces.
 *
 * <ul>
 * <li><code>AdjustmentEvent.TRACK</code> replaces
 * <code>Event.SCROLL_ABSOLUTE</code>
 * <li><code>AdjustmentEvent.UNIT_INCREMENT</code> replaces
 * <code>Event.SCROLL_LINE_UP</code>
 * <li><code>AdjustmentEvent.UNIT_DECREMENT</code> replaces
 * <code>Event.SCROLL_LINE_DOWN</code>
 * <li><code>AdjustmentEvent.BLOCK_INCREMENT</code> replaces
 * <code>Event.SCROLL_PAGE_UP</code>
 * <li><code>AdjustmentEvent.BLOCK_DECREMENT</code> replaces
 * <code>Event.SCROLL_PAGE_DOWN</code>
 * </ul>
 * <p>
 * <b>Note</b>: We recommend using a <code>Scrollbar</code>
 * for value selection only.  If you want to implement
 * a scrollable component inside a container, we recommend you use
 * a {@link ScrollPane ScrollPane}. If you use a
 * <code>Scrollbar</code> for this purpose, you are likely to
 * encounter issues with painting, key handling, sizing and
 * positioning.
 *
 * @author      Sami Shaio
 * @see         java.awt.event.AdjustmentEvent
 * @see         java.awt.event.AdjustmentListener
 * @since       JDK1.0
 */
public class Scrollbar extends Component implements Adjustable, Accessible {

    /**
     * A constant that indicates a horizontal scroll bar.
     */
    public static final int     HORIZONTAL = 0;

    /**
     * A constant that indicates a vertical scroll bar.
     */
    public static final int     VERTICAL   = 1;

    /**
     * The value of the <code>Scrollbar</code>.
     * This property must be greater than or equal to <code>minimum</code>
     * and less than or equal to
     * <code>maximum - visibleAmount</code>
     *
     * @serial
     * @see #getValue
     * @see #setValue
     */
    int value;

    /**
     * The maximum value of the <code>Scrollbar</code>.
     * This value must be greater than the <code>minimum</code>
     * value.<br>
     *
     * @serial
     * @see #getMaximum
     * @see #setMaximum
     */
    int maximum;

    /**
     * The minimum value of the <code>Scrollbar</code>.
     * This value must be less than the <code>maximum</code>
     * value.<br>
     *
     * @serial
     * @see #getMinimum
     * @see #setMinimum
     */
    int minimum;

    /**
     * The size of the <code>Scrollbar</code>'s bubble.
     * When a scroll bar is used to select a range of values,
     * the visibleAmount represents the size of this range.
     * Depending on platform, this may be visually indicated
     * by the size of the bubble.
     *
     * @serial
     * @see #getVisibleAmount
     * @see #setVisibleAmount
     */
    int visibleAmount;

    /**
     * The <code>Scrollbar</code>'s orientation--being either horizontal
     * or vertical.
     * This value should be specified when the scrollbar is created.<BR>
     * orientation can be either : <code>VERTICAL</code> or
     * <code>HORIZONTAL</code> only.
     *
     * @serial
     * @see #getOrientation
     * @see #setOrientation
     */
    int orientation;

    /**
     * The amount by which the scrollbar value will change when going
     * up or down by a line.
     * This value must be greater than zero.
     *
     * @serial
     * @see #getLineIncrement
     * @see #setLineIncrement
     */
    int lineIncrement = 1;

    /**
     * The amount by which the scrollbar value will change when going
     * up or down by a page.
     * This value must be greater than zero.
     *
     * @serial
     * @see #getPageIncrement
     * @see #setPageIncrement
     */
    int pageIncrement = 10;

    /**
     * The adjusting status of the <code>Scrollbar</code>.
     * True if the value is in the process of changing as a result of
     * actions being taken by the user.
     *
     * @see #getValueIsAdjusting
     * @see #setValueIsAdjusting
     * @since 1.4
     */
    transient boolean isAdjusting;

    transient AdjustmentListener adjustmentListener;

    private static final String base = "scrollbar";
    private static int nameCounter = 0;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = 8451667562882310543L;

    /**
     * Initialize JNI field and method IDs.
     */
    private static native void initIDs();

    static {
        /* ensure that the necessary native libraries are loaded */
        Toolkit.loadLibraries();
        if (!GraphicsEnvironment.isHeadless()) {
            initIDs();
        }
    }

    /**
     * Constructs a new vertical scroll bar.
     * The default properties of the scroll bar are listed in
     * the following table:
     *
     * <table border=1 summary="Scrollbar default properties">
     * <tr>
     *   <th>Property</th>
     *   <th>Description</th>
     *   <th>Default Value</th>
     * </tr>
     * <tr>
     *   <td>orientation</td>
     *   <td>indicates whether the scroll bar is vertical
     *   <br>or horizontal</td>
     *   <td><code>Scrollbar.VERTICAL</code></td>
     * </tr>
     * <tr>
     *   <td>value</td>
     *   <td>value which controls the location
     *   <br>of the scroll bar's bubble</td>
     *   <td>0</td>
     * </tr>
     * <tr>
     *   <td>visible amount</td>
     *   <td>visible amount of the scroll bar's range,
     *   <br>typically represented by the size of the
     *   <br>scroll bar's bubble</td>
     *   <td>10</td>
     * </tr>
     * <tr>
     *   <td>minimum</td>
     *   <td>minimum value of the scroll bar</td>
     *   <td>0</td>
     * </tr>
     * <tr>
     *   <td>maximum</td>
     *   <td>maximum value of the scroll bar</td>
     *   <td>100</td>
     * </tr>
     * <tr>
     *   <td>unit increment</td>
     *   <td>amount the value changes when the
     *   <br>Line Up or Line Down key is pressed,
     *   <br>or when the end arrows of the scrollbar
     *   <br>are clicked </td>
     *   <td>1</td>
     * </tr>
     * <tr>
     *   <td>block increment</td>
     *   <td>amount the value changes when the
     *   <br>Page Up or Page Down key is pressed,
     *   <br>or when the scrollbar track is clicked
     *   <br>on either side of the bubble </td>
     *   <td>10</td>
     * </tr>
     * </table>
     *
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public Scrollbar() throws HeadlessException {
        this(VERTICAL, 0, 10, 0, 100);
    }

    /**
     * Constructs a new scroll bar with the specified orientation.
     * <p>
     * The <code>orientation</code> argument must take one of the two
     * values <code>Scrollbar.HORIZONTAL</code>,
     * or <code>Scrollbar.VERTICAL</code>,
     * indicating a horizontal or vertical scroll bar, respectively.
     *
     * @param       orientation   indicates the orientation of the scroll bar
     * @exception   IllegalArgumentException    when an illegal value for
     *                    the <code>orientation</code> argument is supplied
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public Scrollbar(int orientation) throws HeadlessException {
        this(orientation, 0, 10, 0, 100);
    }

    /**
     * Constructs a new scroll bar with the specified orientation,
     * initial value, visible amount, and minimum and maximum values.
     * <p>
     * The <code>orientation</code> argument must take one of the two
     * values <code>Scrollbar.HORIZONTAL</code>,
     * or <code>Scrollbar.VERTICAL</code>,
     * indicating a horizontal or vertical scroll bar, respectively.
     * <p>
     * The parameters supplied to this constructor are subject to the
     * constraints described in {@link #setValues(int, int, int, int)}.
     *
     * @param     orientation   indicates the orientation of the scroll bar.
     * @param     value     the initial value of the scroll bar
     * @param     visible   the visible amount of the scroll bar, typically
     *                      represented by the size of the bubble
     * @param     minimum   the minimum value of the scroll bar
     * @param     maximum   the maximum value of the scroll bar
     * @exception IllegalArgumentException    when an illegal value for
     *                    the <code>orientation</code> argument is supplied
     * @exception HeadlessException if GraphicsEnvironment.isHeadless()
     * returns true.
     * @see #setValues
     * @see java.awt.GraphicsEnvironment#isHeadless
     */
    public Scrollbar(int orientation, int value, int visible, int minimum,
        int maximum) throws HeadlessException {
        GraphicsEnvironment.checkHeadless();
        switch (orientation) {
          case HORIZONTAL:
          case VERTICAL:
            this.orientation = orientation;
            break;
          default:
            throw new IllegalArgumentException("illegal scrollbar orientation");
        }
        setValues(value, visible, minimum, maximum);
    }

    /**
     * Constructs a name for this component.  Called by <code>getName</code>
     * when the name is <code>null</code>.
     */
    String constructComponentName() {
        synchronized (Scrollbar.class) {
            return base + nameCounter++;
        }
    }

    /**
     * Creates the <code>Scrollbar</code>'s peer.  The peer allows you to modify
     * the appearance of the <code>Scrollbar</code> without changing any of its
     * functionality.
     */
    public void addNotify() {
        synchronized (getTreeLock()) {
            if (peer == null)
                peer = getToolkit().createScrollbar(this);
            super.addNotify();
        }
    }

    /**
     * Returns the orientation of this scroll bar.
     *
     * @return    the orientation of this scroll bar, either
     *               <code>Scrollbar.HORIZONTAL</code> or
     *               <code>Scrollbar.VERTICAL</code>
     * @see       java.awt.Scrollbar#setOrientation
     */
    public int getOrientation() {
        return orientation;
    }

    /**
     * Sets the orientation for this scroll bar.
     *
     * @param orientation  the orientation of this scroll bar, either
     *               <code>Scrollbar.HORIZONTAL</code> or
     *               <code>Scrollbar.VERTICAL</code>
     * @see       java.awt.Scrollbar#getOrientation
     * @exception   IllegalArgumentException  if the value supplied
     *                   for <code>orientation</code> is not a
     *                   legal value
     * @since     JDK1.1
     */
    public void setOrientation(int orientation) {
        synchronized (getTreeLock()) {
            if (orientation == this.orientation) {
                return;
            }
            switch (orientation) {
                case HORIZONTAL:
                case VERTICAL:
                    this.orientation = orientation;
                    break;
                default:
                    throw new IllegalArgumentException("illegal scrollbar orientation");
            }
            /* Create a new peer with the specified orientation. */
            if (peer != null) {
                removeNotify();
                addNotify();
                invalidate();
            }
        }
        if (accessibleContext != null) {
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                    ((orientation == VERTICAL)
                     ? AccessibleState.HORIZONTAL : AccessibleState.VERTICAL),
                    ((orientation == VERTICAL)
                     ? AccessibleState.VERTICAL : AccessibleState.HORIZONTAL));
        }
    }

    /**
     * Gets the current value of this scroll bar.
     *
     * @return      the current value of this scroll bar
     * @see         java.awt.Scrollbar#getMinimum
     * @see         java.awt.Scrollbar#getMaximum
     */
    public int getValue() {
        return value;
    }

    /**
     * Sets the value of this scroll bar to the specified value.
     * <p>
     * If the value supplied is less than the current <code>minimum</code>
     * or greater than the current <code>maximum - visibleAmount</code>,
     * then either <code>minimum</code> or <code>maximum - visibleAmount</code>
     * is substituted, as appropriate.
     * <p>
     * Normally, a program should change a scroll bar's
     * value only by calling <code>setValues</code>.
     * The <code>setValues</code> method simultaneously
     * and synchronously sets the minimum, maximum, visible amount,
     * and value properties of a scroll bar, so that they are
     * mutually consistent.
     * <p>
     * Calling this method does not fire an
     * <code>AdjustmentEvent</code>.
     *
     * @param       newValue   the new value of the scroll bar
     * @see         java.awt.Scrollbar#setValues
     * @see         java.awt.Scrollbar#getValue
     * @see         java.awt.Scrollbar#getMinimum
     * @see         java.awt.Scrollbar#getMaximum
     */
    public void setValue(int newValue) {
        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(newValue, visibleAmount, minimum, maximum);
    }

    /**
     * Gets the minimum value of this scroll bar.
     *
     * @return      the minimum value of this scroll bar
     * @see         java.awt.Scrollbar#getValue
     * @see         java.awt.Scrollbar#getMaximum
     */
    public int getMinimum() {
        return minimum;
    }

    /**
     * Sets the minimum value of this scroll bar.
     * <p>
     * When <code>setMinimum</code> is called, the minimum value
     * is changed, and other values (including the maximum, the
     * visible amount, and the current scroll bar value)
     * are changed to be consistent with the new minimum.
     * <p>
     * Normally, a program should change a scroll bar's minimum
     * value only by calling <code>setValues</code>.
     * The <code>setValues</code> method simultaneously
     * and synchronously sets the minimum, maximum, visible amount,
     * and value properties of a scroll bar, so that they are
     * mutually consistent.
     * <p>
     * Note that setting the minimum value to <code>Integer.MAX_VALUE</code>
     * will result in the new minimum value being set to
     * <code>Integer.MAX_VALUE - 1</code>.
     *
     * @param       newMinimum   the new minimum value for this scroll bar
     * @see         java.awt.Scrollbar#setValues
     * @see         java.awt.Scrollbar#setMaximum
     * @since       JDK1.1
     */
    public void setMinimum(int newMinimum) {
        // No checks are necessary in this method since minimum is
        // the first variable checked in the setValues function.

        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(value, visibleAmount, newMinimum, maximum);
    }

    /**
     * Gets the maximum value of this scroll bar.
     *
     * @return      the maximum value of this scroll bar
     * @see         java.awt.Scrollbar#getValue
     * @see         java.awt.Scrollbar#getMinimum
     */
    public int getMaximum() {
        return maximum;
    }

    /**
     * Sets the maximum value of this scroll bar.
     * <p>
     * When <code>setMaximum</code> is called, the maximum value
     * is changed, and other values (including the minimum, the
     * visible amount, and the current scroll bar value)
     * are changed to be consistent with the new maximum.
     * <p>
     * Normally, a program should change a scroll bar's maximum
     * value only by calling <code>setValues</code>.
     * The <code>setValues</code> method simultaneously
     * and synchronously sets the minimum, maximum, visible amount,
     * and value properties of a scroll bar, so that they are
     * mutually consistent.
     * <p>
     * Note that setting the maximum value to <code>Integer.MIN_VALUE</code>
     * will result in the new maximum value being set to
     * <code>Integer.MIN_VALUE + 1</code>.
     *
     * @param       newMaximum   the new maximum value
     *                     for this scroll bar
     * @see         java.awt.Scrollbar#setValues
     * @see         java.awt.Scrollbar#setMinimum
     * @since       JDK1.1
     */
    public void setMaximum(int newMaximum) {
        // minimum is checked first in setValues, so we need to
        // enforce minimum and maximum checks here.
        if (newMaximum == Integer.MIN_VALUE) {
            newMaximum = Integer.MIN_VALUE + 1;
        }

        if (minimum >= newMaximum) {
            minimum = newMaximum - 1;
        }

        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(value, visibleAmount, minimum, newMaximum);
    }

    /**
     * Gets the visible amount of this scroll bar.
     * <p>
     * When a scroll bar is used to select a range of values,
     * the visible amount is used to represent the range of values
     * that are currently visible.  The size of the scroll bar's
     * bubble (also called a thumb or scroll box), usually gives a
     * visual representation of the relationship of the visible
     * amount to the range of the scroll bar.
     * Note that depending on platform, the value of the visible amount property
     * may not be visually indicated by the size of the bubble.
     * <p>
     * The scroll bar's bubble may not be displayed when it is not
     * moveable (e.g. when it takes up the entire length of the
     * scroll bar's track, or when the scroll bar is disabled).
     * Whether the bubble is displayed or not will not affect
     * the value returned by <code>getVisibleAmount</code>.
     *
     * @return      the visible amount of this scroll bar
     * @see         java.awt.Scrollbar#setVisibleAmount
     * @since       JDK1.1
     */
    public int getVisibleAmount() {
        return getVisible();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getVisibleAmount()</code>.
     */
    @Deprecated
    public int getVisible() {
        return visibleAmount;
    }

    /**
     * Sets the visible amount of this scroll bar.
     * <p>
     * When a scroll bar is used to select a range of values,
     * the visible amount is used to represent the range of values
     * that are currently visible.  The size of the scroll bar's
     * bubble (also called a thumb or scroll box), usually gives a
     * visual representation of the relationship of the visible
     * amount to the range of the scroll bar.
     * Note that depending on platform, the value of the visible amount property
     * may not be visually indicated by the size of the bubble.
     * <p>
     * The scroll bar's bubble may not be displayed when it is not
     * moveable (e.g. when it takes up the entire length of the
     * scroll bar's track, or when the scroll bar is disabled).
     * Whether the bubble is displayed or not will not affect
     * the value returned by <code>getVisibleAmount</code>.
     * <p>
     * If the visible amount supplied is less than <code>one</code>
     * or greater than the current <code>maximum - minimum</code>,
     * then either <code>one</code> or <code>maximum - minimum</code>
     * is substituted, as appropriate.
     * <p>
     * Normally, a program should change a scroll bar's
     * value only by calling <code>setValues</code>.
     * The <code>setValues</code> method simultaneously
     * and synchronously sets the minimum, maximum, visible amount,
     * and value properties of a scroll bar, so that they are
     * mutually consistent.
     *
     * @param       newAmount the new visible amount
     * @see         java.awt.Scrollbar#getVisibleAmount
     * @see         java.awt.Scrollbar#setValues
     * @since       JDK1.1
     */
    public void setVisibleAmount(int newAmount) {
        // Use setValues so that a consistent policy relating
        // minimum, maximum, visible amount, and value is enforced.
        setValues(value, newAmount, minimum, maximum);
    }

    /**
     * Sets the unit increment for this scroll bar.
     * <p>
     * The unit increment is the value that is added or subtracted
     * when the user activates the unit increment area of the
     * scroll bar, generally through a mouse or keyboard gesture
     * that the scroll bar receives as an adjustment event.
     * The unit increment must be greater than zero.
     * Attepts to set the unit increment to a value lower than 1
     * will result in a value of 1 being set.
     * <p>
     * In some operating systems, this property
     * can be ignored by the underlying controls.
     *
     * @param        v  the amount by which to increment or decrement
     *                         the scroll bar's value
     * @see          java.awt.Scrollbar#getUnitIncrement
     * @since        JDK1.1
     */
    public void setUnitIncrement(int v) {
        setLineIncrement(v);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>setUnitIncrement(int)</code>.
     */
    @Deprecated
    public synchronized void setLineIncrement(int v) {
        int tmp = (v < 1) ? 1 : v;

        if (lineIncrement == tmp) {
            return;
        }
        lineIncrement = tmp;

        ScrollbarPeer peer = (ScrollbarPeer)this.peer;
        if (peer != null) {
            peer.setLineIncrement(lineIncrement);
        }
    }

    /**
     * Gets the unit increment for this scrollbar.
     * <p>
     * The unit increment is the value that is added or subtracted
     * when the user activates the unit increment area of the
     * scroll bar, generally through a mouse or keyboard gesture
     * that the scroll bar receives as an adjustment event.
     * The unit increment must be greater than zero.
     * <p>
     * In some operating systems, this property
     * can be ignored by the underlying controls.
     *
     * @return      the unit increment of this scroll bar
     * @see         java.awt.Scrollbar#setUnitIncrement
     * @since       JDK1.1
     */
    public int getUnitIncrement() {
        return getLineIncrement();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getUnitIncrement()</code>.
     */
    @Deprecated
    public int getLineIncrement() {
        return lineIncrement;
    }

    /**
     * Sets the block increment for this scroll bar.
     * <p>
     * The block increment is the value that is added or subtracted
     * when the user activates the block increment area of the
     * scroll bar, generally through a mouse or keyboard gesture
     * that the scroll bar receives as an adjustment event.
     * The block increment must be greater than zero.
     * Attepts to set the block increment to a value lower than 1
     * will result in a value of 1 being set.
     *
     * @param        v  the amount by which to increment or decrement
     *                         the scroll bar's value
     * @see          java.awt.Scrollbar#getBlockIncrement
     * @since        JDK1.1
     */
    public void setBlockIncrement(int v) {
        setPageIncrement(v);
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>setBlockIncrement()</code>.
     */
    @Deprecated
    public synchronized void setPageIncrement(int v) {
        int tmp = (v < 1) ? 1 : v;

        if (pageIncrement == tmp) {
            return;
        }
        pageIncrement = tmp;

        ScrollbarPeer peer = (ScrollbarPeer)this.peer;
        if (peer != null) {
            peer.setPageIncrement(pageIncrement);
        }
    }

    /**
     * Gets the block increment of this scroll bar.
     * <p>
     * The block increment is the value that is added or subtracted
     * when the user activates the block increment area of the
     * scroll bar, generally through a mouse or keyboard gesture
     * that the scroll bar receives as an adjustment event.
     * The block increment must be greater than zero.
     *
     * @return      the block increment of this scroll bar
     * @see         java.awt.Scrollbar#setBlockIncrement
     * @since       JDK1.1
     */
    public int getBlockIncrement() {
        return getPageIncrement();
    }

    /**
     * @deprecated As of JDK version 1.1,
     * replaced by <code>getBlockIncrement()</code>.
     */
    @Deprecated
    public int getPageIncrement() {
        return pageIncrement;
    }

    /**
     * Sets the values of four properties for this scroll bar:
     * <code>value</code>, <code>visibleAmount</code>,
     * <code>minimum</code>, and <code>maximum</code>.
     * If the values supplied for these properties are inconsistent
     * or incorrect, they will be changed to ensure consistency.
     * <p>
     * This method simultaneously and synchronously sets the values
     * of four scroll bar properties, assuring that the values of
     * these properties are mutually consistent. It enforces the
     * following constraints:
     * <code>maximum</code> must be greater than <code>minimum</code>,
     * <code>maximum - minimum</code> must not be greater
     *     than <code>Integer.MAX_VALUE</code>,
     * <code>visibleAmount</code> must be greater than zero.
     * <code>visibleAmount</code> must not be greater than
     *     <code>maximum - minimum</code>,
     * <code>value</code> must not be less than <code>minimum</code>,
     * and <code>value</code> must not be greater than
     *     <code>maximum - visibleAmount</code>
     * <p>
     * Calling this method does not fire an
     * <code>AdjustmentEvent</code>.
     *
     * @param      value is the position in the current window
     * @param      visible is the visible amount of the scroll bar
     * @param      minimum is the minimum value of the scroll bar
     * @param      maximum is the maximum value of the scroll bar
     * @see        #setMinimum
     * @see        #setMaximum
     * @see        #setVisibleAmount
     * @see        #setValue
     */
    public void setValues(int value, int visible, int minimum, int maximum) {
        int oldValue;
        synchronized (this) {
            if (minimum == Integer.MAX_VALUE) {
                minimum = Integer.MAX_VALUE - 1;
            }
            if (maximum <= minimum) {
                maximum = minimum + 1;
            }

            long maxMinusMin = (long) maximum - (long) minimum;
            if (maxMinusMin > Integer.MAX_VALUE) {
                maxMinusMin = Integer.MAX_VALUE;
                maximum = minimum + (int) maxMinusMin;
            }
            if (visible > (int) maxMinusMin) {
                visible = (int) maxMinusMin;
            }
            if (visible < 1) {
                visible = 1;
            }

            if (value < minimum) {
                value = minimum;
            }
            if (value > maximum - visible) {
                value = maximum - visible;
            }

            oldValue = this.value;
            this.value = value;
            this.visibleAmount = visible;
            this.minimum = minimum;
            this.maximum = maximum;
            ScrollbarPeer peer = (ScrollbarPeer)this.peer;
            if (peer != null) {
                peer.setValues(value, visibleAmount, minimum, maximum);
            }
        }

        if ((oldValue != value) && (accessibleContext != null))  {
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_VALUE_PROPERTY,
                    Integer.valueOf(oldValue),
                    Integer.valueOf(value));
        }
    }

    /**
     * Returns true if the value is in the process of changing as a
     * result of actions being taken by the user.
     *
     * @return the value of the <code>valueIsAdjusting</code> property
     * @see #setValueIsAdjusting
     * @since 1.4
     */
    public boolean getValueIsAdjusting() {
        return isAdjusting;
    }

    /**
     * Sets the <code>valueIsAdjusting</code> property.
     *
     * @param b new adjustment-in-progress status
     * @see #getValueIsAdjusting
     * @since 1.4
     */
    public void setValueIsAdjusting(boolean b) {
        boolean oldValue;

        synchronized (this) {
            oldValue = isAdjusting;
            isAdjusting = b;
        }

        if ((oldValue != b) && (accessibleContext != null)) {
            accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                    ((oldValue) ? AccessibleState.BUSY : null),
                    ((b) ? AccessibleState.BUSY : null));
        }
    }



    /**
     * Adds the specified adjustment listener to receive instances of
     * <code>AdjustmentEvent</code> from this scroll bar.
     * If l is <code>null</code>, no exception is thrown and no
     * action is performed.
     * <p>Refer to <a href="doc-files/AWTThreadIssues.html#ListenersThreads"
     * >AWT Threading Issues</a> for details on AWT's threading model.
     *
     * @param        l the adjustment listener
     * @see          #removeAdjustmentListener
     * @see          #getAdjustmentListeners
     * @see          java.awt.event.AdjustmentEvent
     * @see          java.awt.event.AdjustmentListener
     * @since        JDK1.1
     */
    public synchronized void addAdjustmentListener(AdjustmentListener l) {
        if (l == null) {
            return;
        }
        adjustmentListener = AWTEventMulticaster.add(adjustmentListener, l);
        newEventsOnly = true;
    }

    /**
     * Removes the specified adjustment listener so that it no longer
     * receives instances of <code>AdjustmentEvent</code> from this scroll bar.
     * If l is <code>null</code>, no exception is thrown and no action
     * is performed.
     * <p>Refer to <a href="doc-files/AWTThreadIssues.html#ListenersThreads"
     * >AWT Threading Issues</a> for details on AWT's threading model.
     *
     * @param           l    the adjustment listener
     * @see             #addAdjustmentListener
     * @see             #getAdjustmentListeners
     * @see             java.awt.event.AdjustmentEvent
     * @see             java.awt.event.AdjustmentListener
     * @since           JDK1.1
     */
    public synchronized void removeAdjustmentListener(AdjustmentListener l) {
        if (l == null) {
            return;
        }
        adjustmentListener = AWTEventMulticaster.remove(adjustmentListener, l);
    }

    /**
     * Returns an array of all the adjustment listeners
     * registered on this scrollbar.
     *
     * @return all of this scrollbar's <code>AdjustmentListener</code>s
     *         or an empty array if no adjustment
     *         listeners are currently registered
     * @see             #addAdjustmentListener
     * @see             #removeAdjustmentListener
     * @see             java.awt.event.AdjustmentEvent
     * @see             java.awt.event.AdjustmentListener
     * @since 1.4
     */
    public synchronized AdjustmentListener[] getAdjustmentListeners() {
        return getListeners(AdjustmentListener.class);
    }

    /**
     * Returns an array of all the objects currently registered
     * as <code><em>Foo</em>Listener</code>s
     * upon this <code>Scrollbar</code>.
     * <code><em>Foo</em>Listener</code>s are registered using the
     * <code>add<em>Foo</em>Listener</code> method.
     * <p>
     * You can specify the <code>listenerType</code> argument
     * with a class literal,  such as
     * <code><em>Foo</em>Listener.class</code>.
     * For example, you can query a
     * <code>Scrollbar</code> <code>c</code>
     * for its mouse listeners with the following code:
     *
     * <pre>MouseListener[] mls = (MouseListener[])(c.getListeners(MouseListener.class));</pre>
     *
     * If no such listeners exist, this method returns an empty array.
     *
     * @param listenerType the type of listeners requested; this parameter
     *          should specify an interface that descends from
     *          <code>java.util.EventListener</code>
     * @return an array of all objects registered as
     *          <code><em>Foo</em>Listener</code>s on this component,
     *          or an empty array if no such listeners have been added
     * @exception ClassCastException if <code>listenerType</code>
     *          doesn't specify a class or interface that implements
     *          <code>java.util.EventListener</code>
     *
     * @since 1.3
     */
    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        EventListener l = null;
        if  (listenerType == AdjustmentListener.class) {
            l = adjustmentListener;
        } else {
            return super.getListeners(listenerType);
        }
        return AWTEventMulticaster.getListeners(l, listenerType);
    }

    // REMIND: remove when filtering is done at lower level
    boolean eventEnabled(AWTEvent e) {
        if (e.id == AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED) {
            if ((eventMask & AWTEvent.ADJUSTMENT_EVENT_MASK) != 0 ||
                adjustmentListener != null) {
                return true;
            }
            return false;
        }
        return super.eventEnabled(e);
    }

    /**
     * Processes events on this scroll bar. If the event is an
     * instance of <code>AdjustmentEvent</code>, it invokes the
     * <code>processAdjustmentEvent</code> method.
     * Otherwise, it invokes its superclass's
     * <code>processEvent</code> method.
     * <p>Note that if the event parameter is <code>null</code>
     * the behavior is unspecified and may result in an
     * exception.
     *
     * @param        e the event
     * @see          java.awt.event.AdjustmentEvent
     * @see          java.awt.Scrollbar#processAdjustmentEvent
     * @since        JDK1.1
     */
    protected void processEvent(AWTEvent e) {
        if (e instanceof AdjustmentEvent) {
            processAdjustmentEvent((AdjustmentEvent)e);
            return;
        }
        super.processEvent(e);
    }

    /**
     * Processes adjustment events occurring on this
     * scrollbar by dispatching them to any registered
     * <code>AdjustmentListener</code> objects.
     * <p>
     * This method is not called unless adjustment events are
     * enabled for this component. Adjustment events are enabled
     * when one of the following occurs:
     * <ul>
     * <li>An <code>AdjustmentListener</code> object is registered
     * via <code>addAdjustmentListener</code>.
     * <li>Adjustment events are enabled via <code>enableEvents</code>.
     * </ul>
     * <p>Note that if the event parameter is <code>null</code>
     * the behavior is unspecified and may result in an
     * exception.
     *
     * @param       e the adjustment event
     * @see         java.awt.event.AdjustmentEvent
     * @see         java.awt.event.AdjustmentListener
     * @see         java.awt.Scrollbar#addAdjustmentListener
     * @see         java.awt.Component#enableEvents
     * @since       JDK1.1
     */
    protected void processAdjustmentEvent(AdjustmentEvent e) {
        AdjustmentListener listener = adjustmentListener;
        if (listener != null) {
            listener.adjustmentValueChanged(e);
        }
    }

    /**
     * Returns a string representing the state of this <code>Scrollbar</code>.
     * This method is intended to be used only for debugging purposes, and the
     * content and format of the returned string may vary between
     * implementations. The returned string may be empty but may not be
     * <code>null</code>.
     *
     * @return      the parameter string of this scroll bar
     */
    protected String paramString() {
        return super.paramString() +
            ",val=" + value +
            ",vis=" + visibleAmount +
            ",min=" + minimum +
            ",max=" + maximum +
            ((orientation == VERTICAL) ? ",vert" : ",horz") +
            ",isAdjusting=" + isAdjusting;
    }


    /* Serialization support.
     */

    /**
     * The scroll bar's serialized Data Version.
     *
     * @serial
     */
    private int scrollbarSerializedDataVersion = 1;

    /**
     * Writes default serializable fields to stream.  Writes
     * a list of serializable <code>AdjustmentListeners</code>
     * as optional data. The non-serializable listeners are
     * detected and no attempt is made to serialize them.
     *
     * @param s the <code>ObjectOutputStream</code> to write
     * @serialData <code>null</code> terminated sequence of 0
     *   or more pairs; the pair consists of a <code>String</code>
     *   and an <code>Object</code>; the <code>String</code> indicates
     *   the type of object and is one of the following:
     *   <code>adjustmentListenerK</code> indicating an
     *     <code>AdjustmentListener</code> object
     *
     * @see AWTEventMulticaster#save(ObjectOutputStream, String, EventListener)
     * @see java.awt.Component#adjustmentListenerK
     * @see #readObject(ObjectInputStream)
     */
    private void writeObject(ObjectOutputStream s)
      throws IOException
    {
      s.defaultWriteObject();

      AWTEventMulticaster.save(s, adjustmentListenerK, adjustmentListener);
      s.writeObject(null);
    }

    /**
     * Reads the <code>ObjectInputStream</code> and if
     * it isn't <code>null</code> adds a listener to
     * receive adjustment events fired by the
     * <code>Scrollbar</code>.
     * Unrecognized keys or values will be ignored.
     *
     * @param s the <code>ObjectInputStream</code> to read
     * @exception HeadlessException if
     *   <code>GraphicsEnvironment.isHeadless</code> returns
     *   <code>true</code>
     * @see java.awt.GraphicsEnvironment#isHeadless
     * @see #writeObject(ObjectOutputStream)
     */
    private void readObject(ObjectInputStream s)
      throws ClassNotFoundException, IOException, HeadlessException
    {
      GraphicsEnvironment.checkHeadless();
      s.defaultReadObject();

      Object keyOrNull;
      while(null != (keyOrNull = s.readObject())) {
        String key = ((String)keyOrNull).intern();

        if (adjustmentListenerK == key)
          addAdjustmentListener((AdjustmentListener)(s.readObject()));

        else // skip value for unrecognized key
          s.readObject();
      }
    }


/////////////////
// Accessibility support
////////////////

    /**
     * Gets the <code>AccessibleContext</code> associated with this
     * <code>Scrollbar</code>. For scrollbars, the
     * <code>AccessibleContext</code> takes the form of an
     * <code>AccessibleAWTScrollBar</code>. A new
     * <code>AccessibleAWTScrollBar</code> instance is created if necessary.
     *
     * @return an <code>AccessibleAWTScrollBar</code> that serves as the
     *         <code>AccessibleContext</code> of this <code>ScrollBar</code>
     * @since 1.3
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleAWTScrollBar();
        }
        return accessibleContext;
    }

    /**
     * This class implements accessibility support for the
     * <code>Scrollbar</code> class.  It provides an implementation of
     * the Java Accessibility API appropriate to scrollbar
     * user-interface elements.
     * @since 1.3
     */
    protected class AccessibleAWTScrollBar extends AccessibleAWTComponent
        implements AccessibleValue
    {
        /*
         * JDK 1.3 serialVersionUID
         */
        private static final long serialVersionUID = -344337268523697807L;

        /**
         * Get the state set of this object.
         *
         * @return an instance of <code>AccessibleState</code>
         *     containing the current state of the object
         * @see AccessibleState
         */
        public AccessibleStateSet getAccessibleStateSet() {
            AccessibleStateSet states = super.getAccessibleStateSet();
            if (getValueIsAdjusting()) {
                states.add(AccessibleState.BUSY);
            }
            if (getOrientation() == VERTICAL) {
                states.add(AccessibleState.VERTICAL);
            } else {
                states.add(AccessibleState.HORIZONTAL);
            }
            return states;
        }

        /**
         * Get the role of this object.
         *
         * @return an instance of <code>AccessibleRole</code>
         *     describing the role of the object
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.SCROLL_BAR;
        }

        /**
         * Get the <code>AccessibleValue</code> associated with this
         * object.  In the implementation of the Java Accessibility
         * API for this class, return this object, which is
         * responsible for implementing the
         * <code>AccessibleValue</code> interface on behalf of itself.
         *
         * @return this object
         */
        public AccessibleValue getAccessibleValue() {
            return this;
        }

        /**
         * Get the accessible value of this object.
         *
         * @return The current value of this object.
         */
        public Number getCurrentAccessibleValue() {
            return Integer.valueOf(getValue());
        }

        /**
         * Set the value of this object as a Number.
         *
         * @return True if the value was set.
         */
        public boolean setCurrentAccessibleValue(Number n) {
            if (n instanceof Integer) {
                setValue(n.intValue());
                return true;
            } else {
                return false;
            }
        }

        /**
         * Get the minimum accessible value of this object.
         *
         * @return The minimum value of this object.
         */
        public Number getMinimumAccessibleValue() {
            return Integer.valueOf(getMinimum());
        }

        /**
         * Get the maximum accessible value of this object.
         *
         * @return The maximum value of this object.
         */
        public Number getMaximumAccessibleValue() {
            return Integer.valueOf(getMaximum());
        }

    } // AccessibleAWTScrollBar

}
