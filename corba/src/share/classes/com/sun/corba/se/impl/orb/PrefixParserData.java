/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.impl.orb ;

import java.util.Properties ;

import com.sun.corba.se.spi.orb.StringPair ;
import com.sun.corba.se.spi.orb.Operation ;
import com.sun.corba.se.spi.orb.PropertyParser ;

public class PrefixParserData extends ParserDataBase {

    private StringPair[] testData ;
    private Class componentType ;

    public PrefixParserData( String  propertyName,
        Operation operation, String fieldName, Object defaultValue,
        Object testValue, StringPair[] testData, Class componentType )
    {
        super( propertyName, operation, fieldName, defaultValue, testValue ) ;
        this.testData = testData ;
        this.componentType = componentType ;
    }

    public void addToParser( PropertyParser parser )
    {
        parser.addPrefix( getPropertyName(), getOperation(), getFieldName(),
            componentType ) ;
    }

    public void addToProperties( Properties props )
    {
        for (int ctr=0; ctr<testData.length; ctr++) {
            StringPair sp = testData[ctr] ;

            String propName = getPropertyName() ;
            if (propName.charAt( propName.length() - 1 ) != '.')
                propName += "." ;

            props.setProperty( propName + sp.getFirst(), sp.getSecond() ) ;
        }
    }
}
