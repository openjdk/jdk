/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.apple.internal.jobjc.generator.classes;

import java.io.PrintStream;
import java.util.List;

import com.apple.internal.jobjc.generator.ClassGenerator;
import com.apple.internal.jobjc.generator.model.Framework;
import com.apple.internal.jobjc.generator.utils.JavaLang;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLCtor;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLField;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLMethod;
import com.apple.jobjc.JObjCRuntime;

public class RootJObjCClass extends GeneratedClassFile {
    private static final String JOBJC_CLASSNAME = "JObjC";
    public static final String FULL_JOBJC_CLASSNAME = ClassGenerator.JOBJC_PACKAGE + "." + JOBJC_CLASSNAME;
    public static final String JOBJC_RUNTIME_INST = FULL_JOBJC_CLASSNAME + ".getInstance()";
    public static final String JOBJC_RUNTIME_INST_R = FULL_JOBJC_CLASSNAME + ".getInstance(getRuntime())";

    public static final String runtimeFrameworkInst(String fwname){
        return JOBJC_RUNTIME_INST + "." + fwname + "()";
    }

    public static final String runtimeFrameworkInstR(String fwname){
        return JOBJC_RUNTIME_INST_R + "." + fwname + "()";
    }

    final List<Framework> frameworks;

    public RootJObjCClass(final List<Framework> frameworks) {
        super(ClassGenerator.JOBJC_PACKAGE, JOBJC_CLASSNAME, "java.lang.Object");
        this.frameworks = frameworks;
    }

    @Override public void writeBeginning(final PrintStream out) {
        out.print(new JLField("private static", JOBJC_CLASSNAME, "instance"));
        out.print(new JLField("private final", JObjCRuntime.class.getName(), "runtime"));

        JLMethod getInstR = new JLMethod("public static", JOBJC_CLASSNAME, "getInstance", "final JObjCRuntime runtime");
        getInstR.body.add("if(runtime == null) throw new NullPointerException(\"runtime\");");
        getInstR.body.add("if(instance == null) instance = new JObjC(runtime);");
        getInstR.body.add("return instance;");
        out.print(getInstR);

        JLMethod getInst = new JLMethod("public static", JOBJC_CLASSNAME, "getInstance");
        getInst.body.add("return getInstance(JObjCRuntime.getInstance());");
        out.print(getInst);

        JLCtor ctor = new JLCtor("private", JOBJC_CLASSNAME, "final JObjCRuntime runtime");
        ctor.body.add("this.runtime = runtime;");
        for (final Framework f : frameworks)
            ctor.body.add("runtime.registerPackage(\"" + f.pkg + "\");");
        out.print(ctor);
    }

    @Override public void writeBody(final PrintStream out) {
        for (final Framework f : frameworks)
            out.println(JavaLang.makeSingleton("_" + f.name, f.name, f.pkg + "." + f.name + "Framework", "runtime"));
    }
}
