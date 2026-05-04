/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test
 * @bug 8277444
 *
 * @library /test/lib
 * @compile SimpleIdentityTransformer.java
 * @run shell MakeJAR.sh retransformAgent
 * @run main/othervm -javaagent:retransformAgent.jar RetransformBigClassTest
 */

import jdk.test.lib.compiler.InMemoryJavaCompiler;

/*
 * JvmtiClassFileReconstituter::copy_bytecodes restores bytecodes rewritten
 * by the linking process. It is used by RetransformClasses.
 * JDK-8277444 is a data race between copy_bytecodes and the linking process.
 * This test puts the linking process in one thread and the retransforming process
 * in another thread. The test uses Class.forName("BigClass", false, classLoader)
 * which does not link the class. When the class is used, the linking process starts.
 * In another thread retransforming of the class is happening.
 * We generate a class with big methods. A number of methods and their size are
 * chosen to make the linking and retransforming processes run concurrently.
 * We delay the retransforming process to follow the linking process.
 * If there is no synchronization between the processes, a data race will happen.
 */
public class RetransformBigClassTest extends AInstrumentationTestCase {

    private static final Object LOCK = new Object();
    private static final int COUNTER_INC_COUNT            = 2000; // A number of 'c+=1;' statements in methods of a class.
    private static final int MIN_LINK_TIME_MS             = 60;   // Large enough so the linking and retransforming processes run in parallel.
    private static final int RETRANSFORM_CLASSES_DELAY_MS = 37;   // We manage to create a data race when a delay is in the range 0.52x - 0.62x of MIN_LINK_TIME_MS.

    private static Class<?> bigClass;
    private static byte[] bigClassBytecode;

    private Thread retransformThread;

    RetransformBigClassTest() {
        super("RetransformBigClassTest");
    }

    public static void main(String[] args) throws Throwable {
        new RetransformBigClassTest().runTest();
    }

    protected final void doRunTest() throws Throwable {
        ClassLoader classLoader = new ClassLoader() {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (name.equals("BigClass")) {
                        return defineClass(name, bigClassBytecode, 0, bigClassBytecode.length);
                    }

                    return super.findClass(name);
                }
        };
        synchronized (LOCK) {
            bigClass = Class.forName("BigClass", false, classLoader);
            LOCK.notify();
        }
        // Make a use of the BigClass
        assertTrue(bigClass.getConstructor().newInstance().hashCode() != 0);
        retransformThread.join();
    }

    private byte[] createClassBytecode(String className, int methodCount) throws Exception {
        String methodBody = "";
        for (int j = 0; j < COUNTER_INC_COUNT; j++) {
            methodBody += "c+=1;";
        }

        String classSrc = "public class " + className + " { int c;";

        for (int i = 0; i < methodCount; i++) {
            classSrc += "\npublic void m" + i + "(){";
            classSrc += methodBody;
            classSrc += "\n}";
        }
        classSrc += "\n}";

        return InMemoryJavaCompiler.compile(className, classSrc);
    }

    // We need a number of methods such that the linking time is greater than
    // or equal to MIN_LINK_TIME_MS.
    // We create a class having 5 methods and trigger the linking process.
    // We measure the time taken and use it to calculate the needed number.
    private int findMethodCount() throws Exception {
        int methodCount = 5;
        final String className = "BigClass" + methodCount;
        final byte[] bytecode = createClassBytecode(className, methodCount);
        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (name.equals(className)) {
                    return defineClass(name, bytecode, 0, bytecode.length);
                }

                return super.findClass(name);
            }
        };
        var bigClass = Class.forName(className, false, classLoader);
        long startTime = System.nanoTime();
        assertTrue(bigClass.getConstructor().newInstance().hashCode() != 0);
        double linkTimeMs = (System.nanoTime() - startTime) / 1000000.0;
        System.out.println("Link time for a class with " + methodCount + " methods each having " + COUNTER_INC_COUNT + " counter increments: " + Math.round(linkTimeMs));
        if (linkTimeMs < MIN_LINK_TIME_MS) {
          methodCount = (int)Math.round((MIN_LINK_TIME_MS * methodCount) / linkTimeMs);
        }
        System.out.println("The number of methods to exceed " + MIN_LINK_TIME_MS + " ms linking time: " + methodCount);
        return methodCount;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        bigClassBytecode = createClassBytecode("BigClass", findMethodCount());
        fInst.addTransformer(new SimpleIdentityTransformer());
        retransformThread = new Thread(() -> {
            try {
                synchronized (LOCK) {
                    while (bigClass == null) {
                        System.out.println("[retransformThread]: Waiting for bigClass");
                        LOCK.wait();
                    }
                }
                Thread.sleep(RETRANSFORM_CLASSES_DELAY_MS);
                fInst.retransformClasses(bigClass);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        retransformThread.start();
        Thread.sleep(100);
    }
}
