/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.management;
import javax.management.DynamicMBean;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.MBeanServerPermission;
import javax.management.NotificationEmitter;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import javax.management.MBeanRegistrationException;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardEmitterMBean;
import javax.management.StandardMBean;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import javax.management.JMX;
import sun.management.ManagementFactoryHelper;
import sun.management.ExtendedPlatformComponent;

/**
 * The {@code ManagementFactory} class is a factory class for getting
 * managed beans for the Java platform.
 * This class consists of static methods each of which returns
 * one or more <i>platform MXBeans</i> representing
 * the management interface of a component of the Java virtual
 * machine.
 *
 * <h3><a name="MXBean">Platform MXBeans</a></h3>
 * <p>
 * A platform MXBean is a <i>managed bean</i> that
 * conforms to the <a href="../../../javax/management/package-summary.html">JMX</a>
 * Instrumentation Specification and only uses a set of basic data types.
 * A JMX management application and the {@linkplain
 * #getPlatformMBeanServer platform MBeanServer}
 * can interoperate without requiring classes for MXBean specific
 * data types.
 * The data types being transmitted between the JMX connector
 * server and the connector client are
 * {@linkplain javax.management.openmbean.OpenType open types}
 * and this allows interoperation across versions.
 * See <a href="../../../javax/management/MXBean.html#MXBean-spec">
 * the specification of MXBeans</a> for details.
 *
 * <a name="MXBeanNames"></a>
 * <p>Each platform MXBean is a {@link PlatformManagedObject}
 * and it has a unique
 * {@link javax.management.ObjectName ObjectName} for
 * registration in the platform {@code MBeanServer} as returned by
 * by the {@link PlatformManagedObject#getObjectName getObjectName}
 * method.
 *
 * <p>
 * An application can access a platform MXBean in the following ways:
 * <h4>1. Direct access to an MXBean interface</h4>
 * <blockquote>
 * <ul>
 *     <li>Get an MXBean instance by calling the
 *         {@link #getPlatformMXBean(Class) getPlatformMXBean} or
 *         {@link #getPlatformMXBeans(Class) getPlatformMXBeans} method
 *         and access the MXBean locally in the running
 *         virtual machine.
 *         </li>
 *     <li>Construct an MXBean proxy instance that forwards the
 *         method calls to a given {@link MBeanServer MBeanServer} by calling
 *         the {@link #getPlatformMXBean(MBeanServerConnection, Class)} or
 *         {@link #getPlatformMXBeans(MBeanServerConnection, Class)} method.
 *         The {@link #newPlatformMXBeanProxy newPlatformMXBeanProxy} method
 *         can also be used to construct an MXBean proxy instance of
 *         a given {@code ObjectName}.
 *         A proxy is typically constructed to remotely access
 *         an MXBean of another running virtual machine.
 *         </li>
 * </ul>
 * <h4>2. Indirect access to an MXBean interface via MBeanServer</h4>
 * <ul>
 *     <li>Go through the platform {@code MBeanServer} to access MXBeans
 *         locally or a specific <tt>MBeanServerConnection</tt> to access
 *         MXBeans remotely.
 *         The attributes and operations of an MXBean use only
 *         <em>JMX open types</em> which include basic data types,
 *         {@link javax.management.openmbean.CompositeData CompositeData},
 *         and {@link javax.management.openmbean.TabularData TabularData}
 *         defined in
 *         {@link javax.management.openmbean.OpenType OpenType}.
 *         The mapping is specified in
 *         the {@linkplain javax.management.MXBean MXBean} specification
 *         for details.
 *        </li>
 * </ul>
 * </blockquote>
 *
 * <p>
 * The {@link #getPlatformManagementInterfaces getPlatformManagementInterfaces}
 * method returns all management interfaces supported in the Java virtual machine
 * including the standard management interfaces listed in the tables
 * below as well as the management interfaces extended by the JDK implementation.
 * <p>
 * A Java virtual machine has a single instance of the following management
 * interfaces:
 *
 * <blockquote>
 * <table border summary="The list of Management Interfaces and their single instances">
 * <tr>
 * <th>Management Interface</th>
 * <th>ObjectName</th>
 * </tr>
 * <tr>
 * <td> {@link ClassLoadingMXBean} </td>
 * <td> {@link #CLASS_LOADING_MXBEAN_NAME
 *             java.lang:type=ClassLoading}</td>
 * </tr>
 * <tr>
 * <td> {@link MemoryMXBean} </td>
 * <td> {@link #MEMORY_MXBEAN_NAME
 *             java.lang:type=Memory}</td>
 * </tr>
 * <tr>
 * <td> {@link ThreadMXBean} </td>
 * <td> {@link #THREAD_MXBEAN_NAME
 *             java.lang:type=Threading}</td>
 * </tr>
 * <tr>
 * <td> {@link RuntimeMXBean} </td>
 * <td> {@link #RUNTIME_MXBEAN_NAME
 *             java.lang:type=Runtime}</td>
 * </tr>
 * <tr>
 * <td> {@link OperatingSystemMXBean} </td>
 * <td> {@link #OPERATING_SYSTEM_MXBEAN_NAME
 *             java.lang:type=OperatingSystem}</td>
 * </tr>
 * <tr>
 * <td> {@link PlatformLoggingMXBean} </td>
 * <td> {@link java.util.logging.LogManager#LOGGING_MXBEAN_NAME
 *             java.util.logging:type=Logging}</td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * <p>
 * A Java virtual machine has zero or a single instance of
 * the following management interfaces.
 *
 * <blockquote>
 * <table border summary="The list of Management Interfaces and their single instances">
 * <tr>
 * <th>Management Interface</th>
 * <th>ObjectName</th>
 * </tr>
 * <tr>
 * <td> {@link CompilationMXBean} </td>
 * <td> {@link #COMPILATION_MXBEAN_NAME
 *             java.lang:type=Compilation}</td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * <p>
 * A Java virtual machine may have one or more instances of the following
 * management interfaces.
 * <blockquote>
 * <table border summary="The list of Management Interfaces and their single instances">
 * <tr>
 * <th>Management Interface</th>
 * <th>ObjectName</th>
 * </tr>
 * <tr>
 * <td> {@link GarbageCollectorMXBean} </td>
 * <td> {@link #GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE
 *             java.lang:type=GarbageCollector}<tt>,name=</tt><i>collector's name</i></td>
 * </tr>
 * <tr>
 * <td> {@link MemoryManagerMXBean} </td>
 * <td> {@link #MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE
 *             java.lang:type=MemoryManager}<tt>,name=</tt><i>manager's name</i></td>
 * </tr>
 * <tr>
 * <td> {@link MemoryPoolMXBean} </td>
 * <td> {@link #MEMORY_POOL_MXBEAN_DOMAIN_TYPE
 *             java.lang:type=MemoryPool}<tt>,name=</tt><i>pool's name</i></td>
 * </tr>
 * <tr>
 * <td> {@link BufferPoolMXBean} </td>
 * <td> {@code java.nio:type=BufferPool,name=}<i>pool name</i></td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * @see <a href="../../../javax/management/package-summary.html">
 *      JMX Specification</a>
 * @see <a href="package-summary.html#examples">
 *      Ways to Access Management Metrics</a>
 * @see javax.management.MXBean
 *
 * @author  Mandy Chung
 * @since   1.5
 */
