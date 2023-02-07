/*
 * @test /nodynamiccopyright/
 * @summary
 * @enablePreview
 * @compile -XDfind=all ForEachTestAllAnalyzers.java
 */
public class ForEachTestAllAnalyzers {
    private void test(Iterable<? extends R> l) {
        for (R(Object a) : l) { }
    }
    record R(Object a) {}
}