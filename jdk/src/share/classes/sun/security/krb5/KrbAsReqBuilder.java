/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.krb5;

import java.io.IOException;
import java.util.Arrays;
import javax.security.auth.kerberos.KeyTab;
import sun.security.jgss.krb5.Krb5Util;
import sun.security.krb5.internal.HostAddresses;
import sun.security.krb5.internal.KDCOptions;
import sun.security.krb5.internal.KRBError;
import sun.security.krb5.internal.KerberosTime;
import sun.security.krb5.internal.Krb5;
import sun.security.krb5.internal.PAData;
import sun.security.krb5.internal.crypto.EType;

/**
 * A manager class for AS-REQ communications.
 *
 * This class does:
 * 1. Gather information to create AS-REQ
 * 2. Create and send AS-REQ
 * 3. Receive AS-REP and KRB-ERROR (-KRB_ERR_RESPONSE_TOO_BIG) and parse them
 * 4. Emit credentials and secret keys (for JAAS storeKey=true with password)
 *
 * This class does not:
 * 1. Deal with real communications (KdcComm does it, and TGS-REQ)
 *    a. Name of KDCs for a realm
 *    b. Server availability, timeout, UDP or TCP
 *    d. KRB_ERR_RESPONSE_TOO_BIG
 * 2. Stores its own copy of password, this means:
 *    a. Do not change/wipe it before Builder finish
 *    b. Builder will not wipe it for you
 *
 * With this class:
 * 1. KrbAsReq has only one constructor
 * 2. Krb5LoginModule and Kinit call a single builder
 * 3. Better handling of sensitive info
 *
 * @since 1.7
 */

public final class KrbAsReqBuilder {

    // Common data for AS-REQ fields
    private KDCOptions options;
    private PrincipalName cname;
    private PrincipalName sname;
    private KerberosTime from;
    private KerberosTime till;
    private KerberosTime rtime;
    private HostAddresses addresses;

    // Secret source: can't be changed once assigned, only one (of the two
    // sources) can be set to non-null
    private final char[] password;
    private final KeyTab ktab;

    // Used to create a ENC-TIMESTAMP in the 2nd AS-REQ
    private PAData[] paList;        // PA-DATA from both KRB-ERROR and AS-REP.
                                    // Used by getKeys() only.
                                    // Only AS-REP should be enough per RFC,
                                    // combined in case etypes are different.

    // The generated and received:
    private KrbAsReq req;
    private KrbAsRep rep;

    private static enum State {
        INIT,       // Initialized, can still add more initialization info
        REQ_OK,     // AS-REQ performed
        DESTROYED,  // Destroyed, not usable anymore
    }
    private State state;

    // Called by other constructors
    private void init(PrincipalName cname)
            throws KrbException {
        if (cname.getRealm() == null) {
            cname.setRealm(Config.getInstance().getDefaultRealm());
        }
        this.cname = cname;
        state = State.INIT;
    }

    /**
     * Creates a builder to be used by {@code cname} with existing keys.
     *
     * @param cname the client of the AS-REQ. Must not be null. Might have no
     * realm, where default realm will be used. This realm will be the target
     * realm for AS-REQ. I believe a client should only get initial TGT from
     * its own realm.
     * @param keys must not be null. if empty, might be quite useless.
     * This argument will neither be modified nor stored by the method.
     * @throws KrbException
     */
    public KrbAsReqBuilder(PrincipalName cname, KeyTab ktab)
            throws KrbException {
        init(cname);
        this.ktab = ktab;
        this.password = null;
    }

    /**
     * Creates a builder to be used by {@code cname} with a known password.
     *
     * @param cname the client of the AS-REQ. Must not be null. Might have no
     * realm, where default realm will be used. This realm will be the target
     * realm for AS-REQ. I believe a client should only get initial TGT from
     * its own realm.
     * @param pass must not be null. This argument will neither be modified
     * nor stored by the method.
     * @throws KrbException
     */
    public KrbAsReqBuilder(PrincipalName cname, char[] pass)
            throws KrbException {
        init(cname);
        this.password = pass.clone();
        this.ktab = null;
    }

