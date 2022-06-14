/*
 * Copyright (c) 1999, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javax.crypto;

import java.security.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.io.Serializable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.ObjectStreamField;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This class contains <code>CryptoPermission</code> objects, organized into
 * PermissionCollections according to algorithm names.
 *
 * <p>When the <code>add</code> method is called to add a
 * <code>CryptoPermission</code>, the <code>CryptoPermission</code> is stored in the
 * appropriate <code>PermissionCollection</code>. If no such
 * collection exists yet, the algorithm name associated with
 * the <code>CryptoPermission</code> object is
 * determined and the <code>newPermissionCollection</code> method
 * is called on the <code>CryptoPermission</code> or <code>CryptoAllPermission</code> class to
 * create the <code>PermissionCollection</code> and add it to the <code>Permissions</code> object.
 *
 * @see javax.crypto.CryptoPermission
 * @see java.security.PermissionCollection
 * @see java.security.Permissions
 *
 * @author Sharon Liu
 * @since 1.4
 */
final class CryptoPermissions extends PermissionCollection
implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 4946547168093391015L;

    /**
     * @serialField perms java.util.Hashtable
     */
    @java.io.Serial
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("perms", Hashtable.class),
    };

    // Switched from Hashtable to ConcurrentHashMap to improve scalability.
    // To maintain serialization compatibility, this field is made transient
    // and custom readObject/writeObject methods are used.
    private transient ConcurrentHashMap<String,PermissionCollection> perms;

    /**
     * Creates a new <code>CryptoPermissions</code> object containing
     * no <code>CryptoPermissionCollection</code> objects.
     */
    CryptoPermissions() {
        perms = new ConcurrentHashMap<>(7);
    }

    /**
     * Populates the crypto policy from the specified
     * InputStream into this <code>CryptoPermissions</code> object.
     *
     * @param in the InputStream to load from.
     *
     * @exception SecurityException if cannot load
     * successfully.
     */
    void load(InputStream in)
        throws IOException, CryptoPolicyParser.ParsingException {
        CryptoPolicyParser parser = new CryptoPolicyParser();
        parser.read(new BufferedReader(new InputStreamReader(in, UTF_8)));

        CryptoPermission[] parsingResult = parser.getPermissions();
        for (int i = 0; i < parsingResult.length; i++) {
            this.add(parsingResult[i]);
        }
    }

    /**
     * Returns true if this <code>CryptoPermissions</code> object doesn't
     * contain any <code>CryptoPermission</code> objects; otherwise, returns
     * false.
     */
    boolean isEmpty() {
        return perms.isEmpty();
    }

    /**
     * Adds a permission object to the
     * <code>PermissionCollection</code> for the algorithm returned by
     * <code>(CryptoPermission)permission.getAlgorithm()</code>.
     *
     * This method creates
     * a new <code>PermissionCollection</code> object (and adds the
     * permission to it) if an appropriate collection does not yet exist.
     *
     * @param permission the <code>Permission</code> object to add.
     *
     * @exception SecurityException if this <code>CryptoPermissions</code>
     * object is marked as readonly.
     *
     * @see isReadOnly
     */
    @Override
    public void add(Permission permission) {

        if (isReadOnly()) {
            throw new SecurityException("Attempt to add a Permission " +
                                        "to a readonly CryptoPermissions " +
                                        "object");
        }

        if (!(permission instanceof CryptoPermission cryptoPerm)) {
            return;
        }

        PermissionCollection pc =
                        getPermissionCollection(cryptoPerm);
        pc.add(cryptoPerm);
        String alg = cryptoPerm.getAlgorithm();
        perms.putIfAbsent(alg, pc);
    }

    /**
     * Checks if this object's <code>PermissionCollection</code> for permissions
     * of the specified permission's algorithm implies the specified
     * permission. Returns true if the checking succeeded.
     *
     * @param permission the <code>Permission</code> object to check.
     *
     * @return true if "permission" is implied by the permissions
     * in the <code>PermissionCollection</code> it belongs to, false if not.
     *
     */
    @Override
    public boolean implies(Permission permission) {
        if (!(permission instanceof CryptoPermission cryptoPerm)) {
            return false;
        }

        PermissionCollection pc =
            getPermissionCollection(cryptoPerm.getAlgorithm());

        if (pc != null) {
            return pc.implies(cryptoPerm);
        } else {
            // none found
            return false;
        }
    }

    /**
     * Returns an enumeration of all the <code>Permission</code> objects
     * in all the PermissionCollections in this
     * <code>CryptoPermissions</code> object.
     * @return an enumeration of all the <code>Permissions</code>.
     */
    @Override
    public Enumeration<Permission> elements() {
        // go through each Permissions in the hash table
        // and call their elements() function.
        return new PermissionsEnumerator(perms.elements());
    }

    /**
     * Returns a <code>CryptoPermissions</code> object which
     * represents the minimum of the specified
     * <code>CryptoPermissions</code> object and this
     * <code>CryptoPermissions</code> object.
     *
     * @param other the <code>CryptoPermission</code>
     * object to compare with this object.
     */
    CryptoPermissions getMinimum(CryptoPermissions other) {
        if (other == null) {
            return null;
        }

        if (this.perms.containsKey(CryptoAllPermission.ALG_NAME)) {
            return other;
        }

        if (other.perms.containsKey(CryptoAllPermission.ALG_NAME)) {
            return this;
        }

        CryptoPermissions ret = new CryptoPermissions();


        PermissionCollection thatWildcard =
                other.perms.get(CryptoPermission.ALG_NAME_WILDCARD);
        int maxKeySize = 0;
        if (thatWildcard != null) {
            maxKeySize = ((CryptoPermission)
                    thatWildcard.elements().nextElement()).getMaxKeySize();
        }
        // For each algorithm in this CryptoPermissions,
        // find out if there is anything we should add into
        // ret.
        Enumeration<String> thisKeys = this.perms.keys();
        while (thisKeys.hasMoreElements()) {
            String alg = thisKeys.nextElement();

            PermissionCollection thisPc = this.perms.get(alg);
            PermissionCollection thatPc = other.perms.get(alg);

            CryptoPermission[] partialResult;

            if (thatPc == null) {
                if (thatWildcard == null) {
                    // The other CryptoPermissions
                    // doesn't allow this given
                    // algorithm at all. Just skip this
                    // algorithm.
                    continue;
                }
                partialResult = getMinimum(maxKeySize, thisPc);
            } else {
                partialResult = getMinimum(thisPc, thatPc);
            }

            for (int i = 0; i < partialResult.length; i++) {
                ret.add(partialResult[i]);
            }
        }

        PermissionCollection thisWildcard =
                this.perms.get(CryptoPermission.ALG_NAME_WILDCARD);

        // If this CryptoPermissions doesn't
        // have a wildcard, we are done.
        if (thisWildcard == null) {
            return ret;
        }

        // Deal with the algorithms only appear
        // in the other CryptoPermissions.
        maxKeySize =
            ((CryptoPermission)
                    thisWildcard.elements().nextElement()).getMaxKeySize();
        Enumeration<String> thatKeys = other.perms.keys();
        while (thatKeys.hasMoreElements()) {
            String alg = thatKeys.nextElement();

            if (this.perms.containsKey(alg)) {
                continue;
            }

            PermissionCollection thatPc = other.perms.get(alg);

            CryptoPermission[] partialResult;

            partialResult = getMinimum(maxKeySize, thatPc);

            for (int i = 0; i < partialResult.length; i++) {
                ret.add(partialResult[i]);
            }
        }
        return ret;
    }

    /**
     * Get the minimum of the two given <code>PermissionCollection</code>
     * <code>thisPc</code> and <code>thatPc</code>.
     *
     * @param thisPc the first given <code>PermissionCollection</code>
     * object.
     *
     * @param thatPc the second given <code>PermissionCollection</code>
     * object.
     */
    private CryptoPermission[] getMinimum(PermissionCollection thisPc,
                                          PermissionCollection thatPc) {
        ArrayList<CryptoPermission> permList = new ArrayList<>(2);

        Enumeration<Permission> thisPcPermissions = thisPc.elements();

        // For each CryptoPermission in
        // thisPc object, do the following:
        // 1) if this CryptoPermission is implied
        //     by thatPc, this CryptoPermission
        //     should be returned, and we can
        //     move on to check the next
        //     CryptoPermission in thisPc.
        // 2) otherwise, we should return
        //     all CryptoPermissions in thatPc
        //     which
        //     are implied by this CryptoPermission.
        //     Then we can move on to the
        //     next CryptoPermission in thisPc.
        while (thisPcPermissions.hasMoreElements()) {
            CryptoPermission thisCp =
                (CryptoPermission)thisPcPermissions.nextElement();

            Enumeration<Permission> thatPcPermissions = thatPc.elements();
            while (thatPcPermissions.hasMoreElements()) {
                CryptoPermission thatCp =
                    (CryptoPermission)thatPcPermissions.nextElement();

                if (thatCp.implies(thisCp)) {
                    permList.add(thisCp);
                    break;
                }
                if (thisCp.implies(thatCp)) {
                    permList.add(thatCp);
                }
            }
        }

        return permList.toArray(new CryptoPermission[0]);
    }

    /**
     * Returns all the <code>CryptoPermission</code> objects in the given
     * <code>PermissionCollection</code> object
     * whose maximum keysize no greater than <code>maxKeySize</code>.
     * For all <code>CryptoPermission</code> objects with a maximum keysize
     * greater than <code>maxKeySize</code>, this method constructs a
     * corresponding <code>CryptoPermission</code> object whose maximum
     * keysize is set to <code>maxKeySize</code>, and includes that in
     * the result.
     *
     * @param maxKeySize the given maximum key size.
     *
     * @param pc the given <code>PermissionCollection</code> object.
     */
    private CryptoPermission[] getMinimum(int maxKeySize,
                                          PermissionCollection pc) {
        ArrayList<CryptoPermission> permList = new ArrayList<>(1);

        Enumeration<Permission> enum_ = pc.elements();

        while (enum_.hasMoreElements()) {
            CryptoPermission cp =
                (CryptoPermission)enum_.nextElement();
            if (cp.getMaxKeySize() <= maxKeySize) {
                permList.add(cp);
            } else {
                if (cp.getCheckParam()) {
                    permList.add(
                           new CryptoPermission(cp.getAlgorithm(),
                                                maxKeySize,
                                                cp.getAlgorithmParameterSpec(),
                                                cp.getExemptionMechanism()));
                } else {
                    permList.add(
                           new CryptoPermission(cp.getAlgorithm(),
                                                maxKeySize,
                                                cp.getExemptionMechanism()));
                }
            }
        }

        return permList.toArray(new CryptoPermission[0]);
    }

    /**
     * Returns the <code>PermissionCollection</code> for the
     * specified algorithm. Returns null if there
     * isn't such a <code>PermissionCollection</code>.
     *
     * @param alg the algorithm name.
     */
    PermissionCollection getPermissionCollection(String alg) {
        // If this CryptoPermissions includes CryptoAllPermission,
        // we should return CryptoAllPermission.
        PermissionCollection pc = perms.get(CryptoAllPermission.ALG_NAME);
        if (pc == null) {
            pc = perms.get(alg);

            // If there isn't a PermissionCollection for
            // the given algorithm,we should return the
            // PermissionCollection for the wildcard
            // if there is one.
            if (pc == null) {
                pc = perms.get(CryptoPermission.ALG_NAME_WILDCARD);
            }
        }
        return pc;
    }

    /**
     * Returns the <code>PermissionCollection</code> for the algorithm
     * associated with the specified <code>CryptoPermission</code>
     * object. Creates such a <code>PermissionCollection</code>
     * if such a <code>PermissionCollection</code> does not
     * exist yet.
     *
     * @param cryptoPerm the <code>CryptoPermission</code> object.
     */
    private PermissionCollection getPermissionCollection(
                                          CryptoPermission cryptoPerm) {

        String alg = cryptoPerm.getAlgorithm();

        PermissionCollection pc = perms.get(alg);

        if (pc == null) {
            pc = cryptoPerm.newPermissionCollection();
        }
        return pc;
    }

    @java.io.Serial
    private void readObject(ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        ObjectInputStream.GetField fields = s.readFields();
        @SuppressWarnings("unchecked")
        Hashtable<String,PermissionCollection> permTable =
                (Hashtable<String,PermissionCollection>)
                (fields.get("perms", null));
        if (permTable != null) {
            perms = new ConcurrentHashMap<>(permTable);
        } else {
            perms = new ConcurrentHashMap<>();
        }
    }

    @java.io.Serial
    private void writeObject(ObjectOutputStream s) throws IOException {
        Hashtable<String,PermissionCollection> permTable =
                new Hashtable<>(perms);
        ObjectOutputStream.PutField fields = s.putFields();
        fields.put("perms", permTable);
        s.writeFields();
    }
}

