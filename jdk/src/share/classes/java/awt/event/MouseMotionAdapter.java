/*
 * Copyright (c) 1996, 2006, Oracle and/or its affiliates. All rights reserved.
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

package java.awt.event;

/**
 * An abstract adapter class for receiving mouse motion events.
 * The methods in this class are empty. This class exists as
 * convenience for creating listener objects.
 * <P>
 * Mouse motion events occur when a mouse is moved or dragged.
 * (Many such events will be generated in a normal program.
 * To track clicks and other mouse events, use the MouseAdapter.)
 * <P>
 * Extend this class to create a <code>MouseEvent</code> listener
 * and override the methods for the events of interest. (If you implement the
 * <code>MouseMotionListener</code> interface, you have to define all of
 * the methods in it. This abstract class defines null methods for them
 * all, so you can only have to define methods for events you care about.)
 * <P>
 * Create a listener object using the extended class and then register it with
 * a component using the component's <code>addMouseMotionListener</code>
 * method. When the mouse is moved or dragged, the relevant method in the
 * listener object is invoked and the <code>MouseEvent</code> is passed to it.
 *
 * @author Amy Fowler
 *
 * @see MouseEvent
 * @see MouseMotionListener
 * @see <a href="http://java.sun.com/docs/books/tutorial/post1.0/ui/mousemotionlistener.html">Tutorial: Writing a Mouse Motion Listener</a>
 *
 * @since 1.1
 */
public abstract class MouseMotionAdapter implements MouseMotionListener {
    /**
     * Invoked when a mouse button is pressed on a component and then
     * dragged.  Mouse drag events will continue to be delivered to
     * the component where the first originated until the mouse button is
     * released (regardless of whether the mouse position is within the
     * bounds of the component).
     */
    public void mouseDragged(MouseEvent e) {}

    /**
     * Invoked when the mouse button has been moved on a component
     * (with no buttons no down).
     */
    public void mouseMoved(MouseEvent e) {}
}
