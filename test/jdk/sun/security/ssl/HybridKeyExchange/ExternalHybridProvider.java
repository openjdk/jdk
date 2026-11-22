/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8385968
 * @summary Test TLS 1.3 use of an external hybrid key exchange provider
 * @library /javax/net/ssl/templates
 * @modules java.base/javax.crypto:+open
 * @run main/othervm ExternalHybridProvider
 */

import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.DecapsulateException;
import javax.crypto.KEM;
import javax.crypto.KEMSpi;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/*
 * This provider models an external hybrid key exchange provider that
 * provides hybrid KeyPairGenerator and KEM, and passes the peer
 * key share as a RAW PublicKey.
 */
public class ExternalHybridProvider extends SSLSocketTemplate {
    private static final String ALGORITHM = "X25519MLKEM768";
    private static final String PROVIDER_NAME = "MockHybrid";

    private ExternalHybridProvider() {
        super(true);
    }

    @Override
    protected void configureServerSocket(SSLServerSocket socket) {
        configure(socket);
    }

    @Override
    protected void configureClientSocket(SSLSocket socket) {
        configure(socket);
    }

    private static void configure(SSLServerSocket socket) {
        socket.setEnabledProtocols(new String[] { "TLSv1.3" });
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setNamedGroups(new String[] { ALGORITHM });
        socket.setSSLParameters(parameters);
    }

    private static void configure(SSLSocket socket) {
        socket.setEnabledProtocols(new String[] { "TLSv1.3" });
        SSLParameters parameters = socket.getSSLParameters();
        parameters.setNamedGroups(new String[] { ALGORITHM });
        socket.setSSLParameters(parameters);
    }

    public static void main(String[] args) throws Exception {
        Provider provider = new MockProvider();
        Security.insertProviderAt(provider, 1);
        try {
            System.out.println(PROVIDER_NAME
                    + " as the most-preferred provider");
            allowMockProvider(provider);
            System.out.println(PROVIDER_NAME
                    + " is marked as JCE-verified provider for this test");
            if (provider.getService("KeyFactory", ALGORITHM) != null) {
                throw new RuntimeException(PROVIDER_NAME
                        + " must not provide "
                        + "KeyFactory." + ALGORITHM);
            }

            new ExternalHybridProvider().run();

            if (MockKPG.kpgCnt.get() == 0) {
                throw new RuntimeException("TLS did not use external "
                        + "KeyPairGenerator." + ALGORITHM);
            }

            if (MockKEM.encapsulatorCnt.get() == 0 ||
                    MockKEM.decapsulatorCnt.get() == 0) {
                throw new RuntimeException("TLS did not use external KEM."
                        + ALGORITHM + " for encapsulator and decapsulator operations");
            }

            System.out.printf("%s use counts: KPG=%d, encapsulator=%d, "
                            + "decapsulator=%d%n", PROVIDER_NAME,
                    MockKPG.kpgCnt.get(),
                    MockKEM.encapsulatorCnt.get(),
                    MockKEM.decapsulatorCnt.get());
        } finally {
            Security.removeProvider(PROVIDER_NAME);
        }
    }

    @SuppressWarnings({ "unchecked", "removal" })
    private static void allowMockProvider(Provider provider) throws Exception {
        Class<?> jceSecurity = Class.forName("javax.crypto.JceSecurity");
        Field queueField = jceSecurity.getDeclaredField("queue");
        queueField.setAccessible(true);
        ReferenceQueue<Object> queue =
                (ReferenceQueue<Object>) queueField.get(null);

        Class<?> wrapper = Class.forName(
                "javax.crypto.JceSecurity$WeakIdentityWrapper");
        Constructor<?> constructor = wrapper.getDeclaredConstructor(
                Provider.class, ReferenceQueue.class);
        constructor.setAccessible(true);
        Object key = constructor.newInstance(provider, queue);

        Field resultsField = jceSecurity.getDeclaredField(
                "verificationResults");
        resultsField.setAccessible(true);
        Map<Object, Object> results =
                (Map<Object, Object>) resultsField.get(null);
        results.put(key, Boolean.TRUE);
    }

