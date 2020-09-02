/*
 * Copyright (c) 1999, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.lang.model.element.Modifier;

import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.StringUtils;

/** Access flags and other modifiers for Java classes and members.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Flags {

    private Flags() {} // uninstantiable

    public static String toString(long flags) {
        StringBuilder buf = new StringBuilder();
        String sep = "";
        for (Flag flag : asFlagSet(flags)) {
            buf.append(sep);
            buf.append(flag);
            sep = " ";
        }
        return buf.toString();
    }

    public static EnumSet<Flag> asFlagSet(long flags) {
        EnumSet<Flag> flagSet = EnumSet.noneOf(Flag.class);
        for (Flag flag : Flag.values()) {
            if ((flags & flag.value) != 0) {
                flagSet.add(flag);
                flags &= ~flag.value;
            }
        }
        Assert.check((flags & (LAST_FLAT_BIT * 2 - 1)) == 0);
        return flagSet;
    }

    /* Standard Java flags.
     */
    public static final int PUBLIC       = 1;
    public static final int PRIVATE      = 1<<1;
    public static final int PROTECTED    = 1<<2;
    public static final int STATIC       = 1<<3;
    public static final int FINAL        = 1<<4;
    public static final int SYNCHRONIZED = 1<<5;
    public static final int VOLATILE     = 1<<6;
    public static final int TRANSIENT    = 1<<7;
    public static final int NATIVE       = 1<<8;
    public static final int INTERFACE    = 1<<9;
    public static final int ABSTRACT     = 1<<10;
    public static final int STRICTFP     = 1<<11;

    /* Flag that marks a symbol synthetic, added in classfile v49.0. */
    public static final int SYNTHETIC    = 1<<12;

    /** Flag that marks attribute interfaces, added in classfile v49.0. */
    public static final int ANNOTATION   = 1<<13;

    /** An enumeration type or an enumeration constant, added in
     *  classfile v49.0. */
    public static final int ENUM         = 1<<14;

    /** Added in SE8, represents constructs implicitly declared in source. */
    public static final int MANDATED     = 1<<15;

    public static final int StandardFlags = 0x0fff;

    // Because the following access flags are overloaded with other
    // bit positions, we translate them when reading and writing class
    // files into unique bits positions: ACC_SYNTHETIC <-> SYNTHETIC,
    // for example.
    public static final int ACC_SUPER    = 0x0020;
    public static final int ACC_BRIDGE   = 0x0040;
    public static final int ACC_VARARGS  = 0x0080;
    public static final int ACC_MODULE   = 0x8000;

    /*****************************************
     * Internal compiler flags (no bits in the lower 16).
     *****************************************/

    //on all Symbols:
    /** Flag is set if symbol is deprecated.  See also DEPRECATED_REMOVAL.
     */
    public static final int DEPRECATED   = 1<<17;

    /**
     * Flag to indicate the given symbol has a @Deprecated annotation.
     */
    public static final long DEPRECATED_ANNOTATION = 1L<<18;

    /**
     * Flag to indicate the given symbol has been deprecated and marked for removal.
     */
    public static final long DEPRECATED_REMOVAL = 1L<<19;

    /**
     * Flag to indicate the API element in question is for a preview API.
     */
    public static final long PREVIEW_API = 1L<<20; //any Symbol kind

    /**
     * Flag to indicate the API element in question is for a preview API.
     */
    public static final long PREVIEW_ESSENTIAL_API = 1L<<21; //any Symbol kind

    //classfile flags:
    /** Flag that marks bridge methods.
     */
    public static final long BRIDGE          = 1L<<22;

    /** Flag that marks formal parameters.
     */
    public static final long PARAMETER   = 1L<<23;

    /** Flag that marks varargs methods.
     */
    public static final long VARARGS   = 1L<<24;

    /**
     * Flag that marks either a default method or an interface containing default methods.
     */
    public static final long DEFAULT = 1L<<25;

    /**
     * Flag to indicate class symbol is for module-info
     */
    public static final long MODULE = 1L<<26;

    //used on trees:
    /** Flag for synthesized default constructors of anonymous classes.
     */
    public static final int ANONCONSTR   = 1<<27;

    /**
     * Flag for synthesized default constructors of anonymous classes that have an enclosing expression.
     */
    public static final long ANONCONSTR_BASED = 1L<<28;

    /** Flag that marks a generated default constructor.
     */
    public static final long GENERATEDCONSTR   = 1L<<29;

    /**
     * Flag to mark a record constructor as a compact one
     */
    public static final long COMPACT_RECORD_CONSTRUCTOR = 1L<<30; // MethodSymbols only

    /**
     * Flag that marks finalize block as body-only, should not be copied into catch clauses.
     * Used to implement try-with-resources.
     */
    public static final long BODY_ONLY_FINALIZE = 1L<<31;
    
    //other:
    /**
     * Flag to indicate that a class is a record. The flag is also used to mark fields that are
     * part of the state vector of a record and to mark the canonical constructor
     */
    public static final long RECORD = 1L<<32; // ClassSymbols, MethodSymbols and VarSymbols

    /** Flag is set for compiler-generated record members, it could be applied to
     *  accessors and fields
     */
    public static final long GENERATED_MEMBER = 1L<<33; // MethodSymbols and VarSymbols

    /**
     * Flag that marks non-override equivalent methods with the same signature,
     * or a conflicting match binding (BindingSymbol).
     */
    public static final long CLASH = 1L<<34;

    /** Flag is set for compiler-generated anonymous method symbols
     *  that `own' an initializer block.
     */
    public static final long BLOCK            = 1L<<35;

    /** Flag is set for a variable symbol if the variable's definition
     *  has an initializer part.
     */
    public static final long HASINIT          = 1L<<36;

    /**
     * Flag that indicates that an inference variable is used in a 'throws' clause.
     */
    public static final long THROWS = 1L<<37;

    /**
     * Flag to indicate sealed class/interface declaration.
     */
    public static final long SEALED = 1L<<38; // ClassSymbols

    /**
     * Flag to indicate that the class/interface was declared with the non-sealed modifier.
     */
    public static final long NON_SEALED = 1L<<39; // ClassSymbols

    /**
     * The last bit used by the "flat" flags above. Should be updated when a new
     * flag is added.
     */
    public static final long LAST_FLAT_BIT = NON_SEALED;

    /** Modifier masks.
     */
    public static final int
        AccessFlags           = PUBLIC | PROTECTED | PRIVATE,
        LocalClassFlags       = FINAL | ABSTRACT | STRICTFP | ENUM | SYNTHETIC,
        StaticLocalFlags      = LocalClassFlags | STATIC | INTERFACE,
        MemberClassFlags      = LocalClassFlags | INTERFACE | AccessFlags,
        MemberRecordFlags     = MemberClassFlags | STATIC,
        ClassFlags            = LocalClassFlags | INTERFACE | PUBLIC | ANNOTATION,
        InterfaceVarFlags     = FINAL | STATIC | PUBLIC,
        VarFlags              = AccessFlags | FINAL | STATIC |
                                VOLATILE | TRANSIENT | ENUM,
        ConstructorFlags      = AccessFlags,
        InterfaceMethodFlags  = ABSTRACT | PUBLIC,
        MethodFlags           = AccessFlags | ABSTRACT | STATIC | NATIVE |
                                SYNCHRONIZED | FINAL | STRICTFP,
        RecordMethodFlags     = AccessFlags | ABSTRACT | STATIC |
                                SYNCHRONIZED | FINAL | STRICTFP;
    public static final long
        ExtendedStandardFlags       = (long)StandardFlags | DEFAULT | SEALED | NON_SEALED,
        ExtendedMemberClassFlags    = (long)MemberClassFlags | SEALED | NON_SEALED,
        ExtendedClassFlags          = (long)ClassFlags | SEALED | NON_SEALED,
        ModifierFlags               = ((long)StandardFlags & ~INTERFACE) | DEFAULT | SEALED | NON_SEALED,
        InterfaceMethodMask         = ABSTRACT | PRIVATE | STATIC | PUBLIC | STRICTFP | DEFAULT,
        AnnotationTypeElementMask   = ABSTRACT | PUBLIC,
        LocalVarFlags               = FINAL | PARAMETER,
        ReceiverParamFlags          = PARAMETER;

    @SuppressWarnings("preview")
    public static Set<Modifier> asModifierSet(long flags) {
        Set<Modifier> modifiers = modifierSets.get(flags);
        if (modifiers == null) {
            modifiers = java.util.EnumSet.noneOf(Modifier.class);
            if (0 != (flags & PUBLIC))    modifiers.add(Modifier.PUBLIC);
            if (0 != (flags & PROTECTED)) modifiers.add(Modifier.PROTECTED);
            if (0 != (flags & PRIVATE))   modifiers.add(Modifier.PRIVATE);
            if (0 != (flags & ABSTRACT))  modifiers.add(Modifier.ABSTRACT);
            if (0 != (flags & STATIC))    modifiers.add(Modifier.STATIC);
            if (0 != (flags & SEALED))    modifiers.add(Modifier.SEALED);
            if (0 != (flags & NON_SEALED))
                                          modifiers.add(Modifier.NON_SEALED);
            if (0 != (flags & FINAL))     modifiers.add(Modifier.FINAL);
            if (0 != (flags & TRANSIENT)) modifiers.add(Modifier.TRANSIENT);
            if (0 != (flags & VOLATILE))  modifiers.add(Modifier.VOLATILE);
            if (0 != (flags & SYNCHRONIZED))
                                          modifiers.add(Modifier.SYNCHRONIZED);
            if (0 != (flags & NATIVE))    modifiers.add(Modifier.NATIVE);
            if (0 != (flags & STRICTFP))  modifiers.add(Modifier.STRICTFP);
            if (0 != (flags & DEFAULT))   modifiers.add(Modifier.DEFAULT);
            modifiers = Collections.unmodifiableSet(modifiers);
            modifierSets.put(flags, modifiers);
        }
        return modifiers;
    }

    // Cache of modifier sets.
    private static final Map<Long, Set<Modifier>> modifierSets = new ConcurrentHashMap<>(64);

    public static boolean isStatic(Symbol symbol) {
        return (symbol.flags() & STATIC) != 0;
    }

    public static boolean isEnum(Symbol symbol) {
        return (symbol.flags() & ENUM) != 0;
    }

    public static boolean isConstant(Symbol.VarSymbol symbol) {
        return symbol.getConstValue() != null;
    }

    public enum TypeSymbolFlags {
        //PackageSymbols:
        /** Flag is set for package symbols if a package has a member or
         *  directory and therefore exists.
         */
        EXISTS,

        /**
         * Flag to indicate the given PackageSymbol contains any non-.java and non-.class resources.
         */
        HAS_RESOURCE,

        //ClassSymbols:
        /** Flag is set for nested classes that do not access instance members
         *  or `this' of an outer class and therefore don't need to be passed
         *  a this$n reference.  This value is currently set only for anonymous
         *  classes in superclass constructor calls.
         *  todo: use this value for optimizing away this$n parameters in
         *  other cases.
         */
        NOOUTERTHIS,

        /** Flag is set for compiler-generated compound classes
         *  representing multiple variable bounds
         */
        COMPOUND,

        /** Flag is set for class symbols if a class file was found for this class.
         */
        CLASS_SEEN,

        /** Flag is set for class symbols if a source file was found for this
         *  class.
         */
        SOURCE_SEEN,

        /** Flag for class symbols is set and later re-set as a lock in
         *  Enter to detect cycles in the superclass/superinterface
         *  relations.  Similarly for constructor call cycle detection in
         *  Attr.
         */
        LOCKED,

        /** Flag for class symbols is set and later re-set to indicate that a class
         *  has been entered but has not yet been attributed.
         */
        UNATTRIBUTED,

        /** Flag for class symbols to indicate it has been checked and found
         *  acyclic.
         */
        ACYCLIC,

        /** Flag for annotation type symbols to indicate it has been
         *  checked and found acyclic.
         */
        ACYCLIC_ANN,

        /**
         * Flag that marks an internal proprietary class.
         */
        PROPRIETARY,

        /**
         * Flag that marks class as auxiliary, ie a non-public class following
         * the public class in a source file, that could block implicit compilation.
         */
        AUXILIARY,

        /**
         * Flag to control recursion in TransTypes
         */
        TYPE_TRANSLATED,

        /**
         * Flag that marks that a symbol is not available in the current profile
         */
        NOT_IN_PROFILE,

        //ModuleSymbols:
        /**
         * Flag to indicate the given ModuleSymbol is an automatic module.
         */
        AUTOMATIC_MODULE,

        /**
         * Flag to indicate the given ModuleSymbol is a system module.
         */
        SYSTEM_MODULE,
        ;

        final long mask;
        private TypeSymbolFlags() {
            this.mask = (1L << ordinal()) * LAST_FLAT_BIT * 2;
            Assert.check(this.mask > 0);
        }
        
    }

    public enum MethodSymbolFlags {
        /** Flag that marks a hypothetical method that need not really be
         *  generated in the binary, but is present in the symbol table to
         *  simplify checking for erasure clashes - also used for 292 poly sig methods.
         */
        HYPOTHETICAL,

        /**
         * Flag that indicates that an override error has been detected by Check.
         */
        BAD_OVERRIDE,

        /**
         * Flag that indicates a signature polymorphic method (292).
         */
        SIGNATURE_POLYMORPHIC,

        /**
         * Flag that marks potentially ambiguous overloads
         */
        POTENTIALLY_AMBIGUOUS,

        /**
         * Flag that marks a synthetic method body for a lambda expression
         */
        LAMBDA_METHOD,

        /** Flag for class symbols to indicate it has been checked and found
         *  acyclic.
         */
        ACYCLIC_CONSTRUCTOR,

        /** Flag for class symbols is set and later re-set as a lock in
         *  Enter to detect cycles in the superclass/superinterface
         *  relations.  Similarly for constructor call cycle detection in
         *  Attr.
         */
        LOCKED_CONSTRUCTOR,
        ;

        final long mask;
        private MethodSymbolFlags() {
            this.mask = (1L << ordinal()) * LAST_FLAT_BIT * 2;
            Assert.check(this.mask > 0);
        }
    }

    public enum VarSymbolFlags {
        /**
         * Flag that marks an 'effectively final' local variable.
         */
        EFFECTIVELY_FINAL,

        /**
         * Flag to indicate the given ParamSymbol has a user-friendly name filled.
         */
        NAME_FILLED,

        /**
         * Flag to indicate the given variable is a match binding variable.
         */
        MATCH_BINDING,

        /**
         * A flag to indicate a match binding variable whose scope extends after the current statement.
         */
        MATCH_BINDING_TO_OUTER,

        /**
         * Flag to mark a record field that was not initialized in the compact constructor
         */
        UNINITIALIZED_FIELD,

        /**
         * Flag that marks a multi-catch parameter.
         */
        UNION,
        ;

        final long mask;
        private VarSymbolFlags() {
            this.mask = (1L << ordinal()) * LAST_FLAT_BIT * 2;
            Assert.check(this.mask > 0);
        }
    }

    public enum Flag {
        PUBLIC(Flags.PUBLIC),
        PRIVATE(Flags.PRIVATE),
        PROTECTED(Flags.PROTECTED),
        STATIC(Flags.STATIC),
        FINAL(Flags.FINAL),
        SYNCHRONIZED(Flags.SYNCHRONIZED),
        VOLATILE(Flags.VOLATILE),
        TRANSIENT(Flags.TRANSIENT),
        NATIVE(Flags.NATIVE),
        INTERFACE(Flags.INTERFACE),
        ABSTRACT(Flags.ABSTRACT),
        DEFAULT(Flags.DEFAULT),
        STRICTFP(Flags.STRICTFP),
        SYNTHETIC(Flags.SYNTHETIC),
        ANNOTATION(Flags.ANNOTATION),
        DEPRECATED(Flags.DEPRECATED),
        ENUM(Flags.ENUM),
        MANDATED(Flags.MANDATED),
        DEPRECATED_ANNOTATION(Flags.DEPRECATED_ANNOTATION),
        DEPRECATED_REMOVAL(Flags.DEPRECATED_REMOVAL),
        PREVIEW_API(Flags.PREVIEW_API),
        PREVIEW_ESSENTIAL_API(Flags.PREVIEW_ESSENTIAL_API),
        BRIDGE(Flags.BRIDGE),
        PARAMETER(Flags.PARAMETER),
        VARARGS(Flags.VARARGS),
        MODULE(Flags.MODULE),
        ANONCONSTR(Flags.ANONCONSTR),
        ANONCONSTR_BASED(Flags.ANONCONSTR_BASED),
        GENERATEDCONSTR(Flags.GENERATEDCONSTR),
        COMPACT_RECORD_CONSTRUCTOR(Flags.COMPACT_RECORD_CONSTRUCTOR),
        BODY_ONLY_FINALIZE(Flags.BODY_ONLY_FINALIZE),
        RECORD(Flags.RECORD),
        GENERATED_MEMBER(Flags.GENERATED_MEMBER),
        CLASH(Flags.CLASH),
        BLOCK(Flags.BLOCK),
        HASINIT(Flags.HASINIT),
        THROWS(Flags.THROWS),
        SEALED(Flags.SEALED),
        NON_SEALED(Flags.NON_SEALED) {
            @Override
            public String toString() {
                return "non-sealed";
            }
        };

        Flag(long flag) {
            this.value = flag;
            this.lowercaseName = StringUtils.toLowerCase(name());
        }

        @Override
        public String toString() {
            return lowercaseName;
        }

        final long value;
        final String lowercaseName;
    }

}
