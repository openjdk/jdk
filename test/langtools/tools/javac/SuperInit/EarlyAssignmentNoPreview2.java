/*
 * @test /nodynamiccopyright/
 * @bug 8334258
 * @summary Disallow early assignment if FLEXIBLE_CONSTRUCTORS preview feature is not enabled
 * @compile/fail/ref=EarlyAssignmentNoPreview2.out -source 24 -XDrawDiagnostics EarlyAssignmentNoPreview2.java
 */
public class EarlyAssignmentNoPreview2 {

    Runnable r;

    public EarlyAssignmentNoPreview2() {
        this.r = () -> System.out.println("hello");
        this(this.r);
    }

    public EarlyAssignmentNoPreview2(Runnable r) {
    }
}
