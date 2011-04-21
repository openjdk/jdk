/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.jgss.krb5;

import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KeyTab;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.security.AccessControlContext;
import sun.security.jgss.GSSUtil;
import sun.security.jgss.GSSCaller;

import sun.security.krb5.Credentials;
import sun.security.krb5.EncryptionKey;
import sun.security.krb5.KrbException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import sun.misc.SharedSecrets;
import sun.security.krb5.PrincipalName;
/**
 * Utilities for obtaining and converting Kerberos tickets.
 *
 */
public class Krb5Util {

    static final boolean DEBUG =
        java.security.AccessController.doPrivileged(
            new sun.security.action.GetBooleanAction
            ("sun.security.krb5.debug")).booleanValue();

    /**
     * Default constructor
     */
    private Krb5Util() {  // Cannot create one of these
    }

    /**
     * Retrieve the service ticket for serverPrincipal from caller's Subject
     * or from Subject obtained by logging in, or if not found, via the
     * Ticket Granting Service using the TGT obtained from the Subject.
     *
     * Caller must have permission to:
     *    - access and update Subject's private credentials
     *    - create LoginContext
     *    - read the auth.login.defaultCallbackHandler security property
     *
     * NOTE: This method is used by JSSE Kerberos Cipher Suites
     */
    public static KerberosTicket getTicketFromSubjectAndTgs(GSSCaller caller,
        String clientPrincipal, String serverPrincipal, String tgsPrincipal,
        AccessControlContext acc)
        throws LoginException, KrbException, IOException {

        // 1. Try to find service ticket in acc subject
        Subject accSubj = Subject.getSubject(acc);
        KerberosTicket ticket = SubjectComber.find(accSubj,
            serverPrincipal, clientPrincipal, KerberosTicket.class);

        if (ticket != null) {
            return ticket;  // found it
        }

        Subject loginSubj = null;
        if (!GSSUtil.useSubjectCredsOnly(caller)) {
            // 2. Try to get ticket from login
            try {
                loginSubj = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
                ticket = SubjectComber.find(loginSubj,
                    serverPrincipal, clientPrincipal, KerberosTicket.class);
                if (ticket != null) {
                    return ticket; // found it
                }
            } catch (LoginException e) {
                // No login entry to use
                // ignore and continue
            }
        }

        // Service ticket not found in subject or login
        // Try to get TGT to acquire service ticket

        // 3. Try to get TGT from acc subject
        KerberosTicket tgt = SubjectComber.find(accSubj,
            tgsPrincipal, clientPrincipal, KerberosTicket.class);

        boolean fromAcc;
        if (tgt == null && loginSubj != null) {
            // 4. Try to get TGT from login subject
            tgt = SubjectComber.find(loginSubj,
                tgsPrincipal, clientPrincipal, KerberosTicket.class);
            fromAcc = false;
        } else {
            fromAcc = true;
        }

        // 5. Try to get service ticket using TGT
        if (tgt != null) {
            Credentials tgtCreds = ticketToCreds(tgt);
            Credentials serviceCreds = Credentials.acquireServiceCreds(
                        serverPrincipal, tgtCreds);
            if (serviceCreds != null) {
                ticket = credsToTicket(serviceCreds);

                // Store service ticket in acc's Subject
                if (fromAcc && accSubj != null && !accSubj.isReadOnly()) {
                    accSubj.getPrivateCredentials().add(ticket);
                }
            }
        }
        return ticket;
    }

    /**
     * Retrieves the ticket corresponding to the client/server principal
     * pair from the Subject in the specified AccessControlContext.
     * If the ticket can not be found in the Subject, and if
     * useSubjectCredsOnly is false, then obtain ticket from
     * a LoginContext.
     */
    static KerberosTicket getTicket(GSSCaller caller,
        String clientPrincipal, String serverPrincipal,
        AccessControlContext acc) throws LoginException {

        // Try to get ticket from acc's Subject
        Subject accSubj = Subject.getSubject(acc);
        KerberosTicket ticket =
            SubjectComber.find(accSubj, serverPrincipal, clientPrincipal,
                  KerberosTicket.class);

        // Try to get ticket from Subject obtained from GSSUtil
        if (ticket == null && !GSSUtil.useSubjectCredsOnly(caller)) {
            Subject subject = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
            ticket = SubjectComber.find(subject,
                serverPrincipal, clientPrincipal, KerberosTicket.class);
        }
        return ticket;
    }

