/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.math.FloatToDecimalChecker;
import jdk.test.lib.RandomFactory;

/*
 * @test
 * @key randomness
 *
 * @modules java.base/jdk.internal.math
 * @library /test/lib
 * @library java.base
 * @build jdk.test.lib.RandomFactory
 * @build java.base/jdk.internal.math.*
 * @run main FloatToDecimalTest 100_000
 */
public class FloatToDecimalTest {

    private static final int RANDOM_COUNT = 100_000;

    public static void main(String[] args) {
        if (args.length == 0) {
            FloatToDecimalChecker.test(RANDOM_COUNT, RandomFactory.getRandom());
        } else if (args[0].equals("all")) {
            FloatToDecimalChecker.testAll();
        } else if (args[0].equals("positive")) {
            FloatToDecimalChecker.testPositive();
        } else {
            try {
                int count = Integer.parseInt(args[0].replace("_", ""));
                FloatToDecimalChecker.test(count, RandomFactory.getRandom());
            } catch (NumberFormatException ignored) {
                FloatToDecimalChecker.test(RANDOM_COUNT, RandomFactory.getRandom());
            }
        }
    }

}
