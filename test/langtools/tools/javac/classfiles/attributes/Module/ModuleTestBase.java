/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.constantpool.*;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class ModuleTestBase {
    protected final ToolBox tb = new ToolBox();
    private final TestResult tr = new TestResult();


    protected void run() throws Exception {
        boolean noTests = true;
        for (Method method : this.getClass().getMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
                noTests = false;
                try {
                    tr.addTestCase(method.getName());
                    method.invoke(this, Paths.get(method.getName()));
                } catch (Throwable th) {
                    tr.addFailure(th);
                }
            }
        }
        if (noTests) throw new AssertionError("Tests are not found.");
        tr.checkStatus();
    }

    protected void testModuleAttribute(Path modulePath, ModuleDescriptor moduleDescriptor) throws Exception {
        ClassModel classFile = ClassFile.of().parse(modulePath.resolve("module-info.class"));
        ModuleAttribute moduleAttribute = classFile.findAttribute(Attributes.MODULE).orElse(null);
        assert moduleAttribute != null;
        testModuleName(moduleDescriptor, moduleAttribute);
        testModuleFlags(moduleDescriptor, moduleAttribute);
        testRequires(moduleDescriptor, moduleAttribute);
        testExports(moduleDescriptor, moduleAttribute);
        testOpens(moduleDescriptor, moduleAttribute);
        testProvides(moduleDescriptor, moduleAttribute);
        testUses(moduleDescriptor, moduleAttribute);
    }

    private void testModuleName(ModuleDescriptor moduleDescriptor, ModuleAttribute module) {
        tr.checkEquals(module.moduleName().name().stringValue(), moduleDescriptor.name, "Unexpected module name");
    }

    private void testModuleFlags(ModuleDescriptor moduleDescriptor, ModuleAttribute module) {
        tr.checkEquals(module.moduleFlagsMask(), moduleDescriptor.flags, "Unexpected module flags");
    }

    private void testRequires(ModuleDescriptor moduleDescriptor, ModuleAttribute module) {
        tr.checkEquals(module.requires().size(), moduleDescriptor.requires.size(), "Wrong amount of requires.");

        List<Requires> actualRequires = new ArrayList<>();
        for (ModuleRequireInfo require : module.requires()) {
            actualRequires.add(new Requires(
                    require.requires().name().stringValue(),
                    require.requiresFlagsMask()));
        }
        tr.checkContains(actualRequires, moduleDescriptor.requires, "Lists of requires don't match");
    }

    private void testExports(ModuleDescriptor moduleDescriptor, ModuleAttribute module) {
        tr.checkEquals(module.exports().size(), moduleDescriptor.exports.size(), "Wrong amount of exports.");
        for (ModuleExportInfo export : module.exports()) {
            String pkg = export.exportedPackage().name().stringValue();
            if (tr.checkTrue(moduleDescriptor.exports.containsKey(pkg), "Unexpected export " + pkg)) {
                Export expectedExport = moduleDescriptor.exports.get(pkg);
                tr.checkEquals(expectedExport.mask, export.exportsFlagsMask(), "Wrong export flags");
                List<String> expectedTo = expectedExport.to;
                tr.checkEquals(export.exportsTo().size(), expectedTo.size(), "Wrong amount of exports to");
                List<String> actualTo = new ArrayList<>();
                for (ModuleEntry toIdx : export.exportsTo()) {
                    actualTo.add(toIdx.name().stringValue());
                }
                tr.checkContains(actualTo, expectedTo, "Lists of \"exports to\" don't match.");
            }
        }
    }

    private void testOpens(ModuleDescriptor moduleDescriptor, ModuleAttribute module) {
        tr.checkEquals(module.opens().size(), moduleDescriptor.opens.size(), "Wrong amount of opens.");
        for (ModuleOpenInfo open : module.opens()) {
            String pkg = open.openedPackage().name().stringValue();
            if (tr.checkTrue(moduleDescriptor.opens.containsKey(pkg), "Unexpected open " + pkg)) {
                Open expectedOpen = moduleDescriptor.opens.get(pkg);
                tr.checkEquals(expectedOpen.mask, open.opensFlagsMask(), "Wrong open flags");
                List<String> expectedTo = expectedOpen.to;
                tr.checkEquals(open.opensTo().size(), expectedTo.size(), "Wrong amount of opens to");
                List<String> actualTo = new ArrayList<>();
                for (ModuleEntry toIdx : open.opensTo()) {
                    actualTo.add(toIdx.name().stringValue());
                }
                tr.checkContains(actualTo, expectedTo, "Lists of \"opens to\" don't match.");
            }
        }
    }

    private void testUses(ModuleDescriptor moduleDescriptor, ModuleAttribute module) {
        tr.checkEquals(module.uses().size(), moduleDescriptor.uses.size(), "Wrong amount of uses.");
        List<String> actualUses = new ArrayList<>();
        for (ClassEntry usesIdx : module.uses()) {
            if (!usesIdx.asSymbol().isClassOrInterface()) continue; //get basename
            String uses = usesIdx.asInternalName();
            actualUses.add(uses);
        }
        tr.checkContains(actualUses, moduleDescriptor.uses, "Lists of uses don't match");
    }

    private void testProvides(ModuleDescriptor moduleDescriptor, ModuleAttribute module) {
        int moduleProvidesCount = module.provides().stream()
                .mapToInt(e -> e.providesWith().size())
                .sum();
        int moduleDescriptorProvidesCount = moduleDescriptor.provides.values().stream()
                .mapToInt(impls -> impls.size())
                .sum();
        tr.checkEquals(moduleProvidesCount, moduleDescriptorProvidesCount, "Wrong amount of provides.");
        Map<String, List<String>> actualProvides = new HashMap<>();
        for (ModuleProvideInfo provide : module.provides()) {
            if (!provide.provides().asSymbol().isClassOrInterface()) continue;
            String provides = provide.provides().asInternalName();
            List<String> impls = new ArrayList<>();
            for (ClassEntry withEntry: provide.providesWith()) {
                if (!withEntry.asSymbol().isClassOrInterface()) continue;
                String with = withEntry.asInternalName();
                impls.add(with);
            }
            actualProvides.put(provides, impls);
        }
        tr.checkContains(actualProvides.entrySet(), moduleDescriptor.provides.entrySet(), "Lists of provides don't match");
    }

    protected void compile(Path base, String... options) throws IOException {
        new JavacTask(tb)
                .options(options)
                .files(findJavaFiles(base))
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    private static Path[] findJavaFiles(Path src) throws IOException {
        return Files.find(src, Integer.MAX_VALUE, (path, attr) -> path.toString().endsWith(".java"))
                .toArray(Path[]::new);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Test {
    }

    interface Mask {
        int getMask();
    }

    public enum ModuleFlag implements Mask {
        OPEN("open", ClassFile.ACC_OPEN);

        private final String token;
        private final int mask;

        ModuleFlag(String token, int mask) {
            this.token = token;
            this.mask = mask;
        }

        @Override
        public int getMask() {
            return mask;
        }
    }

    public enum RequiresFlag implements Mask {
        TRANSITIVE("transitive", ClassFile.ACC_TRANSITIVE),
        STATIC("static", ClassFile.ACC_STATIC_PHASE),
        MANDATED("", ClassFile.ACC_MANDATED);

        private final String token;
        private final int mask;

        RequiresFlag(String token, int mask) {
            this.token = token;
            this.mask = mask;
        }

        @Override
        public int getMask() {
            return mask;
        }
    }

    public enum ExportsFlag implements Mask {
        SYNTHETIC("", ClassFile.ACC_SYNTHETIC);

        private final String token;
        private final int mask;

        ExportsFlag(String token, int mask) {
            this.token = token;
            this.mask = mask;
        }

        @Override
        public int getMask() {
            return mask;
        }
    }

    public enum OpensFlag implements Mask {
        SYNTHETIC("", ClassFile.ACC_SYNTHETIC);

        private final String token;
        private final int mask;

        OpensFlag(String token, int mask) {
            this.token = token;
            this.mask = mask;
        }

        @Override
        public int getMask() {
            return mask;
        }
    }

    private class Export {
        private final String pkg;
        private final int mask;
        private final List<String> to = new ArrayList<>();

        Export(String pkg, int mask) {
            this.pkg = pkg;
            this.mask = mask;
        }
    }

    private class Open {
        private final String pkg;
        private final int mask;
        private final List<String> to = new ArrayList<>();

        Open(String pkg, int mask) {
            this.pkg = pkg;
            this.mask = mask;
        }
    }

    private class Requires {
        private final String module;
        private final int mask;

        Requires(String module, int mask) {
            this.module = module;
            this.mask = mask;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Requires requires = (Requires) o;
            return mask == requires.mask &&
                    Objects.equals(module, requires.module);
        }

        @Override
        public int hashCode() {
            return Objects.hash(module, mask);
        }
    }

    protected class ModuleDescriptor {

        private final String name;
        private final int flags;

        private final List<Requires> requires = new ArrayList<>();

        {
            requires.add(new Requires("java.base", computeMask(RequiresFlag.MANDATED)));
        }

        private final Map<String, Export> exports = new HashMap<>();
        private final Map<String, Open> opens = new HashMap<>();

        //List of service and implementation
        private final Map<String, List<String>> provides = new LinkedHashMap<>();
        private final List<String> uses = new ArrayList<>();

        private static final String LINE_END = ";\n";

        StringBuilder content = new StringBuilder();

        public ModuleDescriptor(String moduleName, ModuleFlag... flags) {
            this.name = moduleName;
            this.flags = computeMask(flags);
            for (ModuleFlag flag : flags) {
                content.append(flag.token).append(" ");
            }
            content.append("module ").append(moduleName).append('{').append('\n');
        }

        public ModuleDescriptor requires(String module) {
            this.requires.add(new Requires(module, 0));
            content.append("    requires ").append(module).append(LINE_END);

            return this;
        }

        public ModuleDescriptor requires(String module, RequiresFlag... flags) {
            this.requires.add(new Requires(module, computeMask(flags)));

            content.append("    requires ");
            for (RequiresFlag flag : flags) {
                content.append(flag.token).append(" ");
            }
            content.append(module).append(LINE_END);

            return this;
        }

        public ModuleDescriptor exports(String pkg, ExportsFlag... flags) {
            this.exports.put(toInternalForm(pkg), new Export(toInternalForm(pkg), computeMask(flags)));
            content.append("    exports ");
            for (ExportsFlag flag : flags) {
                content.append(flag.token).append(" ");
            }
            content.append(pkg).append(LINE_END);
            return this;
        }

        public ModuleDescriptor exportsTo(String pkg, String to, ExportsFlag... flags) {
            List<String> tos = Pattern.compile(",")
                    .splitAsStream(to)
                    .map(String::trim)
                    .toList();
            this.exports.compute(toInternalForm(pkg), (k,v) -> new Export(k, computeMask(flags)))
                    .to.addAll(tos);

            content.append("    exports ");
            for (ExportsFlag flag : flags) {
                content.append(flag.token).append(" ");
            }
            content.append(pkg).append(" to ").append(to).append(LINE_END);
            return this;
        }

        public ModuleDescriptor opens(String pkg, OpensFlag... flags) {
            this.opens.put(toInternalForm(pkg), new Open(toInternalForm(pkg), computeMask(flags)));
            content.append("    opens ");
            for (OpensFlag flag : flags) {
                content.append(flag.token).append(" ");
            }
            content.append(pkg).append(LINE_END);
            return this;
        }

        public ModuleDescriptor opensTo(String pkg, String to, OpensFlag... flags) {
            List<String> tos = Pattern.compile(",")
                    .splitAsStream(to)
                    .map(String::trim)
                    .toList();
            this.opens.compute(toInternalForm(pkg), (k,v) -> new Open(toInternalForm(k), computeMask(flags)))
                    .to.addAll(tos);

            content.append("    opens ");
            for (OpensFlag flag : flags) {
                content.append(flag.token).append(" ");
            }
            content.append(pkg).append(" to ").append(to).append(LINE_END);
            return this;
        }

        public ModuleDescriptor provides(String provides, String... with) {
            List<String> impls = Arrays.stream(with)
                    .map(this::toInternalForm)
                    .collect(Collectors.toList());
            this.provides.put(toInternalForm(provides), impls);
            content.append("    provides ")
                    .append(provides)
                    .append(" with ")
                    .append(String.join(",", with))
                    .append(LINE_END);
            return this;
        }

        public ModuleDescriptor uses(String... uses) {
            for (String use : uses) {
                this.uses.add(toInternalForm(use));
                content.append("    uses ").append(use).append(LINE_END);
            }
            return this;
        }

        public ModuleDescriptor write(Path path) throws IOException {
            String src = content.append('}').toString();

            tb.createDirectories(path);
            tb.writeJavaFiles(path, src);
            return this;
        }

        private String toInternalForm(String name) {
            return name.replace('.', '/');
        }

        private int computeMask(Mask... masks) {
            return Arrays.stream(masks)
                    .map(Mask::getMask)
                    .reduce((a, b) -> a | b)
                    .orElseGet(() -> 0);
        }
    }
}
