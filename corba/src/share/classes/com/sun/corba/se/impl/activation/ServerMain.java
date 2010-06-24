/*
 * Copyright (c) 1997, 2002, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.activation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.io.*;
import java.util.Date;
import java.util.Properties ;

import org.omg.CORBA.ORB ;
import com.sun.corba.se.spi.activation.Activator ;
import com.sun.corba.se.spi.activation.ActivatorHelper ;
import com.sun.corba.se.impl.orbutil.ORBConstants ;

/**
 * @author      Ken Cavanaugh
 * @since       JDK1.2
 */
public class ServerMain
{
    /* TODO:
    * 1.  Rewrite all uses of ORB properties to use constants from someplace.
    *     The strings are scattered between here, the ORB classes, and
    *     ServerTableEntry.
    * 2.  Consider a more general log facility.
    * 3.  Remove ServerCallback from POAORB.
    * 4.  Needs to be merged with Harold's changes to support SSL.
    * 5.  Logs need to be internationalized.
    */

    public final static int OK = 0;
    public final static int MAIN_CLASS_NOT_FOUND = 1;
    public final static int NO_MAIN_METHOD = 2;
    public final static int APPLICATION_ERROR = 3;
    public final static int UNKNOWN_ERROR = 4;
    public final static int NO_SERVER_ID = 5 ;
    public final static int REGISTRATION_FAILED = 6;

    public static String printResult( int result )
    {
        switch (result) {
            case OK :                   return "Server terminated normally" ;
            case MAIN_CLASS_NOT_FOUND : return "main class not found" ;
            case NO_MAIN_METHOD :       return "no main method" ;
            case APPLICATION_ERROR :    return "application error" ;
            case NO_SERVER_ID :         return "server ID not defined" ;
            case REGISTRATION_FAILED:   return "server registration failed" ;
            default :                   return "unknown error" ;
        }
    }

    private void redirectIOStreams()
    {
        // redirect out and err streams
        try {
            String logDirName =
                System.getProperty( ORBConstants.DB_DIR_PROPERTY ) +
                System.getProperty("file.separator") +
                ORBConstants.SERVER_LOG_DIR +
                System.getProperty("file.separator");

            File logDir = new File(logDirName);
            String server = System.getProperty(
                ORBConstants.SERVER_ID_PROPERTY ) ;

            FileOutputStream foutStream =
                new FileOutputStream(logDirName + server+".out", true);
            FileOutputStream ferrStream =
                new FileOutputStream(logDirName + server+".err", true);

            PrintStream pSout = new PrintStream(foutStream, true);
            PrintStream pSerr = new PrintStream(ferrStream, true);

            System.setOut(pSout);
            System.setErr(pSerr);

            logInformation( "Server started" ) ;

        } catch (Exception ex) {}
    }

    /** Write a time-stamped message to the indicated PrintStream.
    */
    private static void writeLogMessage( PrintStream pstream, String msg )
    {
        Date date = new Date();
        pstream.print( "[" + date.toString() + "] " + msg + "\n");
    }

    /** Write information to standard out only.
    */
    public static void logInformation( String msg )
    {
        writeLogMessage( System.out, "        " + msg ) ;
    }

    /** Write error message to standard out and standard err.
    */
    public static void logError( String msg )
    {
        writeLogMessage( System.out, "ERROR:  " + msg ) ;
        writeLogMessage( System.err, "ERROR:  " + msg ) ;
    }

    /** Write final message to log(s) and then terminate by calling
    * System.exit( code ).  If code == OK, write a normal termination
    * message to standard out, otherwise write an abnormal termination
    * message to standard out and standard error.
    */
    public static void logTerminal( String msg, int code )
    {
        if (code == 0) {
            writeLogMessage( System.out, "        " + msg ) ;
        } else {
            writeLogMessage( System.out, "FATAL:  " +
                printResult( code ) + ": " + msg ) ;

            writeLogMessage( System.err, "FATAL:  " +
                printResult( code ) + ": " + msg ) ;
        }

        System.exit( code ) ;
    }

    private Method getMainMethod( Class serverClass )
    {
        Class argTypes[] = new Class[] { String[].class } ;
        Method method = null ;

        try {
            method = serverClass.getDeclaredMethod( "main", argTypes ) ;
        } catch (Exception exc) {
            logTerminal( exc.getMessage(), NO_MAIN_METHOD ) ;
        }

        if (!isPublicStaticVoid( method ))
            logTerminal( "", NO_MAIN_METHOD ) ;

        return method ;
    }

    private boolean isPublicStaticVoid( Method method )
    {
        // check modifiers: public static
        int modifiers =  method.getModifiers ();
        if (!Modifier.isPublic (modifiers) || !Modifier.isStatic (modifiers)) {
            logError( method.getName() + " is not public static" ) ;
            return false ;
        }

        // check return type and exceptions
        if (method.getExceptionTypes ().length != 0) {
            logError( method.getName() + " declares exceptions" ) ;
            return false ;
        }

        if (!method.getReturnType().equals (Void.TYPE)) {
            logError( method.getName() + " does not have a void return type" ) ;
            return false ;
        }

        return true ;
    }

    private Method getNamedMethod( Class serverClass, String methodName )
    {
        Class argTypes[] = new Class[] { org.omg.CORBA.ORB.class } ;
        Method method = null ;

        try {
            method = serverClass.getDeclaredMethod( methodName, argTypes ) ;
        } catch (Exception exc) {
            return null ;
        }

        if (!isPublicStaticVoid( method ))
            return null ;

        return method ;
    }

