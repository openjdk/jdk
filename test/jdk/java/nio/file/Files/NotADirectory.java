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

/* @test
 * @bug 8356678
 * @requires (os.family != "windows")
 * @summary Test Files operations when a path component is not a directory
 * @run junit NotADirectory
 */

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotADirectory {
    private static final Path ROOT      = Path.of(".");
    private static final Path NOT_EXIST = ROOT.resolve("notExist");
    private static final Path DIR       = ROOT.resolve("dir");
    private static final Path FILE      = ROOT.resolve("file");
    private static final Path BASE      = ROOT.resolve("foo");
    private static final Path BOGUS     = BASE.resolve("bar");

    private static final Path[] SRCDST = new Path[] {
        NOT_EXIST, DIR, FILE
    };

    private static final CopyOption[][] COPY_OPTIONS = new CopyOption[][] {
        new CopyOption[] {},
        new CopyOption[] {REPLACE_EXISTING},
    };

    private static final CopyOption[][] MOVE_OPTIONS = new CopyOption[][] {
        new CopyOption[] {},
        new CopyOption[] {ATOMIC_MOVE},
        new CopyOption[] {REPLACE_EXISTING},
        new CopyOption[] {ATOMIC_MOVE, REPLACE_EXISTING},
    };

    @BeforeAll
    public static void init() throws IOException {
        Files.createDirectory(DIR);
        Files.createFile(FILE);
        Files.createFile(BASE);
    }

    @AfterAll
    public static void clean() throws IOException {
        Files.delete(DIR);
        Files.delete(FILE);
        Files.delete(BASE);
    }

    private static Stream<Arguments> copyMoveParams(CopyOption[][] options) {
        List<Arguments> list = new ArrayList<Arguments>();
        for (CopyOption[] opts : options) {
            for (Path p : SRCDST) {
                list.add(Arguments.of(p, BOGUS, opts));
                list.add(Arguments.of(BOGUS, p, opts));
            }
        }
        return list.stream();
    }

    private static Stream<Arguments> copyParams() {
        return copyMoveParams(COPY_OPTIONS);
    }

    private static Stream<Arguments> moveParams() {
        return copyMoveParams(MOVE_OPTIONS);
    }

    @ParameterizedTest
    @MethodSource("copyParams")
    public void copy(Path src, Path dst, CopyOption[] opts)
        throws IOException {
        Class exceptionClass = dst.equals(BOGUS) ?
            FileSystemException.class : NoSuchFileException.class;
        assertThrows(exceptionClass,
                     () -> Files.copy(src, dst, opts));
    }

    @ParameterizedTest
    @MethodSource("moveParams")
    public void move(Path src, Path dst, CopyOption[] opts)
        throws IOException {
        Class exceptionClass = src.equals(BOGUS) || dst.equals(BOGUS) ?
            FileSystemException.class : NoSuchFileException.class;
        assertThrows(exceptionClass,
                     () -> Files.move(src, dst, opts));
    }

    @Test
    public void readBasic() throws IOException {
        assertThrows(NoSuchFileException.class,
                     () -> Files.readAttributes(BOGUS, BasicFileAttributes.class));
    }

    @Test
    public void readPosix() throws IOException {
        assertThrows(NoSuchFileException.class,
                     () -> Files.readAttributes(BOGUS, PosixFileAttributes.class));
    }

    @Test
    public void exists() throws IOException {
        assertFalse(Files.exists(BOGUS));
    }

    @Test
    public void notExists() throws IOException {
        assertTrue(Files.notExists(BOGUS));
    }
}
