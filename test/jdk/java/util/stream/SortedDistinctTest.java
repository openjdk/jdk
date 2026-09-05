/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * @test
 * @bug 8223933
 * @summary Stream.distinct() on sorted streams must use Object.equals
 */
public class SortedDistinctTest {

    /**
     * compareTo uses x; equals uses x and y (weaker ordering than equality).
     * Equal duplicates can be split by another element with the same sort key.
     */
    static final class Element implements Comparable<Element> {
        private final int x;
        private final int y;

        Element(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Element element = (Element) o;
            return x == element.x && y == element.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }

        @Override
        public int compareTo(Element o) {
            return Integer.compare(x, o.x);
        }

        @Override
        public String toString() {
            return "Element{x=" + x + ", y=" + y + "}";
        }
    }

    /**
     * compareTo uses x; equals uses only y (unrelated fields).
     * Equal duplicates can appear in different sort groups.
     */
    static final class ByYEquals implements Comparable<ByYEquals> {
        private final int x;
        private final int y;

        ByYEquals(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            return y == ((ByYEquals) o).y;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(y);
        }

        @Override
        public int compareTo(ByYEquals o) {
            return Integer.compare(x, o.x);
        }

        @Override
        public String toString() {
            return "ByYEquals{x=" + x + ", y=" + y + "}";
        }
    }

    public static void main(String[] args) {
        testSameSortGroupDuplicates();
        testDifferentSortGroupDuplicates();
    }

    private static void testSameSortGroupDuplicates() {
        Element element1 = new Element(1, 1);
        Element element2 = new Element(1, 2);
        Element element3 = new Element(2, 1);
        Element element4 = new Element(2, 2);
        Element element5 = new Element(2, 1);

        List<Element> result = Stream.of(element1, element2, element3, element4, element5)
                .sorted()
                .distinct()
                .collect(Collectors.toList());

        assertNoEqualsDuplicates(result, 4);
    }

    private static void testDifferentSortGroupDuplicates() {
        // After sorted() by x: (1,5), (2,7), (3,5) — last two non-adjacent equals by y
        List<ByYEquals> result = Stream.of(
                        new ByYEquals(1, 5),
                        new ByYEquals(2, 7),
                        new ByYEquals(3, 5))
                .sorted()
                .distinct()
                .collect(Collectors.toList());

        assertNoEqualsDuplicates(result, 2);
    }

    private static <T> void assertNoEqualsDuplicates(List<T> result, int expectedSize) {
        if (result.size() != expectedSize) {
            throw new RuntimeException(
                    "Expected " + expectedSize + " distinct elements, got "
                            + result.size() + ": " + result);
        }
        Set<T> distinctSet = new HashSet<>(result);
        if (distinctSet.size() != result.size()) {
            throw new RuntimeException("Duplicate elements in result: " + result);
        }
    }
}
