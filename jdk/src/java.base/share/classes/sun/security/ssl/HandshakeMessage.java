/*
 * Copyright (c) 1996, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.lang.reflect.*;

import javax.security.auth.x500.X500Principal;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.DHPublicKeySpec;

import javax.net.ssl.*;

import sun.security.internal.spec.TlsPrfParameterSpec;
import sun.security.ssl.CipherSuite.*;
import static sun.security.ssl.CipherSuite.PRF.*;
import sun.security.util.KeyUtil;

/**
 * Many data structures are involved in the handshake messages.  These
 * classes are used as structures, with public data members.  They are
 * not visible outside the SSL package.
 *
 * Handshake messages all have a common header format, and they are all
 * encoded in a "handshake data" SSL record substream.  The base class
 * here (HandshakeMessage) provides a common framework and records the
 * SSL record type of the particular handshake message.
 *
 * This file contains subclasses for all the basic handshake messages.
 * All handshake messages know how to encode and decode themselves on
 * SSL streams; this facilitates using the same code on SSL client and
 * server sides, although they don't send and receive the same messages.
 *
 * Messages also know how to print themselves, which is quite handy
 * for debugging.  They always identify their type, and can optionally
 * dump all of their content.
 *
 * @author David Brownell
 */
public abstract class HandshakeMessage {

    HandshakeMessage() { }

    // enum HandshakeType:
    static final byte   ht_hello_request = 0;
    static final byte   ht_client_hello = 1;
    static final byte   ht_server_hello = 2;

    static final byte   ht_certificate = 11;
    static final byte   ht_server_key_exchange = 12;
    static final byte   ht_certificate_request = 13;
    static final byte   ht_server_hello_done = 14;
    static final byte   ht_certificate_verify = 15;
    static final byte   ht_client_key_exchange = 16;

    static final byte   ht_finished = 20;

    /* Class and subclass dynamic debugging support */
    public static final Debug debug = Debug.getInstance("ssl");

    /**
     * Utility method to convert a BigInteger to a byte array in unsigned
     * format as needed in the handshake messages. BigInteger uses
     * 2's complement format, i.e. it prepends an extra zero if the MSB
     * is set. We remove that.
     */
    static byte[] toByteArray(BigInteger bi) {
        byte[] b = bi.toByteArray();
        if ((b.length > 1) && (b[0] == 0)) {
            int n = b.length - 1;
            byte[] newarray = new byte[n];
            System.arraycopy(b, 1, newarray, 0, n);
            b = newarray;
        }
        return b;
    }

    /*
     * SSL 3.0 MAC padding constants.
     * Also used by CertificateVerify and Finished during the handshake.
     */
    static final byte[] MD5_pad1 = genPad(0x36, 48);
    static final byte[] MD5_pad2 = genPad(0x5c, 48);

    static final byte[] SHA_pad1 = genPad(0x36, 40);
    static final byte[] SHA_pad2 = genPad(0x5c, 40);

    private static byte[] genPad(int b, int count) {
        byte[] padding = new byte[count];
        Arrays.fill(padding, (byte)b);
        return padding;
    }

    /*
     * Write a handshake message on the (handshake) output stream.
     * This is just a four byte header followed by the data.
     *
     * NOTE that huge messages -- notably, ones with huge cert
     * chains -- are handled correctly.
     */
    final void write(HandshakeOutStream s) throws IOException {
        int len = messageLength();
        if (len >= Record.OVERFLOW_OF_INT24) {
            throw new SSLException("Handshake message too big"
                + ", type = " + messageType() + ", len = " + len);
        }
        s.write(messageType());
        s.putInt24(len);
        send(s);
    }

    /*
     * Subclasses implement these methods so those kinds of
     * messages can be emitted.  Base class delegates to subclass.
     */
    abstract int  messageType();
    abstract int  messageLength();
    abstract void send(HandshakeOutStream s) throws IOException;

    /*
     * Write a descriptive message on the output stream; for debugging.
     */
    abstract void print(PrintStream p) throws IOException;

//
// NOTE:  the rest of these classes are nested within this one, and are
// imported by other classes in this package.  There are a few other
// handshake message classes, not neatly nested here because of current
// licensing requirement for native (RSA) methods.  They belong here,
// but those native methods complicate things a lot!
//


/*
 * HelloRequest ... SERVER --> CLIENT
 *
 * Server can ask the client to initiate a new handshake, e.g. to change
 * session parameters after a connection has been (re)established.
 */
static final class HelloRequest extends HandshakeMessage {
    @Override
    int messageType() { return ht_hello_request; }

    HelloRequest() { }

    HelloRequest(HandshakeInStream in) throws IOException
    {
        // nothing in this message
    }

    @Override
    int messageLength() { return 0; }

    @Override
    void send(HandshakeOutStream out) throws IOException
    {
        // nothing in this messaage
    }

    @Override
    void print(PrintStream out) throws IOException
    {
        out.println("*** HelloRequest (empty)");
    }

}


/*
 * ClientHello ... CLIENT --> SERVER
 *
 * Client initiates handshake by telling server what it wants, and what it
 * can support (prioritized by what's first in the ciphe suite list).
 *
 * By RFC2246:7.4.1.2 it's explicitly anticipated that this message
 * will have more data added at the end ... e.g. what CAs the client trusts.
 * Until we know how to parse it, we will just read what we know
 * about, and let our caller handle the jumps over unknown data.
 */
static final class ClientHello extends HandshakeMessage {

    ProtocolVersion     protocolVersion;
    RandomCookie        clnt_random;
    SessionId           sessionId;
    private CipherSuiteList    cipherSuites;
    byte[]              compression_methods;

    HelloExtensions extensions = new HelloExtensions();

    private final static byte[]  NULL_COMPRESSION = new byte[] {0};

    ClientHello(SecureRandom generator, ProtocolVersion protocolVersion,
            SessionId sessionId, CipherSuiteList cipherSuites) {

        this.protocolVersion = protocolVersion;
        this.sessionId = sessionId;
        this.cipherSuites = cipherSuites;

        if (cipherSuites.containsEC()) {
            extensions.add(SupportedEllipticCurvesExtension.DEFAULT);
            extensions.add(SupportedEllipticPointFormatsExtension.DEFAULT);
        }

        clnt_random = new RandomCookie(generator);
        compression_methods = NULL_COMPRESSION;
    }

    ClientHello(HandshakeInStream s, int messageLength) throws IOException {
        protocolVersion = ProtocolVersion.valueOf(s.getInt8(), s.getInt8());
        clnt_random = new RandomCookie(s);
        sessionId = new SessionId(s.getBytes8());
        cipherSuites = new CipherSuiteList(s);
        compression_methods = s.getBytes8();
        if (messageLength() != messageLength) {
            extensions = new HelloExtensions(s);
        }
    }

    CipherSuiteList getCipherSuites() {
        return cipherSuites;
    }

    // add renegotiation_info extension
    void addRenegotiationInfoExtension(byte[] clientVerifyData) {
        HelloExtension renegotiationInfo = new RenegotiationInfoExtension(
                    clientVerifyData, new byte[0]);
        extensions.add(renegotiationInfo);
    }

    // add server_name extension
    void addSNIExtension(List<SNIServerName> serverNames) {
        try {
            extensions.add(new ServerNameExtension(serverNames));
        } catch (IOException ioe) {
            // ignore the exception and return
        }
    }

    // add signature_algorithm extension
    void addSignatureAlgorithmsExtension(
            Collection<SignatureAndHashAlgorithm> algorithms) {
        HelloExtension signatureAlgorithm =
                new SignatureAlgorithmsExtension(algorithms);
        extensions.add(signatureAlgorithm);
    }

    @Override
    int messageType() { return ht_client_hello; }

