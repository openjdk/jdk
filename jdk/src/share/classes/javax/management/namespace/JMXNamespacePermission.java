/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import javax.management.*;
import com.sun.jmx.mbeanserver.Util;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.Permission;

/**
 * <p>A permission controlling access to MBeans located in namespaces.
 * If a security manager has been set using {@link
 * System#setSecurityManager}, most operations on an MBean mounted in a
 * namespace require that the caller's permissions imply a
 * JMXNamespacePermission appropriate for the operation.
 * This is described in detail in the
 * documentation for the
 * <a href="JMXNamespace.html#PermissionChecks">JMXNamespace</a>
 * class.</p>
 *
 * <p>As with other {@link Permission} objects,
 * a JMXNamespacePermission can represent either a permission that
 * you <em>have</em> or a permission that you <em>need</em>.
 * When a sensitive operation is being checked for permission,
 * a JMXNamespacePermission is constructed
 * representing the permission you need.  The operation is only
 * allowed if the permissions you have {@linkplain #implies imply} the
 * permission you need.</p>
 *
 * <p>A JMXNamespacePermission contains four items of information:</p>
 *
 * <ul>
 *
 * <li id="Action"><p>The <em>action</em>.</p>
 * <p>For a permission you need,
 * this is one of the actions in the list <a
 * href="#action-list">below</a>.  For a permission you have, this is
 * a comma-separated list of those actions, or <code>*</code>,
 * representing all actions.</p>
 *
 * <p>The action is returned by {@link #getActions()}.</p>
 *
 * <li id="MBeanServerName"><p>The <em>MBean Server name</em>.</p>
 *
 * <p>For a permission you need, this is the {@linkplain
 * javax.management.MBeanServerFactory#getMBeanServerName
 * name of the MBeanServer}
 * from which the <a href="#MBeanName">MBean</a> is accessed.</p>
 *
 * <p>For a permission you have, this is either the  {@linkplain
 * javax.management.MBeanServerFactory#getMBeanServerName
 * name of the MBeanServer} from which the <a href="#MBeanName">MBean</a>
 * for which you have this permission is accessed,
 * or a pattern against which that MBean Server name will be matched.<br>
 * An {@code mbeanServername} pattern can also be empty, or the single
 * character {@code "*"}, both of which match any {@code MBeanServer} name.
 * The string {@code "-"} doesn't match any MBeanServer name.
 * </p>
 *
 * <p>Example:</p>
 * <pre>
 *   // grant permission to invoke the operation "stop" on any MBean
 *   // whose name matches "a//*&#42;//*:type=JMXConnectorServer" when
 *   // accessed from any MBeanServer whose name matches myapp.*"
 *   permission javax.management.namespace.JMXNamespacePermission "myapp.*::stop[a//*&#42;//*:type=JMXConnectorServer]", "invoke";
 * </pre>
 *
 * <li id="Member"><p>The <em>member</em>.</p>
 *
 * <p>For a permission you need, this is the name of the attribute or
 * operation you are accessing.  For operations that do not reference
 * an attribute or operation, the member is null.</p>
 *
 * <p>For a permission you have, this is either the name of an attribute
 * or operation you can access, or it is empty or the single character
 * "<code>*</code>", both of which grant access to any member.</p>
 *
 * <p>There is a special case for actions {@code registerMBean} and
 *    {@code instantiate}, where for a permission you need, {@code member}
 *    indicates the name of the class for which you are trying
 *    to create, instantiate, or register an MBean instance. For a
 *    permission you have, it is a pattern that will be matched against
 *    the full class name of the MBean being created, instantiated, or
 *    registered.
 * </p>
 *
 *
 * <li id="MBeanName"><p>The <em>object name</em>.</p>
 *
 * <p>For a permission you need, this is the {@link ObjectName} of the
 * MBean you are accessing. It is of the form {@code <namespace>//<mbean name>}
 * where {@code <namespace>} is the name of the name space for which the
 * permission is checked, and {@code <mbean name>} is the name of the MBean
 * within that namespace.
 * <br>
 * For operations that do not reference a
 * single MBean, the <em>object name</em> is null.  It is never an object
 * name pattern.
 * </p>
 *
 * <p>For a permission you have, this is the {@link ObjectName} of the
 * MBean or MBeans you can access. It is of the form
 * {@code <namespace>//<mbean name>}
 * where {@code <namespace>} is the name of the name space for which the
 * permission is checked, and
 * {@code <mbean name>} is the name of the MBean
 * within that namespace. Both {@code <namespace>} and {@code <mbean name>}
 * can be patterns. The <em>object name</em>
 * may also be empty, which grants access to all MBeans whatever their
 * name and namespace.
 * When included in a namespace path the special path element
 * <code>**</code> matches any number of sub namespaces
 * recursively, but only if used as a complete namespace path element,
 * as in <code>*&#42;//b//c//D:k=v</code> or <code>a//*&#42;//c//D:k=v</code>
 * - see <a href="#metawildcard">below</a>.
 * </p>
 *
 *
 * </ul>
 *
 * <p>If you have a JMXNamespacePermission, it allows operations only
 * if all four of the items match.</p>
 *
 * <p>The <a href="#MBeanServerName">MBeanServer name</a>,
 * <a href="#Member">member</a>, and <a href="#MBeanName">object name</a>
 * can be written together
 * as a single string, which is the <em>name</em> of this permission.
 * The name of the permission is the string returned by {@link
 * java.security.Permission#getName() getName()}.
 * The format of the string is:</p>
 *
 * <blockquote>
 * {@code <mbean server name>::<member>[<namespace>//<mbean name>]}
 * </blockquote>
 *
 * <p>
 * The {@code <mbean server name>} is optional. If omitted, {@code "*"} is
 * assumed, and these three permission names
 * are thus equivalent:
 * </p>
 * <blockquote>
 * {@code *::<member>[<namespace>//<mbean name>]}<br>
 * {@code ::<member>[<namespace>//<mbean name>]}<br>
 * {@code <member>[<namespace>//<mbean name>]}<br>
 * </blockquote>
 * <p>
 *    The {@code <namespace>//<mbean name>} string can be in the form
 *    of a traditional ObjectName
 *    pattern - meaning that <code>?</code> will match any single
 *    character, and <code>*</code> will match any sequence of characters,
 *    except {@value
 *    javax.management.namespace.JMXNamespaces#NAMESPACE_SEPARATOR}
 *    In addition, when included in a namespace path the special
 *    path element <code>**</code> matches any number of sub namespaces
 *    recursively.
 *    A {@code <namespace>//<mbean name>} string of the form
 *    <code>*&#42;//*:*</code> thus means that the permission is
 *    granted for all MBeans in all namespaces, recursively (see
 *    <a href="#metawildcard">below</a> for more details.
 * </p>
 * <p>Namespace permission checking may be tricky to configure, depending
 *    on whether the namespaces crossed to reach the MBean are local or
 *    remote.<br>
 *    For instance, let <code>a//b//D:k=v</code> be an MBean exposing an
 *    attribute <code>Foo</code>.
 *    If namespace <code>a</code> is a plain JMXNamespace pointing to
 *    a local MBeanServer in the same JVM, then the permissions you need
 *    to get the attribute <code>Foo</code> will be:
 * </p>
 * <pre>
 *    // granting permission to access attribute 'Foo' of MBean a//b//D:k=v
 *    // from MBeanServer named 'srv1'
 *    // This permission will be checked by the MBeanServer that contains 'a'.
 *    srv1::Foo[a//b//D:k=v]
 *
 *    // Since a is local, you also need the following additional permission,
 *    // which will be checked by the MBeanServer 'srv2' that contains 'b':
 *    //
 *    // granting permission to access attribute 'Foo' of MBean b//D:k=v from
 *    // 'srv2'
 *    srv2::Foo[b//D:k=v]
 * </pre>
 * <p>On the other hand, if namespace <code>a</code> is a JMXRemoteNamespace
 *    pointing to an MBeanServer in a remote JVM, then the only permission you
 *    need to get the attribute <code>Foo</code> will be:
 * </p>
 * <pre>
 *    // granting permission to access attribute 'Foo' of MBean a//b//D:k=v
 *    // from 'srv1'
 *    srv1::Foo[a//b//D:k=v]
 * </pre>
 * <p>The namespace <code>b</code> resides in the remote JVM, and
 *    therefore the permissions concerning access to MBeans from
 *    namespace 'b' will only be checked in the remote JVM, if that JVM is
 *    configured to do so.
 * </p>
 *
 * <p>The {@code <mbean name>} is written using the usual syntax for {@link
 * ObjectName}.  It may contain any legal characters, including
 * <code>]</code>.  It is terminated by a <code>]</code> character
 * that is the last character in the string.
 * </p>
 * <p>Below are some examples of permission names:</p>
 * <pre>
 *    // allows access to Foo in 'a//b//*:*' from any MBeanServer in the JVM.
 *    Foo[a//b//*:*]
 *
 *    // allows access to Foo in all subnamespaces of 'a//b', but only for
 *    // MBeanServers whose name matches 'myapp.*'
 *    myapp.*::Foo[a//b//*&#42;//*:*]
 *
 *    // allows access to Foo from all namespaces in the MBeanServer named
 *    // 'myapp.srv1' - but not recursively.
 *    myapp.srv1::Foo[&#42;//*:*]
 * </pre>
 * <p>For instance, the first two permissions listed above
 *    will let through {@code getAttribute("a//b//D:k=v","Foo");} in
 *    all MBeanServers, but will block access to
 *    {@code getAttribute("a//b//c//D:k=v","Foo");} in MBeanServers whose
 *    name do not start with {@code "myapp."}.
 * </p>
 * <p><a name="metawildcard">Depending on how your namespace hierarchy
 *    is defined some of these wildcard permission names can be useful</a>:</p>
 * <pre>
 *    // allows access to Foo in all namespaces, recursively.
 *    //
 *    *::Foo[*&#42;//*:*]
 *
 *    // This permission name is the equivalent to the permission names above:
 *    // Foo[*&#42;//*:*] and Foo[] are equivalent.
 *    //
 *    Foo[]
 *
 *    // This permission name is the equivalent to the two permission names
 *    // above:
 *    // Foo[*&#42;//*:*], Foo[], Foo are equivalent.
 *    //
 *    Foo
 *
 *    // allows access to Foo from all namespaces - but not recursively.
 *    // This wildcard permission complements the previous one: it allows
 *    // access to 'Foo' from an MBean directly registered in any local namespace.
 *    //
 *    Foo[&#42;//*:*]
 *
 * </pre>
 * <p><b>Note on wildcards:</b> In an object name pattern, a path element
 *    of exactly <code>**</code> corresponds to a meta
 *    wildcard that will match any number of sub namespaces. Hence:</p>
 * <ul>
 * <table border="1">
 * <thead><th>pattern</th><th>matches</th><th>doesn't match</th></thead>
 * <tbody>
 * <tr><td><code>*&#42;//D:k=v</code></td>
 *     <td><code>a//D:k=v</code><br>
 *         <code>a//b//D:k=v</code><br>
 *         <code>a//b//c//D:k=v</code></td>
 *     <td><code>D:k=v</code></td></tr>
 * <tr><td><code>a//*&#42;//D:k=v</code></td>
 *     <td><code>a//b//D:k=v</code><br>
 *         <code>a//b//c//D:k=v</code></td>
 *     <td><code>b//b//c//D:k=v</code><br>
 *         <code>a//D:k=v</code><br>
 *         <code>D:k=v</code></td></tr>
 * <tr><td><code>a//*&#42;//e//D:k=v</code></td>
 *     <td><code>a//b//e//D:k=v</code><br>
 *         <code>a//b//c//e//D:k=v</code></td>
 *     <td><code>a//b//c//c//D:k=v</code><br>
 *         <code>b//b//c//e//D:k=v</code><br>
 *         <code>a//e//D:k=v</code><br>
 *         <code>e//D:k=v</code></td></tr>
 * <tr><td><code>a//b*&#42;//e//D:k=v</code></td>
 *      <td><code>a//b//e//D:k=v</code></td>
 *      <td><code>a//b//c//e//D:k=v</code><br>
 *          because in that case <code>b*&#42;</code><br>
 *         is not a meta-wildcard - and <code>b**</code><br>
 *         is thus equivalent to <code>b*</code>.</td></tr>
 * </tbody>
 * </table>
 *</ul>
 *
 * <p>If {@code <mbean server name>::} is omitted, then one of
 * <code>member</code> or <code>object name</code> may be omitted.
 * If the <code>object name</code> is omitted,
 * the <code>[]</code> may be too (but does not have to be).  It is
 * not legal to omit all items, that is to have a <em>name</em>
 * which is the empty string.</p>
 * <p>If {@code <mbean server name>} is present, it <b>must be followed</b> by
 *    the {@code "::"} separator - otherwise it will be interpreted as
 *    a {@code member name}.
 * </p>
 *
 * <p>
 * One or more of the <a href="#MBeanServerName">MBean Server name</a>,
 * <a href="#Member">member</a>
 * or <a href="#MBeanName">object name</a> may be the character "<code>-</code>",
 * which is equivalent to a null value.  A null value is implied by
 * any value (including another null value) but does not imply any
 * other value.
 * </p>
 *
 * <p><a name="action-list">The possible actions are these:</a></p>
 *
 * <ul>
 * <li>addNotificationListener</li>
 * <li>getAttribute</li>
 * <li>getClassLoader</li>
 * <li>getClassLoaderFor</li>
 * <li>getClassLoaderRepository</li>
 * <li>getMBeanInfo</li>
 * <li>getObjectInstance</li>
 * <li>instantiate</li>
 * <li>invoke</li>
 * <li>isInstanceOf</li>
 * <li>queryMBeans</li>
 * <li>queryNames</li>
 * <li>registerMBean</li>
 * <li>removeNotificationListener</li>
 * <li>setAttribute</li>
 * <li>unregisterMBean</li>
 * </ul>
 *
 * <p>In a comma-separated list of actions, spaces are allowed before
 * and after each action.</p>
 *
 * @since 1.7
 */
