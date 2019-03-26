/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;

// JaCoCo Exclude

/**
 * Substitutions for {@code StringUTF16} methods for JDK9 and later.
 */
@ClassSubstitution(className = "java.lang.StringUTF16", optional = true)
public class StringUTF16Substitutions {

    @MethodSubstitution
    public static char getChar(byte[] value, int i) {
        ReplacementsUtil.runtimeAssert((i << 1) + 1 < value.length, "Trusted caller missed bounds check");
        return getCharDirect(value, i << 1);
    }

    /**
     * Will be intrinsified with an {@link InvocationPlugin} to a {@link JavaReadNode}.
     */
    public static native char getCharDirect(byte[] value, int i);

    @MethodSubstitution
    public static void putChar(byte[] value, int i, int c) {
        ReplacementsUtil.runtimeAssert((i << 1) + 1 < value.length, "Trusted caller missed bounds check");
        putCharDirect(value, i << 1, c);
    }

    /**
     * Will be intrinsified with an {@link InvocationPlugin} to a {@link JavaWriteNode}.
     */
    public static native void putCharDirect(byte[] value, int i, int c);
}
