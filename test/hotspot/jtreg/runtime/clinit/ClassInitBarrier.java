/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @library /test/lib
 *
 * @requires !vm.graal.enabled
 * @enablePreview
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -Xint                   -DTHROW=false -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -Xint                   -DTHROW=true  -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=false -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=true  -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=false -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=true  -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=false -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=true  -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=false -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=true  -XX:CompileCommand=dontinline,*::static* -Xcheck:jni ClassInitBarrier
 *
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=false -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:TieredStopAtLevel=1 -DTHROW=true  -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=false -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 * @run main/othervm/native -Xbatch -XX:CompileCommand=dontinline,*::test* -XX:-TieredCompilation  -DTHROW=true  -XX:CompileCommand=exclude,*::static* -Xcheck:jni ClassInitBarrier
 */

import jdk.test.lib.Asserts;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ClassInitBarrier {
    static {
        System.loadLibrary("ClassInitBarrier");

        if (!init()) {
            throw new Error("init failed");
        }
    }

    static native boolean init();

    static final boolean THROW = Boolean.getBoolean("THROW");

    static value class MyValue {
        int x = 42;

        void verify() {
            Asserts.assertEquals(x, 42);
        }
    }

    static class Test {

        static class A {
            static {
                if (!init(B.class)) {
                    throw new Error("init failed");
                }

                changePhase(Phase.IN_PROGRESS);
                runTests();      // interpreted mode
                warmup();        // trigger compilation
                runTests();      // compiled mode

                ensureBlocked(); // ensure still blocked
                maybeThrow();    // fail initialization if needed

                changePhase(Phase.FINISHED);
            }

            static              void staticM(Runnable action, MyValue val) { action.run(); val.verify(); }
            static synchronized void staticS(Runnable action, MyValue val) { action.run(); val.verify(); }
            static native       void staticN(Runnable action, MyValue val);

            static int staticF;

            int f;
            void m() {}

            static native boolean init(Class<B> cls);
        }

        static class B extends A {}

        static void testInvokeStatic(Runnable action, MyValue val)       { A.staticM(action, val); }
        static void testInvokeStaticSync(Runnable action, MyValue val)   { A.staticS(action, val); }
        static void testInvokeStaticNative(Runnable action, MyValue val) { A.staticN(action, val); }

        static int  testGetStatic(Runnable action, MyValue val)    { int v = A.staticF; action.run(); val.verify(); return v;   }
        static void testPutStatic(Runnable action, MyValue val)    { A.staticF = 1;     action.run(); val.verify(); }
        static A    testNewInstanceA(Runnable action, MyValue val) { A obj = new A();   action.run(); val.verify(); return obj; }
        static B    testNewInstanceB(Runnable action, MyValue val) { B obj = new B();   action.run(); val.verify(); return obj; }

        static int  testGetField(A recv, Runnable action, MyValue val)      { int v = recv.f; action.run(); val.verify(); return v; }
        static void testPutField(A recv, Runnable action, MyValue val)      { recv.f = 1;     action.run(); val.verify(); }
        static void testInvokeVirtual(A recv, Runnable action, MyValue val) { recv.m();       action.run(); val.verify(); }

        static native void testInvokeStaticJNI(Runnable action, MyValue val);
        static native void testInvokeStaticSyncJNI(Runnable action, MyValue val);
        static native void testInvokeStaticNativeJNI(Runnable action, MyValue val);

        static native int  testGetStaticJNI(Runnable action, MyValue val);
        static native void testPutStaticJNI(Runnable action, MyValue val);
        static native A    testNewInstanceAJNI(Runnable action, MyValue val);
        static native B    testNewInstanceBJNI(Runnable action, MyValue val);

        static native int  testGetFieldJNI(A recv, Runnable action, MyValue val);
        static native void testPutFieldJNI(A recv, Runnable action, MyValue val);
        static native void testInvokeVirtualJNI(A recv, Runnable action, MyValue val);

        static void runTests() {
            checkBlockingAction(Test::testInvokeStatic);       // invokestatic
            checkBlockingAction(Test::testInvokeStaticSync);   // invokestatic
            checkBlockingAction(Test::testInvokeStaticNative); // invokestatic
            checkBlockingAction(Test::testGetStatic);          // getstatic
            checkBlockingAction(Test::testPutStatic);          // putstatic
            checkBlockingAction(Test::testNewInstanceA);       // new

            checkNonBlockingAction(Test::testInvokeStaticJNI);       // invokestatic
            checkNonBlockingAction(Test::testInvokeStaticSyncJNI);   // invokestatic
            checkNonBlockingAction(Test::testInvokeStaticNativeJNI); // invokestatic
            checkNonBlockingAction(Test::testGetStaticJNI);          // getstatic
            checkNonBlockingAction(Test::testPutStaticJNI);          // putstatic
            checkBlockingAction(Test::testNewInstanceAJNI);          // new

            A recv = testNewInstanceB(NON_BLOCKING.get(), new MyValue());  // trigger B initialization
            checkNonBlockingAction(Test::testNewInstanceB); // new: NO BLOCKING: same thread: A being initialized, B fully initialized

            checkNonBlockingAction(recv, Test::testGetField);      // getfield
            checkNonBlockingAction(recv, Test::testPutField);      // putfield
            checkNonBlockingAction(recv, Test::testInvokeVirtual); // invokevirtual

            checkNonBlockingAction(Test::testNewInstanceBJNI);        // new: NO BLOCKING: same thread: A being initialized, B fully initialized
            checkNonBlockingAction(recv, Test::testGetFieldJNI);      // getfield
            checkNonBlockingAction(recv, Test::testPutFieldJNI);      // putfield
            checkNonBlockingAction(recv, Test::testInvokeVirtualJNI); // invokevirtual
        }

        static void warmup() {
            MyValue val = new MyValue();
            for (int i = 0; i < 20_000; i++) {
                testInvokeStatic(      NON_BLOCKING_WARMUP, val);
                testInvokeStaticNative(NON_BLOCKING_WARMUP, val);
                testInvokeStaticSync(  NON_BLOCKING_WARMUP, val);
                testGetStatic(         NON_BLOCKING_WARMUP, val);
                testPutStatic(         NON_BLOCKING_WARMUP, val);
                testNewInstanceA(      NON_BLOCKING_WARMUP, val);
                testNewInstanceB(      NON_BLOCKING_WARMUP, val);

                testGetField(new B(),      NON_BLOCKING_WARMUP, val);
                testPutField(new B(),      NON_BLOCKING_WARMUP, val);
                testInvokeVirtual(new B(), NON_BLOCKING_WARMUP, val);
            }
        }

        static void run() {
            execute(ExceptionInInitializerError.class, () -> triggerInitialization(A.class));
            ensureFinished();
            runTests(); // after initialization is over
        }
    }

    // ============================================================================================================== //

    static void execute(Class<? extends Throwable> expectedExceptionClass, Runnable action) {
        try {
            action.run();
            if (THROW) throw failure("no exception thrown");
        } catch (Throwable e) {
            if (THROW) {
                if (e.getClass() == expectedExceptionClass) {
                    // expected
                } else {
                    String msg = String.format("unexpected exception thrown: expected %s, caught %s",
                            expectedExceptionClass.getName(), e);
                    throw failure(msg, e);
                }
            } else {
                throw failure("no exception expected", e);
            }
        }
    }

    private static AssertionError failure(String msg) {
        return new AssertionError(phase + ": " + msg);
    }

    private static AssertionError failure(String msg, Throwable e) {
        return new AssertionError(phase + ": " + msg, e);
    }

    static final List<Thread> BLOCKED_THREADS = Collections.synchronizedList(new ArrayList<>());
    static final Consumer<Thread> ON_BLOCK = BLOCKED_THREADS::add;

    static final Map<Thread,Throwable> FAILED_THREADS = Collections.synchronizedMap(new HashMap<>());
    static final Thread.UncaughtExceptionHandler ON_FAILURE = FAILED_THREADS::put;

    private static void ensureBlocked() {
        for (Thread thr : BLOCKED_THREADS) {
            try {
                thr.join(100);
                if (!thr.isAlive()) {
                    dump(thr);
                    throw new AssertionError("not blocked");
                }
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }


    private static void ensureFinished() {
        for (Thread thr : BLOCKED_THREADS) {
            try {
                thr.join(15_000);
            } catch (InterruptedException e) {
                throw new Error(e);
            }
            if (thr.isAlive()) {
                dump(thr);
                throw new AssertionError(thr + ": still blocked");
            }
        }
        for (Thread thr : BLOCKED_THREADS) {
            if (THROW) {
                if (!FAILED_THREADS.containsKey(thr)) {
                    throw new AssertionError(thr + ": exception not thrown");
                }

                Throwable ex = FAILED_THREADS.get(thr);
                if (ex.getClass() != NoClassDefFoundError.class) {
                    throw new AssertionError(thr + ": wrong exception thrown", ex);
                }
            } else {
                if (FAILED_THREADS.containsKey(thr)) {
                    Throwable ex = FAILED_THREADS.get(thr);
                    throw new AssertionError(thr + ": exception thrown", ex);
                }
            }
        }
        if (THROW) {
            Asserts.assertEquals(BLOCKING_COUNTER.get(), 0);
        } else {
            Asserts.assertEquals(BLOCKING_COUNTER.get(), BLOCKING_ACTIONS.get());
        }

        dumpInfo();
    }

    interface TestCase0 {
        void run(Runnable runnable, MyValue val);
    }

    interface TestCase1<T> {
        void run(T arg, Runnable runnable, MyValue val);
    }

    enum Phase { BEFORE_INIT, IN_PROGRESS, FINISHED, INIT_FAILURE }

    static volatile Phase phase = Phase.BEFORE_INIT;

    static void changePhase(Phase newPhase) {
        dumpInfo();

        Phase oldPhase = phase;
        switch (oldPhase) {
            case BEFORE_INIT:
                Asserts.assertEquals(NON_BLOCKING_ACTIONS.get(), 0);
                Asserts.assertEquals(NON_BLOCKING_COUNTER.get(), 0);

                Asserts.assertEquals(BLOCKING_ACTIONS.get(),     0);
                Asserts.assertEquals(BLOCKING_COUNTER.get(),     0);
                break;
            case IN_PROGRESS:
                Asserts.assertEquals(NON_BLOCKING_COUNTER.get(), NON_BLOCKING_ACTIONS.get());

                Asserts.assertEquals(BLOCKING_COUNTER.get(), 0);
                break;
            default: throw new Error("wrong phase transition " + oldPhase);
        }
        phase = newPhase;
    }

    static void dumpInfo() {
        System.out.println("Phase: " + phase);
        System.out.println("Non-blocking actions: " + NON_BLOCKING_COUNTER.get() + " / " + NON_BLOCKING_ACTIONS.get());
        System.out.println("Blocking actions:     " + BLOCKING_COUNTER.get()     + " / " + BLOCKING_ACTIONS.get());
    }

    static final Runnable NON_BLOCKING_WARMUP = () -> {
        if (phase != Phase.IN_PROGRESS) {
            throw new AssertionError("NON_BLOCKING: wrong phase: " + phase);
        }
    };

    static Runnable disposableAction(final Phase validPhase, final AtomicInteger invocationCounter, final AtomicInteger actionCounter) {
        actionCounter.incrementAndGet();

        final AtomicBoolean cnt = new AtomicBoolean(false);
        return () -> {
            if (cnt.getAndSet(true)) {
                throw new Error("repeated invocation");
            }
            invocationCounter.incrementAndGet();
            if (phase != validPhase) {
                throw new AssertionError("NON_BLOCKING: wrong phase: " + phase);
            }
        };
    }

    @FunctionalInterface
    interface Factory<V> {
        V get();
    }

    static final AtomicInteger NON_BLOCKING_COUNTER = new AtomicInteger(0);
    static final AtomicInteger NON_BLOCKING_ACTIONS = new AtomicInteger(0);
    static final Factory<Runnable> NON_BLOCKING = () -> disposableAction(phase, NON_BLOCKING_COUNTER, NON_BLOCKING_ACTIONS);

    static final AtomicInteger BLOCKING_COUNTER = new AtomicInteger(0);
    static final AtomicInteger BLOCKING_ACTIONS = new AtomicInteger(0);
    static final Factory<Runnable> BLOCKING     = () -> disposableAction(Phase.FINISHED, BLOCKING_COUNTER, BLOCKING_ACTIONS);

    static void checkBlockingAction(TestCase0 r) {
        MyValue val = new MyValue();
        switch (phase) {
            case IN_PROGRESS: {
                // Barrier during class initalization.
                r.run(NON_BLOCKING.get(), val);             // initializing thread
                checkBlocked(ON_BLOCK, ON_FAILURE, r); // different thread
                break;
            }
            case FINISHED: {
                // No barrier after class initalization is over.
                r.run(NON_BLOCKING.get(), val); // initializing thread
                checkNotBlocked(r);        // different thread
                break;
            }
            case INIT_FAILURE: {
                // Exception is thrown after class initialization failed.
                TestCase0 test = (action, valarg) -> execute(NoClassDefFoundError.class, () -> r.run(action, valarg));

                test.run(NON_BLOCKING.get(), val); // initializing thread
                checkNotBlocked(test);        // different thread
                break;
            }
            default: throw new Error("wrong phase: " + phase);
        }
    }

    static void checkNonBlockingAction(TestCase0 r) {
        r.run(NON_BLOCKING.get(), new MyValue()); // initializing thread
        checkNotBlocked(r);        // different thread
    }

    static <T> void checkNonBlockingAction(T recv, TestCase1<T> r) {
        r.run(recv, NON_BLOCKING.get(), new MyValue());                  // initializing thread
        checkNotBlocked((action, val) -> r.run(recv, action, val)); // different thread
    }

    static void checkFailingAction(TestCase0 r) {
        r.run(NON_BLOCKING.get(), new MyValue()); // initializing thread
        checkNotBlocked(r);        // different thread
    }

    static void triggerInitialization(Class<?> cls) {
        try {
            Class<?> loadedClass = Class.forName(cls.getName(), true, cls.getClassLoader());
            if (loadedClass != cls) {
                throw new Error("wrong class");
            }
        } catch (ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    static void checkBlocked(Consumer<Thread> onBlockHandler, Thread.UncaughtExceptionHandler onException, TestCase0 r) {
        Thread thr = new Thread(() -> {
            try {
                r.run(BLOCKING.get(), new MyValue());
                System.out.println("Thread " + Thread.currentThread() + ": Finished successfully");
            } catch(Throwable e) {
                System.out.println("Thread " + Thread.currentThread() + ": Exception thrown: " + e);
                if (!THROW) {
                    e.printStackTrace();
                }
                throw e;
            }
        } );
        thr.setUncaughtExceptionHandler(onException);

        thr.start();
        try {
            thr.join(100);

            dump(thr);
            if (thr.isAlive()) {
                onBlockHandler.accept(thr); // blocked
            } else {
                throw new AssertionError("not blocked");
            }
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    static void checkNotBlocked(TestCase0 r) {
        final Thread thr = new Thread(() -> r.run(NON_BLOCKING.get(), new MyValue()));
        final Throwable[] ex = new Throwable[1];
        thr.setUncaughtExceptionHandler((t, e) -> {
            if (thr != t) {
                ex[0] = new Error("wrong thread: " + thr + " vs " + t);
            } else {
                ex[0] = e;
            }
        });

        thr.start();
        try {
            thr.join(15_000);
            if (thr.isAlive()) {
                dump(thr);
                throw new AssertionError("blocked");
            }
        } catch (InterruptedException e) {
            throw new Error(e);
        }

        if (ex[0] != null) {
            throw new AssertionError("no exception expected", ex[0]);
        }
    }

    static void maybeThrow() {
        if (THROW) {
            changePhase(Phase.INIT_FAILURE);
            throw new RuntimeException("failed class initialization");
        }
    }

    private static void dump(Thread thr) {
        System.out.println("Thread: " + thr);
        System.out.println("Thread state: " + thr.getState());
        if (thr.isAlive()) {
            for (StackTraceElement frame : thr.getStackTrace()) {
                System.out.println(frame);
            }
        } else {
            if (FAILED_THREADS.containsKey(thr)) {
                System.out.println("Failed with an exception: ");
                FAILED_THREADS.get(thr).toString();
            } else {
                System.out.println("Finished successfully");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Test.run();
        System.out.println("TEST PASSED");
    }
}
