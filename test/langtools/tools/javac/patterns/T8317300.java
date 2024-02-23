/*
 * @test /nodynamiccopyright/
 * @bug 8317300
 * @summary javac erroneously allows "final" in front of a record pattern
 * @compile/fail/ref=T8317300.out -XDrawDiagnostics T8317300.java
 */
public class T8317300 {
    record Foo (int x) {}
    record Bar (Foo x) {}

    void test1(Object obj) {
        switch (obj) {
            case final Foo(int x) -> {}
            default -> {}
        }
    }

    void test2(Object obj) {
        switch (obj) {
            case Bar(final Foo(int x)) -> {}
            default -> {}
        }
    }
}
