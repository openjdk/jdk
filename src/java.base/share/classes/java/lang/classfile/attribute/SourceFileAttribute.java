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

package java.lang.classfile.attribute;

import java.lang.classfile.Attribute;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.Utf8Entry;

import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;

/**
 * Models the {@code SourceFile} attribute (JVMS {@jvms 4.7.10}), which
 * can appear on classes. Delivered as a {@link java.lang.classfile.ClassElement}
 * when traversing a {@link ClassModel}.
 * <p>
 * The attribute does not permit multiple instances in a given location.
 * Subsequent occurrence of the attribute takes precedence during the attributed
 * element build or transformation.
 *
 * @since 24
 */
public sealed interface SourceFileAttribute
        extends Attribute<SourceFileAttribute>, ClassElement
        permits BoundAttribute.BoundSourceFileAttribute, UnboundAttribute.UnboundSourceFileAttribute {

    /**
     * {@return the name of the source file from which this class was compiled}
     */
    Utf8Entry sourceFile();

    /**
     * {@return a source file attribute}
     * @param sourceFile the source file name
     */
    static SourceFileAttribute of(String sourceFile) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(sourceFile));
    }

    /**
     * {@return a source file attribute}
     * @param sourceFile the source file name
     */
    static SourceFileAttribute of(Utf8Entry sourceFile) {
        return new UnboundAttribute.UnboundSourceFileAttribute(sourceFile);
    }
}
