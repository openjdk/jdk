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
import java.util.function.Consumer;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPool;
import jdk.internal.classfile.impl.ClassImpl;
import jdk.internal.classfile.impl.verifier.VerifierImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a classfile.  The contents of the classfile can be traversed via
 * a streaming view, or via random access (e.g.,
 * {@link #flags()}), or by freely mixing the two.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ClassModel
        extends CompoundElement<ClassElement>, AttributedElement
        permits ClassImpl {

    /**
     * {@return the constant pool for this class}
     */
    ConstantPool constantPool();

    /** {@return the access flags} */
    AccessFlags flags();

    /** {@return the constant pool entry describing the name of this class} */
    ClassEntry thisClass();

    /** {@return the major classfile version} */
    int majorVersion();

    /** {@return the minor classfile version} */
    int minorVersion();

    /** {@return the fields of this class} */
    List<FieldModel> fields();

    /** {@return the methods of this class} */
    List<MethodModel> methods();

    /** {@return the superclass of this class, if there is one} */
    Optional<ClassEntry> superclass();

    /** {@return the interfaces implemented by this class} */
    List<ClassEntry> interfaces();

    /** {@return whether this class is a module descriptor} */
    boolean isModuleInfo();
}
