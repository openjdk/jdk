/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.test.stdmock;

import static jdk.jpackage.internal.util.function.ExceptionBox.toUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSign.KeychainWithCertsSpec;
import jdk.jpackage.test.MacSign.ResolvedKeychain;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockSpec;


/**
 * Utilities to create macOS signing tool mocks.
 */
public final class MacSignMockUtils {

    private MacSignMockUtils() {
    }

    public static Map<CertificateRequest, X509Certificate> resolveCertificateRequests(
            Collection<CertificateRequest> certificateRequests) {
        Objects.requireNonNull(certificateRequests);

        var caKeys = createKeyPair();

        Function<CertificateRequest, X509Certificate> resolver = toFunction(certRequest -> {
            var builder = new CertificateBuilder()
                    .setSubjectName("CN=" + certRequest.name())
                    .setPublicKey(caKeys.getPublic())
                    .setSerialNumber(BigInteger.ONE)
                    .addSubjectKeyIdExt(caKeys.getPublic())
                    .addAuthorityKeyIdExt(caKeys.getPublic());

            Instant from;
            Instant to;
            if (certRequest.expired()) {
                from = LocalDate.now().minusDays(10).atStartOfDay(ZoneId.systemDefault()).toInstant();
                to = from.plus(Duration.ofDays(1));
            } else {
                from = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
                to = from.plus(Duration.ofDays(certRequest.days()));
            }
            builder.setValidity(Date.from(from), Date.from(to));

            return builder.build(null, caKeys.getPrivate());
        });

        return certificateRequests.stream()
            .distinct()
            .collect(Collectors.toUnmodifiableMap(x -> x, resolver));
    }

    public static Map<CertificateRequest, X509Certificate> resolveCertificateRequests(
            CertificateRequest... certificateRequests) {
        return resolveCertificateRequests(List.of(certificateRequests));
    }

    public static final class SignEnv {

        public SignEnv(List<KeychainWithCertsSpec> spec) {
            Objects.requireNonNull(spec);

            spec.stream().map(keychain -> {
                return keychain.keychain().name();
            }).collect(Collectors.toMap(x -> x, x -> x, (a, b) -> {
                throw new IllegalArgumentException(String.format("Multiple keychains with the same name: %s", a));
            }));

            this.spec = List.copyOf(spec);
            this.env = resolveCertificateRequests(
                    spec.stream().map(KeychainWithCertsSpec::certificateRequests).flatMap(Collection::stream).toList());
        }

        public SignEnv(KeychainWithCertsSpec... spec) {
            this(List.of(spec));
        }

        public List<ResolvedKeychain> keychains() {
            return spec.stream().map(ResolvedKeychain::new).map(keychain -> {
                return keychain.toMock(env);
            }).toList();
        }

        public Map<CertificateRequest, X509Certificate> env() {
            return env;
        }

        private final Map<CertificateRequest, X509Certificate> env;
        private final List<KeychainWithCertsSpec> spec;
    }

    public static CommandMockSpec securityMock(SignEnv signEnv) {
        var action = CommandActionSpec.create("/usr/bin/security", new MacSecurityMock(signEnv));
        return new CommandMockSpec(action.description(), CommandActionSpecs.build().action(action).create());
    }

    private static KeyPair createKeyPair() {
        try {
            var kpg = KeyPairGenerator.getInstance("RSA");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw ExceptionBox.toUnchecked(ex);
        }
    }

    //
    // Reflection proxy for jdk.test.lib.security.CertificateBuilder class.
    //
    // Can't use it directly because it is impossible to cherry-pick this class from the JDK test lib in JUnit tests due to limitations of jtreg.
    //
    // Shared jpackage JUnit tests don't require "jdk.jpackage.test.stdmock", but they depend on "jdk.jpackage.test" package.
    // Source code for these two packages resides in the same directory tree, so jtreg will pull in classes from both packages for the jpackage JUnit tests.
    // Static dependency on jdk.test.lib.security.CertificateBuilder class will force pulling in the entire JDK test lib, because of jtreg limitations.
    //
    // Use dynamic dependency as a workaround. Tests that require jdk.test.lib.security.CertificateBuilder class, should have
    //
    // /*
    //  * ...
    //  * @library /test/lib
    //  * @build jdk.test.lib.security.CertificateBuilder
    //  */
    //
    // in their declarations. They also should have
    //
    //  --add-exports java.base/sun.security.x509=ALL-UNNAMED
    //  --add-exports java.base/sun.security.util=ALL-UNNAMED
    //
    // on javac and java command lines.
    //
    private static final class CertificateBuilder {

        CertificateBuilder() {
            instance = toSupplier(ctor::newInstance).get();
        }

        CertificateBuilder setSubjectName(String v) {
            toRunnable(() -> {
                setSubjectName.invoke(instance, v);
            }).run();
            return this;
        }

        CertificateBuilder setPublicKey(PublicKey v) {
            toRunnable(() -> {
                setPublicKey.invoke(instance, v);
            }).run();
            return this;
        }

        CertificateBuilder setSerialNumber(BigInteger v) {
            toRunnable(() -> {
                setSerialNumber.invoke(instance, v);
            }).run();
            return this;
        }

        CertificateBuilder addSubjectKeyIdExt(PublicKey v) {
            toRunnable(() -> {
                addSubjectKeyIdExt.invoke(instance, v);
            }).run();
            return this;
        }

        CertificateBuilder addAuthorityKeyIdExt(PublicKey v) {
            toRunnable(() -> {
                addAuthorityKeyIdExt.invoke(instance, v);
            }).run();
            return this;
        }

        CertificateBuilder setValidity(Date from, Date to) {
            toRunnable(() -> {
                setValidity.invoke(instance, from, to);
            }).run();
            return this;
        }

        X509Certificate build(X509Certificate issuerCert, PrivateKey issuerKey) throws IOException, CertificateException {
            try {
                return (X509Certificate)toSupplier(() -> {
                    return build.invoke(instance, issuerCert, issuerKey);
                }).get();
            } catch (ExceptionBox box) {
                switch (ExceptionBox.unbox(box)) {
                    case IOException ex -> {
                        throw ex;
                    }
                    case CertificateException ex -> {
                        throw ex;
                    }
                    default -> {
                        throw box;
                    }
                }
            }
        }

        private final Object instance;

        private static final Constructor<?> ctor;
        private static final Method setSubjectName;
        private static final Method setPublicKey;
        private static final Method setSerialNumber;
        private static final Method addSubjectKeyIdExt;
        private static final Method addAuthorityKeyIdExt;
        private static final Method setValidity;
        private static final Method build;

        static {
            try {
                var certificateBuilderClass = Class.forName("jdk.test.lib.security.CertificateBuilder");

                ctor = certificateBuilderClass.getConstructor();

                setSubjectName = certificateBuilderClass.getMethod("setSubjectName", String.class);

                setPublicKey = certificateBuilderClass.getMethod("setPublicKey", PublicKey.class);

                setSerialNumber = certificateBuilderClass.getMethod("setSerialNumber", BigInteger.class);

                addSubjectKeyIdExt = certificateBuilderClass.getMethod("addSubjectKeyIdExt", PublicKey.class);

                addAuthorityKeyIdExt = certificateBuilderClass.getMethod("addAuthorityKeyIdExt", PublicKey.class);

                setValidity = certificateBuilderClass.getMethod("setValidity", Date.class, Date.class);

                build = certificateBuilderClass.getMethod("build", X509Certificate.class, PrivateKey.class);

            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException ex) {
                throw toUnchecked(ex);
            }
        }
    }
}
