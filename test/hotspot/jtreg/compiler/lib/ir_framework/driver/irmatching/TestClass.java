package compiler.lib.ir_framework.driver.irmatching;

import compiler.lib.ir_framework.driver.irmatching.irmethod.AbstractIRMethodMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;

import java.util.List;

public class TestClass implements Matching {
    private final List<IRMethod> irMethods;

    public TestClass(List<IRMethod> irMethods) {
        this.irMethods = irMethods;
    }

    @Override
    public TestClassResult match() {
        TestClassResult result = new TestClassResult();
        for (IRMethod irMethod : irMethods) {
            AbstractIRMethodMatchResult abstractIRMethodMatchResult = irMethod.match();
            if (abstractIRMethodMatchResult.fail()) {
                result.addResult(abstractIRMethodMatchResult);
            }
        }
        return result;
    }
}
