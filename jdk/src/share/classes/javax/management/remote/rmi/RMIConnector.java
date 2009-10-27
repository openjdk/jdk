/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management.remote.rmi;

import com.sun.jmx.mbeanserver.Util;
import com.sun.jmx.remote.internal.ClientCommunicatorAdmin;
import com.sun.jmx.remote.internal.ClientListenerInfo;
import com.sun.jmx.remote.internal.ClientNotifForwarder;
import com.sun.jmx.remote.internal.ProxyRef;
import com.sun.jmx.remote.internal.IIOPHelper;
import com.sun.jmx.remote.util.ClassLogger;
import com.sun.jmx.remote.util.EnvHelp;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.io.WriteAbortedException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.rmi.MarshalException;
import java.rmi.MarshalledObject;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RemoteObject;
import java.rmi.server.RemoteObjectInvocationHandler;
import java.rmi.server.RemoteRef;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.InvalidAttributeValueException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationFilter;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.NotificationResult;
import javax.management.remote.JMXAddressable;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.security.auth.Subject;
import sun.rmi.server.UnicastRef2;
import sun.rmi.transport.LiveRef;

/**
 * <p>A connection to a remote RMI connector.  Usually, such
 * connections are made using {@link
 * javax.management.remote.JMXConnectorFactory JMXConnectorFactory}.
 * However, specialized applications can use this class directly, for
 * example with an {@link RMIServer} stub obtained without going
 * through JNDI.</p>
 *
 * @since 1.5
 */
public class RMIConnector implements JMXConnector, Serializable, JMXAddressable {

    private static final ClassLogger logger =
            new ClassLogger("javax.management.remote.rmi", "RMIConnector");

    private static final long serialVersionUID = 817323035842634473L;

    private RMIConnector(RMIServer rmiServer, JMXServiceURL address,
            Map<String, ?> environment) {
        if (rmiServer == null && address == null) throw new
                IllegalArgumentException("rmiServer and jmxServiceURL both null");

        initTransients();

        this.rmiServer = rmiServer;
        this.jmxServiceURL = address;
        if (environment == null) {
            this.env = Collections.emptyMap();
        } else {
            EnvHelp.checkAttributes(environment);
            this.env = Collections.unmodifiableMap(environment);
        }
    }

    /**
     * <p>Constructs an <code>RMIConnector</code> that will connect
     * the RMI connector server with the given address.</p>
     *
     * <p>The address can refer directly to the connector server,
     * using one of the following syntaxes:</p>
     *
     * <pre>
     * service:jmx:rmi://<em>[host[:port]]</em>/stub/<em>encoded-stub</em>
     * service:jmx:iiop://<em>[host[:port]]</em>/ior/<em>encoded-IOR</em>
     * </pre>
     *
     * <p>(Here, the square brackets <code>[]</code> are not part of the
     * address but indicate that the host and port are optional.)</p>
     *
     * <p>The address can instead indicate where to find an RMI stub
     * through JNDI, using one of the following syntaxes:</p>
     *
     * <pre>
     * service:jmx:rmi://<em>[host[:port]]</em>/jndi/<em>jndi-name</em>
     * service:jmx:iiop://<em>[host[:port]]</em>/jndi/<em>jndi-name</em>
     * </pre>
     *
     * <p>An implementation may also recognize additional address
     * syntaxes, for example:</p>
     *
     * <pre>
     * service:jmx:iiop://<em>[host[:port]]</em>/stub/<em>encoded-stub</em>
     * </pre>
     *
     * @param url the address of the RMI connector server.
     *
     * @param environment additional attributes specifying how to make
     * the connection.  For JNDI-based addresses, these attributes can
     * usefully include JNDI attributes recognized by {@link
     * InitialContext#InitialContext(Hashtable) InitialContext}.  This
     * parameter can be null, which is equivalent to an empty Map.
     *
     * @exception IllegalArgumentException if <code>url</code>
     * is null.
     */
    public RMIConnector(JMXServiceURL url, Map<String,?> environment) {
        this(null, url, environment);
    }

    /**
     * <p>Constructs an <code>RMIConnector</code> using the given RMI stub.
     *
     * @param rmiServer an RMI stub representing the RMI connector server.
     * @param environment additional attributes specifying how to make
     * the connection.  This parameter can be null, which is
     * equivalent to an empty Map.
     *
     * @exception IllegalArgumentException if <code>rmiServer</code>
     * is null.
     */
    public RMIConnector(RMIServer rmiServer, Map<String,?> environment) {
        this(rmiServer, null, environment);
    }

    /**
     * <p>Returns a string representation of this object.  In general,
     * the <code>toString</code> method returns a string that
     * "textually represents" this object. The result should be a
     * concise but informative representation that is easy for a
     * person to read.</p>
     *
     * @return a String representation of this object.
     **/
    @Override
    public String toString() {
        final StringBuilder b = new StringBuilder(this.getClass().getName());
        b.append(":");
        if (rmiServer != null) {
            b.append(" rmiServer=").append(rmiServer.toString());
        }
        if (jmxServiceURL != null) {
            if (rmiServer!=null) b.append(",");
            b.append(" jmxServiceURL=").append(jmxServiceURL.toString());
        }
        return b.toString();
    }

    /**
     * <p>The address of this connector.</p>
     *
     * @return the address of this connector, or null if it
     * does not have one.
     *
     * @since 1.6
     */
    public JMXServiceURL getAddress() {
        return jmxServiceURL;
    }

    //--------------------------------------------------------------------
    // implements JMXConnector interface
    //--------------------------------------------------------------------
    public void connect() throws IOException {
        connect(null);
    }

    public synchronized void connect(Map<String,?> environment)
    throws IOException {
        final boolean tracing = logger.traceOn();
        String        idstr   = (tracing?"["+this.toString()+"]":null);

        if (terminated) {
            logger.trace("connect",idstr + " already closed.");
            throw new IOException("Connector closed");
        }
        if (connected) {
            logger.trace("connect",idstr + " already connected.");
            return;
        }

        try {
            if (tracing) logger.trace("connect",idstr + " connecting...");

            final Map<String, Object> usemap =
                    new HashMap<String, Object>((this.env==null) ?
                        Collections.<String, Object>emptyMap() : this.env);


            if (environment != null) {
                EnvHelp.checkAttributes(environment);
                usemap.putAll(environment);
            }

            // Get RMIServer stub from directory or URL encoding if needed.
            if (tracing) logger.trace("connect",idstr + " finding stub...");
            RMIServer stub = (rmiServer!=null)?rmiServer:
                findRMIServer(jmxServiceURL, usemap);

            // Check for secure RMIServer stub if the corresponding
            // client-side environment property is set to "true".
            //
            boolean checkStub = EnvHelp.computeBooleanFromString(
                    usemap,
                    "jmx.remote.x.check.stub",false);
            if (checkStub) checkStub(stub, rmiServerImplStubClass);

            // Connect IIOP Stub if needed.
            if (tracing) logger.trace("connect",idstr + " connecting stub...");
            stub = connectStub(stub,usemap);
            idstr = (tracing?"["+this.toString()+"]":null);

            // Calling newClient on the RMIServer stub.
            if (tracing)
                logger.trace("connect",idstr + " getting connection...");
            Object credentials = usemap.get(CREDENTIALS);
            connection = getConnection(stub, credentials, checkStub);

            // Always use one of:
            //   ClassLoader provided in Map at connect time,
            //   or contextClassLoader at connect time.
            if (tracing)
                logger.trace("connect",idstr + " getting class loader...");
            defaultClassLoader = EnvHelp.resolveClientClassLoader(usemap);

            usemap.put(JMXConnectorFactory.DEFAULT_CLASS_LOADER,
                    defaultClassLoader);

            rmiNotifClient = new RMINotifClient(defaultClassLoader, usemap);

            env = usemap;
            final long checkPeriod = EnvHelp.getConnectionCheckPeriod(usemap);
            communicatorAdmin = new RMIClientCommunicatorAdmin(checkPeriod);

            connected = true;

            // The connectionId variable is used in doStart(), when
            // reconnecting, to identify the "old" connection.
            //
            connectionId = getConnectionId();

            Notification connectedNotif =
                    new JMXConnectionNotification(JMXConnectionNotification.OPENED,
                    this,
                    connectionId,
                    clientNotifSeqNo++,
                    "Successful connection",
                    null);
            sendNotification(connectedNotif);

            if (tracing) logger.trace("connect",idstr + " done...");
        } catch (IOException e) {
            if (tracing)
                logger.trace("connect",idstr + " failed to connect: " + e);
            throw e;
        } catch (RuntimeException e) {
            if (tracing)
                logger.trace("connect",idstr + " failed to connect: " + e);
            throw e;
        } catch (NamingException e) {
            final String msg = "Failed to retrieve RMIServer stub: " + e;
            if (tracing) logger.trace("connect",idstr + " " + msg);
            throw EnvHelp.initCause(new IOException(msg),e);
        }
    }

    public synchronized String getConnectionId() throws IOException {
        if (terminated || !connected) {
            if (logger.traceOn())
                logger.trace("getConnectionId","["+this.toString()+
                        "] not connected.");

            throw new IOException("Not connected");
        }

        // we do a remote call to have an IOException if the connection is broken.
        // see the bug 4939578
        return connection.getConnectionId();
    }

    public synchronized MBeanServerConnection getMBeanServerConnection()
    throws IOException {
        return getMBeanServerConnection(null);
    }

