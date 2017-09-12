/*
 * Copyright (c) 1997, 2004, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Vector;

import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.INITIALIZE;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.SystemException;

import com.sun.corba.se.spi.activation.BadServerDefinition;
import com.sun.corba.se.spi.activation.RepositoryPackage.ServerDef;
import com.sun.corba.se.spi.activation._RepositoryImplBase;
import com.sun.corba.se.spi.activation.ServerAlreadyRegistered;
import com.sun.corba.se.spi.activation.ServerAlreadyInstalled;
import com.sun.corba.se.spi.activation.ServerAlreadyUninstalled;
import com.sun.corba.se.spi.activation.ServerNotRegistered;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketEndPointInfo;
import com.sun.corba.se.spi.transport.SocketOrChannelAcceptor;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.impl.orbutil.ORBConstants;

import com.sun.corba.se.spi.logging.CORBALogDomains;
import com.sun.corba.se.impl.logging.ActivationSystemException;

/**
 *
 * @author      Rohit Garg
 * @since       JDK1.2
 */
public class RepositoryImpl extends _RepositoryImplBase
    implements Serializable
{

    // added serialver computed by the tool
    private static final long serialVersionUID = 8458417785209341858L;

    RepositoryImpl(ORB orb, File dbDir, boolean debug)
    {
        this.debug = debug ;
        this.orb = orb;
        wrapper = ActivationSystemException.get( orb, CORBALogDomains.ORBD_REPOSITORY ) ;

        // if databse does not exist, create it otherwise read it in
        File dbFile = new File(dbDir, "servers.db");
        if (!dbFile.exists()) {
            db = new RepositoryDB(dbFile);
            db.flush();
        } else {
            try {
                FileInputStream fis = new FileInputStream(dbFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                db = (RepositoryDB) ois.readObject();
                ois.close();
            } catch (Exception e) {
                throw wrapper.cannotReadRepositoryDb( e ) ;
            }
        }

        // export the repository
        orb.connect(this);
    }

    private String printServerDef( ServerDef sd )
    {
        return "ServerDef[applicationName=" + sd.applicationName +
            " serverName=" + sd.serverName +
            " serverClassPath=" + sd.serverClassPath +
            " serverArgs=" + sd. serverArgs +
            " serverVmArgs=" + sd.serverVmArgs +
            "]" ;
    }

    public int registerServer(ServerDef serverDef, int theServerId)
        throws ServerAlreadyRegistered
    {
        int         serverId;
        DBServerDef server = null;

        synchronized (db) {

            // check if server already registered
            Enumeration enumeration = db.serverTable.elements();
            while (enumeration.hasMoreElements()) {
                server = (DBServerDef) enumeration.nextElement();
                if (serverDef.applicationName.equals(server.applicationName)) {
                    if (debug)
                        System.out.println(
                            "RepositoryImpl: registerServer called " +
                            "to register ServerDef " +
                            printServerDef( serverDef ) +
                            " with " + ((theServerId==illegalServerId) ?
                        "a new server Id" : ("server Id " + theServerId)) +
                                           " FAILED because it is already registered." ) ;

                    throw (new ServerAlreadyRegistered(server.id));
                }
            }

            // generate a new server id
            if (theServerId == illegalServerId)
                serverId = db.incrementServerIdCounter();
            else
                serverId = theServerId;

            // add server def to the database
            server = new DBServerDef(serverDef, serverId);
            db.serverTable.put(new Integer(serverId), server);
            db.flush();

            if (debug)
                if (theServerId==illegalServerId)
                    System.out.println( "RepositoryImpl: registerServer called " +
                                        "to register ServerDef " + printServerDef( serverDef ) +
                                        " with new serverId " + serverId ) ;
                else
                    System.out.println( "RepositoryImpl: registerServer called " +
                                        "to register ServerDef " + printServerDef( serverDef ) +
                                        " with assigned serverId " + serverId ) ;

            return serverId;
        }
    }

    public int registerServer(ServerDef serverDef)
        throws ServerAlreadyRegistered, BadServerDefinition
    {
        // verify that the entry is valid
        LegacyServerSocketEndPointInfo endpoint =
            orb.getLegacyServerSocketManager()
                .legacyGetEndpoint(LegacyServerSocketEndPointInfo.BOOT_NAMING);
        int initSvcPort = ((SocketOrChannelAcceptor)endpoint)
            .getServerSocket().getLocalPort();
        ServerTableEntry entry = new ServerTableEntry( wrapper,
            illegalServerId, serverDef, (int) initSvcPort, "", true, debug );

        switch (entry.verify()) {
        case ServerMain.OK:
            break;
        case ServerMain.MAIN_CLASS_NOT_FOUND:
            throw new BadServerDefinition("main class not found.");
        case ServerMain.NO_MAIN_METHOD:
            throw new BadServerDefinition("no main method found.");
        case ServerMain.APPLICATION_ERROR:
            throw new BadServerDefinition("server application error.");
        default:
            throw new BadServerDefinition("unknown Exception.");
        }

        return registerServer(serverDef, illegalServerId);
    }

    public void unregisterServer(int serverId) throws ServerNotRegistered {

        DBServerDef server = null;
        Integer id = new Integer(serverId);

        synchronized (db) {

            // check to see if the server is registered
            server = (DBServerDef) db.serverTable.get(id);
            if (server == null)  {
                if (debug)
                    System.out.println(
                                       "RepositoryImpl: unregisterServer for serverId " +
                                       serverId + " called: server not registered" ) ;

                throw (new ServerNotRegistered());
            }

            // remove server from the database
            db.serverTable.remove(id);
            db.flush();
        }

        if (debug)
            System.out.println(
                               "RepositoryImpl: unregisterServer for serverId " + serverId +
                               " called" ) ;
    }

    private DBServerDef getDBServerDef(int serverId) throws ServerNotRegistered
    {
        Integer id = new Integer(serverId);
        DBServerDef server = (DBServerDef) db.serverTable.get(id);

        if (server == null)
            throw new ServerNotRegistered( serverId );

        return server ;
    }

    public ServerDef getServer(int serverId) throws ServerNotRegistered
    {
        DBServerDef server = getDBServerDef( serverId ) ;

        ServerDef serverDef = new ServerDef(server.applicationName, server.name,
                                            server.classPath, server.args, server.vmArgs);

        if (debug)
            System.out.println(
                               "RepositoryImpl: getServer for serverId " + serverId +
                               " returns " + printServerDef( serverDef ) ) ;

        return serverDef;
    }

    public boolean isInstalled(int serverId) throws ServerNotRegistered {
        DBServerDef server = getDBServerDef( serverId ) ;
        return server.isInstalled ;
    }

    public void install( int serverId )
        throws ServerNotRegistered, ServerAlreadyInstalled
    {
        DBServerDef server = getDBServerDef( serverId ) ;

        if (server.isInstalled)
            throw new ServerAlreadyInstalled( serverId ) ;
        else {
            server.isInstalled = true ;
            db.flush() ;
        }
    }

    public void uninstall( int serverId )
        throws ServerNotRegistered, ServerAlreadyUninstalled
    {
        DBServerDef server = getDBServerDef( serverId ) ;

        if (!server.isInstalled)
            throw new ServerAlreadyUninstalled( serverId ) ;
        else {
            server.isInstalled = false ;
            db.flush() ;
        }
    }

    public int[] listRegisteredServers() {
        synchronized (db) {
            int i=0;

            int servers[] = new int[db.serverTable.size()];

            Enumeration enumeration = db.serverTable.elements();

            while (enumeration.hasMoreElements()) {
                DBServerDef server = (DBServerDef) enumeration.nextElement();
                servers[i++] = server.id;
            }

            if (debug) {
                StringBuffer sb = new StringBuffer() ;
                for (int ctr=0; ctr<servers.length; ctr++) {
                    sb.append( ' ' ) ;
                    sb.append( servers[ctr] ) ;
                }

                System.out.println(
                                   "RepositoryImpl: listRegisteredServers returns" +
                                   sb.toString() ) ;
            }

            return servers;
        }
    }

    public int getServerID(String applicationName) throws ServerNotRegistered {
        synchronized (db) {
            int result = -1 ;

            for (Enumeration serverIds = db.serverTable.keys();
                 serverIds.hasMoreElements();)
                {
                    Integer nextServerId = (Integer) serverIds.nextElement();
                    DBServerDef dbServerDef =
                        (DBServerDef) db.serverTable.get(nextServerId);

                    if (dbServerDef.applicationName.equals(applicationName)) {
                        result = nextServerId.intValue();
                        break ;
                    }
                }

            if (debug)
                System.out.println("RepositoryImpl: getServerID for " +
                                   applicationName + " is " + result ) ;

            if (result == -1) {
                throw (new ServerNotRegistered());
            } else {
                return result ;
            }
        }
    }

    public String[] getApplicationNames() {
        synchronized (db) {
            Vector v = new Vector();
            for (Enumeration serverIds = db.serverTable.keys();
                 serverIds.hasMoreElements();)
                {
                    Integer nextServerId = (Integer) serverIds.nextElement();

                    DBServerDef dbServerDef = (DBServerDef)db.serverTable.get(
                                                                              nextServerId);

                    if (!dbServerDef.applicationName.equals(""))
                        v.addElement( dbServerDef.applicationName ) ;
                }

            String[] apps = new String[v.size()];
            for (int i = 0; i < v.size(); i++) {
                apps[i] = (String)v.elementAt(i);
            }

            if (debug) {
                StringBuffer sb = new StringBuffer() ;
                for (int ctr=0; ctr<apps.length; ctr++) {
                    sb.append( ' ' ) ;
                    sb.append( apps[ctr] ) ;
                }

                System.out.println( "RepositoryImpl: getApplicationNames returns " +
                                    sb.toString() ) ;
            }

            return apps;
        }
    }
    /**
     * Typically the Repositoy is created within the ORBd VM but it can
     * be independently started as well.
     */
    public static void main(String args[]) {
        boolean debug = false ;
        for (int ctr=0; ctr<args.length; ctr++)
            if (args[ctr].equals("-debug"))
                debug = true ;

        try {
            // See Bug 4396928 for more information about why we are
            // initializing the ORBClass to PIORB (now ORBImpl, but see the bug).
            Properties props = new Properties();
            props.put("org.omg.CORBA.ORBClass",
                "com.sun.corba.se.impl.orb.ORBImpl");
            ORB orb = (ORB) ORB.init(args, props);

            // create the repository object
            String db = System.getProperty( ORBConstants.DB_PROPERTY,
                    ORBConstants.DEFAULT_DB_NAME );
            RepositoryImpl repository = new RepositoryImpl(orb, new File(db),
                                                           debug);

            // wait for shutdown
            orb.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    transient private boolean debug = false;

    final static int illegalServerId = -1;

    transient private RepositoryDB db = null;

    transient ORB orb = null;

    transient ActivationSystemException wrapper ;

    class RepositoryDB implements Serializable
    {
        File            db;
        Hashtable       serverTable;
        Integer         serverIdCounter;

        RepositoryDB(File dbFile) {

            db = dbFile;

            // initialize the Server Id counter and hashtable.
            // the lower id range is reserved for system servers
            serverTable     = new Hashtable(255);
            serverIdCounter = new Integer(256);
        }

        int incrementServerIdCounter()
        {
            int value = serverIdCounter.intValue();
            serverIdCounter = new Integer(++value);

            return value;
        }

        void flush()
        {
            try {
                db.delete();
                FileOutputStream fos = new FileOutputStream(db);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(this);
                oos.flush();
                oos.close();
            } catch (Exception ex) {
                throw wrapper.cannotWriteRepositoryDb( ex ) ;
            }
        }
    }

    class DBServerDef implements Serializable
    {
        public String toString() {
            return "DBServerDef(applicationName=" + applicationName +
                ", name=" + name +
                ", classPath=" + classPath +
                ", args=" + args +
                ", vmArgs=" + vmArgs +
                ", id=" + id +
                ", isInstalled=" + isInstalled + ")" ;
        }

        DBServerDef(ServerDef server, int server_id) {
            applicationName     = server.applicationName ;
            name        = server.serverName;
            classPath   = server.serverClassPath;
            args        = server.serverArgs;
            vmArgs      = server.serverVmArgs;
            id          = server_id;
            isInstalled = false ;
        }

        String applicationName;
        String name;
        String classPath;
        String args;
        String vmArgs;
        boolean isInstalled ;
        int    id;
    }
}
