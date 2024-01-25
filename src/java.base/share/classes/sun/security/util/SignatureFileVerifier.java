/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import sun.security.action.GetIntegerAction;
import sun.security.jca.Providers;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;

public class SignatureFileVerifier {

    /* Are we debugging ? */
    private static final Debug debug = Debug.getInstance("jar");

    private final ArrayList<CodeSigner[]> signerCache;

    private static final String ATTR_DIGEST =
        "-DIGEST-" + ManifestDigester.MF_MAIN_ATTRS.toUpperCase(Locale.ENGLISH);

    /** the PKCS7 block for this .DSA/.RSA/.EC file */
    private final PKCS7 block;

    /** the raw bytes of the .SF file */
    private byte[] sfBytes;

    /** the name of the signature block file, uppercase and without
     *  the extension (.DSA/.RSA/.EC)
     */
    private final String name;

    /** the ManifestDigester */
    private final ManifestDigester md;

    /** cache of created MessageDigest objects */
    private HashMap<String, MessageDigest> createdDigests;

    /* workaround for parsing Netscape jars  */
    private boolean workaround = false;

    /* for generating certpath objects */
    private final CertificateFactory certificateFactory;

    /** Algorithms that have been previously checked against disabled
     *  constraints.
     */
    private final Map<String, Boolean> permittedAlgs = new HashMap<>();

    /** ConstraintsParameters for checking disabled algorithms */
    private JarConstraintsParameters params;

    private static final String META_INF = "META-INF/";

    // the maximum allowed size in bytes for the signature-related files
    public static final int MAX_SIG_FILE_SIZE = initializeMaxSigFileSize();

    // The maximum size of array to allocate. Some VMs reserve some header words in an array.
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Create the named SignatureFileVerifier.
     *
     * @param name the name of the signature block file (.DSA/.RSA/.EC)
     *
     * @param rawBytes the raw bytes of the signature block file
     */
    public SignatureFileVerifier(ArrayList<CodeSigner[]> signerCache,
                                 ManifestDigester md,
                                 String name,
                                 byte[] rawBytes)
        throws IOException, CertificateException
    {
        // new PKCS7() calls CertificateFactory.getInstance()
        // need to use local providers here, see Providers class
        Object obj = null;
        try {
            obj = Providers.startJarVerification();
            block = new PKCS7(rawBytes);
            sfBytes = block.getContentInfo().getData();
            certificateFactory = CertificateFactory.getInstance("X509");
        } finally {
            Providers.stopJarVerification(obj);
        }
        this.name = name.substring(0, name.lastIndexOf('.'))
                                                   .toUpperCase(Locale.ENGLISH);
        this.md = md;
        this.signerCache = signerCache;
    }

    /**
     * returns true if we need the .SF file
     */
    public boolean needSignatureFileBytes()
    {

        return sfBytes == null;
    }


    /**
     * returns true if we need this .SF file.
     *
     * @param name the name of the .SF file without the extension
     *
     */
    public boolean needSignatureFile(String name)
    {
        return this.name.equalsIgnoreCase(name);
    }

    /**
     * used to set the raw bytes of the .SF file when it
     * is external to the signature block file.
     */
    public void setSignatureFile(byte[] sfBytes)
    {
        this.sfBytes = sfBytes;
    }

    /**
     * Utility method used by JarVerifier and JarSigner
     * to determine if a path is located directly in the
     * META-INF/ directory
     *
     * @param name the path name to check
     * @return true if the path resides in META-INF directly, ignoring case
     */
    public static boolean isInMetaInf(String name) {
        return name.regionMatches(true, 0, META_INF, 0, META_INF.length())
                && name.lastIndexOf('/') < META_INF.length();
    }
    /**
     * Utility method used by JarVerifier and JarSigner
     * to determine the signature file names and PKCS7 block
     * files names that are supported
     *
     * @param s file name
     * @return true if the input file name is a supported
     *          Signature File or PKCS7 block file name
     * @see #getBlockExtension(PrivateKey)
     */
    public static boolean isBlockOrSF(String s) {
        // Note: keep this in sync with j.u.z.ZipFile.Source#isSignatureRelated
        // we currently only support DSA, RSA or EC PKCS7 blocks
        return s.endsWith(".SF")
            || s.endsWith(".DSA")
            || s.endsWith(".RSA")
            || s.endsWith(".EC");
    }

