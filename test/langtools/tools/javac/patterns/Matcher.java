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
        //translated to:
        String os;
        int oi;
        Object unmatched;
        try {
            var matcher = MethodHandles.publicLookup().findStatic(Matcher.class, "Matcher$Ljava\\|lang\\|String\\?$I", MethodType.methodType(Object.class, Matcher.class));
            if (o instanceof Matcher $m && (unmatched = matcher.invoke($m)) != null) {
                MethodType returnType = MethodType.methodType(Object.class, String.class, int.class); //TODO: return type of the Carrier constructor?
                    os = (String) Carriers.component(returnType, 0).invoke(unmatched);
                    oi = (int) Carriers.component(returnType, 1).invoke(unmatched);
                System.err.println("os: " + os);
                System.err.println("i: " + oi);
            }
        } catch (Throwable t) {
            throw new MatchException("", t);
        }
    }

    //original code:
    public __matcher Matcher(String s, int i) throws Throwable { //XXX: exceptions?
        MethodType returnType = MethodType.methodType(Object.class, String.class, int.class); //TODO: return type of the Carrier constructor?
        return Carriers.factory(returnType).invoke(this.s, this.i);
//        s = this.s;
//        i = this.i;
    }

//    //translated to:
//    public static Object Matcher$Ljava_lang_String$I(Matcher thiss) throws Throwable { //TODO: what about the exceptions from matchers?
//        /*original body:
//        s = this.s;
//        i = this.i;
//        */
//        MethodType returnType = MethodType.methodType(Object.class, String.class, int.class); //TODO: return type of the Carrier constructor?
//        return Carriers.factory(returnType).invoke(thiss.s, thiss.i);
//    }
    
}

//interface matcher {}