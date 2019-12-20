/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.sparc.test;

import static org.graalvm.compiler.core.common.GraalOptions.RegisterPressure;
import static org.junit.Assume.assumeTrue;

import org.graalvm.compiler.core.test.backend.AllocatorTest;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.sparc.SPARC;

public class SPARCAllocatorTest extends AllocatorTest {

    private final GraalHotSpotVMConfig config = ((HotSpotBackend) getBackend()).getRuntime().getVMConfig();

    @Before
    public void checkSPARC() {
        assumeTrue("skipping SPARC specific test", getTarget().arch instanceof SPARC);
        assumeTrue("RegisterPressure is set -> skip", RegisterPressure.getValue(getInitialOptions()) == null);
    }

    @Test
    public void test1() {
        testAllocation("test1snippet", config.threadLocalHandshakes ? 1 : 2, 0, 0);
    }

    public static long test1snippet(long x) {
        return x + 41;
    }

    @Test
    public void test2() {
        testAllocation("test2snippet", config.threadLocalHandshakes ? 1 : 2, 0, 0);
    }

    public static long test2snippet(long x) {
        return x * 41;
    }

    @Test
    public void test3() {
        testAllocation("test3snippet", config.threadLocalHandshakes ? 3 : 4, 0, 0);
    }

    public static long test3snippet(long x) {
        return x / 41 + x % 41;
    }

}
