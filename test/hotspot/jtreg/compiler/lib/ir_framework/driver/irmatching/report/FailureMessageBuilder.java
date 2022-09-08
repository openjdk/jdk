package compiler.lib.ir_framework.driver.irmatching.report;

import compiler.lib.ir_framework.driver.irmatching.TestClassResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.NoCompilePhaseCompilationResult;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultAction;
import compiler.lib.ir_framework.driver.irmatching.visitor.PreOrderMatchResultVisitor;

/**
 * This class creates the complete failure message of each IR matching failure by visiting each match result element.
 */
public class FailureMessageBuilder extends ReportBuilder implements MatchResultAction {
    /**
     * Initial indentation for an IR rule match result message.
     */
    private int irRuleIndentation;

    public FailureMessageBuilder(TestClassResult testClassResult) {
        super(testClassResult);
    }

    @Override
    public void doAction(TestClassResult testClassResult) {
        FailCountVisitor failCountVisitor = new FailCountVisitor();
        testClassResult.acceptChildren(failCountVisitor);
        int failedMethodCount = failCountVisitor.getIrMethodCount();
        int failedIRRulesCount = failCountVisitor.getIrRuleCount();
        msg.append("One or more @IR rules failed:")
           .append(System.lineSeparator())
           .append(System.lineSeparator())
           .append("Failed IR Rules (").append(failedIRRulesCount).append(") of Methods (").append(failedMethodCount)
           .append(")").append(System.lineSeparator())
           .append(getTitleSeparator(failedMethodCount, failedIRRulesCount))
           .append(System.lineSeparator());
    }

    private static String getTitleSeparator(int failedMethodCount, int failedIRRulesCount) {
        return "-".repeat(32 + digitCount(failedIRRulesCount) + digitCount(failedMethodCount));
    }

    @Override
    public void doAction(IRMethodMatchResult irMethodMatchResult) {
        appendIRMethodHeader(irMethodMatchResult);
    }

    private void appendIRMethodHeader(IRMethodMatchResult irMethodMatchResult) {
        appendIRMethodPrefix();
        int reportedMethodCountDigitCount = digitCount(getMethodNumber());
        irRuleIndentation = reportedMethodCountDigitCount + 2;
        msg.append("Method \"").append(irMethodMatchResult.getIRMethod().getMethod())
           .append("\" - [Failed IR rules: ").append(irMethodMatchResult.getFailedIRRuleCount()).append("]:")
           .append(System.lineSeparator());
    }

    @Override
    public void doAction(NotCompiledResult notCompiledResult) {
        appendIRMethodHeader(notCompiledResult);
        msg.append(getIndentation(irRuleIndentation))
           .append("* Method was not compiled. Did you specify a @Run method in STANDALONE mode? In this case, make " +
                   "sure to always trigger a C2 compilation by invoking the test enough times.")
           .append(System.lineSeparator());
    }

    @Override
    public void doAction(IRRuleMatchResult irRuleMatchResult) {
        msg.append(getIndentation(irRuleIndentation)).append("* @IR rule ").append(irRuleMatchResult.getRuleId()).append(": \"")
           .append(irRuleMatchResult.getIRAnno()).append("\"").append(System.lineSeparator());
    }

    @Override
    public void doAction(CompilePhaseIRRuleMatchResult compilePhaseIRRuleMatchResult) {
        appendCompilePhaseIRRule(compilePhaseIRRuleMatchResult);
    }

    private void appendCompilePhaseIRRule(CompilePhaseIRRuleMatchResult compilePhaseIRRuleMatchResult) {
        msg.append(getIndentation(irRuleIndentation + 2))
           .append("> Phase \"").append(compilePhaseIRRuleMatchResult.getCompilePhase().getName()).append("\":")
           .append(System.lineSeparator());
    }

    @Override
    public void doAction(NoCompilePhaseCompilationResult noCompilePhaseCompilationResult) {
        appendCompilePhaseIRRule(noCompilePhaseCompilationResult);
        msg.append(getIndentation(irRuleIndentation + 4))
           .append("- NO compilation output found for this phase! Make sure this phase is emitted or remove it from ")
           .append("the list of compile phases in the @IR rule to match on.")
           .append(System.lineSeparator());
    }

    @Override
    public void doAction(CheckAttributeMatchResult checkAttributeMatchResult) {
        String checkAttributeFailureMsg;
        switch (checkAttributeMatchResult.getCheckAttributeKind()) {
            case FAIL_ON -> checkAttributeFailureMsg = "failOn: Graph contains forbidden nodes";
            case COUNTS -> checkAttributeFailureMsg = "counts: Graph contains wrong number of nodes";
            default ->
                    throw new IllegalStateException("Unexpected value: " + checkAttributeMatchResult.getCheckAttributeKind());
        }
        msg.append(getIndentation(irRuleIndentation + 4)).append("- ").append(checkAttributeFailureMsg)
           .append(":").append(System.lineSeparator());
    }

    @Override
    public void doAction(FailOnConstraintFailure failOnConstraintFailure) {
        msg.append(new FailOnConstraintFailureMessageBuilder(failOnConstraintFailure, irRuleIndentation + 6).build());
    }

    @Override
    public void doAction(CountsConstraintFailure countsConstraintFailure) {
        msg.append(new CountsConstraintFailureMessageBuilder(countsConstraintFailure, irRuleIndentation + 6).build());
    }

    @Override
    public String build() {
        PreOrderMatchResultVisitor visitor = new PreOrderMatchResultVisitor(this);
        visitResults(visitor);
        msg.append(System.lineSeparator())
           .append(">>> Check stdout for compilation output of the failed methods")
           .append(System.lineSeparator()).append(System.lineSeparator());
        return msg.toString();
    }
}
