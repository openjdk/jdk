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

package javax.management.namespace;


import java.io.IOException;

import java.util.UUID;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * MBean Servers can be federated into a single hierarchical name space:
 * A JMXNamespace is an MBean that handles a sub name space in that
 * hierarchical name space.
 * <p>
 * A name space is created simply by registering a {@code JMXNamespace}
 * MBean in the MBean Server. The name of the created name space is defined
 * by the {@linkplain JMXNamespaces#getNamespaceObjectName name of the JMXNamespace}
 * that handles it. A name space is equivalent to
 * an MBean Server within an MBean Server. When creating a {@code JMXNamespace},
 * the MBean Server within is passed to the constructor.
 * </p>
 * <p>
 * The {@code JMXNamespace} class is the base class for implementing
 * all name space handlers. All name space handlers must be instances of
 * {@code JMXNamespace} or a subclass of it.
 * </p>
 * <p>
 * A concrete example of a {@code JMXNamespace} MBean subclass
 * is the {@link JMXRemoteNamespace JMXRemoteNamespace} MBean which
 * is able to mirror all MBeans contained in a remote MBean server known by its
 * {@link javax.management.remote.JMXServiceURL}.
 * </p>
 * <p>
 * You can create a local namespace by supplying a newly created MBean Server
 * to an instance of {@code JMXNamespace}. For instance:
 * <pre>
 * final String namespace = "foo";
 * final ObjectName namespaceName = {@link JMXNamespaces#getNamespaceObjectName
 *       JMXNamespaces.getNamespaceObjectName(namespace)};
 * server.registerMBean(new JMXNamespace(MBeanServerFactory.newMBeanServer()),
 *                      namespaceName);
 * </pre>
 * </p>
 * <p>
 * <u>Note:</u> A JMXNamespace MBean cannot be registered
 * simultaneously in two different
 * MBean servers, or indeed in the same MBean Server with two
 * different names. It is however possible to give the same MBeanServer
 * instance to two different JMXNamespace MBeans, and thus create a graph
 * rather than a tree.
 * </p>
 *
 * <p>To view the content of a namespace, you will usually use an
 *    instance of {@link JMXNamespaceView}. For instance, given the
 *    namespace {@code "foo"} created above, you would do:
 * </p>
 * <pre>
 * final JMXNamespaceView view = new JMXNamespaceView(server);
 * System.out.println("List of namespaces: "+Arrays.toString({@link JMXNamespaceView#list() view.list()}));
 *
 * final JMXNamespaceView foo  = {@link JMXNamespaceView#down view.down("foo")};
 * System.out.println({@link JMXNamespaceView#where() foo.where()}+" contains: " +
 *        {@link JMXNamespaceView#getMBeanServerConnection foo.getMBeanServerConnection()}.queryNames(null,null));
 * </pre>
 *
 * <h2 id="PermissionChecks">JMX Namespace Permission Checks</h2>
 *
 * <p>A special {@link JMXNamespacePermission} is defined to check access
 * to MBean within namespaces.</p>
 * <p>When a JMXNamespace MBean is registered in an
 * MBean server created through the default {@link
 * javax.management.MBeanServerBuilder}, and if a {@link
 * SecurityManager SecurityManager} is
 * {@linkplain System#getSecurityManager() present}, the MBeanServer will
 * check a {@link JMXNamespacePermission} before invoking
 * any method on the {@linkplain #getSourceServer source MBeanServer} of the
 * JMXNamespace.
 * {@linkplain JMXNamespacePermission JMX Namespace Permissions} are similar to
 * {@linkplain javax.management.MBeanPermission MBean Permissions}, except
 * that you usually cannot specify an MBean class name. You can however
 * specify object name patterns - which will allow you for example to only grant
 * permissions for MBeans having a specific {@code type=<MBeanType>} key
 * in their object name.
 * <p>
 * Another difference is that {@link JMXNamespacePermission
 * JMXNamespacePermission} also specifies from which namespace and which
 * MBean server the permission is granted.
 * </p>
 * <p>In the rest of this document, the following terms are used:</p>
 * <ul>
 *    <li id="MBeanServerName"><p>{@code server name} is the
 *    <a href="../MBeanServerFactory.html#MBeanServerName">name of the
 *    MBeanServer</a> in which the permission is granted.
 *    The name of an {@code MBeanServer} can be obtained by calling {@link
 *    javax.management.MBeanServerFactory#getMBeanServerName
 *    MBeanServerFactory.getMBeanServerName(mbeanServer)}
 *    </p>
 *    <li id="NamespaceName"><p>{@code namespace} is the name of the namespace
 *    in the <a href="#MBeanServerName">named MBean server</a> for which the
 *    permission is granted. It doesn't contain any
 *    {@link JMXNamespaces#NAMESPACE_SEPARATOR namespace separator}.
 *    </p>
 *    <li id="MBeanName"><p>{@code mbean} is the name
 *    of the MBean in that {@code namespace}. This is the name of the MBean
 *    in the namespace's {@link JMXNamespace#getSourceServer() source mbean server}.
 *    It might contain no, one, or several {@link
 *    JMXNamespaces#NAMESPACE_SEPARATOR namespace separators}.
 *    </p>
 * </ul>
 *
 * <p>For instance let's assume that some piece of code calls:</p>
 * <pre>
 *     final MBeanServer mbeanServer = ...;
 *     final ObjectName  name   = new ObjectName("a//b//c//D:k=v");
 *     mbeanServer.getAttribute(name,"Foo");
 * </pre>
 * <p>
 *   Assuming that there is a security manager, or that the
 *   implementation chooses to make checks anyway, the checks that will
 *   be made in that case are:
 * </p>
 * <ol>
 * <li id="check1">
 * <code>JMXNamespacePermission(mbeanServerName, "Foo", "<b>a//</b>b//c//D:k=v",
 * "getAttribute")</code>
 * (where {@code mbeanServerName=MBeanServerFactory.getMBeanServerName(mbeanServer)},
 * <code>namespace="<b>a</b>"</code>, and {@code mbean="b//c//D:k=v"})
 * </li>
 * <li id="check2">and in addition if namespace {@code "a"} is local,
 * <code>JMXNamespacePermission(aSourceServerName,"Foo","<b>b//</b>c//D:k=v",
 *       "getAttribute")}</code>
 * (where
 * {@code aSourceServerName=MBeanServerFactory.getMBeanServerName(sourceServer(a))},
 * <code>namespace="<b>b</b>"</code>, and {@code mbean="c//D:k=v"}),
 * </li>
 * <li id="check3">and in addition if namespace {@code "b"} is also local,
 * <code>JMXNamespacePermission(bSourceServerName,"Foo","<b>c//</b>D:k=v",
 *       "getAttribute")}</code>
 * (where
 * {@code bSourceServerName=MBeanServerFactory.getMBeanServerName(sourceServer(b))},
 * <code>namespace="<b>c</b>"</code>, and {@code mbean="D:k=v"}),
 * </li>
 * <li id="check4">and in addition if the source mbean server of namespace
 * {@code "c"} is a also a local MBeanServer in this JVM,
 * {@code MBeanPermission(cSourceServerName,<className(D:k=v)>,"Foo","D:k=v","getAttrinute")},
 * (where
 * {@code cSourceServerName=MBeanServerFactory.getMBeanServerName(sourceServer(c))}).
 * </li>
 * </ol>
 * <p>For any of these MBean servers, if no name was supplied when
 * creating that MBeanServer the {@link JMXNamespacePermission} is
 * created with an {@code mbeanServerName} equal to
 * {@value javax.management.MBeanServerFactory#DEFAULT_MBEANSERVER_NAME}.
 * </p>
 * <p>If the namespace {@code a} is in fact a remote {@code MBeanServer},
 *    for instance because namespace {@code a} is implemented by a {@link
 *    JMXRemoteNamespace} pointing to a distant MBeanServer located in
 *    another JMX agent, then checks <a href="#check2">2</a>,
 *    <a href="#check3">3</a>, and <a href="#check4">4</a> will not
 *    be performed in the local JVM. They might or might not be performed in
 *    the remote agent, depending on how access control and permission
 *    checking are configured in the remote agent, and how authentication
 *    is configured in the connector used by the {@link
 *    JMXRemoteNamespace}.
 * </p>
 * <p>In all cases, {@linkplain JMXNamespacePermission JMX Namespace Permissions}
 * are checked as follows:</p>
 * <p>First, if there is no security manager ({@link
 * System#getSecurityManager()} is null), then an implementation of
 * of MBeanServer that supports JMX namespaces is free not to make any
 * checks.</p>
 *
 * <p>Assuming that there is a security manager, or that the
 * implementation chooses to make checks anyway, the checks are made
 * as detailed below.</p>
 *
 * <p>If a security check fails, the method throws {@link
 * SecurityException}.</p>
 *
 * <ul>
 *
 * <li><p>For the {@link MBeanServer#invoke invoke} method, the caller's
 * permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, &lt;operation name&gt;, &lt;namespace&gt;//&lt;mbean&gt;, "invoke")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#getAttribute getAttribute} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, &lt;attribute&gt;, &lt;namespace&gt;//&lt;mbean&gt;, "getAttribute")}.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#getAttributes getAttributes} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, &lt;null&gt;, &lt;namespace&gt;//&lt;mbean&gt;, "getAttribute")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * Additionally, for each attribute <em>att</em> in the {@link
 * javax.management.AttributeList}, if the caller's permissions do not
 * imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, <em>att</em>,
 * &lt;namespace&gt;//&lt;mbean&gt;, "getAttribute")}, the
 * MBean server will behave as if that attribute had not been in the
 * supplied list.</p>
 *
 * <li><p>For the {@link MBeanServer#setAttribute setAttribute} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, &lt;attrName&gt;, &lt;namespace&gt;//&lt;mbean&gt;, "setAttribute")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace, and
 * <code>attrName</code> is {@link javax.management.Attribute#getName()
 * attribute.getName()}.</p>
 *
 * <li><p>For the {@link MBeanServer#setAttributes setAttributes} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;, "setAttribute")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * Additionally, for each attribute <em>att</em> in the {@link
 * javax.management.AttributeList}, if the caller's permissions do not
 * imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, <em>att</em>, &lt;namespace&gt;//&lt;mbean&gt;, "setAttribute")},
 * the MBean server will behave as if that attribute had not been in the
 * supplied list.</p>
 *
 * <li><p>For the <code>addNotificationListener</code> methods,
 * the caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "addNotificationListener")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * </p>
 *
 * <li><p>For the <code>removeNotificationListener</code> methods,
 * the caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "removeNotificationListener")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#getMBeanInfo getMBeanInfo} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "getMBeanInfo")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#getObjectInstance getObjectInstance} method,
 * the caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "getObjectInstance")},
 * where <a href="#MBeanServerName">mbean server name/a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#isInstanceOf isInstanceOf} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "isInstanceOf")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#queryMBeans queryMBeans} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, null,
 * "queryMBeans")}.
 * Additionally, for each MBean {@code mbean} that matches {@code pattern},
 * if the caller's permissions do not imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "queryMBeans")}, the
 * MBean server will behave as if that MBean did not exist.</p>
 *
 * <p>Certain query elements perform operations on the MBean server.
 * However these operations are usually performed by the MBeanServer at the
 * bottom of the namespace path, and therefore, do not involve any
 * {@link JMXNamespacePermission} permission check. They might involve
 * {@link javax.management.MBeanPermission} checks depending on how security
 * in the JVM in which the bottom MBeanServer resides is implemented.
 * See {@link javax.management.MBeanServer} for more details.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#queryNames queryNames} method, the checks
 * are the same as for <code>queryMBeans</code> except that
 * <code>"queryNames"</code> is used instead of
 * <code>"queryMBeans"</code> in the <code>JMXNamespacePermission</code>
 * objects.  Note that a <code>"queryMBeans"</code> permission implies
 * the corresponding <code>"queryNames"</code> permission.</p>
 *
 * <li><p>For the {@link MBeanServer#getClassLoader getClassLoader} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;loaderName&gt;,
 * "getClassLoader")},
 * where <a href="#MBeanServerName">mbean server name/a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">loaderName</a> is the name of the ClassLoader MBean
 * which is accessed, in that namespace.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#getClassLoaderFor getClassLoaderFor} method,
 * the caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "getClassLoaderFor")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which the action
 * is performed, in that namespace.
 * </p>
 *
 * <li><p>For the {@link MBeanServer#registerMBean registerMBean} method, the
 * caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, &lt;class name&gt;, &lt;namespace&gt;//&lt;mbean&gt;,
 * "registerMBean")}.  Here
 * <code>class name</code> is the string returned by {@code
 * obj.getClass().getName()} where {@code obj} is the mbean reference,
 * <a href="#MBeanServerName"mbean server name/a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean which is being
 * registered, relative to that namespace.
 *
 * <li><p>For the <code>createMBean</code> methods, the caller's
 * permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, &lt;class name&gt;, &lt;namespace&gt;//&lt;mbean&gt;,
 * "instantiate")} and
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, &lt;class name&gt;, &lt;namespace&gt;//&lt;mbean&gt;,
 * "registerMBean")}, where
 * <code>class name</code> is the string passed as first argument to the {@code
 * createMBean} method,
 * <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean which is being
 * created, relative to that namespace.
 *
 * <li><p>For the {@link MBeanServer#unregisterMBean unregisterMBean} method,
 * the caller's permissions must imply {@link
 * JMXNamespacePermission#JMXNamespacePermission(String,String,ObjectName,String)
 * JMXNamespacePermission(&lt;mbean server name&gt;, null, &lt;namespace&gt;//&lt;mbean&gt;,
 * "unregisterMBean")},
 * where <a href="#MBeanServerName">mbean server name</a> is the name of the
 * {@code MBeanServer} in which the {@code JMXNamespace} MBean in charge of
 * <a href="#NamespaceName">namespace</a> is registered, and
 * <a href="#MBeanName">mbean</a> is the name of the MBean on which is
 * being unregistered, relative to that namespace.
 * </p>
 * </ul>
 * </p>
 * <p>It must be noted that if all namespaces are local, and all
 *    local namespaces are implemented by regular MBean servers, that is, there
 *    are no {@linkplain MBeanServerSupport Virtual Namespaces}, then
 *    simple {@linkplain javax.management.MBeanPermission MBean Permission}
 *    checks might be enough to secure an application.
 *    In that case, it is possible to specify the following {@link
 *    JMXNamespacePermission} permission in the policy file, which implies all
 *    other JMX namespace permissions:
 * </p>
 * <pre>
 *     permission javax.management.namespace.JMXNamespacePermission "*::*[]", "*";
 * </pre>
 *
 * @since 1.7
 */
public class JMXNamespace
        implements JMXNamespaceMBean, MBeanRegistration {

    /**
     * The standard value of the {@code type}
     * property key that must be used to construct valid {@link
     * JMXNamespaceMBean} ObjectNames.<br>
     * This is {@value #TYPE}.
     **/
    public static final String TYPE = "JMXNamespace";

    /**
     * The {@link ObjectName#getKeyPropertyListString keyPropertyListString}
     * that must be used to construct valid {@link JMXNamespaceMBean}
     * ObjectNames.<br>
     * This is
     * <code>{@value #TYPE_ASSIGNMENT}</code>.
     **/
    public static final String TYPE_ASSIGNMENT = "type="+TYPE;

    private volatile MBeanServer mbeanServer; // the mbean server in which
                                              // this MBean is registered.
    private volatile ObjectName objectName;   // the ObjectName of this MBean.
    private final MBeanServer sourceServer;   // the MBeanServer within = the
                                              // name space (or the MBean server
                                              // that contains it).
    private final String uuid;

    /**
     * Creates a new JMXNamespace implemented by means of an MBean Server.
     * A namespace is equivalent to an MBeanServer within an MBean Server.
     * The {@code sourceServer} provided to this constructor is the MBean Server
     * within.
     * @param sourceServer the MBean server that implemented by this namespace.
     * @see #getSourceServer
    */
    public JMXNamespace(MBeanServer sourceServer) {
        this.sourceServer = sourceServer;
        this.uuid = UUID.randomUUID().toString();
    }

    /**
     * This method is part of the {@link MBeanRegistration} interface.
     * The {@link JMXNamespace} class uses the {@link MBeanRegistration}
     * interface in order to get a reference to the MBean server in which it is
     * registered. It also checks the validity of its own ObjectName.
     * <p>
     * This method is called by the MBean server.
     * Application classes should never call this method directly.
     * <p>
     * If this method is overridden, the overriding method should call
     * {@code super.preRegister(server,name)}.
     * @see MBeanRegistration#preRegister MBeanRegistration
     * @see JMXNamespaces#getNamespaceObjectName getNamespaceObjectName
     * @param name The object name of the MBean. <var>name</var> must be a
     *  syntactically valid JMXNamespace name, as returned by
     *  {@link JMXNamespaces#getNamespaceObjectName(java.lang.String)
     *   getNamespaceObjectName(namespace)}.
     * @return The name under which the MBean is to be registered.
     * @throws IllegalArgumentException if the name supplied is not valid.
     * @throws Exception can be thrown by subclasses.
     */
    public ObjectName preRegister(MBeanServer server, ObjectName name)
        throws Exception {
        // need to synchronize to protect against multiple registration.
        synchronized(this) {
            if (objectName != null && ! objectName.equals(name))
                throw new IllegalStateException(
                    "Already registered under another name: " + objectName);
            objectName = validateHandlerName(name);
            mbeanServer = server;
        }
        return name;
    }

    /**
     * Validate the ObjectName supplied to preRegister.
     * This method is introduced to allow standard subclasses to use
     * an alternate naming scheme. For instance - if we want to
     * reuse JMXNamespace in order to implement sessions...
     * It is however only available for subclasses in this package.
     **/
    ObjectName validateHandlerName(ObjectName suppliedName) {
        if (suppliedName == null)
            throw new IllegalArgumentException("Must supply a valid name");
        final String dirName = JMXNamespaces.
                normalizeNamespaceName(suppliedName.getDomain());
        final ObjectName handlerName =
                JMXNamespaces.getNamespaceObjectName(dirName);
        if (!suppliedName.equals(handlerName))
            throw new IllegalArgumentException("invalid name space name: "+
                        suppliedName);
        return suppliedName;
    }

    /**
     * This method is part of the {@link MBeanRegistration} interface.
     * The {@link JMXNamespace} class uses the {@link MBeanRegistration}
     * interface in order to get a reference to the MBean server in which it is
     * registered.
     * <p>
     * This method is called by the MBean server. Application classes should
     * not call this method directly. Subclasses are free to override this
     * method with their own specific behavior - but the overriding method
     * shoud still call {@code super.postRegister(registrationDone)}.
     * @see MBeanRegistration#postRegister MBeanRegistration
     */
    public void postRegister(Boolean registrationDone) {
        // nothing to do
    }

    /**
     * This method is part of the {@link MBeanRegistration} interface.
     * The {@link JMXNamespace} class uses the {@link MBeanRegistration}
     * interface in order to get a reference to the MBean server in which it is
     * registered.
     * <p>
     * This method is called by the MBean server. Application classes should
     * not call this method directly. Subclasses are free to override this
     * method with their own specific behavior - but the overriding method
     * shoud still call {@code super.preDeregister()}.
     * @see MBeanRegistration#preDeregister MBeanRegistration
     */
    public void preDeregister() throws Exception {
        // nothing to do
    }

    /**
     * This method is part of the {@link MBeanRegistration} interface.
     * It allows the {@code JMXNamespace} MBean to perform any operations
     * needed after having been unregistered in the MBean server.
     * <p>
     * This method is called by the MBean server. Application classes should
     * not call this method directly. If a subclass overrides this
     * method, the overriding method shoud  call {@code super.postDeregister()}.
     * @see MBeanRegistration#postDeregister MBeanRegistration
     */
    public void postDeregister() {
        // need to synchronize to protect against multiple registration.
        synchronized(this) {
            mbeanServer = null;
            objectName  = null;
        }
    }


    /**
     * Returns the MBeanServer in which this MBean is registered,
     * or null. Chiefly of interest for subclasses.
     * @return the MBeanServer supplied to {@link #preRegister}.
     **/
    public final MBeanServer getMBeanServer() {
        return mbeanServer;
    }

    /**
     * Returns the MBeanServer that contains or emulates the source
     * namespace. When a JMXNamespace MBean is registered in an
     * MBean server created through the default {@link
     * javax.management.MBeanServerBuilder}, the MBeanServer will
     * check {@link JMXNamespacePermission} before invoking
     * any method on the source MBeanServer of the JMXNamespace.
     * See <a href="#PermissionChecks">JMX Namespace Permission Checks</a>
     * above.
     * @return an MBeanServer view of the source namespace
     **/
    public MBeanServer getSourceServer() {
        return sourceServer;
    }

    /**
     * Returns the ObjectName with which this MBean was registered,
     * or null. Chiefly of interest for subclasses.
     * @return the ObjectName supplied to {@link #preRegister}.
     **/
    public final ObjectName getObjectName() {
        return objectName;
    }

    /**
     * HandlerName used in traces.
     **/
    String getHandlerName() {
        final ObjectName name = getObjectName();
        if (name != null) return name.toString();
        return this.toString();
    }

   /**
    * In this class, this method returns {@link #getSourceServer
    * getSourceServer()}.{@link javax.management.MBeanServer#getMBeanCount
    * getMBeanCount()}.
    * <br>This default behaviour may be redefined in subclasses.
    * @throws java.io.IOException can be thrown by subclasses.
    */
    public Integer getMBeanCount() throws IOException {
        return getSourceServer().getMBeanCount();
    }

   /**
    * In this class, this method returns {@link #getSourceServer
    * getSourceServer()}.{@link javax.management.MBeanServer#getDomains
    * getDomains()}.
    * <br>This default behaviour may be redefined in subclasses.
    * @throws java.io.IOException can be thrown by subclasses.
    */
   public String[] getDomains() throws IOException {
       return getSourceServer().getDomains();
    }

   /**
    * In this class, this method returns {@link #getSourceServer
    * getSourceServer()}.{@link javax.management.MBeanServer#getDefaultDomain
    * getDefaultDomain()}.
    * <br>This default behaviour may be redefined in subclasses.
    * @throws java.io.IOException can be thrown by subclasses.
    */
    public String getDefaultDomain() throws IOException {
        return getSourceServer().getDefaultDomain();
    }

    public final String getUUID() {
        return uuid;
    }

}
