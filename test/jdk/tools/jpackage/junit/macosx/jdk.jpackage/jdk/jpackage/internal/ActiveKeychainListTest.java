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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import jdk.jpackage.test.mock.CommandAction;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.MockIllegalStateException;
import jdk.jpackage.test.mock.Script;
import jdk.jpackage.test.stdmock.JPackageMockUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ActiveKeychainListTest {

    @ParameterizedTest
    @CsvSource(value = {
            "'','',''",
            "a,'',a",
            "'',a,a",
            "a,a,a",
            "abc,b,abc",
            "abc,ba,abc",
            "abc,bad,abcd",
            "ac,b,acb"
    })
    void test_ctor_and_createForPlatform(String initial, String requested, String current) throws IOException {

        var initialKeychains = parseKeychainList(initial);
        var requestedKeychains = parseKeychainList(requested);

        var securityMock = new SecurityKeychainListMock(true);
        initialKeychains.stream().map(Keychain::name).forEach(securityMock.keychainNames()::add);

        Globals.main(toSupplier(() -> {
            securityMock.applyToGlobals();
            Globals.instance().setProperty(ActiveKeychainList.class, false);

            assertTrue(ActiveKeychainList.createForPlatform(requestedKeychains.toArray(Keychain[]::new)).isEmpty());

            var akl = new ActiveKeychainList(List.copyOf(requestedKeychains));
            try (akl) {
                assertEquals(initialKeychains, akl.restoreKeychains());
                assertEquals(requestedKeychains, akl.requestedKeychains());
                assertEquals(parseKeychainList(current), akl.currentKeychains());
                assertEquals(akl.currentKeychains(), securityMock.keychains());
            }

            assertEquals(initialKeychains, akl.restoreKeychains());
            assertEquals(requestedKeychains, akl.requestedKeychains());
            assertEquals(parseKeychainList(current), akl.currentKeychains());
            assertEquals(initialKeychains, securityMock.keychains());

            return 0;
        }));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'','','',true",
            "'','','',false",

            "a,'','',true",
            "a,'',a,false",

            "'',a,a,true",
            "'',a,a,false",

            "a,a,a,true",
            "a,a,a,false",

            "abc,b,b,true",
            "abc,b,abc,false",

            "abc,ba,ba,true",
            "abc,ba,abc,false",

            "abc,bad,bad,true",
            "abc,bad,abcd,false",

            "ac,b,b,true",
            "ac,b,acb,false"
    })
    void testCtorWithForced(String initial, String requested, String current, boolean forced) throws IOException {

        var initialKeychains = parseKeychainList(initial);
        var requestedKeychains = parseKeychainList(requested);

        var securityMock = new SecurityKeychainListMock(forced);
        securityMock.keychainNames().addAll(List.of("foo", "bar"));

        Globals.main(toSupplier(() -> {
            securityMock.applyToGlobals();

            var akl = new ActiveKeychainList(List.copyOf(requestedKeychains), List.copyOf(initialKeychains), forced);
            try (akl) {
                assertEquals(initialKeychains, akl.restoreKeychains());
                assertEquals(requestedKeychains, akl.requestedKeychains());
                assertEquals(parseKeychainList(current), akl.currentKeychains());
                assertEquals(akl.currentKeychains(), securityMock.keychains());
            }

            assertEquals(initialKeychains, akl.restoreKeychains());
            assertEquals(requestedKeychains, akl.requestedKeychains());
            assertEquals(parseKeychainList(current), akl.currentKeychains());
            assertEquals(initialKeychains, securityMock.keychains());

            return 0;
        }));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'','',",
            "a,'',",
            "'',a,a",
            "a,a,a",
            "abc,b,abc",
            "abc,ba,abc",
            "abc,bad,abcd",
            "ac,b,acb"
    })
    void test_withKeychain(String initial, String requested, String current) throws IOException {

        var initialKeychains = parseKeychainList(initial);
        var requestedKeychains = parseKeychainList(requested);

        for (boolean isRequired : List.of(true, false)) {
            var securityMock = new SecurityKeychainListMock(true);
            initialKeychains.stream().map(Keychain::name).forEach(securityMock.keychainNames()::add);

            Consumer<List<Keychain>> workload = keychains -> {
                assertEquals(requestedKeychains, keychains);
                if (isRequired && current != null) {
                    assertEquals(parseKeychainList(current), securityMock.keychains());
                } else {
                    assertEquals(initialKeychains, securityMock.keychains());
                }
            };

            Globals.main(toSupplier(() -> {
                Globals.instance().setProperty(ActiveKeychainList.class, isRequired);

                securityMock.applyToGlobals();
                ActiveKeychainList.withKeychains(workload, requestedKeychains);

                assertEquals(initialKeychains, securityMock.keychains());

                if (requestedKeychains.size() == 1) {
                    securityMock.applyToGlobals();
                    ActiveKeychainList.withKeychain(keychain -> {
                        workload.accept(List.of(keychain));
                    }, requestedKeychains.getFirst());

                    assertEquals(initialKeychains, securityMock.keychains());
                }

                return 0;
            }));
        }
    }

    /**
     * Mocks "/usr/bin/security list-keychain" command.
     */
    record SecurityKeychainListMock(List<String> keychainNames, boolean isReadAllowed) implements CommandAction {

        SecurityKeychainListMock {
            Objects.requireNonNull(keychainNames);
        }

        SecurityKeychainListMock(boolean isReadAllowed) {
            this(new ArrayList<>(), isReadAllowed);
        }

        List<Keychain> keychains() {
            return keychainNames.stream().map(Keychain::new).toList();
        }

        void applyToGlobals() {
            CommandActionSpec actionSpec = CommandActionSpec.create("/usr/bin/security", this);

            var script = Script.build()
                    .commandMockBuilderMutator(mockBuilder -> {
                        // Limit the number of times the mock can be executed.
                        // It should be one or twice.
                        // Once, when ActiveKeychainList is constructed such that it doesn't read
                        // the current active keychain list from the "/usr/bin/security" command, but takes it from the parameter.
                        // Twice, when ActiveKeychainList is constructed such that it read
                        // the current active keychain list from the "/usr/bin/security" command.
                        mockBuilder.repeat(isReadAllowed ? 2 : 1);
                    })
                    // Replace "/usr/bin/security" with the mock bound to the keychain mock.
                    .map(new CommandMockSpec(actionSpec.description(), "security-list-keychain", CommandActionSpecs.build().action(actionSpec).create()))
                    .createLoop();

            JPackageMockUtils.buildJPackage()
                    .script(script)
                    .listener(System.out::println)
                    .applyToGlobals();
        }

        @Override
        public Optional<Integer> run(Context context) throws Exception, MockIllegalStateException {
            final var origContext = context;

            if (!context.args().getFirst().equals("list-keychains")) {
                throw origContext.unexpectedArguments();
            }

            context = context.shift();

            if (context.args().isEmpty()) {
                if (isReadAllowed) {
                    keychainNames.stream().map(k -> {
                        return new StringBuilder().append('"').append(k).append('"').toString();
                    }).forEach(context::printlnOut);
                } else {
                    throw origContext.unexpectedArguments();
                }
            } else if (context.args().getFirst().equals("-s")) {
                keychainNames.clear();
                keychainNames.addAll(context.shift().args());
            } else {
                throw origContext.unexpectedArguments();
            }

            return Optional.of(0);
        }
    }

    private static List<Keychain> parseKeychainList(String str) {
        return str.chars().mapToObj(chr -> {
            return new StringBuilder().append((char)chr).toString();
        }).map(Keychain::new).toList();
    }
}
