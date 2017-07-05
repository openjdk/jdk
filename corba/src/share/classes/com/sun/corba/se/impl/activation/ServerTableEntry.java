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
package com.sun.corba.se.impl.activation;

/**
 *
 * @author      Anita Jindal
 * @since       JDK1.2
 */

import org.omg.CORBA.CompletionStatus;

import com.sun.corba.se.spi.activation.Server;
import com.sun.corba.se.spi.activation.EndPointInfo;
import com.sun.corba.se.spi.activation.ORBAlreadyRegistered;
import com.sun.corba.se.spi.activation.ORBPortInfo;
import com.sun.corba.se.spi.activation.InvalidORBid;
import com.sun.corba.se.spi.activation.ServerHeldDown;
import com.sun.corba.se.spi.activation.RepositoryPackage.ServerDef;
import com.sun.corba.se.spi.activation.IIOP_CLEAR_TEXT;
import com.sun.corba.se.spi.orb.ORB ;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.logging.ActivationSystemException ;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ServerTableEntry
{

    private final static int DE_ACTIVATED = 0;
    private final static int ACTIVATING   = 1;
    private final static int ACTIVATED    = 2;
    private final static int RUNNING      = 3;
    private final static int HELD_DOWN    = 4;


    private String printState()
    {
        String str = "UNKNOWN";

        switch (state) {
        case (DE_ACTIVATED) : str = "DE_ACTIVATED"; break;
        case (ACTIVATING  ) : str = "ACTIVATING  "; break;
        case (ACTIVATED   ) : str = "ACTIVATED   "; break;
        case (RUNNING     ) : str = "RUNNING     "; break;
        case (HELD_DOWN   ) : str = "HELD_DOWN   "; break;
        default: break;
        }

        return str;
    }

    private final static long waitTime    = 2000;
    private static final int ActivationRetryMax = 5;

    // state of each entry
    private int state;
    private int serverId;
    private HashMap orbAndPortInfo;
    private Server serverObj;
    private ServerDef serverDef;
    private Process process;
    private int activateRetryCount=0;
    private String activationCmd;
    private ActivationSystemException wrapper ;
    public String toString()
    {
        return "ServerTableEntry[" + "state=" + printState() +
            " serverId=" + serverId +
            " activateRetryCount=" + activateRetryCount + "]" ;
    }

    // get the string needed to make the activation command
    private static String javaHome, classPath, fileSep, pathSep;

    static {
        javaHome  = System.getProperty("java.home");
        classPath = System.getProperty("java.class.path");
        fileSep   = System.getProperty("file.separator");
        pathSep   = System.getProperty("path.separator");
    }

    ServerTableEntry( ActivationSystemException wrapper,
        int serverId, ServerDef serverDef, int initialPort,
        String dbDirName, boolean verify, boolean debug )
    {
        this.wrapper = wrapper ;
        this.serverId = serverId;
        this.serverDef = serverDef;
        this.debug = debug ;
        // create a HashMap with capacity 255
        // Since all methods are synchronized, we don't need any
        // additional synchronization mechanisms
        orbAndPortInfo = new HashMap(255);

        activateRetryCount = 0;
        state = ACTIVATING;

        // compute the activation command
        activationCmd =

            // add path to the java vm
            javaHome + fileSep + "bin" + fileSep + "java " +

            // add any arguments to the server Java VM
            serverDef.serverVmArgs + " " +

            // add ORB properties
            "-Dioser=" + System.getProperty( "ioser" ) + " " +
            "-D" + ORBConstants.INITIAL_PORT_PROPERTY   + "=" + initialPort + " " +
            "-D" + ORBConstants.DB_DIR_PROPERTY         + "=" + dbDirName + " " +
            "-D" + ORBConstants.ACTIVATED_PROPERTY      + "=true " +
            "-D" + ORBConstants.SERVER_ID_PROPERTY      + "=" + serverId + " " +
            "-D" + ORBConstants.SERVER_NAME_PROPERTY    + "=" + serverDef.serverName + " " +
            // we need to pass in the verify flag, so that the server is not
            // launched, when we try to validate its definition during registration
            // into the RepositoryImpl

            (verify ? "-D" + ORBConstants.SERVER_DEF_VERIFY_PROPERTY + "=true ": "") +

            // add classpath to the server
            "-classpath " + classPath +
            (serverDef.serverClassPath.equals("") == true ? "" : pathSep) +
            serverDef.serverClassPath +

            // add server class name and arguments
            " com.sun.corba.se.impl.activation.ServerMain " + serverDef.serverArgs

            // Add the debug flag, if any
            + (debug ? " -debug" : "") ;

        if (debug) System.out.println(
                                      "ServerTableEntry constructed with activation command " +
                                      activationCmd);
    }

    /**
     * Verify whether the server definition is valid.
     */
    public int verify()
    {
        try {

            if (debug)
                System.out.println("Server being verified w/" + activationCmd);

            process = Runtime.getRuntime().exec(activationCmd);
            int result = process.waitFor();
            if (debug)
                printDebug( "verify", "returns " + ServerMain.printResult( result ) ) ;
            return result ;
        } catch (Exception e) {
            if (debug)
                printDebug( "verify", "returns unknown error because of exception " +
                            e ) ;
            return ServerMain.UNKNOWN_ERROR;
        }
    }

    private void printDebug(String method, String msg)
    {
        System.out.println("ServerTableEntry: method  =" + method);
        System.out.println("ServerTableEntry: server  =" + serverId);
        System.out.println("ServerTableEntry: state   =" + printState());
        System.out.println("ServerTableEntry: message =" + msg);
        System.out.println();
    }

    synchronized void activate() throws org.omg.CORBA.SystemException
    {
        state = ACTIVATED;

        try {
            if (debug)
                printDebug("activate", "activating server");
            process = Runtime.getRuntime().exec(activationCmd);
        } catch (Exception e) {
            deActivate();
            if (debug)
                printDebug("activate", "throwing premature process exit");
            throw wrapper.unableToStartProcess() ;
        }
    }

    synchronized void register(Server server)
    {
        if (state == ACTIVATED) {

            serverObj = server;

            //state = RUNNING;
            //notifyAll();

            if (debug)
                printDebug("register", "process registered back");

        } else {

            if (debug)
                printDebug("register", "throwing premature process exit");
            throw wrapper.serverNotExpectedToRegister() ;
        }
    }

    synchronized void registerPorts( String orbId, EndPointInfo [] endpointList)
        throws ORBAlreadyRegistered
    {

        // find if the ORB is already registered, then throw an exception
        if (orbAndPortInfo.containsKey(orbId)) {
            throw new ORBAlreadyRegistered(orbId);
        }

        // store all listener ports and their types
        int numListenerPorts = endpointList.length;
        EndPointInfo [] serverListenerPorts = new EndPointInfo[numListenerPorts];

        for (int i = 0; i < numListenerPorts; i++) {
            serverListenerPorts[i] = new EndPointInfo (endpointList[i].endpointType, endpointList[i].port);
        if (debug)
            System.out.println("registering type: " + serverListenerPorts[i].endpointType  +  "  port  " + serverListenerPorts[i].port);
        }

        // put this set of listener ports in the HashMap associated
        // with the orbId
        orbAndPortInfo.put(orbId, serverListenerPorts);
        if (state == ACTIVATED) {
            state = RUNNING;
            notifyAll();
        }
        // _REVISIT_, If the state is not equal to ACTIVATED then it is a bug
        // need to log that error, once the Logging framework is in place
        // for rip-int.
        if (debug)
            printDebug("registerPorts", "process registered Ports");
    }

    void install()
    {
        Server localServerObj = null;
        synchronized ( this ) {
            if (state == RUNNING)
                localServerObj = serverObj;
            else
                throw wrapper.serverNotRunning() ;
        }
        if (localServerObj != null) {
            localServerObj.install() ;
        }

    }

    void uninstall()
    {
        Server localServerObj = null;
        Process localProcess = null;

        synchronized (this) {
            localServerObj = serverObj;
            localProcess = process;

            if (state == RUNNING) {

                deActivate();

            } else {
                throw wrapper.serverNotRunning() ;
            }
        }
        try {
            if (localServerObj != null) {
                localServerObj.shutdown(); // shutdown the server
                localServerObj.uninstall() ; // call the uninstall
            }

            if (localProcess != null) {
                localProcess.destroy();
            }
        } catch (Exception ex) {
            // what kind of exception should be thrown
        }
    }

    synchronized void holdDown()
    {
        state = HELD_DOWN;

        if (debug)
            printDebug( "holdDown", "server held down" ) ;

        notifyAll();
    }

    synchronized void deActivate()
    {
        state = DE_ACTIVATED;

        if (debug)
            printDebug( "deActivate", "server deactivated" ) ;

        notifyAll();
    }

    synchronized void checkProcessHealth( ) {
        // If the State in the ServerTableEntry is RUNNING and the
        // Process was shut down abnormally, The method will change the
        // server state as De-Activated.
        if( state == RUNNING ) {
            try {
                int exitVal = process.exitValue();
            } catch (IllegalThreadStateException e1) {
                return;
            }
            synchronized ( this ) {
                // Clear the PortInformation as it is old
                orbAndPortInfo.clear();
                // Move the state to De-Activated, So that the next
                // call to this server will re-activate.
                deActivate();
            }
        }
    }

    synchronized boolean isValid()
    {
        if ((state == ACTIVATING) || (state == HELD_DOWN)) {
            if (debug)
                printDebug( "isValid", "returns true" ) ;

            return true;
        }

        try {
            int exitVal = process.exitValue();
        } catch (IllegalThreadStateException e1) {
            return true;
        }

        if (state == ACTIVATED) {
            if (activateRetryCount < ActivationRetryMax) {
                if (debug)
                    printDebug("isValid", "reactivating server");
                activateRetryCount++;
                activate();
                return true;
            }

            if (debug)
                printDebug("isValid", "holding server down");

            holdDown();
            return true;
        }

        deActivate();
        return false;
    }

    synchronized ORBPortInfo[] lookup(String endpointType) throws ServerHeldDown
    {
        while ((state == ACTIVATING) || (state == ACTIVATED)) {
            try {
                wait(waitTime);
                if (!isValid()) break;
            } catch(Exception e) {}
        }
        ORBPortInfo[] orbAndPortList = null;

        if (state == RUNNING) {
            orbAndPortList = new ORBPortInfo[orbAndPortInfo.size()];
            Iterator setORBids = orbAndPortInfo.keySet().iterator();

            try {
                int numElements = 0;
                int i;
                int port;
                while (setORBids.hasNext()) {
                    String orbId = (String) setORBids.next();
                    // get an entry corresponding to orbId
                    EndPointInfo [] serverListenerPorts = (EndPointInfo []) orbAndPortInfo.get(orbId);
                    port = -1;
                    // return the port corresponding to the endpointType
                    for (i = 0; i < serverListenerPorts.length; i++) {
                        if (debug)
                            System.out.println("lookup num-ports " + serverListenerPorts.length + "   " +
                                serverListenerPorts[i].endpointType + "   " +
                                serverListenerPorts[i].port );
                        if ((serverListenerPorts[i].endpointType).equals(endpointType)) {
                            port = serverListenerPorts[i].port;
                            break;
                        }
                    }
                    orbAndPortList[numElements] = new ORBPortInfo(orbId, port);
                    numElements++;
                }
            } catch (NoSuchElementException e) {
                // have everything in the table
            }
            return orbAndPortList;
        }

        if (debug)
            printDebug("lookup", "throwing server held down error");

        throw new ServerHeldDown( serverId ) ;
    }

    synchronized EndPointInfo[] lookupForORB(String orbId)
        throws ServerHeldDown, InvalidORBid
    {
        while ((state == ACTIVATING) || (state == ACTIVATED)) {
            try {
                wait(waitTime);
                if (!isValid()) break;
            } catch(Exception e) {}
        }
        EndPointInfo[] portList = null;

        if (state == RUNNING) {

            try {

                // get an entry corresponding to orbId
                EndPointInfo [] serverListenerPorts = (EndPointInfo []) orbAndPortInfo.get(orbId);

                portList = new EndPointInfo[serverListenerPorts.length];
                // return the port corresponding to the endpointType
                for (int i = 0; i < serverListenerPorts.length; i++) {
                   if (debug)
                      System.out.println("lookup num-ports " + serverListenerPorts.length + "   "
                             + serverListenerPorts[i].endpointType + "   " +
                             serverListenerPorts[i].port );
                   portList[i] = new EndPointInfo(serverListenerPorts[i].endpointType, serverListenerPorts[i].port);
                }
            } catch (NoSuchElementException e) {
                // no element in HashMap corresponding to ORBid found
                throw new InvalidORBid();
            }
            return portList;
        }

        if (debug)
            printDebug("lookup", "throwing server held down error");

        throw new ServerHeldDown( serverId ) ;
    }

    synchronized String[] getORBList()
    {
        String [] orbList = new String[orbAndPortInfo.size()];
        Iterator setORBids = orbAndPortInfo.keySet().iterator();

        try {
            int numElements = 0;
            while (setORBids.hasNext()) {
                String orbId = (String) setORBids.next();
                orbList[numElements++] = orbId ;
            }
        } catch (NoSuchElementException e) {
            // have everything in the table
        }
        return orbList;
    }

    int getServerId()
    {
        return serverId;
    }

    boolean isActive()
    {
        return (state == RUNNING) || (state == ACTIVATED);
    }

    synchronized void destroy()
    {

        Server localServerObj = null;
        Process localProcess = null;

        synchronized (this) {
            localServerObj = serverObj;
            localProcess = process;

            deActivate();
        }

        try {
            if (localServerObj != null)
                localServerObj.shutdown();

            if (debug)
                printDebug( "destroy", "server shutdown successfully" ) ;
        } catch (Exception ex) {
            if (debug)
                printDebug( "destroy",
                            "server shutdown threw exception" + ex ) ;
            // ex.printStackTrace();
        }

        try {
            if (localProcess != null)
                localProcess.destroy();

            if (debug)
                printDebug( "destroy", "process destroyed successfully" ) ;
        } catch (Exception ex) {
            if (debug)
                printDebug( "destroy",
                            "process destroy threw exception" + ex ) ;

            // ex.printStackTrace();
        }
    }

    private boolean debug = false;
}
