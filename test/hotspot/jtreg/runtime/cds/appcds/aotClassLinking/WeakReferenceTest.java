/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test various test cases for archived WeakReference objects.
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build WeakReferenceTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar weakref.jar
 *             WeakReferenceTestApp WeakReferenceTestApp$Inner ShouldNotBeAOTInited ShouldNotBeArchived SharedQueue
 * @run driver WeakReferenceTest AOT
 */

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class WeakReferenceTest {
    static final String appJar = ClassFileInstaller.getJarPath("weakref.jar");
    static final String mainClass = "WeakReferenceTestApp";

    public static void main(String[] args) throws Exception {
        Tester t = new Tester();
        t.run(args);
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
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {
                    "-Xlog:gc,cds+class=debug",
                    "-XX:AOTInitTestClass=WeakReferenceTestApp",
                    "-Xlog:cds+map,cds+map+oops=trace:file=cds.oops.txt:none:filesize=0",
                };
            } else {
                return new String[] {
                    "-Xlog:gc",
                };
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                runMode.toString(),
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            out.shouldHaveExitValue(0);
            out.shouldNotContain("Unexpected exception:");
        }
    }
}

class WeakReferenceTestApp {
    // This class is NOT aot-initialized
    static class Inner {
        static boolean WeakReferenceTestApp_clinit_executed;
    }

    static {
        Inner.WeakReferenceTestApp_clinit_executed = true;

        // During the assembly phase, this block of code is called during the assembly
        // phase (triggered by the -XX:AOTInitTestClass=WeakReferenceTestApp flag).
        // It runs the clinit_for_testXXX() method to set up the aot-initialized data structures
        // that are used by  each testXXX() function.
        //
        // Note that this function is also called during the training run.
        // This function is NOT called during the production run, because WeakReferenceTestApp
        // is aot-initialized.

        clinit_for_testCollectedInAssembly();
        clinit_for_testWeakReferenceCollection();
        clinit_for_testQueue();
    }

    static WeakReference makeRef() {
        System.out.println("WeakReferenceTestApp::makeRef() is executed");
        WeakReference r = new WeakReference(root);
        System.out.println("r.get() = " + r.get());

        ShouldNotBeAOTInited.doit();
        return r;
    }

    static WeakReference makeRef2() {
        return new WeakReference(new WeakReferenceTestApp());
    }

