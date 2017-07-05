/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6879540
 * @summary enable empty password for kerberos 5
 * @modules java.base/sun.net.spi.nameservice
 *          java.base/sun.security.util
 *          java.security.jgss/sun.security.krb5
 *          java.security.jgss/sun.security.krb5.internal
 *          java.security.jgss/sun.security.krb5.internal.ccache
 *          java.security.jgss/sun.security.krb5.internal.crypto
 *          java.security.jgss/sun.security.krb5.internal.ktab
 * @compile -XDignore.symbol.file EmptyPassword.java
 * @run main/othervm EmptyPassword
 */

public class EmptyPassword {

    public static void main(String[] args)
            throws Exception {

        OneKDC kdc = new OneKDC("aes128-cts");
        kdc.addPrincipal("empty", "".toCharArray());

        Context c = Context.fromUserPass("empty", "".toCharArray(), false);
        c.status();
    }
}

