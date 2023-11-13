/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8004727
 * @summary javac should generate method parameters correctly.
 * @modules java.base/jdk.internal.classfile
 *          java.base/jdk.internal.classfile.attribute
 *          java.base/jdk.internal.classfile.constantpool
 *          java.base/jdk.internal.classfile.instruction
 *          java.base/jdk.internal.classfile.components
 *          java.base/jdk.internal.classfile.impl
 *          jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.comp
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.model
 *          jdk.compiler/com.sun.tools.javac.util
 */
// key: opt.arg.parameters
import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.*;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.io.*;
import javax.lang.model.element.*;
import java.nio.file.Files;
import java.util.*;

public class MethodParametersTest {

    static final String Foo_name = "Foo";
    static final String Foo_contents =
        "public class Foo {\n" +
        "  Foo() {}\n" +
        "  void foo0() {}\n" +
        "  void foo2(int j, int k) {}\n" +
        "}";
    static final String Bar_name = "Bar";
    static final String Bar_contents =
        "public class Bar {\n" +
        "  Bar(int i) {}" +
        "  Foo foo() { return new Foo(); }\n" +
        "}";
    static final String Baz_name = "Baz";
    static final String Baz_contents =
        "public class Baz {\n" +
        "  int baz;" +
        "  Baz(int i) {}" +
        "}";
    static final String Qux_name = "Qux";
    static final String Qux_contents =
        "public class Qux extends Baz {\n" +
        "  Qux(int i) { super(i); }" +
        "}";
    static final File classesdir = new File("methodparameters");

    public static void main(String... args) throws Exception {
        new MethodParametersTest().run();
    }

    void run() throws Exception {
        classesdir.mkdir();
        final File Foo_java =
            writeFile(classesdir, Foo_name + ".java", Foo_contents);
        final File Bar_java =
            writeFile(classesdir, Bar_name + ".java", Bar_contents);
        final File Baz_java =
            writeFile(classesdir, Baz_name + ".java", Baz_contents);
        System.err.println("Test compile with -parameter");
        compile("-parameters", "-d", classesdir.getPath(), Foo_java.getPath());
        // First test: make sure javac doesn't choke to death on
        // MethodParameter attributes
        System.err.println("Test compile with classfile containing MethodParameter attributes");
        compile("-parameters", "-d", classesdir.getPath(),
                "-cp", classesdir.getPath(), Bar_java.getPath());
        System.err.println("Examine class foo");
        checkFoo();
        checkBar();
        System.err.println("Test debug information conflict");
        compile("-g", "-parameters", "-d", classesdir.getPath(),
                "-cp", classesdir.getPath(), Baz_java.getPath());
        System.err.println("Introducing debug information conflict");
        Baz_java.delete();
        modifyBaz(false);
        System.err.println("Checking language model");
        inspectBaz();
        System.err.println("Permuting attributes");
        modifyBaz(true);
        System.err.println("Checking language model");
        inspectBaz();

        if(0 != errors)
            throw new Exception("MethodParameters test failed with " +
                                errors + " errors");
    }

    void inspectBaz() throws Exception {
        final File Qux_java =
            writeFile(classesdir, Qux_name + ".java", Qux_contents);
        final String[] args = { "-parameters", "-d",
                                classesdir.getPath(),
                                "-cp", classesdir.getPath(),
                                Qux_java.getPath() };
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);

        // We need to be able to crack open javac and look at its data
        // structures.  We'll rig up a compiler instance, but keep its
        // Context, thus allowing us to get at the ClassReader.
        Context context = new Context();
        Main comp =  new Main("javac", pw);
        JavacFileManager.preRegister(context);

        // Compile Qux, which uses Baz.
        comp.compile(args, context);
        pw.close();
        final String out = sw.toString();
        if (out.length() > 0)
            System.err.println(out);

        // Now get the class finder, construct a name for Baz, and load it.
        com.sun.tools.javac.code.ClassFinder cf =
            com.sun.tools.javac.code.ClassFinder.instance(context);
        Name name = Names.instance(context).fromString(Baz_name);
        Symtab syms = Symtab.instance(context);

