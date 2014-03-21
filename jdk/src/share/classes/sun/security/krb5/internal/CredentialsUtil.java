/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.krb5.internal;

import sun.security.krb5.*;
import java.io.IOException;

/**
 * This class is a utility that contains much of the TGS-Exchange
 * protocol. It is used by ../Credentials.java for service ticket
 * acquisition in both the normal and the x-realm case.
 */
public class CredentialsUtil {

    private static boolean DEBUG = sun.security.krb5.internal.Krb5.DEBUG;

    /**
     * Used by a middle server to acquire credentials on behalf of a
     * client to itself using the S4U2self extension.
     * @param client the client to impersonate
     * @param ccreds the TGT of the middle service
     * @return the new creds (cname=client, sname=middle)
     */
    public static Credentials acquireS4U2selfCreds(PrincipalName client,
            Credentials ccreds) throws KrbException, IOException {
        String uRealm = client.getRealmString();
        String localRealm = ccreds.getClient().getRealmString();
        if (!uRealm.equals(localRealm)) {
            // TODO: we do not support kerberos referral now
            throw new KrbException("Cross realm impersonation not supported");
        }
        KrbTgsReq req = new KrbTgsReq(
                ccreds,
                ccreds.getClient(),
                new PAData(Krb5.PA_FOR_USER,
                    new PAForUserEnc(client,
                        ccreds.getSessionKey()).asn1Encode()));
        Credentials creds = req.sendAndGetCreds();
        if (!creds.getClient().equals(client)) {
            throw new KrbException("S4U2self request not honored by KDC");
        }
        return creds;
    }

    /**
     * Used by a middle server to acquire a service ticket to a backend
     * server using the S4U2proxy extension.
     * @param backend the name of the backend service
     * @param second the client's service ticket to the middle server
     * @param ccreds the TGT of the middle server
     * @return the creds (cname=client, sname=backend)
     */
    public static Credentials acquireS4U2proxyCreds(
                String backend, Ticket second,
                PrincipalName client, Credentials ccreds)
            throws KrbException, IOException {
        KrbTgsReq req = new KrbTgsReq(
                ccreds,
                second,
                new PrincipalName(backend));
        Credentials creds = req.sendAndGetCreds();
        if (!creds.getClient().equals(client)) {
            throw new KrbException("S4U2proxy request not honored by KDC");
        }
        return creds;
    }

    /**
     * Acquires credentials for a specified service using initial
     * credential. When the service has a different realm from the initial
     * credential, we do cross-realm authentication - first, we use the
     * current credential to get a cross-realm credential from the local KDC,
     * then use that cross-realm credential to request service credential
     * from the foreign KDC.
     *
     * @param service the name of service principal
     * @param ccreds client's initial credential
     */
    public static Credentials acquireServiceCreds(
                String service, Credentials ccreds)
            throws KrbException, IOException {
        PrincipalName sname = new PrincipalName(service);
        String serviceRealm = sname.getRealmString();
        String localRealm = ccreds.getClient().getRealmString();

        if (localRealm.equals(serviceRealm)) {
            if (DEBUG) {
                System.out.println(
                        ">>> Credentials acquireServiceCreds: same realm");
            }
            return serviceCreds(sname, ccreds);
        }
        Credentials theCreds = null;

        boolean[] okAsDelegate = new boolean[1];
        Credentials theTgt = getTGTforRealm(localRealm, serviceRealm,
                ccreds, okAsDelegate);
        if (theTgt != null) {
            if (DEBUG) {
                System.out.println(">>> Credentials acquireServiceCreds: "
                        + "got right tgt");
                System.out.println(">>> Credentials acquireServiceCreds: "
                        + "obtaining service creds for " + sname);
            }

            try {
                theCreds = serviceCreds(sname, theTgt);
            } catch (Exception exc) {
                if (DEBUG) {
                    System.out.println(exc);
                }
                theCreds = null;
            }
        }

        if (theCreds != null) {
            if (DEBUG) {
                System.out.println(">>> Credentials acquireServiceCreds: "
                        + "returning creds:");
                Credentials.printDebug(theCreds);
            }
            if (!okAsDelegate[0]) {
                theCreds.resetDelegate();
            }
            return theCreds;
        }
        throw new KrbApErrException(Krb5.KRB_AP_ERR_GEN_CRED,
                                    "No service creds");
    }

