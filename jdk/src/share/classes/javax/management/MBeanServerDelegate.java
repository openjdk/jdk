/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;

import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.defaults.ServiceName;
import com.sun.jmx.mbeanserver.Util;

/**
 * Represents  the MBean server from the management point of view.
 * The MBeanServerDelegate MBean emits the MBeanServerNotifications when
 * an MBean is registered/unregistered in the MBean server.
 *
 * @since 1.5
 */
public class MBeanServerDelegate implements MBeanServerDelegateMBean,
                                            NotificationEmitter   {

    /** The MBean server agent identification.*/
    private String mbeanServerId ;
    private String mbeanServerName;

    /** The NotificationBroadcasterSupport object that sends the
        notifications */
    private final NotificationBroadcasterSupport broadcaster;

    private static long oldStamp = 0;
    private final long stamp;
    private long sequenceNumber = 1;

    private static final MBeanNotificationInfo[] notifsInfo;

    static {
        final String[] types  = {
            MBeanServerNotification.UNREGISTRATION_NOTIFICATION,
            MBeanServerNotification.REGISTRATION_NOTIFICATION
        };
        notifsInfo = new MBeanNotificationInfo[1];
        notifsInfo[0] =
            new MBeanNotificationInfo(types,
                    "javax.management.MBeanServerNotification",
                    "Notifications sent by the MBeanServerDelegate MBean");
    }

    /**
     * Create a MBeanServerDelegate object.
     */
    public MBeanServerDelegate () {
        stamp = getStamp();
        broadcaster = new NotificationBroadcasterSupport() ;
        mbeanServerName=null;
    }


    /**
     * Returns the MBean server agent identity.
     *
     * @return the identity.
     */
    public synchronized String getMBeanServerId() {
        if (mbeanServerId == null) {
            String localHost;
            try {
                localHost = java.net.InetAddress.getLocalHost().getHostName();
            } catch (java.net.UnknownHostException e) {
                JmxProperties.MISC_LOGGER.finest("Can't get local host name, " +
                        "using \"localhost\" instead. Cause is: "+e);
                localHost = "localhost";
            }
            mbeanServerId =
                    Util.insertMBeanServerName(localHost + "_" + stamp,
                    mbeanServerName);
        }
        return mbeanServerId;
    }

    /**
     * The name of the MBeanServer.
     * @return The name of the MBeanServer, or {@value
     * javax.management.MBeanServerFactory#DEFAULT_MBEANSERVER_NAME} if no
     * name was specified.
     *
     * @since 1.7
     * @see #setMBeanServerName
     */
    public synchronized String getMBeanServerName() {
        if (Util.isMBeanServerNameUndefined(mbeanServerName))
            return MBeanServerFactory.DEFAULT_MBEANSERVER_NAME;
        return mbeanServerName;
    }

    /**
     * Sets the name of the MBeanServer. The name will be embedded into the
     * {@link #getMBeanServerId MBeanServerId} using the following format:<br>
     * {@code mbeanServerId: <mbeanServerId>;mbeanServerName=<mbeanServerName>}
     * <p>The characters {@code ':'} (colon), {@code ';'} (semicolon ),
     * {@code '*'} (star) and {@code '?'} (question mark) are not legal in an
     * MBean Server name.</p>
     * <p>For instance, if the {@code mbeanServerName} provided is
     * {@code "com.mycompany.myapp.server1"}, and the original
     * {@code MBeanServerId} was {@code "myhost_1213353064145"},
     * then {@code mbeanServerName} will be
     * embedded in the {@code MBeanServerId} - and the new value of the
     * {@code MBeanServerId} will be:
     * </p>
     * <pre>
     *       "myhost_1213353064145;mbeanServerName=com.mycompany.myapp.server1"
     * </pre>
     * <p>Note: The {@code mbeanServerName} is usually set by the
     *   {@code MBeanServerFactory}. It is set only once, before the
     *   MBean Server is returned by the factory. Once the MBean Server name is
     *   set, it is not possible to change it.
     * </p>
     * @param mbeanServerName The MBeanServer name.
     * @throws IllegalArgumentException if the MBeanServerName is already set
     *         to a different value, or if the provided name contains
     *         illegal characters, or if the provided name is {@code ""}
     *         (the empty string) or "-" (dash).
     * @throws UnsupportedOperationException if this object is of a legacy
     *         subclass of MBeanServerDelegate which overrides {@link
     *         #getMBeanServerId()}
     *         in a way that doesn't support setting an MBeanServer name.
     * @see MBeanServerFactory#getMBeanServerName
     * @since 1.7
     */
    public synchronized void setMBeanServerName(String mbeanServerName) {
        // Sets the name on the delegate. For complex backward
        // compatibility reasons it is not possible to give the
        // name to the MBeanServerDelegate constructor.
        //
        // The method setMBeanServerName() will call getMBeanServerId()
        // to check that the name is accurately set in the MBeanServerId.
        // If not (which could happen if a custom MBeanServerDelegate
        // implementation overrides getMBeanServerId() and was not updated
        // with respect to JMX 2.0 spec), this method will throw an
        // IllegalStateException...

        // will fail if mbeanServerName is illegal
        final String name = Util.checkServerName(mbeanServerName);

        // can only set mbeanServerDelegate once.
        if (this.mbeanServerName != null && !this.mbeanServerName.equals(name))
            throw new IllegalArgumentException(
                    "MBeanServerName already set to a different value");

        this.mbeanServerName = name;

        // will fail if mbeanServerId already has a different mbeanServerName
        mbeanServerId =
           Util.insertMBeanServerName(getMBeanServerId(),name);

        // check that we don't have a subclass which overrides
        // getMBeanServerId() without setting mbeanServerName
        if (!name.equals(
                Util.extractMBeanServerName(getMBeanServerId())))
            throw new UnsupportedOperationException(
                    "Can't set MBeanServerName in MBeanServerId - " +
                    "unsupported by "+this.getClass().getName()+"?");
        // OK: at this point we know that we have correctly set mbeanServerName.
    }

    /**
     * Returns the full name of the JMX specification implemented
     * by this product.
     *
     * @return the specification name.
     */
    public String getSpecificationName() {
        return ServiceName.JMX_SPEC_NAME;
    }

    /**
     * Returns the version of the JMX specification implemented
     * by this product.
     *
     * @return the specification version.
     */
    public String getSpecificationVersion() {
        return ServiceName.JMX_SPEC_VERSION;
    }

    /**
     * Returns the vendor of the JMX specification implemented
     * by this product.
     *
     * @return the specification vendor.
     */
    public String getSpecificationVendor() {
        return ServiceName.JMX_SPEC_VENDOR;
    }

    /**
     * Returns the JMX implementation name (the name of this product).
     *
     * @return the implementation name.
     */
    public String getImplementationName() {
        return ServiceName.JMX_IMPL_NAME;
    }

    /**
     * Returns the JMX implementation version (the version of this product).
     *
     * @return the implementation version.
     */
    public String getImplementationVersion() {
        try {
            return System.getProperty("java.runtime.version");
        } catch (SecurityException e) {
            return "";
        }
    }

    /**
     * Returns the JMX implementation vendor (the vendor of this product).
     *
     * @return the implementation vendor.
     */
    public String getImplementationVendor()  {
        return ServiceName.JMX_IMPL_VENDOR;
    }

    // From NotificationEmitter extends NotificationBroacaster
    //
    public MBeanNotificationInfo[] getNotificationInfo() {
        final int len = MBeanServerDelegate.notifsInfo.length;
        final MBeanNotificationInfo[] infos =
        new MBeanNotificationInfo[len];
        System.arraycopy(MBeanServerDelegate.notifsInfo,0,infos,0,len);
        return infos;
    }

    // From NotificationEmitter extends NotificationBroacaster
    //
    public synchronized
        void addNotificationListener(NotificationListener listener,
                                     NotificationFilter filter,
                                     Object handback)
        throws IllegalArgumentException {
        broadcaster.addNotificationListener(listener,filter,handback) ;
    }

    // From NotificationEmitter extends NotificationBroacaster
    //
    public synchronized
        void removeNotificationListener(NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
        throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener,filter,handback) ;
    }

    // From NotificationEmitter extends NotificationBroacaster
    //
    public synchronized
        void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener) ;
    }

    /**
     * Enables the MBean server to send a notification.
     * If the passed <var>notification</var> has a sequence number lesser
     * or equal to 0, then replace it with the delegate's own sequence
     * number.
     * @param notification The notification to send.
     *
     */
    public void sendNotification(Notification notification) {
        if (notification.getSequenceNumber() < 1) {
            synchronized (this) {
                notification.setSequenceNumber(this.sequenceNumber++);
            }
        }
        broadcaster.sendNotification(notification);
    }

    /**
     * Defines the default ObjectName of the MBeanServerDelegate.
     *
     * @since 1.6
     */
    public static final ObjectName DELEGATE_NAME =
            ObjectName.valueOf("JMImplementation:type=MBeanServerDelegate");

    /* Return a timestamp that is monotonically increasing even if
       System.currentTimeMillis() isn't (for example, if you call this
       constructor more than once in the same millisecond, or if the
       clock always returns the same value).  This means that the ids
       for a given JVM will always be distinact, though there is no
       such guarantee for two different JVMs.  */
    private static synchronized long getStamp() {
        long s = System.currentTimeMillis();
        if (oldStamp >= s) {
            s = oldStamp + 1;
        }
        oldStamp = s;
        return s;
    }
}
