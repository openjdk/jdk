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

import com.sun.jmx.defaults.JmxProperties;
import static com.sun.jmx.defaults.JmxProperties.JMX_INITIAL_BUILDER;
import static com.sun.jmx.defaults.JmxProperties.MBEANSERVER_LOGGER;
import com.sun.jmx.mbeanserver.GetPropertyAction;
import com.sun.jmx.mbeanserver.Util;
import java.security.AccessController;
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.management.loading.ClassLoaderRepository;


/**
 * <p>Provides MBean server references.  There are no instances of
 * this class.</p>
 *
 * <p>Since JMX 1.2 this class makes it possible to replace the default
 * MBeanServer implementation. This is done using the
 * {@link javax.management.MBeanServerBuilder} class.
 * The class of the initial MBeanServerBuilder to be
 * instantiated can be specified through the
 * <b>javax.management.builder.initial</b> system property.
 * The specified class must be a public subclass of
 * {@link javax.management.MBeanServerBuilder}, and must have a public
 * empty constructor.
 * <p>By default, if no value for that property is specified, an instance of
 * {@link
 * javax.management.MBeanServerBuilder javax.management.MBeanServerBuilder}
 * is created. Otherwise, the MBeanServerFactory attempts to load the
 * specified class using
 * {@link java.lang.Thread#getContextClassLoader()
 *   Thread.currentThread().getContextClassLoader()}, or if that is null,
 * {@link java.lang.Class#forName(java.lang.String) Class.forName()}. Then
 * it creates an initial instance of that Class using
 * {@link java.lang.Class#newInstance()}. If any checked exception
 * is raised during this process (e.g.
 * {@link java.lang.ClassNotFoundException},
 * {@link java.lang.InstantiationException}) the MBeanServerFactory
 * will propagate this exception from within a RuntimeException.</p>
 *
 * <p>The <b>javax.management.builder.initial</b> system property is
 * consulted every time a new MBeanServer needs to be created, and the
 * class pointed to by that property is loaded. If that class is different
 * from that of the current MBeanServerBuilder, then a new MBeanServerBuilder
 * is created. Otherwise, the MBeanServerFactory may create a new
 * MBeanServerBuilder or reuse the current one.</p>
 *
 * <p>If the class pointed to by the property cannot be
 * loaded, or does not correspond to a valid subclass of MBeanServerBuilder
 * then an exception is propagated, and no MBeanServer can be created until
 * the <b>javax.management.builder.initial</b> system property is reset to
 * valid value.</p>
 *
 * <p>The MBeanServerBuilder makes it possible to wrap the MBeanServers
 * returned by the default MBeanServerBuilder implementation, for the purpose
 * of e.g. adding an additional security layer.</p>
 *
 * <p id="MBeanServerName">Since version 2.0 of the JMX API, when creating
 * an MBeanServer,
 * it is possible to specify an {@linkplain #getMBeanServerName
 * MBean Server name}.
 * To create an MBean Server with a name, the MBeanServerFactory provides two
 * new methods:</p>
 * <ul><li>{@link #createNamedMBeanServer
 * createNamedMBeanServer(mbeanServerName, defaultDomain)}: creates a named
 * MBeanServer and keeps an internal reference to the created object. The
 * MBeanServer can be later retrieved using {@link #findMBeanServer
 * findMBeanServer(mbeanServerId)} or
 * {@link #findMBeanServerByName findMBeanServerByName(mbeanServerName)}, and
 * can be released through {@link
 * #releaseMBeanServer releaseMBeanServer(mbeanServer)}.</li>
 * <li>{@link #newNamedMBeanServer
 * newNamedMBeanServer(mbeanServerName, defaultDomain)}:
 * creates a named MBeanServer without keeping any internal reference to the
 * named server.</li>
 * </ul>
 * <p>The name of the MBeanServer is stored in the
 * {@linkplain MBeanServerDelegate MBean Server delegate MBean}
 * and is embedded in its {@link MBeanServerDelegate#getMBeanServerId
 * MBeanServerId} attribute.</p>
 * <p>The name of the MBeanServer is particularly useful when
 * <a href="MBeanServer.html#security">MBean permissions</a> are checked:
 * it makes it
 * possible to distinguish between an MBean named "X" in MBeanServer named
 * "M1", and another MBean of the same name "X" in another MBeanServer named
 * "M2".</p>
 * <p>When naming MBean servers it is recommended to use a name that starts
 * with a Java package name. It is also recommended that the default domain and
 * the MBeanServer name be the same.</p>
 *
 * @since 1.5
 */
