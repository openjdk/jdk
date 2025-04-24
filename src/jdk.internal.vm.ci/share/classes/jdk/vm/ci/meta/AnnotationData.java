/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.meta;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an annotation where element values are represented with the types described
 * {@linkplain #get here}.
 *
 * In contrast to the standard annotation API based on {@link Annotation}, use of
 * {@link AnnotationData} allows annotations to be queried without the JVMCI runtime having to
 * support dynamic loading of arbitrary {@link Annotation} classes. Such support is impossible in a
 * closed world, ahead-of-time compiled environment such as libgraal.
 */
public final class AnnotationData {

    private final JavaType type;
    private final Map<String, Object> elements;

    private static final Set<Class<?>> ELEMENT_TYPES = Set.of(
                    Boolean.class,
                    Byte.class,
                    Character.class,
                    Short.class,
                    Integer.class,
                    Float.class,
                    Long.class,
                    Double.class,
                    String.class,
                    EnumData.class,
                    AnnotationData.class);

    /**
     * Creates an annotation.
     *
     * @param type the annotation interface of this annotation, represented as a {@link JavaType}
     * @param elements the names and values of this annotation's element values. Each value's type
     *            must be one of the {@code AnnotationData} types described {@linkplain #get here}
     *            or it must be a {@link ErrorData} object whose {@code toString()} value describes
     *            the error raised while parsing the element. There is no distinction between a
     *            value explicitly present in the annotation and an element's default value.
     * @throws IllegalArgumentException if the value of an entry in {@code elements} is not of an
     *             accepted type
     * @throws NullPointerException if any of the above parameters is null or any entry in
     *             {@code elements} is null
     */
    public AnnotationData(JavaType type, Map.Entry<String, Object>[] elements) {
        this.type = Objects.requireNonNull(type);
        for (Map.Entry<String, Object> e : elements) {
            Object value = e.getValue();
            if (!(value instanceof ErrorData) &&
                            !(value instanceof JavaType) &&
                            !(value instanceof List) &&
                            !ELEMENT_TYPES.contains(value.getClass())) {
                throw new IllegalArgumentException("illegal type for element " + e.getKey() + ": " + value.getClass().getName());
            }
        }
        this.elements = Map.ofEntries(elements);
    }

    /**
     * @return the annotation interface of this annotation, represented as a {@link JavaType}
     */
    public JavaType getAnnotationType() {
        return type;
    }

    // @formatter:off
    /**
     * Gets the annotation element denoted by {@code name}. The following table shows the
     * correspondence between the type of an element as declared by a method in the annotation
     * interface and the type of value returned by this method:
     * <table>
     * <thead>
     * <tr><th>Annotation</th> <th>AnnotationData</th></tr>
     * </thead><tbody>
     * <tr><td>boolean</td>    <td>Boolean</td></tr>
     * <tr><td>byte</td>       <td>Byte</td></tr>
     * <tr><td>char</td>       <td>Character</td></tr>
     * <tr><td>short</td>      <td>Short</td></tr>
     * <tr><td>int</td>        <td>Integer</td></tr>
     * <tr><td>float</td>      <td>Float</td></tr>
     * <tr><td>long</td>       <td>Long</td></tr>
     * <tr><td>double</td>     <td>Double</td></tr>
     * <tr><td>String</td>     <td>String</td></tr>
     * <tr><td>Class</td>      <td>JavaType</td></tr>
     * <tr><td>Enum</td>       <td>EnumData</td></tr>
     * <tr><td>Annotation</td> <td>AnnotationData</td></tr>
     * <tr><td>[]</td><td>immutable List&lt;T&gt; where T is one of the above types</td></tr>
     * </tbody>
     * </table>
     *
     * @param <V> the type of the element as per the {@code AnnotationData} column in the above
     *            table or {@link Object}
     * @param elementType the class for the type of the element
     * @return the annotation element denoted by {@code name}
     * @throws ClassCastException if the element is not of type {@code V}
     * @throws IllegalArgumentException if this annotation has no element named {@code name} or if
     *             there was an error parsing or creating the element value
     */
    // @formatter:on
    public <V> V get(String name, Class<V> elementType) {
        Object val = elements.get(name);
        if (val == null) {
            throw new IllegalArgumentException("no element named " + name);
        }
        Class<? extends Object> valClass = val.getClass();
        if (valClass == ErrorData.class) {
            throw new IllegalArgumentException(val.toString());
        }
        return elementType.cast(val);
    }

    @Override
    public String toString() {
        return "@" + type.getName() + "(" + elements + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AnnotationData) {
            AnnotationData that = (AnnotationData) obj;
            return this.type.equals(that.type) && this.elements.equals(that.elements);

        }
        return false;
    }

    @Override
    public int hashCode() {
        return type.hashCode() ^ elements.hashCode();
    }
}