public class ManagementFactory {
    // A class with only static fields and methods.
    private ManagementFactory() {};

    /**
     * String representation of the
     * <tt>ObjectName</tt> for the {@link ClassLoadingMXBean}.
     */
    public final static String CLASS_LOADING_MXBEAN_NAME =
        "java.lang:type=ClassLoading";

    /**
     * String representation of the
     * <tt>ObjectName</tt> for the {@link CompilationMXBean}.
     */
    public final static String COMPILATION_MXBEAN_NAME =
        "java.lang:type=Compilation";

    /**
     * String representation of the
     * <tt>ObjectName</tt> for the {@link MemoryMXBean}.
     */
    public final static String MEMORY_MXBEAN_NAME =
        "java.lang:type=Memory";

    /**
     * String representation of the
     * <tt>ObjectName</tt> for the {@link OperatingSystemMXBean}.
     */
    public final static String OPERATING_SYSTEM_MXBEAN_NAME =
        "java.lang:type=OperatingSystem";

    /**
     * String representation of the
     * <tt>ObjectName</tt> for the {@link RuntimeMXBean}.
     */
    public final static String RUNTIME_MXBEAN_NAME =
        "java.lang:type=Runtime";

    /**
     * String representation of the
     * <tt>ObjectName</tt> for the {@link ThreadMXBean}.
     */
    public final static String THREAD_MXBEAN_NAME =
        "java.lang:type=Threading";

