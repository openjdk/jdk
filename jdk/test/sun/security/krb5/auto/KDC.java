/*
 * Copyright (c) 2008, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
import sun.net.spi.nameservice.NameService;
import sun.net.spi.nameservice.NameServiceDescriptor;
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
 * System properties recognized:
 * <ul>
 * <li>test.kdc.save.ccache
 * </ul>
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

    // Principal db. principal -> pass. A case-insensitive TreeMap is used
    // so that even if the client provides a name with different case, the KDC
    // can still locate the principal and give back correct salt.
    private TreeMap<String,char[]> passwords = new TreeMap<>
            (String.CASE_INSENSITIVE_ORDER);

    // Realm name
    private String realm;
    // KDC
    private String kdc;
    // Service port number
    private int port;
    // The request/response job queue
    private BlockingQueue<Job> q = new ArrayBlockingQueue<>(100);
    // Options
    private Map<Option,Object> options = new HashMap<>();

    private Thread thread1, thread2, thread3;
    DatagramSocket u1 = null;
    ServerSocket t1 = null;

    /**
     * Option names, to be expanded forever.
     */
    public static enum Option {
        /**
         * Whether pre-authentication is required. Default Boolean.TRUE
         */
        PREAUTH_REQUIRED,
        /**
         * Only issue TGT in RC4
         */
        ONLY_RC4_TGT,
        /**
         * Use RC4 as the first in preauth
         */
        RC4_FIRST_PREAUTH,
        /**
         * Use only one preauth, so that some keys are not easy to generate
         */
        ONLY_ONE_PREAUTH,
        /**
         * Set all name-type to a value in response
         */
        RESP_NT,
        /**
         * Multiple ETYPE-INFO-ENTRY with same etype but different salt
         */
        DUP_ETYPE,
        /**
         * What backend server can be delegated to
         */
        OK_AS_DELEGATE,
        /**
         * Allow S4U2self, List<String> of middle servers.
         * If not set, means KDC does not understand S4U2self at all, therefore
         * would ignore any PA-FOR-USER request and send a ticket using the
         * cname of teh requestor. If set, it returns FORWARDABLE tickets to
         * a server with its name in the list
         */
        ALLOW_S4U2SELF,
        /**
         * Allow S4U2proxy, Map<String,List<String>> of middle servers to
         * backends. If not set or a backend not in a server's list,
         * Krb5.KDC_ERR_POLICY will be send for S4U2proxy request.
         */
        ALLOW_S4U2PROXY,
    };

    static {
        System.setProperty("sun.net.spi.nameservice.provider.1", "ns,mock");
    }

    /**
     * A standalone KDC server.
     */
    public static void main(String[] args) throws Exception {
        KDC kdc = create("RABBIT.HOLE", "kdc.rabbit.hole", 0, false);
        kdc.addPrincipal("dummy", "bogus".toCharArray());
        kdc.addPrincipal("foo", "bar".toCharArray());
        kdc.addPrincipalRandKey("krbtgt/RABBIT.HOLE");
        kdc.addPrincipalRandKey("server/host.rabbit.hole");
        kdc.addPrincipalRandKey("backend/host.rabbit.hole");
        KDC.saveConfig("krb5.conf", kdc, "forwardable = true");
    }

    /**
     * Creates and starts a KDC running as a daemon on a random port.
     * @param realm the realm name
     * @return the running KDC instance
     * @throws java.io.IOException for any socket creation error
     */
    public static KDC create(String realm) throws IOException {
        return create(realm, "kdc." + realm.toLowerCase(), 0, true);
    }

    public static KDC existing(String realm, String kdc, int port) {
        KDC k = new KDC(realm, kdc);
        k.port = port;
        return k;
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
    public static KDC create(String realm, String kdc, int port, boolean asDaemon) throws IOException {
        return new KDC(realm, kdc, port, asDaemon);
    }

    /**
     * Sets an option
     * @param key the option name
     * @param obj the value
     */
    public void setOption(Option key, Object value) {
        if (value == null) {
            options.remove(key);
        } else {
            options.put(key, value);
        }
    }

    /**
     * Writes or appends keys into a keytab.
     * <p>
     * Attention: This is the most basic one of a series of methods below on
     * keytab creation or modification. All these methods reference krb5.conf
     * settings. If you need to modify krb5.conf or switch to another krb5.conf
     * later, please call <code>Config.refresh()</code> again. For example:
     * <pre>
     * kdc.writeKtab("/etc/kdc/ktab", true);  // Config is initialized,
     * System.setProperty("java.security.krb5.conf", "/home/mykrb5.conf");
     * Config.refresh();
     * </pre>
     * Inside this method there are 2 places krb5.conf is used:
     * <ol>
     * <li> (Fatal) Generating keys: EncryptionKey.acquireSecretKeys
     * <li> (Has workaround) Creating PrincipalName
     * </ol>
     * @param tab the keytab file name
     * @param append true if append, otherwise, overwrite.
     * @param names the names to write into, write all if names is empty
     */
    public void writeKtab(String tab, boolean append, String... names)
            throws IOException, KrbException {
        KeyTab ktab = append ? KeyTab.getInstance(tab) : KeyTab.create(tab);
        Iterable<String> entries =
                (names.length != 0) ? Arrays.asList(names): passwords.keySet();
        for (String name : entries) {
            char[] pass = passwords.get(name);
            int kvno = 0;
            if (Character.isDigit(pass[pass.length-1])) {
                kvno = pass[pass.length-1] - '0';
            }
            PrincipalName pn = new PrincipalName(name,
                        name.indexOf('/') < 0 ?
                            PrincipalName.KRB_NT_UNKNOWN :
                            PrincipalName.KRB_NT_SRV_HST);
            ktab.addEntry(pn,
                        getSalt(pn),
                        pass,
                        kvno,
                        true);
        }
        ktab.save();
    }

    /**
     * Writes all principals' keys from multiple KDCs into one keytab file.
     * @throws java.io.IOException for any file output error
     * @throws sun.security.krb5.KrbException for any realm and/or principal
     *         name error.
     */
    public static void writeMultiKtab(String tab, KDC... kdcs)
            throws IOException, KrbException {
        KeyTab.create(tab).save();      // Empty the old keytab
        appendMultiKtab(tab, kdcs);
    }

    /**
     * Appends all principals' keys from multiple KDCs to one keytab file.
     */
    public static void appendMultiKtab(String tab, KDC... kdcs)
            throws IOException, KrbException {
        for (KDC kdc: kdcs) {
            kdc.writeKtab(tab, true);
        }
    }

    /**
     * Write a ktab for this KDC.
     */
    public void writeKtab(String tab) throws IOException, KrbException {
        writeKtab(tab, false);
    }

    /**
     * Appends keys in this KDC to a ktab.
     */
    public void appendKtab(String tab) throws IOException, KrbException {
        writeKtab(tab, true);
    }

    /**
     * Adds a new principal to this realm with a given password.
     * @param user the principal's name. For a service principal, use the
     *        form of host/f.q.d.n
     * @param pass the password for the principal
     */
    public void addPrincipal(String user, char[] pass) {
        if (user.indexOf('@') < 0) {
            user = user + "@" + realm;
        }
        passwords.put(user, pass);
    }

    /**
     * Adds a new principal to this realm with a random password
     * @param user the principal's name. For a service principal, use the
     *        form of host/f.q.d.n
     */
    public void addPrincipalRandKey(String user) {
        addPrincipal(user, randomPassword());
    }

    /**
     * Returns the name of this realm
     * @return the name of this realm
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Returns the name of kdc
     * @return the name of kdc
     */
    public String getKDC() {
        return kdc;
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
     *     kdc = host:port_number
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
     *     kdc = host:port1
     *   }
     *   KDC2.NAME = {
     *     kdc = host:port2
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
    private KDC(String realm, String kdc) {
        this.realm = realm;
        this.kdc = kdc;
    }

    /**
     * A constructor that starts the KDC service also.
     */
    protected KDC(String realm, String kdc, int port, boolean asDaemon)
            throws IOException {
        this(realm, kdc);
        startServer(port, asDaemon);
    }
    /**
     * Generates a 32-char random password
     * @return the password
     */
    private static char[] randomPassword() {
        char[] pass = new char[32];
        for (int i=0; i<31; i++)
            pass[i] = (char)secureRandom.nextInt();
        // The last char cannot be a number, otherwise, keyForUser()
        // believes it's a sign of kvno
        pass[31] = 'Z';
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
    private char[] getPassword(PrincipalName p, boolean server)
            throws KrbException {
        String pn = p.toString();
        if (p.getRealmString() == null) {
            pn = pn + "@" + getRealm();
        }
        char[] pass = passwords.get(pn);
        if (pass == null) {
            throw new KrbException(server?
                Krb5.KDC_ERR_S_PRINCIPAL_UNKNOWN:
                Krb5.KDC_ERR_C_PRINCIPAL_UNKNOWN, pn.toString());
        }
        return pass;
    }

    /**
     * Returns the salt string for the principal.
     * @param p principal
     * @return the salt
     */
    protected String getSalt(PrincipalName p) {
        String pn = p.toString();
        if (p.getRealmString() == null) {
            pn = pn + "@" + getRealm();
        }
        if (passwords.containsKey(pn)) {
            try {
                // Find the principal name with correct case.
                p = new PrincipalName(passwords.ceilingEntry(pn).getKey());
            } catch (RealmException re) {
                // Won't happen
            }
        }
        String s = p.getRealmString();
        if (s == null) s = getRealm();
        for (String n: p.getNameStrings()) {
            s += n;
        }
        return s;
    }

    /**
     * Returns the key for a given principal of the given encryption type
     * @param p the principal
     * @param etype the encryption type
     * @param server looking for a server principal?
     * @return the key
     * @throws sun.security.krb5.KrbException for unknown/unsupported etype
     */
    private EncryptionKey keyForUser(PrincipalName p, int etype, boolean server)
            throws KrbException {
        try {
            // Do not call EncryptionKey.acquireSecretKeys(), otherwise
            // the krb5.conf config file would be loaded.
            Integer kvno = null;
            // For service whose password ending with a number, use it as kvno.
            // Kvno must be postive.
            if (p.toString().indexOf('/') > 0) {
                char[] pass = getPassword(p, server);
                if (Character.isDigit(pass[pass.length-1])) {
                    kvno = pass[pass.length-1] - '0';
                }
            }
            return new EncryptionKey(EncryptionKeyDotStringToKey(
                    getPassword(p, server), getSalt(p), null, etype),
                    etype, kvno);
        } catch (KrbException ke) {
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
    protected byte[] processMessage(byte[] in) throws Exception {
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
    protected byte[] processTgsReq(byte[] in) throws Exception {
        TGSReq tgsReq = new TGSReq(in);
        PrincipalName service = tgsReq.reqBody.sname;
        if (options.containsKey(KDC.Option.RESP_NT)) {
            service = new PrincipalName((int)options.get(KDC.Option.RESP_NT),
                    service.getNameStrings(), service.getRealm());
        }
        try {
            System.out.println(realm + "> " + tgsReq.reqBody.cname +
                    " sends TGS-REQ for " +
                    service);
            KDCReqBody body = tgsReq.reqBody;
            int[] eTypes = KDCReqBodyDotEType(body);
            int e2 = eTypes[0];     // etype for outgoing session key
            int e3 = eTypes[0];     // etype for outgoing ticket

            PAData[] pas = KDCReqDotPAData(tgsReq);

            Ticket tkt = null;
            EncTicketPart etp = null;

            PrincipalName cname = null;
            boolean allowForwardable = true;

            if (pas == null || pas.length == 0) {
                throw new KrbException(Krb5.KDC_ERR_PADATA_TYPE_NOSUPP);
            } else {
                PrincipalName forUserCName = null;
                for (PAData pa: pas) {
                    if (pa.getType() == Krb5.PA_TGS_REQ) {
                        APReq apReq = new APReq(pa.getValue());
                        EncryptedData ed = apReq.authenticator;
                        tkt = apReq.ticket;
                        int te = tkt.encPart.getEType();
                        EncryptionKey kkey = keyForUser(tkt.sname, te, true);
                        byte[] bb = tkt.encPart.decrypt(kkey, KeyUsage.KU_TICKET);
                        DerInputStream derIn = new DerInputStream(bb);
                        DerValue der = derIn.getDerValue();
                        etp = new EncTicketPart(der.toByteArray());
                        // Finally, cname will be overwritten by PA-FOR-USER
                        // if it exists.
                        cname = etp.cname;
                        System.out.println(realm + "> presenting a ticket of "
                                + etp.cname + " to " + tkt.sname);
                    } else if (pa.getType() == Krb5.PA_FOR_USER) {
                        if (options.containsKey(Option.ALLOW_S4U2SELF)) {
                            PAForUserEnc p4u = new PAForUserEnc(
                                    new DerValue(pa.getValue()), null);
                            forUserCName = p4u.name;
                            System.out.println(realm + "> presenting a PA_FOR_USER "
                                    + " in the name of " + p4u.name);
                        }
                    }
                }
                if (forUserCName != null) {
                    List<String> names = (List<String>)options.get(Option.ALLOW_S4U2SELF);
                    if (!names.contains(cname.toString())) {
                        // Mimic the normal KDC behavior. When a server is not
                        // allowed to send S4U2self, do not send an error.
                        // Instead, send a ticket which is useless later.
                        allowForwardable = false;
                    }
                    cname = forUserCName;
                }
                if (tkt == null) {
                    throw new KrbException(Krb5.KDC_ERR_PADATA_TYPE_NOSUPP);
                }
            }

            // Session key for original ticket, TGT
            EncryptionKey ckey = etp.key;

            // Session key for session with the service
            EncryptionKey key = generateRandomKey(e2);

            // Check time, TODO
            KerberosTime till = body.till;
            if (till == null) {
                throw new KrbException(Krb5.KDC_ERR_NEVER_VALID); // TODO
            } else if (till.isZero()) {
                till = new KerberosTime(new Date().getTime() + 1000 * 3600 * 11);
            }

            boolean[] bFlags = new boolean[Krb5.TKT_OPTS_MAX+1];
            if (body.kdcOptions.get(KDCOptions.FORWARDABLE)
                    && allowForwardable) {
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
            if (body.kdcOptions.get(KDCOptions.CNAME_IN_ADDL_TKT)) {
                if (!options.containsKey(Option.ALLOW_S4U2PROXY)) {
                    // Don't understand CNAME_IN_ADDL_TKT
                    throw new KrbException(Krb5.KDC_ERR_BADOPTION);
                } else {
                    Map<String,List<String>> map = (Map<String,List<String>>)
                            options.get(Option.ALLOW_S4U2PROXY);
                    Ticket second = KDCReqBodyDotFirstAdditionalTicket(body);
                    EncryptionKey key2 = keyForUser(second.sname, second.encPart.getEType(), true);
                    byte[] bb = second.encPart.decrypt(key2, KeyUsage.KU_TICKET);
                    DerInputStream derIn = new DerInputStream(bb);
                    DerValue der = derIn.getDerValue();
                    EncTicketPart tktEncPart = new EncTicketPart(der.toByteArray());
                    if (!tktEncPart.flags.get(Krb5.TKT_OPTS_FORWARDABLE)) {
                        //throw new KrbException(Krb5.KDC_ERR_BADOPTION);
                    }
                    PrincipalName client = tktEncPart.cname;
                    System.out.println(realm + "> and an additional ticket of "
                            + client + " to " + second.sname);
                    if (map.containsKey(cname.toString())) {
                        if (map.get(cname.toString()).contains(service.toString())) {
                            System.out.println(realm + "> S4U2proxy OK");
                        } else {
                            throw new KrbException(Krb5.KDC_ERR_BADOPTION);
                        }
                    } else {
                        throw new KrbException(Krb5.KDC_ERR_BADOPTION);
                    }
                    cname = client;
                }
            }

            String okAsDelegate = (String)options.get(Option.OK_AS_DELEGATE);
            if (okAsDelegate != null && (
                    okAsDelegate.isEmpty() ||
                    okAsDelegate.contains(service.getNameString()))) {
                bFlags[Krb5.TKT_OPTS_DELEGATE] = true;
            }
            bFlags[Krb5.TKT_OPTS_INITIAL] = true;

            TicketFlags tFlags = new TicketFlags(bFlags);
            EncTicketPart enc = new EncTicketPart(
                    tFlags,
                    key,
                    cname,
                    new TransitedEncoding(1, new byte[0]),  // TODO
                    new KerberosTime(new Date()),
                    body.from,
                    till, body.rtime,
                    body.addresses != null  // always set caddr
                            ? body.addresses
                            : new HostAddresses(
                                new InetAddress[]{InetAddress.getLocalHost()}),
                    null);
            EncryptionKey skey = keyForUser(service, e3, true);
            if (skey == null) {
                throw new KrbException(Krb5.KDC_ERR_SUMTYPE_NOSUPP); // TODO
            }
            Ticket t = new Ticket(
                    service,
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
                    service,
                    body.addresses != null  // always set caddr
                            ? body.addresses
                            : new HostAddresses(
                                new InetAddress[]{InetAddress.getLocalHost()})
                    );
            EncryptedData edata = new EncryptedData(ckey, enc_part.asn1Encode(), KeyUsage.KU_ENC_TGS_REP_PART_SESSKEY);
            TGSRep tgsRep = new TGSRep(null,
                    cname,
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
                        body.cname,
                        service,
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
    protected byte[] processAsReq(byte[] in) throws Exception {
        ASReq asReq = new ASReq(in);
        int[] eTypes = null;
        List<PAData> outPAs = new ArrayList<>();

        PrincipalName service = asReq.reqBody.sname;
        if (options.containsKey(KDC.Option.RESP_NT)) {
            service = new PrincipalName(service.getNameStrings(),
                    (int)options.get(KDC.Option.RESP_NT));
        }
        try {
            System.out.println(realm + "> " + asReq.reqBody.cname +
                    " sends AS-REQ for " +
                    service);

            KDCReqBody body = asReq.reqBody;

            eTypes = KDCReqBodyDotEType(body);
            int eType = eTypes[0];

            EncryptionKey ckey = keyForUser(body.cname, eType, false);
            EncryptionKey skey = keyForUser(service, eType, true);

            if (options.containsKey(KDC.Option.ONLY_RC4_TGT)) {
                int tgtEType = EncryptedData.ETYPE_ARCFOUR_HMAC;
                boolean found = false;
                for (int i=0; i<eTypes.length; i++) {
                    if (eTypes[i] == tgtEType) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new KrbException(Krb5.KDC_ERR_ETYPE_NOSUPP);
                }
                skey = keyForUser(service, tgtEType, true);
            }
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

            // Creating PA-DATA
            DerValue[] pas2 = null, pas = null;
            if (options.containsKey(KDC.Option.DUP_ETYPE)) {
                int n = (Integer)options.get(KDC.Option.DUP_ETYPE);
                switch (n) {
                    case 1:     // customer's case in 7067974
                        pas2 = new DerValue[] {
                            new DerValue(new ETypeInfo2(1, null, null).asn1Encode()),
                            new DerValue(new ETypeInfo2(1, "", null).asn1Encode()),
                            new DerValue(new ETypeInfo2(1, realm, new byte[]{1}).asn1Encode()),
                        };
                        pas = new DerValue[] {
                            new DerValue(new ETypeInfo(1, null).asn1Encode()),
                            new DerValue(new ETypeInfo(1, "").asn1Encode()),
                            new DerValue(new ETypeInfo(1, realm).asn1Encode()),
                        };
                        break;
                    case 2:     // we still reject non-null s2kparams and prefer E2 over E
                        pas2 = new DerValue[] {
                            new DerValue(new ETypeInfo2(1, realm, new byte[]{1}).asn1Encode()),
                            new DerValue(new ETypeInfo2(1, null, null).asn1Encode()),
                            new DerValue(new ETypeInfo2(1, "", null).asn1Encode()),
                        };
                        pas = new DerValue[] {
                            new DerValue(new ETypeInfo(1, realm).asn1Encode()),
                            new DerValue(new ETypeInfo(1, null).asn1Encode()),
                            new DerValue(new ETypeInfo(1, "").asn1Encode()),
                        };
                        break;
                    case 3:     // but only E is wrong
                        pas = new DerValue[] {
                            new DerValue(new ETypeInfo(1, realm).asn1Encode()),
                            new DerValue(new ETypeInfo(1, null).asn1Encode()),
                            new DerValue(new ETypeInfo(1, "").asn1Encode()),
                        };
                        break;
                    case 4:     // we also ignore rc4-hmac
                        pas = new DerValue[] {
                            new DerValue(new ETypeInfo(23, "ANYTHING").asn1Encode()),
                            new DerValue(new ETypeInfo(1, null).asn1Encode()),
                            new DerValue(new ETypeInfo(1, "").asn1Encode()),
                        };
                        break;
                    case 5:     // "" should be wrong, but we accept it now
                                // See s.s.k.internal.PAData$SaltAndParams
                        pas = new DerValue[] {
                            new DerValue(new ETypeInfo(1, "").asn1Encode()),
                            new DerValue(new ETypeInfo(1, null).asn1Encode()),
                        };
                        break;
                }
            } else {
                int[] epas = eTypes;
                if (options.containsKey(KDC.Option.RC4_FIRST_PREAUTH)) {
                    for (int i=1; i<epas.length; i++) {
                        if (epas[i] == EncryptedData.ETYPE_ARCFOUR_HMAC) {
                            epas[i] = epas[0];
                            epas[0] = EncryptedData.ETYPE_ARCFOUR_HMAC;
                            break;
                        }
                    };
                } else if (options.containsKey(KDC.Option.ONLY_ONE_PREAUTH)) {
                    epas = new int[] { eTypes[0] };
                }
                pas2 = new DerValue[epas.length];
                for (int i=0; i<epas.length; i++) {
                    pas2[i] = new DerValue(new ETypeInfo2(
                            epas[i],
                            epas[i] == EncryptedData.ETYPE_ARCFOUR_HMAC ?
                                null : getSalt(body.cname),
                            null).asn1Encode());
                }
                boolean allOld = true;
                for (int i: eTypes) {
                    if (i == EncryptedData.ETYPE_AES128_CTS_HMAC_SHA1_96 ||
                            i == EncryptedData.ETYPE_AES256_CTS_HMAC_SHA1_96) {
                        allOld = false;
                        break;
                    }
                }
                if (allOld) {
                    pas = new DerValue[epas.length];
                    for (int i=0; i<epas.length; i++) {
                        pas[i] = new DerValue(new ETypeInfo(
                                epas[i],
                                epas[i] == EncryptedData.ETYPE_ARCFOUR_HMAC ?
                                    null : getSalt(body.cname)
                                ).asn1Encode());
                    }
                }
            }

            DerOutputStream eid;
            if (pas2 != null) {
                eid = new DerOutputStream();
                eid.putSequence(pas2);
                outPAs.add(new PAData(Krb5.PA_ETYPE_INFO2, eid.toByteArray()));
            }
            if (pas != null) {
                eid = new DerOutputStream();
                eid.putSequence(pas);
                outPAs.add(new PAData(Krb5.PA_ETYPE_INFO, eid.toByteArray()));
            }

            PAData[] inPAs = KDCReqDotPAData(asReq);
            if (inPAs == null || inPAs.length == 0) {
                Object preauth = options.get(Option.PREAUTH_REQUIRED);
                if (preauth == null || preauth.equals(Boolean.TRUE)) {
                    throw new KrbException(Krb5.KDC_ERR_PREAUTH_REQUIRED);
                }
            } else {
                try {
                    EncryptedData data = newEncryptedData(new DerValue(inPAs[0].getValue()));
                    EncryptionKey pakey = keyForUser(body.cname, data.getEType(), false);
                    data.decrypt(pakey, KeyUsage.KU_PA_ENC_TS);
                } catch (Exception e) {
                    throw new KrbException(Krb5.KDC_ERR_PREAUTH_FAILED);
                }
                bFlags[Krb5.TKT_OPTS_PRE_AUTHENT] = true;
            }

            TicketFlags tFlags = new TicketFlags(bFlags);
            EncTicketPart enc = new EncTicketPart(
                    tFlags,
                    key,
                    body.cname,
                    new TransitedEncoding(1, new byte[0]),
                    new KerberosTime(new Date()),
                    body.from,
                    till, body.rtime,
                    body.addresses,
                    null);
            Ticket t = new Ticket(
                    service,
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
                    service,
                    body.addresses
                    );
            EncryptedData edata = new EncryptedData(ckey, enc_part.asn1Encode(), KeyUsage.KU_ENC_AS_REP_PART);
            ASRep asRep = new ASRep(
                    outPAs.toArray(new PAData[outPAs.size()]),
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
                CredentialsCache cache =
                    CredentialsCache.create(asReq.reqBody.cname, ccache);
                if (cache == null) {
                   throw new IOException("Unable to create the cache file " +
                                         ccache);
                }
                cache.update(credentials);
                cache.save();
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
                    DerOutputStream bytes = new DerOutputStream();
                    bytes.write(new PAData(Krb5.PA_ENC_TIMESTAMP, new byte[0]).asn1Encode());
                    for (PAData p: outPAs) {
                        bytes.write(p.asn1Encode());
                    }
                    DerOutputStream temp = new DerOutputStream();
                    temp.write(DerValue.tag_Sequence, bytes);
                    eData = temp.toByteArray();
                }
                kerr = new KRBError(null, null, null,
                        new KerberosTime(new Date()),
                        0,
                        ke.returnCode(),
                        body.cname,
                        service,
                        KrbException.errorMessage(ke.returnCode()),
                        eData);
            }
            return kerr.asn1Encode();
        }
    }

    /**
     * Generates a line for a KDC to put inside [realms] of krb5.conf
     * @param kdc the KDC
     * @return REALM.NAME = { kdc = host:port }
     */
    private static String realmLineForKDC(KDC kdc) {
        return String.format("%s = {\n    kdc = %s:%d\n}\n",
                kdc.realm,
                kdc.kdc,
                kdc.port);
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
        thread1 = new Thread() {
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
        thread1.setDaemon(asDaemon);
        thread1.start();

        // The TCP consumer
        thread2 = new Thread() {
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
        thread2.setDaemon(asDaemon);
        thread2.start();

        // The dispatcher
        thread3 = new Thread() {
            public void run() {
                while (true) {
                    try {
                        q.take().send();
                    } catch (Exception e) {
                    }
                }
            }
        };
        thread3.setDaemon(true);
        thread3.start();
    }

    public void terminate() {
        try {
            thread1.stop();
            thread2.stop();
            thread3.stop();
            u1.close();
            t1.close();
        } catch (Exception e) {
            // OK
        }
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

    public static class KDCNameService implements NameServiceDescriptor {
        @Override
        public NameService createNameService() throws Exception {
            NameService ns = new NameService() {
                @Override
                public InetAddress[] lookupAllHostAddr(String host)
                        throws UnknownHostException {
                    // Everything is localhost
                    return new InetAddress[]{
                        InetAddress.getByAddress(host, new byte[]{127,0,0,1})
                    };
                }
                @Override
                public String getHostByAddr(byte[] addr)
                        throws UnknownHostException {
                    // No reverse lookup, PrincipalName use original string
                    throw new UnknownHostException();
                }
            };
            return ns;
        }

        @Override
        public String getProviderName() {
            return "mock";
        }

        @Override
        public String getType() {
            return "ns";
        }
    }

    // Calling private methods thru reflections
    private static final Field getPADataField;
    private static final Field getEType;
    private static final Constructor<EncryptedData> ctorEncryptedData;
    private static final Method stringToKey;
    private static final Field getAddlTkt;

    static {
        try {
            ctorEncryptedData = EncryptedData.class.getDeclaredConstructor(DerValue.class);
            ctorEncryptedData.setAccessible(true);
            getPADataField = KDCReq.class.getDeclaredField("pAData");
            getPADataField.setAccessible(true);
            getEType = KDCReqBody.class.getDeclaredField("eType");
            getEType.setAccessible(true);
            stringToKey = EncryptionKey.class.getDeclaredMethod(
                    "stringToKey",
                    char[].class, String.class, byte[].class, Integer.TYPE);
            stringToKey.setAccessible(true);
            getAddlTkt = KDCReqBody.class.getDeclaredField("additionalTickets");
            getAddlTkt.setAccessible(true);
        } catch (NoSuchFieldException nsfe) {
            throw new AssertionError(nsfe);
        } catch (NoSuchMethodException nsme) {
            throw new AssertionError(nsme);
        }
    }
    private EncryptedData newEncryptedData(DerValue der) {
        try {
            return ctorEncryptedData.newInstance(der);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    private static PAData[] KDCReqDotPAData(KDCReq req) {
        try {
            return (PAData[])getPADataField.get(req);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    private static int[] KDCReqBodyDotEType(KDCReqBody body) {
        try {
            return (int[]) getEType.get(body);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    private static byte[] EncryptionKeyDotStringToKey(char[] password, String salt,
            byte[] s2kparams, int keyType) throws KrbCryptoException {
        try {
            return (byte[])stringToKey.invoke(
                    null, password, salt, s2kparams, keyType);
        } catch (InvocationTargetException ex) {
            throw (KrbCryptoException)ex.getCause();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
    private static Ticket KDCReqBodyDotFirstAdditionalTicket(KDCReqBody body) {
        try {
            return ((Ticket[])getAddlTkt.get(body))[0];
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
