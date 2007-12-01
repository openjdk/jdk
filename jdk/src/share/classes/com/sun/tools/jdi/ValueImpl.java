/*
 * Copyright 1998-1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.jdi;

import com.sun.jdi.*;

abstract class ValueImpl extends MirrorImpl implements Value {

    ValueImpl(VirtualMachine aVm) {
        super(aVm);
    }

    static ValueImpl prepareForAssignment(Value value,
                                          ValueContainer destination)
                  throws InvalidTypeException, ClassNotLoadedException {
        if (value == null) {
            /*
             * TO DO: Centralize JNI signature knowledge
             */
            if (destination.signature().length() == 1) {
                throw new InvalidTypeException("Can't set a primitive type to null");
            }
            return null;    // no further checking or conversion necessary
        } else {
            return ((ValueImpl)value).prepareForAssignmentTo(destination);
        }
    }

    static byte typeValueKey(Value val) {
        if (val == null) {
            return JDWP.Tag.OBJECT;
        } else {
            return ((ValueImpl)val).typeValueKey();
        }
    }

    abstract ValueImpl prepareForAssignmentTo(ValueContainer destination)
                 throws InvalidTypeException, ClassNotLoadedException;

    abstract byte typeValueKey();
}
