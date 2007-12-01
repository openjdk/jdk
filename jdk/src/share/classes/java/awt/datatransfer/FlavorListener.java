/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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
package java.awt.datatransfer;

import java.util.EventListener;


/**
 * Defines an object which listens for {@link FlavorEvent}s.
 *
 * @author Alexander Gerasimov
 * @since 1.5
 */
public interface FlavorListener extends EventListener {
    /**
     * Invoked when the target {@link Clipboard} of the listener
     * has changed its available {@link DataFlavor}s.
     * <p>
     * Some notifications may be redundant &#151; they are not
     * caused by a change of the set of DataFlavors available
     * on the clipboard.
     * For example, if the clipboard subsystem supposes that
     * the system clipboard's contents has been changed but it
     * can't ascertain whether its DataFlavors have been changed
     * because of some exceptional condition when accessing the
     * clipboard, the notification is sent to ensure from omitting
     * a significant notification. Ordinarily, those redundant
     * notifications should be occasional.
     *
     * @param e  a <code>FlavorEvent</code> object
     */
    void flavorsChanged(FlavorEvent e);
}
