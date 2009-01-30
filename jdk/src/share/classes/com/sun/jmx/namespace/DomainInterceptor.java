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
import com.sun.jmx.mbeanserver.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanPermission;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.namespace.JMXDomain;

/**
 * A DomainInterceptor wraps a JMXDomain.
 * <p><b>
 * This API is a Sun internal API and is subject to changes without notice.
 * </b></p>
 * @since 1.7
 */
public class DomainInterceptor extends HandlerInterceptor<JMXDomain> {

    // TODO: Ideally DomainInterceptor should be replaced by
    //       something at Repository level.
    //       The problem there will be that we may need to
    //       reinstantiate the 'queryPerformedByRepos' boolean
    //       [or we will need to wrap the repository in
    //        a 'RepositoryInterceptor'?]
    //       Also there's no real need for a DomainInterceptor to
    //       extend RewritingMBeanServerConnection.


    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    private final String           domainName;
    private volatile ObjectName    ALL;
    private final String           serverName;
    private volatile NotificationListener mbsListener;

    private static class PatternNotificationFilter
            implements NotificationFilter {

        final ObjectName pattern;
        public PatternNotificationFilter(ObjectName pattern) {
            this.pattern = pattern==null?ObjectName.WILDCARD:pattern;
        }

        public boolean isNotificationEnabled(Notification notification) {
            if (!(notification instanceof MBeanServerNotification))
                return false;
            final MBeanServerNotification mbsn =
                    (MBeanServerNotification) notification;
            if (pattern.apply(mbsn.getMBeanName()))
                return true;
            return false;
        }

        static final long serialVersionUID = 7409950927025262111L;
    }

    /**
     * Creates a new instance of NamespaceInterceptor
     */
    public DomainInterceptor(String serverName,
                             JMXDomain handler,
                             String domainName) {
        super(handler);
        this.domainName = domainName;
        this.serverName = serverName;
        ALL = ObjectName.valueOf(domainName+":*");
    }

    @Override
    public String toString() {
        return this.getClass().getName()+"(parent="+serverName+
                ", domain="+this.domainName+")";
    }

    final void connectDelegate(final MBeanServerDelegate delegate)
            throws InstanceNotFoundException {
        final NotificationFilter filter =
                new PatternNotificationFilter(getPatternFor(null));
        synchronized (this) {
            if (mbsListener == null) {
                mbsListener = new NotificationListener() {
                    public void handleNotification(Notification notification,
                        Object handback) {
                        if (filter.isNotificationEnabled(notification))
                            delegate.sendNotification(notification);
                    }
                };
            }
        }

        getHandlerInterceptorMBean().
                addMBeanServerNotificationListener(mbsListener, filter);
    }

    final void disconnectDelegate()
            throws InstanceNotFoundException, ListenerNotFoundException {
        final NotificationListener l;
        synchronized (this) {
            l = mbsListener;
            if (l == null) return;
            mbsListener = null;
        }
        getHandlerInterceptorMBean().removeMBeanServerNotificationListener(l);
    }

    public final void addPostRegisterTask(Queue<Runnable> queue,
            final MBeanServerDelegate delegate) {
        if (queue == null)
            throw new IllegalArgumentException("task queue must not be null");
        final Runnable task1 = new Runnable() {
            public void run() {
                try {
                    connectDelegate(delegate);
                } catch (Exception x) {
                    throw new UnsupportedOperationException(
                            "notification forwarding",x);
                }
            }
        };
        queue.add(task1);
    }

    public final void addPostDeregisterTask(Queue<Runnable> queue,
            final MBeanServerDelegate delegate) {
        if (queue == null)
            throw new IllegalArgumentException("task queue must not be null");
        final Runnable task1 = new Runnable() {
            public void run() {
                try {
                    disconnectDelegate();
                } catch (Exception x) {
                    throw new UnsupportedOperationException(
                            "notification forwarding",x);
                }
            }
        };
        queue.add(task1);
    }

    // No name conversion for JMXDomains...
    // Throws IllegalArgumentException if targetName.getDomain() is not
    // in the domain handled.
    //
    @Override
    protected ObjectName toSource(ObjectName targetName) {
        if (targetName == null) return null;
        if (targetName.isDomainPattern()) return targetName;
        final String targetDomain = targetName.getDomain();

        // TODO: revisit this. RuntimeOperationsException may be better?
        //
        if (!targetDomain.equals(domainName))
            throw new IllegalArgumentException(targetName.toString());
        return targetName;
    }

    // No name conversion for JMXDomains...
    @Override
    protected ObjectName toTarget(ObjectName sourceName) {
        return sourceName;
    }



    /**
     * No rewriting: always return sources - stripping instances for which
     * the caller doesn't have permissions.
     **/
    @Override
    Set<ObjectInstance> processOutputInstances(Set<ObjectInstance> sources) {
        if (sources == null || sources.isEmpty() || !checkOn())
            return sources;
        final Set<ObjectInstance> res = Util.equivalentEmptySet(sources);
        for (ObjectInstance o : sources) {
            if (checkQuery(o.getObjectName(), "queryMBeans"))
                res.add(o);
        }
        return res;
    }


    /**
     * No rewriting: always return sourceNames - stripping names for which
     * the caller doesn't have permissions.
     **/
    @Override
    Set<ObjectName> processOutputNames(Set<ObjectName> sourceNames) {
        if (sourceNames == null || sourceNames.isEmpty() || !checkOn())
            return sourceNames;
        final Set<ObjectName> res = Util.equivalentEmptySet(sourceNames);
        for (ObjectName o : sourceNames) {
            if (checkQuery(o, "queryNames"))
                res.add(o);
        }
        return res;
    }

