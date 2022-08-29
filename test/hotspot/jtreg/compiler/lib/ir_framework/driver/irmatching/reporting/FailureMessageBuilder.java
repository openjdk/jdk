package compiler.lib.ir_framework.driver.irmatching.reporting;

import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.MatchResultVisitor;
import compiler.lib.ir_framework.driver.irmatching.TestClassResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseMatchResult;

public class FailureMessageBuilder implements MatchResultVisitor, FailureMessage {

    private final StringBuilder msg = new StringBuilder();
    private int indentation;
    private int reportedMethodCount = 0;
    private final TestClassResult testClassResult;

    public FailureMessageBuilder(TestClassResult testClassResult) {
        this.testClassResult = testClassResult;
    }

    @Override
    public boolean shouldVisit(MatchResult result) {
        return result.fail();
    }

    @Override
    public void visit(TestClassResult testClassResult) {
        msg.append(new TestClassFailureMessageBuilder(testClassResult).build());
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        reportedMethodCount++;
        if (reportedMethodCount > 1) {
            msg.append(System.lineSeparator());
        }
        int reportedMethodCountDigitCount = String.valueOf(reportedMethodCount).length();
        // Format: "X) Method..." -> Initial indentation = digitsCount(X) + ) + " "
//        return failureNumber + ")" + result.buildFailureMessage(failureNumberDigitCount + 2) + System.lineSeparator();
        indentation = reportedMethodCountDigitCount + 2;
        msg.append(reportedMethodCount).append(") Method \"").append(irMethodMatchResult.getIRMethod().getMethod())
           .append("\" - [Failed IR rules: ").append(irMethodMatchResult.getFailedIRRuleCount()).append("]:")
           .append(System.lineSeparator());
    }

    @Override
    public void visit(NotCompiledResult notCompiledResult) {
        msg.append(getIndentation(indentation))
           .append("* Method was not compiled. Did you specify a @Run method in STANDALONE mode? In this case, make " +
                   "sure to always trigger a C2 compilation by invoking the test enough times.");
    }

    @Override
    public void visit(IRRuleMatchResult irRuleMatchResult) {
        IRRule irRule = irRuleMatchResult.getIRRule();
        msg.append(getIndentation(indentation)).append("* @IR rule ").append(irRule.getRuleId()).append(": \"")
           .append(irRule.getIRAnno()).append("\"").append(System.lineSeparator());
    }

    @Override
    public void visit(CompilePhaseMatchResult compilePhaseMatchResult) {
        msg.append(getIndentation(indentation + 2))
           .append("> Phase \"").append(compilePhaseMatchResult.getCompilePhase().getName()).append("\":")
           .append(System.lineSeparator());
        if (compilePhaseMatchResult.hasNoCompilationOutput()) {
            msg.append(buildNoCompilationOutputMessage());
        }
    }

    private String buildNoCompilationOutputMessage() {
        return getIndentation(indentation + 2) + "- NO compilation output found for this phase! Make sure this "
               + "phase is emitted or remove it from the list of compile phases in the @IR rule to match on."
               + System.lineSeparator();
    }

    @Override
    public void visit(CheckAttributeMatchResult checkAttributeMatchResult) {
        String checkAttributeFailureMsg;
        switch (checkAttributeMatchResult.getCheckAttributeKind()) {
            case FAIL_ON -> checkAttributeFailureMsg = "failOn: Graph contains forbidden nodes";
            case COUNTS -> checkAttributeFailureMsg = "counts: Graph contains wrong number of nodes";
            default ->
                    throw new IllegalStateException("Unexpected value: " + checkAttributeMatchResult.getCheckAttributeKind());
        }
        msg.append(getIndentation(indentation + 4)).append("- ").append(checkAttributeFailureMsg)
           .append(":").append(System.lineSeparator());
    }

    @Override
    public void visit(FailOnConstraintFailure constraintFailure) {
        msg.append(new FailOnConstraintFailureMessageBuilder(constraintFailure, indentation + 6).build());
    }

    @Override
    public void visit(CountsConstraintFailure constraintFailure) {
        msg.append(new CountsConstraintFailureMessageBuilder(constraintFailure, indentation + 6).build());
    }

    @Override
    public String build() {
        testClassResult.accept(this);
        msg.append(System.lineSeparator())
           .append(">>> Check stdout for compilation output of the failed methods")
           .append(System.lineSeparator()).append(System.lineSeparator());
        return msg.toString();
    }
}
