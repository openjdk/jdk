/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tracing;

import java.util.Set;

// // The JVM will skip all classes in the jdk.jfr module, so it's not added here.
public final class ExcludeList {
    private static final String[] EXCLUDED_CLASSES = {
        // Used by MethodTiming event to accumulate invocations.
        "java/util/concurrent/atomic/AtomicLong",
        // Used by EventWriter, directly or indirectly.
        "sun/misc/Unsafe",
        "jdk/internal/misc/Unsafe",
        "java/lang/StringLatin1",
        "java/lang/StringUTF16",
    };

    private static final String[] EXCLUDED_PREFIX = {
        // Used by MethodTiming event to store invocations, including inner classes.
        "java/util/concurrent/ConcurrentHashMap",
        // Can't trigger <clinit> of these classes during PlatformTracer::onMethodTrace(...)
        // Also to avoid recursion with EventWriter::putString
        "jdk/internal/", // jdk/internal/classfile, // jdk/internal/vm, jdk/internal/util, jdk/internal/loader and jdk/internal/foreign
        "java/lang/classfile/"
    };

    private static final Set<String> EXCLUDED_METHODS = Set.of(
        // Long used by MethodTiming event when looking up entry for timing entry
        "java.lang.Long::<init>",
        "java.lang.Long::valueOf",
        "java.lang.Number::<init>",
        // Used by EventWriter::putString, directly or indirectly.
        "java.lang.String::charAt",
        "java.lang.String::length",
        "java.lang.String::coder", // Used by charAt(int)
        "java.lang.String::checkIndex", // Used by charAt(int)
        "java.lang.String::isLatin1", // Used by charAt()
        "java.lang.String::equals", // Used by StringPool
        "java.lang.String::hashCode" // Used by StringPool
    );

    public static boolean containsMethod(String methodName) {
        return EXCLUDED_METHODS.contains(methodName);
    }

    public static boolean containsClass(String className) {
        for (String clazz: EXCLUDED_CLASSES) {
            if (clazz.equals(className)) {
                return true;
            }
        }
        for (String prefix : EXCLUDED_PREFIX) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
