/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.io.*;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import sun.security.krb5.*;
import sun.security.krb5.internal.*;
import sun.security.krb5.internal.ccache.CredentialsCache;
import sun.security.krb5.internal.crypto.KeyUsage;
import sun.security.krb5.internal.ktab.KeyTab;
import sun.security.util.DerInputStream;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;

/**
 * A KDC server.
 * <p>
 * Features:
 * <ol>
 * <li> Supports TCP and UDP
 * <li> Supports AS-REQ and TGS-REQ
 * <li> Principal db and other settings hard coded in application
 * <li> Options, say, request preauth or not
 * </ol>
 * Side effects:
 * <ol>
 * <li> The Sun-internal class <code>sun.security.krb5.Config</code> is a
 * singleton and initialized according to Kerberos settings (krb5.conf and
 * java.security.krb5.* system properties). This means once it's initialized
 * it will not automatically notice any changes to these settings (or file
 * changes of krb5.conf). The KDC class normally does not touch these
 * settings (except for the <code>writeKtab()</code> method). However, to make
 * sure nothing ever goes wrong, if you want to make any changes to these
 * settings after calling a KDC method, call <code>Config.refresh()</code> to
 * make sure your changes are reflected in the <code>Config</code> object.
 * </ol>
 * Issues and TODOs:
 * <ol>
 * <li> Generates krb5.conf to be used on another machine, currently the kdc is
 * always localhost
 * <li> More options to KDC, say, error output, say, response nonce !=
 * request nonce
 * </ol>
 * Note: This program uses internal krb5 classes (including reflection to
 * access private fields and methods).
 * <p>
 * Usages:
 * <p>
 * 1. Init and start the KDC:
 * <pre>
 * KDC kdc = KDC.create("REALM.NAME", port, isDaemon);
 * KDC kdc = KDC.create("REALM.NAME");
 * </pre>
 * Here, <code>port</code> is the UDP and TCP port number the KDC server
 * listens on. If zero, a random port is chosen, which you can use getPort()
 * later to retrieve the value.
 * <p>
 * If <code>isDaemon</code> is true, the KDC worker threads will be daemons.
 * <p>
 * The shortcut <code>KDC.create("REALM.NAME")</code> has port=0 and
 * isDaemon=false, and is commonly used in an embedded KDC.
 * <p>
 * 2. Adding users:
 * <pre>
 * kdc.addPrincipal(String principal_name, char[] password);
 * kdc.addPrincipalRandKey(String principal_name);
 * </pre>
 * A service principal's name should look like "host/f.q.d.n". The second form
 * generates a random key. To expose this key, call <code>writeKtab()</code> to
 * save the keys into a keytab file.
 * <p>
 * Note that you need to add the principal name krbtgt/REALM.NAME yourself.
 * <p>
 * Note that you can safely add a principal at any time after the KDC is
 * started and before a user requests info on this principal.
 * <p>
 * 3. Other public methods:
 * <ul>
 * <li> <code>getPort</code>: Returns the port number the KDC uses
 * <li> <code>getRealm</code>: Returns the realm name
 * <li> <code>writeKtab</code>: Writes all principals' keys into a keytab file
 * <li> <code>saveConfig</code>: Saves a krb5.conf file to access this KDC
 * <li> <code>setOption</code>: Sets various options
 * </ul>
 * Read the javadoc for details. Lazy developer can use <code>OneKDC</code>
 * directly.
 */
public class KDC {

    // Under the hood.

    // The random generator to generate random keys (including session keys)
    private static SecureRandom secureRandom = new SecureRandom();
    // Principal db
    private Map<String,char[]> passwords = new HashMap<String,char[]>();
    // Realm name
    private String realm;
    // The request/response job queue
    private BlockingQueue<Job> q = new ArrayBlockingQueue<Job>(100);
    // Service port number
    private int port;
    // Options
    private Map<Option,Object> options = new HashMap<Option,Object>();

    /**
     * Option names, to be expanded forever.
     */
    public static enum Option {
        /**
         * Whether pre-authentication is required. Default Boolean.TRUE
         */
        PREAUTH_REQUIRED,
    };

