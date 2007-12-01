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
 * interface.  Applications can determine if an object supports the
 * AccessibleKeyBinding interface by first obtaining its AccessibleContext
 * (see @link Accessible} and then calling the
 * {@link AccessibleContext#getAccessibleKeyBinding} method.  If the return
 * value is not null, the object supports this interface.
 *
 * @see Accessible
 * @see Accessible#getAccessibleContext
 * @see AccessibleContext
 * @see AccessibleContext#getAccessibleKeyBinding
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
     * on the underlying implementation of the key.  For example, if the
     * Object returned is a javax.swing.KeyStroke, the user of this
     * method should do the following:
     * <nf><code>
     * Component c = <get the component that has the key bindings>
     * AccessibleContext ac = c.getAccessibleContext();
     * AccessibleKeyBinding akb = ac.getAccessibleKeyBinding();
     * for (int i = 0; i < akb.getAccessibleKeyBindingCount(); i++) {
     *     Object o = akb.getAccessibleKeyBinding(i);
     *     if (o instanceof javax.swing.KeyStroke) {
     *         javax.swing.KeyStroke keyStroke = (javax.swing.KeyStroke)o;
     *         <do something with the key binding>
     *     }
     * }
     * </code></nf>
     *
     * @param i zero-based index of the key bindings
     * @return a javax.lang.Object which specifies the key binding
     * @see #getAccessibleKeyBindingCount
     */
    public java.lang.Object getAccessibleKeyBinding(int i);
}
