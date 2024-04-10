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

/*
 * @test
 * @bug 8315575 8328137
 * @summary test that records with invisible annotation can be retransformed
 *
 * @library /test/lib
 * @run shell MakeJAR.sh retransformAgent
 * @run main/othervm -javaagent:retransformAgent.jar -Xlog:redefine+class=trace RetransformRecordAnnotation
 * @run main/othervm -javaagent:retransformAgent.jar -XX:+PreserveAllAnnotations -Xlog:redefine+class=trace RetransformRecordAnnotation
 */

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.security.ProtectionDomain;

public class RetransformRecordAnnotation extends AInstrumentationTestCase {

    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface RuntimeTypeAnno {}

    @Retention(RetentionPolicy.RUNTIME)
    @interface RuntimeParamAnno {
        String s() default "foo";
    }

    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.CLASS)
    @interface ClassTypeAnno {}

    @Retention(RetentionPolicy.CLASS)
    @interface ClassParamAnno {
        String s() default "bar";
    }

    @RuntimeTypeAnno
    @RuntimeParamAnno(s = "1")
    public record VisibleAnnos(@RuntimeTypeAnno @RuntimeParamAnno(s = "2") Object o, Object other) {
    }

    @ClassTypeAnno
    @ClassParamAnno(s = "3")
    public record InvisibleAnnos(@ClassTypeAnno @ClassParamAnno(s = "4") Object o, Object other) {
    }

    @RuntimeTypeAnno
    @RuntimeParamAnno(s = "5")
    @ClassTypeAnno
    @ClassParamAnno(s = "6")
    public record MixedAnnos(@RuntimeTypeAnno @RuntimeParamAnno(s = "7")
                             @ClassTypeAnno @ClassParamAnno(s = "8") Object o, Object other) {
    }

    public static void main (String[] args) throws Throwable {
        ATestCaseScaffold test = new RetransformRecordAnnotation();
        test.beVerbose();
        test.runTest();
    }

    private Transformer transformer;

    public RetransformRecordAnnotation() throws Throwable {
        super("RetransformRecordAnnotation");
    }

    private void log(Object o) {
        System.out.println(String.valueOf(o));
    }

    // Retransforms target class using provided class bytes;
    private void retransform(Class targetClass, byte[] classBytes) throws Throwable {
        transformer.prepare(targetClass, classBytes);
        fInst.retransformClasses(targetClass);
        assertTrue(targetClass.getName() + " was not seen by transform()",
                   transformer.getSeenClassBytes() != null);
    }

    protected final void doRunTest() throws Throwable {
        transformer = new Transformer();
        fInst.addTransformer(transformer, true);

        {
            log("Sanity: retransform to original class bytes");
            retransform(InvisibleAnnos.class, loadClassBytes(InvisibleAnnos.class));
            log("");
        }

        // The following testcases use null as new class bytes (i.e. no transform is performed).
        // However, it is enough for testing purposes as the JvmtiClassFileReconstituter is still involved
        // in preparation of the initial class bytes.
        {
            log("Test: retransform VisibleAnnos to null");
            retransform(VisibleAnnos.class, null);
            log("");
        }

        {
            log("Test: retransform InvisibleAnnos to null");
            retransform(InvisibleAnnos.class, null);
            log("");
        }

        {
            log("Test: retransform MixedAnnos to null");
            retransform(MixedAnnos.class, null);
            log("");
        }
    }

    private byte[] loadClassBytes(Class cls) throws Exception {
        String classFileName = cls.getName() + ".class";
        File classFile = new File(System.getProperty("test.classes", "."), classFileName);
        log("Reading test class from " + classFile);
        byte[] classBytes = Files.readAllBytes(classFile.toPath());
        log("Read " + classBytes.length + " bytes.");
        return classBytes;
    }

    public class Transformer implements ClassFileTransformer {
        private String targetClassName;
        private byte[] seenClassBytes;
        private byte[] newClassBytes;

        public Transformer() {
        }

        // Prepares transformer for Instrumentation.retransformClasses.
        public void prepare(Class targetClass, byte[] classBytes) {
            targetClassName = targetClass.getName();
            newClassBytes = classBytes;
            seenClassBytes = null;
        }

        byte[] getSeenClassBytes() {
            return seenClassBytes;
        }

        public String toString() {
            return Transformer.this.getClass().getName();
        }

        public byte[] transform(ClassLoader loader, String className,
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

            if (className.equals(targetClassName)) {
                log(this + ".transform() sees '" + className
                        + "' of " + classfileBuffer.length + " bytes.");
                seenClassBytes = classfileBuffer;
                if (newClassBytes != null) {
                    log(this + ".transform() sets new classbytes for '" + className
                            + "' of " + newClassBytes.length + " bytes.");
                }
                return newClassBytes;
            }

            return null;
        }
    }
}
