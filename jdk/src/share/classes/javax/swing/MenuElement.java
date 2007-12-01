/*
 * Copyright 1997-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.swing;

import java.awt.*;
import java.awt.event.*;

/**
 * Any component that can be placed into a menu should implement this interface.
 * This interface is used by <code>MenuSelectionManager</code>
 * to handle selection and navigation in menu hierarchies.
 *
 * @author Arnaud Weber
 */

public interface MenuElement {

    /**
     * Processes a mouse event. <code>event</code> is a <code>MouseEvent</code>
     * with source being the receiving element's component.
     * <code>path</code> is the path of the receiving element in the menu
     * hierarchy including the receiving element itself.
     * <code>manager</code> is the <code>MenuSelectionManager</code>
     * for the menu hierarchy.
     * This method should process the <code>MouseEvent</code> and change
     * the menu selection if necessary
     * by using <code>MenuSelectionManager</code>'s API
     * Note: you do not have to forward the event to sub-components.
     * This is done automatically by the <code>MenuSelectionManager</code>.
     */
    public void processMouseEvent(MouseEvent event,MenuElement path[],MenuSelectionManager manager);


    /**
     *  Process a key event.
     */
    public void processKeyEvent(KeyEvent event,MenuElement path[],MenuSelectionManager manager);

    /**
     * Call by the <code>MenuSelectionManager</code> when the
     * <code>MenuElement</code> is added or remove from
     * the menu selection.
     */
    public void menuSelectionChanged(boolean isIncluded);

    /**
     * This method should return an array containing the sub-elements for the receiving menu element
     *
     * @return an array of MenuElements
     */
    public MenuElement[] getSubElements();

    /**
     * This method should return the java.awt.Component used to paint the receiving element.
     * The returned component will be used to convert events and detect if an event is inside
     * a MenuElement's component.
     *
     * @return the Component value
     */
    public Component getComponent();
}
