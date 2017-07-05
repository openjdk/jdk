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

import java.io.IOException;
import java.util.Map;

import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;

/**
 * The {@link NamespaceController} MBean makes it possible to easily
 * create mount points ({@link JMXNamespace JMXNamespaces}) in an
 * {@code MBeanServer}.
 */
// This API was originally in the draft of javax/management/namespaces
// but we decided to retire it. Rather than removing all the associated
// tests I have moved the API to the test hierarchy - so it is now used as
// an additional (though somewhat complex) test case...
//
public interface NamespaceControllerMBean {
    /**
     * Mount MBeans from the source path of the source URL into the specified
     * target path of the target.
     * @param url URL of the mounted source.
     * @param targetPath Target path in which MBeans will be mounted.
     * @param optionsMap connection map and options. See {@link
     *      javax.management.namespace.JMXRemoteNamespace.Options
     *      JMXRemoteNamespace.Options}
     * @throws IOException Connection with the source failed
     * @throws IllegalArgumentException Supplied parameters are
     *         illegal, or combination of supplied parameters is illegal.
     * @return A mount point id.
     */
    public String mount(JMXServiceURL url,
            String targetPath,
            Map<String,Object> optionsMap)
            throws IOException, IllegalArgumentException;

    /**
     * Mount MBeans from the source path of the source URL into the specified
     * target path of the target.
     * @param url URL of the mounted source.
     * @param targetPath Target path in which MBeans will be mounted.
     * @param sourcePath source namespace path.
     * @param optionsMap connection map and options. See {@link
     *      javax.management.namespace.JMXRemoteNamespace.Options
     *      JMXRemoteNamespace.Options}
     * @throws IOException Connection with the source failed
     * @throws IllegalArgumentException Supplied parameters are
     *         illegal, or combination of supplied parameters is illegal.
     * @return A mount point id.
     */
    public String mount(JMXServiceURL url,
            String targetPath,
            String sourcePath,
            Map<String,Object> optionsMap)
            throws IOException, IllegalArgumentException;

    /**
     * Unmount a previously mounted mount point.
     * @param mountPointId A mount point id, as previously returned
     *        by mount.
     * @throws IllegalArgumentException Supplied parameters are
     *         illegal, or combination of supplied parameters is illegal.
     * @throws IOException thrown if the mount point {@link JMXNamespace}
     *         couldn't be unregistered.
     */
    public boolean unmount(String mountPointId)
        throws IOException, IllegalArgumentException;

    /**
     * Tells whether there already exists a {@link JMXNamespace} for
     * the given <var>targetPath</var>.
     * @param targetPath a target name space path.
     * @return true if a {@link JMXNamespace} is registered for that
     *         name space path.
     **/
    public boolean ismounted(String targetPath);

    /**
     * Returns the handler name for the provided target name space
     * path. Can throw IllegalArgumentException if the provided
     * targetPath contains invalid characters (like e.g. ':').
     * @param targetPath A target name space path.
     * @return the handler name for the provided target name space
     * path.
     **/
    public ObjectName getHandlerNameFor(String targetPath);

    /**
     * Return a sorted array of locally mounted name spaces.
     * This is equivalent to calling {@link
     * #findNamespaces(java.lang.String,java.lang.String,int)
     *  findNamespaces(null,null,0)};
     * @return a sorted array of locally mounted name spaces.
     **/
    public String[] findNamespaces();

    /**
     * Return a sorted array of mounted name spaces, starting at
     * <var>from</var> (if non null), and recursively searching up to
     * provided <var>depth</var>.
     * @param from A name spaces from which to start the search. If null,
     *        will start searching from the MBeanServer root.
     *        If not null, all returned names will start with <var>from//</var>.
     * @param regex A regular expression that the returned names must match.
     *        If null - no matching is performed and all found names are
     *        returned. If not null, then all returned names satisfy
     *        {@link String#matches name.matches(regex)};
     * @param depth the maximum number of levels that the search algorithm
     *        will cross. 0 includes only top level name spaces, 1 top level
     *        and first level children etc... <var>depth</var> is evaluated
     *        with regard to where the search starts - if a non null
     *        <var>from</var> parameter is provided - then {@code depth=0}
     *        corresponds to all name spaces found right below
     *        <var>from//</var>.
     * @return A sorted array of name spaces matching the provided criteria.
     *         All returned names end with "//".
     **/
    public String[] findNamespaces(String from, String regex, int depth);
}
