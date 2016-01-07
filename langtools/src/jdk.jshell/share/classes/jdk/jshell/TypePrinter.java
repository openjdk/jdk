/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import static com.sun.tools.javac.code.Flags.COMPOUND;
import static com.sun.tools.javac.code.Kinds.Kind.PCK;
import com.sun.tools.javac.code.Printer;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.util.JavacMessages;
import java.util.Locale;
import java.util.function.BinaryOperator;

/**
 * Print types in source form.
 */
class TypePrinter extends Printer {
    private static final String OBJECT = "Object";

    private final JavacMessages messages;
    private final BinaryOperator<String> fullClassNameAndPackageToClass;
    private boolean useWildCard = false;

    TypePrinter(JavacMessages messages, BinaryOperator<String> fullClassNameAndPackageToClass, Type typeToPrint) {
        this.messages = messages;
        this.fullClassNameAndPackageToClass = fullClassNameAndPackageToClass;
    }

    @Override
    protected String localize(Locale locale, String key, Object... args) {
        return messages.getLocalizedString(locale, key, args);
    }

    @Override
    protected String capturedVarId(Type.CapturedType t, Locale locale) {
        throw new InternalError("should never call this");
    }

    @Override
    public String visitCapturedType(Type.CapturedType t, Locale locale) {
        return visit(t.wildcard, locale);
    }

    @Override
    public String visitWildcardType(Type.WildcardType wt, Locale locale) {
        if (useWildCard) { // at TypeArgument(ex: List<? extends T>)
            return super.visitWildcardType(wt, locale);
        } else { // at TopLevelType(ex: ? extends List<T>, ? extends Number[][])
            Type extendsBound = wt.getExtendsBound();
            return extendsBound == null
                    ? OBJECT
                    : visit(extendsBound, locale);
        }
    }

    @Override
    public String visitType(Type t, Locale locale) {
        String s = (t.tsym == null || t.tsym.name == null)
                ? OBJECT // none
                : t.tsym.name.toString();
        return s;
    }

    @Override
    public String visitClassType(ClassType ct, Locale locale) {
        boolean prevUseWildCard = useWildCard;
        try {
            useWildCard = true;
            return super.visitClassType(ct, locale);
        } finally {
            useWildCard = prevUseWildCard;
        }
    }

    /**
     * Converts a class name into a (possibly localized) string. Anonymous
     * inner classes get converted into a localized string.
     *
     * @param t the type of the class whose name is to be rendered
     * @param longform if set, the class' fullname is displayed - if unset the
     * short name is chosen (w/o package)
     * @param locale the locale in which the string is to be rendered
     * @return localized string representation
     */
    @Override
    protected String className(ClassType t, boolean longform, Locale locale) {
        Symbol sym = t.tsym;
        if (sym.name.length() == 0 && (sym.flags() & COMPOUND) != 0) {
            /***
            StringBuilder s = new StringBuilder(visit(t.supertype_field, locale));
            for (List<Type> is = t.interfaces_field; is.nonEmpty(); is = is.tail) {
                s.append('&');
                s.append(visit(is.head, locale));
            }
            return s.toString();
            ***/
            return OBJECT;
        } else if (sym.name.length() == 0) {
            // Anonymous
            String s;
            ClassType norm = (ClassType) t.tsym.type;
            if (norm == null) {
                s = "object";
            } else if (norm.interfaces_field != null && norm.interfaces_field.nonEmpty()) {
                s = visit(norm.interfaces_field.head, locale);
            } else {
                s = visit(norm.supertype_field, locale);
            }
            return s;
        } else if (longform) {
            String pkg = "";
            for (Symbol psym = sym; psym != null; psym = psym.owner) {
                if (psym.kind == PCK) {
                    pkg = psym.getQualifiedName().toString();
                    break;
                }
            }
            return fullClassNameAndPackageToClass.apply(
                    sym.getQualifiedName().toString(),
                    pkg
            );
        } else {
            return sym.name.toString();
        }
    }

    @Override
    public String visitClassSymbol(ClassSymbol sym, Locale locale) {
        return sym.name.isEmpty()
                ? sym.flatname.toString() // Anonymous
                : sym.fullname.toString();
    }

    @Override
    public String visitPackageSymbol(PackageSymbol s, Locale locale) {
        return s.isUnnamed()
                ? ""   // Unnamed package
                : s.fullname.toString();
    }

}
