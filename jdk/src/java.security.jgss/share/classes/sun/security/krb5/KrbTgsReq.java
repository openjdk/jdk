/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5;

import sun.security.krb5.internal.*;
import sun.security.krb5.internal.crypto.*;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * This class encapsulates a Kerberos TGS-REQ that is sent from the
 * client to the KDC.
 */
public class KrbTgsReq {

    private PrincipalName princName;
    private PrincipalName servName;
    private TGSReq tgsReqMessg;
    private KerberosTime ctime;
    private Ticket secondTicket = null;
    private boolean useSubkey = false;
    EncryptionKey tgsReqKey;

    private static final boolean DEBUG = Krb5.DEBUG;

    private byte[] obuf;
    private byte[] ibuf;

    // Used in CredentialsUtil
    public KrbTgsReq(Credentials asCreds,
                     PrincipalName sname)
        throws KrbException, IOException {
        this(new KDCOptions(),
            asCreds,
            sname,
            null, // KerberosTime from
            null, // KerberosTime till
            null, // KerberosTime rtime
            null, // eTypes, // null, // int[] eTypes
            null, // HostAddresses addresses
            null, // AuthorizationData authorizationData
            null, // Ticket[] additionalTickets
            null); // EncryptionKey subSessionKey
    }

    // S4U2proxy
    public KrbTgsReq(Credentials asCreds,
                     Ticket second,
                     PrincipalName sname)
            throws KrbException, IOException {
        this(KDCOptions.with(KDCOptions.CNAME_IN_ADDL_TKT,
                KDCOptions.FORWARDABLE),
            asCreds,
            sname,
            null,
            null,
            null,
            null,
            null,
            null,
            new Ticket[] {second}, // the service ticket
            null);
    }

    // S4U2user
    public KrbTgsReq(Credentials asCreds,
                     PrincipalName sname,
                     PAData extraPA)
        throws KrbException, IOException {
        this(KDCOptions.with(KDCOptions.FORWARDABLE),
            asCreds,
            asCreds.getClient(),
            sname,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            extraPA); // the PA-FOR-USER
    }

    // Called by Credentials, KrbCred
    KrbTgsReq(
            KDCOptions options,
            Credentials asCreds,
            PrincipalName sname,
            KerberosTime from,
            KerberosTime till,
            KerberosTime rtime,
            int[] eTypes,
            HostAddresses addresses,
            AuthorizationData authorizationData,
            Ticket[] additionalTickets,
            EncryptionKey subKey) throws KrbException, IOException {
        this(options, asCreds, asCreds.getClient(), sname,
                from, till, rtime, eTypes, addresses,
                authorizationData, additionalTickets, subKey, null);
    }

    private KrbTgsReq(
            KDCOptions options,
            Credentials asCreds,
            PrincipalName cname,
            PrincipalName sname,
            KerberosTime from,
            KerberosTime till,
            KerberosTime rtime,
            int[] eTypes,
            HostAddresses addresses,
            AuthorizationData authorizationData,
            Ticket[] additionalTickets,
            EncryptionKey subKey,
            PAData extraPA) throws KrbException, IOException {

        princName = cname;
        servName = sname;
        ctime = KerberosTime.now();

        // check if they are valid arguments. The optional fields
        // should be consistent with settings in KDCOptions.

        if (options.get(KDCOptions.FORWARDABLE) &&
                (!(asCreds.flags.get(Krb5.TKT_OPTS_FORWARDABLE)))) {
            options.set(KDCOptions.FORWARDABLE, false);
        }
        if (options.get(KDCOptions.FORWARDED)) {
            if (!(asCreds.flags.get(KDCOptions.FORWARDABLE)))
                throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        }
        if (options.get(KDCOptions.PROXIABLE) &&
                (!(asCreds.flags.get(Krb5.TKT_OPTS_PROXIABLE)))) {
            throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        }
        if (options.get(KDCOptions.PROXY)) {
            if (!(asCreds.flags.get(KDCOptions.PROXIABLE)))
                throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        }
        if (options.get(KDCOptions.ALLOW_POSTDATE) &&
                (!(asCreds.flags.get(Krb5.TKT_OPTS_MAY_POSTDATE)))) {
            throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        }
        if (options.get(KDCOptions.RENEWABLE) &&
                (!(asCreds.flags.get(Krb5.TKT_OPTS_RENEWABLE)))) {
            throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        }

        if (options.get(KDCOptions.POSTDATED)) {
            if (!(asCreds.flags.get(KDCOptions.POSTDATED)))
                throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        } else {
            if (from != null)  from = null;
        }
        if (options.get(KDCOptions.RENEWABLE)) {
            if (!(asCreds.flags.get(KDCOptions.RENEWABLE)))
                throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        } else {
            if (rtime != null)  rtime = null;
        }
        if (options.get(KDCOptions.ENC_TKT_IN_SKEY) || options.get(KDCOptions.CNAME_IN_ADDL_TKT)) {
            if (additionalTickets == null)
                throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
            // in TGS_REQ there could be more than one additional
            // tickets,  but in file-based credential cache,
            // there is only one additional ticket field.
            secondTicket = additionalTickets[0];
        } else {
            if (additionalTickets != null)
                additionalTickets = null;
        }

        tgsReqMessg = createRequest(
                options,
                asCreds.ticket,
                asCreds.key,
                ctime,
                princName,
                servName,
                from,
                till,
                rtime,
                eTypes,
                addresses,
                authorizationData,
                additionalTickets,
                subKey,
                extraPA);
        obuf = tgsReqMessg.asn1Encode();

        // XXX We need to revisit this to see if can't move it
        // up such that FORWARDED flag set in the options
        // is included in the marshaled request.
        /*
         * If this is based on a forwarded ticket, record that in the
         * options, because the returned TgsRep will contain the
         * FORWARDED flag set.
         */
        if (asCreds.flags.get(KDCOptions.FORWARDED))
            options.set(KDCOptions.FORWARDED, true);


    }

