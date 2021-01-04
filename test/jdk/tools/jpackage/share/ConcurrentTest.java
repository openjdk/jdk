/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Functional;
import jdk.jpackage.test.HelloApp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.nio.file.Path;


/**
 * Concurrent test.  Using ToolProvider, run several jpackage test concurrently
 */

/*
 * @test
 * @summary Concurrent jpackage command runs using ToolProvider
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile ConcurrentTest.java
 * @run main/othervm/timeout=480 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ConcurrentTest
 */
public class ConcurrentTest {

    final int TEST_COUNT = 3; // default number of jpackage commands to run

    @Test
    public void test() throws Exception {
        final Path inputDir = TKit.workDir().resolve("input");
        int count = TEST_COUNT;
        String propValue = System.getProperty("jpackage.concurrent.count");
        if (propValue != null) {
            try {
                count = Integer.parseInt(propValue);
            } catch (Exception e) {
                // ignore - use default count
            }
        }
        long timeout = 2L * count; // minutes to run tests before timeout
        HelloApp.createBundle(JavaAppDesc.parse("hello.jar:Hello"), inputDir);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(Functional.ThrowingRunnable.toRunnable(() ->
                    initTest(inputDir).run(
                    PackageTest.Action.CREATE)));
        }

        ExecutorService exec = Executors.newCachedThreadPool();
        tasks.stream().forEach(exec::execute);
        exec.shutdown();
        boolean finished = exec.awaitTermination(timeout, TimeUnit.MINUTES);
        // even if we are throwing assertion below we need to try to stop these
        // threads before exiting
        if (!finished) {
            exec.shutdownNow();
        }
        TKit.assertTrue(finished, "Executing jpackage " + count +
                " times timed out after " + timeout + " minutes.");
    }

    private PackageTest initTest(Path inputDir)
            throws Exception {
        final Path outputDir;
        synchronized (this) {
            outputDir = TKit.createTempDirectory("output");
        }
        return new PackageTest().addInitializer(cmd -> {
            cmd.useToolProvider(true);
            cmd.setArgumentValue("--input", inputDir);
            cmd.setArgumentValue("--main-class", "Hello");
            cmd.setArgumentValue("--main-jar", "hello.jar");
            cmd.setArgumentValue("--dest", outputDir);
            cmd.addArguments("--verbose");
        });
    }
}
