/* /nodynamiccopyright/ */

public class TestCaseIfElse {

    @AliveRange(varName="o", bytecodeStart=9, bytecodeLength=8)
    @AliveRange(varName="o", bytecodeStart=20, bytecodeLength=9)
    void m0(String[] args) {
        Object o;
        if (args[0] != null) {
            o = "then";
            o.hashCode();
        } else {
            o = "else";
            o.hashCode();
        }
        o = "finish";
    }

    @AliveRange(varName="o", bytecodeStart=10, bytecodeLength=8)
    @AliveRange(varName="o", bytecodeStart=21, bytecodeLength=9)
    void m1() {
        Object o;
        int i = 5;
        if (i == 5) {
            o = "then";
            o.hashCode();
        } else {
            o = "else";
            o.hashCode();
        }
        o = "finish";
    }

    @AliveRange(varName="o", bytecodeStart=10, bytecodeLength=8)
    @AliveRange(varName="o", bytecodeStart=21, bytecodeLength=9)
    void m2(String[] args) {
        Object o;
        int i = 5;
        if (i != 5) {
            o = "then";
            o.hashCode();
        } else {
            o = "else";
            o.hashCode();
        }
        o = "finish";
    }
}
