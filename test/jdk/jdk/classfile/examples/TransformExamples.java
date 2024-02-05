/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile TransformExamples compilation.
 * @compile TransformExamples.java
 */
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Attribute;

/**
 * TransformExamples
 */
public class TransformExamples {
    public byte[] noop(ClassModel cm) {
        return ClassFile.of().transform(cm, ClassTransform.ACCEPT_ALL);
    }

    public byte[] deleteAllMethods(ClassModel cm) {
        return ClassFile.of().transform(cm, (b, e) -> {
            if (!(e instanceof MethodModel))
                b.with(e);
        });
    }

    public byte[] deleteFieldsWithDollarInName(ClassModel cm) {
        return ClassFile.of().transform(cm, (b, e) ->
                        {
                            if (!(e instanceof FieldModel fm && fm.fieldName().stringValue().contains("$")))
                                b.with(e);
                        });
    }

    public byte[] deleteAttributes(ClassModel cm) {
        return ClassFile.of().transform(cm, (b, e) -> {
            if (!(e instanceof Attribute))
                b.with(e);
        });
    }

    public byte[] keepMethodsAndFields(ClassModel cm) {
        return ClassFile.of().transform(cm, (b, e) -> {
            if (e instanceof MethodModel || e instanceof FieldModel)
                b.with(e);
        });
    }
}
