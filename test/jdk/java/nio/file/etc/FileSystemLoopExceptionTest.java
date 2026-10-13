/*
 * Copyright (c) 2026, JetBrains s.r.o.. All rights reserved.
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

/* @test
 * @bug 8393106
 * @summary Check file system loop exception structure
 * @comment `Files.new{Input,Output}Stream()` doesn't trigger a
 *          `FileSystemLoopException` on Windows, and therefore it's excluded
 * @requires os.family != "windows"
 * @library ..
 * @run junit ${test.main.class}
 */

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.channels.FileChannel;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.READ;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class FileSystemLoopExceptionTest {
    private static Path link;

    @BeforeAll
    static void beforeAll() throws Exception {
        link = Files.createTempDirectory("loop_test").resolve("link");
        assumeTrue(TestUtil.supportsSymbolicLinks(link.getParent()), "Test requires symbolic links support");
        Files.createSymbolicLink(link, link);
    }

    @AfterAll
    static void afterAll() throws Exception {
        Files.deleteIfExists(link);
        Files.deleteIfExists(link.getParent());
    }

    @ParameterizedTest
    @MethodSource
    public void symlinkLoop(Function<Path, Executable> executableProvider) {
        FileSystemLoopException expectedException = new FileSystemLoopException(link.toString());
        FileSystemLoopException actualException = assertThrowsExactly(FileSystemLoopException.class, executableProvider.apply(link));

        assertEquals(expectedException.getFile(), actualException.getFile());
        assertEquals(expectedException.getOtherFile(), actualException.getOtherFile());
        assertEquals(expectedException.getReason(), actualException.getReason());
    }

    static private Stream<Arguments> symlinkLoop() {
        return Stream.of(
                Arguments.of((Function<Path, Executable>) link -> () -> Files.newInputStream(link).close()),
                Arguments.of((Function<Path, Executable>) link -> () -> Files.newOutputStream(link).close()),
                Arguments.of((Function<Path, Executable>) link -> () -> FileChannel.open(link, READ).close())
        );
    }
}
