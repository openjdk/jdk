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

/**
 *  <p>The <code>javax.management.namespace</code> package makes it possible
 *  to federate MBeanServers into a hierarchical name space.</p>
 *
 *  <h3 id="WhatIs">What Is a Name Space?</h3>
 *  <p>
 *      A name space is like an {@link javax.management.MBeanServer} within
 *      an {@code MBeanServer}. Just as a file system folder can contain
 *      another file system folder, an {@code MBeanServer} can contain another
 *      {@code MBeanServer}. Similarly, just as a remote folder on a remote
 *      disk can be mounted on a parent folder on a local disk, a remote name
 *      space in a remote {@code MBeanServer} can be mounted on a name
 *      space in a local parent {@code MBeanServer}.
 *  </p>
 *  <p>
 *      The <code>javax.management.namespace</code> API thus makes it possible to
 *      create a hierarchy of MBean servers federated in a hierarchical name
 *      space inside a single {@code MBeanServer}.
 *  </p>
 *  <h3 id="HowToCreate">How To Create a Name Space?</h3>
 *  <p>
 *      To create a name space, you only need to register a
 *      {@link javax.management.namespace.JMXNamespace} MBean in
 *      an MBean server. We have seen that a namespace is like
 *      an {@code MBeanServer} within an {@code MBeanServer}, and
 *      therefore, it is possible to create a namespace that shows the
 *      content of another {@code MBeanServer}. The simplest case is
 *      when that {@code MBeanServer} is another {@code MBeanServer}
 *      created by the {@link javax.management.MBeanServerFactory} as
 *      shown in the extract below:
 *  </p>
 *  <pre>
 *  final MBeanServer server = ....;
 *  final String namespace = "foo";
 *  final ObjectName namespaceName = {@link javax.management.namespace.JMXNamespaces#getNamespaceObjectName
 *        JMXNamespaces.getNamespaceObjectName(namespace)};
 *  server.registerMBean(new JMXNamespace(MBeanServerFactory.newMBeanServer()),
 *                      namespaceName);
 *  </pre>
 *  <p id="NamespaceView">
 *     To navigate in namespaces and view their content, the easiest way is
 *     to use an instance of {@link javax.management.namespace.JMXNamespaceView}. For instance, given
 *     the {@code server} above, in which we created a namespace {@code "foo"},
 *     it is possible to create a {@code JMXNamespaceView} that will make it
 *     possible to navigate easily in the namespaces and sub-namespaces of that
 *     server:
 *  </p>
 *  <pre>
 *  // create a namespace view for 'server'
 *  final JMXNamespaceView view = new JMXNamespaceView(server);
 *
 *  // list all top level namespaces in 'server'
 *  System.out.println("List of namespaces: " + Arrays.toString({@link javax.management.namespace.JMXNamespaceView#list() view.list()}));
 *
 *  // go down into namespace 'foo': provides a namespace view of 'foo' and its
 *  // sub namespaces...
 *  final JMXNamespaceView foo = {@link javax.management.namespace.JMXNamespaceView#down view.down("foo")};
 *
 *  // list all MBeans contained in namespace 'foo'
 *  System.out.println({@link javax.management.namespace.JMXNamespaceView#where() foo.where()} + " contains: " +
 *         {@link javax.management.namespace.JMXNamespaceView#getMBeanServerConnection foo.getMBeanServerConnection()}.queryNames(null,null));
 *  </pre>
 * <p>
 *   It is also possible to create more complex namespaces, such as namespaces
 *   that point to MBean servers located in remote JVMs.
 * </p>
 * <p>
 *      For instance, to mount the MBeanServer accessible
 *      at <code>service:jmx:rmi:///jndi/rmi://localhost:9000/jmxrmi</code>
 *      in a name space {@code "foo"} inside the {@linkplain
 *      java.lang.management.ManagementFactory#getPlatformMBeanServer platform
 *      MBeanServer} you would write the following piece of code:
 *  </p>
 *  <pre>
 *      final JMXServiceURL sourceURL =
 *         new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:9000/jmxrmi");
 *      final MBeanServer platform = ManagementFactory.getPlatformMBeanServer();
 *      final Map&lt;String,Object&gt; options = Collections.emptyMap();
 *      final JMXRemoteNamespace mbean = {@link
 *            javax.management.namespace.JMXRemoteNamespace JMXRemoteNamespace}.
 *         {@link javax.management.namespace.JMXRemoteNamespace#newJMXRemoteNamespace newJMXRemoteNamespace(sourceURL, options)};
 *      final ObjectName name = {@link javax.management.namespace.JMXNamespaces JMXNamespaces}.{@link javax.management.namespace.JMXNamespaces#getNamespaceObjectName(String) getNamespaceObjectName("foo")};
 *      final ObjectInstance ref = platform.registerMBean(mbean,name);
 *      platform.invoke(ref.getObjectName(),"connect",null,null);
 *  </pre>
 *
 *  <h3 id="WhatLike">What Does a Name Space Look Like?</h3>
 *
 *  <p>
 *   We have seen that {@link javax.management.namespace.JMXNamespaceView} class
 *   provides an easy way to navigate within namespaces. It is however also
 *   possible to interact with namespaces directly from the top level
 *   {@code MBeanServer} in which they have been created.
 *      From the outside, a name space only appears as a special MBean in
 *      the MBean server. There's nothing much you can do with this MBean
 *      directly.
 *  </p>
 *  <p>
 *      For instance, let's assume you have registered a {@link
 *      javax.management.namespace.JMXRemoteNamespaceMBean
 *      JMXRemoteNamespaceMBean} to manage the name space {@code "foo"}.
 *      <br>If you query for
 *      <code>platform.queryNames("&#42;//:*",null)</code>, then you will see
 *      one MBean named {@code "foo//:type=JMXNamespace"}.
 *      <br>This is the {@link javax.management.namespace.JMXNamespace}
 *      MBean which is in charge of handling the namespace {@code "foo"}.
 *  </p>
 *  <p>
 *      In fact, name space handler MBeans are instances of
 *      the class {@link javax.management.namespace.JMXNamespace} - or
 *      instances of a subclass of that class.
 *      They have a special {@link javax.management.ObjectName} defined by
 *      {@link javax.management.namespace.JMXNamespaces#getNamespaceObjectName
 *      JMXNamespaces.getNamespaceObjectName}.<br>
 *      {@link javax.management.namespace.JMXNamespace} instances are able
 *      to return an {@link
 *      javax.management.namespace.JMXNamespace#getSourceServer MBeanServer}
 *      which corresponds to the MBeanServer within (= the name space itself).
 *  </p>
 *  <p>
 *      So how does it work? How can you see the MBeans contained in the new
 *      name space?
 *  </p>
 *  <p>In order to address scalability issues, MBeans registered in
 *     namespaces (such as our namespace {@code "foo"} above) can not be
 *     seen with {@code mbeanServer.queryNames("*:*",null)}. To see the MBeans
 *     contained in a namespace, you can use one of these methods:
 *  </p>
 *  <ol>
 *      <li>
 *          You can use the {@link javax.management.namespace.JMXNamespaceView}
 *          class <a href="#NamespaceView">shown above</a>,
 *      </li>
 *      <li>
 *          or you can <a href="#NamespacePrefix">directly look</a> for MBeans
 *          whose names match
 *         {@code "foo//*:*"},
 *      </li>
 *      <li>
 *          or you can <a href="#ChangeTo">narrow down</a> to the namespace
 *          and obtain an MBeanServer
 *          proxy that corresponds to an MBeanServer view of that namespace.
 *          The JMXNamespaces class provides a static method that
 *          allows you to narrow down to a name space, by calling
 *          {@link javax.management.namespace.JMXNamespaces#narrowToNamespace(MBeanServer,String)
 *                 JMXNamespaces.narrowToNamespace}.
 *      </li>
 *  </ol>
 *
 *  <h3 id="NamespacePrefix">Using Name Space Prefixes</h3>
 *  <p>
 *      As we have explained above, MBeans contained in name
 *      spaces are not returned by {@code server.queryNames(null,null)} - or
 *      <code>server.queryNames({@link javax.management.ObjectName#WILDCARD ObjectName.WILDCARD},null)</code>.
 *      <br>
 *      However, these MBeans can still be accessed from the top level
 *      {@code MBeanServer} interface, without using any API specific to the
 *      version 2.0 of the JMX API, simply by using object names with
 *      name space prefixes:
 *      <br>To list MBeans contained in a namespace {@code "foo"} you can
 *      query for MBeans whose names match {@code "foo//*:*"}, as shown
 *      earlier in this document:
 *      <pre>
 *         server.queryNames(new ObjectName("foo//*:*", null);
 *         // or equivalently:
 *         server.queryNames(JMXNamespaces.getWildcardFor("foo"), null);
 *      </pre>
 *      This will return a list of MBean names whose domain name starts
 *      with {@code foo//}.
 *  </p><p>
 *      Using these names, you can invoke any operation on the corresponding
 *      MBeans. For instance, to get the {@link javax.management.MBeanInfo
 *      MBeanInfo} of an MBean
 *      contained in name space {@code "foo"} (assuming
 *      the name of the MBean within its name space is <i>domain:type=Thing</i>,
 *      then simply call:
 *      <pre>
 *         server.getMBeanInfo(new ObjectName("foo//domain:type=Thing"));
 *      </pre>
 *      An easier way to access MBeans contained in a name space is to
 *      <i>cd</i> inside the name space, as shown in the following paragraph.
 *  </p>
 *
 *  <h3 id="ChangeTo">Narrowing Down Into a Name Spaces</h3>
 *  <p>
 *      As we have seen, name spaces are like MBean servers within MBean servers.
 *      Therefore, it is possible to view a name space just as if it were
 *      an other MBean server. This is similar to opening a sub
 *      folder from a parent folder.<br>
 *      This operation is illustrated in the code extract below:
 *      <pre>
 *          final MBeanServer foo =
 *                JMXNamespaces.narrowToNamespace(platform, "foo");
 *          final MBeanInfo info =
 *                foo.getMBeanInfo(new ObjectName("domain:type=Thing"));
 *      </pre>
 *      The {@code MBeanServer} returned by {@link
 *      javax.management.namespace.JMXNamespaces#narrowToNamespace(MBeanServer,String)
 *      JMXNamespaces.narrowToNamespace} is an {@code MBeanServer} view that
 *      narrows down into a given namespace. The MBeans contained inside that
 *      namespace can now be accessed by their regular local name. <br>
 *      The MBean server obtained by narrowing down
 *      to name space {@code "foo"} behaves just like a regular MBean server.
 *      However, it may sometimes throw an {@link
 *      java.lang.UnsupportedOperationException UnsupportedOperationException}
 *      wrapped in a JMX exception if you try to call an operation which is not
 *      supported by the underlying name space handler.
 *      <br>For instance, {@link javax.management.MBeanServer#registerMBean
 *      registerMBean} is not supported for name spaces mounted from remote
 *      MBean servers.
 *  </p>
 *  <p>
 *      <u>Note:</u> If you have a deep hierarchy of namespaces, and if you
 *      are switching from one namespace to another in the course of your
 *      application, it might be more convenient to use a
 *      {@link javax.management.namespace.JMXNamespaceView}
 *      in order to navigate in your namespaces.
 *  </p>
 *
 *  <h3 id="NamespaceTypes">Different Types of Name Spaces</h3>
 *      <p>
 *          This API lets you create several types of name spaces:
 *          <ul>
 *              <li id="RemoteNS">
 *                  You can use the {@link
 *                  javax.management.namespace.JMXRemoteNamespace
 *                  JMXRemoteNamespace} to create
 *                  <b>remote</b> name spaces, mounted from
 *                  a remote sub {@code MBeanServer} source, as shown
 *                  <a href="#HowToCreate">earlier</a> in this document.
 *              </li>
 *              <li id="LocalNS">
 *                  You can also use {@link
 *                  javax.management.namespace.JMXNamespace
 *                  JMXNamespace} to create
 *                  <b>local</b> name spaces,
 *                  by providing a direct reference to another {@code MBeanServer}
 *                  instance living in the same JVM.
 *              </li>
 *              <li id="VirtualNS">
 *                  Finally, you can create
 *                  name spaces containing <b>virtual</b> MBeans,
 *                  by subclassing the {@link
 *                  javax.management.namespace.MBeanServerSupport
 *                  MBeanServerSupport}, and passing an instance of
 *                  your own subclass to a {@link
 *                  javax.management.namespace.JMXNamespace JMXNamespace}.
 *              </li>
 *              <li id="CustomNS">
 *                  If none of these classes suit your needs, you can also provide
 *                  <b>your own</b> subclass of {@link
 *                  javax.management.namespace.JMXNamespace
 *                  JMXNamespace}. This is however discouraged.
 *              </li>
 *          </ul>
 *      </p>
 *
 *      <h3 id="SpecialOp">Name Spaces And Special Operations</h3>
 *      <p>
 *          MBean Naming considerations aside, Name Spaces are transparent for
 *          most {@code MBeanServer} operations. There are however a few
 *          exceptions:
 *      </p>
 *      <ul>
 *          <li>
 *              <p>MBeanServer only operations - these are the operations which are
 *              supported by {@link javax.management.MBeanServer MBeanServer} but
 *              are not present in {@link
 *              javax.management.MBeanServerConnection
 *              MBeanServerConnection}. Since a name space can be a local view of
 *              a remote {@code MBeanServer}, accessible only through an
 *              {@code MBeanServerConnection}, these
 *              kinds of operations are not always supported.</p>
 *              <ul>
 *                  <li id="registerMBean">
 *                      <p>registerMBean:</p>
 *                      <p> The {@link javax.management.MBeanServer#registerMBean
 *                          registerMBean}
 *                          operation is not supported by most name spaces. A call
 *                          to
 *                          <pre>
 *   MBeanServer server = ....;
 *   ThingMBean mbean = new Thing(...);
 *   ObjectName name = new ObjectName("foo//domain:type=Thing");
 *   server.registerMBean(mbean, name);
 *                          </pre>
 *                          will usually fail, unless the name space
 *                          {@code "foo"} is a <a href="#LocalNS">local</a> name
 *                          space. In the case where you attempt to cross
 *                          multiple name spaces, then all name spaces in the
 *                          path must support the {@code registerMBean} operation
 *                          in order for it to succeed.<br>
 *                          To create an MBean inside a name space, it is
 *                          usually safer to use {@code createMBean} -
 *                          although some <a href="#MBeanCreation">special
 *                          considerations</a> can also apply.
 *                      </p>
 *         <p></p>
 *                  </li>
 *                  <li id="getClassLoader">
 *                      <p>getClassLoader:</p>
 *                      <p> Similarly to <a href="#registerMBean">registerMBean</a>,
 *                          and for the same reasons, {@link
 *                          javax.management.MBeanServer#getClassLoader
 *                          getClassLoader} will usually fail, unless the
 *                          class loader is an MBean registered in a
 *                          <a href="#LocalNS">local</a> name space.<br>
 *                      </p>
 *                  </li>
 *                  <li id="getClassLoaderFor">
 *                      <p>getClassLoaderFor:</p>
 *                      <p> The implementation of {@link
 *                          javax.management.MBeanServer#getClassLoaderFor
 *                          getClassLoaderFor} also depends on which
 *                          <a href="#NamespaceTypes">type of name space</a>
 *                          handler is used across the namespace path.
 *                      </p>
 *                      <p>
 *                          A <a href="#LocalNS">local</a> name space will usually
 *                          be able to implement this method just as a real
 *                          {@code MBeanServer} would. A
 *                          <a href="#RemoteNS">remote</a> name space will usually
 *                          return the default class loader configured on the
 *                          internal {@link javax.management.remote.JMXConnector
 *                          JMXConnector} used to connect to the remote server.
 *                          When a {@link
 *                          javax.management.namespace.JMXRemoteNamespace
 *                          JMXRemoteNamespace} is used to connect to a
 *                          remote server that contains MBeans which export
 *                          custom types, the {@link
 *                          javax.management.namespace.JMXRemoteNamespace
 *                          JMXRemoteNamespace} must thus be configured with
 *                          an options map such that the underlying connector
 *                          can obtain a default class loader able
 *                          to handle those types.
 *                      </p>
 *                      <p>
 *                          Other <a href="#NamespaceTypes">types of name spaces</a>
 *                          may implement this method
 *                          as best as they can.
 *                      </p>
 *                  </li>
 *              </ul>
 *          </li>
 *          <li id="MBeanCreation">
 *              <p>MBean creation</p>
 *              <p> MBean creation through {@link
 *                  javax.management.MBeanServerConnection#createMBean
 *                  createMBean} might not be supported by all
 *                  name spaces: <a href="#LocalNS">local</a> name spaces and
 *                  <a href="#LocalNS">remote</a> name spaces will usually
 *                  support it, but <a href="#VirtualNS">virtual</a> name
 *                  spaces and <a href="#CustomNS">custom</a> name
 *                  spaces might not.
 *              </p>
 *              <p>
 *                  In that case, they will throw an {@link
 *                  java.lang.UnsupportedOperationException
 *                  UnsupportedOperationException} usually wrapped into an {@link
 *                  javax.management.MBeanRegistrationException}.
 *              </p>
 *          </li>
 *          <li id="Notifications">
 *              <p>Notifications</p>
 *              <p> Some namespaces might not support JMX Notifications. In that
 *                  case, a call to add or remove notification listener for an
 *                  MBean contained in that name space will raise a
 *                  {@link javax.management.RuntimeOperationsException
 *                  RuntimeOperationsException} wrapping an {@link
 *                  java.lang.UnsupportedOperationException
 *                  UnsupportedOperationException} exception.
 *              </p>
 *          </li>
 *      </ul>
 *
 *      <h3 id="CrossingNamespace">Crossing Several Name Spaces</h3>
 *      <p>
 *          Just as folders can contain other folders, name spaces can contain
 *          other name spaces. For instance, if an {@code MBeanServer} <i>S1</i>
 *          containing a name space {@code "bar"} is mounted in another
 *          {@code MBeanServer} <i>S2</i> with name space {@code "foo"}, then
 *          an MBean <i>M1</i> named {@code "domain:type=Thing"} in namespace
 *          {@code "bar"} will appear as {@code "foo//bar//domain:type=Thing"} in
 *          {@code MBeanServer} <i>S2</i>.
 *      </p>
 *      <p>
 *          When accessing the MBean <i>M1</i> from server <i>S2</i>, the
 *          method call will traverse in a cascade {@code MBeanServer} <i>S2</i>,
 *          then the name space handler for name space {@code "foo"}, then
 *          {@code MBeanServer} <i>S1</i>, before coming to the name space
 *          handler for name space {@code "bar"}.  Any operation invoked
 *          on the MBean from a "top-level" name space will therefore need to
 *          traverse all the name spaces along the name space path until
 *          it eventually reaches the named MBean. This means that an operation
 *          like <a href="#registerMBean">registerMBean</a> for instance,
 *          can only succeed if all the name spaces along the path support it.
 *      </p>
 *      <p>
 *          Narrowing to a nested name space works just the same as narrowing
 *          to a top level name space:
 *      <pre>
 *          final MBeanServer S2 = .... ;
 *          final MBeanServer bar =
 *                JMXNamespaces.narrowToNamespace(S2, "foo//bar");
 *          final MBeanInfo info =
 *                foo.getMBeanInfo(new ObjectName("domain:type=Thing"));
 *      </pre>
 *      </p>
 *
 *      <h3 id="OperationResult">Name Spaces And Operation Results</h3>
 *      <p>
 *          Operation results, as well as attribute values returned by an MBean
 *          contained in a name space must be interpreted in the context of that
 *          name space.<br>
 *          In other words, if an MBean in name space "foo" has an attribute of
 *          type {@code ObjectName}, then it must be assumed that the
 *          {@code ObjectName} returned by that MBean is relative to
 *          name space "foo".<br>
 *          The same rule aplies for MBean names that can be returned by
 *          operations invoked on such an MBean. If one of the MBean operations
 *          return, say, a {@code Set<ObjectName>} then those MBean names must
 *          also be assumed to be relative to name space "foo".<br>
 *      </p>
 *      <p>
 *          In the usual case, a JMX client will first
 *          <a href="#ChangeTo">narrow to a name space</a> before invoking
 *          any operation on the MBeans it contains. In that case the names
 *          returned by the MBean invoked can be directly fed back to the
 *          narrowed connection.
 *          <br>
 *          If however, the JMX client directly invoked the MBean from a higher
 *          name space, without having narrowed to that name space first, then
 *          the names that might be returned by that MBean will not be directly
 *          usable - the JMX client will need to either
 *          <a href="#ChangeTo">narrow to the name space</a> before using the
 *          returned names, or convert the names to the higher level name space
 *          context.
 *          <br>
 *          The {@link javax.management.namespace.JMXNamespaces JMXNamespaces}
 *          class provides methods that can be used to perform that conversion.
 *      </p>
 *
 *      <h3 id="NamespacesAndNotifications">Name Spaces And Notifications</h3>
 *      <p>
 *          As <a href="#WhatIs">already explained</a>, name spaces are very
 *          similar to {@code MBeanServer}s. It is thus possible to get
 *          {@link javax.management.MBeanServerNotification MBeanServerNotifications}
 *          when MBeans are added or removed within a name space, by registering
 *          with the {@link javax.management.MBeanServerDelegate
 *          MBeanServerDelegate} MBean of the corresponding name space.<br>
 *          However, it must be noted that the notifications emitted by a
 *          name space must be interpreted in the context of that name space.
 *          For instance, if an MBean {@code "domain:type=Thing"} contained in
 *          namespace "foo//bar" emits a notification, the source of the
 *          notification will be {@code "domain:type=Thing"}, not
 *          {@code "foo//bar//domain:type=Thing"}. <br>
 *          It is therefore recommended to keep track of the name space
 *          information when registering a listener with an MBean contained in
 *          a name space, especially if the same listener is used to receive
 *          notifications from different name spaces. An easy solution is to
 *          use the handback, as illustrated in the code below.
 *          <pre>
 *            final MBeanServer server = ...;
 *            final NotificationListener listener = new NotificationListener() {
 *                public void handleNotification(Notification n, Object handback) {
 *                    if (!(n instanceof MBeanServerNotification)) {
 *                        System.err.println("Error: expected MBeanServerNotification");
 *                        return;
 *                    }
 *                    final MBeanServerNotification mbsn =
 *                            (MBeanServerNotification) n;
 *
 *                    // We will pass the namespace path in the handback.
 *                    //
 *                    // The received notification must be interpreted in
 *                    // the context of its source - therefore
 *                    // mbsn.getMBeanName() does not include the name space
 *                    // path...
 *                    //
 *                    final String namespace = (String) handback;
 *                    System.out.println("Received " + mbsn.getType() +
 *                            " for MBean " + mbsn.getMBeanName() +
 *                            " from name space " + namespace);
 *                }
 *            };
 *            server.addNotificationListener(JMXNamespaces.insertPath("foo//bar",
 *                    MBeanServerDelegate.DELEGATE_NAME),listener,null,"foo//bar");
 *            server.addNotificationListener(JMXNamespaces.insertPath("foo//joe",
 *                    MBeanServerDelegate.DELEGATE_NAME),listener,null,"foo//joe");
 *          </pre>
 *      </p>
 *      <p>
 *          JMX Connectors may require some configuration in order to be able
 *          to forward notifications from MBeans located in name spaces.
 *          The RMI JMX Connector Server
 *          in the Java SE 7 platform is configured by default to internally
 *          use the new {@linkplain javax.management.event event service} on
 *          the server side.
 *          When the connector server is configured in this way, JMX clients
 *          which use the old JMX Notifications mechanism (such as clients
 *          running on prior versions of the JDK) will be able to
 *          to receive notifications from MBeans located in sub name spaces.
 *          This is because the connector server will transparently delegate
 *          their subscriptions to the underlying {@linkplain
 *          javax.management.event event service}. In summary:
 *          <ul>
 *              <li>
 *                  On the server side: When exporting an {@code MBeanServer}
 *                  through a JMX Connector, you will need to make sure that the
 *                  connector server uses the new {@linkplain javax.management.event
 *                  event service} in order to register for notifications. If the
 *                  connector server doesn't use the event service, only clients
 *                  which explicitly use the new {@linkplain javax.management.event
 *                  event service} will be able to register for notifications
 *                  with MBeans located in sub name spaces.
 *              </li>
 *              <li>
 *                  On the client side: if the JMX Connector server (on the remote
 *                  server side) was configured to internally use the new
 *                  {@linkplain javax.management.event
 *                  event service}, then clients can continue to use the old
 *                  {@code MBeanServerConnection} add / remove notification
 *                  listener methods transparently. Otherwise, only clients which
 *                  explicitly use the new {@linkplain javax.management.event
 *                  event service} will be able to receive notifications from
 *                  MBeans contained in sub name spaces.
 *              </li>
 *          </ul>
 *      </p>
 *      <p>
 *          These configuration issues apply at each node in the name space path,
 *          whenever the name space points to a remote server. The
 *          {@link javax.management.namespace.JMXRemoteNamespace
 *          JMXRemoteNamespace} can be configured in such a way that it will
 *          explicitly use an {@link javax.management.event.EventClient EventClient}
 *          when forwarding subscription to the remote side. Note that this can be
 *          unnecessary (and a waste of resources) if the underlying JMXConnector
 *          returned by the JMXConnectorFactory (client side) already uses the
 *          {@linkplain javax.management.event event service} to register for
 *          notifications with the server side.
 *      </p>
 *
 *      <h3 id="Security">Name Spaces And Access Control</h3>
 *      <p>
 *          Access to MBeans exposed through JMX namespaces is controlled by
 *          {@linkplain javax.management.namespace.JMXNamespacePermission
 *           jmx namespace permissions}. These permissions are checked by the
 *          MBeanServer in which the {@link
 *            javax.management.namespace.JMXNamespace JMXNamespace} MBean is registered.
 *          This is <a href="JMXNamespace.html#PermissionChecks">described in
 *          details</a> in the {@link
 *            javax.management.namespace.JMXNamespace JMXNamespace} class.
 *      </p>
 *      <p>
 *         To implement a "firewall-like" access control in a JMX agent you
 *         can also place an {@link
 *         javax.management.remote.MBeanServerForwarder} in the JMX Connector
 *         Server which exposes the top-level MBeanServer of your application.
 *         This {@code MBeanServerForwarder} will be able to perform
 *         authorization checks for all MBeans, including those located in
 *         sub name spaces.
 *      </p>
 *      <p>
 *         For a tighter access control we recommend using a {@link
 *         java.lang.SecurityManager security manager}.
 *      </p>
 * @since 1.7
 * <p></p>
 **/

package javax.management.namespace;

