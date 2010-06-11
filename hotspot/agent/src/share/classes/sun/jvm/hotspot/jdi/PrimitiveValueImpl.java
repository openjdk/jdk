/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

public abstract class PrimitiveValueImpl extends ValueImpl
                                         implements PrimitiveValue {

    PrimitiveValueImpl(VirtualMachine aVm) {
        super(aVm);
    }

    abstract public boolean booleanValue();
    abstract public byte byteValue();
    abstract public char charValue();
    abstract public short shortValue();
    abstract public int intValue();
    abstract public long longValue();
    abstract public float floatValue();
    abstract public double doubleValue();

    /*
     * The checked versions of the value accessors throw
     * InvalidTypeException if the required conversion is
     * narrowing and would result in the loss of information
     * (either magnitude or precision).
     *
     * Default implementations here do no checking; subclasses
     * override as necessary to do the proper checking.
     */
    byte checkedByteValue() throws InvalidTypeException {
        return byteValue();
    }
    char checkedCharValue() throws InvalidTypeException {
        return charValue();
    }
    short checkedShortValue() throws InvalidTypeException {
        return shortValue();
    }
    int checkedIntValue() throws InvalidTypeException {
        return intValue();
    }
    long checkedLongValue() throws InvalidTypeException {
        return longValue();
    }
    float checkedFloatValue() throws InvalidTypeException {
        return floatValue();
    }

    final boolean checkedBooleanValue() throws InvalidTypeException {
        /*
         * Always disallow a conversion to boolean from any other
         * primitive
         */
        if (this instanceof BooleanValue) {
            return booleanValue();
        } else {
            throw new InvalidTypeException("Can't convert non-boolean value to boolean");
        }
    }

    final double checkedDoubleValue() throws InvalidTypeException {
        /*
         * Can't overflow by converting to double, so this method
         * is never overridden
         */
        return doubleValue();
    }

}