public class JMXNamespacePermission extends Permission {

    private static final long serialVersionUID = -2416928705275160661L;

    private static final String WILDPATH = "**" +
                JMXNamespaces.NAMESPACE_SEPARATOR + "*";

    /**
     * Actions list.
     */
    private static final int AddNotificationListener    = 0x00001;
    private static final int GetAttribute               = 0x00002;
    private static final int GetClassLoader             = 0x00004;
    private static final int GetClassLoaderFor          = 0x00008;
    private static final int GetClassLoaderRepository   = 0x00010;
    // No GetDomains because it is not possible to route a call to
    // getDomains() on a NamespaceInterceptor - getDomains() doesn't
    // have any ObjectName.
    // private static final int GetDomains                 = 0x00020;
    private static final int GetMBeanInfo               = 0x00040;
    private static final int GetObjectInstance          = 0x00080;
    private static final int Instantiate                = 0x00100;
    private static final int Invoke                     = 0x00200;
    private static final int IsInstanceOf               = 0x00400;
    private static final int QueryMBeans                = 0x00800;
    private static final int QueryNames                 = 0x01000;
    private static final int RegisterMBean              = 0x02000;
    private static final int RemoveNotificationListener = 0x04000;
    private static final int SetAttribute               = 0x08000;
    private static final int UnregisterMBean            = 0x10000;

