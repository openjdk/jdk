/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.cosnaming;

import java.io.*;
import org.omg.CosNaming.NameComponent;


public class NamingUtils {
    // Do not instantiate this class
    private NamingUtils() {};

    /**
     * Debug flag which must be true for debug streams to be created and
     * dprint output to be generated.
     */
    public static boolean debug = false;

    /**
     * Prints the message to the debug stream if debugging is enabled.
     * @param msg the debug message to print.
     */
    public static void dprint(String msg) {
        if (debug && debugStream != null)
            debugStream.println(msg);
    }

    /**
     * Prints the message to the error stream (System.err is default).
     * @param msg the error message to print.
     */
    public static void errprint(String msg) {
        if (errStream != null)
            errStream.println(msg);
        else
            System.err.println(msg);
    }

    /**
     * Prints the stacktrace of the supplied exception to the error stream.
     * @param e any Java exception.
     */
    public static void printException(java.lang.Exception e) {
        if (errStream != null)
            e.printStackTrace(errStream);
        else
            e.printStackTrace();
    }

    /**
     * Create a debug print stream to the supplied log file.
     * @param logFile the file to which debug output will go.
     * @exception IOException thrown if the file cannot be opened for output.
     */
    public static void makeDebugStream(File logFile)
        throws java.io.IOException {
        // Create an outputstream for debugging
        java.io.OutputStream logOStream =
            new java.io.FileOutputStream(logFile);
        java.io.DataOutputStream logDStream =
            new java.io.DataOutputStream(logOStream);
        debugStream = new java.io.PrintStream(logDStream);

        // Emit first message
        debugStream.println("Debug Stream Enabled.");
    }

    /**
     * Create a error print stream to the supplied file.
     * @param errFile the file to which error messages will go.
     * @exception IOException thrown if the file cannot be opened for output.
     */
    public static void makeErrStream(File errFile)
        throws java.io.IOException {
        if (debug) {
            // Create an outputstream for errors
            java.io.OutputStream errOStream =
                new java.io.FileOutputStream(errFile);
            java.io.DataOutputStream errDStream =
                new java.io.DataOutputStream(errOStream);
            errStream = new java.io.PrintStream(errDStream);
            dprint("Error stream setup completed.");
        }
    }


    /**
     * A utility method that takes Array of NameComponent and converts
     * into a directory structured name in the format of /id1.kind1/id2.kind2..
     * This is used mainly for Logging.
     */
    static String getDirectoryStructuredName( NameComponent[] name ) {
        StringBuffer directoryStructuredName = new StringBuffer("/");
        for( int i = 0; i < name.length; i++ ) {
            directoryStructuredName.append( name[i].id + "." + name[i].kind );
        }
        return directoryStructuredName.toString( );
    }

    /**
     * The debug printstream.
     */
    public static java.io.PrintStream debugStream;

    /**
     * The error printstream.
     */
    public static java.io.PrintStream errStream;
}
