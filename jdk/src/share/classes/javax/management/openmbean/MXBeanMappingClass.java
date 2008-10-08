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

package javax.management.openmbean;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.management.NotCompliantMBeanException;

/**
 * Specifies the MXBean mapping to be used for this Java type.
 * @see MXBeanMapping
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented @Inherited
public @interface MXBeanMappingClass {
    /**
     * <p>The {@link MXBeanMapping} class to be used to map the
     * annotated type.  This class must have a public constructor with
     * a single argument of type {@link java.lang.reflect.Type}.  The
     * constructor will be called with the annotated type as an
     * argument.  See the {@code MXBeanMapping} documentation
     * for an example.</p>
     *
     * <p>If the {@code MXBeanMapping} cannot in fact handle that
     * type, the constructor should throw an {@link
     * OpenDataException}.  If the constructor throws this or any other
     * exception then an MXBean in which the annotated type appears is
     * invalid, and registering it in the MBean Server will produce a
     * {@link NotCompliantMBeanException}.
     */
    public Class<? extends MXBeanMapping> value();
}