    /**
     * No actions.
     */
    private static final int NONE = 0x00000;

    /**
     * All actions.
     */
    // No GetDomains because it is not possible to route a call to
    // getDomains() on a NamespaceInterceptor - getDomains() doesn't
    // have any ObjectName.
    //
    private static final int ALL =
        AddNotificationListener    |
        GetAttribute               |
        GetClassLoader             |
        GetClassLoaderFor          |
        GetClassLoaderRepository   |
        GetMBeanInfo               |
        GetObjectInstance          |
        Instantiate                |
        Invoke                     |
        IsInstanceOf               |
        QueryMBeans                |
        QueryNames                 |
        RegisterMBean              |
        RemoveNotificationListener |
        SetAttribute               |
        UnregisterMBean;

    /**
     * The actions string.
     */
    private String actions;

    /**
     * The actions mask.
     */
    private transient int mask;

    /**
     * The name of the MBeanServer in which this permission is checked, or
     * granted.  If null, is implied by any MBean server name
     * but does not imply any non-null MBean server name.
     */
    private transient String mbeanServerName;

    /**
     * The member that must match.  If null, is implied by any member
     * but does not imply any non-null member.
     */
    private transient String member;

    /**
     * The objectName that must match.  If null, is implied by any
     * objectName but does not imply any non-null objectName.
     */
    private transient ObjectName objectName;

    /**
     * If objectName is missing from name, then allnames will be
     * set to true.
     */
    private transient boolean  allnames = false;

    /**
     * Parse <code>actions</code> parameter.
     */
    private void parseActions() {

        int amask;

        if (actions == null)
            throw new IllegalArgumentException("JMXNamespaceAccessPermission: " +
                                               "actions can't be null");
        if (actions.equals(""))
            throw new IllegalArgumentException("JMXNamespaceAccessPermission: " +
                                               "actions can't be empty");

        amask = getMask(actions);

        if ((amask & ALL) != amask)
            throw new IllegalArgumentException("Invalid actions mask");
        if (amask == NONE)
            throw new IllegalArgumentException("Invalid actions mask");
        this.mask = amask;
    }

    /**
     * Parse <code>name</code> parameter.
     */
    private void parseName() {
        String name = getName();

        if (name == null)
            throw new IllegalArgumentException("JMXNamespaceAccessPermission name " +
                                               "cannot be null");

        if (name.equals(""))
            throw new IllegalArgumentException("JMXNamespaceAccessPermission name " +
                                               "cannot be empty");
        final int sepIndex = name.indexOf("::");
        if (sepIndex < 0) {
            setMBeanServerName("*");
        } else {
            setMBeanServerName(name.substring(0,sepIndex));
        }

        /* The name looks like "mbeanServerName::member[objectname]".
           We subtract elements from the right as we parse, so after
           parsing the objectname we have "class#member" and after parsing the
           member we have "class".  Each element is optional.  */

        // Parse ObjectName

        final int start = (sepIndex<0)?0:sepIndex+2;
        int openingBracket = name.indexOf("[",start);
        if (openingBracket == -1) {
            // If "[on]" missing then ObjectName("*:*")
            //
            objectName = null;
            allnames = true;
            openingBracket=name.length();
        } else {
            if (!name.endsWith("]")) {
                throw new IllegalArgumentException("JMXNamespaceAccessPermission: " +
                                                   "The ObjectName in the " +
                                                   "target name must be " +
                                                   "included in square " +
                                                   "brackets");
            } else {
                // Create ObjectName
                //
                String on = name.substring(openingBracket + 1,
                                           name.length() - 1);
                try {
                    // If "[]" then allnames are implied
                    //
                    final ObjectName target;
                    final boolean    all;
                    if (on.equals("")) {
                        target = null;
                        all = true;
                    } else if (on.equals("-")) {
                        target = null;
                        all = false;
                    } else {
                        target = new ObjectName(on);
                        all    = false;
                    }
                    setObjectName(target,all);
                } catch (MalformedObjectNameException e) {
                    throw new IllegalArgumentException(
                            "JMXNamespaceAccessPermission: " +
                            "The target name does " +
                            "not specify a valid " +
                            "ObjectName", e);
                }
            }
        }

        final String memberName = name.substring(start,openingBracket);
        setMember(memberName);
    }

