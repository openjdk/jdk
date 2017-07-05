/*
 * Copyright (c) 1999, 2001, Oracle and/or its affiliates. All rights reserved.
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

/*
 * (C) Copyright IBM Corp. 1993 - 1997 - All Rights Reserved
 *
 * The original version of this source code and documentation is
 * copyrighted and owned by IBM, Inc. These materials are provided under
 * terms of a License Agreement between IBM and Sun. This technology is
 * protected by multiple US and International patents. This notice and
 * attribution to IBM may not be removed.
 *
 */


package javax.rmi.CORBA;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Properties;

class GetORBPropertiesFileAction implements PrivilegedAction {
    private boolean debug = false ;

    public GetORBPropertiesFileAction () {
    }

    private String getSystemProperty(final String name) {
        // This will not throw a SecurityException because this
        // class was loaded from rt.jar using the bootstrap classloader.
        String propValue = (String) AccessController.doPrivileged(
            new PrivilegedAction() {
                public java.lang.Object run() {
                    return System.getProperty(name);
                }
            }
        );

        return propValue;
    }

    private void getPropertiesFromFile( Properties props, String fileName )
    {
        try {
            File file = new File( fileName ) ;
            if (!file.exists())
                return ;

            FileInputStream in = new FileInputStream( file ) ;

            try {
                props.load( in ) ;
            } finally {
                in.close() ;
            }
        } catch (Exception exc) {
            if (debug)
                System.out.println( "ORB properties file " + fileName +
                    " not found: " + exc) ;
        }
    }

    public Object run()
    {
        Properties defaults = new Properties() ;

        String javaHome = getSystemProperty( "java.home" ) ;
        String fileName = javaHome + File.separator + "lib" + File.separator +
            "orb.properties" ;

        getPropertiesFromFile( defaults, fileName ) ;

        Properties results = new Properties( defaults ) ;

        String userHome = getSystemProperty( "user.home" ) ;
        fileName = userHome + File.separator + "orb.properties" ;

        getPropertiesFromFile( results, fileName ) ;
        return results ;
    }
}
