/*
 * Copyright (c) 2002, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.jvm;

import java.util.*;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.*;

/** The classfile version target.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum Target {
    JDK1_1("1.1", 45, 3),
    JDK1_2("1.2", 46, 0),
    JDK1_3("1.3", 47, 0),

    /** J2SE1.4 = Merlin. */
    JDK1_4("1.4", 48, 0),

    /** Support for the JSR14 prototype compiler (targeting 1.4 VMs
     *  augmented with a few support classes).  This is a transitional
     *  option that will not be supported in the product.  */
    JSR14("jsr14", 48, 0),

    /** The following are undocumented transitional targets that we
     *  had used to test VM fixes in update releases.  We do not
     *  promise to retain support for them.  */
    JDK1_4_1("1.4.1", 48, 0),
    JDK1_4_2("1.4.2", 48, 0),

    /** Tiger. */
    JDK1_5("1.5", 49, 0),

    /** JDK 6. */
    JDK1_6("1.6", 50, 0),

    /** JDK 7. */
    JDK1_7("1.7", 51, 0);

    private static final Context.Key<Target> targetKey =
        new Context.Key<Target>();

    public static Target instance(Context context) {
        Target instance = context.get(targetKey);
        if (instance == null) {
            Options options = Options.instance(context);
            String targetString = options.get("-target");
            if (targetString != null) instance = lookup(targetString);
            if (instance == null) instance = DEFAULT;
            context.put(targetKey, instance);
        }
        return instance;
    }

    private static Target MIN;
    public static Target MIN() { return MIN; }

    private static Target MAX;
    public static Target MAX() { return MAX; }

    private static Map<String,Target> tab = new HashMap<String,Target>();
    static {
        for (Target t : values()) {
            if (MIN == null) MIN = t;
            MAX = t;
            tab.put(t.name, t);
        }
        tab.put("5", JDK1_5);
        tab.put("6", JDK1_6);
        tab.put("7", JDK1_7);
    }

    public final String name;
    public final int majorVersion;
    public final int minorVersion;
    private Target(String name, int majorVersion, int minorVersion) {
        this.name = name;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public static final Target DEFAULT = JDK1_7;

    public static Target lookup(String name) {
        return tab.get(name);
    }

    /** In -target 1.1 and earlier, the compiler is required to emit
     *  synthetic method definitions in abstract classes for interface
     *  methods that are not overridden.  We call them "Miranda" methods.
     */
    public boolean requiresIproxy() {
        return compareTo(JDK1_1) <= 0;
    }

    /** Beginning in 1.4, we take advantage of the possibility of emitting
     *  code to initialize fields before calling the superclass constructor.
     *  This is allowed by the VM spec, but the verifier refused to allow
     *  it until 1.4.  This is necesary to translate some code involving
     *  inner classes.  See, for example, 4030374.
     */
    public boolean initializeFieldsBeforeSuper() {
        return compareTo(JDK1_4) >= 0;
    }

    /** Beginning with -target 1.2 we obey the JLS rules for binary
     *  compatibility, emitting as the qualifying type of a reference
     *  to a method or field the type of the qualifier.  In earlier
     *  targets we use as the qualifying type the class in which the
     *  member was found.  The following methods named
     *  *binaryCompatibility() indicate places where we vary from this
     *  general rule. */
    public boolean obeyBinaryCompatibility() {
        return compareTo(JDK1_2) >= 0;
    }

    /** Starting in 1.5, the compiler uses an array type as
     *  the qualifier for method calls (such as clone) where required by
     *  the language and VM spec.  Earlier versions of the compiler
     *  qualified them by Object.
     */
    public boolean arrayBinaryCompatibility() {
        return compareTo(JDK1_5) >= 0;
    }

    /** Beginning after 1.2, we follow the binary compatibility rules for
     *  interface fields.  The 1.2 VMs had bugs handling interface fields
     *  when compiled using binary compatibility (see 4400598), so this is
     *  an accommodation to them.
     */
    public boolean interfaceFieldsBinaryCompatibility() {
        return compareTo(JDK1_2) > 0;
    }

    /** Beginning in -target 1.5, we follow the binary compatibility
     *  rules for interface methods that redefine Object methods.
     *  Earlier VMs had bugs handling such methods compiled using binary
     *  compatibility (see 4392595, 4398791, 4392595, 4400415).
     *  The VMs were fixed during or soon after 1.4.  See 4392595.
     */
    public boolean interfaceObjectOverridesBinaryCompatibility() {
        return compareTo(JDK1_5) >= 0;
    }

    /** Beginning in -target 1.4.2, we make synthetic variables
     *  package-private instead of private.  This is to prevent the
     *  necessity of access methods, which effectively relax the
     *  protection of the field but bloat the class files and affect
     *  execution.
     */
    public boolean usePrivateSyntheticFields() {
        return compareTo(JDK1_4_2) < 0;
    }

    /** Sometimes we need to create a field to cache a value like a
     *  class literal of the assertions flag.  In -target 1.4.2 and
     *  later we create a new synthetic class for this instead of
     *  using the outermost class.  See 4401576.
     */
    public boolean useInnerCacheClass() {
        return compareTo(JDK1_4_2) >= 0;
    }

    /** Return true if cldc-style stack maps need to be generated. */
    public boolean generateCLDCStackmap() {
        return false;
    }

    /** Beginning in -target 6, we generate stackmap attribute in
     *  compact format. */
    public boolean generateStackMapTable() {
        return compareTo(JDK1_6) >= 0;
    }

    /** Beginning in -target 6, package-info classes are marked synthetic.
     */
    public boolean isPackageInfoSynthetic() {
        return compareTo(JDK1_6) >= 0;
    }

    /** Do we generate "empty" stackmap slots after double and long?
     */
    public boolean generateEmptyAfterBig() {
        return false;
    }

    /** Beginning in 1.5, we have an unsynchronized version of
     *  StringBuffer called StringBuilder that can be used by the
     *  compiler for string concatenation.
     */
    public boolean useStringBuilder() {
        return compareTo(JDK1_5) >= 0;
    }

    /** Beginning in 1.5, we have flag bits we can use instead of
     *  marker attributes.
     */
    public boolean useSyntheticFlag() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean useEnumFlag() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean useAnnotationFlag() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean useVarargsFlag() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean useBridgeFlag() {
        return compareTo(JDK1_5) >= 0;
    }

    /** Return the character to be used in constructing synthetic
     *  identifiers, where not specified by the JLS.
     */
    public char syntheticNameChar() {
        return '$';
    }

    /** Does the VM have direct support for class literals?
     */
    public boolean hasClassLiterals() {
        return compareTo(JDK1_5) >= 0;
    }

    /** Does the VM support an invokedynamic instruction?
     */
    public boolean hasInvokedynamic() {
        return compareTo(JDK1_7) >= 0;
    }

    /** Although we may not have support for class literals, should we
     *  avoid initializing the class that the literal refers to?
     *  See 4468823
     */
    public boolean classLiteralsNoInit() {
        return compareTo(JDK1_4_2) >= 0;
    }

    /** Although we may not have support for class literals, when we
     *  throw a NoClassDefFoundError, should we initialize its cause?
     */
    public boolean hasInitCause() {
        return compareTo(JDK1_4) >= 0;
    }

    /** For bootstrapping, we use J2SE1.4's wrapper class constructors
     *  to implement boxing.
     */
    public boolean boxWithConstructors() {
        return compareTo(JDK1_5) < 0;
    }

    /** For bootstrapping, we use J2SE1.4's java.util.Collection
     *  instead of java.lang.Iterable.
     */
    public boolean hasIterable() {
        return compareTo(JDK1_5) >= 0;
    }

    /** For bootstrapping javac only, we do without java.lang.Enum if
     *  necessary.
     */
    public boolean compilerBootstrap(Symbol c) {
        return
            this == JSR14 &&
            (c.flags() & Flags.ENUM) != 0 &&
            c.flatName().toString().startsWith("com.sun.tools.")
            // && !Target.class.getSuperclass().getName().equals("java.lang.Enum")
            ;
    }

    /** In J2SE1.5.0, we introduced the "EnclosingMethod" attribute
     *  for improved reflection support.
     */
    public boolean hasEnclosingMethodAttribute() {
        return compareTo(JDK1_5) >= 0 || this == JSR14;
    }
}
