/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.event;

import java.lang.reflect.Field;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * The update method serves as an intrinsic for the exact argument signature.
 * When extending functionality to other types, first ensure that the VM is
 * configured to accept and handle that specific type. Once VM support for the new type is confirmed,
 * implement an additional update method overload and decorate it with @IntrinsicCandidate.
 *
 * JfrEpoch is itself a supported type already configured by the VM,
 * so instances can be used directly when full abstraction isn’t needed.
 */
public final class JfrEpoch {

    /**
     * The update methods takes a state object as an argument and returns true
     * if the epoch was exclusively updated by the current thread.
     */

    @IntrinsicCandidate
    static native boolean update(JfrEpoch epoch);

    @IntrinsicCandidate
    static native boolean update(Field root);
}
