/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.internal.jobjc.generator.utils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.apple.internal.jobjc.generator.Utils;
import com.apple.internal.jobjc.generator.Utils.Substituter;

/**
 * Utility for generating Java source code.
 */
public abstract class JavaLang {

    public static String makeSingleton(final String instanceVariable, final String methodName, final String className, final String...constructorArgs) {
        return generateSingleton(new Substituter(
            "#private $CLASS $IVAR = null;~" +
            "#public $CLASS $METHOD() {~" +
            "##return $IVAR != null ? $IVAR : ($IVAR = new $CLASS($CTORARGS));~" +
            "#}~"),
        instanceVariable, methodName, className, constructorArgs);
    }

    public static String makeStaticSingleton(final String instanceVariable, final String methodName, final String className, final String...constructorArgs) {
        return generateSingleton(new Substituter(
            "#private static $CLASS $IVAR = null;~" +
            "#public static $CLASS $METHOD() {~" +
            "##return $IVAR != null ? $IVAR : ($IVAR = new $CLASS($CTORARGS));~" +
            "#}~"),
        instanceVariable, methodName, className, constructorArgs);
    }

    private static String generateSingleton(final Substituter singleton, final String instanceVariable, final String methodName, final String className, final String...constructorArgs) {
        singleton.replace("IVAR", instanceVariable);
        singleton.replace("METHOD", methodName);
        singleton.replace("CLASS", className);
        singleton.replace("CTORARGS", Utils.joinWComma(constructorArgs));
        return singleton.toString();
    }

    ///

    public static class JLTertiary{
        public Object cond, tExp, fExp;
        public JLTertiary(){}
        public JLTertiary(Object cond, Object tExp, Object fExp){
            this.cond = cond;
            this.tExp = tExp;
            this.fExp = fExp;
        }
        @Override public String toString() {
            return "((" + cond + ")\n\t? (" + tExp + ")\n\t: (" + fExp + "))";
        }
    }

    public static class JLCall{
        public String fun;
        public List<Object> args = new ArrayList<Object>();
        public JLCall(String fun, Object... args){
            this.fun = fun;
            this.args.addAll(Arrays.asList(args));
        }
        @Override public String toString(){
            return fun + "(" + Fp.join(", ", args) + ")";
        }
    }

    public static class JLField{
        public Set<String> mods = new TreeSet<String>();
        public String type;
        public String name;
        public Object value;

        public JLField(String mods, String type, String name){
            this(mods, type, name, null);
        }

        public JLField(String mods, String type, String name, Object value){
            this.mods.addAll(Arrays.asList(mods.split("\\s")));
            this.type = type;
            this.name = name;
            this.value = value;
        }

        @Override public String toString(){
            return "\t" + Fp.join(" ", mods) + " " + type + " " + name + (value==null ? "" : " = " + value) + ";\n";
        }
    }

    public static class JLCtor extends JLMethod{
        public JLCtor(String mods, String name, Object... args) {
            super(mods, "", name, args);
        }

        @Override public String toString(){
            this.type = "";
            return super.toString();
        }
    }

    public static class JLReturn{
        public Object target;
        public JLReturn(Object target){
            this.target = target;
        }
        @Override public String toString(){
            return "return " + target + ";";
        }
    }

    public static class JLMethod{
        public List<String> jdoc = new ArrayList<String>();
        public Set<String> attrs = new TreeSet<String>();
        public Set<String> mods = new TreeSet<String>();
        public String type;
        public String name;
        public List<Object> args = new ArrayList<Object>();
        public List<Object> body = new ArrayList<Object>();

        public JLMethod(){}
        public JLMethod(String mods, String type, String name, Object... args) {
            this.mods.addAll(Arrays.asList(mods.split("\\s")));
            this.type = type;
            this.name = name;
            this.args.addAll(Arrays.asList(args));
        }

        @Override public String toString(){
            StringWriter out = new StringWriter();
            if(jdoc.size() > 0){
                out.append("\t/**\n");
                out.append("\t * " + Fp.join("\n\t * ", jdoc));
                out.append("\t */\n");
            }
            out.append("\t" + Fp.join(" ", attrs) + " " + Fp.join(" ", mods) + " " + type + " " + name + "(" + Fp.join(", ", args) + "){\n");
            out.append("\t\t" + Fp.join("\n\t\t", body) + "\n");
            out.append("\t}\n");
            return out.toString();
        }
    }
}
