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

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import com.apple.internal.jobjc.generator.FunctionGenerator;
import com.apple.internal.jobjc.generator.Utils;
import com.apple.internal.jobjc.generator.model.Category;
import com.apple.internal.jobjc.generator.model.Clazz;
import com.apple.internal.jobjc.generator.model.Constant;
import com.apple.internal.jobjc.generator.model.Framework;
import com.apple.internal.jobjc.generator.model.Function;
import com.apple.internal.jobjc.generator.model.NativeEnum;
import com.apple.internal.jobjc.generator.model.StringConstant;
import com.apple.internal.jobjc.generator.model.Struct;
import com.apple.internal.jobjc.generator.model.types.JType;
import com.apple.internal.jobjc.generator.model.types.JType.JStruct;
import com.apple.internal.jobjc.generator.utils.Fp;
import com.apple.internal.jobjc.generator.utils.JavaLang;
import com.apple.internal.jobjc.generator.utils.Fp.Map1;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLCall;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLField;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLMethod;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLReturn;
import com.apple.jobjc.JObjCRuntime;
import com.apple.jobjc.MacOSXFramework;
import com.apple.jobjc.Invoke.FunCall;

public class FrameworkClassFile extends GeneratedClassFile {
    final Framework framework;

    public FrameworkClassFile(final Framework framework) {
        super(framework.pkg, framework.name + "Framework", MacOSXFramework.class.getName());
        this.framework = framework;
    }

    @Override public void writeBeginning(final PrintStream out) {
        List<String> binPaths = Fp.map(new Map1<File,String>(){
            public String apply(File a) { return "\"" + a.getAbsolutePath() + "\""; }},
            framework.binaries);
        out.println(new Utils.Substituter(
                "#public " + className + "(" + JObjCRuntime.class.getName() + " runtime) {~" +
                "##super(runtime, new String[]{" + Fp.join(", ", binPaths) + "});~" +
                "#}~"
        ));
    }

    @Override public void writeBody(final PrintStream out) {
        for(final Struct struct : new ArrayList<Struct>(framework.structs)){
            out.println("\tpublic " + struct.name + " make" + struct.name + "(){");
            out.println("\t\treturn new " + struct.name + "(getRuntime());");
            out.println("\t}");
            out.println("\tpublic " + struct.name + " make" + struct.name + "(com.apple.jobjc.NativeBuffer base){");
            out.println("\t\treturn new " + struct.name + "(getRuntime(), base);");
            out.println("\t}");
        }

        for(final NativeEnum nenum : framework.enums){
            if(nenum.ignore){
                out.println("\t/**");
                out.println("\t * @deprecated Suggestion: " + nenum.suggestion);
                out.println("\t */");
                out.println("\t@Deprecated");
            }
            out.println(String.format("\tpublic final %3$s %1$s(){ return %2$s; }",
                    nenum.name, nenum.valueToString(), nenum.type.getJType().getJavaReturnTypeName()));
        }

        for(final Constant konst : framework.constants){
            String cacheName = "_" + konst.name;
            final JType jtype = konst.type.getJType();
            final String cast = jtype.getReturnTypeCast() == null ? "" : "(" + jtype.getReturnTypeCast() + ")";
            out.println();

            out.print(new JLField("private", jtype.getJavaTypeName(), cacheName, jtype.getDefaultReturnValue()));

            JLMethod reader = new JLMethod("public final", jtype.getJavaReturnTypeName(), konst.name);
            reader.body.add("if(" + cacheName + " != " + jtype.getDefaultReturnValue() + ") return " + cast + cacheName + ";");

            String contextName = jtype instanceof JStruct ? "returnValue" : "nativeBuffer";

            if(jtype instanceof JStruct)
                reader.body.add(((JStruct)jtype).createReturnValue());
            else
                reader.body.add(jtype.createDeclareBuffer(contextName));

            reader.body.add("getConstant(\"" + konst.name + "\", " + contextName + ", " + jtype.getCoderDescriptor().getCoderInstanceName() + ".sizeof());");

            reader.body.add(jtype.createPop(contextName));
            reader.body.add(cacheName + " = returnValue;");
            reader.body.add(jtype.createReturn());

            out.print(reader);
        }

        for(final StringConstant konst : framework.stringConstants){
            if(Fp.any(new Map1<Constant,Boolean>(){ public Boolean apply(Constant a) {
                return a.name.equals(konst.name);
                }}, new ArrayList<Constant>(framework.constants))){
                System.out.println("Warning: [" + framework.name + "] String constant " + konst.name + " is already defined in constants. Skipping.");
            }
            else{
                out.println("\tpublic final String " + konst.name + "(){ return \"" + escapeQuotes(konst.value) + "\"; }");
            }
        }

        for (final Clazz clazz : framework.classes) {
            final String classClassName = clazz.name + "Class";
            out.println(JavaLang.makeSingleton("_" + classClassName, clazz.name, classClassName, "getRuntime()"));
        }

        for (final Category cat : framework.categories) {
            final String classClassName = cat.category.name + "Class";
            out.println(JavaLang.makeSingleton("_" + classClassName, cat.category.name, classClassName, "getRuntime()"));

            JLMethod jlm = new JLMethod("public", cat.category.name, cat.category.name, "final " + cat.category.superClass.getFullPath() + " obj");
            jlm.body.add(new JLReturn(new JLCall("new " + cat.category.name, "obj", "getRuntime()")));
            out.println(jlm);
        }

        for (final Function fxn : framework.functions){
            FunctionGenerator.writeOutFunction(out, FunCall.class, fxn, null);
        }
    }

    private String escapeQuotes(String s){
        return s.replace("\"", "\\\"");
    }
}
