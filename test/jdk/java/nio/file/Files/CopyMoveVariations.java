/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8073061
 * @requires (os.family == "linux") | (os.family == "mac")
 * @summary Test Files.copy and Files.move with numerous parameters
 * @run junit CopyMoveVariations
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static java.nio.file.LinkOption.*;
import static java.nio.file.StandardCopyOption.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class CopyMoveVariations {
    enum OpType {
        COPY,
        MOVE
    }

    enum PathType {
        FILE,
        DIR,
        LINK
    }

    private static final boolean SUPPORTS_POSIX_PERMISSIONS;

    static {
        Path currentDir = null;
        try {
            currentDir = Files.createTempFile(Path.of("."), "this", "that");
            SUPPORTS_POSIX_PERMISSIONS =
                Files.getFileStore(currentDir).supportsFileAttributeView("posix");
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        } finally {
            if (currentDir != null) {
                try {
                    Files.delete(currentDir);
                } catch (IOException ignore)  {
                }
            }
        }
    }

    private static boolean supportsPosixPermissions() {
        return SUPPORTS_POSIX_PERMISSIONS;
    }

    private static Stream<Arguments> params() {
        List<Arguments> list = new ArrayList<Arguments>();

        boolean[] falseAndTrue = new boolean[] {false, true};
        for (PathType type : PathType.values()) {
            String[] modes = new String[] {
                "---------", "r--r--r--", "-w--w--w-", "rw-rw-rw-"
            };
            for (String mode : modes) {
                for (boolean replaceExisting : falseAndTrue) {
                    for (boolean targetExists : falseAndTrue) {
                        list.add(Arguments.of(type, mode, replaceExisting,
                                              targetExists));
                    }
                }
            }
        }

        return list.stream();
    }

    @ParameterizedTest
    @EnabledIf("supportsPosixPermissions")
    @MethodSource("params")
    void copyFollow(PathType type, String mode, boolean replaceExisting,
                    boolean targetExists) throws IOException {
        op(OpType.COPY, type, mode, replaceExisting, targetExists, true);
    }

    @ParameterizedTest
    @EnabledIf("supportsPosixPermissions")
    @MethodSource("params")
    void copyNoFollow(PathType type, String mode, boolean replaceExisting,
                    boolean targetExists) throws IOException {
        op(OpType.COPY, type, mode, replaceExisting, targetExists, false);
    }

    @ParameterizedTest
    @EnabledIf("supportsPosixPermissions")
    @MethodSource("params")
    void move(PathType type, String mode, boolean replaceExisting,
              boolean targetExists) throws IOException {
        op(OpType.MOVE, type, mode, replaceExisting, targetExists, false);
    }

    void op(OpType op, PathType type, String mode, boolean replaceExisting,
            boolean targetExists, boolean followLinks) throws IOException {

        Path source = null;
        Path target = null;
        Path linkTarget = null;
        Path currentDir = Path.of(".");
        try {
            switch (type) {
                case FILE ->
                    source = Files.createTempFile(currentDir, "file", "dat");
                case DIR ->
                    source = Files.createTempDirectory(currentDir, "dir");
                case LINK -> {
                    linkTarget = Files.createTempFile(currentDir, "link", "target");
                    Path link = Path.of("link");
                    source = Files.createSymbolicLink(link, linkTarget);
                }
            }

            Set<PosixFilePermission> perms =
                PosixFilePermissions.fromString(mode);
            if (op == OpType.COPY && type == PathType.LINK && followLinks)
                Files.setPosixFilePermissions(linkTarget, perms);
            else
                Files.setPosixFilePermissions(source, perms);

            if (targetExists)
                target = Files.createTempFile(currentDir, "file", "target");
            else
                target = Path.of("target");

            Set<CopyOption> optionSet = new HashSet();
            if (replaceExisting)
                optionSet.add(REPLACE_EXISTING);
            if (op == OpType.COPY && !followLinks)
                optionSet.add(NOFOLLOW_LINKS);
            CopyOption[] options = optionSet.toArray(new CopyOption[0]);

            final Path src = source;
            final Path dst = target;
            if (type == PathType.FILE) {
                if (op == OpType.COPY) {
                    try {
                        Files.copy(source, target, options);
                        assert Files.exists(target);
                    } catch (AccessDeniedException ade) {
                        assertTrue(mode.charAt(0) != 'r');
                    } catch (FileAlreadyExistsException faee) {
                        assertTrue(targetExists && !replaceExisting);
                    }
                    if (targetExists && mode.charAt(0) == '-')
                        assertTrue(Files.exists(target));
                } else if (!replaceExisting && targetExists) {
                    assertThrows(FileAlreadyExistsException.class,
                                 () -> Files.move(src, dst, options));
                } else {
                    Files.move(source, target, options);
                    assert Files.exists(target);
                }
            } else if (type == PathType.DIR) {
                if (op == OpType.COPY) {
                    try {
                        Files.copy(source, target, options);
                        assert Files.exists(target);
                    } catch (AccessDeniedException ade) {
                        assertTrue(mode.charAt(0) != 'r');
                    } catch (FileAlreadyExistsException faee) {
                        assertTrue(targetExists && !replaceExisting);
                    }
                    if (targetExists && mode.charAt(0) == '-')
                        assertTrue(Files.exists(target));
                } else {
                    try {
                        Files.move(source, target, options);
                        assert Files.exists(target);
                    } catch (AccessDeniedException ade) {
                        assertTrue(mode.charAt(1) != 'w');
                    } catch (FileAlreadyExistsException faee) {
                        assertTrue(targetExists && !replaceExisting);
                    }
                }
            } else if (type == PathType.LINK) {
                if (op == OpType.COPY) {
                    try {
                        Files.copy(source, target, options);
                        assert Files.exists(target);
                    } catch (AccessDeniedException ade) {
                        assertTrue(mode.charAt(0) != 'r');
                    } catch (FileAlreadyExistsException faee) {
                        assertTrue(targetExists && !replaceExisting);
                    }
                } else {
                    try {
                        Files.move(source, target, options);
                        assert Files.exists(target);
                    } catch (AccessDeniedException ade) {
                        assertTrue(mode.charAt(0) != 'r');
                    } catch (FileAlreadyExistsException faee) {
                        assertTrue(targetExists && !replaceExisting);
                    }
                }
            } else {
                assert false;
            }
        } finally {
            try {
                if (source != null)
                    Files.deleteIfExists(source);
            } catch (IOException x) {
            }
            try {
                if (target != null)
                    Files.deleteIfExists(target);
            } catch (IOException x) {
            }
            try {
                if (linkTarget != null)
                    Files.deleteIfExists(linkTarget);
            } catch (IOException x) {
            }
        }
    }
}
