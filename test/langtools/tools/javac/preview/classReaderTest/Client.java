/*
 * @test /nodynamiccopyright/
 * @bug 8199194
 * @summary smoke test for --enabled-preview classreader support
 * @compile -XDforcePreview --enable-preview -source 11 Bar.java
 * @compile/fail/ref=Client.nopreview.out -Xlint:preview -XDrawDiagnostics Client.java
 * @compile/fail/ref=Client.preview.out -Werror -Xlint:preview -XDrawDiagnostics --enable-preview -source 11 Client.java
 */

public class Client {
    void test() {
        new Bar();
    }
}
