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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespacePermission;

/**
 * A NamespaceInterceptor wraps a JMXNamespace, performing
 * ObjectName rewriting.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public class NamespaceInterceptor extends HandlerInterceptor<JMXNamespace> {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;
    private static final Logger PROBE_LOG = Logger.getLogger(
            JmxProperties.NAMESPACE_LOGGER+".probe");

    // The target name space in which the NamepsaceHandler is mounted.
    private final String           targetNs;

    private final String           serverName;

    private final ObjectNameRouter proc;

    /**
     * Internal hack. The JMXRemoteNamespace can be closed and reconnected.
     * Each time the JMXRemoteNamespace connects, a probe should be sent
     * to detect cycle. The MBeanServer exposed by JMXRemoteNamespace thus
     * implements the DynamicProbe interface, which makes it possible for
     * this handler to know that it should send a new probe.
     *
     * XXX: TODO this probe thing is way too complex and fragile.
     *      This *must* go away or be replaced by something simpler.
     *      ideas are welcomed.
     **/
    public static interface DynamicProbe {
        public boolean isProbeRequested();
    }

    /**
     * Creates a new instance of NamespaceInterceptor
     */
    public NamespaceInterceptor(
            String serverName,
            JMXNamespace handler,
            String targetNamespace) {
        super(handler);
        this.serverName = serverName;
        this.targetNs =
                ObjectNameRouter.normalizeNamespacePath(targetNamespace,
                true, true, false);
        proc = new ObjectNameRouter(targetNamespace, "");
    }

    @Override
    public String toString() {
        return this.getClass().getName()+"(parent="+serverName+
                ", namespace="+this.targetNs+")";
    }

    /*
     * XXX: TODO this probe thing is way too complex and fragile.
     *      This *must* go away or be replaced by something simpler.
     *      ideas are welcomed.
     */
    private volatile boolean probed = false;
    private volatile ObjectName probe;

    // Query Pattern that we will send through the source server in order
    // to detect self-linking namespaces.
    //
    // XXX: TODO this probe thing is way too complex and fragile.
    //      This *must* go away or be replaced by something simpler.
    //      ideas are welcomed.
    final ObjectName makeProbePattern(ObjectName probe)
            throws MalformedObjectNameException {

        // we could probably link the probe pattern with the probe - e.g.
        // using the UUID as key in the pattern - but is it worth it? it
        // also has some side effects on the context namespace - because
        // such a probe may get rejected by the jmx.context// namespace.
        //
        // The trick here is to devise a pattern that is not likely to
        // be blocked by intermediate levels. Querying for all namespace
        // handlers in the source (or source namespace) is more likely to
        // achieve this goal.
        //
        return ObjectName.getInstance("*" +
                JMXNamespaces.NAMESPACE_SEPARATOR + ":" +
                JMXNamespace.TYPE_ASSIGNMENT);
    }

    // tell whether the name pattern corresponds to what might have been
    // sent as a probe.
    // XXX: TODO this probe thing is way too complex and fragile.
    //      This *must* go away or be replaced by something simpler.
    //      ideas are welcomed.
    final boolean isProbePattern(ObjectName name) {
        final ObjectName p = probe;
        if (p == null) return false;
        try {
            return String.valueOf(name).endsWith(targetNs+
                JMXNamespaces.NAMESPACE_SEPARATOR + "*" +
                JMXNamespaces.NAMESPACE_SEPARATOR + ":" +
                JMXNamespace.TYPE_ASSIGNMENT);
        } catch (RuntimeException x) {
            // should not happen.
            PROBE_LOG.finest("Ignoring unexpected exception in self link detection: "+
                    x);
            return false;
        }
    }

    // The first time a request reaches this NamespaceInterceptor, the
    // interceptor will send a probe to detect whether the underlying
    // JMXNamespace links to itslef.
    //
    // One way to create such self-linking namespace would be for instance
    // to create a JMXNamespace whose getSourceServer() method would return:
    // JMXNamespaces.narrowToNamespace(getMBeanServer(),
    //                                 getObjectName().getDomain())
    //
    // If such an MBeanServer is returned, then any call to that MBeanServer
    // will trigger an infinite loop.
    // There can be even trickier configurations if remote connections are
    // involved.
    //
    // In order to prevent this from happening, the NamespaceInterceptor will
    // send a probe, in an attempt to detect whether it will receive it at
    // the other end. If the probe is received, an exception will be thrown
    // in order to break the recursion. The probe is only sent once - when
    // the first request to the namespace occurs. The DynamicProbe interface
    // can also be used by a Sun JMXNamespace implementation to request the
    // emission of a probe at any time (see JMXRemoteNamespace
    // implementation).
    //
    // Probes work this way: the NamespaceInterceptor sets a flag and sends
    // a queryNames() request. If a queryNames() request comes in when the flag
    // is on, then it deduces that there is a self-linking loop - and instead
    // of calling queryNames() on the source MBeanServer of the JMXNamespace
    // handler (which would cause the loop to go on) it breaks the recursion
    // by returning the probe ObjectName.
    // If the NamespaceInterceptor receives the probe ObjectName as result of
    // its original sendProbe() request it knows that it has been looping
    // back on itslef and throws an IOException...
    //
    //
    // XXX: TODO this probe thing is way too complex and fragile.
    //      This *must* go away or be replaced by something simpler.
    //      ideas are welcomed.
    //
    final void sendProbe(MBeanServerConnection msc)
            throws IOException {
        try {
            PROBE_LOG.fine("Sending probe");

            // This is just to prevent any other thread to modify
            // the probe while the detection cycle is in progress.
            //
            final ObjectName probePattern;
            // we don't want to synchronize on this - we use targetNs
            // because it's non null and final.
            synchronized (targetNs) {
                probed = false;
                if (probe != null) {
                    throw new IOException("concurent connection in progress");
                }
                final String uuid = UUID.randomUUID().toString();
                final String endprobe =
                        JMXNamespaces.NAMESPACE_SEPARATOR + uuid +
                        ":type=Probe,key="+uuid;
                final ObjectName newprobe =
                        ObjectName.getInstance(endprobe);
                probePattern = makeProbePattern(newprobe);
                probe = newprobe;
            }

            try {
                PROBE_LOG.finer("Probe query: "+probePattern+" expecting: "+probe);
                final Set<ObjectName> res = msc.queryNames(probePattern, null);
                final ObjectName expected = probe;
                PROBE_LOG.finer("Probe res: "+res);
                if (res.contains(expected)) {
                    throw new IOException("namespace " +
                            targetNs + " is linking to itself: " +
                            "cycle detected by probe");
                }
            } catch (SecurityException x) {
                PROBE_LOG.finer("Can't check for cycles: " + x);
                // can't do anything....
            } catch (RuntimeException x) {
                PROBE_LOG.finer("Exception raised by queryNames: " + x);
                throw x;
            } finally {
                probe = null;
            }
        } catch (MalformedObjectNameException x) {
            final IOException io =
                    new IOException("invalid name space: probe failed");
            io.initCause(x);
            throw io;
        }
        PROBE_LOG.fine("Probe returned - no cycles");
        probed = true;
    }

    // allows a Sun implementation JMX Namespace, such as the
    // JMXRemoteNamespace, to control when a probe should be sent.
    //
    // XXX: TODO this probe thing is way too complex and fragile.
    //      This *must* go away or be replaced by something simpler.
    //      ideas are welcomed.
    private boolean isProbeRequested(Object o) {
        if (o instanceof DynamicProbe)
            return ((DynamicProbe)o).isProbeRequested();
        return false;
    }

    /**
     * This method will send a probe to detect self-linking name spaces.
     * A self linking namespace is a namespace that links back directly
     * on itslef. Calling a method on such a name space always results
     * in an infinite loop going through:
     * [1]MBeanServer -> [2]NamespaceDispatcher -> [3]NamespaceInterceptor
     * [4]JMXNamespace -> { network // or cd // or ... } -> [5]MBeanServer
     * with exactly the same request than [1]...
     *
     * The namespace interceptor [2] tries to detect such condition the
     * *first time* that the connection is used. It does so by setting
     * a flag, and sending a queryNames() through the name space. If the
     * queryNames comes back, it knows that there's a loop.
     *
     * The DynamicProbe interface can also be used by a Sun JMXNamespace
     * implementation to request the emission of a probe at any time
     * (see JMXRemoteNamespace implementation).
     */
    private MBeanServer connection() {
        try {
            final MBeanServer c = super.source();
            if (probe != null) // should not happen
                throw new RuntimeException("connection is being probed");

            if (probed == false || isProbeRequested(c)) {
                try {
                    // Should not happen if class well behaved.
                    // Never probed. Force it.
                    //System.err.println("sending probe for " +
                    //        "target="+targetNs+", source="+srcNs);
                    sendProbe(c);
                } catch (IOException io) {
                    throw new RuntimeException(io.getMessage(), io);
                }
            }

            if (c != null) {
                return c;
            }
        } catch (RuntimeException x) {
            throw x;
        }
        throw new NullPointerException("getMBeanServerConnection");
    }


    @Override
    protected MBeanServer source() {
        return connection();
    }

    @Override
    protected MBeanServer getServerForLoading() {
        // don't want to send probe on getClassLoader/getClassLoaderFor
        return super.source();
    }

    /**
     * Calls {@link MBeanServerConnection#queryNames queryNames}
     * on the underlying
     * {@link #getMBeanServerConnection MBeanServerConnection}.
     **/
    @Override
    public final Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        // XXX: TODO this probe thing is way too complex and fragile.
        //      This *must* go away or be replaced by something simpler.
        //      ideas are welcomed.
        PROBE_LOG.finer("probe is: "+probe+" pattern is: "+name);
        if (probe != null && isProbePattern(name)) {
            PROBE_LOG.finer("Return probe: "+probe);
            return Collections.singleton(probe);
        }
        return super.queryNames(name, query);
    }

    @Override
    protected ObjectName toSource(ObjectName targetName)
            throws MalformedObjectNameException {
        return proc.toSourceContext(targetName, true);
    }

    @Override
    protected ObjectName toTarget(ObjectName sourceName)
            throws MalformedObjectNameException {
        return proc.toTargetContext(sourceName, false);
    }

    //
    // Implements permission checks.
    //
    @Override
    void check(ObjectName routingName, String member, String action) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return;
        if ("getDomains".equals(action)) return;
        final JMXNamespacePermission perm =
                new  JMXNamespacePermission(serverName,member,
                routingName,action);
        sm.checkPermission(perm);
    }

    @Override
    void checkCreate(ObjectName routingName, String className, String action) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return;
        final JMXNamespacePermission perm =
                new  JMXNamespacePermission(serverName,className,
                routingName,action);
        sm.checkPermission(perm);
    }

    //
    // Implements permission filters for attributes...
    //
    @Override
    AttributeList checkAttributes(ObjectName routingName,
            AttributeList attributes, String action) {
        check(routingName,null,action);
        if (attributes == null || attributes.isEmpty()) return attributes;
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return attributes;
        final AttributeList res = new AttributeList();
        for (Attribute at : attributes.asList()) {
            try {
                check(routingName,at.getName(),action);
                res.add(at);
            } catch (SecurityException x) { // DLS: OK
                continue;
            }
        }
        return res;
    }

    //
    // Implements permission filters for attributes...
    //
    @Override
    String[] checkAttributes(ObjectName routingName, String[] attributes,
            String action) {
        check(routingName,null,action);
        if (attributes == null || attributes.length==0) return attributes;
        final SecurityManager sm = System.getSecurityManager();
        if (sm == null) return attributes;
        final List<String> res = new ArrayList<String>(attributes.length);
        for (String at : attributes) {
            try {
                check(routingName,at,action);
                res.add(at);
            } catch (SecurityException x) { // DLS: OK
                continue;
            }
        }
        return res.toArray(new String[res.size()]);
    }

    //
    // Implements permission filters for domains...
    //
    @Override
    String[] checkDomains(String[] domains, String action) {
        // in principle, this method is never called because
        // getDomains() will never be called - since there's
        // no way that MBeanServer.getDomains() can be routed
        // to a NamespaceInterceptor.
        //
        // This is also why there's no getDomains() in a
        // JMXNamespacePermission...
        //
        return super.checkDomains(domains, action);
    }

    //
    // Implements permission filters for queries...
    //
    @Override
    boolean checkQuery(ObjectName routingName, String action) {
        try {
            check(routingName,null,action);
            return true;
        } catch (SecurityException x) { // DLS: OK
            return false;
        }
    }

}
