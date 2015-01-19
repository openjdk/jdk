/* /nodynamiccopyright/ */

import java.io.BufferedReader;
import java.io.FileReader;

public class TestCaseTry {

    @AliveRange(varName="o", bytecodeStart=3, bytecodeLength=8)
    @AliveRange(varName="o", bytecodeStart=15, bytecodeLength=1)
    void m0(String[] args) {
        Object o;
        try {
            o = "";
            o.hashCode();
        } catch (RuntimeException e) {}
        o = "";
    }

    @AliveRange(varName="o", bytecodeStart=3, bytecodeLength=16)
    @AliveRange(varName="o", bytecodeStart=23, bytecodeLength=8)
    @AliveRange(varName="o", bytecodeStart=35, bytecodeLength=11)
    void m1() {
        Object o;
        try {
            o = "";
            o.hashCode();
        } catch (RuntimeException e) {
        }
        finally {
            o = "finally";
            o.hashCode();
        }
        o = "";
    }

    @AliveRange(varName="o", bytecodeStart=3, bytecodeLength=16)
    @AliveRange(varName="o", bytecodeStart=23, bytecodeLength=16)
    @AliveRange(varName="o", bytecodeStart=43, bytecodeLength=11)
    void m2() {
        Object o;
        try {
            o = "";
            o.hashCode();
        } catch (RuntimeException e) {
            o = "catch";
            o.hashCode();
        }
        finally {
            o = "finally";
            o.hashCode();
        }
        o = "";
    }

    @AliveRange(varName="o", bytecodeStart=22, bytecodeLength=38)
    @AliveRange(varName="o", bytecodeStart=103, bytecodeLength=3)
    @AliveRange(varName="o", bytecodeStart=110, bytecodeLength=1)
    void m3() {
        Object o;
        try (BufferedReader br =
                  new BufferedReader(new FileReader("aFile"))) {
            o = "inside try";
            o.hashCode();
        } catch (Exception e) {}
        o = "";
    }

    @AliveRange(varName="o", bytecodeStart=12, bytecodeLength=96)
    @AliveRange(varName="o", bytecodeStart=112, bytecodeLength=1)
    void m4() {
        String o;
        try (BufferedReader br =
                  new BufferedReader(new FileReader(o = "aFile"))) {
            o = "inside try";
            o.hashCode();
        } catch (Exception e) {}
        o = "";
    }
}
