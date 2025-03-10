/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.text;

import java.util.BitSet;
import java.util.Set;

/**
 * Char matcher that can match ASCII characters efficiently.
 */
public class AsciiMatcher implements CharMatcher {
    private final BitSet set;

    private AsciiMatcher(Builder builder) {
        this.set = builder.set;
    }

    @Override
    public boolean matches(char c) {
        return set.get(c);
    }

    public Builder newBuilder() {
        return new Builder((BitSet) set.clone());
    }

    public static Builder builder() {
        return new Builder(new BitSet());
    }

    public static Builder builder(AsciiMatcher matcher) {
        return new Builder((BitSet) matcher.set.clone());
    }

    public static class Builder {
        private final BitSet set;

        private Builder(BitSet set) {
            this.set = set;
        }

        public Builder c(char c) {
            if (c > 127) {
                throw new IllegalArgumentException("Can only match ASCII characters");
            }
            set.set(c);
            return this;
        }

        public Builder anyOf(String s) {
            for (int i = 0; i < s.length(); i++) {
                c(s.charAt(i));
            }
            return this;
        }

        public Builder anyOf(Set<Character> characters) {
            for (Character c : characters) {
                c(c);
            }
            return this;
        }

        public Builder range(char from, char toInclusive) {
            for (char c = from; c <= toInclusive; c++) {
                c(c);
            }
            return this;
        }

        public AsciiMatcher build() {
            return new AsciiMatcher(this);
        }
    }
}
