/*
 * Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.JCDiagnostic.LintWarning;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

/**
 * A class for handling -Xlint suboptions and @SuppressWarnings.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Lint {

    /** The context key for the root Lint object. */
    protected static final Context.Key<Lint> lintKey = new Context.Key<>();

    /** Get the root Lint instance. */
    public static Lint instance(Context context) {
        Lint instance = context.get(lintKey);
        if (instance == null)
            instance = new Lint(context);
        return instance;
    }

    /**
     * Obtain an instance with additional warning supression applied from any
     * @SuppressWarnings and/or @Deprecated annotations on the given symbol.
     *
     * <p>
     * The returned instance will be different from this instance if and only if
     * {@link #suppressionsFrom} returns a non-empty set.
     *
     * @param sym symbol
     * @return lint instance with new warning suppressions applied, or this instance if none
     */
    public Lint augment(Symbol sym) {
        EnumSet<LintCategory> suppressions = suppressionsFrom(sym);
        if (!suppressions.isEmpty()) {
            Lint lint = new Lint(this);
            lint.values.removeAll(suppressions);
            lint.suppressedValues.addAll(suppressions);
            return lint;
        }
        return this;
    }

    /**
     * Returns a new Lint that has the given LintCategorys enabled.
     * @param lc one or more categories to be enabled
     */
    public Lint enable(LintCategory... lc) {
        Lint l = new Lint(this);
        l.values.addAll(Arrays.asList(lc));
        l.suppressedValues.removeAll(Arrays.asList(lc));
        return l;
    }

    /**
     * Returns a new Lint that has the given LintCategorys suppressed.
     * @param lc one or more categories to be suppressed
     */
    public Lint suppress(LintCategory... lc) {
        Lint l = new Lint(this);
        l.values.removeAll(Arrays.asList(lc));
        l.suppressedValues.addAll(Arrays.asList(lc));
        return l;
    }

    private final Context context;
    private final Options options;
    private final Log log;

    // These are initialized lazily to avoid dependency loops
    private Symtab syms;
    private Names names;

    // Invariant: it's never the case that a category is in both "values" and "suppressedValues"
    private EnumSet<LintCategory> values;
    private EnumSet<LintCategory> suppressedValues;

    private static final Map<String, LintCategory> map = new LinkedHashMap<>(40);

    @SuppressWarnings("this-escape")
    protected Lint(Context context) {
        this.context = context;
        context.put(lintKey, this);
        options = Options.instance(context);
        log = Log.instance(context);
    }

    // Instantiate a non-root ("symbol scoped") instance
    protected Lint(Lint other) {
        other.initializeRootIfNeeded();
        this.context = other.context;
        this.options = other.options;
        this.log = other.log;
        this.syms = other.syms;
        this.names = other.names;
        this.values = other.values.clone();
        this.suppressedValues = other.suppressedValues.clone();
    }

    // Process command line options on demand to allow use of root Lint early during startup
    private void initializeRootIfNeeded() {

        // Already initialized?
        if (values != null)
            return;

        // Initialize enabled categories based on "-Xlint" flags
        if (options.isSet(Option.XLINT) || options.isSet(Option.XLINT_CUSTOM, Option.LINT_CUSTOM_ALL)) {
            // If -Xlint or -Xlint:all is given, enable all categories by default
            values = EnumSet.allOf(LintCategory.class);
        } else if (options.isSet(Option.XLINT_CUSTOM, Option.LINT_CUSTOM_NONE)) {
            // if -Xlint:none is given, disable all categories by default
            values = LintCategory.newEmptySet();
        } else {
            // otherwise, enable on-by-default categories
            values = LintCategory.newEmptySet();

            Source source = Source.instance(context);
            if (source.compareTo(Source.JDK9) >= 0) {
                values.add(LintCategory.DEP_ANN);
            }
            if (Source.Feature.REDUNDANT_STRICTFP.allowedInSource(source)) {
                values.add(LintCategory.STRICTFP);
            }
            values.add(LintCategory.REQUIRES_TRANSITIVE_AUTOMATIC);
            values.add(LintCategory.OPENS);
            values.add(LintCategory.MODULE);
            values.add(LintCategory.REMOVAL);
            if (!options.isSet(Option.PREVIEW)) {
                values.add(LintCategory.PREVIEW);
            }
            values.add(LintCategory.IDENTITY);
            values.add(LintCategory.INCUBATING);
        }

        // Look for specific overrides
        for (LintCategory lc : LintCategory.values()) {
            if (options.isLintExplicitlyEnabled(lc)) {
                values.add(lc);
            } else if (options.isLintExplicitlyDisabled(lc)) {
                values.remove(lc);
            }
        }

        suppressedValues = LintCategory.newEmptySet();
    }

    @Override
    public String toString() {
        initializeRootIfNeeded();
        return "Lint:[enable" + values + ",suppress" + suppressedValues + "]";
    }

    /**
     * Categories of warnings that can be generated by the compiler.
     */
    public enum LintCategory {
        /**
         * Warn when code refers to a auxiliary class that is hidden in a source file (ie source file name is
         * different from the class name, and the type is not properly nested) and the referring code
         * is not located in the same source file.
         */
        AUXILIARYCLASS("auxiliaryclass"),

        /**
         * Warn about use of unnecessary casts.
         */
        CAST("cast"),

        /**
         * Warn about issues related to classfile contents.
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        CLASSFILE("classfile", false),

        /**
         * Warn about "dangling" documentation comments,
         * not attached to any declaration.
         */
        DANGLING_DOC_COMMENTS("dangling-doc-comments"),

        /**
         * Warn about use of deprecated items.
         */
        DEPRECATION("deprecation"),

        /**
         * Warn about items which are documented with an {@code @deprecated} JavaDoc
         * comment, but which do not have {@code @Deprecated} annotation.
         */
        DEP_ANN("dep-ann"),

        /**
         * Warn about division by constant integer 0.
         */
        DIVZERO("divzero"),

        /**
         * Warn about empty statement after if.
         */
        EMPTY("empty"),

        /**
         * Warn about issues regarding module exports.
         */
        EXPORTS("exports"),

        /**
         * Warn about falling through from one case of a switch statement to the next.
         */
        FALLTHROUGH("fallthrough"),

        /**
         * Warn about finally clauses that do not terminate normally.
         */
        FINALLY("finally"),

        /**
         * Warn about uses of @ValueBased classes where an identity class is expected.
         */
        IDENTITY("identity", true, "synchronization"),

        /**
         * Warn about use of incubating modules.
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        INCUBATING("incubating", false),

        /**
          * Warn about compiler possible lossy conversions.
          */
        LOSSY_CONVERSIONS("lossy-conversions"),

        /**
          * Warn about compiler generation of a default constructor.
          */
        MISSING_EXPLICIT_CTOR("missing-explicit-ctor"),

        /**
         * Warn about module system related issues.
         */
        MODULE("module"),

        /**
         * Warn about issues regarding module opens.
         */
        OPENS("opens"),

        /**
         * Warn about issues relating to use of command line options.
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        OPTIONS("options", false),

        /**
         * Warn when any output file is written to more than once.
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        OUTPUT_FILE_CLASH("output-file-clash", false),

        /**
         * Warn about issues regarding method overloads.
         */
        OVERLOADS("overloads"),

        /**
         * Warn about issues regarding method overrides.
         */
        OVERRIDES("overrides"),

        /**
         * Warn about invalid path elements on the command line.
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        PATH("path", false),

        /**
         * Warn about issues regarding annotation processing.
         */
        PROCESSING("processing"),

        /**
         * Warn about unchecked operations on raw types.
         */
        RAW("rawtypes"),

        /**
         * Warn about use of deprecated-for-removal items.
         */
        REMOVAL("removal"),

        /**
         * Warn about use of automatic modules in the requires clauses.
         */
        REQUIRES_AUTOMATIC("requires-automatic"),

        /**
         * Warn about automatic modules in requires transitive.
         */
        REQUIRES_TRANSITIVE_AUTOMATIC("requires-transitive-automatic"),

        /**
         * Warn about Serializable classes that do not provide a serial version ID.
         */
        SERIAL("serial"),

        /**
         * Warn about issues relating to use of statics
         */
        STATIC("static"),

        /**
         * Warn about unnecessary uses of the strictfp modifier
         */
        STRICTFP("strictfp"),

        /**
         * Warn about issues relating to use of text blocks
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings} (yet - see JDK-8224228).
         */
        TEXT_BLOCKS("text-blocks", false),

        /**
         * Warn about possible 'this' escapes before subclass instance is fully initialized.
         */
        THIS_ESCAPE("this-escape"),

        /**
         * Warn about issues relating to use of try blocks (i.e. try-with-resources)
         */
        TRY("try"),

        /**
         * Warn about unchecked operations on raw types.
         */
        UNCHECKED("unchecked"),

        /**
         * Warn about potentially unsafe vararg methods
         */
        VARARGS("varargs"),

        /**
         * Warn about use of preview features.
         */
        PREVIEW("preview"),

        /**
         * Warn about use of restricted methods.
         */
        RESTRICTED("restricted");

        LintCategory(String option) {
            this(option, true);
        }

        LintCategory(String option, boolean annotationSuppression, String... aliases) {
            this.option = option;
            this.annotationSuppression = annotationSuppression;
            ArrayList<String> optionList = new ArrayList<>(1 + aliases.length);
            optionList.add(option);
            Collections.addAll(optionList, aliases);
            this.optionList = Collections.unmodifiableList(optionList);
            this.optionList.forEach(ident -> map.put(ident, this));
        }

        /**
         * Get the {@link LintCategory} having the given command line option.
         *
         * @param option lint category option string
         * @return corresponding {@link LintCategory}, or empty if none exists
         */
        public static Optional<LintCategory> get(String option) {
            return Optional.ofNullable(map.get(option));
        }

        /**
         * Get all lint category option strings and aliases.
         */
        public static Set<String> options() {
            return Collections.unmodifiableSet(map.keySet());
        }

        public static EnumSet<LintCategory> newEmptySet() {
            return EnumSet.noneOf(LintCategory.class);
        }

        /** Get the "canonical" string representing this category in @SuppressAnnotations and -Xlint options. */
        public final String option;

        /** Get a list containing "option" followed by zero or more aliases. */
        public final List<String> optionList;

        /** Does this category support being suppressed by the {@code @SuppressWarnings} annotation? */
        public final boolean annotationSuppression;
    }

    /**
     * Checks if a warning category is enabled. A warning category may be enabled
     * on the command line, or by default, and can be temporarily disabled with
     * the SuppressWarnings annotation.
     */
    public boolean isEnabled(LintCategory lc) {
        initializeRootIfNeeded();
        return values.contains(lc);
    }

    /**
     * Checks is a warning category has been specifically suppressed, by means
     * of the SuppressWarnings annotation, or, in the case of the deprecated
     * category, whether it has been implicitly suppressed by virtue of the
     * current entity being itself deprecated.
     */
    public boolean isSuppressed(LintCategory lc) {
        initializeRootIfNeeded();
        return suppressedValues.contains(lc);
    }

    /**
     * Helper method. Log a lint warning if its lint category is enabled.
     *
     * @param warning key for the localized warning message
     */
    public void logIfEnabled(LintWarning warning) {
        logIfEnabled(null, warning);
    }

    /**
     * Helper method. Log a lint warning if its lint category is enabled.
     *
     * @param pos source position at which to report the warning
     * @param warning key for the localized warning message
     */
    public void logIfEnabled(DiagnosticPosition pos, LintWarning warning) {
        if (isEnabled(warning.getLintCategory())) {
            log.warning(pos, warning);
        }
    }

    /**
     * Obtain the set of recognized lint warning categories suppressed at the given symbol's declaration.
     *
     * <p>
     * This set can be non-empty only if the symbol is annotated with either
     * @SuppressWarnings or @Deprecated.
     *
     * @param symbol symbol corresponding to a possibly-annotated declaration
     * @return new warning suppressions applied to sym
     */
    public EnumSet<LintCategory> suppressionsFrom(Symbol symbol) {
        EnumSet<LintCategory> suppressions = suppressionsFrom(symbol.getDeclarationAttributes().stream());
        if (symbol.isDeprecated() && symbol.isDeprecatableViaAnnotation())
            suppressions.add(LintCategory.DEPRECATION);
        return suppressions;
    }

    // Find the @SuppressWarnings annotation in the given stream and extract the recognized suppressions
    private EnumSet<LintCategory> suppressionsFrom(Stream<Attribute.Compound> attributes) {
        initializeSymbolsIfNeeded();
        EnumSet<LintCategory> result = LintCategory.newEmptySet();
        attributes
          .filter(attribute -> attribute.type.tsym == syms.suppressWarningsType.tsym)
          .map(this::suppressionsFrom)
          .forEach(result::addAll);
        return result;
    }

    // Given a @SuppressWarnings annotation, extract the recognized suppressions
    private EnumSet<LintCategory> suppressionsFrom(Attribute.Compound suppressWarnings) {
        EnumSet<LintCategory> result = LintCategory.newEmptySet();
        Attribute.Array values = (Attribute.Array)suppressWarnings.member(names.value);
        for (Attribute value : values.values) {
            Optional.of(value)
              .filter(val -> val instanceof Attribute.Constant)
              .map(val -> (String) ((Attribute.Constant) val).value)
              .flatMap(LintCategory::get)
              .filter(lc -> lc.annotationSuppression)
              .ifPresent(result::add);
        }
        return result;
    }

    private void initializeSymbolsIfNeeded() {
        if (syms == null) {
            syms = Symtab.instance(context);
            names = Names.instance(context);
        }
    }
}
