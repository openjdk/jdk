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
import org.omg.CORBA.NO_IMPLEMENT;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;
import org.omg.DynamicAny.*;
import org.omg.DynamicAny.DynAnyPackage.TypeMismatch;
import org.omg.DynamicAny.DynAnyPackage.InvalidValue;
import org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

abstract class DynAnyCollectionImpl extends DynAnyConstructedImpl
{
    //
    // Instance variables
    //

    // Keep in sync with DynAny[] components at all times.
    Any[] anys = null;

    //
    // Constructors
    //

    private DynAnyCollectionImpl() {
        this(null, (Any)null, false);
    }

    protected DynAnyCollectionImpl(ORB orb, Any any, boolean copyValue) {
        super(orb, any, copyValue);
    }

    protected DynAnyCollectionImpl(ORB orb, TypeCode typeCode) {
        super(orb, typeCode);
    }

    //
    // Utility methods
    //

    protected void createDefaultComponentAt(int i, TypeCode contentType) {
        try {
            components[i] = DynAnyUtil.createMostDerivedDynAny(contentType, orb);
        } catch (InconsistentTypeCode itc) { // impossible
        }
        // get a hold of the default initialized Any without copying
        anys[i] = getAny(components[i]);
    }

    protected TypeCode getContentType() {
        try {
            return any.type().content_type();
        } catch (BadKind badKind) { // impossible
            return null;
        }
    }

    // This method has a different meaning for sequence and array:
    // For sequence value of 0 indicates an unbounded sequence,
    // values > 0 indicate a bounded sequence.
    // For array any value indicates the boundary.
    protected int getBound() {
        try {
            return any.type().length();
        } catch (BadKind badKind) { // impossible
            return 0;
        }
    }

    //
    // DynAny interface methods
    //

    // _REVISIT_ More efficient copy operation

    //
    // Collection methods
    //

    public org.omg.CORBA.Any[] get_elements () {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        return (checkInitComponents() ? anys : null);
    }

    protected abstract void checkValue(Object[] value)
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue;

    // Initializes the elements of the ordered collection.
    // If value does not contain the same number of elements as the array dimension,
    // the operation raises InvalidValue.
    // If one or more elements have a type that is inconsistent with the collections TypeCode,
    // the operation raises TypeMismatch.
    // This operation does not change the current position.
    public void set_elements (org.omg.CORBA.Any[] value)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch,
               org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        checkValue(value);

        components = new DynAny[value.length];
        anys = value;

        // We know that this is of kind tk_sequence or tk_array
        TypeCode expectedTypeCode = getContentType();
        for (int i=0; i<value.length; i++) {
            if (value[i] != null) {
                if (! value[i].type().equal(expectedTypeCode)) {
                    clearData();
                    // _REVISIT_ More info
                    throw new TypeMismatch();
                }
                try {
                    // Creates the appropriate subtype without copying the Any
                    components[i] = DynAnyUtil.createMostDerivedDynAny(value[i], orb, false);
                    //System.out.println(this + " created component " + components[i]);
                } catch (InconsistentTypeCode itc) {
                    throw new InvalidValue();
                }
            } else {
                clearData();
                // _REVISIT_ More info
                throw new InvalidValue();
            }
        }
        index = (value.length == 0 ? NO_INDEX : 0);
        // Other representations are invalidated by this operation
        representations = REPRESENTATION_COMPONENTS;
    }

    public org.omg.DynamicAny.DynAny[] get_elements_as_dyn_any () {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        return (checkInitComponents() ? components : null);
    }

    // Same semantics as set_elements(Any[])
    public void set_elements_as_dyn_any (org.omg.DynamicAny.DynAny[] value)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch,
               org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        checkValue(value);

        components = (value == null ? emptyComponents : value);
        anys = new Any[value.length];

        // We know that this is of kind tk_sequence or tk_array
        TypeCode expectedTypeCode = getContentType();
        for (int i=0; i<value.length; i++) {
            if (value[i] != null) {
                if (! value[i].type().equal(expectedTypeCode)) {
                    clearData();
                    // _REVISIT_ More info
                    throw new TypeMismatch();
                }
                anys[i] = getAny(value[i]);
            } else {
                clearData();
                // _REVISIT_ More info
                throw new InvalidValue();
            }
        }
        index = (value.length == 0 ? NO_INDEX : 0);
        // Other representations are invalidated by this operation
        representations = REPRESENTATION_COMPONENTS;
    }
}
