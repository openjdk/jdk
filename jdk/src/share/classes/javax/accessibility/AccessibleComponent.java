/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package javax.accessibility;

import java.awt.*;
import java.awt.event.*;

/**
 * The AccessibleComponent interface should be supported by any object
 * that is rendered on the screen.  This interface provides the standard
 * mechanism for an assistive technology to determine and set the
 * graphical representation of an object.  Applications can determine
 * if an object supports the AccessibleComponent interface by first
 * obtaining its AccessibleContext
 * and then calling the
 * {@link AccessibleContext#getAccessibleComponent} method.
 * If the return value is not null, the object supports this interface.
 *
 * @see Accessible
 * @see Accessible#getAccessibleContext
 * @see AccessibleContext
 * @see AccessibleContext#getAccessibleComponent
 *
 * @author      Peter Korn
 * @author      Hans Muller
 * @author      Willie Walker
 */
public interface AccessibleComponent {

    /**
     * Gets the background color of this object.
     *
     * @return the background color, if supported, of the object;
     * otherwise, null
     * @see #setBackground
     */
    public Color getBackground();

    /**
     * Sets the background color of this object.
     *
     * @param c the new Color for the background
     * @see #setBackground
     */
    public void setBackground(Color c);

    /**
     * Gets the foreground color of this object.
     *
     * @return the foreground color, if supported, of the object;
     * otherwise, null
     * @see #setForeground
     */
    public Color getForeground();

    /**
     * Sets the foreground color of this object.
     *
     * @param c the new Color for the foreground
     * @see #getForeground
     */
    public void setForeground(Color c);

    /**
     * Gets the Cursor of this object.
     *
     * @return the Cursor, if supported, of the object; otherwise, null
     * @see #setCursor
     */
    public Cursor getCursor();

    /**
     * Sets the Cursor of this object.
     *
     * @param cursor  the new Cursor for the object
     * @see #getCursor
     */
    public void setCursor(Cursor cursor);

    /**
     * Gets the Font of this object.
     *
     * @return the Font,if supported, for the object; otherwise, null
     * @see #setFont
     */
    public Font getFont();

    /**
     * Sets the Font of this object.
     *
     * @param f the new Font for the object
     * @see #getFont
     */
    public void setFont(Font f);

    /**
     * Gets the FontMetrics of this object.
     *
     * @param f the Font
     * @return the FontMetrics, if supported, the object; otherwise, null
     * @see #getFont
     */
    public FontMetrics getFontMetrics(Font f);

    /**
     * Determines if the object is enabled.  Objects that are enabled
     * will also have the AccessibleState.ENABLED state set in their
     * AccessibleStateSets.
     *
     * @return true if object is enabled; otherwise, false
     * @see #setEnabled
     * @see AccessibleContext#getAccessibleStateSet
     * @see AccessibleState#ENABLED
     * @see AccessibleStateSet
     */
    public boolean isEnabled();

    /**
     * Sets the enabled state of the object.
     *
     * @param b if true, enables this object; otherwise, disables it
     * @see #isEnabled
     */
    public void setEnabled(boolean b);

    /**
     * Determines if the object is visible.  Note: this means that the
     * object intends to be visible; however, it may not be
     * showing on the screen because one of the objects that this object
     * is contained by is currently not visible.  To determine if an object is
     * showing on the screen, use isShowing().
     * <p>Objects that are visible will also have the
     * AccessibleState.VISIBLE state set in their AccessibleStateSets.
     *
     * @return true if object is visible; otherwise, false
     * @see #setVisible
     * @see AccessibleContext#getAccessibleStateSet
     * @see AccessibleState#VISIBLE
     * @see AccessibleStateSet
     */
    public boolean isVisible();

    /**
     * Sets the visible state of the object.
     *
     * @param b if true, shows this object; otherwise, hides it
     * @see #isVisible
     */
    public void setVisible(boolean b);

    /**
     * Determines if the object is showing.  This is determined by checking
     * the visibility of the object and its ancestors.
     * Note: this
     * will return true even if the object is obscured by another (for example,
     * it is underneath a menu that was pulled down).
     *
     * @return true if object is showing; otherwise, false
     */
    public boolean isShowing();

    /**
     * Checks whether the specified point is within this object's bounds,
     * where the point's x and y coordinates are defined to be relative to the
     * coordinate system of the object.
     *
     * @param p the Point relative to the coordinate system of the object
     * @return true if object contains Point; otherwise false
     * @see #getBounds
     */
    public boolean contains(Point p);

    /**
     * Returns the location of the object on the screen.
     *
     * @return the location of the object on screen; null if this object
     * is not on the screen
     * @see #getBounds
     * @see #getLocation
     */
    public Point getLocationOnScreen();

    /**
     * Gets the location of the object relative to the parent in the form
     * of a point specifying the object's top-left corner in the screen's
     * coordinate space.
     *
     * @return An instance of Point representing the top-left corner of the
     * object's bounds in the coordinate space of the screen; null if
     * this object or its parent are not on the screen
     * @see #getBounds
     * @see #getLocationOnScreen
     */
    public Point getLocation();

    /**
     * Sets the location of the object relative to the parent.
     * @param p the new position for the top-left corner
     * @see #getLocation
     */
    public void setLocation(Point p);

    /**
     * Gets the bounds of this object in the form of a Rectangle object.
     * The bounds specify this object's width, height, and location
     * relative to its parent.
     *
     * @return A rectangle indicating this component's bounds; null if
     * this object is not on the screen.
     * @see #contains
     */
    public Rectangle getBounds();

    /**
     * Sets the bounds of this object in the form of a Rectangle object.
     * The bounds specify this object's width, height, and location
     * relative to its parent.
     *
     * @param r rectangle indicating this component's bounds
     * @see #getBounds
     */
    public void setBounds(Rectangle r);

    /**
     * Returns the size of this object in the form of a Dimension object.
     * The height field of the Dimension object contains this object's
     * height, and the width field of the Dimension object contains this
     * object's width.
     *
     * @return A Dimension object that indicates the size of this component;
     * null if this object is not on the screen
     * @see #setSize
     */
    public Dimension getSize();

    /**
     * Resizes this object so that it has width and height.
     *
     * @param d The dimension specifying the new size of the object.
     * @see #getSize
     */
    public void setSize(Dimension d);

    /**
     * Returns the Accessible child, if one exists, contained at the local
     * coordinate Point.
     *
     * @param p The point relative to the coordinate system of this object.
     * @return the Accessible, if it exists, at the specified location;
     * otherwise null
     */
    public Accessible getAccessibleAt(Point p);

    /**
     * Returns whether this object can accept focus or not.   Objects that
     * can accept focus will also have the AccessibleState.FOCUSABLE state
     * set in their AccessibleStateSets.
     *
     * @return true if object can accept focus; otherwise false
     * @see AccessibleContext#getAccessibleStateSet
     * @see AccessibleState#FOCUSABLE
     * @see AccessibleState#FOCUSED
     * @see AccessibleStateSet
     */
    public boolean isFocusTraversable();

    /**
     * Requests focus for this object.  If this object cannot accept focus,
     * nothing will happen.  Otherwise, the object will attempt to take
     * focus.
     * @see #isFocusTraversable
     */
    public void requestFocus();

    /**
     * Adds the specified focus listener to receive focus events from this
     * component.
     *
     * @param l the focus listener
     * @see #removeFocusListener
     */
    public void addFocusListener(FocusListener l);

    /**
     * Removes the specified focus listener so it no longer receives focus
     * events from this component.
     *
     * @param l the focus listener
     * @see #addFocusListener
     */
    public void removeFocusListener(FocusListener l);
}
