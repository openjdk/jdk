/**
 * @test /nodynamiccopyright/
 * @bug 6594914
 * @summary \\@SuppressWarnings("deprecation") does not not work for the type of a variable
 * @modules java.base/sun.security.x509
 * @compile/ref=T6594914b.out -XDenableSunApiLintControl -XDrawDiagnostics -Xlint:sunapi T6594914b.java
 */


class T6747671b {

    sun.security.x509.X509CertInfo a1; //warn

    @SuppressWarnings("sunapi")
    sun.security.x509.X509CertInfo a2;

    <X extends sun.security.x509.X509CertInfo>
    sun.security.x509.X509CertInfo m1(sun.security.x509.X509CertInfo a)
            throws sun.security.x509.CertException { return null; } //warn

    @SuppressWarnings("sunapi")
    <X extends sun.security.x509.X509CertInfo>
    sun.security.x509.X509CertInfo m2(sun.security.x509.X509CertInfo a)
            throws sun.security.x509.CertException { return null; }

    void test() {
        sun.security.x509.X509CertInfo a1; //warn

        @SuppressWarnings("sunapi")
        sun.security.x509.X509CertInfo a2;
    }
}
