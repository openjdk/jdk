/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.jmx.namespace.ObjectNameRouter;
import static javax.management.namespace.JMXNamespaces.NAMESPACE_SEPARATOR;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXRemoteNamespaceMBean;
import javax.management.remote.JMXServiceURL;

/**
 * The {@code NamespaceController} MBean makes it possible to easily
 * create mount points ({@linkplain JMXNamespace JMXNamespaces}) in an
 * {@code MBeanServer}.
 * There is at most one instance of NamespaceController in an
 * MBeanServer - which can be created using the {@link #createInstance
 * createInstance} method. The {@code NamespaceController} MBean will
 * make it possible to remotely create name spaces by mounting remote
 * MBeanServers into the MBeanServer in which it was registered.
 */
// This API was originally in the draft of javax/management/namespaces
// but we decided to retire it. Rather than removing all the associated
// tests I have moved the API to the test hierarchy - so it is now used as
// an additional (though somewhat complex) test case...
//
public class NamespaceController implements NamespaceControllerMBean,
        NotificationEmitter, MBeanRegistration {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG =
            Logger.getLogger(NamespaceController.class.getName());

    private static long seqNumber=0;

    private final NotificationBroadcasterSupport broadcaster =
            new NotificationBroadcasterSupport();

    private volatile MBeanServer mbeanServer = null;

    private volatile ObjectName objectName = null;

    //was: NamespaceController.class.getPackage().getName()
    public static final String NAMESPACE_CONTROLLER_DOMAIN = "jmx.ns";

    /**
     * Creates a new NamespaceController.
     * Using {@link #createInstance} should be preferred.
     **/
    public NamespaceController() {
        this(null);
    }

    public NamespaceController(MBeanServer mbeanServer) {
        this.mbeanServer = mbeanServer;
    }

    /*
     * MBeanNotification support
     * You shouldn't update these methods
     */
    public final void addNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object handback) {
        broadcaster.addNotificationListener(listener, filter, handback);
    }

    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[] {
        };
    }

    public final void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }

    public final void removeNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object handback)
            throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener, filter, handback);
    }

    public static synchronized long getNextSeqNumber() {
        return seqNumber++;
    }

    protected final void sendNotification(Notification n) {
        if (n.getSequenceNumber()<=0)
            n.setSequenceNumber(getNextSeqNumber());
        if (n.getSource()==null)
            n.setSource(objectName);
        broadcaster.sendNotification(n);
    }

    /**
     * The ObjectName with which this MBean was registered.
     * <p>Unless changed by subclasses, this is
     * {@code
     *  "javax.management.namespace:type="+this.getClass().getSimpleName()}.
     * @return this MBean's ObjectName, or null if this MBean was never
     *         registered.
     **/
    public final ObjectName getObjectName() {
        return objectName;
    }

    /**
     * The MBeanServer  served by this NamespaceController.
     * @return the MBeanServer  served by this NamespaceController.
     **/
    public final MBeanServer getMBeanServer() {
        return mbeanServer;
    }

    /**
     * Allows the MBean to perform any operations it needs before being
     * registered in the MBean server. If the name of the MBean is not
     * specified, the MBean can provide a name for its registration. If
     * any exception is raised, the MBean will not be registered in the
     * MBean server. Subclasses which override {@code preRegister}
     * must call {@code super.preRegister(name,server)};
     * @param server The MBean server in which the MBean will be registered.
     * @param name The object name of the MBean.
     *        The name must be either {@code null} - or equal to that
     *        described by {@link #getObjectName}.
     * @return The name under which the MBean is to be registered.
     *         This will be the name described by {@link #getObjectName}.
     * @throws MalformedObjectNameException if the supplied name does not
     *        meet expected requirements.
     */
    public ObjectName preRegister(MBeanServer server, ObjectName name)
        throws MalformedObjectNameException {
        objectName = name;
        final ObjectName single =
                ObjectName.getInstance(NAMESPACE_CONTROLLER_DOMAIN+
                ":type="+this.getClass().getSimpleName());
        if (name!=null && !single.equals(name))
            throw new MalformedObjectNameException(name.toString());
        if (mbeanServer == null) mbeanServer = server;
        return single;
    }

    /**
     * Allows the MBean to perform any operations needed after having
     * been registered in the MBean server or after the registration has
     * failed.
     * @param registrationDone Indicates whether or not the MBean has been
     * successfully registered in the MBean server. The value false means
     * that the registration has failed.
     */
    public void postRegister(Boolean registrationDone) {
        //TODO postRegister implementation;
    }

    /**
     * Allows the MBean to perform any operations it needs before being
     * unregistered by the MBean server.
     * @throws Exception This exception will be caught by the MBean server and
     * re-thrown as an MBeanRegistrationException.
     */
    public void preDeregister() throws Exception {
        //TODO preDeregister implementation;
    }

    /**
     * Allows the MBean to perform any operations needed after having been
     * unregistered in the MBean server.
     */
    public void postDeregister() {
        //TODO postDeregister implementation;
    }

    public String mount(JMXServiceURL url,
            String targetPath,
            Map<String,Object> optionsMap)
            throws IOException {
        return mount(url, targetPath, "", optionsMap);
    }

    // see NamespaceControllerMBean
    public String mount(JMXServiceURL url,
            String targetPath,
            String sourcePath,
            Map<String,Object> optionsMap)
            throws IOException {

        // TODO: handle description.
        final String dirName =
                JMXNamespaces.normalizeNamespaceName(targetPath);

         try {
            final ObjectInstance moi =
                    JMXRemoteTargetNamespace.createNamespace(mbeanServer,
                    dirName,url,optionsMap,
                    JMXNamespaces.normalizeNamespaceName(sourcePath)
                    );
            final ObjectName nsMBean = moi.getObjectName();
            try {
                mbeanServer.invoke(nsMBean, "connect", null,null);
            } catch (Throwable t) {
                mbeanServer.unregisterMBean(nsMBean);
                throw t;
            }
            return getMountPointID(nsMBean);
        } catch (InstanceAlreadyExistsException x) {
            throw new IllegalArgumentException(targetPath,x);
         } catch (IOException x) {
            throw x;
        } catch (Throwable x) {
            if (x instanceof Error) throw (Error)x;
            Throwable cause = x.getCause();
            if (cause instanceof IOException)
                throw ((IOException)cause);
            if (cause == null) cause = x;

            final IOException io =
                    new IOException("connect failed: "+cause);
            io.initCause(cause);
            throw io;
        }
    }

    private String getMountPointID(ObjectName dirName) {
            return dirName.toString();
    }

    private ObjectName getHandlerName(String mountPointID) {
        try {
            final ObjectName tryit = ObjectName.getInstance(mountPointID);
            final ObjectName formatted =
                    JMXNamespaces.getNamespaceObjectName(tryit.getDomain());
            if (!formatted.equals(tryit))
                throw new IllegalArgumentException(mountPointID+
                        ": invalid mountPointID");
            return formatted;
        } catch (MalformedObjectNameException x) {
            throw new IllegalArgumentException(mountPointID,x);
        }
    }

    public boolean unmount(String mountPointID)
        throws IOException {
        final ObjectName dirName = getHandlerName(mountPointID);
        if (!mbeanServer.isRegistered(dirName))
            throw new IllegalArgumentException(mountPointID+
                    ": no such name space");
        final JMXRemoteNamespaceMBean mbean =
                JMX.newMBeanProxy(mbeanServer,dirName,
                    JMXRemoteNamespaceMBean.class);
        try {
            mbean.close();
        } catch (IOException io) {
            LOG.fine("Failed to close properly - ignoring exception: "+io);
            LOG.log(Level.FINEST,
                    "Failed to close properly - ignoring exception",io);
        } finally {
            try {
                mbeanServer.unregisterMBean(dirName);
            } catch (InstanceNotFoundException x) {
                throw new IllegalArgumentException(mountPointID+
                        ": no such name space", x);
            } catch (MBeanRegistrationException x) {
                final IOException io =
                        new IOException(mountPointID +": failed to unmount");
                io.initCause(x);
                throw io;
            }
        }
        return true;
    }

    public boolean ismounted(String targetPath) {
        return mbeanServer.isRegistered(JMXNamespaces.getNamespaceObjectName(targetPath));
    }

    public ObjectName getHandlerNameFor(String targetPath) {
        return JMXNamespaces.getNamespaceObjectName(targetPath);
    }

    public String[] findNamespaces() {
        return findNamespaces(null,null,0);
    }


    private ObjectName getDirPattern(String from) {
        try {
            if (from == null)
                return ObjectName.getInstance(ALL_NAMESPACES);
            final String namespace =
                  ObjectNameRouter.normalizeNamespacePath(from,false,true,false);
            if (namespace.equals(""))
                return ObjectName.getInstance(ALL_NAMESPACES);
            if (JMXNamespaces.getNamespaceObjectName(namespace).isDomainPattern())
                throw new IllegalArgumentException(from);
            return ObjectName.getInstance(namespace+NAMESPACE_SEPARATOR+ALL_NAMESPACES);
        } catch (MalformedObjectNameException x) {
            throw new IllegalArgumentException(from,x);
        }
    }

    public String[] findNamespaces(String from, String regex, int depth) {
        if (depth < 0) return new String[0];
        final Set<String> res = new TreeSet<String>();
        final ObjectName all = getDirPattern(from);
        Set<ObjectName> names = mbeanServer.queryNames(all,null);
        for (ObjectName dirName : names) {
            final String dir = dirName.getDomain();
            if (regex == null || dir.matches(regex))
                res.add(dir);
            if (depth > 0)
                res.addAll(Arrays.asList(findNamespaces(dir,regex,depth-1)));
        }
        return res.toArray(new String[res.size()]);
    }

    /**
     * Creates a {@link NamespaceController} MBean in the provided
     * {@link MBeanServerConnection}.
     * <p>The name of the MBean is that returned by {@link #preRegister}
     * as described by {@link #getObjectName}.
     * @throws IOException if an {@code IOException} is raised when invoking
     *         the provided connection.
     * @throws InstanceAlreadyExistsException if an MBean was already
     *         registered with the NamespaceController's name.
     * @throws MBeanRegistrationException if thrown by {@link
     * MBeanServerConnection#createMBean(java.lang.String,javax.management.ObjectName)
     * server.createMBean}
     * @throws MBeanException if thrown by {@link
     * MBeanServerConnection#createMBean(java.lang.String,javax.management.ObjectName)
     * server.createMBean}
     * @return the {@link ObjectInstance}, as returned by {@link
     * MBeanServerConnection#createMBean(java.lang.String,javax.management.ObjectName)
     * server.createMBean}
     **/
    public static ObjectInstance createInstance(MBeanServerConnection server)
        throws IOException, InstanceAlreadyExistsException,
            MBeanRegistrationException, MBeanException {
        try {
            final ObjectInstance instance =
                server.createMBean(NamespaceController.class.getName(), null);
            return instance;
        } catch (NotCompliantMBeanException ex) {
            throw new RuntimeException("unexpected exception: " + ex, ex);
        } catch (ReflectionException ex) {
            throw new RuntimeException("unexpected exception: " + ex, ex);
        }
    }

    private final static String ALL_NAMESPACES=
            "*"+NAMESPACE_SEPARATOR+":"+
            JMXNamespace.TYPE_ASSIGNMENT;

}
