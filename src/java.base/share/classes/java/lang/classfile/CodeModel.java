/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Optional;

import java.lang.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.impl.BufferedCodeBuilder;
import jdk.internal.classfile.impl.CodeImpl;
import java.lang.classfile.instruction.ExceptionCatch;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the body of a method (the {@code Code} attribute).  The instructions
 * of the method body are accessed via a streaming view.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface CodeModel
        extends CompoundElement<CodeElement>, AttributedElement, MethodElement
        permits CodeAttribute, BufferedCodeBuilder.Model {

    /**
     * {@return the enclosing method, if known}
     */
    Optional<MethodModel> parent();

    /**
     * {@return the exception table of the method}  The exception table is also
     * modeled by {@link ExceptionCatch} elements in the streaming view.
     */
    List<ExceptionCatch> exceptionHandlers();
}
