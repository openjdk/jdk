package compiler.lib.ir_framework.driver.irmatching;

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
    private final int initialIndentation;

    public FailureMessageBuilder(int initialIndentation) {
        this.initialIndentation = initialIndentation;
    }

    @Override
    public void visit(IRMethodMatchResult irMethodMatchResult) {
        msg.append(" Method \"").append(irMethodMatchResult.getIRMethod().getMethod()).append("\" - [Failed IR rules: ").append(irMethodMatchResult.getFailedIRRuleCount()).append("]:").append(System.lineSeparator());
    }

    @Override
    public void visit(NotCompiledResult notCompiledResult) {

    }

    @Override
    public void visit(IRRuleMatchResult irRuleMatchResult) {
        IRRule irRule = irRuleMatchResult.getIRRule();
        msg.append(getIndentation(initialIndentation)).append("* @IR rule ").append(irRule.getRuleId()).append(": \"").append(irRule.getIRAnno()).append("\"").append(System.lineSeparator());
    }

    @Override
    public void visit(CompilePhaseMatchResult compilePhaseMatchResult) {
        msg.append(getIndentation(initialIndentation + 2)).append("> Phase \"").append(compilePhaseMatchResult.getCompilePhase().getName()).append("\":").append(System.lineSeparator());
        if (compilePhaseMatchResult.hasNoCompilationOutput()) {
            msg.append(buildNoCompilationOutputMessage());
        }
    }

    private String buildNoCompilationOutputMessage() {
        return getIndentation(initialIndentation + 2) + "- NO compilation output found for this phase! Make sure this " + "phase is emitted or remove it from the list of compile phases in the @IR rule to match on." + System.lineSeparator();
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
        msg.append(getIndentation(initialIndentation + 4)).append("- ").append(checkAttributeFailureMsg)
           .append(":").append(System.lineSeparator());
    }

    @Override
    public void visit(ConstraintFailure constraintFailure) {
        msg.append(buildConstraintHeader(constraintFailure)).append(buildMatchedNodesMessage(constraintFailure));
    }

    private List<String> addWhiteSpacePrefixForEachLine(List<String> matches, String indentation) {
        return matches.stream().map(s -> s.replaceAll(System.lineSeparator(), System.lineSeparator() + indentation)).collect(Collectors.toList());
    }

    private String buildConstraintHeader(ConstraintFailure constraintFailure) {
        return getIndentation(initialIndentation + 6) + "* Constraint " + constraintFailure.getConstraintIndex() + ": \"" + constraintFailure.getNodeRegex() + "\"" + System.lineSeparator();
    }

    private String buildMatchedNodesMessage(ConstraintFailure constraintFailure) {
        return buildMatchedNodesHeader(constraintFailure) + buildMatchedNodesBody(constraintFailure);
    }

    private String buildMatchedNodesHeader(ConstraintFailure constraintFailure) {
        int matchCount = constraintFailure.getMatchedNodes().size();
        return getIndentation(initialIndentation + 8) + "- " + getMatchedPrefix(constraintFailure) + " node" + (matchCount > 1 ? "s (" + matchCount + ")" : "") + ":" + System.lineSeparator();
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
        String indentationString = getIndentation(initialIndentation + 10);
        List<String> matches = addWhiteSpacePrefixForEachLine(constraintFailure.getMatchedNodes(), indentationString + "  ");
        matches.forEach(match -> builder.append(indentationString).append("* ").append(match).append(System.lineSeparator()));
        return builder.toString();
    }


    @Override
    public void visit(CountsConstraintFailure constraintFailure) {
        msg.append(buildConstraintHeader(constraintFailure)).append(buildFailedComparisonMessage(constraintFailure)).append(buildMatchedCountsNodesMessage(constraintFailure));
    }

    private String buildFailedComparisonMessage(CountsConstraintFailure constraintFailure) {
        Comparison<Integer> comparison = constraintFailure.getComparison();
        String failedComparison = "[found] " + constraintFailure.getMatchedNodes().size() + " " + comparison.getComparator() + " " + comparison.getGivenValue() + " [given]";
        return getIndentation(initialIndentation + 8) + "- Failed comparison: " + failedComparison + System.lineSeparator();
    }

    private String buildMatchedCountsNodesMessage(CountsConstraintFailure constraintFailure) {
        if (constraintFailure.getMatchedNodes().isEmpty()) {
            return buildEmptyNodeMatchesMessage();
        } else {
            return buildMatchedNodesMessage(constraintFailure);
        }
    }

    private String buildEmptyNodeMatchesMessage() {
        return getIndentation(initialIndentation + 8) + "- No nodes matched!" + System.lineSeparator();
    }

    @Override
    public boolean shouldVisit(MatchResult result) {
        return result.fail();
    }

    private String getIndentation(int indentationSize) {
        return " ".repeat(indentationSize);
    }

    public String build() {
        return msg.toString();
    }
}
