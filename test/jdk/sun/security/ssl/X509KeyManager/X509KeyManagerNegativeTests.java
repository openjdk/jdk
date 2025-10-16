/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import org.junit.Assert;

/*
 * @test
 * @bug 8369995
 * @summary X509KeyManagerImpl negative tests causing exceptions
 * @library /test/lib
 * @run junit X509KeyManagerNegativeTests
 */
public class X509KeyManagerNegativeTests {
    private static X509KeyManager km;

    @BeforeAll
    public static void beforeAll() throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
        final char[] password = {' '};
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init((KeyStore) null, password);
        km = (X509KeyManager) kmf.getKeyManagers()[0];
    }

    @Test
    public void getCertificateChainIncompleteString() {
        Assert.assertThrows(StringIndexOutOfBoundsException.class,
                () -> km.getCertificateChain("1."));
    }

    @Test
    public void getPrivateKeyIncompleteString() {
        Assert.assertThrows(StringIndexOutOfBoundsException.class,
                () -> km.getPrivateKey("1."));
    }

    @Test
    public void getPrivateKeyIndexOutOfBounds() {
        // .1. would look for an index 1 key in keystore, which doesn't exist
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> km.getPrivateKey("RSA.1.1"));
    }
    @Test
    public void getCertificateChainIndexOutOfBounds() {
        // .1. would look for an index 1 cert in keystore, which doesn't exist
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> km.getPrivateKey("RSA.1.1"));
    }

}
