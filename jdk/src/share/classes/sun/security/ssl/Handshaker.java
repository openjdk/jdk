/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.security.ssl;

import java.io.*;
import java.util.*;
import java.security.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.cert.X509Certificate;

import javax.crypto.*;
import javax.crypto.spec.*;

import javax.net.ssl.*;
import sun.misc.HexDumpEncoder;

import sun.security.internal.spec.*;
import sun.security.internal.interfaces.TlsMasterSecret;

import sun.security.ssl.HandshakeMessage.*;
import sun.security.ssl.CipherSuite.*;

/**
 * Handshaker ... processes handshake records from an SSL V3.0
 * data stream, handling all the details of the handshake protocol.
 *
 * Note that the real protocol work is done in two subclasses, the  base
 * class just provides the control flow and key generation framework.
 *
 * @author David Brownell
 */
abstract class Handshaker {

    // current protocol version
    ProtocolVersion protocolVersion;

    // list of enabled protocols
    ProtocolList enabledProtocols;

    private boolean             isClient;

    SSLSocketImpl               conn = null;
    SSLEngineImpl               engine = null;

    HandshakeHash               handshakeHash;
    HandshakeInStream           input;
    HandshakeOutStream          output;
    int                         state;
    SSLContextImpl              sslContext;
    RandomCookie                clnt_random, svr_random;
    SSLSessionImpl              session;

    // Temporary MD5 and SHA message digests. Must always be left
    // in reset state after use.
    private MessageDigest md5Tmp, shaTmp;

    // list of enabled CipherSuites
    CipherSuiteList     enabledCipherSuites;

    // current CipherSuite. Never null, initially SSL_NULL_WITH_NULL_NULL
    CipherSuite         cipherSuite;

    // current key exchange. Never null, initially K_NULL
    KeyExchange         keyExchange;

    /* True if this session is being resumed (fast handshake) */
    boolean             resumingSession;

    /* True if it's OK to start a new SSL session */
    boolean             enableNewSession;

    // Temporary storage for the individual keys. Set by
    // calculateConnectionKeys() and cleared once the ciphers are
    // activated.
    private SecretKey clntWriteKey, svrWriteKey;
    private IvParameterSpec clntWriteIV, svrWriteIV;
    private SecretKey clntMacSecret, svrMacSecret;

    /*
     * Delegated task subsystem data structures.
     *
     * If thrown is set, we need to propagate this back immediately
     * on entry into processMessage().
     *
     * Data is protected by the SSLEngine.this lock.
     */
    private volatile boolean taskDelegated = false;
    private volatile DelegatedTask delegatedTask = null;
    private volatile Exception thrown = null;

    // Could probably use a java.util.concurrent.atomic.AtomicReference
    // here instead of using this lock.  Consider changing.
    private Object thrownLock = new Object();

    /* Class and subclass dynamic debugging support */
    static final Debug debug = Debug.getInstance("ssl");

    Handshaker(SSLSocketImpl c, SSLContextImpl context,
            ProtocolList enabledProtocols, boolean needCertVerify,
            boolean isClient) {
        this.conn = c;
        init(context, enabledProtocols, needCertVerify, isClient);
    }

    Handshaker(SSLEngineImpl engine, SSLContextImpl context,
            ProtocolList enabledProtocols, boolean needCertVerify,
            boolean isClient) {
        this.engine = engine;
        init(context, enabledProtocols, needCertVerify, isClient);
    }

