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
 * Validates failed command error in jpackage's output.
 */
public final class FailedCommandErrorValidator {

    public FailedCommandErrorValidator(Pattern cmdlinePattern) {
        this.cmdlinePattern = Objects.requireNonNull(cmdlinePattern);
    }

    public JPackageOutputValidator create() {
        var asPredicate = cmdlinePattern.asPredicate();

        var errorMessage = exitCode().map(v -> {
            return JPackageStringBundle.MAIN.cannedFormattedString("error.command-failed-unexpected-exit-code", v, "");
        }).orElseGet(() -> {
            return JPackageStringBundle.MAIN.cannedFormattedString("error.command-failed-unexpected-output", "");
        });

        var errorMessageWithPrefix = JPackageCommand.makeError(errorMessage).getValue();

        var validator = new JPackageOutputValidator().stderr();

        validator.add(TKit.assertTextStream(cmdlinePattern.pattern()).predicate(line -> {
            if (line.startsWith(errorMessageWithPrefix)) {
                line = line.substring(errorMessageWithPrefix.length());
                return asPredicate.test(line);
            } else {
                return false;
            }
        }));

        validator.expectMatchingStrings(JPackageStringBundle.MAIN.cannedFormattedString("message.failed-command-output-header"));

        outputValidator().ifPresent(validator::add);

        return validator;
    }

    public void applyTo(JPackageCommand cmd) {
        create().applyTo(cmd);
    }

    public FailedCommandErrorValidator validator(JPackageOutputValidator v) {
        outputValidator = v;
        return this;
    }

    public FailedCommandErrorValidator validator(List<TKit.TextStreamVerifier> validators) {
        return validator(new JPackageOutputValidator().add(validators));
    }

    public FailedCommandErrorValidator validators(TKit.TextStreamVerifier... validators) {
        return validator(List.of(validators));
    }

    public FailedCommandErrorValidator output(List<String> v) {
        return validator(v.stream().map(TKit::assertTextStream).toList());
    }

    public FailedCommandErrorValidator output(String... output) {
        return output(List.of(output));
    }

    public FailedCommandErrorValidator exitCode(int v) {
        exitCode = v;
        return this;
    }

    private Optional<Integer> exitCode() {
        return Optional.ofNullable(exitCode);
    }

    private Optional<JPackageOutputValidator> outputValidator() {
        return Optional.ofNullable(outputValidator);
    }

    private final Pattern cmdlinePattern;
    private JPackageOutputValidator outputValidator;
    private Integer exitCode;
}
