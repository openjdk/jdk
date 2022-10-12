/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Some useful Java {@link ValueLayout} and associated {@link ValueLayout#arrayElementVarHandle(int...)} var handles.
 */
public class JavaLayouts {
    static final ValueLayout.OfInt JAVA_INT_UNALIGNED = JAVA_INT.withBitAlignment(8);

    static final ValueLayout.OfFloat JAVA_FLOAT_UNALIGNED = JAVA_FLOAT.withBitAlignment(8);

    static final ValueLayout.OfLong JAVA_LONG_UNALIGNED = JAVA_LONG.withBitAlignment(8);

    static final VarHandle VH_INT_UNALIGNED = JAVA_INT_UNALIGNED.arrayElementVarHandle();

    static final VarHandle VH_INT = JAVA_INT.arrayElementVarHandle();

    static final VarHandle VH_FLOAT_UNALIGNED = JAVA_FLOAT_UNALIGNED.arrayElementVarHandle();

    static final VarHandle VH_FLOAT = JAVA_FLOAT.arrayElementVarHandle();

    static final VarHandle VH_LONG_UNALIGNED = JAVA_LONG_UNALIGNED.arrayElementVarHandle();

    static final VarHandle VH_LONG = JAVA_LONG.arrayElementVarHandle();
}