    /**
     * A standalone KDC server.
     * @param args
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if (args[0].equals("-help") || args[0].equals("--help")) {
                System.out.println("Usage:");
                System.out.println("   java " + KDC.class + "       " +
                        "Start KDC on port 8888");
                return;
            }
        }
        String localhost = "localhost";
        try {
            localhost = InetAddress.getByName(localhost)
                    .getCanonicalHostName();
        } catch (UnknownHostException uhe) {
            ;   // Ignore, localhost is still "localhost"
        }
        KDC kdc = create("RABBIT.HOLE", 8888, false);
        kdc.addPrincipal("dummy", "bogus".toCharArray());
        kdc.addPrincipal("foo", "bar".toCharArray());
        kdc.addPrincipalRandKey("krbtgt/" + kdc.realm);
        kdc.addPrincipalRandKey("server/" + localhost);
        kdc.addPrincipalRandKey("backend/" + localhost);
    }

    /**
     * Creates and starts a KDC running as a daemon on a random port.
     * @param realm the realm name
     * @return the running KDC instance
     * @throws java.io.IOException for any socket creation error
     */
    public static KDC create(String realm) throws IOException {
        return create(realm, 0, true);
    }

    /**
     * Creates and starts a KDC server.
     * @param realm the realm name
     * @param port the TCP and UDP port to listen to. A random port will to
     *        chosen if zero.
     * @param asDaemon if true, KDC threads will be daemons. Otherwise, not.
     * @return the running KDC instance
     * @throws java.io.IOException for any socket creation error
     */
    public static KDC create(String realm, int port, boolean asDaemon) throws IOException {
        return new KDC(realm, port, asDaemon);
    }

    /**
     * Sets an option
     * @param key the option name
     * @param obj the value
     */
    public void setOption(Option key, Object value) {
        options.put(key, value);
    }

    /**
     * Write all principals' keys from multiple KDCsinto one keytab file.
     * Note that the keys for the krbtgt principals will not be written.
     * <p>
     * Attention: This method references krb5.conf settings. If you need to
     * setup krb5.conf later, please call <code>Config.refresh()</code> after
     * the new setting. For example:
     * <pre>
     * KDC.writeKtab("/etc/kdc/ktab", kdc);  // Config is initialized,
     * System.setProperty("java.security.krb5.conf", "/home/mykrb5.conf");
     * Config.refresh();
     * </pre>
     *
     * Inside this method there are 2 places krb5.conf is used:
     * <ol>
     * <li> (Fatal) Generating keys: EncryptionKey.acquireSecretKeys
     * <li> (Has workaround) Creating PrincipalName
     * </ol>
     * @param tab The keytab filename to write to.
     * @throws java.io.IOException for any file output error
     * @throws sun.security.krb5.KrbException for any realm and/or principal
     *         name error.
     */
    public static void writeMultiKtab(String tab, KDC... kdcs)
            throws IOException, KrbException {
        KeyTab ktab = KeyTab.create(tab);
        for (KDC kdc: kdcs) {
            for (String name : kdc.passwords.keySet()) {
                if (name.equals("krbtgt/" + kdc.realm)) {
                    continue;
                }
                ktab.addEntry(new PrincipalName(name + "@" + kdc.realm,
                        name.indexOf('/') < 0 ?
                            PrincipalName.KRB_NT_UNKNOWN :
                            PrincipalName.KRB_NT_SRV_HST),
                            kdc.passwords.get(name));
            }
        }
        ktab.save();
    }

    /**
     * Write a ktab for this KDC.
     */
    public void writeKtab(String tab) throws IOException, KrbException {
        KDC.writeMultiKtab(tab, this);
    }

    /**
     * Adds a new principal to this realm with a given password.
     * @param user the principal's name. For a service principal, use the
     *        form of host/f.q.d.n
     * @param pass the password for the principal
     */
    public void addPrincipal(String user, char[] pass) {
        passwords.put(user, pass);
    }

    /**
     * Adds a new principal to this realm with a random password
     * @param user the principal's name. For a service principal, use the
     *        form of host/f.q.d.n
     */
    public void addPrincipalRandKey(String user) {
        passwords.put(user, randomPassword());
    }

