/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.jar;

import java.io.*;
import java.util.*;
import java.security.*;

import sun.net.www.MessageHeader;
import java.util.Base64;


import sun.security.pkcs.*;
import sun.security.x509.AlgorithmId;

/**
 * <p>A signature file as defined in the <a
 * href="manifest.html">Manifest and Signature Format</a>. It has
 * essentially the same structure as a Manifest file in that it is a
 * set of RFC 822 headers (sections). The first section contains meta
 * data relevant to the entire file (i.e "Signature-Version:1.0") and
 * each subsequent section contains data relevant to specific entries:
 * entry sections.
 *
 * <p>Each entry section contains the name of an entry (which must
 * have a counterpart in the manifest). Like the manifest it contains
 * a hash, the hash of the manifest section corresponding to the
 * name. Since the manifest entry contains the hash of the data, this
 * is equivalent to a signature of the data, plus the attributes of
 * the manifest entry.
 *
 * <p>This signature file format deal with PKCS7 encoded DSA signature
 * block. It should be straightforward to extent to support other
 * algorithms.
 *
 * @author      David Brown
 * @author      Benjamin Renaud */

public class SignatureFile {

    /* Are we debugging? */
    static final boolean debug = false;

    /* list of headers that all pertain to a particular file in the
     * archive */
    private Vector<MessageHeader> entries = new Vector<>();

    /* Right now we only support SHA hashes */
    static final String[] hashes = {"SHA"};

    static final void debug(String s) {
        if (debug)
            System.out.println("sig> " + s);
    }

    /*
     * The manifest we're working with.  */
    private Manifest manifest;

    /*
     * The file name for the file. This is the raw name, i.e. the
     * extention-less 8 character name (such as MYSIGN) which wil be
     * used to build the signature filename (MYSIGN.SF) and the block
     * filename (MYSIGN.DSA) */
    private String rawName;

    /* The digital signature block corresponding to this signature
     * file.  */
    private PKCS7 signatureBlock;


    /**
     * Private constructor which takes a name a given signature
     * file. The name must be extension-less and less or equal to 8
     * character in length.  */
    private SignatureFile(String name) throws JarException {

        entries = new Vector<>();

        if (name != null) {
            if (name.length() > 8 || name.indexOf('.') != -1) {
                throw new JarException("invalid file name");
            }
            rawName = name.toUpperCase(Locale.ENGLISH);
        }
    }

    /**
     * Private constructor which takes a name a given signature file
     * and a new file predicate. If it is a new file, a main header
     * will be added. */
    private SignatureFile(String name, boolean newFile)
    throws JarException {

        this(name);

        if (newFile) {
            MessageHeader globals = new MessageHeader();
            globals.set("Signature-Version", "1.0");
            entries.addElement(globals);
        }
    }

    /**
     * Constructs a new Signature file corresponding to a given
     * Manifest. All entries in the manifest are signed.
     *
     * @param manifest the manifest to use.
     *
     * @param name for this signature file. This should
     * be less than 8 characters, and without a suffix (i.e.
     * without a period in it.
     *
     * @exception JarException if an invalid name is passed in.
     */
    public SignatureFile(Manifest manifest, String name)
    throws JarException {

        this(name, true);

        this.manifest = manifest;
        Enumeration<MessageHeader> enum_ = manifest.entries();
        while (enum_.hasMoreElements()) {
            MessageHeader mh = enum_.nextElement();
            String entryName = mh.findValue("Name");
            if (entryName != null) {
                add(entryName);
            }
        }
    }

    /**
     * Constructs a new Signature file corresponding to a given
     * Manifest. Specific entries in the manifest are signed.
     *
     * @param manifest the manifest to use.
     *
     * @param entries the entries to sign.
     *
     * @param filename for this signature file. This should
     * be less than 8 characters, and without a suffix (i.e.
     * without a period in it.
     *
     * @exception JarException if an invalid name is passed in.
     */
    public SignatureFile(Manifest manifest, String[] entries,
                         String filename)
    throws JarException {
        this(filename, true);
        this.manifest = manifest;
        add(entries);
    }

    /**
     * Construct a Signature file from an input stream.
     *
     * @exception IOException if an invalid name is passed in or if a
     * stream exception occurs.
     */
    public SignatureFile(InputStream is, String filename)
    throws IOException {
        this(filename);
        while (is.available() > 0) {
            MessageHeader m = new MessageHeader(is);
            entries.addElement(m);
        }
    }

