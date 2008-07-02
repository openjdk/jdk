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

/**
 * <p>Specifies the MXBean mapping factory to be used for Java types
 * in an MXBean interface, or in all MXBean interfaces in a package.</p>
 *
 * <p>Applying a mapping factory to all Java types in an MXBean interface
 * looks like this:</p>
 *
 * <pre>
 * {@literal @MXBeanMappingFactoryClass}(MyLinkedListMappingFactory.class)
 * public interface SomethingMXBean {
 *     public MyLinkedList getSomething();
 * }
 * </pre>
 *
 * <p>Applying a mapping factory to all Java types in all MXBean interfaces
 * in a package, say {@code com.example.mxbeans}, looks like this.  In the
 * package source directory, create a file called {@code package-info.java}
 * with these contents:</p>
 *
 * <pre>
 * {@literal @MXBeanMappingFactoryClass}(MyLinkedListMappingFactory.class)
 * package com.example.mxbeans;
 * </pre>
 *
 * @see MXBeanMappingFactory
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Documented @Inherited
public @interface MXBeanMappingFactoryClass {
    /**
     * <p>The {@link MXBeanMappingFactory} class to be used to map
     * types in the annotated interface or package.  This class must
     * have a public constructor with no arguments.  See the {@code
     * MXBeanMappingFactory} documentation for an example.</p>
     */
    public Class<? extends MXBeanMappingFactory> value();
}
