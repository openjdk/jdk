/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.jar;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.jar.*;
import java.security.cert.Certificate;
import java.security.AccessController;
import java.security.cert.X509Certificate;
import java.security.PublicKey;
import java.security.Principal;
import sun.security.provider.SystemIdentity;

/**
 * This is OBSOLETE. DO NOT USE THIS. Use
 * java.util.jar.JarEntry.getCertificates instead. It has to stay here
 * because some apps (namely HJ and HJV) call directly into it.
 *
 * This class is stripped down greatly from JDK 1.1.x.
 *
 * @author Roland Schemers
 */
public class JarVerifierStream extends ZipInputStream {

    private JarEntry current;
    private Hashtable<String, Vector<SystemIdentity>> verified
        = new Hashtable<String, Vector<SystemIdentity>>();
    private JarInputStream jis;
    private sun.tools.jar.Manifest man = null;

    /**
     * construct a JarVerfierStream from an input stream.
     */
    public JarVerifierStream(InputStream is)
         throws IOException
    {
        super(is);
        jis = new JarInputStream(is);
    }

    public void close()
        throws IOException
    {
        jis.close();
    }

    public void closeEntry() throws IOException {
        jis.closeEntry();
    }

    /**
     * This method scans to see which entry we're parsing and
     * keeps various state information depending on what type of
     * file is being parsed. Files it treats specially are: <ul>
     *
     * <li>Manifest files. At any point, this stream can be queried
     * for a manifest. If it is present, a Manifest object will be
     * returned.
     *
     * <li>Block Signature file. Like with the manifest, the stream
     * can be queried at any time for all blocks parsed thus far.
     *
     * </ul>
     */
    public synchronized ZipEntry getNextEntry() throws IOException {
        current = (JarEntry) jis.getNextEntry();
        return current;
    }

    /**
     * read a single byte.
     */
    public int read() throws IOException {
        int n = jis.read();
        if (n == -1) {
            addIds();
        }
        return n;
    }

    /**
     * read an array of bytes.
     */
    public int read(byte[] b, int off, int len) throws IOException {
        int n = jis.read(b, off, len);
        if (n == -1) {
            addIds();
        }
        return n;
    }

    private void addIds()
    {

        if (current != null) {
            Certificate[] certs = current.getCertificates();
            if (certs != null) {
                Vector<SystemIdentity> ids = getIds(certs);
                if (ids != null) {
                    verified.put(current.getName(), ids);
                }
            }
        }
    }

    /**
     * Returns a Hashtable mapping filenames to vectors of identities.
     */
    public Hashtable getVerifiedSignatures() {
        /* we may want to return a copy of this at some point.
           For now we simply trust the caller */
        if (verified.isEmpty())
            return null;
        else
            return verified;
    }

    /**
     * Returns an enumeration of PKCS7 blocks. This looks bogus,
     * but Hotjava just checks to see if enumeration is not null
     * to see if anything was signed!
     */
    public Enumeration getBlocks() {
        if (verified.isEmpty()) {
            return null;
        } else {
            return new Enumeration() {
                public boolean hasMoreElements() { return false; }
                public Object nextElement() { return null; }
            };
        }
    }

    /**
     * This method used to be called by various versions of
     * AppletResourceLoader, even though they didn't do anything with
     * the result. We leave them and return null for backwards compatability.
     */
    public Hashtable getNameToHash() {
        return null;
    }

    /**
     * Convert java.util.jar.Manifest object to a sun.tools.jar.Manifest
     * object.
     */

    public sun.tools.jar.Manifest getManifest() {
        if (man == null) {
            try {
                java.util.jar.Manifest jman = jis.getManifest();
                if (jman == null)
                    return null;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                jman.write(baos);
                byte[] data = baos.toByteArray();
                man = new sun.tools.jar.Manifest(data);
            } catch (IOException ioe) {
                // return null
            }
        }
        return man;
    }

    static class CertCache {
        Certificate [] certs;
        Vector<SystemIdentity> ids;

        boolean equals(Certificate[] certs) {
                if (this.certs == null) {
                    if (certs!= null)
                        return false;
                    else
                        return true;
                }

                if (certs == null)
                    return false;

                boolean match;

                for (int i = 0; i < certs.length; i++) {
                    match = false;
                    for (int j = 0; j < this.certs.length; j++) {
                        if (certs[i].equals(this.certs[j])) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) return false;
                }

                for (int i = 0; i < this.certs.length; i++) {
                    match = false;
                    for (int j = 0; j < certs.length; j++) {
                        if (this.certs[i].equals(certs[j])) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) return false;
                }
                return true;
        }
    }

    private ArrayList<CertCache> certCache = null;


    /**
     * Returns the Identity vector for the given array of Certificates
     */
    protected Vector<SystemIdentity> getIds(Certificate[] certs) {
        if (certs == null)
            return null;

        if (certCache == null)
            certCache = new ArrayList<CertCache>();
        CertCache cc;
        for (int i = 0; i < certCache.size(); i++) {
            cc = certCache.get(i);
            if (cc.equals(certs)) {
                return cc.ids;
            }
        }
        cc = new CertCache();
        cc.certs = certs;

        if (certs.length > 0) {
            for (int i=0; i<certs.length; i++) {
                try {
                    X509Certificate cert = (X509Certificate) certs[i];
                    Principal tmpName = cert.getSubjectDN();
                    final SystemIdentity id = new SystemIdentity(
                                                           tmpName.getName(),
                                                           null);

                    byte[] encoded = cert.getEncoded();
                    final java.security.Certificate oldC =
                        new sun.security.x509.X509Cert(encoded);
                    try {
                        AccessController.doPrivileged(
                         new java.security.PrivilegedExceptionAction<Void>() {
                            public Void run()
                                throws java.security.KeyManagementException
                            {
                                id.addCertificate(oldC);
                                return null;
                            }
                        });
                    } catch (java.security.PrivilegedActionException pae) {
                        throw (java.security.KeyManagementException)
                            pae.getException();
                    }
                    if (cc.ids == null)
                        cc.ids = new Vector<SystemIdentity>();
                    cc.ids.addElement(id);
                } catch (java.security.KeyManagementException kme) {
                    // ignore if we can't create Identity
                } catch (IOException ioe) {
                    // ignore if we can't parse
                } catch (java.security.cert.CertificateEncodingException cee) {
                    // ignore if we can't encode
                }
            }
        }
        certCache.add(cc);
        return cc.ids;
    }
}
