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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import java.util.*;
import java.util.function.*;
import java.io.BufferedInputStream;
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
                this.getClass().getSimpleName() + "$Test1$1.class"), 2, typeAnnosInfoTest1);

        checkClassFile(Paths.get(System.getProperty("test.classes"),
                this.getClass().getSimpleName() + "$Test2$1.class"), 0, typeAnnosInfoTest2);

        checkClassFile(Paths.get(System.getProperty("test.classes"),
                this.getClass().getSimpleName() + "$Test3$1.class"), 0, typeAnnosInfoTest3);
    }

    record DeclAnnoData(String attributeName, String annoName, int positionOfAnnotatedParam) {}

    void checkClassFile(final Path cfilePath, int paramAnnoPos, String... expectedTypeAnnosInfo) throws Throwable {
        ClassFile classFile = ClassFile.read(
                new BufferedInputStream(Files.newInputStream(cfilePath)));
        for (Method method : classFile.methods) {
            if (method.getName(classFile.constant_pool).equals("<init>")) {
                checkForDeclAnnos(classFile,
                        method.attributes,
                        "Annotations hasn't been propagated",
                        new DeclAnnoData(Attribute.RuntimeInvisibleAnnotations, "LAnnosNotCopiedToAnonymousCtrTest$CtrAnnotation;", -1),
                        new DeclAnnoData(Attribute.RuntimeInvisibleParameterAnnotations, "LAnnosNotCopiedToAnonymousCtrTest$ParamAnnotation;", paramAnnoPos) );

                checkForTypeAnnos(method.attributes, expectedTypeAnnosInfo);
            }
        }
    }

    void checkForDeclAnnos(ClassFile classFile, Attributes attrs, String errorMsg, DeclAnnoData... attrAndParamPos)
            throws Throwable {
        for (DeclAnnoData attrAndPos : attrAndParamPos) {
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

    void checkForTypeAnnos(Attributes attrs, String... expectedInfo)
            throws Throwable {
        RuntimeTypeAnnotations_attribute typeAnnosAttr =
                (RuntimeTypeAnnotations_attribute) attrs.get(Attribute.RuntimeInvisibleTypeAnnotations);
        TypeAnnotation[] annos = typeAnnosAttr.annotations;
        Assert.check(annos.length == expectedInfo.length);
        for (int i = 0; i < expectedInfo.length; i++) {
            Assert.check(annos[i].toString().equals(expectedInfo[i]));
        }
    }

    String[] typeAnnosInfoTest1 = new String[] {
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnnoForTypeParams; pos: [METHOD_TYPE_PARAMETER, param_index = 0, pos = -1]",
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnnoForTypeParams2; pos: [METHOD_TYPE_PARAMETER, param_index = 1, pos = -1]",
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnno; pos: [METHOD_RETURN, location = ([INNER_TYPE, INNER_TYPE]), pos = -1]",
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnno; pos: [METHOD_FORMAL_PARAMETER, param_index = 1, pos = -1]"
    };

    String[] typeAnnosInfoTest2 = new String[] {
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnnoForTypeParams; pos: [METHOD_TYPE_PARAMETER, param_index = 0, pos = -1]",
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnno; pos: [METHOD_RETURN, location = ([INNER_TYPE, INNER_TYPE]), pos = -1]",
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnno; pos: [METHOD_FORMAL_PARAMETER, param_index = 1, pos = -1]"
    };

    String[] typeAnnosInfoTest3 = new String[] {
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnnoForTypeParams; pos: [METHOD_TYPE_PARAMETER, param_index = 0, pos = -1]",
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnno; pos: [METHOD_RETURN, location = ([INNER_TYPE, INNER_TYPE]), pos = -1]",
            "@AnnosNotCopiedToAnonymousCtrTest$TypeAnno; pos: [METHOD_FORMAL_PARAMETER, param_index = 1, pos = -1]"
    };

    @Target(value = {ElementType.PARAMETER})
    @interface ParamAnnotation {}

    @Target(value = {ElementType.CONSTRUCTOR})
    @interface CtrAnnotation {}

    @Target({ElementType.TYPE_USE})
    @interface TypeAnno {}

    @Target({ElementType.TYPE_PARAMETER})
    @interface TypeAnnoForTypeParams {}

    @Target({ElementType.TYPE_PARAMETER})
    @interface TypeAnnoForTypeParams2 {}

    class Test1 {
        @CtrAnnotation
        @TypeAnno
        <@TypeAnnoForTypeParams T, @TypeAnnoForTypeParams2 X> Test1(String firstParam, @TypeAnno String secondParam, @ParamAnnotation String thirdParam) {}

        public void m() {
            // let's create an anonymous inner class
            new Test1("", "", "") {};
        }
    }

    // ----
    class Test2 {
        @TypeAnno
        @CtrAnnotation
        <@TypeAnnoForTypeParams T>
        Test2(@ParamAnnotation Collection<? extends T> collection, @TypeAnno int characteristics) {}

        void m() {
            new Test2(null, 1) {};
        }
    }

    class Test3 {
        @TypeAnno
        @CtrAnnotation
        <@TypeAnnoForTypeParams T>
        Test3(@ParamAnnotation Collection<? extends T>[] collection, @TypeAnno int characteristics) {}

        void m() {
            new Test3(null, 1) {};
        }
    }
}