    @Override
    int messageLength() {
        /*
         * Add fixed size parts of each field...
         * version + random + session + cipher + compress
         */
        return (2 + 32 + 1 + 2 + 1
            + sessionId.length()                /* ... + variable parts */
            + (cipherSuites.size() * 2)
            + compression_methods.length)
            + extensions.length();
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt8(protocolVersion.major);
        s.putInt8(protocolVersion.minor);
        clnt_random.send(s);
        s.putBytes8(sessionId.getId());
        cipherSuites.send(s);
        s.putBytes8(compression_methods);
        extensions.send(s);
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** ClientHello, " + protocolVersion);

        if (debug != null && Debug.isOn("verbose")) {
            s.print("RandomCookie:  ");
            clnt_random.print(s);

            s.print("Session ID:  ");
            s.println(sessionId);

            s.println("Cipher Suites: " + cipherSuites);

            Debug.println(s, "Compression Methods", compression_methods);
            extensions.print(s);
            s.println("***");
        }
    }
}

/*
 * ServerHello ... SERVER --> CLIENT
 *
 * Server chooses protocol options from among those it supports and the
 * client supports.  Then it sends the basic session descriptive parameters
 * back to the client.
 */
static final
class ServerHello extends HandshakeMessage
{
    @Override
    int messageType() { return ht_server_hello; }

    ProtocolVersion     protocolVersion;
    RandomCookie        svr_random;
    SessionId           sessionId;
    CipherSuite         cipherSuite;
    byte                compression_method;
    HelloExtensions extensions = new HelloExtensions();

    ServerHello() {
        // empty
    }

    ServerHello(HandshakeInStream input, int messageLength)
            throws IOException {
        protocolVersion = ProtocolVersion.valueOf(input.getInt8(),
                                                  input.getInt8());
        svr_random = new RandomCookie(input);
        sessionId = new SessionId(input.getBytes8());
        cipherSuite = CipherSuite.valueOf(input.getInt8(), input.getInt8());
        compression_method = (byte)input.getInt8();
        if (messageLength() != messageLength) {
            extensions = new HelloExtensions(input);
        }
    }

    @Override
    int messageLength()
    {
        // almost fixed size, except session ID and extensions:
        //      major + minor = 2
        //      random = 32
        //      session ID len field = 1
        //      cipher suite + compression = 3
        //      extensions: if present, 2 + length of extensions
        return 38 + sessionId.length() + extensions.length();
    }

    @Override
    void send(HandshakeOutStream s) throws IOException
    {
        s.putInt8(protocolVersion.major);
        s.putInt8(protocolVersion.minor);
        svr_random.send(s);
        s.putBytes8(sessionId.getId());
        s.putInt8(cipherSuite.id >> 8);
        s.putInt8(cipherSuite.id & 0xff);
        s.putInt8(compression_method);
        extensions.send(s);
    }

    @Override
    void print(PrintStream s) throws IOException
    {
        s.println("*** ServerHello, " + protocolVersion);

        if (debug != null && Debug.isOn("verbose")) {
            s.print("RandomCookie:  ");
            svr_random.print(s);

            s.print("Session ID:  ");
            s.println(sessionId);

            s.println("Cipher Suite: " + cipherSuite);
            s.println("Compression Method: " + compression_method);
            extensions.print(s);
            s.println("***");
        }
    }
}


/*
 * CertificateMsg ... send by both CLIENT and SERVER
 *
 * Each end of a connection may need to pass its certificate chain to
 * the other end.  Such chains are intended to validate an identity with
 * reference to some certifying authority.  Examples include companies
 * like Verisign, or financial institutions.  There's some control over
 * the certifying authorities which are sent.
 *
 * NOTE: that these messages might be huge, taking many handshake records.
 * Up to 2^48 bytes of certificate may be sent, in records of at most 2^14
 * bytes each ... up to 2^32 records sent on the output stream.
 */
static final
class CertificateMsg extends HandshakeMessage
{
    @Override
    int messageType() { return ht_certificate; }

    private X509Certificate[] chain;

    private List<byte[]> encodedChain;

    private int messageLength;

    CertificateMsg(X509Certificate[] certs) {
        chain = certs;
    }

    CertificateMsg(HandshakeInStream input) throws IOException {
        int chainLen = input.getInt24();
        List<Certificate> v = new ArrayList<>(4);

        CertificateFactory cf = null;
        while (chainLen > 0) {
            byte[] cert = input.getBytes24();
            chainLen -= (3 + cert.length);
            try {
                if (cf == null) {
                    cf = CertificateFactory.getInstance("X.509");
                }
                v.add(cf.generateCertificate(new ByteArrayInputStream(cert)));
            } catch (CertificateException e) {
                throw (SSLProtocolException)new SSLProtocolException(
                    e.getMessage()).initCause(e);
            }
        }

        chain = v.toArray(new X509Certificate[v.size()]);
    }

    @Override
    int messageLength() {
        if (encodedChain == null) {
            messageLength = 3;
            encodedChain = new ArrayList<byte[]>(chain.length);
            try {
                for (X509Certificate cert : chain) {
                    byte[] b = cert.getEncoded();
                    encodedChain.add(b);
                    messageLength += b.length + 3;
                }
            } catch (CertificateEncodingException e) {
                encodedChain = null;
                throw new RuntimeException("Could not encode certificates", e);
            }
        }
        return messageLength;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt24(messageLength() - 3);
        for (byte[] b : encodedChain) {
            s.putBytes24(b);
        }
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** Certificate chain");

        if (debug != null && Debug.isOn("verbose")) {
            for (int i = 0; i < chain.length; i++)
                s.println("chain [" + i + "] = " + chain[i]);
            s.println("***");
        }
    }

    X509Certificate[] getCertificateChain() {
        return chain.clone();
    }
}

/*
 * ServerKeyExchange ... SERVER --> CLIENT
 *
 * The cipher suite selected, when combined with the certificate exchanged,
 * implies one of several different kinds of key exchange.  Most current
 * cipher suites require the server to send more than its certificate.
 *
 * The primary exceptions are when a server sends an encryption-capable
 * RSA public key in its cert, to be used with RSA (or RSA_export) key
 * exchange; and when a server sends its Diffie-Hellman cert.  Those kinds
 * of key exchange do not require a ServerKeyExchange message.
 *
 * Key exchange can be viewed as having three modes, which are explicit
 * for the Diffie-Hellman flavors and poorly specified for RSA ones:
 *
 *      - "Ephemeral" keys.  Here, a "temporary" key is allocated by the
 *        server, and signed.  Diffie-Hellman keys signed using RSA or
 *        DSS are ephemeral (DHE flavor).  RSA keys get used to do the same
 *        thing, to cut the key size down to 512 bits (export restrictions)
 *        or for signing-only RSA certificates.
 *
 *      - Anonymity.  Here no server certificate is sent, only the public
 *        key of the server.  This case is subject to man-in-the-middle
 *        attacks.  This can be done with Diffie-Hellman keys (DH_anon) or
 *        with RSA keys, but is only used in SSLv3 for DH_anon.
 *
 *      - "Normal" case.  Here a server certificate is sent, and the public
 *        key there is used directly in exchanging the premaster secret.
 *        For example, Diffie-Hellman "DH" flavor, and any RSA flavor with
 *        only 512 bit keys.
 *
 * If a server certificate is sent, there is no anonymity.  However,
 * when a certificate is sent, ephemeral keys may still be used to
 * exchange the premaster secret.  That's how RSA_EXPORT often works,
 * as well as how the DHE_* flavors work.
 */
static abstract class ServerKeyExchange extends HandshakeMessage
{
    @Override
    int messageType() { return ht_server_key_exchange; }
}


/*
 * Using RSA for Key Exchange:  exchange a session key that's not as big
 * as the signing-only key.  Used for export applications, since exported
 * RSA encryption keys can't be bigger than 512 bytes.
 *
 * This is never used when keys are 512 bits or smaller, and isn't used
 * on "US Domestic" ciphers in any case.
 */
static final
class RSA_ServerKeyExchange extends ServerKeyExchange
{
    private byte rsa_modulus[];     // 1 to 2^16 - 1 bytes
    private byte rsa_exponent[];    // 1 to 2^16 - 1 bytes

