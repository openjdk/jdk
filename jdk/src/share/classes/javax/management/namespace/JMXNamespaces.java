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

import com.sun.jmx.defaults.JmxProperties;
import com.sun.jmx.namespace.ObjectNameRouter;
import com.sun.jmx.namespace.serial.RewritingProcessor;
import com.sun.jmx.namespace.RoutingConnectionProxy;
import com.sun.jmx.namespace.RoutingServerProxy;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Static constants and utility methods to help work with
 * JMX name spaces.  There are no instances of this class.
 * @since 1.7
 */
public class JMXNamespaces {

    /**
     * A logger for this class.
     **/
    private static final Logger LOG = JmxProperties.NAMESPACE_LOGGER;

    /** Creates a new instance of JMXNamespaces */
    private JMXNamespaces() {
    }

    /**
     * The name space separator. This is an alias for {@link
     * ObjectName#NAMESPACE_SEPARATOR}.
     **/
    public static final String NAMESPACE_SEPARATOR =
            ObjectName.NAMESPACE_SEPARATOR;
    private static final int NAMESPACE_SEPARATOR_LENGTH =
            NAMESPACE_SEPARATOR.length();


    /**
     * Creates a new {@code MBeanServerConnection} proxy on a
     * {@linkplain javax.management.namespace sub name space}
     * of the given parent.
     *
     * @param parent The parent {@code MBeanServerConnection} that contains
     *               the name space.
     * @param namespace  The {@linkplain javax.management.namespace
     *               name space} in which to narrow.
     * @return A new {@code MBeanServerConnection} proxy that shows the content
     *         of that name space.
     * @throws IllegalArgumentException if either argument is null,
     * or the name space does not exist, or if a proxy for that name space
     * cannot be created.  The {@linkplain Throwable#getCause() cause} of
     * this exception will be an {@link InstanceNotFoundException} if and only
     * if the name space is found not to exist.
     */
    public static MBeanServerConnection narrowToNamespace(
                        MBeanServerConnection parent,
                        String namespace) {
        if (LOG.isLoggable(Level.FINER))
            LOG.finer("Making MBeanServerConnection for: " +namespace);
        return RoutingConnectionProxy.cd(parent, namespace, true);
    }

    /**
     * Creates a new {@code MBeanServer} proxy on a
     * {@linkplain javax.management.namespace sub name space}
     * of the given parent.
     *
     * @param parent The parent {@code MBeanServer} that contains
     *               the name space.
     * @param namespace  The {@linkplain javax.management.namespace
     *               name space} in which to narrow.
     * @return A new {@code MBeanServer} proxy that shows the content
     *         of that name space.
     * @throws IllegalArgumentException if either argument is null,
     * or the name space does not exist, or if a proxy for that name space
     * cannot be created.  The {@linkplain Throwable#getCause() cause} of
     * this exception will be an {@link InstanceNotFoundException} if and only
     * if the name space is found not to exist.
     */
    public static MBeanServer narrowToNamespace(MBeanServer parent,
            String namespace) {
        if (LOG.isLoggable(Level.FINER))
            LOG.finer("Making MBeanServer for: " +namespace);
        return RoutingServerProxy.cd(parent, namespace, true);
    }

    /**
     * Returns an object that is the same as the given object except that
     * any {@link ObjectName} it might contain has its domain modified.
     * The returned object might be identical to the given object if it
     * does not contain any {@code ObjectName} values or if none of them
     * were modified.
     * This method will replace a prefix ({@code toRemove}) from the path of
     * the ObjectNames contained in {@code obj} by another prefix
     * ({@code toAdd}).
     * Therefore, all contained ObjectNames must have a path that start with
     * the given {@code toRemove} prefix. If one of them doesn't, an {@link
     * IllegalArgumentException} is thrown.
     * <p>
     * For instance, if {@code obj} contains the ObjectName
     * {@code x//y//z//d:k=x}, and {@code toAdd} is {@code v//w}, and
     * {@code toRemove}
     *  is {@code x//y} this method will return a copy of {@code obj} that
     * contains {@code v//w//z//d:k=x}.<br>
     * On the other hand, if {@code obj} contains the ObjectName
     * {@code x//y//z//d:k=x}, and {@code toAdd} is {@code v//w}, and
     * {@code toRemove} is {@code v} this method
     * will raise an exception, because {@code x//y//z//d:k=x} doesn't start
     * with {@code v}
     * </p>
     * <p>Note: the default implementation of this method can use the
     *   Java serialization framework to clone and replace ObjectNames in the
     *   provided {@code obj}. It will usually fail if {@code obj} is not
     *   Java serializable, or contains objects which are not Java
     *   serializable.
     * </p>
     * @param obj    The object to deep-rewrite
     * @param toRemove a prefix already present in contained ObjectNames.
     *        If {@code toRemove} is the empty string {@code ""}, nothing
     *        will be removed from the contained ObjectNames.
     * @param toAdd the prefix that will replace (@code toRemove} in contained
     *  ObjectNames.
     *        If {@code toAdd} is the empty string {@code ""}, nothing
     *        will be added to the contained ObjectNames.
     * @return the rewritten object, or possibly {@code obj} if nothing needed
     * to be changed.
     * @throws IllegalArgumentException if {@code obj} couldn't be rewritten or
     * if {@code toRemove} or {@code toAdd} is null.
     **/
    public static <T> T deepReplaceHeadNamespace(T obj, String toRemove, String toAdd) {
        final RewritingProcessor processor =
                RewritingProcessor.newRewritingProcessor(toAdd,toRemove);
        return processor.rewriteOutput(obj);
    }

