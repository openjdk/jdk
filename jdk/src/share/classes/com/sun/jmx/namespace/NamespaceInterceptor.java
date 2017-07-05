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

import java.util.ArrayList;
import java.util.List;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
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


    // The target name space in which the NamepsaceHandler is mounted.
    private final String           targetNs;

    private final String           serverName;

    private final ObjectNameRouter proc;

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
        final MBeanServer c = super.source();
        if (c != null) return c;
        // should not come here
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

    @Override
    protected ObjectName toSource(ObjectName targetName) {
        return proc.toSourceContext(targetName, true);
    }

    @Override
    protected ObjectName toTarget(ObjectName sourceName) {
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
