/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

/**
 * Some useful Java {@link ValueLayout} and associated array var handles.
 */
public class JavaLayouts {

    static final VarHandle VH_INT_UNALIGNED = arrayVarHandle(JAVA_INT_UNALIGNED);
    static final VarHandle VH_INT = arrayVarHandle(JAVA_INT);

    static final VarHandle VH_LONG_UNALIGNED = arrayVarHandle(JAVA_LONG_UNALIGNED);
    static final VarHandle VH_LONG = arrayVarHandle(JAVA_LONG);

    private static VarHandle arrayVarHandle(ValueLayout layout) {
        return MethodHandles.insertCoordinates(layout.arrayElementVarHandle(), 1, 0L);
    }
}
