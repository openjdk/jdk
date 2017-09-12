/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.krb5.internal.ssl;

import sun.security.ssl.ClientKeyExchange;
import sun.security.ssl.Debug;
import sun.security.ssl.ClientKeyExchangeService;
import sun.security.ssl.HandshakeOutStream;

import sun.security.jgss.GSSCaller;
import sun.security.jgss.krb5.Krb5Util;
import sun.security.jgss.krb5.ServiceCreds;
import sun.security.krb5.EncryptedData;
import sun.security.krb5.EncryptionKey;
import sun.security.krb5.KrbException;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.EncTicketPart;
import sun.security.krb5.internal.Ticket;
import sun.security.krb5.internal.crypto.KeyUsage;
import sun.security.ssl.ProtocolVersion;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.kerberos.KeyTab;
import javax.security.auth.kerberos.ServicePermission;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
import java.util.Set;

/**
 * The provider for TLS_KRB_ cipher suites.
 *
 * @since 9
 */
public class Krb5KeyExchangeService implements ClientKeyExchangeService {

    public static final Debug debug = Debug.getInstance("ssl");

    @Override
    public String[] supported() {
        return new String[] { "KRB5", "KRB5_EXPORT" };
    }

    @Override
    public Object getServiceCreds(AccessControlContext acc) {
        try {
            ServiceCreds serviceCreds = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<ServiceCreds>)
                            () -> Krb5Util.getServiceCreds(
                                    GSSCaller.CALLER_SSL_SERVER, null, acc));
            if (serviceCreds == null) {
                if (debug != null && Debug.isOn("handshake")) {
                    System.out.println("Kerberos serviceCreds not available");
                }
                return null;
            }
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("Using Kerberos creds");
            }
            String serverPrincipal = serviceCreds.getName();
            if (serverPrincipal != null) {
                // When service is bound, we check ASAP. Otherwise,
                // will check after client request is received
                // in in Kerberos ClientKeyExchange
                SecurityManager sm = System.getSecurityManager();
                try {
                    if (sm != null) {
                        // Eliminate dependency on ServicePermission
                        sm.checkPermission(new ServicePermission(
                                serverPrincipal, "accept"), acc);
                    }
                } catch (SecurityException se) {
                    if (debug != null && Debug.isOn("handshake")) {
                        System.out.println("Permission to access Kerberos"
                                + " secret key denied");
                    }
                    return null;
                }
            }
            return serviceCreds;
        } catch (PrivilegedActionException e) {
            // Likely exception here is LoginException
            if (debug != null && Debug.isOn("handshake")) {
                System.out.println("Attempt to obtain Kerberos key failed: "
                        + e.toString());
            }
            return null;
        }
    }

    @Override
    public String getServiceHostName(Principal principal) {
        if (principal == null) {
            return null;
        }
        String hostName = null;
        try {
            PrincipalName princName =
                    new PrincipalName(principal.getName(),
                            PrincipalName.KRB_NT_SRV_HST);
            String[] nameParts = princName.getNameStrings();
            if (nameParts.length >= 2) {
                hostName = nameParts[1];
            }
        } catch (Exception e) {
            // ignore
        }
        return hostName;
    }


    @Override
    public boolean isRelated(boolean isClient,
            AccessControlContext acc, Principal p) {

        if (p == null) return false;
        try {
            Subject subject = AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Subject>)
                            () -> Krb5Util.getSubject(
                                    isClient ? GSSCaller.CALLER_SSL_CLIENT
                                            : GSSCaller.CALLER_SSL_SERVER,
                                    acc));
            if (subject == null) {
                if (debug != null && Debug.isOn("session")) {
                    System.out.println("Kerberos credentials are" +
                            " not present in the current Subject;" +
                            " check if " +
                            " javax.security.auth.useSubjectAsCreds" +
                            " system property has been set to false");
                }
                return false;
            }
            Set<Principal> principals =
                    subject.getPrincipals(Principal.class);
            if (principals.contains(p)) {
                // bound to this principal
                return true;
            } else {
                if (isClient) {
                    return false;
                } else {
                    for (KeyTab pc : subject.getPrivateCredentials(KeyTab.class)) {
                        if (!pc.isBound()) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        } catch (PrivilegedActionException pae) {
            if (debug != null && Debug.isOn("session")) {
                System.out.println("Attempt to obtain" +
                        " subject failed! " + pae);
            }
            return false;
        }

    }

    public ClientKeyExchange createClientExchange(
            String serverName, AccessControlContext acc,
            ProtocolVersion protocolVerson, SecureRandom rand) throws IOException {
        return new ExchangerImpl(serverName, acc, protocolVerson, rand);
    }

    public ClientKeyExchange createServerExchange(
            ProtocolVersion protocolVersion, ProtocolVersion clientVersion,
            SecureRandom rand, byte[] encodedTicket, byte[] encrypted,
            AccessControlContext acc, Object serviceCreds) throws IOException {
        return new ExchangerImpl(protocolVersion, clientVersion, rand,
                encodedTicket, encrypted, acc, serviceCreds);
    }

    static class ExchangerImpl extends ClientKeyExchange {

        final private KerberosPreMasterSecret preMaster;
        final private byte[] encodedTicket;
        final private KerberosPrincipal peerPrincipal;
        final private KerberosPrincipal localPrincipal;

        @Override
        public int messageLength() {
            return encodedTicket.length + preMaster.getEncrypted().length + 6;
        }

        @Override
        public void send(HandshakeOutStream s) throws IOException {
            s.putBytes16(encodedTicket);
            s.putBytes16(null);
            s.putBytes16(preMaster.getEncrypted());
        }

        @Override
        public void print(PrintStream s) throws IOException {
            s.println("*** ClientKeyExchange, Kerberos");

            if (debug != null && Debug.isOn("verbose")) {
                Debug.println(s, "Kerberos service ticket", encodedTicket);
                Debug.println(s, "Random Secret", preMaster.getUnencrypted());
                Debug.println(s, "Encrypted random Secret", preMaster.getEncrypted());
            }
        }

        ExchangerImpl(String serverName, AccessControlContext acc,
                ProtocolVersion protocolVersion, SecureRandom rand) throws IOException {

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

        ExchangerImpl(
                ProtocolVersion protocolVersion, ProtocolVersion clientVersion, SecureRandom rand,
                byte[] encodedTicket, byte[] encrypted,
                AccessControlContext acc, Object serviceCreds) throws IOException {

            // Read ticket
            this.encodedTicket = encodedTicket;

            if (debug != null && Debug.isOn("verbose")) {
                Debug.println(System.out,
                        "encoded Kerberos service ticket", encodedTicket);
            }

            EncryptionKey sessionKey = null;
            KerberosPrincipal tmpPeer = null;
            KerberosPrincipal tmpLocal = null;

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
                            sm.checkPermission(new ServicePermission(
                                    ticketSname.toString(), "accept"), acc);
                        }
                    } catch (SecurityException se) {
                        serviceCreds = null;
                        // Do not destroy keys. Will affect Subject
                        if (debug != null && Debug.isOn("handshake")) {
                            System.out.println("Permission to access Kerberos"
                                    + " secret key denied");
                            se.printStackTrace(System.out);
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
                tmpPeer = new KerberosPrincipal(encTicketPart.cname.getName());
                tmpLocal = new KerberosPrincipal(ticketSname.getName());

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

            //input.getBytes16();   // XXX Read and ignore authenticator

            if (sessionKey != null) {
                preMaster = new KerberosPreMasterSecret(protocolVersion,
                        clientVersion, rand, encrypted, sessionKey);
            } else {
                // Generate bogus premaster secret
                preMaster = new KerberosPreMasterSecret(clientVersion, rand);
            }

            peerPrincipal = tmpPeer;
            localPrincipal = tmpLocal;
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
        public SecretKey clientKeyExchange() {
            byte[] secretBytes = preMaster.getUnencrypted();
            return new SecretKeySpec(secretBytes, "TlsPremasterSecret");
        }

        @Override
        public Principal getPeerPrincipal() {
            return peerPrincipal;
        }

        @Override
        public Principal getLocalPrincipal() {
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
}
