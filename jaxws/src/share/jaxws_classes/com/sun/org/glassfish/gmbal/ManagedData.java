/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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


package com.sun.org.glassfish.gmbal ;

import java.lang.annotation.Documented ;
import java.lang.annotation.Target ;
import java.lang.annotation.ElementType ;
import java.lang.annotation.Retention ;
import java.lang.annotation.RetentionPolicy ;

/** This annotation defines CompositeData.   An interface or class annotated as @ManagedData
 * has a corresponding CompositeData instance constructed according to the @ManagedAttribute
 * annotations on its methods.  All inherited annotated methods are included.
 * In the case of conflicts, the most derived method is used (that is the method
 * declared in the method
 * closest to the class annotated as @ManagedData).
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedData {
    /** The name of the ManagedData.
     * <P>
     * Gmbal determines the ManagedData name as follows:
     * <ol>
     * <li>If the class has a final static field of type String with the
     * name "GMBAL_TYPE", the value of the field is the ManagedData name.
     * <li>Otherwise, if the class has an @ManagedData annotation, and the
     * value of the name is not "", the value of the name is the ManagedData name.
     * <li>Otherwise, if the package prefix of the class name matches one of
     * the type prefixes added by an stripPrefix call to the ManagedObjectManager,
     * the ManagedData name is the full class name with the matching prefix removed.
     * <li>Otherwise, if the stripPackagePrefix method was called on the
     * ManagedObjectManager, the ManagedData name is the class name without any
     * package prefixes.
     * <li>Otherwise, the ManagedData name is the class name.
     * </ol>
     *
     */
    String name() default "" ;
}
