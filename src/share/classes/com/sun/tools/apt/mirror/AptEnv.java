/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.apt.mirror;


import com.sun.tools.apt.mirror.declaration.DeclarationMaker;
import com.sun.tools.apt.mirror.type.TypeMaker;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;


/**
 * The environment for a run of apt.
 */
@SuppressWarnings("deprecation")
public class AptEnv {

    public Names names;                 // javac's name table
    public Symtab symtab;               // javac's predefined symbols
    public Types jctypes;               // javac's type utilities
    public Enter enter;                 // javac's enter phase
    public Attr attr;                   // javac's attr phase (to evaluate
                                        //   constant initializers)
    public TypeMaker typeMaker;         // apt's internal type utilities
    public DeclarationMaker declMaker;  // apt's internal declaration utilities


    private static final Context.Key<AptEnv> aptEnvKey =
            new Context.Key<AptEnv>();

    public static AptEnv instance(Context context) {
        AptEnv instance = context.get(aptEnvKey);
        if (instance == null) {
            instance = new AptEnv(context);
        }
        return instance;
    }

    private AptEnv(Context context) {
        context.put(aptEnvKey, this);

        names = Names.instance(context);
        symtab = Symtab.instance(context);
        jctypes = Types.instance(context);
        enter = Enter.instance(context);
        attr = Attr.instance(context);
        typeMaker = TypeMaker.instance(context);
        declMaker = DeclarationMaker.instance(context);
    }


    /**
     * Does a symbol have a given flag?  Forces symbol completion.
     */
    public static boolean hasFlag(Symbol sym, long flag) {
        return (getFlags(sym) & flag) != 0;
    }

    /**
     * Returns a symbol's flags.  Forces completion.
     */
    public static long getFlags(Symbol sym) {
        complete(sym);
        return sym.flags();
    }

    /**
     * Completes a symbol, ignoring completion failures.
     */
    private static void complete(Symbol sym) {
        while (true) {
            try {
                sym.complete();
                return;
            } catch (CompletionFailure e) {
                // Should never see two in a row, but loop just to be sure.
            }
        }
    }
}