    /**
     * Gets a TGT to another realm
     * @param localRealm this realm
     * @param serviceRealm the other realm, cannot equals to localRealm
     * @param ccreds TGT in this realm
     * @param okAsDelegate an [out] argument to receive the okAsDelegate
     * property. True only if all realms allow delegation.
     * @return the TGT for the other realm, null if cannot find a path
     * @throws KrbException if something goes wrong
     */
    private static Credentials getTGTforRealm(String localRealm,
            String serviceRealm, Credentials ccreds, boolean[] okAsDelegate)
            throws KrbException {

        // Get a list of realms to traverse
        String[] realms = Realm.getRealmsList(localRealm, serviceRealm);

        int i = 0, k = 0;
        Credentials cTgt = null, newTgt = null, theTgt = null;
        PrincipalName tempService = null;
        String newTgtRealm = null;

        okAsDelegate[0] = true;
        for (cTgt = ccreds, i = 0; i < realms.length;) {
            tempService = PrincipalName.tgsService(serviceRealm, realms[i]);

            if (DEBUG) {
                System.out.println(
                        ">>> Credentials acquireServiceCreds: main loop: ["
                        + i +"] tempService=" + tempService);
            }

            try {
                newTgt = serviceCreds(tempService, cTgt);
            } catch (Exception exc) {
                newTgt = null;
            }

            if (newTgt == null) {
                if (DEBUG) {
                    System.out.println(">>> Credentials acquireServiceCreds: "
                            + "no tgt; searching thru capath");
                }

                /*
                 * No tgt found. Let's go thru the realms list one by one.
                 */
                for (newTgt = null, k = i+1;
                        newTgt == null && k < realms.length; k++) {
                    tempService = PrincipalName.tgsService(realms[k], realms[i]);
                    if (DEBUG) {
                        System.out.println(
                                ">>> Credentials acquireServiceCreds: "
                                + "inner loop: [" + k
                                + "] tempService=" + tempService);
                    }
                    try {
                        newTgt = serviceCreds(tempService, cTgt);
                    } catch (Exception exc) {
                        newTgt = null;
                    }
                }
            } // Ends 'if (newTgt == null)'

            if (newTgt == null) {
                if (DEBUG) {
                    System.out.println(">>> Credentials acquireServiceCreds: "
                            + "no tgt; cannot get creds");
                }
                break;
            }

            /*
             * We have a tgt. It may or may not be for the target.
             * If it's for the target realm, we're done looking for a tgt.
             */
            newTgtRealm = newTgt.getServer().getInstanceComponent();
            if (okAsDelegate[0] && !newTgt.checkDelegate()) {
                if (DEBUG) {
                    System.out.println(">>> Credentials acquireServiceCreds: " +
                            "global OK-AS-DELEGATE turned off at " +
                            newTgt.getServer());
                }
                okAsDelegate[0] = false;
            }

            if (DEBUG) {
                System.out.println(">>> Credentials acquireServiceCreds: "
                        + "got tgt");
            }

            if (newTgtRealm.equals(serviceRealm)) {
                /* We got the right tgt */
                theTgt = newTgt;
                break;
            }

            /*
             * The new tgt is not for the target realm.
             * See if the realm of the new tgt is in the list of realms
             * and continue looking from there.
             */
            for (k = i+1; k < realms.length; k++) {
                if (newTgtRealm.equals(realms[k])) {
                    break;
                }
            }

            if (k < realms.length) {
                /*
                 * (re)set the counter so we start looking
                 * from the realm we just obtained a tgt for.
                 */
                i = k;
                cTgt = newTgt;

                if (DEBUG) {
                    System.out.println(">>> Credentials acquireServiceCreds: "
                            + "continuing with main loop counter reset to " + i);
                }
                continue;
            }
            else {
                /*
                 * The new tgt's realm is not in the hierarchy of realms.
                 * It's probably not safe to get a tgt from
                 * a tgs that is outside the known list of realms.
                 * Give up now.
                 */
                break;
            }
        } // Ends outermost/main 'for' loop

        return theTgt;
    }

   /*
    * This method does the real job to request the service credential.
    */
    private static Credentials serviceCreds(
            PrincipalName service, Credentials ccreds)
            throws KrbException, IOException {
        return new KrbTgsReq(ccreds, service).sendAndGetCreds();
    }
}
