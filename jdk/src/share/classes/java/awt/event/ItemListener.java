/*
 * Copyright 1996-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.awt.event;

import java.util.EventListener;

/**
 * The listener interface for receiving item events.
 * The class that is interested in processing an item event
 * implements this interface. The object created with that
 * class is then registered with a component using the
 * component's <code>addItemListener</code> method. When an
 * item-selection event occurs, the listener object's
 * <code>itemStateChanged</code> method is invoked.
 *
 * @author Amy Fowler
 *
 * @see java.awt.ItemSelectable
 * @see ItemEvent
 * @see <a href="http://java.sun.com/docs/books/tutorial/post1.0/ui/itemlistener.html">Tutorial: Writing an Item Listener</a>
 *
 * @since 1.1
 */
public interface ItemListener extends EventListener {

    /**
     * Invoked when an item has been selected or deselected by the user.
     * The code written for this method performs the operations
     * that need to occur when an item is selected (or deselected).
     */
    void itemStateChanged(ItemEvent e);

}
