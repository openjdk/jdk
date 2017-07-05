/*
 *
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
 *
 */

package com.sun.corba.se.impl.activation;

import java.io.File;
import java.util.Properties;

import org.omg.CORBA.INITIALIZE;
import org.omg.CORBA.INTERNAL;
import org.omg.CORBA.CompletionStatus;
import org.omg.CosNaming.NamingContext;
import org.omg.PortableServer.POA;

import com.sun.corba.se.pept.transport.Acceptor;

import com.sun.corba.se.spi.activation.Repository;
import com.sun.corba.se.spi.activation.RepositoryPackage.ServerDef;
import com.sun.corba.se.spi.activation.Locator;
import com.sun.corba.se.spi.activation.LocatorHelper;
import com.sun.corba.se.spi.activation.Activator;
import com.sun.corba.se.spi.activation.ActivatorHelper;
import com.sun.corba.se.spi.activation.ServerAlreadyRegistered;
import com.sun.corba.se.spi.legacy.connection.LegacyServerSocketEndPointInfo;
import com.sun.corba.se.spi.transport.SocketInfo;
import com.sun.corba.se.spi.orb.ORB;

import com.sun.corba.se.impl.legacy.connection.SocketFactoryAcceptorImpl;
import com.sun.corba.se.impl.naming.cosnaming.TransientNameService;
import com.sun.corba.se.impl.naming.pcosnaming.NameService;
import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.CorbaResourceUtil;
import com.sun.corba.se.impl.transport.SocketOrChannelAcceptorImpl;

/**
 *
 * @author      Rohit Garg
 * @since       JDK1.2
 */
public class ORBD
{
    private int initSvcPort;

    protected void initializeBootNaming(ORB orb)
    {
        // create a bootstrap server
        initSvcPort = orb.getORBData().getORBInitialPort();

        Acceptor acceptor;
        // REVISIT: see ORBConfigurator. use factory in TransportDefault.
        if (orb.getORBData().getLegacySocketFactory() == null) {
            acceptor =
                new SocketOrChannelAcceptorImpl(
                    orb,
                    initSvcPort,
                    LegacyServerSocketEndPointInfo.BOOT_NAMING,
                    SocketInfo.IIOP_CLEAR_TEXT);
        } else {
            acceptor =
                new SocketFactoryAcceptorImpl(
                    orb,
                    initSvcPort,
                    LegacyServerSocketEndPointInfo.BOOT_NAMING,
                    SocketInfo.IIOP_CLEAR_TEXT);
        }
        orb.getCorbaTransportManager().registerAcceptor(acceptor);
    }

    protected ORB createORB(String[] args)
    {
        Properties props = System.getProperties();

        // For debugging.
        //props.put( ORBConstants.DEBUG_PROPERTY, "naming" ) ;
        //props.put( ORBConstants.DEBUG_PROPERTY, "transport,giop,naming" ) ;

        props.put( ORBConstants.SERVER_ID_PROPERTY, "1000" ) ;
        props.put( ORBConstants.PERSISTENT_SERVER_PORT_PROPERTY,
            props.getProperty( ORBConstants.ORBD_PORT_PROPERTY,
                Integer.toString(
                    ORBConstants.DEFAULT_ACTIVATION_PORT ) ) ) ;

        // See Bug 4396928 for more information about why we are initializing
        // the ORBClass to PIORB (now ORBImpl, but should check the bugid).
        props.put("org.omg.CORBA.ORBClass",
            "com.sun.corba.se.impl.orb.ORBImpl");

        return (ORB) ORB.init(args, props);
    }

    private void run(String[] args)
    {
        try {
            // parse the args and try setting the values for these
            // properties
            processArgs(args);

            ORB orb = createORB(args);

            if (orb.orbdDebugFlag)
                System.out.println( "ORBD begins initialization." ) ;

            boolean firstRun = createSystemDirs( ORBConstants.DEFAULT_DB_DIR );

            startActivationObjects(orb);

            if (firstRun) // orbd is being run the first time
                installOrbServers(getRepository(), getActivator());

            if (orb.orbdDebugFlag) {
                System.out.println( "ORBD is ready." ) ;
                System.out.println("ORBD serverid: " +
                        System.getProperty(ORBConstants.SERVER_ID_PROPERTY));
                System.out.println("activation dbdir: " +
                        System.getProperty(ORBConstants.DB_DIR_PROPERTY));
                System.out.println("activation port: " +
                        System.getProperty(ORBConstants.ORBD_PORT_PROPERTY));

                String pollingTime = System.getProperty(
                    ORBConstants.SERVER_POLLING_TIME);
                if( pollingTime == null ) {
                    pollingTime = Integer.toString(
                        ORBConstants.DEFAULT_SERVER_POLLING_TIME );
                }
                System.out.println("activation Server Polling Time: " +
                        pollingTime + " milli-seconds ");

                String startupDelay = System.getProperty(
                    ORBConstants.SERVER_STARTUP_DELAY);
                if( startupDelay == null ) {
                    startupDelay = Integer.toString(
                        ORBConstants.DEFAULT_SERVER_STARTUP_DELAY );
                }
                System.out.println("activation Server Startup Delay: " +
                        startupDelay + " milli-seconds " );
            }

            // The following two lines start the Persistent NameService
            NameServiceStartThread theThread =
                new NameServiceStartThread( orb, dbDir );
            theThread.start( );

            orb.run();
        } catch( org.omg.CORBA.COMM_FAILURE cex ) {
            System.out.println( CorbaResourceUtil.getText("orbd.commfailure"));
            System.out.println( cex );
            cex.printStackTrace();
        } catch( org.omg.CORBA.INTERNAL iex ) {
            System.out.println( CorbaResourceUtil.getText(
                "orbd.internalexception"));
            System.out.println( iex );
            iex.printStackTrace();
        } catch (Exception ex) {
            System.out.println(CorbaResourceUtil.getText(
                "orbd.usage", "orbd"));
            System.out.println( ex );
            ex.printStackTrace();
        }
    }

