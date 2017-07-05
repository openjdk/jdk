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
package com.apple.internal.jobjc.generator.model.types;

import com.apple.internal.jobjc.generator.classes.RootJObjCClass;
import com.apple.internal.jobjc.generator.model.CFType;
import com.apple.internal.jobjc.generator.model.Clazz;
import com.apple.internal.jobjc.generator.model.Opaque;
import com.apple.internal.jobjc.generator.model.Struct;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor;
import com.apple.internal.jobjc.generator.model.coders.ComplexCoderDescriptor;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor.IDCoderDescriptor;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor.NSClassCoderDescriptor;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor.PointerCoderDescriptor;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor.SELCoderDescriptor;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor.StructCoderDescriptor;
import com.apple.internal.jobjc.generator.model.coders.CoderDescriptor.UnknownCoderDescriptor;
import com.apple.internal.jobjc.generator.model.types.NType.NPrimitive;
import com.apple.jobjc.ID;
import com.apple.jobjc.NSClass;
import com.apple.jobjc.NativeArgumentBuffer;
import com.apple.jobjc.Pointer;
import com.apple.jobjc.SEL;

public abstract class JType {
    public abstract String getJavaTypeName();
    public String getJavaClassName() { return getJavaTypeName().substring(getJavaTypeName().lastIndexOf('.') + 1); }
    public String getJavaReturnTypeName() { return getJavaTypeName(); }
    public String getReturnTypeCast() { return null; }
    public String getTypeNameAsParam() { return getJavaTypeName(); }

    public abstract CoderDescriptor getCoderDescriptor();
    public String getDefaultReturnValue() { return "null"; }
    public String getAppendableDescription() { return getJavaTypeName().substring(getJavaTypeName().lastIndexOf('.') + 1); }

    /**
     * Used for primitive types (like int) that can't be used as generic arguments. This returns an appropriate Java class (like Integer).
     */
    public JType getParameterizableType() { return this; }

    //
    // Writer ops
    //

    public String createDeclareBuffer(String contextName) {
        return "final " + NativeArgumentBuffer.class.getName() + " " + contextName + " = getRuntime().getThreadLocalState();";
    }

    public String createInit(final String contextName, final String functionIdentifier, final String initWithObj) {
        return functionIdentifier + ".init(" + contextName + (initWithObj != null ? ", " + initWithObj : "") + ");";
    }

    public String createInvoke(final String contextName, final String functionIdentifier) {
        return functionIdentifier + ".invoke(" + contextName + ");";
    }

    public String createPop(final String contextName) {
        return getCoderDescriptor().getPopStatementFor(contextName, getJavaTypeName(), "returnValue", null);
    }

    public String createPopAddr(final String runtime, final String addr) {
        return getCoderDescriptor().getPopAddrStatementFor(runtime, addr, getJavaTypeName(), "returnValue", null);
    }

    public String createReturn() {
        final String preCast = getReturnTypeCast();
        return "return " + (preCast == null ? "" : "(" + preCast + ")") + "returnValue;";
    }

    //
    // Specialized
    //

    static public class JUnknown extends JType {
        final Type type;
        protected JUnknown(final Type type) {
            this.type = type;
            TypeCache.inst().getUnknownTypes().add(type);
        }
        @Override public String getJavaTypeName() { return "Object /* " + type + " */"; }
        @Override public String getAppendableDescription() { return "Unknown"; }
        @Override public CoderDescriptor getCoderDescriptor() { return UnknownCoderDescriptor.UNKNOWN_DESC; }
    }

    static class JVoid extends JType {
        public static JVoid INST = new JVoid();
        @Override public String getJavaTypeName() { return "Void"; }
        @Override public String getJavaReturnTypeName() { return "void"; }
        @Override public CoderDescriptor getCoderDescriptor(){ return CoderDescriptor.VOID_DESC; }
        @Override public String createPop(final String contextName){ return ""; }
        @Override public String createReturn(){ return ""; }
    };

    static class JSelector extends JType {
        public static JSelector INST = new JSelector();
        @Override public String getJavaTypeName() { return SEL.class.getName(); }
        @Override public CoderDescriptor getCoderDescriptor() { return SELCoderDescriptor.INST; }
    };

    static class JCFType extends JType{
        final CFType cfType;
        public JCFType(final CFType cfType){ this.cfType = cfType; }
        @Override public String getJavaTypeName() { return cfType.parent.pkg + "." + cfType.name + "CFType"; }
        @Override public CoderDescriptor getCoderDescriptor() { return PointerCoderDescriptor.INST; }
        @Override public String createPop(final String contextName) {
            return "\t\t" + getCoderDescriptor().getPopStatementFor(contextName, getJavaReturnTypeName(), "returnValue", "new " + getJavaTypeName());
        }
    }

