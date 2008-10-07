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

package com.sun.jmx.interceptor;

import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.mbeanserver.MBeanInstantiator;
import com.sun.jmx.mbeanserver.Repository;
import com.sun.jmx.mbeanserver.Util;
import com.sun.jmx.namespace.NamespaceInterceptor;

import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.namespace.JMXDomain;
import javax.management.namespace.JMXNamespace;
import static javax.management.namespace.JMXNamespaces.NAMESPACE_SEPARATOR;

/**
 * A dispatcher that dispatches to NamespaceInterceptors.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public class NamespaceDispatchInterceptor
        extends DispatchInterceptor<NamespaceInterceptor, JMXNamespace> {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    private static final int NAMESPACE_SEPARATOR_LENGTH =
            NAMESPACE_SEPARATOR.length();

    private final DomainDispatchInterceptor nextInterceptor;
    private final String           serverName;

    /**
     * Creates a NamespaceDispatchInterceptor with the specified
     * repository instance.
     * <p>Do not forget to call <code>initialize(outer,delegate)</code>
     * before using this object.
     *
     * @param outer A pointer to the MBeanServer object that must be
     *        passed to the MBeans when invoking their
     *        {@link javax.management.MBeanRegistration} interface.
     * @param delegate A pointer to the MBeanServerDelegate associated
     *        with the new MBeanServer. The new MBeanServer must register
     *        this MBean in its MBean repository.
     * @param instantiator The MBeanInstantiator that will be used to
     *        instantiate MBeans and take care of class loading issues.
     * @param repository The repository to use for this MBeanServer
     */
    public NamespaceDispatchInterceptor(MBeanServer         outer,
                               MBeanServerDelegate delegate,
                               MBeanInstantiator   instantiator,
                               Repository          repository)  {
           nextInterceptor = new DomainDispatchInterceptor(outer,delegate,
                   instantiator,repository,this);
           serverName = Util.getMBeanServerSecurityName(delegate);
    }

    // TODO: Should move that to JMXNamespace? or to ObjectName?
    /**
     * Get first name space in ObjectName path. Ignore leading namespace
     * separators.
     **/
    static String getFirstNamespace(ObjectName name) {
        if (name == null) return "";
        final String domain = name.getDomain();
        if (domain.equals("")) return "";

        // skip leading separators
        int first = 0;
        while (domain.startsWith(NAMESPACE_SEPARATOR,first))
            first += NAMESPACE_SEPARATOR_LENGTH;

        // go to next separator
        final int end = domain.indexOf(NAMESPACE_SEPARATOR,first);
        if (end == -1) return ""; // no namespace

        // This is the first element in the namespace path.
        final String namespace = domain.substring(first,end);

        return namespace;
    }

    /**
     * Called by the DefaultMBeanServerInterceptor, just before adding an
     * MBean to the repository.
     *
     * @param resource the MBean to be registered.
     * @param logicalName the name of the MBean to be registered.
     */
    final void checkLocallyRegistrable(Object resource,
            ObjectName logicalName) {
        if (!(resource instanceof JMXNamespace) &&
                logicalName.getDomain().contains(NAMESPACE_SEPARATOR))
            throw new IllegalArgumentException(String.valueOf(logicalName)+
                    ": Invalid ObjectName for an instance of " +
                    resource.getClass().getName());
    }

    final boolean isLocalHandlerNameFor(String namespace,
            ObjectName handlerName) {
        return handlerName.getDomain().equals(namespace+NAMESPACE_SEPARATOR) &&
               JMXNamespace.TYPE_ASSIGNMENT.equals(
               handlerName.getKeyPropertyListString());
    }

    @Override
    final MBeanServer getInterceptorOrNullFor(ObjectName name) {
        final String namespace = getFirstNamespace(name);
        if (namespace.equals("") || isLocalHandlerNameFor(namespace,name) ||
            name.getDomain().equals(namespace+NAMESPACE_SEPARATOR)) {
            LOG.finer("dispatching to local name space");
            return nextInterceptor;
        }
        final NamespaceInterceptor ns = getInterceptor(namespace);
        if (LOG.isLoggable(Level.FINER)) {
            if (ns != null) {
                LOG.finer("dispatching to name space: " + namespace);
            } else {
                LOG.finer("no handler for: " + namespace);
            }
        }
        return ns;
    }

    @Override
    final QueryInterceptor getInterceptorForQuery(ObjectName pattern) {
        final String namespace = getFirstNamespace(pattern);
        if (namespace.equals("") || isLocalHandlerNameFor(namespace,pattern) ||
            pattern.getDomain().equals(namespace+NAMESPACE_SEPARATOR)) {
            LOG.finer("dispatching to local name space");
            return new QueryInterceptor(nextInterceptor);
        }
        final NamespaceInterceptor ns = getInterceptor(namespace);
        if (LOG.isLoggable(Level.FINER)) {
            if (ns != null) {
                LOG.finer("dispatching to name space: " + namespace);
            } else {
                LOG.finer("no handler for: " + namespace);
            }
        }
        if (ns == null) return null;
        return new QueryInterceptor(ns);
    }

    @Override
    final ObjectName getHandlerNameFor(String key)
        throws MalformedObjectNameException {
        return ObjectName.getInstance(key+NAMESPACE_SEPARATOR,
                    "type", JMXNamespace.TYPE);
    }

    @Override
    final public String getHandlerKey(ObjectName name) {
        return getFirstNamespace(name);
    }

    @Override
    final NamespaceInterceptor createInterceptorFor(String key,
            ObjectName name, JMXNamespace handler,
            Queue<Runnable> postRegisterQueue) {
        final NamespaceInterceptor ns =
                new NamespaceInterceptor(serverName,handler,key);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("NamespaceInterceptor created: "+ns);
        }
        return ns;
    }

    @Override
    final DomainDispatchInterceptor getNextInterceptor() {
        return nextInterceptor;
    }

    /**
     * Returns the list of domains in which any MBean is currently
     * registered.
     */
    @Override
    public String[] getDomains() {
        return nextInterceptor.getDomains();
    }

    @Override
    public void addInterceptorFor(ObjectName name, JMXNamespace handler,
            Queue<Runnable> postRegisterQueue) {
        if (handler instanceof JMXDomain)
            nextInterceptor.addInterceptorFor(name,
                    (JMXDomain)handler,postRegisterQueue);
        else super.addInterceptorFor(name,handler,postRegisterQueue);
    }

    @Override
    public void removeInterceptorFor(ObjectName name, JMXNamespace handler,
            Queue<Runnable> postDeregisterQueue) {
        if (handler instanceof JMXDomain)
            nextInterceptor.removeInterceptorFor(name,(JMXDomain)handler,
                    postDeregisterQueue);
        else super.removeInterceptorFor(name,handler,postDeregisterQueue);
    }


}
