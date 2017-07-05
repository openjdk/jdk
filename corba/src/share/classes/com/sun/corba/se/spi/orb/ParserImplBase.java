/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set ;
import java.util.Iterator ;
import java.util.Properties ;

import java.security.PrivilegedExceptionAction ;
import java.security.PrivilegedActionException ;
import java.security.AccessController ;

import java.lang.reflect.Field ;

import org.omg.CORBA.INTERNAL ;

import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

import com.sun.corba.se.impl.orbutil.ObjectUtility ;

// XXX This could probably be further extended by using more reflection and
// a dynamic proxy that satisfies the interfaces that are inherited by the
// more derived class.  Do we want to go that far?
public abstract class ParserImplBase {
    private ORBUtilSystemException wrapper ;

    protected abstract PropertyParser makeParser() ;

    /** Override this method if there is some needed initialization
    * that takes place after argument parsing.  It is always called
    * at the end of setFields.
    */
    protected void complete()
    {
    }

    public ParserImplBase()
    {
        // Do nothing in this case: no parsing takes place
        wrapper = ORBUtilSystemException.get(
            CORBALogDomains.ORB_LIFECYCLE ) ;
    }

    public void init( DataCollector coll )
    {
        PropertyParser parser = makeParser() ;
        coll.setParser( parser ) ;
        Properties props = coll.getProperties() ;
        Map map = parser.parse( props ) ;
        setFields( map ) ;
    }

    private Field getAnyField( String name )
    {
        Field result = null ;

        try {
            Class cls = this.getClass() ;
            result = cls.getDeclaredField( name ) ;
            while (result == null) {
                cls = cls.getSuperclass() ;
                if (cls == null)
                    break ;

                result = cls.getDeclaredField( name ) ;
            }
        } catch (Exception exc) {
            throw wrapper.fieldNotFound( exc, name ) ;
        }

        if (result == null)
            throw wrapper.fieldNotFound( name ) ;

        return result ;
    }

    protected void setFields( Map map )
    {
        Set entries = map.entrySet() ;
        Iterator iter = entries.iterator() ;
        while (iter.hasNext()) {
            java.util.Map.Entry entry = (java.util.Map.Entry)(iter.next()) ;
            final String name = (String)(entry.getKey()) ;
            final Object value = entry.getValue() ;

            try {
                AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public Object run() throws IllegalAccessException,
                            IllegalArgumentException
                        {
                            Field field = getAnyField( name ) ;
                            field.setAccessible( true ) ;
                            field.set( ParserImplBase.this, value ) ;
                            return null ;
                        }
                    }
                ) ;
            } catch (PrivilegedActionException exc) {
                // Since exc wraps the actual exception, use exc.getCause()
                // instead of exc.
                throw wrapper.errorSettingField( exc.getCause(), name,
                    ObjectUtility.compactObjectToString(value) ) ;
            }
        }

        // Make sure that any extra initialization takes place after all the
        // fields are set from the map.
        complete() ;
    }
}
