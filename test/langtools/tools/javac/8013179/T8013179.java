/*
 * @test /nodynamiccopyright/
 * @bug 8013179
 * @summary assertion failure in javac when compiling with -source 1.6 -target 1.6
 * @compile/fail/ref=T8013179.out -source 6 -target 6 -Xlint:-options -XDrawDiagnostics T8013179.java
 */

import java.lang.invoke.MethodHandle;

class T8013179 {
    static MethodHandle getNamedMember;
    public static Object getMember(String name, Object rec) throws Throwable {
        return getNamedMember.invoke(rec, name);
    }
}
