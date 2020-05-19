/*
 * Copyright (c) 1996, 2020, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.x509;

import java.io.*;
import java.security.interfaces.RSAKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.EdDSAParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.*;
import java.security.interfaces.*;

import sun.security.rsa.PSSParameters;
import sun.security.util.*;


/**
 * This class identifies algorithms, such as cryptographic transforms, each
 * of which may be associated with parameters.  Instances of this base class
 * are used when this runtime environment has no special knowledge of the
 * algorithm type, and may also be used in other cases.  Equivalence is
 * defined according to OID and (where relevant) parameters.
 *
 * <P>Subclasses may be used, for example when the algorithm ID has
 * associated parameters which some code (e.g. code using public keys) needs
 * to have parsed.  Two examples of such algorithms are Diffie-Hellman key
 * exchange, and the Digital Signature Standard Algorithm (DSS/DSA).
 *
 * <P>The OID constants defined in this class correspond to some widely
 * used algorithms, for which conventional string names have been defined.
 * This class is not a general repository for OIDs, or for such string names.
 * Note that the mappings between algorithm IDs and algorithm names is
 * not one-to-one.
 *
 *
 * @author David Brownell
 * @author Amit Kapoor
 * @author Hemma Prafullchandra
 */
public class AlgorithmId implements Serializable, DerEncoder {

    /** use serialVersionUID from JDK 1.1. for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = 7205873507486557157L;

    /**
     * The object identitifer being used for this algorithm.
     */
    private ObjectIdentifier algid;

    // The (parsed) parameters
    @SuppressWarnings("serial") // Not statically typed as Serializable
    private AlgorithmParameters algParams;
    private boolean constructedFromDer = true;

    /**
     * Parameters for this algorithm.  These are stored in unparsed
     * DER-encoded form; subclasses can be made to automaticaly parse
     * them so there is fast access to these parameters.
     */
    @SuppressWarnings("serial") // Not statically typed as Serializable
    protected DerValue          params;


    /**
     * Constructs an algorithm ID which will be initialized
     * separately, for example by deserialization.
     * @deprecated use one of the other constructors.
     */
    @Deprecated
    public AlgorithmId() { }

    /**
     * Constructs a parameterless algorithm ID.
     *
     * @param oid the identifier for the algorithm
     */
    public AlgorithmId(ObjectIdentifier oid) {
        algid = oid;
    }

    /**
     * Constructs an algorithm ID with algorithm parameters.
     *
     * @param oid the identifier for the algorithm.
     * @param algparams the associated algorithm parameters.
     */
    public AlgorithmId(ObjectIdentifier oid, AlgorithmParameters algparams) {
        algid = oid;
        algParams = algparams;
        constructedFromDer = false;
    }

    private AlgorithmId(ObjectIdentifier oid, DerValue params)
            throws IOException {
        this.algid = oid;
        this.params = params;
        if (this.params != null) {
            decodeParams();
        }
    }

    protected void decodeParams() throws IOException {
        String algidName = getName();
        try {
            algParams = AlgorithmParameters.getInstance(algidName);
        } catch (NoSuchAlgorithmException e) {
            /*
             * This algorithm parameter type is not supported, so we cannot
             * parse the parameters.
             */
            algParams = null;
            return;
        }

        // Decode (parse) the parameters
        algParams.init(params.toByteArray());
    }

    /**
     * Marshal a DER-encoded "AlgorithmID" sequence on the DER stream.
     */
    public final void encode(DerOutputStream out) throws IOException {
        derEncode(out);
    }

