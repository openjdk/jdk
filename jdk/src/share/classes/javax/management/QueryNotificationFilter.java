/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.management;

import com.sun.jmx.mbeanserver.NotificationMBeanSupport;
import com.sun.jmx.mbeanserver.Util;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;

/**
 * <p>General-purpose notification filter.  This filter can be used to
 * filter notifications from a possibly-remote MBean.  Most filtering
 * decisions can be coded using this filter, which avoids having to
 * write a custom implementation of the {@link NotificationFilter}
 * class.  Writing a custom implementation requires you to deploy it
 * on both the client and the server in the remote case, so using this class
 * instead is recommended where possible.</p>
 *
 * <p>This class uses the {@linkplain Query Query API} to specify the
 * filtering logic.  For example, to select only notifications where the
 * {@linkplain Notification#getType() type} is {@code "com.example.mytype"},
 * you could use</p>
 *
 * <pre>
 * NotificationFilter filter =
 *     new QueryNotificationFilter("Type = 'com.example.mytype'");
 * </pre>
 *
 * <p>or equivalently</p>
 *
 * <pre>
 * NotificationFilter filter =
 *     new QueryNotificationFilter(
 *             Query.eq(Query.attr("Type"), Query.value("com.example.mytype")));
 * </pre>
 *
 * <p>(This particular example could also use
 * {@link NotificationFilterSupport}.)</p>
 *
 * <p>Here are some other examples of filters you can specify with this class.</p>
 *
 * <dl>
 *
 * <dt>{@code QueryNotificationFilter("Type = 'com.example.type1' or
 * Type = 'com.example.type2'")}
 * <dd>Notifications where the type is either of the given strings.
 *
 * <dt>{@code QueryNotificationFilter("Type in ('com.example.type1',
 * 'com.example.type2')")}
 * <dd>Another way to write the previous example.
 *
 * <dt>{@code QueryNotificationFilter("SequenceNumber > 1000")}
 * <dd>Notifications where the {@linkplain Notification#getSequenceNumber()
 * sequence number} is greater than 1000.
 *
 * <dt>{@code QueryNotificationFilter(AttributeChangeNotification.class, null)}
 * <dd>Notifications where the notification class is
 * {@link AttributeChangeNotification} or a subclass of it.
 *
 * <dt>{@code QueryNotificationFilter(AttributeChangeNotification.class,
 * "AttributeName = 'Size'")}
 * <dd>Notifications where the notification class is
 * {@link AttributeChangeNotification} or a subclass, and where the
 * {@linkplain AttributeChangeNotification#getAttributeName() name of the
 * changed attribute} is {@code Size}.
 *
 * <dt>{@code QueryNotificationFilter(AttributeChangeNotification.class,
 * "AttributeName = 'Size' and NewValue - OldValue > 100")}
 * <dd>As above, but the difference between the
 * {@linkplain AttributeChangeNotification#getNewValue() new value} and the
 * {@linkplain AttributeChangeNotification#getOldValue() old value} must be
 * greater than 100.
 *
 * <dt>{@code QueryNotificationFilter("like 'com.example.mydomain:*'")}
 * <dd>Notifications where the {@linkplain Notification#getSource() source}
 * is an ObjectName that matches the pattern.
 *
 * <dt>{@code QueryNotificationFilter("Source.canonicalName like
 * 'com.example.mydomain:%'")}
 * <dd>Another way to write the previous example.
 *
 * <dt>{@code QueryNotificationFilter(MBeanServerNotification.class,
 * "Type = 'JMX.mbean.registered' and MBeanName.canonicalName like
 * 'com.example.mydomain:%'")}
 * <dd>Notifications of class {@link MBeanServerNotification} representing
 * an object registered in the domain {@code com.example.mydomain}.
 *
 * </dl>
 *
 * <h4>How it works</h4>
 *
 * <p>Although the examples above are clear, looking closely at the
 * Query API reveals a subtlety.  A {@link QueryExp} is evaluated on
 * an {@link ObjectName}, not a {@code Notification}.</p>
 *
 * <p>Every time a {@code Notification} is to be filtered by a
 * {@code QueryNotificationFilter}, a special {@link MBeanServer} is created.
 * This {@code MBeanServer} contains exactly one MBean, which represents the
 * {@code Notification}.  If the {@linkplain Notification#getSource()
 * source} of the notification is an {@code ObjectName}, which is
 * recommended practice, then the name of the MBean representing the
 * {@code Notification} will be this {@code ObjectName}.  Otherwise the
 * name is unspecified.</p>
 *
 * <p>The query specified in the {@code QueryNotificationFilter} constructor
 * is evaluated against this {@code MBeanServer} and {@code ObjectName},
 * and the filter returns true if and only if the query does.  If the
 * query throws an exception, then the filter will return false.</p>
 *
 * <p>The MBean representing the {@code Notification} has one attribute for
 * every property of the {@code Notification}. Specifically, for every public
 * method {@code T getX()} in the {@code NotificationClass}, the MBean will
 * have an attribute called {@code X} of type {@code T}. For example, if the
 * {@code Notification} is an {@code AttributeChangeNotification}, then the
 * MBean will have an attribute called {@code AttributeName} of type
 * {@code "java.lang.String"}, corresponding to the method {@link
 * AttributeChangeNotification#getAttributeName}.</p>
 *
 * <p>Query evaluation usually involves calls to the methods of {@code
 * MBeanServer}.  The methods have the following behavior:</p>
 *
 * <ul>
 * <li>The {@link MBeanServer#getAttribute getAttribute} method returns the
 * value of the corresponding property.
 * <li>The {@link MBeanServer#getObjectInstance getObjectInstance}
 * method returns an {@link ObjectInstance} where the {@link
 * ObjectInstance#getObjectName ObjectName} is the name of the MBean and the
 * {@link ObjectInstance#getClassName ClassName} is the class name of the
 * {@code Notification}.
 * <li>The {@link MBeanServer#isInstanceOf isInstanceOf} method returns true
 * if and only if the {@code Notification}'s {@code ClassLoader} can load the
 * named class, and the {@code Notification} is an {@linkplain Class#isInstance
 * instance} of that class.
 * </ul>
 *
 * <p>These are the only {@code MBeanServer} methods that are needed to
 * evaluate standard queries. The behavior of the other {@code MBeanServer}
 * methods is unspecified.</p>
 *
 * @since 1.7
 */
