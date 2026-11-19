/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.TKit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class HelpTest extends JUnitAdapter.TestSrcInitializer {

    @Test
    public void testHelpOption() {
        final var cmdline = new JOptSimpleOptionsBuilder()
                .options(StandardOption.options())
                .create().apply(new String[] { "-h" }).orElseThrow().create();

        assertTrue(StandardOption.HELP.containsIn(cmdline));
    }

    @Disabled
    @ParameterizedTest
    @EnumSource(names = {"WINDOWS", "LINUX", "MACOS"})
    public void printHelp(OperatingSystem os) {
        new StandardHelpFormatter(os).format(System.out::print);
    }

    @Disabled
    @ParameterizedTest
    @EnumSource(names = {"WINDOWS", "LINUX", "MACOS"})
    public void updateGoldenHelpFiles(OperatingSystem os) throws IOException {
        try (var sink = Files.newBufferedWriter(goldenHelpOutputFile(os)); var pw = new PrintWriter(sink)) {
            new StandardHelpFormatter(os).format(pw::append);
        }
    }

    @Test
    public void printHelp() {
        StandardHelpFormatter.INSTANCE.format(System.out::print);
    }

    @Test
    @EnabledIfSystemProperty(named = "user.language", matches = "en")
    public void verifyHelp() throws IOException {
        verifyHelp(StandardHelpFormatter.INSTANCE, OperatingSystem.current());
    }

    @Test
    public void testOptionGroupNamesUnique() {
        // Test group names are unique.
        Stream.of(StandardHelpFormatter.OptionGroupID.values())
                .map(StandardHelpFormatter.OptionGroupID::groupName)
                .collect(Collectors.toMap(x -> x, x -> x));
    }

    @ParameterizedTest
    @EnumSource(names = {"WINDOWS", "LINUX", "MACOS"})
    public void testOptionGroups(OperatingSystem os) {

        final var groups = Stream.of(StandardHelpFormatter.OptionGroupID.values())
                .filter(Predicate.isEqual(StandardHelpFormatter.OptionGroupID.SAMPLES).negate())
                .map(optionGroupID -> {
                    return optionGroupID.createNonEmptyOptionGroup(os);
                })
                .filter(Optional::isPresent).map(Optional::orElseThrow)
                .collect(Collectors.toMap(HelpFormatter.OptionGroup::name, HelpFormatter.OptionGroup::options));

        // Names of all options supported on the given platform.
        final var allOptionNames = StandardOption.options().stream().map(Option::spec)
                .filter(StandardOption.platformOption(os))
                .map(OptionSpec::names).flatMap(Collection::stream)
                .sorted().toList();

        // Names of all options in the help groups.
        final var groupOptions = groups.values().stream().flatMap(Collection::stream)
                .map(OptionSpec::names).flatMap(Collection::stream)
                .sorted().toList();

        // Test that each option belongs to only one group except of `--runtime-image`
        groupOptions.stream().collect(Collectors.toMap(x -> x, x -> x, (a, b) -> {
            if (a.equals(StandardOption.PREDEFINED_RUNTIME_IMAGE.getSpec().name())) {
                return a;
            } else {
                throw new AssertionError(String.format("Option [%s] is included in multiple groups", a.name()));
            }
        }));

        // Test that each option is added to some group.
        assertEquals(allOptionNames, groupOptions.stream().distinct().toList());
    }

    @ParameterizedTest
    @MethodSource
    public void testOptionSpecSorter(List<String> unsortedNames, List<String> expectedNames) {
        var sortedNames = unsortedNames.stream()
                .map(HelpTest::dummyOptionSpec)
                .sorted(StandardHelpFormatter.optionSpecSorter())
                .map(OptionSpec::name)
                .map(OptionName::name).toList();
        assertEquals(expectedNames, sortedNames);
    }

    private static Iterable<Arguments> testOptionSpecSorter() {
        return List.of(
                Arguments.of(List.of("a", "type", "w"), List.of("type", "a", "w")),
                Arguments.of(List.of("type", "type", "w"), List.of("type", "type", "w")),
                Arguments.of(List.of("a", "type", "type"), List.of("type", "type", "a")),
                Arguments.of(List.of("type", "w", "type"), List.of("type", "type", "w"))
        );
    }

    private static OptionSpec<?> dummyOptionSpec(String name) {
        return new OptionSpec<>(
                List.of(OptionName.of(name)),
                Optional.empty(),
                Set.of(new OptionScope() {}),
                OptionSpec.MergePolicy.USE_FIRST,
                Optional.empty(),
                Optional.empty(),
                "");
    }

    private static void verifyHelp(StandardHelpFormatter helpFormatter, OperatingSystem os) throws IOException {

        var sb = new StringBuilder();
        helpFormatter.format(sb::append);

        var help = new BufferedReader(new StringReader(sb.toString())).lines().toList();

        var expectedHelp = Files.readAllLines(goldenHelpOutputFile(os));

        assertEquals(expectedHelp, help);
    }

    private static Path goldenHelpOutputFile(OperatingSystem os) {
        String fname = String.format("help-%s.txt", os.name().toLowerCase());
        return TKit.TEST_SRC_ROOT.resolve("junit/share/jdk.jpackage/jdk/jpackage/internal/cli", fname);
    }
}