public class MBeanServerFactory {

    /**
     * The <a href="#MBeanServerName">MBean Server name</a> that will be
     * checked by a <a href="MBeanServer.html#security">permission you need</a>
     * when checking access to an MBean registered in an MBeanServer for
     * which no MBeanServer name was specified.
     *
     * @since 1.7
     */
    public final static String DEFAULT_MBEANSERVER_NAME = "default";

    /*
     * There are no instances of this class so don't generate the
     * default public constructor.
     */
    private MBeanServerFactory() {

    }

    /**
     * The builder that will be used to construct MBeanServers.
     *
     **/
    private static MBeanServerBuilder builder = null;

    /**
     * Provide a new {@link javax.management.MBeanServerBuilder}.
     * @param builder The new MBeanServerBuilder that will be used to
     *        create {@link javax.management.MBeanServer}s.
     * @exception IllegalArgumentException if the given builder is null.
     *
     * @exception SecurityException if there is a SecurityManager and
     * the caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("setMBeanServerBuilder")</code>.
     *
     **/
    // public static synchronized void
    //    setMBeanServerBuilder(MBeanServerBuilder builder) {
    //    checkPermission("setMBeanServerBuilder");
    //    MBeanServerFactory.builder = builder;
    // }

    /**
     * Get the current {@link javax.management.MBeanServerBuilder}.
     *
     * @return the current {@link javax.management.MBeanServerBuilder}.
     *
     * @exception SecurityException if there is a SecurityManager and
     * the caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("getMBeanServerBuilder")</code>.
     *
     **/
    // public static synchronized MBeanServerBuilder getMBeanServerBuilder() {
    //     checkPermission("getMBeanServerBuilder");
    //     return builder;
    // }

    /**
     * Remove internal MBeanServerFactory references to a created
     * MBeanServer. This allows the garbage collector to remove the
     * MBeanServer object.
     *
     * @param mbeanServer the MBeanServer object to remove.
     *
     * @exception java.lang.IllegalArgumentException if
     * <code>mbeanServer</code> was not generated by one of the
     * <code>createMBeanServer</code> methods, or if
     * <code>releaseMBeanServer</code> was already called on it.
     *
     * @exception SecurityException if there is a SecurityManager and
     * the caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("releaseMBeanServer")</code>.
     */
    public static void releaseMBeanServer(MBeanServer mbeanServer) {
        checkPermission("releaseMBeanServer");

        removeMBeanServer(mbeanServer);
    }

    /**
     * <p>Return a new object implementing the MBeanServer interface
     * with a standard default domain name.  The default domain name
     * is used as the domain part in the ObjectName of MBeans when the
     * domain is specified by the user is null.</p>
     *
     * <p>The standard default domain name is
     * <code>DefaultDomain</code>.</p>
     *
     * <p>The MBeanServer reference is internally kept. This will
     * allow <CODE>findMBeanServer</CODE> to return a reference to
     * this MBeanServer object.</p>
     *
     * <p>This method is equivalent to <code>createMBeanServer(null)</code>.
     *
     * @return the newly created MBeanServer.
     *
     * @exception SecurityException if there is a SecurityManager and the
     * caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("createMBeanServer")</code>.
     *
     * @exception JMRuntimeException if the property
     * <code>javax.management.builder.initial</code> exists but the
     * class it names cannot be instantiated through a public
     * no-argument constructor; or if the instantiated builder returns
     * null from its {@link MBeanServerBuilder#newMBeanServerDelegate
     * newMBeanServerDelegate} or {@link
     * MBeanServerBuilder#newMBeanServer newMBeanServer} methods.
     *
     * @exception ClassCastException if the property
     * <code>javax.management.builder.initial</code> exists and can be
     * instantiated but is not assignment compatible with {@link
     * MBeanServerBuilder}.
     */
    public static MBeanServer createMBeanServer() {
        return createMBeanServer(null);
    }

