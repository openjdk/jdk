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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
 * A class for handling {@code -Xlint} suboptions and {@code @SuppressWarnings} annotations.
 *
 * <p>
 * Each lint category can be in one of three states: enabled, suppressed, or neither. The "neither"
 * state means it's effectively up to the code doing the check to determine the default behavior, by
 * warning when enabled (i.e., default suppressed) or not warning when suppressed (i.e., default enabled).
 * Some categories default to enabled; most default to neither and the code warns when enabled.
 *
 * <p>
 * A lint category can be explicitly enabled via the command line flag {@code -Xlint:key}, or explicitly
 * disabled via the command line flag {@code -Xlint:-key}. Some lint categories warn at specific
 * locations in the code and can be suppressed within the scope of a symbol declaration via the
 * {@code @SuppressWarnings} annotation.
 *
 * <p>
 * Further details:
 * <ul>
 *  <li>To build an instance augmented with any new suppressions from {@code @SuppressWarnings} and/or
 *      {@code @Deprecated} annotations on a symbol declaration, use {@link #augment} to establish a
 *      new symbol "scope".
 *  <li>You can manually check whether a category {@link #isEnabled} or {@link #isSuppressed};
 *      the convenience method {@link #logIfEnabled} includes a check for {@link #isEnabled}.
 *  <li>The root {@link Lint} singleton initializes itself lazily, so it can be used safely during
 *      compiler startup as long as {@link Options} has been initialized.
 * </ul>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Lint {

    /** The context key for the root {@link Lint} singleton. */
    protected static final Context.Key<Lint> lintKey = new Context.Key<>();

    /** Get the root {@link Lint} singleton. */
    public static Lint instance(Context context) {
        Lint instance = context.get(lintKey);
        if (instance == null)
            instance = new Lint(context);
        return instance;
    }

    /**
     * Obtain an instance with additional warning supression applied from any
     * {@code @SuppressWarnings} and/or {@code @Deprecated} annotations on the given symbol.
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
     * Returns a new Lint that has the given {@link LintCategory}s enabled.
     *
     * @param lc one or more categories to be enabled
     */
    public Lint enable(LintCategory... lc) {
        Lint l = new Lint(this);
        l.values.addAll(Arrays.asList(lc));
        l.suppressedValues.removeAll(Arrays.asList(lc));
        return l;
    }

    /**
     * Returns a new Lint that has the given {@link LintCategory}s suppressed.
     *
     * @param lc one or more categories to be suppressed
     */
    public Lint suppress(LintCategory... lc) {
        Lint l = new Lint(this);
        l.values.removeAll(Arrays.asList(lc));
        l.suppressedValues.addAll(Arrays.asList(lc));
        return l;
    }

    // Compiler context
    private final Context context;
    private final Options options;

    // These are initialized lazily to avoid dependency loops
    private Symtab syms;
    private Names names;

    // For the root instance only, these are initialized lazily
    private EnumSet<LintCategory> values;           // categories enabled by default or "-Xlint:key" and not (yet) suppressed
    private EnumSet<LintCategory> suppressedValues; // categories suppressed by augment() or suppress() (but not "-Xlint:-key")

    // LintCategory lookup by option string
    private static final Map<String, LintCategory> map = new ConcurrentHashMap<>(40);

    // Instantiate the root instance
    @SuppressWarnings("this-escape")
    protected Lint(Context context) {
        this.context = context;
        context.put(lintKey, this);
        options = Options.instance(context);
    }

    // Instantiate a non-root ("symbol scoped") instance
    protected Lint(Lint other) {
        other.initializeRootIfNeeded();
        this.context = other.context;
        this.options = other.options;
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
        if (options.isSet(Option.XLINT) || options.isSet(Option.XLINT_CUSTOM, "all")) {
            // If -Xlint or -Xlint:all is given, enable all categories by default
            values = EnumSet.allOf(LintCategory.class);
        } else if (options.isSet(Option.XLINT_CUSTOM, "none")) {
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
            values.add(LintCategory.SYNCHRONIZATION);
            values.add(LintCategory.INCUBATING);
        }

        // Look for specific overrides via "-Xlint" flags
        for (LintCategory lc : LintCategory.values()) {
            if (options.isSet(Option.XLINT_CUSTOM, lc.option)) {
                values.add(lc);
            } else if (options.isSet(Option.XLINT_CUSTOM, "-" + lc.option)) {
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
         * Warn when code refers to an auxiliary class that is hidden in a source file (i.e., the source file
         * name is different from the class name, and the type is not properly nested) and the referring code
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
        CLASSFILE("classfile"),

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
         * comment, but which do not have the {@code @Deprecated} annotation.
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
         * Warn about use of incubating modules.
         */
        INCUBATING("incubating"),

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
        OPTIONS("options"),

        /**
         * Warn when any output file is written to more than once.
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        OUTPUT_FILE_CLASH("output-file-clash"),

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
        PATH("path"),

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
         * Warn about synchronization attempts on instances of @ValueBased classes.
         */
        SYNCHRONIZATION("synchronization"),

        /**
         * Warn about issues relating to use of text blocks.
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        TEXT_BLOCKS("text-blocks"),

        /**
         * Warn about possible 'this' escapes before subclass instance is fully initialized.
         */
        THIS_ESCAPE("this-escape"),

        /**
         * Warn about issues relating to use of try blocks (i.e., try-with-resources).
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
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}.
         */
        PREVIEW("preview"),

        /**
         * Warn about use of restricted methods.
         */
        RESTRICTED("restricted");

        LintCategory(String option) {
            this.option = option;
            map.put(option, this);
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
         * Create a new, empty, mutable set of {@link LintCategory}.
         */
        public static EnumSet<LintCategory> newEmptySet() {
            return EnumSet.noneOf(LintCategory.class);
        }

        /** Get the string representing this category in {@code @SuppressWarnings} and {@code -Xlint:key} flags. */
        public final String option;
    }

    /**
     * Checks if a warning category is enabled. A warning category may be enabled
     * on the command line, or by default, and can be temporarily disabled with
     * the {@code @SuppressWarnings} annotation.
     *
     * @param lc lint category
     */
    public boolean isEnabled(LintCategory lc) {
        initializeRootIfNeeded();
        return values.contains(lc);
    }

    /**
     * Checks if a warning category has been specifically suppressed, by means of
     * {@code @SuppressWarnings}, {@code @Deprecated} annotations), or {@link #suppress}.
     * Note: this does not detect suppressions via {@code -Xlint:-key} flags.
     *
     * @param lc lint category
     */
    public boolean isSuppressed(LintCategory lc) {
        initializeRootIfNeeded();
        return suppressedValues.contains(lc);
    }

    /**
     * Helper method. Log a lint warning if its lint category is enabled.
     *
     * @param log warning destination
     * @param warning key for the localized warning message
     */
    public void logIfEnabled(Log log, LintWarning warning) {
        logIfEnabled(log, null, warning);
    }

    /**
     * Helper method. Log a lint warning if its lint category is enabled.
     *
     * @param log warning destination
     * @param pos source position at which to report the warning
     * @param warning key for the localized warning message
     */
    public void logIfEnabled(Log log, DiagnosticPosition pos, LintWarning warning) {
        if (isEnabled(warning.getLintCategory())) {
            log.warning(pos, warning);
        }
    }

    /**
     * Obtain the set of recognized lint warning categories suppressed at the given symbol's declaration.
     *
     * <p>
     * This set can be non-empty only if the symbol is annotated with either
     * {@code @SuppressWarnings} or {@code @Deprecated}.
     *
     * <p>
     * Note: The result may include categories that don't support suppression via {@code @SuppressWarnings}.
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

    /**
     * Retrieve the recognized lint categories suppressed by the given {@code @SuppressWarnings} annotation.
     *
     * <p>
     * Note: The result may include categories that don't support suppression via {@code @SuppressWarnings}.
     *
     * @param annotation {@code @SuppressWarnings} annotation, or null
     * @return set of lint categories, possibly empty but never null
     */
    private EnumSet<LintCategory> suppressionsFrom(JCAnnotation annotation) {
        initializeSymbolsIfNeeded();
        if (annotation == null)
            return LintCategory.newEmptySet();
        Assert.check(annotation.attribute.type.tsym == syms.suppressWarningsType.tsym);
        return suppressionsFrom(Stream.of(annotation).map(anno -> anno.attribute));
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
            Optional.of((String)((Attribute.Constant)value).value)
              .flatMap(LintCategory::get)
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