    /**
     * Returns the signed JAR block file extension for a key.
     *
     * Returns "DSA" for unknown algorithms. This is safe because the
     * signature verification process does not require the extension to
     * match the key algorithm as long as it is "RSA", "DSA", or "EC."
     *
     * @param key the key used to sign the JAR file
     * @return the extension
     * @see #isBlockOrSF(String)
     */
    public static String getBlockExtension(PrivateKey key) {
        return switch (key.getAlgorithm().toUpperCase(Locale.ENGLISH)) {
            case "RSA", "RSASSA-PSS" -> "RSA";
            case "EC", "EDDSA", "ED25519", "ED448" -> "EC";
            default -> "DSA";
        };
    }

    /**
     * Yet another utility method used by JarVerifier and JarSigner
     * to determine what files are signature related, which includes
     * the MANIFEST, SF files, known signature block files, and other
     * unknown signature related files (those starting with SIG- with
     * an optional [A-Z0-9]{1,3} extension right inside META-INF).
     *
     * @param name file name
     * @return true if the input file name is signature related
     */
    public static boolean isSigningRelated(String name) {
        if (!isInMetaInf(name)) {
            return false;
        }
        name = name.toUpperCase(Locale.ENGLISH);
        if (isBlockOrSF(name) || name.equals("META-INF/MANIFEST.MF")) {
            return true;
        } else if (name.startsWith("SIG-", META_INF.length())) {
            // check filename extension
            // see https://docs.oracle.com/en/java/javase/19/docs/specs/jar/jar.html#digital-signatures
            // for what filename extensions are legal
            int extIndex = name.lastIndexOf('.');
            if (extIndex != -1) {
                String ext = name.substring(extIndex + 1);
                // validate length first
                if (ext.length() > 3 || ext.length() < 1) {
                    return false;
                }
                // then check chars, must be in [a-zA-Z0-9] per the jar spec
                for (int index = 0; index < ext.length(); index++) {
                    char cc = ext.charAt(index);
                    // chars are promoted to uppercase so skip lowercase checks
                    if ((cc < 'A' || cc > 'Z') && (cc < '0' || cc > '9')) {
                        return false;
                    }
                }
            }
            return true; // no extension is OK
        }
        return false;
    }

    /** get digest from cache */

    private MessageDigest getDigest(String algorithm) {
        if (createdDigests == null)
            createdDigests = new HashMap<>();

        MessageDigest digest = createdDigests.get(algorithm);

        if (digest == null) {
            try {
                digest = MessageDigest.getInstance(algorithm);
                createdDigests.put(algorithm, digest);
            } catch (NoSuchAlgorithmException nsae) {
                // ignore
            }
        }
        return digest;
    }

    /**
     * process the signature block file. Goes through the .SF file
     * and adds code signers for each section where the .SF section
     * hash was verified against the Manifest section.
     *
     *
     */
    public void process(Hashtable<String, CodeSigner[]> signers,
            List<Object> manifestDigests, String manifestName)
        throws IOException, SignatureException, NoSuchAlgorithmException,
            CertificateException
    {
        // calls Signature.getInstance() and MessageDigest.getInstance()
        // need to use local providers here, see Providers class
        Object obj = null;
        try {
            obj = Providers.startJarVerification();
            processImpl(signers, manifestDigests, manifestName);
        } finally {
            Providers.stopJarVerification(obj);
        }

    }

