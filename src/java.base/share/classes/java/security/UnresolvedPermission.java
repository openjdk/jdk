/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

import sun.security.util.IOUtils;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Hashtable;
import java.lang.reflect.*;
import java.security.cert.*;
import java.util.List;
import java.util.Objects;

/**
 * The {@code UnresolvedPermission} class used to hold Permissions that were
 * "unresolved" when the {@code Policy} was initialized. Installing a
 * system-wide {@code Policy} object is no longer supported.
 *
 * @see java.security.Permission
 * @see java.security.Permissions
 * @see java.security.PermissionCollection
 * @see java.security.Policy
 *
 * @author Roland Schemers
 * @since 1.2
 *
 * @deprecated This permission cannot be used for controlling access to
 *       resources as the Security Manager is no longer supported.
 */

@Deprecated(since="25", forRemoval=true)
public final class UnresolvedPermission extends Permission
implements java.io.Serializable
{

    @java.io.Serial
    private static final long serialVersionUID = -4821973115467008846L;

    private static final sun.security.util.Debug debug =
        sun.security.util.Debug.getInstance
        ("policy,access", "UnresolvedPermission");

    /**
     * The class name of the Permission class that will be
     * created when this unresolved permission is resolved.
     *
     * @serial
     */
    private final String type;

    /**
     * The permission name.
     *
     * @serial
     */
    private final String name;

    /**
     * The actions of the permission.
     *
     * @serial
     */
    private final String actions;

    private transient java.security.cert.Certificate[] certs;

    /**
     * Creates a new {@code UnresolvedPermission} containing the permission
     * information needed later to actually create a Permission of the
     * specified class, when the permission is resolved.
     *
     * @param type the class name of the Permission class that will be
     * created when this unresolved permission is resolved.
     * @param name the name of the permission.
     * @param actions the actions of the permission.
     * @param certs the certificates the permission's class was signed with.
     * This is a list of certificate chains, where each chain is composed of a
     * signer certificate and optionally its supporting certificate chain.
     * Each chain is ordered bottom-to-top (i.e., with the signer certificate
     * first and the (root) certificate authority last). The signer
     * certificates are copied from the array. Subsequent changes to
     * the array will not affect this UnresolvedPermission.
     */
    public UnresolvedPermission(String type,
                                String name,
                                String actions,
                                java.security.cert.Certificate[] certs)
    {
        super(type);

        if (type == null)
                throw new NullPointerException("type can't be null");

        // Perform a defensive copy and reassign certs if we have a non-null
        // reference
        if (certs != null) {
            certs = certs.clone();
        }

        this.type = type;
        this.name = name;
        this.actions = actions;

        if (certs != null) {
            // Extract the signer certs from the list of certificates.
            for (int i = 0; i < certs.length; i++) {
                if (!(certs[i] instanceof X509Certificate)) {
                    // there is no concept of signer certs, so we store the
                    // entire cert array.  No further processing is necessary.
                    this.certs = certs;
                    return;
                }
            }

            // Go through the list of certs and see if all the certs are
            // signer certs.
            int i = 0;
            int count = 0;
            while (i < certs.length) {
                count++;
                while (((i + 1) < certs.length) &&
                       ((X509Certificate)certs[i]).getIssuerX500Principal().equals(
                           ((X509Certificate)certs[i + 1]).getSubjectX500Principal())) {
                    i++;
                }
                i++;
            }
            if (count == certs.length) {
                // All the certs are signer certs, so we store the entire
                // array.  No further processing is needed.
                this.certs = certs;
                return;
            }

            // extract the signer certs
            ArrayList<java.security.cert.Certificate> signerCerts =
                new ArrayList<>();
            i = 0;
            while (i < certs.length) {
                signerCerts.add(certs[i]);
                while (((i + 1) < certs.length) &&
                    ((X509Certificate)certs[i]).getIssuerX500Principal().equals(
                      ((X509Certificate)certs[i + 1]).getSubjectX500Principal())) {
                    i++;
                }
                i++;
            }
            this.certs =
                new java.security.cert.Certificate[signerCerts.size()];
            signerCerts.toArray(this.certs);
        }
    }


    private static final Class<?>[] PARAMS0 = { };
    private static final Class<?>[] PARAMS1 = { String.class };
    private static final Class<?>[] PARAMS2 = { String.class, String.class };

    /**
     * try and resolve this permission using the class loader of the permission
     * that was passed in.
     */
    Permission resolve(Permission p, java.security.cert.Certificate[] certs) {
        if (this.certs != null) {
            // if p wasn't signed, we don't have a match
            if (certs == null) {
                return null;
            }

            // all certs in this.certs must be present in certs
            boolean match;
            for (int i = 0; i < this.certs.length; i++) {
                match = false;
                for (int j = 0; j < certs.length; j++) {
                    if (this.certs[i].equals(certs[j])) {
                        match = true;
                        break;
                    }
                }
                if (!match) return null;
            }
        }
        try {
            Class<?> pc = p.getClass();

            if (name == null && actions == null) {
                try {
                    Constructor<?> c = pc.getConstructor(PARAMS0);
                    return (Permission)c.newInstance(new Object[] {});
                } catch (NoSuchMethodException ne) {
                    try {
                        Constructor<?> c = pc.getConstructor(PARAMS1);
                        return (Permission) c.newInstance(
                              new Object[] {null});
                    } catch (NoSuchMethodException ne1) {
                        Constructor<?> c = pc.getConstructor(PARAMS2);
                        return (Permission) c.newInstance(
                              new Object[] {null, null});
                    }
                }
            } else {
                if (name != null && actions == null) {
                    try {
                        Constructor<?> c = pc.getConstructor(PARAMS1);
                        return (Permission) c.newInstance(
                              new Object[] { name});
                    } catch (NoSuchMethodException ne) {
                        Constructor<?> c = pc.getConstructor(PARAMS2);
                        return (Permission) c.newInstance(
                              new Object[] { name, null});
                    }
                } else {
                    Constructor<?> c = pc.getConstructor(PARAMS2);
                    return (Permission) c.newInstance(
                          new Object[] { name, actions });
                }
            }
        } catch (NoSuchMethodException nsme) {
            if (debug != null ) {
                debug.println("NoSuchMethodException:\n  could not find " +
                        "proper constructor for " + type);
                nsme.printStackTrace();
            }
            return null;
        } catch (Exception e) {
            if (debug != null ) {
                debug.println("unable to instantiate " + name);
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * This method always returns {@code false} for unresolved permissions.
     * That is, an {@code UnresolvedPermission} is never considered to
     * imply another permission.
     *
     * @param p the permission to check against.
     *
     * @return {@code false}.
     */
    @Override
    public boolean implies(Permission p) {
        return false;
    }

    /**
     * Checks two {@code UnresolvedPermission} objects for equality.
     * Checks that {@code obj} is an {@code UnresolvedPermission}, and has
     * the same type (class) name, permission name, actions, and
     * certificates as this object.
     *
     * <p> To determine certificate equality, this method only compares
     * actual signer certificates.  Supporting certificate chains
     * are not taken into consideration by this method.
     *
     * @param obj the object we are testing for equality with this object.
     *
     * @return true if {@code obj} is an {@code UnresolvedPermission},
     * and has the same type (class) name, permission name, actions, and
     * certificates as this object.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof UnresolvedPermission that))
            return false;

        // check type
        if (!this.type.equals(that.type)) {
            return false;
        }

        // check name
        if (!Objects.equals(this.name, that.name)) {
            return false;
        }

        // check actions
        if (!Objects.equals(this.actions, that.actions)) {
            return false;
        }

        // check certs
        if (this.certs == null && that.certs != null ||
            this.certs != null && that.certs == null ||
            this.certs != null &&
               this.certs.length != that.certs.length) {
            return false;
        }

        int i,j;
        boolean match;

        for (i = 0; this.certs != null && i < this.certs.length; i++) {
            match = false;
            for (j = 0; j < that.certs.length; j++) {
                if (this.certs[i].equals(that.certs[j])) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
        }

        for (i = 0; that.certs != null && i < that.certs.length; i++) {
            match = false;
            for (j = 0; j < this.certs.length; j++) {
                if (that.certs[i].equals(this.certs[j])) {
                    match = true;
                    break;
                }
            }
            if (!match) return false;
        }
        return true;
    }

    /**
     * {@return the hash code value for this object}
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, name, actions);
    }

    /**
     * Returns the canonical string representation of the actions,
     * which currently is the empty string "", since there are no actions for
     * an {@code UnresolvedPermission}. That is, the actions for the
     * permission that will be created when this {@code UnresolvedPermission}
     * is resolved may be non-null, but an {@code UnresolvedPermission}
     * itself is never considered to have any actions.
     *
     * @return the empty string "".
     */
    @Override
    public String getActions()
    {
        return "";
    }

    /**
     * Get the type (class name) of the underlying permission that
     * has not been resolved.
     *
     * @return the type (class name) of the underlying permission that
     *  has not been resolved
     *
     * @since 1.5
     */
    public String getUnresolvedType() {
        return type;
    }

    /**
     * Get the target name of the underlying permission that
     * has not been resolved.
     *
     * @return the target name of the underlying permission that
     *          has not been resolved, or {@code null},
     *          if there is no target name
     *
     * @since 1.5
     */
    public String getUnresolvedName() {
        return name;
    }

    /**
     * Get the actions for the underlying permission that
     * has not been resolved.
     *
     * @return the actions for the underlying permission that
     *          has not been resolved, or {@code null}
     *          if there are no actions
     *
     * @since 1.5
     */
    public String getUnresolvedActions() {
        return actions;
    }

    /**
     * Get the signer certificates (without any supporting chain)
     * for the underlying permission that has not been resolved.
     *
     * @return the signer certificates for the underlying permission that
     * has not been resolved, or {@code null}, if there are no signer
     * certificates.
     * Returns a new array each time this method is called.
     *
     * @since 1.5
     */
    public java.security.cert.Certificate[] getUnresolvedCerts() {
        return (certs == null) ? null : certs.clone();
    }

    /**
     * Returns a string describing this {@code UnresolvedPermission}.
     * The convention is to specify the class name, the permission name,
     * and the actions, in the following format:
     * '(unresolved "ClassName" "name" "actions")'.
     *
     * @return information about this {@code UnresolvedPermission}.
     */
    @Override
    public String toString() {
        return "(unresolved " + type + " " + name + " " + actions + ")";
    }

    /**
     * Returns a new PermissionCollection object for storing
     * {@code UnresolvedPermission} objects.
     *
     * @return a new PermissionCollection object suitable for
     * storing {@code UnresolvedPermissions}.
     */
    @Override
    public PermissionCollection newPermissionCollection() {
        return new UnresolvedPermissionCollection();
    }

    /**
     * Writes this object out to a stream (i.e., serializes it).
     *
     * @serialData An initial {@code String} denoting the
     * {@code type} is followed by a {@code String} denoting the
     * {@code name} is followed by a {@code String} denoting the
     * {@code actions} is followed by an {@code int} indicating the
     * number of certificates to follow
     * (a value of "zero" denotes that there are no certificates associated
     * with this object).
     * Each certificate is written out starting with a {@code String}
     * denoting the certificate type, followed by an
     * {@code int} specifying the length of the certificate encoding,
     * followed by the certificate encoding itself which is written out as an
     * array of bytes.
     *
     * @param  oos the {@code ObjectOutputStream} to which data is written
     * @throws IOException if an I/O error occurs
     */
    @java.io.Serial
    private void writeObject(java.io.ObjectOutputStream oos)
        throws IOException
    {
        oos.defaultWriteObject();

        if (certs==null || certs.length==0) {
            oos.writeInt(0);
        } else {
            // write out the total number of certs
            oos.writeInt(certs.length);
            // write out each cert, including its type
            for (int i=0; i < certs.length; i++) {
                java.security.cert.Certificate cert = certs[i];
                try {
                    oos.writeUTF(cert.getType());
                    byte[] encoded = cert.getEncoded();
                    oos.writeInt(encoded.length);
                    oos.write(encoded);
                } catch (CertificateEncodingException cee) {
                    throw new IOException(cee.getMessage());
                }
            }
        }
    }

    /**
     * Restores this object from a stream (i.e., deserializes it).
     *
     * @param  ois the {@code ObjectInputStream} from which data is read
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if a serialized class cannot be loaded
     */
    @java.io.Serial
    private void readObject(java.io.ObjectInputStream ois)
        throws IOException, ClassNotFoundException
    {
        CertificateFactory cf;
        Hashtable<String, CertificateFactory> cfs = null;
        List<Certificate> certList = null;

        ois.defaultReadObject();

        if (type == null)
                throw new NullPointerException("type can't be null");

        // process any new-style certs in the stream (if present)
        int size = ois.readInt();
        if (size > 0) {
            // we know of 3 different cert types: X.509, PGP, SDSI, which
            // could all be present in the stream at the same time
            cfs = new Hashtable<>(3);
            certList = new ArrayList<>(Math.min(size, 20));
        } else if (size < 0) {
            throw new IOException("size cannot be negative");
        }

        for (int i=0; i<size; i++) {
            // read the certificate type, and instantiate a certificate
            // factory of that type (reuse existing factory if possible)
            String certType = ois.readUTF();
            if (cfs.containsKey(certType)) {
                // reuse certificate factory
                cf = cfs.get(certType);
            } else {
                // create new certificate factory
                try {
                    cf = CertificateFactory.getInstance(certType);
                } catch (CertificateException ce) {
                    throw new ClassNotFoundException
                        ("Certificate factory for "+certType+" not found");
                }
                // store the certificate factory so we can reuse it later
                cfs.put(certType, cf);
            }
            // parse the certificate
            byte[] encoded = IOUtils.readExactlyNBytes(ois, ois.readInt());
            ByteArrayInputStream bais = new ByteArrayInputStream(encoded);
            try {
                certList.add(cf.generateCertificate(bais));
            } catch (CertificateException ce) {
                throw new IOException(ce.getMessage());
            }
            bais.close();
        }
        if (certList != null) {
            this.certs = certList.toArray(
                    new java.security.cert.Certificate[size]);
        }
    }
}
