/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongBiFunction;
import java.util.stream.Stream;
import static java.nio.file.StandardOpenOption.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8293502
 * @requires (os.family == "linux")
 * @summary Ensure that copying from a file in /proc works
 * @run junit/othervm CopyProcFile
 */
public class CopyProcFile {
    static final String SOURCE = "/proc/version";
    static final String BUFFERED_COPY = "bufferedCopy";
    static final String TARGET = "target";

    static final int BUF_SIZE = 8192;

    static long theSize;

    // copy src to dst via Java buffers
    static long bufferedCopy(String src, String dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] b = new byte[BUF_SIZE];
            long total = 0;
            int n;
            while ((n = in.read(b)) > 0) {
                out.write(b, 0, n);
                total += n;
            }
            return total;
        }
    }

    // copy src to dst using Files::copy
    static long copy(String src, String dst) {
        try {
            Path target = Files.copy(Path.of(src), Path.of(dst));
            return Files.size(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // copy src to dst using InputStream::transferTo
    static long transferToIO(String src, String dst) {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            return in.transferTo(out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // copy src to dst using FileChannel::transferTo
    static long transferToNIO(String src, String dst) {
        try (FileChannel fci = FileChannel.open(Path.of(src), READ);
             FileChannel fco = FileChannel.open(Path.of(dst), CREATE_NEW, WRITE);) {
            return fci.transferTo(0, Long.MAX_VALUE, fco);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // copy src to dst using FileChannel::transferFrom
    static long transferFrom(String src, String dst) {
        try (FileChannel fci = FileChannel.open(Path.of(src), READ);
             FileChannel fco = FileChannel.open(Path.of(dst), CREATE_NEW, WRITE);) {
            return fco.transferFrom(fci, 0, Long.MAX_VALUE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeAll
    public static void createBufferedCopy() throws IOException {
        System.out.printf("Using source file \"%s\"%n", SOURCE);
        try {
            theSize = bufferedCopy(SOURCE, BUFFERED_COPY);
            System.out.printf("Copied %d bytes from %s%n", theSize, SOURCE);
        } catch (IOException e) {
            try {
                Files.delete(Path.of(BUFFERED_COPY));
            } catch (IOException ignore) {}
            throw e;
        }
        if (Files.mismatch(Path.of(BUFFERED_COPY), Path.of(SOURCE)) != -1) {
            throw new RuntimeException("Copy does not match source");
        }
    }

    @AfterAll
    public static void deleteBufferedCopy() {
        try {
            Files.delete(Path.of(BUFFERED_COPY));
        } catch (IOException ignore) {}
    }

    static class FHolder {
        ToLongBiFunction<String,String> f;

        FHolder(ToLongBiFunction<String,String> f) {
            this.f = f;
        }

        long apply(String src, String dst) {
            return f.applyAsLong(src, dst);
        }
    }

    static Stream<FHolder> functions() throws IOException {
        return Stream.of
            (new FHolder((s, d) -> copy(s, d)),
             new FHolder((s, d) -> transferToIO(s, d)),
             new FHolder((s, d) -> transferToNIO(s, d)),
             new FHolder((s, d) -> transferFrom(s, d)));
    }

    @ParameterizedTest
    @MethodSource("functions")
    public void testCopyAndTransfer(FHolder f) throws IOException {
        try {
            long size = f.apply(SOURCE, TARGET);
            if (size != theSize) {
                throw new RuntimeException("Size: expected " + theSize +
                                           "; actual: " + size);
            }
            long mismatch = Files.mismatch(Path.of(BUFFERED_COPY),
                                           Path.of(TARGET));
            assertEquals(-1, mismatch, "Target does not match copy");
        } finally {
            try {
                Files.delete(Path.of(TARGET));
            } catch (IOException ignore) {}
        }
    }
}