    static class JOpaque extends JType{
        final Opaque opaque;
        public JOpaque(final Opaque opaque){ this.opaque = opaque; }
        @Override public String getJavaTypeName() { return opaque.parent.pkg + "." + opaque.name + "Opaque"; }
        @Override public CoderDescriptor getCoderDescriptor() { return PointerCoderDescriptor.INST; }
        @Override public String createPop(final String contextName) {
            return "\t\t" + getCoderDescriptor().getPopStatementFor(contextName, getJavaReturnTypeName(), "returnValue", "new " + getJavaTypeName());
        }
    }

    static class JPointer extends JType {
        static JType VOID_PTR = new JPointer(JVoid.INST);

        final JType subject;
        protected JPointer(final JType javaType) { this.subject = javaType; }

        @Override public String getJavaTypeName() { return Pointer.class.getName() + "<" + subject.getParameterizableType().getJavaTypeName() + ">"; }
        @Override public String getAppendableDescription() { return "PointerTo" + subject.getAppendableDescription(); }
        @Override public CoderDescriptor getCoderDescriptor() { return PointerCoderDescriptor.INST; }
    }

    static class JObject extends JType {
        public static JType ID_TYPE = new JType() {
            @Override public String getJavaTypeName() { return ID.class.getName(); }
            @Override public String getJavaReturnTypeName() { return "<T extends " + getJavaTypeName() + "> T"; }
            @Override public String getReturnTypeCast() { return "T"; }
            @Override public CoderDescriptor getCoderDescriptor() { return IDCoderDescriptor.INST; }
        };

        final Type type;
        final Clazz clazz;

        public JObject(final Type type, final Clazz clazz) {
            this.type = type;
            this.clazz = clazz;
        }

        @Override public String getJavaTypeName() { return clazz.getFullPath();}
        @Override public CoderDescriptor getCoderDescriptor() { return IDCoderDescriptor.INST; }
    }

    static class JClass extends JType {
        public static JClass INST = new JClass();
        @Override public String getJavaTypeName() { return NSClass.class.getName(); }
        @Override public String getJavaReturnTypeName() { return "<T extends " + super.getJavaReturnTypeName() + "> T"; }
        @Override public String getTypeNameAsParam() { return super.getTypeNameAsParam(); }
        @Override public String getReturnTypeCast() { return "T"; }
        @Override public CoderDescriptor getCoderDescriptor() { return NSClassCoderDescriptor.INST; }
    };

    public static class JStruct extends JType {
        public final Struct struct;
        public JStruct(final Struct struct) { this.struct = struct; }

        @Override public String getJavaTypeName() { return struct.parent.pkg + "." + struct.name; }
        @Override public String getJavaReturnTypeName() { return getJavaTypeName(); }

        StructCoderDescriptor coderDescriptor = new StructCoderDescriptor(this);
        @Override public CoderDescriptor getCoderDescriptor() { return coderDescriptor; }

        public String createReturnValue() {
            return "\t\t" + getJavaReturnTypeName() + " returnValue = " + RootJObjCClass.runtimeFrameworkInstR(struct.parent.name)
            + ".make" + struct.name + "();";
        }

        @Override public String createInvoke(final String contextName, final String functionIdentifier) {
            return createReturnValue() + "\n\t\t" + functionIdentifier + ".invoke(" + contextName + ", returnValue);";
        }

        @Override public String createPop(final String contextName){ return ""; }
    }

    public static class JPrimitive extends JType {
        final Type type;
        final ComplexCoderDescriptor coderDescriptor;
        final JType parameterizable;

        public JPrimitive(final Type type, final ComplexCoderDescriptor coderDesc) {
            this.type = type;
            this.coderDescriptor = coderDesc;

            this.parameterizable = new JType() {
                @Override public String getJavaTypeName() { return coderDescriptor.getJavaObjectClass(); }
                @Override public CoderDescriptor getCoderDescriptor() { throw new RuntimeException(); }
            };
        }

        @Override public String getJavaTypeName() { return coderDescriptor.getName(); }
        @Override public String getDefaultReturnValue() { return coderDescriptor.getDefaultReturnValue(); }
        @Override public JType getParameterizableType() { return parameterizable; }
        @Override public CoderDescriptor getCoderDescriptor() { return coderDescriptor; }

        /**
         * Return the suffix placed on java literals to indicate the type. If none applies, return ' '.
         */
        public char getLiteralSuffix() {
            char t = ((NPrimitive)type.type64).type;
            switch(t){
                case 'l': case 'L': case 'f': case 'd': return t;
                case 'q': case 'Q': return 'L';
            }
            return ' ';
        }
    }

}
