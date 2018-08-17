/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.amd64;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.spi.ArrayOffsetProvider;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;

// JaCoCo Exclude

/**
 * Substitutions for {@code java.lang.StringLatin1} methods.
 *
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringLatin1", optional = true)
public class AMD64StringLatin1Substitutions {

    @Fold
    static int byteArrayBaseOffset(@InjectedParameter ArrayOffsetProvider arrayOffsetProvider) {
        return arrayOffsetProvider.arrayBaseOffset(JavaKind.Byte);
    }

    /** Marker value for the {@link InjectedParameter} injected parameter. */
    static final ArrayOffsetProvider INJECTED = null;

    /**
     * @param value is byte[]
     * @param other is byte[]
     */
    @MethodSubstitution
    public static int compareTo(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Byte, JavaKind.Byte);
    }

    /**
     * @param value is byte[]
     * @param other is char[]
     */
    @MethodSubstitution
    public static int compareToUTF16(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Byte, JavaKind.Char);
    }

    @MethodSubstitution(optional = true)
    public static int indexOf(byte[] value, int ch, int origFromIndex) {
        int fromIndex = origFromIndex;
        if (ch >>> 8 != 0) {
            // search value must be a byte value
            return -1;
        }
        int length = value.length;
        if (fromIndex < 0) {
            fromIndex = 0;
        } else if (fromIndex >= length) {
            // Note: fromIndex might be near -1>>>1.
            return -1;
        }
        Pointer sourcePointer = Word.objectToTrackedPointer(value).add(byteArrayBaseOffset(INJECTED)).add(fromIndex);
        int result = AMD64ArrayIndexOfNode.optimizedArrayIndexOf(sourcePointer, length - fromIndex, (char) ch, JavaKind.Byte);
        if (result != -1) {
            return result + fromIndex;
        }
        return result;
    }
}
