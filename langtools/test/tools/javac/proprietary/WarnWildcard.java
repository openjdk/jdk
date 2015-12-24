/*
 * @test    /nodynamiccopyright/
 * @bug     6380059
 * @summary Emit warnings for proprietary packages in the boot class path
 * @author  Peter von der Ah\u00e9
 * @modules java.base/sun.security.x509
 * @compile WarnWildcard.java
 * @compile/fail/ref=WarnWildcard.out -XDrawDiagnostics  -Werror WarnWildcard.java
 * @compile/fail/ref=WarnWildcard.out -XDrawDiagnostics  -Werror -nowarn WarnWildcard.java
 * @compile/fail/ref=WarnWildcard.out -XDrawDiagnostics  -Werror -Xlint:none WarnWildcard.java
 */

public class WarnWildcard {
    java.util.Collection<? extends sun.security.x509.X509CertImpl> x;
}