    public static void main(String[] args) {
        try {
            runTests(args);
        } catch (Throwable t) {
            System.err.println("Unexpected exception:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    static void runTests(String[] args) throws Exception {
        boolean isProduction = args[0].equals("PRODUCTION");

        if (isProduction && Inner.WeakReferenceTestApp_clinit_executed) {
            throw new RuntimeException("WeakReferenceTestApp should have been aot-inited");
        }

        if (isProduction) {
            // A GC should have happened before the heap objects are written into
            // the AOT cache. So any unreachable referents should have been collected.
        } else {
            // We are in the training run. Simulate the GC mentioned in the above comment,
            // so the test cases should observe the same states as in the production run.
            System.gc();
        }

        testCollectedInAssembly(isProduction);
        testWeakReferenceCollection(isProduction);
        testQueue(isProduction);
    }

    //----------------------------------------------------------------------
    // Set up for testCollectedInAssembly()
    static WeakReference refToCollectedObj;

    static void clinit_for_testCollectedInAssembly() {
        // The referent will be GC-ed in the assembly run when the JVM forces a full GC.
        refToCollectedObj = new WeakReference(new String("collected in assembly"));
    }

    // [TEST CASE] Test the storage of a WeakReference whose referent has been collected during the assembly phase.
    static void testCollectedInAssembly(boolean isProduction) {
        System.out.println("refToCollectedObj.get() = " + refToCollectedObj.get());
        System.out.println("refToCollectedObj.isEnqueued() = " + refToCollectedObj.isEnqueued());

        if (refToCollectedObj.get() != null) {
            throw new RuntimeException("refToCollectedObj.get() should have been GC'ed");
        }

        /*
         * FIXME -- why does this fail, even in training run?

        if (!refToCollectedObj.isEnqueued()) {
            throw new RuntimeException("refToCollectedObj.isEnqueued() should be true");
        }
        */
    }

    //----------------------------------------------------------------------
    // Set up for testWeakReferenceCollection()
    static Object root;
    static WeakReference ref;

    static void clinit_for_testWeakReferenceCollection() {
        root = new WeakReferenceTestApp();
        ref = makeRef();
    }

    // [TEST CASE] A WeakReference allocated in assembly phase should be collectable in the production run
    static void testWeakReferenceCollection(boolean isProduction) {
        WeakReference ref2 = makeRef2();
        System.out.println("ref.get() = " + ref.get());   // created during assembly phase
        System.out.println("ref2.get() = " + ref2.get()); // created during production run

        if (ref.get() == null) {
            throw new RuntimeException("ref.get() should not be null");
        }
        if (ref2.get() == null) {
            throw new RuntimeException("ref2.get() should not be null");
        }

        System.out.println("... running GC ...");
        root = null;
        System.gc();

        System.out.println("ref.get() = " + ref.get());
        System.out.println("ref2.get() = " + ref2.get());

        if (ref.get() != null) {
            throw new RuntimeException("ref.get() should be null");
        }
        if (ref2.get() != null) {
            throw new RuntimeException("ref2.get() should be null");
        }

        System.out.println("ShouldNotBeAOTInited.doit_executed = " + ShouldNotBeAOTInited.doit_executed);
        if (isProduction && ShouldNotBeAOTInited.doit_executed) {
            throw new RuntimeException("ShouldNotBeAOTInited should not have been aot-inited");
        }
    }

    //----------------------------------------------------------------------
    // Set up for testQueue()
    static WeakReference refWithQueue;
    static SharedQueue sharedQueueInstance;

    static void clinit_for_testQueue() {
        // Make sure SharedQueue is also cached in *initialized* state.
        sharedQueueInstance = SharedQueue.sharedQueueInstance;

        refWithQueue = new WeakReference(String.class, SharedQueue.queue());
        ShouldNotBeArchived.ref = new WeakReference(ShouldNotBeArchived.instance, SharedQueue.queue());


        // Set to 2 in training run and assembly phase, but this state shouldn't be stored in
        // AOT cache.
        ShouldNotBeArchived.state = 2;
    }

    // [TEST CASE] Unrelated WeakReferences shouldn't be cached even if they are registered with the same queue
    static void testQueue(boolean isProduction) {
        System.out.println("refWithQueue.get() = " + refWithQueue.get());
        System.out.println("ShouldNotBeArchived.state = " + ShouldNotBeArchived.state);

        // [1] Although refWithQueue and ShouldNotBeArchived.ref are registered with the same queue, as both
        //     of their referents are strongly referenced, they are not added to the queue's "head".
        //     (Per javadoc: "registered reference objects are appended by the garbage collector after the
        //     appropriate reachability changes are detected");
        // [2] When the assembly phase scans refWithQueue, it shouldn't discover ShouldNotBeArchived.ref (via the queue),
        //     so ShouldNotBeArchived.ref should not be stored in the AOT cache.
        // [3] As a result, ShouldNotBeArchived should be cached in the *not initialized" state. Its <clinit>
        //     will be executed in the production run to set ShouldNotBeArchived.state to 1.
        if (isProduction && ShouldNotBeArchived.state != 1) {
            throw new RuntimeException("ShouldNotBeArchived should be 1 but is " + ShouldNotBeArchived.state);
        }
    }
}

class ShouldNotBeAOTInited {
    static WeakReference ref;
    static boolean doit_executed;
    static {
        System.out.println("ShouldNotBeAOTInited.<clinit> called");
    }
    static void doit() {
        System.out.println("ShouldNotBeAOTInited.doit()> called");
        doit_executed = true;
        ref = new WeakReference(new ShouldNotBeAOTInited());
    }
}

class ShouldNotBeArchived {
    static ShouldNotBeArchived instance = new ShouldNotBeArchived();
    static WeakReference ref;
    static int state = 1;
}

class SharedQueue {
    static SharedQueue sharedQueueInstance = new SharedQueue();
    private ReferenceQueue<Object> theQueue = new ReferenceQueue<Object>();

    static ReferenceQueue<Object> queue() {
        return sharedQueueInstance.theQueue;
    }
}
