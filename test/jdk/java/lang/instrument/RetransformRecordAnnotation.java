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
 * @bug 8315575
 * @summary test that records with invisible annotation can be retransformed
 *
 * @library /test/lib
 * @run shell MakeJAR.sh retransformAgent
 * @run main/othervm -javaagent:retransformAgent.jar -Xlog:redefine+class=trace RetransformRecordAnnotation
 */

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.security.ProtectionDomain;

public class RetransformRecordAnnotation extends AInstrumentationTestCase {

    // RetentionPolicy.CLASS by default
    @interface MyAnnotation{}
    public record MyRecord(@MyAnnotation Object o, Object other) {}

    public static void main (String[] args) throws Throwable {
        ATestCaseScaffold test = new RetransformRecordAnnotation();
        test.beVerbose();
        test.runTest();
    }

    private String targetClassName = "RetransformRecordAnnotation$MyRecord";
    private String classFileName = targetClassName + ".class";
    private Class targetClass;
    private byte[] originalClassBytes;

    private byte[] seenClassBytes;
    private byte[] newClassBytes;

    public RetransformRecordAnnotation() throws Throwable {
        super("RetransformRecordAnnotation");

        File origClassFile = new File(System.getProperty("test.classes", "."), classFileName);
        log("Reading test class from " + origClassFile);
        originalClassBytes = Files.readAllBytes(origClassFile.toPath());
        log("Read " + originalClassBytes.length + " bytes.");
    }

    private void log(Object o) {
        System.out.println(String.valueOf(o));
    }

    // Retransforms target class using provided class bytes;
    // Returns class bytes passed to the transformer.
    private byte[] retransform(byte[] classBytes) throws Throwable {
        seenClassBytes = null;
        newClassBytes = classBytes;
        fInst.retransformClasses(targetClass);
        assertTrue(targetClassName + " was not seen by transform()", seenClassBytes != null);
        return seenClassBytes;
    }

    protected final void doRunTest() throws Throwable {
        ClassLoader loader = getClass().getClassLoader();
        targetClass = loader.loadClass(targetClassName);

        fInst.addTransformer(new Transformer(), true);

        {
            log("Sanity: retransform to original class bytes");
            retransform(originalClassBytes);
            log("");
        }

        {
            log("Test: retransform to null");
            // Ensure retransformation does not fail with ClassFormatError.
            retransform(null);
            log("");
        }
    }


    public class Transformer implements ClassFileTransformer {
        public Transformer() {
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
