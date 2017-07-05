/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.pcosnaming;

// Import general CORBA classes
import org.omg.CORBA.SystemException;
import org.omg.CORBA.ORB;
import org.omg.CORBA.INTERNAL;

// Get org.omg.CosNaming Types
import org.omg.CosNaming.Binding;
import org.omg.CosNaming.BindingType;
import org.omg.CosNaming.BindingTypeHolder;
import org.omg.CosNaming.NameComponent;
import org.omg.PortableServer.POA;

// Get base implementation
import com.sun.corba.se.impl.naming.pcosnaming.NamingContextImpl;
import com.sun.corba.se.impl.naming.pcosnaming.InternalBindingValue;

import com.sun.corba.se.impl.naming.cosnaming.BindingIteratorImpl;

// Get a hash table
import java.util.Hashtable;
import java.util.Enumeration;

/**
 * Class TransientBindingIterator implements the abstract methods
 * defined by BindingIteratorImpl, to use with the TransientNamingContext
 * implementation of the NamingContextImpl. The TransientBindingIterator
 * implementation receives a hash table of InternalBindingValues, and uses
 * an Enumeration to iterate over the contents of the hash table.
 * @see BindingIteratorImpl
 * @see TransientNamingContext
 */
public class PersistentBindingIterator extends BindingIteratorImpl
{
    private POA biPOA;
    /**
     * Constructs a new PersistentBindingIterator object.
     * @param orb a org.omg.CORBA.ORB object.
     * @param aTable A hashtable containing InternalBindingValues which is
     * the content of the PersistentNamingContext.
     * @exception Exception a Java exception thrown of the base class cannot
     * initialize.
   */
    public PersistentBindingIterator(org.omg.CORBA.ORB orb, Hashtable aTable,
        POA thePOA ) throws java.lang.Exception
    {
        super(orb);
        this.orb = orb;
        theHashtable = aTable;
        theEnumeration = this.theHashtable.keys();
        currentSize = this.theHashtable.size();
        biPOA = thePOA;
    }

    /**
   * Returns the next binding in the NamingContext. Uses the enumeration
   * object to determine if there are more bindings and if so, returns
   * the next binding from the InternalBindingValue.
   * @param b The Binding as an out parameter.
   * @return true if there were more bindings.
   */
    final public boolean NextOne(org.omg.CosNaming.BindingHolder b)
    {
        // If there are more elements get the next element
        boolean hasMore = theEnumeration.hasMoreElements();
        if (hasMore) {
            InternalBindingKey theBindingKey =
                 ((InternalBindingKey)theEnumeration.nextElement());
            InternalBindingValue theElement =
                (InternalBindingValue)theHashtable.get( theBindingKey );
            NameComponent n = new NameComponent( theBindingKey.id, theBindingKey.kind );
            NameComponent[] nlist = new NameComponent[1];
            nlist[0] = n;
            BindingType theType = theElement.theBindingType;

            b.value =
                new Binding( nlist, theType );
        } else {
            // Return empty but marshalable binding
            b.value = new Binding(new NameComponent[0],BindingType.nobject);
        }
        return hasMore;
    }

    /**
   * Destroys this BindingIterator by disconnecting from the ORB
   * @exception org.omg.CORBA.SystemException One of a fixed set of CORBA system exceptions.
   */
    final public void Destroy()
    {
        // Remove the object from the Active Object Map.
        try {
            byte[] objectId = biPOA.servant_to_id( this );
            if( objectId != null ) {
                biPOA.deactivate_object( objectId );
            }
        }
        catch( Exception e ) {
            throw new INTERNAL( "Exception in BindingIterator.Destroy " + e );
        }
    }

    /**
   * Returns the remaining number of elements in the iterator.
   * @return the remaining number of elements in the iterator.
   */
    public final int RemainingElements() {
        return currentSize;
    }

    private int currentSize;
    private Hashtable theHashtable;
    private Enumeration theEnumeration;
    private org.omg.CORBA.ORB orb;
}
