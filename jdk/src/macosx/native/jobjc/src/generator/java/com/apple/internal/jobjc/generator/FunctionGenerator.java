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
package com.apple.internal.jobjc.generator;

import java.io.PrintStream;
import java.io.StringWriter;

import com.apple.internal.jobjc.generator.model.Arg;
import com.apple.internal.jobjc.generator.model.Function;
import com.apple.internal.jobjc.generator.model.Method;
import com.apple.internal.jobjc.generator.model.types.JType;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLCall;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLField;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLMethod;
import com.apple.internal.jobjc.generator.utils.JavaLang.JLTertiary;
import com.apple.jobjc.Coder;
import com.apple.jobjc.Invoke;
import com.apple.jobjc.Invoke.FunCall;
import com.apple.jobjc.Invoke.MsgSend;

public class FunctionGenerator {
    private final static String VARARGS_NAME = "varargs";

    private static String createFieldCache(final Class<? extends Invoke> type, final Function fxn) {
        final String identifier = makeInstanceName(fxn);

        JLField field = new JLField("private static", type.getCanonicalName(), identifier);
        // It's okay to make it static, because the getter isn't static, so the only way to access it is through an instance.
        JLMethod getter = new JLMethod("private final", type.getCanonicalName(), "get_" + identifier);

        JLCall createIt = new JLCall("new " + type.getCanonicalName());
        createIt.args.add(firstArg(fxn));
        createIt.args.add("\"" + fxn.name + "\"");
        createIt.args.add(fxn.returnValue.type.getJType().getCoderDescriptor().getCoderInstanceName());
        for (final Arg arg : fxn.args)
            createIt.args.add(arg.type.getJType().getCoderDescriptor().getCoderInstanceName());

        getter.body.add("return " + new JLTertiary(identifier + " != null", identifier, identifier + " = " + createIt) + ";");

        return field.toString() + getter.toString();
    }

    private static String createLocalForward(final Class<? extends Invoke> type, final Function fxn) {
        final String identifier = makeInstanceName(fxn);
        return new JLField("final", type.getCanonicalName(), identifier, new JLCall("get_" + identifier)).toString();
    }

    private static String createLocalNew(final Class<? extends Invoke> type, final Function fxn) {
        final String identifier = makeInstanceName(fxn);
        StringWriter out = new StringWriter();

        out.append(String.format("%3$s[] argCoders = new %3$s[%1$d + %2$s.length];\n", fxn.args.size(), VARARGS_NAME, Coder.class.getCanonicalName()));

        for(int i = 0; i < fxn.args.size(); i++)
            out.append(String.format("argCoders[%1$d] = %2$s;\n", i, fxn.args.get(0).type.getJType().getCoderDescriptor().getCoderInstanceName()));

        if(fxn.variadic){
            out.append(String.format("for(int i = %1$d; i < (%1$d + %2$s.length); i++)\n", fxn.args.size(), VARARGS_NAME));
            out.append(String.format("\targCoders[i] = %1$s.getCoderAtRuntime(%2$s[i - %3$s]);\n", Coder.class.getCanonicalName(), VARARGS_NAME, fxn.args.size()));
        }

        out.append("final " + type.getCanonicalName() + " " + identifier + " = new " + type.getCanonicalName() + "(" + firstArg(fxn) + ", \"" + fxn.name + "\", "
                + fxn.returnValue.type.getJType().getCoderDescriptor().getCoderInstanceName() + ", argCoders);");

        return out.toString();
    }

    private static final String CONTEXT_NAME = "nativeBuffer";

    public static void writeOutFunction(final PrintStream out, final Class<? extends Invoke> type, final Function fxn, final String initWithObj) {
        final String instName = makeInstanceName(fxn);
        final JType returnJavaType = fxn.returnValue.type.getJType();

        if(!fxn.variadic){
            out.print(createFieldCache(type, fxn));
            out.println();
        }

        JLMethod meth = new JLMethod("public", returnJavaType.getJavaReturnTypeName(), fxn.getJavaName());

        for(Arg arg : fxn.args)
            meth.args.add("final " + arg.type.getJType().getTypeNameAsParam() + " " + arg.javaName);

        if(fxn.variadic)
            meth.args.add("final Object... " + VARARGS_NAME);

        if(fxn instanceof Method && ((Method)fxn).ignore){
            String suggestion = ((Method)fxn).suggestion == null ? "" : (" Suggested work-around: " + ((Method)fxn).suggestion);
            meth.jdoc.add("@deprecated The framework recommends that this method be ignored. (It may be deprecated.)" + suggestion);
            meth.attrs.add("@Deprecated");
        }

        // type mismatch warning
        {
            {
                String retMsg = fxn.returnValue.type.getJType().getCoderDescriptor().mismatchMessage();
                if(retMsg != null){
                    meth.jdoc.add("@deprecated Possible type mismatch: (return value) " + retMsg);
                    meth.attrs.add("@Deprecated");
                }
            }

            for(int i = 0; i < fxn.args.size(); i++){
                final Arg arg = fxn.args.get(i);
                String argMsg = arg.type.getJType().getCoderDescriptor().mismatchMessage();
                if(argMsg != null){
                    meth.jdoc.add("@deprecated Possible type mismatch: (arg" + i + ": " + arg.javaName + ") " + argMsg);
                    meth.attrs.add("@Deprecated");
                }
            }
        }

        if(fxn.variadic)
            meth.body.add(createLocalNew(coreType(fxn), fxn));
        else
            meth.body.add(createLocalForward(coreType(fxn), fxn));

        meth.body.add(returnJavaType.createDeclareBuffer(CONTEXT_NAME));
        meth.body.add(returnJavaType.createInit(CONTEXT_NAME, instName, initWithObj));

        for(final Arg arg : fxn.args)
            meth.body.add(arg.type.getJType().getCoderDescriptor().getPushStatementFor(CONTEXT_NAME, arg.javaName));

        if(fxn.variadic){
            meth.body.add(String.format("for(int i = %1$d; i < (%1$d + %2$s.length); i++)", fxn.args.size(), VARARGS_NAME));
            meth.body.add(String.format("\targCoders[i].push(%1$s, %2$s[i - %3$d]);", CONTEXT_NAME, VARARGS_NAME, fxn.args.size()));
        }

        meth.body.add(returnJavaType.createInvoke(CONTEXT_NAME, instName));
        meth.body.add(returnJavaType.createPop(CONTEXT_NAME));
        meth.body.add(returnJavaType.createReturn());

        out.print(meth.toString());
        out.println();
    }

    private static Class<? extends Invoke> coreType(final Function fxn){
        return fxn instanceof Method ? MsgSend.class : FunCall.class;
    }

    private static String firstArg(Function fxn){
        return fxn instanceof Method ? "getRuntime()" : "this";
    }

    private static String makeInstanceName(Function fxn){
        String ext;
        if(fxn instanceof Method){
            if(((Method) fxn).isClassMethod) ext = "CMetInst";
            else                             ext = "IMetInst";
        }
        else
            ext = "FxnInst";

        return fxn.getJavaName() + "_" + ext;
    }
}
