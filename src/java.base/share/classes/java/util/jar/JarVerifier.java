/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.util.jar;

import java.io.*;
import java.util.*;
import java.security.CodeSigner;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

import sun.security.util.ManifestDigester;
import sun.security.util.ManifestEntryVerifier;
import sun.security.util.SignatureFileVerifier;
import sun.security.util.Debug;

import static sun.security.util.SignatureFileVerifier.isInMetaInf;

/**
 *
 * @author      Roland Schemers
 */
class JarVerifier {

    public static final String MULTIPLE_MANIFEST_WARNING =
            "WARNING: Multiple MANIFEST.MF found. Treat JAR file as unsigned.";

    /* Are we debugging ? */
    static final Debug debug = Debug.getInstance("jar");

    /* a table mapping names to code signers, for jar entries that have
       had their actual hashes verified */
    private Hashtable<String, CodeSigner[]> verifiedSigners;

    /* a table mapping names to code signers, for jar entries that have
       passed the .SF/.DSA/.EC -> MANIFEST check */
    private Hashtable<String, CodeSigner[]> sigFileSigners;

    /* a hash table to hold .SF bytes */
    private Hashtable<String, byte[]> sigFileData;

    /** "queue" of pending PKCS7 blocks that we couldn't parse
     *  until we parsed the .SF file */
    private ArrayList<SignatureFileVerifier> pendingBlocks;

    /* cache of CodeSigner objects */
    private ArrayList<CodeSigner[]> signerCache;

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
    private volatile ManifestDigester manDig;

    /** the bytes for the manDig object */
    byte manifestRawBytes[] = null;

    /** the manifest name this JarVerifier is created upon */
    final String manifestName;

    /** collect -DIGEST-MANIFEST values for deny list */
    private List<Object> manifestDigests;

    /* A cache mapping code signers to the algorithms used to digest jar
       entries, and whether or not the algorithms are permitted. */
    private Map<CodeSigner[], Map<String, Boolean>> signersToAlgs;

    public JarVerifier(String name, byte[] rawBytes) {
        manifestName = name;
        manifestRawBytes = rawBytes;
        sigFileSigners = new Hashtable<>();
        verifiedSigners = new Hashtable<>();
        sigFileData = new Hashtable<>(11);
        pendingBlocks = new ArrayList<>();
        baos = new ByteArrayOutputStream();
        manifestDigests = new ArrayList<>();
        signersToAlgs = new HashMap<>();
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
         * 2. The .SF/.DSA/.EC files follow the manifest, before any normal entries
         * 3. Any of the following will throw a SecurityException:
         *    a. digest mismatch between a manifest section and
         *       the SF section.
         *    b. digest mismatch between the actual jar entry and the manifest
         */

        if (parsingMeta) {

            if (isInMetaInf(name)) {

                if (je.isDirectory()) {
                    mev.setEntry(null, je);
                    return;
                }
                String uname = name.toUpperCase(Locale.ENGLISH);
                if (uname.equals(JarFile.MANIFEST_NAME) ||
                        uname.equals(JarFile.INDEX_NAME)) {
                    return;
                }

                if (SignatureFileVerifier.isBlockOrSF(uname)) {
                    /* We parse only DSA, RSA or EC PKCS7 blocks. */
                    parsingBlockOrSF = true;
                    baos.reset();
                    mev.setEntry(null, je);
                    return;
                }

                // If a META-INF entry is not MF or block or SF, they should
                // be normal entries. According to 2 above, no more block or
                // SF will appear. Let's doneWithMeta.
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
        // (either verified or not)
        if (!name.equalsIgnoreCase(JarFile.MANIFEST_NAME)) {
            if (sigFileSigners.get(name) != null ||
                    verifiedSigners.get(name) != null) {
                mev.setEntry(name, je);
                return;
            }
        }

        // don't compute the digest for this entry
        mev.setEntry(null, je);
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
                je.signers = mev.verify(verifiedSigners, sigFileSigners,
                                        signersToAlgs);
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
                    for (SignatureFileVerifier sfv : pendingBlocks) {
                        if (sfv.needSignatureFile(key)) {
                            if (debug != null) {
                                debug.println(
                                 "processEntry: processing pending block");
                            }

                            sfv.setSignatureFile(bytes);
                            sfv.process(sigFileSigners, manifestDigests,
                                    manifestName);
                        }
                    }
                    return;
                }

                // now we are parsing a signature block file

                String key = uname.substring(0, uname.lastIndexOf('.'));

                if (signerCache == null)
                    signerCache = new ArrayList<>();

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
                    byte[] bytes = sigFileData.get(key);

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
                sfv.process(sigFileSigners, manifestDigests, manifestName);

            } catch (IOException | CertificateException |
                    NoSuchAlgorithmException | SignatureException e) {
                if (debug != null) debug.println("processEntry caught: "+e);
                // ignore and treat as unsigned
            }
        }
    }

    /**
     * Return an array of Certificate objects for
     * the given file in the jar.
     */
    public Certificate[] getCerts(JarEntry entry)
    {
        return mapSignersToCertArray(getCodeSigners(entry));
    }

    /**
     * return an array of CodeSigner objects for
     * the given file in the jar. this array is not cloned.
     *
     */
    public CodeSigner[] getCodeSigners(JarEntry entry)
    {
        return verifiedSigners.get(entry.getName());
    }

    /*
     * Convert an array of signers into an array of concatenated certificate
     * arrays.
     */
    private static Certificate[] mapSignersToCertArray(
        CodeSigner[] signers) {

        if (signers != null) {
            ArrayList<Certificate> certChains = new ArrayList<>();
            for (CodeSigner signer : signers) {
                certChains.addAll(
                    signer.getSignerCertPath().getCertificates());
            }

            // Convert into a Certificate[]
            return certChains.toArray(
                    new Certificate[certChains.size()]);
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
        // MANIFEST.MF is always treated as signed and verified,
        // move its signers from sigFileSigners to verifiedSigners.
        CodeSigner[] codeSigners = sigFileSigners.remove(manifestName);
        if (codeSigners != null) {
            verifiedSigners.put(manifestName, codeSigners);
        }
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
            this.is = Objects.requireNonNull(is);
            this.jv = jv;
            this.mev = new ManifestEntryVerifier(man, jv.manifestName);
            this.jv.beginEntry(je, mev);
            this.numLeft = je.getSize();
            if (this.numLeft == 0)
                this.jv.update(-1, this.mev);
        }

        public int read() throws IOException
        {
            ensureOpen();
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

        public int read(byte[] b, int off, int len) throws IOException {
            ensureOpen();
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
            ensureOpen();
            return is.available();
        }

        private void ensureOpen() throws IOException {
            if (is == null) {
                throw new IOException("stream closed");
            }
        }
    }

    /**
     * Returns whether the name is trusted. Used by
     * {@link Manifest#getTrustedAttributes(String)}.
     */
    boolean isTrustedManifestEntry(String name) {
        // How many signers? MANIFEST.MF is always verified
        CodeSigner[] forMan = verifiedSigners.get(manifestName);
        if (forMan == null) {
            return true;
        }
        // Check sigFileSigners first, because we are mainly dealing with
        // non-file entries which will stay in sigFileSigners forever.
        CodeSigner[] forName = sigFileSigners.get(name);
        if (forName == null) {
            forName = verifiedSigners.get(name);
        }
        // Returns trusted if all signers sign the entry
        return forName != null && forName.length == forMan.length;
    }
}