    /**
     * <p>Return a new object implementing the {@link MBeanServer}
     * interface with the specified default domain name.  The given
     * domain name is used as the domain part in the ObjectName of
     * MBeans when the domain is specified by the user is null.</p>
     *
     * <p>The MBeanServer reference is internally kept. This will
     * allow <CODE>findMBeanServer</CODE> to return a reference to
     * this MBeanServer object.</p>
     *
     * @param domain the default domain name for the created
     * MBeanServer.  This is the value that will be returned by {@link
     * MBeanServer#getDefaultDomain}.
     *
     * @return the newly created MBeanServer.
     *
     * @exception SecurityException if there is a SecurityManager and
     * the caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("createMBeanServer")</code>.
     *
     * @exception JMRuntimeException if the property
     * <code>javax.management.builder.initial</code> exists but the
     * class it names cannot be instantiated through a public
     * no-argument constructor; or if the instantiated builder returns
     * null from its {@link MBeanServerBuilder#newMBeanServerDelegate
     * newMBeanServerDelegate} or {@link
     * MBeanServerBuilder#newMBeanServer newMBeanServer} methods.
     *
     * @exception ClassCastException if the property
     * <code>javax.management.builder.initial</code> exists and can be
     * instantiated but is not assignment compatible with {@link
     * MBeanServerBuilder}.
     *
     * @see #createNamedMBeanServer
     */
    public static MBeanServer createMBeanServer(String domain)  {
        return createMBeanServer(null,domain);
    }

    /**
     * <p>Return a new object implementing the {@link MBeanServer}
     * interface with the specified
     * <a href="#MBeanServerName">MBean Server name</a>
     * and default domain name. The given MBean server name
     * is used in <a href="MBeanServer.html#security">security checks</a>, and
     * can also be used to {@linkplain #findMBeanServerByName(java.lang.String)
     * find an MBeanServer by name}. The given
     * domain name is used as the domain part in the ObjectName of
     * MBeans when the domain is specified by the user is null.</p>
     *
     * <p>The MBeanServer reference is internally kept. This will
     * allow <CODE>findMBeanServer</CODE> to return a reference to
     * this MBeanServer object.</p>
     *
     * @param mbeanServerName the name for the created
     * MBeanServer.  This is the name that will be included in the
     * {@linkplain MBeanPermission permission you need} when checking
     * <a href="MBeanServer.html#security">MBean Permissions</a> for accessing
     * an MBean registered in the returned MBeanServer. The characters
     * {@code ':'} (colon), {@code ';'} (semicolon), {@code '*'} (star)
     * and  {@code '?'} are not legal.
     * It is recommended that the {@code mbeanServerName}
     * be unique in the context of a JVM, and in the form of a java package
     * identifier. If {@code mbeanServerName} is {@code null} then the created
     * MBean Server has no name - and {@value #DEFAULT_MBEANSERVER_NAME} is used.
     * Calling {@code createNamedMBeanServer(null,domain)} is equivalent
     * to calling {@link #createMBeanServer(String) createMBeanServer(domain)}.
     *
     * @param domain the default domain name for the created
     * MBeanServer.  This is the value that will be returned by {@link
     * MBeanServer#getDefaultDomain}. If a non null mbeanServerName is given,
     * it is recommended to pass the same value as default domain.
     *
     * @return the newly created MBeanServer.
     *
     * @exception SecurityException if there is a SecurityManager and
     * the caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("createMBeanServer")</code>.
     *
     * @exception JMRuntimeException if the property
     * <code>javax.management.builder.initial</code> exists but the
     * class it names cannot be instantiated through a public
     * no-argument constructor; or if the instantiated builder returns
     * null from its {@link MBeanServerBuilder#newMBeanServerDelegate
     * newMBeanServerDelegate} or {@link
     * MBeanServerBuilder#newMBeanServer newMBeanServer} methods.
     *
     * @exception ClassCastException if the property
     * <code>javax.management.builder.initial</code> exists and can be
     * instantiated but is not assignment compatible with {@link
     * MBeanServerBuilder}.
     *
     * @exception IllegalArgumentException if the specified
     * {@code mbeanServerName} is empty, or is {@code "-"}, or contains a
     * character which is not legal.
     *
     * @exception UnsupportedOperationException if the specified
     * {@code mbeanServerName} cannot be set.
     *
     * @since 1.7
     */
    public static MBeanServer createNamedMBeanServer(String mbeanServerName,
            String domain)  {
        return createMBeanServer(mbeanServerName, domain);
    }

