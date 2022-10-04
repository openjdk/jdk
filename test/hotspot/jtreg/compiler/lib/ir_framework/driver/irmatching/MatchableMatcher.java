package compiler.lib.ir_framework.driver.irmatching;

import java.util.ArrayList;
import java.util.List;

public class MatchableMatcher {

    private final List<? extends Matchable> matchables;

    public MatchableMatcher() {
        this.matchables = new ArrayList<>();
    }

    public MatchableMatcher(List<? extends Matchable> matchables) {
        this.matchables = matchables;
    }

    public List<MatchResult> match() {
        List<MatchResult> results = new ArrayList<>();
        for (Matchable matchable : matchables) {
            MatchResult IRMethodMatchResult = matchable.match();
            if (IRMethodMatchResult.fail()) {
                results.add(IRMethodMatchResult);
            }
        }
        return results;
    }
}
