/*
 * Copyright 1994-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.util;

/**
 * An object that implements the Enumeration interface generates a
 * series of elements, one at a time. Successive calls to the
 * <code>nextElement</code> method return successive elements of the
 * series.
 * <p>
 * For example, to print all elements of a <tt>Vector&lt;E&gt;</tt> <i>v</i>:
 * <pre>
 *   for (Enumeration&lt;E&gt; e = v.elements(); e.hasMoreElements();)
 *       System.out.println(e.nextElement());</pre>
 * <p>
 * Methods are provided to enumerate through the elements of a
 * vector, the keys of a hashtable, and the values in a hashtable.
 * Enumerations are also used to specify the input streams to a
 * <code>SequenceInputStream</code>.
 * <p>
 * NOTE: The functionality of this interface is duplicated by the Iterator
 * interface.  In addition, Iterator adds an optional remove operation, and
 * has shorter method names.  New implementations should consider using
 * Iterator in preference to Enumeration.
 *
 * @see     java.util.Iterator
 * @see     java.io.SequenceInputStream
 * @see     java.util.Enumeration#nextElement()
 * @see     java.util.Hashtable
 * @see     java.util.Hashtable#elements()
 * @see     java.util.Hashtable#keys()
 * @see     java.util.Vector
 * @see     java.util.Vector#elements()
 *
 * @author  Lee Boynton
 * @since   JDK1.0
 */
public interface Enumeration<E> {
    /**
     * Tests if this enumeration contains more elements.
     *
     * @return  <code>true</code> if and only if this enumeration object
     *           contains at least one more element to provide;
     *          <code>false</code> otherwise.
     */
    boolean hasMoreElements();

    /**
     * Returns the next element of this enumeration if this enumeration
     * object has at least one more element to provide.
     *
     * @return     the next element of this enumeration.
     * @exception  NoSuchElementException  if no more elements exist.
     */
    E nextElement();
}
