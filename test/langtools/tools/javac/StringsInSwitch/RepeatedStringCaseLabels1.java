/*
 * @test  /nodynamiccopyright/
 * @bug 6827009
 * @summary Check for repeated string case labels.
 * @compile/fail/ref=RSCL1_6.out -XDrawDiagnostics -source 6 RepeatedStringCaseLabels1.java
 * @compile/fail/ref=RSCL1.out -XDrawDiagnostics RepeatedStringCaseLabels1.java
 */
class RepeatedStringCaseLabels1 {
    String m(String s) {
        switch(s) {
        case "Hello World":
            return(s);
        case "Hello" + " " + "World":
            return (s + s);
        }
    }
}