    /**
     * Returns the name of this realm
     * @return the name of this realm
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Writes a krb5.conf for one or more KDC that includes KDC locations for
     * each realm and the default realm name. You can also add extra strings
     * into the file. The method should be called like:
     * <pre>
     *   KDC.saveConfig("krb5.conf", kdc1, kdc2, ..., line1, line2, ...);
     * </pre>
     * Here you can provide one or more kdc# and zero or more line# arguments.
     * The line# will be put after [libdefaults] and before [realms]. Therefore
     * you can append new lines into [libdefaults] and/or create your new
     * stanzas as well. Note that a newline character will be appended to
     * each line# argument.
     * <p>
     * For example:
     * <pre>
     * KDC.saveConfig("krb5.conf", this);
     * </pre>
     * generates:
     * <pre>
     * [libdefaults]
     * default_realm = REALM.NAME
     *
     * [realms]
     *   REALM.NAME = {
     *     kdc = localhost:port_number
     *   }
     * </pre>
     *
     * Another example:
     * <pre>
     * KDC.saveConfig("krb5.conf", kdc1, kdc2, "forwardable = true", "",
     *         "[domain_realm]",
     *         ".kdc1.com = KDC1.NAME");
     * </pre>
     * generates:
     * <pre>
     * [libdefaults]
     * default_realm = KDC1.NAME
     * forwardable = true
     *
     * [domain_realm]
     * .kdc1.com = KDC1.NAME
     *
     * [realms]
     *   KDC1.NAME = {
     *     kdc = localhost:port1
     *   }
     *   KDC2.NAME = {
     *     kdc = localhost:port2
     *   }
     * </pre>
     * @param file the name of the file to write into
     * @param kdc the first (and default) KDC
     * @param more more KDCs or extra lines (in their appearing order) to
     * insert into the krb5.conf file. This method reads each argument's type
     * to determine what it's for. This argument can be empty.
     * @throws java.io.IOException for any file output error
     */
    public static void saveConfig(String file, KDC kdc, Object... more)
            throws IOException {
        File f = new File(file);
        StringBuffer sb = new StringBuffer();
        sb.append("[libdefaults]\ndefault_realm = ");
        sb.append(kdc.realm);
        sb.append("\n");
        for (Object o: more) {
            if (o instanceof String) {
                sb.append(o);
                sb.append("\n");
            }
        }
        sb.append("\n[realms]\n");
        sb.append(realmLineForKDC(kdc));
        for (Object o: more) {
            if (o instanceof KDC) {
                sb.append(realmLineForKDC((KDC)o));
            }
        }
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(sb.toString().getBytes());
        fos.close();
    }

    /**
     * Returns the service port of the KDC server.
     * @return the KDC service port
     */
    public int getPort() {
        return port;
    }

    // Private helper methods

    /**
     * Private constructor, cannot be called outside.
     * @param realm
     */
    private KDC(String realm) {
        this.realm = realm;
    }

    /**
     * A constructor that starts the KDC service also.
     */
    protected KDC(String realm, int port, boolean asDaemon)
            throws IOException {
        this(realm);
        startServer(port, asDaemon);
    }
    /**
     * Generates a 32-char random password
     * @return the password
     */
    private static char[] randomPassword() {
        char[] pass = new char[32];
        for (int i=0; i<32; i++)
            pass[i] = (char)secureRandom.nextInt();
        return pass;
    }

    /**
     * Generates a random key for the given encryption type.
     * @param eType the encryption type
     * @return the generated key
     * @throws sun.security.krb5.KrbException for unknown/unsupported etype
     */
    private static EncryptionKey generateRandomKey(int eType)
            throws KrbException  {
        // Is 32 enough for AES256? I should have generated the keys directly
        // but different cryptos have different rules on what keys are valid.
        char[] pass = randomPassword();
        String algo;
        switch (eType) {
            case EncryptedData.ETYPE_DES_CBC_MD5: algo = "DES"; break;
            case EncryptedData.ETYPE_DES3_CBC_HMAC_SHA1_KD: algo = "DESede"; break;
            case EncryptedData.ETYPE_AES128_CTS_HMAC_SHA1_96: algo = "AES128"; break;
            case EncryptedData.ETYPE_ARCFOUR_HMAC: algo = "ArcFourHMAC"; break;
            case EncryptedData.ETYPE_AES256_CTS_HMAC_SHA1_96: algo = "AES256"; break;
            default: algo = "DES"; break;
        }
        return new EncryptionKey(pass, "NOTHING", algo);    // Silly
    }

