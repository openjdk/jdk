/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.corba.se.spi.servicecontext;

import java.io.Serializable ;
import org.omg.CORBA.SystemException;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.UNKNOWN;
import org.omg.CORBA_2_3.portable.InputStream;
import org.omg.CORBA_2_3.portable.OutputStream;
import com.sun.corba.se.spi.ior.iiop.GIOPVersion;
import com.sun.corba.se.spi.servicecontext.ServiceContext ;

public class UEInfoServiceContext extends ServiceContext {
    public UEInfoServiceContext( Throwable ex )
    {
        unknown = ex ;
    }

    public UEInfoServiceContext(InputStream is, GIOPVersion gv)
    {
        super(is, gv) ;

        try {
            unknown = (Throwable) in.read_value() ;
        } catch (ThreadDeath d) {
            throw d ;
        } catch (Throwable e) {
            unknown = new UNKNOWN( 0, CompletionStatus.COMPLETED_MAYBE ) ;
        }
    }

    // Required SERVICE_CONTEXT_ID and getId definitions
    public static final int SERVICE_CONTEXT_ID = 9 ;
    public int getId() { return SERVICE_CONTEXT_ID ; }

    public void writeData( OutputStream os ) throws SystemException
    {
        os.write_value( (Serializable)unknown ) ;
    }

    public Throwable getUE() { return unknown ; }

    private Throwable unknown = null ;

    public String toString()
    {
        return "UEInfoServiceContext[ unknown=" + unknown.toString() + " ]" ;
    }
}
