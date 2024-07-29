/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.javaagent;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.constant.ClassDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;

import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

/*
 * @test
 * @summary Verify that a subclass of the JFR Event class
 *          can be successfully instrumented.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jartool/sun.tools.jar
 * @enablePreview
 * @build jdk.jfr.javaagent.InstrumentationEventCallback
 *        jdk.jfr.javaagent.TestEventInstrumentation
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *             jdk.jfr.javaagent.TestEventInstrumentation TestEventInstrumentation.jar
 *             Can-Redefine-Classes:true Can-Retransform-Classes:true
 * @run main/othervm -javaagent:TestEventInstrumentation.jar
 *      jdk.jfr.javaagent.TestEventInstrumentation
 */
public class TestEventInstrumentation {
    private static Instrumentation instrumentation = null;
    private static TestEventInstrumentation testTransformer = null;
    private static Exception transformException = null;

    public static class TestEvent extends Event {
    }

    public static void main(String[] args) throws Throwable {
        // loads test event class, run empty constructor w/o instrumentation
        TestEvent event = new TestEvent();

        // add instrumentation and test an instrumented constructor
        instrumentation.addTransformer(new Transformer(), true);
        instrumentation.retransformClasses(TestEvent.class);
        event = new TestEvent();
        Asserts.assertTrue(InstrumentationEventCallback.wasCalled());

        // record test event with instrumented constructor, verify it is recorded
        InstrumentationEventCallback.clear();
        try (Recording r = new Recording()) {
            r.enable(TestEvent.class);
            r.start();
            new TestEvent().commit();
            Asserts.assertTrue(InstrumentationEventCallback.wasCalled());
            Path rf = Paths.get("", "recording.jfr");
            r.dump(rf);
            Asserts.assertFalse(RecordingFile.readAllEvents(rf).isEmpty());
        }
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    // ======================== Java agent used to transform classes
    public static void premain(String args, Instrumentation inst) throws Exception {
        instrumentation = inst;
    }

    static class Transformer implements ClassFileTransformer {
        private static final ClassDesc CD_InstrumentationEventCallback = InstrumentationEventCallback.class
                .describeConstable().orElseThrow();

        public byte[] transform(ClassLoader classLoader, String className,
                                Class<?> classBeingRedefined, ProtectionDomain pd,
                                byte[] bytes) throws IllegalClassFormatException {
            byte[] result = null;
            try {
                // Check if this class should be instrumented.
                if (!className.contains("TestEventInstrumentation$TestEvent")) {
                    return null;
                }

                var cf = ClassFile.of();
                result = cf.transformClass(cf.parse(bytes), (clb, ce) -> {
                    if (ce instanceof MethodModel mm && mm.methodName().equalsString(INIT_NAME)) {
                        clb.transformMethod(mm, MethodTransform.transformingCode(new CodeTransform() {
                            @Override
                            public void atStart(CodeBuilder cb) {
                                cb.invokestatic(CD_InstrumentationEventCallback, "callback", MTD_void);
                                log("instrumented <init> in class " + className);
                            }

                            @Override
                            public void accept(CodeBuilder cb, CodeElement ce) {
                                cb.accept(ce);
                            }
                        }));
                    } else {
                        clb.with(ce);
                    }
                });
            } catch (Exception e) {
                log("Exception occured in transform(): " + e.getMessage());
                e.printStackTrace(System.out);
                transformException = e;
            }
            return result;
        }
    }
}
