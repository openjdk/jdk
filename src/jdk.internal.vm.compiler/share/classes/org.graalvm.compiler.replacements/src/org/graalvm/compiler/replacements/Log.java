/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.replacements.nodes.CStringConstant.cstring;

import java.io.PrintStream;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.meta.JavaKind;

//JaCoCo Exclude

/**
 * Provides {@link PrintStream}-like logging facility.
 */
public final class Log {

    public static final ForeignCallDescriptor LOG_PRIMITIVE = new ForeignCallDescriptor("logPrimitive", void.class, int.class, long.class, boolean.class);
    public static final ForeignCallDescriptor LOG_OBJECT = new ForeignCallDescriptor("logObject", void.class, Object.class, boolean.class, boolean.class);
    public static final ForeignCallDescriptor LOG_PRINTF = new ForeignCallDescriptor("logPrintf", void.class, Word.class, long.class, long.class, long.class);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void log(@ConstantNodeParameter ForeignCallDescriptor logObject, Object object, boolean asString, boolean newline);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void log(@ConstantNodeParameter ForeignCallDescriptor logPrimitive, int typeChar, long value, boolean newline);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void printf(@ConstantNodeParameter ForeignCallDescriptor logPrintf, Word format, long v1, long v2, long v3);

    public static void print(boolean value) {
        log(LOG_PRIMITIVE, JavaKind.Boolean.getTypeChar(), value ? 1L : 0L, false);
    }

    public static void print(byte value) {
        log(LOG_PRIMITIVE, JavaKind.Byte.getTypeChar(), value, false);
    }

    public static void print(char value) {
        log(LOG_PRIMITIVE, JavaKind.Char.getTypeChar(), value, false);
    }

    public static void print(short value) {
        log(LOG_PRIMITIVE, JavaKind.Short.getTypeChar(), value, false);
    }

    public static void print(int value) {
        log(LOG_PRIMITIVE, JavaKind.Int.getTypeChar(), value, false);
    }

    public static void print(long value) {
        log(LOG_PRIMITIVE, JavaKind.Long.getTypeChar(), value, false);
    }

    /**
     * Prints a formatted string to the log stream.
     *
     * @param format a C style printf format value that can contain at most one conversion specifier
     *            (i.e., a sequence of characters starting with '%').
     * @param value the value associated with the conversion specifier
     */
    public static void printf(String format, long value) {
        printf(LOG_PRINTF, cstring(format), value, 0L, 0L);
    }

    public static void printf(String format, long v1, long v2) {
        printf(LOG_PRINTF, cstring(format), v1, v2, 0L);
    }

    public static void printf(String format, long v1, long v2, long v3) {
        printf(LOG_PRINTF, cstring(format), v1, v2, v3);
    }

    public static void print(float value) {
        if (Float.isNaN(value)) {
            print("NaN");
        } else if (value == Float.POSITIVE_INFINITY) {
            print("Infinity");
        } else if (value == Float.NEGATIVE_INFINITY) {
            print("-Infinity");
        } else {
            log(LOG_PRIMITIVE, JavaKind.Float.getTypeChar(), Float.floatToRawIntBits(value), false);
        }
    }

    public static void print(double value) {
        if (Double.isNaN(value)) {
            print("NaN");
        } else if (value == Double.POSITIVE_INFINITY) {
            print("Infinity");
        } else if (value == Double.NEGATIVE_INFINITY) {
            print("-Infinity");
        } else {
            log(LOG_PRIMITIVE, JavaKind.Double.getTypeChar(), Double.doubleToRawLongBits(value), false);
        }
    }

    public static void print(String value) {
        log(LOG_OBJECT, value, true, false);
    }

    public static void printObject(Object o) {
        log(LOG_OBJECT, o, false, false);
    }

    public static void println(boolean value) {
        log(LOG_PRIMITIVE, JavaKind.Boolean.getTypeChar(), value ? 1L : 0L, true);
    }

    public static void println(byte value) {
        log(LOG_PRIMITIVE, JavaKind.Byte.getTypeChar(), value, true);
    }

    public static void println(char value) {
        log(LOG_PRIMITIVE, JavaKind.Char.getTypeChar(), value, true);
    }

    public static void println(short value) {
        log(LOG_PRIMITIVE, JavaKind.Short.getTypeChar(), value, true);
    }

    public static void println(int value) {
        log(LOG_PRIMITIVE, JavaKind.Int.getTypeChar(), value, true);
    }

    public static void println(long value) {
        log(LOG_PRIMITIVE, JavaKind.Long.getTypeChar(), value, true);
    }

    public static void println(float value) {
        if (Float.isNaN(value)) {
            println("NaN");
        } else if (value == Float.POSITIVE_INFINITY) {
            println("Infinity");
        } else if (value == Float.NEGATIVE_INFINITY) {
            println("-Infinity");
        } else {
            log(LOG_PRIMITIVE, JavaKind.Float.getTypeChar(), Float.floatToRawIntBits(value), true);
        }
    }

    public static void println(double value) {
        if (Double.isNaN(value)) {
            println("NaN");
        } else if (value == Double.POSITIVE_INFINITY) {
            println("Infinity");
        } else if (value == Double.NEGATIVE_INFINITY) {
            println("-Infinity");
        } else {
            log(LOG_PRIMITIVE, JavaKind.Double.getTypeChar(), Double.doubleToRawLongBits(value), true);
        }
    }

    public static void println(String value) {
        log(LOG_OBJECT, value, true, true);
    }

    public static void printlnObject(Object o) {
        log(LOG_OBJECT, o, false, true);
    }

    public static void println() {
        println("");
    }
}