    /**
     * Retrieves the caller's Subject, or Subject obtained by logging in
     * via the specified caller.
     *
     * Caller must have permission to:
     *    - access the Subject
     *    - create LoginContext
     *    - read the auth.login.defaultCallbackHandler security property
     *
     * NOTE: This method is used by JSSE Kerberos Cipher Suites
     */
    public static Subject getSubject(GSSCaller caller,
        AccessControlContext acc) throws LoginException {

        // Try to get the Subject from acc
        Subject subject = Subject.getSubject(acc);

        // Try to get Subject obtained from GSSUtil
        if (subject == null && !GSSUtil.useSubjectCredsOnly(caller)) {
            subject = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
        }
        return subject;
    }

    // A special KerberosKey, used as keys read from a KeyTab object.
    // Each time new keys are read from KeyTab objects in the private
    // credentials set, old ones are removed and new ones added.
    public static class KeysFromKeyTab extends KerberosKey {
        public KeysFromKeyTab(KerberosKey key) {
            super(key.getPrincipal(), key.getEncoded(),
                    key.getKeyType(), key.getVersionNumber());
        }
    }

    /**
     * Credentials of a service, the private secret to authenticate its
     * identity, which can be:
     *   1. Some KerberosKeys (generated from password)
     *   2. A KeyTab (for a typical service)
     *   3. A TGT (for a user2user service. Not supported yet)
     *
     * Note that some creds can coexist. For example, a user2user service
     * can use its keytab (or keys) if the client can successfully obtain a
     * normal service ticket, otherwise, it can uses the TGT (actually, the
     * session key of the TGT) if the client can only acquire a service ticket
     * of ENC-TKT-IN-SKEY style.
     */
    public static class ServiceCreds {
        private KerberosPrincipal kp;
        private List<KeyTab> ktabs;
        private List<KerberosKey> kk;
        private Subject subj;
        //private KerberosTicket tgt;   // user2user, not supported yet

        private static ServiceCreds getInstance(
                Subject subj, String serverPrincipal) {

            ServiceCreds sc = new ServiceCreds();
            sc.subj = subj;

            for (KerberosPrincipal p: subj.getPrincipals(KerberosPrincipal.class)) {
                if (serverPrincipal == null ||
                        p.getName().equals(serverPrincipal)) {
                    sc.kp = p;
                    serverPrincipal = p.getName();
                    break;
                }
            }
            if (sc.kp == null) {
                // Compatibility with old behavior: even when there is no
                // KerberosPrincipal, we can find one from KerberosKeys
                List<KerberosKey> keys = SubjectComber.findMany(
                        subj, null, null, KerberosKey.class);
                if (!keys.isEmpty()) {
                    sc.kp = keys.get(0).getPrincipal();
                    serverPrincipal = sc.kp.getName();
                    if (DEBUG) {
                        System.out.println(">>> ServiceCreds: no kp?"
                                + " find one from kk: " + serverPrincipal);
                    }
                } else {
                    return null;
                }
            }
            sc.ktabs = SubjectComber.findMany(
                        subj, null, null, KeyTab.class);
            sc.kk = SubjectComber.findMany(
                        subj, serverPrincipal, null, KerberosKey.class);
            if (sc.ktabs.isEmpty() && sc.kk.isEmpty()) {
                return null;
            }
            return sc;
        }

        public String getName() {
            return kp.getName();
        }

