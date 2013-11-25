/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8022595
 * @summary JSR292: deadlock during class loading of MethodHandles, MethodHandleImpl & MethodHandleNatives
 *
 * @run main/othervm ConcurrentClassLoadingTest
 */
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class ConcurrentClassLoadingTest {
    int numThreads = 0;
    long seed = 0;
    CyclicBarrier l;
    Random rand;

    public static void main(String[] args) throws Throwable {
        ConcurrentClassLoadingTest test = new ConcurrentClassLoadingTest();
        test.parseArgs(args);
        test.run();
    }

    void parseArgs(String[] args) {
        int i = 0;
        while (i < args.length) {
            String flag = args[i];
            switch(flag) {
                case "-seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "-numThreads":
                    numThreads = Integer.parseInt(args[++i]);
                    break;
                default:
                    throw new Error("Unknown flag: " + flag);
            }
            ++i;
        }
    }

    void init() {
        if (numThreads == 0) {
            numThreads = Runtime.getRuntime().availableProcessors();
        }

        if (seed == 0) {
            seed = (new Random()).nextLong();
        }
        rand = new Random(seed);

        l = new CyclicBarrier(numThreads + 1);

        System.out.printf("Threads: %d\n", numThreads);
        System.out.printf("Seed: %d\n", seed);
    }

    final List<Loader> loaders = new ArrayList<>();

    void prepare() {
        List<String> c = new ArrayList<>(Arrays.asList(classNames));

        // Split classes between loading threads
        int count = (classNames.length / numThreads) + 1;
        for (int t = 0; t < numThreads; t++) {
            List<String> sel = new ArrayList<>();

            System.out.printf("Thread #%d:\n", t);
            for (int i = 0; i < count; i++) {
                if (c.size() == 0) break;

                int k = rand.nextInt(c.size());
                String elem = c.remove(k);
                sel.add(elem);
                System.out.printf("\t%s\n", elem);
            }
            loaders.add(new Loader(sel));
        }

        // Print diagnostic info when the test hangs
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                boolean alive = false;
                for (Loader l : loaders) {
                    if (!l.isAlive())  continue;

                    if (!alive) {
                        System.out.println("Some threads are still alive:");
                        alive = true;
                    }

                    System.out.println(l.getName());
                    for (StackTraceElement elem : l.getStackTrace()) {
                        System.out.println("\t"+elem.toString());
                    }
                }
            }
        });
    }

    public void run() throws Throwable {
        init();
        prepare();

        for (Loader loader : loaders) {
            loader.start();
        }

        l.await();

        for (Loader loader : loaders) {
            loader.join();
        }
    }

    class Loader extends Thread {
        List<String> classes;

        public Loader(List<String> classes) {
            this.classes = classes;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                l.await();

                for (String name : classes) {
                    Class.forName(name).getName();
                }
            } catch (ClassNotFoundException | BrokenBarrierException | InterruptedException e) {
                throw new Error(e);
            }
        }
    }

    final static String[] classNames = {
            "java.lang.invoke.AbstractValidatingLambdaMetafactory",
            "java.lang.invoke.BoundMethodHandle",
            "java.lang.invoke.CallSite",
            "java.lang.invoke.ConstantCallSite",
            "java.lang.invoke.DirectMethodHandle",
            "java.lang.invoke.InnerClassLambdaMetafactory",
            "java.lang.invoke.InvokeDynamic",
            "java.lang.invoke.InvokeGeneric",
            "java.lang.invoke.InvokerBytecodeGenerator",
            "java.lang.invoke.Invokers",
            "java.lang.invoke.LambdaConversionException",
            "java.lang.invoke.LambdaForm",
            "java.lang.invoke.LambdaMetafactory",
            "java.lang.invoke.MemberName",
            "java.lang.invoke.MethodHandle",
            "java.lang.invoke.MethodHandleImpl",
            "java.lang.invoke.MethodHandleInfo",
            "java.lang.invoke.MethodHandleNatives",
            "java.lang.invoke.MethodHandleProxies",
            "java.lang.invoke.MethodHandles",
            "java.lang.invoke.MethodHandleStatics",
            "java.lang.invoke.MethodType",
            "java.lang.invoke.MethodTypeForm",
            "java.lang.invoke.MutableCallSite",
            "java.lang.invoke.SerializedLambda",
            "java.lang.invoke.SimpleMethodHandle",
            "java.lang.invoke.SwitchPoint",
            "java.lang.invoke.TypeConvertingMethodAdapter",
            "java.lang.invoke.VolatileCallSite",
            "java.lang.invoke.WrongMethodTypeException"
    };
}
