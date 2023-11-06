/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.tools.*;
import java.lang.reflect.AccessFlag;

import jdk.internal.classfile.Classfile;
import jdk.internal.classfile.attribute.ConstantValueAttribute;

/* Create an invalid classfile with a static final field of type object with
 * ConstantValue of type String.
 */
@SupportedAnnotationTypes("*")
public class CreateBadClassFile extends AbstractProcessor {
    public boolean process(Set<? extends TypeElement> elems, RoundEnvironment renv) {
        if (++round == 1) {
            byte[] bytes = Classfile.of().build(ClassDesc.of("Test"), cb -> {
                cb.withSuperclass(ConstantDescs.CD_Object);
                cb.withVersion(51, 0);
                cb.withFlags(AccessFlag.ABSTRACT ,
                        AccessFlag.INTERFACE ,
                        AccessFlag.PUBLIC);
                cb.withField("test", ConstantDescs.CD_Object, fieldBuilder -> {
                    fieldBuilder.withFlags(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL);
                    fieldBuilder.with(ConstantValueAttribute.of("ConstantValue"));
                });
            });
            try {
                JavaFileObject clazz = processingEnv.getFiler().createClassFile("Test");
                try (OutputStream out = clazz.openOutputStream()) {
                    out.write(bytes);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return false;
    }

    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    int round = 0;
}
