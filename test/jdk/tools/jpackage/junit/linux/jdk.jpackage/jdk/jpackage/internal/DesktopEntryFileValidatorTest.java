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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockExit;
import jdk.jpackage.test.mock.ToolProviderCommandMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DesktopEntryFileValidatorTest {

    @ParameterizedTest
    @EnumSource(value = CommandMockExit.class)
    void test_createDefault(CommandMockExit exit) {

        var validator = DesktopEntryFileValidator.createDefault();

        var counter = new AtomicInteger();

        ThrowingRunnable<Exception> incremeter = counter::getAndIncrement;

        ToolProviderCommandMock desktop_file_validate = CommandActionSpecs.build()
                .action(CommandActionSpec.create("increment counter", incremeter))
                .exit(exit)
                .toCommandMockBuilder().name("desktop-file-validate-mock").create();

        final int validateCount = 10;

        Globals.main(() -> {
            Globals.instance().executorFactory(() -> {
                return new Executor().mapper(executor -> {
                    return executor.copy().mapper(null).toolProvider(desktop_file_validate);
                });
            });

            IntStream.range(0, validateCount).forEach(_ -> {
                var result = validator.validate(Path.of("foo.desktop"));
                switch (exit) {
                    case SUCCEED -> assertEquals(0, result.getExitCode());
                    case EXIT_1 -> assertEquals(1, result.getExitCode());
                    case THROW_MOCK_IO_EXCEPTION -> assertThrowsExactly(IllegalStateException.class, result::getExitCode);
                }
            });

            switch (exit) {
                case SUCCEED, EXIT_1 -> assertEquals(validateCount, counter.get());
                case THROW_MOCK_IO_EXCEPTION -> assertEquals(1, counter.get());
            }

            return 0;
        });
    }
}