    /**
     * Returns the password for a given principal
     * @param p principal
     * @return the password
     * @throws sun.security.krb5.KrbException when the principal is not inside
     *         the database.
     */
    private char[] getPassword(PrincipalName p) throws KrbException {
        char[] pass = passwords.get(p.getNameString());
        if (pass == null) {
            throw new KrbException(Krb5.KDC_ERR_C_PRINCIPAL_UNKNOWN);
        }
        return pass;
    }

    /**
     * Returns the salt string for the principal. For normal users, the
     * concatenation for the realm name and the sections of the principal;
     * for krgtgt/A@B and krbtgt/B@A, always return AB (so that inter-realm
     * principals have the same key).
     * @param p principal
     * @return the salt
     */
    private String getSalt(PrincipalName p) {
        String[] ns = p.getNameStrings();
        if (ns[0].equals("krbtgt") && ns.length > 1) {
            // Shared cross-realm keys must be the same
            if (ns[1].compareTo(realm) < 0) {
                return ns[1] + realm;
            } else {
                return realm + ns[1];
            }
        } else {
            String s = getRealm();
            for (String n: p.getNameStrings()) {
                s += n;
            }
            return s;
        }
    }

    /**
     * Returns the key for a given principal of the given encryption type
     * @param p the principal
     * @param etype the encryption type
     * @return the key
     * @throws sun.security.krb5.KrbException for unknown/unsupported etype
     */
    private EncryptionKey keyForUser(PrincipalName p, int etype) throws KrbException {
        try {
            // Do not call EncryptionKey.acquireSecretKeys(), otherwise
            // the krb5.conf config file would be loaded.
            Method stringToKey = EncryptionKey.class.getDeclaredMethod("stringToKey", char[].class, String.class, byte[].class, Integer.TYPE);
            stringToKey.setAccessible(true);
            return new EncryptionKey((byte[]) stringToKey.invoke(null, getPassword(p), getSalt(p), null, etype), etype, null);
        } catch (InvocationTargetException ex) {
            KrbException ke = (KrbException)ex.getCause();
            throw ke;
        } catch (Exception e) {
            throw new RuntimeException(e);  // should not happen
        }
    }

    /**
     * Processes an incoming request and generates a response.
     * @param in the request
     * @return the response
     * @throws java.lang.Exception for various errors
     */
    private byte[] processMessage(byte[] in) throws Exception {
        if ((in[0] & 0x1f) == Krb5.KRB_AS_REQ)
            return processAsReq(in);
        else
            return processTgsReq(in);
    }