    /** No rewriting: always return source **/
    @Override
    ObjectInstance processOutputInstance(ObjectInstance source) {
        return source;
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        try {
            // We don't trust the wrapped JMXDomain...
            final ObjectName pattern = getPatternFor(name);
            final Set<ObjectName> res = super.queryNames(pattern,query);
            return Util.filterMatchingNames(pattern,res);
        } catch (Exception x) {
            if (LOG.isLoggable(Level.FINE))
                LOG.fine("Unexpected exception raised in queryNames: "+x);
            LOG.log(Level.FINEST,"Unexpected exception raised in queryNames",x);
            return Collections.emptySet();
        }
    }

    // Compute a new pattern which is a sub pattern of 'name' but only selects
    // the MBeans in domain 'domainName'
    // When we reach here, it has been verified that 'name' matches our domain
    // name (done by DomainDispatchInterceptor)
    private ObjectName getPatternFor(final ObjectName name) {
        if (name == null) return ALL;
        if (name.getDomain().equals(domainName)) return name;
        return name.withDomain(domainName);
   }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        try {
            // We don't trust the wrapped JMXDomain...
            final ObjectName pattern = getPatternFor(name);
            final Set<ObjectInstance> res = super.queryMBeans(pattern,query);
            return Util.filterMatchingInstances(pattern,res);
        } catch (Exception x) {
            if (LOG.isLoggable(Level.FINE))
                LOG.fine("Unexpected exception raised in queryNames: "+x);
            LOG.log(Level.FINEST,"Unexpected exception raised in queryNames",x);
            return Collections.emptySet();
        }
    }

    @Override
    public String getDefaultDomain() {
        return domainName;
    }

    @Override
    public String[] getDomains() {
        return new String[] {domainName};
    }

    // We call getMBeanCount() on the namespace rather than on the
    // source server in order to avoid counting MBeans which are not
    // in the domain.
    @Override
    public Integer getMBeanCount() {
        return getHandlerInterceptorMBean().getMBeanCount();
    }

    private boolean checkOn() {
        final SecurityManager sm = System.getSecurityManager();
        return (sm != null);
    }

    //
    // Implements permission checks.
    //
    @Override
    void check(ObjectName routingName, String member, String action) {
        if (!checkOn()) return;
        final String act = (action==null)?"-":action;
        if("queryMBeans".equals(act) || "queryNames".equals(act)) {
            // This is tricky. check with 3 parameters is called
            // by queryNames/queryMBeans before performing the query.
            // At this point we must check with no class name.
            // Therefore we pass a className of "-".
            // The filtering will be done later - processOutputNames and
            // processOutputInstance will call checkQuery.
            //
            check(routingName, "-", "-", act);
        } else {
            // This is also tricky:
            // passing null here will cause check to retrieve the classname,
            // if needed.
            check(routingName, null, member, act);
        }
    }

    //
    // Implements permission checks.
    //
    @Override
    void checkCreate(ObjectName routingName, String className, String action) {
        if (!checkOn()) return;
        check(routingName,className,"-",action);
    }

    //
    // Implements permission checks.
    //
    void check(ObjectName routingName, String className, String member,
            String action) {
        if (!checkOn()) return;
        final MBeanPermission perm;

        final String act = (action==null)?"-":action;
        if ("getDomains".equals(act)) { // ES: OK
            perm = new  MBeanPermission(serverName,"-",member,
                    routingName,act);
        } else {
            final String clazz =
                    (className==null)?getClassName(routingName):className;
            perm = new  MBeanPermission(serverName,clazz,member,
                    routingName,act);
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkPermission(perm);
    }

    String getClassName(ObjectName routingName) {
        if (routingName == null || routingName.isPattern()) return "-";
        try {
            return getHandlerInterceptorMBean().getSourceServer().
                    getObjectInstance(routingName).getClassName();
        } catch (InstanceNotFoundException ex) {
            LOG.finest("Can't get class name for "+routingName+
                    ", using \"-\". Cause is: "+ex);
            return "-";
        }
    }

    //
    // Implements permission filters for attributes...
    //
    @Override
    AttributeList checkAttributes(ObjectName routingName,
            AttributeList attributes, String action) {
        if (!checkOn()) return attributes;
        final String className = getClassName(routingName);
        check(routingName,className,"-",action);
        if (attributes == null || attributes.isEmpty()) return attributes;
        final AttributeList res = new AttributeList();
        for (Attribute at : attributes.asList()) {
            try {
                check(routingName,className,at.getName(),action);
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
        if (!checkOn()) return attributes;
        final String className = getClassName(routingName);
        check(routingName,className,"-",action);
        if (attributes == null || attributes.length==0) return attributes;
        final List<String> res = new ArrayList<String>(attributes.length);
        for (String at : attributes) {
            try {
                check(routingName,className,at,action);
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
         if (domains == null || domains.length==0 || !checkOn())
             return domains;
         int count=0;
         for (int i=0;i<domains.length;i++) {
             try {
                 check(ObjectName.valueOf(domains[i]+":x=x"),"-",
                         "-","getDomains");
             } catch (SecurityException x) { // DLS: OK
                 count++;
                 domains[i]=null;
             }
         }
         if (count == 0) return domains;
         final String[] res = new String[domains.length-count];
         count = 0;
         for (int i=0;i<domains.length;i++)
             if (domains[i]!=null) res[count++]=domains[i];
         return res;
    }

    //
    // Implements permission filters for queries...
    //
    @Override
    boolean checkQuery(ObjectName routingName, String action) {
        try {
            final String className = getClassName(routingName);
            check(routingName,className,"-",action);
            return true;
        } catch (SecurityException x) { // DLS: OK
            return false;
        }
    }
}
