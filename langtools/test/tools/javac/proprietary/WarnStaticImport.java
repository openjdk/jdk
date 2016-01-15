/*
 * @test    /nodynamiccopyright/
 * @bug     6380059
 * @summary Emit warnings for proprietary packages in the boot class path
 * @author  Peter von der Ah\u00e9
 * @compile WarnStaticImport.java
 * @compile/fail/ref=WarnStaticImport.out -XDrawDiagnostics  -Werror WarnStaticImport.java
 * @compile/fail/ref=WarnStaticImport.out -XDrawDiagnostics  -Werror -nowarn WarnStaticImport.java
 * @compile/fail/ref=WarnStaticImport.out -XDrawDiagnostics  -Werror -Xlint:none WarnStaticImport.java
 */

import static sun.security.x509.OIDMap.getOID;

public class WarnStaticImport {}
