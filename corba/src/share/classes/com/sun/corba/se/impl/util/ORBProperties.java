/*
 * Copyright 1998-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

public class ORBProperties {

    public static final String ORB_CLASS =
        "org.omg.CORBA.ORBClass=com.sun.corba.se.impl.orb.ORBImpl";
    public static final String ORB_SINGLETON_CLASS =
        "org.omg.CORBA.ORBSingletonClass=com.sun.corba.se.impl.orb.ORBSingleton";

    public static void main (String[] args) {

        try {
            // Check if orb.properties exists
            String javaHome = System.getProperty("java.home");
            File propFile = new File(javaHome + File.separator
                                     + "lib" + File.separator
                                     + "orb.properties");

            if (propFile.exists())
                return;

            // Write properties to orb.properties
            FileOutputStream out = new FileOutputStream(propFile);
            PrintWriter pw = new PrintWriter(out);

            try {
                pw.println(ORB_CLASS);
                pw.println(ORB_SINGLETON_CLASS);
            } finally {
                pw.close();
                out.close();
            }

        } catch (Exception ex) { }

    }
}
