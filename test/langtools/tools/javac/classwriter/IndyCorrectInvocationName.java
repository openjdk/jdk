/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8277634
 * @summary Verify the correct constantpool entries are created for invokedynamic instructions using
 *          the same bootstrap and type, but different name.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.jvm
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.tree
 *          jdk.compiler/com.sun.tools.javac.util
 *          java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.JarTask toolbox.JavacTask toolbox.JavapTask toolbox.ToolBox
 * @run main IndyCorrectInvocationName
 */

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import jdk.internal.classfile.constantpool.*;
import jdk.internal.classfile.instruction.*;

import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.PoolConstant;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.Tag;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import toolbox.JarTask;
import toolbox.ToolBox;


public class IndyCorrectInvocationName implements Plugin {
    private static final String NL = System.lineSeparator();

    public static void main(String... args) throws Exception {
        new IndyCorrectInvocationName().run();
    }

    void run() throws Exception {
        ToolBox tb = new ToolBox();
        Path pluginClasses = Path.of("plugin-classes");
        tb.writeFile(pluginClasses.resolve("META-INF").resolve("services").resolve(Plugin.class.getName()),
                IndyCorrectInvocationName.class.getName() + System.lineSeparator());
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Path.of(ToolBox.testClasses))) {
            for (Path p : ds) {
                if (p.getFileName().toString().startsWith("IndyCorrectInvocationName") ||
                    p.getFileName().toString().endsWith(".class")) {
                    Files.copy(p, pluginClasses.resolve(p.getFileName()));
                }
            }
        }

        Path pluginJar = Path.of("plugin.jar");
        new JarTask(tb, pluginJar)
                .baseDir(pluginClasses)
                .files(".")
                .run();

        Path src = Path.of("src");
            tb.writeJavaFiles(src,
                    """
                    import java.lang.invoke.CallSite;
                    import java.lang.invoke.ConstantCallSite;
                    import java.lang.invoke.MethodHandles;
                    import java.lang.invoke.MethodHandles.Lookup;
                    import java.lang.invoke.MethodType;
                    public class Test{
                        private static final String NL = System.lineSeparator();
                        private static StringBuilder output = new StringBuilder();
                        public static void doRun() {
                            method("a");
                            method("b");
                            method("a");
                            method("b");
                        }
                        public static String run() {
                            doRun();
                            return output.toString();
                        }
                        public static void method(String name) {}
                        public static void actualMethod(String name) {
                            output.append(name).append(NL);
                        }
                        public static CallSite bootstrap(Lookup lookup, String name, MethodType type) throws Exception {
                            return new ConstantCallSite(MethodHandles.lookup()
                                                                     .findStatic(Test.class,
                                                                                 "actualMethod",
                                                                                 MethodType.methodType(void.class,
                                                                                                       String.class))
                                                                     .bindTo(name));
                        }
                    }
                    """);
        Path classes = Files.createDirectories(Path.of("classes"));

        new toolbox.JavacTask(tb)
                .classpath(pluginJar)
                .options("-XDaccessInternalAPI")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run()
                .writeAll();

        URLClassLoader cl = new URLClassLoader(new URL[] {classes.toUri().toURL()});

        String actual = (String) cl.loadClass("Test")
                                   .getMethod("run")
                                   .invoke(null);
        String expected = "a" + NL + "b" + NL + "a" + NL +"b" + NL;
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("expected: " + expected + "; but got: " + actual);
        }

        Path testClass = classes.resolve("Test.class");
        ClassModel cf = Classfile.of().parse(testClass);
        BootstrapMethodsAttribute bootAttr = cf.findAttribute(Attributes.BOOTSTRAP_METHODS).orElseThrow();
        if (bootAttr.bootstrapMethodsSize() != 1) {
            throw new AssertionError("Incorrect number of bootstrap methods: " +
                                     bootAttr.bootstrapMethodsSize());
        }
        CodeAttribute codeAttr = cf.methods().get(1).findAttribute(Attributes.CODE).orElseThrow();
        Set<BootstrapMethodEntry> seenBootstraps = new HashSet<>();
        Set<NameAndTypeEntry> seenNameAndTypes = new HashSet<>();
        Set<String> seenNames = new HashSet<>();
        for (CodeElement i : codeAttr.elementList()) {
            if (i instanceof Instruction instruction) {
                switch (instruction ) {
                    case InvokeDynamicInstruction indy -> {
                        InvokeDynamicEntry dynamicInfo = indy.invokedynamic();
                        seenBootstraps.add(dynamicInfo.bootstrap());
                        seenNameAndTypes.add(dynamicInfo.nameAndType());
                        NameAndTypeEntry nameAndTypeInfo = dynamicInfo.nameAndType();
                        seenNames.add(nameAndTypeInfo.name().stringValue());
                    }
                    case ReturnInstruction returnInstruction -> {
                    }
                    default -> throw new AssertionError("Unexpected instruction: " + instruction.opcode());
                }
            }
        }
        if (seenBootstraps.size() != 1) {
            throw new AssertionError("Unexpected bootstraps: " + seenBootstraps);
        }
        if (seenNameAndTypes.size() != 2) {
            throw new AssertionError("Unexpected names and types: " + seenNameAndTypes);
        }
        if (!seenNames.equals(Set.of("a", "b"))) {
            throw new AssertionError("Unexpected names and types: " + seenNames);
        }
    }

    // Plugin impl...

    @Override
    public String getName() { return "IndyCorrectInvocationName"; }

    @Override
    public void init(JavacTask task, String... args) {
        Context c = ((BasicJavacTask) task).getContext();
        task.addTaskListener(new TaskListener() {
            @Override
            public void started(TaskEvent e) {
                if (e.getKind() == TaskEvent.Kind.GENERATE) {
                    convert(c, (JCCompilationUnit) e.getCompilationUnit());
                }
            }
        });
    }

    @Override
    public boolean autoStart() {
        return true;
    }

    private void convert(Context context, JCCompilationUnit toplevel) {
        TreeMaker make = TreeMaker.instance(context);
        Names names = Names.instance(context);
        Symtab syms = Symtab.instance(context);
        new TreeScanner() {
            MethodSymbol bootstrap;
            @Override
            public void visitClassDef(JCClassDecl tree) {
                bootstrap = (MethodSymbol) tree.sym.members().getSymbolsByName(names.fromString("bootstrap")).iterator().next();
                super.visitClassDef(tree);
            }
            @Override
            public void visitApply(JCMethodInvocation tree) {
                if (tree.args.size() == 1 && tree.args.head.hasTag(Tag.LITERAL)) {
                    String name = (String) ((JCLiteral) tree.args.head).value;
                    Type.MethodType indyType = new Type.MethodType(
                            com.sun.tools.javac.util.List.nil(),
                            syms.voidType,
                            com.sun.tools.javac.util.List.nil(),
                            syms.methodClass
                    );
                    Symbol.DynamicMethodSymbol dynSym = new Symbol.DynamicMethodSymbol(names.fromString(name),
                            syms.noSymbol,
                            bootstrap.asHandle(),
                            indyType,
                            new PoolConstant.LoadableConstant[0]);

                    JCTree.JCFieldAccess qualifier = make.Select(make.QualIdent(bootstrap.owner), dynSym.name);
                    qualifier.sym = dynSym;
                    qualifier.type = syms.voidType;
                    tree.meth = qualifier;
                    tree.args = com.sun.tools.javac.util.List.nil();
                    tree.type = syms.voidType;
                }
                super.visitApply(tree);
            }

        }.scan(toplevel);
    }

}
