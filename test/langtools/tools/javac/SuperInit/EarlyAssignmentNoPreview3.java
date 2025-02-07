/*
 * @test /nodynamiccopyright/
 * @bug 8334258
 * @summary Disallow early assignment if FLEXIBLE_CONSTRUCTORS preview feature is not enabled
 * @compile/fail/ref=EarlyAssignmentNoPreview3.out -XDrawDiagnostics EarlyAssignmentNoPreview3.java
 */
public class EarlyAssignmentNoPreview3 {

    Runnable r;

    public EarlyAssignmentNoPreview3() {
        this(EarlyAssignmentNoPreview3.this.r = () -> System.out.println("hello"));
    }

    public EarlyAssignmentNoPreview3(Runnable r) {
    }

    public static void main(String[] args) {
        new EarlyAssignmentNoPreview3();
    }
}
