/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.security.auth.module.Krb5LoginModule;
import java.security.Key;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosTicket;
import javax.security.auth.login.LoginContext;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.MessageProp;
import org.ietf.jgss.Oid;
import com.sun.security.jgss.ExtendedGSSContext;
import com.sun.security.jgss.InquireType;
import com.sun.security.jgss.AuthorizationDataEntry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Context of a JGSS subject, encapsulating Subject and GSSContext.
 *
 * Three "constructors", which acquire the (private) credentials and fill
 * it into the Subject:
 *
 * 1. static fromJAAS(): Creates a Context using a JAAS login config entry
 * 2. static fromUserPass(): Creates a Context using a username and a password
 * 3. delegated(): A new context which uses the delegated credentials from a
 *    previously established acceptor Context
 *
 * Two context initiators, which create the GSSContext object inside:
 *
 * 1. startAsClient()
 * 2. startAsServer()
 *
 * Privileged action:
 *    doAs(): Performs an action in the name of the Subject
 *
 * Handshake process:
 *    static handShake(initiator, acceptor)
 *
 * A four-phase typical data communication which includes all four GSS
 * actions (wrap, unwrap, getMic and veryfyMiC):
 *    static transmit(message, from, to)
 */
public class Context {

    private Subject s;
    private ExtendedGSSContext x;
    private boolean f;      // context established?
    private String name;
    private GSSCredential cred;     // see static method delegated().

    static boolean usingStream = false;

    private Context() {}

    /**
     * Using the delegated credentials from a previous acceptor
     * @param c
     */
    public Context delegated() throws Exception {
        Context out = new Context();
        out.s = s;
        out.cred = x.getDelegCred();
        out.name = name + " as " + out.cred.getName().toString();
        return out;
    }

    /**
     * Logins with a JAAS login config entry name
     */
    public static Context fromJAAS(final String name) throws Exception {
        Context out = new Context();
        out.name = name;
        LoginContext lc = new LoginContext(name);
        lc.login();
        out.s = lc.getSubject();
        return out;
    }

    /**
     * Logins with a username and a password, using Krb5LoginModule directly
     * @param storeKey true if key should be saved, used on acceptor side
     */
    public static Context fromUserPass(String user, char[] pass, boolean storeKey)
            throws Exception {
        Context out = new Context();
        out.name = user;
        out.s = new Subject();
        Krb5LoginModule krb5 = new Krb5LoginModule();
        Map<String, String> map = new HashMap<>();
        Map<String, Object> shared = new HashMap<>();

        if (pass != null) {
            map.put("useFirstPass", "true");
            shared.put("javax.security.auth.login.name", user);
            shared.put("javax.security.auth.login.password", pass);
        } else {
            map.put("doNotPrompt", "true");
            map.put("useTicketCache", "true");
            if (user != null) {
                map.put("principal", user);
            }
        }
        if (storeKey) {
            map.put("storeKey", "true");
        }

        krb5.initialize(out.s, null, shared, map);
        krb5.login();
        krb5.commit();
        return out;
    }

    /**
     * Logins with a username and a keytab, using Krb5LoginModule directly
     * @param storeKey true if key should be saved, used on acceptor side
     */
    public static Context fromUserKtab(String user, String ktab, boolean storeKey)
            throws Exception {
        Context out = new Context();
        out.name = user;
        out.s = new Subject();
        Krb5LoginModule krb5 = new Krb5LoginModule();
        Map<String, String> map = new HashMap<>();

        map.put("doNotPrompt", "true");
        map.put("useTicketCache", "false");
        map.put("useKeyTab", "true");
        map.put("keyTab", ktab);
        map.put("principal", user);
        if (storeKey) {
            map.put("storeKey", "true");
        }

        krb5.initialize(out.s, null, null, map);
        krb5.login();
        krb5.commit();
        return out;
    }

    /**
     * Starts as a client
     * @param target communication peer
     * @param mech GSS mech
     * @throws java.lang.Exception
     */
    public void startAsClient(final String target, final Oid mech) throws Exception {
        doAs(new Action() {
            @Override
            public byte[] run(Context me, byte[] dummy) throws Exception {
                GSSManager m = GSSManager.getInstance();
                me.x = (ExtendedGSSContext)m.createContext(
                          target.indexOf('@') < 0 ?
                            m.createName(target, null) :
                            m.createName(target, GSSName.NT_HOSTBASED_SERVICE),
                        mech,
                        cred,
                        GSSContext.DEFAULT_LIFETIME);
                return null;
            }
        }, null);
        f = false;
    }

    /**
     * Starts as a server
     * @param mech GSS mech
     * @throws java.lang.Exception
     */
    public void startAsServer(final Oid mech) throws Exception {
        doAs(new Action() {
            @Override
            public byte[] run(Context me, byte[] dummy) throws Exception {
                GSSManager m = GSSManager.getInstance();
                me.x = (ExtendedGSSContext)m.createContext(m.createCredential(
                        null,
                        GSSCredential.INDEFINITE_LIFETIME,
                        mech,
                        GSSCredential.ACCEPT_ONLY));
                return null;
            }
        }, null);
        f = false;
    }