    private void processImpl(Hashtable<String, CodeSigner[]> signers,
            List<Object> manifestDigests, String manifestName)
        throws IOException, SignatureException, NoSuchAlgorithmException,
            CertificateException
    {
        Manifest sf = new Manifest();
        sf.read(new ByteArrayInputStream(sfBytes));

        String version =
            sf.getMainAttributes().getValue(Attributes.Name.SIGNATURE_VERSION);

        if ((version == null) || !(version.equalsIgnoreCase("1.0"))) {
            // XXX: should this be an exception?
            // for now, we just ignore this signature file
            return;
        }

        SignerInfo[] infos = block.verify(sfBytes);

        if (infos == null) {
            throw new SecurityException("cannot verify signature block file " +
                                        name);
        }

        CodeSigner[] newSigners = getSigners(infos, block);

        // make sure we have something to do all this work for...
        if (newSigners == null) {
            return;
        }

        // check if any of the algorithms used to verify the SignerInfos
        // are disabled
        params = new JarConstraintsParameters(newSigners);
        Set<String> notDisabledAlgorithms =
            SignerInfo.verifyAlgorithms(infos, params, name + " PKCS7");

        // add the SignerInfo algorithms that are ok to the permittedAlgs map
        // so they are not checked again
        for (String algorithm : notDisabledAlgorithms) {
            permittedAlgs.put(algorithm, Boolean.TRUE);
        }

        Iterator<Map.Entry<String,Attributes>> entries =
                                sf.getEntries().entrySet().iterator();

        // see if we can verify the whole manifest first
        boolean manifestSigned = verifyManifestHash(sf, md, manifestDigests);

        // verify manifest main attributes
        if (!manifestSigned && !verifyManifestMainAttrs(sf, md)) {
            throw new SecurityException
                ("Invalid signature file digest for Manifest main attributes");
        }

        // go through each section in the signature file
        while(entries.hasNext()) {

            Map.Entry<String,Attributes> e = entries.next();
            String name = e.getKey();

            if (manifestSigned ||
                (verifySection(e.getValue(), name, md))) {

                if (name.startsWith("./"))
                    name = name.substring(2);

                if (name.startsWith("/"))
                    name = name.substring(1);

                updateSigners(newSigners, signers, name);

                if (debug != null) {
                    debug.println("processSignature signed name = "+name);
                }

            } else if (debug != null) {
                debug.println("processSignature unsigned name = "+name);
            }
        }

        // MANIFEST.MF is always regarded as signed
        updateSigners(newSigners, signers, manifestName);
    }

    /**
     * Check if algorithm is permitted using the permittedAlgs Map.
     * If the algorithm is not in the map, check against disabled algorithms and
     * store the result. If the algorithm is in the map use that result.
     * False is returned for weak algorithm, true for good algorithms.
     */
    private boolean permittedCheck(String key, String algorithm) {
        Boolean permitted = permittedAlgs.get(algorithm);
        if (permitted == null) {
            try {
                params.setExtendedExceptionMsg(name + ".SF", key + " attribute");
                DisabledAlgorithmConstraints
                    .jarConstraints().permits(algorithm, params, false);
            } catch (GeneralSecurityException e) {
                permittedAlgs.put(algorithm, Boolean.FALSE);
                permittedAlgs.put(key.toUpperCase(Locale.ENGLISH),
                        Boolean.FALSE);
                if (debug != null) {
                    if (e.getMessage() != null) {
                        debug.println(key + ":  " + e.getMessage());
                    } else {
                        debug.println("Debug info only. " +  key + ":  " +
                            algorithm +
                            " was disabled, no exception msg given.");
                        e.printStackTrace();
                    }
                }
                return false;
            }

            permittedAlgs.put(algorithm, Boolean.TRUE);
            return true;
        }

        // Algorithm has already been checked, return the value from map.
        return permitted.booleanValue();
    }

    /**
     * With a given header (*-DIGEST*), return a string that lists all the
     * algorithms associated with the header.
     * If there are none, return "Unknown Algorithm".
     */
    String getWeakAlgorithms(String header) {
        String w = "";
        try {
            for (String key : permittedAlgs.keySet()) {
                if (key.endsWith(header)) {
                    w += key.substring(0, key.length() - header.length()) + " ";
                }
            }
        } catch (RuntimeException e) {
            w = "Unknown Algorithm(s).  Error processing " + header + ".  " +
                    e.getMessage();
        }

        // This means we have an error in finding weak algorithms, run in
        // debug mode to see permittedAlgs map's values.
        if (w.isEmpty()) {
            return "Unknown Algorithm(s)";
        }

        return w;
    }

