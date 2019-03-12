/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot.amd64;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.SnippetStub;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

/**
 * Stub called to support {@link Math}.
 */
public class AMD64MathStub extends SnippetStub {

    public AMD64MathStub(UnaryOperation operation, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetName(operation), options, providers, linkage);
    }

    public AMD64MathStub(BinaryOperation operation, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(snippetName(operation), options, providers, linkage);
    }

    private static String snippetName(UnaryOperation operation) {
        switch (operation) {
            case SIN:
                return "sin";
            case COS:
                return "cos";
            case TAN:
                return "tan";
            case EXP:
                return "exp";
            case LOG:
                return "log";
            case LOG10:
                return "log10";
            default:
                throw GraalError.shouldNotReachHere("Unknown operation " + operation);
        }
    }

    private static String snippetName(BinaryOperation operation) {
        if (operation == BinaryOperation.POW) {
            return "pow";
        }
        throw GraalError.shouldNotReachHere("Unknown operation " + operation);
    }

    @Snippet
    private static double log(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.LOG);
    }

    @Snippet
    private static double log10(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.LOG10);
    }

    @Snippet
    private static double sin(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.SIN);
    }

    @Snippet
    private static double cos(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.COS);
    }

    @Snippet
    private static double tan(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.TAN);
    }

    @Snippet
    private static double exp(double value) {
        return UnaryMathIntrinsicNode.compute(value, UnaryOperation.EXP);
    }

    @Snippet
    private static double pow(double value1, double value2) {
        return BinaryMathIntrinsicNode.compute(value1, value2, BinaryOperation.POW);
    }
}