    /**
     * Accesses the internal GSSContext object. Currently it's used for --
     *
     * 1. calling requestXXX() before handshake
     * 2. accessing source name
     *
     * Note: If the application needs to do any privileged call on this
     * object, please use doAs(). Otherwise, it can be done directly. The
     * methods listed above are all non-privileged calls.
     *
     * @return the GSSContext object
     */
    public ExtendedGSSContext x() {
        return x;
    }

    /**
     * Disposes the GSSContext within
     * @throws org.ietf.jgss.GSSException
     */
    public void dispose() throws GSSException {
        x.dispose();
    }

    /**
     * Does something using the Subject inside
     * @param action the action
     * @param in the input byte
     * @return the output byte
     * @throws java.lang.Exception
     */
    public byte[] doAs(final Action action, final byte[] in) throws Exception {
        try {
            return Subject.doAs(s, new PrivilegedExceptionAction<byte[]>() {

                @Override
                public byte[] run() throws Exception {
                    return action.run(Context.this, in);
                }
            });
        } catch (PrivilegedActionException pae) {
            throw pae.getException();
        }
    }

    /**
     * Prints status of GSSContext and Subject
     * @throws java.lang.Exception
     */
    public void status() throws Exception {
        System.out.println("STATUS OF " + name.toUpperCase());
        try {
            StringBuffer sb = new StringBuffer();
            if (x.getAnonymityState()) {
                sb.append("anon, ");
            }
            if (x.getConfState()) {
                sb.append("conf, ");
            }
            if (x.getCredDelegState()) {
                sb.append("deleg, ");
            }
            if (x.getIntegState()) {
                sb.append("integ, ");
            }
            if (x.getMutualAuthState()) {
                sb.append("mutual, ");
            }
            if (x.getReplayDetState()) {
                sb.append("rep det, ");
            }
            if (x.getSequenceDetState()) {
                sb.append("seq det, ");
            }
            if (x instanceof ExtendedGSSContext) {
                if (((ExtendedGSSContext)x).getDelegPolicyState()) {
                    sb.append("deleg policy, ");
                }
            }
            System.out.println("Context status of " + name + ": " + sb.toString());
            System.out.println(x.getSrcName() + " -> " + x.getTargName());
        } catch (Exception e) {
            ;// Don't care
        }
        System.out.println("=====================================");
        for (Object o : s.getPrivateCredentials()) {
            System.out.println("    " + o.getClass());
            if (o instanceof KerberosTicket) {
                KerberosTicket kt = (KerberosTicket) o;
                System.out.println("        " + kt.getServer() + " for " + kt.getClient());
            } else if (o instanceof KerberosKey) {
                KerberosKey kk = (KerberosKey) o;
                System.out.print("        " + kk.getKeyType() + " " + kk.getVersionNumber() + " " + kk.getAlgorithm() + " ");
                for (byte b : kk.getEncoded()) {
                    System.out.printf("%02X", b & 0xff);
                }
                System.out.println();
            } else if (o instanceof Map) {
                Map map = (Map) o;
                for (Object k : map.keySet()) {
                    System.out.println("        " + k + ": " + map.get(k));
                }
            }
        }
        if (x != null && x instanceof ExtendedGSSContext) {
            if (x.isEstablished()) {
                ExtendedGSSContext ex = (ExtendedGSSContext)x;
                Key k = (Key)ex.inquireSecContext(
                        InquireType.KRB5_GET_SESSION_KEY);
                if (k == null) {
                    throw new Exception("Session key cannot be null");
                }
                System.out.println("Session key is: " + k);
                boolean[] flags = (boolean[])ex.inquireSecContext(
                        InquireType.KRB5_GET_TKT_FLAGS);
                if (flags == null) {
                    throw new Exception("Ticket flags cannot be null");
                }
                System.out.println("Ticket flags is: " + Arrays.toString(flags));
                String authTime = (String)ex.inquireSecContext(
                        InquireType.KRB5_GET_AUTHTIME);
                if (authTime == null) {
                    throw new Exception("Auth time cannot be null");
                }
                System.out.println("AuthTime is: " + authTime);
                if (!x.isInitiator()) {
                    AuthorizationDataEntry[] ad = (AuthorizationDataEntry[])ex.inquireSecContext(
                            InquireType.KRB5_GET_AUTHZ_DATA);
                    System.out.println("AuthzData is: " + Arrays.toString(ad));
                }
            }
        }
    }

