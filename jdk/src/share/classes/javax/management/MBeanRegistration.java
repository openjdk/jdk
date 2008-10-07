/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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


/**
 * <p>Can be implemented by an MBean in order to
 * carry out operations before and after being registered or unregistered from
 * the MBean Server.  An MBean can also implement this interface in order
 * to get a reference to the MBean Server and/or its name within that
 * MBean Server.</p>
 *
 * <h4 id="injection">Resource injection</h4>
 *
 * <p>As an alternative to implementing {@code MBeanRegistration}, if all that
 * is needed is the MBean Server or ObjectName then an MBean can use
 * <em>resource injection</em>.</p>
 *
 * <p>If a field in the MBean object has type {@link ObjectName} and has
 * the {@link javax.annotation.Resource &#64;Resource} annotation,
 * then the {@code ObjectName} under which the MBean is registered is
 * assigned to that field during registration.  Likewise, if a field has type
 * {@link MBeanServer} and the <code>&#64;Resource</code> annotation, then it will
 * be set to the {@code MBeanServer} in which the MBean is registered.</p>
 *
 * <p>For example:</p>
 *
 * <pre>
 * public Configuration implements ConfigurationMBean {
 *     &#64;Resource
 *     private volatile MBeanServer mbeanServer;
 *     &#64;Resource
 *     private volatile ObjectName objectName;
 *     ...
 *     void unregisterSelf() throws Exception {
 *         mbeanServer.unregisterMBean(objectName);
 *     }
 * }
 * </pre>
 *
 * <p>Resource injection can also be used on fields of type
 * {@link SendNotification} to simplify notification sending.  Such a field
 * will get a reference to an object of type {@code SendNotification} when
 * the MBean is registered, and it can use this reference to send notifications.
 * For example:</p>
 *
 * <pre>
 * public Configuration implements ConfigurationMBean {
 *     &#64;Resource
 *     private volatile SendNotification sender;
 *     ...
 *     private void updated() {
 *         Notification n = new Notification(...);
 *         sender.sendNotification(n);
 *     }
 * }
 * </pre>
 *
 * <p>A field to be injected must not be static.  It is recommended that
 * such fields be declared {@code volatile}.</p>
 *
 * <p>It is also possible to use the <code>&#64;Resource</code> annotation on
 * methods. Such a method must have a {@code void} return type and a single
 * argument of the appropriate type, for example {@code ObjectName}.</p>
 *
 * <p>Any number of fields and methods may have the <code>&#64;Resource</code>
 * annotation.  All fields and methods with type {@code ObjectName}
 * (for example) will receive the same {@code ObjectName} value.</p>
 *
 * <p>Resource injection is available for all types of MBeans, not just
 * Standard MBeans.</p>
 *
 * <p>If an MBean implements the {@link DynamicWrapperMBean} interface then
 * resource injection happens on the object returned by that interface's
 * {@link DynamicWrapperMBean#getWrappedObject() getWrappedObject()} method
 * rather than on the MBean object itself.
 *
 * <p>Resource injection happens after the {@link #preRegister preRegister}
 * method is called (if any), and before the MBean is actually registered
 * in the MBean Server. If a <code>&#64;Resource</code> method throws
 * an exception, the effect is the same as if {@code preRegister} had
 * thrown the exception. In particular it will prevent the MBean from being
 * registered.</p>
 *
 * <p>Resource injection can be used on a field or method where the type
 * is a parent of the injected type, if the injected type is explicitly
 * specified in the <code>&#64;Resource</code> annotation.  For example:</p>
 *
 * <pre>
 *     &#64;Resource(type = MBeanServer.class)
 *     private volatile MBeanServerConnection mbsc;
 * </pre>
 *
 * <p>Formally, suppose <em>R</em> is the type in the <code>&#64;Resource</code>
 * annotation and <em>T</em> is the type of the method parameter or field.
 * Then one of <em>R</em> and <em>T</em> must be a subtype of the other
 * (or they must be the same type).  Injection happens if this subtype
 * is {@code MBeanServer}, {@code ObjectName}, or {@code SendNotification}.
 * Otherwise the <code>&#64;Resource</code> annotation is ignored.</p>
 *
 * <p>Resource injection in MBeans is new in version 2.0 of the JMX API.</p>
 *
 * @since 1.5
 */
public interface MBeanRegistration   {


    /**
     * Allows the MBean to perform any operations it needs before
     * being registered in the MBean Server.  If the name of the MBean
     * is not specified, the MBean can provide a name for its
     * registration.  If any exception is raised, the MBean will not be
     * registered in the MBean Server.
     *
     * @param server The MBean Server in which the MBean will be registered.
     *
     * @param name The object name of the MBean.  This name is null if
     * the name parameter to one of the <code>createMBean</code> or
     * <code>registerMBean</code> methods in the {@link MBeanServer}
     * interface is null.  In that case, this method must return a
     * non-null ObjectName for the new MBean.
     *
     * @return The name under which the MBean is to be registered.
     * This value must not be null.  If the <code>name</code>
     * parameter is not null, it will usually but not necessarily be
     * the returned value.
     *
     * @exception java.lang.Exception This exception will be caught by
     * the MBean Server and re-thrown as an {@link
     * MBeanRegistrationException}.
     */
    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws java.lang.Exception;

    /**
     * Allows the MBean to perform any operations needed after having been
     * registered in the MBean server or after the registration has failed.
     * <p>If the implementation of this method throws a {@link RuntimeException}
     * or an {@link Error}, the MBean Server will rethrow those inside
     * a {@link RuntimeMBeanException} or {@link RuntimeErrorException},
     * respectively. However, throwing an exception in {@code postRegister}
     * will not change the state of the MBean:
     * if the MBean was already registered ({@code registrationDone} is
     * {@code true}), the MBean will remain registered. </p>
     * <p>This might be confusing for the code calling {@code createMBean()}
     * or {@code registerMBean()}, as such code might assume that MBean
     * registration has failed when such an exception is raised.
     * Therefore it is recommended that implementations of
     * {@code postRegister} do not throw Runtime Exceptions or Errors if it
     * can be avoided.</p>
     * @param registrationDone Indicates whether or not the MBean has
     * been successfully registered in the MBean server. The value
     * false means that the registration phase has failed.
     */
    public void postRegister(Boolean registrationDone);

    /**
     * Allows the MBean to perform any operations it needs before
     * being unregistered by the MBean server.
     *
     * @exception java.lang.Exception This exception will be caught by
     * the MBean server and re-thrown as an {@link
     * MBeanRegistrationException}.
     */
    public void preDeregister() throws java.lang.Exception ;

    /**
     * Allows the MBean to perform any operations needed after having been
     * unregistered in the MBean server.
     * <p>If the implementation of this method throws a {@link RuntimeException}
     * or an {@link Error}, the MBean Server will rethrow those inside
     * a {@link RuntimeMBeanException} or {@link RuntimeErrorException},
     * respectively. However, throwing an excepption in {@code postDeregister}
     * will not change the state of the MBean:
     * the MBean was already successfully deregistered and will remain so. </p>
     * <p>This might be confusing for the code calling
     * {@code unregisterMBean()}, as it might assume that MBean deregistration
     * has failed. Therefore it is recommended that implementations of
     * {@code postDeregister} do not throw Runtime Exceptions or Errors if it
     * can be avoided.</p>
     */
    public void postDeregister();

 }
