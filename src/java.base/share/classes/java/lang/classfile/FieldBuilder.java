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

import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.AccessFlagsImpl;
import jdk.internal.classfile.impl.ChainedFieldBuilder;
import jdk.internal.classfile.impl.TerminalFieldBuilder;

/**
 * A builder for fields.  The main way to obtain a field builder is via {@link
 * ClassBuilder#withField(String, ClassDesc, Consumer)}.  The {@linkplain
 * ClassBuilder#withField(String, ClassDesc, int) access flag overload} is
 * useful if no attribute needs to be configured, skipping the handler.
 * <p>
 * Refer to {@link ClassFileBuilder} for general guidance and caution around
 * the use of builders for structures in the {@code class} file format.
 *
 * @see ClassBuilder#withField(String, ClassDesc, Consumer)
 * @see FieldModel
 * @see FieldTransform
 * @since 24
 */
public sealed interface FieldBuilder
        extends ClassFileBuilder<FieldElement, FieldBuilder>
        permits TerminalFieldBuilder, ChainedFieldBuilder {

    /**
     * Sets the field access flags.
     *
     * @param flags the access flags, as a bit mask
     * @return this builder
     * @see AccessFlags
     * @see AccessFlag.Location#FIELD
     * @see ClassBuilder#withField(String, ClassDesc, int)
     */
    default FieldBuilder withFlags(int flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.FIELD, flags));
    }

    /**
     * Sets the field access flags.
     *
     * @param flags the access flags, as a bit mask
     * @return this builder
     * @see AccessFlags
     * @see AccessFlag.Location#FIELD
     * @see ClassBuilder#withField(String, ClassDesc, int)
     */
    default FieldBuilder withFlags(AccessFlag... flags) {
        return with(new AccessFlagsImpl(AccessFlag.Location.FIELD, flags));
    }

}
