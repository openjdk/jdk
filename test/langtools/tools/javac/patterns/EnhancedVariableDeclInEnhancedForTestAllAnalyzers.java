/*
 * @test /nodynamiccopyright/
 * @summary Verify enhanced for declarations with all analyzers enabled
 * @enablePreview
 * @compile -XDfind=all EnhancedVariableDeclInEnhancedForTestAllAnalyzers.java
 */
public class EnhancedVariableDeclInEnhancedForTestAllAnalyzers {
    private void test(Iterable<? extends R> l) {
        for (R(Object a) : l) { }
    }
    record R(Object a) {}
}