    /**
     * See if the whole manifest was signed.
     */
    private boolean verifyManifestHash(Manifest sf,
                                       ManifestDigester md,
                                       List<Object> manifestDigests)
         throws SignatureException
    {
        Attributes mattr = sf.getMainAttributes();
        boolean manifestSigned = false;
        // If only weak algorithms are used.
        boolean weakAlgs = true;
        // If a "*-DIGEST-MANIFEST" entry is found.
        boolean validEntry = false;

        // go through all the attributes and process *-Digest-Manifest entries
        for (Map.Entry<Object,Object> se : mattr.entrySet()) {

            String key = se.getKey().toString();

            if (key.toUpperCase(Locale.ENGLISH).endsWith("-DIGEST-MANIFEST")) {
                // 16 is length of "-Digest-Manifest"
                String algorithm = key.substring(0, key.length()-16);
                validEntry = true;

                // Check if this algorithm is permitted, skip if false.
                if (!permittedCheck(key, algorithm)) {
                    continue;
                }

                // A non-weak algorithm was used, any weak algorithms found do
                // not need to be reported.
                weakAlgs = false;

                manifestDigests.add(key);
                manifestDigests.add(se.getValue());
                MessageDigest digest = getDigest(algorithm);
                if (digest != null) {
                    byte[] computedHash = md.manifestDigest(digest);
                    byte[] expectedHash =
                        Base64.getMimeDecoder().decode((String)se.getValue());

                    if (debug != null) {
                        debug.println("Signature File: Manifest digest " +
                                algorithm);
                        debug.println( "  sigfile  " + HexFormat.of().formatHex(expectedHash));
                        debug.println( "  computed " + HexFormat.of().formatHex(computedHash));
                        debug.println();
                    }

                    if (MessageDigest.isEqual(computedHash, expectedHash)) {
                        manifestSigned = true;
                    } else {
                        //XXX: we will continue and verify each section
                    }
                }
            }
        }

        if (debug != null) {
            debug.println("PermittedAlgs mapping: ");
            for (String key : permittedAlgs.keySet()) {
                debug.println(key + " : " +
                        permittedAlgs.get(key).toString());
            }
        }

        // If there were only weak algorithms entries used, throw an exception.
        if (validEntry && weakAlgs) {
            throw new SignatureException("Manifest hash check failed " +
                    "(DIGEST-MANIFEST). Disabled algorithm(s) used: " +
                    getWeakAlgorithms("-DIGEST-MANIFEST"));
        }
        return manifestSigned;
    }

    private boolean verifyManifestMainAttrs(Manifest sf, ManifestDigester md)
         throws SignatureException
    {
        Attributes mattr = sf.getMainAttributes();
        boolean attrsVerified = true;
        // If only weak algorithms are used.
        boolean weakAlgs = true;
        // If a ATTR_DIGEST entry is found.
        boolean validEntry = false;

        // go through all the attributes and process
        // digest entries for the manifest main attributes
        for (Map.Entry<Object,Object> se : mattr.entrySet()) {
            String key = se.getKey().toString();

            if (key.toUpperCase(Locale.ENGLISH).endsWith(ATTR_DIGEST)) {
                String algorithm =
                        key.substring(0, key.length() - ATTR_DIGEST.length());
                validEntry = true;

                // Check if this algorithm is permitted, skip if false.
                if (!permittedCheck(key, algorithm)) {
                    continue;
                }

                // A non-weak algorithm was used, any weak algorithms found do
                // not need to be reported.
                weakAlgs = false;

                MessageDigest digest = getDigest(algorithm);
                if (digest != null) {
                    ManifestDigester.Entry mde = md.getMainAttsEntry(false);
                    if (mde == null) {
                        throw new SignatureException("Manifest Main Attribute check " +
                                "failed due to missing main attributes entry");
                    }
                    byte[] computedHash = mde.digest(digest);
                    byte[] expectedHash =
                        Base64.getMimeDecoder().decode((String)se.getValue());

                    if (debug != null) {
                     debug.println("Signature File: " +
                                        "Manifest Main Attributes digest " +
                                        digest.getAlgorithm());
                     debug.println( "  sigfile  " + HexFormat.of().formatHex(expectedHash));
                     debug.println( "  computed " + HexFormat.of().formatHex(computedHash));
                     debug.println();
                    }

                    if (MessageDigest.isEqual(computedHash, expectedHash)) {
                        // good
                    } else {
                        // we will *not* continue and verify each section
                        attrsVerified = false;
                        if (debug != null) {
                            debug.println("Verification of " +
                                        "Manifest main attributes failed");
                            debug.println();
                        }
                        break;
                    }
                }
            }
        }

        if (debug != null) {
            debug.println("PermittedAlgs mapping: ");
            for (String key : permittedAlgs.keySet()) {
                debug.println(key + " : " +
                        permittedAlgs.get(key).toString());
            }
        }

        // If there were only weak algorithms entries used, throw an exception.
        if (validEntry && weakAlgs) {
            throw new SignatureException("Manifest Main Attribute check " +
                    "failed (" + ATTR_DIGEST + ").  " +
                    "Disabled algorithm(s) used: " +
                    getWeakAlgorithms(ATTR_DIGEST));
        }

        // this method returns 'true' if either:
        //      . manifest main attributes were not signed, or
        //      . manifest main attributes were signed and verified
        return attrsVerified;
    }

