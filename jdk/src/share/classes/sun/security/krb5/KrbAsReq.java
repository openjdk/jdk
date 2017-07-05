/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.krb5.internal.crypto.EType;
import sun.security.krb5.internal.crypto.Nonce;
import sun.security.krb5.internal.crypto.KeyUsage;
import sun.security.util.*;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

/**
 * This class encapsulates the KRB-AS-REQ message that the client
 * sends to the KDC.
 */
public class KrbAsReq extends KrbKdcReq {
    private PrincipalName princName;
    private ASReq asReqMessg;

    private boolean DEBUG = Krb5.DEBUG;
    private static KDCOptions defaultKDCOptions = new KDCOptions();

    // pre-auth info
    private boolean PA_ENC_TIMESTAMP_REQUIRED = false;
    private boolean pa_exists = false;
    private int pa_etype = 0;
    private String pa_salt = null;
    private byte[] pa_s2kparams = null;

    // default is address-less tickets
    private boolean KDC_EMPTY_ADDRESSES_ALLOWED = true;

    /**
     * Creates a KRB-AS-REQ to send to the default KDC
     * @throws KrbException
     * @throws IOException
     */
     // Called by Credentials
    KrbAsReq(PrincipalName principal, EncryptionKey[] keys)
        throws KrbException, IOException {
        this(keys, // for pre-authentication
             false, 0, null, null, // pre-auth values
             defaultKDCOptions,
             principal,
             null, // PrincipalName sname
             null, // KerberosTime from
             null, // KerberosTime till
             null, // KerberosTime rtime
             null, // int[] eTypes
             null, // HostAddresses addresses
             null); // Ticket[] additionalTickets
    }

    /**
     * Creates a KRB-AS-REQ to send to the default KDC
     * with pre-authentication values
     */
    KrbAsReq(PrincipalName principal, EncryptionKey[] keys,
        boolean pa_exists, int etype, String salt, byte[] s2kparams)
        throws KrbException, IOException {
        this(keys, // for pre-authentication
             pa_exists, etype, salt, s2kparams, // pre-auth values
             defaultKDCOptions,
             principal,
             null, // PrincipalName sname
             null, // KerberosTime from
             null, // KerberosTime till
             null, // KerberosTime rtime
             null, // int[] eTypes
             null, // HostAddresses addresses
             null); // Ticket[] additionalTickets
    }

     private static int[] getETypesFromKeys(EncryptionKey[] keys) {
         int[] types = new int[keys.length];
         for (int i = 0; i < keys.length; i++) {
             types[i] = keys[i].getEType();
         }
         return types;
     }

    // update with pre-auth info
    public void updatePA(int etype, String salt, byte[] params, PrincipalName name) {
        // set the pre-auth values
        pa_exists = true;
        pa_etype = etype;
        pa_salt = salt;
        pa_s2kparams = params;

        // update salt in PrincipalName
        if (salt != null && salt.length() > 0) {
            name.setSalt(salt);
            if (DEBUG) {
                System.out.println("Updated salt from pre-auth = " + name.getSalt());
            }
        }
        PA_ENC_TIMESTAMP_REQUIRED = true;
    }

     // Used by Kinit
    public KrbAsReq(
                    char[] password,
                    KDCOptions options,
                    PrincipalName cname,
                    PrincipalName sname,
                    KerberosTime from,
                    KerberosTime till,
                    KerberosTime rtime,
                    int[] eTypes,
                    HostAddresses addresses,
                    Ticket[] additionalTickets)
        throws KrbException, IOException {
        this(password,
             false, 0, null, null, // pre-auth values
             options,
             cname,
             sname, // PrincipalName sname
             from,  // KerberosTime from
             till,  // KerberosTime till
             rtime, // KerberosTime rtime
             eTypes, // int[] eTypes
             addresses, // HostAddresses addresses
             additionalTickets); // Ticket[] additionalTickets
    }

     // Used by Kinit
    public KrbAsReq(
                    char[] password,
                    boolean pa_exists,
                    int etype,
                    String salt,
                    byte[] s2kparams,
                    KDCOptions options,
                    PrincipalName cname,
                    PrincipalName sname,
                    KerberosTime from,
                    KerberosTime till,
                    KerberosTime rtime,
                    int[] eTypes,
                    HostAddresses addresses,
                    Ticket[] additionalTickets)
        throws KrbException, IOException {

        EncryptionKey[] keys = null;

        // update with preauth info
        if (pa_exists) {
            updatePA(etype, salt, s2kparams, cname);
        }

        if (password != null) {
            keys = EncryptionKey.acquireSecretKeys(password, cname.getSalt(), pa_exists,
                                                        pa_etype, pa_s2kparams);
        }
        if (DEBUG) {
            System.out.println(">>>KrbAsReq salt is " + cname.getSalt());
        }

        try {
            init(
                 keys,
                 options,
                 cname,
                 sname,
                 from,
                 till,
                 rtime,
                 eTypes,
                 addresses,
                 additionalTickets);
        }
        finally {
            /*
             * Its ok to destroy the key here because we created it and are
             * now done with it.
             */
             if (keys != null) {
                 for (int i = 0; i < keys.length; i++) {
                     keys[i].destroy();
                 }
             }
        }
    }

