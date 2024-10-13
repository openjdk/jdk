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
package java.lang.classfile.attribute;

import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.constant.ModuleDesc;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.javac.PreviewFeature;

/**
 * Models hash information for a single module in the {@link ModuleHashesAttribute}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ModuleHashInfo
        permits UnboundAttribute.UnboundModuleHashInfo {

    /**
     * {@return the name of the related module}
     */
    ModuleEntry moduleName();

    /**
     * {@return the hash of the related module}
     */
    byte[] hash();

    /**
     * {@return a module hash description}
     * @param moduleName the module name
     * @param hash the hash value
     */
    static ModuleHashInfo of(ModuleEntry moduleName, byte[] hash) {
        return new UnboundAttribute.UnboundModuleHashInfo(moduleName, hash);
    }

    /**
     * {@return a module hash description}
     * @param moduleDesc the module name
     * @param hash the hash value
     */
    static ModuleHashInfo of(ModuleDesc moduleDesc, byte[] hash) {
        return new UnboundAttribute.UnboundModuleHashInfo(TemporaryConstantPool.INSTANCE.moduleEntry(TemporaryConstantPool.INSTANCE.utf8Entry(moduleDesc.name())), hash);
    }
}