    /**
     * Sends a TGS request to the realm of the target.
     * @throws KrbException
     * @throws IOException
     */
    public void send() throws IOException, KrbException {
        String realmStr = null;
        if (servName != null)
            realmStr = servName.getRealmString();
        KdcComm comm = new KdcComm(realmStr);
        ibuf = comm.send(obuf);
    }

    public KrbTgsRep getReply()
        throws KrbException, IOException {
        return new KrbTgsRep(ibuf, this);
    }

    /**
     * Sends the request, waits for a reply, and returns the Credentials.
     * Used in Credentials, KrbCred, and internal/CredentialsUtil.
     */
    public Credentials sendAndGetCreds() throws IOException, KrbException {
        KrbTgsRep tgs_rep = null;
        String kdc = null;
        send();
        tgs_rep = getReply();
        return tgs_rep.getCreds();
    }

    KerberosTime getCtime() {
        return ctime;
    }

    private TGSReq createRequest(
                         KDCOptions kdc_options,
                         Ticket ticket,
                         EncryptionKey key,
                         KerberosTime ctime,
                         PrincipalName cname,
                         PrincipalName sname,
                         KerberosTime from,
                         KerberosTime till,
                         KerberosTime rtime,
                         int[] eTypes,
                         HostAddresses addresses,
                         AuthorizationData authorizationData,
                         Ticket[] additionalTickets,
                         EncryptionKey subKey,
                         PAData extraPA)
        throws IOException, KrbException, UnknownHostException {
        KerberosTime req_till = null;
        if (till == null) {
            req_till = new KerberosTime(0);
        } else {
            req_till = till;
        }

        /*
         * RFC 4120, Section 5.4.2.
         * For KRB_TGS_REP, the ciphertext is encrypted in the
         * sub-session key from the Authenticator, or if absent,
         * the session key from the ticket-granting ticket used
         * in the request.
         *
         * To support this, use tgsReqKey to remember which key to use.
         */
        tgsReqKey = key;

        int[] req_eTypes = null;
        if (eTypes == null) {
            req_eTypes = EType.getDefaults("default_tgs_enctypes");
        } else {
            req_eTypes = eTypes;
        }

        EncryptionKey reqKey = null;
        EncryptedData encAuthorizationData = null;
        if (authorizationData != null) {
            byte[] ad = authorizationData.asn1Encode();
            if (subKey != null) {
                reqKey = subKey;
                tgsReqKey = subKey;    // Key to use to decrypt reply
                useSubkey = true;
                encAuthorizationData = new EncryptedData(reqKey, ad,
                    KeyUsage.KU_TGS_REQ_AUTH_DATA_SUBKEY);
            } else
                encAuthorizationData = new EncryptedData(key, ad,
                    KeyUsage.KU_TGS_REQ_AUTH_DATA_SESSKEY);
        }

        KDCReqBody reqBody = new KDCReqBody(
                                            kdc_options,
                                            cname,
                                            sname,
                                            from,
                                            req_till,
                                            rtime,
                                            Nonce.value(),
                                            req_eTypes,
                                            addresses,
                                            encAuthorizationData,
                                            additionalTickets);

        byte[] temp = reqBody.asn1Encode(Krb5.KRB_TGS_REQ);
        // if the checksum type is one of the keyed checksum types,
        // use session key.
        Checksum cksum;
        switch (Checksum.CKSUMTYPE_DEFAULT) {
        case Checksum.CKSUMTYPE_RSA_MD4_DES:
        case Checksum.CKSUMTYPE_DES_MAC:
        case Checksum.CKSUMTYPE_DES_MAC_K:
        case Checksum.CKSUMTYPE_RSA_MD4_DES_K:
        case Checksum.CKSUMTYPE_RSA_MD5_DES:
        case Checksum.CKSUMTYPE_HMAC_SHA1_DES3_KD:
        case Checksum.CKSUMTYPE_HMAC_MD5_ARCFOUR:
        case Checksum.CKSUMTYPE_HMAC_SHA1_96_AES128:
        case Checksum.CKSUMTYPE_HMAC_SHA1_96_AES256:
            cksum = new Checksum(Checksum.CKSUMTYPE_DEFAULT, temp, key,
                KeyUsage.KU_PA_TGS_REQ_CKSUM);
            break;
        case Checksum.CKSUMTYPE_CRC32:
        case Checksum.CKSUMTYPE_RSA_MD4:
        case Checksum.CKSUMTYPE_RSA_MD5:
        default:
            cksum = new Checksum(Checksum.CKSUMTYPE_DEFAULT, temp);
        }

        // Usage will be KeyUsage.KU_PA_TGS_REQ_AUTHENTICATOR

        byte[] tgs_ap_req = new KrbApReq(
                                         new APOptions(),
                                         ticket,
                                         key,
                                         cname,
                                         cksum,
                                         ctime,
                                         reqKey,
                                         null,
                                         null).getMessage();

        PAData tgsPAData = new PAData(Krb5.PA_TGS_REQ, tgs_ap_req);
        return new TGSReq(
                extraPA != null ?
                    new PAData[] {extraPA, tgsPAData } :
                    new PAData[] {tgsPAData},
                reqBody);
    }

    TGSReq getMessage() {
        return tgsReqMessg;
    }

    Ticket getSecondTicket() {
        return secondTicket;
    }

    private static void debug(String message) {
        //      System.err.println(">>> KrbTgsReq: " + message);
    }

    boolean usedSubkey() {
        return useSubkey;
    }

}