    private void setObjectName(ObjectName target, boolean all) {
        if (target != null &&
            !Util.wildpathmatch(target.getDomain(), WILDPATH)) {
            throw new IllegalArgumentException(
                    "The target name does not contain " +
                    "any namespace: "+String.valueOf(target));
        } else if (target != null) {
            final String domain = target.getDomain();
            final int seplen = JMXNamespaces.NAMESPACE_SEPARATOR.length();
            final int sepc = domain.indexOf(JMXNamespaces.NAMESPACE_SEPARATOR);
            if (sepc < 0 || (sepc+seplen)==domain.length()) {
                throw new IllegalArgumentException(String.valueOf(target)+
                        ": no namespace in domain");
            }
        }
        objectName = target;
        allnames = all;
    }

    /**
     * Assign fields based on className, member, and objectName
     * parameters.
     */
//    private void initName(String namespaceName, String member,
//                          ObjectName objectName, boolean allnames) {
//        setNamespace(namespaceName);
    private void initName(String mbeanServerName, String member,
                          ObjectName mbeanName, boolean all) {
        setMBeanServerName(mbeanServerName);
        setMember(member);
        setObjectName(mbeanName, all);
    }

    private void setMBeanServerName(String mbeanServerName) {
        if (mbeanServerName == null || mbeanServerName.equals("-")) {
            this.mbeanServerName = null;
        } else if (mbeanServerName.equals("")) {
            this.mbeanServerName = "*";
        } else {
            this.mbeanServerName = mbeanServerName;
        }
    }

    private void setMember(String member) {
        if (member == null || member.equals("-"))
            this.member = null;
        else if (member.equals(""))
            this.member = "*";
        else
            this.member = member;
    }

    /**
     * <p>Create a new JMXNamespacePermission object with the
     * specified target name and actions.</p>
     *
     * <p>The target name is of the form
     * "<code>mbeanServerName::member[objectName]</code>" where each part is
     * optional. This target name must not be empty or null.
     * If <code>objectName</code> is present, it is of
     * the form <code>namespace//MBeanName</code>.
     * </p>
     * <p>
     * For a permission you need, {@code mbeanServerName} is the
     * <a href="#MBeanServerName">name of the MBeanServer</a> from
     * which {@code objectName} is being accessed.
     * </p>
     * <p>
     * For a permission you have, {@code mbeanServerName} is the
     * <a href="#MBeanServerName">name of the MBeanServer</a> from
     * which access to {@code objectName} is granted.
     * It can also be a pattern, and if omitted, {@code "*"} is assumed,
     * meaning that access to {@code objectName} is granted in all
     * MBean servers in the JVM.
     * </p>
     *
     * <p>The actions parameter contains a comma-separated list of the
     * desired actions granted on the target name.  It must not be
     * empty or null.</p>
     *
     * @param name the triplet "mbeanServerName::member[objectName]".
     * If <code>objectName</code> is present, it is of
     * the form <code>namespace//MBeanName</code>.
     * @param actions the action string.
     *
     * @exception IllegalArgumentException if the <code>name</code> or
     * <code>actions</code> is invalid.
     */
    public JMXNamespacePermission(String name, String actions) {
        super(name);

        parseName();

        this.actions = actions;
        parseActions();
    }

    /**
     * <p>Create a new JMXNamespacePermission object with the specified
     * target name (namespace name, member, object name) and actions.</p>
     *
     * <p>The {@code MBeanServer} name, member and object name
     * parameters define a target name of the form
     * "<code>mbeanServerName::member[objectName]</code>" where each
     * part is optional.  This will be the result of {@link #getName()} on the
     * resultant JMXNamespacePermission.
     * If the <code>mbeanServerName</code> is empty or exactly {@code "*"}, then
     * "{@code mbeanServerName::}" is omitted in that result.
     * </p>
     *
     * <p>The actions parameter contains a comma-separated list of the
     * desired actions granted on the target name.  It must not be
     * empty or null.</p>
     *
     * @param mbeanServerName the name of the {@code MBeanServer} to which this
     * permission applies.
     * May be null or <code>"-"</code>, which represents an MBeanServer name
     * that is implied by any MBeanServer name but does not imply any other
     * MBeanServer name.
     * @param member the member to which this permission applies.  May
     * be null or <code>"-"</code>, which represents a member that is
     * implied by any member but does not imply any other member.
     * @param objectName the object name to which this permission
     * applies.
     * May be null, which represents an object name that is
     * implied by any object name but does not imply any other object
     * name. If not null, the {@code objectName} must be of the
     * form {@code <namespace>//<mbean name>} - where {@code <namespace>}
     * can be a domain pattern, and {@code <mbean name>} can be an ObjectName
     * pattern.
     * For a permission you need, {@code <namespace>} is the name of the
     * name space for which the permission is checked, and {@code <mbean name>}
     * is the name of the MBean in that namespace.
     * The composed name {@code <namespace>//<mbean name>} thus represents the
     * name of the MBean as seen by the {@code mbeanServerName} containing
     * {@code <namespace>}.
     *
     * @param actions the action string.
     */
    public JMXNamespacePermission(
                           String mbeanServerName,
                           String member,
                           ObjectName objectName,
                           String actions) {
        this(mbeanServerName, member, objectName, false, actions);
//        this(member, objectName, false, actions);
    }

