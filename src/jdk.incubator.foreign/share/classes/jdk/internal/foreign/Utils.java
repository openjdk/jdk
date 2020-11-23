/*
 *  Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign;

import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemorySegment;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.VM;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Optional;
import java.util.function.Supplier;

import static sun.security.action.GetPropertyAction.*;

/**
 * This class contains misc helper functions to support creation of memory segments.
 */
public final class Utils {

    // used when testing invoke exact behavior of memory access handles
    private static final boolean SHOULD_ADAPT_HANDLES
        = Boolean.parseBoolean(privilegedGetProperty("jdk.internal.foreign.SHOULD_ADAPT_HANDLES", "true"));

    private static final String foreignRestrictedAccess = Optional.ofNullable(VM.getSavedProperty("foreign.restricted"))
            .orElse("deny");

    private static final MethodHandle SEGMENT_FILTER;

    static {
        try {
            SEGMENT_FILTER = MethodHandles.lookup().findStatic(Utils.class, "filterSegment",
                    MethodType.methodType(MemorySegmentProxy.class, MemorySegment.class));
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & -alignment;
    }

    public static long bitsToBytesOrThrow(long bits, Supplier<RuntimeException> exFactory) {
        if (bits % 8 == 0) {
            return bits / 8;
        } else {
            throw exFactory.get();
        }
    }

    public static VarHandle fixUpVarHandle(VarHandle handle) {
        // This adaptation is required, otherwise the memory access var handle will have type MemorySegmentProxy,
        // and not MemorySegment (which the user expects), which causes performance issues with asType() adaptations.
        return SHOULD_ADAPT_HANDLES
            ? MemoryHandles.filterCoordinates(handle, 0, SEGMENT_FILTER)
            : handle;
    }

    private static MemorySegmentProxy filterSegment(MemorySegment segment) {
        return (AbstractMemorySegmentImpl)segment;
    }

    public static void checkRestrictedAccess(String method) {
        switch (foreignRestrictedAccess) {
            case "deny" -> throwIllegalAccessError(foreignRestrictedAccess, method);
            case "warn" -> System.err.println("WARNING: Accessing restricted foreign method: " + method);
            case "debug" -> {
                StringBuilder sb = new StringBuilder("DEBUG: restricted foreign method: \" + method");
                StackWalker.getInstance().forEach(f -> sb.append(System.lineSeparator())
                        .append("\tat ")
                        .append(f));
                System.err.println(sb.toString());
            }
            case "permit" -> {}
            default -> throwIllegalAccessError(foreignRestrictedAccess, method);
        }
    }

    private static void throwIllegalAccessError(String value, String method) {
        throw new IllegalAccessError("Illegal access to restricted foreign method: " + method +
                " ; system property 'foreign.restricted' is set to '" + value + "'");
    }
}
