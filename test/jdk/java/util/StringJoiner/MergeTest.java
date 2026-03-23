/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8017231 8020977 8054221
 * @summary test  StringJoiner::merge
 * @modules java.base/jdk.internal.util
 * @requires vm.bits == "64" & os.maxMemory > 4G
 * @run junit/othervm -Xmx4g -XX:+CompactStrings MergeTest
 */

import java.util.StringJoiner;
import java.util.stream.Stream;
import static jdk.internal.util.ArraysSupport.SOFT_MAX_ARRAY_LENGTH;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

public class MergeTest {
    private static final String[] PREFIXES = {"", "{", "@#$%"};
    private static final String[] SUFFIXES = {"", "}", "*&%$"};

    private static class Fixes {
        public String pre0, suf0;
        public String pre1, suf1;
        public Fixes(String prefix0, String suffix0,
                     String prefix1, String suffix1) {
            this.pre0 = prefix0;
            this.suf0 = suffix0;
            this.pre1 = prefix1;
            this.suf1 = suffix1;
        }
    }

    private static Stream<Fixes> fixesStream() {
        Stream.Builder<Fixes> builder = Stream.builder();
        for (final String prefix0 : PREFIXES) {
            for (final String suffix0 : SUFFIXES) {
                for (final String prefix1 : PREFIXES) {
                    for (final String suffix1 : SUFFIXES) {
                        builder.accept(new Fixes(prefix0, suffix0,
                                                 prefix1, suffix1));
                    }
                }
            }
        }
        return builder.build();
    }

    @Test
    public void testNull() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        Assertions.assertThrows(NullPointerException.class, () -> sj.merge(null));
    }

    @Test
    public void testSimple() {
        fixesStream().forEach(fixes -> {
            StringJoiner sj = new StringJoiner(",", fixes.pre0, fixes.suf0);
            StringJoiner other = new StringJoiner(",", fixes.pre1, fixes.suf1);
            Stream.of("a", "b", "c").forEachOrdered(sj::add);
            Stream.of("d", "e", "f").forEachOrdered(other::add);

            sj.merge(other);
            assertEquals(fixes.pre0 + "a,b,c,d,e,f" + fixes.suf0, sj.toString());
        });
    }

    @Test
    public void testEmptyOther() {
        fixesStream().forEach(fixes -> {
            StringJoiner sj = new StringJoiner(",", fixes.pre0, fixes.suf0);
            StringJoiner other = new StringJoiner(",", fixes.pre1, fixes.suf1);
            Stream.of("a", "b", "c").forEachOrdered(sj::add);

            sj.merge(other);
            assertEquals(fixes.pre0 + "a,b,c" + fixes.suf0, sj.toString());

            other.setEmptyValue("EMPTY");
            sj.merge(other);
            assertEquals(fixes.pre0 + "a,b,c" + fixes.suf0, sj.toString());
        });
    }

    @Test
    public void testEmptyThis() {
        fixesStream().forEach(fixes -> {
            StringJoiner sj = new StringJoiner(",", fixes.pre0, fixes.suf0);
            StringJoiner other = new StringJoiner(":", fixes.pre1, fixes.suf1);
            Stream.of("d", "e", "f").forEachOrdered(other::add);

            sj.merge(other);
            assertEquals(fixes.pre0 + "d:e:f" + fixes.suf0, sj.toString());

            sj = new StringJoiner(",", fixes.pre0, fixes.suf0).setEmptyValue("EMPTY");
            assertEquals("EMPTY", sj.toString());
            sj.merge(other);
            assertEquals(fixes.pre0 + "d:e:f" + fixes.suf0, sj.toString());
        });
    }

    @Test
    public void testEmptyBoth() {
        fixesStream().forEach(fixes -> {
            StringJoiner sj = new StringJoiner(",", fixes.pre0, fixes.suf0);
            StringJoiner other = new StringJoiner(":", fixes.pre1, fixes.suf1);

            sj.merge(other);
            assertEquals(fixes.pre0 + fixes.suf0, sj.toString());

            other.setEmptyValue("NOTHING");
            sj.merge(other);
            assertEquals(fixes.pre0 + fixes.suf0, sj.toString());

            sj = new StringJoiner(",", fixes.pre0, fixes.suf0).setEmptyValue("EMPTY");
            assertEquals("EMPTY", sj.toString());
            sj.merge(other);
            assertEquals("EMPTY", sj.toString());
        });
    }

    @Test
    public void testCascadeEmpty() {
        fixesStream().forEach(fixes -> {
            StringJoiner sj = new StringJoiner(",", fixes.pre0, fixes.suf0);
            StringJoiner o1 = new StringJoiner(":", fixes.pre1, fixes.suf1).setEmptyValue("Empty1");
            StringJoiner o2 = new StringJoiner(",", "<", ">").setEmptyValue("Empty2");

            o1.merge(o2);
            assertEquals("Empty1", o1.toString());

            sj.merge(o1);
            assertEquals(fixes.pre0 + fixes.suf0, sj.toString());
        });
    }

    @Test
    public void testDelimiter() {
        fixesStream().forEach(fixes -> {
            StringJoiner sj = new StringJoiner(",", fixes.pre0, fixes.suf0);
            StringJoiner other = new StringJoiner(":", fixes.pre1, fixes.suf1);
            Stream.of("a", "b", "c").forEachOrdered(sj::add);
            Stream.of("d", "e", "f").forEachOrdered(other::add);

            sj.merge(other);
            assertEquals(fixes.pre0 + "a,b,c,d:e:f" + fixes.suf0, sj.toString());
        });
    }

    @Test
    public void testMergeSelf() {
        fixesStream().forEach(fixes -> {
            final StringJoiner sj = new StringJoiner(",", fixes.pre0, fixes.suf0).add("a").add("b");
            assertEquals(fixes.pre0 + "a,b,a,b" + fixes.suf0, sj.merge(sj).toString());
            assertEquals(fixes.pre0 + "a,b,a,b,a,b,a,b" + fixes.suf0, sj.merge(sj).toString());
        });
    }

    @Test
    public void OOM() {
        String maxString = "*".repeat(SOFT_MAX_ARRAY_LENGTH);

        try {
            StringJoiner sj1 = new StringJoiner("", "", "");
            sj1.add(maxString);
            StringJoiner sj2 = new StringJoiner("", "", "");
            sj2.add(maxString);
            sj1.merge(sj2);
            fail("Should have thrown OutOfMemoryError");
        } catch (OutOfMemoryError ex) {
            // okay
        }
    }
}
