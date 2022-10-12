/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

// key: compiler.warn.serializable.missing.access.no.arg.ctor
// key: compiler.warn.non.serializable.instance.field
// key: compiler.warn.non.serializable.instance.field.array

// options: -Xlint:serial

import java.io.*;

class SerialMissingNoArgCtor {
    public SerialMissingNoArgCtor(int foo) {
    }

    // Not accessible to SerialSubclass
    private SerialMissingNoArgCtor() {}

    // SerialSubclass does not have access to a non-arg ctor in the
    // first non-serializable superclass in its superclass chain.
    static class SerialSubclass extends SerialMissingNoArgCtor
        implements Serializable {

        private static final long serialVersionUID = 42;

        // non-serializable non-transient instance field
        private Object datum = null;

        // base component type of array is non-serializable
        private Object[] data = null;

        public SerialSubclass() {
            super(1);
        }
    }
}