    /**
     * <p>Return a new object implementing the MBeanServer interface
     * with a standard default domain name, without keeping an
     * internal reference to this new object.  The default domain name
     * is used as the domain part in the ObjectName of MBeans when the
     * domain is specified by the user is null.</p>
     *
     * <p>The standard default domain name is
     * <code>DefaultDomain</code>.</p>
     *
     * <p>No reference is kept. <CODE>findMBeanServer</CODE> will not
     * be able to return a reference to this MBeanServer object, but
     * the garbage collector will be able to remove the MBeanServer
     * object when it is no longer referenced.</p>
     *
     * <p>This method is equivalent to <code>newMBeanServer(null)</code>.</p>
     *
     * @return the newly created MBeanServer.
     *
     * @exception SecurityException if there is a SecurityManager and the
     * caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("newMBeanServer")</code>.
     *
     * @exception JMRuntimeException if the property
     * <code>javax.management.builder.initial</code> exists but the
     * class it names cannot be instantiated through a public
     * no-argument constructor; or if the instantiated builder returns
     * null from its {@link MBeanServerBuilder#newMBeanServerDelegate
     * newMBeanServerDelegate} or {@link
     * MBeanServerBuilder#newMBeanServer newMBeanServer} methods.
     *
     * @exception ClassCastException if the property
     * <code>javax.management.builder.initial</code> exists and can be
     * instantiated but is not assignment compatible with {@link
     * MBeanServerBuilder}.
     */
    public static MBeanServer newMBeanServer() {
        return newMBeanServer(null);
    }

    /**
     * <p>Return a new object implementing the MBeanServer interface
     * with the specified default domain name, without keeping an
     * internal reference to this new object.  The given domain name
     * is used as the domain part in the ObjectName of MBeans when the
     * domain is specified by the user is null.</p>
     *
     * <p>No reference is kept. <CODE>findMBeanServer</CODE> will not
     * be able to return a reference to this MBeanServer object, but
     * the garbage collector will be able to remove the MBeanServer
     * object when it is no longer referenced.</p>
     *
     * @param domain the default domain name for the created
     * MBeanServer.  This is the value that will be returned by {@link
     * MBeanServer#getDefaultDomain}.
     *
     * @return the newly created MBeanServer.
     *
     * @exception SecurityException if there is a SecurityManager and the
     * caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("newMBeanServer")</code>.
     *
     * @exception JMRuntimeException if the property
     * <code>javax.management.builder.initial</code> exists but the
     * class it names cannot be instantiated through a public
     * no-argument constructor; or if the instantiated builder returns
     * null from its {@link MBeanServerBuilder#newMBeanServerDelegate
     * newMBeanServerDelegate} or {@link
     * MBeanServerBuilder#newMBeanServer newMBeanServer} methods.
     *
     * @exception ClassCastException if the property
     * <code>javax.management.builder.initial</code> exists and can be
     * instantiated but is not assignment compatible with {@link
     * MBeanServerBuilder}.
     */
    public static MBeanServer newMBeanServer(String domain)  {
        return newMBeanServer(null,domain);
    }