    /**
     * Processes a TGS_REQ and generates a TGS_REP (or KRB_ERROR)
     * @param in the request
     * @return the response
     * @throws java.lang.Exception for various errors
     */
    private byte[] processTgsReq(byte[] in) throws Exception {
        TGSReq tgsReq = new TGSReq(in);
        try {
            System.out.println(realm + "> " + tgsReq.reqBody.cname +
                    " sends TGS-REQ for " +
                    tgsReq.reqBody.sname);
            KDCReqBody body = tgsReq.reqBody;
            int etype = 0;

            // Reflection: PAData[] pas = tgsReq.pAData;
            Field f = KDCReq.class.getDeclaredField("pAData");
            f.setAccessible(true);
            PAData[] pas = (PAData[])f.get(tgsReq);

            Ticket tkt = null;
            EncTicketPart etp = null;
            if (pas == null || pas.length == 0) {
                throw new KrbException(Krb5.KDC_ERR_PADATA_TYPE_NOSUPP);
            } else {
                for (PAData pa: pas) {
                    if (pa.getType() == Krb5.PA_TGS_REQ) {
                        APReq apReq = new APReq(pa.getValue());
                        EncryptedData ed = apReq.authenticator;
                        tkt = apReq.ticket;
                        etype = tkt.encPart.getEType();
                        EncryptionKey kkey = null;
                        if (!tkt.realm.toString().equals(realm)) {
                            if (tkt.sname.getNameString().equals("krbtgt/" + realm)) {
                                kkey = keyForUser(new PrincipalName("krbtgt/" + tkt.realm.toString(), realm), etype);
                            }
                        } else {
                            kkey = keyForUser(tkt.sname, etype);
                        }
                        byte[] bb = tkt.encPart.decrypt(kkey, KeyUsage.KU_TICKET);
                        DerInputStream derIn = new DerInputStream(bb);
                        DerValue der = derIn.getDerValue();
                        etp = new EncTicketPart(der.toByteArray());
                    }
                }
                if (tkt == null) {
                    throw new KrbException(Krb5.KDC_ERR_PADATA_TYPE_NOSUPP);
                }
            }
            EncryptionKey skey = keyForUser(body.sname, etype);
            if (skey == null) {
                throw new KrbException(Krb5.KDC_ERR_SUMTYPE_NOSUPP); // TODO
            }

            // Session key for original ticket, TGT
            EncryptionKey ckey = etp.key;

            // Session key for session with the service
            EncryptionKey key = generateRandomKey(etype);

            // Check time, TODO
            KerberosTime till = body.till;
            if (till == null) {
                throw new KrbException(Krb5.KDC_ERR_NEVER_VALID); // TODO
            } else if (till.isZero()) {
                till = new KerberosTime(new Date().getTime() + 1000 * 3600 * 11);
            }

            boolean[] bFlags = new boolean[Krb5.TKT_OPTS_MAX+1];
            if (body.kdcOptions.get(KDCOptions.FORWARDABLE)) {
                bFlags[Krb5.TKT_OPTS_FORWARDABLE] = true;
            }
            if (body.kdcOptions.get(KDCOptions.FORWARDED) ||
                    etp.flags.get(Krb5.TKT_OPTS_FORWARDED)) {
                bFlags[Krb5.TKT_OPTS_FORWARDED] = true;
            }
            if (body.kdcOptions.get(KDCOptions.RENEWABLE)) {
                bFlags[Krb5.TKT_OPTS_RENEWABLE] = true;
                //renew = new KerberosTime(new Date().getTime() + 1000 * 3600 * 24 * 7);
            }
            if (body.kdcOptions.get(KDCOptions.PROXIABLE)) {
                bFlags[Krb5.TKT_OPTS_PROXIABLE] = true;
            }
            if (body.kdcOptions.get(KDCOptions.POSTDATED)) {
                bFlags[Krb5.TKT_OPTS_POSTDATED] = true;
            }
            if (body.kdcOptions.get(KDCOptions.ALLOW_POSTDATE)) {
                bFlags[Krb5.TKT_OPTS_MAY_POSTDATE] = true;
            }
            bFlags[Krb5.TKT_OPTS_INITIAL] = true;

            TicketFlags tFlags = new TicketFlags(bFlags);
            EncTicketPart enc = new EncTicketPart(
                    tFlags,
                    key,
                    etp.crealm,
                    etp.cname,
                    new TransitedEncoding(1, new byte[0]),  // TODO
                    new KerberosTime(new Date()),
                    body.from,
                    till, body.rtime,
                    body.addresses,
                    null);
            Ticket t = new Ticket(
                    body.crealm,
                    body.sname,
                    new EncryptedData(skey, enc.asn1Encode(), KeyUsage.KU_TICKET)
            );
            EncTGSRepPart enc_part = new EncTGSRepPart(
                    key,
                    new LastReq(new LastReqEntry[]{
                        new LastReqEntry(0, new KerberosTime(new Date().getTime() - 10000))
                    }),
                    body.getNonce(),    // TODO: detect replay
                    new KerberosTime(new Date().getTime() + 1000 * 3600 * 24),
                    // Next 5 and last MUST be same with ticket
                    tFlags,
                    new KerberosTime(new Date()),
                    body.from,
                    till, body.rtime,
                    body.crealm,
                    body.sname,
                    body.addresses
                    );
            EncryptedData edata = new EncryptedData(ckey, enc_part.asn1Encode(), KeyUsage.KU_ENC_TGS_REP_PART_SESSKEY);
            TGSRep tgsRep = new TGSRep(null,
                    etp.crealm,
                    etp.cname,
                    t,
                    edata);
            System.out.println("     Return " + tgsRep.cname
                    + " ticket for " + tgsRep.ticket.sname);

            DerOutputStream out = new DerOutputStream();
            out.write(DerValue.createTag(DerValue.TAG_APPLICATION,
                    true, (byte)Krb5.KRB_TGS_REP), tgsRep.asn1Encode());
            return out.toByteArray();
        } catch (KrbException ke) {
            ke.printStackTrace(System.out);
            KRBError kerr = ke.getError();
            KDCReqBody body = tgsReq.reqBody;
            System.out.println("     Error " + ke.returnCode()
                    + " " +ke.returnCodeMessage());
            if (kerr == null) {
                kerr = new KRBError(null, null, null,
                        new KerberosTime(new Date()),
                        0,
                        ke.returnCode(),
                        body.crealm, body.cname,
                        new Realm(getRealm()), body.sname,
                        KrbException.errorMessage(ke.returnCode()),
                        null);
            }
            return kerr.asn1Encode();
        }
    }

