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


package jdk.jpackage.test;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Verifies failed command error in jpackage's output.
 */
public final class FailedCommandErrorVerifier {

    public FailedCommandErrorVerifier(Pattern cmdlinePattern) {
        this.cmdlinePattern = Objects.requireNonNull(cmdlinePattern);
    }

    public TKit.TextStreamVerifier.Group createGroup() {
        var asPredicate = cmdlinePattern.asPredicate();

        var errorMessage = exitCode().map(v -> {
            return JPackageStringBundle.MAIN.cannedFormattedString("error.command-failed-unexpected-exit-code", v, "");
        }).orElseGet(() -> {
            return JPackageStringBundle.MAIN.cannedFormattedString("error.command-failed-unexpected-output", "");
        });

        var errorMessageWithPrefix = JPackageCommand.makeError(errorMessage).getValue();

        var group = TKit.TextStreamVerifier.group();

        group.add(TKit.assertTextStream(cmdlinePattern.pattern()).predicate(line -> {
            if (line.startsWith(errorMessageWithPrefix)) {
                line = line.substring(errorMessageWithPrefix.length());
                return asPredicate.test(line);
            } else {
                return false;
            }
        }));

        group.add(TKit.assertTextStream(
                JPackageStringBundle.MAIN.cannedFormattedString("message.failed-command-output-header").getValue()
        ).predicate(String::equals));

        outputVerifier().ifPresent(group::add);

        return group;
    }

    public void applyTo(JPackageCommand cmd) {
        cmd.validateOutput(createGroup().create());
    }

    public FailedCommandErrorVerifier outputVerifier(TKit.TextStreamVerifier.Group v) {
        outputVerifier = v;
        return this;
    }

    public FailedCommandErrorVerifier outputVerifiers(List<TKit.TextStreamVerifier> verifiers) {
        var group = TKit.TextStreamVerifier.group();
        verifiers.forEach(group::add);
        return outputVerifier(group);
    }

    public FailedCommandErrorVerifier outputVerifiers(TKit.TextStreamVerifier... verifiers) {
        return outputVerifiers(List.of(verifiers));
    }

    public FailedCommandErrorVerifier output(List<String> v) {
        return outputVerifiers(v.stream().map(TKit::assertTextStream).toList());
    }

    public FailedCommandErrorVerifier output(String... output) {
        return output(List.of(output));
    }

    public FailedCommandErrorVerifier exitCode(int v) {
        exitCode = v;
        return this;
    }

    private Optional<Integer> exitCode() {
        return Optional.ofNullable(exitCode);
    }

    private Optional<TKit.TextStreamVerifier.Group> outputVerifier() {
        return Optional.ofNullable(outputVerifier);
    }

    private final Pattern cmdlinePattern;
    private TKit.TextStreamVerifier.Group outputVerifier;
    private Integer exitCode;
}
