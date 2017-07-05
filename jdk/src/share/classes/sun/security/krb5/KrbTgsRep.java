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
import sun.security.util.*;
import java.io.IOException;

/**
 * This class encapsulates a TGS-REP that is sent from the KDC to the
 * Kerberos client.
 */
public class KrbTgsRep extends KrbKdcRep {
    private TGSRep rep;
    private Credentials creds;
    private Ticket secondTicket;
    private static final boolean DEBUG = Krb5.DEBUG;

    KrbTgsRep(byte[] ibuf, KrbTgsReq tgsReq)
        throws KrbException, IOException {
        DerValue ref = new DerValue(ibuf);
        TGSReq req = tgsReq.getMessage();
        TGSRep rep = null;
        try {
            rep = new TGSRep(ref);
        } catch (Asn1Exception e) {
            rep = null;
            KRBError err = new KRBError(ref);
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
                ke = new KrbException(err.getErrorCode());
            } else {
                // override default text with server text
                ke = new KrbException(err.getErrorCode(), eText);
            }
            ke.initCause(e);
            throw ke;
        }
        byte[] enc_tgs_rep_bytes = rep.encPart.decrypt(tgsReq.tgsReqKey,
            tgsReq.usedSubkey() ? KeyUsage.KU_ENC_TGS_REP_PART_SUBKEY :
            KeyUsage.KU_ENC_TGS_REP_PART_SESSKEY);

        byte[] enc_tgs_rep_part = rep.encPart.reset(enc_tgs_rep_bytes);
        ref = new DerValue(enc_tgs_rep_part);
        EncTGSRepPart enc_part = new EncTGSRepPart(ref);
        rep.ticket.sname.setRealm(rep.ticket.realm);
        rep.encKDCRepPart = enc_part;

        check(req, rep);

        creds = new Credentials(rep.ticket,
                                req.reqBody.cname,
                                rep.ticket.sname,
                                enc_part.key,
                                enc_part.flags,
                                enc_part.authtime,
                                enc_part.starttime,
                                enc_part.endtime,
                                enc_part.renewTill,
                                enc_part.caddr
                                );
        this.rep = rep;
        this.creds = creds;
        this.secondTicket = tgsReq.getSecondTicket();
    }

    /**
     * Return the credentials that were contained in this KRB-TGS-REP.
     */
    public Credentials getCreds() {
        return creds;
    }

    sun.security.krb5.internal.ccache.Credentials setCredentials() {
        return new sun.security.krb5.internal.ccache.Credentials(rep, secondTicket);
    }
}
