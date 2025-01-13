/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.ModuleExportInfo;
import java.lang.classfile.attribute.ModuleOpenInfo;
import java.lang.classfile.attribute.ModuleRequireInfo;
import java.lang.module.ModuleDescriptor;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;

import static java.lang.classfile.ClassFile.*;
import static java.lang.reflect.ClassFileFormatVersion.*;
import static java.util.Map.entry;

/**
 * Represents a JVM access or module-related flag on a runtime member,
 * such as a {@linkplain Class class}, {@linkplain Field field}, or
 * {@linkplain Executable method}.
 *
 * <P>JVM access and module-related flags are related to, but distinct
 * from Java language {@linkplain Modifier modifiers}. Some modifiers
 * and access flags have a one-to-one correspondence, such as {@code
 * public}. In other cases, some language-level modifiers do
 * <em>not</em> have an access flag, such as {@code sealed} (JVMS
 * {@jvms 4.7.31}) and some access flags have no corresponding
 * modifier, such as {@linkplain #SYNTHETIC synthetic}.
 *
 * <p>The values for the constants representing the access and module
 * flags are taken from sections of <cite>The Java Virtual Machine
 * Specification</cite> including {@jvms 4.1} (class access and
 * property modifiers), {@jvms 4.5} (field access and property flags),
 * {@jvms 4.6} (method access and property flags), {@jvms 4.7.6}
 * (nested class access and property flags), {@jvms 4.7.24} (method
 * parameters), and {@jvms 4.7.25} (module flags and requires,
 * exports, and opens flags).
 *
 * <p>The {@linkplain #mask() mask} values for the different access
 * flags are <em>not</em> distinct. Flags are defined for different
 * kinds of JVM structures and the same bit position has different
 * meanings in different contexts. For example, {@code 0x0000_0040}
 * indicates a {@link #VOLATILE volatile} field but a {@linkplain
 * #BRIDGE bridge method}; {@code 0x0000_0080} indicates a {@link
 * #TRANSIENT transient} field but a {@linkplain #VARARGS variable
 * arity (varargs)} method.
 *
 * @implSpec
 * The access flag constants are ordered by non-decreasing mask
 * value; that is the mask value of a constant is greater than or
 * equal to the mask value of an immediate neighbor to its (syntactic)
 * left. If new constants are added, this property will be
 * maintained. That implies new constants will not necessarily be
 * added at the end of the existing list.
 *
 * @apiNote
 * The JVM class file format has a {@linkplain ClassFileFormatVersion new version} defined for each new
 * {@linkplain Runtime.Version#feature() feature release}. A new class
 * file version may define new access flags or retire old ones. {@code
 * AccessFlag} is intended to model the set of access flags across
 * class file format versions. The range of versions an access flag is
 * recognized is not explicitly indicated in this API. See the current
 * <cite>The Java Virtual Machine Specification</cite> for
 * details. Unless otherwise indicated, access flags can be assumed to
 * be recognized in the {@linkplain Runtime#version() current
 * version}.
 *
 * @see java.lang.reflect.Modifier
 * @see java.lang.module.ModuleDescriptor.Modifier
 * @see java.lang.module.ModuleDescriptor.Requires.Modifier
 * @see java.lang.module.ModuleDescriptor.Exports.Modifier
 * @see java.lang.module.ModuleDescriptor.Opens.Modifier
 * @see java.compiler/javax.lang.model.element.Modifier
 * @since 20
 */
@SuppressWarnings("doclint:reference") // cross-module link
public enum AccessFlag {
    /**
     * The access flag {@code ACC_PUBLIC}, corresponding to the source
     * modifier {@link Modifier#PUBLIC public}, with a mask value of
     * <code>{@value "0x%04x" Modifier#PUBLIC}</code>.
     */
    PUBLIC(Modifier.PUBLIC, true,
           Location.SET_CLASS_FIELD_METHOD_INNER_CLASS,
           List.of(Map.entry(RELEASE_0, Location.SET_CLASS_FIELD_METHOD))),

