/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
package sun.management;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXServiceURL;

import static sun.management.AgentConfigurationError.*;
import sun.management.jmxremote.ConnectorBootstrap;
import sun.management.jdp.JdpController;
import sun.management.jdp.JdpException;
import sun.misc.VMSupport;

/**
 * This Agent is started by the VM when -Dcom.sun.management.snmp or
 * -Dcom.sun.management.jmxremote is set. This class will be loaded by the
 * system class loader. Also jmx framework could be started by jcmd
 */
public class Agent {
    // management properties

    private static Properties mgmtProps;
    private static ResourceBundle messageRB;
    private static final String CONFIG_FILE =
            "com.sun.management.config.file";
    private static final String SNMP_PORT =
            "com.sun.management.snmp.port";
    private static final String JMXREMOTE =
            "com.sun.management.jmxremote";
    private static final String JMXREMOTE_PORT =
            "com.sun.management.jmxremote.port";
    private static final String RMI_PORT =
            "com.sun.management.jmxremote.rmi.port";
    private static final String ENABLE_THREAD_CONTENTION_MONITORING =
            "com.sun.management.enableThreadContentionMonitoring";
    private static final String LOCAL_CONNECTOR_ADDRESS_PROP =
            "com.sun.management.jmxremote.localConnectorAddress";
    private static final String SNMP_ADAPTOR_BOOTSTRAP_CLASS_NAME =
            "sun.management.snmp.AdaptorBootstrap";

    private static final String JDP_DEFAULT_ADDRESS = "224.0.23.178";
    private static final int JDP_DEFAULT_PORT = 7095;

    // The only active agent allowed
    private static JMXConnectorServer jmxServer = null;

    // Parse string com.sun.management.prop=xxx,com.sun.management.prop=yyyy
    // and return property set if args is null or empty
    // return empty property set
    private static Properties parseString(String args) {
        Properties argProps = new Properties();
        if (args != null && !args.trim().equals("")) {
            for (String option : args.split(",")) {
                String s[] = option.split("=", 2);
                String name = s[0].trim();
                String value = (s.length > 1) ? s[1].trim() : "";

                if (!name.startsWith("com.sun.management.")) {
                    error(INVALID_OPTION, name);
                }

                argProps.setProperty(name, value);
            }
        }

        return argProps;
    }

    // invoked by -javaagent or -Dcom.sun.management.agent.class
    public static void premain(String args) throws Exception {
        agentmain(args);
    }

    // invoked by attach mechanism
    public static void agentmain(String args) throws Exception {
        if (args == null || args.length() == 0) {
            args = JMXREMOTE;           // default to local management
        }

        Properties arg_props = parseString(args);

        // Read properties from the config file
        Properties config_props = new Properties();
        String fname = arg_props.getProperty(CONFIG_FILE);
        readConfiguration(fname, config_props);

        // Arguments override config file
        config_props.putAll(arg_props);
        startAgent(config_props);
    }

    // jcmd ManagementAgent.start_local entry point
    // Also called due to command-line via startAgent()
    private static synchronized void startLocalManagementAgent() {
        Properties agentProps = VMSupport.getAgentProperties();

        // start local connector if not started
        if (agentProps.get(LOCAL_CONNECTOR_ADDRESS_PROP) == null) {
            JMXConnectorServer cs = ConnectorBootstrap.startLocalConnectorServer();
            String address = cs.getAddress().toString();
            // Add the local connector address to the agent properties
            agentProps.put(LOCAL_CONNECTOR_ADDRESS_PROP, address);

            try {
                // export the address to the instrumentation buffer
                ConnectorAddressLink.export(address);
            } catch (Exception x) {
                // Connector server started but unable to export address
                // to instrumentation buffer - non-fatal error.
                warning(EXPORT_ADDRESS_FAILED, x.getMessage());
            }
        }
    }

