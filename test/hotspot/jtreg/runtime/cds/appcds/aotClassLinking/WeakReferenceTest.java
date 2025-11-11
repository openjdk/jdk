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
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build WeakReferenceTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar weakref.jar
 *             WeakReferenceTestApp WeakReferenceTestApp$Inner ShouldNotBeAOTInited ShouldNotBeArchived SharedQueue
 *             WeakReferenceTestBadApp1 WeakReferenceTestBadApp2
 * @run driver WeakReferenceTest AOT --two-step-training
 */

import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;
import jtreg.SkippedException;

public class WeakReferenceTest {
    static final String appJar = ClassFileInstaller.getJarPath("weakref.jar");

    static final String goodApp = "WeakReferenceTestApp";
    static final String badApp1 = "WeakReferenceTestBadApp1";
    static final String badApp2 = "WeakReferenceTestBadApp2";

    public static void main(String[] args) throws Exception {
        new Tester(goodApp).run(args);

        runBadApp(badApp1, args);
        runBadApp(badApp2, args);
    }

    static void runBadApp(String badApp, String[] args) throws Exception {
        try {
            new Tester(badApp).run(args);
            throw new RuntimeException(badApp + " did not fail in assembly phase as expected");
        } catch (SkippedException e) {
            System.out.println("Negative test: expected SkippedException");
        }
    }

    static class Tester extends CDSAppTester {
        String mainClass;
        public Tester(String mainClass) {
            super(mainClass);
            this.mainClass = mainClass;

            if (mainClass != goodApp) {
                setCheckExitValue(false);
            }
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
                    "-XX:AOTInitTestClass=" + mainClass,
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
            if (runMode == RunMode.ASSEMBLY && mainClass != goodApp) {
                out.shouldNotHaveExitValue(0);
                out.shouldMatch("Cannot archive reference object .* of class java.lang.ref.WeakReference");
                if (mainClass == badApp1) {
                    out.shouldContain("referent cannot be null");
                } else {
                    out.shouldContain("referent is not registered with CDS.keepAlive()");
                }
                throw new SkippedException("Assembly phase expected to fail");
            }

            out.shouldHaveExitValue(0);
            out.shouldNotContain("Unexpected exception:");
        }
    }
}

class WeakReferenceTestApp {
    static class Inner { // This class is NOT aot-initialized
        static boolean WeakReferenceTestApp_clinit_executed;
    }

    static {
        Inner.WeakReferenceTestApp_clinit_executed = true;

        // This static {} block is executed the training run (which uses no AOT cache).
        //
        // During the assembly phase, this static {} block of is also executed
        // (triggered by the -XX:AOTInitTestClass=WeakReferenceTestApp flag).
        // It runs the aot_init_for_testXXX() method to set up the aot-initialized data structures
        // that are used by  each testXXX() function.
        //
        // This block is NOT executed during the production run, because WeakReferenceTestApp
        // is aot-initialized.

        aot_init_for_testCollectedInAssembly();
        aot_init_for_testWeakReferenceCollection();
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
    }

    //----------------------------------------------------------------------
    // Set up for testCollectedInAssembly()
    static WeakReference refToCollectedObj;

    static void aot_init_for_testCollectedInAssembly() {
        // The referent will be GC-ed in the assembly run when the JVM forces a full GC.
        refToCollectedObj = new WeakReference(new String("collected in assembly"));
    }

    // [TEST CASE] Test the storage of a WeakReference whose referent has been collected during the assembly phase.
    static void testCollectedInAssembly(boolean isProduction) {
        System.out.println("refToCollectedObj.get() = " + refToCollectedObj.get());

        if (refToCollectedObj.get() != null) {
            throw new RuntimeException("refToCollectedObj.get() should have been GC'ed");
        }
    }

    //----------------------------------------------------------------------
    // Set up for testWeakReferenceCollection()
    static Object root;
    static WeakReference ref;

    static void aot_init_for_testWeakReferenceCollection() {
        root = new String("to be collected in production");
        ref = makeRef();
    }

    static WeakReference makeRef() {
        System.out.println("WeakReferenceTestApp::makeRef() is executed");
        WeakReference r = new WeakReference(root);
        System.out.println("r.get() = " + r.get());

        ShouldNotBeAOTInited.doit();
        return r;
    }

    static WeakReference makeRef2() {
        return new WeakReference(new String("to be collected in production"));
    }


    // [TEST CASE] A WeakReference allocated in assembly phase should be collectable in the production run
    static void testWeakReferenceCollection(boolean isProduction) {
        WeakReference ref2 = makeRef2();
        System.out.println("ref.get() = " + ref.get());   // created during assembly phase
        System.out.println("ref2.get() = " + ref2.get()); // created during production run

        if (ref.get() == null) {
            throw new RuntimeException("ref.get() should not be null");
        }

        System.out.println("... running GC ...");
        root = null; // make ref.referent() eligible for collection
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

class WeakReferenceTestBadApp1 {
    static WeakReference refWithQueue;
    static SharedQueue sharedQueueInstance;

    static {
        // See comments in aotReferenceObjSupport.cpp: group [2] references cannot have null referent.
        sharedQueueInstance = SharedQueue.sharedQueueInstance;
        refWithQueue = new WeakReference(String.class, SharedQueue.queue());
        refWithQueue.clear();
    }

    public static void main(String args[]) {}
}

class WeakReferenceTestBadApp2 {
    static WeakReference refWithQueue;
    static SharedQueue sharedQueueInstance;

    static {
        // See comments in aotReferenceObjSupport.cpp: group [2] references must be registered with CDS.keepAlive()
        sharedQueueInstance = SharedQueue.sharedQueueInstance;
        refWithQueue = new WeakReference(String.class, SharedQueue.queue());
    }

    public static void main(String args[]) {}
}
