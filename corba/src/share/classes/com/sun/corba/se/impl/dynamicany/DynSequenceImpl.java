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
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.omg.CORBA.TypeCodePackage.Bounds;
import org.omg.CORBA.portable.InputStream;
import org.omg.CORBA.portable.OutputStream;
import org.omg.DynamicAny.*;
import org.omg.DynamicAny.DynAnyPackage.TypeMismatch;
import org.omg.DynamicAny.DynAnyPackage.InvalidValue;
import org.omg.DynamicAny.DynAnyFactoryPackage.InconsistentTypeCode;

import com.sun.corba.se.spi.orb.ORB ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

// _REVIST_ Could make this a subclass of DynArrayImpl
// But that would mean that an object that implements DynSequence also implements DynArray
// which the spec doesn't mention (it also doesn't forbid it).
public class DynSequenceImpl extends DynAnyCollectionImpl implements DynSequence
{
    //
    // Constructors
    //

    private DynSequenceImpl() {
        this(null, (Any)null, false);
    }

    protected DynSequenceImpl(ORB orb, Any any, boolean copyValue) {
        super(orb, any, copyValue);
    }

    // Sets the current position to -1 and creates an empty sequence.
    protected DynSequenceImpl(ORB orb, TypeCode typeCode) {
        super(orb, typeCode);
    }

    // Initializes components and anys representation
    // from the Any representation
    protected boolean initializeComponentsFromAny() {
        // This typeCode is of kind tk_sequence.
        TypeCode typeCode = any.type();
        int length;
        TypeCode contentType = getContentType();
        InputStream input;

        try {
            input = any.create_input_stream();
        } catch (BAD_OPERATION e) {
            return false;
        }

        length = input.read_long();
        components = new DynAny[length];
        anys = new Any[length];

        for (int i=0; i<length; i++) {
            // _REVISIT_ Could use read_xxx_array() methods on InputStream for efficiency
            // but only for primitive types
            anys[i] = DynAnyUtil.extractAnyFromStream(contentType, input, orb);
            try {
                // Creates the appropriate subtype without copying the Any
                components[i] = DynAnyUtil.createMostDerivedDynAny(anys[i], orb, false);
            } catch (InconsistentTypeCode itc) { // impossible
            }
        }
        return true;
    }

    // Sets the current position to -1 and creates an empty sequence.
    protected boolean initializeComponentsFromTypeCode() {
        // already done in the type code constructor
        components = new DynAny[0];
        anys = new Any[0];
        return true;
    }

    // Collapses the whole DynAny hierarchys values into one single streamed Any
    protected boolean initializeAnyFromComponents() {
        OutputStream out = any.create_output_stream();
        // Writing the length first is the only difference to supers implementation
        out.write_long(components.length);
        for (int i=0; i<components.length; i++) {
            if (components[i] instanceof DynAnyImpl) {
                ((DynAnyImpl)components[i]).writeAny(out);
            } else {
                // Not our implementation. Nothing we can do to prevent copying.
                components[i].to_any().write_value(out);
            }
        }
        any.read_value(out.create_input_stream(), any.type());
        return true;
    }


    //
    // DynSequence interface methods
    //

    // Returns the current length of the sequence
    public int get_length() {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        return (checkInitComponents() ? components.length : 0);
    }

    // Sets the length of the sequence. Increasing the length of a sequence
    // adds new elements at the tail without affecting the values of already
    // existing elements. Newly added elements are default-initialized.
    //
    // Increasing the length of a sequence sets the current position to the first
    // newly-added element if the previous current position was -1.
    // Otherwise, if the previous current position was not -1,
    // the current position is not affected.
    //
    // Increasing the length of a bounded sequence to a value larger than the bound
    // raises InvalidValue.
    //
    // Decreasing the length of a sequence removes elements from the tail
    // without affecting the value of those elements that remain.
    // The new current position after decreasing the length of a sequence is determined
    // as follows:
    // ?f the length of the sequence is set to zero, the current position is set to -1.
    // ?f the current position is -1 before decreasing the length, it remains at -1.
    // ?f the current position indicates a valid element and that element is not removed
    // when the length is decreased, the current position remains unaffected.
    // ?f the current position indicates a valid element and that element is removed, the
    // current position is set to -1.
    public void set_length(int len)
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (status == STATUS_DESTROYED) {
            throw wrapper.dynAnyDestroyed() ;
        }
        int bound = getBound();
        if (bound > 0 && len > bound) {
            throw new InvalidValue();
        }

        checkInitComponents();

        int oldLength = components.length;
        if (len > oldLength) {
            // Increase length
            DynAny[] newComponents = new DynAny[len];
            Any[] newAnys = new Any[len];
            System.arraycopy(components, 0, newComponents, 0, oldLength);
            System.arraycopy(anys, 0, newAnys, 0, oldLength);
            components = newComponents;
            anys = newAnys;

            // Newly added elements are default-initialized
            TypeCode contentType = getContentType();
            for (int i=oldLength; i<len; i++) {
                createDefaultComponentAt(i, contentType);
            }

            // Increasing the length of a sequence sets the current position to the first
            // newly-added element if the previous current position was -1.
            if (index == NO_INDEX)
                index = oldLength;
        } else if (len < oldLength) {
            // Decrease length
            DynAny[] newComponents = new DynAny[len];
            Any[] newAnys = new Any[len];
            System.arraycopy(components, 0, newComponents, 0, len);
            System.arraycopy(anys, 0, newAnys, 0, len);
            // It is probably right not to destroy the released component DynAnys.
            // Some other DynAny or a user variable might still hold onto them
            // and if not then the garbage collector will take care of it.
            //for (int i=len; i<oldLength; i++) {
            //    components[i].destroy();
            //}
            components = newComponents;
            anys = newAnys;

            // ?f the length of the sequence is set to zero, the current position is set to -1.
            // ?f the current position is -1 before decreasing the length, it remains at -1.
            // ?f the current position indicates a valid element and that element is not removed
            // when the length is decreased, the current position remains unaffected.
            // ?f the current position indicates a valid element and that element is removed,
            // the current position is set to -1.
            if (len == 0 || index >= len) {
                index = NO_INDEX;
            }
        } else {
            // Length unchanged
            // Maybe components is now default initialized from type code
            if (index == NO_INDEX && len > 0) {
                index = 0;
            }
        }
    }

    // Initializes the elements of the sequence.
    // The length of the DynSequence is set to the length of value.
    // The current position is set to zero if value has non-zero length
    // and to -1 if value is a zero-length sequence.
    // If the length of value exceeds the bound of a bounded sequence,
    // the operation raises InvalidValue.
    // If value contains one or more elements whose TypeCode is not equivalent
    // to the element TypeCode of the DynSequence, the operation raises TypeMismatch.
/*
    public void set_elements(org.omg.CORBA.Any[] value)
        throws org.omg.DynamicAny.DynAnyPackage.TypeMismatch,
               org.omg.DynamicAny.DynAnyPackage.InvalidValue;
*/

    //
    // Utility methods
    //

    protected void checkValue(Object[] value)
        throws org.omg.DynamicAny.DynAnyPackage.InvalidValue
    {
        if (value == null || value.length == 0) {
            clearData();
            index = NO_INDEX;
            return;
        } else {
            index = 0;
        }
        int bound = getBound();
        if (bound > 0 && value.length > bound) {
            throw new InvalidValue();
        }
    }
}
