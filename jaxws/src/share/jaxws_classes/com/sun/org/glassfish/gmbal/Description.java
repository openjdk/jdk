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

/** This annotation is applied to a method that takes no arguments and returns a value
 * that is converted into a String for use in the ObjectName when an instance of the enclosing
 * class is used to construct an open MBean.
 */
@Documented
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Description {
    /** The description to be applied to the annotated element.
     * This value must not be empty.  It can either be the actual string that is inserted
     * into the MBean info class, or a key into a resource bundle associated with the
     * ManagedObjectManager.  If there is no bundle value associated with the key, or no
     * resource bundle is specified, the value is used directly in the MBean info class.
     */
    String value() ;

    /** Optional key to use in a resource bundle for this description. If present,
     * a gmbal tool will generate a resource bundle that contains key=value taken
     * from the description annotation.
     * <p>
     * If this key is not present, the default key is given by the class name,
     * if this annotation appears on a class, or the class name.method name if
     * this annotation appears on a method.  It is an error to use the default
     * value for more than one method of the same name, except for setters and getters.
     *
     */
    String key() default "" ;
}
