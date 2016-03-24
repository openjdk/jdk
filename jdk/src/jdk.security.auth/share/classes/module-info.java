/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

module jdk.security.auth {
    requires public java.naming;
    requires java.security.jgss;

    exports com.sun.security.auth;
    exports com.sun.security.auth.callback;
    exports com.sun.security.auth.login;
    exports com.sun.security.auth.module;
    provides javax.security.auth.spi.LoginModule with
        com.sun.security.auth.module.Krb5LoginModule;
    provides javax.security.auth.spi.LoginModule with
        com.sun.security.auth.module.UnixLoginModule;
    provides javax.security.auth.spi.LoginModule with
        com.sun.security.auth.module.JndiLoginModule;
    provides javax.security.auth.spi.LoginModule with
        com.sun.security.auth.module.KeyStoreLoginModule;
    provides javax.security.auth.spi.LoginModule with
        com.sun.security.auth.module.LdapLoginModule;
    provides javax.security.auth.spi.LoginModule with
        com.sun.security.auth.module.NTLoginModule;
}

