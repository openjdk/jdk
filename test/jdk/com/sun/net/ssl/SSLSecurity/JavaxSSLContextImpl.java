/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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

import java.security.*;
import javax.net.ssl.*;

public class JavaxSSLContextImpl extends SSLContextSpi {

    public JavaxSSLContextImpl() {
        System.out.println("JavaxSSLContextImpl initialized");
    }

    protected void engineInit(KeyManager[] km,
           TrustManager[] tm, SecureRandom sr) throws KeyManagementException {
        System.out.println("JavaxSSLContextImpl init'd");
    }

    protected SSLEngine engineCreateSSLEngine() {
        return null;
    }

    protected SSLEngine engineCreateSSLEngine(String host, int port) {
        return null;
    }

    protected SSLSocketFactory engineGetSocketFactory() {
        return null;
    }

    protected SSLServerSocketFactory engineGetServerSocketFactory() {
        return null;
    }

    protected SSLSessionContext engineGetServerSessionContext() {
        return null;
    }

    protected SSLSessionContext engineGetClientSessionContext() {
        return null;
    }
}