    /**
     * <p>Return a new object implementing the MBeanServer interface
     * with the specified <a href="#MBeanServerName">MBean server name</a>
     * and default domain name, without keeping an
     * internal reference to this new object.  The given MBean server name
     * is used in <a href="MBeanServer.html#security">security checks</a>.
     * The given domain name
     * is used as the domain part in the ObjectName of MBeans when the
     * domain is specified by the user is null.</p>
     *
     * <p>No reference is kept. <CODE>findMBeanServer</CODE> and
     * <CODE>findMBeanServerByName</CODE> will not
     * be able to return a reference to this MBeanServer object, but
     * the garbage collector will be able to remove the MBeanServer
     * object when it is no longer referenced.</p>
     *
     * @param mbeanServerName the name for the created
     * MBeanServer.  This is the name that will be included in the
     * {@linkplain MBeanPermission permission you need} when checking
     * <a href="MBeanServer.html#security">MBean Permissions</a> for accessing
     * an MBean registered in the returned MBeanServer. The characters
     * {@code ':'} (colon), {@code ';'} (semicolon), {@code '*'} (star)
     * and  {@code '?'} are not legal.
     * It is recommended that the mbeanServerName
     * be unique in the context of a JVM, and in the form of a java package
     * identifier. If {@code mbeanServerName} is {@code null} then the created
     * MBean Server has no name - and {@value #DEFAULT_MBEANSERVER_NAME} is used.
     * Calling {@code newNamedMBeanServer(null,domain)} is equivalent
     * to calling {@link #newMBeanServer(String) newMBeanServer(domain)}.
     *
     * @param domain the default domain name for the created
     * MBeanServer.  This is the value that will be returned by {@link
     * MBeanServer#getDefaultDomain}.
     *
     * @return the newly created MBeanServer.
     *
     * @exception SecurityException if there is a SecurityManager and the
     * caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("newMBeanServer")</code>.
     *
     * @exception JMRuntimeException if the property
     * <code>javax.management.builder.initial</code> exists but the
     * class it names cannot be instantiated through a public
     * no-argument constructor; or if the instantiated builder returns
     * null from its {@link MBeanServerBuilder#newMBeanServerDelegate
     * newMBeanServerDelegate} or {@link
     * MBeanServerBuilder#newMBeanServer newMBeanServer} methods.
     *
     * @exception ClassCastException if the property
     * <code>javax.management.builder.initial</code> exists and can be
     * instantiated but is not assignment compatible with {@link
     * MBeanServerBuilder}.
     *
     * @exception IllegalArgumentException if the specified
     * {@code mbeanServerName} is empty, or is {@code "-"},
     * or contains a character which is not legal.
     *
     * @exception UnsupportedOperationException if the specified
     * {@code mbeanServerName} cannot be set.
     *
     * @since 1.7
     */
    public static MBeanServer newNamedMBeanServer(String mbeanServerName,
            String domain)  {
        return newMBeanServer(mbeanServerName, domain);
    }

    private static MBeanServer createMBeanServer(String mbeanServerName,
            String domain)  {
        checkPermission("createMBeanServer");

        final MBeanServer mBeanServer =
                newMBeanServer(mbeanServerName,domain);
        addMBeanServer(mBeanServer);
        return mBeanServer;
    }

    private static MBeanServer newMBeanServer(String mbeanServerName,
            String domain) {
        checkPermission("newMBeanServer");

        // Get the builder. Creates a new one if necessary.
        //
        final MBeanServerBuilder mbsBuilder = getNewMBeanServerBuilder();
        // Returned value cannot be null.  NullPointerException if violated.

        synchronized(mbsBuilder) {
            final MBeanServerDelegate delegate  =
                    mbsBuilder.newMBeanServerDelegate();
            if (delegate == null) {
                final String msg =
                        "MBeanServerBuilder.newMBeanServerDelegate() " +
                        "returned null";
                throw new JMRuntimeException(msg);
            }

            // Sets the name on the delegate. For complex backward
            // compatibility reasons it is not possible to give the
            // name to the MBeanServerDelegate constructor.
            //
            // The method setMBeanServerName() will call getMBeanServerId()
            // to check that the name is accurately set in the MBeanServerId.
            // If not (which could happen if a custom MBeanServerDelegate
            // implementation overrides getMBeanServerId() and was not updated
            // with respect to JMX 2.0 spec, this method will throw an
            // IllegalStateException...
            //
            if (!Util.isMBeanServerNameUndefined(mbeanServerName)) {
                delegate.setMBeanServerName(mbeanServerName);
            }

            final MBeanServer mbeanServer =
                    mbsBuilder.newMBeanServer(domain,null,delegate);
            if (mbeanServer == null) {
                final String msg =
                        "MBeanServerBuilder.newMBeanServer() returned null";
                throw new JMRuntimeException(msg);
            }

            // double check that the MBeanServer name is correctly set.
            // "*" might mean that the caller doesn't have the permission
            // to see the MBeanServer name.
            //
            final String mbsName = Util.getMBeanServerSecurityName(mbeanServer);
            if (!mbsName.equals(Util.checkServerName(mbeanServerName))
                && !mbsName.equals("*")) {
                throw new UnsupportedOperationException(
                        "can't create MBeanServer with name \""+
                        mbeanServerName+"\" using "+
                        builder.getClass().getName());
            }

            return mbeanServer;
        }
    }