public class QueryNotificationFilter implements NotificationFilter {
    private static final long serialVersionUID = -8408613922660635231L;

    private static final ObjectName DEFAULT_NAME =
            ObjectName.valueOf(":type=Notification");
    private static final QueryExp trueQuery;
    static {
        ValueExp zero = Query.value(0);
        trueQuery = Query.eq(zero, zero);
    }

    private final QueryExp query;

    /**
     * Construct a {@code QueryNotificationFilter} that evaluates the given
     * {@code QueryExp} to determine whether to accept a notification.
     *
     * @param query the {@code QueryExp} to evaluate.  Can be null,
     * in which case all notifications are accepted.
     */
    public QueryNotificationFilter(QueryExp query) {
        if (query == null)
            this.query = trueQuery;
        else
            this.query = query;
    }

    /**
     * Construct a {@code QueryNotificationFilter} that evaluates the query
     * in the given string to determine whether to accept a notification.
     * The string is converted into a {@code QueryExp} using
     * {@link Query#fromString Query.fromString}.
     *
     * @param query the string specifying the query to evaluate.  Can be null,
     * in which case all notifications are accepted.
     *
      * @throws IllegalArgumentException if the string is not a valid
      * query string.
     */
    public QueryNotificationFilter(String query) {
        this(Query.fromString(query));
    }

    /**
     * <p>Construct a {@code QueryNotificationFilter} that evaluates the query
     * in the given string to determine whether to accept a notification,
     * and where the notification must also be an instance of the given class.
     * The string is converted into a {@code QueryExp} using
     * {@link Query#fromString Query.fromString}.</p>
     *
     * @param notifClass the class that the notification must be an instance of.
     * Cannot be null.
     *
     * @param query the string specifying the query to evaluate.  Can be null,
     * in which case all notifications are accepted.
     *
     * @throws IllegalArgumentException if the string is not a valid
     * query string, or if {@code notifClass} is null.
     */
    public QueryNotificationFilter(
            Class<? extends Notification> notifClass, String query) {
        this(Query.and(Query.isInstanceOf(Query.value(notNull(notifClass).getName())),
                       Query.fromString(query)));
    }

    private static <T> T notNull(T x) {
        if (x == null)
            throw new IllegalArgumentException("Null argument");
        return x;
    }