    /**
     * Processes a AS_REQ and generates a AS_REP (or KRB_ERROR)
     * @param in the request
     * @return the response
     * @throws java.lang.Exception for various errors
     */
    private byte[] processAsReq(byte[] in) throws Exception {
        ASReq asReq = new ASReq(in);
        int[] eTypes = null;
        try {
            System.out.println(realm + "> " + asReq.reqBody.cname +
                    " sends AS-REQ for " +
                    asReq.reqBody.sname);

            KDCReqBody body = asReq.reqBody;

            // Reflection: int[] eType = body.eType;
            Field f = KDCReqBody.class.getDeclaredField("eType");
            f.setAccessible(true);
            eTypes = (int[])f.get(body);
            int eType = eTypes[0];

            EncryptionKey ckey = keyForUser(body.cname, eType);
            EncryptionKey skey = keyForUser(body.sname, eType);
            if (ckey == null) {
                throw new KrbException(Krb5.KDC_ERR_ETYPE_NOSUPP);
            }
            if (skey == null) {
                throw new KrbException(Krb5.KDC_ERR_SUMTYPE_NOSUPP); // TODO
            }

            // Session key
            EncryptionKey key = generateRandomKey(eType);
            // Check time, TODO
            KerberosTime till = body.till;
            if (till == null) {
                throw new KrbException(Krb5.KDC_ERR_NEVER_VALID); // TODO
            } else if (till.isZero()) {
                till = new KerberosTime(new Date().getTime() + 1000 * 3600 * 11);
            }
            //body.from
            boolean[] bFlags = new boolean[Krb5.TKT_OPTS_MAX+1];
            if (body.kdcOptions.get(KDCOptions.FORWARDABLE)) {
                bFlags[Krb5.TKT_OPTS_FORWARDABLE] = true;
            }
            if (body.kdcOptions.get(KDCOptions.RENEWABLE)) {
                bFlags[Krb5.TKT_OPTS_RENEWABLE] = true;
                //renew = new KerberosTime(new Date().getTime() + 1000 * 3600 * 24 * 7);
            }
            if (body.kdcOptions.get(KDCOptions.PROXIABLE)) {
                bFlags[Krb5.TKT_OPTS_PROXIABLE] = true;
            }
            if (body.kdcOptions.get(KDCOptions.POSTDATED)) {
                bFlags[Krb5.TKT_OPTS_POSTDATED] = true;
            }
            if (body.kdcOptions.get(KDCOptions.ALLOW_POSTDATE)) {
                bFlags[Krb5.TKT_OPTS_MAY_POSTDATE] = true;
            }
            bFlags[Krb5.TKT_OPTS_INITIAL] = true;

            f = KDCReq.class.getDeclaredField("pAData");
            f.setAccessible(true);
            PAData[] pas = (PAData[])f.get(asReq);
            if (pas == null || pas.length == 0) {
                Object preauth = options.get(Option.PREAUTH_REQUIRED);
                if (preauth == null || preauth.equals(Boolean.TRUE)) {
                    throw new KrbException(Krb5.KDC_ERR_PREAUTH_REQUIRED);
                }
            } else {
                try {
                    Constructor<EncryptedData> ctor = EncryptedData.class.getDeclaredConstructor(DerValue.class);
                    ctor.setAccessible(true);
                    EncryptedData data = ctor.newInstance(new DerValue(pas[0].getValue()));
                    data.decrypt(ckey, KeyUsage.KU_PA_ENC_TS);
                } catch (Exception e) {
                    throw new KrbException(Krb5.KDC_ERR_PREAUTH_FAILED);
                }
                bFlags[Krb5.TKT_OPTS_PRE_AUTHENT] = true;
            }

            TicketFlags tFlags = new TicketFlags(bFlags);
            EncTicketPart enc = new EncTicketPart(
                    tFlags,
                    key,
                    body.crealm,
                    body.cname,
                    new TransitedEncoding(1, new byte[0]),
                    new KerberosTime(new Date()),
                    body.from,
                    till, body.rtime,
                    body.addresses,
                    null);
            Ticket t = new Ticket(
                    body.crealm,
                    body.sname,
                    new EncryptedData(skey, enc.asn1Encode(), KeyUsage.KU_TICKET)
            );
            EncASRepPart enc_part = new EncASRepPart(
                    key,
                    new LastReq(new LastReqEntry[]{
                        new LastReqEntry(0, new KerberosTime(new Date().getTime() - 10000))
                    }),
                    body.getNonce(),    // TODO: detect replay?
                    new KerberosTime(new Date().getTime() + 1000 * 3600 * 24),
                    // Next 5 and last MUST be same with ticket
                    tFlags,
                    new KerberosTime(new Date()),
                    body.from,
                    till, body.rtime,
                    body.crealm,
                    body.sname,
                    body.addresses
                    );
            EncryptedData edata = new EncryptedData(ckey, enc_part.asn1Encode(), KeyUsage.KU_ENC_AS_REP_PART);
            ASRep asRep = new ASRep(null,
                    body.crealm,
                    body.cname,
                    t,
                    edata);

            System.out.println("     Return " + asRep.cname
                    + " ticket for " + asRep.ticket.sname);

            DerOutputStream out = new DerOutputStream();
            out.write(DerValue.createTag(DerValue.TAG_APPLICATION,
                    true, (byte)Krb5.KRB_AS_REP), asRep.asn1Encode());
            byte[] result = out.toByteArray();

            // Added feature:
            // Write the current issuing TGT into a ccache file specified
            // by the system property below.
            String ccache = System.getProperty("test.kdc.save.ccache");
            if (ccache != null) {
                asRep.encKDCRepPart = enc_part;
                sun.security.krb5.internal.ccache.Credentials credentials =
                    new sun.security.krb5.internal.ccache.Credentials(asRep);
                asReq.reqBody.cname.setRealm(getRealm());
                CredentialsCache cache =
                    CredentialsCache.create(asReq.reqBody.cname, ccache);
                if (cache == null) {
                   throw new IOException("Unable to create the cache file " +
                                         ccache);
                }
                cache.update(credentials);
                cache.save();
                new File(ccache).deleteOnExit();
            }

            return result;
        } catch (KrbException ke) {
            ke.printStackTrace(System.out);
            KRBError kerr = ke.getError();
            KDCReqBody body = asReq.reqBody;
            System.out.println("     Error " + ke.returnCode()
                    + " " +ke.returnCodeMessage());
            byte[] eData = null;
            if (kerr == null) {
                if (ke.returnCode() == Krb5.KDC_ERR_PREAUTH_REQUIRED ||
                        ke.returnCode() == Krb5.KDC_ERR_PREAUTH_FAILED) {
                    PAData pa;

                    ETypeInfo2 ei2 = new ETypeInfo2(eTypes[0], null, null);
                    DerOutputStream eid = new DerOutputStream();
                    eid.write(DerValue.tag_Sequence, ei2.asn1Encode());

                    pa = new PAData(Krb5.PA_ETYPE_INFO2, eid.toByteArray());

                    DerOutputStream bytes = new DerOutputStream();
                    bytes.write(new PAData(Krb5.PA_ENC_TIMESTAMP, new byte[0]).asn1Encode());
                    bytes.write(pa.asn1Encode());

                    boolean allOld = true;
                    for (int i: eTypes) {
                        if (i == EncryptedData.ETYPE_AES128_CTS_HMAC_SHA1_96 ||
                                i == EncryptedData.ETYPE_AES256_CTS_HMAC_SHA1_96) {
                            allOld = false;
                            break;
                        }
                    }
                    if (allOld) {
                        ETypeInfo ei = new ETypeInfo(eTypes[0], null);
                        eid = new DerOutputStream();
                        eid.write(DerValue.tag_Sequence, ei.asn1Encode());
                        pa = new PAData(Krb5.PA_ETYPE_INFO, eid.toByteArray());
                        bytes.write(pa.asn1Encode());
                    }
                    DerOutputStream temp = new DerOutputStream();
                    temp.write(DerValue.tag_Sequence, bytes);
                    eData = temp.toByteArray();
                }
                kerr = new KRBError(null, null, null,
                        new KerberosTime(new Date()),
                        0,
                        ke.returnCode(),
                        body.crealm, body.cname,
                        new Realm(getRealm()), body.sname,
                        KrbException.errorMessage(ke.returnCode()),
                        eData);
            }
            return kerr.asn1Encode();
        }
    }

