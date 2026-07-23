/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.visitor;

import compiler.lib.ir_framework.driver.irmatching.LeafMatchResult;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.TestClassMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompilableIRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledIRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseNoCompilationIRRuleMatchResult;

import java.util.function.Consumer;

/**
 * This interface specifies visit methods for each {@link MatchResult} class must be implemented a by a concrete visitor.
 *
 * <p>
 * There are two kinds of visits:
 *
 * <ol>
 *   <li>
 *     {@link #visit} on {@link MatchResult}s that can have one or more sub results. This interface provides the following
 *     default implementation for them (directly override this method when you do not want to visit sub results later):
 *     <ol>
 *       <li>
 *         {@link #enter}: First called to visit the current {@link MatchResult} before visiting sub results. Override
 *                         this method to specify behavior at this stage. Afterward, the sub results will be visited.
 *                         By default, this method does nothing.
 *       </li>
 *       <li>
 *         {@link #visitSubResults}: Called after {@link #enter} to visit the sub results. This should not be overridden.
 *       </li>
 *       <li>
 *         {@link #leave}: After visiting the sub results, this method is called to visit the current {@link MatchResult}
 *                         again to do some post work. Override this method to specify behavior at this stage.
 *                         By default, this method does nothing.
 *       </li>
 *     </ol>
 *   </li>
 *   <li>
 *     {@link #visitLeaf} on {@link LeafMatchResult}s that do not have any sub results. This interface provides
 *     an empty default implementation. Override {@link #visitLeaf} to specify a different behavior.
 *   </li>
 * </ol>
 */
public interface MatchResultVisitor {

    /**
     * Should a result be visited? By default, we only visit failing results. Override this method to change this behavior.
     */
    default boolean shouldVisit(MatchResult result) {
        return result.fail();
    }

    default void visit(TestClassMatchResult result) {
        doVisit(result, this::enter, this::leave);
    }

    default void enter(TestClassMatchResult result) {}
    default void leave(TestClassMatchResult result) {}

    default void visit(IRMethodMatchResult result) {
        doVisit(result, this::enter, this::leave);
    }

    default void enter(IRMethodMatchResult result) {}
    default void leave(IRMethodMatchResult result) {}

    default void visit(IRRuleMatchResult result) {
        doVisit(result, this::enter, this::leave);
    }

    default void enter(IRRuleMatchResult result) {}
    default void leave(IRRuleMatchResult result) {}

    default void visit(CompilePhaseIRRuleMatchResult result) {
        doVisit(result, this::enter, this::leave);
    }

    default void enter(CompilePhaseIRRuleMatchResult result) {}
    default void leave(CompilePhaseIRRuleMatchResult result) {}

    default void visit(CheckAttributeMatchResult result) {
        doVisit(result, this::enter, this::leave);
    }

    default void enter(CheckAttributeMatchResult result) {}
    default void leave(CheckAttributeMatchResult result) {}

    /**
     * Default visit when {@link #visit} is not overridden.
     *
     * <p>
     * Note: Do not override this method.
     */
    default <R extends MatchResult> void doVisit(R result, Consumer<R> enter, Consumer<R> leave) {
        if (!shouldVisit(result)) {
            return;
        }
        enter.accept(result);
        visitSubResults(result);
        leave.accept(result);
    }

    /**
     * Visit children of {@code result}. This is called when {@link #visit} is not overridden.
     *
     * <p>
     * Note: Do not override this method.
     */
    default void visitSubResults(MatchResult result) {
        for (MatchResult subResult : result.subResults()) {
            if (shouldVisit(subResult)) {
                subResult.accept(this);
            }
        }
    }

    /*
     * Visit methods for LeafMatchResults without sub results.
     */

    default void visitLeaf(CompilePhaseNoCompilationIRRuleMatchResult result) {}
    default void visitLeaf(NotCompiledIRMethodMatchResult result) {}
    default void visitLeaf(NotCompilableIRMethodMatchResult result) {}
    default void visitLeaf(FailOnConstraintFailure result) {}
    default void visitLeaf(CountsConstraintFailure result) {}
}
