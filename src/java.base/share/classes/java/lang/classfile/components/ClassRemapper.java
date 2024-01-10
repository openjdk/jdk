/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.components;

import java.lang.constant.ClassDesc;
import java.util.Map;
import java.util.function.Function;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldTransform;
import java.lang.classfile.MethodTransform;
import jdk.internal.classfile.impl.ClassRemapperImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * {@code ClassRemapper} is a {@link ClassTransform}, {@link FieldTransform},
 * {@link MethodTransform} and {@link CodeTransform}
 * deeply re-mapping all class references in any form, according to given map or
 * map function.
 * <p>
 * The re-mapping is applied to superclass, interfaces, all kinds of descriptors
 * and signatures, all attributes referencing classes in any form (including all
 * types of annotations), and to all instructions referencing to classes.
 * <p>
 * Primitive types and arrays are never subjects of mapping and are not allowed
 * targets of mapping.
 * <p>
 * Arrays of reference types are always decomposed, mapped as the base reference
 * types and composed back to arrays.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ClassRemapper extends ClassTransform permits ClassRemapperImpl {

    /**
     * Creates new instance of {@code ClassRemapper} instructed with a class map.
     * Map may contain only re-mapping entries, identity mapping is applied by default.
     * @param classMap class map
     * @return new instance of {@code ClassRemapper}
     */
    static ClassRemapper of(Map<ClassDesc, ClassDesc> classMap) {
        return of(desc -> classMap.getOrDefault(desc, desc));
    }

    /**
     * Creates new instance of {@code ClassRemapper} instructed with a map function.
     * Map function must return valid {@link java.lang.constant.ClassDesc} of an interface
     * or a class, even for identity mappings.
     * @param mapFunction class map function
     * @return new instance of {@code ClassRemapper}
     */
    static ClassRemapper of(Function<ClassDesc, ClassDesc> mapFunction) {
        return new ClassRemapperImpl(mapFunction);
    }

    /**
     * Access method to internal class mapping function.
     * @param desc source class
     * @return target class
     */
    ClassDesc map(ClassDesc desc);

    /**
     * {@return this {@code ClassRemapper} as {@link FieldTransform} instance}
     */
    FieldTransform asFieldTransform();

    /**
     * {@return this {@code ClassRemapper} as {@link MethodTransform} instance}
     */
    MethodTransform asMethodTransform();

    /**
     * {@return this {@code ClassRemapper} as {@link CodeTransform} instance}
     */
    CodeTransform asCodeTransform();

    /**
     * Remaps the whole ClassModel into a new class file, including the class name.
     * @param context ClassFile context
     * @param clm class model to re-map
     * @return re-mapped class file bytes
     */
    default byte[] remapClass(ClassFile context, ClassModel clm) {
        return context.transform(clm, map(clm.thisClass().asSymbol()), this);
    }
}