    /**
     * Transmits a message from one Context to another. The sender wraps the
     * message and sends it to the receiver. The receiver unwraps it, creates
     * a MIC of the clear text and sends it back to the sender. The sender
     * verifies the MIC against the message sent earlier.
     * @param message the message
     * @param s1 the sender
     * @param s2 the receiver
     * @throws java.lang.Exception If anything goes wrong
     */
    static public void transmit(final String message, final Context s1,
            final Context s2) throws Exception {
        final byte[] messageBytes = message.getBytes();
        System.out.printf("-------------------- TRANSMIT from %s to %s------------------------\n",
                s1.name, s2.name);

        byte[] t = s1.doAs(new Action() {
            @Override
            public byte[] run(Context me, byte[] dummy) throws Exception {
                System.out.println("wrap");
                MessageProp p1 = new MessageProp(0, true);
                byte[] out;
                if (usingStream) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    me.x.wrap(new ByteArrayInputStream(messageBytes), os, p1);
                    out = os.toByteArray();
                } else {
                    out = me.x.wrap(messageBytes, 0, messageBytes.length, p1);
                }
                System.out.println(printProp(p1));
                return out;
            }
        }, null);

        t = s2.doAs(new Action() {
            @Override
            public byte[] run(Context me, byte[] input) throws Exception {
                MessageProp p1 = new MessageProp(0, true);
                byte[] bytes;
                if (usingStream) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    me.x.unwrap(new ByteArrayInputStream(input), os, p1);
                    bytes = os.toByteArray();
                } else {
                    bytes = me.x.unwrap(input, 0, input.length, p1);
                }
                if (!Arrays.equals(messageBytes, bytes))
                    throw new Exception("wrap/unwrap mismatch");
                System.out.println("unwrap");
                System.out.println(printProp(p1));
                p1 = new MessageProp(0, true);
                System.out.println("getMIC");
                if (usingStream) {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    me.x.getMIC(new ByteArrayInputStream(messageBytes), os, p1);
                    bytes = os.toByteArray();
                } else {
                    bytes = me.x.getMIC(messageBytes, 0, messageBytes.length, p1);
                }
                System.out.println(printProp(p1));
                return bytes;
            }
        }, t);

        // Re-unwrap should make p2.isDuplicateToken() returns true
        s1.doAs(new Action() {
            @Override
            public byte[] run(Context me, byte[] input) throws Exception {
                MessageProp p1 = new MessageProp(0, true);
                System.out.println("verifyMIC");
                if (usingStream) {
                    me.x.verifyMIC(new ByteArrayInputStream(input),
                            new ByteArrayInputStream(messageBytes), p1);
                } else {
                    me.x.verifyMIC(input, 0, input.length,
                            messageBytes, 0, messageBytes.length,
                            p1);
                }
                System.out.println(printProp(p1));
                return null;
            }
        }, t);
    }

    /**
     * Returns a string description of a MessageProp object
     * @param prop the object
     * @return the description
     */
    static public String printProp(MessageProp prop) {
        StringBuffer sb = new StringBuffer();
        sb.append("MessagePop: ");
        sb.append("QOP="+ prop.getQOP() + ", ");
        sb.append(prop.getPrivacy()?"privacy, ":"");
        sb.append(prop.isDuplicateToken()?"dup, ":"");
        sb.append(prop.isGapToken()?"gap, ":"");
        sb.append(prop.isOldToken()?"old, ":"");
        sb.append(prop.isUnseqToken()?"unseq, ":"");
        if (prop.getMinorStatus() != 0) {
            sb.append(prop.getMinorString()+ "(" + prop.getMinorStatus()+")");
        }
        return sb.toString();
    }

    /**
     * Handshake (security context establishment process) between two Contexts
     * @param c the initiator
     * @param s the acceptor
     * @throws java.lang.Exception
     */
    static public void handshake(final Context c, final Context s) throws Exception {
        byte[] t = new byte[0];
        while (!c.f || !s.f) {
            t = c.doAs(new Action() {
                @Override
                public byte[] run(Context me, byte[] input) throws Exception {
                    if (me.x.isEstablished()) {
                        me.f = true;
                        System.out.println(c.name + " side established");
                        if (input != null) {
                            throw new Exception("Context established but " +
                                    "still receive token at " + c.name);
                        }
                        return null;
                    } else {
                        System.out.println(c.name + " call initSecContext");
                        if (usingStream) {
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            me.x.initSecContext(new ByteArrayInputStream(input), os);
                            return os.size() == 0 ? null : os.toByteArray();
                        } else {
                            return me.x.initSecContext(input, 0, input.length);
                        }
                    }
                }
            }, t);

            t = s.doAs(new Action() {
                @Override
                public byte[] run(Context me, byte[] input) throws Exception {
                    if (me.x.isEstablished()) {
                        me.f = true;
                        System.out.println(s.name + " side established");
                        if (input != null) {
                            throw new Exception("Context established but " +
                                    "still receive token at " + s.name);
                        }
                        return null;
                    } else {
                        System.out.println(s.name + " called acceptSecContext");
                        if (usingStream) {
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            me.x.acceptSecContext(new ByteArrayInputStream(input), os);
                            return os.size() == 0 ? null : os.toByteArray();
                        } else {
                            return me.x.acceptSecContext(input, 0, input.length);
                        }
                    }
                }
            }, t);
        }
    }
}
