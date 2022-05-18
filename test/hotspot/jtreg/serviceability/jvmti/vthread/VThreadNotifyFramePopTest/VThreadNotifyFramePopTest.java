/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies that FRAME_POP event is delivered when called from URL.openStream().
 * @compile --enable-preview -source ${jdk.version}  VThreadNotifyFramePopTest.java
 * @run main/othervm/native
 *     --enable-preview
 *     -agentlib:VThreadNotifyFramePopTest
 *     -Djdk.defaultScheduler.parallelism=2 -Djdk.defaultScheduler.maxPoolSize=2
 *     VThreadNotifyFramePopTest
 */

/*
 * This test reproduces a bug with NotifyFramePop not working properly when called
 * from URL.openStream() while executing on a VThread. The FRAME_POP event is never
 * delivered.
 *
 * The test first sets up a breakpoint on URL.openStream(). Once it is hit the test
 * does a NotifyFramePop on the current frame, and also sets up a breakpoint on
 * the test's brkpoint() method, which is called immediately after URL.openStream()
 * returns. The expaction is that the FRAME_POP event should be delevered before
 * hitting the breakpoint in brkpoint().
 */
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class VThreadNotifyFramePopTest {
    private static final String agentLib = "VThreadNotifyFramePopTest";

    static native void enableEvents(Thread thread, Class testClass, Class urlClass);
    static native boolean check();

    static int brkpoint() {
        return 5;
    }
    static void run() {
        run2();
    }
    static void run2() {
        run3();
    }
    static void run3() {
        run4();
    }
    static void run4() {
        try {
            URL url = URI.create("http://openjdk.java.net/").toURL();
            try (InputStream in = url.openStream()) {
                brkpoint();
                in.readAllBytes();
                System.out.println("readAllBytes done");
            }
            System.out.println("try done");
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("run done");
    }

    void runTest() throws Exception {
        enableEvents(Thread.currentThread(), VThreadNotifyFramePopTest.class, URL.class);
        ThreadFactory factory = Thread.ofVirtual().factory();
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            executor.submit(VThreadNotifyFramePopTest::run);
            System.out.println("submit done");
        }
        System.out.println("Step::main done");
    }

    public static void main(String[] args) throws Exception {
        try {
            System.out.println("loading " + agentLib + " lib");
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }

        VThreadNotifyFramePopTest obj = new VThreadNotifyFramePopTest();
        obj.runTest();
        if (!check()) {
            System.out.println("VThreadNotifyFramePopTest failed!");
            throw new RuntimeException("VThreadNotifyFramePopTest failed!");
        } else {
            System.out.println("VThreadNotifyFramePopTest passed\n");
        }
        System.out.println("\n#####   main: finished  #####\n");
    }
}
