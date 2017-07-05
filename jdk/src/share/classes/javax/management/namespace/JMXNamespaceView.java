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
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * This class makes it possible to navigate easily within a hierarchical
 * namespace view.
 *
 * <pre>
 * MBeanServerConnnection rootConnection = ...;
 *
 * // create a view at the local root of the namespace hierarchy.
 * //
 * JMXNamespaceView view = new JMXNamespaceView(rootConnection);
 *
 * // list all top level namespaces
 * String[] list = view.list();
 *
 * // select one namespace from the list
 * String whereToGo = ... ;
 *
 * // go down to the selected namespace:
 * view = view.down(whereToGo);
 * System.out.println("I am now in: " + view.where());
 * System.out.println("I can see these MBeans:" +
 *    view.getMBeanServerConnection().queryNames(null,null));
 *
 * // list sub namespaces in current view ('whereToGo')
 * list = view.list();
 * System.out.println("Here are the sub namespaces of "+view.where()+": "+
 *                    Arrays.toString(list));
 *
 * // go up one level
 * view = view.up();
 * System.out.println("I am now back to: " +
 *    (view.isRoot() ? "root namespace" : view.where()));
 * </pre>
 * @since 1.7
 */
public class JMXNamespaceView {

    private static final ObjectName ALL_NAMESPACES;
    static {
        try {
            ALL_NAMESPACES = ObjectName.getInstance("*" +
                    JMXNamespaces.NAMESPACE_SEPARATOR + ":"+
                    JMXNamespace.TYPE_ASSIGNMENT);
        } catch (MalformedObjectNameException x) {
            throw new ExceptionInInitializerError(x);
        }
    }
    private static final int NAMESPACE_SEPARATOR_LENGTH =
            JMXNamespaces.NAMESPACE_SEPARATOR.length();

    private final JMXNamespaceView parent;
    private final MBeanServerConnection here;
    private final String where;

    private static MBeanServerConnection checkRoot(MBeanServerConnection root) {
        if (root == null)
            throw new IllegalArgumentException(
                    "namespaceRoot: null is not a valid value");
        return root;
    }

    /**
     * Creates a view at the top of a JMX namespace hierarchy.
     * @param namespaceRoot The {@code MBeanServerConnection} at the
     *        top of the hierarchy.
     */
    public JMXNamespaceView(MBeanServerConnection namespaceRoot) {
        this(null,checkRoot(namespaceRoot),"");
    }

    // This constructor should remain private. A user can only create
    // JMXNamespaceView at the top of the hierarchy.
    // JMXNamespaceView sub nodes are created by their parent nodes.
    private JMXNamespaceView(JMXNamespaceView parent,
            MBeanServerConnection here, String where) {
        this.parent = parent;
        this.here   = here;
        this.where  = where;
    }

    /**
     * Returns the path leading to the namespace in this view, from
     * the top of the hierarchy.
     * @return The path to the namespace in this view.
     */
    public String where() {
        return where;
    }

    /**
     * Lists all direct sub namespaces in this view.  The returned strings
     * do not contain the {@code //} separator.
     *
     * @return A list of direct sub name spaces accessible from this
     *         namespace.
     * @throws IOException if the attempt to list the namespaces fails because
     * of a communication problem.
     */
    public String[] list() throws IOException {
        final Set<ObjectName> names =
                here.queryNames(ALL_NAMESPACES,null);
        final String[] res = new String[names.size()];
        int i = 0;
        for (ObjectName dirName : names) {
            final String dir = dirName.getDomain();
            res[i++]=dir.substring(0,dir.length()-NAMESPACE_SEPARATOR_LENGTH);
        }
        return res;
    }