    private void init(SSLContextImpl context, ProtocolList enabledProtocols,
            boolean needCertVerify, boolean isClient) {

        this.sslContext = context;
        this.isClient = isClient;
        enableNewSession = true;

        setCipherSuite(CipherSuite.C_NULL);

        md5Tmp = JsseJce.getMD5();
        shaTmp = JsseJce.getSHA();

        //
        // We accumulate digests of the handshake messages so that
        // we can read/write CertificateVerify and Finished messages,
        // getting assurance against some particular active attacks.
        //
        handshakeHash = new HandshakeHash(needCertVerify);

        setEnabledProtocols(enabledProtocols);

        if (conn != null) {
            conn.getAppInputStream().r.setHandshakeHash(handshakeHash);
        } else {        // engine != null
            engine.inputRecord.setHandshakeHash(handshakeHash);
        }


        //
        // In addition to the connection state machine, controlling
        // how the connection deals with the different sorts of records
        // that get sent (notably handshake transitions!), there's
        // also a handshaking state machine that controls message
        // sequencing.
        //
        // It's a convenient artifact of the protocol that this can,
        // with only a couple of minor exceptions, be driven by the
        // type constant for the last message seen:  except for the
        // client's cert verify, those constants are in a convenient
        // order to drastically simplify state machine checking.
        //
        state = -1;
    }

    /*
     * Reroutes calls to the SSLSocket or SSLEngine (*SE).
     *
     * We could have also done it by extra classes
     * and letting them override, but this seemed much
     * less involved.
     */
    void fatalSE(byte b, String diagnostic) throws IOException {
        fatalSE(b, diagnostic, null);
    }

    void fatalSE(byte b, Throwable cause) throws IOException {
        fatalSE(b, null, cause);
    }

    void fatalSE(byte b, String diagnostic, Throwable cause)
            throws IOException {
        if (conn != null) {
            conn.fatal(b, diagnostic, cause);
        } else {
            engine.fatal(b, diagnostic, cause);
        }
    }

    void warningSE(byte b) {
        if (conn != null) {
            conn.warning(b);
        } else {
            engine.warning(b);
        }
    }

    String getHostSE() {
        if (conn != null) {
            return conn.getHost();
        } else {
            return engine.getPeerHost();
        }
    }

    String getHostAddressSE() {
        if (conn != null) {
            return conn.getInetAddress().getHostAddress();
        } else {
            /*
             * This is for caching only, doesn't matter that's is really
             * a hostname.  The main thing is that it doesn't do
             * a reverse DNS lookup, potentially slowing things down.
             */
            return engine.getPeerHost();
        }
    }

    boolean isLoopbackSE() {
        if (conn != null) {
            return conn.getInetAddress().isLoopbackAddress();
        } else {
            return false;
        }
    }

    int getPortSE() {
        if (conn != null) {
            return conn.getPort();
        } else {
            return engine.getPeerPort();
        }
    }

    int getLocalPortSE() {
        if (conn != null) {
            return conn.getLocalPort();
        } else {
            return -1;
        }
    }

    String getHostnameVerificationSE() {
        if (conn != null) {
            return conn.getHostnameVerification();
        } else {
            return engine.getHostnameVerification();
        }
    }

    AccessControlContext getAccSE() {
        if (conn != null) {
            return conn.getAcc();
        } else {
            return engine.getAcc();
        }
    }

    private void setVersionSE(ProtocolVersion protocolVersion) {
        if (conn != null) {
            conn.setVersion(protocolVersion);
        } else {
            engine.setVersion(protocolVersion);
        }
    }