    // jcmd ManagementAgent.start entry point
    // This method starts the remote JMX agent and starts neither
    // the local JMX agent nor the SNMP agent
    // @see #startLocalManagementAgent and also @see #startAgent.
    private static synchronized void startRemoteManagementAgent(String args) throws Exception {
        if (jmxServer != null) {
            throw new RuntimeException(getText(INVALID_STATE, "Agent already started"));
        }

        try {
            Properties argProps = parseString(args);
            Properties configProps = new Properties();

            // Load the management properties from the config file
            // if config file is not specified readConfiguration implicitly
            // reads <java.home>/conf/management/management.properties

            String fname = System.getProperty(CONFIG_FILE);
            readConfiguration(fname, configProps);

            // management properties can be overridden by system properties
            // which take precedence
            Properties sysProps = System.getProperties();
            synchronized (sysProps) {
                configProps.putAll(sysProps);
            }

            // if user specifies config file into command line for either
            // jcmd utilities or attach command it overrides properties set in
            // command line at the time of VM start
            String fnameUser = argProps.getProperty(CONFIG_FILE);
            if (fnameUser != null) {
                readConfiguration(fnameUser, configProps);
            }

            // arguments specified in command line of jcmd utilities
            // override both system properties and one set by config file
            // specified in jcmd command line
            configProps.putAll(argProps);

            // jcmd doesn't allow to change ThreadContentionMonitoring, but user
            // can specify this property inside config file, so enable optional
            // monitoring functionality if this property is set
            final String enableThreadContentionMonitoring =
                    configProps.getProperty(ENABLE_THREAD_CONTENTION_MONITORING);

            if (enableThreadContentionMonitoring != null) {
                ManagementFactory.getThreadMXBean().
                        setThreadContentionMonitoringEnabled(true);
            }

            String jmxremotePort = configProps.getProperty(JMXREMOTE_PORT);
            if (jmxremotePort != null) {
                jmxServer = ConnectorBootstrap.
                        startRemoteConnectorServer(jmxremotePort, configProps);

                startDiscoveryService(configProps);
            } else {
                throw new AgentConfigurationError(INVALID_JMXREMOTE_PORT, "No port specified");
            }
        } catch (JdpException e) {
            error(e);
        } catch (AgentConfigurationError err) {
            error(err.getError(), err.getParams());
        }
    }

    private static synchronized void stopRemoteManagementAgent() throws Exception {

        JdpController.stopDiscoveryService();

        if (jmxServer != null) {
            ConnectorBootstrap.unexportRegistry();

            // Attempt to stop already stopped agent
            // Don't cause any errors.
            jmxServer.stop();
            jmxServer = null;
        }
    }

    private static void startAgent(Properties props) throws Exception {
        String snmpPort = props.getProperty(SNMP_PORT);
        String jmxremote = props.getProperty(JMXREMOTE);
        String jmxremotePort = props.getProperty(JMXREMOTE_PORT);

        // Enable optional monitoring functionality if requested
        final String enableThreadContentionMonitoring =
                props.getProperty(ENABLE_THREAD_CONTENTION_MONITORING);
        if (enableThreadContentionMonitoring != null) {
            ManagementFactory.getThreadMXBean().
                    setThreadContentionMonitoringEnabled(true);
        }

        try {
            if (snmpPort != null) {
                loadSnmpAgent(snmpPort, props);
            }

            /*
             * If the jmxremote.port property is set then we start the
             * RMIConnectorServer for remote M&M.
             *
             * If the jmxremote or jmxremote.port properties are set then
             * we start a RMIConnectorServer for local M&M. The address
             * of this "local" server is exported as a counter to the jstat
             * instrumentation buffer.
             */
            if (jmxremote != null || jmxremotePort != null) {
                if (jmxremotePort != null) {
                    jmxServer = ConnectorBootstrap.
                            startRemoteConnectorServer(jmxremotePort, props);
                    startDiscoveryService(props);
                }
                startLocalManagementAgent();
            }

        } catch (AgentConfigurationError e) {
            error(e.getError(), e.getParams());
        } catch (Exception e) {
            error(e);
        }
    }

