/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.spi.logging ;

import java.util.logging.Level ;
import java.util.logging.Logger ;
import java.util.logging.LogRecord ;

public abstract class LogWrapperBase {
    protected Logger logger ;

    protected String loggerName ;

    protected LogWrapperBase( Logger logger )
    {
        this.logger = logger ;
        this.loggerName = logger.getName( );
    }

    protected void doLog( Level level, String key, Object[] params, Class wrapperClass,
        Throwable thr )
    {
        LogRecord lrec = new LogRecord( level, key ) ;
        if (params != null)
            lrec.setParameters( params ) ;
        inferCaller( wrapperClass, lrec ) ;
        lrec.setThrown( thr ) ;
        lrec.setLoggerName( loggerName );
        lrec.setResourceBundle( logger.getResourceBundle() ) ;
        logger.log( lrec ) ;
    }

    private void inferCaller( Class wrapperClass, LogRecord lrec )
    {
        // Private method to infer the caller's class and method names

        // Get the stack trace.
        StackTraceElement stack[] = (new Throwable()).getStackTrace();
        StackTraceElement frame = null ;
        String wcname = wrapperClass.getName() ;
        String baseName = LogWrapperBase.class.getName() ;

        // The top of the stack should always be a method in the wrapper class,
        // or in this base class.
        // Search back to the first method not in the wrapper class or this class.
        int ix = 0;
        while (ix < stack.length) {
            frame = stack[ix];
            String cname = frame.getClassName();
            if (!cname.equals(wcname) && !cname.equals(baseName))  {
                break;
            }

            ix++;
        }

        // Set the class and method if we are not past the end of the stack
        // trace
        if (ix < stack.length) {
            lrec.setSourceClassName(frame.getClassName());
            lrec.setSourceMethodName(frame.getMethodName());
        }
    }

    protected void doLog( Level level, String key, Class wrapperClass, Throwable thr )
    {
        doLog( level, key, null, wrapperClass, thr ) ;
    }
}
