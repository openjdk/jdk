package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.ConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseMatchResult;

public interface MatchResultVisitor {
    default void visit(TestClassResult testClassResult) {}
    default void visit(IRMethodMatchResult irMethodMatchResult) {}
    default void visit(NotCompiledResult notCompiledResult) {}
    default void visit(IRRuleMatchResult irRuleMatchResult) {}
    default void visit(CompilePhaseMatchResult compilePhaseMatchResult) {}
    default void visit(CheckAttributeMatchResult checkAttributeMatchResult) {}
    default void visit(ConstraintFailure constraintFailure) {}
    default void visit(CountsConstraintFailure constraintFailure) {}

    default boolean shouldVisit(MatchResult matchResult) { return true; }
}

