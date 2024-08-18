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

import java.util.function.Consumer;

import java.lang.classfile.constantpool.Utf8Entry;

import jdk.internal.classfile.impl.AccessFlagsImpl;
import jdk.internal.classfile.impl.ChainedMethodBuilder;
import jdk.internal.classfile.impl.TerminalMethodBuilder;
import java.lang.reflect.AccessFlag;
import jdk.internal.javac.PreviewFeature;

/**
 * A builder for methods.  Builders are not created directly; they are passed
 * to handlers by methods such as {@link ClassBuilder#withMethod(Utf8Entry, Utf8Entry, int, Consumer)}
 * or to method transforms.  The elements of a method can be specified
 * abstractly (by passing a {@link MethodElement} to {@link #with(ClassFileElement)}
 * or concretely by calling the various {@code withXxx} methods.
 *
 * @see MethodTransform
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface MethodBuilder
        extends ClassFileBuilder<MethodElement, MethodBuilder>
        permits ChainedMethodBuilder, TerminalMethodBuilder {

    /**
     * Sets the method access flags.
     * @param flags the access flags, as a bit mask
     * @return this builder
     */
    default MethodBuilder withFlags(int flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.METHOD, flags));
    }

    /**
     * Sets the method access flags.
     * @param flags the access flags, as a bit mask
     * @return this builder
     */
    default MethodBuilder withFlags(AccessFlag... flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.METHOD, flags));
    }

    /**
     * Build the method body for this method.
     * @param code a handler receiving a {@link CodeBuilder}
     * @return this builder
     */
    MethodBuilder withCode(Consumer<? super CodeBuilder> code);

    /**
     * Build the method body for this method by transforming the body of another
     * method.
     *
     * @implNote
     * <p>This method behaves as if:
     * {@snippet lang=java :
     *     withCode(b -> b.transformCode(code, transform));
     * }
     *
     * @param code the method body to be transformed
     * @param transform the transform to apply to the method body
     * @return this builder
     */
    MethodBuilder transformCode(CodeModel code, CodeTransform transform);
}
