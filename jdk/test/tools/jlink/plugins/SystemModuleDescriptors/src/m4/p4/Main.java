/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package p4;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Layer;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

public class Main {
    // the system module plugin by default drops ModuleTarget attribute
    private static boolean expectModuleTarget = false;
    public static void main(String... args) throws IOException {
        if (args.length > 0) {
            if (!args[0].equals("retainModuleTarget")) {
                throw new IllegalArgumentException(args[0]);
            }

            expectModuleTarget = true;
        }

        // java.base is packaged with osName/osArch/osVersion
        ModuleDescriptor md = Layer.boot().findModule("java.base").get()
                                   .getDescriptor();
        if (!md.osName().isPresent() ||
                !md.osArch().isPresent() ||
                !md.osVersion().isPresent()) {
            throw new RuntimeException("osName/osArch/osVersion is missing: " +
                md.osName() + " " + md.osArch() + " " + md.osVersion());
        }

        // verify module-info.class for m1 and m4
        checkModule("m1", "p1", "p2");
        checkModule("m4", "p4");
    }

    private static void checkModule(String mn, String... packages) throws IOException {
        // verify ModuleDescriptor from the runtime module
        ModuleDescriptor md = Layer.boot().findModule(mn).get()
                                   .getDescriptor();
        checkModuleDescriptor(md, packages);

        // verify ModuleDescriptor from module-info.class read from ModuleReader
        try (InputStream in = ModuleFinder.ofSystem().find(mn).get()
            .open().open("module-info.class").get()) {
            checkModuleDescriptor(ModuleDescriptor.read(in), packages);
        }

        // verify ModuleDescriptor from module-info.class read from jimage
        FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"),
            Collections.emptyMap());
        Path path = fs.getPath("/", "modules", mn, "module-info.class");
        checkModuleDescriptor(ModuleDescriptor.read(Files.newInputStream(path)), packages);
    }

    static void checkModuleDescriptor(ModuleDescriptor md, String... packages) {
        String mainClass = md.name().replace('m', 'p') + ".Main";
        if (!md.mainClass().get().equals(mainClass)) {
            throw new RuntimeException(md.mainClass().toString());
        }

        if (expectModuleTarget) {
            // ModuleTarget attribute is retained
            if (!md.osName().isPresent() || !md.osArch().isPresent()) {
                throw new RuntimeException("osName or osArch is missing: " +
                    md.osName() + " " + md.osArch());
            }
        } else {
            // by default ModuleTarget attribute is dropped
            if (md.osName().isPresent() || md.osArch().isPresent()) {
                throw new RuntimeException("osName and osArch should not be set: " +
                    md.osName() + " " + md.osArch());
            }
        }

        if (md.osVersion().isPresent()) {
            throw new RuntimeException("Expected no osVersion set: " + md.osVersion());
        }

        Set<String> pkgs = md.packages();
        if (!pkgs.equals(Set.of(packages))) {
            throw new RuntimeException(pkgs + " expected: " + Set.of(packages));
        }
    }
}
