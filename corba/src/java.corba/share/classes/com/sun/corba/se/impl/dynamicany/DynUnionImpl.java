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
import org.omg.CORBA.TCKind;
import org.omg.CORBA.Any;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;
import org.omg.CORBA.portable.InputStream;
import org.omg.DynamicAny.*;
import org.omg.DynamicAny.DynAnyPackage.TypeMismatch;
import org.omg.DynamicAny.DynAnyPackage.InvalidValue;
import org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class DynUnionImpl extends DynAnyConstructedImpl implements DynUnion
{
    //
    // Instance variables
    //

    DynAny discriminator = null;
    // index either points to the discriminator or the named member is it exists.
    // The currently active member, which is of the same type as the discriminator.
    DynAny currentMember = null;
    int currentMemberIndex = NO_INDEX;

    //
    // Constructors
    //

    private DynUnionImpl() {
        this(null, (Any)null, false);
    }

    protected DynUnionImpl(ORB orb, Any any, boolean copyValue) {
        // We can be sure that typeCode is of kind tk_union
        super(orb, any, copyValue);
    }

    protected DynUnionImpl(ORB orb, TypeCode typeCode) {
        // We can be sure that typeCode is of kind tk_union
        super(orb, typeCode);
    }

    protected boolean initializeComponentsFromAny() {
        try {
            InputStream input = any.create_input_stream();
            Any discriminatorAny = DynAnyUtil.extractAnyFromStream(discriminatorType(), input, orb);
            discriminator = DynAnyUtil.createMostDerivedDynAny(discriminatorAny, orb, false);
            currentMemberIndex = currentUnionMemberIndex(discriminatorAny);
            Any memberAny = DynAnyUtil.extractAnyFromStream(memberType(currentMemberIndex), input, orb);
            currentMember = DynAnyUtil.createMostDerivedDynAny(memberAny, orb, false);
            components = new DynAny[] {discriminator, currentMember};
        } catch (InconsistentTypeCode ictc) { // impossible
        }
        return true;
    }

    // Sets the current position to zero.
    // The discriminator value is set to a value consistent with the first named member
    // of the union. That member is activated and (recursively) initialized to its default value.
    protected boolean initializeComponentsFromTypeCode() {
        //System.out.println(this + " initializeComponentsFromTypeCode");
        try {
            // We can be sure that memberCount() > 0 according to the IDL language spec
            discriminator = DynAnyUtil.createMostDerivedDynAny(memberLabel(0), orb, false);
            index = 0;
            currentMemberIndex = 0;
            currentMember = DynAnyUtil.createMostDerivedDynAny(memberType(0), orb);
            components = new DynAny[] {discriminator, currentMember};
        } catch (InconsistentTypeCode ictc) { // impossible
        }
        return true;
    }

    //
    // Convenience methods
    //

    private TypeCode discriminatorType() {
        TypeCode discriminatorType = null;
        try {
            discriminatorType = any.type().discriminator_type();
        } catch (BadKind bad) {
        }
        return discriminatorType;
    }

    private int memberCount() {
        int memberCount = 0;
        try {
            memberCount = any.type().member_count();
        } catch (BadKind bad) {
        }
        return memberCount;
    }

    private Any memberLabel(int i) {
        Any memberLabel = null;
        try {
            memberLabel = any.type().member_label(i);
        } catch (BadKind bad) {
        } catch (Bounds bounds) {
        }
        return memberLabel;
    }

    private TypeCode memberType(int i) {
        TypeCode memberType = null;
        try {
            memberType = any.type().member_type(i);
        } catch (BadKind bad) {
        } catch (Bounds bounds) {
        }
        return memberType;
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

    private int defaultIndex() {
        int defaultIndex = -1;
        try {
            defaultIndex = any.type().default_index();
        } catch (BadKind bad) {
        }
        return defaultIndex;
    }

    private int currentUnionMemberIndex(Any discriminatorValue) {
        int memberCount = memberCount();
        Any memberLabel;
        for (int i=0; i<memberCount; i++) {
            memberLabel = memberLabel(i);
            if (memberLabel.equal(discriminatorValue)) {
                return i;
            }
        }
        if (defaultIndex() != -1) {
            return defaultIndex();
        }
        return NO_INDEX;
    }

    protected void clearData() {
        super.clearData();
        discriminator = null;
        // Necessary to guarantee OBJECT_NOT_EXIST in member()
        currentMember.destroy();
        currentMember = null;
        currentMemberIndex = NO_INDEX;
    }

    //
    // DynAny interface methods
    //

    // _REVISIT_ More efficient copy operation

    //
    // DynUnion interface methods
    //

    /**
    * Returns the current discriminator value.
    */
    public org.omg.DynamicAny.DynAny get_discriminator () {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        return (checkInitComponents() ? discriminator : null);
    }

    // Sets the discriminator of the DynUnion to the specified value.
    // If the TypeCode of the parameter is not equivalent
    // to the TypeCode of the unions discriminator, the operation raises TypeMismatch.
    //
    // Setting the discriminator to a value that is consistent with the currently
    // active union member does not affect the currently active member.
    // Setting the discriminator to a value that is inconsistent with the currently
    // active member deactivates the member and activates the member that is consistent
    // with the new discriminator value (if there is a member for that value)
    // by initializing the member to its default value.
    //
    // If the discriminator value indicates a non-existent union member
    // this operation sets the current position to 0
    // (has_no_active_member returns true in this case).
    // Otherwise the current position is set to 1 (has_no_active_member returns false and
    // component_count returns 2 in this case).
    public void set_discriminator (org.omg.DynamicAny.DynAny newDiscriminator)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        if ( ! newDiscriminator.type().equal(discriminatorType())) {
            throw new TypeMismatch();
        }
        newDiscriminator = DynAnyUtil.convertToNative(newDiscriminator, orb);
        Any newDiscriminatorAny = getAny(newDiscriminator);
        int newCurrentMemberIndex = currentUnionMemberIndex(newDiscriminatorAny);
        if (newCurrentMemberIndex == NO_INDEX) {
            clearData();
            index = 0;
        } else {
            // _REVISIT_ Could possibly optimize here if we don't need to initialize components
            checkInitComponents();
            if (currentMemberIndex == NO_INDEX || newCurrentMemberIndex != currentMemberIndex) {
                clearData();
                index = 1;
                currentMemberIndex = newCurrentMemberIndex;
                try {
                currentMember = DynAnyUtil.createMostDerivedDynAny(memberType(currentMemberIndex), orb);
                } catch (InconsistentTypeCode ictc) {}
                discriminator = newDiscriminator;
                components = new DynAny[] { discriminator, currentMember };
                representations = REPRESENTATION_COMPONENTS;
            }
        }
    }

    // Sets the discriminator to a value that is consistent with the value
    // of the default case of a union; it sets the current position to
    // zero and causes component_count to return 2.
    // Calling set_to_default_member on a union that does not have an explicit
    // default case raises TypeMismatch.
    public void set_to_default_member ()
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        int defaultIndex = defaultIndex();
        if (defaultIndex == -1) {
            throw new TypeMismatch();
        }
        try {
            clearData();
            index = 1;
            currentMemberIndex = defaultIndex;
            currentMember = DynAnyUtil.createMostDerivedDynAny(memberType(defaultIndex), orb);
            components = new DynAny[] {discriminator, currentMember};
            Any discriminatorAny = orb.create_any();
            discriminatorAny.insert_octet((byte)0);
            discriminator = DynAnyUtil.createMostDerivedDynAny(discriminatorAny, orb, false);
            representations = REPRESENTATION_COMPONENTS;
        } catch (InconsistentTypeCode ictc) {}
    }

    // Sets the discriminator to a value that does not correspond
    // to any of the unions case labels.
    // It sets the current position to zero and causes component_count to return 1.
    // Calling set_to_no_active_member on a union that has an explicit default case
    // or on a union that uses the entire range of discriminator values
    // for explicit case labels raises TypeMismatch.
    public void set_to_no_active_member ()
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        // _REVISIT_ How does one check for "entire range of discriminator values"?
        if (defaultIndex() != -1) {
            throw new TypeMismatch();
        }
        checkInitComponents();
        Any discriminatorAny = getAny(discriminator);
        // erase the discriminators value so that it does not correspond
        // to any of the unions case labels
        discriminatorAny.type(discriminatorAny.type());
        index = 0;
        currentMemberIndex = NO_INDEX;
        // Necessary to guarantee OBJECT_NOT_EXIST in member()
        currentMember.destroy();
        currentMember = null;
        components[0] = discriminator;
        representations = REPRESENTATION_COMPONENTS;
    }

    // Returns true if the union has no active member
    // (that is, the unions value consists solely of its discriminator because the
    // discriminator has a value that is not listed as an explicit case label).
    // Calling this operation on a union that has a default case returns false.
    // Calling this operation on a union that uses the entire range of discriminator
    // values for explicit case labels returns false.
    public boolean has_no_active_member () {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        // _REVISIT_ How does one check for "entire range of discriminator values"?
        if (defaultIndex() != -1) {
            return false;
        }
        checkInitComponents();
        return (checkInitComponents() ? (currentMemberIndex == NO_INDEX) : false);
    }

    public org.omg.CORBA.TCKind discriminator_kind () {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        return discriminatorType().kind();
    }

    // Returns the currently active member.
    // If the union has no active member, the operation raises InvalidValue.
    // Note that the returned reference remains valid only for as long
    // as the currently active member does not change.
    // Using the returned reference beyond the life time
    // of the currently active member raises OBJECT_NOT_EXIST.
    public org.omg.DynamicAny.DynAny member ()
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        if ( ! checkInitComponents() || currentMemberIndex == NO_INDEX)
            throw new InvalidValue();
        return currentMember;
    }

    // Returns the name of the currently active member.
    // If the unions TypeCode does not contain a member name for the currently active member,
    // the operation returns an empty string.
    // Calling member_name on a union without an active member raises InvalidValue.
    public String member_name ()
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        if ( ! checkInitComponents() || currentMemberIndex == NO_INDEX)
            throw new InvalidValue();
        String memberName = memberName(currentMemberIndex);
        return (memberName == null ? "" : memberName);
    }

    // Returns the TCKind value of the TypeCode of the currently active member.
    // If the union has no active member, the operation raises InvalidValue.
    public org.omg.CORBA.TCKind member_kind ()
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        if ( ! checkInitComponents() || currentMemberIndex == NO_INDEX)
            throw new InvalidValue();
        return memberType(currentMemberIndex).kind();
    }
}