    public static final class MockProvider extends Provider {
        public MockProvider() {
            super(PROVIDER_NAME, "1.0", "Mock TLS hybrid key exchange provider");
            put("KeyPairGenerator." + ALGORITHM, MockKPG.class.getName());
            put("KEM." + ALGORITHM, MockKEM.class.getName());
        }
    }

    public static final class MockKPG extends KeyPairGeneratorSpi {
        static final AtomicInteger kpgCnt = new AtomicInteger();
        private final KeyPairGenerator delegate;

        public MockKPG() {
            try {
                System.out.println("MockProvider KeyPairGenerator selected; "
                        + "delegating key generation to SunJCE");
                delegate = KeyPairGenerator.getInstance(ALGORITHM, "SunJCE");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void initialize(int keysize, SecureRandom random) {
            throw new InvalidParameterException("Use named parameters");
        }

        @Override
        public void initialize(AlgorithmParameterSpec params,
                SecureRandom random) throws InvalidAlgorithmParameterException {
            if (!(params instanceof NamedParameterSpec namedSpec) ||
                    !ALGORITHM.equalsIgnoreCase(namedSpec.getName())) {
                throw new InvalidAlgorithmParameterException(
                        "Expected NamedParameterSpec for " + ALGORITHM);
            }

            delegate.initialize(params, random);
        }

        @Override
        public KeyPair generateKeyPair() {
            kpgCnt.incrementAndGet();
            System.out.println("MockProvider KeyPairGenerator generated a "
                    + "hybrid key pair");
            return delegate.generateKeyPair();
        }
    }

    public static final class MockKEM implements KEMSpi {
        static final AtomicInteger encapsulatorCnt = new AtomicInteger();
        static final AtomicInteger decapsulatorCnt = new AtomicInteger();

        @Override
        public EncapsulatorSpi engineNewEncapsulator(PublicKey publicKey,
                AlgorithmParameterSpec spec, SecureRandom secureRandom) throws
                InvalidAlgorithmParameterException, InvalidKeyException {
            if (!"RAW".equalsIgnoreCase(publicKey.getFormat())) {
                throw new InvalidKeyException("Expected a RAW TLS key_share");
            }
            encapsulatorCnt.incrementAndGet();

            System.out.println("MockProvider KEM encapsulator selected for "
                    + "a RAW TLS key_share; delegating encapsulation to "
                    + "SunJCE");

            KEM kem = sunJceKEM();
            return new MockEncapsulator(kem.newEncapsulator(publicKey, spec,
                    secureRandom));
        }

        @Override
        public DecapsulatorSpi engineNewDecapsulator(PrivateKey privateKey,
                AlgorithmParameterSpec spec)
                throws InvalidAlgorithmParameterException, InvalidKeyException {
            decapsulatorCnt.incrementAndGet();

            System.out.println("MockProvider KEM decapsulator selected; "
                    + "delegating decapsulation to SunJCE");

            KEM kem = sunJceKEM();
            return new MockDecapsulator(kem.newDecapsulator(privateKey, spec));
        }

        private static KEM sunJceKEM() {
            try {
                return KEM.getInstance(ALGORITHM, "SunJCE");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private record MockEncapsulator(KEM.Encapsulator delegate)
            implements KEMSpi.EncapsulatorSpi {
        @Override
        public KEM.Encapsulated engineEncapsulate(int from, int to,
                String algorithm) {
            return delegate.encapsulate(from, to, algorithm);
        }

        @Override
        public int engineSecretSize() {
            return delegate.secretSize();
        }

        @Override
        public int engineEncapsulationSize() {
            return delegate.encapsulationSize();
        }
    }

    private record MockDecapsulator(KEM.Decapsulator delegate)
            implements KEMSpi.DecapsulatorSpi {
        @Override
        public SecretKey engineDecapsulate(byte[] encapsulation, int from,
                int to, String algorithm) throws DecapsulateException {
            return delegate.decapsulate(encapsulation, from, to, algorithm);
        }

        @Override
        public int engineSecretSize() {
            return delegate.secretSize();
        }

        @Override
        public int engineEncapsulationSize() {
            return delegate.encapsulationSize();
        }
    }
}
