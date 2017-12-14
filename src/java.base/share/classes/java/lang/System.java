/*
 * Copyright (c) 1994, 2017, Oracle and/or its affiliates. All rights reserved.
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
package java.lang;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.security.AccessControlContext;
import java.security.ProtectionDomain;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.nio.channels.Channel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.PropertyPermission;
import java.util.ResourceBundle;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import jdk.internal.module.ModuleBootstrap;
import jdk.internal.module.ServicesCatalog;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;
import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.JavaLangAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.misc.VM;
import jdk.internal.logger.LoggerFinderLoader;
import jdk.internal.logger.LazyLoggers;
import jdk.internal.logger.LocalizedLoggerWrapper;
import sun.reflect.annotation.AnnotationType;
import sun.nio.ch.Interruptible;
import sun.security.util.SecurityConstants;

/**
 * The <code>System</code> class contains several useful class fields
 * and methods. It cannot be instantiated.
 *
 * <p>Among the facilities provided by the <code>System</code> class
 * are standard input, standard output, and error output streams;
 * access to externally defined properties and environment
 * variables; a means of loading files and libraries; and a utility
 * method for quickly copying a portion of an array.
 *
 * @author  unascribed
 * @since   1.0
 */
public final class System {
    /* register the natives via the static initializer.
     *
     * VM will invoke the initializeSystemClass method to complete
     * the initialization for this class separated from clinit.
     * Note that to use properties set by the VM, see the constraints
     * described in the initializeSystemClass method.
     */
    private static native void registerNatives();
    static {
        registerNatives();
    }

    /** Don't let anyone instantiate this class */
    private System() {
    }

    /**
     * The "standard" input stream. This stream is already
     * open and ready to supply input data. Typically this stream
     * corresponds to keyboard input or another input source specified by
     * the host environment or user.
     */
    public static final InputStream in = null;

    /**
     * The "standard" output stream. This stream is already
     * open and ready to accept output data. Typically this stream
     * corresponds to display output or another output destination
     * specified by the host environment or user.
     * <p>
     * For simple stand-alone Java applications, a typical way to write
     * a line of output data is:
     * <blockquote><pre>
     *     System.out.println(data)
     * </pre></blockquote>
     * <p>
     * See the <code>println</code> methods in class <code>PrintStream</code>.
     *
     * @see     java.io.PrintStream#println()
     * @see     java.io.PrintStream#println(boolean)
     * @see     java.io.PrintStream#println(char)
     * @see     java.io.PrintStream#println(char[])
     * @see     java.io.PrintStream#println(double)
     * @see     java.io.PrintStream#println(float)
     * @see     java.io.PrintStream#println(int)
     * @see     java.io.PrintStream#println(long)
     * @see     java.io.PrintStream#println(java.lang.Object)
     * @see     java.io.PrintStream#println(java.lang.String)
     */
    public static final PrintStream out = null;

    /**
     * The "standard" error output stream. This stream is already
     * open and ready to accept output data.
     * <p>
     * Typically this stream corresponds to display output or another
     * output destination specified by the host environment or user. By
     * convention, this output stream is used to display error messages
     * or other information that should come to the immediate attention
     * of a user even if the principal output stream, the value of the
     * variable <code>out</code>, has been redirected to a file or other
     * destination that is typically not continuously monitored.
     */
    public static final PrintStream err = null;

    /* The security manager for the system.
     */
    private static volatile SecurityManager security;

    /**
     * Reassigns the "standard" input stream.
     *
     * <p>First, if there is a security manager, its <code>checkPermission</code>
     * method is called with a <code>RuntimePermission("setIO")</code> permission
     *  to see if it's ok to reassign the "standard" input stream.
     *
     * @param in the new standard input stream.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow
     *        reassigning of the standard input stream.
     *
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     *
     * @since   1.1
     */
    public static void setIn(InputStream in) {
        checkIO();
        setIn0(in);
    }

    /**
     * Reassigns the "standard" output stream.
     *
     * <p>First, if there is a security manager, its <code>checkPermission</code>
     * method is called with a <code>RuntimePermission("setIO")</code> permission
     *  to see if it's ok to reassign the "standard" output stream.
     *
     * @param out the new standard output stream
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow
     *        reassigning of the standard output stream.
     *
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     *
     * @since   1.1
     */
    public static void setOut(PrintStream out) {
        checkIO();
        setOut0(out);
    }

    /**
     * Reassigns the "standard" error output stream.
     *
     * <p>First, if there is a security manager, its <code>checkPermission</code>
     * method is called with a <code>RuntimePermission("setIO")</code> permission
     *  to see if it's ok to reassign the "standard" error output stream.
     *
     * @param err the new standard error output stream.
     *
     * @throws SecurityException
     *        if a security manager exists and its
     *        <code>checkPermission</code> method doesn't allow
     *        reassigning of the standard error output stream.
     *
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     *
     * @since   1.1
     */
    public static void setErr(PrintStream err) {
        checkIO();
        setErr0(err);
    }

    private static volatile Console cons;
    /**
     * Returns the unique {@link java.io.Console Console} object associated
     * with the current Java virtual machine, if any.
     *
     * @return  The system console, if any, otherwise {@code null}.
     *
     * @since   1.6
     */
     public static Console console() {
         Console c;
         if ((c = cons) == null) {
             synchronized (System.class) {
                 if ((c = cons) == null) {
                     cons = c = SharedSecrets.getJavaIOAccess().console();
                 }
             }
         }
         return c;
     }

    /**
     * Returns the channel inherited from the entity that created this
     * Java virtual machine.
     *
     * <p> This method returns the channel obtained by invoking the
     * {@link java.nio.channels.spi.SelectorProvider#inheritedChannel
     * inheritedChannel} method of the system-wide default
     * {@link java.nio.channels.spi.SelectorProvider} object. </p>
     *
     * <p> In addition to the network-oriented channels described in
     * {@link java.nio.channels.spi.SelectorProvider#inheritedChannel
     * inheritedChannel}, this method may return other kinds of
     * channels in the future.
     *
     * @return  The inherited channel, if any, otherwise {@code null}.
     *
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @throws  SecurityException
     *          If a security manager is present and it does not
     *          permit access to the channel.
     *
     * @since 1.5
     */
    public static Channel inheritedChannel() throws IOException {
        return SelectorProvider.provider().inheritedChannel();
    }

