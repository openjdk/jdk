/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.corba.se.impl.naming.pcosnaming;

import java.io.File;
import java.util.Properties;

import com.sun.corba.se.impl.orbutil.ORBConstants;
import com.sun.corba.se.impl.orbutil.CorbaResourceUtil;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.activation.InitialNameService;
import com.sun.corba.se.spi.activation.InitialNameServiceHelper;
import org.omg.CosNaming.NamingContext;
/**
 * Class NameServer is a standalone application which
 * implements a persistent and a transient name service.
 * It uses the PersistentNameService and TransientNameService
 * classes for the name service implementation.
 *
 * @author      Hemanth Puttaswamy
 * @since       JDK1.2
 */

public class NameServer
{
    private ORB orb;

    private File dbDir; // name server database directory

    private final static String dbName = "names.db";

    public static void main(String args[])
    {
        NameServer ns = new NameServer(args);
        ns.run();
    }

    protected NameServer(String args[])
    {
        // create the ORB Object
        java.util.Properties props = System.getProperties();
        props.put( ORBConstants.SERVER_ID_PROPERTY, "1000" ) ;
        props.put("org.omg.CORBA.ORBClass",
                  "com.sun.corba.se.impl.orb.ORBImpl");
        orb = (ORB) org.omg.CORBA.ORB.init(args,props);

        // set up the database directory
        String dbDirName = props.getProperty( ORBConstants.DB_DIR_PROPERTY ) +
            props.getProperty("file.separator") + dbName +
            props.getProperty("file.separator");

        dbDir = new File(dbDirName);
        if (!dbDir.exists()) dbDir.mkdir();
    }

    protected void run()
    {
        try {

            // create the persistent name service
            NameService ns = new NameService(orb, dbDir);

            // add root naming context to initial naming
            NamingContext rootContext = ns.initialNamingContext();
            InitialNameService ins = InitialNameServiceHelper.narrow(
                                     orb.resolve_initial_references(
                                     ORBConstants.INITIAL_NAME_SERVICE_NAME ));
            ins.bind( "NameService", rootContext, true);
            System.out.println(CorbaResourceUtil.getText("pnameserv.success"));

            // wait for invocations
            orb.run();

        } catch (Exception ex) {

            ex.printStackTrace(System.err);
        }
    }

}
