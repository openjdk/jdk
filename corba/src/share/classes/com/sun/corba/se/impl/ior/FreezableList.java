/*
 * Copyright (c) 2000, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.ior ;

import java.util.Collection ;
import java.util.List ;
import java.util.AbstractList ;
import java.util.ListIterator ;
import java.util.Iterator ;

import com.sun.corba.se.spi.ior.MakeImmutable ;

/** Simple class that delegates all List operations to
* another list.  It also can be frozen, which means that
* a number of operations can be performed on the list,
* and then the list can be made immutable, so that no
* further changes are possible.  A FreezableList is frozen
* using the makeImmutable method.
*/
public class FreezableList extends AbstractList {
    private List delegate = null ;
    private boolean immutable = false ;

    public boolean equals( Object obj )
    {
        if (obj == null)
            return false ;

        if (!(obj instanceof FreezableList))
            return false ;

        FreezableList other = (FreezableList)obj ;

        return delegate.equals( other.delegate ) &&
            (immutable == other.immutable) ;
    }

    public int hashCode()
    {
        return delegate.hashCode() ;
    }

    public FreezableList( List delegate, boolean immutable  )
    {
        this.delegate = delegate ;
        this.immutable = immutable ;
    }

    public FreezableList( List delegate )
    {
        this( delegate, false ) ;
    }

    public void makeImmutable()
    {
        immutable = true ;
    }

    public boolean isImmutable()
    {
        return immutable ;
    }

    public void makeElementsImmutable()
    {
        Iterator iter = iterator() ;
        while (iter.hasNext()) {
            Object obj = iter.next() ;
            if (obj instanceof MakeImmutable) {
                MakeImmutable element = (MakeImmutable)obj ;
                element.makeImmutable() ;
            }
        }
    }

    // Methods overridden from AbstractList

    public int size()
    {
        return delegate.size() ;
    }

    public Object get(int index)
    {
        return delegate.get(index) ;
    }

    public Object set(int index, Object element)
    {
        if (immutable)
            throw new UnsupportedOperationException() ;

        return delegate.set(index, element) ;
    }

    public void add(int index, Object element)
    {
        if (immutable)
            throw new UnsupportedOperationException() ;

        delegate.add(index, element) ;
    }

    public Object remove(int index)
    {
        if (immutable)
            throw new UnsupportedOperationException() ;

        return delegate.remove(index) ;
    }

    // We also override subList so that the result is a FreezableList.
    public List subList(int fromIndex, int toIndex)
    {
        List list = delegate.subList(fromIndex, toIndex) ;
        List result = new FreezableList( list, immutable ) ;
        return result ;
    }
}
