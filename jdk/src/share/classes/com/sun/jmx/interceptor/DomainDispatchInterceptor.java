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
import com.sun.jmx.namespace.DomainInterceptor;
import java.util.Queue;
import java.util.Set;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.namespace.JMXDomain;
import static javax.management.namespace.JMXNamespaces.NAMESPACE_SEPARATOR;

/**
 * A dispatcher that dispatch incoming MBeanServer requests to
 * DomainInterceptors.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
//
// See comments in  DispatchInterceptor.
//
class DomainDispatchInterceptor
        extends DispatchInterceptor<DomainInterceptor, JMXDomain> {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    private static final ObjectName ALL_DOMAINS =
            JMXDomain.getDomainObjectName("*");


    /**
     *  A QueryInterceptor that perform & aggregates queries spanning several
     *  domains.
     */
    final static class AggregatingQueryInterceptor extends QueryInterceptor {

        private final DomainDispatchInterceptor parent;
        AggregatingQueryInterceptor(DomainDispatchInterceptor dispatcher) {
            super(dispatcher.nextInterceptor);
            parent = dispatcher;
        }

        /**
         * Perform queryNames or queryMBeans, depending on which QueryInvoker
         * is passed as argument. This is closures without closures.
         **/
        @Override
        <T> Set<T> query(ObjectName pattern, QueryExp query,
                QueryInvoker<T> invoker, MBeanServer localNamespace) {
            final Set<T> local = invoker.query(localNamespace, pattern, query);

            // Add all matching MBeans from local namespace.
            final Set<T> res = Util.cloneSet(local);

            if (pattern == null) pattern = ObjectName.WILDCARD;
            final boolean all = pattern.getDomain().equals("*");

            final String domain = pattern.getDomain();

            // If there's no domain pattern, just include the pattern's domain.
            // Otherwiae, loop over all virtual domains (parent.getKeys()).
            final String[] keys =
                (pattern.isDomainPattern() ?
                    parent.getKeys() : new String[]{domain});

            // Add all matching MBeans from each virtual domain
            //
            for (String key : keys) {
                // Only invoke those virtual domain which are selected
                // by the domain pattern
                //
                if (!all && !Util.isDomainSelected(key, domain))
                    continue;

                try {
                    final MBeanServer mbs = parent.getInterceptor(key);

                    // mbs can be null if the interceptor was removed
                    // concurrently...
                    // See handlerMap and getKeys() in DispatchInterceptor
                    //
                    if (mbs == null) continue;

                    // If the domain is selected, we can replace the pattern
                    // by the actual domain. This is safer if we want to avoid
                    // a domain (which could be backed up by an MBeanServer) to
                    // return names from outside the domain.
                    // So instead of asking the domain handler for "foo" to
                    // return all names which match "?o*:type=Bla,*" we're
                    // going to ask it to return all names which match
                    // "foo:type=Bla,*"
                    //
                    final ObjectName subPattern = pattern.withDomain(key);
                    res.addAll(invoker.query(mbs, subPattern, query));
                } catch (Exception x) {
                    LOG.finest("Ignoring exception " +
                            "when attempting to query namespace "+key+": "+x);
                    continue;
                }
            }
            return res;
        }
    }

    private final DefaultMBeanServerInterceptor nextInterceptor;
    private final String mbeanServerName;
    private final MBeanServerDelegate delegate;

    /**
     * Creates a DomainDispatchInterceptor with the specified
     * repository instance.
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
    public DomainDispatchInterceptor(MBeanServer         outer,
                            MBeanServerDelegate delegate,
                            MBeanInstantiator   instantiator,
                            Repository          repository,
                            NamespaceDispatchInterceptor namespaces)  {
           nextInterceptor = new DefaultMBeanServerInterceptor(outer,
                   delegate, instantiator,repository,namespaces);
           mbeanServerName = Util.getMBeanServerSecurityName(delegate);
           this.delegate = delegate;
    }

    final boolean isLocalHandlerNameFor(String domain,
            ObjectName handlerName) {
        if (domain == null) return true;
        return handlerName.getDomain().equals(domain) &&
               JMXDomain.TYPE_ASSIGNMENT.equals(
               handlerName.getKeyPropertyListString());
    }

    @Override
    void validateHandlerNameFor(String key, ObjectName name) {
        super.validateHandlerNameFor(key,name);
        final String[] domains = nextInterceptor.getDomains();
        for (int i=0;i<domains.length;i++) {
            if (domains[i].equals(key))
                throw new IllegalArgumentException("domain "+key+
                        " is not empty");
        }
    }

    @Override
    final MBeanServer getInterceptorOrNullFor(ObjectName name) {

        if (name == null) return nextInterceptor;

        final String domain = name.getDomain();
        if (domain.endsWith(NAMESPACE_SEPARATOR))
            return nextInterceptor; // This can be a namespace handler.
        if (domain.contains(NAMESPACE_SEPARATOR))
            return null; // shouldn't reach here.
        if (isLocalHandlerNameFor(domain,name)) {
            // This is the name of a JMXDomain MBean. Return nextInterceptor.
            LOG.finer("dispatching to local namespace");
            return nextInterceptor;
        }

        final DomainInterceptor ns = getInterceptor(domain);
        if (ns == null) {
            // no JMXDomain found for that domain - return nextInterceptor.
            if (LOG.isLoggable(Level.FINER)) {
                LOG.finer("dispatching to local namespace: " + domain);
            }
            return getNextInterceptor();
        }

        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("dispatching to domain: " + domain);
        }
        return ns;
    }

    // This method returns true if the given pattern must be evaluated against
    // several interceptors. This happens when either:
    //
    //   a) the pattern can select several domains (it's null, or it's a
    //        domain pattern)
    //   or b) it's not a domain pattern, but it might select the name of a
    //        JMXDomain MBean in charge of that domain. Since the JMXDomain
    //        MBean is located in the nextInterceptor, the pattern might need
    //        to be evaluated on two interceptors.
    //
    // 1. When this method returns false, the query is evaluated on a single
    // interceptor:
    //    The interceptor for pattern.getDomain(), if there is one,
    //    or the next interceptor, if there is none.
    //
    // 2. When this method returns true, we loop over all the domain
    // interceptors:
    //    in the list, and if the domain pattern matches the interceptor domain
    //    we evaluate the query on that interceptor and aggregate the results.
    //    Eventually we also evaluate the pattern against the next interceptor.
    //
    // See getInterceptorForQuery below.
    //
    private boolean multipleQuery(ObjectName pattern) {
        // case a) above
        if (pattern == null) return true;
        if (pattern.isDomainPattern()) return true;

        try {
            // case b) above.
            //
            // This is a bit of a hack. If there's any chance that a JMXDomain
            // MBean name is selected by the given pattern then we must include
            // the local namespace in our search.
            //
            // Returning true will have this effect. see 2. above.
            //
            if (pattern.apply(ALL_DOMAINS.withDomain(pattern.getDomain())))
                return true;
        } catch (MalformedObjectNameException x) {
            // should not happen
            throw new IllegalArgumentException(String.valueOf(pattern), x);
        }
        return false;
    }

    @Override
    final QueryInterceptor getInterceptorForQuery(ObjectName pattern) {

        // Check if we need to aggregate.
        if (multipleQuery(pattern))
            return new AggregatingQueryInterceptor(this);

        // We don't need to aggregate: do the "simple" thing...
        final String domain = pattern.getDomain();

        // Do we have a virtual domain?
        final DomainInterceptor ns = getInterceptor(domain);
        if (ns != null) {
            if (LOG.isLoggable(Level.FINER))
                LOG.finer("dispatching to domain: " + domain);
            return new QueryInterceptor(ns);
        }

        // We don't have a virtual domain. Send to local domains.
        if (LOG.isLoggable(Level.FINER))
             LOG.finer("dispatching to local namespace: " + domain);
        return new QueryInterceptor(nextInterceptor);
    }

    @Override
    final ObjectName getHandlerNameFor(String key)
        throws MalformedObjectNameException {
        return JMXDomain.getDomainObjectName(key);
    }

    @Override
    final public String getHandlerKey(ObjectName name) {
        return name.getDomain();
    }

    @Override
    final DomainInterceptor createInterceptorFor(String key,
            ObjectName name, JMXDomain handler,
            Queue<Runnable> postRegisterQueue) {
        final DomainInterceptor ns =
                new DomainInterceptor(mbeanServerName,handler,key);
        ns.addPostRegisterTask(postRegisterQueue, delegate);
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("DomainInterceptor created: "+ns);
        }
        return ns;
    }

    @Override
    final void interceptorReleased(DomainInterceptor interceptor,
            Queue<Runnable> postDeregisterQueue) {
        interceptor.addPostDeregisterTask(postDeregisterQueue, delegate);
    }

    @Override
    final DefaultMBeanServerInterceptor getNextInterceptor() {
        return nextInterceptor;
    }

    /**
     * Returns the list of domains in which any MBean is currently
     * registered.
     */
    @Override
    public String[] getDomains() {
        // A JMXDomain is registered in its own domain.
        // Therefore, nextInterceptor.getDomains() contains all domains.
        // In addition, nextInterceptor will perform the necessary
        // MBeanPermission checks for getDomains().
        //
        return nextInterceptor.getDomains();
    }

    /**
     * Returns the number of MBeans registered in the MBean server.
     */
    @Override
    public Integer getMBeanCount() {
        int count = getNextInterceptor().getMBeanCount();
        final String[] keys = getKeys();
        for (String key:keys) {
            final MBeanServer mbs = getInterceptor(key);
            if (mbs == null) continue;
            count += mbs.getMBeanCount();
        }
        return count;
    }
}
