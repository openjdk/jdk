/*
 * Copyright 2000-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.spi.ior;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import com.sun.corba.se.impl.ior.FreezableList ;

import com.sun.corba.se.spi.ior.TaggedComponent ;
import com.sun.corba.se.spi.ior.Identifiable ;

/** Convenience class for defining objects that contain lists of Identifiables.
 * Mainly implements iteratorById.  Also note that the constructor creates the
 * list, which here is always an ArrayList, as this is much more efficient overall
 * for short lists.
 * @author  Ken Cavanaugh
 */
public class IdentifiableContainerBase extends FreezableList
{
    /** Create this class with an empty list of identifiables.
     * The current implementation uses an ArrayList.
     */
    public IdentifiableContainerBase()
    {
        super( new ArrayList() ) ;
    }

    /** Return an iterator which iterates over all contained Identifiables
     * with type given by id.
     */
    public Iterator iteratorById( final int id)
    {
        return new Iterator() {
            Iterator iter = IdentifiableContainerBase.this.iterator() ;
            Object current = advance() ;

            private Object advance()
            {
                while (iter.hasNext()) {
                    Identifiable ide = (Identifiable)(iter.next()) ;
                    if (ide.getId() == id)
                        return ide ;
                }

                return null ;
            }

            public boolean hasNext()
            {
                return current != null ;
            }

            public Object next()
            {
                Object result = current ;
                current = advance() ;
                return result ;
            }

            public void remove()
            {
                iter.remove() ;
            }
        } ;
    }
}
