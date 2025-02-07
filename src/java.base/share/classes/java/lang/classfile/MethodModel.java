/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.Optional;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.BufferedMethodBuilder;
import jdk.internal.classfile.impl.MethodImpl;
import jdk.internal.classfile.impl.Util;

/**
 * Models a method.  A method can be viewed as a {@linkplain CompoundElement
 * composition} of {@link MethodElement}s, or by random access via accessor
 * methods if only specific parts of the method is needed.
 * <p>
 * Methods can be obtained from {@link ClassModel#methods()}, or in the
 * traversal of member elements of a class.
 * <p>
 * {@link ClassBuilder#withMethod(String, MethodTypeDesc, int, Consumer)} is the
 * main way to construct methods.  {@link ClassBuilder#transformMethod} allows
 * creating a new method by selectively processing the original method elements
 * and directing the results to a method builder.
 * <p>
 * All method attributes are accessible as member elements.
 *
 * @see ClassModel#methods()
 * @see MethodTransform
 * @jvms 4.6 Methods
 * @since 24
 */
public sealed interface MethodModel
        extends CompoundElement<MethodElement>, AttributedElement, ClassElement
        permits BufferedMethodBuilder.Model, MethodImpl {

    /**
     * {@return the access flags}
     *
     * @see AccessFlag.Location#METHOD
     */
    AccessFlags flags();

    /** {@return the class model this method is a member of, if known} */
    Optional<ClassModel> parent();

    /** {@return the name of this method} */
    Utf8Entry methodName();

    /** {@return the method descriptor string of this method} */
    Utf8Entry methodType();

    /** {@return the method type, as a symbolic descriptor} */
    default MethodTypeDesc methodTypeSymbol() {
        return Util.methodTypeSymbol(methodType());
    }

    /** {@return the body of this method, if there is one} */
    Optional<CodeModel> code();
}
