/*
 * @test    /nodynamiccopyright/
 * @bug     6380059
 * @summary Emit warnings for proprietary packages in the boot class path
 * @author  Peter von der Ah\u00e9
 * @modules java.base/sun.security.x509
 * @compile WarnMethod.java
 * @compile/fail/ref=WarnMethod.out -XDrawDiagnostics  -Werror WarnMethod.java
 * @compile/fail/ref=WarnMethod.out -XDrawDiagnostics  -Werror -nowarn WarnMethod.java
 * @compile/fail/ref=WarnMethod.out -XDrawDiagnostics  -Werror -Xlint:none WarnMethod.java
 */

public class WarnMethod {
    public static void main(String... args) {
        System.out.println(sun.security.x509.OIDMap.getOID(""));
    }
}
