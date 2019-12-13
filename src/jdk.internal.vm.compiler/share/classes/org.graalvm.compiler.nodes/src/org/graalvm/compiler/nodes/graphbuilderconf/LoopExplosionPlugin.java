/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.graphbuilderconf;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public interface LoopExplosionPlugin extends GraphBuilderPlugin {

    enum LoopExplosionKind {
        /**
         * No loop explosion.
         */
        NONE(false, false, false, false),
        /**
         * Fully unroll all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are merged so that the subsequent loop iteration is
         * processed only once. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+1+1+1 = 4 copies of the loop body.
         */
        FULL_UNROLL(true, false, false, false),
        /**
         * Like {@link #FULL_UNROLL}, but in addition loop unrolling duplicates loop exits in every
         * iteration instead of merging them. Code after a loop exit is duplicated for every loop
         * exit and every loop iteration. For example, a loop with 4 iterations and 2 loop exits
         * (exit1 and exit2, where exit1 is an early return inside a loop) leads to 4 copies of the
         * loop body and 4 copies of exit1 and 1 copy if exit2. After each exit all code until a
         * return is duplicated per iteration. Beware of break statements inside loops since they
         * cause additional loop exits leading to code duplication along exit2.
         */
        FULL_UNROLL_UNTIL_RETURN(true, false, true, false),
        /**
         * Fully explode all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are not merged so that subsequent loop iterations are
         * processed multiple times. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+2+4+8 = 15 copies of the loop body.
         */
        FULL_EXPLODE(true, true, false, false),
        /**
         * Like {@link #FULL_EXPLODE}, but in addition explosion does not stop at loop exits. Code
         * after the loop is duplicated for every loop exit of every loop iteration. For example, a
         * loop with 4 iterations and 2 loop exits leads to 4 * 2 = 8 copies of the code after the
         * loop.
         */
        FULL_EXPLODE_UNTIL_RETURN(true, true, true, false),
        /**
         * like {@link #FULL_EXPLODE}, but copies of the loop body that have the exact same state
         * (all local variables have the same value) are merged. This reduces the number of copies
         * necessary, but can introduce loops again. This kind is useful for bytecode interpreter
         * loops.
         */
        MERGE_EXPLODE(true, true, false, true);

        private final boolean unrollLoops;
        private final boolean duplicateLoopEnds;
        private final boolean duplicateLoopExits;
        private final boolean mergeLoops;

        LoopExplosionKind(boolean unrollLoops, boolean duplicateLoopEnds, boolean duplicateLoopExits, boolean mergeLoops) {
            this.unrollLoops = unrollLoops;
            assert !duplicateLoopEnds || unrollLoops;
            this.duplicateLoopEnds = duplicateLoopEnds;
            assert !duplicateLoopExits || unrollLoops;
            this.duplicateLoopExits = duplicateLoopExits;
            this.mergeLoops = mergeLoops;
        }

        public boolean unrollLoops() {
            return unrollLoops;
        }

        public boolean duplicateLoopExits() {
            return duplicateLoopExits;
        }

        public boolean duplicateLoopEnds() {
            return duplicateLoopEnds;
        }

        public boolean mergeLoops() {
            return mergeLoops;
        }

        public boolean useExplosion() {
            return this != NONE;
        }

        public boolean isNoExplosion() {
            return this == NONE;
        }
    }

    LoopExplosionKind loopExplosionKind(ResolvedJavaMethod method);
}
