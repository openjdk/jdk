/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8319817
 * @summary Check that aliases cannot be mutated
 * @run junit AliasesCopy
 */

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Set;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class AliasesCopy {
    private static final Set<String> ALIASES_SET = Set.of("foo-alias");
    private static final String[] ALIASES_ARRAY = ALIASES_SET.toArray(String[]::new);

    @Test
    public void aliasesCopy() {
        final FooCharset cs = new FooCharset(ALIASES_ARRAY);
        ALIASES_ARRAY[0] = "bar-alias";
        assertIterableEquals(ALIASES_SET, cs.aliases());
    }

    private static final class FooCharset extends Charset {
        private FooCharset(String[] aliases) {
            super("foo", aliases);
        }

        @Override
        public CharsetEncoder newEncoder() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public CharsetDecoder newDecoder() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public boolean contains(Charset cs) {
            throw new RuntimeException("not implemented");
        }
    }
}
