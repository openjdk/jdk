/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management.event;

import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanException;
import javax.management.NotificationFilter;
import javax.management.ObjectName;
import javax.management.remote.NotificationResult;

/**
 * <p>This interface specifies necessary methods on the MBean server
 * side for a JMX remote client to manage its notification listeners as
 * if they are local.
 * Users do not usually work directly with this MBean; instead, the {@link
 * EventClient} class is designed to be used directly by the user.</p>
 *
 * <p>A default implementation of this interface can be added to an MBean
 * Server in one of several ways.</p>
 *
 * <ul>
 * <li><p>The most usual is to insert an {@link
 * javax.management.remote.MBeanServerForwarder MBeanServerForwarder} between
 * the {@linkplain javax.management.remote.JMXConnectorServer Connector Server}
 * and the MBean Server, that will intercept accesses to the Event Client
 * Delegate MBean and treat them as the real MBean would. This forwarder is
 * inserted by default with the standard RMI Connector Server, and can also
 * be created explicitly using {@link EventClientDelegate#newForwarder()}.
 *
 * <li><p>A variant on the above is to replace the MBean Server that is
 * used locally with a forwarder as described above.  Since
 * {@code MBeanServerForwarder} extends {@code MBeanServer}, you can use
 * a forwarder anywhere you would have used the original MBean Server.  The
 * code to do this replacement typically looks something like this:</p>
 *
 * <pre>
 * MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();  // or whatever
 * MBeanServerForwarder mbsf = EventClientDelegate.newForwarder();
 * mbsf.setMBeanServer(mbs);
 * mbs = mbsf;
 * // now use mbs just as you did before, but it will have an EventClientDelegate
 * </pre>
 *
 * <li><p>The final way is to create an instance of {@link EventClientDelegate}
 * and register it in the MBean Server under the standard {@linkplain
 * #OBJECT_NAME name}:</p>
 *
 * <pre>
 * MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();  // or whatever
 * EventClientDelegate ecd = EventClientDelegate.getEventClientDelegate(mbs);
 * mbs.registerMBean(ecd, EventClientDelegateMBean.OBJECT_NAME);
 * <pre>
 * </ul>
 *
 * @since JMX 2.0
 */
public interface EventClientDelegateMBean {
    /**
     * The string representation of {@link #OBJECT_NAME}.
     */
    // This shouldn't really be necessary but an apparent javadoc bug
    // meant that the {@value} tags didn't work if this was a
    // field in EventClientDelegate, even a public field.
    public static final String OBJECT_NAME_STRING =
            "javax.management.event:type=EventClientDelegate";

    /**
     * The standard <code>ObjectName</code> used to register the default
     * <code>EventClientDelegateMBean</code>.  The name is
     * <code>{@value #OBJECT_NAME_STRING}</code>.
     */
    public final static ObjectName OBJECT_NAME =
            ObjectName.valueOf(OBJECT_NAME_STRING);

    /**
     * A unique listener identifier specified for an EventClient.
     * Any notification associated with this id is intended for
     * the EventClient which receives the notification, rather than
     * a listener added using that EventClient.
     */
    public static final int EVENT_CLIENT_LISTENER_ID = -100;

    /**
     * Adds a new client to the <code>EventClientDelegateMBean</code> with
     * a user-specified
     * {@link EventForwarder} to forward notifications to the client. The
     * <code>EventForwarder</code> is created by calling
     * {@link javax.management.MBeanServer#instantiate(String, Object[],
     * String[])}.
     *
     * @param className The class name used to create an
     * {@code EventForwarder}.
     * @param params An array containing the parameters of the constructor to
     * be invoked.
     * @param sig An array containing the signature of the constructor to be
     * invoked
     * @return A client identifier.
     * @exception IOException Reserved for a remote call to throw on the client
     * side.
     * @exception MBeanException An exception thrown when creating the user
     * specified <code>EventForwarder</code>.
     */
    public String addClient(String className, Object[] params, String[] sig)
    throws IOException, MBeanException;

    /**
     * Adds a new client to the <code>EventClientDelegateMBean</code> with
     * a user-specified
     * {@link EventForwarder} to forward notifications to the client. The
     * <code>EventForwarder</code> is created by calling
     * {@link javax.management.MBeanServer#instantiate(String, ObjectName,
     * Object[], String[])}. A user-specified class loader is used to create
     * this <code>EventForwarder</code>.
     *
     * @param className The class name used to create an
     * {@code EventForwarder}.
     * @param classLoader An ObjectName registered as a
     *        <code>ClassLoader</code> MBean.
     * @param params An array containing the parameters of the constructor to
     * be invoked.
     * @param sig An array containing the signature of the constructor to be
     * invoked
     * @return A client identifier.
     * @exception IOException Reserved for a remote call to throw on the client
     * side.
     * @exception MBeanException An exception thrown when creating the user
     * specified <code>EventForwarder</code>.
     */
    public String addClient(String className,
            ObjectName classLoader,
            Object[] params,
            String[] sig) throws IOException, MBeanException;

    /**
     * Removes an added client. Calling this method will remove all listeners
     * added with the client.
     *
     * @exception EventClientNotFoundException If the {@code clientId} is
     * not found.
     * @exception IOException Reserved for a remote call to throw on the client
     * side.
     */
    public void removeClient(String clientID)
    throws EventClientNotFoundException, IOException;

