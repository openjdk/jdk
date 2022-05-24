/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import static jdk.test.lib.Asserts.*;

/*
 * @test
 * @library /test/lib
 * @build  jdk.test.lib.Asserts
 * @bug     8042901 8283092
 * @summary Check encapsulation of PlatformMBeanProvider Constructor
 * @modules java.management/sun.management.spi
 * @run main PlatformMBeanProviderConstructorCheck
 */
public class PlatformMBeanProviderConstructorCheck {

    /**
     * jtreg invokes this test with module arguments that permit compilation
     * of the MyProvider class, which extends PlatformMBeanProvider.
     * First check we can invoke that class, then re-invoke ourself without
     * those module arguments, and expect a failure calling MyProvider.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("---PlatformMBeanProviderConstructorCheck:");
        boolean expectedFail = false;

        // Recognise argument to signify we were re-invoked, and MyProvider should fail:
        if (args.length == 1) {
            if (args[0].equals("--nomoduleargs")) {
                expectedFail = true;
                verifyNoModuleArguments();
            } else {
                throw new RuntimeException("unknown argument: '" + args[0] + "'");
            }
        }
        System.out.println("---PlatformMBeanProviderConstructorCheck: invoke MyProvider with expectedFail = " + expectedFail);
        Throwable e = null;
        try {
            new MyProvider();
        } catch (IllegalAccessError iae) {
            System.out.println("---PlatformMBeanProviderConstructorCheck got exception: " + iae);
            e = iae;
        }

        if (!expectedFail) {
            // This was the first invocation, should have worked OK:
            assertNull(e);
            System.out.println("---PlatformMBeanProviderConstructorCheck PASSED (1) (expectedFail = " + expectedFail + ")");

            // Re-invoke this test to check failure:
            System.out.println("---PlatformMBeanProviderConstructorCheck: re-invoke without --add-modules or --add-exports");
            OutputAnalyzer output =  ProcessTools.executeTestJava("PlatformMBeanProviderConstructorCheck", "--nomoduleargs");
            output.reportDiagnosticSummary();
            output.shouldContain("java.lang.IllegalAccessError: superclass access check failed:");
            output.shouldContain(" module java.management does not export sun.management.spi to ");
            output.shouldNotContain("MyProvider constructor.");
        } else {
            // This was the re-invocation without module access, should fail:
            assertNotNull(e);
            System.out.println("---PlatformMBeanProviderConstructorCheck PASSED (2) (expectedFail = " + expectedFail + ")");
        }
    }

    private static void verifyNoModuleArguments() {
        RuntimeMXBean mxbean = ManagementFactory.getRuntimeMXBean();
        for (String s : mxbean.getInputArguments()) {
            if (s.startsWith("--add-modules") || s.startsWith("--add-exports")) {
                System.out.println("arg: " + s);
                throw new RuntimeException("argument list contains: " + s);
            }
        }
    }

    private static class MyProvider extends sun.management.spi.PlatformMBeanProvider {
        @Override
        public List<PlatformComponent<?>> getPlatformComponentList() {
            System.out.println("MyProvider constructor.");
            return null;
        }
    }
}
