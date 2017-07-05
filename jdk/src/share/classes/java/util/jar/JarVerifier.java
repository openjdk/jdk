/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package java.util.jar;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.security.*;
import java.security.cert.CertificateException;

import sun.security.util.ManifestDigester;
import sun.security.util.ManifestEntryVerifier;
import sun.security.util.SignatureFileVerifier;
import sun.security.util.Debug;

/**
 *
 * @author      Roland Schemers
 */
class JarVerifier {

    /* Are we debugging ? */
    static final Debug debug = Debug.getInstance("jar");

    /* a table mapping names to code signers, for jar entries that have
       had their actual hashes verified */
    private Hashtable verifiedSigners;

    /* a table mapping names to code signers, for jar entries that have
       passed the .SF/.DSA -> MANIFEST check */
    private Hashtable sigFileSigners;

    /* a hash table to hold .SF bytes */
    private Hashtable sigFileData;

    /** "queue" of pending PKCS7 blocks that we couldn't parse
     *  until we parsed the .SF file */
    private ArrayList pendingBlocks;

    /* cache of CodeSigner objects */
    private ArrayList signerCache;

    /* Are we parsing a block? */
    private boolean parsingBlockOrSF = false;

    /* Are we done parsing META-INF entries? */
    private boolean parsingMeta = true;

    /* Are there are files to verify? */
    private boolean anyToVerify = true;

    /* The output stream to use when keeping track of files we are interested
       in */
    private ByteArrayOutputStream baos;

    /** The ManifestDigester object */
    private ManifestDigester manDig;

    /** the bytes for the manDig object */
    byte manifestRawBytes[] = null;

    public JarVerifier(byte rawBytes[]) {
        manifestRawBytes = rawBytes;
        sigFileSigners = new Hashtable();
        verifiedSigners = new Hashtable();
        sigFileData = new Hashtable(11);
        pendingBlocks = new ArrayList();
        baos = new ByteArrayOutputStream();
    }

    /**
     * This method scans to see which entry we're parsing and
     * keeps various state information depending on what type of
     * file is being parsed.
     */
    public void beginEntry(JarEntry je, ManifestEntryVerifier mev)
        throws IOException
    {
        if (je == null)
            return;

        if (debug != null) {
            debug.println("beginEntry "+je.getName());
        }

        String name = je.getName();

        /*
         * Assumptions:
         * 1. The manifest should be the first entry in the META-INF directory.
         * 2. The .SF/.DSA files follow the manifest, before any normal entries
         * 3. Any of the following will throw a SecurityException:
         *    a. digest mismatch between a manifest section and
         *       the SF section.
         *    b. digest mismatch between the actual jar entry and the manifest
         */

        if (parsingMeta) {
            String uname = name.toUpperCase(Locale.ENGLISH);
            if ((uname.startsWith("META-INF/") ||
                 uname.startsWith("/META-INF/"))) {

                if (je.isDirectory()) {
                    mev.setEntry(null, je);
                    return;
                }

                if (SignatureFileVerifier.isBlockOrSF(uname)) {
                    /* We parse only DSA or RSA PKCS7 blocks. */
                    parsingBlockOrSF = true;
                    baos.reset();
                    mev.setEntry(null, je);
                }
                return;
            }
        }

        if (parsingMeta) {
            doneWithMeta();
        }

        if (je.isDirectory()) {
            mev.setEntry(null, je);
            return;
        }

        // be liberal in what you accept. If the name starts with ./, remove
        // it as we internally canonicalize it with out the ./.
        if (name.startsWith("./"))
            name = name.substring(2);

        // be liberal in what you accept. If the name starts with /, remove
        // it as we internally canonicalize it with out the /.
        if (name.startsWith("/"))
            name = name.substring(1);

        // only set the jev object for entries that have a signature
        if (sigFileSigners.get(name) != null) {
            mev.setEntry(name, je);
            return;
        }

        // don't compute the digest for this entry
        mev.setEntry(null, je);

        return;
    }

    /**
     * update a single byte.
     */

    public void update(int b, ManifestEntryVerifier mev)
        throws IOException
    {
        if (b != -1) {
            if (parsingBlockOrSF) {
                baos.write(b);
            } else {
                mev.update((byte)b);
            }
        } else {
            processEntry(mev);
        }
    }

    /**
     * update an array of bytes.
     */

    public void update(int n, byte[] b, int off, int len,
                       ManifestEntryVerifier mev)
        throws IOException
    {
        if (n != -1) {
            if (parsingBlockOrSF) {
                baos.write(b, off, n);
            } else {
                mev.update(b, off, n);
            }
        } else {
            processEntry(mev);
        }
    }