    /**
     * Returns the identifiers of listeners added or subscribed to with the
     * specified client identifier.
     * <P> If no listener is currently registered with the client, an empty
     * array is returned.
     * @param clientID The client identifier with which the listeners are
     * added or subscribed to.
     * @return An array of listener identifiers.
     * @exception EventClientNotFoundException If the {@code clientId} is
     * not found.
     * @exception IOException Reserved for a remote call to throw on the client
     * side.
     */
    public Integer[] getListenerIds(String clientID)
    throws EventClientNotFoundException, IOException;

    /**
     * Adds a listener to receive notifications from an MBean and returns
     * a non-negative integer as the identifier of the listener.
     * <P>This method is called by an {@link EventClient} to implement the
     * method  {@link EventClient#addNotificationListener(ObjectName,
     * NotificationListener, NotificationFilter, Object)}.
     *
     * @param name The name of the MBean onto which the listener should be added.
     * @param filter The filter object. If  {@code filter} is null,
     *        no filtering will be performed before handling notifications.
     * @param clientId The client identifier with which the listener is added.
     * @return A listener identifier.
     * @throws EventClientNotFoundException Thrown if the {@code clientId} is
     * not found.
     * @throws InstanceNotFoundException Thrown if the MBean is not found.
     * @throws IOException Reserved for a remote call to throw on the client
     * side.
     */
    public Integer addListener(String clientId,
            ObjectName name,
            NotificationFilter filter)
            throws InstanceNotFoundException, EventClientNotFoundException,
            IOException;


    /**
     * <p>Subscribes a listener to receive notifications from an MBean or a
     * set of MBeans represented by an {@code ObjectName} pattern.  (It is
     * not an error if no MBeans match the pattern at the time this method is
     * called.)</p>
     *
     * <p>Returns a non-negative integer as the identifier of the listener.</p>
     *
     * <p>This method is called by an {@link EventClient} to execute its
     * method {@link EventClient#subscribe(ObjectName, NotificationListener,
     * NotificationFilter, Object)}.</p>
     *
     * @param clientId The remote client's identifier.
     * @param name The name of an MBean or an {@code ObjectName} pattern
     * representing a set of MBeans to which the listener should listen.
     * @param filter The filter object. If {@code filter} is null, no
     * filtering will be performed before notifications are handled.
     *
     * @return A listener identifier.
     *
     * @throws IllegalArgumentException If the {@code name} or
     * {@code listener} is null.
     * @throws EventClientNotFoundException If the client ID is not found.
     * @throws IOException Reserved for a remote client to throw if
     * an I/O error occurs.
     *
     * @see EventConsumer#subscribe(ObjectName, NotificationListener,
     * NotificationFilter,Object)
     * @see #removeListenerOrSubscriber(String, Integer)
     */
    public Integer addSubscriber(String clientId, ObjectName name,
            NotificationFilter filter)
            throws EventClientNotFoundException, IOException;

    /**
     * Removes a listener, to stop receiving notifications.
     * <P> This method is called by an {@link EventClient} to execute its
     * methods {@link EventClient#removeNotificationListener(ObjectName,
     * NotificationListener, NotificationFilter, Object)},
     * {@link EventClient#removeNotificationListener(ObjectName,
     * NotificationListener)}, and {@link EventClient#unsubscribe}.
     *
     * @param clientId The client identifier with which the listener was added.
     * @param listenerId The listener identifier to be removed. This must be
     * an identifier returned by a previous {@link #addListener addListener}
     * or {@link #addSubscriber addSubscriber} call.
     *
     * @throws InstanceNotFoundException if the MBean on which the listener
     * was added no longer exists.
     * @throws ListenerNotFoundException if there is no listener with the
     * given {@code listenerId}.
     * @throws EventClientNotFoundException if the {@code clientId} is
     * not found.
     * @throws IOException Reserved for a remote call to throw on the client
     * side.
     */
    public void removeListenerOrSubscriber(String clientId, Integer listenerId)
    throws InstanceNotFoundException, ListenerNotFoundException,
            EventClientNotFoundException, IOException;

    /**
     * Called by a client to fetch notifications that are to be sent to its
     * listeners.
     *
     * @param clientId The client's identifier.
     * @param startSequenceNumber The first sequence number to
     * consider.
     * @param timeout The maximum waiting time.
     * @param maxNotifs The maximum number of notifications to return.
     *
     * @throws EventClientNotFoundException Thrown if the {@code clientId} is
     * not found.
     * @throws IllegalArgumentException if the client was {@linkplain
     * #addClient(String, Object[], String[]) added} with an {@link
     * EventForwarder} that is not a {@link FetchingEventForwarder}.
     * @throws IOException Reserved for a remote call to throw on the client
     * side.
     */
    public NotificationResult fetchNotifications(String clientId,
            long startSequenceNumber,
            int maxNotifs,
            long timeout)
            throws EventClientNotFoundException, IOException;

    /**
     * An {@code EventClient} calls this method to keep its {@code clientId}
     * alive in this MBean. The client will be removed if the lease times out.
     *
     * @param clientId The client's identifier.
     * @param timeout The time in milliseconds by which the lease is to be
     * extended.  The value zero has no special meaning, so it will cause the
     * lease to time out immediately.
     *
     * @return The new lifetime of the lease in milliseconds.  This may be
     * different from the requested time.
     *
     * @throws EventClientNotFoundException if the {@code clientId} is
     * not found.
     * @throws IOException reserved for a remote call to throw on the client
     * side.
     * @throws IllegalArgumentException if {@code clientId} is null or
     * {@code timeout} is negative.
     */
    public long lease(String clientId, long timeout)
    throws IOException, EventClientNotFoundException;
}
