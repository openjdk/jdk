/*
 * @test
 * @summary Example test to use the new test framework.
 * @library /test/lib /
 * @run driver TestCastIIAtUnsignedCmp
 */

import compiler.lib.ir_framework.*;

public class TestCastIIAtUnsignedCmp {
    float fFld;
    boolean flag;
    int m;

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Warmup(1)
    @IR(failOn = IRNode.STORE_F)
    int[] test() {
        int positiveInt = flag ? 0 : Integer.MAX_VALUE; // [0, max_int]
        int size = m & positiveInt;
        int[] arr = new int[size]; // CastII(size) // [0, max_int - 2]

        // size                       <=u positiveInt  (canonicalized condition)
        // CastII(size & positiveInt) <=u positiveInt
        //
        // Before JDK-8354282:
        //
        // After Loop Opts -> widen CastII in CastII::Value() -> same type as AndI input
        // -> ConstraintCastNode::Identity() removes CastII because not UnconditionalDependency
        // -> can now apply BoolNode::Value_cmpu_and_mask() case (1a):
        //     size & positiveInt <=u positiveInt
        // -> always true -> always take else path, and we remove the StoreF and fold the condition.
        //
        // After JDK-8354282:
        //
        // After Loop Opts -> widen CastII in CastII::Ideal() + set to non-narrowing dependency
        // -> same type as AndI input -> ConstraintCastNode::Identity() does NOT remove CastII
        // because non-narrowing dependency -> fail to apply Value_cmpu_and_mask (cannot look
        // through CastII) and thus cannot fold the condition
        if (Integer.compareUnsigned(size, positiveInt) > 0) {
            fFld = 34;
        }
        return arr;
    }
}
