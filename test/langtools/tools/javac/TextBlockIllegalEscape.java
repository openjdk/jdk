/*
 * @test /nodynamiccopyright/
 * @bug 8227640
 * @summary Verify that illegal escapes in text blocks do not crash the javac.
 * @compile/fail/ref=TextBlockIllegalEscape.out --enable-preview -source ${jdk.version} -XDrawDiagnostics TextBlockIllegalEscape.java
 */

public class TextBlockIllegalEscape {
    static void test() {
        EQ("""
           \!
           """, "");
    }
}
