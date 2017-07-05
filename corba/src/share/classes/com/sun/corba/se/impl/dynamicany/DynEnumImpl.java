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

package com.sun.corba.se.impl.dynamicany;

import org.omg.CORBA.TypeCode;
import org.omg.CORBA.Any;
import org.omg.CORBA.BAD_OPERATION;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;
import org.omg.DynamicAny.*;
import org.omg.DynamicAny.DynAnyPackage.*;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class DynEnumImpl extends DynAnyBasicImpl implements DynEnum
{
    //
    // Instance variables
    //

    // This int and the any value are kept in sync at all times
    int currentEnumeratorIndex = NO_INDEX;

    //
    // Constructors
    //

    private DynEnumImpl() {
        this(null, (Any)null, false);
    }

    // The current position of a DynEnum is always -1.
    protected DynEnumImpl(ORB orb, Any anAny, boolean copyValue) {
        super(orb, anAny, copyValue);
        index = NO_INDEX;
        // The any doesn't have to be initialized. We have a default value in this case.
        try {
            currentEnumeratorIndex = any.extract_long();
        } catch (BAD_OPERATION e) {
            // _REVISIT_: Fix Me
            currentEnumeratorIndex = 0;
            any.type(any.type());
            any.insert_long(0);
        }
    }

    // Sets the current position to -1 and sets the value of the enumerator
    // to the first enumerator value indicated by the TypeCode.
    protected DynEnumImpl(ORB orb, TypeCode typeCode) {
        super(orb, typeCode);
        index = NO_INDEX;
        currentEnumeratorIndex = 0;
        any.insert_long(0);
    }

    //
    // Utility methods
    //

    private int memberCount() {
        int memberCount = 0;
        try {
            memberCount = any.type().member_count();
        } catch (BadKind bad) {
        }
        return memberCount;
    }

    private String memberName(int i) {
        String memberName = null;
        try {
            memberName = any.type().member_name(i);
        } catch (BadKind bad) {
        } catch (Bounds bounds) {
        }
        return memberName;
    }

    private int computeCurrentEnumeratorIndex(String value) {
        int memberCount = memberCount();
        for (int i=0; i<memberCount; i++) {
            if (memberName(i).equals(value)) {
                return i;
            }
        }
        return NO_INDEX;
    }

    //
    // DynAny interface methods
    //

    // Returns always 0 for DynEnum
    public int component_count() {
        return 0;
    }

    // Calling current_component on a DynAny that cannot have components,
    // such as a DynEnum or an empty exception, raises TypeMismatch.
    public org.omg.DynamicAny.DynAny current_component()
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        throw new TypeMismatch();
    }

    //
    // DynEnum interface methods
    //

    // Returns the value of the DynEnum as an IDL identifier.
    public String get_as_string () {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        return memberName(currentEnumeratorIndex);
    }

    // Sets the value of the DynEnum to the enumerated value
    // whose IDL identifier is passed in the value parameter.
    // If value contains a string that is not a valid IDL identifier
    // for the corresponding enumerated type, the operation raises InvalidValue.
    public void set_as_string (String value)
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        int newIndex = computeCurrentEnumeratorIndex(value);
        if (newIndex == NO_INDEX) {
            throw new InvalidValue();
        }
        currentEnumeratorIndex = newIndex;
        any.insert_long(newIndex);
    }

    // Returns the value of the DynEnum as the enumerated values ordinal value.
    // Enumerators have ordinal values 0 to n-1,
    // as they appear from left to right in the corresponding IDL definition.
    public int get_as_ulong () {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        return currentEnumeratorIndex;
    }

    // Sets the value of the DynEnum as the enumerated values ordinal value.
    // If value contains a value that is outside the range of ordinal values
    // for the corresponding enumerated type, the operation raises InvalidValue.
    public void set_as_ulong (int value)
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        if (value < 0 || value >= memberCount()) {
            throw new InvalidValue();
        }
        currentEnumeratorIndex = value;
        any.insert_long(value);
    }
}