     // Used in Kinit
    public KrbAsReq(
                    EncryptionKey[] keys,
                    KDCOptions options,
                    PrincipalName cname,
                    PrincipalName sname,
                    KerberosTime from,
                    KerberosTime till,
                    KerberosTime rtime,
                    int[] eTypes,
                    HostAddresses addresses,
                    Ticket[] additionalTickets)
        throws KrbException, IOException {
        this(keys,
             false, 0, null, null, // pre-auth values
             options,
             cname,
             sname, // PrincipalName sname
             from,  // KerberosTime from
             till,  // KerberosTime till
             rtime, // KerberosTime rtime
             eTypes, // int[] eTypes
             addresses, // HostAddresses addresses
             additionalTickets); // Ticket[] additionalTickets
    }

    // Used by Kinit
    public KrbAsReq(
                    EncryptionKey[] keys,
                    boolean pa_exists,
                    int etype,
                    String salt,
                    byte[] s2kparams,
                    KDCOptions options,
                    PrincipalName cname,
                    PrincipalName sname,
                    KerberosTime from,
                    KerberosTime till,
                    KerberosTime rtime,
                    int[] eTypes,
                    HostAddresses addresses,
                    Ticket[] additionalTickets)
        throws KrbException, IOException {

        // update with preauth info
        if (pa_exists) {
            // update pre-auth info
            updatePA(etype, salt, s2kparams, cname);

            if (DEBUG) {
                System.out.println(">>>KrbAsReq salt is " + cname.getSalt());
            }
        }

        init(
             keys,
             options,
             cname,
             sname,
             from,
             till,
             rtime,
             eTypes,
             addresses,
             additionalTickets);
    }

     /*
    private KrbAsReq(KDCOptions options,
             PrincipalName cname,
             PrincipalName sname,
             KerberosTime from,
             KerberosTime till,
             KerberosTime rtime,
             int[] eTypes,
             HostAddresses addresses,
             Ticket[] additionalTickets)
        throws KrbException, IOException {
        init(null,
             options,
             cname,
             sname,
             from,
             till,
             rtime,
             eTypes,
             addresses,
             additionalTickets);
    }
*/

    private void init(EncryptionKey[] keys,
                      KDCOptions options,
                      PrincipalName cname,
                      PrincipalName sname,
                      KerberosTime from,
                      KerberosTime till,
                      KerberosTime rtime,
                      int[] eTypes,
                      HostAddresses addresses,
                      Ticket[] additionalTickets )
        throws KrbException, IOException {

        // check if they are valid arguments. The optional fields should be
        // consistent with settings in KDCOptions. Mar 17 2000
        if (options.get(KDCOptions.FORWARDED) ||
            options.get(KDCOptions.PROXY) ||
            options.get(KDCOptions.ENC_TKT_IN_SKEY) ||
            options.get(KDCOptions.RENEW) ||
            options.get(KDCOptions.VALIDATE)) {
            // this option is only specified in a request to the
            // ticket-granting server
            throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        }
        if (options.get(KDCOptions.POSTDATED)) {
            //  if (from == null)
            //          throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        } else {
            if (from != null)  from = null;
        }
        if (options.get(KDCOptions.RENEWABLE)) {
            //  if (rtime == null)
            //          throw new KrbException(Krb5.KRB_AP_ERR_REQ_OPTIONS);
        } else {
            if (rtime != null)  rtime = null;
        }

        princName = cname;
        int[] tktETypes = EType.getDefaults("default_tkt_enctypes", keys);
        PAData[] paData = null;
        if (PA_ENC_TIMESTAMP_REQUIRED) {
            EncryptionKey key = null;
            if (pa_etype != EncryptedData.ETYPE_NULL) {
                if (DEBUG) {
                    System.out.println("Pre-Authenticaton: find key for etype = " + pa_etype);
                }
                key = EncryptionKey.findKey(pa_etype, keys);
            } else {
                if (tktETypes.length > 0) {
                    key = EncryptionKey.findKey(tktETypes[0], keys);
                }
            }
            if (DEBUG) {
                System.out.println("AS-REQ: Add PA_ENC_TIMESTAMP now");
            }
            PAEncTSEnc ts = new PAEncTSEnc();
            byte[] temp = ts.asn1Encode();
            if (key != null) {
                // Use first key in list
                EncryptedData encTs = new EncryptedData(key, temp,
                    KeyUsage.KU_PA_ENC_TS);
                paData = new PAData[1];
                paData[0] = new PAData( Krb5.PA_ENC_TIMESTAMP,
                                        encTs.asn1Encode());
            }
        }

        if (DEBUG) {
            System.out.println(">>> KrbAsReq calling createMessage");
        }

        if (eTypes == null) {
            eTypes = tktETypes;
        }

        // check to use addresses in tickets
        if (Config.getInstance().useAddresses()) {
            KDC_EMPTY_ADDRESSES_ALLOWED = false;
        }
        // get the local InetAddress if required
        if (addresses == null && !KDC_EMPTY_ADDRESSES_ALLOWED) {
            addresses = HostAddresses.getLocalAddresses();
        }

        asReqMessg = createMessage(
                                   paData,
                                   options,
                                   cname,
                                   cname.getRealm(),
                                   sname,
                                   from,
                                   till,
                                   rtime,
                                   eTypes,
                                   addresses,
                                   additionalTickets);
        obuf = asReqMessg.asn1Encode();
    }