    /**
     * given the .SF digest header, and the data from the
     * section in the manifest, see if the hashes match.
     * if not, throw a SecurityException.
     *
     * @return true if all the -Digest headers verified
     * @exception SecurityException if the hash was not equal
     */

    private boolean verifySection(Attributes sfAttr,
                                  String name,
                                  ManifestDigester md)
         throws SignatureException
    {
        boolean oneDigestVerified = false;
        ManifestDigester.Entry mde = md.get(name,block.isOldStyle());
        // If only weak algorithms are used.
        boolean weakAlgs = true;
        // If a "*-DIGEST" entry is found.
        boolean validEntry = false;

        if (mde == null) {
            throw new SecurityException(
                  "no manifest section for signature file entry "+name);
        }

        if (sfAttr != null) {
            //sun.security.util.HexDumpEncoder hex = new sun.security.util.HexDumpEncoder();
            //hex.encodeBuffer(data, System.out);

            // go through all the attributes and process *-Digest entries
            for (Map.Entry<Object,Object> se : sfAttr.entrySet()) {
                String key = se.getKey().toString();

                if (key.toUpperCase(Locale.ENGLISH).endsWith("-DIGEST")) {
                    // 7 is length of "-Digest"
                    String algorithm = key.substring(0, key.length()-7);
                    validEntry = true;

                    // Check if this algorithm is permitted, skip if false.
                    if (!permittedCheck(key, algorithm)) {
                        continue;
                    }

                    // A non-weak algorithm was used, any weak algorithms found do
                    // not need to be reported.
                    weakAlgs = false;

                    MessageDigest digest = getDigest(algorithm);

                    if (digest != null) {
                        boolean ok = false;

                        byte[] expected =
                            Base64.getMimeDecoder().decode((String)se.getValue());
                        byte[] computed;
                        if (workaround) {
                            computed = mde.digestWorkaround(digest);
                        } else {
                            computed = mde.digest(digest);
                        }

                        if (debug != null) {
                          debug.println("Signature Block File: " +
                                   name + " digest=" + digest.getAlgorithm());
                          debug.println("  expected " + HexFormat.of().formatHex(expected));
                          debug.println("  computed " + HexFormat.of().formatHex(computed));
                          debug.println();
                        }

                        if (MessageDigest.isEqual(computed, expected)) {
                            oneDigestVerified = true;
                            ok = true;
                        } else {
                            // attempt to fallback to the workaround
                            if (!workaround) {
                               computed = mde.digestWorkaround(digest);
                               if (MessageDigest.isEqual(computed, expected)) {
                                   if (debug != null) {
                                       debug.println("  re-computed " + HexFormat.of().formatHex(computed));
                                       debug.println();
                                   }
                                   workaround = true;
                                   oneDigestVerified = true;
                                   ok = true;
                               }
                            }
                        }
                        if (!ok){
                            throw new SecurityException("invalid " +
                                       digest.getAlgorithm() +
                                       " signature file digest for " + name);
                        }
                    }
                }
            }
        }

        if (debug != null) {
            debug.println("PermittedAlgs mapping: ");
            for (String key : permittedAlgs.keySet()) {
                debug.println(key + " : " +
                        permittedAlgs.get(key).toString());
            }
        }

        // If there were only weak algorithms entries used, throw an exception.
        if (validEntry && weakAlgs) {
            throw new SignatureException("Manifest Main Attribute check " +
                    "failed (DIGEST).  Disabled algorithm(s) used: " +
                    getWeakAlgorithms("DIGEST"));
        }

        return oneDigestVerified;
    }

