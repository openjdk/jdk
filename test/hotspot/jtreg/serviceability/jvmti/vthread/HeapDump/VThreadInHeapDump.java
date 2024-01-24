/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.ProcessTools;

import jdk.test.lib.hprof.model.JavaClass;
import jdk.test.lib.hprof.model.JavaHeapObject;
import jdk.test.lib.hprof.model.Root;
import jdk.test.lib.hprof.model.Snapshot;
import jdk.test.lib.hprof.model.StackFrame;
import jdk.test.lib.hprof.model.StackTrace;
import jdk.test.lib.hprof.model.ThreadObject;
import jdk.test.lib.hprof.parser.Reader;

/**
 * @test id=default
 * @requires vm.jvmti
 * @requires vm.continuations
 * @library /test/lib
 * @run main VThreadInHeapDump
 */

/**
 * @test id=no-vmcontinuations
 * @requires vm.jvmti
 * @library /test/lib
 * @comment pass extra VM arguments as the test arguments
 * @run main VThreadInHeapDump
 *           -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations
 */

class VThreadInHeapDumpTarg extends LingeredApp {

    public static class VThreadUnmountedReferenced {
    }
    public static class VThreadMountedReferenced {
    }
    public static class PThreadReferenced {
    }

    public class ThreadBase {
        private volatile boolean threadReady = false;

        protected void ready() {
            threadReady = true;
        }

        public void waitReady() {
            while (!threadReady) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public class VthreadUnmounted extends ThreadBase implements Runnable {
        public void run() {
            Object referenced = new VThreadUnmountedReferenced();
            ready();
            // The thread will be unmounted in awaitToStop().
            awaitToStop();
            Reference.reachabilityFence(referenced);
        }
    }

    public class VthreadMounted extends ThreadBase implements Runnable {
        int dummy = -1;

        public void run() {
            Object referenced = new VThreadMountedReferenced();
            ready();
            // Don't give a chance for the thread to unmount.
            while (!timeToStop) {
                if (++dummy == 10000) {
                    dummy = 0;
                }
            }
            Reference.reachabilityFence(referenced);
        }
    }

    public class Pthread extends ThreadBase implements Runnable {
        public void run() {
            Object referenced = new PThreadReferenced();
            ready();
            awaitToStop();
            Reference.reachabilityFence(referenced);
        }
    }

    CountDownLatch timeToStopLatch = new CountDownLatch(1);
    volatile boolean timeToStop = false;

    void awaitToStop() {
        try {
            timeToStopLatch.await();
        } catch (InterruptedException e) {
        }
    }

    private void runTest(String[] args) {
        try {
            // Unmounted virtual thread.
            VthreadUnmounted vthreadUnmounted = new VthreadUnmounted();
            Thread.ofVirtual().start(vthreadUnmounted);
            vthreadUnmounted.waitReady();

            // Mounted virtual thread.
            VthreadMounted vthreadMounted = new VthreadMounted();
            Thread.ofVirtual().start(vthreadMounted);
            vthreadMounted.waitReady();

            // Platform thread.
            Pthread pthread = new Pthread();
            Thread.ofPlatform().start(pthread);
            pthread.waitReady();

            // We are ready.
            LingeredApp.main(args);

        } finally {
            // Signal all threads to finish.
            timeToStop = true;
            timeToStopLatch.countDown();
        }
    }

    public static void main(String[] args) {
        VThreadInHeapDumpTarg test = new VThreadInHeapDumpTarg();
        test.runTest(args);
    }

}


public class VThreadInHeapDump {

    // test arguments are extra VM options for target process
    public static void main(String[] args) throws Exception {
        File dumpFile = new File("Myheapdump.hprof");
        createDump(dumpFile, args);
        verifyDump(dumpFile);
    }

