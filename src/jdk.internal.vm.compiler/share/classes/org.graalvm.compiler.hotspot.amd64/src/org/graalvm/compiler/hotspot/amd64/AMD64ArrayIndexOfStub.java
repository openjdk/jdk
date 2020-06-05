/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOfNode;

public class AMD64ArrayIndexOfStub extends SnippetStub {

    public AMD64ArrayIndexOfStub(OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(linkage.getDescriptor().getName(), options, providers, linkage);
    }

    @Snippet
    private static int indexOfTwoConsecutiveBytes(byte[] array, int arrayLength, int fromIndex, int searchValue) {
        return AMD64ArrayIndexOfNode.indexOf2ConsecutiveBytes(array, arrayLength, fromIndex, searchValue);
    }

    @Snippet
    private static int indexOfTwoConsecutiveChars(char[] array, int arrayLength, int fromIndex, int searchValue) {
        return AMD64ArrayIndexOfNode.indexOf2ConsecutiveChars(array, arrayLength, fromIndex, searchValue);
    }

    @Snippet
    private static int indexOfTwoConsecutiveCharsCompact(byte[] array, int arrayLength, int fromIndex, int searchValue) {
        return AMD64ArrayIndexOfNode.indexOf2ConsecutiveChars(array, arrayLength, fromIndex, searchValue);
    }

    @Snippet
    private static int indexOf1Byte(byte[] array, int arrayLength, int fromIndex, byte b) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, b);
    }

    @Snippet
    private static int indexOf2Bytes(byte[] array, int arrayLength, int fromIndex, byte b1, byte b2) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, b1, b2);
    }

    @Snippet
    private static int indexOf3Bytes(byte[] array, int arrayLength, int fromIndex, byte b1, byte b2, byte b3) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, b1, b2, b3);
    }

    @Snippet
    private static int indexOf4Bytes(byte[] array, int arrayLength, int fromIndex, byte b1, byte b2, byte b3, byte b4) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, b1, b2, b3, b4);
    }

    @Snippet
    private static int indexOf1Char(char[] array, int arrayLength, int fromIndex, char c) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c);
    }

    @Snippet
    private static int indexOf2Chars(char[] array, int arrayLength, int fromIndex, char c1, char c2) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c1, c2);
    }

    @Snippet
    private static int indexOf3Chars(char[] array, int arrayLength, int fromIndex, char c1, char c2, char c3) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c1, c2, c3);
    }

    @Snippet
    private static int indexOf4Chars(char[] array, int arrayLength, int fromIndex, char c1, char c2, char c3, char c4) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c1, c2, c3, c4);
    }

    @Snippet
    private static int indexOf1CharCompact(byte[] array, int arrayLength, int fromIndex, char c) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c);
    }

    @Snippet
    private static int indexOf2CharsCompact(byte[] array, int arrayLength, int fromIndex, char c1, char c2) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c1, c2);
    }

    @Snippet
    private static int indexOf3CharsCompact(byte[] array, int arrayLength, int fromIndex, char c1, char c2, char c3) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c1, c2, c3);
    }

    @Snippet
    private static int indexOf4CharsCompact(byte[] array, int arrayLength, int fromIndex, char c1, char c2, char c3, char c4) {
        return AMD64ArrayIndexOfNode.indexOf(array, arrayLength, fromIndex, c1, c2, c3, c4);
    }
}