    private Signature signature;
    private byte[] signatureBytes;

    /*
     * Hash the nonces and the ephemeral RSA public key.
     */
    private void updateSignature(byte clntNonce[], byte svrNonce[])
            throws SignatureException {
        int tmp;

        signature.update(clntNonce);
        signature.update(svrNonce);

        tmp = rsa_modulus.length;
        signature.update((byte)(tmp >> 8));
        signature.update((byte)(tmp & 0x0ff));
        signature.update(rsa_modulus);

        tmp = rsa_exponent.length;
        signature.update((byte)(tmp >> 8));
        signature.update((byte)(tmp & 0x0ff));
        signature.update(rsa_exponent);
    }


    /*
     * Construct an RSA server key exchange message, using data
     * known _only_ to the server.
     *
     * The client knows the public key corresponding to this private
     * key, from the Certificate message sent previously.  To comply
     * with US export regulations we use short RSA keys ... either
     * long term ones in the server's X509 cert, or else ephemeral
     * ones sent using this message.
     */
    RSA_ServerKeyExchange(PublicKey ephemeralKey, PrivateKey privateKey,
            RandomCookie clntNonce, RandomCookie svrNonce, SecureRandom sr)
            throws GeneralSecurityException {
        RSAPublicKeySpec rsaKey = JsseJce.getRSAPublicKeySpec(ephemeralKey);
        rsa_modulus = toByteArray(rsaKey.getModulus());
        rsa_exponent = toByteArray(rsaKey.getPublicExponent());
        signature = RSASignature.getInstance();
        signature.initSign(privateKey, sr);
        updateSignature(clntNonce.random_bytes, svrNonce.random_bytes);
        signatureBytes = signature.sign();
    }


    /*
     * Parse an RSA server key exchange message, using data known
     * to the client (and, in some situations, eavesdroppers).
     */
    RSA_ServerKeyExchange(HandshakeInStream input)
            throws IOException, NoSuchAlgorithmException {
        signature = RSASignature.getInstance();
        rsa_modulus = input.getBytes16();
        rsa_exponent = input.getBytes16();
        signatureBytes = input.getBytes16();
    }

    /*
     * Get the ephemeral RSA public key that will be used in this
     * SSL connection.
     */
    PublicKey getPublicKey() {
        try {
            KeyFactory kfac = JsseJce.getKeyFactory("RSA");
            // modulus and exponent are always positive
            RSAPublicKeySpec kspec = new RSAPublicKeySpec(
                new BigInteger(1, rsa_modulus),
                new BigInteger(1, rsa_exponent));
            return kfac.generatePublic(kspec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * Verify the signed temporary key using the hashes computed
     * from it and the two nonces.  This is called by clients
     * with "exportable" RSA flavors.
     */
    boolean verify(PublicKey certifiedKey, RandomCookie clntNonce,
            RandomCookie svrNonce) throws GeneralSecurityException {
        signature.initVerify(certifiedKey);
        updateSignature(clntNonce.random_bytes, svrNonce.random_bytes);
        return signature.verify(signatureBytes);
    }

    @Override
    int messageLength() {
        return 6 + rsa_modulus.length + rsa_exponent.length
               + signatureBytes.length;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putBytes16(rsa_modulus);
        s.putBytes16(rsa_exponent);
        s.putBytes16(signatureBytes);
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** RSA ServerKeyExchange");

        if (debug != null && Debug.isOn("verbose")) {
            Debug.println(s, "RSA Modulus", rsa_modulus);
            Debug.println(s, "RSA Public Exponent", rsa_exponent);
        }
    }
}


/*
 * Using Diffie-Hellman algorithm for key exchange.  All we really need to
 * do is securely get Diffie-Hellman keys (using the same P, G parameters)
 * to our peer, then we automatically have a shared secret without need
 * to exchange any more data.  (D-H only solutions, such as SKIP, could
 * eliminate key exchange negotiations and get faster connection setup.
 * But they still need a signature algorithm like DSS/DSA to support the
 * trusted distribution of keys without relying on unscalable physical
 * key distribution systems.)
 *
 * This class supports several DH-based key exchange algorithms, though
 * perhaps eventually each deserves its own class.  Notably, this has
 * basic support for DH_anon and its DHE_DSS and DHE_RSA signed variants.
 */
static final
class DH_ServerKeyExchange extends ServerKeyExchange
{
    // Fix message encoding, see 4348279
    private final static boolean dhKeyExchangeFix =
        Debug.getBooleanProperty("com.sun.net.ssl.dhKeyExchangeFix", true);

    private byte                dh_p [];        // 1 to 2^16 - 1 bytes
    private byte                dh_g [];        // 1 to 2^16 - 1 bytes
    private byte                dh_Ys [];       // 1 to 2^16 - 1 bytes

    private byte                signature [];

    // protocol version being established using this ServerKeyExchange message
    ProtocolVersion protocolVersion;

    // the preferable signature algorithm used by this ServerKeyExchange message
    private SignatureAndHashAlgorithm preferableSignatureAlgorithm;

    /*
     * Construct from initialized DH key object, for DH_anon
     * key exchange.
     */
    DH_ServerKeyExchange(DHCrypt obj, ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
        this.preferableSignatureAlgorithm = null;

        // The DH key has been validated in the constructor of DHCrypt.
        setValues(obj);
        signature = null;
    }

    /*
     * Construct from initialized DH key object and the key associated
     * with the cert chain which was sent ... for DHE_DSS and DHE_RSA
     * key exchange.  (Constructor called by server.)
     */
    DH_ServerKeyExchange(DHCrypt obj, PrivateKey key, byte clntNonce[],
            byte svrNonce[], SecureRandom sr,
            SignatureAndHashAlgorithm signAlgorithm,
            ProtocolVersion protocolVersion) throws GeneralSecurityException {

        this.protocolVersion = protocolVersion;

        // The DH key has been validated in the constructor of DHCrypt.
        setValues(obj);

        Signature sig;
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            this.preferableSignatureAlgorithm = signAlgorithm;
            sig = JsseJce.getSignature(signAlgorithm.getAlgorithmName());
        } else {
            this.preferableSignatureAlgorithm = null;
            if (key.getAlgorithm().equals("DSA")) {
                sig = JsseJce.getSignature(JsseJce.SIGNATURE_DSA);
            } else {
                sig = RSASignature.getInstance();
            }
        }

        sig.initSign(key, sr);
        updateSignature(sig, clntNonce, svrNonce);
        signature = sig.sign();
    }

    /*
     * Construct a DH_ServerKeyExchange message from an input
     * stream, as if sent from server to client for use with
     * DH_anon key exchange
     */
    DH_ServerKeyExchange(HandshakeInStream input,
            ProtocolVersion protocolVersion)
            throws IOException, GeneralSecurityException {

        this.protocolVersion = protocolVersion;
        this.preferableSignatureAlgorithm = null;

        dh_p = input.getBytes16();
        dh_g = input.getBytes16();
        dh_Ys = input.getBytes16();
        KeyUtil.validate(new DHPublicKeySpec(new BigInteger(1, dh_Ys),
                                             new BigInteger(1, dh_p),
                                             new BigInteger(1, dh_g)));

        signature = null;
    }

    /*
     * Construct a DH_ServerKeyExchange message from an input stream
     * and a certificate, as if sent from server to client for use with
     * DHE_DSS or DHE_RSA key exchange.  (Called by client.)
     */
    DH_ServerKeyExchange(HandshakeInStream input, PublicKey publicKey,
            byte clntNonce[], byte svrNonce[], int messageSize,
            Collection<SignatureAndHashAlgorithm> localSupportedSignAlgs,
            ProtocolVersion protocolVersion)
            throws IOException, GeneralSecurityException {

        this.protocolVersion = protocolVersion;

        // read params: ServerDHParams
        dh_p = input.getBytes16();
        dh_g = input.getBytes16();
        dh_Ys = input.getBytes16();
        KeyUtil.validate(new DHPublicKeySpec(new BigInteger(1, dh_Ys),
                                             new BigInteger(1, dh_p),
                                             new BigInteger(1, dh_g)));

        // read the signature and hash algorithm
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            int hash = input.getInt8();         // hash algorithm
            int signature = input.getInt8();    // signature algorithm

            preferableSignatureAlgorithm =
                SignatureAndHashAlgorithm.valueOf(hash, signature, 0);

            // Is it a local supported signature algorithm?
            if (!localSupportedSignAlgs.contains(
                    preferableSignatureAlgorithm)) {
                throw new SSLHandshakeException(
                        "Unsupported SignatureAndHashAlgorithm in " +
                        "ServerKeyExchange message");
            }
        } else {
            this.preferableSignatureAlgorithm = null;
        }

        // read the signature
        byte signature[];
        if (dhKeyExchangeFix) {
            signature = input.getBytes16();
        } else {
            messageSize -= (dh_p.length + 2);
            messageSize -= (dh_g.length + 2);
            messageSize -= (dh_Ys.length + 2);

            signature = new byte[messageSize];
            input.read(signature);
        }

        Signature sig;
        String algorithm = publicKey.getAlgorithm();
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            sig = JsseJce.getSignature(
                        preferableSignatureAlgorithm.getAlgorithmName());
        } else {
                switch (algorithm) {
                    case "DSA":
                        sig = JsseJce.getSignature(JsseJce.SIGNATURE_DSA);
                        break;
                    case "RSA":
                        sig = RSASignature.getInstance();
                        break;
                    default:
                        throw new SSLKeyException("neither an RSA or a DSA key");
                }
        }

        sig.initVerify(publicKey);
        updateSignature(sig, clntNonce, svrNonce);

        if (sig.verify(signature) == false ) {
            throw new SSLKeyException("Server D-H key verification failed");
        }
    }