    /**
     * The access flag {@code ACC_PRIVATE}, corresponding to the
     * source modifier {@link Modifier#PRIVATE private}, with a mask
     * value of <code>{@value "0x%04x" Modifier#PRIVATE}</code>.
     */
    PRIVATE(Modifier.PRIVATE, true, Location.SET_FIELD_METHOD_INNER_CLASS,
            List.of(Map.entry(RELEASE_0, Location.SET_FIELD_METHOD))),

    /**
     * The access flag {@code ACC_PROTECTED}, corresponding to the
     * source modifier {@link Modifier#PROTECTED protected}, with a mask
     * value of <code>{@value "0x%04x" Modifier#PROTECTED}</code>.
     */
    PROTECTED(Modifier.PROTECTED, true, Location.SET_FIELD_METHOD_INNER_CLASS,
              List.of(Map.entry(RELEASE_0, Location.SET_FIELD_METHOD))),

    /**
     * The access flag {@code ACC_STATIC}, corresponding to the source
     * modifier {@link Modifier#STATIC static}, with a mask value of
     * <code>{@value "0x%04x" Modifier#STATIC}</code>.
     */
    STATIC(Modifier.STATIC, true, Location.SET_FIELD_METHOD_INNER_CLASS,
           List.of(Map.entry(RELEASE_0, Location.SET_FIELD_METHOD))),

    /**
     * The access flag {@code ACC_FINAL}, corresponding to the source
     * modifier {@link Modifier#FINAL final}, with a mask
     * value of <code>{@value "0x%04x" Modifier#FINAL}</code>.
     */
    FINAL(Modifier.FINAL, true,
          Location.SET_FINAL_8,
          List.of(Map.entry(RELEASE_7, Location.SET_CLASS_FIELD_METHOD_INNER_CLASS),
                  Map.entry(RELEASE_0, Location.SET_CLASS_FIELD_METHOD))),

    /**
     * The access flag {@code ACC_SUPER} with a mask value of {@code
     * 0x0020}.
     *
     * @apiNote
     * In Java SE 8 and above, the JVM treats the {@code ACC_SUPER}
     * flag as set in every class file (JVMS {@jvms 4.1}).
     */
    SUPER(0x0000_0020, false, Location.SET_CLASS, List.of()),

    /**
     * The module flag {@code ACC_OPEN} with a mask value of {@code
     * 0x0020}.
     * @see java.lang.module.ModuleDescriptor#isOpen
     */
    OPEN(0x0000_0020, false, Location.SET_MODULE,
         List.of(Map.entry(RELEASE_8, Location.EMPTY_SET))),

