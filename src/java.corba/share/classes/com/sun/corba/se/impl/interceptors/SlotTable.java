/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.interceptors;

import com.sun.corba.se.impl.corba.AnyImpl;
import com.sun.corba.se.spi.orb.ORB;
import org.omg.PortableInterceptor.Current;
import org.omg.PortableInterceptor.InvalidSlot;
import org.omg.CORBA.Any;

/**
 * SlotTable is used internally by PICurrent to store the slot information.
 */
public class SlotTable {
    // The vector where all the slot data for the current thread is stored
    private Any[] theSlotData;

    // Required for instantiating Any object.
    private ORB orb;

    // The flag to check whether there are any updates in the current SlotTable.
    // The slots will be reset to null, only if this flag is set.
    private boolean dirtyFlag;

    /**
     * The constructor instantiates an Array of Any[] of size given by slotSize
     * parameter.
     */
    SlotTable( ORB orb, int slotSize ) {
        dirtyFlag = false;
        this.orb = orb;
        theSlotData = new Any[slotSize];
    }

    /**
     * This method sets the slot data at the given slot id (index).
     */
    public void set_slot( int id, Any data ) throws InvalidSlot
    {
        // First check whether the slot is allocated
        // If not, raise the invalid slot exception
        if( id >= theSlotData.length ) {
            throw new InvalidSlot();
        }
        dirtyFlag = true;
        theSlotData[id] = data;
    }

    /**
     * This method get the slot data for the given slot id (index).
     */
    public Any get_slot( int id ) throws InvalidSlot
    {
        // First check whether the slot is allocated
        // If not, raise the invalid slot exception
        if( id >= theSlotData.length ) {
            throw new InvalidSlot();
        }
        if( theSlotData[id] == null ) {
            theSlotData [id] = new AnyImpl(orb);
        }
        return theSlotData[ id ];
    }


    /**
     * This method resets all the slot data to null if dirtyFlag is set.
     */
    void resetSlots( ) {
        if( dirtyFlag == true ) {
            for( int i = 0; i < theSlotData.length; i++ ) {
                theSlotData[i] = null;
            }
        }
    }

    /**
     * This method returns the size of the allocated slots.
     */
    int getSize( ) {
        return theSlotData.length;
    }

}
