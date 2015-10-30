/*
 * @test    /nodynamiccopyright/
 * @bug     6380059
 * @summary Emit warnings for proprietary packages in the boot class path
 * @author  Peter von der Ah\u00e9
 * @modules java.base/sun.security.x509
 * @compile WarnClass.java
 * @compile/fail/ref=WarnClass.out -XDrawDiagnostics  -Werror WarnClass.java
 * @compile/fail/ref=WarnClass.out -XDrawDiagnostics  -Werror -nowarn WarnClass.java
 * @compile/fail/ref=WarnClass.out -XDrawDiagnostics  -Werror -Xlint:none WarnClass.java
 */

public class WarnClass extends sun.security.x509.X509CertInfo {}