    /**
     * Returns an AS-REP message corresponding to the AS-REQ that
     * was sent.
     * @param password The password that will be used to derive the
     * secret key that will decrypt the AS-REP from  the KDC.
     * @exception KrbException if an error occurs while reading the data.
     * @exception IOException if an I/O error occurs while reading encoded data.
     */
    public KrbAsRep getReply(char[] password)
        throws KrbException, IOException {

        if (password == null)
            throw new KrbException(Krb5.API_INVALID_ARG);
        KrbAsRep temp = null;
        EncryptionKey[] keys = null;
        try {
            keys = EncryptionKey.acquireSecretKeys(password,
                princName.getSalt(), pa_exists, pa_etype, pa_s2kparams);
            temp = getReply(keys);
        } finally {
            /*
             * Its ok to destroy the key here because we created it and are
             * now done with it.
             */
             if (keys != null) {
                for (int i = 0; i < keys.length; i++) {
                    keys[i].destroy();
                }
             }
        }
        return temp;
    }

    /**
     * Sends an AS request to the realm of the client.
     * returns the KDC hostname that the request was sent to
     */

    public String send()
        throws IOException, KrbException
    {
        String realmStr = null;
        if (princName != null)
            realmStr = princName.getRealmString();

        return (send(realmStr));
    }

    /**
     * Returns an AS-REP message corresponding to the AS-REQ that
     * was sent.
     * @param keys The secret keys that will decrypt the AS-REP from
     * the KDC; key selected depends on etype used to encrypt data.
     * @exception KrbException if an error occurs while reading the data.
     * @exception IOException if an I/O error occurs while reading encoded
     * data.
     *
     */
    public KrbAsRep getReply(EncryptionKey[] keys)
        throws KrbException,IOException {
        return new KrbAsRep(ibuf, keys, this);
    }

    private ASReq createMessage(
                        PAData[] paData,
                        KDCOptions kdc_options,
                        PrincipalName cname,
                        Realm crealm,
                        PrincipalName sname,
                        KerberosTime from,
                        KerberosTime till,
                        KerberosTime rtime,
                        int[] eTypes,
                        HostAddresses addresses,
                        Ticket[] additionalTickets
                        ) throws Asn1Exception, KrbApErrException,
                        RealmException, UnknownHostException, IOException {

        if (DEBUG) {
            System.out.println(">>> KrbAsReq in createMessage");
        }

        PrincipalName req_sname = null;
        if (sname == null) {
            if (crealm == null) {
                throw new RealmException(Krb5.REALM_NULL,
                                         "default realm not specified ");
            }
            req_sname = new PrincipalName(
                                          "krbtgt" +
                                          PrincipalName.NAME_COMPONENT_SEPARATOR +
                                          crealm.toString(),
                                          PrincipalName.KRB_NT_SRV_INST);
        } else
            req_sname = sname;

        KerberosTime req_till = null;
        if (till == null) {
            req_till = new KerberosTime();
        } else {
            req_till = till;
        }

        KDCReqBody kdc_req_body = new KDCReqBody(kdc_options,
                                                 cname,
                                                 crealm,
                                                 req_sname,
                                                 from,
                                                 req_till,
                                                 rtime,
                                                 Nonce.value(),
                                                 eTypes,
                                                 addresses,
                                                 null,
                                                 additionalTickets);

        return new ASReq(
                         paData,
                         kdc_req_body);
    }

    ASReq getMessage() {
        return asReqMessg;
    }
}