    /**
     * Appends {@code namespace} to {@code path}.
     * This methods appends {@code namespace} to {@code path} to obtain a
     * a <i>full path</i>, and normalizes the result thus obtained:
     * <ul>
     * <li>If {@code path} is empty, the full path is
     *     {@code namespace}.</li>
     * <li>Otherwise, if {@code namespace} is empty,
     *     the full path is {@code path}</li>
     * <li>Otherwise, and this is the regular case, the full path is the
     *     result of the concatenation of
     *     {@code path}+{@value #NAMESPACE_SEPARATOR}+{@code namespace}</li>
     * <li>finally, the full path is normalized: multiple consecutive
     *     occurrences of {@value #NAMESPACE_SEPARATOR} are replaced by a
     *     single {@value #NAMESPACE_SEPARATOR} in the result, and trailing
     *     occurences of {@value #NAMESPACE_SEPARATOR} are removed.
     * </li>
     * </ul>
     * @param path a name space path prefix
     * @param namespace a name space name to append to the path
     * @return a syntactically valid name space path, or "" if both parameters
     * are null or empty.
     * @throws IllegalArgumentException if either argument is null or ends with
     * an odd number of {@code /} characters.
     **/
    public static String concat(String path, String namespace) {
        if (path == null || namespace == null)
            throw new IllegalArgumentException("Null argument");
        checkTrailingSlashes(path);
        checkTrailingSlashes(namespace);
        final String result;
        if (path.equals("")) result=namespace;
        else if (namespace.equals("")) result=path;
        else result=path+NAMESPACE_SEPARATOR+namespace;
        return ObjectNameRouter.normalizeNamespacePath(result,false,true,false);
    }

    /**
     * Returns a syntactically valid name space path.
     * If the provided {@code namespace} ends with {@code "//"},
     * recursively strips trailing {@code "//"}.  Each sequence of an
     * even number of {@code "/"} characters is also replaced by {@code "//"},
     * for example {@code "foo//bar////baz/////buh"} will become
     * {@code "foo//bar//baz///buh"}.
     *
     * @param namespace A name space path
     * @return {@code ""} - if the provided {@code namespace} resolves to
     * the empty string; otherwise a syntactically valid name space string
     * stripped of trailing and redundant {@code "//"}.
     * @throws IllegalArgumentException if {@code namespace} is null or
     * is not syntactically valid (e.g. it contains
     * invalid characters like ':', or it ends with an odd
     * number of '/').
     */
    public static String normalizeNamespaceName(String namespace) {
        if (namespace == null)
            throw new IllegalArgumentException("Null namespace");
        final String sourcePath =
                ObjectNameRouter.normalizeNamespacePath(namespace,false,true,false);
        if (sourcePath.equals("")) return sourcePath;

        // Will throw an IllegalArgumentException if the namespace name
        // is not syntactically valid...
        //
        getNamespaceObjectName(sourcePath);
        return sourcePath;
    }


