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
package com.apple.internal.jobjc.generator.classes;

import java.io.PrintStream;

import com.apple.internal.jobjc.generator.model.Struct;
import com.apple.internal.jobjc.generator.model.Struct.Field;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor;
import com.apple.internal.jobjc.generator.model.types.JType;
import com.apple.internal.jobjc.generator.model.types.JType.JStruct;
import com.apple.internal.jobjc.generator.model.types.NType.NBitfield;
import com.apple.internal.jobjc.generator.model.types.NType.NStruct;
import com.apple.internal.jobjc.generator.utils.Fp;
import com.apple.internal.jobjc.generator.utils.Fp.Map1;
import com.apple.jobjc.JObjCRuntime;
import com.apple.jobjc.Coder.StructCoder;

public class StructClassFile extends GeneratedClassFile {
    final Struct struct;

    public StructClassFile(final Struct struct) {
        super(struct.parent.pkg, struct.name, com.apple.jobjc.Struct.class.getName());
        this.struct = struct;
    }

    @Override public boolean isFinal(){ return true; }

    @Override public void writeBeginning(final PrintStream out) {
        out.println();
        out.println("\tpublic static int SIZEOF = " + JObjCRuntime.class.getName() + ".IS64 ? "
                + ((NStruct) struct.type.type64).sizeof64() + " : " + ((NStruct) struct.type.type32).sizeof32() + ";");
        out.println();
        out.format("\tpublic final static %1$s getStructCoder(){ return coder; }\n", StructCoder.class.getCanonicalName());
        out.format("\t@Override public final %1$s getCoder(){ return coder; }\n", StructCoder.class.getCanonicalName());
        out.format("\tprivate final static %1$s coder = new %1$s(SIZEOF%2$s%3$s){\n", StructCoder.class.getCanonicalName(),
                (struct.fields.size() > 0 ? ",\n\t\t" : ""),
                Fp.join(",\n\t\t", Fp.map(new Map1<Field,String>(){
                    public String apply(Field a) {
                        return a.type.getJType().getCoderDescriptor().getCoderInstanceName();
                    }}, struct.fields)));
        out.format("\t\t@Override protected %1$s newInstance(%2$s runtime){ return new %1$s(runtime); }\n",
                struct.name,
                JObjCRuntime.class.getCanonicalName());
        out.println("\t};");
        out.println();
        out.println("\t" + struct.name + "(final " + JObjCRuntime.class.getCanonicalName() + " runtime){");
        out.println("\t\tsuper(runtime, SIZEOF);");
        out.println("\t}");
        out.println();
        out.println("\tpublic " + struct.name + "(final " + JObjCRuntime.class.getCanonicalName() + " runtime, final com.apple.jobjc.NativeBuffer buffer) {");
        out.println("\t\tsuper(runtime, buffer, SIZEOF);");
        out.println("\t}");
    }

    @Override public void writeBody(final PrintStream out) {
        for(Struct.Field field : struct.fields){
            if(field.type.type64 instanceof NStruct && field.type.type32 instanceof NStruct)
                writeStructField(field, out);
            else
                writeField(field, out);
        }
    }

    private void writeField(final Struct.Field field, final PrintStream out){
        if(field.type.type32 instanceof NBitfield){
            out.format("\t// Skipping bitfield '%1$s'\n", field.name);
            return;
        }
        String privName = field.name + "__";
        String offsetName = field.name.toUpperCase() + "_OFFSET";
        JType jtype = field.type.getJType();
        String retType = jtype.getJavaReturnTypeName();
        CoderDescriptor cdesc = jtype.getCoderDescriptor();
        out.println();
        out.println("\tprivate static final int " + offsetName + " = " + JObjCRuntime.class.getName()
                + ".IS64 ? " + field.field64.offset64() + " : " + field.field32.offset32() + ";");

        out.println("\t//" + cdesc.getClass().toString());
        out.println("\tpublic " + retType + " " + getterName(field) + "(){");
        out.println(jtype.createPopAddr("getRuntime()", "this.raw.bufferPtr + " + offsetName));
        out.println(jtype.createReturn());
        out.println("\t}");
        out.println();
        out.println("\tpublic void " + setterName(field.name) + "(final " + retType + " " + privName + "){");
        out.println("\t\t" + cdesc.getPushAddrStatementFor("getRuntime()", "this.raw.bufferPtr + " + offsetName, privName));
        out.println("\t}");
    }

    private void writeStructField(final Struct.Field field, final PrintStream out){
        if(field.type.getJType() == null || !(field.type.getJType() instanceof JStruct)){
            out.println("\t// Found bad JavaType (" + field.type.getJType() + ") for field (" + field.name + ") of type (" + field.type + ")");
            return;
        }
        String privName = field.name + "__";
        String offsetName = field.name.toUpperCase() + "_OFFSET";
        JStruct jstype = (JStruct) field.type.getJType();
        String retTypeName = jstype.getJavaReturnTypeName();
        out.println();
        out.println("\tprivate static final int " + offsetName + " = " + JObjCRuntime.class.getName() + ".IS64"
                + " ? " + field.field64.offset64() + " : " + field.field32.offset32() + ";");

        out.println("\tprivate " + retTypeName + " " + privName + " = null;");
        out.println("\tpublic " + retTypeName + " " + getterName(field) + "(){");
        out.println("\t\tif(null==" + privName + "){");
        out.println("\t\t\tthis.raw.position(" + offsetName + ");");
        out.println("\t\t\t" + privName + " = " + RootJObjCClass.runtimeFrameworkInstR(struct.parent.name) + ".make" + jstype.struct.name + "(this.raw.slice());");
        out.println("\t\t}");
        out.println("\t\treturn " + privName + ";");
        out.println("\t}");
    }

    private String getterName(Struct.Field field) {
        if(com.apple.internal.jobjc.generator.RestrictedKeywords.isRestricted(field.name))
            return field.name + field.type.getJType().getAppendableDescription();
        return field.name;
    }

    private String setterName(String name) {
        return "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @Override public void writeEnd(final PrintStream out) {
    }
}
