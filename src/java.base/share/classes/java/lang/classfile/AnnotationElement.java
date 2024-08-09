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

import java.lang.constant.ClassDesc;

import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.AnnotationImpl;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a key-value pair of an annotation.
 *
 * @see Annotation
 * @see AnnotationValue
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface AnnotationElement
        permits AnnotationImpl.AnnotationElementImpl {

    /**
     * {@return the element name}
     */
    Utf8Entry name();

    /**
     * {@return the element value}
     */
    AnnotationValue value();

    /**
     * {@return an annotation key-value pair}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement of(Utf8Entry name,
                                AnnotationValue value) {
        return new AnnotationImpl.AnnotationElementImpl(name, value);
    }

    /**
     * {@return an annotation key-value pair}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement of(String name,
                                AnnotationValue value) {
        return of(TemporaryConstantPool.INSTANCE.utf8Entry(name), value);
    }

    /**
     * {@return an annotation key-value pair for a class-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofClass(String name,
                                     ClassDesc value) {
        return of(name, AnnotationValue.ofClass(value));
    }

    /**
     * {@return an annotation key-value pair for a string-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofString(String name,
                                      String value) {
        return of(name, AnnotationValue.ofString(value));
    }

    /**
     * {@return an annotation key-value pair for a long-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofLong(String name,
                                    long value) {
        return of(name, AnnotationValue.ofLong(value));
    }

    /**
     * {@return an annotation key-value pair for an int-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofInt(String name,
                                   int value) {
        return of(name, AnnotationValue.ofInt(value));
    }

    /**
     * {@return an annotation key-value pair for a char-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofChar(String name,
                                    char value) {
        return of(name, AnnotationValue.ofChar(value));
    }

    /**
     * {@return an annotation key-value pair for a short-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofShort(String name,
                                     short value) {
        return of(name, AnnotationValue.ofShort(value));
    }

    /**
     * {@return an annotation key-value pair for a byte-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofByte(String name,
                                      byte value) {
        return of(name, AnnotationValue.ofByte(value));
    }

    /**
     * {@return an annotation key-value pair for a boolean-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofBoolean(String name,
                                      boolean value) {
        return of(name, AnnotationValue.ofBoolean(value));
    }

    /**
     * {@return an annotation key-value pair for a double-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofDouble(String name,
                                      double value) {
        return of(name, AnnotationValue.ofDouble(value));
    }

    /**
     * {@return an annotation key-value pair for a float-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofFloat(String name,
                                     float value) {
        return of(name, AnnotationValue.ofFloat(value));
    }

    /**
     * {@return an annotation key-value pair for an annotation-valued annotation}
     * @param name the name of the key
     * @param value the associated value
     */
    static AnnotationElement ofAnnotation(String name,
                                          Annotation value) {
        return of(name, AnnotationValue.ofAnnotation(value));
    }

    /**
     * {@return an annotation key-value pair for an array-valued annotation}
     * @param name the name of the key
     * @param values the associated values
     */
    static AnnotationElement ofArray(String name,
                                     AnnotationValue... values) {
        return of(name, AnnotationValue.ofArray(values));
    }
}