    private void processArgs(String[] args)
    {
        Properties props = System.getProperties();
        for (int i=0; i < args.length; i++) {
            if (args[i].equals("-port")) {
                if ((i+1) < args.length) {
                    props.put(ORBConstants.ORBD_PORT_PROPERTY, args[++i]);
                } else {
                    System.out.println(CorbaResourceUtil.getText(
                        "orbd.usage", "orbd"));
                }
            } else if (args[i].equals("-defaultdb")) {
                if ((i+1) < args.length) {
                    props.put(ORBConstants.DB_DIR_PROPERTY, args[++i]);
                } else {
                    System.out.println(CorbaResourceUtil.getText(
                        "orbd.usage", "orbd"));
                }
            } else if (args[i].equals("-serverid")) {
                if ((i+1) < args.length) {
                    props.put(ORBConstants.SERVER_ID_PROPERTY, args[++i]);
                } else {
                    System.out.println(CorbaResourceUtil.getText(
                        "orbd.usage", "orbd"));
                }
            } else if (args[i].equals("-serverPollingTime")) {
                if ((i+1) < args.length) {
                    props.put(ORBConstants.SERVER_POLLING_TIME, args[++i]);
                } else {
                    System.out.println(CorbaResourceUtil.getText(
                        "orbd.usage", "orbd"));
                }
            } else if (args[i].equals("-serverStartupDelay")) {
                if ((i+1) < args.length) {
                    props.put(ORBConstants.SERVER_STARTUP_DELAY, args[++i]);
                } else {
                    System.out.println(CorbaResourceUtil.getText(
                        "orbd.usage", "orbd"));
                }
            }
        }
    }

    /**
     * Ensure that the Db directory exists. If not, create the Db
     * and the log directory and return true. Otherwise return false.
     */
    protected boolean createSystemDirs(String defaultDbDir)
    {
        boolean dirCreated = false;
        Properties props = System.getProperties();
        String fileSep = props.getProperty("file.separator");

        // determine the ORB db directory
        dbDir = new File (props.getProperty( ORBConstants.DB_DIR_PROPERTY,
            props.getProperty("user.dir") + fileSep + defaultDbDir));

        // create the db and the logs directories
        dbDirName = dbDir.getAbsolutePath();
        props.put(ORBConstants.DB_DIR_PROPERTY, dbDirName);
        if (!dbDir.exists()) {
            dbDir.mkdir();
            dirCreated = true;
        }

        File logDir = new File (dbDir, ORBConstants.SERVER_LOG_DIR ) ;
        if (!logDir.exists()) logDir.mkdir();

        return dirCreated;
    }

    protected File dbDir;
    protected File getDbDir()
    {
        return dbDir;
    }

    private String dbDirName;
    protected String getDbDirName()
    {
        return dbDirName;
    }

    protected void startActivationObjects(ORB orb) throws Exception
    {
        // create Initial Name Service object
        initializeBootNaming(orb);

        // create Repository object
        repository = new RepositoryImpl(orb, dbDir, orb.orbdDebugFlag );
        orb.register_initial_reference( ORBConstants.SERVER_REPOSITORY_NAME, repository );

        // create Locator and Activator objects
        ServerManagerImpl serverMgr =
            new ServerManagerImpl( orb,
                                   orb.getCorbaTransportManager(),
                                   repository,
                                   getDbDirName(),
                                   orb.orbdDebugFlag );

        locator = LocatorHelper.narrow(serverMgr);
        orb.register_initial_reference( ORBConstants.SERVER_LOCATOR_NAME, locator );

        activator = ActivatorHelper.narrow(serverMgr);
        orb.register_initial_reference( ORBConstants.SERVER_ACTIVATOR_NAME, activator );

        // start Name Service
        TransientNameService nameService = new TransientNameService(orb,
            ORBConstants.TRANSIENT_NAME_SERVICE_NAME);
    }

    protected Locator locator;
    protected Locator getLocator()
    {
        return locator;
    }

    protected Activator activator;
    protected Activator getActivator()
    {
        return activator;
    }

    protected RepositoryImpl repository;
    protected RepositoryImpl getRepository()
    {
        return repository;
    }

    /**
     * Go through the list of ORB Servers and initialize and start
     * them up.
     */
    protected void installOrbServers(RepositoryImpl repository,
                                     Activator activator)
    {
        int serverId;
        String[] server;
        ServerDef serverDef;

        for (int i=0; i < orbServers.length; i++) {
            try {
                server = orbServers[i];
                serverDef = new ServerDef(server[1], server[2],
                                          server[3], server[4], server[5] );

                serverId = Integer.valueOf(orbServers[i][0]).intValue();

                repository.registerServer(serverDef, serverId);

                activator.activate(serverId);

            } catch (Exception ex) {}
        }
    }

    public static void main(String[] args) {
        ORBD orbd = new ORBD();
        orbd.run(args);
    }

    /**
     * List of servers to be auto registered and started by the ORBd.
     *
     * Each server entry is of the form {id, name, path, args, vmargs}.
     */
    private static String[][] orbServers = {
        {""}
    };
}
