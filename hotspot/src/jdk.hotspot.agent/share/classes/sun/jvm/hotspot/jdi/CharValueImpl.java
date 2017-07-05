/*
 * Copyright (c) 2002, 2011, Oracle and/or its affiliates. All rights reserved.
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

public class CharValueImpl extends PrimitiveValueImpl
                           implements CharValue {
    private char value;

    CharValueImpl(VirtualMachine aVm,char aValue) {
        super(aVm);

        value = aValue;
    }

    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof CharValue)) {
            return (value == ((CharValue)obj).value()) &&
                   super.equals(obj);
        } else {
            return false;
        }
    }

    public int hashCode() {
        /*
         * TO DO: Better hash code
         */
        return intValue();
    }

    public int compareTo(CharValue charVal) {
        return value() - charVal.value();
    }

    public Type type() {
        return vm.theCharType();
    }

    public char value() {
        return value;
    }

    public boolean booleanValue() {
        return(value == 0)?false:true;
    }

    public byte byteValue() {
        return(byte)value;
    }

    public char charValue() {
        return(char)value;
    }

    public short shortValue() {
        return(short)value;
    }

    public int intValue() {
        return(int)value;
    }

    public long longValue() {
        return(long)value;
    }

    public float floatValue() {
        return(float)value;
    }

    public double doubleValue() {
        return(double)value;
    }

    public String toString() {
        return "" + value;
    }

    byte checkedByteValue() throws InvalidTypeException {
        // Note: since char is unsigned, don't check against MIN_VALUE
        if (value > Byte.MAX_VALUE) {
            throw new InvalidTypeException("Can't convert " + value + " to byte");
        } else {
            return super.checkedByteValue();
        }
    }

    short checkedShortValue() throws InvalidTypeException {
        // Note: since char is unsigned, don't check against MIN_VALUE
        if (value > Short.MAX_VALUE) {
            throw new InvalidTypeException("Can't convert " + value + " to short");
        } else {
            return super.checkedShortValue();
        }
    }
}
