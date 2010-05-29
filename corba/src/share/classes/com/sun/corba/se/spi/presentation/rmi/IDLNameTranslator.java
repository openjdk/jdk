/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.presentation.rmi ;

import java.lang.reflect.Method ;

/** Translates between methods on an interface and RMI-IIOP encodings
 * of those methods as names.
 */
public interface IDLNameTranslator
{
    /** Get the interfaces that this IDLNameTranslator describes.
     */
    Class[] getInterfaces() ;

    /** Get all methods for this remote interface.
     * The methods are returned in a canonical order, that is,
     * they are always in the same order for a particular interface.
     */
    Method[] getMethods() ;

    /** Get the method from this IDLNameTranslator's interfaces that
     * corresponds to the mangled name idlName.  Returns null
     * if there is no matching method.
     */
    Method getMethod( String idlName )  ;

    /** Get the mangled name that corresponds to the given method
     * on this IDLNameTranslator's interface.  Returns null
     * if there is no matching name.
     */
    String getIDLName( Method method )  ;
}
