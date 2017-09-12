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

import org.omg.CORBA_2_3.portable.InputStream ;
import org.omg.CORBA_2_3.portable.OutputStream ;

import com.sun.corba.se.spi.ior.TaggedComponentBase ;

import com.sun.corba.se.spi.ior.iiop.CodeSetsComponent ;

import org.omg.IOP.TAG_CODE_SETS ;

import com.sun.corba.se.impl.encoding.CodeSetComponentInfo ;
import com.sun.corba.se.impl.encoding.MarshalOutputStream ;
import com.sun.corba.se.impl.encoding.MarshalInputStream ;

public class CodeSetsComponentImpl extends TaggedComponentBase
    implements CodeSetsComponent
{
    CodeSetComponentInfo csci ;

    public boolean equals( Object obj )
    {
        if (!(obj instanceof CodeSetsComponentImpl))
            return false ;

        CodeSetsComponentImpl other = (CodeSetsComponentImpl)obj ;

        return csci.equals( other.csci ) ;
    }

    public int hashCode()
    {
        return csci.hashCode() ;
    }

    public String toString()
    {
        return "CodeSetsComponentImpl[csci=" + csci + "]" ;
    }

    public CodeSetsComponentImpl()
    {
        // Uses our default code sets (see CodeSetComponentInfo)
        csci = new CodeSetComponentInfo() ;
    }

    public CodeSetsComponentImpl( InputStream is )
    {
        csci = new CodeSetComponentInfo() ;
        csci.read( (MarshalInputStream)is ) ;
    }

    public CodeSetsComponentImpl(com.sun.corba.se.spi.orb.ORB orb)
    {
        if (orb == null)
            csci = new CodeSetComponentInfo();
        else
            csci = orb.getORBData().getCodeSetComponentInfo();
    }

    public CodeSetComponentInfo getCodeSetComponentInfo()
    {
        return csci ;
    }

    public void writeContents(OutputStream os)
    {
        csci.write( (MarshalOutputStream)os ) ;
    }

    public int getId()
    {
        return TAG_CODE_SETS.value ; // 1 in CORBA 2.3.1 13.6.3
    }
}
