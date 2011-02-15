/**
 * @test /nodynamiccopyright/
 * @bug 6594914
 * @summary \\@SuppressWarnings("deprecation") does not not work for the type of a variable
 * @compile/ref=T6594914b.out -XDenableSunApiLintControl -XDrawDiagnostics -Xlint:sunapi T6594914b.java
 */


class T6747671b {

    sun.misc.Lock a1; //warn

    @SuppressWarnings("sunapi")
    sun.misc.Lock a2;

    <X extends sun.misc.Lock> sun.misc.Lock m1(sun.misc.Lock a)
            throws sun.misc.CEFormatException { return null; } //warn

    @SuppressWarnings("sunapi")
    <X extends sun.misc.Lock> sun.misc.Lock m2(sun.misc.Lock a)
            throws sun.misc.CEFormatException { return null; }

    void test() {
        sun.misc.Lock a1; //warn

        @SuppressWarnings("sunapi")
        sun.misc.Lock a2;
    }
}
