/**
 * @test
 * @enablePreview
 * @compile Matcher.java
 * @run main Matcher
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.template.Carriers;

public record Matcher(String s, int i) {

    public static void main(String... args) {
        test(new Matcher("a", 0));
    }

    public static void test(Object o) {
        //original code:
        if (o instanceof Matcher(String os, int oi)) {
            System.err.println("os: " + os);
            System.err.println("i: " + oi);
        }
    }

    //original code:
    public __matcher Matcher(String s, int i) throws Throwable { //XXX: exceptions?
        MethodType returnType = MethodType.methodType(Object.class, String.class, int.class); //TODO: return type of the Carrier constructor?
        return Carriers.factory(returnType).invoke(this.s, this.i);
//        s = this.s;
//        i = this.i;
    }

}