    private static void checkIO() {
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("setIO"));
        }
    }

    private static native void setIn0(InputStream in);
    private static native void setOut0(PrintStream out);
    private static native void setErr0(PrintStream err);

    /**
     * Sets the System security.
     *
     * <p> If there is a security manager already installed, this method first
     * calls the security manager's <code>checkPermission</code> method
     * with a <code>RuntimePermission("setSecurityManager")</code>
     * permission to ensure it's ok to replace the existing
     * security manager.
     * This may result in throwing a <code>SecurityException</code>.
     *
     * <p> Otherwise, the argument is established as the current
     * security manager. If the argument is <code>null</code> and no
     * security manager has been established, then no action is taken and
     * the method simply returns.
     *
     * @param      s   the security manager.
     * @exception  SecurityException  if the security manager has already
     *             been set and its <code>checkPermission</code> method
     *             doesn't allow it to be replaced.
     * @see #getSecurityManager
     * @see SecurityManager#checkPermission
     * @see java.lang.RuntimePermission
     */
    public static void setSecurityManager(final SecurityManager s) {
        if (security == null) {
            // ensure image reader is initialized
            Object.class.getResource("java/lang/ANY");
        }
        if (s != null) {
            try {
                s.checkPackageAccess("java.lang");
            } catch (Exception e) {
                // no-op
            }
        }
        setSecurityManager0(s);
    }

    private static synchronized
    void setSecurityManager0(final SecurityManager s) {
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            // ask the currently installed security manager if we
            // can replace it.
            sm.checkPermission(new RuntimePermission
                                     ("setSecurityManager"));
        }

        if ((s != null) && (s.getClass().getClassLoader() != null)) {
            // New security manager class is not on bootstrap classpath.
            // Cause policy to get initialized before we install the new
            // security manager, in order to prevent infinite loops when
            // trying to initialize the policy (which usually involves
            // accessing some security and/or system properties, which in turn
            // calls the installed security manager's checkPermission method
            // which will loop infinitely if there is a non-system class
            // (in this case: the new security manager class) on the stack).
            AccessController.doPrivileged(new PrivilegedAction<>() {
                public Object run() {
                    s.getClass().getProtectionDomain().implies
                        (SecurityConstants.ALL_PERMISSION);
                    return null;
                }
            });
        }

        security = s;
    }

    /**
     * Gets the system security interface.
     *
     * @return  if a security manager has already been established for the
     *          current application, then that security manager is returned;
     *          otherwise, <code>null</code> is returned.
     * @see     #setSecurityManager
     */
    public static SecurityManager getSecurityManager() {
        return security;
    }

    /**
     * Returns the current time in milliseconds.  Note that
     * while the unit of time of the return value is a millisecond,
     * the granularity of the value depends on the underlying
     * operating system and may be larger.  For example, many
     * operating systems measure time in units of tens of
     * milliseconds.
     *
     * <p> See the description of the class <code>Date</code> for
     * a discussion of slight discrepancies that may arise between
     * "computer time" and coordinated universal time (UTC).
     *
     * @return  the difference, measured in milliseconds, between
     *          the current time and midnight, January 1, 1970 UTC.
     * @see     java.util.Date
     */
    @HotSpotIntrinsicCandidate
    public static native long currentTimeMillis();

    /**
     * Returns the current value of the running Java Virtual Machine's
     * high-resolution time source, in nanoseconds.
     *
     * <p>This method can only be used to measure elapsed time and is
     * not related to any other notion of system or wall-clock time.
     * The value returned represents nanoseconds since some fixed but
     * arbitrary <i>origin</i> time (perhaps in the future, so values
     * may be negative).  The same origin is used by all invocations of
     * this method in an instance of a Java virtual machine; other
     * virtual machine instances are likely to use a different origin.
     *
     * <p>This method provides nanosecond precision, but not necessarily
     * nanosecond resolution (that is, how frequently the value changes)
     * - no guarantees are made except that the resolution is at least as
     * good as that of {@link #currentTimeMillis()}.
     *
     * <p>Differences in successive calls that span greater than
     * approximately 292 years (2<sup>63</sup> nanoseconds) will not
     * correctly compute elapsed time due to numerical overflow.
     *
     * <p>The values returned by this method become meaningful only when
     * the difference between two such values, obtained within the same
     * instance of a Java virtual machine, is computed.
     *
     * <p>For example, to measure how long some code takes to execute:
     * <pre> {@code
     * long startTime = System.nanoTime();
     * // ... the code being measured ...
     * long elapsedNanos = System.nanoTime() - startTime;}</pre>
     *
     * <p>To compare elapsed time against a timeout, use <pre> {@code
     * if (System.nanoTime() - startTime >= timeoutNanos) ...}</pre>
     * instead of <pre> {@code
     * if (System.nanoTime() >= startTime + timeoutNanos) ...}</pre>
     * because of the possibility of numerical overflow.
     *
     * @return the current value of the running Java Virtual Machine's
     *         high-resolution time source, in nanoseconds
     * @since 1.5
     */
    @HotSpotIntrinsicCandidate
    public static native long nanoTime();

    /**
     * Copies an array from the specified source array, beginning at the
     * specified position, to the specified position of the destination array.
     * A subsequence of array components are copied from the source
     * array referenced by <code>src</code> to the destination array
     * referenced by <code>dest</code>. The number of components copied is
     * equal to the <code>length</code> argument. The components at
     * positions <code>srcPos</code> through
     * <code>srcPos+length-1</code> in the source array are copied into
     * positions <code>destPos</code> through
     * <code>destPos+length-1</code>, respectively, of the destination
     * array.
     * <p>
     * If the <code>src</code> and <code>dest</code> arguments refer to the
     * same array object, then the copying is performed as if the
     * components at positions <code>srcPos</code> through
     * <code>srcPos+length-1</code> were first copied to a temporary
     * array with <code>length</code> components and then the contents of
     * the temporary array were copied into positions
     * <code>destPos</code> through <code>destPos+length-1</code> of the
     * destination array.
     * <p>
     * If <code>dest</code> is <code>null</code>, then a
     * <code>NullPointerException</code> is thrown.
     * <p>
     * If <code>src</code> is <code>null</code>, then a
     * <code>NullPointerException</code> is thrown and the destination
     * array is not modified.
     * <p>
     * Otherwise, if any of the following is true, an
     * <code>ArrayStoreException</code> is thrown and the destination is
     * not modified:
     * <ul>
     * <li>The <code>src</code> argument refers to an object that is not an
     *     array.
     * <li>The <code>dest</code> argument refers to an object that is not an
     *     array.
     * <li>The <code>src</code> argument and <code>dest</code> argument refer
     *     to arrays whose component types are different primitive types.
     * <li>The <code>src</code> argument refers to an array with a primitive
     *    component type and the <code>dest</code> argument refers to an array
     *     with a reference component type.
     * <li>The <code>src</code> argument refers to an array with a reference
     *    component type and the <code>dest</code> argument refers to an array
     *     with a primitive component type.
     * </ul>
     * <p>
     * Otherwise, if any of the following is true, an
     * <code>IndexOutOfBoundsException</code> is
     * thrown and the destination is not modified:
     * <ul>
     * <li>The <code>srcPos</code> argument is negative.
     * <li>The <code>destPos</code> argument is negative.
     * <li>The <code>length</code> argument is negative.
     * <li><code>srcPos+length</code> is greater than
     *     <code>src.length</code>, the length of the source array.
     * <li><code>destPos+length</code> is greater than
     *     <code>dest.length</code>, the length of the destination array.
     * </ul>
     * <p>
     * Otherwise, if any actual component of the source array from
     * position <code>srcPos</code> through
     * <code>srcPos+length-1</code> cannot be converted to the component
     * type of the destination array by assignment conversion, an
     * <code>ArrayStoreException</code> is thrown. In this case, let
     * <b><i>k</i></b> be the smallest nonnegative integer less than
     * length such that <code>src[srcPos+</code><i>k</i><code>]</code>
     * cannot be converted to the component type of the destination
     * array; when the exception is thrown, source array components from
     * positions <code>srcPos</code> through
     * <code>srcPos+</code><i>k</i><code>-1</code>
     * will already have been copied to destination array positions
     * <code>destPos</code> through
     * <code>destPos+</code><i>k</I><code>-1</code> and no other
     * positions of the destination array will have been modified.
     * (Because of the restrictions already itemized, this
     * paragraph effectively applies only to the situation where both
     * arrays have component types that are reference types.)
     *
     * @param      src      the source array.
     * @param      srcPos   starting position in the source array.
     * @param      dest     the destination array.
     * @param      destPos  starting position in the destination data.
     * @param      length   the number of array elements to be copied.
     * @exception  IndexOutOfBoundsException  if copying would cause
     *               access of data outside array bounds.
     * @exception  ArrayStoreException  if an element in the <code>src</code>
     *               array could not be stored into the <code>dest</code> array
     *               because of a type mismatch.
     * @exception  NullPointerException if either <code>src</code> or
     *               <code>dest</code> is <code>null</code>.
     */
    @HotSpotIntrinsicCandidate
    public static native void arraycopy(Object src,  int  srcPos,
                                        Object dest, int destPos,
                                        int length);

    /**
     * Returns the same hash code for the given object as
     * would be returned by the default method hashCode(),
     * whether or not the given object's class overrides
     * hashCode().
     * The hash code for the null reference is zero.
     *
     * @param x object for which the hashCode is to be calculated
     * @return  the hashCode
     * @since   1.1
     * @see Object#hashCode
     * @see java.util.Objects#hashCode(Object)
     */
    @HotSpotIntrinsicCandidate
    public static native int identityHashCode(Object x);

    /**
     * System properties. The following properties are guaranteed to be defined:
     * <dl>
     * <dt>java.version         <dd>Java version number
     * <dt>java.version.date    <dd>Java version date
     * <dt>java.vendor          <dd>Java vendor specific string
     * <dt>java.vendor.url      <dd>Java vendor URL
     * <dt>java.vendor.version  <dd>Java vendor version
     * <dt>java.home            <dd>Java installation directory
     * <dt>java.class.version   <dd>Java class version number
     * <dt>java.class.path      <dd>Java classpath
     * <dt>os.name              <dd>Operating System Name
     * <dt>os.arch              <dd>Operating System Architecture
     * <dt>os.version           <dd>Operating System Version
     * <dt>file.separator       <dd>File separator ("/" on Unix)
     * <dt>path.separator       <dd>Path separator (":" on Unix)
     * <dt>line.separator       <dd>Line separator ("\n" on Unix)
     * <dt>user.name            <dd>User account name
     * <dt>user.home            <dd>User home directory
     * <dt>user.dir             <dd>User's current working directory
     * </dl>
     */

    private static Properties props;
    private static native Properties initProperties(Properties props);

    /**
     * Determines the current system properties.
     * <p>
     * First, if there is a security manager, its
     * <code>checkPropertiesAccess</code> method is called with no
     * arguments. This may result in a security exception.
     * <p>
     * The current set of system properties for use by the
     * {@link #getProperty(String)} method is returned as a
     * <code>Properties</code> object. If there is no current set of
     * system properties, a set of system properties is first created and
     * initialized. This set of system properties always includes values
     * for the following keys:
     * <table class="striped" style="text-align:left">
     * <caption style="display:none">Shows property keys and associated values</caption>
     * <thead>
     * <tr><th scope="col">Key</th>
     *     <th scope="col">Description of Associated Value</th></tr>
     * </thead>
     * <tbody>
     * <tr><th scope="row"><code>java.version</code></th>
     *     <td>Java Runtime Environment version, which may be interpreted
     *     as a {@link Runtime.Version}</td></tr>
     * <tr><th scope="row"><code>java.version.date</code></th>
     *     <td>Java Runtime Environment version date, in ISO-8601 YYYY-MM-DD
     *     format, which may be interpreted as a {@link
     *     java.time.LocalDate}</td></tr>
     * <tr><th scope="row"><code>java.vendor</code></th>
     *     <td>Java Runtime Environment vendor</td></tr>
     * <tr><th scope="row"><code>java.vendor.url</code></th>
     *     <td>Java vendor URL</td></tr>
     * <tr><th scope="row"><code>java.vendor.version</code></th>
     *     <td>Java vendor version</td></tr>
     * <tr><th scope="row"><code>java.home</code></th>
     *     <td>Java installation directory</td></tr>
     * <tr><th scope="row"><code>java.vm.specification.version</code></th>
     *     <td>Java Virtual Machine specification version which may be
     *     interpreted as a {@link Runtime.Version}</td></tr>
     * <tr><th scope="row"><code>java.vm.specification.vendor</code></th>
     *     <td>Java Virtual Machine specification vendor</td></tr>
     * <tr><th scope="row"><code>java.vm.specification.name</code></th>
     *     <td>Java Virtual Machine specification name</td></tr>
     * <tr><th scope="row"><code>java.vm.version</code></th>
     *     <td>Java Virtual Machine implementation version which may be
     *     interpreted as a {@link Runtime.Version}</td></tr>
     * <tr><th scope="row"><code>java.vm.vendor</code></th>
     *     <td>Java Virtual Machine implementation vendor</td></tr>
     * <tr><th scope="row"><code>java.vm.name</code></th>
     *     <td>Java Virtual Machine implementation name</td></tr>
     * <tr><th scope="row"><code>java.specification.version</code></th>
     *     <td>Java Runtime Environment specification version which may be
     *     interpreted as a {@link Runtime.Version}</td></tr>
     * <tr><th scope="row"><code>java.specification.vendor</code></th>
     *     <td>Java Runtime Environment specification  vendor</td></tr>
     * <tr><th scope="row"><code>java.specification.name</code></th>
     *     <td>Java Runtime Environment specification  name</td></tr>
     * <tr><th scope="row"><code>java.class.version</code></th>
     *     <td>Java class format version number</td></tr>
     * <tr><th scope="row"><code>java.class.path</code></th>
     *     <td>Java class path</td></tr>
     * <tr><th scope="row"><code>java.library.path</code></th>
     *     <td>List of paths to search when loading libraries</td></tr>
     * <tr><th scope="row"><code>java.io.tmpdir</code></th>
     *     <td>Default temp file path</td></tr>
     * <tr><th scope="row"><code>java.compiler</code></th>
     *     <td>Name of JIT compiler to use</td></tr>
     * <tr><th scope="row"><code>os.name</code></th>
     *     <td>Operating system name</td></tr>
     * <tr><th scope="row"><code>os.arch</code></th>
     *     <td>Operating system architecture</td></tr>
     * <tr><th scope="row"><code>os.version</code></th>
     *     <td>Operating system version</td></tr>
     * <tr><th scope="row"><code>file.separator</code></th>
     *     <td>File separator ("/" on UNIX)</td></tr>
     * <tr><th scope="row"><code>path.separator</code></th>
     *     <td>Path separator (":" on UNIX)</td></tr>
     * <tr><th scope="row"><code>line.separator</code></th>
     *     <td>Line separator ("\n" on UNIX)</td></tr>
     * <tr><th scope="row"><code>user.name</code></th>
     *     <td>User's account name</td></tr>
     * <tr><th scope="row"><code>user.home</code></th>
     *     <td>User's home directory</td></tr>
     * <tr><th scope="row"><code>user.dir</code></th>
     *     <td>User's current working directory</td></tr>
     * </tbody>
     * </table>
     * <p>
     * Multiple paths in a system property value are separated by the path
     * separator character of the platform.
     * <p>
     * Note that even if the security manager does not permit the
     * <code>getProperties</code> operation, it may choose to permit the
     * {@link #getProperty(String)} operation.
     *
     * @implNote In addition to the standard system properties, the system
     * properties may include the following keys:
     * <table class="striped">
     * <caption style="display:none">Shows property keys and associated values</caption>
     * <thead>
     * <tr><th scope="col">Key</th>
     *     <th scope="col">Description of Associated Value</th></tr>
     * </thead>
     * <tbody>
     * <tr><th scope="row">{@code jdk.module.path}</th>
     *     <td>The application module path</td></tr>
     * <tr><th scope="row">{@code jdk.module.upgrade.path}</th>
     *     <td>The upgrade module path</td></tr>
     * <tr><th scope="row">{@code jdk.module.main}</th>
     *     <td>The module name of the initial/main module</td></tr>
     * <tr><th scope="row">{@code jdk.module.main.class}</th>
     *     <td>The main class name of the initial module</td></tr>
     * </tbody>
     * </table>
     *
     * @return     the system properties
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertiesAccess</code> method doesn't allow access
     *              to the system properties.
     * @see        #setProperties
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkPropertiesAccess()
     * @see        java.util.Properties
     */
    public static Properties getProperties() {
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPropertiesAccess();
        }

        return props;
    }

    /**
     * Returns the system-dependent line separator string.  It always
     * returns the same value - the initial value of the {@linkplain
     * #getProperty(String) system property} {@code line.separator}.
     *
     * <p>On UNIX systems, it returns {@code "\n"}; on Microsoft
     * Windows systems it returns {@code "\r\n"}.
     *
     * @return the system-dependent line separator string
     * @since 1.7
     */
    public static String lineSeparator() {
        return lineSeparator;
    }

    private static String lineSeparator;

    /**
     * Sets the system properties to the <code>Properties</code>
     * argument.
     * <p>
     * First, if there is a security manager, its
     * <code>checkPropertiesAccess</code> method is called with no
     * arguments. This may result in a security exception.
     * <p>
     * The argument becomes the current set of system properties for use
     * by the {@link #getProperty(String)} method. If the argument is
     * <code>null</code>, then the current set of system properties is
     * forgotten.
     *
     * @param      props   the new system properties.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertiesAccess</code> method doesn't allow access
     *              to the system properties.
     * @see        #getProperties
     * @see        java.util.Properties
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkPropertiesAccess()
     */
    public static void setProperties(Properties props) {
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPropertiesAccess();
        }
        if (props == null) {
            props = new Properties();
            initProperties(props);
        }
        System.props = props;
    }

    /**
     * Gets the system property indicated by the specified key.
     * <p>
     * First, if there is a security manager, its
     * <code>checkPropertyAccess</code> method is called with the key as
     * its argument. This may result in a SecurityException.
     * <p>
     * If there is no current set of system properties, a set of system
     * properties is first created and initialized in the same manner as
     * for the <code>getProperties</code> method.
     *
     * @param      key   the name of the system property.
     * @return     the string value of the system property,
     *             or <code>null</code> if there is no property with that key.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertyAccess</code> method doesn't allow
     *              access to the specified system property.
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     * @see        #setProperty
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkPropertyAccess(java.lang.String)
     * @see        java.lang.System#getProperties()
     */
    public static String getProperty(String key) {
        checkKey(key);
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPropertyAccess(key);
        }

        return props.getProperty(key);
    }

    /**
     * Gets the system property indicated by the specified key.
     * <p>
     * First, if there is a security manager, its
     * <code>checkPropertyAccess</code> method is called with the
     * <code>key</code> as its argument.
     * <p>
     * If there is no current set of system properties, a set of system
     * properties is first created and initialized in the same manner as
     * for the <code>getProperties</code> method.
     *
     * @param      key   the name of the system property.
     * @param      def   a default value.
     * @return     the string value of the system property,
     *             or the default value if there is no property with that key.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertyAccess</code> method doesn't allow
     *             access to the specified system property.
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     * @see        #setProperty
     * @see        java.lang.SecurityManager#checkPropertyAccess(java.lang.String)
     * @see        java.lang.System#getProperties()
     */
    public static String getProperty(String key, String def) {
        checkKey(key);
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPropertyAccess(key);
        }

        return props.getProperty(key, def);
    }

    /**
     * Sets the system property indicated by the specified key.
     * <p>
     * First, if a security manager exists, its
     * <code>SecurityManager.checkPermission</code> method
     * is called with a <code>PropertyPermission(key, "write")</code>
     * permission. This may result in a SecurityException being thrown.
     * If no exception is thrown, the specified property is set to the given
     * value.
     *
     * @param      key   the name of the system property.
     * @param      value the value of the system property.
     * @return     the previous value of the system property,
     *             or <code>null</code> if it did not have one.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPermission</code> method doesn't allow
     *             setting of the specified property.
     * @exception  NullPointerException if <code>key</code> or
     *             <code>value</code> is <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     * @see        #getProperty
     * @see        java.lang.System#getProperty(java.lang.String)
     * @see        java.lang.System#getProperty(java.lang.String, java.lang.String)
     * @see        java.util.PropertyPermission
     * @see        SecurityManager#checkPermission
     * @since      1.2
     */
    public static String setProperty(String key, String value) {
        checkKey(key);
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new PropertyPermission(key,
                SecurityConstants.PROPERTY_WRITE_ACTION));
        }

        return (String) props.setProperty(key, value);
    }

    /**
     * Removes the system property indicated by the specified key.
     * <p>
     * First, if a security manager exists, its
     * <code>SecurityManager.checkPermission</code> method
     * is called with a <code>PropertyPermission(key, "write")</code>
     * permission. This may result in a SecurityException being thrown.
     * If no exception is thrown, the specified property is removed.
     *
     * @param      key   the name of the system property to be removed.
     * @return     the previous string value of the system property,
     *             or <code>null</code> if there was no property with that key.
     *
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkPropertyAccess</code> method doesn't allow
     *              access to the specified system property.
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     * @see        #getProperty
     * @see        #setProperty
     * @see        java.util.Properties
     * @see        java.lang.SecurityException
     * @see        java.lang.SecurityManager#checkPropertiesAccess()
     * @since 1.5
     */
    public static String clearProperty(String key) {
        checkKey(key);
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new PropertyPermission(key, "write"));
        }

        return (String) props.remove(key);
    }

    private static void checkKey(String key) {
        if (key == null) {
            throw new NullPointerException("key can't be null");
        }
        if (key.equals("")) {
            throw new IllegalArgumentException("key can't be empty");
        }
    }

    /**
     * Gets the value of the specified environment variable. An
     * environment variable is a system-dependent external named
     * value.
     *
     * <p>If a security manager exists, its
     * {@link SecurityManager#checkPermission checkPermission}
     * method is called with a
     * <code>{@link RuntimePermission}("getenv."+name)</code>
     * permission.  This may result in a {@link SecurityException}
     * being thrown.  If no exception is thrown the value of the
     * variable <code>name</code> is returned.
     *
     * <p><a id="EnvironmentVSSystemProperties"><i>System
     * properties</i> and <i>environment variables</i></a> are both
     * conceptually mappings between names and values.  Both
     * mechanisms can be used to pass user-defined information to a
     * Java process.  Environment variables have a more global effect,
     * because they are visible to all descendants of the process
     * which defines them, not just the immediate Java subprocess.
     * They can have subtly different semantics, such as case
     * insensitivity, on different operating systems.  For these
     * reasons, environment variables are more likely to have
     * unintended side effects.  It is best to use system properties
     * where possible.  Environment variables should be used when a
     * global effect is desired, or when an external system interface
     * requires an environment variable (such as <code>PATH</code>).
     *
     * <p>On UNIX systems the alphabetic case of <code>name</code> is
     * typically significant, while on Microsoft Windows systems it is
     * typically not.  For example, the expression
     * <code>System.getenv("FOO").equals(System.getenv("foo"))</code>
     * is likely to be true on Microsoft Windows.
     *
     * @param  name the name of the environment variable
     * @return the string value of the variable, or <code>null</code>
     *         if the variable is not defined in the system environment
     * @throws NullPointerException if <code>name</code> is <code>null</code>
     * @throws SecurityException
     *         if a security manager exists and its
     *         {@link SecurityManager#checkPermission checkPermission}
     *         method doesn't allow access to the environment variable
     *         <code>name</code>
     * @see    #getenv()
     * @see    ProcessBuilder#environment()
     */
    public static String getenv(String name) {
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("getenv."+name));
        }

        return ProcessEnvironment.getenv(name);
    }


    /**
     * Returns an unmodifiable string map view of the current system environment.
     * The environment is a system-dependent mapping from names to
     * values which is passed from parent to child processes.
     *
     * <p>If the system does not support environment variables, an
     * empty map is returned.
     *
     * <p>The returned map will never contain null keys or values.
     * Attempting to query the presence of a null key or value will
     * throw a {@link NullPointerException}.  Attempting to query
     * the presence of a key or value which is not of type
     * {@link String} will throw a {@link ClassCastException}.
     *
     * <p>The returned map and its collection views may not obey the
     * general contract of the {@link Object#equals} and
     * {@link Object#hashCode} methods.
     *
     * <p>The returned map is typically case-sensitive on all platforms.
     *
     * <p>If a security manager exists, its
     * {@link SecurityManager#checkPermission checkPermission}
     * method is called with a
     * <code>{@link RuntimePermission}("getenv.*")</code>
     * permission.  This may result in a {@link SecurityException} being
     * thrown.
     *
     * <p>When passing information to a Java subprocess,
     * <a href=#EnvironmentVSSystemProperties>system properties</a>
     * are generally preferred over environment variables.
     *
     * @return the environment as a map of variable names to values
     * @throws SecurityException
     *         if a security manager exists and its
     *         {@link SecurityManager#checkPermission checkPermission}
     *         method doesn't allow access to the process environment
     * @see    #getenv(String)
     * @see    ProcessBuilder#environment()
     * @since  1.5
     */
    public static java.util.Map<String,String> getenv() {
        SecurityManager sm = getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new RuntimePermission("getenv.*"));
        }

        return ProcessEnvironment.getenv();
    }

    /**
     * {@code System.Logger} instances log messages that will be
     * routed to the underlying logging framework the {@link System.LoggerFinder
     * LoggerFinder} uses.
     * <p>
     * {@code System.Logger} instances are typically obtained from
     * the {@link java.lang.System System} class, by calling
     * {@link java.lang.System#getLogger(java.lang.String) System.getLogger(loggerName)}
     * or {@link java.lang.System#getLogger(java.lang.String, java.util.ResourceBundle)
     * System.getLogger(loggerName, bundle)}.
     *
     * @see java.lang.System#getLogger(java.lang.String)
     * @see java.lang.System#getLogger(java.lang.String, java.util.ResourceBundle)
     * @see java.lang.System.LoggerFinder
     *
     * @since 9
     *
     */
    public interface Logger {

        /**
         * System {@linkplain Logger loggers} levels.
         * <p>
         * A level has a {@linkplain #getName() name} and {@linkplain
         * #getSeverity() severity}.
         * Level values are {@link #ALL}, {@link #TRACE}, {@link #DEBUG},
         * {@link #INFO}, {@link #WARNING}, {@link #ERROR}, {@link #OFF},
         * by order of increasing severity.
         * <br>
         * {@link #ALL} and {@link #OFF}
         * are simple markers with severities mapped respectively to
         * {@link java.lang.Integer#MIN_VALUE Integer.MIN_VALUE} and
         * {@link java.lang.Integer#MAX_VALUE Integer.MAX_VALUE}.
         * <p>
         * <b>Severity values and Mapping to {@code java.util.logging.Level}.</b>
         * <p>
         * {@linkplain System.Logger.Level System logger levels} are mapped to
         * {@linkplain java.util.logging.Level  java.util.logging levels}
         * of corresponding severity.
         * <br>The mapping is as follows:
         * <br><br>
         * <table class="striped">
         * <caption>System.Logger Severity Level Mapping</caption>
         * <thead>
         * <tr><th scope="col">System.Logger Levels</th>
         *     <th scope="col">java.util.logging Levels</th>
         * </thead>
         * <tbody>
         * <tr><th scope="row">{@link Logger.Level#ALL ALL}</th>
         *     <td>{@link java.util.logging.Level#ALL ALL}</td>
         * <tr><th scope="row">{@link Logger.Level#TRACE TRACE}</th>
         *     <td>{@link java.util.logging.Level#FINER FINER}</td>
         * <tr><th scope="row">{@link Logger.Level#DEBUG DEBUG}</th>
         *     <td>{@link java.util.logging.Level#FINE FINE}</td>
         * <tr><th scope="row">{@link Logger.Level#INFO INFO}</th>
         *     <td>{@link java.util.logging.Level#INFO INFO}</td>
         * <tr><th scope="row">{@link Logger.Level#WARNING WARNING}</th>
         *     <td>{@link java.util.logging.Level#WARNING WARNING}</td>
         * <tr><th scope="row">{@link Logger.Level#ERROR ERROR}</th>
         *     <td>{@link java.util.logging.Level#SEVERE SEVERE}</td>
         * <tr><th scope="row">{@link Logger.Level#OFF OFF}</th>
         *     <td>{@link java.util.logging.Level#OFF OFF}</td>
         * </tbody>
         * </table>
         *
         * @since 9
         *
         * @see java.lang.System.LoggerFinder
         * @see java.lang.System.Logger
         */
        public enum Level {

            // for convenience, we're reusing java.util.logging.Level int values
            // the mapping logic in sun.util.logging.PlatformLogger depends
            // on this.
            /**
             * A marker to indicate that all levels are enabled.
             * This level {@linkplain #getSeverity() severity} is
             * {@link Integer#MIN_VALUE}.
             */
            ALL(Integer.MIN_VALUE),  // typically mapped to/from j.u.l.Level.ALL
            /**
             * {@code TRACE} level: usually used to log diagnostic information.
             * This level {@linkplain #getSeverity() severity} is
             * {@code 400}.
             */
            TRACE(400),   // typically mapped to/from j.u.l.Level.FINER
            /**
             * {@code DEBUG} level: usually used to log debug information traces.
             * This level {@linkplain #getSeverity() severity} is
             * {@code 500}.
             */
            DEBUG(500),   // typically mapped to/from j.u.l.Level.FINEST/FINE/CONFIG
            /**
             * {@code INFO} level: usually used to log information messages.
             * This level {@linkplain #getSeverity() severity} is
             * {@code 800}.
             */
            INFO(800),    // typically mapped to/from j.u.l.Level.INFO
            /**
             * {@code WARNING} level: usually used to log warning messages.
             * This level {@linkplain #getSeverity() severity} is
             * {@code 900}.
             */
            WARNING(900), // typically mapped to/from j.u.l.Level.WARNING
            /**
             * {@code ERROR} level: usually used to log error messages.
             * This level {@linkplain #getSeverity() severity} is
             * {@code 1000}.
             */
            ERROR(1000),  // typically mapped to/from j.u.l.Level.SEVERE
            /**
             * A marker to indicate that all levels are disabled.
             * This level {@linkplain #getSeverity() severity} is
             * {@link Integer#MAX_VALUE}.
             */
            OFF(Integer.MAX_VALUE);  // typically mapped to/from j.u.l.Level.OFF

            private final int severity;

            private Level(int severity) {
                this.severity = severity;
            }

            /**
             * Returns the name of this level.
             * @return this level {@linkplain #name()}.
             */
            public final String getName() {
                return name();
            }

            /**
             * Returns the severity of this level.
             * A higher severity means a more severe condition.
             * @return this level severity.
             */
            public final int getSeverity() {
                return severity;
            }
        }

        /**
         * Returns the name of this logger.
         *
         * @return the logger name.
         */
        public String getName();

        /**
         * Checks if a message of the given level would be logged by
         * this logger.
         *
         * @param level the log message level.
         * @return {@code true} if the given log message level is currently
         *         being logged.
         *
         * @throws NullPointerException if {@code level} is {@code null}.
         */
        public boolean isLoggable(Level level);

        /**
         * Logs a message.
         *
         * @implSpec The default implementation for this method calls
         * {@code this.log(level, (ResourceBundle)null, msg, (Object[])null);}
         *
         * @param level the log message level.
         * @param msg the string message (or a key in the message catalog, if
         * this logger is a {@link
         * LoggerFinder#getLocalizedLogger(java.lang.String,
         * java.util.ResourceBundle, java.lang.Module) localized logger});
         * can be {@code null}.
         *
         * @throws NullPointerException if {@code level} is {@code null}.
         */
        public default void log(Level level, String msg) {
            log(level, (ResourceBundle) null, msg, (Object[]) null);
        }

        /**
         * Logs a lazily supplied message.
         * <p>
         * If the logger is currently enabled for the given log message level
         * then a message is logged that is the result produced by the
         * given supplier function.  Otherwise, the supplier is not operated on.
         *
         * @implSpec When logging is enabled for the given level, the default
         * implementation for this method calls
         * {@code this.log(level, (ResourceBundle)null, msgSupplier.get(), (Object[])null);}
         *
         * @param level the log message level.
         * @param msgSupplier a supplier function that produces a message.
         *
         * @throws NullPointerException if {@code level} is {@code null},
         *         or {@code msgSupplier} is {@code null}.
         */
        public default void log(Level level, Supplier<String> msgSupplier) {
            Objects.requireNonNull(msgSupplier);
            if (isLoggable(Objects.requireNonNull(level))) {
                log(level, (ResourceBundle) null, msgSupplier.get(), (Object[]) null);
            }
        }

        /**
         * Logs a message produced from the given object.
         * <p>
         * If the logger is currently enabled for the given log message level then
         * a message is logged that, by default, is the result produced from
         * calling  toString on the given object.
         * Otherwise, the object is not operated on.
         *
         * @implSpec When logging is enabled for the given level, the default
         * implementation for this method calls
         * {@code this.log(level, (ResourceBundle)null, obj.toString(), (Object[])null);}
         *
         * @param level the log message level.
         * @param obj the object to log.
         *
         * @throws NullPointerException if {@code level} is {@code null}, or
         *         {@code obj} is {@code null}.
         */
        public default void log(Level level, Object obj) {
            Objects.requireNonNull(obj);
            if (isLoggable(Objects.requireNonNull(level))) {
                this.log(level, (ResourceBundle) null, obj.toString(), (Object[]) null);
            }
        }

        /**
         * Logs a message associated with a given throwable.
         *
         * @implSpec The default implementation for this method calls
         * {@code this.log(level, (ResourceBundle)null, msg, thrown);}
         *
         * @param level the log message level.
         * @param msg the string message (or a key in the message catalog, if
         * this logger is a {@link
         * LoggerFinder#getLocalizedLogger(java.lang.String,
         * java.util.ResourceBundle, java.lang.Module) localized logger});
         * can be {@code null}.
         * @param thrown a {@code Throwable} associated with the log message;
         *        can be {@code null}.
         *
         * @throws NullPointerException if {@code level} is {@code null}.
         */
        public default void log(Level level, String msg, Throwable thrown) {
            this.log(level, null, msg, thrown);
        }

        /**
         * Logs a lazily supplied message associated with a given throwable.
         * <p>
         * If the logger is currently enabled for the given log message level
         * then a message is logged that is the result produced by the
         * given supplier function.  Otherwise, the supplier is not operated on.
         *
         * @implSpec When logging is enabled for the given level, the default
         * implementation for this method calls
         * {@code this.log(level, (ResourceBundle)null, msgSupplier.get(), thrown);}
         *
         * @param level one of the log message level identifiers.
         * @param msgSupplier a supplier function that produces a message.
         * @param thrown a {@code Throwable} associated with log message;
         *               can be {@code null}.
         *
         * @throws NullPointerException if {@code level} is {@code null}, or
         *                               {@code msgSupplier} is {@code null}.
         */
        public default void log(Level level, Supplier<String> msgSupplier,
                Throwable thrown) {
            Objects.requireNonNull(msgSupplier);
            if (isLoggable(Objects.requireNonNull(level))) {
                this.log(level, null, msgSupplier.get(), thrown);
            }
        }

        /**
         * Logs a message with an optional list of parameters.
         *
         * @implSpec The default implementation for this method calls
         * {@code this.log(level, (ResourceBundle)null, format, params);}
         *
         * @param level one of the log message level identifiers.
         * @param format the string message format in {@link
         * java.text.MessageFormat} format, (or a key in the message
         * catalog, if this logger is a {@link
         * LoggerFinder#getLocalizedLogger(java.lang.String,
         * java.util.ResourceBundle, java.lang.Module) localized logger});
         * can be {@code null}.
         * @param params an optional list of parameters to the message (may be
         * none).
         *
         * @throws NullPointerException if {@code level} is {@code null}.
         */
        public default void log(Level level, String format, Object... params) {
            this.log(level, null, format, params);
        }

        /**
         * Logs a localized message associated with a given throwable.
         * <p>
         * If the given resource bundle is non-{@code null},  the {@code msg}
         * string is localized using the given resource bundle.
         * Otherwise the {@code msg} string is not localized.
         *
         * @param level the log message level.
         * @param bundle a resource bundle to localize {@code msg}; can be
         * {@code null}.
         * @param msg the string message (or a key in the message catalog,
         *            if {@code bundle} is not {@code null}); can be {@code null}.
         * @param thrown a {@code Throwable} associated with the log message;
         *        can be {@code null}.
         *
         * @throws NullPointerException if {@code level} is {@code null}.
         */
        public void log(Level level, ResourceBundle bundle, String msg,
                Throwable thrown);

        /**
         * Logs a message with resource bundle and an optional list of
         * parameters.
         * <p>
         * If the given resource bundle is non-{@code null},  the {@code format}
         * string is localized using the given resource bundle.
         * Otherwise the {@code format} string is not localized.
         *
         * @param level the log message level.
         * @param bundle a resource bundle to localize {@code format}; can be
         * {@code null}.
         * @param format the string message format in {@link
         * java.text.MessageFormat} format, (or a key in the message
         * catalog if {@code bundle} is not {@code null}); can be {@code null}.
         * @param params an optional list of parameters to the message (may be
         * none).
         *
         * @throws NullPointerException if {@code level} is {@code null}.
         */
        public void log(Level level, ResourceBundle bundle, String format,
                Object... params);


    }

    /**
     * The {@code LoggerFinder} service is responsible for creating, managing,
     * and configuring loggers to the underlying framework it uses.
     * <p>
     * A logger finder is a concrete implementation of this class that has a
     * zero-argument constructor and implements the abstract methods defined
     * by this class.
     * The loggers returned from a logger finder are capable of routing log
     * messages to the logging backend this provider supports.
     * A given invocation of the Java Runtime maintains a single
     * system-wide LoggerFinder instance that is loaded as follows:
     * <ul>
     *    <li>First it finds any custom {@code LoggerFinder} provider
     *        using the {@link java.util.ServiceLoader} facility with the
     *        {@linkplain ClassLoader#getSystemClassLoader() system class
     *        loader}.</li>
     *    <li>If no {@code LoggerFinder} provider is found, the system default
     *        {@code LoggerFinder} implementation will be used.</li>
     * </ul>
     * <p>
     * An application can replace the logging backend
     * <i>even when the java.logging module is present</i>, by simply providing
     * and declaring an implementation of the {@link LoggerFinder} service.
     * <p>
     * <b>Default Implementation</b>
     * <p>
     * The system default {@code LoggerFinder} implementation uses
     * {@code java.util.logging} as the backend framework when the
     * {@code java.logging} module is present.
     * It returns a {@linkplain System.Logger logger} instance
     * that will route log messages to a {@link java.util.logging.Logger
     * java.util.logging.Logger}. Otherwise, if {@code java.logging} is not
     * present, the default implementation will return a simple logger
     * instance that will route log messages of {@code INFO} level and above to
     * the console ({@code System.err}).
     * <p>
     * <b>Logging Configuration</b>
     * <p>
     * {@linkplain Logger Logger} instances obtained from the
     * {@code LoggerFinder} factory methods are not directly configurable by
     * the application. Configuration is the responsibility of the underlying
     * logging backend, and usually requires using APIs specific to that backend.
     * <p>For the default {@code LoggerFinder} implementation
     * using {@code java.util.logging} as its backend, refer to
     * {@link java.util.logging java.util.logging} for logging configuration.
     * For the default {@code LoggerFinder} implementation returning simple loggers
     * when the {@code java.logging} module is absent, the configuration
     * is implementation dependent.
     * <p>
     * Usually an application that uses a logging framework will log messages
     * through a logger facade defined (or supported) by that framework.
     * Applications that wish to use an external framework should log
     * through the facade associated with that framework.
     * <p>
     * A system class that needs to log messages will typically obtain
     * a {@link System.Logger} instance to route messages to the logging
     * framework selected by the application.
     * <p>
     * Libraries and classes that only need loggers to produce log messages
     * should not attempt to configure loggers by themselves, as that
     * would make them dependent from a specific implementation of the
     * {@code LoggerFinder} service.
     * <p>
     * In addition, when a security manager is present, loggers provided to
     * system classes should not be directly configurable through the logging
     * backend without requiring permissions.
     * <br>
     * It is the responsibility of the provider of
     * the concrete {@code LoggerFinder} implementation to ensure that
     * these loggers are not configured by untrusted code without proper
     * permission checks, as configuration performed on such loggers usually
     * affects all applications in the same Java Runtime.
     * <p>
     * <b>Message Levels and Mapping to backend levels</b>
     * <p>
     * A logger finder is responsible for mapping from a {@code
     * System.Logger.Level} to a level supported by the logging backend it uses.
     * <br>The default LoggerFinder using {@code java.util.logging} as the backend
     * maps {@code System.Logger} levels to
     * {@linkplain java.util.logging.Level java.util.logging} levels
     * of corresponding severity - as described in {@link Logger.Level
     * Logger.Level}.
     *
     * @see java.lang.System
     * @see java.lang.System.Logger
     *
     * @since 9
     */
    public static abstract class LoggerFinder {
        /**
         * The {@code RuntimePermission("loggerFinder")} is
         * necessary to subclass and instantiate the {@code LoggerFinder} class,
         * as well as to obtain loggers from an instance of that class.
         */
        static final RuntimePermission LOGGERFINDER_PERMISSION =
                new RuntimePermission("loggerFinder");

        /**
         * Creates a new instance of {@code LoggerFinder}.
         *
         * @implNote It is recommended that a {@code LoggerFinder} service
         *   implementation does not perform any heavy initialization in its
         *   constructor, in order to avoid possible risks of deadlock or class
         *   loading cycles during the instantiation of the service provider.
         *
         * @throws SecurityException if a security manager is present and its
         *         {@code checkPermission} method doesn't allow the
         *         {@code RuntimePermission("loggerFinder")}.
         */
        protected LoggerFinder() {
            this(checkPermission());
        }

        private LoggerFinder(Void unused) {
            // nothing to do.
        }

        private static Void checkPermission() {
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(LOGGERFINDER_PERMISSION);
            }
            return null;
        }

        /**
         * Returns an instance of {@link Logger Logger}
         * for the given {@code module}.
         *
         * @param name the name of the logger.
         * @param module the module for which the logger is being requested.
         *
         * @return a {@link Logger logger} suitable for use within the given
         *         module.
         * @throws NullPointerException if {@code name} is {@code null} or
         *        {@code module} is {@code null}.
         * @throws SecurityException if a security manager is present and its
         *         {@code checkPermission} method doesn't allow the
         *         {@code RuntimePermission("loggerFinder")}.
         */
        public abstract Logger getLogger(String name, Module module);

        /**
         * Returns a localizable instance of {@link Logger Logger}
         * for the given {@code module}.
         * The returned logger will use the provided resource bundle for
         * message localization.
         *
         * @implSpec By default, this method calls {@link
         * #getLogger(java.lang.String, java.lang.Module)
         * this.getLogger(name, module)} to obtain a logger, then wraps that
         * logger in a {@link Logger} instance where all methods that do not
         * take a {@link ResourceBundle} as parameter are redirected to one
         * which does - passing the given {@code bundle} for
         * localization. So for instance, a call to {@link
         * Logger#log(Level, String) Logger.log(Level.INFO, msg)}
         * will end up as a call to {@link
         * Logger#log(Level, ResourceBundle, String, Object...)
         * Logger.log(Level.INFO, bundle, msg, (Object[])null)} on the wrapped
         * logger instance.
         * Note however that by default, string messages returned by {@link
         * java.util.function.Supplier Supplier&lt;String&gt;} will not be
         * localized, as it is assumed that such strings are messages which are
         * already constructed, rather than keys in a resource bundle.
         * <p>
         * An implementation of {@code LoggerFinder} may override this method,
         * for example, when the underlying logging backend provides its own
         * mechanism for localizing log messages, then such a
         * {@code LoggerFinder} would be free to return a logger
         * that makes direct use of the mechanism provided by the backend.
         *
         * @param name    the name of the logger.
         * @param bundle  a resource bundle; can be {@code null}.
         * @param module  the module for which the logger is being requested.
         * @return an instance of {@link Logger Logger}  which will use the
         * provided resource bundle for message localization.
         *
         * @throws NullPointerException if {@code name} is {@code null} or
         *         {@code module} is {@code null}.
         * @throws SecurityException if a security manager is present and its
         *         {@code checkPermission} method doesn't allow the
         *         {@code RuntimePermission("loggerFinder")}.
         */
        public Logger getLocalizedLogger(String name, ResourceBundle bundle,
                                         Module module) {
            return new LocalizedLoggerWrapper<>(getLogger(name, module), bundle);
        }

        /**
         * Returns the {@code LoggerFinder} instance. There is one
         * single system-wide {@code LoggerFinder} instance in
         * the Java Runtime.  See the class specification of how the
         * {@link LoggerFinder LoggerFinder} implementation is located and
         * loaded.

         * @return the {@link LoggerFinder LoggerFinder} instance.
         * @throws SecurityException if a security manager is present and its
         *         {@code checkPermission} method doesn't allow the
         *         {@code RuntimePermission("loggerFinder")}.
         */
        public static LoggerFinder getLoggerFinder() {
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(LOGGERFINDER_PERMISSION);
            }
            return accessProvider();
        }


        private static volatile LoggerFinder service;
        static LoggerFinder accessProvider() {
            // We do not need to synchronize: LoggerFinderLoader will
            // always return the same instance, so if we don't have it,
            // just fetch it again.
            if (service == null) {
                PrivilegedAction<LoggerFinder> pa =
                        () -> LoggerFinderLoader.getLoggerFinder();
                service = AccessController.doPrivileged(pa, null,
                        LOGGERFINDER_PERMISSION);
            }
            return service;
        }

    }


    /**
     * Returns an instance of {@link Logger Logger} for the caller's
     * use.
     *
     * @implSpec
     * Instances returned by this method route messages to loggers
     * obtained by calling {@link LoggerFinder#getLogger(java.lang.String,
     * java.lang.Module) LoggerFinder.getLogger(name, module)}, where
     * {@code module} is the caller's module.
     * In cases where {@code System.getLogger} is called from a context where
     * there is no caller frame on the stack (e.g when called directly
     * from a JNI attached thread), {@code IllegalCallerException} is thrown.
     * To obtain a logger in such a context, use an auxiliary class that will
     * implicitly be identified as the caller, or use the system {@link
     * LoggerFinder#getLoggerFinder() LoggerFinder} to obtain a logger instead.
     * Note that doing the latter may eagerly initialize the underlying
     * logging system.
     *
     * @apiNote
     * This method may defer calling the {@link
     * LoggerFinder#getLogger(java.lang.String, java.lang.Module)
     * LoggerFinder.getLogger} method to create an actual logger supplied by
     * the logging backend, for instance, to allow loggers to be obtained during
     * the system initialization time.
     *
     * @param name the name of the logger.
     * @return an instance of {@link Logger} that can be used by the calling
     *         class.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalCallerException if there is no Java caller frame on the
     *         stack.
     *
     * @since 9
     */
    @CallerSensitive
    public static Logger getLogger(String name) {
        Objects.requireNonNull(name);
        final Class<?> caller = Reflection.getCallerClass();
        if (caller == null) {
            throw new IllegalCallerException("no caller frame");
        }
        return LazyLoggers.getLogger(name, caller.getModule());
    }

    /**
     * Returns a localizable instance of {@link Logger
     * Logger} for the caller's use.
     * The returned logger will use the provided resource bundle for message
     * localization.
     *
     * @implSpec
     * The returned logger will perform message localization as specified
     * by {@link LoggerFinder#getLocalizedLogger(java.lang.String,
     * java.util.ResourceBundle, java.lang.Module)
     * LoggerFinder.getLocalizedLogger(name, bundle, module)}, where
     * {@code module} is the caller's module.
     * In cases where {@code System.getLogger} is called from a context where
     * there is no caller frame on the stack (e.g when called directly
     * from a JNI attached thread), {@code IllegalCallerException} is thrown.
     * To obtain a logger in such a context, use an auxiliary class that
     * will implicitly be identified as the caller, or use the system {@link
     * LoggerFinder#getLoggerFinder() LoggerFinder} to obtain a logger instead.
     * Note that doing the latter may eagerly initialize the underlying
     * logging system.
     *
     * @apiNote
     * This method is intended to be used after the system is fully initialized.
     * This method may trigger the immediate loading and initialization
     * of the {@link LoggerFinder} service, which may cause issues if the
     * Java Runtime is not ready to initialize the concrete service
     * implementation yet.
     * System classes which may be loaded early in the boot sequence and
     * need to log localized messages should create a logger using
     * {@link #getLogger(java.lang.String)} and then use the log methods that
     * take a resource bundle as parameter.
     *
     * @param name    the name of the logger.
     * @param bundle  a resource bundle.
     * @return an instance of {@link Logger} which will use the provided
     * resource bundle for message localization.
     * @throws NullPointerException if {@code name} is {@code null} or
     *         {@code bundle} is {@code null}.
     * @throws IllegalCallerException if there is no Java caller frame on the
     *         stack.
     *
     * @since 9
     */
    @CallerSensitive
    public static Logger getLogger(String name, ResourceBundle bundle) {
        final ResourceBundle rb = Objects.requireNonNull(bundle);
        Objects.requireNonNull(name);
        final Class<?> caller = Reflection.getCallerClass();
        if (caller == null) {
            throw new IllegalCallerException("no caller frame");
        }
        final SecurityManager sm = System.getSecurityManager();
        // We don't use LazyLoggers if a resource bundle is specified.
        // Bootstrap sensitive classes in the JDK do not use resource bundles
        // when logging. This could be revisited later, if it needs to.
        if (sm != null) {
            final PrivilegedAction<Logger> pa =
                    () -> LoggerFinder.accessProvider()
                            .getLocalizedLogger(name, rb, caller.getModule());
            return AccessController.doPrivileged(pa, null,
                                         LoggerFinder.LOGGERFINDER_PERMISSION);
        }
        return LoggerFinder.accessProvider()
                .getLocalizedLogger(name, rb, caller.getModule());
    }

    /**
     * Terminates the currently running Java Virtual Machine. The
     * argument serves as a status code; by convention, a nonzero status
     * code indicates abnormal termination.
     * <p>
     * This method calls the <code>exit</code> method in class
     * <code>Runtime</code>. This method never returns normally.
     * <p>
     * The call <code>System.exit(n)</code> is effectively equivalent to
     * the call:
     * <blockquote><pre>
     * Runtime.getRuntime().exit(n)
     * </pre></blockquote>
     *
     * @param      status   exit status.
     * @throws  SecurityException
     *        if a security manager exists and its <code>checkExit</code>
     *        method doesn't allow exit with the specified status.
     * @see        java.lang.Runtime#exit(int)
     */
    public static void exit(int status) {
        Runtime.getRuntime().exit(status);
    }

    /**
     * Runs the garbage collector.
     * <p>
     * Calling the <code>gc</code> method suggests that the Java Virtual
     * Machine expend effort toward recycling unused objects in order to
     * make the memory they currently occupy available for quick reuse.
     * When control returns from the method call, the Java Virtual
     * Machine has made a best effort to reclaim space from all discarded
     * objects.
     * <p>
     * The call <code>System.gc()</code> is effectively equivalent to the
     * call:
     * <blockquote><pre>
     * Runtime.getRuntime().gc()
     * </pre></blockquote>
     *
     * @see     java.lang.Runtime#gc()
     */
    public static void gc() {
        Runtime.getRuntime().gc();
    }

    /**
     * Runs the finalization methods of any objects pending finalization.
     * <p>
     * Calling this method suggests that the Java Virtual Machine expend
     * effort toward running the <code>finalize</code> methods of objects
     * that have been found to be discarded but whose <code>finalize</code>
     * methods have not yet been run. When control returns from the
     * method call, the Java Virtual Machine has made a best effort to
     * complete all outstanding finalizations.
     * <p>
     * The call <code>System.runFinalization()</code> is effectively
     * equivalent to the call:
     * <blockquote><pre>
     * Runtime.getRuntime().runFinalization()
     * </pre></blockquote>
     *
     * @see     java.lang.Runtime#runFinalization()
     */
    public static void runFinalization() {
        Runtime.getRuntime().runFinalization();
    }

    /**
     * Enable or disable finalization on exit; doing so specifies that the
     * finalizers of all objects that have finalizers that have not yet been
     * automatically invoked are to be run before the Java runtime exits.
     * By default, finalization on exit is disabled.
     *
     * <p>If there is a security manager,
     * its <code>checkExit</code> method is first called
     * with 0 as its argument to ensure the exit is allowed.
     * This could result in a SecurityException.
     *
     * @deprecated  This method is inherently unsafe.  It may result in
     *      finalizers being called on live objects while other threads are
     *      concurrently manipulating those objects, resulting in erratic
     *      behavior or deadlock.
     *      This method is subject to removal in a future version of Java SE.
     * @param value indicating enabling or disabling of finalization
     * @throws  SecurityException
     *        if a security manager exists and its <code>checkExit</code>
     *        method doesn't allow the exit.
     *
     * @see     java.lang.Runtime#exit(int)
     * @see     java.lang.Runtime#gc()
     * @see     java.lang.SecurityManager#checkExit(int)
     * @since   1.1
     */
    @Deprecated(since="1.2", forRemoval=true)
    @SuppressWarnings("removal")
    public static void runFinalizersOnExit(boolean value) {
        Runtime.runFinalizersOnExit(value);
    }

    /**
     * Loads the native library specified by the filename argument.  The filename
     * argument must be an absolute path name.
     *
     * If the filename argument, when stripped of any platform-specific library
     * prefix, path, and file extension, indicates a library whose name is,
     * for example, L, and a native library called L is statically linked
     * with the VM, then the JNI_OnLoad_L function exported by the library
     * is invoked rather than attempting to load a dynamic library.
     * A filename matching the argument does not have to exist in the
     * file system.
     * See the <a href="{@docRoot}/../specs/jni/index.html"> JNI Specification</a>
     * for more details.
     *
     * Otherwise, the filename argument is mapped to a native library image in
     * an implementation-dependent manner.
     *
     * <p>
     * The call <code>System.load(name)</code> is effectively equivalent
     * to the call:
     * <blockquote><pre>
     * Runtime.getRuntime().load(name)
     * </pre></blockquote>
     *
     * @param      filename   the file to load.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkLink</code> method doesn't allow
     *             loading of the specified dynamic library
     * @exception  UnsatisfiedLinkError  if either the filename is not an
     *             absolute path name, the native library is not statically
     *             linked with the VM, or the library cannot be mapped to
     *             a native library image by the host system.
     * @exception  NullPointerException if <code>filename</code> is
     *             <code>null</code>
     * @see        java.lang.Runtime#load(java.lang.String)
     * @see        java.lang.SecurityManager#checkLink(java.lang.String)
     */
    @CallerSensitive
    public static void load(String filename) {
        Runtime.getRuntime().load0(Reflection.getCallerClass(), filename);
    }

    /**
     * Loads the native library specified by the <code>libname</code>
     * argument.  The <code>libname</code> argument must not contain any platform
     * specific prefix, file extension or path. If a native library
     * called <code>libname</code> is statically linked with the VM, then the
     * JNI_OnLoad_<code>libname</code> function exported by the library is invoked.
     * See the <a href="{@docRoot}/../specs/jni/index.html"> JNI Specification</a>
     * for more details.
     *
     * Otherwise, the libname argument is loaded from a system library
     * location and mapped to a native library image in an implementation-
     * dependent manner.
     * <p>
     * The call <code>System.loadLibrary(name)</code> is effectively
     * equivalent to the call
     * <blockquote><pre>
     * Runtime.getRuntime().loadLibrary(name)
     * </pre></blockquote>
     *
     * @param      libname   the name of the library.
     * @exception  SecurityException  if a security manager exists and its
     *             <code>checkLink</code> method doesn't allow
     *             loading of the specified dynamic library
     * @exception  UnsatisfiedLinkError if either the libname argument
     *             contains a file path, the native library is not statically
     *             linked with the VM,  or the library cannot be mapped to a
     *             native library image by the host system.
     * @exception  NullPointerException if <code>libname</code> is
     *             <code>null</code>
     * @see        java.lang.Runtime#loadLibrary(java.lang.String)
     * @see        java.lang.SecurityManager#checkLink(java.lang.String)
     */
    @CallerSensitive
    public static void loadLibrary(String libname) {
        Runtime.getRuntime().loadLibrary0(Reflection.getCallerClass(), libname);
    }

    /**
     * Maps a library name into a platform-specific string representing
     * a native library.
     *
     * @param      libname the name of the library.
     * @return     a platform-dependent native library name.
     * @exception  NullPointerException if <code>libname</code> is
     *             <code>null</code>
     * @see        java.lang.System#loadLibrary(java.lang.String)
     * @see        java.lang.ClassLoader#findLibrary(java.lang.String)
     * @since      1.2
     */
    public static native String mapLibraryName(String libname);

    /**
     * Create PrintStream for stdout/err based on encoding.
     */
    private static PrintStream newPrintStream(FileOutputStream fos, String enc) {
       if (enc != null) {
            try {
                return new PrintStream(new BufferedOutputStream(fos, 128), true, enc);
            } catch (UnsupportedEncodingException uee) {}
        }
        return new PrintStream(new BufferedOutputStream(fos, 128), true);
    }

    /**
     * Logs an exception/error at initialization time to stdout or stderr.
     *
     * @param printToStderr to print to stderr rather than stdout
     * @param printStackTrace to print the stack trace
     * @param msg the message to print before the exception, can be {@code null}
     * @param e the exception or error
     */
    private static void logInitException(boolean printToStderr,
                                         boolean printStackTrace,
                                         String msg,
                                         Throwable e) {
        if (VM.initLevel() < 1) {
            throw new InternalError("system classes not initialized");
        }
        PrintStream log = (printToStderr) ? err : out;
        if (msg != null) {
            log.println(msg);
        }
        if (printStackTrace) {
            e.printStackTrace(log);
        } else {
            log.println(e);
            for (Throwable suppressed : e.getSuppressed()) {
                log.println("Suppressed: " + suppressed);
            }
            Throwable cause = e.getCause();
            if (cause != null) {
                log.println("Caused by: " + cause);
            }
        }
    }

    /**
     * Initialize the system class.  Called after thread initialization.
     */
    private static void initPhase1() {

        // VM might invoke JNU_NewStringPlatform() to set those encoding
        // sensitive properties (user.home, user.name, boot.class.path, etc.)
        // during "props" initialization, in which it may need access, via
        // System.getProperty(), to the related system encoding property that
        // have been initialized (put into "props") at early stage of the
        // initialization. So make sure the "props" is available at the
        // very beginning of the initialization and all system properties to
        // be put into it directly.
        props = new Properties(84);
        initProperties(props);  // initialized by the VM

        // There are certain system configurations that may be controlled by
        // VM options such as the maximum amount of direct memory and
        // Integer cache size used to support the object identity semantics
        // of autoboxing.  Typically, the library will obtain these values
        // from the properties set by the VM.  If the properties are for
        // internal implementation use only, these properties should be
        // removed from the system properties.
        //
        // See java.lang.Integer.IntegerCache and the
        // VM.saveAndRemoveProperties method for example.
        //
        // Save a private copy of the system properties object that
        // can only be accessed by the internal implementation.  Remove
        // certain system properties that are not intended for public access.
        VM.saveAndRemoveProperties(props);

        lineSeparator = props.getProperty("line.separator");
        VersionProps.init();

        FileInputStream fdIn = new FileInputStream(FileDescriptor.in);
        FileOutputStream fdOut = new FileOutputStream(FileDescriptor.out);
        FileOutputStream fdErr = new FileOutputStream(FileDescriptor.err);
        setIn0(new BufferedInputStream(fdIn));
        setOut0(newPrintStream(fdOut, props.getProperty("sun.stdout.encoding")));
        setErr0(newPrintStream(fdErr, props.getProperty("sun.stderr.encoding")));

        // Setup Java signal handlers for HUP, TERM, and INT (where available).
        Terminator.setup();

        // Initialize any miscellaneous operating system settings that need to be
        // set for the class libraries. Currently this is no-op everywhere except
        // for Windows where the process-wide error mode is set before the java.io
        // classes are used.
        VM.initializeOSEnvironment();

        // The main thread is not added to its thread group in the same
        // way as other threads; we must do it ourselves here.
        Thread current = Thread.currentThread();
        current.getThreadGroup().add(current);

        // register shared secrets
        setJavaLangAccess();

        // Subsystems that are invoked during initialization can invoke
        // VM.isBooted() in order to avoid doing things that should
        // wait until the VM is fully initialized. The initialization level
        // is incremented from 0 to 1 here to indicate the first phase of
        // initialization has completed.
        // IMPORTANT: Ensure that this remains the last initialization action!
        VM.initLevel(1);
    }

    // @see #initPhase2()
    static ModuleLayer bootLayer;

    /*
     * Invoked by VM.  Phase 2 module system initialization.
     * Only classes in java.base can be loaded in this phase.
     *
     * @param printToStderr print exceptions to stderr rather than stdout
     * @param printStackTrace print stack trace when exception occurs
     *
     * @return JNI_OK for success, JNI_ERR for failure
     */
    private static int initPhase2(boolean printToStderr, boolean printStackTrace) {
        try {
            bootLayer = ModuleBootstrap.boot();
        } catch (Exception | Error e) {
            logInitException(printToStderr, printStackTrace,
                             "Error occurred during initialization of boot layer", e);
            return -1; // JNI_ERR
        }

        // module system initialized
        VM.initLevel(2);

        return 0; // JNI_OK
    }

    /*
     * Invoked by VM.  Phase 3 is the final system initialization:
     * 1. set security manager
     * 2. set system class loader
     * 3. set TCCL
     *
     * This method must be called after the module system initialization.
     * The security manager and system class loader may be custom class from
     * the application classpath or modulepath.
     */
    private static void initPhase3() {
        // set security manager
        String cn = System.getProperty("java.security.manager");
        if (cn != null) {
            if (cn.isEmpty() || "default".equals(cn)) {
                System.setSecurityManager(new SecurityManager());
            } else {
                try {
                    Class<?> c = Class.forName(cn, false, ClassLoader.getBuiltinAppClassLoader());
                    Constructor<?> ctor = c.getConstructor();
                    // Must be a public subclass of SecurityManager with
                    // a public no-arg constructor
                    if (!SecurityManager.class.isAssignableFrom(c) ||
                            !Modifier.isPublic(c.getModifiers()) ||
                            !Modifier.isPublic(ctor.getModifiers())) {
                        throw new Error("Could not create SecurityManager: " + ctor.toString());
                    }
                    // custom security manager implementation may be in unnamed module
                    // or a named module but non-exported package
                    ctor.setAccessible(true);
                    SecurityManager sm = (SecurityManager) ctor.newInstance();
                    System.setSecurityManager(sm);
                } catch (Exception e) {
                    throw new Error("Could not create SecurityManager", e);
                }
            }
        }

        // initializing the system class loader
        VM.initLevel(3);

        // system class loader initialized
        ClassLoader scl = ClassLoader.initSystemClassLoader();

        // set TCCL
        Thread.currentThread().setContextClassLoader(scl);

        // system is fully initialized
        VM.initLevel(4);
    }

    private static void setJavaLangAccess() {
        // Allow privileged classes outside of java.lang
        SharedSecrets.setJavaLangAccess(new JavaLangAccess() {
            public List<Method> getDeclaredPublicMethods(Class<?> klass, String name, Class<?>... parameterTypes) {
                return klass.getDeclaredPublicMethods(name, parameterTypes);
            }
            public jdk.internal.reflect.ConstantPool getConstantPool(Class<?> klass) {
                return klass.getConstantPool();
            }
            public boolean casAnnotationType(Class<?> klass, AnnotationType oldType, AnnotationType newType) {
                return klass.casAnnotationType(oldType, newType);
            }
            public AnnotationType getAnnotationType(Class<?> klass) {
                return klass.getAnnotationType();
            }
            public Map<Class<? extends Annotation>, Annotation> getDeclaredAnnotationMap(Class<?> klass) {
                return klass.getDeclaredAnnotationMap();
            }
            public byte[] getRawClassAnnotations(Class<?> klass) {
                return klass.getRawAnnotations();
            }
            public byte[] getRawClassTypeAnnotations(Class<?> klass) {
                return klass.getRawTypeAnnotations();
            }
            public byte[] getRawExecutableTypeAnnotations(Executable executable) {
                return Class.getExecutableTypeAnnotationBytes(executable);
            }
            public <E extends Enum<E>>
            E[] getEnumConstantsShared(Class<E> klass) {
                return klass.getEnumConstantsShared();
            }
            public void blockedOn(Thread t, Interruptible b) {
                t.blockedOn(b);
            }
            public void registerShutdownHook(int slot, boolean registerShutdownInProgress, Runnable hook) {
                Shutdown.add(slot, registerShutdownInProgress, hook);
            }
            public Thread newThreadWithAcc(Runnable target, AccessControlContext acc) {
                return new Thread(target, acc);
            }
            @SuppressWarnings("deprecation")
            public void invokeFinalize(Object o) throws Throwable {
                o.finalize();
            }
            public ConcurrentHashMap<?, ?> createOrGetClassLoaderValueMap(ClassLoader cl) {
                return cl.createOrGetClassLoaderValueMap();
            }
            public Class<?> defineClass(ClassLoader loader, String name, byte[] b, ProtectionDomain pd, String source) {
                return ClassLoader.defineClass1(loader, name, b, 0, b.length, pd, source);
            }
            public Class<?> findBootstrapClassOrNull(ClassLoader cl, String name) {
                return cl.findBootstrapClassOrNull(name);
            }
            public Package definePackage(ClassLoader cl, String name, Module module) {
                return cl.definePackage(name, module);
            }
            public String fastUUID(long lsb, long msb) {
                return Long.fastUUID(lsb, msb);
            }
            public void addNonExportedPackages(ModuleLayer layer) {
                SecurityManager.addNonExportedPackages(layer);
            }
            public void invalidatePackageAccessCache() {
                SecurityManager.invalidatePackageAccessCache();
            }
            public Module defineModule(ClassLoader loader,
                                       ModuleDescriptor descriptor,
                                       URI uri) {
                return new Module(null, loader, descriptor, uri);
            }
            public Module defineUnnamedModule(ClassLoader loader) {
                return new Module(loader);
            }
            public void addReads(Module m1, Module m2) {
                m1.implAddReads(m2);
            }
            public void addReadsAllUnnamed(Module m) {
                m.implAddReadsAllUnnamed();
            }
            public void addExports(Module m, String pn, Module other) {
                m.implAddExports(pn, other);
            }
            public void addExportsToAllUnnamed(Module m, String pn) {
                m.implAddExportsToAllUnnamed(pn);
            }
            public void addOpens(Module m, String pn, Module other) {
                m.implAddOpens(pn, other);
            }
            public void addOpensToAllUnnamed(Module m, String pn) {
                m.implAddOpensToAllUnnamed(pn);
            }
            public void addOpensToAllUnnamed(Module m, Iterator<String> packages) {
                m.implAddOpensToAllUnnamed(packages);
            }
            public void addUses(Module m, Class<?> service) {
                m.implAddUses(service);
            }
            public boolean isReflectivelyExported(Module m, String pn, Module other) {
                return m.isReflectivelyExported(pn, other);
            }
            public boolean isReflectivelyOpened(Module m, String pn, Module other) {
                return m.isReflectivelyOpened(pn, other);
            }
            public ServicesCatalog getServicesCatalog(ModuleLayer layer) {
                return layer.getServicesCatalog();
            }
            public Stream<ModuleLayer> layers(ModuleLayer layer) {
                return layer.layers();
            }
            public Stream<ModuleLayer> layers(ClassLoader loader) {
                return ModuleLayer.layers(loader);
            }

            public String newStringUTF8NoRepl(byte[] bytes, int off, int len) {
                return StringCoding.newStringUTF8NoRepl(bytes, off, len);
            }

            public byte[] getBytesUTF8NoRepl(String s) {
                return StringCoding.getBytesUTF8NoRepl(s);
            }

        });
    }
}
