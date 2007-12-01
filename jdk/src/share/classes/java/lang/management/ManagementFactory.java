/*
 * Copyright 2003-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.lang.management;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerPermission;
import javax.management.ObjectName;
import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;
import java.util.List;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import javax.management.JMX;

/**
 * The <tt>ManagementFactory</tt> class is a factory class for getting
 * managed beans for the Java platform.
 * This class consists of static methods each of which returns
 * one or more <a href="#MXBean">platform MXBean(s)</a> representing
 * the management interface of a component of the Java virtual
 * machine.
 *
 * <p>
 * An application can access a platform MXBean in the following ways:
 * <ul>
 * <li><i>Direct access to an MXBean interface</i>
 *     <ol type="a">
 *     <li>Get the MXBean instance through the static factory method
 *         and access the MXBean locally of the running
 *         virtual machine.
 *         </li>
 *     <li>Construct an MXBean proxy instance that forwards the
 *         method calls to a given {@link MBeanServer MBeanServer} by calling
 *         {@link #newPlatformMXBeanProxy newPlatfromMXBeanProxy}.
 *         A proxy is typically constructed to remotely access
 *         an MXBean of another running virtual machine.
 *         </li>
 *     </ol></li>
 * <li><i>Indirect access to an MXBean interface via MBeanServer</i>
 *     <ol type="a">
 *     <li>Go through the {@link #getPlatformMBeanServer
 *         platform MBeanServer} to access MXBeans locally or
 *         a specific <tt>MBeanServerConnection</tt> to access
 *         MXBeans remotely.
 *         The attributes and operations of an MXBean use only
 *         <em>JMX open types</em> which include basic data types,
 *         {@link javax.management.openmbean.CompositeData CompositeData},
 *         and {@link javax.management.openmbean.TabularData TabularData}
 *         defined in
 *         {@link javax.management.openmbean.OpenType OpenType}.
 *         The mapping is specified below.
 *        </li>
 *     </ol></li>
 * </ul>
 *
 * <h4><a name="MXBean">Platform MXBeans</a></h4>
 * A platform MXBean is a <i>managed bean</i> that conforms to
 * the JMX Instrumentation Specification and only uses
 * a set of basic data types described below.
 * See <a href="../../../javax/management/MXBean.html#MXBean-spec">
 * the specification of MXBeans</a> for details.
 * A JMX management application and the platform <tt>MBeanServer</tt>
 * can interoperate without requiring classes for MXBean specific
 * data types.
 * The data types being transmitted between the JMX connector
 * server and the connector client are
 * {@linkplain javax.management.openmbean.OpenType open types}
 * and this allows interoperation across versions.
 * <p>
 * The platform MXBean interfaces use only the following data types:
 * <ul>
 *   <li>Primitive types such as <tt>int</tt>, <tt>long</tt>,
 *       <tt>boolean</tt>, etc</li>
 *   <li>Wrapper classes for primitive types such as
 *       {@link java.lang.Integer Integer}, {@link java.lang.Long Long},
 *       {@link java.lang.Boolean Boolean}, etc and
 *       {@link java.lang.String String}</li>
 *   <li>{@link java.lang.Enum Enum} classes</li>
 *   <li>Classes that define only getter methods and define a static
 *       <tt>from</tt> method with a
 *       {@link javax.management.openmbean.CompositeData CompositeData}
 *       argument to convert from an input <tt>CompositeData</tt> to
 *       an instance of that class
 *       </li>
 *   <li>{@link java.util.List List&lt;E&gt;}
 *       where <tt>E</tt> is a primitive type, a wrapper class,
 *       an enum class, or a class supporting conversion from a
 *       <tt>CompositeData</tt> to its class
 *       </li>
 *   <li>{@link java.util.Map Map&lt;K,V&gt;}
 *       where <tt>K</tt> and <tt>V</tt> are
 *       a primitive type, a wrapper class,
 *       an enum class, or a class supporting conversion from a
 *       <tt>CompositeData</tt> to its class
 *       </li>
 * </ul>
 *
 * <p>
 * When an attribute or operation of a platform MXBean
 * is accessed via an <tt>MBeanServer</tt>, the data types are mapped
 * as follows:
 * <ul>
 *   <li>A primitive type or a wrapper class is mapped
 *       to the same type.
 *       </li>
 *   <li>An {@link Enum} is mapped to
 *       <tt>String</tt> whose value is the name of the enum constant.
 *   <li>A class that defines only getter methods and a static
 *       <tt>from</tt> method with a
 *       {@link javax.management.openmbean.CompositeData CompositeData}
 *       argument is mapped to
 *       {@link javax.management.openmbean.CompositeData CompositeData}.
 *       </li>
 *   <li><tt>Map&lt;K,V&gt;</tt> is mapped to
 *       {@link javax.management.openmbean.TabularData TabularData}
 *       whose row type is a
 *       {@link javax.management.openmbean.CompositeType CompositeType} with
 *       two items whose names are <i>"key"</i> and <i>"value"</i>
 *       and the item types are
 *       the corresponding mapped type of <tt>K</tt> and <tt>V</tt>
 *       respectively and the <i>"key"</i> is the index.
 *       </li>
 *   <li><tt>List&lt;E&gt;</tt> is mapped to an array with the mapped
 *       type of <tt>E</tt> as the element type.
 *       </li>
 *   <li>An array of element type <tt>E</tt> is mapped to
 *       an array of the same dimenions with the mapped type of <tt>E</tt>
 *       as the element type.</li>
 * </ul>
 *
 * The {@link javax.management.MBeanInfo MBeanInfo}
 * for a platform MXBean
 * describes the data types of the attributes and operations
 * as primitive or open types mapped as specified above.
 *
 * <p>
 * For example, the {@link MemoryMXBean}
 * interface has the following <i>getter</i> and <i>setter</i> methods:
 *
 * <blockquote><pre>
 * public MemoryUsage getHeapMemoryUsage();
 * public boolean isVerbose();
 * public void setVerbose(boolean value);
 * </pre></blockquote>
 *
 * These attributes in the <tt>MBeanInfo</tt>
 * of the <tt>MemoryMXBean</tt> have the following names and types:
 *
 * <blockquote>
 * <table border>
 * <tr>
 *   <th>Attribute Name</th>
 *   <th>Type</th>
 *   </tr>
 * <tr>
 *   <td><tt>HeapMemoryUsage</tt></td>
 *   <td>{@link MemoryUsage#from
 *              CompositeData representing MemoryUsage}</td>
 * </tr>
 * <tr>
 *   <td><tt>Verbose</tt></td>
 *   <td><tt>boolean</tt></td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * <h4><a name="MXBeanNames">MXBean Names</a></h4>
 * Each platform MXBean for a Java virtual machine has a unique
 * {@link javax.management.ObjectName ObjectName} for
 * registration in the platform <tt>MBeanServer</tt>.
 * A Java virtual machine has a single instance of the following management
 * interfaces:
 *
 * <blockquote>
 * <table border>
 * <tr>
 * <th>Management Interface</th>
 * <th>ObjectName</th>
 * </tr>
 * <tr>
 * <td> {@link ClassLoadingMXBean} </td>
 * <td> {@link #CLASS_LOADING_MXBEAN_NAME
 *             <tt>java.lang:type=ClassLoading</tt>}</td>
 * </tr>
 * <tr>
 * <td> {@link MemoryMXBean} </td>
 * <td> {@link #MEMORY_MXBEAN_NAME
 *             <tt>java.lang:type=Memory</tt>}</td>
 * </tr>
 * <tr>
 * <td> {@link ThreadMXBean} </td>
 * <td> {@link #THREAD_MXBEAN_NAME
 *             <tt>java.lang:type=Threading</tt>}</td>
 * </tr>
 * <tr>
 * <td> {@link RuntimeMXBean} </td>
 * <td> {@link #RUNTIME_MXBEAN_NAME
 *             <tt>java.lang:type=Runtime</tt>}</td>
 * </tr>
 * <tr>
 * <td> {@link OperatingSystemMXBean} </td>
 * <td> {@link #OPERATING_SYSTEM_MXBEAN_NAME
 *             <tt>java.lang:type=OperatingSystem</tt>}</td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * <p>
 * A Java virtual machine has zero or a single instance of
 * the following management interfaces.
 *
 * <blockquote>
 * <table border>
 * <tr>
 * <th>Management Interface</th>
 * <th>ObjectName</th>
 * </tr>
 * <tr>
 * <td> {@link CompilationMXBean} </td>
 * <td> {@link #COMPILATION_MXBEAN_NAME
 *             <tt>java.lang:type=Compilation</tt>}</td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * <p>
 * A Java virtual machine may have one or more instances of the following
 * management interfaces.
 * <blockquote>
 * <table border>
 * <tr>
 * <th>Management Interface</th>
 * <th>ObjectName</th>
 * </tr>
 * <tr>
 * <td> {@link GarbageCollectorMXBean} </td>
 * <td> {@link #GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE
 *    <tt>java.lang:type=GarbageCollector</tt>}<tt>,name=</tt><i>collector's name</i></td>
 * </tr>
 * <tr>
 * <td> {@link MemoryManagerMXBean} </td>
 * <td> {@link #MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE
 *    <tt>java.lang:type=MemoryManager</tt>}<tt>,name=</tt><i>manager's name</i></td>
 * </tr>
 * <tr>
 * <td> {@link MemoryPoolMXBean} </td>
 * <td> {@link #MEMORY_POOL_MXBEAN_DOMAIN_TYPE
 *    <tt>java.lang:type=MemoryPool</tt>}<tt>,name=</tt><i>pool's name</i></td>
 * </tr>
 * </table>
 * </blockquote>
 *
 * @see <a href="../../../javax/management/package-summary.html">
 *      JMX Specification.</a>
 * @see <a href="package-summary.html#examples">
 *      Ways to Access Management Metrics</a>
 * @see java.util.logging.LoggingMXBean
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
        return sun.management.ManagementFactory.getClassLoadingMXBean();
    }

    /**
     * Returns the managed bean for the memory system of
     * the Java virtual machine.
     *
     * @return a {@link MemoryMXBean} object for the Java virtual machine.
     */
    public static MemoryMXBean getMemoryMXBean() {
        return sun.management.ManagementFactory.getMemoryMXBean();
    }

    /**
     * Returns the managed bean for the thread system of
     * the Java virtual machine.
     *
     * @return a {@link ThreadMXBean} object for the Java virtual machine.
     */
    public static ThreadMXBean getThreadMXBean() {
        return sun.management.ManagementFactory.getThreadMXBean();
    }

    /**
     * Returns the managed bean for the runtime system of
     * the Java virtual machine.
     *
     * @return a {@link RuntimeMXBean} object for the Java virtual machine.

     */
    public static RuntimeMXBean getRuntimeMXBean() {
        return sun.management.ManagementFactory.getRuntimeMXBean();
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
        return sun.management.ManagementFactory.getCompilationMXBean();
    }

    /**
     * Returns the managed bean for the operating system on which
     * the Java virtual machine is running.
     *
     * @return an {@link OperatingSystemMXBean} object for
     * the Java virtual machine.
     */
    public static OperatingSystemMXBean getOperatingSystemMXBean() {
        return sun.management.ManagementFactory.getOperatingSystemMXBean();
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
        return sun.management.ManagementFactory.getMemoryPoolMXBeans();
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
        return sun.management.ManagementFactory.getMemoryManagerMXBeans();
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
        return sun.management.ManagementFactory.getGarbageCollectorMXBeans();
    }

    private static MBeanServer platformMBeanServer;
    /**
     * Returns the platform {@link javax.management.MBeanServer MBeanServer}.
     * On the first call to this method, it first creates the platform
     * <tt>MBeanServer</tt> by calling the
     * {@link javax.management.MBeanServerFactory#createMBeanServer
     * MBeanServerFactory.createMBeanServer}
     * method and registers the platform MXBeans in this platform
     * <tt>MBeanServer</tt> using the <a href="#MXBeanNames">MXBean names</a>
     * defined in the class description.
     * This method, in subsequent calls, will simply return the
     * initially created platform <tt>MBeanServer</tt>.
     * <p>
     * MXBeans that get created and destroyed dynamically, for example,
     * memory {@link MemoryPoolMXBean pools} and
     * {@link MemoryManagerMXBean managers},
     * will automatically be registered and deregistered into the platform
     * <tt>MBeanServer</tt>.
     * <p>
     * If the system property <tt>javax.management.builder.initial</tt>
     * is set, the platform <tt>MBeanServer</tt> creation will be done
     * by the specified {@link javax.management.MBeanServerBuilder}.
     * <p>
     * It is recommended that this platform MBeanServer also be used
     * to register other application managed beans
     * besides the platform MXBeans.
     * This will allow all MBeans to be published through the same
     * <tt>MBeanServer</tt> and hence allow for easier network publishing
     * and discovery.
     * Name conflicts with the platform MXBeans should be avoided.
     *
     * @return the platform <tt>MBeanServer</tt>; the platform
     *         MXBeans are registered into the platform <tt>MBeanServer</tt>
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
            platformMBeanServer =
                sun.management.ManagementFactory.createPlatformMBeanServer();
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

        final Class interfaceClass = mxbeanInterface;
        // Only allow MXBean interfaces from rt.jar loaded by the
        // bootstrap class loader
        final ClassLoader loader =
            AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    return interfaceClass.getClassLoader();
                }
            });
        if (loader != null) {
            throw new IllegalArgumentException(mxbeanName +
                " is not a platform MXBean");
        }

        try {
            final ObjectName objName = new ObjectName(mxbeanName);
            if (!connection.isInstanceOf(objName, interfaceClass.getName())) {
                throw new IllegalArgumentException(mxbeanName +
                    " is not an instance of " + interfaceClass);
            }

            final Class[] interfaces;
            // check if the registered MBean is a notification emitter
            boolean emitter = connection.isInstanceOf(objName, NOTIF_EMITTER);

            // create an MXBean proxy
            return JMX.newMXBeanProxy(connection, objName, mxbeanInterface,
                                      emitter);
        } catch (InstanceNotFoundException e) {
            final IllegalArgumentException iae =
                new IllegalArgumentException(mxbeanName +
                    " not found in the connection.");
            iae.initCause(e);
            throw iae;
        } catch (MalformedObjectNameException e) {
            final IllegalArgumentException iae =
                new IllegalArgumentException(mxbeanName +
                    " is not a valid ObjectName format.");
            iae.initCause(e);
            throw iae;
        }
    }

    private static final String NOTIF_EMITTER =
        "javax.management.NotificationEmitter";
}