    /* Return the Diffie-Hellman modulus */
    BigInteger getModulus() {
        return new BigInteger(1, dh_p);
    }

    /* Return the Diffie-Hellman base/generator */
    BigInteger getBase() {
        return new BigInteger(1, dh_g);
    }

    /* Return the server's Diffie-Hellman public key */
    BigInteger getServerPublicKey() {
        return new BigInteger(1, dh_Ys);
    }

    /*
     * Update sig with nonces and Diffie-Hellman public key.
     */
    private void updateSignature(Signature sig, byte clntNonce[],
            byte svrNonce[]) throws SignatureException {
        int tmp;

        sig.update(clntNonce);
        sig.update(svrNonce);

        tmp = dh_p.length;
        sig.update((byte)(tmp >> 8));
        sig.update((byte)(tmp & 0x0ff));
        sig.update(dh_p);

        tmp = dh_g.length;
        sig.update((byte)(tmp >> 8));
        sig.update((byte)(tmp & 0x0ff));
        sig.update(dh_g);

        tmp = dh_Ys.length;
        sig.update((byte)(tmp >> 8));
        sig.update((byte)(tmp & 0x0ff));
        sig.update(dh_Ys);
    }

    private void setValues(DHCrypt obj) {
        dh_p = toByteArray(obj.getModulus());
        dh_g = toByteArray(obj.getBase());
        dh_Ys = toByteArray(obj.getPublicKey());
    }

    @Override
    int messageLength() {
        int temp = 6;   // overhead for p, g, y(s) values.

        temp += dh_p.length;
        temp += dh_g.length;
        temp += dh_Ys.length;

        if (signature != null) {
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                temp += SignatureAndHashAlgorithm.sizeInRecord();
            }

            temp += signature.length;
            if (dhKeyExchangeFix) {
                temp += 2;
            }
        }

        return temp;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putBytes16(dh_p);
        s.putBytes16(dh_g);
        s.putBytes16(dh_Ys);

        if (signature != null) {
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                s.putInt8(preferableSignatureAlgorithm.getHashValue());
                s.putInt8(preferableSignatureAlgorithm.getSignatureValue());
            }

            if (dhKeyExchangeFix) {
                s.putBytes16(signature);
            } else {
                s.write(signature);
            }
        }
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** Diffie-Hellman ServerKeyExchange");

        if (debug != null && Debug.isOn("verbose")) {
            Debug.println(s, "DH Modulus", dh_p);
            Debug.println(s, "DH Base", dh_g);
            Debug.println(s, "Server DH Public Key", dh_Ys);

            if (signature == null) {
                s.println("Anonymous");
            } else {
                if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                    s.println("Signature Algorithm " +
                        preferableSignatureAlgorithm.getAlgorithmName());
                }

                s.println("Signed with a DSA or RSA public key");
            }
        }
    }
}

/*
 * ECDH server key exchange message. Sent by the server for ECDHE and ECDH_anon
 * ciphersuites to communicate its ephemeral public key (including the
 * EC domain parameters).
 *
 * We support named curves only, no explicitly encoded curves.
 */
static final
class ECDH_ServerKeyExchange extends ServerKeyExchange {

    // constants for ECCurveType
    private final static int CURVE_EXPLICIT_PRIME = 1;
    private final static int CURVE_EXPLICIT_CHAR2 = 2;
    private final static int CURVE_NAMED_CURVE    = 3;

    // id of the curve we are using
    private int curveId;
    // encoded public point
    private byte[] pointBytes;

    // signature bytes (or null if anonymous)
    private byte[] signatureBytes;

    // public key object encapsulated in this message
    private ECPublicKey publicKey;

    // protocol version being established using this ServerKeyExchange message
    ProtocolVersion protocolVersion;

    // the preferable signature algorithm used by this ServerKeyExchange message
    private SignatureAndHashAlgorithm preferableSignatureAlgorithm;

    ECDH_ServerKeyExchange(ECDHCrypt obj, PrivateKey privateKey,
            byte[] clntNonce, byte[] svrNonce, SecureRandom sr,
            SignatureAndHashAlgorithm signAlgorithm,
            ProtocolVersion protocolVersion) throws GeneralSecurityException {

        this.protocolVersion = protocolVersion;

        publicKey = (ECPublicKey)obj.getPublicKey();
        ECParameterSpec params = publicKey.getParams();
        ECPoint point = publicKey.getW();
        pointBytes = JsseJce.encodePoint(point, params.getCurve());
        curveId = SupportedEllipticCurvesExtension.getCurveIndex(params);

        if (privateKey == null) {
            // ECDH_anon
            return;
        }

        Signature sig;
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            this.preferableSignatureAlgorithm = signAlgorithm;
            sig = JsseJce.getSignature(signAlgorithm.getAlgorithmName());
        } else {
            sig = getSignature(privateKey.getAlgorithm());
        }
        sig.initSign(privateKey);  // where is the SecureRandom?

