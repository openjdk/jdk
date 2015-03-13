/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;
import java.util.SplittableRandom;

public class RandomSeed {
    private long seed = 0L;
    private Random rnd = null;
    private SplittableRandom srnd = null;

    public RandomSeed(boolean isSplittableRandom) {
        init(isSplittableRandom);
    }

    private void init(boolean isSplittableRandom) {
        // obtain seed from environment if supplied
        boolean isSeedProvided = false;
        try {
            // note that Long.valueOf(null) also throws a NumberFormatException
            // so if the property is undefined this will still work correctly
            seed = Long.valueOf(System.getProperty("seed"));
            isSeedProvided = true;
        } catch (NumberFormatException e) {
            // do nothing: isSeedProvided is already false
        }

        // if no seed from environment, create a fresh one
        Random tmpRnd = null;
        if (!isSeedProvided) {
            tmpRnd = new Random();
            seed = tmpRnd.nextLong();
        }

        // create the PRNG
        if (isSplittableRandom) {
            srnd = new SplittableRandom(seed);
        } else {
            rnd = tmpRnd != null ? tmpRnd : new Random();
            rnd.setSeed(seed);
        }
    }

    public Random getRandom() {
        if (rnd == null) {
            throw new IllegalStateException("Variable of type Random not initialized");
        }
        return rnd;
    }

    public SplittableRandom getSplittableRandom() {
        if (srnd == null) {
            throw new IllegalStateException("Variable of type SplittableRandom not initialized");
        }
        return srnd;
    }

    public long getSeed() {
        return seed;
    }
}
