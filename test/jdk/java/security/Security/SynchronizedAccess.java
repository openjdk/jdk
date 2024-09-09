/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4162583 7054918 8130181 8028127
 * @library /test/lib ../testlibrary
 * @summary Make sure Provider api implementations are synchronized properly
 */

import java.security.*;

import jdk.test.lib.Asserts;

public class SynchronizedAccess {

    public static void main(String[] args) throws Exception {
        ProvidersSnapshot snapshot = ProvidersSnapshot.create();
        try {
            main0(args);
        } finally {
            snapshot.restore();
        }
    }

    public static void main0(String[] args) throws Exception {
        var providersCountBefore = Security.getProviders().length;
        AccessorThread[] acc = new AccessorThread[200];
        for (int i = 0; i < acc.length; i++) {
            acc[i] = new AccessorThread("thread" + i);
        }
        for (int i = 0; i < acc.length; i++) {
            acc[i].start();
        }
        for (int i = 0; i < acc.length; i++) {
            acc[i].join();
        }
        var providersCountAfter = Security.getProviders().length;
        Asserts.assertEquals(providersCountBefore, providersCountAfter);
    }

    static class AccessorThread extends Thread {

        public AccessorThread(String str) {
            super(str);
        }

        public void run() {
            Provider[] provs = new Provider[10];
            for (int i = 0; i < provs.length; i++) {
                provs[i] = new MyProvider("name" + i, "1", "test");
            }

            int rounds = 20;
            while (rounds-- > 0) {
                for (int i = 0; i < provs.length; i++) {
                    // Might install (>=0) or not (-1) if already installed
                    Security.addProvider(provs[i]);
                    Thread.yield();
                }

                try {
                    Signature.getInstance("sigalg");
                    Thread.yield();
                } catch (NoSuchAlgorithmException nsae) {
                    // All providers may have been deregistered.  Ok.
                }

                for (int i = 0; i < provs.length; i++) {
                    // Might or might not remove (silent return)
                    Security.removeProvider("name" + i);
                    Thread.yield();
                }
            } // while
        }

        public static final class MyProvider extends Provider {
            public MyProvider(String name, String version, String info) {
                super(name, version, info);
                put("Signature.sigalg", SigImpl.class.getName());
            }
        }

        public static final class SigImpl extends Signature {

            public SigImpl() {
                super(null);
            }

            @Override
            protected void engineInitVerify(PublicKey publicKey) {
            }

            @Override
            protected void engineInitSign(PrivateKey privateKey) {
            }

            @Override
            protected void engineUpdate(byte b) {
            }

            @Override
            protected void engineUpdate(byte[] b, int off, int len) {
            }

            @Override
            protected byte[] engineSign() {
                return new byte[0];
            }

            @Override
            protected boolean engineVerify(byte[] sigBytes) {
                return false;
            }

            @Override
            protected void engineSetParameter(String param, Object value)
                    throws InvalidParameterException {
            }

            @Override
            protected Object engineGetParameter(String param)
                    throws InvalidParameterException {
                return null;
            }
        }
    }
}