    public synchronized MBeanServerConnection
            getMBeanServerConnection(Subject delegationSubject)
            throws IOException {

        if (terminated) {
            if (logger.traceOn())
                logger.trace("getMBeanServerConnection","[" + this.toString() +
                        "] already closed.");
            throw new IOException("Connection closed");
        } else if (!connected) {
            if (logger.traceOn())
                logger.trace("getMBeanServerConnection","[" + this.toString() +
                        "] is not connected.");
            throw new IOException("Not connected");
        }

        MBeanServerConnection rmbsc = rmbscMap.get(delegationSubject);
        if (rmbsc != null) {
            return rmbsc;
        }

        rmbsc = new RemoteMBeanServerConnection(delegationSubject);
        rmbscMap.put(delegationSubject, rmbsc);
        return rmbsc;
    }

    public void
            addConnectionNotificationListener(NotificationListener listener,
            NotificationFilter filter,
            Object handback) {
        if (listener == null)
            throw new NullPointerException("listener");
        connectionBroadcaster.addNotificationListener(listener, filter,
                handback);
    }

    public void
            removeConnectionNotificationListener(NotificationListener listener)
            throws ListenerNotFoundException {
        if (listener == null)
            throw new NullPointerException("listener");
        connectionBroadcaster.removeNotificationListener(listener);
    }

    public void
            removeConnectionNotificationListener(NotificationListener listener,
            NotificationFilter filter,
            Object handback)
            throws ListenerNotFoundException {
        if (listener == null)
            throw new NullPointerException("listener");
        connectionBroadcaster.removeNotificationListener(listener, filter,
                handback);
    }

    private void sendNotification(Notification n) {
        connectionBroadcaster.sendNotification(n);
    }

    public synchronized void close() throws IOException {
        close(false);
    }

    // allows to do close after setting the flag "terminated" to true.
    // It is necessary to avoid a deadlock, see 6296324
    private synchronized void close(boolean intern) throws IOException {
        final boolean tracing = logger.traceOn();
        final boolean debug   = logger.debugOn();
        final String  idstr   = (tracing?"["+this.toString()+"]":null);

        if (!intern) {
            // Return if already cleanly closed.
            //
            if (terminated) {
                if (closeException == null) {
                    if (tracing) logger.trace("close",idstr + " already closed.");
                    return;
                }
            } else {
                terminated = true;
            }
        }

        if (closeException != null && tracing) {
            // Already closed, but not cleanly. Attempt again.
            //
            if (tracing) {
                logger.trace("close",idstr + " had failed: " + closeException);
                logger.trace("close",idstr + " attempting to close again.");
            }
        }

        String savedConnectionId = null;
        if (connected) {
            savedConnectionId = connectionId;
        }

        closeException = null;

        if (tracing) logger.trace("close",idstr + " closing.");

        if (communicatorAdmin != null) {
            communicatorAdmin.terminate();
        }

        if (rmiNotifClient != null) {
            try {
                rmiNotifClient.terminate();
                if (tracing) logger.trace("close",idstr +
                        " RMI Notification client terminated.");
            } catch (RuntimeException x) {
                closeException = x;
                if (tracing) logger.trace("close",idstr +
                        " Failed to terminate RMI Notification client: " + x);
                if (debug) logger.debug("close",x);
            }
        }

        if (connection != null) {
            try {
                connection.close();
                if (tracing) logger.trace("close",idstr + " closed.");
            } catch (NoSuchObjectException nse) {
                // OK, the server maybe closed itself.
            } catch (IOException e) {
                closeException = e;
                if (tracing) logger.trace("close",idstr +
                        " Failed to close RMIServer: " + e);
                if (debug) logger.debug("close",e);
            }
        }

        // Clean up MBeanServerConnection table
        //
        rmbscMap.clear();

        /* Send notification of closure.  We don't do this if the user
         * never called connect() on the connector, because there's no
         * connection id in that case.  */

        if (savedConnectionId != null) {
            Notification closedNotif =
                    new JMXConnectionNotification(JMXConnectionNotification.CLOSED,
                    this,
                    savedConnectionId,
                    clientNotifSeqNo++,
                    "Client has been closed",
                    null);
            sendNotification(closedNotif);
        }

        // throw exception if needed
        //
        if (closeException != null) {
            if (tracing) logger.trace("close",idstr + " failed to close: " +
                    closeException);
            if (closeException instanceof IOException)
                throw (IOException) closeException;
            if (closeException instanceof RuntimeException)
                throw (RuntimeException) closeException;
            final IOException x =
                    new IOException("Failed to close: " + closeException);
            throw EnvHelp.initCause(x,closeException);
        }
    }

    // added for re-connection
    private Integer addListenerWithSubject(ObjectName name,
                                           MarshalledObject<NotificationFilter> filter,
                                           Subject delegationSubject,
                                           boolean reconnect)
        throws InstanceNotFoundException, IOException {

        final boolean debug = logger.debugOn();
        if (debug)
            logger.debug("addListenerWithSubject",
                    "(ObjectName,MarshalledObject,Subject)");

        final ObjectName[] names = new ObjectName[] {name};
        final MarshalledObject<NotificationFilter>[] filters =
                Util.cast(new MarshalledObject<?>[] {filter});
        final Subject[] delegationSubjects = new Subject[] {
            delegationSubject
        };

        final Integer[] listenerIDs =
                addListenersWithSubjects(names,filters,delegationSubjects,
                reconnect);

        if (debug) logger.debug("addListenerWithSubject","listenerID="
                + listenerIDs[0]);
        return listenerIDs[0];
    }

    // added for re-connection
    private Integer[] addListenersWithSubjects(ObjectName[]       names,
                             MarshalledObject<NotificationFilter>[] filters,
                             Subject[]          delegationSubjects,
                             boolean            reconnect)
        throws InstanceNotFoundException, IOException {

        final boolean debug = logger.debugOn();
        if (debug)
            logger.debug("addListenersWithSubjects",
                    "(ObjectName[],MarshalledObject[],Subject[])");

        final ClassLoader old = pushDefaultClassLoader();
        Integer[] listenerIDs = null;

        try {
            listenerIDs = connection.addNotificationListeners(names,
                    filters,
                    delegationSubjects);
        } catch (NoSuchObjectException noe) {
            // maybe reconnect
            if (reconnect) {
                communicatorAdmin.gotIOException(noe);

                listenerIDs = connection.addNotificationListeners(names,
                        filters,
                        delegationSubjects);
            } else {
                throw noe;
            }
        } catch (IOException ioe) {
            // send a failed notif if necessary
            communicatorAdmin.gotIOException(ioe);
        } finally {
            popDefaultClassLoader(old);
        }

        if (debug) logger.debug("addListenersWithSubjects","registered "
                + ((listenerIDs==null)?0:listenerIDs.length)
                + " listener(s)");
        return listenerIDs;
    }

    //--------------------------------------------------------------------
    // Implementation of MBeanServerConnection
    //--------------------------------------------------------------------
    private class RemoteMBeanServerConnection implements MBeanServerConnection {
        private Subject delegationSubject;

        public RemoteMBeanServerConnection() {
            this(null);
        }

        public RemoteMBeanServerConnection(Subject delegationSubject) {
            this.delegationSubject = delegationSubject;
        }

