/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 7021650
 * @summary Fix Context issues
 * @library /tools/javac/lib
 * @build JavacTestingAbstractProcessor T7021650
 * @run main T7021650
 */

import java.io.*;
import java.net.*;
import java.util.*;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.*;

import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

public class T7021650 extends JavacTestingAbstractProcessor {
    public static void main(String... args) throws Exception {
        new T7021650().run();
    }

    static File testSrc = new File(System.getProperty("test.src"));
    static final int MAX_ROUNDS = 3;

    /**
     * Perform a compilation with custom factories registered in the context,
     * and verify that corresponding objects are created in each round.
     */
    void run() throws Exception {
        Counter demoCounter = new Counter();
        Counter myAttrCounter = new Counter();

        Context context = new Context();
        // Use a custom file manager which creates classloaders for annotation
        // processors with a sensible delegation parent, so that all instances
        // of test classes come from the same class loader. This is important
        // because the test performs class checks on the instances of classes
        // found in the context for each round or processing.
        context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
            public JavaFileManager make(Context c) {
                return new JavacFileManager(c, true, null) {
                    @Override
                    protected ClassLoader getClassLoader(URL[] urls) {
                        return new URLClassLoader(urls, T7021650.class.getClassLoader());
                    }
                };
            }
        });

        Demo.preRegister(context, demoCounter);
        MyAttr.preRegister(context, myAttrCounter);

        String[] args = {
            "-d", ".",
            "-processor", T7021650.class.getName(),
            "-XprintRounds",
            new File(testSrc, T7021650.class.getName() + ".java").getPath()
        };

        compile(context, args);

        // Expect to create Demo for initial round, then MAX_ROUNDS in which
        // GenX files are generated, then standard final round of processing.
        checkEqual("demoCounter", demoCounter.count, MAX_ROUNDS + 2);

        // Expect to create MyAttr for same processing rounds as for Demo,
        // plus additional context for final compilation.
        checkEqual("myAttrCounter", myAttrCounter.count, MAX_ROUNDS + 3);
    }

    void compile(Context context, String... args) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Main m = new Main("javac", pw);
        Main.Result res = m.compile(args, context);
        pw.close();
        String out = sw.toString();
        if (!out.isEmpty())
            System.err.println(out);
        if (!res.isOK())
            throw new Exception("compilation failed unexpectedly: result=" + res);
    }

    void checkEqual(String label, int found, int expect) throws Exception {
        if (found != expect)
            throw new Exception("unexpected value for " + label
                    + ": expected " + expect
                    + ": found " + found);
    }

    //---------------

    /*
     * A custom class unknown to javac but nonetheless registered in the context.
     */
    static class Demo {
        static void preRegister(Context context, final Counter counter) {
            context.put(Demo.class, new Context.Factory<Demo>() {
                public Demo make(Context c) {
                    counter.count++;
                    return new Demo(c);
                }
            });
        }

        Demo(Context c) {
            c.put(Demo.class, this);
        }

        static Demo instance(Context context) {
            return context.get(Demo.class);
        }
    }

    /**
     * A custom version of a standard javac component.
     */
    static class MyAttr extends Attr {
        static void preRegister(Context context, final Counter counter) {
            context.put(attrKey, new Context.Factory<Attr>() {
                public Attr make(Context c) {
                    counter.count++;
                    return new MyAttr(c);
                }
            });
        }

        MyAttr(Context c) {
            super(c);
        }
    }

    static class Counter {
        int count;
    }

    //---------------

    int round = 0;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        round++;

        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();

        // verify items in context as expected
        check("Demo", Demo.instance(context), Demo.class);
        check("Attr", Attr.instance(context), MyAttr.class);

        // For a few rounds, generate new source files, so that we can check whether
        // values in the context are correctly handled in subsequent processing rounds
        if (round <= MAX_ROUNDS) {
            String pkg = "p";
            String currClass = "Gen" + round;
            String curr = pkg + "." + currClass;
            String next = (pkg + ".Gen" + (round + 1));
            StringBuilder text = new StringBuilder();
            text.append("package ").append(pkg).append(";\n");
            text.append("public class ").append(currClass).append(" {\n");
            if (round < MAX_ROUNDS)
                text.append("    ").append(next).append(" x;\n");
            text.append("}\n");

            try {
                JavaFileObject fo = filer.createSourceFile(curr);
                Writer out = fo.openWriter();
                try {
                    out.write(text.toString());
                } finally {
                    out.close();
                }
            } catch (IOException e) {
                throw new Error(e);
            }
        }

        return true;
    }

    void check(String label, Object o, Class<?> clazz) {
        if (o == null)
            throw new IllegalStateException(label + ": no item found");
        if (!clazz.isAssignableFrom(o.getClass()))
            throw new IllegalStateException(label + ": unexpected class: " + o.getClass());
    }
}
