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

import java.util.List ;
import java.util.LinkedList ;
import java.util.Map ;
import java.util.HashMap ;
import java.util.Iterator ;
import java.util.Properties ;

import com.sun.corba.se.impl.orb.ParserAction ;
import com.sun.corba.se.impl.orb.ParserActionFactory ;

public class PropertyParser {
    private List actions ;

    public PropertyParser( )
    {
        actions = new LinkedList() ;
    }

    public PropertyParser add( String propName,
        Operation action, String fieldName )
    {
        actions.add( ParserActionFactory.makeNormalAction( propName,
            action, fieldName ) ) ;
        return this ;
    }

    public PropertyParser addPrefix( String propName,
        Operation action, String fieldName, Class componentType )
    {
        actions.add( ParserActionFactory.makePrefixAction( propName,
            action, fieldName, componentType ) ) ;
        return this ;
    }

    /** Return a map from field name to value.
    */
    public Map parse( Properties props )
    {
        Map map = new HashMap() ;
        Iterator iter = actions.iterator() ;
        while (iter.hasNext()) {
            ParserAction act = (ParserAction)(iter.next()) ;

            Object result = act.apply( props ) ;

            // A null result means that the property was not set for
            // this action, so do not override the default value in this case.
            if (result != null)
                map.put( act.getFieldName(), result ) ;
        }

        return map ;
    }

    public Iterator iterator()
    {
        return actions.iterator() ;
    }
}