    /**
     * The domain name and the type key property in
     * the <tt>ObjectName</tt> for a {@link GarbageCollectorMXBean}.
     * The unique <tt>ObjectName</tt> for a <tt>GarbageCollectorMXBean</tt>
     * can be formed by appending this string with
     * "<tt>,name=</tt><i>collector's name</i>".
     */
    public final static String GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE =
        "java.lang:type=GarbageCollector";

    /**
     * The domain name and the type key property in
     * the <tt>ObjectName</tt> for a {@link MemoryManagerMXBean}.
     * The unique <tt>ObjectName</tt> for a <tt>MemoryManagerMXBean</tt>
     * can be formed by appending this string with
     * "<tt>,name=</tt><i>manager's name</i>".
     */
    public final static String MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE=
        "java.lang:type=MemoryManager";

    /**
     * The domain name and the type key property in
     * the <tt>ObjectName</tt> for a {@link MemoryPoolMXBean}.
     * The unique <tt>ObjectName</tt> for a <tt>MemoryPoolMXBean</tt>
     * can be formed by appending this string with
     * <tt>,name=</tt><i>pool's name</i>.
     */
    public final static String MEMORY_POOL_MXBEAN_DOMAIN_TYPE=
        "java.lang:type=MemoryPool";

    /**
     * Returns the managed bean for the class loading system of
     * the Java virtual machine.
     *
     * @return a {@link ClassLoadingMXBean} object for
     * the Java virtual machine.
     */
    public static ClassLoadingMXBean getClassLoadingMXBean() {
        return ManagementFactoryHelper.getClassLoadingMXBean();
    }

    /**
     * Returns the managed bean for the memory system of
     * the Java virtual machine.
     *
     * @return a {@link MemoryMXBean} object for the Java virtual machine.
     */
    public static MemoryMXBean getMemoryMXBean() {
        return ManagementFactoryHelper.getMemoryMXBean();
    }

    /**
     * Returns the managed bean for the thread system of
     * the Java virtual machine.
     *
     * @return a {@link ThreadMXBean} object for the Java virtual machine.
     */
    public static ThreadMXBean getThreadMXBean() {
        return ManagementFactoryHelper.getThreadMXBean();
    }

    /**
     * Returns the managed bean for the runtime system of
     * the Java virtual machine.
     *
     * @return a {@link RuntimeMXBean} object for the Java virtual machine.

     */
    public static RuntimeMXBean getRuntimeMXBean() {
        return ManagementFactoryHelper.getRuntimeMXBean();
    }

    /**
     * Returns the managed bean for the compilation system of
     * the Java virtual machine.  This method returns <tt>null</tt>
     * if the Java virtual machine has no compilation system.
     *
     * @return a {@link CompilationMXBean} object for the Java virtual
     *   machine or <tt>null</tt> if the Java virtual machine has
     *   no compilation system.
     */
    public static CompilationMXBean getCompilationMXBean() {
        return ManagementFactoryHelper.getCompilationMXBean();
    }

    /**
     * Returns the managed bean for the operating system on which
     * the Java virtual machine is running.
     *
     * @return an {@link OperatingSystemMXBean} object for
     * the Java virtual machine.
     */
    public static OperatingSystemMXBean getOperatingSystemMXBean() {
        return ManagementFactoryHelper.getOperatingSystemMXBean();
    }