    /**
     * Retrieve the query that this notification filter will evaluate for
     * each notification.
     *
     * @return the query.
     */
    public QueryExp getQuery() {
        return query;
    }

    public boolean isNotificationEnabled(Notification notification) {
        ObjectName name;

        Object source = notification.getSource();
        if (source instanceof ObjectName)
            name = (ObjectName) source;
        else
            name = DEFAULT_NAME;

        MBS mbsImpl = new MBS(notification, name);
        MBeanServer mbs = (MBeanServer) Proxy.newProxyInstance(
                MBeanServer.class.getClassLoader(),
                new Class<?>[] {MBeanServer.class},
                new ForwardIH(mbsImpl));
        return evalQuery(query, mbs, name);
    }

    private static boolean evalQuery(
            QueryExp query, MBeanServer mbs, ObjectName name) {
        MBeanServer oldMBS = QueryEval.getMBeanServer();
        try {
            if (mbs != null)
                query.setMBeanServer(mbs);
            return query.apply(name);
        } catch (Exception e) {
            return false;
        } finally {
            query.setMBeanServer(oldMBS);
        }
    }

    private static class ForwardIH implements InvocationHandler {
        private final MBS mbs;

        ForwardIH(MBS mbs) {
            this.mbs = mbs;
        }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            Method forward;
            try {
                forward = MBS.class.getMethod(
                        method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException(method.getName());
            }
            try {
                return forward.invoke(mbs, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static class MBS {
        private final Notification notification;
        private final ObjectName objectName;
        private final ObjectInstance objectInstance;
        private volatile DynamicMBean mbean;

        MBS(Notification n, ObjectName name) {
            this.notification = n;
            this.objectName = name;
            this.objectInstance = new ObjectInstance(name, n.getClass().getName());
        }

        private void checkName(ObjectName name) throws InstanceNotFoundException {
            if (!objectName.equals(name))
                throw new InstanceNotFoundException(String.valueOf(name));
        }

        private DynamicMBean mbean(ObjectName name)
                throws InstanceNotFoundException, ReflectionException {
            if (mbean == null) {
                try {
                    mbean = new NotificationMBeanSupport(notification);
                } catch (NotCompliantMBeanException e) {
                    throw new ReflectionException(e);
                }
            }
            return mbean;
        }

        public ObjectInstance getObjectInstance(ObjectName name)
                throws InstanceNotFoundException {
            checkName(name);
            return objectInstance;
        }

        public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
            Set<ObjectName> names = queryNames(name, query);
            switch (names.size()) {
            case 0:
                return Collections.emptySet();
            case 1:
                return Collections.singleton(objectInstance);
            default:
                throw new UnsupportedOperationException("Internal error");
            }
        }

        public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
            if ((name != null && !name.apply(objectName)) ||
                    (query != null && !evalQuery(query, null, name)))
                return Collections.emptySet();
            return Collections.singleton(objectName);
        }

        public boolean isRegistered(ObjectName name) {
            return objectName.equals(name);
        }

        public Integer getMBeanCount() {
            return 1;
        }

        public Object getAttribute(ObjectName name, String attribute)
                throws MBeanException, AttributeNotFoundException,
                       InstanceNotFoundException, ReflectionException {
            return mbean(name).getAttribute(attribute);
        }

        public AttributeList getAttributes(ObjectName name, String[] attributes)
                throws InstanceNotFoundException, ReflectionException {
            return mbean(name).getAttributes(attributes);
        }

        public String getDefaultDomain() {
            return objectName.getDomain();
        }

        public String[] getDomains() {
            return new String[] {objectName.getDomain()};
        }

        public MBeanInfo getMBeanInfo(ObjectName name)
                throws InstanceNotFoundException, ReflectionException {
            return mbean(name).getMBeanInfo();
        }

        public boolean isInstanceOf(ObjectName name, String className)
                throws InstanceNotFoundException {
            try {
                mbean(name);
                ClassLoader loader = notification.getClass().getClassLoader();
                Class<?> c = Class.forName(className, false, loader);
                return c.isInstance(notification);
            } catch (ReflectionException e) {
                return false;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        public ClassLoader getClassLoaderFor(ObjectName mbeanName)
                throws InstanceNotFoundException {
            checkName(mbeanName);
            return notification.getClass().getClassLoader();
        }
    }
}
