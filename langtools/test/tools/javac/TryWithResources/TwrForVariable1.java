/* @test /nodynamiccopyright/
 * @bug 7196163
 * @summary Verify that variables can be used as operands to try-with-resources
 * @compile/fail/ref=TwrForVariable1.out -source 8 -XDrawDiagnostics -Xlint:-options TwrForVariable1.java
 * @compile TwrForVariable1.java
 * @run main TwrForVariable1
 */
public class TwrForVariable1 implements AutoCloseable {
    private static int closeCount = 0;
    public static void main(String... args) {
        TwrForVariable1 v = new TwrForVariable1();

        try (v) {
            assertCloseCount(0);
        }
        try (/**@deprecated*/v) {
            assertCloseCount(1);
        }
        try (v.finalWrapper.finalField) {
            assertCloseCount(2);
        } catch (Exception ex) {
        }
        try (new TwrForVariable1() { }.finalWrapper.finalField) {
            assertCloseCount(3);
        } catch (Exception ex) {
        }
        try ((args.length > 0 ? v : new TwrForVariable1()).finalWrapper.finalField) {
            assertCloseCount(4);
        } catch (Exception ex) {
        }
        try {
            throw new CloseableException();
        } catch (CloseableException ex) {
            try (ex) {
                assertCloseCount(5);
            }
        }

        assertCloseCount(6);
    }

    static void assertCloseCount(int expectedCloseCount) {
        if (closeCount != expectedCloseCount)
            throw new RuntimeException("bad closeCount: " + closeCount +
                                       "; expected: " + expectedCloseCount);
    }

    public void close() {
        closeCount++;
    }

    final FinalWrapper finalWrapper = new FinalWrapper();

    static class FinalWrapper {
        public final AutoCloseable finalField = new AutoCloseable() {
            @Override
            public void close() throws Exception {
                closeCount++;
            }
        };
    }

    static class CloseableException extends Exception implements AutoCloseable {
        @Override
        public void close() {
            closeCount++;
        }
    }
}