        // Now walk down the language model and check the name of the
        // parameter.
        final Element baz = cf.loadClass(syms.unnamedModule, name);
        for (Element e : baz.getEnclosedElements()) {
            if (e instanceof ExecutableElement ee) {
                final List<? extends VariableElement> params =
                    ee.getParameters();
                if (1 != params.size())
                    throw new Exception("Classfile Baz badly formed: wrong number of methods");
                final VariableElement param = params.get(0);
                if (!param.getSimpleName().contentEquals("baz")) {
                    errors++;
                    System.err.println("javac did not correctly resolve the metadata conflict, parameter's name reads as " + param.getSimpleName());
                } else
                    System.err.println("javac did correctly resolve the metadata conflict");
            }
        }
    }

    void modifyBaz(boolean flip) throws Exception {
        final File Baz_class = new File(classesdir, Baz_name + ".class");
        final ClassModel baz = Classfile.of().parse(Baz_class.toPath());

        // Find MethodParameters and the Code attributes
        if (baz.methods().size() != 1)
            throw new Exception("Classfile Baz badly formed: wrong number of methods");
        if (!baz.methods().get(0).methodName().equalsString("<init>"))
            throw new Exception("Classfile Baz badly formed: method has name " +
                                baz.methods().get(0).methodName().stringValue());
        MethodParametersAttribute mpattr = baz.methods().get(0).findAttribute(Attributes.METHOD_PARAMETERS).orElse(null);
        CodeAttribute cattr = baz.methods().get(0).findAttribute(Attributes.CODE).orElse(null);;
        if (null == mpattr)
            throw new Exception("Classfile Baz badly formed: no method parameters info");
        if (null == cattr)
            throw new Exception("Classfile Baz badly formed: no local variable table");

        // Alter the MethodParameters attribute, changing the name of
        // the parameter from i to baz.
        byte[] bazBytes = Classfile.of().transform(baz, ClassTransform.transformingMethods((methodBuilder, methodElement) -> {
            if (methodElement instanceof MethodParametersAttribute a) {
                List<MethodParameterInfo> newParameterInfos = new ArrayList<>();
                for (MethodParameterInfo info : a.parameters()) {
                    newParameterInfos.add(MethodParameterInfo.ofParameter("baz".describeConstable(), info.flagsMask()));
                }
                a = MethodParametersAttribute.of(newParameterInfos);
                methodBuilder.with(a);
            } else {
                methodBuilder.with(methodElement);
            }
        }));

        // Flip the code and method attributes().  This is for checking
        // that order doesn't matter.
        if (flip) {
            bazBytes = Classfile.of().transform(baz, ClassTransform.transformingMethods((methodBuilder, methodElement) -> {
                if (methodElement instanceof MethodParametersAttribute) {
                    methodBuilder.with(cattr);
                } else if (methodElement instanceof CodeAttribute){
                    methodBuilder.with(mpattr);
                } else {
                    methodBuilder.with(methodElement);
                }
            }));
        }
        Files.write(Baz_class.toPath(), bazBytes);
    }

    // Run a bunch of structural tests on foo to make sure it looks right.
    void checkFoo() throws Exception {
        final File Foo_class = new File(classesdir, Foo_name + ".class");
        final ClassModel foo = Classfile.of().parse(Foo_class.toPath());
        for (int i = 0; i < foo.methods().size(); i++) {
            System.err.println("Examine method Foo." + foo.methods().get(i).methodName());
            if (foo.methods().get(i).methodName().equalsString("foo2")) {
                for (int j = 0; j < foo.methods().get(i).attributes().size(); j++)
                    if (foo.methods().get(i).attributes().get(j) instanceof  MethodParametersAttribute mp) {
                        System.err.println("Foo.foo2 should have 2 parameters: j and k");
                        if (2 != mp.parameters().size())
                            error("expected 2 method parameter entries in foo2, got " +
                                  mp.parameters().size());
                        else if (!mp.parameters().get(0).name().orElseThrow().equalsString("j"))
                            error("expected first parameter to foo2 to be \"j\", got \"" +
                                  mp.parameters().get(0).name().orElseThrow().stringValue() +
                                  "\" instead");
                        else if  (!mp.parameters().get(1).name().orElseThrow().equalsString("k"))
                            error("expected first parameter to foo2 to be \"k\", got \"" +
                                 mp.parameters().get(1).name().orElseThrow() +
                                  "\" instead");
                    }
            }
            else if (foo.methods().get(i).methodName().equalsString("<init>")) {
                for (int j = 0; j < foo.methods().get(i).attributes().size(); j++) {
                    if (foo.methods().get(i).attributes().get(j) instanceof
                        MethodParametersAttribute)
                        error("Zero-argument constructor shouldn't have MethodParameters");
                }
            }
            else if (foo.methods().get(i).methodName().equalsString("foo0")) {
                for (int j = 0; j < foo.methods().get(i).attributes().size(); j++)
                    if (foo.methods().get(i).attributes().get(j) instanceof
                        MethodParametersAttribute)
                        error("Zero-argument method shouldn't have MethodParameters");
            }
            else
                error("Unknown method " + foo.methods().get(i).methodName() + " showed up in class Foo");
        }
    }

    // Run a bunch of structural tests on Bar to make sure it looks right.
    void checkBar() throws Exception {
        final File Bar_class = new File(classesdir, Bar_name + ".class");
        final ClassModel bar = Classfile.of().parse(Bar_class.toPath());
        for (int i = 0; i < bar.methods().size(); i++) {
            System.err.println("Examine method Bar." + bar.methods().get(i).methodName());
            if (bar.methods().get(i).methodName().equalsString("<init>")) {
                for (int j = 0; j < bar.methods().get(i).attributes().size(); j++)
                    if (bar.methods().get(i).attributes().get(j) instanceof
                        MethodParametersAttribute mp) {
                        System.err.println("Bar constructor should have 1 parameter: i");
                        if (1 != mp.parameters().size())
                            error("expected 1 method parameter entries in constructor, got " +
                                  mp.parameters().size());
                        else if (!mp.parameters().get(0).name().orElseThrow().equalsString("i"))
                            error("expected first parameter to foo2 to be \"i\", got \"" +
                                  mp.parameters().get(0).name().orElseThrow() +
                                  "\" instead");
                    }
            }
            else if (bar.methods().get(i).methodName().equalsString("foo")) {
                for (int j = 0; j < bar.methods().get(i).attributes().size(); j++) {
                    if (bar.methods().get(i).attributes().get(j) instanceof
                        MethodParametersAttribute)
                        error("Zero-argument constructor shouldn't have MethodParameters");
                }
            }
        }
    }

    String compile(String... args) throws Exception {
        System.err.println("compile: " + Arrays.asList(args));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = com.sun.tools.javac.Main.compile(args, pw);
        pw.close();
        String out = sw.toString();
        if (out.length() > 0)
            System.err.println(out);
        if (rc != 0)
            error("compilation failed, rc=" + rc);
        return out;
    }

    File writeFile(File dir, String path, String body) throws IOException {
        File f = new File(dir, path);
        f.getParentFile().mkdirs();
        FileWriter out = new FileWriter(f);
        out.write(body);
        out.close();
        return f;
    }

    void error(String msg) {
        System.err.println("Error: " + msg);
        errors++;
    }

    int errors;
}
