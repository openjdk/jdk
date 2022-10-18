/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.*;
import java.util.*;

/**
 * @test
 * @summary Test StringConcatFactory.StringConcatItem... methods.
 * @compile --enable-preview -source ${jdk.version} StringConcatItem.java
 * @run main/othervm --enable-preview StringConcatItem
 */

public class StringConcatItem {





    public static void main(String... args) {
        interpolate();
    }

    static class MyConcatItem implements StringConcatFactory.StringConcatItem {
        MyConcatItem() {
        }

        @Override
        public long mix(long lengthCoder) {
            return lengthCoder + 3;
        }

        @Override
        public long prepend(long lengthCoder, byte[] buffer) throws Throwable {
            if (0 <= lengthCoder) {
                buffer[(int)--lengthCoder] = '3';
                buffer[(int)--lengthCoder] = '2';
                buffer[(int)--lengthCoder] = '1';
            } else {
                throw new RuntimeException("Should have been Latin1");
            }
            return lengthCoder;
        }
    }

    static void interpolate() {
        try {
            MethodHandle m = StringConcatFactory.makeConcatWithTemplate(
                    List.of("abc", "xyz"),
                    List.of(MyConcatItem.class));
            String s = (String)m.invoke(new MyConcatItem());

            if (!"abc123xyz".equals(s)) {
                throw new RuntimeException("incorrect result: " + s);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
