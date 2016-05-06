/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package toolbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleBuilder {

    private final ToolBox tb;
    private final String name;
    private String requires = "";
    private String exports = "";
    private String uses = "";
    private String provides = "";
    private String modulePath = "";
    private List<String> content = new ArrayList<>();

    public ModuleBuilder(ToolBox tb, String name) {
        this.tb = tb;
        this.name = name;
    }

    public ModuleBuilder requiresPublic(String requires, Path... modulePath) {
        return requires("public " + requires, modulePath);
    }

    public ModuleBuilder requires(String requires, Path... modulePath) {
        this.requires += "    requires " + requires + ";\n";
        this.modulePath += Arrays.stream(modulePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        return this;
    }

    public ModuleBuilder exportsTo(String pkg, String module) {
        return exports(pkg + " to " + module);
    }

    public ModuleBuilder exports(String pkg) {
        this.exports += "    exports " + pkg + ";\n";
        return this;
    }

    public ModuleBuilder uses(String uses) {
        this.uses += "    uses " + uses + ";\n";
        return this;
    }

    public ModuleBuilder provides(String service, String implementation) {
        this.provides += "    provides " + service + " with " + implementation + ";\n";
        return this;
    }

    public ModuleBuilder classes(String... content) {
        this.content.addAll(Arrays.asList(content));
        return this;
    }

    public Path write(Path where) throws IOException {
        Files.createDirectories(where);
        List<String> sources = new ArrayList<>();
        sources.add("module " + name + "{"
                + requires
                + exports
                + uses
                + provides
                + "}");
        sources.addAll(content);
        Path moduleSrc = where.resolve(name + "/src");
        tb.writeJavaFiles(moduleSrc, sources.toArray(new String[]{}));
        return moduleSrc;
    }

    public void build(Path where) throws IOException {
        Path moduleSrc = write(where);
        new JavacTask(tb)
                .outdir(where.resolve(name))
                .options("-mp", modulePath)
                .files(tb.findJavaFiles(moduleSrc))
                .run()
                .writeAll();
    }
}
