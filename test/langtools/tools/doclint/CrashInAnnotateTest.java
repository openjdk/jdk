/*
 * @test /nodynamiccopyright/
 * @bug 8266082
 * @summary javac should not crash when seeing type annotations in links
 * @compile/fail/ref=CrashInAnnotateTest.out -Xdoclint -XDrawDiagnostics CrashInAnnotateTest.java
 */

import java.util.List;

/** {@link #equals(@Deprecated Object)} */
class CrashInAnnotateTest {
}

/** {@link #compare(Object, List<List<@Deprecated Object>>)} */
class CrashInAnnotateTest2 {
    void compare(Object o, List<List<Object>> l) {}
}

/** {@link java.lang.@Deprecated Object#hashCode()} */
class CrashInAnnotateTest3 { }

/** {@link CrashInAnnotateTest4.@Deprecated Inner#aField} */
class CrashInAnnotateTest4 {
    class Inner {
        Object aField;
    }
}