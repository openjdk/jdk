/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import jdk.test.lib.thread.VThreadPinner;

/*
 * @test
 * @summary javaagent + tracePinnedThreads will cause jvm crash/ run into deadlock when the virtual thread is pinned
 * @library /test/lib
 * @requires vm.continuations
 * @requires vm.jvmti
 * @modules java.base/java.lang:+open
 * @compile TestPinCaseWithCFLH.java
 * @build jdk.test.lib.Utils
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *             TestPinCaseWithCFLH TestPinCaseWithCFLH.jar
 * @run main/othervm/timeout=100  -Djdk.virtualThreadScheduler.maxPoolSize=1
 *       -Djdk.tracePinnedThreads=full --enable-native-access=ALL-UNNAMED
 *       -javaagent:TestPinCaseWithCFLH.jar TestPinCaseWithCFLH
 */
public class TestPinCaseWithCFLH {

    public static class TestClassFileTransformer implements ClassFileTransformer {
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer)
                                throws IllegalClassFormatException {
            return classfileBuffer;
        }
    }

    // Called when agent is loaded at startup
    public static void premain(String agentArgs, Instrumentation instrumentation) throws Exception {
        instrumentation.addTransformer(new TestClassFileTransformer());
    }

    private static int result = 0;

    public static void main(String[] args) throws Exception{
        Thread t1 = Thread.ofVirtual().name("vthread-1").start(() -> {
            VThreadPinner.runPinned(() -> {
                try {
                    // try yield, will pin,
                    // javaagent + tracePinnedThreads should not lead to crash
                    // (because of the class `PinnedThreadPrinter`)
                    Thread.sleep(500);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
        t1.join();
    }

}