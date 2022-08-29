package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.driver.irmatching.irmethod.AbstractIRMethodMatchResult;

import java.util.Set;
import java.util.TreeSet;

public class TestClassResult implements MatchResult {
    private final Set<AbstractIRMethodMatchResult> results = new TreeSet<>();

    @Override
    public boolean fail() {
        return !results.isEmpty();
    }

    public Set<AbstractIRMethodMatchResult> getResults() {
        return results;
    }

    public void addResult(AbstractIRMethodMatchResult abstractIRMethodMatchResult) {
        this.results.add(abstractIRMethodMatchResult);
    }

    @Override
    public void accept(MatchResultVisitor visitor) {
        for (var result : results) {
            if (visitor.shouldVisit(result)) {
                result.accept(visitor);
            }
        }
        // Visit the class last when we got all information
        visitor.visit(this);
    }
}
