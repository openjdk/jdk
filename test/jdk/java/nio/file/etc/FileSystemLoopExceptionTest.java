/*
 * Copyright 2026 JetBrains s.r.o.
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
 * @summary Check file system loop exception structure
 * @bug 8393106
 * @requires os.family != "windows"
 * @run main/othervm
 *      FileSystemLoopExceptionTest
 */

import java.nio.channels.FileChannel;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static java.nio.file.StandardOpenOption.READ;

public class FileSystemLoopExceptionTest {

    public static void main(String[] args) throws Exception {
        Path link = Files.createTempDirectory("loop_test").resolve("link");
        Files.createSymbolicLink(link, link);

        try {
            test(
                    new FileSystemLoopException(link.toString()),
                    () -> Files.newInputStream(link).close()
            );

            test(
                    new FileSystemLoopException(link.toString()),
                    () -> Files.newOutputStream(link).close()
            );

            test(
                    new FileSystemLoopException(link.toString()),
                    () -> FileChannel.open(link, READ).close()
            );
        } finally {
            Files.delete(link);
            Files.delete(link.getParent());
        }
    }

    private static void test(FileSystemLoopException expectedException, TestRunnable fn) {
        test(expectedException, () -> {
            fn.run();
            return Void.TYPE;
        });
    }

    private static <T> void test(FileSystemLoopException expectedException, TestComputable<T> fn) {
        final T result;
        try {
            result = fn.run();
        } catch (Exception err) {
            if (err instanceof FileSystemLoopException loopException
                    && Objects.equals(loopException.getFile(), expectedException.getFile())
                    && Objects.equals(loopException.getOtherFile(), expectedException.getOtherFile())
                    && Objects.equals(loopException.getReason(), expectedException.getReason())
            ) {
                return;
            }
            AssertionError assertionError = new AssertionError("Another exception was expected", err);
            assertionError.addSuppressed(expectedException);
            throw assertionError;
        }
        throw new AssertionError("Expected an exception but got a result: " + result);
    }

    @FunctionalInterface
    private interface TestRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface TestComputable<T> {
        T run() throws Exception;
    }
}
