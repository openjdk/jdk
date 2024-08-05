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

import java.lang.classfile.constantpool.Utf8Entry;

import jdk.internal.classfile.impl.AccessFlagsImpl;
import jdk.internal.classfile.impl.ChainedFieldBuilder;
import jdk.internal.classfile.impl.TerminalFieldBuilder;
import java.lang.reflect.AccessFlag;

import java.util.function.Consumer;
import jdk.internal.javac.PreviewFeature;

/**
 * A builder for fields.  Builders are not created directly; they are passed
 * to handlers by methods such as {@link ClassBuilder#withField(Utf8Entry, Utf8Entry, Consumer)}
 * or to field transforms.  The elements of a field can be specified
 * abstractly (by passing a {@link FieldElement} to {@link #with(ClassFileElement)}
 * or concretely by calling the various {@code withXxx} methods.
 *
 * @see FieldTransform
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface FieldBuilder
        extends ClassFileBuilder<FieldElement, FieldBuilder>
        permits TerminalFieldBuilder, ChainedFieldBuilder {

    /**
     * Sets the field access flags.
     * @param flags the access flags, as a bit mask
     * @return this builder
     */
    default FieldBuilder withFlags(int flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.FIELD, flags));
    }

    /**
     * Sets the field access flags.
     * @param flags the access flags, as a bit mask
     * @return this builder
     */
    default FieldBuilder withFlags(AccessFlag... flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.FIELD, flags));
    }

}
