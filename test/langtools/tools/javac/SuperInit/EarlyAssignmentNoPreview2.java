/*
 * @test /nodynamiccopyright/
 * @bug 8334258
 * @summary Disallow early assignment if FLEXIBLE_CONSTRUCTORS preview feature is not enabled
 * @compile/fail/ref=EarlyAssignmentNoPreview2.out --release 24 -XDrawDiagnostics EarlyAssignmentNoPreview2.java
 */
public class EarlyAssignmentNoPreview2 {

    Runnable r;

    public EarlyAssignmentNoPreview2() {
        this(this.r = () -> System.out.println("hello"));
    }

    public EarlyAssignmentNoPreview2(Runnable r) {
    }

    public static void main(String[] args) {
        new EarlyAssignmentNoPreview2();
    }
}
