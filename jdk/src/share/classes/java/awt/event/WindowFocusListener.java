/*
 * Copyright 2000-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * The listener interface for receiving <code>WindowEvents</code>, including
 * <code>WINDOW_GAINED_FOCUS</code> and <code>WINDOW_LOST_FOCUS</code> events.
 * The class that is interested in processing a <code>WindowEvent</code>
 * either implements this interface (and
 * all the methods it contains) or extends the abstract
 * <code>WindowAdapter</code> class (overriding only the methods of interest).
 * The listener object created from that class is then registered with a
 * <code>Window</code>
 * using the <code>Window</code>'s <code>addWindowFocusListener</code> method.
 * When the <code>Window</code>'s
 * status changes by virtue of it being opened, closed, activated, deactivated,
 * iconified, or deiconified, or by focus being transfered into or out of the
 * <code>Window</code>, the relevant method in the listener object is invoked,
 * and the <code>WindowEvent</code> is passed to it.
 *
 * @author David Mendenhall
 *
 * @see WindowAdapter
 * @see WindowEvent
 * @see <a href="http://java.sun.com/docs/books/tutorial/post1.0/ui/windowlistener.html">Tutorial: Writing a Window Listener</a>
 *
 * @since 1.4
 */
public interface WindowFocusListener extends EventListener {

    /**
     * Invoked when the Window is set to be the focused Window, which means
     * that the Window, or one of its subcomponents, will receive keyboard
     * events.
     */
    public void windowGainedFocus(WindowEvent e);

    /**
     * Invoked when the Window is no longer the focused Window, which means
     * that keyboard events will no longer be delivered to the Window or any of
     * its subcomponents.
     */
    public void windowLostFocus(WindowEvent e);
}
