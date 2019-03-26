/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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



package org.graalvm.compiler.hotspot.replacements;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.nodes.ComputeObjectAddressNode;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.WordFactory;

@ClassSubstitution(className = "jdk.internal.util.ArraysSupport", optional = true)
public class ArraysSupportSubstitutions {

    @SuppressWarnings("unused")
    @MethodSubstitution(isStatic = true)
    static int vectorizedMismatch(Object a, long aOffset, Object b, long bOffset, int length, int log2ArrayIndexScale) {
        Word aAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(a, aOffset));
        Word bAddr = WordFactory.unsigned(ComputeObjectAddressNode.get(b, bOffset));

        return HotSpotBackend.vectorizedMismatch(aAddr, bAddr, length, log2ArrayIndexScale);
    }
}
