/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8274617
 * @summary constructor and parameter annotations are not copied to the anonymous class constructor
 * @modules jdk.jdeps/com.sun.tools.classfile
 *          jdk.compiler/com.sun.tools.javac.util
 * @run main AnnosNotCopiedToAnonymousCtrTest
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.io.BufferedInputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.sun.tools.classfile.*;
import com.sun.tools.javac.util.Assert;

public class AnnosNotCopiedToAnonymousCtrTest {

    public static void main(String[] args) throws Throwable {
        new AnnosNotCopiedToAnonymousCtrTest().run();
    }

    void run() throws Throwable {
        checkClassFile(Paths.get(System.getProperty("test.classes"),
                this.getClass().getSimpleName() + "$Test$1.class"));
    }

    record AnnoData(String attributeName, String annoName, int positionOfAnnotatedParam) {}

    void checkClassFile(final Path cfilePath) throws Throwable {
        ClassFile classFile = ClassFile.read(
                new BufferedInputStream(Files.newInputStream(cfilePath)));
        for (Method method : classFile.methods) {
            if (method.getName(classFile.constant_pool).equals("<init>")) {
                checkForAttr(classFile,
                        method.attributes,
                        "Annotations hasn't been propagated",
                        new AnnoData(Attribute.RuntimeVisibleAnnotations, "LAnnosNotCopiedToAnonymousCtrTest$VisibleCtrAnnotation;", -1),
                        new AnnoData(Attribute.RuntimeInvisibleAnnotations, "LAnnosNotCopiedToAnonymousCtrTest$InvisibleCtrAnnotation;", -1),
                        new AnnoData(Attribute.RuntimeVisibleParameterAnnotations, "LAnnosNotCopiedToAnonymousCtrTest$VisibleParamAnnotation;", 1),
                        new AnnoData(Attribute.RuntimeInvisibleParameterAnnotations, "LAnnosNotCopiedToAnonymousCtrTest$InvisibleParamAnnotation;", 2) );
            }
        }
    }

    void checkForAttr(ClassFile classFile, Attributes attrs, String errorMsg, AnnoData... attrAndParamPos)
            throws Throwable {
        for (AnnoData attrAndPos : attrAndParamPos) {
            Assert.checkNonNull(attrs.get(attrAndPos.attributeName()), errorMsg);
            boolean isParamAnno = attrs.get(attrAndPos.attributeName()) instanceof RuntimeParameterAnnotations_attribute;
            if (isParamAnno) {
                RuntimeParameterAnnotations_attribute paramAnnotation =
                        (RuntimeParameterAnnotations_attribute)attrs.get(attrAndPos.attributeName());
                for (int i = 0; i < paramAnnotation.parameter_annotations.length; i++) {
                    Annotation[] annos = paramAnnotation.parameter_annotations[i];
                    if (i != attrAndPos.positionOfAnnotatedParam()) {
                        Assert.check(annos.length == 0);
                    } else {
                        Assert.check(annos.length == 1);
                        Assert.check(classFile.constant_pool.getUTF8Value(annos[0].type_index).equals(attrAndPos.annoName()));
                    }
                }
            } else {
                RuntimeAnnotations_attribute ctrAnnotation = (RuntimeAnnotations_attribute)attrs.get(attrAndPos.attributeName());
                Assert.check(ctrAnnotation.annotations.length == 1);
                Assert.check(classFile.constant_pool.getUTF8Value(ctrAnnotation.annotations[0].type_index).equals(attrAndPos.annoName()));
            }
        }
    }

    @Target(value = {ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @interface VisibleParamAnnotation {}

    @Target(value = {ElementType.PARAMETER})
    @interface InvisibleParamAnnotation {}

    @Target(value = {ElementType.CONSTRUCTOR})
    @Retention(RetentionPolicy.RUNTIME)
    @interface VisibleCtrAnnotation {}

    @Target(value = {ElementType.CONSTRUCTOR})
    @interface InvisibleCtrAnnotation {}

    public class Test {
        @VisibleCtrAnnotation
        @InvisibleCtrAnnotation
        public Test(String firstParam, @VisibleParamAnnotation String secondParam, @InvisibleParamAnnotation String thirdParam) {}

        public void m() {
            // let's create an anonymous inner class
            new Test("", "", "") {};
        }
    }
}
