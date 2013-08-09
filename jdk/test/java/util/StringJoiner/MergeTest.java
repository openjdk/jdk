/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8017231 8020977
 * @summary test  StringJoiner::merge
 * @run testng MergeTest
 */

import java.util.StringJoiner;
import java.util.stream.Stream;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test
public class MergeTest {
    public void testNull() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        try {
            sj.merge(null);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException npe) {
            // expected
        }
    }

    public void testSimple() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        StringJoiner other = new StringJoiner(",", "[", "]");
        Stream.of("a", "b", "c").forEachOrdered(sj::add);
        Stream.of("d", "e", "f").forEachOrdered(other::add);

        sj.merge(other);
        assertEquals(sj.toString(), "{a,b,c,d,e,f}");
    }

    public void testEmptyOther() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        StringJoiner other = new StringJoiner(",", "[", "]");
        Stream.of("a", "b", "c").forEachOrdered(sj::add);

        sj.merge(other);
        assertEquals(sj.toString(), "{a,b,c}");

        other.setEmptyValue("EMPTY");
        sj.merge(other);
        assertEquals(sj.toString(), "{a,b,c}");
    }

    public void testEmptyThis() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        StringJoiner other = new StringJoiner(":", "[", "]");
        Stream.of("d", "e", "f").forEachOrdered(other::add);

        sj.merge(other);
        assertEquals(sj.toString(), "{d:e:f}");

        sj = new StringJoiner(",", "{", "}").setEmptyValue("EMPTY");
        assertEquals(sj.toString(), "EMPTY");
        sj.merge(other);
        assertEquals(sj.toString(), "{d:e:f}");
    }

    public void testEmptyBoth() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        StringJoiner other = new StringJoiner(":", "[", "]");

        sj.merge(other);
        assertEquals(sj.toString(), "{}");

        other.setEmptyValue("NOTHING");
        sj.merge(other);
        assertEquals(sj.toString(), "{}");

        sj = new StringJoiner(",", "{", "}").setEmptyValue("EMPTY");
        assertEquals(sj.toString(), "EMPTY");
        sj.merge(other);
        assertEquals(sj.toString(), "EMPTY");
    }

    public void testCascadeEmpty() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        StringJoiner o1 = new StringJoiner(":", "[", "]").setEmptyValue("Empty1");
        StringJoiner o2 = new StringJoiner(",", "<", ">").setEmptyValue("Empty2");

        o1.merge(o2);
        assertEquals(o1.toString(), "Empty1");

        sj.merge(o1);
        assertEquals(sj.toString(), "{}");
    }

    public void testDelimiter() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        StringJoiner other = new StringJoiner(":", "[", "]");
        Stream.of("a", "b", "c").forEachOrdered(sj::add);
        Stream.of("d", "e", "f").forEachOrdered(other::add);

        sj.merge(other);
        assertEquals(sj.toString(), "{a,b,c,d:e:f}");
    }

    public void testMergeSelf() {
        final StringJoiner sj = new StringJoiner(",", "[", "]").add("a").add("b");
        assertEquals(sj.merge(sj).toString(), "[a,b,a,b]");
        assertEquals(sj.merge(sj).toString(), "[a,b,a,b,a,b,a,b]");

        final StringJoiner sj2 = new StringJoiner(",").add("c").add("d");
        assertEquals(sj2.merge(sj2).toString(), "c,d,c,d");
    }
}
