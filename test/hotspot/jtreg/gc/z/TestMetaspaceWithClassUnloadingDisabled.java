/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package gc.z;

/*
 * @test
 * @bug 8394682
 * @summary Test that ZGC completes metaspace allocations with class unloading disabled
 * @requires vm.gc.Z
 * @requires vm.opt.ClassUnloading == null | !vm.opt.ClassUnloading
 * @library /test/lib
 * @run driver/timeout=120 gc.z.TestMetaspaceWithClassUnloadingDisabled
 */

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Utils;
import jdk.test.lib.classloader.GeneratingClassLoader;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.thread.TestThread;

public class TestMetaspaceWithClassUnloadingDisabled {
    public static void main(String[] args) throws Exception {
        // Keep jtreg outside the VM that exhausts metaspace.
        Process process = ProcessTools.createTestJavaProcessBuilder(
                "-XX:+UseZGC", "-XX:-ClassUnloading", "-Xms64m", "-Xmx128m",
                "-XX:MaxMetaspaceSize=16m", "-Xlog:metaspace=info",
                Test.class.getName()).start();
        try {
            OutputAnalyzer output = new OutputAnalyzer(process);
            // Enforce the hang-detection timeout outside the exhausted VM.
            if (!process.waitFor(Utils.adjustTimeout(60_000), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly().waitFor();
                output.reportDiagnosticSummary();
                throw new RuntimeException("Metaspace allocation workers did not complete");
            }
            output.shouldHaveExitValue(0)
                  .shouldContain("All workers completed with metaspace OOME");
        } finally {
            process.destroyForcibly().waitFor();
        }
    }

    public static class Test {
        public static void main(String[] args) throws Throwable {
            // Concurrent allocation failures can wait for another thread's GC.
            // With class unloading disabled, those requests must still complete
            // so that every worker gets OOME instead of remaining blocked.
            int count = 8;
            CyclicBarrier start = new CyclicBarrier(count);
            TestThread[] workers = new TestThread[count];
            for (int i = 0; i < count; i++) {
                GeneratingClassLoader loader = new GeneratingClassLoader();
                // Resolve the generator's setup before exhausting metaspace.
                loader.loadClass(loader.getClassName(0));
                workers[i] = new TestThread(() -> exhaustMetaspace(loader, start));
            }
            for (TestThread worker : workers) {
                worker.start();
            }

            // Successful runs finish as soon as all workers get OOME.
            // The parent detects a worker stuck waiting for purge.
            for (TestThread worker : workers) {
                worker.joinAndThrow();
            }
            System.out.println("All workers completed with metaspace OOME");
        }

        private static void exhaustMetaspace(GeneratingClassLoader loader, CyclicBarrier start) {
            try {
                start.await();
                for (int i = 1; ; i++) {
                    loader.loadClass(loader.getClassName(i));
                }
            } catch (OutOfMemoryError e) {
                String message = e.getMessage();
                if (!"Metaspace".equals(message) && !"Compressed class space".equals(message)) {
                    throw e;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