        updateSignature(sig, clntNonce, svrNonce);
        signatureBytes = sig.sign();
    }

    /*
     * Parse an ECDH server key exchange message.
     */
    ECDH_ServerKeyExchange(HandshakeInStream input, PublicKey signingKey,
            byte[] clntNonce, byte[] svrNonce,
            Collection<SignatureAndHashAlgorithm> localSupportedSignAlgs,
            ProtocolVersion protocolVersion)
            throws IOException, GeneralSecurityException {

        this.protocolVersion = protocolVersion;

        // read params: ServerECDHParams
        int curveType = input.getInt8();
        ECParameterSpec parameters;
        // These parsing errors should never occur as we negotiated
        // the supported curves during the exchange of the Hello messages.
        if (curveType == CURVE_NAMED_CURVE) {
            curveId = input.getInt16();
            if (SupportedEllipticCurvesExtension.isSupported(curveId)
                    == false) {
                throw new SSLHandshakeException(
                    "Unsupported curveId: " + curveId);
            }
            String curveOid =
                SupportedEllipticCurvesExtension.getCurveOid(curveId);
            if (curveOid == null) {
                throw new SSLHandshakeException(
                    "Unknown named curve: " + curveId);
            }
            parameters = JsseJce.getECParameterSpec(curveOid);
            if (parameters == null) {
                throw new SSLHandshakeException(
                    "Unsupported curve: " + curveOid);
            }
        } else {
            throw new SSLHandshakeException(
                "Unsupported ECCurveType: " + curveType);
        }
        pointBytes = input.getBytes8();

        ECPoint point = JsseJce.decodePoint(pointBytes, parameters.getCurve());
        KeyFactory factory = JsseJce.getKeyFactory("EC");
        publicKey = (ECPublicKey)factory.generatePublic(
            new ECPublicKeySpec(point, parameters));

        if (signingKey == null) {
            // ECDH_anon
            return;
        }

        // read the signature and hash algorithm
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            int hash = input.getInt8();         // hash algorithm
            int signature = input.getInt8();    // signature algorithm

            preferableSignatureAlgorithm =
                SignatureAndHashAlgorithm.valueOf(hash, signature, 0);

            // Is it a local supported signature algorithm?
            if (!localSupportedSignAlgs.contains(
                    preferableSignatureAlgorithm)) {
                throw new SSLHandshakeException(
                        "Unsupported SignatureAndHashAlgorithm in " +
                        "ServerKeyExchange message");
            }
        }

        // read the signature
        signatureBytes = input.getBytes16();

        // verify the signature
        Signature sig;
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            sig = JsseJce.getSignature(
                        preferableSignatureAlgorithm.getAlgorithmName());
        } else {
            sig = getSignature(signingKey.getAlgorithm());
        }
        sig.initVerify(signingKey);

        updateSignature(sig, clntNonce, svrNonce);

        if (sig.verify(signatureBytes) == false ) {
            throw new SSLKeyException(
                "Invalid signature on ECDH server key exchange message");
        }
    }

    /*
     * Get the ephemeral EC public key encapsulated in this message.
     */
    ECPublicKey getPublicKey() {
        return publicKey;
    }

    private static Signature getSignature(String keyAlgorithm)
            throws NoSuchAlgorithmException {
            switch (keyAlgorithm) {
                case "EC":
                    return JsseJce.getSignature(JsseJce.SIGNATURE_ECDSA);
                case "RSA":
                    return RSASignature.getInstance();
                default:
                    throw new NoSuchAlgorithmException("neither an RSA or a EC key");
            }
    }

    private void updateSignature(Signature sig, byte clntNonce[],
            byte svrNonce[]) throws SignatureException {
        sig.update(clntNonce);
        sig.update(svrNonce);

        sig.update((byte)CURVE_NAMED_CURVE);
        sig.update((byte)(curveId >> 8));
        sig.update((byte)curveId);
        sig.update((byte)pointBytes.length);
        sig.update(pointBytes);
    }

    @Override
    int messageLength() {
        int sigLen = 0;
        if (signatureBytes != null) {
            sigLen = 2 + signatureBytes.length;
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                sigLen += SignatureAndHashAlgorithm.sizeInRecord();
            }
        }

        return 4 + pointBytes.length + sigLen;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt8(CURVE_NAMED_CURVE);
        s.putInt16(curveId);
        s.putBytes8(pointBytes);

        if (signatureBytes != null) {
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                s.putInt8(preferableSignatureAlgorithm.getHashValue());
                s.putInt8(preferableSignatureAlgorithm.getSignatureValue());
            }

            s.putBytes16(signatureBytes);
        }
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** ECDH ServerKeyExchange");

        if (debug != null && Debug.isOn("verbose")) {
            if (signatureBytes == null) {
                s.println("Anonymous");
            } else {
                if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                    s.println("Signature Algorithm " +
                            preferableSignatureAlgorithm.getAlgorithmName());
                }
            }

            s.println("Server key: " + publicKey);
        }
    }
}

static final class DistinguishedName {

    /*
     * DER encoded distinguished name.
     * TLS requires that its not longer than 65535 bytes.
     */
    byte name[];

    DistinguishedName(HandshakeInStream input) throws IOException {
        name = input.getBytes16();
    }

    DistinguishedName(X500Principal dn) {
        name = dn.getEncoded();
    }

    X500Principal getX500Principal() throws IOException {
        try {
            return new X500Principal(name);
        } catch (IllegalArgumentException e) {
            throw (SSLProtocolException)new SSLProtocolException(
                e.getMessage()).initCause(e);
        }
    }

    int length() {
        return 2 + name.length;
    }

    void send(HandshakeOutStream output) throws IOException {
        output.putBytes16(name);
    }

    void print(PrintStream output) throws IOException {
        X500Principal principal = new X500Principal(name);
        output.println("<" + principal.toString() + ">");
    }
}

/*
 * CertificateRequest ... SERVER --> CLIENT
 *
 * Authenticated servers may ask clients to authenticate themselves
 * in turn, using this message.
 *
 * Prior to TLS 1.2, the structure of the message is defined as:
 *     struct {
 *         ClientCertificateType certificate_types<1..2^8-1>;
 *         DistinguishedName certificate_authorities<0..2^16-1>;
 *     } CertificateRequest;
 *
 * In TLS 1.2, the structure is changed to:
 *     struct {
 *         ClientCertificateType certificate_types<1..2^8-1>;
 *         SignatureAndHashAlgorithm
 *           supported_signature_algorithms<2^16-1>;
 *         DistinguishedName certificate_authorities<0..2^16-1>;
 *     } CertificateRequest;
 *
 */
static final
class CertificateRequest extends HandshakeMessage
{
    // enum ClientCertificateType
    static final int   cct_rsa_sign = 1;
    static final int   cct_dss_sign = 2;
    static final int   cct_rsa_fixed_dh = 3;
    static final int   cct_dss_fixed_dh = 4;

    // The existance of these two values is a bug in the SSL specification.
    // They are never used in the protocol.
    static final int   cct_rsa_ephemeral_dh = 5;
    static final int   cct_dss_ephemeral_dh = 6;

    // From RFC 4492 (ECC)
    static final int    cct_ecdsa_sign       = 64;
    static final int    cct_rsa_fixed_ecdh   = 65;
    static final int    cct_ecdsa_fixed_ecdh = 66;

    private final static byte[] TYPES_NO_ECC = { cct_rsa_sign, cct_dss_sign };
    private final static byte[] TYPES_ECC =
        { cct_rsa_sign, cct_dss_sign, cct_ecdsa_sign };

    byte                types [];               // 1 to 255 types
    DistinguishedName   authorities [];         // 3 to 2^16 - 1
        // ... "3" because that's the smallest DER-encoded X500 DN

    // protocol version being established using this CertificateRequest message
    ProtocolVersion protocolVersion;

    // supported_signature_algorithms for TLS 1.2 or later
    private Collection<SignatureAndHashAlgorithm> algorithms;