    /**
     * <p>Create a new JMXNamespacePermission object with the specified
     * MBean Server name, member, and actions.</p>
     *
     * <p>The {@code MBeanServer} name and member
     * parameters define a target name of the form
     * "<code>mbeanServerName::member[]</code>" where each
     * part is optional.  This will be the result of {@link #getName()} on the
     * resultant JMXNamespacePermission.
     * If the <code>mbeanServerName</code> is empty or exactly {@code "*"}, then
     * "{@code mbeanServerName::}" is omitted in that result.
     * </p>
     *
     * <p>The actions parameter contains a comma-separated list of the
     * desired actions granted on the target name.  It must not be
     * empty or null.</p>
     *
     * @param mbeanServerName the name of the {@code MBeanServer} to which this
     * permission applies.
     * May be null or <code>"-"</code>, which represents an MBeanServer name
     * that is implied by any MBeanServer name but does not imply any other
     * MBeanServer name.
     * @param member the member to which this permission applies.  May
     * be null or <code>"-"</code>, which represents a member that is
     * implied by any member but does not imply any other member.
     * @param actions the action string.
     */
    public JMXNamespacePermission(String mbeanServerName,
                           String member,
                           String actions) {
        this(mbeanServerName,member,null,true,actions);
        // this(member,null,allnames,actions);
    }

    /**
     * <p>Create a new JMXNamespacePermission object with the specified
     * target name (namespace name, member, object name) and actions.</p>
     *
     * <p>The MBean Server name, member and object name parameters define a
     * target name of the form
     * "<code>mbeanServerName::member[objectName]</code>" where each part is
     * optional.  This will be the result of {@link
     * java.security.Permission#getName() getName()} on the
     * resultant JMXNamespacePermission.</p>
     *
     * <p>The actions parameter contains a comma-separated list of the
     * desired actions granted on the target name.  It must not be
     * empty or null.</p>
     *
     * @param mbeanServerName the name of the {@code MBeanServer} to which this
     * permission applies.
     * May be null or <code>"-"</code>, which represents an MBeanServer name
     * that is implied by any MBeanServer name but does not imply any other
     * MBeanServer name.
     * @param member the member to which this permission applies.  May
     * be null or <code>"-"</code>, which represents a member that is
     * implied by any member but does not imply any other member.
     * @param objectName the object name to which this permission
     * applies.  If null, and allnames is false, represents an object
     * name that is implied by any object name but does not imply any
     * other object name. Otherwise, if allnames is true, it represents
     * a meta wildcard that matches all object names. It is equivalent to
     * a missing objectName ("[]") in the {@link
     * java.security.Permission#getName() name} property.
     * @param allnames represent a meta wildcard indicating that the
     *        objectName was not specified. This implies all objectnames
     *        that match "*:*" and all object names that match
     *        "*&#42;//*:*"
     * @param actions the action string.
     */
    private JMXNamespacePermission(String mbeanServerName,
                           String member,
                           ObjectName objectName,
                           boolean allnames,
                           String actions) {

        super(makeName(mbeanServerName,
                member, objectName, allnames));
        initName(mbeanServerName,
                member, objectName, allnames);

        this.actions = actions;
        parseActions();
    }

    private static String makeName(String mbeanServerName,
            String memberName, ObjectName objName, boolean allMBeans) {
        final StringBuilder name = new StringBuilder();
        if (mbeanServerName == null)
            mbeanServerName = "-";
        if (!mbeanServerName.equals("") && !mbeanServerName.equals("*"))
            name.append(mbeanServerName).append("::");
        if (memberName == null)
            memberName = "-";
        name.append(memberName);
        if (objName == null) {
            if (allMBeans)
                name.append("[]");
            else
                name.append("[-]");
        } else {
            final String domain = objName.getDomain();
            final int seplen = JMXNamespaces.NAMESPACE_SEPARATOR.length();
            final int sepc = domain.indexOf(JMXNamespaces.NAMESPACE_SEPARATOR);
            if (sepc < 0 || (sepc+seplen)==domain.length()) {
                throw new IllegalArgumentException(String.valueOf(objName)+
                        ": no namespace in domain");
            }
            final String can = objName.getCanonicalName();
            name.append("[").append(can).append("]");
        }
        return name.toString();
    }

    /**
     * Returns the "canonical string representation" of the actions. That is,
     * this method always returns actions in alphabetical order.
     *
     * @return the canonical string representation of the actions.
     */
    public String getActions() {

        if (actions == null)
            actions = getActions(this.mask);

        return actions;
    }

    /**
     * Returns the "canonical string representation"
     * of the actions from the mask.
     */
    private static String getActions(int mask) {
        final StringBuilder sb = new StringBuilder();
        boolean comma = false;

        if ((mask & AddNotificationListener) == AddNotificationListener) {
            comma = true;
            sb.append("addNotificationListener");
        }

        if ((mask & GetAttribute) == GetAttribute) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("getAttribute");
        }

