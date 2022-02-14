/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.annotation.ElementType;
import java.util.Set;
import static java.lang.annotation.ElementType.*;

/**
 * Represents a JVM access flag on a runtime member, such as a
 * {@linkplain Class class}, {@linkplain Field field}, or {@linkplain
 * Executable method}.
 *
 * <P>JVM access flags are related to, but distinct from Java language
 * {@linkplain Modifier modifiers}. Some modifiers and access flags
 * have a one-to-one correspondence, such as {@code public}. In other
 * cases, some language-level modifiers do <em>not</em> have an access
 * flag, such as {@code sealed} (JVMS {@jvms 4.7.31}) and some access
 * flags have no corresponding modifier, such as {@linkplain SYNTHETIC
 * synthetic}
 *
 * <p>The values for the constants representing the access flags are
 * taken from the tables in sections {@jvms 4.1}, {@jvms 4.4}, {@jvms
 * 4.5}, and {@jvms 4.7} of <cite>The Java Virtual Machine
 * Specification</cite>.
 *
 * @see java.lang.reflect.Modifier
 * @see java.compiler/javax.lang.model.element.Modifier
 * @since 19
 */
@SuppressWarnings("doclint:reference") // cross-module link
public enum AccessFlag {
    /**
     * The access flag {@code ACC_PUBLIC}, corresponding to the source
     * modifier {@link Modifier#PUBLIC public}.
     */
    PUBLIC(Modifier.PUBLIC, true, Set.of(TYPE, CONSTRUCTOR, METHOD, FIELD)),

    /**
     * The access flag {@code ACC_PROTECTED}, corresponding to the
     * source modifier {@link Modifier#PROTECTED protected}.
     */
    PROTECTED(Modifier.PROTECTED, true, Set.of(CONSTRUCTOR, METHOD, FIELD)),

    /**
     * The access flag {@code ACC_PRIVATE}, corresponding to the
     * source modifier {@link Modifier#PRIVATE private}.
     */
    PRIVATE(Modifier.PRIVATE, true, Set.of(CONSTRUCTOR, METHOD, FIELD)),

    /**
     * The access flag {@code ACC_ABSTRACT}, corresponding to the
     * source modifier {@code link Modifier#ABSTRACT abstract}.
     */
    ABSTRACT(Modifier.ABSTRACT, true, Set.of(TYPE, METHOD)),

    /**
     * The access flag {@code ACC_STATIC}, corresponding to the source
     * modifier {@link Modifier#STATIC static}.
     */
    STATIC(Modifier.STATIC, true, Set.of(FIELD, METHOD)),

    /**
     * The access flag {@code ACC_FINAL}, corresponding to the source
     * modifier {@link Modifier#FINAL final}.
     */
    FINAL(Modifier.FINAL, true, Set.of(FIELD, METHOD, PARAMETER, TYPE)),

    /**
     * The access flag {@code ACC_TRANSIENT}, corresponding to the
     * source modifier {@link Modifier#TRANSIENT transient}.
     */
    TRANSIENT(Modifier.TRANSIENT, true, Set.of(FIELD)),

     /**
      * The access flag {@code ACC_VOLATILE}, corresponding to the
      * source modifier {@link Modifier#VOLATILE volatile}.
      */
    VOLATILE(Modifier.VOLATILE, true, Set.of(FIELD)),

    /**
     * The access flag {@code ACC_SYNCHRONIZED}, corresponding to the
     * source modifier {@link Modifier#SYNCHRONIZED synchronized}.
     */
    SYNCHRONIZED(Modifier.SYNCHRONIZED, true, Set.of(METHOD, CONSTRUCTOR)),

    /**
     * The access flag {@code ACC_NATIVE}, corresponding to the source
     * modifier {@link Modifier#NATIVE native}.
     */
    NATIVE(Modifier.NATIVE, true, Set.of(METHOD)),

    /**
     * The access flag {@code ACC_INTERFACE}.
     * @see Class#isInterface()
     */
    INTERFACE(Modifier.INTERFACE, false, Set.of(TYPE)),

    /**
     * The access flag {@code ACC_STRICT}, corresponding to the source
     * modifier {@link Modifier#STRICT strictfp}.
     */
    STRICT(Modifier.STRICT, true, Set.of(METHOD, CONSTRUCTOR)),