    // length of supported_signature_algorithms
    private int algorithmsLen;

    CertificateRequest(X509Certificate ca[], KeyExchange keyExchange,
            Collection<SignatureAndHashAlgorithm> signAlgs,
            ProtocolVersion protocolVersion) throws IOException {

        this.protocolVersion = protocolVersion;

        // always use X500Principal
        authorities = new DistinguishedName[ca.length];
        for (int i = 0; i < ca.length; i++) {
            X500Principal x500Principal = ca[i].getSubjectX500Principal();
            authorities[i] = new DistinguishedName(x500Principal);
        }
        // we support RSA, DSS, and ECDSA client authentication and they
        // can be used with all ciphersuites. If this changes, the code
        // needs to be adapted to take keyExchange into account.
        // We only request ECDSA client auth if we have ECC crypto available.
        this.types = JsseJce.isEcAvailable() ? TYPES_ECC : TYPES_NO_ECC;

        // Use supported_signature_algorithms for TLS 1.2 or later.
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            if (signAlgs == null || signAlgs.isEmpty()) {
                throw new SSLProtocolException(
                        "No supported signature algorithms");
            }

            algorithms = new ArrayList<SignatureAndHashAlgorithm>(signAlgs);
            algorithmsLen =
                SignatureAndHashAlgorithm.sizeInRecord() * algorithms.size();
        } else {
            algorithms = new ArrayList<SignatureAndHashAlgorithm>();
            algorithmsLen = 0;
        }
    }

    CertificateRequest(HandshakeInStream input,
            ProtocolVersion protocolVersion) throws IOException {

        this.protocolVersion = protocolVersion;

        // Read the certificate_types.
        types = input.getBytes8();

        // Read the supported_signature_algorithms for TLS 1.2 or later.
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            algorithmsLen = input.getInt16();
            if (algorithmsLen < 2) {
                throw new SSLProtocolException(
                        "Invalid supported_signature_algorithms field");
            }

            algorithms = new ArrayList<SignatureAndHashAlgorithm>();
            int remains = algorithmsLen;
            int sequence = 0;
            while (remains > 1) {    // needs at least two bytes
                int hash = input.getInt8();         // hash algorithm
                int signature = input.getInt8();    // signature algorithm

                SignatureAndHashAlgorithm algorithm =
                    SignatureAndHashAlgorithm.valueOf(hash, signature,
                                                                ++sequence);
                algorithms.add(algorithm);
                remains -= 2;  // one byte for hash, one byte for signature
            }

            if (remains != 0) {
                throw new SSLProtocolException(
                        "Invalid supported_signature_algorithms field");
            }
        } else {
            algorithms = new ArrayList<SignatureAndHashAlgorithm>();
            algorithmsLen = 0;
        }

        // read the certificate_authorities
        int len = input.getInt16();
        ArrayList<DistinguishedName> v = new ArrayList<>();
        while (len >= 3) {
            DistinguishedName dn = new DistinguishedName(input);
            v.add(dn);
            len -= dn.length();
        }

        if (len != 0) {
            throw new SSLProtocolException("Bad CertificateRequest DN length");
        }

        authorities = v.toArray(new DistinguishedName[v.size()]);
    }

    X500Principal[] getAuthorities() throws IOException {
        X500Principal[] ret = new X500Principal[authorities.length];
        for (int i = 0; i < authorities.length; i++) {
            ret[i] = authorities[i].getX500Principal();
        }
        return ret;
    }

    Collection<SignatureAndHashAlgorithm> getSignAlgorithms() {
        return algorithms;
    }

    @Override
    int messageType() {
        return ht_certificate_request;
    }

    @Override
    int messageLength() {
        int len = 1 + types.length + 2;

        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            len += algorithmsLen + 2;
        }

        for (int i = 0; i < authorities.length; i++) {
            len += authorities[i].length();
        }

        return len;
    }

    @Override
    void send(HandshakeOutStream output) throws IOException {
        // put certificate_types
        output.putBytes8(types);

        // put supported_signature_algorithms
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            output.putInt16(algorithmsLen);
            for (SignatureAndHashAlgorithm algorithm : algorithms) {
                output.putInt8(algorithm.getHashValue());      // hash
                output.putInt8(algorithm.getSignatureValue()); // signature
            }
        }

        // put certificate_authorities
        int len = 0;
        for (int i = 0; i < authorities.length; i++) {
            len += authorities[i].length();
        }

        output.putInt16(len);
        for (int i = 0; i < authorities.length; i++) {
            authorities[i].send(output);
        }
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** CertificateRequest");

        if (debug != null && Debug.isOn("verbose")) {
            s.print("Cert Types: ");
            for (int i = 0; i < types.length; i++) {
                switch (types[i]) {
                  case cct_rsa_sign:
                    s.print("RSA"); break;
                  case cct_dss_sign:
                    s.print("DSS"); break;
                  case cct_rsa_fixed_dh:
                    s.print("Fixed DH (RSA sig)"); break;
                  case cct_dss_fixed_dh:
                    s.print("Fixed DH (DSS sig)"); break;
                  case cct_rsa_ephemeral_dh:
                    s.print("Ephemeral DH (RSA sig)"); break;
                  case cct_dss_ephemeral_dh:
                    s.print("Ephemeral DH (DSS sig)"); break;
                  case cct_ecdsa_sign:
                    s.print("ECDSA"); break;
                  case cct_rsa_fixed_ecdh:
                    s.print("Fixed ECDH (RSA sig)"); break;
                  case cct_ecdsa_fixed_ecdh:
                    s.print("Fixed ECDH (ECDSA sig)"); break;
                  default:
                    s.print("Type-" + (types[i] & 0xff)); break;
                }
                if (i != types.length - 1) {
                    s.print(", ");
                }
            }
            s.println();

            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                StringBuilder sb = new StringBuilder();
                boolean opened = false;
                for (SignatureAndHashAlgorithm signAlg : algorithms) {
                    if (opened) {
                        sb.append(", ").append(signAlg.getAlgorithmName());
                    } else {
                        sb.append(signAlg.getAlgorithmName());
                        opened = true;
                    }
                }
                s.println("Supported Signature Algorithms: " + sb);
            }

            s.println("Cert Authorities:");
            if (authorities.length == 0) {
                s.println("<Empty>");
            } else {
                for (int i = 0; i < authorities.length; i++) {
                    authorities[i].print(s);
                }
            }
        }
    }
}


/*
 * ServerHelloDone ... SERVER --> CLIENT
 *
 * When server's done sending its messages in response to the client's
 * "hello" (e.g. its own hello, certificate, key exchange message, perhaps
 * client certificate request) it sends this message to flag that it's
 * done that part of the handshake.
 */
static final
class ServerHelloDone extends HandshakeMessage
{
    @Override
    int messageType() { return ht_server_hello_done; }

    ServerHelloDone() { }

    ServerHelloDone(HandshakeInStream input)
    {
        // nothing to do
    }

    @Override
    int messageLength()
    {
        return 0;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException
    {
        // nothing to send
    }

    @Override
    void print(PrintStream s) throws IOException
    {
        s.println("*** ServerHelloDone");
    }
}


/*
 * CertificateVerify ... CLIENT --> SERVER
 *
 * Sent after client sends signature-capable certificates (e.g. not
 * Diffie-Hellman) to verify.
 */
static final class CertificateVerify extends HandshakeMessage {

    // the signature bytes
    private byte[] signature;

    // protocol version being established using this ServerKeyExchange message
    ProtocolVersion protocolVersion;

    // the preferable signature algorithm used by this CertificateVerify message
    private SignatureAndHashAlgorithm preferableSignatureAlgorithm = null;

