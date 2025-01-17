/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.RedefineClasses;


import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadFactory;

import nsk.share.TestFailure;
import nsk.share.gc.GCTestBase;
import nsk.share.test.ExecutionController;
import nsk.share.test.Stresser;
import nsk.share.test.Tests;

import jdk.test.lib.compiler.InMemoryJavaCompiler;

/**
 * There is a data structure named "dictionary" in class BlockFreelist. It stores
 * information about free memory blocks for further reusing. Allocation of new block goes
 * from dictionary only if dictionary is fat enough. (At the moment of test creation this limit is 64K.)
 *
 * This tests stresses dictionary as other test metaspace/StressDictionary does, but instead of
 * failing classloading this test leverages redefineClass method from jvmti.
 */
public class StressRedefine extends GCTestBase {
    private static int staticMethodCallersNumber = 10;
    private static int nonstaticMethodCallersNumber = 10;
    private static int redefiningThreadsNumber = 40;
    private static double corruptingBytecodeProbability = .75;
    private static boolean virtualThreads = false;

    private static volatile Class<?> myClass;
    private static ExecutionController stresser;
    private static String[] args;

    private static byte[] bytecode;

    // This is random generator used for generating seeds for other Randoms. Setting seed
    // from command line sets seed for this random.
    static Random seedGenerator;

    static {
        try {
            System.loadLibrary("stressRedefine");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Could not load stressRedefine library");
            System.err.println("java.library.path:" +
                System.getProperty("java.library.path"));
            throw e;
        }
    }

    native static int makeRedefinition(int verbose, Class<?> redefClass, byte[] classBytes);

    public static void main(String[] args) {
        StressRedefine.args = args;
        Tests.runTest(new StressRedefine(), args);
    }

    @Override
    public void run() {
        seedGenerator = new Random(runParams.getSeed());
        GenerateSourceHelper.setRandom(new Random(seedGenerator.nextLong()));
        stresser = new Stresser(args);

        for (int i = 0; i < args.length; i++ ) {
            if ("-staticMethodCallersNumber".equals(args[i])) {
                staticMethodCallersNumber = Integer.parseInt(args[i + 1]);
            } else if ("-nonstaticMethodCallersNumber".equals(args[i])) {
                nonstaticMethodCallersNumber = Integer.parseInt(args[i + 1]);
            } else if ("-redefiningThreadsNumber".equals(args[i])) {
                redefiningThreadsNumber = Integer.parseInt(args[i + 1]);
            } else if ("-corruptingBytecodeProbability".equals(args[i])) {
                corruptingBytecodeProbability = Double.parseDouble(args[i + 1]);
            } else if ("-virtualThreads".equals(args[i])) {
                virtualThreads = true;
            }
        }

        //Dynamic attach if required
        nsk.share.jvmti.JVMTITest.commonInit(args);

        new StressRedefine().runIt();
    }

    private static void runMethod(Random random, String name) {
        while (stresser.continueExecution()) {
            try {
                // Just for fun we transfer parameters to method
                Object res = myClass.getMethod(name, double.class, int.class, Object.class)
                                         .invoke(myClass.newInstance(), random.nextDouble(), random.nextInt(), new Object());
             } catch (IllegalArgumentException | InvocationTargetException | InstantiationException
                     | IllegalAccessException | NoSuchMethodException e) {
                 // It's okay to get exception here since we are corrupting bytecode and can't expect
                 // class to work properly.
                 System.out.println("Got expected exception: " + e.toString());
             }
        }
    }

    private static class StaticMethodCaller implements Runnable {
        private Random random;
        public StaticMethodCaller() {random = new Random(seedGenerator.nextLong());}

        @Override
        public void run() {
            runMethod(random, GenerateSourceHelper.STATIC_METHOD_NAME);
        }
    }

    private static class NonstaticMethodCaller implements Runnable {
        private Random random;
        public NonstaticMethodCaller() {random = new Random(seedGenerator.nextLong());}

        @Override
        public void run() {
            runMethod(random, GenerateSourceHelper.NONSTATIC_METHOD_NAME);
        }
    }

    private static class Worker implements Runnable {
        private Random random;
        public Worker() {random = new Random(seedGenerator.nextLong());}

        @Override
        public void run() {
            while (stresser.continueExecution()) {
                byte[] badBytecode = bytecode.clone();
                if (random.nextDouble() < corruptingBytecodeProbability) {
                    badBytecode[random.nextInt(bytecode.length)] = 42;
                }
                makeRedefinition(2, myClass, badBytecode);
            }
        }
    }

    private void runIt() {
        myClass = new DefiningClassLoader().defineClass(generateAndCompile());
        stresser.start(0);

        // Generate some bytecode.
        bytecode = generateAndCompile();

        List<Thread> threads = new LinkedList<Thread>();
        var threadFactory = virtualThreads ? virtualThreadFactory() : platformThreadFactory();
        for (int i = 0; i < staticMethodCallersNumber; i++) {
            threads.add(threadFactory.newThread(new StaticMethodCaller()));
        }
        for (int i = 0; i < nonstaticMethodCallersNumber; i++) {
            threads.add(threadFactory.newThread(new NonstaticMethodCaller()));
        }
        for (int i = 0; i < redefiningThreadsNumber; i++) {
            threads.add(threadFactory.newThread(new Worker()));
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new TestFailure("Thread " + Thread.currentThread() + " was interrupted:", e);
            }
        }
    }

    private static ThreadFactory platformThreadFactory() {
        return task -> new Thread(task);
    }

    private static ThreadFactory virtualThreadFactory() {
        return Thread.ofVirtual().factory();
    }

    private static byte[] generateAndCompile() {
        return InMemoryJavaCompiler.compile(GenerateSourceHelper.CLASS_NAME, GenerateSourceHelper.generateSource());
    }

    // Auxiliary classloader. Used only once at the beginning.
    private static class DefiningClassLoader extends URLClassLoader {
        public DefiningClassLoader() {
            super(new URL[0]);
        }

        Class<?> defineClass(byte[] bytecode) {
            return defineClass(null, bytecode, 0, bytecode.length);
        }
    }
}
