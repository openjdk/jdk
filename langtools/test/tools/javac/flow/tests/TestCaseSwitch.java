/* /nodynamiccopyright/ */

public class TestCaseSwitch {

    @AliveRange(varName="o", bytecodeStart=31, bytecodeLength=16)
    @AliveRange(varName="o", bytecodeStart=50, bytecodeLength=15)
    @AliveRange(varName="o", bytecodeStart=68, bytecodeLength=1)
    @AliveRange(varName="oo", bytecodeStart=39, bytecodeLength=26)
    @AliveRange(varName="uu", bytecodeStart=59, bytecodeLength=6)
    void m1(String[] args) {
        Object o;
        switch (args.length) {
            case 0:
                    o = "0";
                    o.hashCode();
                    Object oo = "oo";
                    oo.hashCode();
                    break;
            case 1:
                    o = "1";
                    o.hashCode();
                    Object uu = "uu";
                    uu.hashCode();
                    break;
        }
        o = "return";
    }

    @AliveRange(varName="o", bytecodeStart=95, bytecodeLength=18)
    @AliveRange(varName="o", bytecodeStart=116, bytecodeLength=15)
    @AliveRange(varName="o", bytecodeStart=134, bytecodeLength=1)
    @AliveRange(varName="oo", bytecodeStart=104, bytecodeLength=27)
    @AliveRange(varName="uu", bytecodeStart=125, bytecodeLength=6)
    void m2(String[] args) {
        Object o;
        switch (args[0]) {
            case "string0":
                    o = "0";
                    o.hashCode();
                    Object oo = "oo";
                    oo.hashCode();
                    break;
            case "string1":
                    o = "1";
                    o.hashCode();
                    Object uu = "uu";
                    uu.hashCode();
                    break;
        }
        o = "return";
    }

    @AliveRange(varName="o", bytecodeStart=31, bytecodeLength=8)
    @AliveRange(varName="o", bytecodeStart=42, bytecodeLength=8)
    @AliveRange(varName="o", bytecodeStart=53, bytecodeLength=9)
    void m3(String[] args) {
        Object o;
        switch (args.length) {
            case 0:
                    o = "0";
                    o.hashCode();
                    break;
            case 1:
                    o = "1";
                    o.hashCode();
                    break;
            default:
                    o = "default";
                    o.hashCode();
        }
        o = "finish";
    }
}