        if ((mask & GetClassLoader) == GetClassLoader) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("getClassLoader");
        }

        if ((mask & GetClassLoaderFor) == GetClassLoaderFor) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("getClassLoaderFor");
        }

        if ((mask & GetClassLoaderRepository) == GetClassLoaderRepository) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("getClassLoaderRepository");
        }

        if ((mask & GetMBeanInfo) == GetMBeanInfo) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("getMBeanInfo");
        }

        if ((mask & GetObjectInstance) == GetObjectInstance) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("getObjectInstance");
        }

        if ((mask & Instantiate) == Instantiate) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("instantiate");
        }

        if ((mask & Invoke) == Invoke) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("invoke");
        }

        if ((mask & IsInstanceOf) == IsInstanceOf) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("isInstanceOf");
        }

        if ((mask & QueryMBeans) == QueryMBeans) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("queryMBeans");
        }

        if ((mask & QueryNames) == QueryNames) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("queryNames");
        }

        if ((mask & RegisterMBean) == RegisterMBean) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("registerMBean");
        }

        if ((mask & RemoveNotificationListener) == RemoveNotificationListener) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("removeNotificationListener");
        }

        if ((mask & SetAttribute) == SetAttribute) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("setAttribute");
        }

        if ((mask & UnregisterMBean) == UnregisterMBean) {
            if (comma) sb.append(',');
            else comma = true;
            sb.append("unregisterMBean");
        }

        // No GetDomains because it is not possible to route a call to
        // getDomains() on a NamespaceInterceptor - getDomains() doesn't
        // have any ObjectName.

        return sb.toString();
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode() + this.getActions().hashCode();
    }

    /**
     * Converts an action String to an integer action mask.
     *
     * @param action the action string.
     * @return the action mask.
     */
    private static int getMask(String action) {

        /*
         * BE CAREFUL HERE! PARSING ORDER IS IMPORTANT IN THIS ALGORITHM.
         *
         * The 'string length' test must be performed for the lengthiest
         * strings first.
         *
         * In this permission if the "unregisterMBean" string length test is
         * performed after the "registerMBean" string length test the algorithm
         * considers the 'unregisterMBean' action as being the 'registerMBean'
         * action and a parsing error is returned.
         */

        int mask = NONE;

        if (action == null) {
            return mask;
        }

        if (action.equals("*")) {
            return ALL;
        }

        char[] a = action.toCharArray();

        int i = a.length - 1;
        if (i < 0)
            return mask;

        while (i != -1) {
            char c;

            // skip whitespace
            while ((i!=-1) && ((c = a[i]) == ' ' ||
                               c == '\r' ||
                               c == '\n' ||
                               c == '\f' ||
                               c == '\t'))
                i--;

            // check for the known strings
            int matchlen;

            // No GetDomains because it is not possible to route a call to
            // getDomains() on a NamespaceInterceptor - getDomains() doesn't
            // have any ObjectName.

            if (i >= 25 && /* removeNotificationListener */
                (a[i-25] == 'r') &&
                (a[i-24] == 'e') &&
                (a[i-23] == 'm') &&
                (a[i-22] == 'o') &&
                (a[i-21] == 'v') &&
                (a[i-20] == 'e') &&
                (a[i-19] == 'N') &&
                (a[i-18] == 'o') &&
                (a[i-17] == 't') &&
                (a[i-16] == 'i') &&
                (a[i-15] == 'f') &&
                (a[i-14] == 'i') &&
                (a[i-13] == 'c') &&
                (a[i-12] == 'a') &&
                (a[i-11] == 't') &&
                (a[i-10] == 'i') &&
                (a[i-9] == 'o') &&
                (a[i-8] == 'n') &&
                (a[i-7] == 'L') &&
                (a[i-6] == 'i') &&
                (a[i-5] == 's') &&
                (a[i-4] == 't') &&
                (a[i-3] == 'e') &&
                (a[i-2] == 'n') &&
                (a[i-1] == 'e') &&
                (a[i] == 'r')) {
                matchlen = 26;
                mask |= RemoveNotificationListener;
            } else if (i >= 23 && /* getClassLoaderRepository */
                       (a[i-23] == 'g') &&
                       (a[i-22] == 'e') &&
                       (a[i-21] == 't') &&
                       (a[i-20] == 'C') &&
                       (a[i-19] == 'l') &&
                       (a[i-18] == 'a') &&
                       (a[i-17] == 's') &&
                       (a[i-16] == 's') &&
                       (a[i-15] == 'L') &&
                       (a[i-14] == 'o') &&
                       (a[i-13] == 'a') &&
                       (a[i-12] == 'd') &&
                       (a[i-11] == 'e') &&
                       (a[i-10] == 'r') &&
                       (a[i-9] == 'R') &&
                       (a[i-8] == 'e') &&
                       (a[i-7] == 'p') &&
                       (a[i-6] == 'o') &&
                       (a[i-5] == 's') &&
                       (a[i-4] == 'i') &&
                       (a[i-3] == 't') &&
                       (a[i-2] == 'o') &&
                       (a[i-1] == 'r') &&
                       (a[i] == 'y')) {
                matchlen = 24;
                mask |= GetClassLoaderRepository;
            } else if (i >= 22 && /* addNotificationListener */
                       (a[i-22] == 'a') &&
                       (a[i-21] == 'd') &&
                       (a[i-20] == 'd') &&
                       (a[i-19] == 'N') &&
                       (a[i-18] == 'o') &&
                       (a[i-17] == 't') &&
                       (a[i-16] == 'i') &&
                       (a[i-15] == 'f') &&
                       (a[i-14] == 'i') &&
                       (a[i-13] == 'c') &&
                       (a[i-12] == 'a') &&
                       (a[i-11] == 't') &&
                       (a[i-10] == 'i') &&
                       (a[i-9] == 'o') &&
                       (a[i-8] == 'n') &&
                       (a[i-7] == 'L') &&
                       (a[i-6] == 'i') &&
                       (a[i-5] == 's') &&
                       (a[i-4] == 't') &&
                       (a[i-3] == 'e') &&
                       (a[i-2] == 'n') &&
                       (a[i-1] == 'e') &&
                       (a[i] == 'r')) {
                matchlen = 23;
                mask |= AddNotificationListener;
            } else if (i >= 16 && /* getClassLoaderFor */
                       (a[i-16] == 'g') &&
                       (a[i-15] == 'e') &&
                       (a[i-14] == 't') &&
                       (a[i-13] == 'C') &&
                       (a[i-12] == 'l') &&
                       (a[i-11] == 'a') &&
                       (a[i-10] == 's') &&
                       (a[i-9] == 's') &&
                       (a[i-8] == 'L') &&
                       (a[i-7] == 'o') &&
                       (a[i-6] == 'a') &&
                       (a[i-5] == 'd') &&
                       (a[i-4] == 'e') &&
                       (a[i-3] == 'r') &&
                       (a[i-2] == 'F') &&
                       (a[i-1] == 'o') &&
                       (a[i] == 'r')) {
                matchlen = 17;
                mask |= GetClassLoaderFor;
            } else if (i >= 16 && /* getObjectInstance */
                       (a[i-16] == 'g') &&
                       (a[i-15] == 'e') &&
                       (a[i-14] == 't') &&
                       (a[i-13] == 'O') &&
                       (a[i-12] == 'b') &&
                       (a[i-11] == 'j') &&
                       (a[i-10] == 'e') &&
                       (a[i-9] == 'c') &&
                       (a[i-8] == 't') &&
                       (a[i-7] == 'I') &&
                       (a[i-6] == 'n') &&
                       (a[i-5] == 's') &&
                       (a[i-4] == 't') &&
                       (a[i-3] == 'a') &&
                       (a[i-2] == 'n') &&
                       (a[i-1] == 'c') &&
                       (a[i] == 'e')) {
                matchlen = 17;
                mask |= GetObjectInstance;
            } else if (i >= 14 && /* unregisterMBean */
                       (a[i-14] == 'u') &&
                       (a[i-13] == 'n') &&
                       (a[i-12] == 'r') &&
                       (a[i-11] == 'e') &&
                       (a[i-10] == 'g') &&
                       (a[i-9] == 'i') &&
                       (a[i-8] == 's') &&
                       (a[i-7] == 't') &&
                       (a[i-6] == 'e') &&
                       (a[i-5] == 'r') &&
                       (a[i-4] == 'M') &&
                       (a[i-3] == 'B') &&
                       (a[i-2] == 'e') &&
                       (a[i-1] == 'a') &&
                       (a[i] == 'n')) {
                matchlen = 15;
                mask |= UnregisterMBean;
            } else if (i >= 13 && /* getClassLoader */
                       (a[i-13] == 'g') &&
                       (a[i-12] == 'e') &&
                       (a[i-11] == 't') &&
                       (a[i-10] == 'C') &&
                       (a[i-9] == 'l') &&
                       (a[i-8] == 'a') &&
                       (a[i-7] == 's') &&
                       (a[i-6] == 's') &&
                       (a[i-5] == 'L') &&
                       (a[i-4] == 'o') &&
                       (a[i-3] == 'a') &&
                       (a[i-2] == 'd') &&
                       (a[i-1] == 'e') &&
                       (a[i] == 'r')) {
                matchlen = 14;
                mask |= GetClassLoader;
            } else if (i >= 12 && /* registerMBean */
                       (a[i-12] == 'r') &&
                       (a[i-11] == 'e') &&
                       (a[i-10] == 'g') &&
                       (a[i-9] == 'i') &&
                       (a[i-8] == 's') &&
                       (a[i-7] == 't') &&
                       (a[i-6] == 'e') &&
                       (a[i-5] == 'r') &&
                       (a[i-4] == 'M') &&
                       (a[i-3] == 'B') &&
                       (a[i-2] == 'e') &&
                       (a[i-1] == 'a') &&
                       (a[i] == 'n')) {
                matchlen = 13;
                mask |= RegisterMBean;
            } else if (i >= 11 && /* getAttribute */
                       (a[i-11] == 'g') &&
                       (a[i-10] == 'e') &&
                       (a[i-9] == 't') &&
                       (a[i-8] == 'A') &&
                       (a[i-7] == 't') &&
                       (a[i-6] == 't') &&
                       (a[i-5] == 'r') &&
                       (a[i-4] == 'i') &&
                       (a[i-3] == 'b') &&
                       (a[i-2] == 'u') &&
                       (a[i-1] == 't') &&
                       (a[i] == 'e')) {
                matchlen = 12;
                mask |= GetAttribute;
            } else if (i >= 11 && /* getMBeanInfo */
                       (a[i-11] == 'g') &&
                       (a[i-10] == 'e') &&
                       (a[i-9] == 't') &&
                       (a[i-8] == 'M') &&
                       (a[i-7] == 'B') &&
                       (a[i-6] == 'e') &&
                       (a[i-5] == 'a') &&
                       (a[i-4] == 'n') &&
                       (a[i-3] == 'I') &&
                       (a[i-2] == 'n') &&
                       (a[i-1] == 'f') &&
                       (a[i] == 'o')) {
                matchlen = 12;
                mask |= GetMBeanInfo;
            } else if (i >= 11 && /* isInstanceOf */
                       (a[i-11] == 'i') &&
                       (a[i-10] == 's') &&
                       (a[i-9] == 'I') &&
                       (a[i-8] == 'n') &&
                       (a[i-7] == 's') &&
                       (a[i-6] == 't') &&
                       (a[i-5] == 'a') &&
                       (a[i-4] == 'n') &&
                       (a[i-3] == 'c') &&
                       (a[i-2] == 'e') &&
                       (a[i-1] == 'O') &&
                       (a[i] == 'f')) {
                matchlen = 12;
                mask |= IsInstanceOf;
            } else if (i >= 11 && /* setAttribute */
                       (a[i-11] == 's') &&
                       (a[i-10] == 'e') &&
                       (a[i-9] == 't') &&
                       (a[i-8] == 'A') &&
                       (a[i-7] == 't') &&
                       (a[i-6] == 't') &&
                       (a[i-5] == 'r') &&
                       (a[i-4] == 'i') &&
                       (a[i-3] == 'b') &&
                       (a[i-2] == 'u') &&
                       (a[i-1] == 't') &&
                       (a[i] == 'e')) {
                matchlen = 12;
                mask |= SetAttribute;
            } else if (i >= 10 && /* instantiate */
                       (a[i-10] == 'i') &&
                       (a[i-9] == 'n') &&
                       (a[i-8] == 's') &&
                       (a[i-7] == 't') &&
                       (a[i-6] == 'a') &&
                       (a[i-5] == 'n') &&
                       (a[i-4] == 't') &&
                       (a[i-3] == 'i') &&
                       (a[i-2] == 'a') &&
                       (a[i-1] == 't') &&
                       (a[i] == 'e')) {
                matchlen = 11;
                mask |= Instantiate;
            } else if (i >= 10 && /* queryMBeans */
                       (a[i-10] == 'q') &&
                       (a[i-9] == 'u') &&
                       (a[i-8] == 'e') &&
                       (a[i-7] == 'r') &&
                       (a[i-6] == 'y') &&
                       (a[i-5] == 'M') &&
                       (a[i-4] == 'B') &&
                       (a[i-3] == 'e') &&
                       (a[i-2] == 'a') &&
                       (a[i-1] == 'n') &&
                       (a[i] == 's')) {
                matchlen = 11;
                mask |= QueryMBeans;
            } else if (i >= 9 && /* queryNames */
                       (a[i-9] == 'q') &&
                       (a[i-8] == 'u') &&
                       (a[i-7] == 'e') &&
                       (a[i-6] == 'r') &&
                       (a[i-5] == 'y') &&
                       (a[i-4] == 'N') &&
                       (a[i-3] == 'a') &&
                       (a[i-2] == 'm') &&
                       (a[i-1] == 'e') &&
                       (a[i] == 's')) {
                matchlen = 10;
                mask |= QueryNames;
            } else if (i >= 5 && /* invoke */
                       (a[i-5] == 'i') &&
                       (a[i-4] == 'n') &&
                       (a[i-3] == 'v') &&
                       (a[i-2] == 'o') &&
                       (a[i-1] == 'k') &&
                       (a[i] == 'e')) {
                matchlen = 6;
                mask |= Invoke;
            } else {
                // parse error
                throw new IllegalArgumentException("Invalid permission: " +
                                                   action);
            }

            // make sure we didn't just match the tail of a word
            // like "ackbarfaccept".  Also, skip to the comma.
            boolean seencomma = false;
            while (i >= matchlen && !seencomma) {
                switch(a[i-matchlen]) {
                case ',':
                    seencomma = true;
                    break;
                case ' ': case '\r': case '\n':
                case '\f': case '\t':
                    break;
                default:
                    throw new IllegalArgumentException("Invalid permission: " +
                                                       action);
                }
                i--;
            }

            // point i at the location of the comma minus one (or -1).
            i -= matchlen;
        }

        return mask;
    }

    /**
     * <p>Checks if this JMXNamespacePermission object "implies" the
     * specified permission.</p>
     *
     * <p>More specifically, this method returns true if:</p>
     *
     * <ul>
     *
     * <li> <i>p</i> is an instance of JMXNamespacePermission; and</li>
     *
     * <li> <i>p</i> has a null mbeanServerName or <i>p</i>'s mbeanServerName
     * matches this object's mbeanServerName; and</li>
     *
     * <li> <i>p</i> has a null member or <i>p</i>'s member matches this
     * object's member; and</li>
     *
     * <li> <i>p</i> has a null object name or <i>p</i>'s
     * object name matches this object's object name; and</li>
     *
     * <li> <i>p</i>'s actions are a subset of this object's actions</li>
     *
     * </ul>
     *
     * <p>If this object's mbeanServerName is a pattern, then <i>p</i>'s
     *    mbeanServerName is matched against that pattern. An empty
     *    mbeanServerName is equivalent to "{@code *}". A null
     *    mbeanServerName is equivalent to "{@code -}".</p>
     * <p>If this object's mbeanServerName is "<code>*</code>" or is
     * empty, <i>p</i>'s mbeanServerName always matches it.</p>
     *
     * <p>If this object's member is "<code>*</code>", <i>p</i>'s
     * member always matches it.</p>
     *
     * <p>If this object's objectName <i>n1</i> is an object name pattern,
     * <i>p</i>'s objectName <i>n2</i> matches it if
     * {@link ObjectName#equals <i>n1</i>.equals(<i>n2</i>)} or if
     * {@link ObjectName#apply <i>n1</i>.apply(<i>n2</i>)}.</p>
     *
     * <p>A permission that includes the <code>queryMBeans</code> action
     * is considered to include <code>queryNames</code> as well.</p>
     *
     * @param p the permission to check against.
     * @return true if the specified permission is implied by this object,
     * false if not.
     */
    public boolean implies(Permission p) {
        if (!(p instanceof JMXNamespacePermission))
            return false;

        JMXNamespacePermission that = (JMXNamespacePermission) p;

        // Actions
        //
        // The actions in 'this' permission must be a
        // superset of the actions in 'that' permission
        //

        /* "queryMBeans" implies "queryNames" */
        if ((this.mask & QueryMBeans) == QueryMBeans) {
            if (((this.mask | QueryNames) & that.mask) != that.mask) {
                //System.out.println("action [with QueryNames] does not imply");
                return false;
            }
        } else {
            if ((this.mask & that.mask) != that.mask) {
                //System.out.println("action does not imply");
                return false;
            }
        }

        // Target name
        //
        // The 'mbeanServerName' check is true iff:
        // 1) the mbeanServerName in 'this' permission is omitted or "*", or
        // 2) the mbeanServerName in 'that' permission is omitted or "*", or
        // 3) the mbeanServerName in 'this' permission does pattern
        //    matching with the mbeanServerName in 'that' permission.
        //
        // The 'member' check is true iff:
        // 1) the member in 'this' member is omitted or "*", or
        // 2) the member in 'that' member is omitted or "*", or
        // 3) the member in 'this' permission equals the member in
        //    'that' permission.
        //
        // The 'object name' check is true iff:
        // 1) the object name in 'this' permission is omitted, or
        // 2) the object name in 'that' permission is omitted, or
        // 3) the object name in 'this' permission does pattern
        //    matching with the object name in 'that' permission.
        //

        if (that.mbeanServerName == null) {
            // bottom is implied
        } else if (this.mbeanServerName == null) {
            // bottom implies nothing but itself
            return false;
        } else if (that.mbeanServerName.equals(this.mbeanServerName)) {
            // exact match
        } else if (!Util.wildmatch(that.mbeanServerName,this.mbeanServerName)) {
            return false; // no match
        }

        /* Check if this.member implies that.member */

        if (that.member == null) {
            // bottom is implied
        } else if (this.member == null) {
            // bottom implies nothing but itself
            return false;
        } else if (this.member.equals("*")) {
            // wildcard implies everything (including itself)
        } else if (this.member.equals(that.member)) {
            // exact match
        } else if (!Util.wildmatch(that.member,this.member)) {
            return false; // no match
        }

        /* Check if this.objectName implies that.objectName */

        if (that.objectName == null) {
            // bottom is implied
        } else if (this.objectName == null) {
            // bottom implies nothing but itself
            if (allnames == false) return false;
        } else if (!this.objectName.apply(that.objectName)) {
            /* ObjectName.apply returns false if that.objectName is a
               wildcard so we also allow equals for that case.  This
               never happens during real permission checks, but means
               the implies relation is reflexive.  */
            if (!this.objectName.equals(that.objectName))
                return false;
        }

        return true;
    }

    /**
     * Checks two JMXNamespacePermission objects for equality. Checks
     * that <i>obj</i> is an JMXNamespacePermission, and has the same
     * name and actions as this object.
     * <P>
     * @param obj the object we are testing for equality with this object.
     * @return true if obj is an JMXNamespacePermission, and has the
     * same name and actions as this JMXNamespacePermission object.
     */
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (! (obj instanceof JMXNamespacePermission))
            return false;

        JMXNamespacePermission that = (JMXNamespacePermission) obj;

        return (this.mask == that.mask) &&
            (this.getName().equals(that.getName()));
    }

    /**
     * Deserialize this object based on its name and actions.
     */
    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        parseName();
        parseActions();
    }
}