    /**
     * DER encode this object onto an output stream.
     * Implements the <code>DerEncoder</code> interface.
     *
     * @param out
     * the output stream on which to write the DER encoding.
     *
     * @exception IOException on encoding error.
     */
    public void derEncode (OutputStream out) throws IOException {
        DerOutputStream bytes = new DerOutputStream();
        DerOutputStream tmp = new DerOutputStream();

        bytes.putOID(algid);
        // Setup params from algParams since no DER encoding is given
        if (constructedFromDer == false) {
            if (algParams != null) {
                params = new DerValue(algParams.getEncoded());
            } else {
                params = null;
            }
        }
        if (params == null) {
            // Changes backed out for compatibility with Solaris

            // Several AlgorithmId should omit the whole parameter part when
            // it's NULL. They are ---
            // RFC 3370 2.1: Implementations SHOULD generate SHA-1
            // AlgorithmIdentifiers with absent parameters.
            // RFC 3447 C1: When id-sha1, id-sha224, id-sha256, id-sha384 and
            // id-sha512 are used in an AlgorithmIdentifier the parameters
            // (which are optional) SHOULD be omitted.
            // RFC 3279 2.3.2: The id-dsa algorithm syntax includes optional
            // domain parameters... When omitted, the parameters component
            // MUST be omitted entirely
            // RFC 3370 3.1: When the id-dsa-with-sha1 algorithm identifier
            // is used, the AlgorithmIdentifier parameters field MUST be absent.
            /*if (
                algid.equals((Object)SHA_oid) ||
                algid.equals((Object)SHA224_oid) ||
                algid.equals((Object)SHA256_oid) ||
                algid.equals((Object)SHA384_oid) ||
                algid.equals((Object)SHA512_oid) ||
                algid.equals((Object)SHA512_224_oid) ||
                algid.equals((Object)SHA512_256_oid) ||
                algid.equals((Object)DSA_oid) ||
                algid.equals((Object)sha1WithDSA_oid)) {
                ; // no parameter part encoded
            } else {
                bytes.putNull();
            }*/
            if (algid.equals(RSASSA_PSS_oid) || algid.equals(ed448_oid)
                    || algid.equals(ed25519_oid)) {
                // RFC 4055 3.3: when an RSASSA-PSS key does not require
                // parameter validation, field is absent.
            } else {
                bytes.putNull();
            }
        } else {
            bytes.putDerValue(params);
        }
        tmp.write(DerValue.tag_Sequence, bytes);
        out.write(tmp.toByteArray());
    }


    /**
     * Returns the DER-encoded X.509 AlgorithmId as a byte array.
     */
    public final byte[] encode() throws IOException {
        DerOutputStream out = new DerOutputStream();
        derEncode(out);
        return out.toByteArray();
    }

    /**
     * Returns the ISO OID for this algorithm.  This is usually converted
     * to a string and used as part of an algorithm name, for example
     * "OID.1.3.14.3.2.13" style notation.  Use the <code>getName</code>
     * call when you do not need to ensure cross-system portability
     * of algorithm names, or need a user friendly name.
     */
    public final ObjectIdentifier getOID () {
        return algid;
    }

