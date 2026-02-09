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

import static jdk.jpackage.internal.util.PListWriter.writeArray;
import static jdk.jpackage.internal.util.PListWriter.writeBoolean;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writeKey;
import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collector;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSign.CertificateType;
import jdk.jpackage.test.MacSign.KeychainWithCertsSpec;
import jdk.jpackage.test.MacSign.ResolvedKeychain;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;


/**
 * Utilities to create macOS signing tool mocks.
 */
public final class MacSignMockUtils {

    private MacSignMockUtils() {
    }

    public record SignEnv(List<KeychainWithCertsSpec> spec, Map<CertificateRequest, X509Certificate> env) {

        public SignEnv {
            Objects.requireNonNull(spec);
            Objects.requireNonNull(env);

            var unresolvedCertificateRequests = spec.stream()
                    .map(KeychainWithCertsSpec::certificateRequests)
                    .flatMap(Collection::stream)
                    .filter(Predicate.not(env::containsKey))
                    .sorted()
                    .toList();

            if (!unresolvedCertificateRequests.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Unresolved certificate requests: %s", unresolvedCertificateRequests));
            }

            spec.stream().map(keychain -> {
                return keychain.keychain().name();
            }).reduce((a, b) -> {
                throw new IllegalArgumentException(String.format("Multiple keychains with the same name: %s", a));
            });
        }

        public SignEnv(String keychainName, Map<CertificateRequest, X509Certificate> env, Collection<CertificateRequest> keychainContent) {
            this(List.of(KeychainWithCertsSpec.build()
                    .name(Objects.requireNonNull(keychainName))
                    .addCerts(keychainContent)
                    .create()), env);
        }

        public SignEnv(String keychainName, Map<CertificateRequest, X509Certificate> env, CertificateRequest... keychainContent) {
            this(keychainName, env, List.of(keychainContent));
        }

        public List<ResolvedKeychain> keychains() {
            return spec.stream().map(ResolvedKeychain::new).map(keychain -> {
                return keychain.toMock(env);
            }).toList();
        }
    }

    public static CommandMockSpec securityMock(SignEnv signEnv) {
        var action = CommandActionSpec.create("/usr/bin/security", new MacSecurityMock(signEnv));
        return new CommandMockSpec(action.description(), CommandActionSpecs.build().action(action).create());
    }

    public static void save(Map<CertificateRequest, X509Certificate> certs, Path path) {
        Objects.requireNonNull(certs);
        Objects.requireNonNull(path);

        if (!(certs instanceof SequencedMap)) {
            save(new TreeMap<>(certs), path);
            return;
        }

        TKit.trace(String.format("Saving signing env in [%s] ...", path.normalize().toAbsolutePath()));
        try {
            XmlUtils.createXml(path, xml -> {
                writePList(xml, toXmlConsumer(() -> {
                xml.writeComment("""

This is an automatically generated file.

It contains self-signed certificates for testing signing in jpackage collected from a local test environment.
It doesn't contan sensitive or private information.
                        """);

                    writeDict(xml, toXmlConsumer(() -> {
                        writeKey(xml, "certificates");
                        writeArray(xml, toXmlConsumer(() -> {
                            for (var e : certs.entrySet()) {
                                writeResolvedCertificateRequest(e, xml);
                            }
                        }));
                    }));
                }));
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static SequencedMap<CertificateRequest, X509Certificate> load(Path path) {
        TKit.trace(String.format("Loading signing env from [%s] ...", path.normalize().toAbsolutePath()));

        var plistReader = MacHelper.readPList(path);

        return plistReader.queryArrayValue("certificates", true).map(obj -> {
            return (Map<String, PListReader.Raw>)obj;
        }).map(props -> {
            return Map.entry(createCertificateRequest(props), createResolvedCertificateRequest(props));
        }).collect(Collector.of(() -> {
            return new LinkedHashMap<CertificateRequest, X509Certificate>();
        }, (map, e) -> {
            var k = e.getKey();
            map.merge(k, Objects.requireNonNull(e.getValue()), (_, _) -> {
                throw duplicatedCertificateRequest(k);
            });
        }, (map1, map2) -> {
            map2.forEach((k, v) -> {
                map1.merge(k, v, (_, _) -> {
                    throw duplicatedCertificateRequest(k);
                });
            });
            return map1;
        }));
    }

    private static void writeCertificateRequest(CertificateRequest certRequest, XMLStreamWriter xml) throws XMLStreamException, IOException {
        writeString(xml, CertificateRequestField.NAME.keyName(), certRequest.name());
        writeString(xml, CertificateRequestField.TYPE.keyName(), certRequest.type());
        writeKey(xml, CertificateRequestField.DAYS.keyName());
        xml.writeStartElement("integer");
        xml.writeCharacters(Integer.toString(certRequest.days()));
        xml.writeEndElement();
        writeBoolean(xml, CertificateRequestField.EXPIRED.keyName(), certRequest.expired());
        writeBoolean(xml, CertificateRequestField.TRUSTED.keyName(), certRequest.trusted());
    }

    private static void writeX509Certificate(X509Certificate cert, XMLStreamWriter xml) throws XMLStreamException, IOException {
        var formattedCert = MacSign.formatX509Certificate(cert);
        writeString(xml, X509CertificateField.DATA.keyName(), formattedCert);
    }

    private static void writeResolvedCertificateRequest(
            Map.Entry<CertificateRequest, X509Certificate> resolvedCert, XMLStreamWriter xml) throws XMLStreamException, IOException {
        writeDict(xml, toXmlConsumer(() -> {
            writeCertificateRequest(resolvedCert.getKey(), xml);
            writeX509Certificate(resolvedCert.getValue(), xml);
        }));
    }

    private static CertificateRequest createCertificateRequest(Map<String, PListReader.Raw> props) {
        var name = props.get(CertificateRequestField.NAME.keyName()).value();
        var type = CertificateType.valueOf(props.get(CertificateRequestField.TYPE.keyName()).value());
        var days = Integer.parseInt(props.get(CertificateRequestField.DAYS.keyName()).value());
        var expired = Boolean.parseBoolean(props.get(CertificateRequestField.EXPIRED.keyName()).value());
        var trusted = Boolean.parseBoolean(props.get(CertificateRequestField.TRUSTED.keyName()).value());
        return new CertificateRequest(name, type, days, expired, trusted);
    }

    private static X509Certificate createResolvedCertificateRequest(Map<String, PListReader.Raw> props) {
        var base64WithBounds = props.get(X509CertificateField.DATA.keyName()).value();
        try {
            final var cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate)cf.generateCertificate(
                    new ByteArrayInputStream(base64WithBounds.getBytes(StandardCharsets.ISO_8859_1)));
        } catch (CertificateException ex) {
            throw ExceptionBox.toUnchecked(ex);
        }
    }

    private static RuntimeException duplicatedCertificateRequest(CertificateRequest certRequest) {
        Objects.requireNonNull(certRequest);
        return new RuntimeException(String.format("Duplicated certificate request: %s", certRequest));
    }

    private interface Field {

        public String name();

        default String keyName() {
            return name().toLowerCase();
        }
    }

    private enum CertificateRequestField implements Field {
        NAME,
        TYPE,
        DAYS,
        EXPIRED,
        TRUSTED,
        ;
    }

    private enum X509CertificateField implements Field {
        DATA,
        ;
    }
}
