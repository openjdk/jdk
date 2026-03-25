/*
 * @test /nodynamiccopyright/
 * @bug 8334258
 * @summary Disallow early assignment if FLEXIBLE_CONSTRUCTORS preview feature is not enabled
 * @compile/fail/ref=EarlyAssignmentNoPreview1.out -source 24 -XDrawDiagnostics EarlyAssignmentNoPreview1.java
 */
public class EarlyAssignmentNoPreview1 {

    Runnable r;

    public EarlyAssignmentNoPreview1() {
        r = () -> System.out.println("hello");
        this(r);
    }

    public EarlyAssignmentNoPreview1(Runnable r) {
    }
}
