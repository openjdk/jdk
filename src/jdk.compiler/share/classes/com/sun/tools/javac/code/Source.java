/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;

import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;

import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.Fragments;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.Error;
import com.sun.tools.javac.util.JCDiagnostic.Fragment;

import static com.sun.tools.javac.main.Option.*;

/** The source language version accepted.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum Source {
    /** 1.0 had no inner classes, and so could not pass the JCK. */
    // public static final Source JDK1_0 =              new Source("1.0");

    /** 1.1 did not have strictfp, and so could not pass the JCK. */
    // public static final Source JDK1_1 =              new Source("1.1");

    /** 1.2 introduced strictfp. */
    JDK1_2("1.2"),

    /** 1.3 is the same language as 1.2. */
    JDK1_3("1.3"),

    /** 1.4 introduced assert. */
    JDK1_4("1.4"),

    /** 1.5 introduced generics, attributes, foreach, boxing, static import,
     *  covariant return, enums, varargs, et al. */
    JDK5("5"),

    /** 1.6 reports encoding problems as errors instead of warnings. */
    JDK6("6"),

    /** 1.7 introduced try-with-resources, multi-catch, string switch, etc. */
    JDK7("7"),

    /** 1.8 lambda expressions and default methods. */
    JDK8("8"),

    /** 1.9 modularity. */
    JDK9("9"),

    /** 1.10 local-variable type inference (var). */
    JDK10("10"),

    /** 1.11 local-variable syntax for lambda parameters */
    JDK11("11"),

    /** 12 covers the to be determined language features that will be added in JDK 12. */
    JDK12("12"),

    /** 13 covers the to be determined language features that will be added in JDK 13. */
    JDK13("13");

    private static final Context.Key<Source> sourceKey = new Context.Key<>();

    public static Source instance(Context context) {
        Source instance = context.get(sourceKey);
        if (instance == null) {
            Options options = Options.instance(context);
            String sourceString = options.get(SOURCE);
            if (sourceString != null) instance = lookup(sourceString);
            if (instance == null) instance = DEFAULT;
            context.put(sourceKey, instance);
        }
        return instance;
    }

    public final String name;

    private static final Map<String,Source> tab = new HashMap<>();
    static {
        for (Source s : values()) {
            tab.put(s.name, s);
        }
        tab.put("1.5", JDK5); // Make 5 an alias for 1.5
        tab.put("1.6", JDK6); // Make 6 an alias for 1.6
        tab.put("1.7", JDK7); // Make 7 an alias for 1.7
        tab.put("1.8", JDK8); // Make 8 an alias for 1.8
        tab.put("1.9", JDK9); // Make 9 an alias for 1.9
        tab.put("1.10", JDK10); // Make 10 an alias for 1.10
        // Decline to make 1.11 an alias for 11.
    }

    private Source(String name) {
        this.name = name;
    }

    public static final Source MIN = Source.JDK7;

    private static final Source MAX = values()[values().length - 1];

    public static final Source DEFAULT = MAX;

    public static Source lookup(String name) {
        return tab.get(name);
    }

    public boolean isSupported() {
        return this.compareTo(MIN) >= 0;
    }

    public Target requiredTarget() {
        if (this.compareTo(JDK13) >= 0) return Target.JDK1_13;
        if (this.compareTo(JDK12) >= 0) return Target.JDK1_12;
        if (this.compareTo(JDK11) >= 0) return Target.JDK1_11;
        if (this.compareTo(JDK10) >= 0) return Target.JDK1_10;
        if (this.compareTo(JDK9) >= 0) return Target.JDK1_9;
        if (this.compareTo(JDK8) >= 0) return Target.JDK1_8;
        if (this.compareTo(JDK7) >= 0) return Target.JDK1_7;
        if (this.compareTo(JDK6) >= 0) return Target.JDK1_6;
        if (this.compareTo(JDK5) >= 0) return Target.JDK1_5;
        if (this.compareTo(JDK1_4) >= 0) return Target.JDK1_4;
        return Target.JDK1_1;
    }

    /**
     * Models a feature of the Java programming language. Each feature can be associated with a
     * minimum source level, a maximum source level and a diagnostic fragment describing the feature,
     * which is used to generate error messages of the kind {@code feature XYZ not supported in source N}.
     */
    public enum Feature {

        DIAMOND(JDK7, Fragments.FeatureDiamond, DiagKind.NORMAL),
        MODULES(JDK9, Fragments.FeatureModules, DiagKind.PLURAL),
        EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES(JDK9, Fragments.FeatureVarInTryWithResources, DiagKind.PLURAL),
        DEPRECATION_ON_IMPORT(MIN, JDK8),
        POLY(JDK8),
        LAMBDA(JDK8, Fragments.FeatureLambda, DiagKind.PLURAL),
        METHOD_REFERENCES(JDK8, Fragments.FeatureMethodReferences, DiagKind.PLURAL),
        DEFAULT_METHODS(JDK8, Fragments.FeatureDefaultMethods, DiagKind.PLURAL),
        STATIC_INTERFACE_METHODS(JDK8, Fragments.FeatureStaticIntfMethods, DiagKind.PLURAL),
        STATIC_INTERFACE_METHODS_INVOKE(JDK8, Fragments.FeatureStaticIntfMethodInvoke, DiagKind.PLURAL),
        STRICT_METHOD_CLASH_CHECK(JDK8),
        EFFECTIVELY_FINAL_IN_INNER_CLASSES(JDK8),
        TYPE_ANNOTATIONS(JDK8, Fragments.FeatureTypeAnnotations, DiagKind.PLURAL),
        ANNOTATIONS_AFTER_TYPE_PARAMS(JDK8, Fragments.FeatureAnnotationsAfterTypeParams, DiagKind.PLURAL),
        REPEATED_ANNOTATIONS(JDK8, Fragments.FeatureRepeatableAnnotations, DiagKind.PLURAL),
        INTERSECTION_TYPES_IN_CAST(JDK8, Fragments.FeatureIntersectionTypesInCast, DiagKind.PLURAL),
        GRAPH_INFERENCE(JDK8),
        FUNCTIONAL_INTERFACE_MOST_SPECIFIC(JDK8),
        POST_APPLICABILITY_VARARGS_ACCESS_CHECK(JDK8),
        MAP_CAPTURES_TO_BOUNDS(MIN, JDK7),
        PRIVATE_SAFE_VARARGS(JDK9),
        DIAMOND_WITH_ANONYMOUS_CLASS_CREATION(JDK9, Fragments.FeatureDiamondAndAnonClass, DiagKind.NORMAL),
        UNDERSCORE_IDENTIFIER(MIN, JDK8),
        PRIVATE_INTERFACE_METHODS(JDK9, Fragments.FeaturePrivateIntfMethods, DiagKind.PLURAL),
        LOCAL_VARIABLE_TYPE_INFERENCE(JDK10),
        VAR_SYNTAX_IMPLICIT_LAMBDAS(JDK11, Fragments.FeatureVarSyntaxInImplicitLambda, DiagKind.PLURAL),
        IMPORT_ON_DEMAND_OBSERVABLE_PACKAGES(JDK1_2, JDK8),
        SWITCH_MULTIPLE_CASE_LABELS(JDK13, Fragments.FeatureMultipleCaseLabels, DiagKind.PLURAL),
        SWITCH_RULE(JDK13, Fragments.FeatureSwitchRules, DiagKind.PLURAL),
        SWITCH_EXPRESSION(JDK13, Fragments.FeatureSwitchExpressions, DiagKind.PLURAL),
        RAW_STRING_LITERALS(JDK13, Fragments.FeatureRawStringLiterals, DiagKind.PLURAL);

        enum DiagKind {
            NORMAL,
            PLURAL;
        }

        private final Source minLevel;
        private final Source maxLevel;
        private final Fragment optFragment;
        private final DiagKind optKind;

        Feature(Source minLevel) {
            this(minLevel, null, null);
        }

        Feature(Source minLevel, Fragment optFragment, DiagKind optKind) {
            this(minLevel, MAX, optFragment, optKind);
        }

        Feature(Source minLevel, Source maxLevel) {
            this(minLevel, maxLevel, null, null);
        }

        Feature(Source minLevel, Source maxLevel, Fragment optFragment, DiagKind optKind) {
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.optFragment = optFragment;
            this.optKind = optKind;
        }

        public boolean allowedInSource(Source source) {
            return source.compareTo(minLevel) >= 0 &&
                    source.compareTo(maxLevel) <= 0;
        }

        public boolean isPlural() {
            Assert.checkNonNull(optKind);
            return optKind == DiagKind.PLURAL;
        }

        public Fragment nameFragment() {
            Assert.checkNonNull(optFragment);
            return optFragment;
        }

        public Fragment fragment(String sourceName) {
            Assert.checkNonNull(optFragment);
            return optKind == DiagKind.NORMAL ?
                    Fragments.FeatureNotSupportedInSource(optFragment, sourceName, minLevel.name) :
                    Fragments.FeatureNotSupportedInSourcePlural(optFragment, sourceName, minLevel.name);
        }

        public Error error(String sourceName) {
            Assert.checkNonNull(optFragment);
            return optKind == DiagKind.NORMAL ?
                    Errors.FeatureNotSupportedInSource(optFragment, sourceName, minLevel.name) :
                    Errors.FeatureNotSupportedInSourcePlural(optFragment, sourceName, minLevel.name);
        }
    }

    public static SourceVersion toSourceVersion(Source source) {
        switch(source) {
        case JDK1_2:
            return RELEASE_2;
        case JDK1_3:
            return RELEASE_3;
        case JDK1_4:
            return RELEASE_4;
        case JDK5:
            return RELEASE_5;
        case JDK6:
            return RELEASE_6;
        case JDK7:
            return RELEASE_7;
        case JDK8:
            return RELEASE_8;
        case JDK9:
            return RELEASE_9;
        case JDK10:
            return RELEASE_10;
        case JDK11:
            return RELEASE_11;
        case JDK12:
            return RELEASE_12;
        case JDK13:
            return RELEASE_13;
        default:
            return null;
        }
    }
}