    /**
     * Returns a name for the algorithm which may be more intelligible
     * to humans than the algorithm's OID, but which won't necessarily
     * be comprehensible on other systems.  For example, this might
     * return a name such as "MD5withRSA" for a signature algorithm on
     * some systems.  It also returns names like "OID.1.2.3.4", when
     * no particular name for the algorithm is known.
     *
     * Note: for ecdsa-with-SHA2 plus hash algorithm (Ex: SHA-256), this method
     * returns the "full" signature algorithm (Ex: SHA256withECDSA) directly.
     */
    public String getName() {
        String oidStr = algid.toString();
        // first check the list of support oids
        KnownOIDs o = KnownOIDs.findMatch(oidStr);
        if (o == KnownOIDs.SpecifiedSHA2withECDSA) {
            if (params != null) {
                try {
                    AlgorithmId paramsId =
                        AlgorithmId.parse(new DerValue(params.toByteArray()));
                    String paramsName = paramsId.getName();
                    return makeSigAlg(paramsName, "EC");
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        if (o != null) {
            return o.stdName();
        } else {
            String n = aliasOidsTable().get(oidStr);
            if (n != null) {
                return n;
            } else {
                return algid.toString();
            }
        }
    }

    public AlgorithmParameters getParameters() {
        return algParams;
    }

    /**
     * Returns the DER encoded parameter, which can then be
     * used to initialize java.security.AlgorithmParameters.
     *
     * Note: for ecdsa-with-SHA2 plus hash algorithm (Ex: SHA-256), this method
     * returns null because {@link #getName()} has already returned the "full"
     * signature algorithm (Ex: SHA256withECDSA).
     *
     * @return DER encoded parameters, or null not present.
     */
    public byte[] getEncodedParams() throws IOException {
        return (params == null ||
            algid.toString().equals(KnownOIDs.SpecifiedSHA2withECDSA.value()))
                ? null
                : params.toByteArray();
    }

    /**
     * Returns true iff the argument indicates the same algorithm
     * with the same parameters.
     */
    public boolean equals(AlgorithmId other) {
        boolean paramsEqual = Objects.equals(other.params, params);
        return (algid.equals((Object)other.algid) && paramsEqual);
    }

    /**
     * Compares this AlgorithmID to another.  If algorithm parameters are
     * available, they are compared.  Otherwise, just the object IDs
     * for the algorithm are compared.
     *
     * @param other preferably an AlgorithmId, else an ObjectIdentifier
     */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof AlgorithmId) {
            return equals((AlgorithmId) other);
        } else if (other instanceof ObjectIdentifier) {
            return equals((ObjectIdentifier) other);
        } else {
            return false;
        }
    }

    /**
     * Compares two algorithm IDs for equality.  Returns true iff
     * they are the same algorithm, ignoring algorithm parameters.
     */
    public final boolean equals(ObjectIdentifier id) {
        return algid.equals((Object)id);
    }

    /**
     * Returns a hashcode for this AlgorithmId.
     *
     * @return a hashcode for this AlgorithmId.
     */
    public int hashCode() {
        StringBuilder sbuf = new StringBuilder();
        sbuf.append(algid.toString());
        sbuf.append(paramsToString());
        return sbuf.toString().hashCode();
    }

    /**
     * Provides a human-readable description of the algorithm parameters.
     * This may be redefined by subclasses which parse those parameters.
     */
    protected String paramsToString() {
        if (params == null) {
            return "";
        } else if (algParams != null) {
            return algParams.toString();
        } else {
            return ", params unparsed";
        }
    }

    /**
     * Returns a string describing the algorithm and its parameters.
     */
    public String toString() {
        return getName() + paramsToString();
    }

    /**
     * Parse (unmarshal) an ID from a DER sequence input value.  This form
     * parsing might be used when expanding a value which has already been
     * partially unmarshaled as a set or sequence member.
     *
     * @exception IOException on error.
     * @param val the input value, which contains the algid and, if
     *          there are any parameters, those parameters.
     * @return an ID for the algorithm.  If the system is configured
     *          appropriately, this may be an instance of a class
     *          with some kind of special support for this algorithm.
     *          In that case, you may "narrow" the type of the ID.
     */
    public static AlgorithmId parse(DerValue val) throws IOException {
        if (val.tag != DerValue.tag_Sequence) {
            throw new IOException("algid parse error, not a sequence");
        }

        /*
         * Get the algorithm ID and any parameters.
         */
        ObjectIdentifier        algid;
        DerValue                params;
        DerInputStream          in = val.toDerInputStream();

        algid = in.getOID();
        if (in.available() == 0) {
            params = null;
        } else {
            params = in.getDerValue();
            if (params.tag == DerValue.tag_Null) {
                if (params.length() != 0) {
                    throw new IOException("invalid NULL");
                }
                params = null;
            }
            if (in.available() != 0) {
                throw new IOException("Invalid AlgorithmIdentifier: extra data");
            }
        }

        return new AlgorithmId(algid, params);
    }

    /**
     * Returns one of the algorithm IDs most commonly associated
     * with this algorithm name.
     *
     * @param algname the name being used
     * @deprecated use the short get form of this method.
     * @exception NoSuchAlgorithmException on error.
     */
    @Deprecated
    public static AlgorithmId getAlgorithmId(String algname)
            throws NoSuchAlgorithmException {
        return get(algname);
    }

    /**
     * Returns one of the algorithm IDs most commonly associated
     * with this algorithm name.
     *
     * @param algname the name being used
     * @exception NoSuchAlgorithmException on error.
     */
    public static AlgorithmId get(String algname)
            throws NoSuchAlgorithmException {
        ObjectIdentifier oid;
        try {
            oid = algOID(algname);
        } catch (IOException ioe) {
            throw new NoSuchAlgorithmException
                ("Invalid ObjectIdentifier " + algname);
        }

        if (oid == null) {
            throw new NoSuchAlgorithmException
                ("unrecognized algorithm name: " + algname);
        }
        return new AlgorithmId(oid);
    }

    /**
     * Returns one of the algorithm IDs most commonly associated
     * with this algorithm parameters.
     *
     * @param algparams the associated algorithm parameters.
     * @exception NoSuchAlgorithmException on error.
     */
    public static AlgorithmId get(AlgorithmParameters algparams)
            throws NoSuchAlgorithmException {
        ObjectIdentifier oid;
        String algname = algparams.getAlgorithm();
        try {
            oid = algOID(algname);
        } catch (IOException ioe) {
            throw new NoSuchAlgorithmException
                ("Invalid ObjectIdentifier " + algname);
        }
        if (oid == null) {
            throw new NoSuchAlgorithmException
                ("unrecognized algorithm name: " + algname);
        }
        return new AlgorithmId(oid, algparams);
    }

    /*
     * Translates from some common algorithm names to the
     * OID with which they're usually associated ... this mapping
     * is the reverse of the one below, except in those cases
     * where synonyms are supported or where a given algorithm
     * is commonly associated with multiple OIDs.
     *
     * XXX This method needs to be enhanced so that we can also pass the
     * scope of the algorithm name to it, e.g., the algorithm name "DSA"
     * may have a different OID when used as a "Signature" algorithm than when
     * used as a "KeyPairGenerator" algorithm.
     */
    private static ObjectIdentifier algOID(String name) throws IOException {
        if (name.startsWith("OID.")) {
            name = name.substring("OID.".length());
        }

        KnownOIDs k = KnownOIDs.findMatch(name);
        if (k != null) {
            return ObjectIdentifier.of(k);
        }

        // unknown algorithm oids
        if (name.indexOf(".") == -1) {
            // see if there is a matching oid string alias mapping from
            // 3rd party providers
            name = name.toUpperCase(Locale.ENGLISH);
            String oidStr = aliasOidsTable().get(name);
            if (oidStr != null) {
                return ObjectIdentifier.of(oidStr);
            } return null;
        } else {
            return ObjectIdentifier.of(name);
        }
    }

    // oid string cache index'ed by algorithm name and oid strings
    private static volatile Map<String,String> aliasOidsTable;

    // returns the aliasOidsTable, lazily initializing it on first access.
    private static Map<String,String> aliasOidsTable() {
        // Double checked locking; safe because aliasOidsTable is volatile
        Map<String,String> tab = aliasOidsTable;
        if (tab == null) {
            synchronized (AlgorithmId.class) {
                if ((tab = aliasOidsTable) == null) {
                    aliasOidsTable = tab = collectOIDAliases();
                }
            }
        }
        return tab;
    }

    private static boolean isKnownProvider(Provider p) {
        String pn = p.getName();
        String mn = p.getClass().getModule().getName();
        if (pn != null && mn != null) {
            return ((mn.equals("java.base") &&
                    (pn.equals("SUN") || pn.equals("SunRsaSign") ||
                    pn.equals("SunJCE") || pn.equals("SunJSSE"))) ||
                (mn.equals("jdk.crypto.ec") && pn.equals("SunEC")) ||
                (mn.equals("jdk.crypto.mscapi") && pn.equals("SunMSCAPI")) ||
                (mn.equals("jdk.crypto.cryptoki") &&
                    pn.startsWith("SunPKCS11")));
        } else {
            return false;
        }
    }

    private static ConcurrentHashMap<String, String> collectOIDAliases() {
        ConcurrentHashMap<String, String> t = new ConcurrentHashMap<>();
        for (Provider provider : Security.getProviders()) {
            // skip providers which are already using SecurityProviderConstants
            // and KnownOIDs
            if (isKnownProvider(provider)) {
                continue;
            }
            for (Object key : provider.keySet()) {
                String alias = (String)key;
                String upperCaseAlias = alias.toUpperCase(Locale.ENGLISH);
                int index;
                if (upperCaseAlias.startsWith("ALG.ALIAS") &&
                    (index = upperCaseAlias.indexOf("OID.", 0)) != -1) {
                    index += "OID.".length();
                    if (index == alias.length()) {
                        // invalid alias entry
                        break;
                    }
                    String ostr = alias.substring(index);
                    String stdAlgName = provider.getProperty(alias);
                    if (stdAlgName != null) {
                        stdAlgName = stdAlgName.toUpperCase(Locale.ENGLISH);
                    }
                    // add the name->oid and oid->name mappings if none exists
                    if (KnownOIDs.findMatch(stdAlgName) == null) {
                        // not override earlier entries if it exists
                        t.putIfAbsent(stdAlgName, ostr);
                    }
                    if (KnownOIDs.findMatch(ostr) == null) {
                        // not override earlier entries if it exists
                        t.putIfAbsent(ostr, stdAlgName);
                    }
                }
            }
        }
        return t;
    }

    public static final ObjectIdentifier MD2_oid =
            ObjectIdentifier.of(KnownOIDs.MD2);

    public static final ObjectIdentifier MD5_oid =
            ObjectIdentifier.of(KnownOIDs.MD5);

    public static final ObjectIdentifier SHA_oid =
            ObjectIdentifier.of(KnownOIDs.SHA_1);

    public static final ObjectIdentifier SHA224_oid =
            ObjectIdentifier.of(KnownOIDs.SHA_224);

    public static final ObjectIdentifier SHA256_oid =
            ObjectIdentifier.of(KnownOIDs.SHA_256);

    public static final ObjectIdentifier SHA384_oid =
            ObjectIdentifier.of(KnownOIDs.SHA_384);

    public static final ObjectIdentifier SHA512_oid =
            ObjectIdentifier.of(KnownOIDs.SHA_512);

    public static final ObjectIdentifier SHA512_224_oid =
            ObjectIdentifier.of(KnownOIDs.SHA_512$224);

    public static final ObjectIdentifier SHA512_256_oid =
            ObjectIdentifier.of(KnownOIDs.SHA_512$256);

    public static final ObjectIdentifier DSA_oid =
            ObjectIdentifier.of(KnownOIDs.DSA);

    public static final ObjectIdentifier EC_oid =
            ObjectIdentifier.of(KnownOIDs.EC);

    public static final ObjectIdentifier RSAEncryption_oid =
            ObjectIdentifier.of(KnownOIDs.RSA);

    public static final ObjectIdentifier RSASSA_PSS_oid =
            ObjectIdentifier.of(KnownOIDs.RSASSA_PSS);

    public static final ObjectIdentifier MGF1_oid =
            ObjectIdentifier.of(KnownOIDs.MGF1);

    public static final ObjectIdentifier ed25519_oid =
            ObjectIdentifier.of(KnownOIDs.Ed25519);
    public static final ObjectIdentifier ed448_oid =
            ObjectIdentifier.of(KnownOIDs.Ed448);

    /**
     * Creates a signature algorithm name from a digest algorithm
     * name and a encryption algorithm name.
     */
    public static String makeSigAlg(String digAlg, String encAlg) {
        digAlg = digAlg.replace("-", "");
        if (encAlg.equalsIgnoreCase("EC")) encAlg = "ECDSA";

        return digAlg + "with" + encAlg;
    }

    /**
     * Extracts the encryption algorithm name from a signature
     * algorithm name.
     */
    public static String getEncAlgFromSigAlg(String signatureAlgorithm) {
        signatureAlgorithm = signatureAlgorithm.toUpperCase(Locale.ENGLISH);
        int with = signatureAlgorithm.indexOf("WITH");
        String keyAlgorithm = null;
        if (with > 0) {
            int and = signatureAlgorithm.indexOf("AND", with + 4);
            if (and > 0) {
                keyAlgorithm = signatureAlgorithm.substring(with + 4, and);
            } else {
                keyAlgorithm = signatureAlgorithm.substring(with + 4);
            }
            if (keyAlgorithm.equalsIgnoreCase("ECDSA")) {
                keyAlgorithm = "EC";
            }
        }
        return keyAlgorithm;
    }

    /**
     * Extracts the digest algorithm name from a signature
     * algorithm name.
     */
    public static String getDigAlgFromSigAlg(String signatureAlgorithm) {
        signatureAlgorithm = signatureAlgorithm.toUpperCase(Locale.ENGLISH);
        int with = signatureAlgorithm.indexOf("WITH");
        if (with > 0) {
            return signatureAlgorithm.substring(0, with);
        }
        return null;
    }

    /**
     * Checks if a signature algorithm matches a key algorithm, i.e. a
     * signature can be initialized with a key.
     *
     * @param kAlg must not be null
     * @param sAlg must not be null
     * @throws IllegalArgumentException if they do not match
     */
    public static void checkKeyAndSigAlgMatch(String kAlg, String sAlg) {
        String sAlgUp = sAlg.toUpperCase(Locale.US);
        if ((sAlgUp.endsWith("WITHRSA") && !kAlg.equalsIgnoreCase("RSA")) ||
                (sAlgUp.endsWith("WITHECDSA") && !kAlg.equalsIgnoreCase("EC")) ||
                (sAlgUp.endsWith("WITHDSA") && !kAlg.equalsIgnoreCase("DSA"))) {
            throw new IllegalArgumentException(
                    "key algorithm not compatible with signature algorithm");
        }
    }

    /**
     * Returns the default signature algorithm for a private key. The digest
     * part might evolve with time. Remember to update the spec of
     * {@link jdk.security.jarsigner.JarSigner.Builder#getDefaultSignatureAlgorithm(PrivateKey)}
     * if updated.
     *
     * @param k cannot be null
     * @return the default alg, might be null if unsupported
     */
    public static String getDefaultSigAlgForKey(PrivateKey k) {
        switch (k.getAlgorithm().toUpperCase(Locale.ENGLISH)) {
            case "EC":
                return ecStrength(KeyUtil.getKeySize(k))
                    + "withECDSA";
            case "DSA":
                return ifcFfcStrength(KeyUtil.getKeySize(k))
                    + "withDSA";
            case "RSA":
                return ifcFfcStrength(KeyUtil.getKeySize(k))
                    + "withRSA";
            case "RSASSA-PSS":
                return "RSASSA-PSS";
            case "EDDSA":
                return edAlgFromKey(k);
            default:
                return null;
        }
    }

    // Most commonly used PSSParameterSpec and AlgorithmId
    private static class PSSParamsHolder {

        final static PSSParameterSpec PSS_256_SPEC = new PSSParameterSpec(
                "SHA-256", "MGF1",
                new MGF1ParameterSpec("SHA-256"),
                32, PSSParameterSpec.TRAILER_FIELD_BC);
        final static PSSParameterSpec PSS_384_SPEC = new PSSParameterSpec(
                "SHA-384", "MGF1",
                new MGF1ParameterSpec("SHA-384"),
                48, PSSParameterSpec.TRAILER_FIELD_BC);
        final static PSSParameterSpec PSS_512_SPEC = new PSSParameterSpec(
                "SHA-512", "MGF1",
                new MGF1ParameterSpec("SHA-512"),
                64, PSSParameterSpec.TRAILER_FIELD_BC);

        final static AlgorithmId PSS_256_ID;
        final static AlgorithmId PSS_384_ID;
        final static AlgorithmId PSS_512_ID;

        static {
            try {
                PSS_256_ID = new AlgorithmId(RSASSA_PSS_oid,
                        new DerValue(PSSParameters.getEncoded(PSS_256_SPEC)));
                PSS_384_ID = new AlgorithmId(RSASSA_PSS_oid,
                        new DerValue(PSSParameters.getEncoded(PSS_384_SPEC)));
                PSS_512_ID = new AlgorithmId(RSASSA_PSS_oid,
                        new DerValue(PSSParameters.getEncoded(PSS_512_SPEC)));
            } catch (IOException e) {
                throw new AssertionError("Should not happen", e);
            }
        }
    }

    public static AlgorithmId getWithParameterSpec(String algName,
            AlgorithmParameterSpec spec) throws NoSuchAlgorithmException {

        if (spec == null) {
            return AlgorithmId.get(algName);
        } else if (spec == PSSParamsHolder.PSS_256_SPEC) {
            return PSSParamsHolder.PSS_256_ID;
        } else if (spec == PSSParamsHolder.PSS_384_SPEC) {
            return PSSParamsHolder.PSS_384_ID;
        } else if (spec == PSSParamsHolder.PSS_512_SPEC) {
            return PSSParamsHolder.PSS_512_ID;
        } else if (spec instanceof EdDSAParameterSpec) {
            return AlgorithmId.get(algName);
        } else {
            try {
                AlgorithmParameters result =
                        AlgorithmParameters.getInstance(algName);
                result.init(spec);
                return get(result);
            } catch (InvalidParameterSpecException | NoSuchAlgorithmException e) {
                throw new ProviderException(e);
            }
        }
    }

    public static AlgorithmParameterSpec getDefaultAlgorithmParameterSpec(
            String sigAlg, PrivateKey k) {
        if (sigAlg.equalsIgnoreCase("RSASSA-PSS")) {
            if (k instanceof RSAKey) {
                AlgorithmParameterSpec spec = ((RSAKey) k).getParams();
                if (spec instanceof PSSParameterSpec) {
                    return spec;
                }
            }
            switch (ifcFfcStrength(KeyUtil.getKeySize(k))) {
                case "SHA256":
                    return PSSParamsHolder.PSS_256_SPEC;
                case "SHA384":
                    return PSSParamsHolder.PSS_384_SPEC;
                case "SHA512":
                    return PSSParamsHolder.PSS_512_SPEC;
                default:
                    throw new AssertionError("Should not happen");
            }
        } else {
            return null;
        }
    }

    private static String edAlgFromKey(PrivateKey k) {
        if (k instanceof EdECPrivateKey) {
            EdECPrivateKey edKey = (EdECPrivateKey) k;
            return edKey.getParams().getName();
        }
        return "EdDSA";
    }

    // Values from SP800-57 part 1 rev 4 tables 2 and 3
    private static String ecStrength (int bitLength) {
        if (bitLength >= 512) { // 256 bits of strength
            return "SHA512";
        } else if (bitLength >= 384) {  // 192 bits of strength
            return "SHA384";
        } else { // 128 bits of strength and less
            return "SHA256";
        }
    }

    // Same values for RSA and DSA
    private static String ifcFfcStrength (int bitLength) {
        if (bitLength > 7680) { // 256 bits
            return "SHA512";
        } else if (bitLength > 3072) {  // 192 bits
            return "SHA384";
        } else  { // 128 bits and less
            return "SHA256";
        }
    }
}
