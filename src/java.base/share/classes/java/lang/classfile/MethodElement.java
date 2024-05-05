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
package java.lang.classfile;

import java.lang.classfile.attribute.AnnotationDefaultAttribute;
import java.lang.classfile.attribute.DeprecatedAttribute;
import java.lang.classfile.attribute.ExceptionsAttribute;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.classfile.attribute.SignatureAttribute;
import java.lang.classfile.attribute.SyntheticAttribute;
import java.lang.classfile.attribute.UnknownAttribute;
import jdk.internal.javac.PreviewFeature;

/**
 * A marker interface for elements that can appear when traversing
 * a {@link MethodModel} or be presented to a {@link MethodBuilder}.
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface MethodElement
        extends ClassFileElement
        permits AccessFlags, CodeModel, CustomAttribute,
                AnnotationDefaultAttribute, DeprecatedAttribute,
                ExceptionsAttribute, MethodParametersAttribute,
                RuntimeInvisibleAnnotationsAttribute, RuntimeInvisibleParameterAnnotationsAttribute,
                RuntimeInvisibleTypeAnnotationsAttribute, RuntimeVisibleAnnotationsAttribute,
                RuntimeVisibleParameterAnnotationsAttribute, RuntimeVisibleTypeAnnotationsAttribute,
                SignatureAttribute, SyntheticAttribute, UnknownAttribute {

}
