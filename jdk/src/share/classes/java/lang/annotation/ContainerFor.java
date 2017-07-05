/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.annotation;

/**
 * The annotation type {@code java.lang.annotation.ContainerFor} is
 * used to indicate that the annotation type whose declaration it
 * (meta-)annotates is a <em>containing annotation type</em>. The
 * value of {@code @ContainerFor} indicates the <em>repeatable
 * annotation type</em> for the containing annotation type.
 *
 * <p>The pair of annotation types {@link
 * java.lang.annotation.ContainedBy @ContainedBy} and
 * {@code @ContainerFor} are used to indicate that annotation types
 * are repeatable. Specifically:
 *
 * <ul>
 * <li>The annotation type {@code @ContainedBy} is used on the
 * declaration of a repeatable annotation type (JLS 9.6) to indicate
 * its containing annotation type.
 *
 * <li>The annotation type {@code @ContainerFor} is used on the
 * declaration of a containing annotation type (JLS 9.6) to indicate
 * the repeatable annotation type for which it serves as the
 * containing annotation type.
 * </ul>
 *
 * <p>
 * An inconsistent pair of {@code @ContainedBy} and
 * {@code @ContainerFor} annotations on a repeatable annotation type
 * and its containing annotation type (JLS 9.6) will lead to
 * compile-time errors and runtime exceptions when using reflection to
 * read annotations of a repeatable type.
 *
 * @see java.lang.annotation.ContainedBy
 * @since 1.8
 * @jls 9.6 Annotation Types
 * @jls 9.7 Annotations
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface ContainerFor {

    /**
     * Indicates the repeatable annotation type for the containing
     * annotation type.
     */
    Class<? extends Annotation> value();
}
