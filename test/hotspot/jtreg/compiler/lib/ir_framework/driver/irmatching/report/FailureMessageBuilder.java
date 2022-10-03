package compiler.lib.ir_framework.driver.irmatching.report;

import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.TestClassMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOnConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseIRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.NoCompilePhaseCompilationResult;
import compiler.lib.ir_framework.driver.irmatching.visitor.MatchResultVisitor;

/**
 * This class creates the complete failure message of each IR matching failure by visiting each match result element.
 */
public class FailureMessageBuilder extends ReportBuilder implements MatchResultVisitor {
    /**
     * Initial indentation for an IR rule match result message.
     */
    private int irRuleIndentation;

    public FailureMessageBuilder(MatchResult testClassResult) {
        super(testClassResult);
    }

    @Override
    public void visit(TestClassMatchResult testClassMatchResult) {
        FailCountVisitor failCountVisitor = new FailCountVisitor();
        testClassMatchResult.acceptChildren(failCountVisitor);
        int failedMethodCount = failCountVisitor.getIrMethodCount();
        int failedIRRulesCount = failCountVisitor.getIrRuleCount();
        msg.append("One or more @IR rules failed:")
           .append(System.lineSeparator())
           .append(System.lineSeparator())
           .append("Failed IR Rules (").append(failedIRRulesCount).append(") of Methods (").append(failedMethodCount)
           .append(")").append(System.lineSeparator())
           .append(getTitleSeparator(failedMethodCount, failedIRRulesCount))
           .append(System.lineSeparator());
        SortedIRMethodResultCollector sortedIRMethodResultCollector = new SortedIRMethodResultCollector();
        testClassMatchResult.acceptChildren(this, sortedIRMethodResultCollector.collect(testClassMatchResult));
    }

    private static String getTitleSeparator(int failedMethodCount, int failedIRRulesCount) {
        return "-".repeat(32 + digitCount(failedIRRulesCount) + digitCount(failedMethodCount));
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        appendIRMethodHeader(irMethodMatchResult);
        irMethodMatchResult.acceptChildren(this);
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
    public void visit(NotCompiledResult notCompiledResult) {
        appendIRMethodHeader(notCompiledResult);
        msg.append(getIndentation(irRuleIndentation))
           .append("* Method was not compiled. Did you specify a @Run method in STANDALONE mode? In this case, make " +
                   "sure to always trigger a C2 compilation by invoking the test enough times.")
           .append(System.lineSeparator());
    }

    @Override
    public void visit(IRRuleMatchResult irRuleMatchResult) {
        msg.append(getIndentation(irRuleIndentation)).append("* @IR rule ").append(irRuleMatchResult.getRuleId()).append(": \"")
           .append(irRuleMatchResult.getIRAnno()).append("\"").append(System.lineSeparator());
        SortedCompilePhaseResultCollector sortedCompilePhaseResultCollector = new SortedCompilePhaseResultCollector();
        irRuleMatchResult.acceptChildren(this, sortedCompilePhaseResultCollector.collect(irRuleMatchResult));
    }

    @Override
    public void visit(CompilePhaseIRRuleMatchResult compilePhaseIRRuleMatchResult) {
        appendCompilePhaseIRRule(compilePhaseIRRuleMatchResult);
        compilePhaseIRRuleMatchResult.acceptChildren(this);
    }

    private void appendCompilePhaseIRRule(CompilePhaseIRRuleMatchResult compilePhaseIRRuleMatchResult) {
        msg.append(getIndentation(irRuleIndentation + 2))
           .append("> Phase \"").append(compilePhaseIRRuleMatchResult.getCompilePhase().getName()).append("\":")
           .append(System.lineSeparator());
    }

    @Override
    public void visit(NoCompilePhaseCompilationResult noCompilePhaseCompilationResult) {
        appendCompilePhaseIRRule(noCompilePhaseCompilationResult);
        msg.append(getIndentation(irRuleIndentation + 4))
           .append("- NO compilation output found for this phase! Make sure this phase is emitted or remove it from ")
           .append("the list of compile phases in the @IR rule to match on.")
           .append(System.lineSeparator());
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
        msg.append(getIndentation(irRuleIndentation + 4)).append("- ").append(checkAttributeFailureMsg)
           .append(":").append(System.lineSeparator());
        checkAttributeMatchResult.acceptChildren(this);
    }

    @Override
    public void visit(FailOnConstraintFailure failOnConstraintFailure) {
        ConstraintFailureMessageBuilder constrainFailureMessageBuilder =
                new ConstraintFailureMessageBuilder(failOnConstraintFailure,irRuleIndentation + 6);
        String failureMessage = constrainFailureMessageBuilder.buildConstraintHeader() +
                                constrainFailureMessageBuilder.buildMatchedNodesMessage("Matched forbidden");
        msg.append(failureMessage);
    }

    @Override
    public void visit(CountsConstraintFailure countsConstraintMatchResult) {
        msg.append(new CountsConstraintFailureMessageBuilder(countsConstraintMatchResult, irRuleIndentation + 6).build());
    }

    @Override
    public String build() {
        visitResults(this);
        msg.append(System.lineSeparator())
           .append(">>> Check stdout for compilation output of the failed methods")
           .append(System.lineSeparator()).append(System.lineSeparator());
        return msg.toString();
    }
}

