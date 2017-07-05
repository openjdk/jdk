/*
 * Copyright (c) 2006, 2011, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.jmx.examples.scandir;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.rmi.ssl.SslRMIClientSocketFactory;

/**
 * The ScanDirClient class is a very simple programmatic client example
 * which is able to connect to a secured JMX <i>scandir</i> application.
 * <p>The program initialize the connection environment map with the
 * appropriate properties and credentials, and then connects to the
 * secure JMX <i>scandir</i> daemon.</p>
 * <p>It gets the application's current configuration and prints it on
 * its <code>System.out</code>.</p>
 * <p>The {@link #main main} method takes two arguments: the host on which
 * the server is running (localhost), and the port number
 * that was configured to start the server RMI Connector (4545).
 * </p>
 * @author Sun Microsystems, 2006 - All rights reserved.
 **/
public class ScanDirClient {

    // This class has only a main.
    private ScanDirClient() { }

    /**
     * The usage string for the ScanDirClient.
     */
    public static final String USAGE = ScanDirClient.class.getSimpleName() +
            " <server-host> <rmi-port-number>";

    /**
     * Connects to a secured JMX <i>scandir</i> application.
     * @param args The {@code main} method takes two parameters:
     *        <ul>
     *        <li>args[0] must be the server's host</li>
     *        <li>args[1] must be the rmi port number at which the
     *        JMX <i>scandir</i> daemon is listening for connections
     *        - that is, the port number of its JMX RMI Connector which
     *        was configured in {@code management.properties}
     *        </li>
     *        <ul>
     **/
    public static void main(String[] args) {
        try {
            // Check args
            //
            if (args==null || args.length!=2) {
                System.err.println("Bad number of arguments: usage is: \n\t" +
                        USAGE);
                System.exit(1);
            }
            try {
                InetAddress.getByName(args[0]);
            } catch (UnknownHostException x) {
                System.err.println("No such host: " + args[0]+
                            "\n usage is: \n\t" + USAGE);
                System.exit(2);
            } catch (Exception x) {
                System.err.println("Bad address: " + args[0]+
                            "\n usage is: \n\t" + USAGE);
                System.exit(2);
            }
            try {
                if (Integer.parseInt(args[1]) <= 0) {
                    System.err.println("Bad port value: " + args[1]+
                            "\n usage is: \n\t" + USAGE);
                    System.exit(2);
                }
            } catch (Exception x) {
                System.err.println("Bad argument: " + args[1]+
                        "\n usage is: \n\t" + USAGE);
                System.exit(2);
            }

            // Create an environment map to hold connection properties
            // like credentials etc... We will later pass this map
            // to the JMX Connector.
            //
            System.out.println("\nInitialize the environment map");
            final Map<String,Object> env = new HashMap<String,Object>();

            // Provide the credentials required by the server
            // to successfully perform user authentication
            //
            final String[] credentials = new String[] { "guest" , "guestpasswd" };
            env.put("jmx.remote.credentials", credentials);

            // Provide the SSL/TLS-based RMI Client Socket Factory required
            // by the JNDI/RMI Registry Service Provider to communicate with
            // the SSL/TLS-protected RMI Registry
            //
            env.put("com.sun.jndi.rmi.factory.socket",
                    new SslRMIClientSocketFactory());

            // Create the RMI connector client and
            // connect it to the RMI connector server
            // args[0] is the server's host - localhost
            // args[1] is the secure server port - 4545
            //
            System.out.println("\nCreate the RMI connector client and " +
                    "connect it to the RMI connector server");
            final JMXServiceURL url = new JMXServiceURL(
                    "service:jmx:rmi:///jndi/rmi://"+args[0]+":"+args[1] +
                    "/jmxrmi");

            System.out.println("Connecting to: "+url);
            final JMXConnector jmxc = JMXConnectorFactory.connect(url, env);

            // Get an MBeanServerConnection
            //
            System.out.println("\nGet the MBeanServerConnection");
            final MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();

            // Create a proxy for the ScanManager MXBean
            //
            final ScanManagerMXBean proxy =
                    ScanManager.newSingletonProxy(mbsc);

            // Get the ScanDirConfig MXBean from the scan manager
            //
            System.out.println(
                    "\nGet ScanDirConfigMXBean from ScanManagerMXBean");
            final ScanDirConfigMXBean configMBean =
                    proxy.getConfigurationMBean();

            // Print the scan dir configuration
            //
            System.out.println(
                    "\nGet 'Configuration' attribute on ScanDirConfigMXBean");
            System.out.println("\nConfiguration:\n" +
                    configMBean.getConfiguration());

            // Try to invoke the "close" method on the ScanManager MXBean.
            //
            // Should get a SecurityException as the user "guest" doesn't
            // have readwrite access.
            //
            System.out.println("\nInvoke 'close' on ScanManagerMXBean");
            try {
                proxy.close();
            } catch (SecurityException e) {
                System.out.println("\nGot expected security exception: " + e);
            }

            // Close MBeanServer connection
            //
            System.out.println("\nClose the connection to the server");
            jmxc.close();
            System.out.println("\nBye! Bye!");
        } catch (Exception e) {
            System.out.println("\nGot unexpected exception: " + e);
            e.printStackTrace();
            System.exit(3);
        }
    }
}
