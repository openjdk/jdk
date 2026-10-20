/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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
package sun.jvm.hotspot.oops;

import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.types.WrongTypeException;


public final class LayoutKind {

    public static LayoutKind REFERENCE;
    public static LayoutKind BUFFERED;
    public static LayoutKind NULL_FREE_NON_ATOMIC_FLAT;
    public static LayoutKind NULL_FREE_ATOMIC_FLAT;
    public static LayoutKind NULLABLE_ATOMIC_FLAT;
    public static LayoutKind NULLABLE_NON_ATOMIC_FLAT;
    public static LayoutKind UNKNOWN;

    private final int value;

    static {
        VM.registerVMInitializedObserver((_, _) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
        Type type = db.lookupType("LayoutKind");

        REFERENCE = new LayoutKind(db.lookupIntConstant("LayoutKind::REFERENCE").intValue());
        BUFFERED = new LayoutKind(db.lookupIntConstant("LayoutKind::BUFFERED").intValue());
        NULL_FREE_NON_ATOMIC_FLAT = new LayoutKind(db.lookupIntConstant("LayoutKind::NULL_FREE_NON_ATOMIC_FLAT").intValue());
        NULL_FREE_ATOMIC_FLAT = new LayoutKind(db.lookupIntConstant("LayoutKind::NULL_FREE_ATOMIC_FLAT").intValue());
        NULLABLE_ATOMIC_FLAT = new LayoutKind(db.lookupIntConstant("LayoutKind::NULLABLE_ATOMIC_FLAT").intValue());
        NULLABLE_NON_ATOMIC_FLAT = new LayoutKind(db.lookupIntConstant("LayoutKind::NULLABLE_NON_ATOMIC_FLAT").intValue());
        UNKNOWN = new LayoutKind(db.lookupIntConstant("LayoutKind::UNKNOWN").intValue());
    }

    private LayoutKind(int value) {
        this.value = value;
    }

    public static LayoutKind valueOf(int rawValue) {
        if (rawValue == REFERENCE.value) {
            return REFERENCE;
        } else if (rawValue == BUFFERED.value) {
            return BUFFERED;
        } else if (rawValue == NULL_FREE_NON_ATOMIC_FLAT.value) {
            return NULL_FREE_NON_ATOMIC_FLAT;
        } else if (rawValue == NULL_FREE_ATOMIC_FLAT.value) {
            return NULL_FREE_ATOMIC_FLAT;
        } else if (rawValue == NULLABLE_ATOMIC_FLAT.value) {
            return NULLABLE_ATOMIC_FLAT;
        } else if (rawValue == NULLABLE_NON_ATOMIC_FLAT.value) {
            return NULLABLE_NON_ATOMIC_FLAT;
        } else if (rawValue == UNKNOWN.value) {
            return UNKNOWN;
        } else {
            throw new IllegalArgumentException("Out of range");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LayoutKind k) {
            return k.value == this.value;
        }
        return false;
    }
}
