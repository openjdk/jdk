/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.security.jgss.ExtendedGSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;
import sun.security.jgss.GSSUtil;
import sun.security.krb5.Config;

public class OkAsDelegate {

    public static void main(String[] args)
            throws Exception {
        OkAsDelegate ok = new OkAsDelegate();
        ok.go(
                Boolean.valueOf(args[0]),   // FORWARDABLE in krb5.conf on?
                Boolean.valueOf(args[1]),   // requestDelegState
                Boolean.valueOf(args[2]),   // requestDelegPolicyState
                Boolean.valueOf(args[3]),   // DelegState in response
                Boolean.valueOf(args[4]),   // DelegPolicyState in response
                Boolean.valueOf(args[5])    // getDelegCred OK?
                );
    }

    void go(
            boolean forwardable,
            boolean requestDelegState,
            boolean requestDelegPolicyState,
            boolean delegState,
            boolean delegPolicyState,
            boolean delegated
            ) throws Exception {
        OneKDC kdc = new OneKDC(null);
        kdc.setPolicy("ok-as-delegate",
                System.getProperty("test.kdc.policy.ok-as-delegate"));
        kdc.writeJAASConf();
        if (!forwardable) {
            // The default OneKDC always includes "forwardable = true"
            // in krb5.conf, override it.
            KDC.saveConfig(OneKDC.KRB5_CONF, kdc,
                    "default_keytab_name = " + OneKDC.KTAB);
            Config.refresh();
        }

        Context c, s;
        c = Context.fromJAAS("client");
        s = Context.fromJAAS("server");

        Oid mech = GSSUtil.GSS_KRB5_MECH_OID;
        if (System.getProperty("test.spnego") != null) {
            mech = GSSUtil.GSS_SPNEGO_MECH_OID;
        }
        c.startAsClient(OneKDC.SERVER, mech);
        ExtendedGSSContext cx = (ExtendedGSSContext)c.x();
        cx.requestCredDeleg(requestDelegState);
        cx.requestDelegPolicy(requestDelegPolicyState);
        s.startAsServer(mech);
        ExtendedGSSContext sx = (ExtendedGSSContext)s.x();

        Context.handshake(c, s);

        if (cx.getCredDelegState() != delegState) {
            throw new Exception("Initiator cred state error");
        }
        if (sx.getCredDelegState() != delegState) {
            throw new Exception("Acceptor cred state error");
        }
        if (cx.getDelegPolicyState() != delegPolicyState) {
            throw new Exception("Initiator cred policy state error");
        }

        GSSCredential cred = null;
        try {
            cred = s.x().getDelegCred();
        } catch (GSSException e) {
            // leave cred as null
        }

        if (delegated != (cred != null)) {
            throw new Exception("get cred error");
        }
    }
}