    /**
     * <p>Return a list of registered MBeanServer objects.  A
     * registered MBeanServer object is one that was created by one of
     * the <code>createMBeanServer</code> methods and not subsequently
     * released with <code>releaseMBeanServer</code>.</p>
     *
     * @param agentId The agent identifier of the MBeanServer to
     * retrieve.  If this parameter is null, all registered
     * MBeanServers in this JVM are returned.  Otherwise, only
     * MBeanServers whose id is equal to <code>agentId</code> are
     * returned.  The id of an MBeanServer is the
     * <code>MBeanServerId</code> attribute of its delegate MBean.
     *
     * @return A list of MBeanServer objects.
     *
     * @exception SecurityException if there is a SecurityManager and the
     * caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("findMBeanServer")</code>.
     */
    public synchronized static
            ArrayList<MBeanServer> findMBeanServer(String agentId) {

        checkPermission("findMBeanServer");

        if (agentId == null)
            return new ArrayList<MBeanServer>(mBeanServerList);

        ArrayList<MBeanServer> result = new ArrayList<MBeanServer>();
        for (MBeanServer mbs : mBeanServerList) {
            String name = mBeanServerId(mbs);
            if (agentId.equals(name))
                result.add(mbs);
        }
        return result;
    }

    /**
     * <p>Returns a list of registered MBeanServer objects with the given name.  A
     * registered MBeanServer object is one that was created by one of
     * the <code>createMBeanServer</code> or <code>createNamedMBeanServer</code>
     * methods and not subsequently released with <code>releaseMBeanServer</code>.</p>
     * <p>See the section about <a href="#MBeanServerName">MBean Server names</a>
     * above.</p>
     *
     * @param mbeanServerName The name of the MBeanServer to
     * retrieve.  If this parameter is null, all registered MBeanServers
     * in this JVM are returned.
     * Otherwise, only those MBeanServers that have a name
     * matching <code>mbeanServerName</code> are returned: this
     * parameter can be a pattern, where {@code '*'} matches any
     * sequence of characters and {@code '?'} matches any character.<br>
     * The name of an MBeanServer, if specified, is embedded in the
     * <code>MBeanServerId</code> attribute of its delegate MBean:
     * this method will parse the <code>MBeanServerId</code> to get the
     * MBeanServer name. If this parameter is equal to {@code "*"} then
     * all registered MBeanServers in this JVM are returned, whether they have
     * a name or not: {@code findMBeanServerByName(null)},
     * {@code findMBeanServerByName("*")} and {@code findMBeanServer(null)},
     * are equivalent. It is also possible to get all MBeanServers for which
     * no name was specified by calling <code>findMBeanServerByName({@value
     * #DEFAULT_MBEANSERVER_NAME})</code>.
     *
     * @return A list of MBeanServer objects.
     *
     * @exception SecurityException if there is a SecurityManager and the
     * caller's permissions do not include or imply <code>{@link
     * MBeanServerPermission}("findMBeanServer")</code>.
     *
     * @see #getMBeanServerName(MBeanServer)
     * @since 1.7
     */
    public synchronized static
            List<MBeanServer> findMBeanServerByName(String mbeanServerName) {

        checkPermission("findMBeanServer");

        if (mbeanServerName==null || "*".equals(mbeanServerName))
            return new ArrayList<MBeanServer>(mBeanServerList);

        // noname=true iff we are looking for MBeanServers for which no name
        // were specified.
        ArrayList<MBeanServer> result = new ArrayList<MBeanServer>();
        for (MBeanServer mbs : mBeanServerList) {
            final String name = Util.getMBeanServerSecurityName(mbs);
            if (Util.wildmatch(name, mbeanServerName)) result.add(mbs);
        }
        return result;
    }

