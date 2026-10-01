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

/**
 * @test
 * @bug 8387045
 * @summary Transforming during VM shutdown could lead to deadlock
 * @requires vm.jvmti
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.instrument
 * @run driver TransformerShutdownDeadlockTest
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.helpers.ClassFileInstaller;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.Collections;

public class TransformerShutdownDeadlockTest {

    private static String manifest = "Premain-Class: " +
                    TransformerShutdownDeadlockTest.Agent.class.getName() + "\n"
                    + "Can-Retransform-Classes: true\n";

    public static void main(String args[]) throws Throwable {
        // The JVMTI vm_death spin loop lasts 60 seconds. Set up a 30
        // second deadline to detect excessive spinning.
        long deadline_ns = 30L * 1000 * 1000 * 1000;

        String agentJar = buildAgent();
        ProcessBuilder pb =
            ProcessTools.createLimitedTestJavaProcessBuilder("-javaagent:" + agentJar,
                                                             "-Xlog:class+load=info",
                                                             TransformerShutdownDeadlockTest.Agent.class.getName());
        long start = System.nanoTime();
        ProcessTools.executeProcess(pb).shouldHaveExitValue(0);
        long end = System.nanoTime();
        if (end - start > deadline_ns) {
            throw new Error("VM exit deadlock potentially detected: " + (end - start));
        }
    }

    private static String buildAgent() throws Exception {
        Path jar = Files.createTempFile(Paths.get("."), null, ".jar");
        String jarPath = jar.toAbsolutePath().toString();
        ClassFileInstaller.writeJar(jarPath,
                ClassFileInstaller.Manifest.fromString(manifest),
                TransformerShutdownDeadlockTest.class.getName());
        return jarPath;
    }

    // The class to retransform when loaded.
    static class Transform {
        static {
            System.out.println(Thread.currentThread().getName() + " doing init for Transform");
        }
    }

    public static class Agent implements ClassFileTransformer {
        private static Instrumentation instrumentation;

        public static void premain(String agentArgs, Instrumentation inst) {
            instrumentation = inst;
        }

        private static volatile boolean transform_running = false;
        private static volatile boolean starting_exit = false;

        @Override
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer)
            throws IllegalClassFormatException {

            if (!"TransformerShutdownDeadlockTest$Transform".equals(className)) {
                return null;
            }

            String name = Thread.currentThread().getName();
            System.out.println(name + " in transform()");
            transform_running = true; // Release main thread
            try {
                while (!starting_exit); // Wait for main thread to be ready
                Thread.sleep(200);     // Give main thread a chance to hit vm_death
                // Force-load some new classes and do some active work. The deadlock is
                // triggered by a deopt request.
                System.out.println(name + " creating FileSystem");
                URL jarUrl = Agent.class.getProtectionDomain().getCodeSource().getLocation();
                URI jarUri = URI.create("jar:" + jarUrl.toURI());
                try (FileSystem jar = FileSystems.newFileSystem(jarUri, Collections.emptyMap())) {
                }
            }
            catch (Throwable t) {
                throw new Error("Unexpected exception: ", t);
            }
            System.out.println(name + " done retransform");
            return null;

        }

        public static void main(String[] args) throws Exception {
            instrumentation.addTransformer(new TransformerShutdownDeadlockTest.Agent(), true);

            // Start a daemon thread to load the class that will be transformed
            final Thread t = new Thread() {
                    public void run() {
                        System.out.println("TransformerThread about to load Transform");
                        Transform tr = new Transform();
                        System.out.println("TransformerThread done");
                    }
                };
            t.setName("TransformerThread");
            t.setDaemon(true);
            System.out.println("Main thread about to start TransformerThread");
            t.start();

            while (!transform_running); // Wait for transform to start
            System.out.println("Main thread calling exit");
            starting_exit = true; // Release transform thread
            System.exit(0);
        }
    }
}