    /*
     * Create an RSA or DSA signed certificate verify message.
     */
    CertificateVerify(ProtocolVersion protocolVersion,
            HandshakeHash handshakeHash, PrivateKey privateKey,
            SecretKey masterSecret, SecureRandom sr,
            SignatureAndHashAlgorithm signAlgorithm)
            throws GeneralSecurityException {

        this.protocolVersion = protocolVersion;

        String algorithm = privateKey.getAlgorithm();
        Signature sig = null;
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            this.preferableSignatureAlgorithm = signAlgorithm;
            sig = JsseJce.getSignature(signAlgorithm.getAlgorithmName());
        } else {
            sig = getSignature(protocolVersion, algorithm);
        }
        sig.initSign(privateKey, sr);
        updateSignature(sig, protocolVersion, handshakeHash, algorithm,
                        masterSecret);
        signature = sig.sign();
    }

    //
    // Unmarshal the signed data from the input stream.
    //
    CertificateVerify(HandshakeInStream input,
            Collection<SignatureAndHashAlgorithm> localSupportedSignAlgs,
            ProtocolVersion protocolVersion) throws IOException  {

        this.protocolVersion = protocolVersion;

        // read the signature and hash algorithm
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            int hashAlg = input.getInt8();         // hash algorithm
            int signAlg = input.getInt8();         // signature algorithm

            preferableSignatureAlgorithm =
                SignatureAndHashAlgorithm.valueOf(hashAlg, signAlg, 0);

            // Is it a local supported signature algorithm?
            if (!localSupportedSignAlgs.contains(
                    preferableSignatureAlgorithm)) {
                throw new SSLHandshakeException(
                        "Unsupported SignatureAndHashAlgorithm in " +
                        "ServerKeyExchange message");
            }
        }

        // read the signature
        signature = input.getBytes16();
    }

    /*
     * Get the preferable signature algorithm used by this message
     */
    SignatureAndHashAlgorithm getPreferableSignatureAlgorithm() {
        return preferableSignatureAlgorithm;
    }

    /*
     * Verify a certificate verify message. Return the result of verification,
     * if there is a problem throw a GeneralSecurityException.
     */
    boolean verify(ProtocolVersion protocolVersion,
            HandshakeHash handshakeHash, PublicKey publicKey,
            SecretKey masterSecret) throws GeneralSecurityException {
        String algorithm = publicKey.getAlgorithm();
        Signature sig = null;
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            sig = JsseJce.getSignature(
                        preferableSignatureAlgorithm.getAlgorithmName());
        } else {
            sig = getSignature(protocolVersion, algorithm);
        }
        sig.initVerify(publicKey);
        updateSignature(sig, protocolVersion, handshakeHash, algorithm,
                        masterSecret);
        return sig.verify(signature);
    }

    /*
     * Get the Signature object appropriate for verification using the
     * given signature algorithm and protocol version.
     */
    private static Signature getSignature(ProtocolVersion protocolVersion,
            String algorithm) throws GeneralSecurityException {
            switch (algorithm) {
                case "RSA":
                    return RSASignature.getInternalInstance();
                case "DSA":
                    return JsseJce.getSignature(JsseJce.SIGNATURE_RAWDSA);
                case "EC":
                    return JsseJce.getSignature(JsseJce.SIGNATURE_RAWECDSA);
                default:
                    throw new SignatureException("Unrecognized algorithm: "
                        + algorithm);
            }
    }

    /*
     * Update the Signature with the data appropriate for the given
     * signature algorithm and protocol version so that the object is
     * ready for signing or verifying.
     */
    private static void updateSignature(Signature sig,
            ProtocolVersion protocolVersion,
            HandshakeHash handshakeHash, String algorithm, SecretKey masterKey)
            throws SignatureException {

        if (algorithm.equals("RSA")) {
            if (protocolVersion.v < ProtocolVersion.TLS12.v) { // TLS1.1-
                MessageDigest md5Clone = handshakeHash.getMD5Clone();
                MessageDigest shaClone = handshakeHash.getSHAClone();

                if (protocolVersion.v < ProtocolVersion.TLS10.v) { // SSLv3
                    updateDigest(md5Clone, MD5_pad1, MD5_pad2, masterKey);
                    updateDigest(shaClone, SHA_pad1, SHA_pad2, masterKey);
                }

                // The signature must be an instance of RSASignature, need
                // to use these hashes directly.
                RSASignature.setHashes(sig, md5Clone, shaClone);
            } else {  // TLS1.2+
                sig.update(handshakeHash.getAllHandshakeMessages());
            }
        } else { // DSA, ECDSA
            if (protocolVersion.v < ProtocolVersion.TLS12.v) { // TLS1.1-
                MessageDigest shaClone = handshakeHash.getSHAClone();

                if (protocolVersion.v < ProtocolVersion.TLS10.v) { // SSLv3
                    updateDigest(shaClone, SHA_pad1, SHA_pad2, masterKey);
                }

                sig.update(shaClone.digest());
            } else {  // TLS1.2+
                sig.update(handshakeHash.getAllHandshakeMessages());
            }
        }
    }

    /*
     * Update the MessageDigest for SSLv3 certificate verify or finished
     * message calculation. The digest must already have been updated with
     * all preceding handshake messages.
     * Used by the Finished class as well.
     */
    private static void updateDigest(MessageDigest md,
            byte[] pad1, byte[] pad2,
            SecretKey masterSecret) {
        // Digest the key bytes if available.
        // Otherwise (sensitive key), try digesting the key directly.
        // That is currently only implemented in SunPKCS11 using a private
        // reflection API, so we avoid that if possible.
        byte[] keyBytes = "RAW".equals(masterSecret.getFormat())
                        ? masterSecret.getEncoded() : null;
        if (keyBytes != null) {
            md.update(keyBytes);
        } else {
            digestKey(md, masterSecret);
        }
        md.update(pad1);
        byte[] temp = md.digest();

        if (keyBytes != null) {
            md.update(keyBytes);
        } else {
            digestKey(md, masterSecret);
        }
        md.update(pad2);
        md.update(temp);
    }

    private final static Class<?> delegate;
    private final static Field spiField;

    static {
        try {
            delegate = Class.forName("java.security.MessageDigest$Delegate");
            spiField = delegate.getDeclaredField("digestSpi");
        } catch (Exception e) {
            throw new RuntimeException("Reflection failed", e);
        }
        makeAccessible(spiField);
    }

    private static void makeAccessible(final AccessibleObject o) {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                o.setAccessible(true);
                return null;
            }
        });
    }

    // ConcurrentHashMap does not allow null values, use this marker object
    private final static Object NULL_OBJECT = new Object();

    // cache Method objects per Spi class
    // Note that this will prevent the Spi classes from being GC'd. We assume
    // that is not a problem.
    private final static Map<Class<?>,Object> methodCache =
                                        new ConcurrentHashMap<>();

    private static void digestKey(MessageDigest md, SecretKey key) {
        try {
            // Verify that md is implemented via MessageDigestSpi, not
            // via JDK 1.1 style MessageDigest subclassing.
            if (md.getClass() != delegate) {
                throw new Exception("Digest is not a MessageDigestSpi");
            }
            MessageDigestSpi spi = (MessageDigestSpi)spiField.get(md);
            Class<?> clazz = spi.getClass();
            Object r = methodCache.get(clazz);
            if (r == null) {
                try {
                    r = clazz.getDeclaredMethod("implUpdate", SecretKey.class);
                    makeAccessible((Method)r);
                } catch (NoSuchMethodException e) {
                    r = NULL_OBJECT;
                }
                methodCache.put(clazz, r);
            }
            if (r == NULL_OBJECT) {
                throw new Exception(
                    "Digest does not support implUpdate(SecretKey)");
            }
            Method update = (Method)r;
            update.invoke(spi, key);
        } catch (Exception e) {
            throw new RuntimeException(
                "Could not obtain encoded key and "
                + "MessageDigest cannot digest key", e);
        }
    }

    @Override
    int messageType() {
        return ht_certificate_verify;
    }

    @Override
    int messageLength() {
        int temp = 2;

        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            temp += SignatureAndHashAlgorithm.sizeInRecord();
        }

        return temp + signature.length;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
            s.putInt8(preferableSignatureAlgorithm.getHashValue());
            s.putInt8(preferableSignatureAlgorithm.getSignatureValue());
        }

        s.putBytes16(signature);
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** CertificateVerify");

        if (debug != null && Debug.isOn("verbose")) {
            if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                s.println("Signature Algorithm " +
                        preferableSignatureAlgorithm.getAlgorithmName());
            }
        }
    }
}