    /**
     * The module requires flag {@code ACC_TRANSITIVE} with a mask
     * value of {@code 0x0020}.
     * @see java.lang.module.ModuleDescriptor.Requires.Modifier#TRANSITIVE
     */
    TRANSITIVE(0x0000_0020, false, Location.SET_MODULE_REQUIRES,
               List.of(Map.entry(RELEASE_8, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_SYNCHRONIZED}, corresponding to the
     * source modifier {@link Modifier#SYNCHRONIZED synchronized}, with
     * a mask value of <code>{@value "0x%04x" Modifier#SYNCHRONIZED}</code>.
     */
    SYNCHRONIZED(Modifier.SYNCHRONIZED, true, Location.SET_METHOD, List.of()),

    /**
     * The module requires flag {@code ACC_STATIC_PHASE} with a mask
     * value of {@code 0x0040}.
     * @see java.lang.module.ModuleDescriptor.Requires.Modifier#STATIC
     */
    STATIC_PHASE(0x0000_0040, false, Location.SET_MODULE_REQUIRES,
                 List.of(Map.entry(RELEASE_8, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_VOLATILE}, corresponding to the
     * source modifier {@link Modifier#VOLATILE volatile}, with a mask
     * value of <code>{@value "0x%04x" Modifier#VOLATILE}</code>.
     */
    VOLATILE(Modifier.VOLATILE, true, Location.SET_FIELD, List.of()),

    /**
     * The access flag {@code ACC_BRIDGE} with a mask value of
     * <code>{@value "0x%04x" Modifier#BRIDGE}</code>
     * @see Method#isBridge()
     */
    BRIDGE(Modifier.BRIDGE, false, Location.SET_METHOD,
           List.of(Map.entry(RELEASE_4, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_TRANSIENT}, corresponding to the
     * source modifier {@link Modifier#TRANSIENT transient}, with a
     * mask value of <code>{@value "0x%04x" Modifier#TRANSIENT}</code>.
     */
    TRANSIENT(Modifier.TRANSIENT, true, Location.SET_FIELD, List.of()),

    /**
     * The access flag {@code ACC_VARARGS} with a mask value of
     * <code>{@value "0x%04x" Modifier#VARARGS}</code>.
     * @see Executable#isVarArgs()
     */
    VARARGS(Modifier.VARARGS, false, Location.SET_METHOD,
            List.of(Map.entry(RELEASE_4, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_NATIVE}, corresponding to the source
     * modifier {@link Modifier#NATIVE native}, with a mask value of
     * <code>{@value "0x%04x" Modifier#NATIVE}</code>.
     */
    NATIVE(Modifier.NATIVE, true, Location.SET_METHOD, List.of()),

    /**
     * The access flag {@code ACC_INTERFACE} with a mask value of
     * {@code 0x0200}.
     * @see Class#isInterface()
     */
    INTERFACE(Modifier.INTERFACE, false, Location.SET_CLASS_INNER_CLASS,
              List.of(Map.entry(RELEASE_0, Location.SET_CLASS))),

    /**
     * The access flag {@code ACC_ABSTRACT}, corresponding to the
     * source modifier {@link Modifier#ABSTRACT abstract}, with a mask
     * value of <code>{@value "0x%04x" Modifier#ABSTRACT}</code>.
     */
    ABSTRACT(Modifier.ABSTRACT, true,
             Location.SET_CLASS_METHOD_INNER_CLASS,
             List.of(Map.entry(RELEASE_0, Location.SET_CLASS_METHOD))),

    /**
     * The access flag {@code ACC_STRICT}, corresponding to the source
     * modifier {@link Modifier#STRICT strictfp}, with a mask value of
     * <code>{@value "0x%04x" Modifier#STRICT}</code>.
     *
     * @apiNote
     * The {@code ACC_STRICT} access flag is defined for class file
     * major versions 46 through 60, inclusive (JVMS {@jvms 4.6}),
     * corresponding to Java SE 1.2 through 16.
     */
    STRICT(Modifier.STRICT, true, Location.EMPTY_SET,
           List.of(Map.entry(RELEASE_16, Location.SET_METHOD),
                   Map.entry(RELEASE_1, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_SYNTHETIC} with a mask value of
     * <code>{@value "0x%04x" Modifier#SYNTHETIC}</code>.
     * @see Class#isSynthetic()
     * @see Executable#isSynthetic()
     * @see java.lang.module.ModuleDescriptor.Modifier#SYNTHETIC
     */
    SYNTHETIC(Modifier.SYNTHETIC, false, Location.SET_SYNTHETIC_9,
              List.of(Map.entry(RELEASE_8, Location.SET_SYNTHETIC_8),
                      Map.entry(RELEASE_7, Location.SET_SYNTHETIC_5),
                      Map.entry(RELEASE_4, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_ANNOTATION} with a mask value of
     * <code>{@value "0x%04x" Modifier#ANNOTATION}</code>.
     * @see Class#isAnnotation()
     */
    ANNOTATION(Modifier.ANNOTATION, false, Location.SET_CLASS_INNER_CLASS,
               List.of(Map.entry(RELEASE_4, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_ENUM} with a mask value of
     * <code>{@value "0x%04x" Modifier#ENUM}</code>.
     * @see Class#isEnum()
     */
    ENUM(Modifier.ENUM, false, Location.SET_CLASS_FIELD_INNER_CLASS,
         List.of(Map.entry(RELEASE_4, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_MANDATED} with a mask value of
     * <code>{@value "0x%04x" Modifier#MANDATED}</code>.
     */
    MANDATED(Modifier.MANDATED, false, Location.SET_MANDATED_9,
             List.of(Map.entry(RELEASE_8, Location.SET_METHOD_PARAM),
                     Map.entry(RELEASE_7, Location.EMPTY_SET))),

    /**
     * The access flag {@code ACC_MODULE} with a mask value of {@code
     * 0x8000}.
     */
    MODULE(0x0000_8000, false, Location.SET_CLASS,
           List.of(Map.entry(RELEASE_8, Location.EMPTY_SET))),
    ;

    // May want to override toString for a different enum constant ->
    // name mapping.

    private final int mask;
    private final boolean sourceModifier;

    // Intentionally using Set rather than EnumSet since EnumSet is
    // mutable.
    private final Set<Location> locations;
    // historical locations up to a given version
    private final List<Map.Entry<ClassFileFormatVersion, Set<Location>>> historicalLocations;

    private AccessFlag(int mask,
                       boolean sourceModifier,
                       Set<Location> locations,
                       List<Map.Entry<ClassFileFormatVersion, Set<Location>>> historicalLocations) {
        this.mask = mask;
        this.sourceModifier = sourceModifier;
        this.locations = locations;
        this.historicalLocations = Location.ensureHistoryOrdered(historicalLocations);
    }

    /**
     * {@return the corresponding integer mask for the access flag}
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
     * {@return kinds of constructs the flag can be applied to in the
     * latest class file format version}
     */
    public Set<Location> locations() {
        return locations;
    }

    /**
     * {@return kinds of constructs the flag can be applied to in the
     * given class file format version}
     * @param cffv the class file format version to use
     * @throws NullPointerException if the parameter is {@code null}
     */
    public Set<Location> locations(ClassFileFormatVersion cffv) {
        return Location.findInHistory(locations, historicalLocations, cffv);
    }

    /**
     * {@return an unmodifiable set of access flags for the given mask value
     * appropriate for the location in question}
     *
     * @param mask bit mask of access flags
     * @param location context to interpret mask value
     * @throws IllegalArgumentException if the mask contains bit
     * positions not support for the location in question
     * @throws NullPointerException if {@code location} is {@code null}
     */
    public static Set<AccessFlag> maskToAccessFlags(int mask, Location location) {
        Set<AccessFlag> result = java.util.EnumSet.noneOf(AccessFlag.class);
        for (var accessFlag : LocationToFlags.LOCATION_TO_FLAGS.get(location)) {
            int accessMask = accessFlag.mask();
            if ((mask &  accessMask) != 0) {
                result.add(accessFlag);
                mask = mask & ~accessMask;
            }
        }
        if (mask != 0) {
            throw new IllegalArgumentException("Unmatched bit position 0x" +
                                               Integer.toHexString(mask) +
                                               " for location " + location);
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * A location within a class file where flags can be applied.
     *
     * Note that since these locations represent class file structures
     * rather than language structures many language structures, such
     * as constructors and interfaces, are <em>not</em> present.
     * @since 20
     */
    public enum Location {
        /**
         * Class location.
         *
         * @see Class#accessFlags()
         * @see ClassModel#flags()
         * @jvms 4.1 The {@code ClassFile} Structure
         */
        CLASS(ACC_PUBLIC | ACC_FINAL | ACC_SUPER |
              ACC_INTERFACE | ACC_ABSTRACT |
              ACC_SYNTHETIC | ACC_ANNOTATION |
              ACC_ENUM | ACC_MODULE,
              List.of(Map.entry(RELEASE_8, // no module
                                ACC_PUBLIC | ACC_FINAL | ACC_SUPER |
                                ACC_INTERFACE | ACC_ABSTRACT |
                                ACC_SYNTHETIC | ACC_ANNOTATION | ACC_ENUM),
                      Map.entry(RELEASE_4, // no synthetic, annotation, enum
                                ACC_PUBLIC | ACC_FINAL | ACC_SUPER |
                                ACC_INTERFACE | ACC_ABSTRACT))),

        /**
         * Field location.
         *
         * @see Field#accessFlags()
         * @see FieldModel#flags()
         * @jvms 4.5 Fields
         */
        FIELD(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
              ACC_STATIC | ACC_FINAL | ACC_VOLATILE |
              ACC_TRANSIENT | ACC_SYNTHETIC | ACC_ENUM,
              List.of(Map.entry(RELEASE_4, // no synthetic, enum
                                ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
                                ACC_STATIC | ACC_FINAL | ACC_VOLATILE |
                                ACC_TRANSIENT))),

        /**
         * Method location.
         *
         * @see Executable#accessFlags()
         * @see MethodModel#flags()
         * @jvms 4.6 Methods
         */
        METHOD(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
               ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED |
               ACC_BRIDGE | ACC_VARARGS | ACC_NATIVE |
               ACC_ABSTRACT | ACC_SYNTHETIC,
               List.of(Map.entry(RELEASE_16, // had strict
                                 ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
                                 ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED |
                                 ACC_BRIDGE | ACC_VARARGS | ACC_NATIVE |
                                 ACC_ABSTRACT | ACC_STRICT | ACC_SYNTHETIC),
                       Map.entry(RELEASE_4, // no bridge, varargs, synthetic
                                 ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
                                 ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED |
                                 ACC_NATIVE | ACC_ABSTRACT | ACC_STRICT),
                       Map.entry(RELEASE_1, // no strict
                                 ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
                                 ACC_STATIC | ACC_FINAL | ACC_SYNCHRONIZED |
                                 ACC_NATIVE | ACC_ABSTRACT))),

        /**
         * Inner class location.
         *
         * @see Class#accessFlags()
         * @see InnerClassInfo#flags()
         * @jvms 4.7.6 The {@code InnerClasses} Attribute
         */
        INNER_CLASS(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
                    ACC_STATIC | ACC_FINAL | ACC_INTERFACE | ACC_ABSTRACT |
                    ACC_SYNTHETIC | ACC_ANNOTATION | ACC_ENUM,
                    List.of(Map.entry(RELEASE_4, // no synthetic, annotation, enum
                            ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED |
                            ACC_STATIC | ACC_FINAL | ACC_INTERFACE |
                            ACC_ABSTRACT),
                            Map.entry(RELEASE_0, 0))), // did not exist

        /**
         * Method parameter location.
         *
         * @see Parameter#accessFlags()
         * @see MethodParameterInfo#flags()
         * @jvms 4.7.24 The {@code MethodParameters} Attribute
         */
        METHOD_PARAMETER(ACC_FINAL | ACC_SYNTHETIC | ACC_MANDATED,
                         List.of(Map.entry(RELEASE_7, 0))),  // did not exist

        /**
         * Module location.
         *
         * @see ModuleDescriptor#accessFlags()
         * @see ModuleAttribute#moduleFlags()
         * @jvms 4.7.25 The {@code Module} Attribute
         */
        MODULE(ACC_OPEN | ACC_SYNTHETIC | ACC_MANDATED,
               List.of(Map.entry(RELEASE_8, 0))),  // did not exist

        /**
         * Module requires location.
         *
         * @see ModuleDescriptor.Requires#accessFlags()
         * @see ModuleRequireInfo#requiresFlags()
         * @jvms 4.7.25 The {@code Module} Attribute
         */
        MODULE_REQUIRES(ACC_TRANSITIVE | ACC_STATIC_PHASE | ACC_SYNTHETIC | ACC_MANDATED,
                        List.of(Map.entry(RELEASE_8, 0))),  // did not exist

        /**
         * Module exports location.
         *
         * @see ModuleDescriptor.Exports#accessFlags()
         * @see ModuleExportInfo#exportsFlags()
         * @jvms 4.7.25 The {@code Module} Attribute
         */
        MODULE_EXPORTS(ACC_SYNTHETIC | ACC_MANDATED,
                       List.of(Map.entry(RELEASE_8, 0))),  // did not exist

        /**
         * Module opens location.
         *
         * @see ModuleDescriptor.Opens#accessFlags()
         * @see ModuleOpenInfo#opensFlags()
         * @jvms 4.7.25 The {@code Module} Attribute
         */
        MODULE_OPENS(ACC_SYNTHETIC | ACC_MANDATED,
                     List.of(Map.entry(RELEASE_8, 0))),  // did not exist
        ;

        // Repeated sets of locations used by AccessFlag constants
        private static final Set<Location> EMPTY_SET = Set.of();
        private static final Set<Location> SET_MODULE = Set.of(MODULE);
        private static final Set<Location> SET_CLASS_METHOD_INNER_CLASS =
            Set.of(CLASS, METHOD, INNER_CLASS);
        private static final Set<Location> SET_CLASS_FIELD_METHOD =
            Set.of(CLASS, FIELD, METHOD);
        private static final Set<Location> SET_CLASS_FIELD_INNER_CLASS =
            Set.of(CLASS, FIELD, INNER_CLASS);
        private static final Set<Location> SET_CLASS_FIELD_METHOD_INNER_CLASS =
            Set.of(CLASS, FIELD, METHOD, INNER_CLASS);
        private static final Set<Location> SET_CLASS_METHOD =
            Set.of(CLASS, METHOD);
        private static final Set<Location> SET_FIELD_METHOD =
            Set.of(FIELD, METHOD);
        private static final Set<Location> SET_FIELD_METHOD_INNER_CLASS =
            Set.of(FIELD, METHOD, INNER_CLASS);
        private static final Set<Location> SET_METHOD = Set.of(METHOD);
        private static final Set<Location> SET_METHOD_PARAM = Set.of(METHOD_PARAMETER);
        private static final Set<Location> SET_FIELD = Set.of(FIELD);
        private static final Set<Location> SET_CLASS = Set.of(CLASS);
        private static final Set<Location> SET_CLASS_INNER_CLASS =
            Set.of(CLASS, INNER_CLASS);
        private static final Set<Location> SET_MODULE_REQUIRES =
            Set.of(MODULE_REQUIRES);
        private static final Set<Location> SET_FINAL_8 =
            Set.of(CLASS, FIELD, METHOD,
                   INNER_CLASS,     /* added in 1.1 */
                   METHOD_PARAMETER); /* added in 8 */
        private static final Set<Location> SET_SYNTHETIC_5 =
              Set.of(CLASS, FIELD, METHOD,
                     INNER_CLASS);
        private static final Set<Location> SET_SYNTHETIC_8 =
              Set.of(CLASS, FIELD, METHOD,
                     INNER_CLASS, METHOD_PARAMETER);
        private static final Set<Location> SET_SYNTHETIC_9 =
              // Added as an access flag in 5.0
              Set.of(CLASS, FIELD, METHOD,
                     INNER_CLASS,
                     METHOD_PARAMETER, // Added in 8
                     // Module-related items added in 9
                     MODULE, MODULE_REQUIRES,
                     MODULE_EXPORTS, MODULE_OPENS);
        private static final Set<Location> SET_MANDATED_9 =
            Set.of(METHOD_PARAMETER, // From 8
                   // Starting in 9
                   MODULE, MODULE_REQUIRES,
                   MODULE_EXPORTS, MODULE_OPENS);

        private final int flagsMask;
        private final List<Map.Entry<ClassFileFormatVersion, Integer>> historicalFlagsMasks;

        Location(int flagsMask,
                 List<Map.Entry<ClassFileFormatVersion, Integer>> historicalFlagsMasks) {
            this.flagsMask = flagsMask;
            this.historicalFlagsMasks = ensureHistoryOrdered(historicalFlagsMasks);
        }

        // Ensures the historical versions are from newest to oldest and do not include the latest
        // These 2 utilities reside in Location because Location must be initialized before AccessFlag
        private static <T> List<Map.Entry<ClassFileFormatVersion, T>> ensureHistoryOrdered(
                List<Map.Entry<ClassFileFormatVersion, T>> history) {
            var lastVersion = ClassFileFormatVersion.latest();
            for (var e : history) {
                var historyVersion = e.getKey();
                if (lastVersion.compareTo(historyVersion) <= 0) {
                    throw new IllegalArgumentException("Versions out of order");
                }
                lastVersion = historyVersion;
            }
            return history;
        }

        private static <T> T findInHistory(T candidate, List<Map.Entry<ClassFileFormatVersion, T>> history,
                                           ClassFileFormatVersion cffv) {
            Objects.requireNonNull(cffv);
            for (var e : history) {
                if (e.getKey().compareTo(cffv) < 0) {
                    // last version found was valid
                    return candidate;
                }
                candidate = e.getValue();
            }
            return candidate;
        }

        /**
         * {@return the union of integer masks of all access flags defined for
         * this location in the latest class file format version}  If {@code
         * mask & ~location.flagsMask() != 0}, then a bit mask {@code mask} has
         * one or more undefined bits set for {@code location}.  This union of
         * access flags mask may not itself be a valid flag value.
         *
         * @since 25
         */
        public int flagsMask() {
            return flagsMask;
        }

        /**
         * {@return the union of integer masks of all access flags defined for
         * this location in the given class file format version}  If {@code
         * mask & ~location.flagsMask(cffv) != 0}, then a bit mask {@code mask}
         * has one or more undefined bits set for {@code location} in {@code
         * cffv}.  This union of access flags mask may not itself be a valid
         * flag value.
         * <p>
         * This method may return {@code 0} if the structure did not exist in
         * the given {@code cffv}.
         *
         * @param cffv the class file format version
         * @throws NullPointerException if {@code cffv} is {@code null}
         * @since 25
         */
        public int flagsMask(ClassFileFormatVersion cffv) {
            return findInHistory(flagsMask, historicalFlagsMasks, cffv);
        }

        /**
         * {@return all access flags defined for this location, as a set of
         * flag enums}  This set may include mutually exclusive flags.
         *
         * @since 25
         */
        public Set<AccessFlag> flags() {
            if (this == METHOD) {
                return LocationToFlags.CURRENT_METHOD_FLAGS;
            }
            return LocationToFlags.LOCATION_TO_FLAGS.get(this);
        }

        /**
         * {@return all access flags defined for this location, as a set of flag
         * enums}  This set may include mutually exclusive flags.
         * <p>
         * This method may return an empty set if the structure did not exist in
         * the given {@code cffv}.
         *
         * @param cffv the class file format version
         * @throws NullPointerException if {@code cffv} is {@code null}
         * @since 25
         */
        public Set<AccessFlag> flags(ClassFileFormatVersion cffv) {
            int flagsMask = flagsMask(cffv); // implicit null check
            return maskToAccessFlags(flagsMask, this);
        }
    }

    private static final class LocationToFlags {
        // A map from location to flags that ever existed on the location
        private static final Map<Location, Set<AccessFlag>> LOCATION_TO_FLAGS =
            Map.ofEntries(entry(Location.CLASS,
                                Set.of(PUBLIC, FINAL, SUPER,
                                       INTERFACE, ABSTRACT,
                                       SYNTHETIC, ANNOTATION,
                                       ENUM, AccessFlag.MODULE)),
                          entry(Location.FIELD,
                                Set.of(PUBLIC, PRIVATE, PROTECTED,
                                       STATIC, FINAL, VOLATILE,
                                       TRANSIENT, SYNTHETIC, ENUM)),
                          entry(Location.METHOD,
                                Set.of(PUBLIC, PRIVATE, PROTECTED,
                                       STATIC, FINAL, SYNCHRONIZED,
                                       BRIDGE, VARARGS, NATIVE,
                                       ABSTRACT, STRICT, SYNTHETIC)),
                          entry(Location.INNER_CLASS,
                                Set.of(PUBLIC, PRIVATE, PROTECTED,
                                       STATIC, FINAL, INTERFACE, ABSTRACT,
                                       SYNTHETIC, ANNOTATION, ENUM)),
                          entry(Location.METHOD_PARAMETER,
                                Set.of(FINAL, SYNTHETIC, MANDATED)),
                          entry(Location.MODULE,
                                Set.of(OPEN, SYNTHETIC, MANDATED)),
                          entry(Location.MODULE_REQUIRES,
                                Set.of(TRANSITIVE, STATIC_PHASE, SYNTHETIC, MANDATED)),
                          entry(Location.MODULE_EXPORTS,
                                Set.of(SYNTHETIC, MANDATED)),
                          entry(Location.MODULE_OPENS,
                                Set.of(SYNTHETIC, MANDATED)));
        // Current recognized flags on method, which does not include strict
        private static final Set<AccessFlag> CURRENT_METHOD_FLAGS = Set.of(
                PUBLIC, PRIVATE, PROTECTED,
                STATIC, FINAL, SYNCHRONIZED,
                BRIDGE, VARARGS, NATIVE,
                ABSTRACT, SYNTHETIC);
    }
}
