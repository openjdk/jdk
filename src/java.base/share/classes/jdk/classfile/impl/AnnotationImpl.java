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
package jdk.classfile.impl;

import jdk.classfile.*;
import jdk.classfile.constantpool.AnnotationConstantValueEntry;
import jdk.classfile.constantpool.Utf8Entry;

import java.lang.constant.ConstantDesc;
import java.util.List;

public final class AnnotationImpl implements Annotation {
    private final Utf8Entry className;
    private final List<AnnotationElement> elements;

    public AnnotationImpl(Utf8Entry className,
                          List<AnnotationElement> elems) {
        this.className = className;
        this.elements = List.copyOf(elems);
    }

    @Override
    public Utf8Entry className() {
        return className;
    }

    @Override
    public List<AnnotationElement> elements() {
        return elements;
    }

    @Override
    public void writeTo(BufWriter buf) {
        buf.writeIndex(className());
        buf.writeList(elements());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Annotation[");
        sb.append(className().stringValue());
        List<AnnotationElement> evps = elements();
        if (!evps.isEmpty())
            sb.append(" [");
        for (AnnotationElement evp : evps) {
            sb.append(evp.name().stringValue())
                    .append("=")
                    .append(evp.value().toString())
                    .append(", ");
        }
        if (!evps.isEmpty()) {
            sb.delete(sb.length()-1, sb.length());
            sb.append("]");
        }
        sb.append("]");
        return sb.toString();
    }

    public record AnnotationElementImpl(Utf8Entry name,
                                        AnnotationValue value)
            implements AnnotationElement {

        @Override
        public void writeTo(BufWriter buf) {
            buf.writeIndex(name());
            value().writeTo(buf);
        }
    }

    public record OfConstantImpl(char tag, AnnotationConstantValueEntry constant)
            implements AnnotationValue.OfConstant {

        @Override
        public void writeTo(BufWriter buf) {
            buf.writeU1(tag());
            buf.writeIndex(constant);
        }

        @Override
        public ConstantDesc constantValue() {
            return constant.constantValue();
        }

    }

    public record OfArrayImpl(List<AnnotationValue> values)
            implements AnnotationValue.OfArray {

        public OfArrayImpl(List<AnnotationValue> values) {
            this.values = List.copyOf(values);;
        }

        @Override
        public char tag() {
            return '[';
        }

        @Override
        public void writeTo(BufWriter buf) {
            buf.writeU1(tag());
            buf.writeList(values);
        }

    }

    public record OfEnumImpl(Utf8Entry className, Utf8Entry constantName)
            implements AnnotationValue.OfEnum {
        @Override
        public char tag() {
            return 'e';
        }

        @Override
        public void writeTo(BufWriter buf) {
            buf.writeU1(tag());
            buf.writeIndex(className);
            buf.writeIndex(constantName);
        }

    }

    public record OfAnnotationImpl(Annotation annotation)
            implements AnnotationValue.OfAnnotation {
        @Override
        public char tag() {
            return '@';
        }

        @Override
        public void writeTo(BufWriter buf) {
            buf.writeU1(tag());
            annotation.writeTo(buf);
        }

    }

    public record OfClassImpl(Utf8Entry className)
            implements AnnotationValue.OfClass {
        @Override
        public char tag() {
            return 'c';
        }

        @Override
        public void writeTo(BufWriter buf) {
            buf.writeU1(tag());
            buf.writeIndex(className);
        }

    }
}
