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

import javax.management.MBeanException;
import javax.management.MBeanRegistrationException;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.namespace.JMXNamespaces;


/**
 * A RoutingProxy narrows on a given name space in a
 * source object implementing MBeanServerConnection.
 * It is used to implement
 * {@code JMXNamespaces.narrowToNamespace(...)}.
 * This abstract class has two concrete subclasses:
 * <p>{@link RoutingConnectionProxy}: to narrow down into an
 *    MBeanServerConnection.</p>
 * <p>{@link RoutingServerProxy}: to narrow down into an MBeanServer.</p>
 *
 * <p>This class can also be used to "broaden" from a namespace.  The same
 * class is used for both purposes because in both cases all that happens
 * is that ObjectNames are rewritten in one way on the way in (e.g. the
 * parameter of getMBeanInfo) and another way on the way out (e.g. the
 * return value of queryNames).</p>
 *
 * <p>Specifically, if you narrow into "a//" then you want to add the
 * "a//" prefix to ObjectNames on the way in and subtract it on the way
 * out.  But ClientContext uses this class to subtract the
 * "jmx.context//foo=bar//" prefix on the way in and add it back on the
 * way out.</p>
 *
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
//
// RoutingProxies are client side objects which are used to narrow down
// into a namespace. They are used to perform ObjectName translation,
// adding the namespace to the routing ObjectName before sending it over
// to the source connection, and removing that prefix from results of
// queries, createMBean, registerMBean, and getObjectInstance.
// This translation is the opposite to that which is performed by
// NamespaceInterceptors.
//
// There is however a special case where routing proxies are used on the
// 'server' side to remove a namespace - rather than to add it:
// This the case of ClientContext.
// When an ObjectName like "jmx.context//c1=v1,c2=v2//D:k=v" reaches the
// jmx.context namespace, a routing proxy is used to remove the prefix
// c1=v1,c2=v2// from the routing objectname.
//
// For a RoutingProxy used in a narrowDownToNamespace operation, we have:
//     targetNs="" // targetNS is the namespace 'to remove'
//     sourceNS=<namespace-we-narrow-down-to> // namespace 'to add'
//
// For a RoutingProxy used in a ClientContext operation, we have:
//     targetNs=<encoded-context> // context must be removed from object name
//     sourceNs="" // nothing to add...
//
// RoutingProxies can also be used on the client side to implement
// "withClientContext" operations. In that case, the boolean parameter
// 'forwards context' is set to true, targetNs is "", and sourceNS may
// also be "". When forwardsContext is true, the RoutingProxy dynamically
// creates an ObjectNameRouter for each operation - in order to dynamically add
// the context attached to the thread to the routing ObjectName. This is
// performed in the getObjectNameRouter() method.
//
// Finally, in order to avoid too many layers of wrapping,
// RoutingConnectionProxy and RoutingServerProxy can be created through a
// factory method that can concatenate namespace pathes in order to
// return a single RoutingProxy - rather than wrapping a RoutingProxy inside
// another RoutingProxy. See RoutingConnectionProxy.cd and
// RoutingServerProxy.cd
//
// The class hierarchy is as follows:
//
//                           RoutingMBeanServerConnection
//                   [abstract class for all routing interceptors,
//                    such as RoutingProxies and HandlerInterceptors]
//                            /                          \
//                           /                            \
//                    RoutingProxy                HandlerInterceptor
//          [base class for                   [base class for server side
//           client-side objects used          objects, created by
//           in narrowDownTo]                  DispatchInterceptors]
//           /                  \                   |          \
//  RoutingConnectionProxy       \                  |      NamespaceInterceptor
//  [wraps MBeanServerConnection  \                 |     [used to remove
//   objects]                      \                |      namespace prefix and
//                        RoutingServerProxy        |      wrap  JMXNamespace]
//                        [wraps MBeanServer        |
//                         Objects]                 |
//                                            DomainInterceptor
//                                            [used to wrap JMXDomain]
//
// RoutingProxies also differ from HandlerInterceptors in that they transform
// calls to MBeanServerConnection operations that do not have any parameters
// into a call to the underlying JMXNamespace MBean.
// So for instance a call to:
//    JMXNamespaces.narrowDownToNamespace(conn,"foo").getDomains()
// is transformed into
//    conn.getAttribute("foo//type=JMXNamespace","Domains");
//
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
                     ex.getCause(),
                     ex.getCause());
         } catch (Exception ex) {
             throw new IOException("Failed to get "+attributeName+": "+
                     ex,ex);
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

    // Creates an instance of a subclass 'R' of RoutingProxy<T>
    // RoutingServerProxy and RoutingConnectionProxy have their own factory
    // instance.
    static interface RoutingProxyFactory<T extends MBeanServerConnection,
            R extends RoutingProxy<T>> {
            R newInstance(T source,
                    String sourcePath, String targetPath,
                    boolean forwardsContext);
            R newInstance(T source,
                    String sourcePath);
    }

    // Performs a narrowDownToNamespace operation.
    // This method will attempt to merge two RoutingProxies in a single
    // one if they are of the same class.
    //
    // This method is never called directly - it should be called only by
    // subclasses of RoutingProxy.
    //
    // As for now it is called by:
    // RoutingServerProxy.cd and RoutingConnectionProxy.cd.
    //
    static <T extends MBeanServerConnection, R extends RoutingProxy<T>>
           R cd(Class<R> routingProxyClass,
              RoutingProxyFactory<T,R> factory,
              T source, String sourcePath) {
        if (source == null) throw new IllegalArgumentException("null");
        if (source.getClass().equals(routingProxyClass)) {
            // cast is OK here, but findbugs complains unless we use class.cast
            final R other = routingProxyClass.cast(source);
            final String target = other.getTargetNamespace();

            // Avoid multiple layers of serialization.
            //
            // We construct a new proxy from the original source instead of
            // stacking a new proxy on top of the old one.
            // - that is we replace
            //      cd ( cd ( x, dir1), dir2);
            // by
            //      cd (x, dir1//dir2);
            //
            // We can do this only when the source class is exactly
            //    RoutingServerProxy.
            //
            if (target == null || target.equals("")) {
                final String path =
                    JMXNamespaces.concat(other.getSourceNamespace(),
                    sourcePath);
                return factory.newInstance(other.source(),path,"",
                                           other.forwardsContext);
            }
            // Note: we could do possibly something here - but it would involve
            //       removing part of targetDir, and possibly adding
            //       something to sourcePath.
            //       Too complex to bother! => simply default to stacking...
        }
        return factory.newInstance(source,sourcePath);
    }
}