    private static void createDump(File dumpFile, String[] extraOptions) throws Exception {
        LingeredApp theApp = null;
        try {
            theApp = new VThreadInHeapDumpTarg();

            List<String> extraVMArgs = new ArrayList<>();
            extraVMArgs.add("-Djdk.virtualThreadScheduler.parallelism=1");
            extraVMArgs.add("-Xlog:heapdump");
            extraVMArgs.addAll(Arrays.asList(extraOptions));
            LingeredApp.startApp(theApp, extraVMArgs.toArray(new String[0]));

            //jcmd <pid> GC.heap_dump <file_path>
            JDKToolLauncher launcher = JDKToolLauncher
                    .createUsingTestJDK("jcmd")
                    .addToolArg(Long.toString(theApp.getPid()))
                    .addToolArg("GC.heap_dump")
                    .addToolArg(dumpFile.getAbsolutePath());
            Process p = ProcessTools.startProcess("jcmd", new ProcessBuilder(launcher.getCommand()));
            // If something goes wrong with heap dumping most likely we'll get crash of the target VM.
            while (!p.waitFor(5, TimeUnit.SECONDS)) {
                if (!theApp.getProcess().isAlive()) {
                    log("ERROR: target VM died, killing jcmd...");
                    p.destroyForcibly();
                    throw new Exception("Target VM died");
                }
            }

            if (p.exitValue() != 0) {
                throw new Exception("Jcmd exited with code " + p.exitValue());
            }
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    private static void verifyDump(File dumpFile) throws Exception {
        Asserts.assertTrue(dumpFile.exists(), "Heap dump file not found.");

        log("Reading " + dumpFile + "...");
        try (Snapshot snapshot = Reader.readFile(dumpFile.getPath(), true, 0)) {
            log("Resolving snapshot...");
            snapshot.resolve(true);
            log("Snapshot resolved.");

            // Log all threads with stack traces and stack references.
            List<ThreadObject> threads = snapshot.getThreads();
            List<Root> roots = Collections.list(snapshot.getRoots());
            // And detect thread object duplicates.
            Set<Long> uniqueThreads = new HashSet<>();

            log("Threads:");
            for (ThreadObject thread: threads) {
                JavaHeapObject threadObj = snapshot.findThing(thread.getId());
                JavaClass threadClass = threadObj.getClazz();
                StackTrace st = thread.getStackTrace();
                StackFrame[] frames = st.getFrames();
                log("thread " + thread.getIdString() + " (" + threadClass.getName() + "), " + frames.length + " frames");

                if (uniqueThreads.contains(thread.getId())) {
                    log(" - ERROR: duplicate");
                } else {
                    uniqueThreads.add(thread.getId());
                }

                List<Root> stackRoots = findStackRoot(roots, thread);
                for (int i = 0; i < frames.length; i++) {
                    log("  - [" + i + "] "
                        + frames[i].getClassName() + "." + frames[i].getMethodName()
                        + frames[i].getMethodSignature()
                        + " (" + frames[i].getSourceFileName()
                        + ":" + frames[i].getLineNumber() + ")");

                    for (Root r: stackRoots) {
                        StackFrame[] rootFrames = r.getStackTrace().getFrames();
                        // the frame this local belongs to
                        StackFrame frame = rootFrames[rootFrames.length - 1];
                        if (frame == frames[i]) {
                            JavaHeapObject obj = snapshot.findThing(r.getId());
                            JavaClass objClass = obj.getClazz();
                            log("      " + r.getDescription() + ": " + objClass.getName());
                        }
                    }
                }
            }

            if (threads.size() != uniqueThreads.size()) {
                throw new RuntimeException("Thread duplicates detected (" + (threads.size() - uniqueThreads.size()) + ")");
            }

            // Verify objects from thread stacks are dumped.
            test(snapshot, VThreadInHeapDumpTarg.VThreadMountedReferenced.class);
            test(snapshot, VThreadInHeapDumpTarg.PThreadReferenced.class);
            test(snapshot, VThreadInHeapDumpTarg.VThreadUnmountedReferenced.class);
        }

    }

    private static List<Root> findStackRoot(List<Root> roots, ThreadObject thread) {
        List<Root> result = new ArrayList<>();
        for (Root root: roots) {
            if (root.getReferrerId() == thread.getId()) {
                result.add(root);
            }
        }
        return result;
    }

    private static void test(Snapshot snapshot, String className) {
        log("Testing " + className + "...");
        JavaClass jClass = snapshot.findClass(className);
        if (jClass == null) {
            throw new RuntimeException("'" + className + "' not found");
        }
        int instanceCount = jClass.getInstancesCount(false);
        if (instanceCount != 1) {
            throw new RuntimeException("Expected 1 instance, " + instanceCount + " instances found");
        }
        // There is the only instance.
        JavaHeapObject heapObj = jClass.getInstances(false).nextElement();

        Root root = heapObj.getRoot();
        if (root == null) {
            throw new RuntimeException("No root for " + className + " instance");
        }
        log("  root: " + root.getDescription());
        JavaHeapObject referrer = root.getReferrer();
        log("  referrer: " + referrer);
    }

    private static void test(Snapshot snapshot, Class cls) {
        test(snapshot, cls.getName());
    }

    private static void log(Object s) {
        System.out.println(s);
    }
}
