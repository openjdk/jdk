/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl.krb5;

import java.io.IOException;
import java.io.PrintStream;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.security.SecureRandom;
import java.net.InetAddress;
import java.security.PrivilegedAction;

import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.ServicePermission;
import sun.security.jgss.GSSCaller;

import sun.security.krb5.EncryptionKey;
import sun.security.krb5.EncryptedData;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.Ticket;
import sun.security.krb5.internal.EncTicketPart;
import sun.security.krb5.internal.crypto.KeyUsage;

import sun.security.jgss.krb5.Krb5Util;
import sun.security.jgss.krb5.ServiceCreds;
import sun.security.krb5.KrbException;
import sun.security.krb5.internal.Krb5;

import sun.security.ssl.Debug;
import sun.security.ssl.HandshakeInStream;
import sun.security.ssl.HandshakeOutStream;
import sun.security.ssl.Krb5Helper;
import sun.security.ssl.ProtocolVersion;

/**
 * This is Kerberos option in the client key exchange message
 * (CLIENT -> SERVER). It holds the Kerberos ticket and the encrypted
 * premaster secret encrypted with the session key sealed in the ticket.
 * From RFC 2712:
 *  struct
 *  {
 *    opaque Ticket;
 *    opaque authenticator;            // optional
 *    opaque EncryptedPreMasterSecret; // encrypted with the session key
 *                                     // which is sealed in the ticket
 *  } KerberosWrapper;
 *
 *
 * Ticket and authenticator are encrypted as per RFC 1510 (in ASN.1)
 * Encrypted pre-master secret has the same structure as it does for RSA
 * except for Kerberos, the encryption key is the session key instead of
 * the RSA public key.
 *
 * XXX authenticator currently ignored
 *
 */
