/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8080842
 * @summary Ensure Scope impl can cope with remove() when a field and method share the name.
 * @modules jdk.compiler/com.sun.tools.javac.code
 *          jdk.compiler/com.sun.tools.javac.file
 *          jdk.compiler/com.sun.tools.javac.util
 */

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.file.JavacFileManager;

public class RemoveSymbolUnitTest {

    Context context;
    Names names;
    Symtab symtab;

    public static void main(String... args) throws Exception {
        new RemoveSymbolUnitTest().run();
    }

    public void run() {
        context = new Context();
        JavacFileManager.preRegister(context); // required by ClassReader which is required by Symtab
        names = Names.instance(context);
        symtab = Symtab.instance(context);

        Name hasNext =  names.fromString("hasNext");
        ClassSymbol clazz = new ClassSymbol(0,
                                            names.fromString("X"),
                                            Type.noType,
                                            symtab.unnamedModule.unnamedPackage);

        VarSymbol v = new VarSymbol(0, hasNext, Type.noType, clazz);
        MethodSymbol m = new MethodSymbol(0, hasNext, Type.noType, clazz);

        // Try enter and remove in different shuffled combinations.
        // working with fresh scope each time.
        WriteableScope cs = WriteableScope.create(clazz);
        cs.enter(v);
        cs.enter(m);
        cs.remove(v);
        Symbol s = cs.findFirst(hasNext);
        if (s != m)
            throw new AssertionError("Wrong symbol");

        cs = WriteableScope.create(clazz);
        cs.enter(m);
        cs.enter(v);
        cs.remove(v);
        s = cs.findFirst(hasNext);
        if (s != m)
            throw new AssertionError("Wrong symbol");

        cs = WriteableScope.create(clazz);
        cs.enter(v);
        cs.enter(m);
        cs.remove(m);
        s = cs.findFirst(hasNext);
        if (s != v)
            throw new AssertionError("Wrong symbol");

        cs = WriteableScope.create(clazz);
        cs.enter(m);
        cs.enter(v);
        cs.remove(m);
        s = cs.findFirst(hasNext);
        if (s != v)
            throw new AssertionError("Wrong symbol");
    }
}
