/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * This app defines a module using the ModuleLayer.defineModulesWithOneLoader API
 * which calls the JVM_DefineModule.
 **/

import java.nio.file.Path;
import java.lang.ModuleLayer.Controller;
import java.lang.module.*;
import java.util.List;
import java.util.Set;

public class DefineModuleApp {
    public static void main(String[] args) throws Throwable {
        if (args.length != 2) {
            throw new RuntimeException("DefineModuleApp expects 2 args but saw " + args.length);
        }
        final Path MODS = Path.of(args[0]);
        final String MODULE_NAME = args[1];
        Configuration cf = ModuleLayer.boot()
                .configuration()
                .resolve(ModuleFinder.of(), ModuleFinder.of(MODS), Set.of(MODULE_NAME));
        ResolvedModule m = cf.findModule(MODULE_NAME).orElseThrow();
        ModuleLayer bootLayer = ModuleLayer.boot();
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        Controller controller = ModuleLayer.defineModulesWithOneLoader(cf, List.of(bootLayer), scl);
    }
}