    private void run(String[] args)
    {
        try {
            redirectIOStreams() ;

            String serverClassName = System.getProperty(
                ORBConstants.SERVER_NAME_PROPERTY ) ;

            // determine the class loader to be used for loading the class
            // since ServerMain is going to be in JDK and we need to have this
            // class to load application classes, this is required here.
            ClassLoader cl = Thread.currentThread().getContextClassLoader();

            if (cl == null)
                cl = ClassLoader.getSystemClassLoader();

            // determine the main class
            Class serverClass = null;

            try {
                // determine the main class, try loading with current class loader
                serverClass = Class.forName( serverClassName ) ;
            } catch (ClassNotFoundException ex) {
                // eat the exception and try to load using SystemClassLoader
                serverClass = Class.forName( serverClassName, true, cl);
            }

            if (debug)
                System.out.println("class " + serverClassName + " found");

            // get the main method
            Method mainMethod = getMainMethod( serverClass ) ;

            // This piece of code is required, to verify the server definition
            // without launching it.

            // verify the server

            boolean serverVerifyFlag = Boolean.getBoolean(
                ORBConstants.SERVER_DEF_VERIFY_PROPERTY) ;
            if (serverVerifyFlag) {
                if (mainMethod == null)
                    logTerminal("", NO_MAIN_METHOD);
                else {
                    if (debug)
                        System.out.println("Valid Server");
                    logTerminal("", OK);
                }
            }


            registerCallback( serverClass ) ;

            // build args to the main and call it
            Object params [] = new Object [1];
            params[0] = args;
            mainMethod.invoke(null, params);

        } catch (ClassNotFoundException e) {
            logTerminal("ClassNotFound exception: " + e.getMessage(),
                MAIN_CLASS_NOT_FOUND);
        } catch (Exception e) {
            logTerminal("Exception: " + e.getMessage(),
                APPLICATION_ERROR);
        }
    }

    public static void main(String[] args) {
        ServerMain server = new ServerMain();
        server.run(args);
    }

    private static final boolean debug = false;

    private int getServerId()
    {
        Integer serverId = Integer.getInteger( ORBConstants.SERVER_ID_PROPERTY ) ;

        if (serverId == null)
            logTerminal( "", NO_SERVER_ID ) ;

        return serverId.intValue() ;
    }

    private void registerCallback( Class serverClass )
    {
        Method installMethod = getNamedMethod( serverClass, "install" ) ;
        Method uninstallMethod = getNamedMethod( serverClass, "uninstall" ) ;
        Method shutdownMethod = getNamedMethod( serverClass, "shutdown" ) ;

        Properties props = new Properties() ;
        props.put( "org.omg.CORBA.ORBClass",
            "com.sun.corba.se.impl.orb.ORBImpl" ) ;
        // NOTE: Very important to pass this property, otherwise the
        // Persistent Server registration will be unsucessfull.
        props.put( ORBConstants.ACTIVATED_PROPERTY, "false" );
        String args[] = null ;
        ORB orb = ORB.init( args, props ) ;

        ServerCallback serverObj = new ServerCallback( orb,
            installMethod, uninstallMethod, shutdownMethod ) ;

        int serverId = getServerId() ;

        try {
            Activator activator = ActivatorHelper.narrow(
                orb.resolve_initial_references( ORBConstants.SERVER_ACTIVATOR_NAME ));
            activator.active(serverId, serverObj);
        } catch (Exception ex) {
            logTerminal( "exception " + ex.getMessage(),
                REGISTRATION_FAILED ) ;
        }
    }
}

class ServerCallback extends
    com.sun.corba.se.spi.activation._ServerImplBase
{
    private ORB orb;
    private Method installMethod ;
    private Method uninstallMethod ;
    private Method shutdownMethod ;
    private Object methodArgs[] ;

    ServerCallback(ORB orb, Method installMethod, Method uninstallMethod,
        Method shutdownMethod )
    {
        this.orb = orb;
        this.installMethod = installMethod ;
        this.uninstallMethod = uninstallMethod ;
        this.shutdownMethod = shutdownMethod ;

        orb.connect( this ) ;

        methodArgs = new Object[] { orb } ;
    }

    private void invokeMethod( Method method )
    {
        if (method != null)
            try {
                method.invoke( null, methodArgs ) ;
            } catch (Exception exc) {
                ServerMain.logError( "could not invoke " + method.getName() +
                    " method: " + exc.getMessage() ) ;
            }
    }

    // shutdown the ORB and wait for completion
    public void shutdown()
    {
        ServerMain.logInformation( "Shutdown starting" ) ;

        invokeMethod( shutdownMethod ) ;

        orb.shutdown(true);

        ServerMain.logTerminal( "Shutdown completed", ServerMain.OK ) ;
    }

    public void install()
    {
        ServerMain.logInformation( "Install starting" ) ;

        invokeMethod( installMethod ) ;

        ServerMain.logInformation( "Install completed" ) ;
    }

    public void uninstall()
    {
        ServerMain.logInformation( "uninstall starting" ) ;

        invokeMethod( uninstallMethod ) ;

        ServerMain.logInformation( "uninstall completed" ) ;
    }
}
