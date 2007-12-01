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

public class DynValueBoxImpl extends DynValueCommonImpl implements DynValueBox
{
    //
    // Constructors
    //

    private DynValueBoxImpl() {
        this(null, (Any)null, false);
    }

    protected DynValueBoxImpl(ORB orb, Any any, boolean copyValue) {
        super(orb, any, copyValue);
    }

    protected DynValueBoxImpl(ORB orb, TypeCode typeCode) {
        super(orb, typeCode);
    }

    //
    // DynValueBox methods
    //

    public Any get_boxed_value()
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (isNull) {
            throw new InvalidValue();
        }
        checkInitAny();
        return any;
    }

    public void set_boxed_value(org.omg.CORBA.Any boxed)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch
    {
        if ( ! isNull && ! boxed.type().equal(this.type())) {
            throw new TypeMismatch();
        }
        clearData();
        any = boxed;
        representations = REPRESENTATION_ANY;
        index = 0;
        isNull = false;
    }

    public DynAny get_boxed_value_as_dyn_any()
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (isNull) {
            throw new InvalidValue();
        }
        checkInitComponents();
        return components[0];
    }

    public void set_boxed_value_as_dyn_any(DynAny boxed)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch
    {
        if ( ! isNull && ! boxed.type().equal(this.type())) {
            throw new TypeMismatch();
        }
        clearData();
        components = new DynAny[] {boxed};
        representations = REPRESENTATION_COMPONENTS;
        index = 0;
        isNull = false;
    }

    protected boolean initializeComponentsFromAny() {
        try {
            components = new DynAny[] {DynAnyUtil.createMostDerivedDynAny(any, orb, false)};
        } catch (InconsistentTypeCode ictc) {
            return false; // impossible
        }
        return true;
    }

    protected boolean initializeComponentsFromTypeCode() {
        try {
            any = DynAnyUtil.createDefaultAnyOfType(any.type(), orb);
            components = new DynAny[] {DynAnyUtil.createMostDerivedDynAny(any, orb, false)};
        } catch (InconsistentTypeCode ictc) {
            return false; // impossible
        }
        return true;
    }

    protected boolean initializeAnyFromComponents() {
        any = getAny(components[0]);
        return true;
    }
}
