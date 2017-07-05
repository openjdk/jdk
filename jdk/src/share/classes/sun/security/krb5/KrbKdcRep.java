/*
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

abstract class KrbKdcRep {

    static void check(
                      KDCReq req,
                      KDCRep rep
                      ) throws KrbApErrException {

        if (!req.reqBody.cname.equalsWithoutRealm(rep.cname)) {
            rep.encKDCRepPart.key.destroy();
            throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
        }

        /**** XXX
              if (!req.reqBody.crealm.equals(rep.crealm)) {
              rep.encKDCRepPart.key.destroy();
              throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
              }
        *****/

        if (!req.reqBody.sname.equalsWithoutRealm(rep.encKDCRepPart.sname)) {
            rep.encKDCRepPart.key.destroy();
            throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
        }

        if (!req.reqBody.crealm.equals(rep.encKDCRepPart.srealm)) {
            rep.encKDCRepPart.key.destroy();
            throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
        }

        if (req.reqBody.getNonce() != rep.encKDCRepPart.nonce) {
            rep.encKDCRepPart.key.destroy();
            throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
        }

        if (
            ((req.reqBody.addresses != null && rep.encKDCRepPart.caddr != null) &&
             !req.reqBody.addresses.equals(rep.encKDCRepPart.caddr))) {
            rep.encKDCRepPart.key.destroy();
            throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
        }


        for (int i = 1; i < 6; i++) {
            if (req.reqBody.kdcOptions.get(i) !=
                rep.encKDCRepPart.flags.get(i)) {
                throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
            }
        }

        // XXX Can renew a ticket but not ask for a renewable renewed ticket
        // See impl of Credentials.renew().
        if (req.reqBody.kdcOptions.get(KDCOptions.RENEWABLE) !=
            rep.encKDCRepPart.flags.get(KDCOptions.RENEWABLE)) {
            throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
        }
        if ((req.reqBody.from == null) || req.reqBody.from.isZero())
            // verify this is allowed
            if ((rep.encKDCRepPart.starttime != null) &&
                !rep.encKDCRepPart.starttime.inClockSkew()) {
                rep.encKDCRepPart.key.destroy();
                throw new KrbApErrException(Krb5.KRB_AP_ERR_SKEW);
            }

        if ((req.reqBody.from != null) && !req.reqBody.from.isZero())
            // verify this is allowed
            if ((rep.encKDCRepPart.starttime != null) &&
                !req.reqBody.from.equals(rep.encKDCRepPart.starttime)) {
                rep.encKDCRepPart.key.destroy();
                throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
            }

        if (!req.reqBody.till.isZero() &&
            rep.encKDCRepPart.endtime.greaterThan(req.reqBody.till)) {
            rep.encKDCRepPart.key.destroy();
            throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
        }

        if (req.reqBody.kdcOptions.get(KDCOptions.RENEWABLE))
            if (req.reqBody.rtime != null && !req.reqBody.rtime.isZero())
                                // verify this is required
                if ((rep.encKDCRepPart.renewTill == null) ||
                    rep.encKDCRepPart.renewTill.greaterThan(req.reqBody.rtime)
                    ) {
                    rep.encKDCRepPart.key.destroy();
                    throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
                }

        if (req.reqBody.kdcOptions.get(KDCOptions.RENEWABLE_OK) &&
            rep.encKDCRepPart.flags.get(KDCOptions.RENEWABLE))
            if (!req.reqBody.till.isZero())
                                // verify this is required
                if ((rep.encKDCRepPart.renewTill == null) ||
                    rep.encKDCRepPart.renewTill.greaterThan(req.reqBody.till)
                    ) {
                    rep.encKDCRepPart.key.destroy();
                    throw new KrbApErrException(Krb5.KRB_AP_ERR_MODIFIED);
                }
    }


}
