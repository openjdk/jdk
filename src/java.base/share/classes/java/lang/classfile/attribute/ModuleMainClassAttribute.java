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

import java.lang.constant.ClassDesc;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassElement;
import java.lang.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the {@code ModuleMainClass} attribute {@jvms 4.7.27}, which can
 * appear on classes that represent module descriptors.
 * Delivered as a {@link java.lang.classfile.ClassElement} when
 * traversing the elements of a {@link java.lang.classfile.ClassModel}.
 * <p>
 * The attribute does not permit multiple instances in a given location.
 * Subsequent occurrence of the attribute takes precedence during the attributed
 * element build or transformation.
 * <p>
 * The attribute was introduced in the Java SE Platform version 9.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ModuleMainClassAttribute
        extends Attribute<ModuleMainClassAttribute>, ClassElement
        permits BoundAttribute.BoundModuleMainClassAttribute, UnboundAttribute.UnboundModuleMainClassAttribute {

    /**
     * {@return main class for this module}
     */
    ClassEntry mainClass();

    /**
     * {@return a {@code ModuleMainClass} attribute}
     * @param mainClass the main class
     */
    static ModuleMainClassAttribute of(ClassEntry mainClass) {
        return new UnboundAttribute.UnboundModuleMainClassAttribute(mainClass);
    }

    /**
     * {@return a {@code ModuleMainClass} attribute}
     * @param mainClass the main class
     * @throws IllegalArgumentException if {@code mainClass} represents a primitive type
     */
    static ModuleMainClassAttribute of(ClassDesc mainClass) {
        return new UnboundAttribute.UnboundModuleMainClassAttribute(TemporaryConstantPool.INSTANCE.classEntry(mainClass));
    }
}
