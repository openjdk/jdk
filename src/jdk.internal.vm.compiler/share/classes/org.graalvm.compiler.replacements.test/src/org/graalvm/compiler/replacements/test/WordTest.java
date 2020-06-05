/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.Builder;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.word.Word;
import jdk.internal.vm.compiler.word.Pointer;
import jdk.internal.vm.compiler.word.UnsignedWord;
import jdk.internal.vm.compiler.word.WordBase;
import jdk.internal.vm.compiler.word.WordFactory;
import org.junit.Test;

/**
 * Tests for the {@link Word} type.
 */
public class WordTest extends SnippetsTest {

    @Override
    protected StructuredGraph parse(Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        // create a copy to assign a valid compilation id
        DebugContext debug = getDebugContext();
        StructuredGraph originalGraph = installer.makeGraph(debug, bytecodeProvider, builder.getMethod(), null, null, false, null);
        return originalGraph.copyWithIdentifier(builder.getCompilationId(), debug);
    }

    @Test
    public void construction() {
        long[] words = new long[]{Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE, Integer.MAX_VALUE - 1L, Integer.MAX_VALUE, Integer.MAX_VALUE + 1L,
                        Integer.MIN_VALUE - 1L, Integer.MIN_VALUE, Integer.MIN_VALUE + 1L};
        for (long word : words) {
            test("unsignedLong", word);
            test("unsignedInt", (int) word);
            test("signedLong", word);
            test("signedInt", (int) word);
        }
    }

    @Test
    public void testArithmetic() {
        long[] words = new long[]{Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE, Integer.MAX_VALUE - 1L, Integer.MAX_VALUE, Integer.MAX_VALUE + 1L,
                        Integer.MIN_VALUE - 1L, Integer.MIN_VALUE, Integer.MIN_VALUE + 1L};
        for (long word : words) {
            test("unsignedNot", word);
            test("signedNot", word);
            for (long addend : words) {
                test("unsignedPlusInt", word, (int) addend);
                test("unsignedMinusInt", word, (int) addend);
                test("unsignedPlusInt", word, -((int) addend));
                test("unsignedMinusInt", word, -((int) addend));
                test("unsignedPlusLong", word, addend);
                test("unsignedMinusLong", word, addend);
                test("unsignedPlusLong", word, -addend);
                test("unsignedMinusLong", word, -addend);
                test("signedPlusInt", word, (int) addend);
                test("signedMinusInt", word, (int) addend);
                test("signedPlusInt", word, -((int) addend));
                test("signedMinusInt", word, -((int) addend));
                test("signedPlusLong", word, addend);
                test("signedMinusLong", word, addend);
                test("signedPlusLong", word, -addend);
                test("signedMinusLong", word, -addend);

                test("andInt", word, (int) addend);
                test("orInt", word, (int) addend);
                test("andInt", word, -((int) addend));
                test("orInt", word, -((int) addend));
                test("andLong", word, addend);
                test("orLong", word, addend);
                test("andLong", word, -addend);
                test("orLong", word, -addend);
            }
        }
    }

    @Test
    public void testCompare() {
        long[] words = new long[]{Long.MIN_VALUE, Long.MIN_VALUE + 1, -1L, 0L, 1L, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        for (long word1 : words) {
            for (long word2 : words) {
                for (String method : new String[]{"aboveOrEqual", "above", "belowOrEqual", "below"}) {
                    test(method, word1, word2);
                    test(method, word2, word1);
                }
            }
        }
    }

    @Test
    public void testCast() {
        test("cast", 1234L);
    }

    @Snippet
    public static long cast(long input) {
        WordBase base = WordFactory.signed(input);
        UnsignedWord unsigned = (UnsignedWord) base;
        Pointer pointer = (Pointer) unsigned;
        Word word = (Word) pointer;
        return word.rawValue();
    }

    @Snippet
    public static long unsignedLong(long word) {
        return WordFactory.unsigned(word).rawValue();
    }

    @Snippet
    public static long unsignedInt(int word) {
        return WordFactory.unsigned(word).rawValue();
    }

    @Snippet
    public static long signedLong(long word) {
        return WordFactory.signed(word).rawValue();
    }

    @Snippet
    public static long signedInt(int word) {
        return WordFactory.signed(word).rawValue();
    }

    @Snippet
    public static long unsignedPlusInt(long word, int addend) {
        return WordFactory.unsigned(word).add(addend).rawValue();
    }

    @Snippet
    public static long unsignedMinusInt(long word, int addend) {
        return WordFactory.unsigned(word).subtract(addend).rawValue();
    }

    @Snippet
    public static long unsignedPlusLong(long word, long addend) {
        return WordFactory.unsigned(word).add(WordFactory.unsigned(addend)).rawValue();
    }

    @Snippet
    public static long unsignedMinusLong(long word, long addend) {
        return WordFactory.unsigned(word).subtract(WordFactory.unsigned(addend)).rawValue();
    }

    @Snippet
    public static long signedPlusInt(long word, int addend) {
        return WordFactory.signed(word).add(addend).rawValue();
    }

    @Snippet
    public static long signedMinusInt(long word, int addend) {
        return WordFactory.signed(word).subtract(addend).rawValue();
    }

    @Snippet
    public static long signedPlusLong(long word, long addend) {
        return WordFactory.signed(word).add(WordFactory.signed(addend)).rawValue();
    }

    @Snippet
    public static long signedMinusLong(long word, long addend) {
        return WordFactory.signed(word).subtract(WordFactory.signed(addend)).rawValue();
    }

    @Snippet
    public static long signedNot(long word) {
        return WordFactory.signed(word).not().rawValue();
    }

    @Snippet
    public static long unsignedNot(long word) {
        return WordFactory.unsigned(word).not().rawValue();
    }

    @Snippet
    public static boolean aboveOrEqual(long word1, long word2) {
        return WordFactory.unsigned(word1).aboveOrEqual(WordFactory.unsigned(word2));
    }

    @Snippet
    public static boolean above(long word1, long word2) {
        return WordFactory.unsigned(word1).aboveThan(WordFactory.unsigned(word2));
    }

    @Snippet
    public static boolean belowOrEqual(long word1, long word2) {
        return WordFactory.unsigned(word1).belowOrEqual(WordFactory.unsigned(word2));
    }

    @Snippet
    public static boolean below(long word1, long word2) {
        return WordFactory.unsigned(word1).belowThan(WordFactory.unsigned(word2));
    }

    @Snippet
    public static long andInt(long word, int addend) {
        return WordFactory.unsigned(word).and(addend).rawValue();
    }

    @Snippet
    public static long orInt(long word, int addend) {
        return WordFactory.unsigned(word).or(addend).rawValue();
    }

    @Snippet
    public static long andLong(long word, long addend) {
        return WordFactory.unsigned(word).and(WordFactory.unsigned(addend)).rawValue();
    }

    @Snippet
    public static long orLong(long word, long addend) {
        return WordFactory.unsigned(word).or(WordFactory.unsigned(addend)).rawValue();
    }
}
