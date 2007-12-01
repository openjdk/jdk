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
package com.sun.corba.se.impl.naming.namingutil;

import org.omg.CORBA.CompletionStatus;
import java.util.StringTokenizer;

/**
 *  This class is the entry point to parse different types of INS URL's.
 *
 *  @Author Hemanth
 */

public class INSURLHandler {

    private static INSURLHandler insURLHandler = null;

    // Length of corbaloc:
    private static final int CORBALOC_PREFIX_LENGTH = 9;

    // Length of corbaname:
    private static final int CORBANAME_PREFIX_LENGTH = 10;

    private INSURLHandler( ) {
    }

    public synchronized static INSURLHandler getINSURLHandler( ) {
        if( insURLHandler == null ) {
            insURLHandler = new INSURLHandler( );
        }
        return insURLHandler;
    }

    public INSURL parseURL( String aUrl ) {
        String url = aUrl;
        if ( url.startsWith( "corbaloc:" ) == true ) {
            return new CorbalocURL( url.substring( CORBALOC_PREFIX_LENGTH ) );
        } else if ( url.startsWith ( "corbaname:" ) == true ) {
            return new CorbanameURL( url.substring( CORBANAME_PREFIX_LENGTH ) );
        }
        return null;
    }
}
