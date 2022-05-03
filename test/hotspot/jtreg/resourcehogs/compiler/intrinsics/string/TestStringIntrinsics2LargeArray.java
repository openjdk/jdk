/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016 SAP SE. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test
 * @bug 8254790
 * @requires vm.bits == "64" & os.maxMemory > 8G
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /test/hotspot/jtreg
 *
 * @build compiler.intrinsics.string.TestStringIntrinsics2
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 *
 * @run main/othervm
 *        -mx8G
 *        -Xbootclasspath/a:.
 *        -Xmixed
 *        -XX:+UnlockDiagnosticVMOptions
 *        -XX:+WhiteBoxAPI
 *        -XX:+IgnoreUnrecognizedVMOptions
 *        -XX:MaxInlineSize=70
 *        -XX:MinInlineFrequencyRatio=0
 *        resourcehogs.compiler.intrinsics.string.TestStringIntrinsics2LargeArray
 */

package resourcehogs.compiler.intrinsics.string;

import java.lang.ref.Reference;

import compiler.intrinsics.string.TestStringIntrinsics2;

public final class TestStringIntrinsics2LargeArray {
    public static void main(String[] args) throws Exception {
        int[] hugeArray = new int[Integer.MAX_VALUE / 2];
        TestStringIntrinsics2.main(args);
        Reference.reachabilityFence(hugeArray);
    }
}