    /**
     * Set the active protocol version and propagate it to the SSLSocket
     * and our handshake streams. Called from ClientHandshaker
     * and ServerHandshaker with the negotiated protocol version.
     */
    void setVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
        setVersionSE(protocolVersion);
        output.r.setVersion(protocolVersion);
    }


    /**
     * Set the enabled protocols. Called from the constructor or
     * SSLSocketImpl.setEnabledProtocols() (if the handshake is not yet
     * in progress).
     */
    void setEnabledProtocols(ProtocolList enabledProtocols) {
        this.enabledProtocols = enabledProtocols;

        // temporary protocol version until the actual protocol version
        // is negotiated in the Hello exchange. This affects the record
        // version we sent with the ClientHello. Using max() as the record
        // version is not really correct but some implementations fail to
        // correctly negotiate TLS otherwise.
        protocolVersion = enabledProtocols.max;

        ProtocolVersion helloVersion = enabledProtocols.helloVersion;

        input = new HandshakeInStream(handshakeHash);

        if (conn != null) {
            output = new HandshakeOutStream(protocolVersion, helloVersion,
                                        handshakeHash, conn);
            conn.getAppInputStream().r.setHelloVersion(helloVersion);
        } else {
            output = new HandshakeOutStream(protocolVersion, helloVersion,
                                        handshakeHash, engine);
            engine.outputRecord.setHelloVersion(helloVersion);
        }

    }

    /**
     * Set cipherSuite and keyExchange to the given CipherSuite.
     * Does not perform any verification that this is a valid selection,
     * this must be done before calling this method.
     */
    void setCipherSuite(CipherSuite s) {
        this.cipherSuite = s;
        this.keyExchange = s.keyExchange;
    }

    /**
     * Check if the given ciphersuite is enabled and available.
     * (Enabled ciphersuites are always available unless the status has
     * changed due to change in JCE providers since it was enabled).
     * Does not check if the required server certificates are available.
     */
    boolean isEnabled(CipherSuite s) {
        return enabledCipherSuites.contains(s) && s.isAvailable();
    }

    /**
     * As long as handshaking has not started, we can
     * change whether session creations are allowed.
     *
     * Callers should do their own checking if handshaking
     * has started.
     */
    void setEnableSessionCreation(boolean newSessions) {
        enableNewSession = newSessions;
    }

    /**
     * Create a new read cipher and return it to caller.
     */
    CipherBox newReadCipher() throws NoSuchAlgorithmException {
        BulkCipher cipher = cipherSuite.cipher;
        CipherBox box;
        if (isClient) {
            box = cipher.newCipher(protocolVersion, svrWriteKey, svrWriteIV,
                                   false);
            svrWriteKey = null;
            svrWriteIV = null;
        } else {
            box = cipher.newCipher(protocolVersion, clntWriteKey, clntWriteIV,
                                   false);
            clntWriteKey = null;
            clntWriteIV = null;
        }
        return box;
    }

    /**
     * Create a new write cipher and return it to caller.
     */
    CipherBox newWriteCipher() throws NoSuchAlgorithmException {
        BulkCipher cipher = cipherSuite.cipher;
        CipherBox box;
        if (isClient) {
            box = cipher.newCipher(protocolVersion, clntWriteKey, clntWriteIV,
                                   true);
            clntWriteKey = null;
            clntWriteIV = null;
        } else {
            box = cipher.newCipher(protocolVersion, svrWriteKey, svrWriteIV,
                                   true);
            svrWriteKey = null;
            svrWriteIV = null;
        }
        return box;
    }

    /**
     * Create a new read MAC and return it to caller.
     */
    MAC newReadMAC() throws NoSuchAlgorithmException, InvalidKeyException {
        MacAlg macAlg = cipherSuite.macAlg;
        MAC mac;
        if (isClient) {
            mac = macAlg.newMac(protocolVersion, svrMacSecret);
            svrMacSecret = null;
        } else {
            mac = macAlg.newMac(protocolVersion, clntMacSecret);
            clntMacSecret = null;
        }
        return mac;
    }

    /**
     * Create a new write MAC and return it to caller.
     */
    MAC newWriteMAC() throws NoSuchAlgorithmException, InvalidKeyException {
        MacAlg macAlg = cipherSuite.macAlg;
        MAC mac;
        if (isClient) {
            mac = macAlg.newMac(protocolVersion, clntMacSecret);
            clntMacSecret = null;
        } else {
            mac = macAlg.newMac(protocolVersion, svrMacSecret);
            svrMacSecret = null;
        }
        return mac;
    }

    /*
     * Returns true iff the handshake sequence is done, so that
     * this freshly created session can become the current one.
     */
    boolean isDone() {
        return state == HandshakeMessage.ht_finished;
    }


    /*
     * Returns the session which was created through this
     * handshake sequence ... should be called after isDone()
     * returns true.
     */
    SSLSessionImpl getSession() {
        return session;
    }

    /*
     * This routine is fed SSL handshake records when they become available,
     * and processes messages found therein.
     */
    void process_record(InputRecord r, boolean expectingFinished)
            throws IOException {

        checkThrown();

        /*
         * Store the incoming handshake data, then see if we can
         * now process any completed handshake messages
         */
        input.incomingRecord(r);

        /*
         * We don't need to create a separate delegatable task
         * for finished messages.
         */
        if ((conn != null) || expectingFinished) {
            processLoop();
        } else {
            delegateTask(new PrivilegedExceptionAction<Void>() {
                public Void run() throws Exception {
                    processLoop();
                    return null;
                }
            });
        }
    }

    /*
     * On input, we hash messages one at a time since servers may need
     * to access an intermediate hash to validate a CertificateVerify
     * message.
     *
     * Note that many handshake messages can come in one record (and often
     * do, to reduce network resource utilization), and one message can also
     * require multiple records (e.g. very large Certificate messages).
     */
    void processLoop() throws IOException {

        while (input.available() > 0) {
            byte messageType;
            int messageLen;

            /*
             * See if we can read the handshake message header, and
             * then the entire handshake message.  If not, wait till
             * we can read and process an entire message.
             */
            input.mark(4);

            messageType = (byte)input.getInt8();
            messageLen = input.getInt24();

            if (input.available() < messageLen) {
                input.reset();
                return;
            }

            /*
             * Process the messsage.  We require
             * that processMessage() consumes the entire message.  In
             * lieu of explicit error checks (how?!) we assume that the
             * data will look like garbage on encoding/processing errors,
             * and that other protocol code will detect such errors.
             *
             * Note that digesting is normally deferred till after the
             * message has been processed, though to process at least the
             * client's Finished message (i.e. send the server's) we need
             * to acccelerate that digesting.
             *
             * Also, note that hello request messages are never hashed;
             * that includes the hello request header, too.
             */
            if (messageType == HandshakeMessage.ht_hello_request) {
                input.reset();
                processMessage(messageType, messageLen);
                input.ignore(4 + messageLen);
            } else {
                input.mark(messageLen);
                processMessage(messageType, messageLen);
                input.digestNow();
            }
        }
    }


    /**
     * Returns true iff the handshaker has sent any messages.
     * Server kickstarting is not as neat as it should be; we
     * need to create a new handshaker, this method lets us
     * know if we should.
     */
    boolean started() {
        return state >= 0;
    }


    /*
     * Used to kickstart the negotiation ... either writing a
     * ClientHello or a HelloRequest as appropriate, whichever
     * the subclass returns.  NOP if handshaking's already started.
     */
    void kickstart() throws IOException {
        if (state >= 0) {
            return;
        }
        HandshakeMessage m = getKickstartMessage();

        if (debug != null && Debug.isOn("handshake")) {
            m.print(System.out);
        }
        m.write(output);
        output.flush();

        state = m.messageType();
    }

    /**
     * Both client and server modes can start handshaking; but the
     * message they send to do so is different.
     */
    abstract HandshakeMessage getKickstartMessage() throws SSLException;

    /*
     * Client and Server side protocols are each driven though this
     * call, which processes a single message and drives the appropriate
     * side of the protocol state machine (depending on the subclass).
     */
    abstract void processMessage(byte messageType, int messageLen)
        throws IOException;

    /*
     * Most alerts in the protocol relate to handshaking problems.
     * Alerts are detected as the connection reads data.
     */
    abstract void handshakeAlert(byte description) throws SSLProtocolException;

    /*
     * Sends a change cipher spec message and updates the write side
     * cipher state so that future messages use the just-negotiated spec.
     */
    void sendChangeCipherSpec(Finished mesg, boolean lastMessage)
            throws IOException {

        output.flush(); // i.e. handshake data

        /*
         * The write cipher state is protected by the connection write lock
         * so we must grab it while making the change. We also
         * make sure no writes occur between sending the ChangeCipherSpec
         * message, installing the new cipher state, and sending the
         * Finished message.
         *
         * We already hold SSLEngine/SSLSocket "this" by virtue
         * of this being called from the readRecord code.
         */
        OutputRecord r;
        if (conn != null) {
            r = new OutputRecord(Record.ct_change_cipher_spec);
        } else {
            r = new EngineOutputRecord(Record.ct_change_cipher_spec, engine);
        }

        r.setVersion(protocolVersion);
        r.write(1);     // single byte of data

        if (conn != null) {
            conn.writeLock.lock();
            try {
                conn.writeRecord(r);
                conn.changeWriteCiphers();
                if (debug != null && Debug.isOn("handshake")) {
                    mesg.print(System.out);
                }
                mesg.write(output);
                output.flush();
            } finally {
                conn.writeLock.unlock();
            }
        } else {
            synchronized (engine.writeLock) {
                engine.writeRecord((EngineOutputRecord)r);
                engine.changeWriteCiphers();
                if (debug != null && Debug.isOn("handshake")) {
                    mesg.print(System.out);
                }
                mesg.write(output);

                if (lastMessage) {
                    output.setFinishedMsg();
                }
                output.flush();
            }
        }
    }

    /*
     * Single access point to key calculation logic.  Given the
     * pre-master secret and the nonces from client and server,
     * produce all the keying material to be used.
     */
    void calculateKeys(SecretKey preMasterSecret, ProtocolVersion version) {
        SecretKey master = calculateMasterSecret(preMasterSecret, version);
        session.setMasterSecret(master);
        calculateConnectionKeys(master);
    }


    /*
     * Calculate the master secret from its various components.  This is
     * used for key exchange by all cipher suites.
     *
     * The master secret is the catenation of three MD5 hashes, each
     * consisting of the pre-master secret and a SHA1 hash.  Those three
     * SHA1 hashes are of (different) constant strings, the pre-master
     * secret, and the nonces provided by the client and the server.
     */
    private SecretKey calculateMasterSecret(SecretKey preMasterSecret,
            ProtocolVersion requestedVersion) {
        TlsMasterSecretParameterSpec spec = new TlsMasterSecretParameterSpec
                (preMasterSecret, protocolVersion.major, protocolVersion.minor,
                clnt_random.random_bytes, svr_random.random_bytes);

        if (debug != null && Debug.isOn("keygen")) {
            HexDumpEncoder      dump = new HexDumpEncoder();

            System.out.println("SESSION KEYGEN:");

            System.out.println("PreMaster Secret:");
            printHex(dump, preMasterSecret.getEncoded());

            // Nonces are dumped with connection keygen, no
            // benefit to doing it twice
        }

        SecretKey masterSecret;
        try {
            KeyGenerator kg = JsseJce.getKeyGenerator("SunTlsMasterSecret");
            kg.init(spec);
            masterSecret = kg.generateKey();
        } catch (GeneralSecurityException e) {
            // For RSA premaster secrets, do not signal a protocol error
            // due to the Bleichenbacher attack. See comments further down.
            if (!preMasterSecret.getAlgorithm().equals("TlsRsaPremasterSecret")) {
                throw new ProviderException(e);
            }
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("RSA master secret generation error:");
                e.printStackTrace(System.out);
                System.out.println("Generating new random premaster secret");
            }
            preMasterSecret = RSAClientKeyExchange.generateDummySecret(protocolVersion);
            // recursive call with new premaster secret
            return calculateMasterSecret(preMasterSecret, null);
        }

        // if no version check requested (client side handshake),
        // or version information is not available (not an RSA premaster secret),
        // return master secret immediately.
        if ((requestedVersion == null) || !(masterSecret instanceof TlsMasterSecret)) {
            return masterSecret;
        }
        TlsMasterSecret tlsKey = (TlsMasterSecret)masterSecret;
        int major = tlsKey.getMajorVersion();
        int minor = tlsKey.getMinorVersion();
        if ((major < 0) || (minor < 0)) {
            return masterSecret;
        }

        // check if the premaster secret version is ok
        // the specification says that it must be the maximum version supported
        // by the client from its ClientHello message. However, many
        // implementations send the negotiated version, so accept both
        // NOTE that we may be comparing two unsupported version numbers in
        // the second case, which is why we cannot use object reference
        // equality in this special case
        ProtocolVersion premasterVersion = ProtocolVersion.valueOf(major, minor);
        boolean versionMismatch = (premasterVersion != protocolVersion) &&
                                  (premasterVersion.v != requestedVersion.v);


        if (versionMismatch == false) {
            // check passed, return key
            return masterSecret;
        }

        // Due to the Bleichenbacher attack, do not signal a protocol error.
        // Generate a random premaster secret and continue with the handshake,
        // which will fail when verifying the finished messages.
        // For more information, see comments in PreMasterSecret.
        if (debug != null && Debug.isOn("handshake")) {
            System.out.println("RSA PreMasterSecret version error: expected"
                + protocolVersion + " or " + requestedVersion + ", decrypted: "
                + premasterVersion);
            System.out.println("Generating new random premaster secret");
        }
        preMasterSecret = RSAClientKeyExchange.generateDummySecret(protocolVersion);
        // recursive call with new premaster secret
        return calculateMasterSecret(preMasterSecret, null);
    }

    /*
     * Calculate the keys needed for this connection, once the session's
     * master secret has been calculated.  Uses the master key and nonces;
     * the amount of keying material generated is a function of the cipher
     * suite that's been negotiated.
     *
     * This gets called both on the "full handshake" (where we exchanged
     * a premaster secret and started a new session) as well as on the
     * "fast handshake" (where we just resumed a pre-existing session).
     */
    void calculateConnectionKeys(SecretKey masterKey) {
        /*
         * For both the read and write sides of the protocol, we use the
         * master to generate MAC secrets and cipher keying material.  Block
         * ciphers need initialization vectors, which we also generate.
         *
         * First we figure out how much keying material is needed.
         */
        int hashSize = cipherSuite.macAlg.size;
        boolean is_exportable = cipherSuite.exportable;
        BulkCipher cipher = cipherSuite.cipher;
        int keySize = cipher.keySize;
        int ivSize = cipher.ivSize;
        int expandedKeySize = is_exportable ? cipher.expandedKeySize : 0;

        TlsKeyMaterialParameterSpec spec = new TlsKeyMaterialParameterSpec
            (masterKey, protocolVersion.major, protocolVersion.minor,
            clnt_random.random_bytes, svr_random.random_bytes,
            cipher.algorithm, cipher.keySize, expandedKeySize,
            cipher.ivSize, hashSize);

        try {
            KeyGenerator kg = JsseJce.getKeyGenerator("SunTlsKeyMaterial");
            kg.init(spec);
            TlsKeyMaterialSpec keySpec = (TlsKeyMaterialSpec)kg.generateKey();

            clntWriteKey = keySpec.getClientCipherKey();
            svrWriteKey = keySpec.getServerCipherKey();

            clntWriteIV = keySpec.getClientIv();
            svrWriteIV = keySpec.getServerIv();

            clntMacSecret = keySpec.getClientMacKey();
            svrMacSecret = keySpec.getServerMacKey();
        } catch (GeneralSecurityException e) {
            throw new ProviderException(e);
        }

        //
        // Dump the connection keys as they're generated.
        //
        if (debug != null && Debug.isOn("keygen")) {
            synchronized (System.out) {
                HexDumpEncoder  dump = new HexDumpEncoder();

                System.out.println("CONNECTION KEYGEN:");

                // Inputs:
                System.out.println("Client Nonce:");
                printHex(dump, clnt_random.random_bytes);
                System.out.println("Server Nonce:");
                printHex(dump, svr_random.random_bytes);
                System.out.println("Master Secret:");
                printHex(dump, masterKey.getEncoded());

                // Outputs:
                System.out.println("Client MAC write Secret:");
                printHex(dump, clntMacSecret.getEncoded());
                System.out.println("Server MAC write Secret:");
                printHex(dump, svrMacSecret.getEncoded());

                if (clntWriteKey != null) {
                    System.out.println("Client write key:");
                    printHex(dump, clntWriteKey.getEncoded());
                    System.out.println("Server write key:");
                    printHex(dump, svrWriteKey.getEncoded());
                } else {
                    System.out.println("... no encryption keys used");
                }

                if (clntWriteIV != null) {
                    System.out.println("Client write IV:");
                    printHex(dump, clntWriteIV.getIV());
                    System.out.println("Server write IV:");
                    printHex(dump, svrWriteIV.getIV());
                } else {
                    System.out.println("... no IV used for this cipher");
                }
                System.out.flush();
            }
        }
    }

    private static void printHex(HexDumpEncoder dump, byte[] bytes) {
        if (bytes == null) {
            System.out.println("(key bytes not available)");
        } else {
            try {
                dump.encodeBuffer(bytes, System.out);
            } catch (IOException e) {
                // just for debugging, ignore this
            }
        }
    }

    /**
     * Throw an SSLException with the specified message and cause.
     * Shorthand until a new SSLException constructor is added.
     * This method never returns.
     */
    static void throwSSLException(String msg, Throwable cause)
            throws SSLException {
        SSLException e = new SSLException(msg);
        e.initCause(cause);
        throw e;
    }


    /*
     * Implement a simple task delegator.
     *
     * We are currently implementing this as a single delegator, may
     * try for parallel tasks later.  Client Authentication could
     * benefit from this, where ClientKeyExchange/CertificateVerify
     * could be carried out in parallel.
     */
    class DelegatedTask<E> implements Runnable {

        private PrivilegedExceptionAction<E> pea;

        DelegatedTask(PrivilegedExceptionAction<E> pea) {
            this.pea = pea;
        }

        public void run() {
            synchronized (engine) {
                try {
                    AccessController.doPrivileged(pea, engine.getAcc());
                } catch (PrivilegedActionException pae) {
                    thrown = pae.getException();
                } catch (RuntimeException rte) {
                    thrown = rte;
                }
                delegatedTask = null;
                taskDelegated = false;
            }
        }
    }

    private <T> void delegateTask(PrivilegedExceptionAction<T> pea) {
        delegatedTask = new DelegatedTask<T>(pea);
        taskDelegated = false;
        thrown = null;
    }

    DelegatedTask getTask() {
        if (!taskDelegated) {
            taskDelegated = true;
            return delegatedTask;
        } else {
            return null;
        }
    }

    /*
     * See if there are any tasks which need to be delegated
     *
     * Locked by SSLEngine.this.
     */
    boolean taskOutstanding() {
        return (delegatedTask != null);
    }

    /*
     * The previous caller failed for some reason, report back the
     * Exception.  We won't worry about Error's.
     *
     * Locked by SSLEngine.this.
     */
    void checkThrown() throws SSLException {
        synchronized (thrownLock) {
            if (thrown != null) {

                String msg = thrown.getMessage();

                if (msg == null) {
                    msg = "Delegated task threw Exception/Error";
                }

                /*
                 * See what the underlying type of exception is.  We should
                 * throw the same thing.  Chain thrown to the new exception.
                 */
                Exception e = thrown;
                thrown = null;

                if (e instanceof RuntimeException) {
                    throw (RuntimeException)
                        new RuntimeException(msg).initCause(e);
                } else if (e instanceof SSLHandshakeException) {
                    throw (SSLHandshakeException)
                        new SSLHandshakeException(msg).initCause(e);
                } else if (e instanceof SSLKeyException) {
                    throw (SSLKeyException)
                        new SSLKeyException(msg).initCause(e);
                } else if (e instanceof SSLPeerUnverifiedException) {
                    throw (SSLPeerUnverifiedException)
                        new SSLPeerUnverifiedException(msg).initCause(e);
                } else if (e instanceof SSLProtocolException) {
                    throw (SSLProtocolException)
                        new SSLProtocolException(msg).initCause(e);
                } else {
                    /*
                     * If it's SSLException or any other Exception,
                     * we'll wrap it in an SSLException.
                     */
                    throw (SSLException)
                        new SSLException(msg).initCause(e);
                }
            }
        }
    }
}
