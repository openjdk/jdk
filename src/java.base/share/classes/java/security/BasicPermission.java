/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.security;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code BasicPermission} class extends the {@code Permission} class, and
 * can be used as the base class for permissions that want to
 * follow the same naming convention as {@code BasicPermission}.
 * <P>
 * The name for a {@code BasicPermission} is the name of the given permission
 * (for example, "exit",
 * "setFactory", "print.queueJob", etc.). The naming
 * convention follows the  hierarchical property naming convention.
 * An asterisk may appear by itself, or if immediately preceded by a "."
 * may appear at the end of the name, to signify a wildcard match.
 * For example, "*" and "java.*" signify a wildcard match, while "*java", "a*b",
 * and "java*" do not.
 * <P>
 * The action string (inherited from {@code Permission}) is unused.
 * Thus, {@code BasicPermission} is commonly used as the base class for
 * "named" permissions
 * (ones that contain a name but no actions list; you either have the
 * named permission or you don't.)
 * Subclasses may implement actions on top of {@code BasicPermission},
 * if desired.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.lang.SecurityManager
 *
 * @author Marianne Mueller
 * @author Roland Schemers
 * @since 1.2
 */

public abstract class BasicPermission extends Permission
    implements java.io.Serializable
{

    @java.io.Serial
    private static final long serialVersionUID = 6279438298436773498L;

    // does this permission have a wildcard at the end?
    private transient boolean wildcard;

    // the name without the wildcard on the end
    private transient String path;

    // is this permission the old-style exitVM permission (pre JDK 1.6)?
    private transient boolean exitVM;

    /**
     * initialize a {@code BasicPermission} object. Common to all constructors.
     */
    private void init(String name) {
        if (name == null)
            throw new NullPointerException("name can't be null");

        int len = name.length();

        if (len == 0) {
            throw new IllegalArgumentException("name can't be empty");
        }

        char last = name.charAt(len - 1);

        // Is wildcard or ends with ".*"?
        if (last == '*' && (len == 1 || name.charAt(len - 2) == '.')) {
            wildcard = true;
            if (len == 1) {
                path = "";
            } else {
                path = name.substring(0, len - 1);
            }
        } else {
            if (name.equals("exitVM")) {
                wildcard = true;
                path = "exitVM.";
                exitVM = true;
            } else {
                path = name;
            }
        }
    }

    /**
     * Creates a new {@code BasicPermission} with the specified name.
     * Name is the symbolic name of the permission, such as
     * "setFactory",
     * "print.queueJob", or "topLevelWindow", etc.
     *
     * @param name the name of the {@code BasicPermission}.
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public BasicPermission(String name) {
        super(name);
        init(name);
    }


    /**
     * Creates a new {@code BasicPermission} object with the specified name.
     * The name is the symbolic name of the {@code BasicPermission}, and the
     * actions {@code String} is currently unused.
     *
     * @param name the name of the {@code BasicPermission}.
     * @param actions ignored.
     *
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    public BasicPermission(String name, String actions) {
        super(name);
        init(name);
    }

    /**
     * Checks if the specified permission is "implied" by
     * this object.
     * <P>
     * More specifically, this method returns {@code true} if:
     * <ul>
     * <li> {@code p}'s class is the same as this object's class, and
     * <li> {@code p}'s name equals or (in the case of wildcards)
     *      is implied by this object's
     *      name. For example, "a.b.*" implies "a.b.c".
     * </ul>
     *
     * @param p the permission to check against.
     *
     * @return {@code true} if the passed permission is equal to or
     * implied by this permission, {@code false} otherwise.
     */
    @Override
    public boolean implies(Permission p) {
        if ((p == null) || (p.getClass() != getClass()))
            return false;

        BasicPermission that = (BasicPermission) p;

        if (this.wildcard) {
            if (that.wildcard) {
                // one wildcard can imply another
                return that.path.startsWith(path);
            } else {
                // make sure ap.path is longer so a.b.* doesn't imply a.b
                return (that.path.length() > this.path.length()) &&
                    that.path.startsWith(this.path);
            }
        } else {
            if (that.wildcard) {
                // a non-wildcard can't imply a wildcard
                return false;
            }
            else {
                return this.path.equals(that.path);
            }
        }
    }

    /**
     * Checks two {@code BasicPermission} objects for equality.
     * Checks that {@code obj}'s class is the same as this object's class
     * and has the same name as this object.
     *
     * @param obj the object we are testing for equality with this object.
     * @return {@code true} if {@code obj}'s class is the same as this
     * object's class and has the same name as this {@code BasicPermission}
     * object, {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if ((obj == null) || (obj.getClass() != getClass()))
            return false;

        BasicPermission bp = (BasicPermission) obj;

        return getName().equals(bp.getName());
    }


    /**
     * Returns the hash code value for this object.
     * The hash code used is the hash code of the name, that is,
     * {@code getName().hashCode()}, where {@code getName} is
     * from the {@code Permission} superclass.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }

    /**
     * Returns the canonical string representation of the actions,
     * which currently is the empty string "", since there are no actions for
     * a {@code BasicPermission}.
     *
     * @return the empty string "".
     */
    @Override
    public String getActions() {
        return "";
    }

    /**
     * Returns a new {@code PermissionCollection} object for storing
     * {@code BasicPermission} objects.
     *
     * <p>{@code BasicPermission} objects must be stored in a manner
     * that allows them to be inserted in any order, but that also enables the
     * {@link PermissionCollection#implies} method
     * to be implemented in an efficient (and consistent) manner.
     *
     * @return a new {@code PermissionCollection} object suitable for
     * storing {@code BasicPermission} objects.
     */
    @Override
    public PermissionCollection newPermissionCollection() {
        return new BasicPermissionCollection(this.getClass());
    }

    /**
     * {@code readObject} is called to restore the state of the
     * (@code BasicPermission} from a stream.
     *
     * @param  s the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(ObjectInputStream s)
         throws IOException, ClassNotFoundException
    {
        s.defaultReadObject();
        // init is called to initialize the rest of the values.
        init(getName());
    }

    /**
     * Returns the canonical name of this {@code BasicPermission}.
     * All internal invocations of getName should invoke this method, so
     * that the pre-JDK 1.6 "exitVM" and current "exitVM.*" permission are
     * equivalent in equals/hashCode methods.
     *
     * @return the canonical name of this {@code BasicPermission}.
     */
    final String getCanonicalName() {
        return exitVM ? "exitVM.*" : getName();
    }
}

/**
 * A {@code BasicPermissionCollection} stores a collection
 * of {@code BasicPermission} permissions. {@code BasicPermission} objects
 * must be stored in a manner that allows them to be inserted in any
 * order, but enable the implies function to evaluate the implies
 * method in an efficient (and consistent) manner.
 *
 * A {@code BasicPermissionCollection} handles comparing a permission
 * like "a.b.c.d.e" with a {@code Permission} such as "a.b.*", or "*".
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 *
 *
 * @author Roland Schemers
 *
 * @serial include
 */

final class BasicPermissionCollection
    extends PermissionCollection
    implements java.io.Serializable
{

    @java.io.Serial
    private static final long serialVersionUID = 739301742472979399L;

    /**
     * Key is name, value is permission. All permission objects in
     * collection must be of the same type.
     * Not serialized; see serialization section at end of class.
     */
    private transient ConcurrentHashMap<String, Permission> perms;

    /**
     * This is set to {@code true} if this (@code BasicPermissionCollection}
     * contains a {code BasicPermission} with '*' as its permission name.
     *
     * @see #serialPersistentFields
     */
    private boolean all_allowed;

    /**
     * The class to which all {@code BasicPermission} objects in this
     * BasicPermissionCollection belong.
     *
     * @see #serialPersistentFields
     */
    private Class<?> permClass;

    /**
     * Create an empty BasicPermissionCollection object.
     *
     */
    public BasicPermissionCollection(Class<?> clazz) {
        perms = new ConcurrentHashMap<>(11);
        all_allowed = false;
        permClass = clazz;
    }

    /**
     * Adds a permission to the {@code BasicPermission} object.
     * The key for the hash is permission.path.
     *
     * @param permission the {@code Permission} object to add.
     *
     * @throws    IllegalArgumentException   if the permission is not a
     *                                       {@code BasicPermission}, or if
     *                                       the permission is not of the
     *                                       same class as the other
     *                                       permissions in this collection.
     *
     * @throws    SecurityException   if this {@code BasicPermissionCollection}
     *                                object has been marked readonly
     */
    @Override
    public void add(Permission permission) {
        if (!(permission instanceof BasicPermission basicPermission))
            throw new IllegalArgumentException("invalid permission: "+
                                               permission);
        if (isReadOnly())
            throw new SecurityException("attempt to add a Permission to a readonly PermissionCollection");

        // make sure we only add new BasicPermission objects of the same class
        // Also check null for compatibility with deserialized form from
        // previous versions.
        if (permClass == null) {
            // adding first permission
            permClass = basicPermission.getClass();
        } else {
            if (basicPermission.getClass() != permClass)
                throw new IllegalArgumentException("invalid permission: " +
                                                permission);
        }

        String canonName = basicPermission.getCanonicalName();
        perms.put(canonName, permission);

        // No sync on all_allowed; staleness OK
        if (!all_allowed) {
            if (canonName.equals("*"))
                all_allowed = true;
        }
    }

    /**
     * Check and see if this set of permissions implies the permissions
     * expressed in "permission".
     *
     * @param permission the Permission object to compare
     *
     * @return {@code true} if "permission" is a proper subset of a
     * permission in the set, {@code false} if not.
     */
    @Override
    public boolean implies(Permission permission) {
        if (!(permission instanceof BasicPermission basicPermission))
            return false;

        // random subclasses of BasicPermission do not imply each other
        if (basicPermission.getClass() != permClass)
            return false;

        // short circuit if the "*" Permission was added
        if (all_allowed)
            return true;

        // strategy:
        // Check for full match first. Then work our way up the
        // path looking for matches on a.b..*

        String path = basicPermission.getCanonicalName();
        //System.out.println("check "+path);

        Permission x = perms.get(path);

        if (x != null) {
            // we have a direct hit!
            return x.implies(permission);
        }

        // work our way up the tree...
        int last, offset;

        offset = path.length()-1;

        while ((last = path.lastIndexOf('.', offset)) != -1) {

            path = path.substring(0, last+1) + "*";
            //System.out.println("check "+path);

            x = perms.get(path);

            if (x != null) {
                return x.implies(permission);
            }
            offset = last -1;
        }

        // we don't have to check for "*" as it was already checked
        // at the top (all_allowed), so we just return false
        return false;
    }

    /**
     * Returns an enumeration of all the BasicPermission objects in the
     * container.
     *
     * @return an enumeration of all the BasicPermission objects.
     */
    @Override
    public Enumeration<Permission> elements() {
        return perms.elements();
    }

    // Need to maintain serialization interoperability with earlier releases,
    // which had the serializable field:
    //
    // @serial the Hashtable is indexed by the BasicPermission name
    //
    // private Hashtable permissions;
    /**
     * @serialField permissions java.util.Hashtable
     *    The {@code BasicPermission} objects in this
     *    {@code BasicPermissionCollection}.
     *    All {@code BasicPermission} objects in the collection must belong
     *    to the same class.
     *    The Hashtable is indexed by the {@code BasicPermission} name;
     *    the value of the Hashtable entry is the permission.
     * @serialField all_allowed boolean
     *   This is set to {@code true} if this {@code BasicPermissionCollection}
     *   contains a {@code BasicPermission} object
     *   with '*' as its permission name.
     * @serialField permClass java.lang.Class
     *   The class to which all {@code BasicPermission} objectss in this
     *   {@code BasicPermissionCollection} belongs.
     */
    @java.io.Serial
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("permissions", Hashtable.class),
        new ObjectStreamField("all_allowed", Boolean.TYPE),
        new ObjectStreamField("permClass", Class.class),
    };

    /*
     * @serialData Default fields.
     */

    /**
     * Writes the contents of the perms field out as a Hashtable for
     * serialization compatibility with earlier releases. all_allowed
     * and permClass unchanged.
     *
     * @param  out the {@code ObjectOutputStream} to which data is written
     * @throws IOException if an I/O error occurs
     */
    @java.io.Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        // Don't call out.defaultWriteObject()

        // Copy perms into a Hashtable
        Hashtable<String, Permission> permissions =
                new Hashtable<>(perms.size()*2);

        permissions.putAll(perms);

        // Write out serializable fields
        ObjectOutputStream.PutField pfields = out.putFields();
        pfields.put("all_allowed", all_allowed);
        pfields.put("permissions", permissions);
        pfields.put("permClass", permClass);
        out.writeFields();
    }

    /**
     * readObject is called to restore the state of the
     * {@code BasicPermissionCollection} from a stream.
     *
     * @param  in the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(java.io.ObjectInputStream in)
         throws IOException, ClassNotFoundException
    {
        // Don't call defaultReadObject()

        // Read in serialized fields
        ObjectInputStream.GetField gfields = in.readFields();

        // Get permissions
        // writeObject writes a Hashtable<String, Permission> for the
        // permissions key, so this cast is safe, unless the data is corrupt.
        @SuppressWarnings("unchecked")
        Hashtable<String, Permission> permissions =
                (Hashtable<String, Permission>)gfields.get("permissions", null);
        perms = new ConcurrentHashMap<>(permissions.size()*2);
        perms.putAll(permissions);

        // Get all_allowed
        all_allowed = gfields.get("all_allowed", false);

        // Get permClass
        permClass = (Class<?>) gfields.get("permClass", null);

        if (permClass == null) {
            // set permClass
            Enumeration<Permission> e = permissions.elements();
            if (e.hasMoreElements()) {
                Permission p = e.nextElement();
                permClass = p.getClass();
            }
        }
    }
}
