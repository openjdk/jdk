/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.code.Lint.LintCategory.DEPRECATION;

/**
 * Utility class for calculating Lint category suppressions from annotations on a declaration.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LintSuppression {

    /** The context key for the LintSuppression object. */
    protected static final Context.Key<LintSuppression> lintSuppressionKey = new Context.Key<>();

    private final Context context;

    // These are initialized lazily to avoid dependency loops
    private Symtab syms;
    private Names names;

    /** Get the LintSuppression instance. */
    public static LintSuppression instance(Context context) {
        LintSuppression instance = context.get(lintSuppressionKey);
        if (instance == null)
            instance = new LintSuppression(context);
        return instance;
    }

    private LintSuppression(Context context) {
        this.context = context;
        context.put(lintSuppressionKey, this);
    }

    /**
     * Obtain the set of lint warning categories suppressed at the given symbol's declaration.
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
            suppressions.add(DEPRECATION);
        return suppressions;
    }

    /**
     * Retrieve the lint categories suppressed by the given @SuppressWarnings annotation.
     *
     * @param annotation @SuppressWarnings annotation, or null
     * @return set of lint categories, possibly empty but never null
     */
    private EnumSet<LintCategory> suppressionsFrom(JCAnnotation annotation) {
        initializeIfNeeded();
        if (annotation == null)
            return LintCategory.newEmptySet();
        Assert.check(annotation.attribute.type.tsym == syms.suppressWarningsType.tsym);
        return suppressionsFrom(Stream.of(annotation).map(anno -> anno.attribute));
    }

    // Find the @SuppressWarnings annotation in the attribute stream and extract the suppressions
    private EnumSet<LintCategory> suppressionsFrom(Stream<Attribute.Compound> attributes) {
        initializeIfNeeded();
        return attributes
          .filter(attribute -> attribute.type.tsym == syms.suppressWarningsType.tsym)
          .map(attribute -> attribute.member(names.value))
          .flatMap(attribute -> Stream.of(((Attribute.Array)attribute).values))
          .map(Attribute.Constant.class::cast)
          .map(elem -> elem.value)
          .map(String.class::cast)
          .map(LintCategory::get)
          .filter(Objects::nonNull)
          .collect(Collectors.toCollection(LintCategory::newEmptySet));
    }

    private void initializeIfNeeded() {
        if (syms == null) {
            syms = Symtab.instance(context);
            names = Names.instance(context);
        }
    }
}