final class PermissionsEnumerator implements Enumeration<Permission> {

    // all the perms
    private final Enumeration<PermissionCollection> perms;
    // the current set
    private Enumeration<Permission> permset;

    PermissionsEnumerator(Enumeration<PermissionCollection> e) {
        perms = e;
        permset = getNextEnumWithMore();
    }

    @Override
    public synchronized boolean hasMoreElements() {
        // if we enter with permissionimpl null, we know
        // there are no more left.

        if (permset == null) {
            return  false;
        }

        // try to see if there are any left in the current one

        if (permset.hasMoreElements()) {
            return true;
        }

        // get the next one that has something in it...
        permset = getNextEnumWithMore();

        // if it is null, we are done!
        return (permset != null);
    }

    @Override
    public synchronized Permission nextElement() {
        // hasMoreElements will update permset to the next permset
        // with something in it...

        if (hasMoreElements()) {
            return permset.nextElement();
        } else {
            throw new NoSuchElementException("PermissionsEnumerator");
        }
    }

    private Enumeration<Permission> getNextEnumWithMore() {
        while (perms.hasMoreElements()) {
            PermissionCollection pc = perms.nextElement();
            Enumeration<Permission> next = pc.elements();
            if (next.hasMoreElements()) {
                return next;
            }
        }
        return null;
    }
}