    /**
     * called when we reach the end of entry in one of the read() methods.
     */
    private void processEntry(ManifestEntryVerifier mev)
        throws IOException
    {
        if (!parsingBlockOrSF) {
            JarEntry je = mev.getEntry();
            if ((je != null) && (je.signers == null)) {
                je.signers = mev.verify(verifiedSigners, sigFileSigners);
                je.certs = mapSignersToCertArray(je.signers);
            }
        } else {

            try {
                parsingBlockOrSF = false;

                if (debug != null) {
                    debug.println("processEntry: processing block");
                }

                String uname = mev.getEntry().getName()
                                             .toUpperCase(Locale.ENGLISH);

                if (uname.endsWith(".SF")) {
                    String key = uname.substring(0, uname.length()-3);
                    byte bytes[] = baos.toByteArray();
                    // add to sigFileData in case future blocks need it
                    sigFileData.put(key, bytes);
                    // check pending blocks, we can now process
                    // anyone waiting for this .SF file
                    Iterator it = pendingBlocks.iterator();
                    while (it.hasNext()) {
                        SignatureFileVerifier sfv =
                            (SignatureFileVerifier) it.next();
                        if (sfv.needSignatureFile(key)) {
                            if (debug != null) {
                                debug.println(
                                 "processEntry: processing pending block");
                            }

                            sfv.setSignatureFile(bytes);
                            sfv.process(sigFileSigners);
                        }
                    }
                    return;
                }

                // now we are parsing a signature block file

                String key = uname.substring(0, uname.lastIndexOf("."));

                if (signerCache == null)
                    signerCache = new ArrayList();

                if (manDig == null) {
                    synchronized(manifestRawBytes) {
                        if (manDig == null) {
                            manDig = new ManifestDigester(manifestRawBytes);
                            manifestRawBytes = null;
                        }
                    }
                }

                SignatureFileVerifier sfv =
                  new SignatureFileVerifier(signerCache,
                                            manDig, uname, baos.toByteArray());

                if (sfv.needSignatureFileBytes()) {
                    // see if we have already parsed an external .SF file
                    byte[] bytes = (byte[]) sigFileData.get(key);

                    if (bytes == null) {
                        // put this block on queue for later processing
                        // since we don't have the .SF bytes yet
                        // (uname, block);
                        if (debug != null) {
                            debug.println("adding pending block");
                        }
                        pendingBlocks.add(sfv);
                        return;
                    } else {
                        sfv.setSignatureFile(bytes);
                    }
                }
                sfv.process(sigFileSigners);

            } catch (IOException ioe) {
                // e.g. sun.security.pkcs.ParsingException
                if (debug != null) debug.println("processEntry caught: "+ioe);
                // ignore and treat as unsigned
            } catch (SignatureException se) {
                if (debug != null) debug.println("processEntry caught: "+se);
                // ignore and treat as unsigned
            } catch (NoSuchAlgorithmException nsae) {
                if (debug != null) debug.println("processEntry caught: "+nsae);
                // ignore and treat as unsigned
            } catch (CertificateException ce) {
                if (debug != null) debug.println("processEntry caught: "+ce);
                // ignore and treat as unsigned
            }
        }
    }

    /**
     * Return an array of java.security.cert.Certificate objects for
     * the given file in the jar.
     */
    public java.security.cert.Certificate[] getCerts(String name)
    {
        return mapSignersToCertArray(getCodeSigners(name));
    }

    /**
     * return an array of CodeSigner objects for
     * the given file in the jar. this array is not cloned.
     *
     */
    public CodeSigner[] getCodeSigners(String name)
    {
        return (CodeSigner[])verifiedSigners.get(name);
    }

    /*
     * Convert an array of signers into an array of concatenated certificate
     * arrays.
     */
    private static java.security.cert.Certificate[] mapSignersToCertArray(
        CodeSigner[] signers) {

        if (signers != null) {
            ArrayList certChains = new ArrayList();
            for (int i = 0; i < signers.length; i++) {
                certChains.addAll(
                    signers[i].getSignerCertPath().getCertificates());
            }

            // Convert into a Certificate[]
            return (java.security.cert.Certificate[])
                certChains.toArray(
                    new java.security.cert.Certificate[certChains.size()]);
        }
        return null;
    }

    /**
     * returns true if there no files to verify.
     * should only be called after all the META-INF entries
     * have been processed.
     */
    boolean nothingToVerify()
    {
        return (anyToVerify == false);
    }

    /**
     * called to let us know we have processed all the
     * META-INF entries, and if we re-read one of them, don't
     * re-process it. Also gets rid of any data structures
     * we needed when parsing META-INF entries.
     */
    void doneWithMeta()
    {
        parsingMeta = false;
        anyToVerify = !sigFileSigners.isEmpty();
        baos = null;
        sigFileData = null;
        pendingBlocks = null;
        signerCache = null;
        manDig = null;
    }

    static class VerifierStream extends java.io.InputStream {

        private InputStream is;
        private JarVerifier jv;
        private ManifestEntryVerifier mev;
        private long numLeft;

        VerifierStream(Manifest man,
                       JarEntry je,
                       InputStream is,
                       JarVerifier jv) throws IOException
        {
            this.is = is;
            this.jv = jv;
            this.mev = new ManifestEntryVerifier(man);
            this.jv.beginEntry(je, mev);
            this.numLeft = je.getSize();
            if (this.numLeft == 0)
                this.jv.update(-1, this.mev);
        }

        public int read() throws IOException
        {
            if (numLeft > 0) {
                int b = is.read();
                jv.update(b, mev);
                numLeft--;
                if (numLeft == 0)
                    jv.update(-1, mev);
                return b;
            } else {
                return -1;
            }
        }

        public int read(byte b[], int off, int len) throws IOException {
            if ((numLeft > 0) && (numLeft < len)) {
                len = (int)numLeft;
            }

            if (numLeft > 0) {
                int n = is.read(b, off, len);
                jv.update(n, b, off, len, mev);
                numLeft -= n;
                if (numLeft == 0)
                    jv.update(-1, b, off, len, mev);
                return n;
            } else {
                return -1;
            }
        }

        public void close()
            throws IOException
        {
            if (is != null)
                is.close();
            is = null;
            mev = null;
            jv = null;
        }

        public int available() throws IOException {
            return is.available();
        }

    }
}