    /**
     * Generates a line for a KDC to put inside [realms] of krb5.conf
     * @param kdc the KDC
     * @return REALM.NAME = { kdc = localhost:port }
     */
    private static String realmLineForKDC(KDC kdc) {
        return String.format("  %s = {\n    kdc = localhost:%d\n  }\n", kdc.realm, kdc.port);
    }

    /**
     * Start the KDC service. This server listens on both UDP and TCP using
     * the same port number. It uses three threads to deal with requests.
     * They can be set to daemon threads if requested.
     * @param port the port number to listen to. If zero, a random available
     *  port no less than 8000 will be chosen and used.
     * @param asDaemon true if the KDC threads should be daemons
     * @throws java.io.IOException for any communication error
     */
    protected void startServer(int port, boolean asDaemon) throws IOException {
        DatagramSocket u1 = null;
        ServerSocket t1 = null;
        if (port > 0) {
            u1 = new DatagramSocket(port, InetAddress.getByName("127.0.0.1"));
            t1 = new ServerSocket(port);
        } else {
            while (true) {
                // Try to find a port number that's both TCP and UDP free
                try {
                    port = 8000 + new java.util.Random().nextInt(10000);
                    u1 = null;
                    u1 = new DatagramSocket(port, InetAddress.getByName("127.0.0.1"));
                    t1 = new ServerSocket(port);
                    break;
                } catch (Exception e) {
                    if (u1 != null) u1.close();
                }
            }
        }
        final DatagramSocket udp = u1;
        final ServerSocket tcp = t1;
        System.out.println("Start KDC on " + port);

        this.port = port;

        // The UDP consumer
        Thread thread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        byte[] inbuf = new byte[8192];
                        DatagramPacket p = new DatagramPacket(inbuf, inbuf.length);
                        udp.receive(p);
                        System.out.println("-----------------------------------------------");
                        System.out.println(">>>>> UDP packet received");
                        q.put(new Job(processMessage(Arrays.copyOf(inbuf, p.getLength())), udp, p));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setDaemon(asDaemon);
        thread.start();

        // The TCP consumer
        thread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        Socket socket = tcp.accept();
                        System.out.println("-----------------------------------------------");
                        System.out.println(">>>>> TCP connection established");
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        byte[] token = new byte[in.readInt()];
                        in.readFully(token);
                        q.put(new Job(processMessage(token), socket, out));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setDaemon(asDaemon);
        thread.start();

        // The dispatcher
        thread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        q.take().send();
                    } catch (Exception e) {
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Helper class to encapsulate a job in a KDC.
     */
    private static class Job {
        byte[] token;           // The received request at creation time and
                                // the response at send time
        Socket s;               // The TCP socket from where the request comes
        DataOutputStream out;   // The OutputStream of the TCP socket
        DatagramSocket s2;      // The UDP socket from where the request comes
        DatagramPacket dp;      // The incoming UDP datagram packet
        boolean useTCP;         // Whether TCP or UDP is used

        // Creates a job object for TCP
        Job(byte[] token, Socket s, DataOutputStream out) {
            useTCP = true;
            this.token = token;
            this.s = s;
            this.out = out;
        }

        // Creates a job object for UDP
        Job(byte[] token, DatagramSocket s2, DatagramPacket dp) {
            useTCP = false;
            this.token = token;
            this.s2 = s2;
            this.dp = dp;
        }

        // Sends the output back to the client
        void send() {
            try {
                if (useTCP) {
                    System.out.println(">>>>> TCP request honored");
                    out.writeInt(token.length);
                    out.write(token);
                    s.close();
                } else {
                    System.out.println(">>>>> UDP request honored");
                    s2.send(new DatagramPacket(token, token.length, dp.getAddress(), dp.getPort()));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
