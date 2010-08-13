/*
 * @test  /nodynamiccopyright/
 * @bug 6911256 6964740 6965277 6967065
 * @author Joseph D. Darcy
 * @summary Check that -Xlint:arm warnings are generated as expected
 * @compile/ref=ArmLint.out -Xlint:arm,deprecation -XDrawDiagnostics ArmLint.java
 */

class ArmLint implements AutoCloseable {
    private static void test1() {
        try(ArmLint r1 = new ArmLint();
            ArmLint r2 = new ArmLint();
            ArmLint r3 = new ArmLint()) {
            r1.close();   // The resource's close
            r2.close(42); // *Not* the resource's close
            // r3 not referenced
        }

    }

    @SuppressWarnings("arm")
    private static void test2() {
        try(@SuppressWarnings("deprecation") AutoCloseable r4 =
            new DeprecatedAutoCloseable()) {
            // r4 not referenced
        } catch(Exception e) {
            ;
        }
    }

    /**
     * The AutoCloseable method of a resource.
     */
    @Override
    public void close () {
        return;
    }

    /**
     * <em>Not</em> the AutoCloseable method of a resource.
     */
    public void close (int arg) {
        return;
    }
}

@Deprecated
class DeprecatedAutoCloseable implements AutoCloseable {
    public DeprecatedAutoCloseable(){super();}

    @Override
    public void close () {
        return;
    }
}
