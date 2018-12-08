/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.lir.util;

import static org.graalvm.compiler.lir.LIRValueUtil.asVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.asVirtualStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.isVariable;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import org.graalvm.compiler.debug.GraalError;

import jdk.vm.ci.meta.Value;

public class VariableVirtualStackValueMap<K extends Value, T> extends ValueMap<K, T> {

    private final Object[] variables;
    private final Object[] slots;

    public VariableVirtualStackValueMap(int initialVariableCapacity, int initialStackSlotCapacity) {
        variables = new Object[initialVariableCapacity];
        slots = new Object[initialStackSlotCapacity];
    }

    @Override
    public T get(K value) {
        if (isVariable(value)) {
            return get(variables, asVariable(value).index);
        }
        if (isVirtualStackSlot(value)) {
            return get(slots, asVirtualStackSlot(value).getId());
        }
        throw GraalError.shouldNotReachHere("Unsupported Value: " + value);
    }

    @Override
    public void remove(K value) {
        if (isVariable(value)) {
            remove(variables, asVariable(value).index);
        } else if (isVirtualStackSlot(value)) {
            remove(slots, asVirtualStackSlot(value).getId());
        } else {
            throw GraalError.shouldNotReachHere("Unsupported Value: " + value);
        }
    }

    @Override
    public void put(K value, T object) {
        if (isVariable(value)) {
            put(variables, asVariable(value).index, object);
        } else if (isVirtualStackSlot(value)) {
            put(slots, asVirtualStackSlot(value).getId(), object);
        } else {
            throw GraalError.shouldNotReachHere("Unsupported Value: " + value);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object[] array, int index) {
        if (index >= array.length) {
            return null;
        }
        return (T) array[index];
    }

    private static void remove(Object[] array, int index) {
        if (index >= array.length) {
            return;
        }
        array[index] = null;
    }

    private static <T> Object[] put(Object[] array, int index, T object) {
        if (index >= array.length) {
            Object[] newArray = new Object[index + 1];
            System.arraycopy(array, 0, newArray, 0, array.length);
            newArray[index] = object;
            return newArray;
        }
        array[index] = object;
        return null;
    }
}