    /**
     * Return a canonical handler name for the provided {@code namespace},
     * The handler name returned will be
     * {@link #normalizeNamespaceName normalizeNamespaceName}{@code (namespace) +
     * "//:type=JMXNamespace"}.
     *
     * @param namespace A name space path
     * @return a canonical ObjectName for a name space handler.
     * @see #normalizeNamespaceName
     * @throws IllegalArgumentException if the provided
     *          {@code namespace} is null or not valid.
     */
    public static ObjectName getNamespaceObjectName(String namespace) {
        if (namespace == null || namespace.equals(""))
            throw new IllegalArgumentException("Null or empty namespace");
        final String sourcePath =
                ObjectNameRouter.normalizeNamespacePath(namespace,false,
                            true,false);
        try {
            // We could use ObjectName.valueOf here - but throwing an
            // IllegalArgumentException that contains just the supplied
            // namespace instead of the whole ObjectName seems preferable.
            return ObjectName.getInstance(sourcePath+
                    NAMESPACE_SEPARATOR+":"+
                    JMXNamespace.TYPE_ASSIGNMENT);
        } catch (MalformedObjectNameException x) {
            throw new IllegalArgumentException("Invalid namespace: " +
                                               namespace,x);
        }
    }

    /**
     * Returns an ObjectName pattern that can be used to query for all MBeans
     * contained in the given name space.
     * For instance, if {@code namespace="foo//bar"}, this method will
     * return {@code "foo//bar//*:*"}
     * @return an ObjectName pattern that selects all MBeans in the given
     *         name space.
     **/
    public static ObjectName getWildcardFor(String namespace) {
            return insertPath(namespace,ObjectName.WILDCARD);
    }


    /**
     * Returns an ObjectName that can be used to access an MBean
     * contained in the given name space.
     * For instance, if {@code path="foo//bar"}, and
     * {@code to="domain:type=Thing"} this method will
     * return {@code "foo//bar//domain:type=Thing"}
     * @return an ObjectName that can be used to invoke an MBean located in a
     *         sub name space.
     * @throws IllegalArgumentException if {@code path} ends with an
     * odd number of {@code /} characters.
     **/
    public static ObjectName insertPath(String path, ObjectName to) {
        if (path == null || to == null)
            throw new IllegalArgumentException("Null argument");
        checkTrailingSlashes(path);
        String prefix = path;
        if (!prefix.equals(""))
            prefix = ObjectNameRouter.normalizeNamespacePath(
                        prefix + NAMESPACE_SEPARATOR,false,false,false);
         return to.withDomain(
                    ObjectNameRouter.normalizeDomain(
                        prefix+to.getDomain(),false));
    }

    /**
     * Returns the normalized name space path of the name space expected to
     * contain {@code ObjectName}.
     * For instance, for {@code "foo//domain:type=Thing"} this will be
     * {@code "foo"}. For {@code "//foo//bar//domain:type=Thing"} this will be
     * {@code "foo//bar"}. For {@code //foo//bar//baz//domain:type=Thing}
     * this will be {@code "foo//bar//baz"}. For
     * {@code //foo//bar//baz//:type=JMXNamespace}
     * this will be {@code "foo//bar"}.
     *
     * @param name an {@code ObjectName}
     * @return the name space path of the name space that could contain such
     *         a name. If {@code name} has no name space, returns {@code ""}.
     * @throws IllegalArgumentException if {@code name} is null.
     **/
    public static String getContainingNamespace(ObjectName name) {
        return getNormalizedPath(name,true);
    }


    static String getNormalizedPath(ObjectName name,
            boolean removeLeadingSep) {
        if (name == null)
            throw new IllegalArgumentException("Null name");
        String domain =
                ObjectNameRouter.normalizeDomain(name.getDomain(),removeLeadingSep);
        int end = domain.length();

        // special case of domain part being a single '/'
        //
        if (domain.endsWith(NAMESPACE_SEPARATOR+"/"))
            return domain.substring(0,end-NAMESPACE_SEPARATOR_LENGTH-1);

        // special case of namespace handler
        //
        if (domain.endsWith(NAMESPACE_SEPARATOR))
            domain = domain.substring(0,end-NAMESPACE_SEPARATOR_LENGTH);

        int last = domain.lastIndexOf(NAMESPACE_SEPARATOR);
        if (last < 0) return "";
        if (last == 0) return domain;

        // special case of domain part starting with '/'
        // last=0 is not possible - we took care of this above.
        if (domain.charAt(last-1) == '/') last--;

        return domain.substring(0,last);
    }

    private static void checkTrailingSlashes(String path) {
        int i;
        for (i = path.length() - 1; i >= 0 && path.charAt(i) == '/'; i--)
            continue;
        if (path.length() - i % 2 == 0)
            throw new IllegalArgumentException("Path ends with odd number of /");
    }
}
