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

package com.sun.corba.se.impl.orb ;

import java.util.Properties ;

import com.sun.corba.se.spi.orb.Operation ;

public abstract class ParserActionBase implements ParserAction {
    private String propertyName ;
    private boolean prefix ;
    private Operation operation ;
    private String fieldName ;

    public int hashCode()
    {
        return propertyName.hashCode() ^ operation.hashCode() ^
            fieldName.hashCode() ^ (prefix ? 0 : 1) ;
    }

    public boolean equals( Object obj )
    {
        if (obj == this)
            return true ;

        if (!(obj instanceof ParserActionBase))
            return false ;

        ParserActionBase other = (ParserActionBase)obj ;

        return propertyName.equals( other.propertyName ) &&
            prefix == other.prefix &&
            operation.equals( other.operation ) &&
            fieldName.equals( other.fieldName ) ;
    }

    public ParserActionBase( String propertyName, boolean prefix,
        Operation operation, String fieldName )
    {
        this.propertyName       = propertyName ;
        this.prefix             = prefix ;
        this.operation          = operation ;
        this.fieldName          = fieldName ;
    }

    public String getPropertyName()
    {
        return propertyName ;
    }

    public boolean isPrefix()
    {
        return prefix ;
    }

    public String getFieldName()
    {
        return fieldName ;
    }

    public abstract Object apply( Properties props ) ;

    protected Operation getOperation()
    {
        return operation ;
    }
}
