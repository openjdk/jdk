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
package jdk.vm.ci.hotspot.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.test.VirtualObjectTestBase;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaType;

public class VirtualObjectLayoutTest extends VirtualObjectTestBase {

    @Override
    protected void test(ResolvedJavaType klass, JavaValue[] values, JavaKind[] kinds, boolean error) {
        // Verify that the layout checking will correctly report errors
        VirtualObject virtual = VirtualObject.get(klass, 0);
        virtual.setValues(values, kinds);
        try {
            HotSpotCompiledCode.verifyVirtualObject(virtual);
        } catch (JVMCIError e) {
            Assert.assertTrue("Unexpected error verifying " + virtual, error);
            return;
        }
        Assert.assertFalse("Expected error but passed verifying " + virtual, error);
    }

    @Test
    public void testFormat() {
        testBase();
    }
}
