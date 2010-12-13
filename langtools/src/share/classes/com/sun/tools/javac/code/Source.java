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

package com.sun.tools.javac.code;

import java.util.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.jvm.Target;

import static com.sun.tools.javac.main.OptionName.*;

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
    JDK1_5("1.5"),

    /** 1.6 reports encoding problems as errors instead of warnings. */
    JDK1_6("1.6"),

    /** 1.7 covers the to be determined language features that will be added in JDK 7. */
    JDK1_7("1.7");

    private static final Context.Key<Source> sourceKey
        = new Context.Key<Source>();

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

    private static Map<String,Source> tab = new HashMap<String,Source>();
    static {
        for (Source s : values()) {
            tab.put(s.name, s);
        }
        tab.put("5", JDK1_5); // Make 5 an alias for 1.5
        tab.put("6", JDK1_6); // Make 6 an alias for 1.6
        tab.put("7", JDK1_7); // Make 7 an alias for 1.7
    }

    private Source(String name) {
        this.name = name;
    }

    public static final Source DEFAULT = JDK1_7;

    public static Source lookup(String name) {
        return tab.get(name);
    }

    public Target requiredTarget() {
        if (this.compareTo(JDK1_7) >= 0) return Target.JDK1_7;
        if (this.compareTo(JDK1_6) >= 0) return Target.JDK1_6;
        if (this.compareTo(JDK1_5) >= 0) return Target.JDK1_5;
        if (this.compareTo(JDK1_4) >= 0) return Target.JDK1_4;
        return Target.JDK1_1;
    }

    /** Allow encoding errors, giving only warnings. */
    public boolean allowEncodingErrors() {
        return compareTo(JDK1_6) < 0;
    }
    public boolean allowAsserts() {
        return compareTo(JDK1_4) >= 0;
    }
    public boolean allowCovariantReturns() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowGenerics() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowDiamond() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowMulticatch() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowEnums() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowForeach() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowStaticImport() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowBoxing() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowVarargs() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowAnnotations() {
        return compareTo(JDK1_5) >= 0;
    }
    // hex floating-point literals supported?
    public boolean allowHexFloats() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowAnonOuterThis() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean addBridges() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean enforceMandatoryWarnings() {
        return compareTo(JDK1_5) >= 0;
    }
    public boolean allowTryWithResources() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowTypeAnnotations() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowBinaryLiterals() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowUnderscoresInLiterals() {
        return compareTo(JDK1_7) >= 0;
    }
    public boolean allowStringsInSwitch() {
        return compareTo(JDK1_7) >= 0;
    }
    public static SourceVersion toSourceVersion(Source source) {
        switch(source) {
        case JDK1_2:
            return RELEASE_2;
        case JDK1_3:
            return RELEASE_3;
        case JDK1_4:
            return RELEASE_4;
        case JDK1_5:
            return RELEASE_5;
        case JDK1_6:
            return RELEASE_6;
        case JDK1_7:
            return RELEASE_7;
        default:
            return null;
        }
    }
}
