/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shell script resource.
 */
final class ShellScriptResource {

    ShellScriptResource(String publicFileName) {
        this.publicFileName = Path.of(publicFileName);
    }

    void saveInFolder(Path folder) throws IOException {
        Path dstFile = folder.resolve(publicFileName);
        resource.saveToFile(dstFile);

        Files.setPosixFilePermissions(dstFile, Stream.of(execPerms, Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ
        )).flatMap(x -> x.stream()).collect(Collectors.toSet()));
    }

    ShellScriptResource setResource(OverridableResource v) {
        resource = v;
        return this;
    }

    ShellScriptResource onlyOwnerCanExecute(boolean v) {
        execPerms = v ? OWNER_CAN_EXECUTE : ALL_CAN_EXECUTE;
        return this;
    }

    OverridableResource getResource() {
        return resource;
    }

    final Path publicFileName;
    private Set<PosixFilePermission> execPerms = ALL_CAN_EXECUTE;
    private OverridableResource resource;

    private static final Set<PosixFilePermission> ALL_CAN_EXECUTE = Set.of(
            PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_EXECUTE);
    private static final Set<PosixFilePermission> OWNER_CAN_EXECUTE = Set.of(
            PosixFilePermission.OWNER_EXECUTE);
}
