/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.jvmci
 * @modules jdk.internal.vm.ci/jdk.vm.ci.hotspot
 *          jdk.internal.vm.ci/jdk.vm.ci.meta
 * @library /compiler/jvmci/common/patches
 * @build jdk.internal.vm.ci/jdk.vm.ci.hotspot.HotSpotResolvedJavaFieldHelper
 * @run testng/othervm
 *      -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI -XX:-UseJVMCICompiler
 *      jdk.vm.ci.hotspot.test.TestHotSpotResolvedJavaField
 */

package jdk.vm.ci.hotspot.test;

import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaFieldHelper;
import jdk.vm.ci.meta.ResolvedJavaField;

public class TestHotSpotResolvedJavaField {

    @Test
    public void testIndex() {
        int max = Character.MAX_VALUE;
        int[] valid = {0, 1, max - 1, max};
        for (int index : valid) {
            ResolvedJavaField field = HotSpotResolvedJavaFieldHelper.createField(null, null, 0, 0, index);
            Assert.assertEquals(HotSpotResolvedJavaFieldHelper.getIndex(field), index);
        }
    }

    @Test
    public void testOffset() {
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        int[] valid = {min, min + 1, -2, 0, 1, max - 1, max};
        for (int offset : valid) {
            ResolvedJavaField field = HotSpotResolvedJavaFieldHelper.createField(null, null, offset, 0, 0);
            Assert.assertEquals(field.getOffset(), offset);
        }
    }
}
