/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.cfg;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

public class CFGVerifier {

    public static <T extends AbstractBlockBase<T>, C extends AbstractControlFlowGraph<T>> boolean verify(C cfg) {
        for (T block : cfg.getBlocks()) {
            assert block.getId() >= 0;
            assert cfg.getBlocks()[block.getId()] == block;

            for (T pred : block.getPredecessors()) {
                assert Arrays.asList(pred.getSuccessors()).contains(block);
                assert pred.getId() < block.getId() || pred.isLoopEnd();
            }

            for (T sux : block.getSuccessors()) {
                assert Arrays.asList(sux.getPredecessors()).contains(block);
                assert sux.getId() > block.getId() || sux.isLoopHeader();
            }

            if (block.getDominator() != null) {
                assert block.getDominator().getId() < block.getId();

                AbstractBlockBase<?> domChild = block.getDominator().getFirstDominated();
                while (domChild != null) {
                    if (domChild == block) {
                        break;
                    }
                    domChild = domChild.getDominatedSibling();
                }
                assert domChild != null : "dominators must contain block";
            }

            T dominated = block.getFirstDominated();
            while (dominated != null) {
                assert dominated.getId() > block.getId();
                assert dominated.getDominator() == block;
                dominated = dominated.getDominatedSibling();
            }

            T postDominatorBlock = block.getPostdominator();
            if (postDominatorBlock != null) {
                assert block.getSuccessorCount() > 0 : "block has post-dominator block, but no successors";

                BlockMap<Boolean> visitedBlocks = new BlockMap<>(cfg);
                visitedBlocks.put(block, true);

                Deque<T> stack = new ArrayDeque<>();
                for (T sux : block.getSuccessors()) {
                    visitedBlocks.put(sux, true);
                    stack.push(sux);
                }

                while (stack.size() > 0) {
                    T tos = stack.pop();
                    assert tos.getId() <= postDominatorBlock.getId();
                    if (tos == postDominatorBlock) {
                        continue; // found a valid path
                    }
                    assert tos.getSuccessorCount() > 0 : "no path found";

                    for (T sux : tos.getSuccessors()) {
                        if (visitedBlocks.get(sux) == null) {
                            visitedBlocks.put(sux, true);
                            stack.push(sux);
                        }
                    }
                }
            }

            assert cfg.getLoops() == null || !block.isLoopHeader() || block.getLoop().getHeader() == block;
        }

        if (cfg.getLoops() != null) {
            for (Loop<T> loop : cfg.getLoops()) {
                assert loop.getHeader().isLoopHeader();

                for (T block : loop.getBlocks()) {
                    assert block.getId() >= loop.getHeader().getId();

                    Loop<?> blockLoop = block.getLoop();
                    while (blockLoop != loop) {
                        assert blockLoop != null;
                        blockLoop = blockLoop.getParent();
                    }

                    if (!(block.isLoopHeader() && block.getLoop() == loop)) {
                        for (T pred : block.getPredecessors()) {
                            if (!loop.getBlocks().contains(pred)) {
                                assert false : "Loop " + loop + " does not contain " + pred;
                                return false;
                            }
                        }
                    }
                }

                for (T block : loop.getExits()) {
                    assert block.getId() >= loop.getHeader().getId();

                    Loop<?> blockLoop = block.getLoop();
                    while (blockLoop != null) {
                        blockLoop = blockLoop.getParent();
                        assert blockLoop != loop;
                    }
                }
            }
        }

        return true;
    }
}
