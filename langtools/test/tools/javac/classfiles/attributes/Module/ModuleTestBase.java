/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPool;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Module_attribute;
import com.sun.tools.javac.util.Pair;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        ClassFile classFile = ClassFile.read(modulePath.resolve("module-info.class"));
        Module_attribute moduleAttribute = (Module_attribute) classFile.getAttribute("Module");
        ConstantPool constantPool = classFile.constant_pool;

        testRequires(moduleDescriptor, moduleAttribute, constantPool);
        testExports(moduleDescriptor, moduleAttribute, constantPool);
        testProvides(moduleDescriptor, moduleAttribute, constantPool);
        testUses(moduleDescriptor, moduleAttribute, constantPool);
    }

    private void testRequires(ModuleDescriptor moduleDescriptor, Module_attribute module, ConstantPool constantPool) throws ConstantPoolException {
        tr.checkEquals(module.requires_count, moduleDescriptor.requires.size(), "Wrong amount of requires.");

        List<Pair<String, Integer>> actualRequires = new ArrayList<>();
        for (Module_attribute.RequiresEntry require : module.requires) {
            actualRequires.add(Pair.of(
                    require.getRequires(constantPool), require.requires_flags));
        }
        tr.checkContains(actualRequires, moduleDescriptor.requires, "Lists of requires don't match");
    }

    private void testExports(ModuleDescriptor moduleDescriptor, Module_attribute module, ConstantPool constantPool) throws ConstantPool.InvalidIndex, ConstantPool.UnexpectedEntry {
        tr.checkEquals(module.exports_count, moduleDescriptor.exports.size(), "Wrong amount of exports.");
        for (Module_attribute.ExportsEntry export : module.exports) {
            String pkg = constantPool.getUTF8Value(export.exports_index);
            if (tr.checkTrue(moduleDescriptor.exports.containsKey(pkg), "Unexpected export " + pkg)) {
                List<String> expectedTo = moduleDescriptor.exports.get(pkg);
                tr.checkEquals(export.exports_to_count, expectedTo.size(), "Wrong amount of exports to");
                List<String> actualTo = new ArrayList<>();
                for (int toIdx : export.exports_to_index) {
                    actualTo.add(constantPool.getUTF8Value(toIdx));
                }
                tr.checkContains(actualTo, expectedTo, "Lists of \"exports to\" don't match.");
            }
        }
    }

    private void testUses(ModuleDescriptor moduleDescriptor, Module_attribute module, ConstantPool constantPool) throws ConstantPoolException {
        tr.checkEquals(module.uses_count, moduleDescriptor.uses.size(), "Wrong amount of uses.");
        List<String> actualUses = new ArrayList<>();
        for (int usesIdx : module.uses_index) {
            String uses = constantPool.getClassInfo(usesIdx).getBaseName().replace('/', '.');
            actualUses.add(uses);
        }
        tr.checkContains(actualUses, moduleDescriptor.uses, "Lists of uses don't match");
    }

    private void testProvides(ModuleDescriptor moduleDescriptor, Module_attribute module, ConstantPool constantPool) throws ConstantPoolException {
        tr.checkEquals(module.provides_count, moduleDescriptor.provides.size(), "Wrong amount of provides.");
        List<Pair<String, String>> actualProvides = new ArrayList<>();
        for (Module_attribute.ProvidesEntry provide : module.provides) {
            String provides = constantPool.getClassInfo(provide.provides_index).getBaseName().replace('/', '.');
            String with = constantPool.getClassInfo(provide.with_index).getBaseName().replace('/', '.');
            actualProvides.add(Pair.of(provides, with));
        }
        tr.checkContains(actualProvides, moduleDescriptor.provides, "Lists of provides don't match");
    }

    protected void compile(Path base) throws IOException {
        tb.new JavacTask()
                .files(findJavaFiles(base))
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();
    }

    private static Path[] findJavaFiles(Path src) throws IOException {
        return Files.find(src, Integer.MAX_VALUE, (path, attr) -> path.toString().endsWith(".java"))
                .toArray(Path[]::new);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Test {
    }

    class ModuleDescriptor {

        private final String name;
        //pair is name of module and flag(public,mandated,synthetic)
        private final List<Pair<String, Integer>> requires = new ArrayList<>();

        {
            requires.add(new Pair<>("java.base", Module_attribute.ACC_MANDATED));
        }

        private final Map<String, List<String>> exports = new HashMap<>();

        //List of service and implementation
        private final List<Pair<String, String>> provides = new ArrayList<>();
        private final List<String> uses = new ArrayList<>();

        private static final String LINE_END = ";\n";

        StringBuilder content = new StringBuilder("module ");

        public ModuleDescriptor(String moduleName) {
            this.name = moduleName;
            content.append(name).append('{').append('\n');
        }

        public ModuleDescriptor requires(String... requires) {
            for (String require : requires) {
                this.requires.add(Pair.of(require, 0));
                content.append("    requires ").append(require).append(LINE_END);
            }
            return this;
        }

        public ModuleDescriptor requiresPublic(String... requiresPublic) {
            for (String require : requiresPublic) {
                this.requires.add(new Pair<>(require, Module_attribute.ACC_PUBLIC));
                content.append("    requires public ").append(require).append(LINE_END);
            }
            return this;
        }

        public ModuleDescriptor exports(String... exports) {
            for (String export : exports) {
                this.exports.putIfAbsent(export, new ArrayList<>());
                content.append("    exports ").append(export).append(LINE_END);
            }
            return this;
        }

        public ModuleDescriptor exportsTo(String exports, String to) {
            List<String> tos = Pattern.compile(",")
                    .splitAsStream(to)
                    .map(String::trim)
                    .collect(Collectors.toList());
            this.exports.computeIfAbsent(exports, k -> new ArrayList<>()).addAll(tos);
            content.append("    exports ").append(exports).append(" to ").append(to).append(LINE_END);
            return this;
        }

        public ModuleDescriptor provides(String provides, String with) {
            this.provides.add(Pair.of(provides, with));
            content.append("    provides ").append(provides).append(" with ").append(with).append(LINE_END);
            return this;
        }

        public ModuleDescriptor uses(String... uses) {
            Collections.addAll(this.uses, uses);
            for (String use : uses) {
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
    }
}