    /**
     * Retrieves an array of secret keys for the client. This is used when
     * the client supplies password but need keys to act as an acceptor. For
     * an initiator, it must be called after AS-REQ is performed (state is OK).
     * For an acceptor, it can be called when this KrbAsReqBuilder object is
     * constructed (state is INIT).
     * @param isInitiator if the caller is an initiator
     * @return generated keys from password. PA-DATA from server might be used.
     * All "default_tkt_enctypes" keys will be generated, Never null.
     * @throws IllegalStateException if not constructed from a password
     * @throws KrbException
     */
    public EncryptionKey[] getKeys(boolean isInitiator) throws KrbException {
        checkState(isInitiator?State.REQ_OK:State.INIT, "Cannot get keys");
        if (password != null) {
            int[] eTypes = EType.getDefaults("default_tkt_enctypes");
            EncryptionKey[] result = new EncryptionKey[eTypes.length];

            /*
             * Returns an array of keys. Before KrbAsReqBuilder, all etypes
             * use the same salt which is either the default one or a new salt
             * coming from PA-DATA. After KrbAsReqBuilder, each etype uses its
             * own new salt from PA-DATA. For an etype with no PA-DATA new salt
             * at all, what salt should it use?
             *
             * Commonly, the stored keys are only to be used by an acceptor to
             * decrypt service ticket in AP-REQ. Most impls only allow keys
             * from a keytab on acceptor, but unfortunately (?) Java supports
             * acceptor using password. In this case, if the service ticket is
             * encrypted using an etype which we don't have PA-DATA new salt,
             * using the default salt might be wrong (say, case-insensitive
             * user name). Instead, we would use the new salt of another etype.
             */

            String salt = null;     // the saved new salt
            try {
                for (int i=0; i<eTypes.length; i++) {
                    // First round, only calculate those have a PA entry
                    PAData.SaltAndParams snp =
                            PAData.getSaltAndParams(eTypes[i], paList);
                    if (snp != null) {
                        // Never uses a salt for rc4-hmac, it does not use
                        // a salt at all
                        if (eTypes[i] != EncryptedData.ETYPE_ARCFOUR_HMAC &&
                                snp.salt != null) {
                            salt = snp.salt;
                        }
                        result[i] = EncryptionKey.acquireSecretKey(cname,
                                password,
                                eTypes[i],
                                snp);
                    }
                }
                // No new salt from PA, maybe empty, maybe only rc4-hmac
                if (salt == null) salt = cname.getSalt();
                for (int i=0; i<eTypes.length; i++) {
                    // Second round, calculate those with no PA entry
                    if (result[i] == null) {
                        result[i] = EncryptionKey.acquireSecretKey(password,
                                salt,
                                eTypes[i],
                                null);
                    }
                }
            } catch (IOException ioe) {
                KrbException ke = new KrbException(Krb5.ASN1_PARSE_ERROR);
                ke.initCause(ioe);
                throw ke;
            }
            return result;
        } else {
            throw new IllegalStateException("Required password not provided");
        }
    }

    /**
     * Sets or clears options. If cleared, default options will be used
     * at creation time.
     * @param options
     */
    public void setOptions(KDCOptions options) {
        checkState(State.INIT, "Cannot specify options");
        this.options = options;
    }

    /**
     * Sets or clears target. If cleared, KrbAsReq might choose krbtgt
     * for cname realm
     * @param sname
     */
    public void setTarget(PrincipalName sname) {
        checkState(State.INIT, "Cannot specify target");
        this.sname = sname;
    }

    /**
     * Adds or clears addresses. KrbAsReq might add some if empty
     * field not allowed
     * @param addresses
     */
    public void setAddresses(HostAddresses addresses) {
        checkState(State.INIT, "Cannot specify addresses");
        this.addresses = addresses;
    }

    /**
     * Build a KrbAsReq object from all info fed above. Normally this method
     * will be called twice: initial AS-REQ and second with pakey
     * @param key null (initial AS-REQ) or pakey (with preauth)
     * @return the KrbAsReq object
     * @throws KrbException
     * @throws IOException
     */
    private KrbAsReq build(EncryptionKey key) throws KrbException, IOException {
        int[] eTypes;
        if (password != null) {
            eTypes = EType.getDefaults("default_tkt_enctypes");
        } else {
            EncryptionKey[] ks = Krb5Util.keysFromJavaxKeyTab(ktab, cname);
            eTypes = EType.getDefaults("default_tkt_enctypes",
                    ks);
            for (EncryptionKey k: ks) k.destroy();
        }
        return new KrbAsReq(key,
            options,
            cname,
            sname,
            from,
            till,
            rtime,
            eTypes,
            addresses);
    }

