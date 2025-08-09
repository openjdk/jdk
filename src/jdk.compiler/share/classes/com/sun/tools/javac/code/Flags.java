/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import com.sun.tools.javac.util.Assert;

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
        for (FlagsEnum flag : asFlagSet(flags)) {
            buf.append(sep);
            buf.append(flag);
            sep = " ";
        }
        return buf.toString();
    }

    public static EnumSet<FlagsEnum> asFlagSet(long flags) {
        EnumSet<FlagsEnum> flagSet = EnumSet.noneOf(FlagsEnum.class);
        for (FlagsEnum flag : FlagsEnum.values()) {
            if ((flags & flag.value()) != 0) {
                flagSet.add(flag);
                flags &= ~flag.value();
            }
        }
        Assert.check(flags == 0);
        return flagSet;
    }

    /* Standard Java flags.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final int PUBLIC       = 1;
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final int PRIVATE      = 1<<1;
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final int PROTECTED    = 1<<2;
    @Use({FlagTarget.BLOCK, FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final int STATIC       = 1<<3;
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final int FINAL        = 1<<4;
    @Use({FlagTarget.METHOD})
    public static final int SYNCHRONIZED = 1<<5;
    @Use({FlagTarget.VARIABLE})
    public static final int VOLATILE     = 1<<6;
    @Use({FlagTarget.VARIABLE})
    public static final int TRANSIENT    = 1<<7;
    @Use({FlagTarget.METHOD})
    public static final int NATIVE       = 1<<8;
    @Use({FlagTarget.CLASS})
    public static final int INTERFACE    = 1<<9;
    @Use({FlagTarget.CLASS, FlagTarget.METHOD})
    public static final int ABSTRACT     = 1<<10;
    @Use({FlagTarget.CLASS, FlagTarget.METHOD})
    public static final int STRICTFP     = 1<<11;

    /* Flag that marks a symbol synthetic, added in classfile v49.0. */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final int SYNTHETIC    = 1<<12;

    /** Flag that marks attribute interfaces, added in classfile v49.0. */
    @Use({FlagTarget.CLASS})
    public static final int ANNOTATION   = 1<<13;

    /** An enumeration type or an enumeration constant, added in
     *  classfile v49.0. */
    @Use({FlagTarget.CLASS, FlagTarget.VARIABLE})
    public static final int ENUM         = 1<<14;

    /** Added in SE8, represents constructs implicitly declared in source. */
    @Use({FlagTarget.MODULE, FlagTarget.VARIABLE})
    public static final int MANDATED     = 1<<15;

    @NotFlag
    public static final int StandardFlags = 0x0fff;

    // Because the following access flags are overloaded with other
    // bit positions, we translate them when reading and writing class
    // files into unique bits positions: ACC_SYNTHETIC <-> SYNTHETIC,
    // for example.
    @Use({FlagTarget.CLASS})
    @NoToStringValue
    public static final int ACC_SUPER    = 1<<5;
    @Use({FlagTarget.METHOD})
    @NoToStringValue
    public static final int ACC_BRIDGE   = 1<<6;
    @Use({FlagTarget.METHOD})
    @NoToStringValue
    public static final int ACC_VARARGS  = 1<<7;
    @Use({FlagTarget.CLASS})
    @NoToStringValue
    public static final int ACC_MODULE   = 1<<15;

    /* ***************************************
     * Internal compiler flags (no bits in the lower 16).
     *****************************************/

    /** Flag is set if symbol is deprecated.  See also DEPRECATED_REMOVAL.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.MODULE, FlagTarget.PACKAGE, FlagTarget.TYPE_VAR, FlagTarget.VARIABLE})
    public static final int DEPRECATED   = 1<<17;

    /** Flag is set for a variable symbol if the variable's definition
     *  has an initializer part.
     */
    @Use({FlagTarget.VARIABLE})
    public static final int HASINIT          = 1<<18;

    /** Class is an implicitly declared top level class.
     */
    @Use({FlagTarget.CLASS})
    public static final int IMPLICIT_CLASS    = 1<<19;

    /** Flag is set for compiler-generated anonymous method symbols
     *  that `own' an initializer block.
     */
    @Use({FlagTarget.METHOD})
    public static final int BLOCK            = 1<<20;

    /** Flag is set for ClassSymbols that are being compiled from source.
     */
    @Use({FlagTarget.CLASS})
    public static final int FROM_SOURCE      = 1<<21;

    /** Flag is set for nested classes that do not access instance members
     *  or `this' of an outer class and therefore don't need to be passed
     *  a this$n reference.  This value is currently set only for anonymous
     *  classes in superclass constructor calls.
     *  todo: use this value for optimizing away this$n parameters in
     *  other cases.
     */
    @Use({FlagTarget.CLASS, FlagTarget.VARIABLE})
    public static final int NOOUTERTHIS  = 1<<22;

    /** Flag is set for package symbols if a package has a member or
     *  directory and therefore exists.
     */
    @Use({FlagTarget.CLASS, FlagTarget.PACKAGE})
    public static final int EXISTS           = 1<<23;

    /** Flag is set for compiler-generated compound classes
     *  representing multiple variable bounds
     */
    @Use({FlagTarget.CLASS})
    public static final int COMPOUND     = 1<<24;

    /** Flag is set for class symbols if a class file was found for this class.
     */
    @Use({FlagTarget.CLASS})
    public static final int CLASS_SEEN   = 1<<25;

    /** Flag is set for class symbols if a source file was found for this
     *  class.
     */
    @Use({FlagTarget.CLASS})
    public static final int SOURCE_SEEN  = 1<<26;

    /* State flags (are reset during compilation).
     */

    /** Flag for class symbols is set and later re-set as a lock in
     *  Enter to detect cycles in the superclass/superinterface
     *  relations.  Similarly for constructor call cycle detection in
     *  Attr.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD})
    public static final int LOCKED           = 1<<27;

    /** Flag for class symbols is set and later re-set to indicate that a class
     *  has been entered but has not yet been attributed.
     */
    @Use({FlagTarget.CLASS})
    public static final int UNATTRIBUTED = 1<<28;

    /** Flag for synthesized default constructors of anonymous classes.
     */
    @Use({FlagTarget.METHOD})
    public static final int ANONCONSTR   = 1<<29;

    /**
     * Flag to indicate the superclasses of this ClassSymbol has been attributed.
     */
    @Use({FlagTarget.CLASS})
    public static final int SUPER_OWNER_ATTRIBUTED = 1<<29;

    /** Flag for class symbols to indicate it has been checked and found
     *  acyclic.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.TYPE_VAR})
    public static final int ACYCLIC          = 1<<30;

    /** Flag that marks bridge methods.
     */
    @Use({FlagTarget.METHOD})
    public static final long BRIDGE          = 1L<<31;

    /** Flag that marks formal parameters.
     */
    @Use({FlagTarget.VARIABLE})
    public static final long PARAMETER   = 1L<<33;

    /** Flag that marks varargs methods.
     */
    @Use({FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final long VARARGS   = 1L<<34;

    /** Flag for annotation type symbols to indicate it has been
     *  checked and found acyclic.
     */
    @Use({FlagTarget.CLASS})
    public static final long ACYCLIC_ANN      = 1L<<35;

    /** Flag that marks a generated default constructor.
     */
    @Use({FlagTarget.METHOD})
    public static final long GENERATEDCONSTR   = 1L<<36;

    /** Flag that marks a hypothetical method that need not really be
     *  generated in the binary, but is present in the symbol table to
     *  simplify checking for erasure clashes - also used for 292 poly sig methods.
     */
    @Use({FlagTarget.METHOD})
    public static final long HYPOTHETICAL   = 1L<<37;

    /**
     * Flag that marks an internal proprietary class.
     */
    @Use({FlagTarget.CLASS})
    public static final long PROPRIETARY = 1L<<38;

    /**
     * Flag that marks a multi-catch parameter.
     */
    @Use({FlagTarget.VARIABLE})
    public static final long UNION = 1L<<39;

    /**
     * Flags an erroneous TypeSymbol as viable for recovery.
     * TypeSymbols only.
     */
    @Use({FlagTarget.CLASS, FlagTarget.TYPE_VAR})
    public static final long RECOVERABLE = 1L<<40;

    /**
     * Flag that marks an 'effectively final' local variable.
     */
    @Use({FlagTarget.VARIABLE})
    public static final long EFFECTIVELY_FINAL = 1L<<41;

    /**
     * Flag that marks non-override equivalent methods with the same signature,
     * or a conflicting match binding (BindingSymbol).
     */
    @Use({FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final long CLASH = 1L<<42;

    /**
     * Flag that marks either a default method or an interface containing default methods.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD})
    public static final long DEFAULT = 1L<<43; // part of ExtendedStandardFlags, cannot be reused

    /**
     * Flag that marks class as auxiliary, ie a non-public class following
     * the public class in a source file, that could block implicit compilation.
     */
    @Use({FlagTarget.CLASS})
    public static final long AUXILIARY = 1L<<44;

    /**
     * Flag that marks that a symbol is not available in the current profile
     */
    @Use({FlagTarget.CLASS})
    public static final long NOT_IN_PROFILE = 1L<<45;

    /**
     * Flag that indicates that an override error has been detected by Check.
     */
    @Use({FlagTarget.METHOD})
    public static final long BAD_OVERRIDE = 1L<<45;

    /**
     * Flag that indicates a signature polymorphic method (292).
     */
    @Use({FlagTarget.METHOD})
    public static final long SIGNATURE_POLYMORPHIC = 1L<<46;

    /**
     * Flag that indicates that an inference variable is used in a 'throws' clause.
     */
    @Use({FlagTarget.TYPE_VAR})
    public static final long THROWS = 1L<<47;

    /**
     * Flag to indicate sealed class/interface declaration.
     */
    @Use({FlagTarget.CLASS})
    public static final long SEALED = 1L<<48; // part of ExtendedStandardFlags, cannot be reused

    /**
     * Flag that marks a synthetic method body for a lambda expression
     */
    @Use({FlagTarget.METHOD})
    public static final long LAMBDA_METHOD = 1L<<49;

    /**
     * Flag that marks a synthetic local capture field in a local/anon class
     */
    @Use({FlagTarget.VARIABLE})
    public static final long LOCAL_CAPTURE_FIELD = 1L<<49;

    /**
     * Flag to control recursion in TransTypes
     */
    @Use({FlagTarget.CLASS})
    public static final long TYPE_TRANSLATED = 1L<<50;

    /**
     * Flag to indicate class symbol is for module-info
     */
    @Use({FlagTarget.CLASS})
    public static final long MODULE = 1L<<51;

    /**
     * Flag to indicate the given ModuleSymbol is an automatic module.
     */
    @Use({FlagTarget.MODULE})
    public static final long AUTOMATIC_MODULE = 1L<<52;

    /**
     * Flag to indicate the given PackageSymbol contains any non-.java and non-.class resources.
     */
    @Use({FlagTarget.PACKAGE})
    public static final long HAS_RESOURCE = 1L<<52;

    /**
     * Flag to indicate the given ParamSymbol has a user-friendly name filled.
     */
    @Use({FlagTarget.VARIABLE}) //ParamSymbols only
    public static final long NAME_FILLED = 1L<<52;

    /**
     * Flag to indicate the given ModuleSymbol is a system module.
     */
    @Use({FlagTarget.MODULE})
    public static final long SYSTEM_MODULE = 1L<<53;

    /**
     * Flag to indicate the given ClassSymbol is a value based.
     */
    @Use({FlagTarget.CLASS})
    public static final long VALUE_BASED = 1L<<53;

    /**
     * Flag to indicate the given symbol has a @Deprecated annotation.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.MODULE, FlagTarget.PACKAGE, FlagTarget.TYPE_VAR, FlagTarget.VARIABLE})
    public static final long DEPRECATED_ANNOTATION = 1L<<54;

    /**
     * Flag to indicate the given symbol has been deprecated and marked for removal.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.MODULE, FlagTarget.PACKAGE, FlagTarget.TYPE_VAR, FlagTarget.VARIABLE})
    public static final long DEPRECATED_REMOVAL = 1L<<55;

    /**
     * Flag to indicate the API element in question is for a preview API.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.MODULE, FlagTarget.PACKAGE, FlagTarget.TYPE_VAR, FlagTarget.VARIABLE})
    public static final long PREVIEW_API = 1L<<56; //any Symbol kind

    /**
     * Flag for synthesized default constructors of anonymous classes that have an enclosing expression.
     */
    @Use({FlagTarget.METHOD})
    public static final long ANONCONSTR_BASED = 1L<<57;

    /**
     * Flag that marks finalize block as body-only, should not be copied into catch clauses.
     * Used to implement try-with-resources.
     */
    @Use({FlagTarget.BLOCK})
    public static final long BODY_ONLY_FINALIZE = 1L<<17;

    /**
     * Flag to indicate the API element in question is for a preview API.
     */
    @Use({FlagTarget.CLASS, FlagTarget.METHOD, FlagTarget.MODULE, FlagTarget.PACKAGE, FlagTarget.TYPE_VAR, FlagTarget.VARIABLE})
    public static final long PREVIEW_REFLECTIVE = 1L<<58; //any Symbol kind

    /**
     * Flag to indicate the given variable is a match binding variable.
     */
    @Use({FlagTarget.VARIABLE})
    public static final long MATCH_BINDING = 1L<<59;

    /**
     * A flag to indicate a match binding variable whose scope extends after the current statement.
     */
    @Use({FlagTarget.VARIABLE})
    public static final long MATCH_BINDING_TO_OUTER = 1L<<60;

    /**
     * Flag to indicate that a class is a record. The flag is also used to mark fields that are
     * part of the state vector of a record and to mark the canonical constructor
     */
    @Use({FlagTarget.CLASS, FlagTarget.VARIABLE, FlagTarget.METHOD})
    public static final long RECORD = 1L<<61;

    /**
     * Flag to mark a record constructor as a compact one
     */
    @Use({FlagTarget.METHOD})
    public static final long COMPACT_RECORD_CONSTRUCTOR = 1L<<51;

    /**
     * Flag to mark a record field that was not initialized in the compact constructor
     */
    @Use({FlagTarget.VARIABLE})
    public static final long UNINITIALIZED_FIELD= 1L<<51;

    /** Flag is set for compiler-generated record members, it could be applied to
     *  accessors and fields
     */
    @Use({FlagTarget.METHOD, FlagTarget.VARIABLE})
    public static final int GENERATED_MEMBER = 1<<24;

    /**
     * Flag to indicate restricted method declaration.
     */
    @Use({FlagTarget.METHOD})
    public static final long RESTRICTED = 1L<<62;

    /**
     * Flag to indicate parameters that require identity.
     */
    @Use({FlagTarget.VARIABLE}) //ParamSymbols only
    public static final long REQUIRES_IDENTITY = 1L<<62;

    /**
     * Flag to indicate type annotations have been queued for field initializers.
     */
    @Use({FlagTarget.VARIABLE})
    public static final long FIELD_INIT_TYPE_ANNOTATIONS_QUEUED = 1L<<53;

    /**
     * Flag to indicate that the class/interface was declared with the non-sealed modifier.
     */
    @Use({FlagTarget.CLASS})
    @CustomToStringValue("non-sealed")
    public static final long NON_SEALED = 1L<<63;  // part of ExtendedStandardFlags, cannot be reused

    /**
     * Describe modifier flags as they might appear in source code, i.e.,
     * separated by spaces and in the order suggested by JLS 8.1.1.
     */
    public static String toSource(long flags) {
        return asModifierSet(flags).stream()
          .map(Modifier::toString)
          .collect(Collectors.joining(" "));
    }

    /** Modifier masks.
     */
    @NotFlag
    public static final int
        AccessFlags                       = PUBLIC | PROTECTED | PRIVATE,
        LocalClassFlags                   = FINAL | ABSTRACT | STRICTFP | ENUM | SYNTHETIC,
        StaticLocalFlags                  = LocalClassFlags | STATIC | INTERFACE,
        MemberClassFlags                  = LocalClassFlags | INTERFACE | AccessFlags,
        MemberStaticClassFlags            = MemberClassFlags | STATIC,
        ClassFlags                        = LocalClassFlags | INTERFACE | PUBLIC | ANNOTATION,
        InterfaceVarFlags                 = FINAL | STATIC | PUBLIC,
        VarFlags                          = AccessFlags | FINAL | STATIC |
                                            VOLATILE | TRANSIENT | ENUM,
        ConstructorFlags                  = AccessFlags,
        InterfaceMethodFlags              = ABSTRACT | PUBLIC,
        MethodFlags                       = AccessFlags | ABSTRACT | STATIC | NATIVE |
                                            SYNCHRONIZED | FINAL | STRICTFP,
        RecordMethodFlags                 = AccessFlags | ABSTRACT | STATIC |
                                            SYNCHRONIZED | FINAL | STRICTFP;
    @NotFlag
    public static final long
        //NOTE: flags in ExtendedStandardFlags cannot be overlayed across Symbol kinds:
        ExtendedStandardFlags             = (long)StandardFlags | DEFAULT | SEALED | NON_SEALED,
        ExtendedMemberClassFlags          = (long)MemberClassFlags | SEALED | NON_SEALED,
        ExtendedMemberStaticClassFlags    = (long) MemberStaticClassFlags | SEALED | NON_SEALED,
        ExtendedClassFlags                = (long)ClassFlags | SEALED | NON_SEALED,
        ModifierFlags                     = ((long)StandardFlags & ~INTERFACE) | DEFAULT | SEALED | NON_SEALED,
        InterfaceMethodMask               = ABSTRACT | PRIVATE | STATIC | PUBLIC | STRICTFP | DEFAULT,
        AnnotationTypeElementMask         = ABSTRACT | PUBLIC,
        LocalVarFlags                     = FINAL | PARAMETER,
        ReceiverParamFlags                = PARAMETER;

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

    public enum FlagTarget {
        /** This flag can appear the JCBlock.
         */
        BLOCK,
        /** This flag can appear on ClassSymbols.
         */
        CLASS,
        /** This flag can appear on ModuleSymbols.
         */
        MODULE,
        /** This flag can appear on PackageSymbols.
         */
        PACKAGE,
        /** This flag can appear on TypeVarSymbols.
         */
        TYPE_VAR,
        /** This flag can appear on MethodSymbols.
         */
        METHOD,
        /** This flag can appear on VarSymbols, includes
         *  including ParamSymbol, and BindingSymbol.
         */
        VARIABLE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface Use {
        public FlagTarget[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotFlag {}

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CustomToStringValue {
        public String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface NoToStringValue {
    }
}