    /**
     * Given the PKCS7 block and SignerInfo[], create an array of
     * CodeSigner objects. We do this only *once* for a given
     * signature block file.
     */
    private CodeSigner[] getSigners(SignerInfo[] infos, PKCS7 block)
        throws IOException, NoSuchAlgorithmException, SignatureException,
            CertificateException {

        ArrayList<CodeSigner> signers = null;

        for (int i = 0; i < infos.length; i++) {

            SignerInfo info = infos[i];
            ArrayList<X509Certificate> chain = info.getCertificateChain(block);
            CertPath certChain = certificateFactory.generateCertPath(chain);
            if (signers == null) {
                signers = new ArrayList<>();
            }
            // Append the new code signer. If timestamp is invalid, this
            // jar will be treated as unsigned.
            signers.add(new CodeSigner(certChain, info.getTimestamp()));

            if (debug != null) {
                debug.println("Signature Block Certificate: " +
                    chain.get(0));
            }
        }

        if (signers != null) {
            return signers.toArray(new CodeSigner[0]);
        } else {
            return null;
        }
    }

    // returns true if set contains signer
    static boolean contains(CodeSigner[] set, CodeSigner signer)
    {
        for (int i = 0; i < set.length; i++) {
            if (set[i].equals(signer))
                return true;
        }
        return false;
    }

    // returns true if subset is a subset of set
    static boolean isSubSet(CodeSigner[] subset, CodeSigner[] set)
    {
        // check for the same object
        if (set == subset)
            return true;

        for (int i = 0; i < subset.length; i++) {
            if (!contains(set, subset[i]))
                return false;
        }
        return true;
    }

    /**
     * returns true if signer contains exactly the same code signers as
     * oldSigner and newSigner, false otherwise. oldSigner
     * is allowed to be null.
     */
    static boolean matches(CodeSigner[] signers, CodeSigner[] oldSigners,
        CodeSigner[] newSigners) {

        // special case
        if ((oldSigners == null) && (signers == newSigners))
            return true;

        // make sure all oldSigners are in signers
        if ((oldSigners != null) && !isSubSet(oldSigners, signers))
            return false;

        // make sure all newSigners are in signers
        if (!isSubSet(newSigners, signers)) {
            return false;
        }

        // now make sure all the code signers in signers are
        // also in oldSigners or newSigners

        for (int i = 0; i < signers.length; i++) {
            boolean found =
                ((oldSigners != null) && contains(oldSigners, signers[i])) ||
                contains(newSigners, signers[i]);
            if (!found)
                return false;
        }
        return true;
    }

    void updateSigners(CodeSigner[] newSigners,
        Hashtable<String, CodeSigner[]> signers, String name) {

        CodeSigner[] oldSigners = signers.get(name);

        // search through the cache for a match, go in reverse order
        // as we are more likely to find a match with the last one
        // added to the cache

        CodeSigner[] cachedSigners;
        for (int i = signerCache.size() - 1; i != -1; i--) {
            cachedSigners = signerCache.get(i);
            if (matches(cachedSigners, oldSigners, newSigners)) {
                signers.put(name, cachedSigners);
                return;
            }
        }

        if (oldSigners == null) {
            cachedSigners = newSigners;
        } else {
            cachedSigners =
                new CodeSigner[oldSigners.length + newSigners.length];
            System.arraycopy(oldSigners, 0, cachedSigners, 0,
                oldSigners.length);
            System.arraycopy(newSigners, 0, cachedSigners, oldSigners.length,
                newSigners.length);
        }
        signerCache.add(cachedSigners);
        signers.put(name, cachedSigners);
    }

    private static int initializeMaxSigFileSize() {
        /*
         * System property "jdk.jar.maxSignatureFileSize" used to configure
         * the maximum allowed number of bytes for the signature-related files
         * in a JAR file.
         */
        int tmp = GetIntegerAction.privilegedGetProperty(
                "jdk.jar.maxSignatureFileSize", 16000000);
        if (tmp < 0 || tmp > MAX_ARRAY_SIZE) {
            if (debug != null) {
                debug.println("The default signature file size of 16000000 bytes " +
                        "will be used for the jdk.jar.maxSignatureFileSize " +
                        "system property since the specified value " +
                        "is out of range: " + tmp);
            }
            tmp = 16000000;
        }
        return tmp;
    }
}
