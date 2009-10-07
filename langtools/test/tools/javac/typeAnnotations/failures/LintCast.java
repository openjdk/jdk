import java.util.List;

/*
 * @test /nodynamiccopyright/
 * @bug 6843077
 * @summary test that compiler doesn't warn about annotated redundant casts
 * @author Mahmood Ali
 * @compile/ref=LintCast.out -Xlint:cast -XDrawDiagnostics -source 1.7 LintCast.java
 */
class LintCast {
    void unparameterized() {
        String s = "m";
        String s1 = (String)s;
        String s2 = (@A String)s;
    }

    void parameterized() {
        List<String> l = null;
        List<String> l1 = (List<String>)l;
        List<String> l2 = (List<@A String>)l;
    }

    void array() {
        int @A [] a = null;
        int[] a1 = (int[])a;
        int[] a2 = (int @A [])a;
    }

    void sameAnnotations() {
        @A String annotated = null;
        String unannotated = null;

        // compiler ignore annotated casts even if redundant
        @A String anno1 = (@A String)annotated;

        // warn if redundant without an annotation
        String anno2 = (String)annotated;
        String unanno2 = (String)unannotated;
    }
}

@interface A { }
