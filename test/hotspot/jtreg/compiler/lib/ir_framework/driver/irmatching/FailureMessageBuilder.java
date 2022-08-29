package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.driver.irmatching.irmethod.AbstractIRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.NotCompiledResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CheckAttributeMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.ConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraintFailure;
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseMatchResult;
import compiler.lib.ir_framework.shared.Comparison;

import java.util.List;
import java.util.stream.Collectors;

public class FailureMessageBuilder implements MatchResultVisitor {

    private final StringBuilder msg = new StringBuilder();
    private int indentation;
    private int reportedMethodCount = 0;

    @Override
    public void visit(TestClassResult testClassResult) {
        msg.insert(0, buildTestClassMessage(testClassResult));
    }

    private static String buildTestClassMessage(TestClassResult testClassResult) {
        int failedIRRulesCount = getFailedIRRulesCount(testClassResult);
        long failedMethodCount = getFailedMethodCount(testClassResult);
        return "One or more @IR rules failed:" + System.lineSeparator() + System.lineSeparator()
               + "Failed IR Rules (" + failedIRRulesCount + ") of Methods (" + failedMethodCount + ")"
               + System.lineSeparator()
               +  "-".repeat(32 + digitCount(failedIRRulesCount) + digitCount(failedMethodCount))
               + System.lineSeparator();
    }

    private static int getFailedIRRulesCount(TestClassResult testClassResult) {
        return testClassResult.getResults().stream()
                              .map(AbstractIRMethodMatchResult::getFailedIRRuleCount)
                              .reduce(0, Integer::sum);
    }

    private static long getFailedMethodCount(TestClassResult testClassResult) {
        return testClassResult.getResults().stream()
                              .filter(AbstractIRMethodMatchResult::fail)
                              .count();
    }

    private static int digitCount(long digit) {
        return String.valueOf(digit).length();
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        reportedMethodCount++;
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
    }

    @Override
    public void visit(IRRuleMatchResult irRuleMatchResult) {
        IRRule irRule = irRuleMatchResult.getIRRule();
        msg.append(getIndentation(indentation)).append("* @IR rule ").append(irRule.getRuleId()).append(": \"")
           .append(irRule.getIRAnno()).append("\"").append(System.lineSeparator());
    }

    @Override
    public void visit(CompilePhaseMatchResult compilePhaseMatchResult) {
        msg.append(getIndentation(indentation + 2)).append("> Phase \"")
           .append(compilePhaseMatchResult.getCompilePhase().getName()).append("\":").append(System.lineSeparator());
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
    public void visit(ConstraintFailure constraintFailure) {
        msg.append(buildConstraintHeader(constraintFailure)).append(buildMatchedNodesMessage(constraintFailure));
    }

    private List<String> addWhiteSpacePrefixForEachLine(List<String> matches, String indentation) {
        return matches.stream()
                      .map(s -> s.replaceAll(System.lineSeparator(), System.lineSeparator() + indentation))
                      .collect(Collectors.toList());
    }

    private String buildConstraintHeader(ConstraintFailure constraintFailure) {
        return getIndentation(indentation + 6) + "* Constraint "
               + constraintFailure.getConstraintIndex() + ": \"" + constraintFailure.getNodeRegex() + "\""
               + System.lineSeparator();
    }

    private String buildMatchedNodesMessage(ConstraintFailure constraintFailure) {
        return buildMatchedNodesHeader(constraintFailure) + buildMatchedNodesBody(constraintFailure);
    }

    private String buildMatchedNodesHeader(ConstraintFailure constraintFailure) {
        int matchCount = constraintFailure.getMatchedNodes().size();
        return getIndentation(indentation + 8) + "- " + getMatchedPrefix(constraintFailure)
               + " node" + (matchCount > 1 ? "s (" + matchCount + ")" : "") + ":" + System.lineSeparator();
    }

    private String getMatchedPrefix(ConstraintFailure constraintFailure) {
        switch (constraintFailure.getCheckAttributeKind()) {
            case FAIL_ON -> {
                return "Matched forbidden";
            }
            case COUNTS -> {
                return "Matched";
            }
            default ->
                    throw new IllegalStateException("Unexpected value: " + constraintFailure.getCheckAttributeKind());
        }
    }

    private String buildMatchedNodesBody(ConstraintFailure constraintFailure) {
        StringBuilder builder = new StringBuilder();
        String indentationString = getIndentation(indentation + 10);
        List<String> matches = addWhiteSpacePrefixForEachLine(constraintFailure.getMatchedNodes(),
                                                              indentationString + "  ");
        matches.forEach(match -> builder.append(indentationString).append("* ").append(match).append(System.lineSeparator()));
        return builder.toString();
    }


    @Override
    public void visit(CountsConstraintFailure constraintFailure) {
        msg.append(buildConstraintHeader(constraintFailure)).append(buildFailedComparisonMessage(constraintFailure))
           .append(buildMatchedCountsNodesMessage(constraintFailure));
    }

    private String buildFailedComparisonMessage(CountsConstraintFailure constraintFailure) {
        Comparison<Integer> comparison = constraintFailure.getComparison();
        String failedComparison = "[found] " + constraintFailure.getMatchedNodes().size() + " "
                                  + comparison.getComparator() + " " + comparison.getGivenValue() + " [given]";
        return getIndentation(indentation + 8) + "- Failed comparison: " + failedComparison
               + System.lineSeparator();
    }

    private String buildMatchedCountsNodesMessage(CountsConstraintFailure constraintFailure) {
        if (constraintFailure.getMatchedNodes().isEmpty()) {
            return buildEmptyNodeMatchesMessage();
        } else {
            return buildMatchedNodesMessage(constraintFailure);
        }
    }

    private String buildEmptyNodeMatchesMessage() {
        return getIndentation(indentation + 8) + "- No nodes matched!" + System.lineSeparator();
    }

    @Override
    public boolean shouldVisit(MatchResult result) {
        return result.fail();
    }

    private String getIndentation(int indentationSize) {
        return " ".repeat(indentationSize);
    }

    public String build(TestClassResult testClassResult) {
        testClassResult.accept(this);
        return msg.toString();
    }
}
