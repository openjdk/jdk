/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test for com.sun.tools.javac.comp.Check::validateAnnotation, com.sun.tools.javac.code.SymbolMetadata::removeDeclarationMetadata and ::removeFromCompoundList
 * @bug 8241312 8246774
 * @library /tools/lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile.impl
 * @run main ApplicableAnnotationsOnRecords
 */
import java.lang.classfile.*;
import com.sun.tools.javac.util.Assert;
import java.lang.annotation.*;
import java.io.InputStream;
import java.util.Objects;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@interface FieldAnnotation {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@interface MethodAnnotation {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
@interface ParameterAnnotation {
}

public record ApplicableAnnotationsOnRecords(@FieldAnnotation @MethodAnnotation @ParameterAnnotation String s, @FieldAnnotation @MethodAnnotation @ParameterAnnotation int i) {

    public static void main(String... args) throws Exception {
        try ( InputStream in = ApplicableAnnotationsOnRecords.class.getResourceAsStream("ApplicableAnnotationsOnRecords.class")) {
            ClassModel cm = ClassFile.of().parse(Objects.requireNonNull(in).readAllBytes());
            Assert.check(cm.methods().size() > 5);
            for (MethodModel mm : cm.methods()) {
                String methodName = mm.methodName().stringValue();
                if (methodName.equals("toString") || methodName.equals("hashCode") || methodName.equals("equals") || methodName.equals("main")) {
                    // ignore
                } else if (methodName.equals("<init>")) {
                    var paAnnos = mm.findAttribute(Attributes.RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS).orElseThrow().parameterAnnotations();
                    Assert.check(paAnnos.size() > 0);
                    for (var pa : paAnnos) {
                        Assert.check(pa.size() == 1);
                        Assert.check(Objects.equals(pa.get(0).classSymbol().descriptorString(), "LParameterAnnotation;"));
                    }
                } else {
                    var annos = mm.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).orElseThrow().annotations();
                    Assert.check(annos.size() == 1);
                    Assert.check(Objects.equals(annos.get(0).classSymbol().descriptorString(), "LMethodAnnotation;"));
                }
            }
            Assert.check(cm.fields().size() > 0);
            for (FieldModel fm : cm.fields()) {
                var annos = fm.findAttribute(Attributes.RUNTIME_VISIBLE_ANNOTATIONS).orElseThrow().annotations();
                Assert.check(annos.size() == 1);
                Assert.check(Objects.equals(annos.getFirst().classSymbol().descriptorString(), "LFieldAnnotation;"));
            }
        }
    }
}
