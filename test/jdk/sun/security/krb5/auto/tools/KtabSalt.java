/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8279064
 * @summary New options for ktab to provide non-default salt
 * @requires os.family == "windows"
 * @library /test/lib
 * @library /sun/security/krb5/auto
 * @compile -XDignore.symbol.file KtabSalt.java
 * @run main jdk.test.lib.FileInstaller ../TestHosts TestHosts
 * @run main/othervm -Djdk.net.hosts.file=TestHosts KtabSalt
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

import javax.security.auth.login.LoginException;

public class KtabSalt {

    public static void main(String[] args) throws Exception {

        OneKDC kdc = new OneKDC(null).writeJAASConf();
        kdc.addPrincipal("u1", "password".toCharArray(),
                "this_is_my_salt", null);

        // Using password works
        Context.fromUserPass("u1", "password".toCharArray(), true);

        // Using KDC's keytab works
        kdc.writeKtab("ktab0");
        Context.fromUserKtabAsClient("u1", "ktab0", true);

        // Self-created keytab with default salt does not work
        ktab("-a u1 password -k ktab1");
        Utils.runAndCheckException(
                () -> Context.fromUserKtabAsClient("u1", "ktab1", true),
                LoginException.class);

        // Self-creating keytab with specified salt works
        ktab("-a u1 password -s this_is_my_salt -k ktab2");
        Context.fromUserKtabAsClient("u1", "ktab2", true);

        // Self-creating keytab with salt from KDC works
        ktab("-a u1 password -f -k ktab3");
        Context.fromUserKtabAsClient("u1", "ktab3", true);
    }

    static OutputAnalyzer ktab(String cmdLine) throws Exception {
        String fullCmdLine = String.format(
                "-J-Djava.security.krb5.conf=%s -J-Djdk.net.hosts.file=TestHosts %s",
                OneKDC.KRB5_CONF, cmdLine);
        return SecurityTools.ktab(fullCmdLine).shouldHaveExitValue(0);
    }
}