/*
 * FINISHED ... sent by both CLIENT and SERVER
 *
 * This is the FINISHED message as defined in the SSL and TLS protocols.
 * Both protocols define this handshake message slightly differently.
 * This class supports both formats.
 *
 * When handshaking is finished, each side sends a "change_cipher_spec"
 * record, then immediately sends a "finished" handshake message prepared
 * according to the newly adopted cipher spec.
 *
 * NOTE that until this is sent, no application data may be passed, unless
 * some non-default cipher suite has already been set up on this connection
 * connection (e.g. a previous handshake arranged one).
 */
static final class Finished extends HandshakeMessage {

    // constant for a Finished message sent by the client
    final static int CLIENT = 1;

    // constant for a Finished message sent by the server
    final static int SERVER = 2;

    // enum Sender:  "CLNT" and "SRVR"
    private static final byte[] SSL_CLIENT = { 0x43, 0x4C, 0x4E, 0x54 };
    private static final byte[] SSL_SERVER = { 0x53, 0x52, 0x56, 0x52 };

    /*
     * Contents of the finished message ("checksum"). For TLS, it
     * is 12 bytes long, for SSLv3 36 bytes.
     */
    private byte[] verifyData;

    /*
     * Current cipher suite we are negotiating.  TLS 1.2 has
     * ciphersuite-defined PRF algorithms.
     */
    private ProtocolVersion protocolVersion;
    private CipherSuite cipherSuite;

    /*
     * Create a finished message to send to the remote peer.
     */
    Finished(ProtocolVersion protocolVersion, HandshakeHash handshakeHash,
            int sender, SecretKey master, CipherSuite cipherSuite) {
        this.protocolVersion = protocolVersion;
        this.cipherSuite = cipherSuite;
        verifyData = getFinished(handshakeHash, sender, master);
    }

    /*
     * Constructor that reads FINISHED message from stream.
     */
    Finished(ProtocolVersion protocolVersion, HandshakeInStream input,
            CipherSuite cipherSuite) throws IOException {
        this.protocolVersion = protocolVersion;
        this.cipherSuite = cipherSuite;
        int msgLen = (protocolVersion.v >= ProtocolVersion.TLS10.v) ? 12 : 36;
        verifyData = new byte[msgLen];
        input.read(verifyData);
    }

    /*
     * Verify that the hashes here are what would have been produced
     * according to a given set of inputs.  This is used to ensure that
     * both client and server are fully in sync, and that the handshake
     * computations have been successful.
     */
    boolean verify(HandshakeHash handshakeHash, int sender, SecretKey master) {
        byte[] myFinished = getFinished(handshakeHash, sender, master);
        return Arrays.equals(myFinished, verifyData);
    }

    /*
     * Perform the actual finished message calculation.
     */
    private byte[] getFinished(HandshakeHash handshakeHash,
            int sender, SecretKey masterKey) {
        byte[] sslLabel;
        String tlsLabel;
        if (sender == CLIENT) {
            sslLabel = SSL_CLIENT;
            tlsLabel = "client finished";
        } else if (sender == SERVER) {
            sslLabel = SSL_SERVER;
            tlsLabel = "server finished";
        } else {
            throw new RuntimeException("Invalid sender: " + sender);
        }

        if (protocolVersion.v >= ProtocolVersion.TLS10.v) {
            // TLS 1.0+
            try {
                byte [] seed;
                String prfAlg;
                PRF prf;

                // Get the KeyGenerator alg and calculate the seed.
                if (protocolVersion.v >= ProtocolVersion.TLS12.v) {
                    // TLS 1.2
                    seed = handshakeHash.getFinishedHash();

                    prfAlg = "SunTls12Prf";
                    prf = cipherSuite.prfAlg;
                } else {
                    // TLS 1.0/1.1
                    MessageDigest md5Clone = handshakeHash.getMD5Clone();
                    MessageDigest shaClone = handshakeHash.getSHAClone();
                    seed = new byte[36];
                    md5Clone.digest(seed, 0, 16);
                    shaClone.digest(seed, 16, 20);

                    prfAlg = "SunTlsPrf";
                    prf = P_NONE;
                }

                String prfHashAlg = prf.getPRFHashAlg();
                int prfHashLength = prf.getPRFHashLength();
                int prfBlockSize = prf.getPRFBlockSize();

                /*
                 * RFC 5246/7.4.9 says that finished messages can
                 * be ciphersuite-specific in both length/PRF hash
                 * algorithm.  If we ever run across a different
                 * length, this call will need to be updated.
                 */
                @SuppressWarnings("deprecation")
                TlsPrfParameterSpec spec = new TlsPrfParameterSpec(
                    masterKey, tlsLabel, seed, 12,
                    prfHashAlg, prfHashLength, prfBlockSize);

                KeyGenerator kg = JsseJce.getKeyGenerator(prfAlg);
                kg.init(spec);
                SecretKey prfKey = kg.generateKey();
                if ("RAW".equals(prfKey.getFormat()) == false) {
                    throw new ProviderException(
                        "Invalid PRF output, format must be RAW");
                }
                byte[] finished = prfKey.getEncoded();
                return finished;
            } catch (GeneralSecurityException e) {
                throw new RuntimeException("PRF failed", e);
            }
        } else {
            // SSLv3
            MessageDigest md5Clone = handshakeHash.getMD5Clone();
            MessageDigest shaClone = handshakeHash.getSHAClone();
            updateDigest(md5Clone, sslLabel, MD5_pad1, MD5_pad2, masterKey);
            updateDigest(shaClone, sslLabel, SHA_pad1, SHA_pad2, masterKey);
            byte[] finished = new byte[36];
            try {
                md5Clone.digest(finished, 0, 16);
                shaClone.digest(finished, 16, 20);
            } catch (DigestException e) {
                // cannot occur
                throw new RuntimeException("Digest failed", e);
            }
            return finished;
        }
    }

    /*
     * Update the MessageDigest for SSLv3 finished message calculation.
     * The digest must already have been updated with all preceding handshake
     * messages. This operation is almost identical to the certificate verify
     * hash, reuse that code.
     */
    private static void updateDigest(MessageDigest md, byte[] sender,
            byte[] pad1, byte[] pad2, SecretKey masterSecret) {
        md.update(sender);
        CertificateVerify.updateDigest(md, pad1, pad2, masterSecret);
    }

    // get the verify_data of the finished message
    byte[] getVerifyData() {
        return verifyData;
    }

    @Override
    int messageType() { return ht_finished; }

    @Override
    int messageLength() {
        return verifyData.length;
    }

    @Override
    void send(HandshakeOutStream out) throws IOException {
        out.write(verifyData);
    }

    @Override
    void print(PrintStream s) throws IOException {
        s.println("*** Finished");
        if (debug != null && Debug.isOn("verbose")) {
            Debug.println(s, "verify_data", verifyData);
            s.println("***");
        }
    }
}

//
// END of nested classes
//

}
