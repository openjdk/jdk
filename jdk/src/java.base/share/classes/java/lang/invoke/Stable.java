/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.invoke;

import java.lang.annotation.*;

/**
 * A field may be annotated as stable if all of its component variables
 * changes value at most once.
 * A field's value counts as its component value.
 * If the field is typed as an array, then all the non-null components
 * of the array, of depth up to the rank of the field's array type,
 * also count as component values.
 * By extension, any variable (either array or field) which has annotated
 * as stable is called a stable variable, and its non-null or non-zero
 * value is called a stable value.
 * <p>
 * Since all fields begin with a default value of null for references
 * (resp., zero for primitives), it follows that this annotation indicates
 * that the first non-null (resp., non-zero) value stored in the field
 * will never be changed.
 * <p>
 * If the field is not of an array type, there are no array elements,
 * then the value indicated as stable is simply the value of the field.
 * If the dynamic type of the field value is an array but the static type
 * is not, the components of the array are <em>not</em> regarded as stable.
 * <p>
 * If the field is an array type, then both the field value and
 * all the components of the field value (if the field value is non-null)
 * are indicated to be stable.
 * If the field type is an array type with rank {@code N > 1},
 * then each component of the field value (if the field value is non-null),
 * is regarded as a stable array of rank {@code N-1}.
 * <p>
 * Fields which are declared {@code final} may also be annotated as stable.
 * Since final fields already behave as stable values, such an annotation
 * indicates no additional information, unless the type of the field is
 * an array type.
 * <p>
 * It is (currently) undefined what happens if a field annotated as stable
 * is given a third value.  In practice, if the JVM relies on this annotation
 * to promote a field reference to a constant, it may be that the Java memory
 * model would appear to be broken, if such a constant (the second value of the field)
 * is used as the value of the field even after the field value has changed.
 */
/* package-private */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface Stable {
}