   /**
     * Construct a Signature file from an input stream.
     *
     * @exception IOException if an invalid name is passed in or if a
     * stream exception occurs.
     */
    public SignatureFile(InputStream is) throws IOException {
        this(is, null);
    }

    public SignatureFile(byte[] bytes) throws IOException {
        this(new ByteArrayInputStream(bytes));
    }

    /**
     * Returns the name of the signature file, ending with a ".SF"
     * suffix */
    public String getName() {
        return "META-INF/" + rawName + ".SF";
    }

    /**
     * Returns the name of the block file, ending with a block suffix
     * such as ".DSA". */
    public String getBlockName() {
        String suffix = "DSA";
        if (signatureBlock != null) {
            SignerInfo info = signatureBlock.getSignerInfos()[0];
            suffix = info.getDigestEncryptionAlgorithmId().getName();
            String temp = AlgorithmId.getEncAlgFromSigAlg(suffix);
            if (temp != null) suffix = temp;
        }
        return "META-INF/" + rawName + "." + suffix;
    }

    /**
     * Returns the signature block associated with this file.
     */
    public PKCS7 getBlock() {
        return signatureBlock;
    }

    /**
     * Sets the signature block associated with this file.
     */
    public void setBlock(PKCS7 block) {
        this.signatureBlock = block;
    }

    /**
     * Add a set of entries from the current manifest.
     */
    public void add(String[] entries) throws JarException {
        for (int i = 0; i < entries.length; i++) {
            add (entries[i]);
        }
    }

    /**
     * Add a specific entry from the current manifest.
     */
    public void add(String entry) throws JarException {
        MessageHeader mh = manifest.getEntry(entry);
        if (mh == null) {
            throw new JarException("entry " + entry + " not in manifest");
        }
        MessageHeader smh;
        try {
            smh = computeEntry(mh);
        } catch (IOException e) {
            throw new JarException(e.getMessage());
        }
        entries.addElement(smh);
    }

    /**
     * Get the entry corresponding to a given name. Returns null if
     *the entry does not exist.
     */
    public MessageHeader getEntry(String name) {
        Enumeration<MessageHeader> enum_ = entries();
        while(enum_.hasMoreElements()) {
            MessageHeader mh = enum_.nextElement();
            if (name.equals(mh.findValue("Name"))) {
                return mh;
            }
        }
        return null;
    }

    /**
     * Returns the n-th entry. The global header is a entry 0.  */
    public MessageHeader entryAt(int n) {
        return entries.elementAt(n);
    }

    /**
     * Returns an enumeration of the entries.
     */
    public Enumeration<MessageHeader> entries() {
        return entries.elements();
    }

    /**
     * Given a manifest entry, computes the signature entry for this
     * manifest entry.
     */
    private MessageHeader computeEntry(MessageHeader mh) throws IOException {
        MessageHeader smh = new MessageHeader();

        String name = mh.findValue("Name");
        if (name == null) {
            return null;
        }
        smh.set("Name", name);

        try {
            for (int i = 0; i < hashes.length; ++i) {
                MessageDigest dig = getDigest(hashes[i]);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                mh.print(ps);
                byte[] headerBytes = baos.toByteArray();
                byte[] digest = dig.digest(headerBytes);
                smh.set(hashes[i] + "-Digest", Base64.getMimeEncoder().encodeToString(digest));
            }
            return smh;
        } catch (NoSuchAlgorithmException e) {
            throw new JarException(e.getMessage());
        }
    }

    private Hashtable<String, MessageDigest> digests = new Hashtable<>();

    private MessageDigest getDigest(String algorithm)
    throws NoSuchAlgorithmException {
        MessageDigest dig = digests.get(algorithm);
        if (dig == null) {
            dig = MessageDigest.getInstance(algorithm);
            digests.put(algorithm, dig);
        }
        dig.reset();
        return dig;
    }


    /**
     * Add a signature file at current position in a stream
     */
    public void stream(OutputStream os) throws IOException {

        /* the first header in the file should be the global one.
         * It should say "SignatureFile-Version: x.x"; barf if not
         */
        MessageHeader globals = entries.elementAt(0);
        if (globals.findValue("Signature-Version") == null) {
            throw new JarException("Signature file requires " +
                            "Signature-Version: 1.0 in 1st header");
        }

        PrintStream ps = new PrintStream(os);
        globals.print(ps);

        for (int i = 1; i < entries.size(); ++i) {
            MessageHeader mh = entries.elementAt(i);
            mh.print(ps);
        }
    }
}