        public ObjectInstance createMBean(String className,
                ObjectName name)
                throws ReflectionException,
                InstanceAlreadyExistsException,
                MBeanRegistrationException,
                MBeanException,
                NotCompliantMBeanException,
                IOException {
            if (logger.debugOn())
                logger.debug("createMBean(String,ObjectName)",
                        "className=" + className + ", name=" +
                        name);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.createMBean(className,
                        name,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.createMBean(className,
                        name,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public ObjectInstance createMBean(String className,
                ObjectName name,
                ObjectName loaderName)
                throws ReflectionException,
                InstanceAlreadyExistsException,
                MBeanRegistrationException,
                MBeanException,
                NotCompliantMBeanException,
                InstanceNotFoundException,
                IOException {

            if (logger.debugOn())
                logger.debug("createMBean(String,ObjectName,ObjectName)",
                        "className=" + className + ", name="
                        + name + ", loaderName="
                        + loaderName + ")");

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.createMBean(className,
                        name,
                        loaderName,
                        delegationSubject);

            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.createMBean(className,
                        name,
                        loaderName,
                        delegationSubject);

            } finally {
                popDefaultClassLoader(old);
            }
        }

        public ObjectInstance createMBean(String className,
                ObjectName name,
                Object params[],
                String signature[])
                throws ReflectionException,
                InstanceAlreadyExistsException,
                MBeanRegistrationException,
                MBeanException,
                NotCompliantMBeanException,
                IOException {
            if (logger.debugOn())
                logger.debug("createMBean(String,ObjectName,Object[],String[])",
                        "className=" + className + ", name="
                        + name + ", params="
                        + objects(params) + ", signature="
                        + strings(signature));

            final MarshalledObject<Object[]> sParams =
                    new MarshalledObject<Object[]>(params);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.createMBean(className,
                        name,
                        sParams,
                        signature,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.createMBean(className,
                        name,
                        sParams,
                        signature,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public ObjectInstance createMBean(String className,
                ObjectName name,
                ObjectName loaderName,
                Object params[],
                String signature[])
                throws ReflectionException,
                InstanceAlreadyExistsException,
                MBeanRegistrationException,
                MBeanException,
                NotCompliantMBeanException,
                InstanceNotFoundException,
                IOException {
            if (logger.debugOn()) logger.debug(
                    "createMBean(String,ObjectName,ObjectName,Object[],String[])",
                    "className=" + className + ", name=" + name + ", loaderName="
                    + loaderName + ", params=" + objects(params)
                    + ", signature=" + strings(signature));

            final MarshalledObject<Object[]> sParams =
                    new MarshalledObject<Object[]>(params);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.createMBean(className,
                        name,
                        loaderName,
                        sParams,
                        signature,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.createMBean(className,
                        name,
                        loaderName,
                        sParams,
                        signature,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public void unregisterMBean(ObjectName name)
        throws InstanceNotFoundException,
                MBeanRegistrationException,
                IOException {
            if (logger.debugOn())
                logger.debug("unregisterMBean", "name=" + name);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                connection.unregisterMBean(name, delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.unregisterMBean(name, delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public ObjectInstance getObjectInstance(ObjectName name)
        throws InstanceNotFoundException,
                IOException {
            if (logger.debugOn())
                logger.debug("getObjectInstance", "name=" + name);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.getObjectInstance(name, delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.getObjectInstance(name, delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public Set<ObjectInstance> queryMBeans(ObjectName name,
                QueryExp query)
                throws IOException {
            if (logger.debugOn()) logger.debug("queryMBeans",
                    "name=" + name + ", query=" + query);

            final MarshalledObject<QueryExp> sQuery =
                    new MarshalledObject<QueryExp>(query);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.queryMBeans(name, sQuery, delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.queryMBeans(name, sQuery, delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public Set<ObjectName> queryNames(ObjectName name,
                QueryExp query)
                throws IOException {
            if (logger.debugOn()) logger.debug("queryNames",
                    "name=" + name + ", query=" + query);

            final MarshalledObject<QueryExp> sQuery =
                    new MarshalledObject<QueryExp>(query);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.queryNames(name, sQuery, delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.queryNames(name, sQuery, delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public boolean isRegistered(ObjectName name)
        throws IOException {
            if (logger.debugOn())
                logger.debug("isRegistered", "name=" + name);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.isRegistered(name, delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.isRegistered(name, delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public Integer getMBeanCount()
        throws IOException {
            if (logger.debugOn()) logger.debug("getMBeanCount", "");

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.getMBeanCount(delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.getMBeanCount(delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public Object getAttribute(ObjectName name,
                String attribute)
                throws MBeanException,
                AttributeNotFoundException,
                InstanceNotFoundException,
                ReflectionException,
                IOException {
            if (logger.debugOn()) logger.debug("getAttribute",
                    "name=" + name + ", attribute="
                    + attribute);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.getAttribute(name,
                        attribute,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.getAttribute(name,
                        attribute,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public AttributeList getAttributes(ObjectName name,
                String[] attributes)
                throws InstanceNotFoundException,
                ReflectionException,
                IOException {
            if (logger.debugOn()) logger.debug("getAttributes",
                    "name=" + name + ", attributes="
                    + strings(attributes));

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.getAttributes(name,
                        attributes,
                        delegationSubject);

            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.getAttributes(name,
                        attributes,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }


        public void setAttribute(ObjectName name,
                Attribute attribute)
                throws InstanceNotFoundException,
                AttributeNotFoundException,
                InvalidAttributeValueException,
                MBeanException,
                ReflectionException,
                IOException {

            if (logger.debugOn()) logger.debug("setAttribute",
                    "name=" + name + ", attribute="
                    + attribute);

            final MarshalledObject<Attribute> sAttribute =
                    new MarshalledObject<Attribute>(attribute);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                connection.setAttribute(name, sAttribute, delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.setAttribute(name, sAttribute, delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public AttributeList setAttributes(ObjectName name,
                AttributeList attributes)
                throws InstanceNotFoundException,
                ReflectionException,
                IOException {

            if (logger.debugOn()) logger.debug("setAttributes",
                    "name=" + name + ", attributes="
                    + attributes);

            final MarshalledObject<AttributeList> sAttributes =
                    new MarshalledObject<AttributeList>(attributes);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.setAttributes(name,
                        sAttributes,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.setAttributes(name,
                        sAttributes,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }


        public Object invoke(ObjectName name,
                String operationName,
                Object params[],
                String signature[])
                throws InstanceNotFoundException,
                MBeanException,
                ReflectionException,
                IOException {

            if (logger.debugOn()) logger.debug("invoke",
                    "name=" + name
                    + ", operationName=" + operationName
                    + ", params=" + objects(params)
                    + ", signature=" + strings(signature));

            final MarshalledObject<Object[]> sParams =
                    new MarshalledObject<Object[]>(params);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.invoke(name,
                        operationName,
                        sParams,
                        signature,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.invoke(name,
                        operationName,
                        sParams,
                        signature,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }


        public String getDefaultDomain()
        throws IOException {
            if (logger.debugOn()) logger.debug("getDefaultDomain", "");

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.getDefaultDomain(delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.getDefaultDomain(delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public String[] getDomains() throws IOException {
            if (logger.debugOn()) logger.debug("getDomains", "");

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.getDomains(delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.getDomains(delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public MBeanInfo getMBeanInfo(ObjectName name)
        throws InstanceNotFoundException,
                IntrospectionException,
                ReflectionException,
                IOException {

            if (logger.debugOn()) logger.debug("getMBeanInfo", "name=" + name);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.getMBeanInfo(name, delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.getMBeanInfo(name, delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }


        public boolean isInstanceOf(ObjectName name,
                String className)
                throws InstanceNotFoundException,
                IOException {
            if (logger.debugOn())
                logger.debug("isInstanceOf", "name=" + name +
                        ", className=" + className);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                return connection.isInstanceOf(name,
                        className,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                return connection.isInstanceOf(name,
                        className,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public void addNotificationListener(ObjectName name,
                ObjectName listener,
                NotificationFilter filter,
                Object handback)
                throws InstanceNotFoundException,
                IOException {

            if (logger.debugOn())
                logger.debug("addNotificationListener" +
                        "(ObjectName,ObjectName,NotificationFilter,Object)",
                        "name=" + name + ", listener=" + listener
                        + ", filter=" + filter + ", handback=" + handback);

            final MarshalledObject<NotificationFilter> sFilter =
                    new MarshalledObject<NotificationFilter>(filter);
            final MarshalledObject<Object> sHandback =
                    new MarshalledObject<Object>(handback);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                connection.addNotificationListener(name,
                        listener,
                        sFilter,
                        sHandback,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.addNotificationListener(name,
                        listener,
                        sFilter,
                        sHandback,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public void removeNotificationListener(ObjectName name,
                ObjectName listener)
                throws InstanceNotFoundException,
                ListenerNotFoundException,
                IOException {

            if (logger.debugOn()) logger.debug("removeNotificationListener" +
                    "(ObjectName,ObjectName)",
                    "name=" + name
                    + ", listener=" + listener);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                connection.removeNotificationListener(name,
                        listener,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.removeNotificationListener(name,
                        listener,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        public void removeNotificationListener(ObjectName name,
                ObjectName listener,
                NotificationFilter filter,
                Object handback)
                throws InstanceNotFoundException,
                ListenerNotFoundException,
                IOException {
            if (logger.debugOn())
                logger.debug("removeNotificationListener" +
                        "(ObjectName,ObjectName,NotificationFilter,Object)",
                        "name=" + name
                        + ", listener=" + listener
                        + ", filter=" + filter
                        + ", handback=" + handback);

            final MarshalledObject<NotificationFilter> sFilter =
                    new MarshalledObject<NotificationFilter>(filter);
            final MarshalledObject<Object> sHandback =
                    new MarshalledObject<Object>(handback);
            final ClassLoader old = pushDefaultClassLoader();
            try {
                connection.removeNotificationListener(name,
                        listener,
                        sFilter,
                        sHandback,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.removeNotificationListener(name,
                        listener,
                        sFilter,
                        sHandback,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }
        }

        // Specific Notification Handle ----------------------------------

        public void addNotificationListener(ObjectName name,
                NotificationListener listener,
                NotificationFilter filter,
                Object handback)
                throws InstanceNotFoundException,
                IOException {

            final boolean debug = logger.debugOn();

            if (debug)
                logger.debug("addNotificationListener" +
                        "(ObjectName,NotificationListener,"+
                        "NotificationFilter,Object)",
                        "name=" + name
                        + ", listener=" + listener
                        + ", filter=" + filter
                        + ", handback=" + handback);

            final Integer listenerID =
                    addListenerWithSubject(name,
                    new MarshalledObject<NotificationFilter>(filter),
                    delegationSubject,true);
            rmiNotifClient.addNotificationListener(listenerID, name, listener,
                    filter, handback,
                    delegationSubject);
        }

        public void removeNotificationListener(ObjectName name,
                NotificationListener listener)
                throws InstanceNotFoundException,
                ListenerNotFoundException,
                IOException {

            final boolean debug = logger.debugOn();

            if (debug) logger.debug("removeNotificationListener"+
                    "(ObjectName,NotificationListener)",
                    "name=" + name
                    + ", listener=" + listener);

            final Integer[] ret =
                    rmiNotifClient.removeNotificationListener(name, listener);

            if (debug) logger.debug("removeNotificationListener",
                    "listenerIDs=" + objects(ret));

            final ClassLoader old = pushDefaultClassLoader();

            try {
                connection.removeNotificationListeners(name,
                        ret,
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.removeNotificationListeners(name,
                        ret,
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }

        }

        public void removeNotificationListener(ObjectName name,
                NotificationListener listener,
                NotificationFilter filter,
                Object handback)
                throws InstanceNotFoundException,
                ListenerNotFoundException,
                IOException {
            final boolean debug = logger.debugOn();

            if (debug)
                logger.debug("removeNotificationListener"+
                        "(ObjectName,NotificationListener,"+
                        "NotificationFilter,Object)",
                        "name=" + name
                        + ", listener=" + listener
                        + ", filter=" + filter
                        + ", handback=" + handback);

            final Integer ret =
                    rmiNotifClient.removeNotificationListener(name, listener,
                    filter, handback);

            if (debug) logger.debug("removeNotificationListener",
                    "listenerID=" + ret);

            final ClassLoader old = pushDefaultClassLoader();
            try {
                connection.removeNotificationListeners(name,
                        new Integer[] {ret},
                        delegationSubject);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.removeNotificationListeners(name,
                        new Integer[] {ret},
                        delegationSubject);
            } finally {
                popDefaultClassLoader(old);
            }

        }
    }

    //--------------------------------------------------------------------
    private class RMINotifClient extends ClientNotifForwarder {
        public RMINotifClient(ClassLoader cl, Map<String, ?> env) {
            super(cl, env);
        }

        protected NotificationResult fetchNotifs(long clientSequenceNumber,
                int maxNotifications,
                long timeout)
                throws IOException, ClassNotFoundException {
            IOException org;

            while (true) { // used for a successful re-connection
                try {
                    return connection.fetchNotifications(clientSequenceNumber,
                            maxNotifications,
                            timeout);
                } catch (IOException ioe) {
                    org = ioe;

                    // inform of IOException
                    try {
                        communicatorAdmin.gotIOException(ioe);

                        // The connection should be re-established.
                        continue;
                    } catch (IOException ee) {
                        // No more fetch, the Exception will be re-thrown.
                        break;
                    } // never reached
                } // never reached
            }

            // specially treating for an UnmarshalException
            if (org instanceof UnmarshalException) {
                UnmarshalException ume = (UnmarshalException)org;

                if (ume.detail instanceof ClassNotFoundException)
                    throw (ClassNotFoundException) ume.detail;

                /* In Sun's RMI implementation, if a method return
                   contains an unserializable object, then we get
                   UnmarshalException wrapping WriteAbortedException
                   wrapping NotSerializableException.  In that case we
                   extract the NotSerializableException so that our
                   caller can realize it should try to skip past the
                   notification that presumably caused it.  It's not
                   certain that every other RMI implementation will
                   generate this exact exception sequence.  If not, we
                   will not detect that the problem is due to an
                   unserializable object, and we will stop trying to
                   receive notifications from the server.  It's not
                   clear we can do much better.  */
                if (ume.detail instanceof WriteAbortedException) {
                    WriteAbortedException wae =
                            (WriteAbortedException) ume.detail;
                    if (wae.detail instanceof IOException)
                        throw (IOException) wae.detail;
                }
            } else if (org instanceof MarshalException) {
                // IIOP will throw MarshalException wrapping a NotSerializableException
                // when a server fails to serialize a response.
                MarshalException me = (MarshalException)org;
                if (me.detail instanceof NotSerializableException) {
                    throw (NotSerializableException)me.detail;
                }
            }

            // Not serialization problem, simply re-throw the orginal exception
            throw org;
        }

        protected Integer addListenerForMBeanRemovedNotif()
        throws IOException, InstanceNotFoundException {
            NotificationFilterSupport clientFilter =
                    new NotificationFilterSupport();
            clientFilter.enableType(
                    MBeanServerNotification.UNREGISTRATION_NOTIFICATION);
            MarshalledObject<NotificationFilter> sFilter =
                new MarshalledObject<NotificationFilter>(clientFilter);

            Integer[] listenerIDs;
            final ObjectName[] names =
                new ObjectName[] {MBeanServerDelegate.DELEGATE_NAME};
            final MarshalledObject<NotificationFilter>[] filters =
                Util.cast(new MarshalledObject<?>[] {sFilter});
            final Subject[] subjects = new Subject[] {null};
            try {
                listenerIDs =
                        connection.addNotificationListeners(names,
                        filters,
                        subjects);

            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                listenerIDs =
                        connection.addNotificationListeners(names,
                        filters,
                        subjects);
            }
            return listenerIDs[0];
        }

        protected void removeListenerForMBeanRemovedNotif(Integer id)
        throws IOException, InstanceNotFoundException,
                ListenerNotFoundException {
            try {
                connection.removeNotificationListeners(
                        MBeanServerDelegate.DELEGATE_NAME,
                        new Integer[] {id},
                        null);
            } catch (IOException ioe) {
                communicatorAdmin.gotIOException(ioe);

                connection.removeNotificationListeners(
                        MBeanServerDelegate.DELEGATE_NAME,
                        new Integer[] {id},
                        null);
            }

        }

        protected void lostNotifs(String message, long number) {
            final String notifType = JMXConnectionNotification.NOTIFS_LOST;

            final JMXConnectionNotification n =
                new JMXConnectionNotification(notifType,
                                              RMIConnector.this,
                                              connectionId,
                                              clientNotifCounter++,
                                              message,
                                              Long.valueOf(number));
            sendNotification(n);
        }
    }

    private class RMIClientCommunicatorAdmin extends ClientCommunicatorAdmin {
        public RMIClientCommunicatorAdmin(long period) {
            super(period);
        }

        @Override
        public void gotIOException(IOException ioe) throws IOException {
            if (ioe instanceof NoSuchObjectException) {
                // need to restart
                super.gotIOException(ioe);

                return;
            }

            // check if the connection is broken
            try {
                connection.getDefaultDomain(null);
            } catch (IOException ioexc) {
                boolean toClose = false;

                synchronized(this) {
                    if (!terminated) {
                        terminated = true;

                        toClose = true;
                    }
                }

                if (toClose) {
                    // we should close the connection,
                    // but send a failed notif at first
                    final Notification failedNotif =
                            new JMXConnectionNotification(
                            JMXConnectionNotification.FAILED,
                            this,
                            connectionId,
                            clientNotifSeqNo++,
                            "Failed to communicate with the server: "+ioe.toString(),
                            ioe);

                    sendNotification(failedNotif);

                    try {
                        close(true);
                    } catch (Exception e) {
                        // OK.
                        // We are closing
                    }
                }
            }

            // forward the exception
            if (ioe instanceof ServerException) {
                /* Need to unwrap the exception.
                   Some user-thrown exception at server side will be wrapped by
                   rmi into a ServerException.
                   For example, a RMIConnnectorServer will wrap a
                   ClassNotFoundException into a UnmarshalException, and rmi
                   will throw a ServerException at client side which wraps this
                   UnmarshalException.
                   No failed notif here.
                 */
                Throwable tt = ((ServerException)ioe).detail;

                if (tt instanceof IOException) {
                    throw (IOException)tt;
                } else if (tt instanceof RuntimeException) {
                    throw (RuntimeException)tt;
                }
            }

            throw ioe;
        }

        public void reconnectNotificationListeners(ClientListenerInfo[] old) throws IOException {
            final int len  = old.length;
            int i;

            ClientListenerInfo[] clis = new ClientListenerInfo[len];

            final Subject[] subjects = new Subject[len];
            final ObjectName[] names = new ObjectName[len];
            final NotificationListener[] listeners = new NotificationListener[len];
            final NotificationFilter[] filters = new NotificationFilter[len];
            final MarshalledObject<NotificationFilter>[] mFilters =
                    Util.cast(new MarshalledObject<?>[len]);
            final Object[] handbacks = new Object[len];

            for (i=0;i<len;i++) {
                subjects[i]  = old[i].getDelegationSubject();
                names[i]     = old[i].getObjectName();
                listeners[i] = old[i].getListener();
                filters[i]   = old[i].getNotificationFilter();
                mFilters[i]  = new MarshalledObject<NotificationFilter>(filters[i]);
                handbacks[i] = old[i].getHandback();
            }

            try {
                Integer[] ids = addListenersWithSubjects(names,mFilters,subjects,false);

                for (i=0;i<len;i++) {
                    clis[i] = new ClientListenerInfo(ids[i],
                            names[i],
                            listeners[i],
                            filters[i],
                            handbacks[i],
                            subjects[i]);
                }

                rmiNotifClient.postReconnection(clis);

                return;
            } catch (InstanceNotFoundException infe) {
                // OK, we will do one by one
            }

            int j = 0;
            for (i=0;i<len;i++) {
                try {
                    Integer id = addListenerWithSubject(names[i],
                            new MarshalledObject<NotificationFilter>(filters[i]),
                            subjects[i],
                            false);

                    clis[j++] = new ClientListenerInfo(id,
                            names[i],
                            listeners[i],
                            filters[i],
                            handbacks[i],
                            subjects[i]);
                } catch (InstanceNotFoundException infe) {
                    logger.warning("reconnectNotificationListeners",
                            "Can't reconnect listener for " +
                            names[i]);
                }
            }

            if (j != len) {
                ClientListenerInfo[] tmp = clis;
                clis = new ClientListenerInfo[j];
                System.arraycopy(tmp, 0, clis, 0, j);
            }

            rmiNotifClient.postReconnection(clis);
        }

        protected void checkConnection() throws IOException {
            if (logger.debugOn())
                logger.debug("RMIClientCommunicatorAdmin-checkConnection",
                        "Calling the method getDefaultDomain.");

            connection.getDefaultDomain(null);
        }

        protected void doStart() throws IOException {
            // Get RMIServer stub from directory or URL encoding if needed.
            RMIServer stub;
            try {
                stub = (rmiServer!=null)?rmiServer:
                    findRMIServer(jmxServiceURL, env);
            } catch (NamingException ne) {
                throw new IOException("Failed to get a RMI stub: "+ne);
            }

            // Connect IIOP Stub if needed.
            stub = connectStub(stub,env);

            // Calling newClient on the RMIServer stub.
            Object credentials = env.get(CREDENTIALS);
            connection = stub.newClient(credentials);

            // notif issues
            final ClientListenerInfo[] old = rmiNotifClient.preReconnection();

            reconnectNotificationListeners(old);

            connectionId = getConnectionId();

            Notification reconnectedNotif =
                    new JMXConnectionNotification(JMXConnectionNotification.OPENED,
                    this,
                    connectionId,
                    clientNotifSeqNo++,
                    "Reconnected to server",
                    null);
            sendNotification(reconnectedNotif);

        }

        protected void doStop() {
            try {
                close();
            } catch (IOException ioe) {
                logger.warning("RMIClientCommunicatorAdmin-doStop",
                        "Failed to call the method close():" + ioe);
                logger.debug("RMIClientCommunicatorAdmin-doStop",ioe);
            }
        }
    }

    //--------------------------------------------------------------------
    // Private stuff - Serialization
    //--------------------------------------------------------------------
    /**
     * <p>In order to be usable, an IIOP stub must be connected to an ORB.
     * The stub is automatically connected to the ORB if:
     * <ul>
     *     <li> It was returned by the COS naming</li>
     *     <li> Its server counterpart has been registered in COS naming
     *          through JNDI.</li>
     * </ul>
     * Otherwise, it is not connected. A stub which is deserialized
     * from Jini is not connected. A stub which is obtained from a
     * non registered RMIIIOPServerImpl is not a connected.<br>
     * A stub which is not connected can't be serialized, and thus
     * can't be registered in Jini. A stub which is not connected can't
     * be used to invoke methods on the server.
     * <p>
     * In order to palliate this, this method will connect the
     * given stub if it is not yet connected. If the given
     * <var>RMIServer</var> is not an instance of
     * {@link javax.rmi.CORBA.Stub javax.rmi.CORBA.Stub}, then the
     * method do nothing and simply returns that stub. Otherwise,
     * this method will attempt to connect the stub to an ORB as
     * follows:
     * <ul>
     * <p>This method looks in the provided <var>environment</var> for
     * the "java.naming.corba.orb" property. If it is found, the
     * referenced object (an {@link org.omg.CORBA.ORB ORB}) is used to
     * connect the stub. Otherwise, a new org.omg.CORBA.ORB is created
     * by calling {@link
     * org.omg.CORBA.ORB#init(String[], Properties)
     * org.omg.CORBA.ORB.init((String[])null,(Properties)null)}
     * <p>The new created ORB is kept in a static
     * {@link WeakReference} and can be reused for connecting other
     * stubs. However, no reference is ever kept on the ORB provided
     * in the <var>environment</var> map, if any.
     * </ul>
     * @param rmiServer A RMI Server Stub.
     * @param environment An environment map, possibly containing an ORB.
     * @return the given stub.
     * @exception IllegalArgumentException if the
     *      <tt>java.naming.corba.orb</tt> property is specified and
     *      does not point to an {@link org.omg.CORBA.ORB ORB}.
     * @exception IOException if the connection to the ORB failed.
     **/
    static RMIServer connectStub(RMIServer rmiServer,
                                 Map<String, ?> environment)
        throws IOException {
        if (IIOPHelper.isStub(rmiServer)) {
            try {
                IIOPHelper.getOrb(rmiServer);
            } catch (UnsupportedOperationException x) {
                // BAD_OPERATION
                IIOPHelper.connect(rmiServer, resolveOrb(environment));
            }
        }
        return rmiServer;
    }

    /**
     * Get the ORB specified by <var>environment</var>, or create a
     * new one.
     * <p>This method looks in the provided <var>environment</var> for
     * the "java.naming.corba.orb" property. If it is found, the
     * referenced object (an {@link org.omg.CORBA.ORB ORB}) is
     * returned. Otherwise, a new org.omg.CORBA.ORB is created
     * by calling {@link
     * org.omg.CORBA.ORB#init(String[], java.util.Properties)
     * org.omg.CORBA.ORB.init((String[])null,(Properties)null)}
     * <p>The new created ORB is kept in a static
     * {@link WeakReference} and can be reused for connecting other
     * stubs. However, no reference is ever kept on the ORB provided
     * in the <var>environment</var> map, if any.
     * @param environment An environment map, possibly containing an ORB.
     * @return An ORB.
     * @exception IllegalArgumentException if the
     *      <tt>java.naming.corba.orb</tt> property is specified and
     *      does not point to an {@link org.omg.CORBA.ORB ORB}.
     * @exception IOException if the ORB initialization failed.
     **/
    static Object resolveOrb(Map<String, ?> environment)
        throws IOException {
        if (environment != null) {
            final Object orb = environment.get(EnvHelp.DEFAULT_ORB);
            if (orb != null && !(IIOPHelper.isOrb(orb)))
                throw new IllegalArgumentException(EnvHelp.DEFAULT_ORB +
                        " must be an instance of org.omg.CORBA.ORB.");
            if (orb != null) return orb;
        }
        final Object orb =
                (RMIConnector.orb==null)?null:RMIConnector.orb.get();
        if (orb != null) return orb;

        final Object newOrb =
                IIOPHelper.createOrb((String[])null, (Properties)null);
        RMIConnector.orb = new WeakReference<Object>(newOrb);
        return newOrb;
    }

    /**
     * Read RMIConnector fields from an {@link java.io.ObjectInputStream
     * ObjectInputStream}.
     * Calls <code>s.defaultReadObject()</code> and then initializes
     * all transient variables that need initializing.
     * @param s The ObjectInputStream to read from.
     * @exception InvalidObjectException if none of <var>rmiServer</var> stub
     *    or <var>jmxServiceURL</var> are set.
     * @see #RMIConnector(JMXServiceURL,Map)
     * @see #RMIConnector(RMIServer,Map)
     **/
    private void readObject(java.io.ObjectInputStream s)
    throws IOException, ClassNotFoundException  {
        s.defaultReadObject();

        if (rmiServer == null && jmxServiceURL == null) throw new
                InvalidObjectException("rmiServer and jmxServiceURL both null");

        initTransients();
    }

    /**
     * Writes the RMIConnector fields to an {@link java.io.ObjectOutputStream
     * ObjectOutputStream}.
     * <p>Connects the underlying RMIServer stub to an ORB, if needed,
     * before serializing it. This is done using the environment
     * map that was provided to the constructor, if any, and as documented
     * in {@link javax.management.remote.rmi}.</p>
     * <p>This method then calls <code>s.defaultWriteObject()</code>.
     * Usually, <var>rmiServer</var> is null if this object
     * was constructed with a JMXServiceURL, and <var>jmxServiceURL</var>
     * is null if this object is constructed with a RMIServer stub.
     * <p>Note that the environment Map is not serialized, since the objects
     * it contains are assumed to be contextual and relevant only
     * with respect to the local environment (class loader, ORB, etc...).</p>
     * <p>After an RMIConnector is deserialized, it is assumed that the
     * user will call {@link #connect(Map)}, providing a new Map that
     * can contain values which are contextually relevant to the new
     * local environment.</p>
     * <p>Since connection to the ORB is needed prior to serializing, and
     * since the ORB to connect to is one of those contextual parameters,
     * it is not recommended to re-serialize a just de-serialized object -
     * as the de-serialized object has no map. Thus, when an RMIConnector
     * object is needed for serialization or transmission to a remote
     * application, it is recommended to obtain a new RMIConnector stub
     * by calling {@link RMIConnectorServer#toJMXConnector(Map)}.</p>
     * @param s The ObjectOutputStream to write to.
     * @exception InvalidObjectException if none of <var>rmiServer</var> stub
     *    or <var>jmxServiceURL</var> are set.
     * @see #RMIConnector(JMXServiceURL,Map)
     * @see #RMIConnector(RMIServer,Map)
     **/
    private void writeObject(java.io.ObjectOutputStream s)
    throws IOException {
        if (rmiServer == null && jmxServiceURL == null) throw new
                InvalidObjectException("rmiServer and jmxServiceURL both null.");
        connectStub(this.rmiServer,env);
        s.defaultWriteObject();
    }

    // Initialization of transient variables.
    private void initTransients() {
        rmbscMap = new WeakHashMap<Subject, MBeanServerConnection>();
        connected = false;
        terminated = false;

        connectionBroadcaster = new NotificationBroadcasterSupport();
    }

    //--------------------------------------------------------------------
    // Private stuff - Check if stub can be trusted.
    //--------------------------------------------------------------------

    private static void checkStub(Remote stub,
            Class<?> stubClass) {

        // Check remote stub is from the expected class.
        //
        if (stub.getClass() != stubClass) {
            if (!Proxy.isProxyClass(stub.getClass())) {
                throw new SecurityException(
                        "Expecting a " + stubClass.getName() + " stub!");
            } else {
                InvocationHandler handler = Proxy.getInvocationHandler(stub);
                if (handler.getClass() != RemoteObjectInvocationHandler.class)
                    throw new SecurityException(
                            "Expecting a dynamic proxy instance with a " +
                            RemoteObjectInvocationHandler.class.getName() +
                            " invocation handler!");
                else
                    stub = (Remote) handler;
            }
        }

        // Check RemoteRef in stub is from the expected class
        // "sun.rmi.server.UnicastRef2".
        //
        RemoteRef ref = ((RemoteObject)stub).getRef();
        if (ref.getClass() != UnicastRef2.class)
            throw new SecurityException(
                    "Expecting a " + UnicastRef2.class.getName() +
                    " remote reference in stub!");

        // Check RMIClientSocketFactory in stub is from the expected class
        // "javax.rmi.ssl.SslRMIClientSocketFactory".
        //
        LiveRef liveRef = ((UnicastRef2)ref).getLiveRef();
        RMIClientSocketFactory csf = liveRef.getClientSocketFactory();
        if (csf == null || csf.getClass() != SslRMIClientSocketFactory.class)
            throw new SecurityException(
                    "Expecting a " + SslRMIClientSocketFactory.class.getName() +
                    " RMI client socket factory in stub!");
    }

    //--------------------------------------------------------------------
    // Private stuff - RMIServer creation
    //--------------------------------------------------------------------

    private RMIServer findRMIServer(JMXServiceURL directoryURL,
            Map<String, Object> environment)
            throws NamingException, IOException {
        final boolean isIiop = RMIConnectorServer.isIiopURL(directoryURL,true);
        if (isIiop) {
            // Make sure java.naming.corba.orb is in the Map.
            environment.put(EnvHelp.DEFAULT_ORB,resolveOrb(environment));
        }

        String path = directoryURL.getURLPath();
        int end = path.indexOf(';');
        if (end < 0) end = path.length();
        if (path.startsWith("/jndi/"))
            return findRMIServerJNDI(path.substring(6,end), environment, isIiop);
        else if (path.startsWith("/stub/"))
            return findRMIServerJRMP(path.substring(6,end), environment, isIiop);
        else if (path.startsWith("/ior/")) {
            if (!IIOPHelper.isAvailable())
                throw new IOException("iiop protocol not available");
            return findRMIServerIIOP(path.substring(5,end), environment, isIiop);
        } else {
            final String msg = "URL path must begin with /jndi/ or /stub/ " +
                    "or /ior/: " + path;
            throw new MalformedURLException(msg);
        }
    }

    /**
     * Lookup the RMIServer stub in a directory.
     * @param jndiURL A JNDI URL indicating the location of the Stub
     *                (see {@link javax.management.remote.rmi}), e.g.:
     *   <ul><li><tt>rmi://registry-host:port/rmi-stub-name</tt></li>
     *       <li>or <tt>iiop://cosnaming-host:port/iiop-stub-name</tt></li>
     *       <li>or <tt>ldap://ldap-host:port/java-container-dn</tt></li>
     *   </ul>
     * @param env the environment Map passed to the connector.
     * @param isIiop true if the stub is expected to be an IIOP stub.
     * @return The retrieved RMIServer stub.
     * @exception NamingException if the stub couldn't be found.
     **/
    private RMIServer findRMIServerJNDI(String jndiURL, Map<String, ?> env,
            boolean isIiop)
            throws NamingException {

        InitialContext ctx = new InitialContext(EnvHelp.mapToHashtable(env));

        Object objref = ctx.lookup(jndiURL);
        ctx.close();

        if (isIiop)
            return narrowIIOPServer(objref);
        else
            return narrowJRMPServer(objref);
    }

    private static RMIServer narrowJRMPServer(Object objref) {

        return (RMIServer) objref;
    }

    private static RMIServer narrowIIOPServer(Object objref) {
        try {
            return IIOPHelper.narrow(objref, RMIServer.class);
        } catch (ClassCastException e) {
            if (logger.traceOn())
                logger.trace("narrowIIOPServer","Failed to narrow objref=" +
                        objref + ": " + e);
            if (logger.debugOn()) logger.debug("narrowIIOPServer",e);
            return null;
        }
    }

    private RMIServer findRMIServerIIOP(String ior, Map<String, ?> env, boolean isIiop) {
        // could forbid "rmi:" URL here -- but do we need to?
        final Object orb = env.get(EnvHelp.DEFAULT_ORB);
        final Object stub = IIOPHelper.stringToObject(orb, ior);
        return IIOPHelper.narrow(stub, RMIServer.class);
    }

    private RMIServer findRMIServerJRMP(String base64, Map<String, ?> env, boolean isIiop)
        throws IOException {
        // could forbid "iiop:" URL here -- but do we need to?
        final byte[] serialized;
        try {
            serialized = base64ToByteArray(base64);
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException("Bad BASE64 encoding: " +
                    e.getMessage());
        }
        final ByteArrayInputStream bin = new ByteArrayInputStream(serialized);

        final ClassLoader loader = EnvHelp.resolveClientClassLoader(env);
        final ObjectInputStream oin =
                (loader == null) ?
                    new ObjectInputStream(bin) :
                    new ObjectInputStreamWithLoader(bin, loader);
        final Object stub;
        try {
            stub = oin.readObject();
        } catch (ClassNotFoundException e) {
            throw new MalformedURLException("Class not found: " + e);
        }
        return (RMIServer)stub;
    }

    private static final class ObjectInputStreamWithLoader
            extends ObjectInputStream {
        ObjectInputStreamWithLoader(InputStream in, ClassLoader cl)
        throws IOException {
            super(in);
            this.loader = cl;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass classDesc)
                throws IOException, ClassNotFoundException {
            return Class.forName(classDesc.getName(), false, loader);
        }

        private final ClassLoader loader;
    }

    /*
       The following section of code avoids a class loading problem
       with RMI.  The problem is that an RMI stub, when deserializing
       a remote method return value or exception, will first of all
       consult the first non-bootstrap class loader it finds in the
       call stack.  This can lead to behavior that is not portable
       between implementations of the JMX Remote API.  Notably, an
       implementation on J2SE 1.4 will find the RMI stub's loader on
       the stack.  But in J2SE 5, this stub is loaded by the
       bootstrap loader, so RMI will find the loader of the user code
       that called an MBeanServerConnection method.

       To avoid this problem, we take advantage of what the RMI stub
       is doing internally.  Each remote call will end up calling
       ref.invoke(...), where ref is the RemoteRef parameter given to
       the RMI stub's constructor.  It is within this call that the
       deserialization will happen.  So we fabricate our own RemoteRef
       that delegates everything to the "real" one but that is loaded
       by a class loader that knows no other classes.  The class
       loader NoCallStackClassLoader does this: the RemoteRef is an
       instance of the class named by proxyRefClassName, which is
       fabricated by the class loader using byte code that is defined
       by the string below.

       The call stack when the deserialization happens is thus this:
       MBeanServerConnection.getAttribute (or whatever)
       -> RMIConnectionImpl_Stub.getAttribute
          -> ProxyRef.invoke(...getAttribute...)
             -> UnicastRef.invoke(...getAttribute...)
                -> internal RMI stuff

       Here UnicastRef is the RemoteRef created when the stub was
       deserialized (which is of some RMI internal class).  It and the
       "internal RMI stuff" are loaded by the bootstrap loader, so are
       transparent to the stack search.  The first non-bootstrap
       loader found is our ProxyRefLoader, as required.

       In a future version of this code as integrated into J2SE 5,
       this workaround could be replaced by direct access to the
       internals of RMI.  For now, we use the same code base for J2SE
       and for the standalone Reference Implementation.

       The byte code below encodes the following class, compiled using
       J2SE 1.4.2 with the -g:none option.

        package com.sun.jmx.remote.internal;

        import java.lang.reflect.Method;
        import java.rmi.Remote;
        import java.rmi.server.RemoteRef;
        import com.sun.jmx.remote.internal.ProxyRef;

        public class PRef extends ProxyRef {
            public PRef(RemoteRef ref) {
                super(ref);
            }

            public Object invoke(Remote obj, Method method,
                                 Object[] params, long opnum)
                    throws Exception {
                return ref.invoke(obj, method, params, opnum);
            }
        }
     */

    private static final String rmiServerImplStubClassName =
        RMIServer.class.getName() + "Impl_Stub";
    private static final Class<?> rmiServerImplStubClass;
    private static final String rmiConnectionImplStubClassName =
            RMIConnection.class.getName() + "Impl_Stub";
    private static final Class<?> rmiConnectionImplStubClass;
    private static final String pRefClassName =
        "com.sun.jmx.remote.internal.PRef";
    private static final Constructor<?> proxyRefConstructor;
    static {
        final String pRefByteCodeString =
                "\312\376\272\276\0\0\0.\0\27\12\0\5\0\15\11\0\4\0\16\13\0\17\0"+
                "\20\7\0\21\7\0\22\1\0\6<init>\1\0\36(Ljava/rmi/server/RemoteRef;"+
                ")V\1\0\4Code\1\0\6invoke\1\0S(Ljava/rmi/Remote;Ljava/lang/reflec"+
                "t/Method;[Ljava/lang/Object;J)Ljava/lang/Object;\1\0\12Exception"+
                "s\7\0\23\14\0\6\0\7\14\0\24\0\25\7\0\26\14\0\11\0\12\1\0\40com/"+
                "sun/jmx/remote/internal/PRef\1\0$com/sun/jmx/remote/internal/Pr"+
                "oxyRef\1\0\23java/lang/Exception\1\0\3ref\1\0\33Ljava/rmi/serve"+
                "r/RemoteRef;\1\0\31java/rmi/server/RemoteRef\0!\0\4\0\5\0\0\0\0"+
                "\0\2\0\1\0\6\0\7\0\1\0\10\0\0\0\22\0\2\0\2\0\0\0\6*+\267\0\1\261"+
                "\0\0\0\0\0\1\0\11\0\12\0\2\0\10\0\0\0\33\0\6\0\6\0\0\0\17*\264\0"+
                "\2+,-\26\4\271\0\3\6\0\260\0\0\0\0\0\13\0\0\0\4\0\1\0\14\0\0";
        final byte[] pRefByteCode =
                NoCallStackClassLoader.stringToBytes(pRefByteCodeString);
        PrivilegedExceptionAction<Constructor<?>> action =
                new PrivilegedExceptionAction<Constructor<?>>() {
            public Constructor<?> run() throws Exception {
                Class thisClass = RMIConnector.class;
                ClassLoader thisLoader = thisClass.getClassLoader();
                ProtectionDomain thisProtectionDomain =
                        thisClass.getProtectionDomain();
                String[] otherClassNames = {ProxyRef.class.getName()};
                ClassLoader cl =
                        new NoCallStackClassLoader(pRefClassName,
                        pRefByteCode,
                        otherClassNames,
                        thisLoader,
                        thisProtectionDomain);
                Class<?> c = cl.loadClass(pRefClassName);
                return c.getConstructor(RemoteRef.class);
            }
        };

        Class<?> serverStubClass;
        try {
            serverStubClass = Class.forName(rmiServerImplStubClassName);
        } catch (Exception e) {
            logger.error("<clinit>",
                    "Failed to instantiate " +
                    rmiServerImplStubClassName + ": " + e);
            logger.debug("<clinit>",e);
            serverStubClass = null;
        }
        rmiServerImplStubClass = serverStubClass;

        Class<?> stubClass;
        Constructor<?> constr;
        try {
            stubClass = Class.forName(rmiConnectionImplStubClassName);
            constr = (Constructor<?>) AccessController.doPrivileged(action);
        } catch (Exception e) {
            logger.error("<clinit>",
                    "Failed to initialize proxy reference constructor "+
                    "for " + rmiConnectionImplStubClassName + ": " + e);
            logger.debug("<clinit>",e);
            stubClass = null;
            constr = null;
        }
        rmiConnectionImplStubClass = stubClass;
        proxyRefConstructor = constr;
    }

    private static RMIConnection shadowJrmpStub(RemoteObject stub)
    throws InstantiationException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException,
            NoSuchMethodException {
        RemoteRef ref = stub.getRef();
        RemoteRef proxyRef = (RemoteRef)
            proxyRefConstructor.newInstance(new Object[] {ref});
        final Constructor<?> rmiConnectionImplStubConstructor =
            rmiConnectionImplStubClass.getConstructor(RemoteRef.class);
        Object[] args = {proxyRef};
        RMIConnection proxyStub = (RMIConnection)
        rmiConnectionImplStubConstructor.newInstance(args);
        return proxyStub;
    }

    /*
       The following code performs a similar trick for RMI/IIOP to the
       one described above for RMI/JRMP.  Unlike JRMP, though, we
       can't easily insert an object between the RMIConnection stub
       and the RMI/IIOP deserialization code, as explained below.

       A method in an RMI/IIOP stub does the following.  It makes an
       org.omg.CORBA_2_3.portable.OutputStream for each request, and
       writes the parameters to it.  Then it calls
       _invoke(OutputStream) which it inherits from CORBA's
       ObjectImpl.  That returns an
       org.omg.CORBA_2_3.portable.InputStream.  The return value is
       read from this InputStream.  So the stack during
       deserialization looks like this:

       MBeanServerConnection.getAttribute (or whatever)
       -> _RMIConnection_Stub.getAttribute
          -> Util.readAny (a CORBA method)
             -> InputStream.read_any
                -> internal CORBA stuff

       What we would have *liked* to have done would be the same thing
       as for RMI/JRMP.  We create a "ProxyDelegate" that is an
       org.omg.CORBA.portable.Delegate that simply forwards every
       operation to the real original Delegate from the RMIConnection
       stub, except that the InputStream returned by _invoke is
       wrapped by a "ProxyInputStream" that is loaded by our
       NoCallStackClassLoader.

       Unfortunately, this doesn't work, at least with Sun's J2SE
       1.4.2, because the CORBA code is not designed to allow you to
       change Delegates arbitrarily.  You get a ClassCastException
       from code that expects the Delegate to implement an internal
       interface.

       So instead we do the following.  We create a subclass of the
       stub that overrides the _invoke method so as to wrap the
       returned InputStream in a ProxyInputStream.  We create a
       subclass of ProxyInputStream using the NoCallStackClassLoader
       and override its read_any and read_value(Class) methods.
       (These are the only methods called during deserialization of
       MBeanServerConnection return values.)  We extract the Delegate
       from the original stub and insert it into our subclass stub,
       and away we go.  The state of a stub consists solely of its
       Delegate.

       We also need to catch ApplicationException, which will encode
       any exceptions declared in the throws clause of the called
       method.  Its InputStream needs to be wrapped in a
       ProxyInputSteam too.

       We override _releaseReply in the stub subclass so that it
       replaces a ProxyInputStream argument with the original
       InputStream.  This avoids problems if the implementation of
       _releaseReply ends up casting this InputStream to an
       implementation-specific interface (which in Sun's J2SE 5 it
       does).

       It is not strictly necessary for the stub subclass to be loaded
       by a NoCallStackClassLoader, since the call-stack search stops
       at the ProxyInputStream subclass.  However, it is convenient
       for two reasons.  One is that it means that the
       ProxyInputStream subclass can be accessed directly, without
       using reflection.  The other is that it avoids build problems,
       since usually stubs are created after other classes are
       compiled, so we can't access them from this class without,
       again, using reflection.

       The strings below encode the following two Java classes,
       compiled using javac -g:none.

        package com.sun.jmx.remote.protocol.iiop;

        import org.omg.stub.javax.management.remote.rmi._RMIConnection_Stub;

        import org.omg.CORBA.portable.ApplicationException;
        import org.omg.CORBA.portable.InputStream;
        import org.omg.CORBA.portable.OutputStream;
        import org.omg.CORBA.portable.RemarshalException;

        public class ProxyStub extends _RMIConnection_Stub {
            public InputStream _invoke(OutputStream out)
                    throws ApplicationException, RemarshalException {
                try {
                    return new PInputStream(super._invoke(out));
                } catch (ApplicationException e) {
                    InputStream pis = new PInputStream(e.getInputStream());
                    throw new ApplicationException(e.getId(), pis);
                }
            }

            public void _releaseReply(InputStream in) {
                if (in != null)
                    in = ((PInputStream)in).getProxiedInputStream();
                super._releaseReply(in);
            }
        }

        package com.sun.jmx.remote.protocol.iiop;

        public class PInputStream extends ProxyInputStream {
            public PInputStream(org.omg.CORBA.portable.InputStream in) {
                super(in);
            }

            public org.omg.CORBA.Any read_any() {
                return in.read_any();
            }

            public java.io.Serializable read_value(Class clz) {
                return narrow().read_value(clz);
            }
        }


     */
    private static final String iiopConnectionStubClassName =
        "org.omg.stub.javax.management.remote.rmi._RMIConnection_Stub";
    private static final String proxyStubClassName =
        "com.sun.jmx.remote.protocol.iiop.ProxyStub";
    private static final String ProxyInputStreamClassName =
        "com.sun.jmx.remote.protocol.iiop.ProxyInputStream";
    private static final String pInputStreamClassName =
        "com.sun.jmx.remote.protocol.iiop.PInputStream";
    private static final Class<?> proxyStubClass;
    static {
        final String proxyStubByteCodeString =
                "\312\376\272\276\0\0\0\63\0+\12\0\14\0\30\7\0\31\12\0\14\0\32\12"+
                "\0\2\0\33\7\0\34\12\0\5\0\35\12\0\5\0\36\12\0\5\0\37\12\0\2\0 "+
                "\12\0\14\0!\7\0\"\7\0#\1\0\6<init>\1\0\3()V\1\0\4Code\1\0\7_in"+
                "voke\1\0K(Lorg/omg/CORBA/portable/OutputStream;)Lorg/omg/CORBA"+
                "/portable/InputStream;\1\0\15StackMapTable\7\0\34\1\0\12Except"+
                "ions\7\0$\1\0\15_releaseReply\1\0'(Lorg/omg/CORBA/portable/Inp"+
                "utStream;)V\14\0\15\0\16\1\0-com/sun/jmx/remote/protocol/iiop/"+
                "PInputStream\14\0\20\0\21\14\0\15\0\27\1\0+org/omg/CORBA/porta"+
                "ble/ApplicationException\14\0%\0&\14\0'\0(\14\0\15\0)\14\0*\0&"+
                "\14\0\26\0\27\1\0*com/sun/jmx/remote/protocol/iiop/ProxyStub\1"+
                "\0<org/omg/stub/javax/management/remote/rmi/_RMIConnection_Stu"+
                "b\1\0)org/omg/CORBA/portable/RemarshalException\1\0\16getInput"+
                "Stream\1\0&()Lorg/omg/CORBA/portable/InputStream;\1\0\5getId\1"+
                "\0\24()Ljava/lang/String;\1\09(Ljava/lang/String;Lorg/omg/CORB"+
                "A/portable/InputStream;)V\1\0\25getProxiedInputStream\0!\0\13\0"+
                "\14\0\0\0\0\0\3\0\1\0\15\0\16\0\1\0\17\0\0\0\21\0\1\0\1\0\0\0\5"+
                "*\267\0\1\261\0\0\0\0\0\1\0\20\0\21\0\2\0\17\0\0\0G\0\4\0\4\0\0"+
                "\0'\273\0\2Y*+\267\0\3\267\0\4\260M\273\0\2Y,\266\0\6\267\0\4N"+
                "\273\0\5Y,\266\0\7-\267\0\10\277\0\1\0\0\0\14\0\15\0\5\0\1\0\22"+
                "\0\0\0\6\0\1M\7\0\23\0\24\0\0\0\6\0\2\0\5\0\25\0\1\0\26\0\27\0"+
                "\1\0\17\0\0\0'\0\2\0\2\0\0\0\22+\306\0\13+\300\0\2\266\0\11L*+"+
                "\267\0\12\261\0\0\0\1\0\22\0\0\0\3\0\1\14\0\0";
        final String pInputStreamByteCodeString =
                "\312\376\272\276\0\0\0\63\0\36\12\0\7\0\17\11\0\6\0\20\12\0\21"+
                "\0\22\12\0\6\0\23\12\0\24\0\25\7\0\26\7\0\27\1\0\6<init>\1\0'("+
                "Lorg/omg/CORBA/portable/InputStream;)V\1\0\4Code\1\0\10read_an"+
                "y\1\0\25()Lorg/omg/CORBA/Any;\1\0\12read_value\1\0)(Ljava/lang"+
                "/Class;)Ljava/io/Serializable;\14\0\10\0\11\14\0\30\0\31\7\0\32"+
                "\14\0\13\0\14\14\0\33\0\34\7\0\35\14\0\15\0\16\1\0-com/sun/jmx"+
                "/remote/protocol/iiop/PInputStream\1\0\61com/sun/jmx/remote/pr"+
                "otocol/iiop/ProxyInputStream\1\0\2in\1\0$Lorg/omg/CORBA/portab"+
                "le/InputStream;\1\0\"org/omg/CORBA/portable/InputStream\1\0\6n"+
                "arrow\1\0*()Lorg/omg/CORBA_2_3/portable/InputStream;\1\0&org/o"+
                "mg/CORBA_2_3/portable/InputStream\0!\0\6\0\7\0\0\0\0\0\3\0\1\0"+
                "\10\0\11\0\1\0\12\0\0\0\22\0\2\0\2\0\0\0\6*+\267\0\1\261\0\0\0"+
                "\0\0\1\0\13\0\14\0\1\0\12\0\0\0\24\0\1\0\1\0\0\0\10*\264\0\2\266"+
                "\0\3\260\0\0\0\0\0\1\0\15\0\16\0\1\0\12\0\0\0\25\0\2\0\2\0\0\0"+
                "\11*\266\0\4+\266\0\5\260\0\0\0\0\0\0";
        final byte[] proxyStubByteCode =
                NoCallStackClassLoader.stringToBytes(proxyStubByteCodeString);
        final byte[] pInputStreamByteCode =
                NoCallStackClassLoader.stringToBytes(pInputStreamByteCodeString);
        final String[] classNames={proxyStubClassName, pInputStreamClassName};
        final byte[][] byteCodes = {proxyStubByteCode, pInputStreamByteCode};
        final String[] otherClassNames = {
            iiopConnectionStubClassName,
            ProxyInputStreamClassName,
        };
        if (IIOPHelper.isAvailable()) {
            PrivilegedExceptionAction<Class<?>> action =
                new PrivilegedExceptionAction<Class<?>>() {
              public Class<?> run() throws Exception {
                Class thisClass = RMIConnector.class;
                ClassLoader thisLoader = thisClass.getClassLoader();
                ProtectionDomain thisProtectionDomain =
                        thisClass.getProtectionDomain();
                ClassLoader cl =
                        new NoCallStackClassLoader(classNames,
                        byteCodes,
                        otherClassNames,
                        thisLoader,
                        thisProtectionDomain);
                return cl.loadClass(proxyStubClassName);
              }
            };
            Class<?> stubClass;
            try {
                stubClass = AccessController.doPrivileged(action);
            } catch (Exception e) {
                logger.error("<clinit>",
                        "Unexpected exception making shadow IIOP stub class: "+e);
                logger.debug("<clinit>",e);
                stubClass = null;
            }
            proxyStubClass = stubClass;
        } else {
            proxyStubClass = null;
        }
    }

    private static RMIConnection shadowIiopStub(Object stub)
    throws InstantiationException, IllegalAccessException {
        Object proxyStub = proxyStubClass.newInstance();
        IIOPHelper.setDelegate(proxyStub, IIOPHelper.getDelegate(stub));
        return (RMIConnection) proxyStub;
    }

    private static RMIConnection getConnection(RMIServer server,
            Object credentials,
            boolean checkStub)
            throws IOException {
        RMIConnection c = server.newClient(credentials);
        if (checkStub) checkStub(c, rmiConnectionImplStubClass);
        try {
            if (c.getClass() == rmiConnectionImplStubClass)
                return shadowJrmpStub((RemoteObject) c);
            if (c.getClass().getName().equals(iiopConnectionStubClassName))
                return shadowIiopStub(c);
            logger.trace("getConnection",
                    "Did not wrap " + c.getClass() + " to foil " +
                    "stack search for classes: class loading semantics " +
                    "may be incorrect");
        } catch (Exception e) {
            logger.error("getConnection",
                    "Could not wrap " + c.getClass() + " to foil " +
                    "stack search for classes: class loading semantics " +
                    "may be incorrect: " + e);
            logger.debug("getConnection",e);
            // so just return the original stub, which will work for all
            // but the most exotic class loading situations
        }
        return c;
    }

    private static byte[] base64ToByteArray(String s) {
        int sLen = s.length();
        int numGroups = sLen/4;
        if (4*numGroups != sLen)
            throw new IllegalArgumentException(
                    "String length must be a multiple of four.");
        int missingBytesInLastGroup = 0;
        int numFullGroups = numGroups;
        if (sLen != 0) {
            if (s.charAt(sLen-1) == '=') {
                missingBytesInLastGroup++;
                numFullGroups--;
            }
            if (s.charAt(sLen-2) == '=')
                missingBytesInLastGroup++;
        }
        byte[] result = new byte[3*numGroups - missingBytesInLastGroup];

        // Translate all full groups from base64 to byte array elements
        int inCursor = 0, outCursor = 0;
        for (int i=0; i<numFullGroups; i++) {
            int ch0 = base64toInt(s.charAt(inCursor++));
            int ch1 = base64toInt(s.charAt(inCursor++));
            int ch2 = base64toInt(s.charAt(inCursor++));
            int ch3 = base64toInt(s.charAt(inCursor++));
            result[outCursor++] = (byte) ((ch0 << 2) | (ch1 >> 4));
            result[outCursor++] = (byte) ((ch1 << 4) | (ch2 >> 2));
            result[outCursor++] = (byte) ((ch2 << 6) | ch3);
        }

        // Translate partial group, if present
        if (missingBytesInLastGroup != 0) {
            int ch0 = base64toInt(s.charAt(inCursor++));
            int ch1 = base64toInt(s.charAt(inCursor++));
            result[outCursor++] = (byte) ((ch0 << 2) | (ch1 >> 4));

            if (missingBytesInLastGroup == 1) {
                int ch2 = base64toInt(s.charAt(inCursor++));
                result[outCursor++] = (byte) ((ch1 << 4) | (ch2 >> 2));
            }
        }
        // assert inCursor == s.length()-missingBytesInLastGroup;
        // assert outCursor == result.length;
        return result;
    }

    /**
     * Translates the specified character, which is assumed to be in the
     * "Base 64 Alphabet" into its equivalent 6-bit positive integer.
     *
     * @throws IllegalArgumentException if
     *        c is not in the Base64 Alphabet.
     */
    private static int base64toInt(char c) {
        int result;

        if (c >= base64ToInt.length)
            result = -1;
        else
            result = base64ToInt[c];

        if (result < 0)
            throw new IllegalArgumentException("Illegal character " + c);
        return result;
    }

    /**
     * This array is a lookup table that translates unicode characters
     * drawn from the "Base64 Alphabet" (as specified in Table 1 of RFC 2045)
     * into their 6-bit positive integer equivalents.  Characters that
     * are not in the Base64 alphabet but fall within the bounds of the
     * array are translated to -1.
     */
    private static final byte base64ToInt[] = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, 52, 53, 54,
        55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, -1, 0, 1, 2, 3, 4,
        5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
        24, 25, -1, -1, -1, -1, -1, -1, 26, 27, 28, 29, 30, 31, 32, 33, 34,
        35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51
    };

    //--------------------------------------------------------------------
    // Private stuff - Find / Set default class loader
    //--------------------------------------------------------------------
    private ClassLoader pushDefaultClassLoader() {
        final Thread t = Thread.currentThread();
        final ClassLoader old =  t.getContextClassLoader();
        if (defaultClassLoader != null)
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    t.setContextClassLoader(defaultClassLoader);
                    return null;
                }
            });
            return old;
    }

    private void popDefaultClassLoader(final ClassLoader old) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                Thread.currentThread().setContextClassLoader(old);
                return null;
            }
        });
    }

    //--------------------------------------------------------------------
    // Private variables
    //--------------------------------------------------------------------
    /**
     * @serial The RMIServer stub of the RMI JMX Connector server to
     * which this client connector is (or will be) connected. This
     * field can be null when <var>jmxServiceURL</var> is not
     * null. This includes the case where <var>jmxServiceURL</var>
     * contains a serialized RMIServer stub. If both
     * <var>rmiServer</var> and <var>jmxServiceURL</var> are null then
     * serialization will fail.
     *
     * @see #RMIConnector(RMIServer,Map)
     **/
    private final RMIServer rmiServer;

    /**
     * @serial The JMXServiceURL of the RMI JMX Connector server to
     * which this client connector will be connected. This field can
     * be null when <var>rmiServer</var> is not null. If both
     * <var>rmiServer</var> and <var>jmxServiceURL</var> are null then
     * serialization will fail.
     *
     * @see #RMIConnector(JMXServiceURL,Map)
     **/
    private final JMXServiceURL jmxServiceURL;

    // ---------------------------------------------------------
    // WARNING - WARNING - WARNING - WARNING - WARNING - WARNING
    // ---------------------------------------------------------
    // Any transient variable which needs to be initialized should
    // be initialized in the method initTransient()
    private transient Map<String, Object> env;
    private transient ClassLoader defaultClassLoader;
    private transient RMIConnection connection;
    private transient String connectionId;

    private transient long clientNotifSeqNo = 0;

    private transient WeakHashMap<Subject, MBeanServerConnection> rmbscMap;

    private transient RMINotifClient rmiNotifClient;
    // = new RMINotifClient(new Integer(0));

    private transient long clientNotifCounter = 0;

    private transient boolean connected;
    // = false;
    private transient boolean terminated;
    // = false;

    private transient Exception closeException;

    private transient NotificationBroadcasterSupport connectionBroadcaster;

    private transient ClientCommunicatorAdmin communicatorAdmin;

    /**
     * A static WeakReference to an {@link org.omg.CORBA.ORB ORB} to
     * connect unconnected stubs.
     **/
    private static volatile WeakReference<Object> orb = null;

    // TRACES & DEBUG
    //---------------
    private static String objects(final Object[] objs) {
        if (objs == null)
            return "null";
        else
            return Arrays.asList(objs).toString();
    }

    private static String strings(final String[] strs) {
        return objects(strs);
    }
}
