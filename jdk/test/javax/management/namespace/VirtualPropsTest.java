/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 5108776 5072476
 * @summary Test the properties use case for Virtual MBeans that is documented
 * in MBeanServerSupport.
 * @author Eamonn McManus
 */

import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.namespace.JMXNamespace;
import javax.management.namespace.JMXNamespaces;
import javax.management.namespace.VirtualEventManager;
import javax.management.namespace.MBeanServerSupport;

public class VirtualPropsTest {
    public static interface PropertyMBean {
        public String getValue();
    }

    public static class PropsMBS extends MBeanServerSupport {
        private static ObjectName newObjectName(String name) {
            try {
                return new ObjectName(name);
            } catch (MalformedObjectNameException e) {
                throw new AssertionError(e);
            }
        }

        public static class PropertyImpl implements PropertyMBean {
            private final String name;

            public PropertyImpl(String name) {
                this.name = name;
            }

            public String getValue() {
                return System.getProperty(name);
            }
        }

        @Override
        public DynamicMBean getDynamicMBeanFor(ObjectName name)
                throws InstanceNotFoundException {
            ObjectName namePattern = newObjectName(
                        "com.example:type=Property,name=\"*\"");
            if (!namePattern.apply(name))
                throw new InstanceNotFoundException(name);

            String propName = ObjectName.unquote(name.getKeyProperty("name"));
            if (System.getProperty(propName) == null)
                throw new InstanceNotFoundException(name);
            PropertyMBean propMBean = new PropertyImpl(propName);
            return new StandardMBean(propMBean, PropertyMBean.class, false);
        }

        @Override
        protected Set<ObjectName> getNames() {
            Set<ObjectName> names = new TreeSet<ObjectName>();
            Properties props = System.getProperties();
            for (String propName : props.stringPropertyNames()) {
                ObjectName objectName = newObjectName(
                        "com.example:type=Property,name=" +
                        ObjectName.quote(propName));
                names.add(objectName);
            }
            return names;
        }

        private final VirtualEventManager vem = new VirtualEventManager();

        @Override
        public NotificationEmitter getNotificationEmitterFor(
                ObjectName name) throws InstanceNotFoundException {
            getDynamicMBeanFor(name);  // check that the name is valid
            return vem.getNotificationEmitterFor(name);
        }

        public void propertyChanged(String name, String newValue) {
            ObjectName objectName = newObjectName(
                    "com.example:type=Property,name=" + ObjectName.quote(name));
            Notification n = new Notification(
                    "com.example.property.changed", objectName, 0L,
                    "Property " + name + " changed");
            n.setUserData(newValue);
            vem.publish(objectName, n);
        }
    }

    static class QueueListener implements NotificationListener {
        BlockingQueue<Notification> q = new ArrayBlockingQueue<Notification>(10);
        public void handleNotification(Notification notification,
                                       Object handback) {
            q.add(notification);
        }
    }

    public static void main(String[] args) throws Exception {
        MBeanServer mmbs = ManagementFactory.getPlatformMBeanServer();
        String namespace = "props";
        PropsMBS pmbs = new PropsMBS();
        Object namespaceMBean = new JMXNamespace(pmbs);
        mmbs.registerMBean(namespaceMBean, new ObjectName(
                namespace + "//:type=JMXNamespace"));
        MBeanServer mbs = JMXNamespaces.narrowToNamespace(mmbs, namespace);

        Properties props = System.getProperties();

        int nprops = props.stringPropertyNames().size();
        if (nprops != mbs.getMBeanCount()) {
            throw new Exception(String.format("Properties: %d; MBeans: %d",
                    nprops, mbs.getMBeanCount()));
        }

        for (String propName : props.stringPropertyNames()) {
            ObjectName propObjectName = new ObjectName(
                    "com.example:type=Property,name=" + ObjectName.quote(propName));
            PropertyMBean propProx = JMX.newMBeanProxy(
                    mbs, propObjectName, PropertyMBean.class);
            String propValue = propProx.getValue();
            String realPropValue = props.getProperty(propName);
            if (!realPropValue.equals(propValue)) {
                throw new Exception(String.format("Property %s: value is \"%s\"; " +
                        "mbean says \"%s\"", propName, realPropValue, propValue));
            }
        }

        ObjectName fooPropObjectName =
                new ObjectName("com.example:type=Property,name=\"java.home\"");
        QueueListener ql = new QueueListener();
        mbs.addNotificationListener(fooPropObjectName, ql, null, null);
        pmbs.propertyChanged("java.home", "bar");
        Notification n = ql.q.poll(1, TimeUnit.SECONDS);
        if (n == null)
            throw new Exception("Notif didn't arrive");
        if (!"bar".equals(n.getUserData()))
            throw new Exception("Bad user data: " + n.getUserData());

        System.out.println("TEST PASSED");
    }
}
