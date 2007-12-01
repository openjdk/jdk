/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.dynamicany;

import org.omg.CORBA.TypeCode;
import org.omg.CORBA.TCKind;
import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;
import org.omg.DynamicAny.*;
import org.omg.DynamicAny.DynAnyPackage.TypeMismatch;
import org.omg.DynamicAny.DynAnyPackage.InvalidValue;
import org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

abstract class DynValueCommonImpl extends DynAnyComplexImpl implements DynValueCommon
{
    //
    // Constructors
    //

    protected boolean isNull;

    private DynValueCommonImpl() {
        this(null, (Any)null, false);
        isNull = true;
    }

    protected DynValueCommonImpl(ORB orb, Any any, boolean copyValue) {
        super(orb, any, copyValue);
        isNull = checkInitComponents();
    }

    protected DynValueCommonImpl(ORB orb, TypeCode typeCode) {
        super(orb, typeCode);
        isNull = true;
    }

    //
    // DynValueCommon methods
    //

    // Returns TRUE if this object represents a null valuetype
    public boolean is_null() {
        return isNull;
    }

    // Changes the representation to a null valuetype.
    public void set_to_null() {
        isNull = true;
        clearData();
    }

    // If this object represents a null valuetype then this operation
    // replaces it with a newly constructed value with its components
    // initialized to default values as in DynAnyFactory::create_dyn_any_from_type_code.
    // If this object represents a non-null valuetype, then this operation has no effect.
    public void set_to_value() {
        if (isNull) {
            isNull = false;
            // the rest is done lazily
        }
        // else: there is nothing to do
    }

    //
    // Methods differing from DynStruct
    //

    // Required to raise InvalidValue if this is a null value type.
    public org.omg.DynamicAny.NameValuePair[] get_members ()
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        if (isNull) {
            throw new InvalidValue();
        }
        checkInitComponents();
        return nameValuePairs;
    }

    // Required to raise InvalidValue if this is a null value type.
    public org.omg.DynamicAny.NameDynAnyPair[] get_members_as_dyn_any ()
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        if (isNull) {
            throw new InvalidValue();
        }
        checkInitComponents();
        return nameDynAnyPairs;
    }

    //
    // Overridden methods
    //

    // Overridden to change to non-null status.
    public void set_members (org.omg.DynamicAny.NameValuePair[] value)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch,
               org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        super.set_members(value);
        // If we didn't get an exception then this must be a valid non-null value
        isNull = false;
    }

    // Overridden to change to non-null status.
    public void set_members_as_dyn_any (org.omg.DynamicAny.NameDynAnyPair[] value)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch,
               org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        super.set_members_as_dyn_any(value);
        // If we didn't get an exception then this must be a valid non-null value
        isNull = false;
    }
}
