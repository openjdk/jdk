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

package javax.accessibility;

/**
 * The AccessibleKeyBinding interface should be supported by any object
 * that has a keyboard bindings such as a keyboard mnemonic and/or keyboard
 * shortcut which can be used to select the object.  This interface provides
 * the standard mechanism for an assistive technology to determine the
 * key bindings which exist for this object.
 * Any object that has such key bindings should support this
 * interface.
 *
 * @see Accessible
 * @see Accessible#getAccessibleContext
 * @see AccessibleContext
 *
 * @author      Lynn Monsanto
 * @since 1.4
 */
public interface AccessibleKeyBinding {

    /**
     * Returns the number of key bindings for this object
     *
     * @return the zero-based number of key bindings for this object
     */
    public int getAccessibleKeyBindingCount();

    /**
     * Returns a key binding for this object.  The value returned is an
     * java.lang.Object which must be cast to appropriate type depending
     * on the underlying implementation of the key.
     *
     * @param i zero-based index of the key bindings
     * @return a javax.lang.Object which specifies the key binding
     * @see #getAccessibleKeyBindingCount
     */
    public java.lang.Object getAccessibleKeyBinding(int i);
}
