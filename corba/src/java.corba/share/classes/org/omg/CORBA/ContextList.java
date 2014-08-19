/*
 * Copyright (c) 1996, 1999, Oracle and/or its affiliates. All rights reserved.
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

package org.omg.CORBA;

/**
 * An object containing a modifiable list of <code>String</code> objects
 * that represent property names.
 * This class is used in <code>Request</code> operations to
 * describe the contexts that need to be resolved and sent with the
 * invocation.  (A context is resolved by giving a property name
 * and getting back the value associated with it.)  This is done
 * by calling the <code>Context</code> method
 * <code>get_values</code> and supplying a string from a
 * <code>ContextList</code> object as the third parameter.
 * The method <code>get_values</code> returns an <code>NVList</code>
 * object containing the <code>NamedValue</code> objects that hold
 * the value(s) identified by the given string.
 * <P>
 * A <code>ContextList</code> object is created by the ORB, as
 * illustrated here:
 * <PRE>
 *   ORB orb = ORB.init(args, null);
 *   org.omg.CORBA.ContextList ctxList = orb.create_context_list();
 * </PRE>
 * The variable <code>ctxList</code> represents an empty
 * <code>ContextList</code> object.  Strings are added to
 * the list with the method <code>add</code>, accessed
 * with the method <code>item</code>, and removed with the
 * method <code>remove</code>.
 *
 * @see Context
 * @since   JDK1.2
 */

public abstract class ContextList {

    /**
     * Returns the number of <code>String</code> objects in this
     * <code>ContextList</code> object.
     *
     * @return                  an <code>int</code> representing the number of
     * <code>String</code>s in this <code>ContextList</code> object
     */

    public abstract int count();

    /**
     * Adds a <code>String</code> object to this <code>ContextList</code>
     * object.
     *
     * @param ctx               the <code>String</code> object to be added
     */

    public abstract void add(String ctx);

    /**
     * Returns the <code>String</code> object at the given index.
     *
     * @param index             the index of the string desired, with 0 being the
     index of the first string
     * @return                  the string at the given index
     * @exception org.omg.CORBA.Bounds  if the index is greater than
     *                          or equal to the number of strings in this
     *                <code>ContextList</code> object
     */

    public abstract String item(int index) throws org.omg.CORBA.Bounds;

    /**
     * Removes the <code>String</code> object at the given index. Note that
     * the indices of all strings following the one removed are
     * shifted down by one.
     *
     * @param index     the index of the <code>String</code> object to be removed,
     *                with 0 designating the first string
     * @exception org.omg.CORBA.Bounds  if the index is greater than
     *                          or equal to the number of <code>String</code> objects in
     *                this <code>ContextList</code> object
     */

    public abstract void remove(int index) throws org.omg.CORBA.Bounds;

}
