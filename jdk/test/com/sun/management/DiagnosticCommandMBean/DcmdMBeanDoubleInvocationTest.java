/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug     7150256
 * @summary Basic Test for the DiagnosticCommandMBean
 * @author  Frederic Parain
 *
 * @run main/othervm -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.port=8125 DcmdMBeanDoubleInvocationTest
 */


import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.Descriptor;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.*;
import javax.management.remote.*;

public class DcmdMBeanDoubleInvocationTest {

    private static String HOTSPOT_DIAGNOSTIC_MXBEAN_NAME =
        "com.sun.management:type=DiagnosticCommand";

    public static void main(String[] args) {
        MBeanServerConnection mbs = null;
        try {
            JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:8125/jmxrmi");
            JMXConnector connector = JMXConnectorFactory.connect(url);
            mbs = connector.getMBeanServerConnection();
        } catch(Throwable t) {
            t.printStackTrace();
        }
        ObjectName name;
        try {
            name = new ObjectName(HOTSPOT_DIAGNOSTIC_MXBEAN_NAME);
            MBeanInfo info = mbs.getMBeanInfo(name);
            String[] helpArgs = {"-all", "\n", "VM.version"};
            Object[] dcmdArgs = {helpArgs};
            String[] signature = {String[].class.getName()};
            String result = (String) mbs.invoke(name, "help", dcmdArgs, signature);
            System.out.println(result);
         } catch (RuntimeMBeanException ex) {
            if (ex.getCause() instanceof IllegalArgumentException) {
                System.out.println("Test passed");
                return;
            } else {
                ex.printStackTrace();
                throw new RuntimeException("TEST FAILED");
            }
        } catch (InstanceNotFoundException | IntrospectionException
                | ReflectionException | MalformedObjectNameException
                | MBeanException|IOException ex) {
            ex.printStackTrace();
            throw new RuntimeException("TEST FAILED");
        }
        System.out.println("Double commands have not been detected");
        throw new RuntimeException("TEST FAILED");
    }
}