    /**
     * Go down into a sub namespace.
     * @param namespace the namespace to go down to.  It can contain one or
     * more {@code //} separators, to traverse intermediate namespaces, but
     * it must not begin or end with {@code //} or contain an empty
     * intermediate namespace.  If it is the empty string, then {@code this} is
     * returned.
     * @return A view of the named sub namespace.
     * @throws IllegalArgumentException if the {@code namespace} begins or
     * ends with {@code //}.
     */
    public JMXNamespaceView down(String namespace) {
        if (namespace.equals("")) return this;
        if (namespace.startsWith(JMXNamespaces.NAMESPACE_SEPARATOR))
            throw new IllegalArgumentException(namespace+": can't start with "+
                    JMXNamespaces.NAMESPACE_SEPARATOR);

        // This is a convenience to handle paths like xxx//yyy
        final String[] elts =
                namespace.split(JMXNamespaces.NAMESPACE_SEPARATOR);

        // Go down the path, creating all sub namespaces along the way.
        // Usually there will be a single element in the given namespace
        // name, but we don't want to forbid things like
        // down("xxx//yyy/www");
        //
        JMXNamespaceView previous = this;
        String cursor = where;
        for (String elt : elts) {
            // empty path elements are not allowed. It means we
            // had something like "xxx////yyy"
            if (elt.equals(""))
                throw new IllegalArgumentException(namespace+
                        ": invalid path element");

            // compute the "where" for the child.
            cursor = JMXNamespaces.concat(cursor, elt);

            // create the child...
            final JMXNamespaceView next =
                    makeJMXNamespaceView(root(), previous, cursor);

            // the current child will be the parent of the next child...
            previous = next;
        }

        // We return the last child that was created.
        return previous;
    }

    /**
     * Go back up one level. If this view is at the root of the
     * hierarchy, returns {@code null}.
     * @return A view of the parent namespace, or {@code null} if we're at
     *         the root of the hierarchy.
     */
    public JMXNamespaceView up() {
        return parent;
    }

    /**
     * Tells whether this view is at the root of the hierarchy.
     * @return {@code true} if this view is at the root of the hierachy.
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * Returns the view at the root of the hierarchy.
     * If we are already at the root, this is {@code this}.
     * @return the view at the root of the hierarchy.
     */
    public JMXNamespaceView root() {
        if (parent == null) return this;
        return parent.root();
    }

    /**
     * A MBeanServerConnection to the namespace shown by this view.
     * This is what would have been obtained by doing:
     * <pre>
     *   JMX.narrowToNamespace(this.root().getMBeanServerConnection(),
     *       this.where());
     * </pre>
     * @return A MBeanServerConnection to the namespace shown by this view.
     */
    public MBeanServerConnection getMBeanServerConnection() {
        return here;
    }

    /**
     * <p>Get the name of the JMXNamespaceMBean handling the namespace shown by
     * this view, relative to the root of the hierarchy.  If we are at the root
     * of the hierarchy, this method returns {@code null}.</p>
     *
     * <p>You can use this method to make a proxy for the JMXNamespaceMBean
     * as follows:</p>
     *
     * <pre>
     * JMXNamespaceView view = ...;
     * ObjectName namespaceMBeanName = view.getJMXNamespaceMBeanName();
     * JMXNamespaceMBean namespaceMBean = JMX.newMBeanProxy(
     *     view.root().getMBeanServerConnection(), namespaceMBeanName,
     *     JMXNamespaceMBean.class);
     * </pre>
     *
     * @return The name of the {@code JMXNamespaceMBean} handling the namespace
     *         shown by this view, or {@code null}.
     */
    public ObjectName getJMXNamespaceMBeanName() {
        if (parent == null)
            return null;
        else
            return JMXNamespaces.getNamespaceObjectName(where);
    }

    @Override
    public int hashCode() {
        return where.hashCode();
    }

    /**
     * Returns true if this object is equal to the given object.  The
     * two objects are equal if the other object is also a {@code
     * JMXNamespaceView} and both objects have the same {@linkplain #root root}
     * MBeanServerConnection and the same {@linkplain #where path}.
     * @param o the other object to compare to.
     * @return true if both objects are equal.
     */
    @Override
    public boolean equals(Object o) {
        if (o==this) return true;
        if (! (o instanceof JMXNamespaceView)) return false;
        if (!where.equals(((JMXNamespaceView)o).where)) return false;
        return root().getMBeanServerConnection().equals(
                ((JMXNamespaceView)o).root().getMBeanServerConnection());
    }

    private JMXNamespaceView makeJMXNamespaceView(final JMXNamespaceView root,
            final JMXNamespaceView directParent, final String pathFromRoot) {
        if (pathFromRoot.equals("")) return root;

        return new JMXNamespaceView(directParent,
                narrowToNamespace(root.getMBeanServerConnection(),
                pathFromRoot),pathFromRoot);
    }

    private MBeanServerConnection narrowToNamespace(MBeanServerConnection root,
            String path) {
        if (root instanceof MBeanServer)
            return JMXNamespaces.narrowToNamespace((MBeanServer)root, path);
        return JMXNamespaces.narrowToNamespace(root, path);
    }

}
