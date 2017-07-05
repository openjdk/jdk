/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.management;

import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

import javax.management.remote.JMXConnectorServer;

import sun.management.jmxremote.ConnectorBootstrap;
import static sun.management.AgentConfigurationError.*;
import sun.misc.VMSupport;

/**
 * This Agent is started by the VM when -Dcom.sun.management.snmp
 * or -Dcom.sun.management.jmxremote is set. This class will be
 * loaded by the system class loader.
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
    private static final String ENABLE_THREAD_CONTENTION_MONITORING =
        "com.sun.management.enableThreadContentionMonitoring";
    private static final String LOCAL_CONNECTOR_ADDRESS_PROP =
        "com.sun.management.jmxremote.localConnectorAddress";

    private static final String SNMP_ADAPTOR_BOOTSTRAP_CLASS_NAME =
            "sun.management.snmp.AdaptorBootstrap";

    // invoked by -javaagent or -Dcom.sun.management.agent.class
    public static void premain(String args) throws Exception {
        agentmain(args);
    }

    // invoked by attach mechanism
    public static void agentmain(String args) throws Exception {
        if (args == null || args.length() == 0) {
            args = JMXREMOTE;           // default to local management
        }

        // Parse agent options into properties

        Properties arg_props = new Properties();
        if (args != null) {
            String[] options = args.split(",");
            for (int i=0; i<options.length; i++) {
                String[] option = options[i].split("=");
                if (option.length >= 1 && option.length <= 2) {
                    String name = option[0];
                    String value = (option.length == 1) ? "" : option[1];
                    if (name != null && name.length() > 0) {

                        // Assume that any com.sun.management.* options are okay
                        if (name.startsWith("com.sun.management.")) {
                            arg_props.setProperty(name, value);
                        } else {
                            error(INVALID_OPTION, name);
                        }
                    }
                }
            }
        }

        // Read properties from the config file
        Properties config_props = new Properties();
        String fname = arg_props.getProperty(CONFIG_FILE);
        readConfiguration(fname, config_props);

        // Arguments override config file
        config_props.putAll(arg_props);
        startAgent(config_props);
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
                    ConnectorBootstrap.initialize(jmxremotePort, props);
                }

                Properties agentProps = VMSupport.getAgentProperties();
                // start local connector if not started
                // System.out.println("local address : " +
                  //     agentProps.get(LOCAL_CONNECTOR_ADDRESS_PROP));
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
        } catch (AgentConfigurationError e) {
            error(e.getError(), e.getParams());
        } catch (Exception e) {
            error(e);
        }
    }

    public static Properties loadManagementProperties() {
        Properties props = new Properties();

        // Load the management properties from the config file

        String fname = System.getProperty(CONFIG_FILE);
        readConfiguration(fname, props);

        // management properties can be overridden by system properties
        // which take precedence
        props.putAll(System.getProperties());

        return props;
    }

    public static synchronized Properties getManagementProperties() {
        if (mgmtProps == null) {
            String configFile = System.getProperty(CONFIG_FILE);
            String snmpPort = System.getProperty(SNMP_PORT);
            String jmxremote = System.getProperty(JMXREMOTE);
            String jmxremotePort = System.getProperty(JMXREMOTE_PORT);

            if (configFile == null && snmpPort == null &&
                jmxremote == null && jmxremotePort == null) {
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
                Class.forName(SNMP_ADAPTOR_BOOTSTRAP_CLASS_NAME,true,null);
            final Method initializeMethod =
                    adaptorClass.getMethod("initialize",
                        String.class, Properties.class);
            initializeMethod.invoke(null,snmpPort,props);
        } catch (ClassNotFoundException x) {
            // The SNMP packages are not present: throws an exception.
            throw new UnsupportedOperationException("Unsupported management property: " + SNMP_PORT,x);
        } catch (NoSuchMethodException x) {
            // should not happen...
            throw new UnsupportedOperationException("Unsupported management property: " + SNMP_PORT,x);
        } catch (InvocationTargetException x) {
            final Throwable cause = x.getCause();
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            else if (cause instanceof Error)
                throw (Error) cause;
            // should not happen...
            throw new UnsupportedOperationException("Unsupported management property: " + SNMP_PORT,cause);
        } catch (IllegalAccessException x) {
            // should not happen...
            throw new UnsupportedOperationException("Unsupported management property: " + SNMP_PORT,x);
        }
    }

    // read config file and initialize the properties
    private static void readConfiguration(String fname, Properties p) {
        if (fname == null) {
            String home = System.getProperty("java.home");
            if (home == null) {
                throw new Error("Can't find java.home ??");
            }
            StringBuffer defaultFileName = new StringBuffer(home);
            defaultFileName.append(File.separator).append("lib");
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
                                               new Class[] { String.class });
                premain.invoke(null, /* static */
                               new Object[] { args });
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
            StringBuffer message = new StringBuffer(params[0]);
            for (int i = 1; i < params.length; i++) {
                message.append(" " + params[i]);
            }
            error(key, message.toString());
        }
    }


    public static void error(String key, String message) {
        String keyText = getText(key);
        System.err.print(getText("agent.err.error") + ": " + keyText);
        System.err.println(": " + message);
        throw new RuntimeException(keyText);
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
            format = "missing resource key: key = \"" + key + "\", " +
                "arguments = \"{0}\", \"{1}\", \"{2}\"";
        }
        return MessageFormat.format(format, (Object[]) args);
    }

}
