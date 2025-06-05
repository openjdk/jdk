/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile ExperimentalTransformExamples compilation.
 * @compile ExperimentalTransformExamples.java
 */
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import java.lang.classfile.*;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;

/**
 * ExperimentalTransformExamples
 *
 */
public class ExperimentalTransformExamples {
    private static final FileSystem JRT = FileSystems.getFileSystem(URI.create("jrt:/"));

    static MethodTransform dropMethodAnnos = (mb, me) -> {
        if (!(me instanceof RuntimeVisibleAnnotationsAttribute || me instanceof RuntimeInvisibleAnnotationsAttribute))
            mb.with(me);
    };

    static FieldTransform dropFieldAnnos = (fb, fe) -> {
        if (!(fe instanceof RuntimeVisibleAnnotationsAttribute || fe instanceof RuntimeInvisibleAnnotationsAttribute))
            fb.with(fe);
    };

    public byte[] deleteAnnotations(ClassModel cm) {
        return ClassFile.of().transformClass(cm, (cb, ce) -> {
            switch (ce) {
                case MethodModel m -> cb.transformMethod(m, dropMethodAnnos);
                case FieldModel f -> cb.transformField(f, dropFieldAnnos);
                default -> cb.with(ce);
            }
        });
    }
}