    /**
     * Returns the name of the MBeanServer embedded in the MBeanServerId of
     * the given {@code server}. If the given MBeanServerId doesn't contain
     * any name, {@value #DEFAULT_MBEANSERVER_NAME} is returned.
     * The MBeanServerId is expected to be of the form:
     * {@code *[;mbeanServerName=<mbeanServerName>[;*]]}
     * <br>where {@code *} denotes any sequence of characters, and {@code [ ]}
     * indicate optional parts.
     * </p>
     * <p>For instance, if an MBeanServer is created using {@link
     * #createNamedMBeanServer(java.lang.String, java.lang.String)
     * server =
     * MBeanServerFactory.createNamedMBeanServer("com.mycompany.myapp.server1",
     * null)} then {@code MBeanServerFactory.getMBeanServerName(server)}
     * will return {@code "com.mycompany.myapp.server1"} and
     * <code>server.getAttribute({@link
     * javax.management.MBeanServerDelegate#DELEGATE_NAME
     * MBeanServerDelegate.DELEGATE_NAME}, "MBeanServerId")</code> will return
     * something like
     * {@code "myhost_1213353064145;mbeanServerName=com.mycompany.myapp.server1"}.
     * </p>
     * <p>See the section about <a href="#MBeanServerName">MBean Server names</a>
     * above.</p>
     * @param  server A named (or unnamed) MBeanServer.
     * @return the name of the MBeanServer if found, or
     *         {@value #DEFAULT_MBEANSERVER_NAME} if no name is
     *         present in its MBeanServerId, or "*" if its
     *         MBeanServerId couldn't be obtained. Returning "*" means that
     *         only {@link MBeanPermission}s that allow all MBean Server names
     *         will apply to this MBean Server.
     * @see MBeanServerDelegate
     * @since 1.7
     */
    public static String getMBeanServerName(MBeanServer server) {
        return Util.getMBeanServerSecurityName(server);
    }

    /**
     * Return the ClassLoaderRepository used by the given MBeanServer.
     * This method is equivalent to {@link
     * MBeanServer#getClassLoaderRepository() server.getClassLoaderRepository()}.
     * @param server The MBeanServer under examination. Since JMX 1.2,
     * if <code>server</code> is <code>null</code>, the result is a
     * {@link NullPointerException}.  This behavior differs from what
     * was implemented in JMX 1.1 - where the possibility to use
     * <code>null</code> was deprecated.
     * @return The Class Loader Repository used by the given MBeanServer.
     * @exception SecurityException if there is a SecurityManager and
     * the caller's permissions do not include or imply <code>{@link
     * MBeanPermission}("getClassLoaderRepository")</code>.
     *
     * @exception NullPointerException if <code>server</code> is null.
     *
     **/
    public static ClassLoaderRepository getClassLoaderRepository(
            MBeanServer server) {
        return server.getClassLoaderRepository();
    }

    private static String mBeanServerId(MBeanServer mbs) {
        try {
            return (String) mbs.getAttribute(MBeanServerDelegate.DELEGATE_NAME,
                    "MBeanServerId");
        } catch (JMException e) {
            JmxProperties.MISC_LOGGER.finest(
                    "Ignoring exception while getting MBeanServerId: "+e);
            return null;
        }
    }