        public KerberosKey[] getKKeys() {
            if (ktabs.isEmpty()) {
                return kk.toArray(new KerberosKey[kk.size()]);
            } else {
                List<KerberosKey> keys = new ArrayList<>();
                for (KeyTab ktab: ktabs) {
                    for (KerberosKey k: ktab.getKeys(kp)) {
                        keys.add(k);
                    }
                }
                // Compatibility: also add keys to privCredSet. Remove old
                // ones first, only remove those from keytab.
                if (!subj.isReadOnly()) {
                    Set<Object> pcs = subj.getPrivateCredentials();
                    synchronized (pcs) {
                        Iterator<Object> iterator = pcs.iterator();
                        while (iterator.hasNext()) {
                            Object obj = iterator.next();
                            if (obj instanceof KeysFromKeyTab) {
                                KerberosKey key = (KerberosKey)obj;
                                if (Objects.equals(key.getPrincipal(), kp)) {
                                    iterator.remove();
                                }
                            }
                        }
                    }
                    for (KerberosKey key: keys) {
                        subj.getPrivateCredentials().add(new KeysFromKeyTab(key));
                    }
                }
                return keys.toArray(new KerberosKey[keys.size()]);
            }
        }

        public EncryptionKey[] getEKeys() {
            KerberosKey[] kkeys = getKKeys();
            EncryptionKey[] ekeys = new EncryptionKey[kkeys.length];
            for (int i=0; i<ekeys.length; i++) {
                ekeys[i] =  new EncryptionKey(
                            kkeys[i].getEncoded(), kkeys[i].getKeyType(),
                            new Integer(kkeys[i].getVersionNumber()));
            }
            return ekeys;
        }

        public void destroy() {
            kp = null;
            ktabs = null;
            kk = null;
        }
    }
    /**
     * Retrieves the ServiceCreds for the specified server principal from
     * the Subject in the specified AccessControlContext. If not found, and if
     * useSubjectCredsOnly is false, then obtain from a LoginContext.
     *
     * NOTE: This method is also used by JSSE Kerberos Cipher Suites
     */
    public static ServiceCreds getServiceCreds(GSSCaller caller,
        String serverPrincipal, AccessControlContext acc)
                throws LoginException {

        Subject accSubj = Subject.getSubject(acc);
        ServiceCreds sc = null;
        if (accSubj != null) {
            sc = ServiceCreds.getInstance(accSubj, serverPrincipal);
        }
        if (sc == null && !GSSUtil.useSubjectCredsOnly(caller)) {
            Subject subject = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
            sc = ServiceCreds.getInstance(subject, serverPrincipal);
        }
        return sc;
    }

    public static KerberosTicket credsToTicket(Credentials serviceCreds) {
        EncryptionKey sessionKey =  serviceCreds.getSessionKey();
        return new KerberosTicket(
            serviceCreds.getEncoded(),
            new KerberosPrincipal(serviceCreds.getClient().getName()),
            new KerberosPrincipal(serviceCreds.getServer().getName(),
                                KerberosPrincipal.KRB_NT_SRV_INST),
            sessionKey.getBytes(),
            sessionKey.getEType(),
            serviceCreds.getFlags(),
            serviceCreds.getAuthTime(),
            serviceCreds.getStartTime(),
            serviceCreds.getEndTime(),
            serviceCreds.getRenewTill(),
            serviceCreds.getClientAddresses());
    };

    public static Credentials ticketToCreds(KerberosTicket kerbTicket)
        throws KrbException, IOException {
        return new Credentials(
            kerbTicket.getEncoded(),
            kerbTicket.getClient().getName(),
            kerbTicket.getServer().getName(),
            kerbTicket.getSessionKey().getEncoded(),
            kerbTicket.getSessionKeyType(),
            kerbTicket.getFlags(),
            kerbTicket.getAuthTime(),
            kerbTicket.getStartTime(),
            kerbTicket.getEndTime(),
            kerbTicket.getRenewTill(),
            kerbTicket.getClientAddresses());
    }

    /**
     * A helper method to get EncryptionKeys from a javax..KeyTab
     * @param ktab the javax..KeyTab class
     * @param cname the PrincipalName
     * @return the EKeys, never null, might be empty
     */
    public static EncryptionKey[] keysFromJavaxKeyTab(
            KeyTab ktab, PrincipalName cname) {
        return SharedSecrets.getJavaxSecurityAuthKerberosAccess().
                keyTabGetEncryptionKeys(ktab, cname);
    }

}
