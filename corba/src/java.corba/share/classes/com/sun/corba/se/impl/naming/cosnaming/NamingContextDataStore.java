/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.cosnaming;

// Import general CORBA classes
import org.omg.CORBA.Object;

// Import org.omg.CosNaming classes
import org.omg.CosNaming.BindingType;
import org.omg.CosNaming.BindingTypeHolder;
import org.omg.CosNaming.BindingListHolder;
import org.omg.CosNaming.BindingIteratorHolder;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContext;
import org.omg.PortableServer.POA;

/**
 * This interface defines a set of methods that must be implemented by the
 * "data store" associated with a NamingContext implementation.
 * It allows for different implementations of naming contexts that
 * support the same API but differ in storage mechanism.
 */
public interface NamingContextDataStore {
    /**
     * Method which implements binding a name to an object as
     * the specified binding type.
     * @param n a NameComponent which is the name under which the object
     * will be bound.
     * @param obj the object reference to be bound.
     * @param bt Type of binding (as object or as context).
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
     */
    void Bind(NameComponent n, org.omg.CORBA.Object obj, BindingType bt)
        throws org.omg.CORBA.SystemException;

    /**
     * Method which implements resolving the specified name,
     * returning the type of the binding and the bound object reference.
     * If the id and kind of the NameComponent are both empty, the initial
     * naming context (i.e., the local root) must be returned.
     * @param n a NameComponent which is the name to be resolved.
     * @param bth the BindingType as an out parameter.
     * @return the object reference bound under the supplied name.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
     */
    org.omg.CORBA.Object Resolve(NameComponent n,BindingTypeHolder bth)
        throws org.omg.CORBA.SystemException;

    /**
     * Method which implements unbinding a name.
     * @return the object reference bound to the name, or null if not found.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
     */
    org.omg.CORBA.Object Unbind(NameComponent n)
        throws org.omg.CORBA.SystemException;

    /**
     * Method which implements listing the contents of this
     * NamingContext and return a binding list and a binding iterator.
     * @param how_many The number of requested bindings in the BindingList.
     * @param bl The BindingList as an out parameter.
     * @param bi The BindingIterator as an out parameter.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
     */
    void List(int how_many, BindingListHolder bl, BindingIteratorHolder bi)
        throws org.omg.CORBA.SystemException;

    /**
     * Method which implements creating a new NamingContext.
     * @return an object reference for a new NamingContext object implemented
     * by this Name Server.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
     */
    NamingContext NewContext()
        throws org.omg.CORBA.SystemException;

    /**
     * Method which implements destroying this NamingContext.
     * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
     */
    void Destroy()
        throws org.omg.CORBA.SystemException;

    /**
     * Method which returns whether this NamingContext is empty
     * or not.
     * @return true if this NamingContext contains no bindings.
     */
    boolean IsEmpty();

    POA getNSPOA( );
}