    private static void startDiscoveryService(Properties props)
            throws IOException, JdpException {
        // Start discovery service if requested
        String discoveryPort = props.getProperty("com.sun.management.jdp.port");
        String discoveryAddress = props.getProperty("com.sun.management.jdp.address");
        String discoveryShouldStart = props.getProperty("com.sun.management.jmxremote.autodiscovery");

        // Decide whether we should start autodicovery service.
        // To start autodiscovery following conditions should be met:
        // autodiscovery==true OR (autodicovery==null AND jdp.port != NULL)

        boolean shouldStart = false;
        if (discoveryShouldStart == null){
            shouldStart = (discoveryPort != null);
        }
        else{
            try{
               shouldStart = Boolean.parseBoolean(discoveryShouldStart);
            } catch (NumberFormatException e) {
                throw new AgentConfigurationError(AGENT_EXCEPTION, "Couldn't parse autodiscovery argument");
            }
        }

        if (shouldStart) {
            // port and address are required arguments and have no default values
            InetAddress address;
            try {
                address = (discoveryAddress == null) ?
                        InetAddress.getByName(JDP_DEFAULT_ADDRESS) : InetAddress.getByName(discoveryAddress);
            } catch (UnknownHostException e) {
                throw new AgentConfigurationError(AGENT_EXCEPTION, e, "Unable to broadcast to requested address");
            }

            int port = JDP_DEFAULT_PORT;
            if (discoveryPort != null) {
               try {
                  port = Integer.parseInt(discoveryPort);
               } catch (NumberFormatException e) {
                 throw new AgentConfigurationError(AGENT_EXCEPTION, "Couldn't parse JDP port argument");
               }
            }

            // Rebuilding service URL to broadcast it
            String jmxremotePort = props.getProperty(JMXREMOTE_PORT);
            String rmiPort = props.getProperty(RMI_PORT);

            JMXServiceURL url = jmxServer.getAddress();
            String hostname = url.getHost();

            String jmxUrlStr = (rmiPort != null)
                    ? String.format(
                    "service:jmx:rmi://%s:%s/jndi/rmi://%s:%s/jmxrmi",
                    hostname, rmiPort, hostname, jmxremotePort)
                    : String.format(
                    "service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", hostname, jmxremotePort);

            String instanceName = props.getProperty("com.sun.management.jdp.name");

            JdpController.startDiscoveryService(address, port, instanceName, jmxUrlStr);
        }
    }

    public static Properties loadManagementProperties() {
        Properties props = new Properties();

        // Load the management properties from the config file

        String fname = System.getProperty(CONFIG_FILE);
        readConfiguration(fname, props);

        // management properties can be overridden by system properties
        // which take precedence
        Properties sysProps = System.getProperties();
        synchronized (sysProps) {
            props.putAll(sysProps);
        }

        return props;
    }

    public static synchronized Properties getManagementProperties() {
        if (mgmtProps == null) {
            String configFile = System.getProperty(CONFIG_FILE);
            String snmpPort = System.getProperty(SNMP_PORT);
            String jmxremote = System.getProperty(JMXREMOTE);
            String jmxremotePort = System.getProperty(JMXREMOTE_PORT);

            if (configFile == null && snmpPort == null
                    && jmxremote == null && jmxremotePort == null) {
                // return if out-of-the-management option is not specified
                return null;
            }
            mgmtProps = loadManagementProperties();
        }
        return mgmtProps;
    }

    private static void loadSnmpAgent(String snmpPort, Properties props) {
        try {
            // invoke the following through reflection:
            //     AdaptorBootstrap.initialize(snmpPort, props);
            final Class<?> adaptorClass =
                    Class.forName(SNMP_ADAPTOR_BOOTSTRAP_CLASS_NAME, true, null);
            final Method initializeMethod =
                    adaptorClass.getMethod("initialize",
                    String.class, Properties.class);
            initializeMethod.invoke(null, snmpPort, props);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException x) {
            // snmp runtime doesn't exist - initialization fails
            throw new UnsupportedOperationException("Unsupported management property: " + SNMP_PORT, x);
        } catch (InvocationTargetException x) {
            final Throwable cause = x.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else if (cause instanceof Error) {
                throw (Error) cause;
            }
            // should not happen...
            throw new UnsupportedOperationException("Unsupported management property: " + SNMP_PORT, cause);
        }
    }

