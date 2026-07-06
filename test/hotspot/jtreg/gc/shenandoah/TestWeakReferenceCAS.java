/*
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

/**
 * @test
 * @summary Test that weak CAS attempt on Reference.referent is handled properly
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @modules java.base/jdk.internal.misc:+open
 *
 * @run driver TestWeakReferenceCAS
 */

import java.util.*;
import java.lang.ref.*;
import java.lang.reflect.*;
import jdk.internal.misc.Unsafe;

import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestWeakReferenceCAS {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            driver("CAS");
            driver("CAE");
            driver("GAS");
        } else {
            switch (args[0]) {
                case "CAS":
                    Test.testCAS();
                    break;
                case "CAE":
                    Test.testCAE();
                    break;
                case "GAS":
                    Test.testGAS();
                    break;
                default:
                    throw new IllegalArgumentException("Unknown test mode: " + args[0]);
            }
        }
    }

    private static void driver(String test) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
            "-Xmx128m",
            "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.ref=ALL-UNNAMED",
            "-XX:+UseShenandoahGC",
            TestWeakReferenceCAS.class.getName(),
            test);
        if (Platform.isDebugBuild()) {
            output.shouldNotHaveExitValue(0);
            output.shouldContain("Application error:");
        } else {
            output.shouldHaveExitValue(0);
        }
    }

    static class Test {
        static final Unsafe UNSAFE = Unsafe.getUnsafe();
        static final long OFFSET;

        static {
            try {
                Field f = Reference.class.getDeclaredField("referent");
                OFFSET = UNSAFE.objectFieldOffset(f);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static void testCAS() {
            Object obj = new Object();
            Object next = new Object();
            WeakReference<Object> ref = new WeakReference<>(obj);
            UNSAFE.compareAndSetReference(ref, OFFSET, obj, next);
        }

        static void testCAE() {
            Object obj = new Object();
            Object next = new Object();
            WeakReference<Object> ref = new WeakReference<>(obj);
            UNSAFE.compareAndExchangeReference(ref, OFFSET, obj, next);
        }

        static void testGAS() {
            Object obj = new Object();
            Object next = new Object();
            WeakReference<Object> ref = new WeakReference<>(obj);
            UNSAFE.getAndSetReference(ref, OFFSET, next);
        }
    }
}
