/**
 * @test
 * @bug     6547131
 * @summary java.lang.ClassFormatError when using old collection API
 * @compile p/Outer.jasm p/Outer$I.jasm T.java
 * @run main T
 */

import p.*;

class SubI implements Outer.I {
    SubI() { }
    Outer.I getI() { return this; }
}

public class T {
    public static void main(String argv[]){
        SubI sub = new SubI();
        Outer.I inter = (Outer.I)sub.getI();
    }
}
