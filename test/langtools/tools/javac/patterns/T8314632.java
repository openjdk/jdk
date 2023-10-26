/*
 * @test /nodynamiccopyright/
 * @bug 8314632
 * @summary Intra-case dominance check fails in the presence of a guard
 * @enablePreview
 * @compile/fail/ref=T8314632.out -XDrawDiagnostics T8314632.java
 */
public class T8314632 {
    void test1(Object obj) {
        switch (obj) {
            case Float _ -> {}
            case Integer _, CharSequence _, String _ when obj.hashCode() > 0 -> { } // error
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }
    }

    void test2(Object obj) {
        switch (obj) {
            case Float _ -> {}
            case Integer _, CharSequence _, String _ -> { }
            default -> throw new IllegalStateException("Unexpected value: " + obj); // error
        }
    }

    void test3(Object obj) {
        switch (obj) {
            case Float _, CharSequence _ when obj.hashCode() > 0 -> { } // OK
            case Integer _, String _     when obj.hashCode() > 0 -> { } // OK, since the previous case is guarded
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }
    }

    void test4(Object obj) {
        switch (obj) {
            case Float _, CharSequence _ -> { } // OK
            case Integer _, String _     when obj.hashCode() > 0 -> { } // error, since the previous case is unguarded
            default -> throw new IllegalStateException("Unexpected value: " + obj);
        }
    }
}
