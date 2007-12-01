/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.security.jgss.krb5;

import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;
import java.security.AccessControlContext;
import sun.security.jgss.GSSUtil;

import sun.security.krb5.Credentials;
import sun.security.krb5.EncryptionKey;
import sun.security.krb5.KrbException;
import java.io.IOException;
import java.util.List;
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
    public static KerberosTicket getTicketFromSubjectAndTgs(int caller,
        String clientPrincipal, String serverPrincipal, String tgsPrincipal,
        AccessControlContext acc)
        throws LoginException, KrbException, IOException {

        // 1. Try to find service ticket in acc subject
        Subject accSubj = Subject.getSubject(acc);
        KerberosTicket ticket = (KerberosTicket) SubjectComber.find(accSubj,
            serverPrincipal, clientPrincipal, KerberosTicket.class);

        if (ticket != null) {
            return ticket;  // found it
        }

        Subject loginSubj = null;
        if (!GSSUtil.useSubjectCredsOnly(caller)) {
            // 2. Try to get ticket from login
            try {
                loginSubj = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
                ticket = (KerberosTicket) SubjectComber.find(loginSubj,
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
        KerberosTicket tgt = (KerberosTicket) SubjectComber.find(accSubj,
            tgsPrincipal, clientPrincipal, KerberosTicket.class);

        boolean fromAcc;
        if (tgt == null && loginSubj != null) {
            // 4. Try to get TGT from login subject
            tgt = (KerberosTicket) SubjectComber.find(loginSubj,
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
    static KerberosTicket getTicket(int caller,
        String clientPrincipal, String serverPrincipal,
        AccessControlContext acc) throws LoginException {

        // Try to get ticket from acc's Subject
        Subject accSubj = Subject.getSubject(acc);
        KerberosTicket ticket = (KerberosTicket)
            SubjectComber.find(accSubj, serverPrincipal, clientPrincipal,
                  KerberosTicket.class);

        // Try to get ticket from Subject obtained from GSSUtil
        if (ticket == null && !GSSUtil.useSubjectCredsOnly(caller)) {
            Subject subject = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
            ticket = (KerberosTicket) SubjectComber.find(subject,
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
    public static Subject getSubject(int caller,
        AccessControlContext acc) throws LoginException {

        // Try to get the Subject from acc
        Subject subject = Subject.getSubject(acc);

        // Try to get Subject obtained from GSSUtil
        if (subject == null && !GSSUtil.useSubjectCredsOnly(caller)) {
            subject = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
        }
        return subject;
    }

    /**
     * Retrieves the keys for the specified server principal from
     * the Subject in the specified AccessControlContext.
     * If the ticket can not be found in the Subject, and if
     * useSubjectCredsOnly is false, then obtain keys from
     * a LoginContext.
     *
     * NOTE: This method is used by JSSE Kerberos Cipher Suites
     */
    public static KerberosKey[] getKeys(int caller,
        String serverPrincipal, AccessControlContext acc)
                throws LoginException {

        Subject accSubj = Subject.getSubject(acc);
        List<KerberosKey> kkeys = (List<KerberosKey>)SubjectComber.findMany(
                accSubj, serverPrincipal, null, KerberosKey.class);

        if (kkeys == null && !GSSUtil.useSubjectCredsOnly(caller)) {
            Subject subject = GSSUtil.login(caller, GSSUtil.GSS_KRB5_MECH_OID);
            kkeys = (List<KerberosKey>) SubjectComber.findMany(subject,
                serverPrincipal, null, KerberosKey.class);
        }

        int len;
        if (kkeys != null && (len = kkeys.size()) > 0) {
            KerberosKey[] keys = new KerberosKey[len];
            kkeys.toArray(keys);
            return keys;
        } else {
            return null;
        }
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
}