    // read config file and initialize the properties
    private static void readConfiguration(String fname, Properties p) {
        if (fname == null) {
            String home = System.getProperty("java.home");
            if (home == null) {
                throw new Error("Can't find java.home ??");
            }
            StringBuilder defaultFileName = new StringBuilder(home);
            defaultFileName.append(File.separator).append("conf");
            defaultFileName.append(File.separator).append("management");
            defaultFileName.append(File.separator).append("management.properties");
            // Set file name
            fname = defaultFileName.toString();
        }
        final File configFile = new File(fname);
        if (!configFile.exists()) {
            error(CONFIG_FILE_NOT_FOUND, fname);
        }

        InputStream in = null;
        try {
            in = new FileInputStream(configFile);
            BufferedInputStream bin = new BufferedInputStream(in);
            p.load(bin);
        } catch (FileNotFoundException e) {
            error(CONFIG_FILE_OPEN_FAILED, e.getMessage());
        } catch (IOException e) {
            error(CONFIG_FILE_OPEN_FAILED, e.getMessage());
        } catch (SecurityException e) {
            error(CONFIG_FILE_ACCESS_DENIED, fname);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    error(CONFIG_FILE_CLOSE_FAILED, fname);
                }
            }
        }
    }

    public static void startAgent() throws Exception {
        String prop = System.getProperty("com.sun.management.agent.class");

        // -Dcom.sun.management.agent.class not set so read management
        // properties and start agent
        if (prop == null) {
            // initialize management properties
            Properties props = getManagementProperties();
            if (props != null) {
                startAgent(props);
            }
            return;
        }

        // -Dcom.sun.management.agent.class=<agent classname>:<agent args>
        String[] values = prop.split(":");
        if (values.length < 1 || values.length > 2) {
            error(AGENT_CLASS_INVALID, "\"" + prop + "\"");
        }
        String cname = values[0];
        String args = (values.length == 2 ? values[1] : null);

        if (cname == null || cname.length() == 0) {
            error(AGENT_CLASS_INVALID, "\"" + prop + "\"");
        }

        if (cname != null) {
            try {
                // Instantiate the named class.
                // invoke the premain(String args) method
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(cname);
                Method premain = clz.getMethod("premain",
                        new Class<?>[]{String.class});
                premain.invoke(null, /* static */
                        new Object[]{args});
            } catch (ClassNotFoundException ex) {
                error(AGENT_CLASS_NOT_FOUND, "\"" + cname + "\"");
            } catch (NoSuchMethodException ex) {
                error(AGENT_CLASS_PREMAIN_NOT_FOUND, "\"" + cname + "\"");
            } catch (SecurityException ex) {
                error(AGENT_CLASS_ACCESS_DENIED);
            } catch (Exception ex) {
                String msg = (ex.getCause() == null
                        ? ex.getMessage()
                        : ex.getCause().getMessage());
                error(AGENT_CLASS_FAILED, msg);
            }
        }
    }

    public static void error(String key) {
        String keyText = getText(key);
        System.err.print(getText("agent.err.error") + ": " + keyText);
        throw new RuntimeException(keyText);
    }

    public static void error(String key, String[] params) {
        if (params == null || params.length == 0) {
            error(key);
        } else {
            StringBuilder message = new StringBuilder(params[0]);
            for (int i = 1; i < params.length; i++) {
                message.append(' ').append(params[i]);
            }
            error(key, message.toString());
        }
    }

    public static void error(String key, String message) {
        String keyText = getText(key);
        System.err.print(getText("agent.err.error") + ": " + keyText);
        System.err.println(": " + message);
        throw new RuntimeException(keyText + ": " + message);
    }

    public static void error(Exception e) {
        e.printStackTrace();
        System.err.println(getText(AGENT_EXCEPTION) + ": " + e.toString());
        throw new RuntimeException(e);
    }

    public static void warning(String key, String message) {
        System.err.print(getText("agent.err.warning") + ": " + getText(key));
        System.err.println(": " + message);
    }

    private static void initResource() {
        try {
            messageRB =
                    ResourceBundle.getBundle("sun.management.resources.agent");
        } catch (MissingResourceException e) {
            throw new Error("Fatal: Resource for management agent is missing");
        }
    }

    public static String getText(String key) {
        if (messageRB == null) {
            initResource();
        }
        try {
            return messageRB.getString(key);
        } catch (MissingResourceException e) {
            return "Missing management agent resource bundle: key = \"" + key + "\"";
        }
    }

    public static String getText(String key, String... args) {
        if (messageRB == null) {
            initResource();
        }
        String format = messageRB.getString(key);
        if (format == null) {
            format = "missing resource key: key = \"" + key + "\", "
                    + "arguments = \"{0}\", \"{1}\", \"{2}\"";
        }
        return MessageFormat.format(format, (Object[]) args);
    }
}