    /**
     * The access flag {@code ACC_BRIDGE}
     * @see Method#isBridge()
     */
    BRIDGE(0x00000040, false, Set.of(METHOD, CONSTRUCTOR)),

    /**
     * The access flag {@code ACC_VARARGS}.
     * @see Executable#isVarArgs()
     */
    VARARGS(0x00000080, false, Set.of(METHOD, CONSTRUCTOR)),

    /**
     * The access flag {@code ACC_SYNTHETIC}.
     * @see Class#isSynthetic()
     * @see Executable#isSynthetic()
     */
    SYNTHETIC(0x00001000, false,
              Set.of(TYPE, FIELD, METHOD, CONSTRUCTOR, ElementType.MODULE, PARAMETER)),

    /**
     * The access flag {@code ACC_MANDATED}.
     */
    MANDATED(0x00008000, false, Set.of(ElementType.MODULE, PARAMETER)),

    /**
     * The access flag {@code ACC_ANNOTATION}.
     * @see Class#isAnnotation()
     */
    ANNOTATION(0x00002000, false, Set.of(TYPE)),

   /**
    * The access flag {@code ACC_ENUM}.
    * @see Class#isEnum()
    */
    ENUM(0x00004000, false, Set.of(TYPE, FIELD)),

   /**
    * The access flag {@code ACC_MODULE}.
    */
    MODULE(0x8000, false, Set.of(TYPE))
    ;

    // May want to override toString for a different enum constant ->
    // name mapping.

    private int mask;
    private boolean sourceModifier;

    // For now, reuse ElementType rather than defining a separate
    // type.
    // Intentionally using Set rather than EnumSet since EnumSet is
    // mutable.
    private Set<ElementType> targets;

    private AccessFlag(Set<ElementType> targets) {
        this.mask = 0x0;
        this.sourceModifier = false;
        this.targets = targets;
    }

    private AccessFlag(int mask, boolean sourceModifier, Set<ElementType> targets) {
        this.mask = mask;
        this.sourceModifier = sourceModifier;
        this.targets = targets;
    }

    /**
     * {@return corresponding integer mask for the flag; 0 if none}
     */
    public int mask() {
        return mask;
    }

    /**
     * {@return whether or not the flag has a directly corresponding
     * modifier in the Java programming language}
     */
    public boolean sourceModifier() {
        return sourceModifier;
    }

    /**
     * {@return kinds of constructs the flag can be applied to}
     */
    public Set<ElementType> targets() {
        return targets;
    }
    // -------------------------------------------------------------

    // Future utilities and static factories of this enum class will
    // be written using the information below, copied from Modifier
    // for bootstrapping.

    // /**
    // * The Java source modifiers that can be applied to a class.
    // * @jls 8.1.1 Class Modifiers
    // */
    // private static final int CLASS_MODIFIERS =
    // Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE |
    // Modifier.ABSTRACT | Modifier.STATIC | Modifier.FINAL |
    // Modifier.STRICT;

    // /**
    // * The Java source modifiers that can be applied to an interface.
    // * @jls 9.1.1 Interface Modifiers
    // */
    // private static final int INTERFACE_MODIFIERS =
    // Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE |
    // Modifier.ABSTRACT | Modifier.STATIC | Modifier.STRICT;


    // /**
    // * The Java source modifiers that can be applied to a constructor.
    // * @jls 8.8.3 Constructor Modifiers
    // */
    // private static final int CONSTRUCTOR_MODIFIERS =
    // Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;

    // /**
    // * The Java source modifiers that can be applied to a method.
    // * @jls 8.4.3 Method Modifiers
    // */
    // private static final int METHOD_MODIFIERS =
    // Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE |
    // Modifier.ABSTRACT | Modifier.STATIC | Modifier.FINAL |
    // Modifier.SYNCHRONIZED | Modifier.NATIVE | Modifier.STRICT;

    // /**
    // * The Java source modifiers that can be applied to a field.
    // * @jls 8.3.1 Field Modifiers
    // */
    // private static final int FIELD_MODIFIERS =
    // Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE |
    // Modifier.STATIC | Modifier.FINAL | Modifier.TRANSIENT |
    // Modifier.VOLATILE;

    // /**
    // * The Java source modifiers that can be applied to a method or constructor parameter.
    // * @jls 8.4.1 Formal Parameters
    // */
    // private static final int PARAMETER_MODIFIERS =
    // Modifier.FINAL;

    // static final int ACCESS_MODIFIERS =
    // Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;
}
