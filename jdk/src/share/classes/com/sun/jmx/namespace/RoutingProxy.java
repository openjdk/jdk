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

package com.sun.jmx.namespace;

import com.sun.jmx.defaults.JmxProperties;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.namespace.JMXNamespaces;


/**
 * An RoutingProxy narrows on a given name space in a
 * source object implementing MBeanServerConnection.
 * It is used to implement
 * {@code JMXNamespaces.narrowToNamespace(...)}.
 * This abstract class has two concrete subclasses:
 * <p>{@link RoutingConnectionProxy}: to cd in an MBeanServerConnection.</p>
 * <p>{@link RoutingServerProxy}: to cd in an MBeanServer.</p>
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public abstract class RoutingProxy<T extends MBeanServerConnection>
        extends RoutingMBeanServerConnection<T> {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    // The source MBeanServerConnection
    private final T source;

    // The name space we're narrowing to (usually some name space in
    // the source MBeanServerConnection
    private final String                sourceNs;

    // The name space we pretend to be mounted in (usually "")
    private final String                targetNs;

    // The name of the JMXNamespace that handles the source name space
    private final ObjectName            handlerName;
    private final ObjectNameRouter      router;
    final boolean forwardsContext;
    private volatile String             defaultDomain = null;

    /**
     * Creates a new instance of RoutingProxy
     */
    protected RoutingProxy(T source,
                          String sourceNs,
                          String targetNs,
                          boolean forwardsContext) {
        if (source == null) throw new IllegalArgumentException("null");
        this.sourceNs = JMXNamespaces.normalizeNamespaceName(sourceNs);

        // Usually sourceNs is not null, except when implementing
        // Client Contexts
        //
        if (sourceNs.equals("")) {
            this.handlerName = null;
        } else {
            // System.err.println("sourceNs: "+sourceNs);
            this.handlerName =
                JMXNamespaces.getNamespaceObjectName(this.sourceNs);
            try {
                // System.err.println("handlerName: "+handlerName);
                if (!source.isRegistered(handlerName))
                    throw new IllegalArgumentException(sourceNs +
                            ": no such name space");
            } catch (IOException x) {
                throw new IllegalArgumentException("source stale: "+x,x);
            }
        }
        this.source = source;
        this.targetNs = (targetNs==null?"":
            JMXNamespaces.normalizeNamespaceName(targetNs));
        this.router =
                new ObjectNameRouter(this.targetNs,this.sourceNs);
        this.forwardsContext = forwardsContext;

        if (LOG.isLoggable(Level.FINER))
            LOG.finer("RoutingProxy for " + this.sourceNs + " created");
    }

    @Override
    public T source() { return source; }

    ObjectNameRouter getObjectNameRouter() {
// TODO: uncomment this when contexts are added
//        if (forwardsContext)
//            return ObjectNameRouter.wrapWithContext(router);
//        else
            return router;
    }

    @Override
    public ObjectName toSource(ObjectName targetName)
        throws MalformedObjectNameException {
        if (targetName == null) return null;
        if (targetName.getDomain().equals("") && targetNs.equals("")) {
            try {
                if (defaultDomain == null)
                    defaultDomain = getDefaultDomain();
            } catch(Exception x) {
                LOG.log(Level.FINEST,"Failed to get default domain",x);
            }
            if (defaultDomain != null)
                targetName = targetName.withDomain(defaultDomain);
        }
        final ObjectNameRouter r = getObjectNameRouter();
        return r.toSourceContext(targetName,true);
    }

    @Override
    protected ObjectName newSourceMBeanName(ObjectName targetName)
        throws MBeanRegistrationException {
        if (targetName != null) return super.newSourceMBeanName(targetName);

        // OK => we can accept null if sourceNs is empty.
        if (sourceNs.equals("")) return null;

        throw new MBeanRegistrationException(
                new IllegalArgumentException(
                "Can't use null ObjectName with namespaces"));
    }

    @Override
    public ObjectName toTarget(ObjectName sourceName)
        throws MalformedObjectNameException {
        if (sourceName == null) return null;
        final ObjectNameRouter r = getObjectNameRouter();
        return r.toTargetContext(sourceName,false);
    }

    private Object getAttributeFromHandler(String attributeName)
            throws IOException {

        try {
            return source().getAttribute(handlerName,attributeName);
         } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
         } catch (IOException x) {
             throw x;
         } catch (MBeanException ex) {
             throw new IOException("Failed to get "+attributeName+": "+
                     ex.getMessage(),
                     ex.getTargetException());
         } catch (AttributeNotFoundException ex) {
             throw new IOException("Failed to get "+attributeName+": "+
                     ex.getMessage(),ex);
         } catch (InstanceNotFoundException ex) {
             throw new IOException("Failed to get "+attributeName+": "+
                     ex.getMessage(),ex);
         } catch (ReflectionException ex) {
             throw new IOException("Failed to get "+attributeName+": "+
                     ex.getMessage(),ex);
         }
    }

    // We cannot call getMBeanCount() on the underlying
    // MBeanServerConnection, because it would return the number of
    // 'top-level' MBeans, not the number of MBeans in the name space
    // we are narrowing to. Instead we're calling getMBeanCount() on
    // the JMXNamespace that handles the source name space.
    //
    // There is however one particular case when the sourceNs is empty.
    // In that case, there's no handler - and the 'source' is the top
    // level namespace. In that particular case, handlerName will be null,
    // and we directly invoke the top level source().
    // This later complex case is only used when implementing ClientContexts.
    //
    @Override
    public Integer getMBeanCount() throws IOException {
        try {
            if (handlerName == null) return source().getMBeanCount();
            return (Integer) getAttributeFromHandler("MBeanCount");
         } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
         }
    }

    // We cannot call getDomains() on the underlying
    // MBeanServerConnection, because it would return the domains of
    // 'top-level' MBeans, not the domains of MBeans in the name space
    // we are narrowing to. Instead we're calling getDomains() on
    // the JMXNamespace that handles the source name space.
    //
    // There is however one particular case when the sourceNs is empty.
    // In that case, there's no handler - and the 'source' is the top
    // level namespace. In that particular case, handlerName will be null,
    // and we directly invoke the top level source().
    // This later complex case is only used when implementing ClientContexts.
    //
    @Override
    public String[] getDomains() throws IOException {
        try {
            if (handlerName == null) return source().getDomains();
            return (String[]) getAttributeFromHandler("Domains");
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    // We cannot call getDefaultDomain() on the underlying
    // MBeanServerConnection, because it would return the default domain of
    // 'top-level' namespace, not the default domain in the name space
    // we are narrowing to. Instead we're calling getDefaultDomain() on
    // the JMXNamespace that handles the source name space.
    //
    // There is however one particular case when the sourceNs is empty.
    // In that case, there's no handler - and the 'source' is the top
    // level namespace. In that particular case, handlerName will be null,
    // and we directly invoke the top level source().
    // This later complex case is only used when implementing ClientContexts.
    //
    @Override
    public String getDefaultDomain() throws IOException {
        try {
            if (handlerName == null) {
                defaultDomain = source().getDefaultDomain();
            } else {
                defaultDomain =(String)
                        getAttributeFromHandler("DefaultDomain");
            }
            return defaultDomain;
        } catch (RuntimeException ex) {
            throw makeCompliantRuntimeException(ex);
        }
    }

    public String getSourceNamespace() {
        return sourceNs;
    }

    public String getTargetNamespace() {
        return targetNs;
    }

    @Override
    public String toString() {
        return super.toString()+", sourceNs="+
                sourceNs + (targetNs.equals("")?"":
                    (" mounted on targetNs="+targetNs));
    }

}
