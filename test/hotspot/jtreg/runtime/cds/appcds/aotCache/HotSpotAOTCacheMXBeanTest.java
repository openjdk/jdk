/*
 * Copyright (c) 2025, Microsoft, Inc. All rights reserved.
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
 *
 */


/*
 * @test
 * @summary Sanity test for HotSpotAOTCache MXBean
 * @requires vm.cds.write.archived.java.heap
 * @library /test/jdk/lib/testlibrary /test/lib
 * @build HotSpotAOTCacheMXBeanTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar HotSpotAOTCacheMXBeanApp
 * @run driver HotSpotAOTCacheMXBeanTest
 */

import java.io.IOException;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import jdk.management.HotSpotAOTCacheMXBean;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class HotSpotAOTCacheMXBeanTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "HotSpotAOTCacheMXBeanApp";
    public static void main(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.runAOTWorkflow();
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
               "-Xlog:cds+class=trace",
                "--add-modules=jdk.management"
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass, runMode.name()
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            var name = runMode.name();
            if (runMode.isApplicationExecuted()) {
                if(runMode == RunMode.TRAINING) {
                    out.shouldContain("Hello Leyden " + name);
                    out.shouldContain("Successfully stopped recording");
                } else if (runMode == RunMode.ASSEMBLY) {
                    out.shouldNotContain("Hello Leyden ");
                } else if (runMode == RunMode.PRODUCTION) {
                    out.shouldContain("Hello Leyden " + name);
                    out.shouldContain("Failed to stop recording");
                }
                out.shouldNotContain("HotSpotAOTCacheMXBean is not available");
                out.shouldNotContain("IOException occurred!");
            }
        }
    }
}

class HotSpotAOTCacheMXBeanApp {
    public static void main(String[] args) {
        System.out.println("Hello Leyden " + args[0]);
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            HotSpotAOTCacheMXBean aotBean = ManagementFactory.newPlatformMXBeanProxy(server,
                    "jdk.management:type=HotSpotAOTCache",
                    HotSpotAOTCacheMXBean.class);
            if (aotBean == null) {
                System.out.println("HotSpotAOTCacheMXBean is not available");
                return;
            }
            if (aotBean.endRecording()) {
                System.out.println("Successfully stopped recording");
            } else {
                System.out.println("Failed to stop recording");
            }
        } catch (IOException e) {
            System.out.println("IOException occurred!");
        }
    }
}