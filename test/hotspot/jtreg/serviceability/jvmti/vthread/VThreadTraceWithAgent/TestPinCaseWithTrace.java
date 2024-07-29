/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary javaagent+tracePinnedThreads will cause jvm crash/ run into deadlock when the virtual thread is pinned
 * @library /test/lib
 * @requires os.family == "linux"
 * @requires os.arch != "riscv64"
 * @modules java.base/java.lang:+open
 * @build TestPinCaseWithTrace
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *      TestPinCaseWithTrace TestPinCaseWithTrace.jar
 * @run main/othervm/timeout=100 -Djdk.tracePinnedThreads=full TestPinCaseWithTrace
 * @run main/othervm/timeout=100 -javaagent:TestPinCaseWithTrace.jar TestPinCaseWithTrace
 * @run main/othervm/timeout=100 -Djdk.tracePinnedThreads=full -javaagent:TestPinCaseWithTrace.jar TestPinCaseWithTrace
 */
import java.util.concurrent.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class TestPinCaseWithTrace {

    public static class TestClassFileTransformer implements ClassFileTransformer {
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            return classfileBuffer;
        }
    }

    // Called when agent is loaded at startup
    public static void premain(String agentArgs, Instrumentation instrumentation) throws Exception {
        instrumentation.addTransformer(new TestClassFileTransformer());
    }

    public static void main(String[] args) throws Exception{
        ExecutorService scheduler = Executors.newFixedThreadPool(1);
        Thread.Builder builder = TestPinCaseWithTrace.virtualThreadBuilder(scheduler);
        Thread t1 = builder.name("vthread-1").start(() -> {
            System.out.println("call native: " + nativeFuncPin(1));
        });
    }

    static int native2Java(int b) {
        try {
            Thread.sleep(500); // try yield, will pin, javaagent+tracePinnedThreads will crash here (because of the class `PinnedThreadPrinter`)
        } catch (Exception e) {
            e.printStackTrace();
        }

        return b+1;
    }

    private static native int nativeFuncPin(int x);

    static {
        System.loadLibrary("PinJNI");
    }

    private static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
            ctor.setAccessible(true);
            return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
