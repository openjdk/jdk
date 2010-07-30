/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import java.util.EnumSet;
import java.util.Locale;

import com.sun.tools.javac.api.Formattable;
import com.sun.tools.javac.api.Messages;

import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.code.Flags.*;

/** Internal symbol kinds, which distinguish between elements of
 *  different subclasses of Symbol. Symbol kinds are organized so they can be
 *  or'ed to sets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Kinds {

    private Kinds() {} // uninstantiable

    /** The empty set of kinds.
     */
    public final static int NIL = 0;

    /** The kind of package symbols.
     */
    public final static int PCK = 1 << 0;

    /** The kind of type symbols (classes, interfaces and type variables).
     */
    public final static int TYP = 1 << 1;

    /** The kind of variable symbols.
     */
    public final static int VAR = 1 << 2;

    /** The kind of values (variables or non-variable expressions), includes VAR.
     */
    public final static int VAL = (1 << 3) | VAR;

    /** The kind of methods.
     */
    public final static int MTH = 1 << 4;

    /** The error kind, which includes all other kinds.
     */
    public final static int ERR = (1 << 5) - 1;

    /** The set of all kinds.
     */
    public final static int AllKinds = ERR;

    /** Kinds for erroneous symbols that complement the above
     */
    public static final int ERRONEOUS = 1 << 6;
    public static final int AMBIGUOUS    = ERRONEOUS+1; // ambiguous reference
    public static final int HIDDEN       = ERRONEOUS+2; // hidden method or field
    public static final int STATICERR    = ERRONEOUS+3; // nonstatic member from static context
    public static final int ABSENT_VAR   = ERRONEOUS+4; // missing variable
    public static final int WRONG_MTHS   = ERRONEOUS+5; // methods with wrong arguments
    public static final int WRONG_MTH    = ERRONEOUS+6; // one method with wrong arguments
    public static final int ABSENT_MTH   = ERRONEOUS+7; // missing method
    public static final int ABSENT_TYP   = ERRONEOUS+8; // missing type

    public enum KindName implements Formattable {
        ANNOTATION("kindname.annotation"),
        CONSTRUCTOR("kindname.constructor"),
        INTERFACE("kindname.interface"),
        ENUM("kindname.enum"),
        STATIC("kindname.static"),
        TYPEVAR("kindname.type.variable"),
        BOUND("kindname.type.variable.bound"),
        VAR("kindname.variable"),
        VAL("kindname.value"),
        METHOD("kindname.method"),
        CLASS("kindname.class"),
        PACKAGE("kindname.package");

        private String name;

        KindName(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }

        public String getKind() {
            return "Kindname";
        }

        public String toString(Locale locale, Messages messages) {
            String s = toString();
            return messages.getLocalizedString(locale, "compiler.misc." + s);
        }
    }

    /** A KindName representing a given symbol kind
     */
    public static KindName kindName(int kind) {
        switch (kind) {
        case PCK: return KindName.PACKAGE;
        case TYP: return KindName.CLASS;
        case VAR: return KindName.VAR;
        case VAL: return KindName.VAL;
        case MTH: return KindName.METHOD;
            default : throw new AssertionError("Unexpected kind: "+kind);
        }
    }

    /** A KindName representing a given symbol
     */
    public static KindName kindName(Symbol sym) {
        switch (sym.getKind()) {
        case PACKAGE:
            return KindName.PACKAGE;

        case ENUM:
            return KindName.ENUM;

        case ANNOTATION_TYPE:
        case CLASS:
            return KindName.CLASS;

        case INTERFACE:
            return KindName.INTERFACE;

        case TYPE_PARAMETER:
            return KindName.TYPEVAR;

        case ENUM_CONSTANT:
        case FIELD:
        case PARAMETER:
        case LOCAL_VARIABLE:
        case EXCEPTION_PARAMETER:
            return KindName.VAR;

        case CONSTRUCTOR:
            return KindName.CONSTRUCTOR;

        case METHOD:
        case STATIC_INIT:
        case INSTANCE_INIT:
            return KindName.METHOD;

        default:
            if (sym.kind == VAL)
                // I don't think this can happen but it can't harm
                // playing it safe --ahe
                return KindName.VAL;
            else
                throw new AssertionError("Unexpected kind: "+sym.getKind());
        }
    }

    /** A set of KindName(s) representing a set of symbol's kinds.
     */
    public static EnumSet<KindName> kindNames(int kind) {
        EnumSet<KindName> kinds = EnumSet.noneOf(KindName.class);
        if ((kind & VAL) != 0)
            kinds.add(((kind & VAL) == VAR) ? KindName.VAR : KindName.VAL);
        if ((kind & MTH) != 0) kinds.add(KindName.METHOD);
        if ((kind & TYP) != 0) kinds.add(KindName.CLASS);
        if ((kind & PCK) != 0) kinds.add(KindName.PACKAGE);
        return kinds;
    }

    /** A KindName representing the kind of a given class/interface type.
     */
    public static KindName typeKindName(Type t) {
        if (t.tag == TYPEVAR ||
            t.tag == CLASS && (t.tsym.flags() & COMPOUND) != 0)
            return KindName.BOUND;
        else if (t.tag == PACKAGE)
            return KindName.PACKAGE;
        else if ((t.tsym.flags_field & ANNOTATION) != 0)
            return KindName.ANNOTATION;
        else if ((t.tsym.flags_field & INTERFACE) != 0)
            return KindName.INTERFACE;
        else
            return KindName.CLASS;
    }

    /** A KindName representing the kind of a a missing symbol, given an
     *  error kind.
     * */
    public static KindName absentKind(int kind) {
        switch (kind) {
        case ABSENT_VAR:
            return KindName.VAR;
        case WRONG_MTHS: case WRONG_MTH: case ABSENT_MTH:
            return KindName.METHOD;
        case ABSENT_TYP:
            return KindName.CLASS;
        default:
            throw new AssertionError("Unexpected kind: "+kind);
        }
    }
}
