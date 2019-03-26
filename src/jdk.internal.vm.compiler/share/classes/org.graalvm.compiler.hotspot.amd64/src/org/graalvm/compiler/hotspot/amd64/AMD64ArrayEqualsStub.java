/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import jdk.internal.vm.compiler.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

public final class AMD64ArrayEqualsStub extends SnippetStub {

    public static final ForeignCallDescriptor STUB_BOOLEAN_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "booleanArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_BYTE_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "byteArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_CHAR_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "charArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_SHORT_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "shortArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_INT_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "intArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_LONG_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "longArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_FLOAT_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "floatArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);
    public static final ForeignCallDescriptor STUB_DOUBLE_ARRAY_EQUALS = new ForeignCallDescriptor(
                    "doubleArraysEquals", boolean.class, Pointer.class, Pointer.class, int.class);

    public AMD64ArrayEqualsStub(ForeignCallDescriptor foreignCallDescriptor, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(foreignCallDescriptor.getName(), options, providers, linkage);
    }

    @Snippet
    private static boolean booleanArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Boolean);
    }

    @Snippet
    private static boolean byteArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Byte);
    }

    @Snippet
    private static boolean charArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Char);
    }

    @Snippet
    private static boolean shortArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Short);
    }

    @Snippet
    private static boolean intArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Int);
    }

    @Snippet
    private static boolean longArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Long);
    }

    @Snippet
    private static boolean floatArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Float);
    }

    @Snippet
    private static boolean doubleArraysEquals(Pointer array1, Pointer array2, int length) {
        return ArrayEqualsNode.equals(array1, array2, length, JavaKind.Double);
    }
}
