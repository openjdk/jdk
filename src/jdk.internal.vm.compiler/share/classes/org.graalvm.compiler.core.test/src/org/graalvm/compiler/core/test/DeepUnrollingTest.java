/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DeepUnrollingTest extends SubprocessTest {

    public static void loops() {
        for (int i = 0; i < 6; i++) {
            for (int n = 2; n < 30; n++) {
                for (int j = 1; j <= n; j++) {
                    for (int k = 1; k <= j; k++) {
                        // nop
                    }
                }
            }
        }
    }

    public static int reference(int a, int n) {
        int v = a;
        for (int i = 0; i < n; i++) {
            if (v % 2 == 0) {
                v = v / 2;
            } else {
                v = 3 * v + 1;
            }
        }
        return v;
    }

    private static final int ACCEPTABLE_FACTOR = 50;

    public void loopTest() {
        // warmup
        time("reference");
        time("loops");
        long reference = time("reference");
        long loops = time("loops");
        // observed ratio is ~20-30x. Pathological case before fix was ~300x
        if (loops > reference * ACCEPTABLE_FACTOR) {
            fail("Compilation of the loop nest is too slow. loops: %dms > %d * reference: %dms", loops, ACCEPTABLE_FACTOR, reference);
        }
    }

    public long time(String methodName) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.FullUnroll, true);
        long start = System.nanoTime();
        getCode(method, null, true, false, options);
        long end = System.nanoTime();
        return TimeUnit.NANOSECONDS.toMillis(end - start);
    }

    @Test
    public void test() throws IOException, InterruptedException {
        launchSubprocess(this::loopTest);
    }
}
