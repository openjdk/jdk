/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package p1;

import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

public class Main {
    public static void main(String... args) throws Exception {
        // load another package
        p2.T.test();

        // check the module descriptor of an installed module
        validate(Main.class.getModule().getDescriptor());

        // read m1/module-info.class
        FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"),
                                                  Collections.emptyMap());
        Path path = fs.getPath("/", "modules", "m1", "module-info.class");
        validate(ModuleDescriptor.read(Files.newInputStream(path)));
    }

    static void validate(ModuleDescriptor md) {
        checkPackages(md.conceals(), "p1", "p2");
        checkPackages(md.packages(), "p1", "p2");
    }

    static void checkPackages(Set<String> pkgs, String... expected) {
        for (String pn : expected) {
            if (!pkgs.contains(pn)) {
                throw new RuntimeException(pn + " missing in " + pkgs);
            }
        }
    }
}