    private static void checkPermission(String action)
    throws SecurityException {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            Permission perm = new MBeanServerPermission(action);
            sm.checkPermission(perm);
        }
    }

    private static synchronized void addMBeanServer(MBeanServer mbs) {
        mBeanServerList.add(mbs);
    }

    private static synchronized void removeMBeanServer(MBeanServer mbs) {
        boolean removed = mBeanServerList.remove(mbs);
        if (!removed) {
            MBEANSERVER_LOGGER.logp(Level.FINER,
                    MBeanServerFactory.class.getName(),
                    "removeMBeanServer(MBeanServer)",
                    "MBeanServer was not in list!");
            throw new IllegalArgumentException("MBeanServer was not in list!");
        }
    }

    private static final ArrayList<MBeanServer> mBeanServerList =
            new ArrayList<MBeanServer>();

    /**
     * Load the builder class through the context class loader.
     * @param builderClassName The name of the builder class.
     **/
    private static Class<?> loadBuilderClass(String builderClassName)
    throws ClassNotFoundException {
        final ClassLoader loader =
                Thread.currentThread().getContextClassLoader();

        if (loader != null) {
            // Try with context class loader
            return loader.loadClass(builderClassName);
        }

        // No context class loader? Try with Class.forName()
        return Class.forName(builderClassName);
    }

    /**
     * Creates the initial builder according to the
     * javax.management.builder.initial System property - if specified.
     * If any checked exception needs to be thrown, it is embedded in
     * a JMRuntimeException.
     **/
    private static MBeanServerBuilder newBuilder(Class<?> builderClass) {
        try {
            final Object abuilder = builderClass.newInstance();
            return (MBeanServerBuilder)abuilder;
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            final String msg =
                    "Failed to instantiate a MBeanServerBuilder from " +
                    builderClass + ": " + x;
            throw new JMRuntimeException(msg, x);
        }
    }

    /**
     * Instantiate a new builder according to the
     * javax.management.builder.initial System property - if needed.
     **/
    private static synchronized void checkMBeanServerBuilder() {
        try {
            GetPropertyAction act =
                    new GetPropertyAction(JMX_INITIAL_BUILDER);
            String builderClassName = AccessController.doPrivileged(act);

            try {
                final Class<?> newBuilderClass;
                if (builderClassName == null || builderClassName.length() == 0)
                    newBuilderClass = MBeanServerBuilder.class;
                else
                    newBuilderClass = loadBuilderClass(builderClassName);

                // Check whether a new builder needs to be created
                if (builder != null) {
                    final Class<?> builderClass = builder.getClass();
                    if (newBuilderClass == builderClass)
                        return; // no need to create a new builder...
                }

                // Create a new builder
                builder = newBuilder(newBuilderClass);
            } catch (ClassNotFoundException x) {
                final String msg =
                        "Failed to load MBeanServerBuilder class " +
                        builderClassName + ": " + x;
                throw new JMRuntimeException(msg, x);
            }
        } catch (RuntimeException x) {
            if (MBEANSERVER_LOGGER.isLoggable(Level.FINEST)) {
                StringBuilder strb = new StringBuilder()
                .append("Failed to instantiate MBeanServerBuilder: ").append(x)
                .append("\n\t\tCheck the value of the ")
                .append(JMX_INITIAL_BUILDER).append(" property.");
                MBEANSERVER_LOGGER.logp(Level.FINEST,
                        MBeanServerFactory.class.getName(),
                        "checkMBeanServerBuilder",
                        strb.toString());
            }
            throw x;
        }
    }

    /**
     * Get the current {@link javax.management.MBeanServerBuilder},
     * as specified by the current value of the
     * javax.management.builder.initial property.
     *
     * This method consults the property and instantiates a new builder
     * if needed.
     *
     * @return the new current {@link javax.management.MBeanServerBuilder}.
     *
     * @exception SecurityException if there is a SecurityManager and
     * the caller's permissions do not make it possible to instantiate
     * a new builder.
     * @exception JMRuntimeException if the builder instantiation
     *   fails with a checked exception -
     *   {@link java.lang.ClassNotFoundException} etc...
     *
     **/
    private static synchronized MBeanServerBuilder getNewMBeanServerBuilder() {
        checkMBeanServerBuilder();
        return builder;
    }

}
