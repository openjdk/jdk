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

package com.sun.corba.se.impl.orb ;

import org.omg.CORBA.INITIALIZE ;

import java.util.Properties ;
import java.util.List ;
import java.util.LinkedList ;
import java.util.Iterator ;

import java.lang.reflect.Array ;

import com.sun.corba.se.spi.orb.Operation ;
import com.sun.corba.se.spi.orb.StringPair ;
import com.sun.corba.se.spi.logging.CORBALogDomains ;

import com.sun.corba.se.impl.orbutil.ObjectUtility ;
import com.sun.corba.se.impl.logging.ORBUtilSystemException ;

public class PrefixParserAction extends ParserActionBase {
    private Class componentType ;
    private ORBUtilSystemException wrapper ;

    public PrefixParserAction( String propertyName,
        Operation operation, String fieldName, Class componentType )
    {
        super( propertyName, true, operation, fieldName ) ;
        this.componentType = componentType ;
        this.wrapper = ORBUtilSystemException.get(
            CORBALogDomains.ORB_LIFECYCLE ) ;
    }

    /** For each String s that matches the prefix given by getPropertyName(),
     * apply getOperation() to { suffix( s ), value }
     * and add the result to an Object[]
     * which forms the result of apply.  Returns null if there are no
     * matches.
     */
    public Object apply( Properties props )
    {
        String prefix = getPropertyName() ;
        int prefixLength = prefix.length() ;
        if (prefix.charAt( prefixLength - 1 ) != '.') {
            prefix += '.' ;
            prefixLength++ ;
        }

        List matches = new LinkedList() ;

        // Find all keys in props that start with propertyName
        Iterator iter = props.keySet().iterator() ;
        while (iter.hasNext()) {
            String key = (String)(iter.next()) ;
            if (key.startsWith( prefix )) {
                String suffix = key.substring( prefixLength ) ;
                String value = props.getProperty( key ) ;
                StringPair data = new StringPair( suffix, value ) ;
                Object result = getOperation().operate( data ) ;
                matches.add( result ) ;
            }
        }

        int size = matches.size() ;
        if (size > 0) {
            // Convert the list into an array of the proper type.
            // An Object[] as a result does NOT work.  Also report
            // any errors carefully, as errors here or in parsers that
            // use this Operation often show up at ORB.init().
            Object result = null ;
            try {
                result = Array.newInstance( componentType, size ) ;
            } catch (Throwable thr) {
                throw wrapper.couldNotCreateArray( thr,
                    getPropertyName(), componentType,
                    new Integer( size ) ) ;
            }

            Iterator iter2 = matches.iterator() ;
            int ctr = 0 ;
            while (iter2.hasNext()) {
                Object obj = iter2.next() ;

                try {
                    Array.set( result, ctr, obj ) ;
                } catch (Throwable thr) {
                    throw wrapper.couldNotSetArray( thr,
                        getPropertyName(), new Integer(ctr),
                        componentType, new Integer(size),
                        ObjectUtility.compactObjectToString( obj )) ;
                }
                ctr++ ;
            }

            return result ;
        } else
            return null ;
    }
}
