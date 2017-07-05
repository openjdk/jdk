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
import sun.security.krb5.internal.crypto.KeyUsage;
import sun.security.krb5.internal.crypto.EType;
import sun.security.util.*;
import java.io.IOException;

/**
 * This class encapsulates a AS-REP message that the KDC sends to the
 * client.
 */
public class KrbAsRep extends KrbKdcRep {

    private ASRep rep;
    private Credentials creds;

    private boolean DEBUG = Krb5.DEBUG;

    KrbAsRep(byte[] ibuf, EncryptionKey[] keys, KrbAsReq asReq) throws
    KrbException, Asn1Exception, IOException {
        if (keys == null)
            throw new KrbException(Krb5.API_INVALID_ARG);
        DerValue encoding = new DerValue(ibuf);
        ASReq req = asReq.getMessage();
        ASRep rep = null;
        try {
            rep = new ASRep(encoding);
        } catch (Asn1Exception e) {
            rep = null;
            KRBError err = new KRBError(encoding);
            String errStr = err.getErrorString();
            String eText = null; // pick up text sent by the server (if any)

            if (errStr != null && errStr.length() > 0) {
                if (errStr.charAt(errStr.length() - 1) == 0)
                    eText = errStr.substring(0, errStr.length() - 1);
                else
                    eText = errStr;
            }
            KrbException ke;
            if (eText == null) {
                // no text sent from server
                ke = new KrbException(err);
            } else {
                if (DEBUG) {
                    System.out.println("KRBError received: " + eText);
                }
                // override default text with server text
                ke = new KrbException(err, eText);
            }
            ke.initCause(e);
            throw ke;
        }

        int encPartKeyType = rep.encPart.getEType();
        EncryptionKey dkey = EncryptionKey.findKey(encPartKeyType, keys);

        if (dkey == null) {
            throw new KrbException(Krb5.API_INVALID_ARG,
                "Cannot find key of appropriate type to decrypt AS REP - " +
                EType.toString(encPartKeyType));
        }

        byte[] enc_as_rep_bytes = rep.encPart.decrypt(dkey,
            KeyUsage.KU_ENC_AS_REP_PART);
        byte[] enc_as_rep_part = rep.encPart.reset(enc_as_rep_bytes);

        encoding = new DerValue(enc_as_rep_part);
        EncASRepPart enc_part = new EncASRepPart(encoding);
        rep.ticket.sname.setRealm(rep.ticket.realm);
        rep.encKDCRepPart = enc_part;

        check(req, rep);

        creds = new Credentials(
                                rep.ticket,
                                req.reqBody.cname,
                                rep.ticket.sname,
                                enc_part.key,
                                enc_part.flags,
                                enc_part.authtime,
                                enc_part.starttime,
                                enc_part.endtime,
                                enc_part.renewTill,
                                enc_part.caddr);
        if (DEBUG) {
            System.out.println(">>> KrbAsRep cons in KrbAsReq.getReply " +
                               req.reqBody.cname.getNameString());
        }

        this.rep = rep;
        this.creds = creds;
    }

    public Credentials getCreds() {
        return creds;
    }

    // made public for Kinit
    public sun.security.krb5.internal.ccache.Credentials setCredentials() {
        return new sun.security.krb5.internal.ccache.Credentials(rep);
    }
}
