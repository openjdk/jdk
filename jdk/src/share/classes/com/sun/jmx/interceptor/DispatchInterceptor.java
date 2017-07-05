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


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.namespace.JMXNamespace;

/**
 * A dispatcher that dispatches to MBeanServers.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
//
// This is the base class for implementing dispatchers. We have two concrete
// dispatcher implementations:
//
//   * A NamespaceDispatchInterceptor, which dispatch calls to existing
//     namespace interceptors
//   * A DomainDispatchInterceptor, which dispatch calls to existing domain
//     interceptors.
//
// With the JMX Namespaces feature, the JMX MBeanServer is now structured
// as follows:
//
// The JMX MBeanServer delegates to a NamespaceDispatchInterceptor,
// which either dispatches to a namespace, or delegates to the
// DomainDispatchInterceptor (if the object name contained no namespace).
// The DomainDispatchInterceptor in turn either dispatches to a domain (if
// there is a JMXDomain for that domain) or delegates to the
// DefaultMBeanServerInterceptor (if there is no JMXDomain for that
// domain). This makes the following picture:
//
//             JMX MBeanServer (outer shell)
//                          |
//                          |
//              NamespaceDispatchInterceptor
//                   /               \
//     no namespace in object name?   \
//                 /                   \
//                /                   dispatch to namespace
//         DomainDispatchInterceptor
//              /              \
//    no JMXDomain for domain?  \
//            /                  \
//           /                   dispatch to domain
//  DefaultMBeanServerInterceptor
//         /
//   invoke locally registered MBean
//
//  The logic for maintaining a map of interceptors
//  and dispatching to impacted interceptor, is implemented in this
//  base class, which both NamespaceDispatchInterceptor and
//  DomainDispatchInterceptor extend.
//
public abstract class DispatchInterceptor
        <T extends MBeanServer, N extends JMXNamespace>
        extends MBeanServerInterceptorSupport {

    /**
     * This is an abstraction which allows us to handle queryNames
     * and queryMBeans with the same algorithm. There are some subclasses
     * where we need to override both queryNames & queryMBeans to apply
     * the same transformation (usually aggregation of results when
     * several namespaces/domains are impacted) to both algorithms.
     * Usually the only thing that varies between the algorithm of
     * queryNames & the algorithm of queryMBean is the type of objects
     * in the returned Set. By using a QueryInvoker we can implement the
     * transformation only once and apply it to both queryNames &
     * queryMBeans.
     * @see QueryInterceptor below, and its subclass in
     * {@link DomainDispatcher}.
     **/
    static abstract class QueryInvoker<T> {
        abstract Set<T> query(MBeanServer mbs,
                        ObjectName pattern, QueryExp query);
    }

    /**
     * Used to perform queryNames. A QueryInvoker that invokes
     * queryNames on an MBeanServer.
     **/
    final static QueryInvoker<ObjectName> queryNamesInvoker =
            new QueryInvoker<ObjectName>() {
        Set<ObjectName> query(MBeanServer mbs,
                        ObjectName pattern, QueryExp query) {
            return mbs.queryNames(pattern,query);
        }
    };

    /**
     * Used to perform queryMBeans. A QueryInvoker that invokes
     * queryMBeans on an MBeanServer.
     **/
    final static QueryInvoker<ObjectInstance> queryMBeansInvoker =
            new QueryInvoker<ObjectInstance>() {
        Set<ObjectInstance> query(MBeanServer mbs,
                        ObjectName pattern, QueryExp query) {
            return mbs.queryMBeans(pattern,query);
        }
    };

    /**
     * We use this class to intercept queries.
     * There's a special case for JMXNamespace MBeans, because
     * "namespace//*:*" matches both "namespace//domain:k=v" and
     * "namespace//:type=JMXNamespace".
     * Therefore, queries may need to be forwarded to more than
     * on interceptor and the results aggregated...
     */
     static class QueryInterceptor {
        final MBeanServer wrapped;
        QueryInterceptor(MBeanServer mbs) {
            wrapped = mbs;
        }
        <X> Set<X> query(ObjectName pattern, QueryExp query,
                QueryInvoker<X> invoker, MBeanServer server) {
            return invoker.query(server, pattern, query);
        }

        public Set<ObjectName> queryNames(ObjectName pattern, QueryExp query) {
            return query(pattern,query,queryNamesInvoker,wrapped);
        }

        public Set<ObjectInstance> queryMBeans(ObjectName pattern,
                QueryExp query) {
            return query(pattern,query,queryMBeansInvoker,wrapped);
        }
    }

    // We don't need a ConcurrentHashMap here because getkeys() returns
    // an array of keys. Therefore there's no risk to have a
    // ConcurrentModificationException. We must however take into
    // account the fact that there can be no interceptor for
    // some of the returned keys if the map is being modified by
    // another thread, or by a callback within the same thread...
    // See getKeys() in this class and query() in DomainDispatcher.
    //
    private final Map<String,T> handlerMap =
            Collections.synchronizedMap(
            new HashMap<String,T>());

    // The key at which an interceptor for accessing the named MBean can be
    // found in the handlerMap. Note: there doesn't need to be an interceptor
    // for that key in the Map.
    //
    abstract String getHandlerKey(ObjectName name);

    // Returns an interceptor for that name, or null if there's no interceptor
    // for that name.
    abstract MBeanServer getInterceptorOrNullFor(ObjectName name);

    // Returns a QueryInterceptor for that pattern.
    abstract QueryInterceptor getInterceptorForQuery(ObjectName pattern);

    // Returns the ObjectName of the JMXNamespace (or JMXDomain) for that
    // key (a namespace or a domain name).
    abstract ObjectName getHandlerNameFor(String key)
        throws MalformedObjectNameException;

    // Creates an interceptor for the given key, name, JMXNamespace (or
    // JMXDomain). Note: this will be either a NamespaceInterceptor
    // wrapping a JMXNamespace, if this object is an instance of
    // NamespaceDispatchInterceptor, or a DomainInterceptor wrapping a
    // JMXDomain, if this object is an instance of DomainDispatchInterceptor.
    abstract T createInterceptorFor(String key, ObjectName name,
            N jmxNamespace, Queue<Runnable> postRegisterQueue);
    //
    // The next interceptor in the chain.
    //
    // For the NamespaceDispatchInterceptor, this the DomainDispatchInterceptor.
    // For the DomainDispatchInterceptor, this is the
    // DefaultMBeanServerInterceptor.
    //
    // The logic of when to invoke the next interceptor in the chain depends
    // on the logic of the concrete dispatcher class.
    //
    // For instance, the NamespaceDispatchInterceptor invokes the next
    // interceptor when the object name doesn't contain any namespace.
    //
    // On the other hand, the DomainDispatchInterceptor invokes the
    // next interceptor when there's no interceptor for the accessed domain.
    //
    abstract MBeanServer getNextInterceptor();

    // hook for cleanup in subclasses.
    void interceptorReleased(T interceptor,
            Queue<Runnable> postDeregisterQueue) {
        // hook
    }

    // Hook for subclasses.
    MBeanServer getInterceptorForCreate(ObjectName name)
        throws MBeanRegistrationException {
        final MBeanServer ns = getInterceptorOrNullFor(name);
        if (ns == null) // name cannot be null here.
            throw new MBeanRegistrationException(
                    new IllegalArgumentException("No such MBean handler: " +
                        getHandlerKey(name) + " for " +name));
        return ns;
    }

    // Hook for subclasses.
    MBeanServer getInterceptorForInstance(ObjectName name)
        throws InstanceNotFoundException {
        final MBeanServer ns = getInterceptorOrNullFor(name);
        if (ns == null) // name cannot be null here.
            throw new InstanceNotFoundException(String.valueOf(name));
        return ns;
    }

    // sanity checks
    void validateHandlerNameFor(String key, ObjectName name) {
        if (key == null || key.equals(""))
            throw new IllegalArgumentException("invalid key for "+name+": "+key);
        try {
            final ObjectName handlerName = getHandlerNameFor(key);
            if (!name.equals(handlerName))
                throw new IllegalArgumentException("bad handler name: "+name+
                        ". Should be: "+handlerName);
        } catch (MalformedObjectNameException x) {
            throw new IllegalArgumentException(name.toString(),x);
        }
    }

    // Called by the DefaultMBeanServerInterceptor when an instance
    // of JMXNamespace (or a subclass of it) is registered as an MBean.
    // This method is usually invoked from within the repository lock,
    // hence the necessity of the postRegisterQueue.
    public void addInterceptorFor(ObjectName name, N jmxNamespace,
            Queue<Runnable> postRegisterQueue) {
        final String key = getHandlerKey(name);
        validateHandlerNameFor(key,name);
        synchronized (handlerMap) {
            final T exists =
                    handlerMap.get(key);
            if (exists != null)
                throw new IllegalArgumentException(key+
                        ": handler already exists");

            final T ns = createInterceptorFor(key,name,jmxNamespace,
                    postRegisterQueue);
            handlerMap.put(key,ns);
        }
    }

    // Called by the DefaultMBeanServerInterceptor when an instance
    // of JMXNamespace (or a subclass of it) is deregistered.
    // This method is usually invoked from within the repository lock,
    // hence the necessity of the postDeregisterQueue.
    public void removeInterceptorFor(ObjectName name, N jmxNamespace,
            Queue<Runnable> postDeregisterQueue) {
        final String key = getHandlerKey(name);
        final T ns;
        synchronized(handlerMap) {
            ns = handlerMap.remove(key);
        }
        interceptorReleased(ns,postDeregisterQueue);
    }

    // Get the interceptor for that key.
    T getInterceptor(String key) {
        synchronized (handlerMap) {
            return handlerMap.get(key);
        }
    }

    // We return an array of keys, which makes it possible to make
    // concurrent modifications of the handlerMap, provided that
    // the code which loops over the keys is prepared to handle null
    // interceptors.
    // See declaration of handlerMap above, and see also query() in
    // DomainDispatcher
    //
    public String[] getKeys() {
        synchronized (handlerMap) {
            final int size = handlerMap.size();
            return handlerMap.keySet().toArray(new String[size]);
        }
    }

    // From MBeanServer
    public final ObjectInstance createMBean(String className, ObjectName name)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException {
        return getInterceptorForCreate(name).createMBean(className,name);
    }

    // From MBeanServer
    public final ObjectInstance createMBean(String className, ObjectName name,
                                      ObjectName loaderName)
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException{
        return getInterceptorForCreate(name).createMBean(className,name,loaderName);
    }

    // From MBeanServer
    public final ObjectInstance createMBean(String className, ObjectName name,
                                      Object params[], String signature[])
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException{
        return getInterceptorForCreate(name).
                createMBean(className,name,params,signature);
    }

    // From MBeanServer
    public final ObjectInstance createMBean(String className, ObjectName name,
                                      ObjectName loaderName, Object params[],
                                      String signature[])
            throws ReflectionException, InstanceAlreadyExistsException,
                   MBeanRegistrationException, MBeanException,
                   NotCompliantMBeanException, InstanceNotFoundException{
        return getInterceptorForCreate(name).createMBean(className,name,loaderName,
                                                   params,signature);
    }

    // From MBeanServer
    public final ObjectInstance registerMBean(Object object, ObjectName name)
            throws InstanceAlreadyExistsException, MBeanRegistrationException,
                   NotCompliantMBeanException {
        return getInterceptorForCreate(name).registerMBean(object,name);
    }

    // From MBeanServer
    public final void unregisterMBean(ObjectName name)
            throws InstanceNotFoundException, MBeanRegistrationException {
        getInterceptorForInstance(name).unregisterMBean(name);
    }

    // From MBeanServer
    public final ObjectInstance getObjectInstance(ObjectName name)
            throws InstanceNotFoundException {
        return getInterceptorForInstance(name).getObjectInstance(name);
    }

    // From MBeanServer
    public final Set<ObjectInstance> queryMBeans(ObjectName name,
            QueryExp query) {
        final QueryInterceptor queryInvoker =
                getInterceptorForQuery(name);
        if (queryInvoker == null)  return Collections.emptySet();
        else return queryInvoker.queryMBeans(name,query);
    }

    // From MBeanServer
    public final Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        final QueryInterceptor queryInvoker =
                getInterceptorForQuery(name);
        if (queryInvoker == null)  return Collections.emptySet();
        else return queryInvoker.queryNames(name,query);
    }

    // From MBeanServer
    public final boolean isRegistered(ObjectName name) {
        final MBeanServer mbs = getInterceptorOrNullFor(name);
        if (mbs == null) return false;
        else return mbs.isRegistered(name);
    }

    // From MBeanServer
    public Integer getMBeanCount() {
        return getNextInterceptor().getMBeanCount();
    }

    // From MBeanServer
    public final Object getAttribute(ObjectName name, String attribute)
            throws MBeanException, AttributeNotFoundException,
                   InstanceNotFoundException, ReflectionException {
        return getInterceptorForInstance(name).getAttribute(name,attribute);
    }

    // From MBeanServer
    public final AttributeList getAttributes(ObjectName name,
            String[] attributes)
            throws InstanceNotFoundException, ReflectionException {
        return getInterceptorForInstance(name).getAttributes(name,attributes);
    }

    // From MBeanServer
    public final void setAttribute(ObjectName name, Attribute attribute)
            throws InstanceNotFoundException, AttributeNotFoundException,
                   InvalidAttributeValueException, MBeanException,
                   ReflectionException {
        getInterceptorForInstance(name).setAttribute(name,attribute);
    }

    // From MBeanServer
    public final AttributeList setAttributes(ObjectName name,
                                       AttributeList attributes)
        throws InstanceNotFoundException, ReflectionException {
        return getInterceptorForInstance(name).setAttributes(name,attributes);
    }

    // From MBeanServer
    public final Object invoke(ObjectName name, String operationName,
                         Object params[], String signature[])
            throws InstanceNotFoundException, MBeanException,
                   ReflectionException {
        return getInterceptorForInstance(name).invoke(name,operationName,params,
                signature);
    }

    // From MBeanServer
    public String getDefaultDomain() {
        return getNextInterceptor().getDefaultDomain();
    }

    /**
     * Returns the list of domains in which any MBean is currently
     * registered.
     */
    public abstract String[] getDomains();

    // From MBeanServer
    public final void addNotificationListener(ObjectName name,
                                        NotificationListener listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws InstanceNotFoundException {
        getInterceptorForInstance(name).
                addNotificationListener(name,listener,filter,
                handback);
    }


    // From MBeanServer
    public final void addNotificationListener(ObjectName name,
                                        ObjectName listener,
                                        NotificationFilter filter,
                                        Object handback)
            throws InstanceNotFoundException {
        getInterceptorForInstance(name).
                addNotificationListener(name,listener,filter,
                handback);
    }

    // From MBeanServer
    public final void removeNotificationListener(ObjectName name,
                                           ObjectName listener)
        throws InstanceNotFoundException, ListenerNotFoundException {
        getInterceptorForInstance(name).
                removeNotificationListener(name,listener);
    }

    // From MBeanServer
    public final void removeNotificationListener(ObjectName name,
                                           ObjectName listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException {
        getInterceptorForInstance(name).
                removeNotificationListener(name,listener,filter,
                handback);
    }


    // From MBeanServer
    public final void removeNotificationListener(ObjectName name,
                                           NotificationListener listener)
            throws InstanceNotFoundException, ListenerNotFoundException {
        getInterceptorForInstance(name).
                removeNotificationListener(name,listener);
    }

    // From MBeanServer
    public final void removeNotificationListener(ObjectName name,
                                           NotificationListener listener,
                                           NotificationFilter filter,
                                           Object handback)
            throws InstanceNotFoundException, ListenerNotFoundException {
        getInterceptorForInstance(name).
                removeNotificationListener(name,listener,filter,
                handback);
    }

    // From MBeanServer
    public final MBeanInfo getMBeanInfo(ObjectName name)
            throws InstanceNotFoundException, IntrospectionException,
                   ReflectionException {
        return getInterceptorForInstance(name).getMBeanInfo(name);
    }


    // From MBeanServer
    public final boolean isInstanceOf(ObjectName name, String className)
            throws InstanceNotFoundException {
        return getInterceptorForInstance(name).isInstanceOf(name,className);
    }

    // From MBeanServer
    public final ClassLoader getClassLoaderFor(ObjectName mbeanName)
        throws InstanceNotFoundException {
        return getInterceptorForInstance(mbeanName).
                getClassLoaderFor(mbeanName);
    }

    // From MBeanServer
    public final ClassLoader getClassLoader(ObjectName loaderName)
        throws InstanceNotFoundException {
        return getInterceptorForInstance(loaderName).
                getClassLoader(loaderName);
    }

}
