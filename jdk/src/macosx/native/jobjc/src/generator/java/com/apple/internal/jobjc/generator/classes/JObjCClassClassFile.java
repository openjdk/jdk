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
import java.util.HashSet;
import java.util.Set;

import com.apple.internal.jobjc.generator.ClassGenerator;
import com.apple.internal.jobjc.generator.FunctionGenerator;
import com.apple.internal.jobjc.generator.model.Clazz;
import com.apple.internal.jobjc.generator.model.Method;
import com.apple.jobjc.JObjCRuntime;
import com.apple.jobjc.Invoke.MsgSend;

public class JObjCClassClassFile extends AbstractObjCClassFile {
    public JObjCClassClassFile(final Clazz clazz) {
        super(clazz, clazz.name + "Class", clazz.superClass != null ? clazz.superClass.getFullPath() + "Class" : ClassGenerator.JOBJC_PACKAGE + ".NSClass<" + clazz.name + ">");
    }

    @Override public void writeBeginning(final PrintStream out) {
        out.format(
                "\tpublic %1$s(%2$s runtime) {\n" +
                "\t\tsuper(runtime);\n" +
                "\t}\n",
                className, JObjCRuntime.class.getCanonicalName());
        out.format(
                "\tpublic %1$s(String name, %2$s runtime) {\n" +
                "\t\tsuper(name, runtime);\n" +
                "\t}\n",
                className, JObjCRuntime.class.getCanonicalName());
        out.format(
                "\tpublic %1$s(long ptr, %2$s runtime) {\n" +
                "\t\tsuper(ptr, runtime);\n" +
                "\t}\n",
                className, JObjCRuntime.class.getCanonicalName());
    }

    @Override public void writeBody(final PrintStream out) {
        Set<String> written = new HashSet<String>();
        for(final Method method : this.clazz.classMethods)
            if(written.add(method.name))
                FunctionGenerator.writeOutFunction(out, MsgSend.class, method, "this");
            else
                System.out.format("Duplicate method: %1$s %2$s -%3$s\n", clazz.parent.name, className, method.name);
    }
}
