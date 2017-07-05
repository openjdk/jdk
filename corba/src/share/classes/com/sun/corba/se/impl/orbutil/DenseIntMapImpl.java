/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.orbutil ;

import java.util.ArrayList ;

/** Utility for managing mappings from densely allocated integer
 * keys to arbitrary objects.  This should only be used for
 * keys in the range 0..max such that "most" of the key space is actually
 * used.
 */
public class DenseIntMapImpl
{
    private ArrayList list = new ArrayList() ;

    private void checkKey( int key )
    {
        if (key < 0)
            throw new IllegalArgumentException( "Key must be >= 0." ) ;
    }

    /** If key >= 0, return the value bound to key, or null if none.
     * Throws IllegalArgumentException if key <0.
     */
    public Object get( int key )
    {
        checkKey( key ) ;

        Object result = null ;
        if (key < list.size())
            result = list.get( key ) ;

        return result ;
    }

    /** If key >= 0, bind value to the key.
     * Throws IllegalArgumentException if key <0.
     */
    public void set( int key, Object value )
    {
        checkKey( key ) ;
        extend( key ) ;
        list.set( key, value ) ;
    }

    private void extend( int index )
    {
        if (index >= list.size()) {
            list.ensureCapacity( index + 1 ) ;
            int max = list.size() ;
            while (max++ <= index)
                list.add( null ) ;
        }
    }
}