    /**
     * Returns a list of {@link MemoryPoolMXBean} objects in the
     * Java virtual machine.
     * The Java virtual machine can have one or more memory pools.
     * It may add or remove memory pools during execution.
     *
     * @return a list of <tt>MemoryPoolMXBean</tt> objects.
     *
     */
    public static List<MemoryPoolMXBean> getMemoryPoolMXBeans() {
        return ManagementFactoryHelper.getMemoryPoolMXBeans();
    }

    /**
     * Returns a list of {@link MemoryManagerMXBean} objects
     * in the Java virtual machine.
     * The Java virtual machine can have one or more memory managers.
     * It may add or remove memory managers during execution.
     *
     * @return a list of <tt>MemoryManagerMXBean</tt> objects.
     *
     */
    public static List<MemoryManagerMXBean> getMemoryManagerMXBeans() {
        return ManagementFactoryHelper.getMemoryManagerMXBeans();
    }


    /**
     * Returns a list of {@link GarbageCollectorMXBean} objects
     * in the Java virtual machine.
     * The Java virtual machine may have one or more
     * <tt>GarbageCollectorMXBean</tt> objects.
     * It may add or remove <tt>GarbageCollectorMXBean</tt>
     * during execution.
     *
     * @return a list of <tt>GarbageCollectorMXBean</tt> objects.
     *
     */
    public static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        return ManagementFactoryHelper.getGarbageCollectorMXBeans();
    }

    private static MBeanServer platformMBeanServer;
    /**
     * Returns the platform {@link javax.management.MBeanServer MBeanServer}.
     * On the first call to this method, it first creates the platform
     * {@code MBeanServer} by calling the
     * {@link javax.management.MBeanServerFactory#createMBeanServer
     * MBeanServerFactory.createMBeanServer}
     * method and registers each platform MXBean in this platform
     * {@code MBeanServer} with its
     * {@link PlatformManagedObject#getObjectName ObjectName}.
     * This method, in subsequent calls, will simply return the
     * initially created platform {@code MBeanServer}.
     * <p>
     * MXBeans that get created and destroyed dynamically, for example,
     * memory {@link MemoryPoolMXBean pools} and
     * {@link MemoryManagerMXBean managers},
     * will automatically be registered and deregistered into the platform
     * {@code MBeanServer}.
     * <p>
     * If the system property {@code javax.management.builder.initial}
     * is set, the platform {@code MBeanServer} creation will be done
     * by the specified {@link javax.management.MBeanServerBuilder}.
     * <p>
     * It is recommended that this platform MBeanServer also be used
     * to register other application managed beans
     * besides the platform MXBeans.
     * This will allow all MBeans to be published through the same
     * {@code MBeanServer} and hence allow for easier network publishing
     * and discovery.
     * Name conflicts with the platform MXBeans should be avoided.
     *
     * @return the platform {@code MBeanServer}; the platform
     *         MXBeans are registered into the platform {@code MBeanServer}
     *         at the first time this method is called.
     *
     * @exception SecurityException if there is a security manager
     * and the caller does not have the permission required by
     * {@link javax.management.MBeanServerFactory#createMBeanServer}.
     *
     * @see javax.management.MBeanServerFactory
     * @see javax.management.MBeanServerFactory#createMBeanServer
     */
    public static synchronized MBeanServer getPlatformMBeanServer() {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            Permission perm = new MBeanServerPermission("createMBeanServer");
            sm.checkPermission(perm);
        }

        if (platformMBeanServer == null) {
            platformMBeanServer = MBeanServerFactory.createMBeanServer();
            for (PlatformComponent pc : PlatformComponent.values()) {
                List<? extends PlatformManagedObject> list =
                    pc.getMXBeans(pc.getMXBeanInterface());
                for (PlatformManagedObject o : list) {
                    // Each PlatformComponent represents one management
                    // interface. Some MXBean may extend another one.
                    // The MXBean instances for one platform component
                    // (returned by pc.getMXBeans()) might be also
                    // the MXBean instances for another platform component.
                    // e.g. com.sun.management.GarbageCollectorMXBean
                    //
                    // So need to check if an MXBean instance is registered
                    // before registering into the platform MBeanServer
                    if (!platformMBeanServer.isRegistered(o.getObjectName())) {
                        addMXBean(platformMBeanServer, o);
                    }
                }
            }
            HashMap<ObjectName, DynamicMBean> dynmbeans =
                    ManagementFactoryHelper.getPlatformDynamicMBeans();
            for (Map.Entry<ObjectName, DynamicMBean> e : dynmbeans.entrySet()) {
                addDynamicMBean(platformMBeanServer, e.getValue(), e.getKey());
            }
            for (final PlatformManagedObject o :
                                       ExtendedPlatformComponent.getMXBeans()) {
                if (!platformMBeanServer.isRegistered(o.getObjectName())) {
                    addMXBean(platformMBeanServer, o);
                }
            }
        }
        return platformMBeanServer;
    }

    /**
     * Returns a proxy for a platform MXBean interface of a
     * given <a href="#MXBeanNames">MXBean name</a>
     * that forwards its method calls through the given
     * <tt>MBeanServerConnection</tt>.
     *
     * <p>This method is equivalent to:
     * <blockquote>
     * {@link java.lang.reflect.Proxy#newProxyInstance
     *        Proxy.newProxyInstance}<tt>(mxbeanInterface.getClassLoader(),
     *        new Class[] { mxbeanInterface }, handler)</tt>
     * </blockquote>
     *
     * where <tt>handler</tt> is an {@link java.lang.reflect.InvocationHandler
     * InvocationHandler} to which method invocations to the MXBean interface
     * are dispatched. This <tt>handler</tt> converts an input parameter
     * from an MXBean data type to its mapped open type before forwarding
     * to the <tt>MBeanServer</tt> and converts a return value from
     * an MXBean method call through the <tt>MBeanServer</tt>
     * from an open type to the corresponding return type declared in
     * the MXBean interface.
     *
     * <p>
     * If the MXBean is a notification emitter (i.e.,
     * it implements
     * {@link javax.management.NotificationEmitter NotificationEmitter}),
     * both the <tt>mxbeanInterface</tt> and <tt>NotificationEmitter</tt>
     * will be implemented by this proxy.
     *
     * <p>
     * <b>Notes:</b>
     * <ol>
     * <li>Using an MXBean proxy is a convenience remote access to
     * a platform MXBean of a running virtual machine.  All method
     * calls to the MXBean proxy are forwarded to an
     * <tt>MBeanServerConnection</tt> where
     * {@link java.io.IOException IOException} may be thrown
     * when the communication problem occurs with the connector server.
     * An application remotely accesses the platform MXBeans using
     * proxy should prepare to catch <tt>IOException</tt> as if
     * accessing with the <tt>MBeanServerConnector</tt> interface.</li>
     *
     * <li>When a client application is designed to remotely access MXBeans
     * for a running virtual machine whose version is different than
     * the version on which the application is running,
     * it should prepare to catch
     * {@link java.io.InvalidObjectException InvalidObjectException}
     * which is thrown when an MXBean proxy receives a name of an
     * enum constant which is missing in the enum class loaded in
     * the client application. </li>
     *
     * <li>{@link javax.management.MBeanServerInvocationHandler
     * MBeanServerInvocationHandler} or its
     * {@link javax.management.MBeanServerInvocationHandler#newProxyInstance
     * newProxyInstance} method cannot be used to create
     * a proxy for a platform MXBean. The proxy object created
     * by <tt>MBeanServerInvocationHandler</tt> does not handle
     * the properties of the platform MXBeans described in
     * the <a href="#MXBean">class specification</a>.
     *</li>
     * </ol>
     *
     * @param connection the <tt>MBeanServerConnection</tt> to forward to.
     * @param mxbeanName the name of a platform MXBean within
     * <tt>connection</tt> to forward to. <tt>mxbeanName</tt> must be
     * in the format of {@link ObjectName ObjectName}.
     * @param mxbeanInterface the MXBean interface to be implemented
     * by the proxy.
     * @param <T> an {@code mxbeanInterface} type parameter
     *
     * @return a proxy for a platform MXBean interface of a
     * given <a href="#MXBeanNames">MXBean name</a>
     * that forwards its method calls through the given
     * <tt>MBeanServerConnection</tt>, or {@code null} if not exist.
     *
     * @throws IllegalArgumentException if
     * <ul>
     * <li><tt>mxbeanName</tt> is not with a valid
     *     {@link ObjectName ObjectName} format, or</li>
     * <li>the named MXBean in the <tt>connection</tt> is
     *     not a MXBean provided by the platform, or</li>
     * <li>the named MXBean is not registered in the
     *     <tt>MBeanServerConnection</tt>, or</li>
     * <li>the named MXBean is not an instance of the given
     *     <tt>mxbeanInterface</tt></li>
     * </ul>
     *
     * @throws java.io.IOException if a communication problem
     * occurred when accessing the <tt>MBeanServerConnection</tt>.
     */
    public static <T> T
        newPlatformMXBeanProxy(MBeanServerConnection connection,
                               String mxbeanName,
                               Class<T> mxbeanInterface)
            throws java.io.IOException {

        // Only allow MXBean interfaces from rt.jar loaded by the
        // bootstrap class loader
        final Class<?> cls = mxbeanInterface;
        ClassLoader loader =
            AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return cls.getClassLoader();
                }
            });
        if (!sun.misc.VM.isSystemDomainLoader(loader)) {
            throw new IllegalArgumentException(mxbeanName +
                " is not a platform MXBean");
        }

        try {
            final ObjectName objName = new ObjectName(mxbeanName);
            // skip the isInstanceOf check for LoggingMXBean
            String intfName = mxbeanInterface.getName();
            if (!connection.isInstanceOf(objName, intfName)) {
                throw new IllegalArgumentException(mxbeanName +
                    " is not an instance of " + mxbeanInterface);
            }

            final Class<?>[] interfaces;
            // check if the registered MBean is a notification emitter
            boolean emitter = connection.isInstanceOf(objName, NOTIF_EMITTER);

            // create an MXBean proxy
            return JMX.newMXBeanProxy(connection, objName, mxbeanInterface,
                                      emitter);
        } catch (InstanceNotFoundException|MalformedObjectNameException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Returns the platform MXBean implementing
     * the given {@code mxbeanInterface} which is specified
     * to have one single instance in the Java virtual machine.
     * This method may return {@code null} if the management interface
     * is not implemented in the Java virtual machine (for example,
     * a Java virtual machine with no compilation system does not
     * implement {@link CompilationMXBean});
     * otherwise, this method is equivalent to calling:
     * <pre>
     *    {@link #getPlatformMXBeans(Class)
     *      getPlatformMXBeans(mxbeanInterface)}.get(0);
     * </pre>
     *
     * @param mxbeanInterface a management interface for a platform
     *     MXBean with one single instance in the Java virtual machine
     *     if implemented.
     * @param <T> an {@code mxbeanInterface} type parameter
     *
     * @return the platform MXBean that implements
     * {@code mxbeanInterface}, or {@code null} if not exist.
     *
     * @throws IllegalArgumentException if {@code mxbeanInterface}
     * is not a platform management interface or
     * not a singleton platform MXBean.
     *
     * @since 1.7
     */
    public static <T extends PlatformManagedObject>
            T getPlatformMXBean(Class<T> mxbeanInterface) {
        PlatformComponent pc = PlatformComponent.getPlatformComponent(mxbeanInterface);
        if (pc == null) {
            T mbean = ExtendedPlatformComponent.getMXBean(mxbeanInterface);
            if (mbean != null) {
                return mbean;
            }
            throw new IllegalArgumentException(mxbeanInterface.getName() +
                " is not a platform management interface");
        }
        if (!pc.isSingleton())
            throw new IllegalArgumentException(mxbeanInterface.getName() +
                " can have zero or more than one instances");

        return pc.getSingletonMXBean(mxbeanInterface);
    }

    /**
     * Returns the list of platform MXBeans implementing
     * the given {@code mxbeanInterface} in the Java
     * virtual machine.
     * The returned list may contain zero, one, or more instances.
     * The number of instances in the returned list is defined
     * in the specification of the given management interface.
     * The order is undefined and there is no guarantee that
     * the list returned is in the same order as previous invocations.
     *
     * @param mxbeanInterface a management interface for a platform
     *                        MXBean
     * @param <T> an {@code mxbeanInterface} type parameter
     *
     * @return the list of platform MXBeans that implement
     * {@code mxbeanInterface}.
     *
     * @throws IllegalArgumentException if {@code mxbeanInterface}
     * is not a platform management interface.
     *
     * @since 1.7
     */
    public static <T extends PlatformManagedObject> List<T>
            getPlatformMXBeans(Class<T> mxbeanInterface) {
        PlatformComponent pc = PlatformComponent.getPlatformComponent(mxbeanInterface);
        if (pc == null) {
            T mbean = ExtendedPlatformComponent.getMXBean(mxbeanInterface);
            if (mbean != null) {
                return Collections.singletonList(mbean);
            }
            throw new IllegalArgumentException(mxbeanInterface.getName() +
                " is not a platform management interface");
        }
        return Collections.unmodifiableList(pc.getMXBeans(mxbeanInterface));
    }

    /**
     * Returns the platform MXBean proxy for
     * {@code mxbeanInterface} which is specified to have one single
     * instance in a Java virtual machine and the proxy will
     * forward the method calls through the given {@code MBeanServerConnection}.
     * This method may return {@code null} if the management interface
     * is not implemented in the Java virtual machine being monitored
     * (for example, a Java virtual machine with no compilation system
     * does not implement {@link CompilationMXBean});
     * otherwise, this method is equivalent to calling:
     * <pre>
     *     {@link #getPlatformMXBeans(MBeanServerConnection, Class)
     *        getPlatformMXBeans(connection, mxbeanInterface)}.get(0);
     * </pre>
     *
     * @param connection the {@code MBeanServerConnection} to forward to.
     * @param mxbeanInterface a management interface for a platform
     *     MXBean with one single instance in the Java virtual machine
     *     being monitored, if implemented.
     * @param <T> an {@code mxbeanInterface} type parameter
     *
     * @return the platform MXBean proxy for
     * forwarding the method calls of the {@code mxbeanInterface}
     * through the given {@code MBeanServerConnection},
     * or {@code null} if not exist.
     *
     * @throws IllegalArgumentException if {@code mxbeanInterface}
     * is not a platform management interface or
     * not a singleton platform MXBean.
     * @throws java.io.IOException if a communication problem
     * occurred when accessing the {@code MBeanServerConnection}.
     *
     * @see #newPlatformMXBeanProxy
     * @since 1.7
     */
    public static <T extends PlatformManagedObject>
            T getPlatformMXBean(MBeanServerConnection connection,
                                Class<T> mxbeanInterface)
        throws java.io.IOException
    {
        PlatformComponent pc = PlatformComponent.getPlatformComponent(mxbeanInterface);
        if (pc == null) {
            T mbean = ExtendedPlatformComponent.getMXBean(mxbeanInterface);
            if (mbean != null) {
                ObjectName on = mbean.getObjectName();
                return ManagementFactory.newPlatformMXBeanProxy(connection,
                                                                on.getCanonicalName(),
                                                                mxbeanInterface);
            }
            throw new IllegalArgumentException(mxbeanInterface.getName() +
                " is not a platform management interface");
        }
        if (!pc.isSingleton())
            throw new IllegalArgumentException(mxbeanInterface.getName() +
                " can have zero or more than one instances");
        return pc.getSingletonMXBean(connection, mxbeanInterface);
    }

    /**
     * Returns the list of the platform MXBean proxies for
     * forwarding the method calls of the {@code mxbeanInterface}
     * through the given {@code MBeanServerConnection}.
     * The returned list may contain zero, one, or more instances.
     * The number of instances in the returned list is defined
     * in the specification of the given management interface.
     * The order is undefined and there is no guarantee that
     * the list returned is in the same order as previous invocations.
     *
     * @param connection the {@code MBeanServerConnection} to forward to.
     * @param mxbeanInterface a management interface for a platform
     *                        MXBean
     * @param <T> an {@code mxbeanInterface} type parameter
     *
     * @return the list of platform MXBean proxies for
     * forwarding the method calls of the {@code mxbeanInterface}
     * through the given {@code MBeanServerConnection}.
     *
     * @throws IllegalArgumentException if {@code mxbeanInterface}
     * is not a platform management interface.
     *
     * @throws java.io.IOException if a communication problem
     * occurred when accessing the {@code MBeanServerConnection}.
     *
     * @see #newPlatformMXBeanProxy
     * @since 1.7
     */
    public static <T extends PlatformManagedObject>
            List<T> getPlatformMXBeans(MBeanServerConnection connection,
                                       Class<T> mxbeanInterface)
        throws java.io.IOException
    {
        PlatformComponent pc = PlatformComponent.getPlatformComponent(mxbeanInterface);
        if (pc == null) {
            T mbean = ExtendedPlatformComponent.getMXBean(mxbeanInterface);
            if (mbean != null) {
                ObjectName on = mbean.getObjectName();
                T proxy = ManagementFactory.newPlatformMXBeanProxy(connection,
                            on.getCanonicalName(), mxbeanInterface);
                return Collections.singletonList(proxy);
            }
            throw new IllegalArgumentException(mxbeanInterface.getName() +
                " is not a platform management interface");
        }
        return Collections.unmodifiableList(pc.getMXBeans(connection, mxbeanInterface));
    }

    /**
     * Returns the set of {@code Class} objects, subinterface of
     * {@link PlatformManagedObject}, representing
     * all management interfaces for
     * monitoring and managing the Java platform.
     *
     * @return the set of {@code Class} objects, subinterface of
     * {@link PlatformManagedObject} representing
     * the management interfaces for
     * monitoring and managing the Java platform.
     *
     * @since 1.7
     */
    public static Set<Class<? extends PlatformManagedObject>>
           getPlatformManagementInterfaces()
    {
        Set<Class<? extends PlatformManagedObject>> result =
            new HashSet<>();
        for (PlatformComponent component: PlatformComponent.values()) {
            result.add(component.getMXBeanInterface());
        }
        return Collections.unmodifiableSet(result);
    }

    private static final String NOTIF_EMITTER =
        "javax.management.NotificationEmitter";

    /**
     * Registers an MXBean.
     */
    private static void addMXBean(final MBeanServer mbs, final PlatformManagedObject pmo) {
        // Make DynamicMBean out of MXBean by wrapping it with a StandardMBean
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                public Void run() throws InstanceAlreadyExistsException,
                                         MBeanRegistrationException,
                                         NotCompliantMBeanException {
                    final DynamicMBean dmbean;
                    if (pmo instanceof DynamicMBean) {
                        dmbean = DynamicMBean.class.cast(pmo);
                    } else if (pmo instanceof NotificationEmitter) {
                        dmbean = new StandardEmitterMBean(pmo, null, true, (NotificationEmitter) pmo);
                    } else {
                        dmbean = new StandardMBean(pmo, null, true);
                    }

                    mbs.registerMBean(dmbean, pmo.getObjectName());
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException(e.getException());
        }
    }

    /**
     * Registers a DynamicMBean.
     */
    private static void addDynamicMBean(final MBeanServer mbs,
                                        final DynamicMBean dmbean,
                                        final ObjectName on) {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Void>() {
                @Override
                public Void run() throws InstanceAlreadyExistsException,
                                         MBeanRegistrationException,
                                         NotCompliantMBeanException {
                    mbs.registerMBean(dmbean, on);
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            throw new RuntimeException(e.getException());
        }
    }
}
