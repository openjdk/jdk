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
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
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
 * {@code @SuppressWarnings} annotation; see {@link LintCategory#annotationSuppression}.
 *
 * <p>
 * The meta-categories {@code "suppression-option"} and {@code "suppression"} warn about unnecessary
 * {@code -Xlint:-key} flags and {@code @SuppressWarnings} annotations (respectively), i.e., they warn
 * about explicit suppressions that don't actually suppress anything. In order for this calculation
 * to be correct, <i>code that generates a warning must execute even when the corresponding category
 * is disabled or suppressed</i>.
 *
 * <p>
 * To ensure this happens, code should use {@link #isActive} to determine whether to bother performing
 * a warning calculation (if the calculation is non-trivial), and it should use {@code Log.warning()}
 * to actually log any warnings found. Even if the warning is suppressed, {@code Log.warning()} will note
 * that any suppression in effect is actually doing something useful. This is termed the <i>validation</i>
 * of the suppression.
 *
 * <p>
 * Further details:
 * <ul>
 *  <li>To build an instance augmented with any new suppressions from {@code @SuppressWarnings} and/or
 *      {@code @Deprecated} annotations on a symbol declaration, use {@link #augment} to establish a
 *      new symbol "scope".
 *  <li>Any category for which {@link #isActive} returns true must be checked; this is true even if
 *      {@link #isEnabled} returns false and/or {@link #isSuppressed} returns true. Use of {@link #isActive}
 *      is optional; it simply allows you to skip unnecessary work. For trivial checks, it's not needed.
 *  <li>When a warnable condition is found, invoke {@code Log.warning()}. If the warning is suppressed,
 *      it won't actually be logged, but the category will still be validated. All lint warnings that would
 *      have been generated but aren't because of suppression must still validate the corresponding category.
 *  <li>You can manually check whether a category {@link #isEnabled} or {@link #isSuppressed}. These methods
 *      include a boolean parameter to optionally also validate any suppression of the category; <i>always
 *      do so if a warning will actually be generated if the method returns a certain value</i>.
 *  <li>If needed, you can validate suppressions manually via {@link #validateSuppression}.
 *  <li>The root {@link Lint} singleton initializes itself lazily, so it can be used safely during startup.
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
        EnumSet<LintCategory> suppressions = suppressionsFrom(sym, false);
        if (!suppressions.isEmpty()) {
            Lint lint = new Lint(this, sym);
            lint.enabled.removeAll(suppressions);
            lint.suppressed.addAll(suppressions);
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
        Lint l = new Lint(this, symbolInScope);
        l.enabled.addAll(Arrays.asList(lc));
        l.suppressed.removeAll(Arrays.asList(lc));
        return l;
    }

    /**
     * Returns a new Lint that has the given {@link LintCategory}s suppressed.
     *
     * @param lc one or more categories to be suppressed
     */
    public Lint suppress(LintCategory... lc) {
        Lint l = new Lint(this, symbolInScope);
        l.enabled.removeAll(Arrays.asList(lc));
        l.suppressed.addAll(Arrays.asList(lc));
        return l;
    }

    // Information shared by all Lint instances
    private static class Common {

        // Compiler context
        private final Context context;
        private final Log log;
        private final LintMapper lintMapper;

        private Symtab syms;                                // initialized lazily by initializeSymbolsIfNeeded()
        private Names names;                                // initialized lazily by initializeSymbolsIfNeeded()

        // "Xlint" suppression info
        private EnumSet<LintCategory> optionFlagSuppressions;   // categories for which a "-Xlint:-key" flag exists

        Common(Context context) {
            this.context = context;
            log = Log.instance(context);
            lintMapper = LintMapper.instance(context);
        }
    }

    // Shared state
    private final Common common;

    // The current symbol in scope (having @SuppressWarnings or @Deprecated), or null for global scope
    private final Symbol symbolInScope;

    // For the root instance only, these are initialized lazily by initializeRootIfNeeded()
    private EnumSet<LintCategory> enabled;              // categories enabled in this instance
    private EnumSet<LintCategory> suppressed;           // categories suppressed by augment() or suppress() (but not "-Xlint:-key")

    // LintCategory lookup by option string
    private static final Map<String, LintCategory> map = new ConcurrentHashMap<>(40);

    // Instantiate the root instance
    @SuppressWarnings("this-escape")
    protected Lint(Context context) {
        context.put(lintKey, this);
        common = new Common(context);
        symbolInScope = null;
    }

    // Instantiate a non-root ("symbol scoped") instance
    protected Lint(Lint other, Symbol symbolInScope) {
        Assert.check(symbolInScope != null);
        other.initializeRootIfNeeded();
        this.common = other.common;
        this.symbolInScope = symbolInScope;
        this.enabled = other.enabled.clone();
        this.suppressed = other.suppressed.clone();
    }

    // Process command line options on demand to allow use of root Lint early during startup
    private void initializeRootIfNeeded() {

        // Already initialized?
        if (enabled != null)
            return;

        // Initialize enabled categories based on "-Xlint" flags
        Options options = Options.instance(common.context);
        if (options.isSet(Option.XLINT) || options.isSet(Option.XLINT_CUSTOM, "all")) {
            // If -Xlint or -Xlint:all is given, enable all categories by default
            enabled = EnumSet.allOf(LintCategory.class);
        } else if (options.isSet(Option.XLINT_CUSTOM, "none")) {
            // if -Xlint:none is given, disable all categories by default
            enabled = LintCategory.newEmptySet();
        } else {
            // otherwise, enable on-by-default categories
            enabled = LintCategory.newEmptySet();

            Source source = Source.instance(common.context);
            if (source.compareTo(Source.JDK9) >= 0) {
                enabled.add(LintCategory.DEP_ANN);
            }
            if (Source.Feature.REDUNDANT_STRICTFP.allowedInSource(source)) {
                enabled.add(LintCategory.STRICTFP);
            }
            enabled.add(LintCategory.REQUIRES_TRANSITIVE_AUTOMATIC);
            enabled.add(LintCategory.OPENS);
            enabled.add(LintCategory.MODULE);
            enabled.add(LintCategory.REMOVAL);
            if (!options.isSet(Option.PREVIEW)) {
                enabled.add(LintCategory.PREVIEW);
            }
            enabled.add(LintCategory.SYNCHRONIZATION);
            enabled.add(LintCategory.INCUBATING);
        }

        // Look for specific overrides via "-Xlint" flags
        common.optionFlagSuppressions = LintCategory.newEmptySet();
        for (LintCategory lc : LintCategory.values()) {
            if (options.isSet(Option.XLINT_CUSTOM, lc.option)) {
                enabled.add(lc);
            } else if (options.isSet(Option.XLINT_CUSTOM, "-" + lc.option)) {
                common.optionFlagSuppressions.add(lc);
                enabled.remove(lc);
            }
        }

        // Categories suppressed by @SuppressWarnings is initially empty
        suppressed = LintCategory.newEmptySet();
    }

    @Override
    public String toString() {
        initializeRootIfNeeded();
        return "Lint["
          + (symbolInScope != null ? "sym=" + symbolInScope : "ROOT")
          + ",enable" + enabled
          + ",suppress" + suppressed
          + "]";
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
         * This category is not supported by {@code @SuppressWarnings}
         * and is not tracked for unnecessary suppression.
         */
        OPTIONS("options", false, false),

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
         * This category is not supported by {@code @SuppressWarnings}
         * and is not tracked for unnecessary suppression.
         */
        PATH("path", false, false),

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
         * Warn about recognized {@code @SuppressWarnings} lint categories that don't actually suppress any warnings.
         *
         * <p>
         * This category is not tracked for unnecessary suppression.
         */
        SUPPRESSION("suppression", true, false),

        /**
         * Warn about {@code -Xlint:-key} options that don't actually suppress any warnings (requires {@link #OPTIONS}).
         *
         * <p>
         * This category is not supported by {@code @SuppressWarnings}
         * and is not tracked for unnecessary suppression.
         */
        SUPPRESSION_OPTION("suppression-option", false, false),

        /**
         * Warn about synchronization attempts on instances of @ValueBased classes.
         */
        SYNCHRONIZATION("synchronization"),

        /**
         * Warn about issues relating to use of text blocks
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
         */
        PREVIEW("preview"),

        /**
         * Warn about use of restricted methods.
         */
        RESTRICTED("restricted");

        LintCategory(String option) {
            this(option, true);
        }

        LintCategory(String option, boolean annotationSuppression) {
            this(option, annotationSuppression, true);
        }

        LintCategory(String option, boolean annotationSuppression, boolean suppressionTracking) {
            this.option = option;
            this.annotationSuppression = annotationSuppression;
            this.suppressionTracking = suppressionTracking;
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

        /** Does this category support being suppressed by the {@code @SuppressWarnings} annotation? */
        public final boolean annotationSuppression;

        /** Do the {@code "suppression"} and {@code "suppression-option"} categories track suppressions in this category? */
        public final boolean suppressionTracking;
    }

    /**
     * Determine whether warnings in the given category should be calculated, because either
     * (a) the category is enabled, or (b) one of {@code "suppression"} or {@code "suppression-option"} is enabled.
     *
     * <p>
     * Use of this method is never required; it simply helps avoid potentially useless work.
     */
    public boolean isActive(LintCategory lc) {
        initializeRootIfNeeded();
        return enabled.contains(lc) || needsSuppressionTracking(lc);
    }

    /**
     * Checks if a warning category is enabled. A warning category may be enabled
     * on the command line, or by default, and can be temporarily disabled with
     * the {@code @SuppressWarnings} annotation.
     *
     * <p>
     * This method also optionally validates any warning suppressions currently in scope.
     * If you just want to know the configuration of this instance, set {@code validate} to false.
     * If you are using the result of this method to control whether a warning is actually
     * generated, then set {@code validate} to true to ensure that any suppression of the category
     * in scope is validated (i.e., determined to actually be suppressing something).
     *
     * @param lc lint category
     * @param validateSuppression true to also validate any suppression of the category
     */
    public boolean isEnabled(LintCategory lc, boolean validateSuppression) {
        initializeRootIfNeeded();
        if (validateSuppression)
            validateSuppression(lc);
        return enabled.contains(lc);
    }

    /**
     * Checks if a warning category has been specifically suppressed, by means of
     * {@code @SuppressWarnings}, {@code @Deprecated}, or {@link #suppress}.
     * Note: this does not detect suppressions via {@code -Xlint:-key} flags.
     *
     * <p>
     * This method also optionally validates any warning suppressions currently in scope.
     * If you just want to know the configuration of this instance, set {@code validate} to false.
     * If you are using the result of this method to control whether a warning is actually
     * generated, then set {@code validate} to true to ensure that any suppression of the category
     * in scope is validated (i.e., determined to actually be suppressing something).
     *
     * @param lc lint category
     * @param validateSuppression true to also validate any suppression of the category
     */
    public boolean isSuppressed(LintCategory lc, boolean validateSuppression) {
        initializeRootIfNeeded();
        if (validateSuppression)
            validateSuppression(lc);
        return suppressed.contains(lc);
    }

    /**
     * Get all categories for which a {@code -Xlint:-key} global suppression flag was specified.
     *
     * @return categories for which a {@code -Xlint:-key} flag was specified
     */
    public EnumSet<LintCategory> getOptionFlagSuppressions() {
        initializeRootIfNeeded();
        return EnumSet.copyOf(common.optionFlagSuppressions);
    }

    /**
     * Obtain the set of recognized lint warning categories suppressed at the given symbol's declaration.
     *
     * <p>
     * This set can be non-empty only if the symbol is annotated with either
     * {@code @SuppressWarnings} or {@code @Deprecated}.
     *
     * @param symbol symbol corresponding to a possibly-annotated declaration
     * @param includeAll true to include all categories, false to filter out those not supporting {@code @SuppressWarnings}
     * @return new warning suppressions applied to sym
     */
    public EnumSet<LintCategory> suppressionsFrom(Symbol symbol, boolean includeAll) {
        EnumSet<LintCategory> suppressions = suppressionsFrom(symbol.getDeclarationAttributes().stream(), includeAll);
        if (symbol.isDeprecated() && symbol.isDeprecatableViaAnnotation())
            suppressions.add(LintCategory.DEPRECATION);
        return suppressions;
    }

    /**
     * Retrieve the recognized lint categories suppressed by the given {@code @SuppressWarnings} annotation.
     *
     * @param annotation {@code @SuppressWarnings} annotation, or null
     * @param includeAll true to include all categories, false to filter out those not supporting {@code @SuppressWarnings}
     * @return set of lint categories, possibly empty but never null
     */
    public EnumSet<LintCategory> suppressionsFrom(JCAnnotation annotation, boolean includeAll) {
        initializeSymbolsIfNeeded();
        if (annotation == null)
            return LintCategory.newEmptySet();
        Assert.check(annotation.attribute.type.tsym == common.syms.suppressWarningsType.tsym);
        return suppressionsFrom(Stream.of(annotation).map(anno -> anno.attribute), includeAll);
    }

    /**
     * Retrieve the recognized lint categories suppressed by the {@code @SuppressWarnings} annotation in the stream, if any.
     *
     * @param attributes annotations
     * @param includeAll true to include all categories, false to filter out those not supporting {@code @SuppressWarnings}
     * @return set of lint categories, possibly empty but never null
     */
    public EnumSet<LintCategory> suppressionsFrom(Stream<Attribute.Compound> attributes, boolean includeAll) {
        initializeSymbolsIfNeeded();
        EnumSet<LintCategory> result = LintCategory.newEmptySet();
        attributes
          .filter(attribute -> attribute.type.tsym == common.syms.suppressWarningsType.tsym)
          .map(attribute -> suppressionsFrom(attribute, includeAll))
          .forEach(result::addAll);
        return result;
    }

    // Given a @SuppressWarnings annotation, extract the recognized suppressions
    private EnumSet<LintCategory> suppressionsFrom(Attribute.Compound suppressWarnings, boolean includeAll) {
        EnumSet<LintCategory> result = LintCategory.newEmptySet();
        Attribute.Array values = (Attribute.Array)suppressWarnings.member(common.names.value);
        for (Attribute value : values.values) {
            Optional.of((String)((Attribute.Constant)value).value)
              .flatMap(LintCategory::get)
              .filter(lc -> includeAll || lc.annotationSuppression)
              .ifPresent(result::add);
        }
        return result;
    }

    /**
     * Validate any suppression of the given category currently in scope.
     *
     * <p>
     * Such a suppression will therefore <b>not</b> be declared as unnecessary by the
     * {@code "suppression"} or {@code "suppression-option"} warnings.
     *
     * @param lc the lint category to be validated
     * @return this instance
     */
    public Lint validateSuppression(LintCategory lc) {
        if (needsSuppressionTracking(lc))
            common.lintMapper.validateSuppression(symbolInScope, lc);
        return this;
    }

    /**
     * Determine whether we should bother tracking suppression validation for the given lint category.
     *
     * <p>
     * We need to track validation of suppression of a lint category if:
     * <ul>
     *  <li>It's supported by {@code "suppression"} and {@code "suppression-option"} suppression tracking
     *  <li>One or both of {@code "suppression"} or {@code "suppression-option"} is currently enabled
     * </ul>
     */
    private boolean needsSuppressionTracking(LintCategory lc) {
        initializeRootIfNeeded();
        return lc.suppressionTracking &&
            (enabled.contains(LintCategory.SUPPRESSION) || enabled.contains(LintCategory.SUPPRESSION_OPTION));
    }

    private void initializeSymbolsIfNeeded() {
        if (common.syms == null) {
            common.syms = Symtab.instance(common.context);
            common.names = Names.instance(common.context);
        }
    }
}
