/*
 * Copyright (c) 2000, 2006, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.krb5.internal.crypto.KeyUsage;
import java.io.IOException;
import sun.security.util.DerValue;

/**
 * This class encapsulates the KRB-CRED message that a client uses to
 * send its delegated credentials to a server.
 *
 * Supports delegation of one ticket only.
 * @author Mayank Upadhyay
 */
public class KrbCred {

    private static boolean DEBUG = Krb5.DEBUG;

    private byte[] obuf = null;
    private KRBCred credMessg = null;
    private Ticket ticket = null;
    private EncKrbCredPart encPart = null;
    private Credentials creds = null;
    private KerberosTime timeStamp = null;

         // Used in InitialToken with null key
    public KrbCred(Credentials tgt,
                   Credentials serviceTicket,
                   EncryptionKey key)
        throws KrbException, IOException {

        PrincipalName client = tgt.getClient();
        PrincipalName tgService = tgt.getServer();
        PrincipalName server = serviceTicket.getServer();
        if (!serviceTicket.getClient().equals(client))
            throw new KrbException(Krb5.KRB_ERR_GENERIC,
                                "Client principal does not match");

        // XXX Check Windows flag OK-TO-FORWARD-TO

        // Invoke TGS-REQ to get a forwarded TGT for the peer

        KDCOptions options = new KDCOptions();
        options.set(KDCOptions.FORWARDED, true);
        options.set(KDCOptions.FORWARDABLE, true);

        HostAddresses sAddrs = null;
        // XXX Also NT_GSS_KRB5_PRINCIPAL can be a host based principal
        // GSSName.NT_HOSTBASED_SERVICE should display with KRB_NT_SRV_HST
        if (server.getNameType() == PrincipalName.KRB_NT_SRV_HST)
            sAddrs=  new HostAddresses(server);

        KrbTgsReq tgsReq = new KrbTgsReq(options, tgt, tgService,
                                         null, null, null, null, sAddrs, null, null, null);
        credMessg = createMessage(tgsReq.sendAndGetCreds(), key);

        obuf = credMessg.asn1Encode();
    }

    KRBCred createMessage(Credentials delegatedCreds, EncryptionKey key)
        throws KrbException, IOException {

        EncryptionKey sessionKey
            = delegatedCreds.getSessionKey();
        PrincipalName princ = delegatedCreds.getClient();
        Realm realm = princ.getRealm();
        PrincipalName tgService = delegatedCreds.getServer();
        Realm tgsRealm = tgService.getRealm();

        KrbCredInfo credInfo = new KrbCredInfo(sessionKey, realm,
                                               princ, delegatedCreds.flags, delegatedCreds.authTime,
                                               delegatedCreds.startTime, delegatedCreds.endTime,
                                               delegatedCreds.renewTill, tgsRealm, tgService,
                                               delegatedCreds.cAddr);

        timeStamp = new KerberosTime(KerberosTime.NOW);
        KrbCredInfo[] credInfos = {credInfo};
        EncKrbCredPart encPart =
            new EncKrbCredPart(credInfos,
                               timeStamp, null, null, null, null);

        EncryptedData encEncPart = new EncryptedData(key,
            encPart.asn1Encode(), KeyUsage.KU_ENC_KRB_CRED_PART);

        Ticket[] tickets = {delegatedCreds.ticket};

        credMessg = new KRBCred(tickets, encEncPart);

        return credMessg;
    }

         // Used in InitialToken, key always NULL_KEY
    public KrbCred(byte[] asn1Message, EncryptionKey key)
        throws KrbException, IOException {

        credMessg = new KRBCred(asn1Message);

        ticket = credMessg.tickets[0];

        byte[] temp = credMessg.encPart.decrypt(key,
            KeyUsage.KU_ENC_KRB_CRED_PART);
        byte[] plainText = credMessg.encPart.reset(temp, true);
        DerValue encoding = new DerValue(plainText);
        EncKrbCredPart encPart = new EncKrbCredPart(encoding);

        timeStamp = encPart.timeStamp;

        KrbCredInfo credInfo = encPart.ticketInfo[0];
        EncryptionKey credInfoKey = credInfo.key;
        Realm prealm = credInfo.prealm;
        // XXX PrincipalName can store realm + principalname or
        // just principal name.
        PrincipalName pname = credInfo.pname;
        pname.setRealm(prealm);
        TicketFlags flags = credInfo.flags;
        KerberosTime authtime = credInfo.authtime;
        KerberosTime starttime = credInfo.starttime;
        KerberosTime endtime = credInfo.endtime;
        KerberosTime renewTill = credInfo.renewTill;
        Realm srealm = credInfo.srealm;
        PrincipalName sname = credInfo.sname;
        sname.setRealm(srealm);
        HostAddresses caddr = credInfo.caddr;

        if (DEBUG) {
            System.out.println(">>>Delegated Creds have pname=" + pname
                               + " sname=" + sname
                               + " authtime=" + authtime
                               + " starttime=" + starttime
                               + " endtime=" + endtime
                               + "renewTill=" + renewTill);
        }
        creds = new Credentials(ticket, pname, sname, credInfoKey,
                                flags, authtime, starttime, endtime, renewTill, caddr);
    }

    /**
     * Returns the delegated credentials from the peer.
     */
    public Credentials[] getDelegatedCreds() {

        Credentials[] allCreds = {creds};
        return allCreds;
    }

    /**
     * Returns the ASN.1 encoding that should be sent to the peer.
     */
    public byte[] getMessage() {
        return obuf;
    }
}
