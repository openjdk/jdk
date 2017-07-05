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

package javax.management.namespace;

import java.io.IOException;
import javax.management.ListenerNotFoundException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import static javax.management.namespace.JMXNamespaces.NAMESPACE_SEPARATOR;


import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * A special {@link JMXNamespace} that can handle part of
 * the MBeanServer local name space.
 * <p>
 * A {@code JMXDomain} makes a domain <i>X</i> of a
 * {@linkplain #getSourceServer() source MBean server} appear in the same domain
 * <i>X</i> of a containing {@code MBeanServer} in which the
 * {@code JMXDomain} MBean {@linkplain #getMBeanServer() is registered}.
 * </p>
 * <p>
 * The JMX infrastructure of the containing {@code MBeanServer} takes care of
 * routing all calls to MBeans whose names have domain <i>X</i> to the
 * {@linkplain #getSourceServer() source MBean server} exported by the
 * {@code JMXDomain} MBean in charge of domain <i>X</i>.
 * </p>
 * <p>
 * The {@linkplain #getSourceServer() source MBean server} of a {@code JMXDomain}
 * can, but need not be a regular {@code MBeanServer} created through
 * the {@link javax.management.MBeanServerFactory}. It could also be,
 * for instance, an instance of a subclass of {@link MBeanServerSupport},
 * or a custom object implementing the {@link MBeanServer} interface.
 * </p>
 *
 * <h4>Differences between {@code JMXNamespace} and {@code JMXDomain}</h4>
 *
 * <p>
 * A {@code JMXDomain} is a special kind of {@code JMXNamespace}.
 * A {@code JMXNamespace} such as {@code foo//} is triggered by an
 * {@code ObjectName} that begins with the string {@code foo//}, for example
 * {@code foo//bar:type=Baz}.  A {@code JMXDomain} such as {@code foo} is
 * triggered by an {@code ObjectName} with that exact domain, for example
 * {@code foo:type=Baz}.  A client can immediately see that an MBean is
 * handled by a {@code JMXNamespace} because of the {@code //} in the name.
 * A client cannot see whether a name such as {@code foo:type=Baz} is an
 * ordinary MBean or is handled by a {@code JMXDomain}.
 * </p>
 *
 * <p>
 * A {@linkplain MBeanServer#queryNames query} on the containing {@code
 * MBeanserver} will return all MBeans from the {@code JMXDomain} that match
 * the query.  In particular, {@code queryNames(null, null)} will return all
 * MBeans including those from {@code JMXDomain} domains.  On the other hand,
 * a query will not include MBeans from a {@code JMXNamespace} unless the
 * {@code ObjectName} pattern in the query starts with the name of that
 * namespace.
 * </p>
 *
 * <h4 id="security">Permission checks</h4>
 *
 * <p>
 * When a JMXDomain MBean is registered in a containing
 * MBean server created through the default {@link
 * javax.management.MBeanServerBuilder}, and if a {@link
 * SecurityManager SecurityManager} is
 * {@linkplain System#getSecurityManager() present}, the containing MBeanServer will
 * check an {@link javax.management.MBeanPermission} before invoking
 * any method on the {@linkplain #getSourceServer() source MBeanServer} of the
 * JMXDomain.
 * </p>
 *
 * <p>First, if there is no security manager ({@link
 * System#getSecurityManager()} is null), that containing
 * {@code MBeanServer} is free not to make any checks.
 * </p>
 *
 * <p>
 * Assuming that there is a security manager, or that the
 * implementation chooses to make checks anyway, the containing
 * {@code MBeanServer} will perform
 * {@link javax.management.MBeanPermission MBeanPermission} checks
 * for access to the MBeans in domain <i>X</i> handled by a {@code JMXDomain}
 * in the same way that it would do for MBeans registered in its own local
 * repository, and as <a href="../MBeanServer.html#security">described in
 * the MBeanServer interface</a>, with the following exceptions:
 * </p>
 *
 * <p>
 * For those permissions that require a {@code className}, the
 * <code>className</code> is the
 * string returned by {@link #getSourceServer() getSourceServer()}.
 * {@link MBeanServer#getObjectInstance(ObjectName)
 * getObjectInstance(mbeanName)}.
 * {@link javax.management.ObjectInstance#getClassName() getClassName()},
 * except for {@code createMBean} and {@code registerMBean} operations,
 * for which the permission checks are performed as follows:
 * </p>
 * <ul>
 * <li><p>For {@code createMBean} operations, the {@code className} of the
 * permission you need is the {@code className} passed as first parameter
 * to {@code createMBean}.</p>
 *
 * <li><p>For {@code registerMBean} operations, the {@code className} of the
 * permission you need is the name of the class of the mbean object, as
 * returned by {@code mbean.getClass().getClassName()}, where
 * {@code mbean} is the mbean reference passed as first parameter
 * to {@code registerMBean}.</p>
 *
 * <li><p>In addition, for {@code createMBean} and {@code registerMBean}, the
 * permission you need is checked with the {@linkplain ObjectName object name} of
 * the mbean that is passed as second parameter to the {@code createMBean} or
 * {@code registerMBean} operation.
 * </p>
 *
 * <li><p>Contrarily to what is done for regular MBeans registered in the
 *     MBeanServer local repository, the containing MBeanServer will not
 *     check the {@link javax.management.MBeanTrustPermission#MBeanTrustPermission(String)
 *     MBeanTrustPermission("register")} against the protection domain
 *     of the MBean's class. This check can be performed by the
 *     {@linkplain #getSourceServer source MBean server} implementation,
 *     if necessary.
 * </p>
 * </ul>
 *
 * <p>If a security check fails, the method throws {@link
 * SecurityException}.</p>
 *
 * <p>For methods that can throw {@link InstanceNotFoundException},
 * this exception is thrown for a non-existent MBean, regardless of
 * permissions.  This is because a non-existent MBean has no
 * <code>className</code>.</p>
 *
 * All these checks are performed by the containing {@code MBeanServer},
 * before accessing the JMXDomain {@linkplain #getSourceServer source MBean server}.
 * The implementation of the JMXDomain {@linkplain #getSourceServer source MBean
 * server} is free to make any additional checks. In fact, if the JMXDomain
 * {@linkplain #getSourceServer source MBean server} is an {@code MBeanServer}
 * obtained through the {@link javax.management.MBeanServerFactory}, it will
 * again make permission checks as described in the
 * <a href="../MBeanServer.html#security">MBeanServer</a> interface.
 *
 * <p>See the <a href="../MBeanServer.html#security">MBeanServer</a> interface
 * for more details on permission checks.</p>
 *
 * @since 1.7
 */
public class JMXDomain extends JMXNamespace {


    /**
     * This constant contains the value of the {@code type}
     * key used in defining a standard JMXDomain MBean object name.
     * By definition, a standard JMXDomain MBean object name must be of
     * the form:
     * <pre>
     * {@code "<domain>:"}+{@value javax.management.namespace.JMXDomain#TYPE_ASSIGNMENT}
     * </pre>
     */
    public static final String TYPE = "JMXDomain";

    /**
     * This constant contains the value of the standard
     * {@linkplain javax.management.ObjectName#getKeyPropertyListString() key
     * property list string} for JMXDomain MBean object names.
     * By definition, a standard JMXDomain MBean object name must be of
     * the form:
     * <pre>
     * {@code <domain>}+":"+{@value javax.management.namespace.JMXDomain#TYPE_ASSIGNMENT}
     * </pre>
     */
    public static final String TYPE_ASSIGNMENT = "type="+TYPE;



    /**
     * Creates a new instance of JMXDomain. The MBeans contained in this
     * domain are handled by the {@code virtualServer} object given to
     * this constructor. Frequently, this will be an instance of
     * {@link MBeanServerSupport}.
     * @param virtualServer The virtual server that acts as a container for
     *        the MBeans handled by this JMXDomain object. Frequently, this will
     *        be an instance of {@link MBeanServerSupport}
     * @see JMXNamespace#JMXNamespace(MBeanServer)
     */
    public JMXDomain(MBeanServer virtualServer) {
        super(virtualServer);
    }

    /**
     * Return the name of domain handled by this JMXDomain.
     * @return the domain handled by this JMXDomain.
     * @throws IOException - if the domain cannot be determined,
     *         for instance, if the MBean is not registered yet.
     */
    @Override
    public final String getDefaultDomain() {
        final ObjectName name = getObjectName();
        if (name == null)
            throw new IllegalStateException("DefaultDomain is not yet known");
        final String dom = name.getDomain();
        return dom;
    }

    /**
     * Returns a singleton array, containing the only domain handled by
     * this JMXDomain object. This is
     * {@code new String[] {getDefaultDomain()}}.
     * @return the only domain handled by this JMXDomain.
     * @throws IOException if the domain cannot be determined,
     *         for instance, if the MBean is not registered yet.
     * @see #getDefaultDomain()
     */
    @Override
    public final String[] getDomains() {
        return new String[] {getDefaultDomain()};
    }

    /**
     * This method returns the number of MBeans in the domain handled
     * by this JMXDomain object. The default implementation is:
     * <pre>
     *    getSourceServer().queryNames(
     *        new ObjectName(getObjectName().getDomain()+":*"), null).size();
     * </pre>
     * If this JMXDomain is not yet registered, this method returns 0.
     * Subclasses can override the above behavior and provide a better
     * implementation.
     * <p>
     * The getMBeanCount() method is called when computing the number
     * of MBeans in the {@linkplain #getMBeanServer() containing MBeanServer}.
     * @return the number of MBeans in this domain, or 0.
     */
    @Override
    public Integer getMBeanCount()  {
        final ObjectName name = getObjectName();
        if (name == null) return 0;
        try {
            return getSourceServer().
               queryNames(ObjectName.WILDCARD.withDomain(name.getDomain()),
               null).size();
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new RuntimeException("Unexpected exception: "+x,x);
        }
    }



    /**
     * Return a canonical handler name for the provided local
     * <var>domain</var> name, or null if the provided domain name is
     * {@code null}.
     * If not null, the handler name returned will be
     * {@code domain+":type="+}{@link #TYPE TYPE}, for example
     * {@code foo:type=JMXDomain}.
     * @param domain A domain name
     * @return a canonical ObjectName for a domain handler.
     * @throws IllegalArgumentException if the provided
     *         <var>domain</var> is not valid - e.g it contains "//".
     */
    public static ObjectName getDomainObjectName(String domain) {
        if (domain == null) return null;
        if (domain.contains(NAMESPACE_SEPARATOR))
            throw new IllegalArgumentException(domain);
        try {
            return ObjectName.getInstance(domain, "type", TYPE);
        } catch (MalformedObjectNameException x) {
            throw new IllegalArgumentException(domain,x);
        }
    }


    /**
     * Validate the ObjectName supplied to preRegister.
     * This method is introduced to allow standard subclasses to use
     * an alternate naming scheme. For instance - if we want to
     * reuse JMXNamespace in order to implement sessions...
     * It is however only available for subclasses in this package.
     **/
    @Override
    ObjectName validateHandlerName(ObjectName suppliedName) {
        if (suppliedName == null)
            throw new IllegalArgumentException("Must supply a valid name");
        final String dirName = JMXNamespaces.
                normalizeNamespaceName(suppliedName.getDomain());
        final ObjectName handlerName = getDomainObjectName(dirName);
        if (!suppliedName.equals(handlerName))
            throw new IllegalArgumentException("invalid name space name: "+
                        suppliedName);

        return suppliedName;
    }

    /**
     * This method is called by the JMX framework to register a
     * NotificationListener that will forward {@linkplain
     * javax.management.MBeanServerNotification mbean server notifications}
     * through the delegate of the {@linkplain #getMBeanServer()
     * containing MBeanServer}.
     * The default implementation of this method is to call
     * <pre>
     *    getSourceServer().addNotificationListener(
     *           MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
     * </pre>
     * Subclasses can redefine this behavior if needed. In particular,
     * subclasses can send their own instances of {@link
     * javax.management.MBeanServerNotification} by calling
     * {@code listener.handleNotification()}.
     *
     * @param listener The MBeanServerNotification listener for this domain.
     * @param filter   A notification filter.
     */
    public void addMBeanServerNotificationListener(
            NotificationListener listener, NotificationFilter filter) {
        try {
            getSourceServer().addNotificationListener(
                MBeanServerDelegate.DELEGATE_NAME, listener, filter, null);
        } catch(InstanceNotFoundException x) {
            throw new UnsupportedOperationException(
                    "Unexpected exception: " +
                    "Emission of MBeanServerNotification disabled.", x);
        }
    }

    /**
     * This method is called by the JMX framework to remove the
     * NotificationListener that was added with {@link
     * #addMBeanServerNotificationListener addMBeanServerNotificationListener}.
     * The default implementation of this method is to call
     * <pre>
     *    getSourceServer().removeNotificationListener(
     *           MBeanServerDelegate.DELEGATE_NAME, listener);
     * </pre>
     * Subclasses can redefine this behavior if needed.
     *
     * @param listener The MBeanServerNotification listener for this domain.
     * @throws ListenerNotFoundException if the listener is not found.
     */
    public void removeMBeanServerNotificationListener(
            NotificationListener listener)
            throws ListenerNotFoundException {
        try {
            getSourceServer().removeNotificationListener(
                MBeanServerDelegate.DELEGATE_NAME, listener);
        } catch(InstanceNotFoundException x) {
            throw new UnsupportedOperationException(
                    "Unexpected exception: " +
                    "Emission of MBeanServerNotification disabled.", x);
        }
    }

}
