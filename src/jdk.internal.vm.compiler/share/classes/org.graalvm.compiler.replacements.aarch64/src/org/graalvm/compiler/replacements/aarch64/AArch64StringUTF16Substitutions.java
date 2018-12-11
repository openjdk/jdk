/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.aarch64;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.replacements.nodes.ArrayCompareToNode;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Substitutions for {@code java.lang.StringUTF16} methods.
 *
 * Since JDK 9.
 */
@ClassSubstitution(className = "java.lang.StringUTF16", optional = true)
public class AArch64StringUTF16Substitutions {

    /**
     * @param value is char[]
     * @param other is char[]
     */
    @MethodSubstitution
    public static int compareTo(byte[] value, byte[] other) {
        return ArrayCompareToNode.compareTo(value, other, value.length, other.length, JavaKind.Char, JavaKind.Char);
    }

    /**
     * @param value is char[]
     * @param other is byte[]
     */
    @MethodSubstitution
    public static int compareToLatin1(byte[] value, byte[] other) {
        /*
         * Swapping array arguments because intrinsic expects order to be byte[]/char[] but kind
         * arguments stay in original order.
         */
        return ArrayCompareToNode.compareTo(other, value, other.length, value.length, JavaKind.Char, JavaKind.Byte);
    }

}