    /**
     * Parses AS-REP, decrypts enc-part, retrieves ticket and session key
     * @throws KrbException
     * @throws Asn1Exception
     * @throws IOException
     */
    private KrbAsReqBuilder resolve()
            throws KrbException, Asn1Exception, IOException {
        if (ktab != null) {
            rep.decryptUsingKeyTab(ktab, req, cname);
        } else {
            rep.decryptUsingPassword(password, req, cname);
        }
        if (rep.getPA() != null) {
            if (paList == null || paList.length == 0) {
                paList = rep.getPA();
            } else {
                int extraLen = rep.getPA().length;
                if (extraLen > 0) {
                    int oldLen = paList.length;
                    paList = Arrays.copyOf(paList, paList.length + extraLen);
                    System.arraycopy(rep.getPA(), 0, paList, oldLen, extraLen);
                }
            }
        }
        return this;
    }

    /**
     * Communication until AS-REP or non preauth-related KRB-ERROR received
     * @throws KrbException
     * @throws IOException
     */
    private KrbAsReqBuilder send() throws KrbException, IOException {
        boolean preAuthFailedOnce = false;
        KdcComm comm = new KdcComm(cname.getRealmAsString());
        EncryptionKey pakey = null;
        while (true) {
            try {
                req = build(pakey);
                rep = new KrbAsRep(comm.send(req.encoding()));
                return this;
            } catch (KrbException ke) {
                if (!preAuthFailedOnce && (
                        ke.returnCode() == Krb5.KDC_ERR_PREAUTH_FAILED ||
                        ke.returnCode() == Krb5.KDC_ERR_PREAUTH_REQUIRED)) {
                    if (Krb5.DEBUG) {
                        System.out.println("KrbAsReqBuilder: " +
                                "PREAUTH FAILED/REQ, re-send AS-REQ");
                    }
                    preAuthFailedOnce = true;
                    KRBError kerr = ke.getError();
                    int paEType = PAData.getPreferredEType(kerr.getPA(),
                            EType.getDefaults("default_tkt_enctypes")[0]);
                    if (password == null) {
                        EncryptionKey[] ks = Krb5Util.keysFromJavaxKeyTab(ktab, cname);
                        pakey = EncryptionKey.findKey(paEType, ks);
                        if (pakey != null) pakey = (EncryptionKey)pakey.clone();
                        for (EncryptionKey k: ks) k.destroy();
                    } else {
                        pakey = EncryptionKey.acquireSecretKey(cname,
                                password,
                                paEType,
                                PAData.getSaltAndParams(
                                    paEType, kerr.getPA()));
                    }
                    paList = kerr.getPA();  // Update current paList
                } else {
                    throw ke;
                }
            }
        }
    }

    /**
     * Performs AS-REQ send and AS-REP receive.
     * Maybe a state is needed here, to divide prepare process and getCreds.
     * @throws KrbException
     * @throws Asn1Exception
     * @throws IOException
     */
    public KrbAsReqBuilder action()
            throws KrbException, Asn1Exception, IOException {
        checkState(State.INIT, "Cannot call action");
        state = State.REQ_OK;
        return send().resolve();
    }

    /**
     * Gets Credentials object after action
     */
    public Credentials getCreds() {
        checkState(State.REQ_OK, "Cannot retrieve creds");
        return rep.getCreds();
    }

    /**
     * Gets another type of Credentials after action
     */
    public sun.security.krb5.internal.ccache.Credentials getCCreds() {
        checkState(State.REQ_OK, "Cannot retrieve CCreds");
        return rep.getCCreds();
    }

    /**
     * Destroys the object and clears keys and password info.
     */
    public void destroy() {
        state = State.DESTROYED;
        if (password != null) {
            Arrays.fill(password, (char)0);
        }
    }

    /**
     * Checks if the current state is the specified one.
     * @param st the expected state
     * @param msg error message if state is not correct
     * @throws IllegalStateException if state is not correct
     */
    private void checkState(State st, String msg) {
        if (state != st) {
            throw new IllegalStateException(msg + " at " + st + " state");
        }
    }
}
