/**
 * @test
 * @compile --enable-preview -source ${jdk.version} Unnamed.java
 */
public class Unnamed {
    private void unnamedTest() {
        int _ = 0;
        int _ = 1;
        try (Lock _ = null) {
            try (Lock _ = null) {
            } catch (Exception _) {
                try {
                } catch (Exception _) {}
            }
        }
        String[] strs = null;
        for (var _ : strs) {
            for (var _ : strs) {
            }
        }
        TwoParams p1 = (_, _) -> {};
        TwoParams p2 = (var _, var _) -> {};
        R r = new R(null);
        if (r instanceof R(_)) {}
        for (int _ = 0, _ = 1; ;) {}
    }
    class Lock implements AutoCloseable {
        @Override
        public void close() {}
    }
    interface TwoParams {
        public void run(Object o1, Object o2);
    }
    record R(Object o) {}
}