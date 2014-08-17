/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.orbutil;

import java.security.PrivilegedAction;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Properties;
import java.util.IdentityHashMap;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger ;
import java.math.BigDecimal ;

public final class ObjectUtility {
    private ObjectUtility() {}


    /** If arr1 and arr2 are both arrays of the same component type,
     * return an array of that component type that consists of the
     * elements of arr1 followed by the elements of arr2.
     * Throws IllegalArgumentException otherwise.
     */
    public static Object concatenateArrays( Object arr1, Object arr2 )
    {
        Class comp1 = arr1.getClass().getComponentType() ;
        Class comp2 = arr2.getClass().getComponentType() ;
        int len1 = Array.getLength( arr1 ) ;
        int len2 = Array.getLength( arr2 ) ;

        if ((comp1 == null) || (comp2 == null))
            throw new IllegalStateException( "Arguments must be arrays" ) ;
        if (!comp1.equals( comp2 ))
            throw new IllegalStateException(
                "Arguments must be arrays with the same component type" ) ;

        Object result = Array.newInstance( comp1, len1 + len2 ) ;

        int index = 0 ;

        for (int ctr=0; ctr<len1; ctr++)
            Array.set( result, index++, Array.get( arr1, ctr ) ) ;

        for (int ctr=0; ctr<len2; ctr++)
            Array.set( result, index++, Array.get( arr2, ctr ) ) ;

        return result ;
    }

}
