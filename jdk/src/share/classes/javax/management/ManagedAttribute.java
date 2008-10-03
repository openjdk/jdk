/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package javax.management;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Indicates that a method in an MBean class defines an MBean attribute.
 * This annotation must be applied to a public method of a public class
 * that is itself annotated with an {@link MBean @MBean} or
 * {@link MXBean @MXBean} annotation, or inherits such an annotation from
 * a superclass.</p>
 *
 * <p>The annotated method must be a getter or setter.  In other words,
 * it must look like one of the following...</p>
 *
 * <pre>
 * <i>T</i> get<i>Foo</i>()
 * void set<i>Foo</i>(<i>T</i> param)
 * </pre>
 *
 * <p>...where <i>{@code T}</i> is any type and <i>{@code Foo}</i> is the
 * name of the attribute.  For any attribute <i>{@code Foo}</i>, if only
 * a {@code get}<i>{@code Foo}</i> method has a {@code ManagedAttribute}
 * annotation, then <i>{@code Foo}</i> is a read-only attribute.  If only
 * a {@code set}<i>{@code Foo}</i> method has a {@code ManagedAttribute}
 * annotation, then <i>{@code Foo}</i> is a write-only attribute.  If
 * both {@code get}<i>{@code Foo}</i> and {@code set}<i>{@code Foo}</i>
 * methods have the annotation, then <i>{@code Foo}</i> is a read-write
 * attribute.  In this last case, the type <i>{@code T}</i> must be the
 * same in both methods.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface ManagedAttribute {
}