public final class KerberosClientKeyExchangeImpl
    extends sun.security.ssl.KerberosClientKeyExchange {

    private KerberosPreMasterSecret preMaster;
    private byte[] encodedTicket;
    private KerberosPrincipal peerPrincipal;
    private KerberosPrincipal localPrincipal;

    public KerberosClientKeyExchangeImpl() {
    }

    /**
     * Creates an instance of KerberosClientKeyExchange consisting of the
     * Kerberos service ticket, authenticator and encrypted premaster secret.
     * Called by client handshaker.
     *
     * @param serverName name of server with which to do handshake;
     *             this is used to get the Kerberos service ticket
     * @param protocolVersion Maximum version supported by client (i.e,
     *          version it requested in client hello)
     * @param rand random number generator to use for generating pre-master
     *          secret
     */
    @Override
    public void init(String serverName,
        AccessControlContext acc, ProtocolVersion protocolVersion,
        SecureRandom rand) throws IOException {

         // Get service ticket
         KerberosTicket ticket = getServiceTicket(serverName, acc);
         encodedTicket = ticket.getEncoded();

         // Record the Kerberos principals
         peerPrincipal = ticket.getServer();
         localPrincipal = ticket.getClient();

         // Optional authenticator, encrypted using session key,
         // currently ignored

         // Generate premaster secret and encrypt it using session key
         EncryptionKey sessionKey = new EncryptionKey(
                                        ticket.getSessionKeyType(),
                                        ticket.getSessionKey().getEncoded());

         preMaster = new KerberosPreMasterSecret(protocolVersion,
             rand, sessionKey);
    }

    /**
     * Creates an instance of KerberosClientKeyExchange from its ASN.1 encoding.
     * Used by ServerHandshaker to verify and obtain premaster secret.
     *
     * @param protocolVersion current protocol version
     * @param clientVersion version requested by client in its ClientHello;
     *          used by premaster secret version check
     * @param rand random number generator used for generating random
     *          premaster secret if ticket and/or premaster verification fails
     * @param input inputstream from which to get ASN.1-encoded KerberosWrapper
     * @param acc the AccessControlContext of the handshaker
     * @param serviceCreds server's creds
     */
    @Override
    public void init(ProtocolVersion protocolVersion,
        ProtocolVersion clientVersion,
        SecureRandom rand, HandshakeInStream input, AccessControlContext acc, Object serviceCreds)
        throws IOException {

        // Read ticket
        encodedTicket = input.getBytes16();

        if (debug != null && Debug.isOn("verbose")) {
            Debug.println(System.out,
                "encoded Kerberos service ticket", encodedTicket);
        }

        EncryptionKey sessionKey = null;

        try {
            Ticket t = new Ticket(encodedTicket);

            EncryptedData encPart = t.encPart;
            PrincipalName ticketSname = t.sname;

            final ServiceCreds creds = (ServiceCreds)serviceCreds;
            final KerberosPrincipal princ =
                    new KerberosPrincipal(ticketSname.toString());

            // For bound service, permission already checked at setup
            if (creds.getName() == null) {
                SecurityManager sm = System.getSecurityManager();
                try {
                    if (sm != null) {
                        // Eliminate dependency on ServicePermission
                        sm.checkPermission(Krb5Helper.getServicePermission(
                                ticketSname.toString(), "accept"), acc);
                    }
                } catch (SecurityException se) {
                    serviceCreds = null;
                    // Do not destroy keys. Will affect Subject
                    if (debug != null && Debug.isOn("handshake")) {
                        System.out.println("Permission to access Kerberos"
                                + " secret key denied");
                    }
                    throw new IOException("Kerberos service not allowedy");
                }
            }
            KerberosKey[] serverKeys = AccessController.doPrivileged(
                    new PrivilegedAction<KerberosKey[]>() {
                        @Override
                        public KerberosKey[] run() {
                            return creds.getKKeys(princ);
                        }
                    });
            if (serverKeys.length == 0) {
                throw new IOException("Found no key for " + princ +
                        (creds.getName() == null ? "" :
                        (", this keytab is for " + creds.getName() + " only")));
            }

            /*
             * permission to access and use the secret key of the Kerberized
             * "host" service is done in ServerHandshaker.getKerberosKeys()
             * to ensure server has the permission to use the secret key
             * before promising the client
             */

            // See if we have the right key to decrypt the ticket to get
            // the session key.
            int encPartKeyType = encPart.getEType();
            Integer encPartKeyVersion = encPart.getKeyVersionNumber();
            KerberosKey dkey = null;
            try {
                dkey = findKey(encPartKeyType, encPartKeyVersion, serverKeys);
            } catch (KrbException ke) { // a kvno mismatch
                throw new IOException(
                        "Cannot find key matching version number", ke);
            }
            if (dkey == null) {
                // %%% Should print string repr of etype
                throw new IOException("Cannot find key of appropriate type" +
                        " to decrypt ticket - need etype " + encPartKeyType);
            }

            EncryptionKey secretKey = new EncryptionKey(
                encPartKeyType,
                dkey.getEncoded());

            // Decrypt encPart using server's secret key
            byte[] bytes = encPart.decrypt(secretKey, KeyUsage.KU_TICKET);

            // Reset data stream after decryption, remove redundant bytes
            byte[] temp = encPart.reset(bytes);
            EncTicketPart encTicketPart = new EncTicketPart(temp);

            // Record the Kerberos Principals
            peerPrincipal =
                new KerberosPrincipal(encTicketPart.cname.getName());
            localPrincipal = new KerberosPrincipal(ticketSname.getName());

            sessionKey = encTicketPart.key;

            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("server principal: " + ticketSname);
                System.out.println("cname: " + encTicketPart.cname.toString());
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("KerberosWrapper error getting session key,"
                        + " generating random secret (" + e.getMessage() + ")");
            }
            sessionKey = null;
        }

        input.getBytes16();   // XXX Read and ignore authenticator

        if (sessionKey != null) {
            preMaster = new KerberosPreMasterSecret(protocolVersion,
                clientVersion, rand, input, sessionKey);
        } else {
            // Generate bogus premaster secret
            preMaster = new KerberosPreMasterSecret(clientVersion, rand);
        }
    }

    @Override
    public int messageLength() {
        return (6 + encodedTicket.length + preMaster.getEncrypted().length);
    }

    @Override
    public void send(HandshakeOutStream s) throws IOException {
        s.putBytes16(encodedTicket);
        s.putBytes16(null); // XXX no authenticator
        s.putBytes16(preMaster.getEncrypted());
    }

    @Override
    public void print(PrintStream s) throws IOException {
        s.println("*** ClientKeyExchange, Kerberos");

        if (debug != null && Debug.isOn("verbose")) {
            Debug.println(s, "Kerberos service ticket", encodedTicket);
            Debug.println(s, "Random Secret", preMaster.getUnencrypted());
            Debug.println(s, "Encrypted random Secret",
                preMaster.getEncrypted());
        }
    }

    // Similar to sun.security.jgss.krb5.Krb5InitCredenetial/Krb5Context
    private static KerberosTicket getServiceTicket(String serverName,
        final AccessControlContext acc) throws IOException {

        if ("localhost".equals(serverName) ||
                "localhost.localdomain".equals(serverName)) {

            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("Get the local hostname");
            }
            String localHost = java.security.AccessController.doPrivileged(
                new java.security.PrivilegedAction<String>() {
                public String run() {
                    try {
                        return InetAddress.getLocalHost().getHostName();
                    } catch (java.net.UnknownHostException e) {
                        if (debug != null && Debug.isOn("handshake")) {
                            System.out.println("Warning,"
                                + " cannot get the local hostname: "
                                + e.getMessage());
                        }
                        return null;
                    }
                }
            });
            if (localHost != null) {
                serverName = localHost;
            }
        }

        // Resolve serverName (possibly in IP addr form) to Kerberos principal
        // name for service with hostname
        String serviceName = "host/" + serverName;
        PrincipalName principal;
        try {
            principal = new PrincipalName(serviceName,
                                PrincipalName.KRB_NT_SRV_HST);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            IOException ioe = new IOException("Invalid service principal" +
                                " name: " + serviceName);
            ioe.initCause(e);
            throw ioe;
        }
        String realm = principal.getRealmAsString();

        final String serverPrincipal = principal.toString();
        final String tgsPrincipal = "krbtgt/" + realm + "@" + realm;
        final String clientPrincipal = null;  // use default


        // check permission to obtain a service ticket to initiate a
        // context with the "host" service
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
           sm.checkPermission(new ServicePermission(serverPrincipal,
                                "initiate"), acc);
        }

        try {
            KerberosTicket ticket = AccessController.doPrivileged(
                new PrivilegedExceptionAction<KerberosTicket>() {
                public KerberosTicket run() throws Exception {
                    return Krb5Util.getTicketFromSubjectAndTgs(
                        GSSCaller.CALLER_SSL_CLIENT,
                        clientPrincipal, serverPrincipal,
                        tgsPrincipal, acc);
                        }});

            if (ticket == null) {
                throw new IOException("Failed to find any kerberos service" +
                        " ticket for " + serverPrincipal);
            }
            return ticket;
        } catch (PrivilegedActionException e) {
            IOException ioe = new IOException(
                "Attempt to obtain kerberos service ticket for " +
                        serverPrincipal + " failed!");
            ioe.initCause(e);
            throw ioe;
        }
    }

    @Override
    public byte[] getUnencryptedPreMasterSecret() {
        return preMaster.getUnencrypted();
    }

    @Override
    public KerberosPrincipal getPeerPrincipal() {
        return peerPrincipal;
    }

    @Override
    public KerberosPrincipal getLocalPrincipal() {
        return localPrincipal;
    }

    /**
     * Determines if a kvno matches another kvno. Used in the method
     * findKey(etype, version, keys). Always returns true if either input
     * is null or zero, in case any side does not have kvno info available.
     *
     * Note: zero is included because N/A is not a legal value for kvno
     * in javax.security.auth.kerberos.KerberosKey. Therefore, the info
     * that the kvno is N/A might be lost when converting between
     * EncryptionKey and KerberosKey.
     */
    private static boolean versionMatches(Integer v1, int v2) {
        if (v1 == null || v1 == 0 || v2 == 0) {
            return true;
        }
        return v1.equals(v2);
    }

    private static KerberosKey findKey(int etype, Integer version,
            KerberosKey[] keys) throws KrbException {
        int ktype;
        boolean etypeFound = false;

        // When no matched kvno is found, returns tke key of the same
        // etype with the highest kvno
        int kvno_found = 0;
        KerberosKey key_found = null;

        for (int i = 0; i < keys.length; i++) {
            ktype = keys[i].getKeyType();
            if (etype == ktype) {
                int kv = keys[i].getVersionNumber();
                etypeFound = true;
                if (versionMatches(version, kv)) {
                    return keys[i];
                } else if (kv > kvno_found) {
                    key_found = keys[i];
                    kvno_found = kv;
                }
            }
        }
        // Key not found.
        // %%% kludge to allow DES keys to be used for diff etypes
        if ((etype == EncryptedData.ETYPE_DES_CBC_CRC ||
            etype == EncryptedData.ETYPE_DES_CBC_MD5)) {
            for (int i = 0; i < keys.length; i++) {
                ktype = keys[i].getKeyType();
                if (ktype == EncryptedData.ETYPE_DES_CBC_CRC ||
                        ktype == EncryptedData.ETYPE_DES_CBC_MD5) {
                    int kv = keys[i].getVersionNumber();
                    etypeFound = true;
                    if (versionMatches(version, kv)) {
                        return new KerberosKey(keys[i].getPrincipal(),
                            keys[i].getEncoded(),
                            etype,
                            kv);
                    } else if (kv > kvno_found) {
                        key_found = new KerberosKey(keys[i].getPrincipal(),
                                keys[i].getEncoded(),
                                etype,
                                kv);
                        kvno_found = kv;
                    }
                }
            }
        }
        if (etypeFound) {
            return key_found;
        }
        return null;
    }
}
