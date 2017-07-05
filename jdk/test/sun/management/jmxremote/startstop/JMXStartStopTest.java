/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.*;
import javax.management.remote.*;

import sun.management.AgentConfigurationError;
import sun.management.jmxremote.ConnectorBootstrap;

public class JMXStartStopTest {

    static boolean verbose = false;

    static void dbg_print(String msg){
        if (verbose) {
            System.err.println("DBG: " +msg);
        }
    }

    static void dbg_print(String msg, Throwable ex){
        if (verbose) {
            System.err.println("DBG: " + msg + " " + ex.getMessage() );
            ex.printStackTrace(System.err);
        }
    }

    public static int listMBeans(MBeanServerConnection server, ObjectName pattern, QueryExp query)
    throws Exception {

        Set names = server.queryNames(pattern,query);
        for (Iterator i=names.iterator(); i.hasNext(); ) {
            ObjectName name = (ObjectName)i.next();
            MBeanInfo info = server.getMBeanInfo(name);
            dbg_print("Got MBean: " + name);

            MBeanAttributeInfo[] attrs = info.getAttributes();
            if (attrs == null)
                continue;

            for (int j=0; j<attrs.length; j++) {
                if (attrs[j].isReadable()) {
                    Object o = server.getAttribute(name,attrs[j].getName());
                }
            }
        }
        return names.size();
    }


    public void run_local(String strPid)
    throws Exception {

        String jmxUrlStr = null;
        int pid = Integer.parseInt(strPid);

        try {
            jmxUrlStr = sun.management.ConnectorAddressLink.importFrom(pid);
            dbg_print("Local Service URL: " +jmxUrlStr);
            if ( jmxUrlStr == null ) {
                throw new Exception("No Service URL. Local agent not started?");
            }

            JMXServiceURL url = new JMXServiceURL(jmxUrlStr);
            Map m = new HashMap();

            JMXConnector c = JMXConnectorFactory.connect(url,m);

            MBeanServerConnection conn = c.getMBeanServerConnection();
            ObjectName pattern = new ObjectName("java.lang:type=Memory,*");

            int count = listMBeans(conn,pattern,null);
            if (count == 0)
                throw new Exception("Expected at least one matching "+ "MBean for "+pattern);


        } catch (IOException e) {
            dbg_print("Cannot find process : " + pid);
            throw e;
        }
    }

    public void run(String args[]) throws Exception {

        dbg_print("RmiRegistry lookup...");

        int port = 4567;
        if (args != null && args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        dbg_print("Using port: " + port);

        int rmiPort = 0;
        if (args != null && args.length > 1) {
            rmiPort = Integer.parseInt(args[1]);
        }
        dbg_print("Using rmi port: " + rmiPort);

        Registry registry = LocateRegistry.getRegistry(port);

        // "jmxrmi"
        String[] relist = registry.list();
        for (int i = 0; i < relist.length; ++i) {
            dbg_print("Got registry: " + relist[i]);
        }

        String jmxUrlStr = (rmiPort != 0) ?
                           String.format("service:jmx:rmi://localhost:%d/jndi/rmi://localhost:%d/jmxrmi", rmiPort, port) :
                           String.format("service:jmx:rmi:///jndi/rmi://localhost:%d/jmxrmi",port);

        JMXServiceURL url = new JMXServiceURL(jmxUrlStr);
        Map m = new HashMap();

        JMXConnector c = JMXConnectorFactory.connect(url,m);

        MBeanServerConnection conn = c.getMBeanServerConnection();
        ObjectName pattern = new ObjectName("java.lang:type=Memory,*");

        int count = listMBeans(conn,pattern,null);
        if (count == 0)
            throw new Exception("Expected at least one matching "+ "MBean for "+pattern);
    }


    public static void main(String args[]) {
        JMXStartStopTest manager = new JMXStartStopTest();
        try {
            if (args!=null && args[0].equals("local")) {
                manager.run_local(args[1]);
            } else {
                manager.run(args);
            }
        } catch (RuntimeException r) {
            dbg_print("No connection: ", r);
            System.out.print("NO_CONN");
            System.exit(1);
        } catch (Throwable t) {
            dbg_print("No connection: ", t);
            System.out.print("NO_CONN");
            System.exit(2);
        }
        System.out.print("OK_CONN");
        System.exit(0);
    }

}
