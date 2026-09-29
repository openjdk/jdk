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

import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.MacSign.CertificateRequest;
import jdk.jpackage.test.MacSign.ResolvedKeychain;
import jdk.jpackage.test.mock.CommandAction;
import jdk.jpackage.test.mock.MockIllegalStateException;

/**
 * Mocks /usr/bin/security command.
 */
final class MacSecurityMock implements CommandAction {

    MacSecurityMock(MacSignMockUtils.SignEnv signEnv) {
        Objects.requireNonNull(signEnv);

        var keychains = signEnv.keychains();

        var stdUserKeychains = Stream.of(StandardKeychain.values()).map(StandardKeychain::keychainName).filter(name -> {
            // Add standard keychain unless it is defined in the signing environment.
            return keychains.stream().noneMatch(keychain -> {
                return keychain.name().equals(name);
            });
        }).map(name -> {
            // Assume the standard keychain is empty.
            return ResolvedKeychain.createMock(name, Map.of());
        });

        allKnownKeychains = Stream.of(
                stdUserKeychains,
                keychains.stream()
        ).flatMap(x -> x).collect(Collectors.toUnmodifiableMap(ResolvedKeychain::name, x -> x));

        currentKeychains.addAll(Stream.of(StandardKeychain.values())
                .map(StandardKeychain::keychainName)
                .map(allKnownKeychains::get)
                .map(Objects::requireNonNull).toList());
    }

    @Override
    public Optional<Integer> run(Context context) {
        switch (context.args().getFirst()) {
            case "list-keychains" -> {
                listKeychains(context.shift());
                return Optional.of(0);
            }
            case "find-certificate" -> {
                findCertificate(context.shift());
                return Optional.of(0);
            }
            default -> {
                throw context.unexpectedArguments();
            }
        }
    }

    private void listKeychains(Context context) {
        if (context.args().getFirst().equals("-s")) {
            currentKeychains.clear();
            currentKeychains.addAll(context.shift().args().stream().map(name -> {
                return Optional.ofNullable(allKnownKeychains.get(name)).orElseThrow(() -> {
                    throw new MockIllegalStateException(String.format("Unknown keychain name: %s", name));
                });
            }).toList());
        } else if (context.args().isEmpty()) {
            currentKeychains.stream().map(keychain -> {
                return String.format("  \"%s\"", keychain.name());
            }).forEach(context::printlnOut);
        } else {
            throw context.unexpectedArguments();
        }
    }

    private void findCertificate(Context context) {

        var args = new ArrayList<>(context.args());
        for (var mandatoryArg : List.of("-p", "-a")) {
            if (!args.remove(mandatoryArg)) {
                throw context.unexpectedArguments();
            }
        }

        var certNameFilter = context.findOptionValue("-c").map(certNameSubstr -> {

            // Remove option name and its value.
            var idx = args.indexOf("-c");
            args.remove(idx);
            args.remove(idx);

            Predicate<Map.Entry<CertificateRequest, X509Certificate>> pred = e -> {
                return e.getKey().name().contains(certNameSubstr);
            };
            return pred;
        });

        Stream<ResolvedKeychain> keychains;
        if (args.isEmpty()) {
            keychains = currentKeychains.stream();
        } else {
            // Remaining arguments must be keychain names.
            keychains = args.stream().map(keychainName -> {
                return Optional.ofNullable(allKnownKeychains.get(keychainName)).orElseThrow(() -> {
                    throw new MockIllegalStateException(String.format("Unknown keychain name: %s", keychainName));
                });
            });
        }

        var certStream = keychains.flatMap(keychain -> {
            return keychain.mapCertificateRequests().entrySet().stream();
        });

        if (certNameFilter.isPresent()) {
            certStream = certStream.filter(certNameFilter.get());
        }

        certStream.map(Map.Entry::getValue).map(MacSign::formatX509Certificate).forEach(formattedCert -> {
            context.out().print(formattedCert);
        });
    }

    // Keep the order of the items as the corresponding keychains appear
    // in the output of the "/usr/bin/security list-keychains" command.
    private enum StandardKeychain {
        USER_KEYCHAIN {
            @Override
            String keychainName() {
                return Path.of(System.getProperty("user.home")).resolve("Library/Keychains/login.keychain-db").toString();
            }
        },
        SYSTEM_KEYCHAIN {
            @Override
            String keychainName() {
                return "/Library/Keychains/System.keychain";
            }
        },
        ;

        abstract String keychainName();
    }

    private final List<ResolvedKeychain> currentKeychains = new ArrayList<ResolvedKeychain>();
    private final Map<String, ResolvedKeychain> allKnownKeychains;
}
