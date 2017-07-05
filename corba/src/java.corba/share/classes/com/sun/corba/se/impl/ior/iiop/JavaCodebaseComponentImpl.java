/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.ior.iiop;

import org.omg.IOP.TAG_JAVA_CODEBASE ;

import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.TaggedComponentBase ;

import com.sun.corba.se.spi.ior.iiop.JavaCodebaseComponent ;

/**
 * @author
 */
public class JavaCodebaseComponentImpl extends TaggedComponentBase
    implements JavaCodebaseComponent
{
    private String URLs ;

    public boolean equals( Object obj )
    {
        if (obj == null)
            return false ;

        if (!(obj instanceof JavaCodebaseComponentImpl))
            return false ;

        JavaCodebaseComponentImpl other = (JavaCodebaseComponentImpl)obj ;

        return URLs.equals( other.getURLs() ) ;
    }

    public int hashCode()
    {
        return URLs.hashCode() ;
    }

    public String toString()
    {
        return "JavaCodebaseComponentImpl[URLs=" + URLs + "]" ;
    }

    public String getURLs()
    {
        return URLs ;
    }

    public JavaCodebaseComponentImpl( String URLs )
    {
        this.URLs = URLs ;
    }

    public void writeContents(OutputStream os)
    {
        os.write_string( URLs ) ;
    }

    public int getId()
    {
        return TAG_JAVA_CODEBASE.value ; // 25 in CORBA 2.3.1 13.6.3
    }
}
