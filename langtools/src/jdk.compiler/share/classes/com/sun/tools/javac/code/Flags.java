/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
        Assert.check(flags == 0);
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

    /** Flag is set if symbol is deprecated.
     */
    public static final int DEPRECATED   = 1<<17;

    /** Flag is set for a variable symbol if the variable's definition
     *  has an initializer part.
     */
    public static final int HASINIT          = 1<<18;

    /** Flag is set for compiler-generated anonymous method symbols
     *  that `own' an initializer block.
     */
    public static final int BLOCK            = 1<<20;

    /** Flag bit 21 is available. (used earlier to tag compiler-generated abstract methods that implement
     *  an interface method (Miranda methods)).
     */

    /** Flag is set for nested classes that do not access instance members
     *  or `this' of an outer class and therefore don't need to be passed
     *  a this$n reference.  This value is currently set only for anonymous
     *  classes in superclass constructor calls and only for pre 1.4 targets.
     *  todo: use this value for optimizing away this$n parameters in
     *  other cases.
     */
    public static final int NOOUTERTHIS  = 1<<22;

    /** Flag is set for package symbols if a package has a member or
     *  directory and therefore exists.
     */
    public static final int EXISTS           = 1<<23;

    /** Flag is set for compiler-generated compound classes
     *  representing multiple variable bounds
     */
    public static final int COMPOUND     = 1<<24;

    /** Flag is set for class symbols if a class file was found for this class.
     */
    public static final int CLASS_SEEN   = 1<<25;

    /** Flag is set for class symbols if a source file was found for this
     *  class.
     */
    public static final int SOURCE_SEEN  = 1<<26;

    /* State flags (are reset during compilation).
     */

    /** Flag for class symbols is set and later re-set as a lock in
     *  Enter to detect cycles in the superclass/superinterface
     *  relations.  Similarly for constructor call cycle detection in
     *  Attr.
     */
    public static final int LOCKED           = 1<<27;

    /** Flag for class symbols is set and later re-set to indicate that a class
     *  has been entered but has not yet been attributed.
     */
    public static final int UNATTRIBUTED = 1<<28;

    /** Flag for synthesized default constructors of anonymous classes.
     */
    public static final int ANONCONSTR   = 1<<29;

    /** Flag for class symbols to indicate it has been checked and found
     *  acyclic.
     */
    public static final int ACYCLIC          = 1<<30;

    /** Flag that marks bridge methods.
     */
    public static final long BRIDGE          = 1L<<31;

    /** Flag that marks formal parameters.
     */
    public static final long PARAMETER   = 1L<<33;

    /** Flag that marks varargs methods.
     */
    public static final long VARARGS   = 1L<<34;

    /** Flag for annotation type symbols to indicate it has been
     *  checked and found acyclic.
     */
    public static final long ACYCLIC_ANN      = 1L<<35;

    /** Flag that marks a generated default constructor.
     */
    public static final long GENERATEDCONSTR   = 1L<<36;

    /** Flag that marks a hypothetical method that need not really be
     *  generated in the binary, but is present in the symbol table to
     *  simplify checking for erasure clashes - also used for 292 poly sig methods.
     */
    public static final long HYPOTHETICAL   = 1L<<37;

    /**
     * Flag that marks an internal proprietary class.
     */
    public static final long PROPRIETARY = 1L<<38;

    /**
     * Flag that marks a multi-catch parameter.
     */
    public static final long UNION = 1L<<39;

    // Flag bit (1L << 40) is available.

    /**
     * Flag that marks an 'effectively final' local variable.
     */
    public static final long EFFECTIVELY_FINAL = 1L<<41;

    /**
     * Flag that marks non-override equivalent methods with the same signature.
     */
    public static final long CLASH = 1L<<42;

    /**
     * Flag that marks either a default method or an interface containing default methods.
     */
    public static final long DEFAULT = 1L<<43;

    /**
     * Flag that marks class as auxiliary, ie a non-public class following
     * the public class in a source file, that could block implicit compilation.
     */
    public static final long AUXILIARY = 1L<<44;

    /**
     * Flag that marks that a symbol is not available in the current profile
     */
    public static final long NOT_IN_PROFILE = 1L<<45;

    /**
     * Flag that indicates that an override error has been detected by Check.
     */
    public static final long BAD_OVERRIDE = 1L<<45;

    /**
     * Flag that indicates a signature polymorphic method (292).
     */
    public static final long SIGNATURE_POLYMORPHIC = 1L<<46;

    /**
     * Flag that indicates that an inference variable is used in a 'throws' clause.
     */
    public static final long THROWS = 1L<<47;

    /**
     * Flag that marks potentially ambiguous overloads
     */
    public static final long POTENTIALLY_AMBIGUOUS = 1L<<48;

    /**
     * Flag that marks a synthetic method body for a lambda expression
     */
    public static final long LAMBDA_METHOD = 1L<<49;

    /**
     * Flag to control recursion in TransTypes
     */
    public static final long TYPE_TRANSLATED = 1L<<50;

    /**
     * Flag to indicate class symbol is for module-info
     */
    public static final long MODULE = 1L<<51;

    /**
     * Flag to indicate the given ModuleSymbol is an automatic module.
     */
    public static final long AUTOMATIC_MODULE = 1L<<52;

    /**
     * Flag to indicate the given ModuleSymbol is a system module.
     */
    public static final long SYSTEM_MODULE = 1L<<53;

    /** Modifier masks.
     */
    public static final int
        AccessFlags           = PUBLIC | PROTECTED | PRIVATE,
        LocalClassFlags       = FINAL | ABSTRACT | STRICTFP | ENUM | SYNTHETIC,
        MemberClassFlags      = LocalClassFlags | INTERFACE | AccessFlags,
        ClassFlags            = LocalClassFlags | INTERFACE | PUBLIC | ANNOTATION,
        InterfaceVarFlags     = FINAL | STATIC | PUBLIC,
        VarFlags              = AccessFlags | FINAL | STATIC |
                                VOLATILE | TRANSIENT | ENUM,
        ConstructorFlags      = AccessFlags,
        InterfaceMethodFlags  = ABSTRACT | PUBLIC,
        MethodFlags           = AccessFlags | ABSTRACT | STATIC | NATIVE |
                                SYNCHRONIZED | FINAL | STRICTFP;
    public static final long
        ExtendedStandardFlags       = (long)StandardFlags | DEFAULT,
        ModifierFlags               = ((long)StandardFlags & ~INTERFACE) | DEFAULT,
        InterfaceMethodMask         = ABSTRACT | PRIVATE | STATIC | PUBLIC | STRICTFP | DEFAULT,
        AnnotationTypeElementMask   = ABSTRACT | PUBLIC,
        LocalVarFlags               = FINAL | PARAMETER,
        ReceiverParamFlags          = PARAMETER;


    public static Set<Modifier> asModifierSet(long flags) {
        Set<Modifier> modifiers = modifierSets.get(flags);
        if (modifiers == null) {
            modifiers = java.util.EnumSet.noneOf(Modifier.class);
            if (0 != (flags & PUBLIC))    modifiers.add(Modifier.PUBLIC);
            if (0 != (flags & PROTECTED)) modifiers.add(Modifier.PROTECTED);
            if (0 != (flags & PRIVATE))   modifiers.add(Modifier.PRIVATE);
            if (0 != (flags & ABSTRACT))  modifiers.add(Modifier.ABSTRACT);
            if (0 != (flags & STATIC))    modifiers.add(Modifier.STATIC);
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
        BRIDGE(Flags.BRIDGE),
        SYNTHETIC(Flags.SYNTHETIC),
        ANNOTATION(Flags.ANNOTATION),
        DEPRECATED(Flags.DEPRECATED),
        HASINIT(Flags.HASINIT),
        BLOCK(Flags.BLOCK),
        ENUM(Flags.ENUM),
        MANDATED(Flags.MANDATED),
        NOOUTERTHIS(Flags.NOOUTERTHIS),
        EXISTS(Flags.EXISTS),
        COMPOUND(Flags.COMPOUND),
        CLASS_SEEN(Flags.CLASS_SEEN),
        SOURCE_SEEN(Flags.SOURCE_SEEN),
        LOCKED(Flags.LOCKED),
        UNATTRIBUTED(Flags.UNATTRIBUTED),
        ANONCONSTR(Flags.ANONCONSTR),
        ACYCLIC(Flags.ACYCLIC),
        PARAMETER(Flags.PARAMETER),
        VARARGS(Flags.VARARGS),
        ACYCLIC_ANN(Flags.ACYCLIC_ANN),
        GENERATEDCONSTR(Flags.GENERATEDCONSTR),
        HYPOTHETICAL(Flags.HYPOTHETICAL),
        PROPRIETARY(Flags.PROPRIETARY),
        UNION(Flags.UNION),
        EFFECTIVELY_FINAL(Flags.EFFECTIVELY_FINAL),
        CLASH(Flags.CLASH),
        AUXILIARY(Flags.AUXILIARY),
        NOT_IN_PROFILE(Flags.NOT_IN_PROFILE),
        BAD_OVERRIDE(Flags.BAD_OVERRIDE),
        SIGNATURE_POLYMORPHIC(Flags.SIGNATURE_POLYMORPHIC),
        THROWS(Flags.THROWS),
        LAMBDA_METHOD(Flags.LAMBDA_METHOD),
        TYPE_TRANSLATED(Flags.TYPE_TRANSLATED),
        MODULE(Flags.MODULE);

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
