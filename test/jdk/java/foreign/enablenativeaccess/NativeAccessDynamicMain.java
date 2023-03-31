/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.Module;
import java.lang.ModuleLayer;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;

// This class creates a dynamic module layer and loads the
// panama_module in it. enableNativeAccess on that dynamic
// module is called depending on the command line option.
//
// Usage:
//   java --enable-native-access=ALL-UNNAMED NativeAccessDynamicMain <module-path> <mod/class> <true|false> [main-args]
public class NativeAccessDynamicMain {
    public static void main(String[] args) throws Exception {
        String modulePath = args[0];
        String moduleAndClsName = args[1];
        boolean enableNativeAccess = Boolean.parseBoolean(args[2]);
        String[] mainArgs = args.length > 2? Arrays.copyOfRange(args, 3, args.length) : new String[0];

        int idx = moduleAndClsName.indexOf('/');
        String moduleName = moduleAndClsName.substring(0, idx);
        String className = moduleAndClsName.substring(idx+1);

        Path[] paths = Stream.of(modulePath.split(File.pathSeparator))
            .map(Paths::get)
            .toArray(Path[]::new);
        ModuleFinder mf = ModuleFinder.of(paths);
        var mrefs = mf.findAll();
        if (mrefs.isEmpty()) {
            throw new RuntimeException("No modules module path: " + modulePath);
        }

        var rootMods = mrefs.stream().
            map(mr->mr.descriptor().name()).
            collect(Collectors.toSet());

        ModuleLayer boot = ModuleLayer.boot();
        var conf = boot.configuration().
            resolve(mf, ModuleFinder.of(), rootMods);
        String firstMod = rootMods.iterator().next();
        URLClassLoader cl = new URLClassLoader(new URL[] { paths[0].toFile().toURL() });
        ModuleLayer.Controller controller = boot.defineModulesWithOneLoader(conf, List.of(boot), cl);
        ModuleLayer layer = controller.layer();
        Module mod = layer.findModule(firstMod).get();

        // conditionally grant native access to the dynamic module created
        if (enableNativeAccess) {
            controller.enableNativeAccess(mod);
        }
        Class mainCls = Class.forName(mod, className);
        var main = mainCls.getMethod("main", String[].class);
        main.invoke(null, (Object)mainArgs);
    }
}
