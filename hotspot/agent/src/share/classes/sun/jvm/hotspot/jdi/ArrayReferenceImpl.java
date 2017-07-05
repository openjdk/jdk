/*
 * Copyright (c) 2002, 2004, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.jdi;

import com.sun.jdi.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import sun.jvm.hotspot.oops.Instance;
import sun.jvm.hotspot.oops.Array;
import sun.jvm.hotspot.runtime.BasicType;
import sun.jvm.hotspot.utilities.Assert;

public class ArrayReferenceImpl extends ObjectReferenceImpl
    implements ArrayReference
{
    private int length;
    ArrayReferenceImpl(VirtualMachine aVm, sun.jvm.hotspot.oops.Array aRef) {
        super(aVm, aRef);
        length = (int) aRef.getLength();
    }

    ArrayTypeImpl arrayType() {
        return (ArrayTypeImpl)type();
    }

    /**
     * Return array length.
     */
    public int length() {
        return length;
    }

    public Value getValue(int index) {
        List list = getValues(index, 1);
        return (Value)list.get(0);
    }

    public List getValues() {
        return getValues(0, -1);
    }

    /**
     * Validate that the range to set/get is valid.
     * length of -1 (meaning rest of array) has been converted
     * before entry.
     */
    private void validateArrayAccess(int index, int len) {
        // because length can be computed from index,
        // index must be tested first for correct error message
        if ((index < 0) || (index > length())) {
            throw new IndexOutOfBoundsException(
                        "Invalid array index: " + index);
        }
        if (len < 0) {
            throw new IndexOutOfBoundsException(
                        "Invalid array range length: " + len);
        }
        if (index + len > length()) {
            throw new IndexOutOfBoundsException(
                        "Invalid array range: " +
                        index + " to " + (index + len - 1));
        }
    }

    public List getValues(int index, int len) {
        if (len == -1) { // -1 means the rest of the array
           len = length() - index;
        }
        validateArrayAccess(index, len);
        List vals = new ArrayList();
        if (len == 0) {
            return vals;
        }

        sun.jvm.hotspot.oops.TypeArray typeArray = null;
        sun.jvm.hotspot.oops.ObjArray objArray = null;
        if (ref() instanceof sun.jvm.hotspot.oops.TypeArray) {
            typeArray = (sun.jvm.hotspot.oops.TypeArray)ref();
        } else if (ref() instanceof sun.jvm.hotspot.oops.ObjArray) {
            objArray = (sun.jvm.hotspot.oops.ObjArray)ref();
        } else {
            throw new RuntimeException("should not reach here");
        }

        char c = arrayType().componentSignature().charAt(0);
        BasicType variableType = BasicType.charToBasicType(c);

        final int limit = index + len;
        for (int ii = index; ii < limit; ii++) {
            ValueImpl valueImpl;
            if (variableType == BasicType.T_BOOLEAN) {
                valueImpl = (BooleanValueImpl) vm.mirrorOf(typeArray.getBooleanAt(ii));
            } else if (variableType == BasicType.T_CHAR) {
                valueImpl = (CharValueImpl) vm.mirrorOf(typeArray.getCharAt(ii));
            } else if (variableType == BasicType.T_FLOAT) {
                valueImpl = (FloatValueImpl) vm.mirrorOf(typeArray.getFloatAt(ii));
            } else if (variableType == BasicType.T_DOUBLE) {
                valueImpl =  (DoubleValueImpl) vm.mirrorOf(typeArray.getDoubleAt(ii));
            } else if (variableType == BasicType.T_BYTE) {
                valueImpl =  (ByteValueImpl) vm.mirrorOf(typeArray.getByteAt(ii));
            } else if (variableType == BasicType.T_SHORT) {
                valueImpl =  (ShortValueImpl) vm.mirrorOf(typeArray.getShortAt(ii));
            } else if (variableType == BasicType.T_INT) {
                valueImpl =  (IntegerValueImpl) vm.mirrorOf(typeArray.getIntAt(ii));
            } else if (variableType == BasicType.T_LONG) {
                valueImpl =  (LongValueImpl) vm.mirrorOf(typeArray.getLongAt(ii));
            } else if (variableType == BasicType.T_OBJECT) {
                // we may have an [Ljava/lang/Object; - i.e., Object[] with the
                // elements themselves may be arrays because every array is an Object.
                valueImpl = (ObjectReferenceImpl) vm.objectMirror(objArray.getObjAt(ii));
            } else if (variableType == BasicType.T_ARRAY) {
                valueImpl = (ArrayReferenceImpl) vm.arrayMirror((Array) objArray.getObjAt(ii));
            } else {
                throw new RuntimeException("should not reach here");
            }
            vals.add (valueImpl);
        }
        return vals;
    }

    public void setValue(int index, Value value)
            throws InvalidTypeException,
                   ClassNotLoadedException {
        vm.throwNotReadOnlyException("ArrayReference.setValue(...)");
    }

    public void setValues(List values)
            throws InvalidTypeException,
                   ClassNotLoadedException {
        setValues(0, values, 0, -1);
    }

    public void setValues(int index, List values,
                          int srcIndex, int length)
            throws InvalidTypeException,
                   ClassNotLoadedException {

        vm.throwNotReadOnlyException("ArrayReference.setValue(...)");

    }

    public String toString() {
        return "instance of " + arrayType().componentTypeName() +
               "[" + length() + "] (id=" + uniqueID() + ")";
    }
}
