/*
 * @test    /nodynamiccopyright/
 * @bug     6380059
 * @summary Emit warnings for proprietary packages in the boot class path
 * @author  Peter von der Ah\u00e9
 * @compile WarnImport.java
 * @compile/fail/ref=WarnImport.out -XDrawDiagnostics  -Werror WarnImport.java
 * @compile/fail/ref=WarnImport.out -XDrawDiagnostics  -Werror -nowarn WarnImport.java
 * @compile/fail/ref=WarnImport.out -XDrawDiagnostics  -Werror -Xlint:none WarnImport.java
 */

import sun.security.x509.X509CertInfo;

public class WarnImport {}
