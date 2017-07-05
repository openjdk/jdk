/*
 * Copyright 1994-1997 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.lang;

/**
 * Thrown when an application attempts to use <code>null</code> in a
 * case where an object is required. These include:
 * <ul>
 * <li>Calling the instance method of a <code>null</code> object.
 * <li>Accessing or modifying the field of a <code>null</code> object.
 * <li>Taking the length of <code>null</code> as if it were an array.
 * <li>Accessing or modifying the slots of <code>null</code> as if it
 *     were an array.
 * <li>Throwing <code>null</code> as if it were a <code>Throwable</code>
 *     value.
 * </ul>
 * <p>
 * Applications should throw instances of this class to indicate
 * other illegal uses of the <code>null</code> object.
 *
 * @author  unascribed
 * @since   JDK1.0
 */
public
class NullPointerException extends RuntimeException {
    /**
     * Constructs a <code>NullPointerException</code> with no detail message.
     */
    public NullPointerException() {
        super();
    }

    /**
     * Constructs a <code>NullPointerException</code> with the specified
     * detail message.
     *
     * @param   s   the detail message.
     */
    public NullPointerException(String s) {
        super(s);
    }
}
