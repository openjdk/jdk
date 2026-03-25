/*
 * @test /nodynamiccopyright/
 * @bug 8334258
 * @summary Disallow early assignment if FLEXIBLE_CONSTRUCTORS preview feature is not enabled
 * @compile/fail/ref=EarlyAssignmentNoPreview3.out -source 24 -XDrawDiagnostics EarlyAssignmentNoPreview3.java
 */
public class EarlyAssignmentNoPreview3 {

    Runnable r;

    public EarlyAssignmentNoPreview3() {
        EarlyAssignmentNoPreview3.this.r = () -> System.out.println("hello");
        this(EarlyAssignmentNoPreview3.this.r);
    }

    public EarlyAssignmentNoPreview3(Runnable r) {
    }
}
