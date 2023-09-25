/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.classfile;

import jdk.internal.classfile.attribute.AnnotationDefaultAttribute;
import jdk.internal.classfile.attribute.DeprecatedAttribute;
import jdk.internal.classfile.attribute.ExceptionsAttribute;
import jdk.internal.classfile.attribute.MethodParametersAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import jdk.internal.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import jdk.internal.classfile.attribute.SignatureAttribute;
import jdk.internal.classfile.attribute.SyntheticAttribute;
import jdk.internal.classfile.attribute.UnknownAttribute;

/**
 * A {@link ClassfileElement} that can appear when traversing the elements
 * of a {@link MethodModel} or be presented to a {@link MethodBuilder}.
 */
public sealed interface MethodElement
        extends ClassfileElement
        permits AccessFlags, CodeModel, CustomAttribute,
                AnnotationDefaultAttribute, DeprecatedAttribute,
                ExceptionsAttribute, MethodParametersAttribute,
                RuntimeInvisibleAnnotationsAttribute, RuntimeInvisibleParameterAnnotationsAttribute,
                RuntimeInvisibleTypeAnnotationsAttribute, RuntimeVisibleAnnotationsAttribute,
                RuntimeVisibleParameterAnnotationsAttribute, RuntimeVisibleTypeAnnotationsAttribute,
                SignatureAttribute, SyntheticAttribute, UnknownAttribute {

}
