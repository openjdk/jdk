package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.ConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseMatchResult;

public interface MatchResultVisitor {
    void visit(IRMethodMatchResult irMethodMatchResult);
    void visit(NotCompiledResult notCompiledResult);
    void visit(IRRuleMatchResult irRuleMatchResult);
    void visit(CompilePhaseMatchResult compilePhaseMatchResult);
    void visit(CheckAttributeMatchResult checkAttributeMatchResult);
    void visit(ConstraintFailure constraintFailure);
    void visit(CountsConstraintFailure constraintFailure);

    boolean shouldVisit(MatchResult v);
}

