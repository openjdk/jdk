/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.corba.se.spi.orb ;

import java.util.Map ;
import java.util.AbstractMap ;
import java.util.Set ;
import java.util.AbstractSet ;
import java.util.Iterator ;
import java.util.Properties ;

import java.lang.reflect.Field ;

import org.omg.CORBA.INTERNAL ;

// XXX This could probably be further extended by using more reflection and
// a dynamic proxy that satisfies the interfaces that are inherited by the
// more derived class.  Do we want to go that far?
public abstract class ParserImplTableBase extends ParserImplBase {
    private final ParserData[] entries ;

    public ParserImplTableBase( ParserData[] entries )
    {
        this.entries = entries ;
        setDefaultValues() ;
    }

    protected PropertyParser makeParser()
    {
        PropertyParser result = new PropertyParser() ;
        for (int ctr=0; ctr<entries.length; ctr++ ) {
            ParserData entry = entries[ctr] ;
            entry.addToParser( result ) ;
        }

        return result ;
    }

    private static final class MapEntry implements Map.Entry {
        private Object key ;
        private Object value ;

        public MapEntry( Object key )
        {
            this.key = key ;
        }

        public Object getKey()
        {
            return key ;
        }

        public Object getValue()
        {
            return value ;
        }

        public Object setValue( Object value )
        {
            Object result = this.value ;
            this.value = value ;
            return result ;
        }

        public boolean equals( Object obj )
        {
            if (!(obj instanceof MapEntry))
                return false ;

            MapEntry other = (MapEntry)obj ;

            return (key.equals( other.key )) &&
                (value.equals( other.value )) ;
        }

        public int hashCode()
        {
            return key.hashCode() ^ value.hashCode() ;
        }
    }

    // Construct a map that maps field names to test or default values,
    // then use setFields from the parent class.  A map is constructed
    // by implementing AbstractMap, which requires implementing the
    // entrySet() method, which requires implementing a set of
    // map entries, which requires implementing an iterator,
    // which iterates over the ParserData, extracting the
    // correct (key, value) pairs (nested typed lambda expression).
    private static class FieldMap extends AbstractMap {
        private final ParserData[] entries ;
        private final boolean useDefault ;

        public FieldMap( ParserData[] entries, boolean useDefault )
        {
            this.entries = entries ;
            this.useDefault = useDefault ;
        }

        public Set entrySet()
        {
            return new AbstractSet()
            {
                public Iterator iterator()
                {
                    return new Iterator() {
                        // index of next element to return
                        int ctr = 0 ;

                        public boolean hasNext()
                        {
                            return ctr < entries.length ;
                        }

                        public Object next()
                        {
                            ParserData pd = entries[ctr++] ;
                            Map.Entry result = new MapEntry( pd.getFieldName() ) ;
                            if (useDefault)
                                result.setValue( pd.getDefaultValue() ) ;
                            else
                                result.setValue( pd.getTestValue() ) ;
                            return result ;
                        }

                        public void remove()
                        {
                            throw new UnsupportedOperationException() ;
                        }
                    } ;
                }

                public int size()
                {
                    return entries.length ;
                }
            } ;
        }
    } ;

    protected void setDefaultValues()
    {
        Map map = new FieldMap( entries, true ) ;
        setFields( map ) ;
    }

    public void setTestValues()
    {
        Map map = new FieldMap( entries, false ) ;
        setFields( map ) ;
    }
}
