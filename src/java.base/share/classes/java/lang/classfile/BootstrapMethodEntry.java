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

import java.util.List;

import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import jdk.internal.classfile.impl.BootstrapMethodEntryImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * Models an entry in the bootstrap method table.  The bootstrap method table
 * is stored in the {@code BootstrapMethods} attribute, but is modeled by
 * the {@link ConstantPool}, since the bootstrap method table is logically
 * part of the constant pool.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface BootstrapMethodEntry
        extends WritableElement<BootstrapMethodEntry>
        permits BootstrapMethodEntryImpl {

    /**
     * {@return the constant pool associated with this entry}
     */
    ConstantPool constantPool();

    /**
     * {@return the index into the bootstrap method table corresponding to this entry}
     */
    int bsmIndex();

    /**
     * {@return the bootstrap method}
     */
    MethodHandleEntry bootstrapMethod();

    /**
     * {@return the bootstrap arguments}
     */
    List<LoadableConstantEntry> arguments();
}
