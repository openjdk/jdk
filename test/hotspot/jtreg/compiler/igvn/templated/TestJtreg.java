/*
 * @test
 * @bug 8370459
 * @summary TODO: desc
 * @run main/othervm
 *      -Xcomp -ea -esa -XX:CompileThreshold=100 -XX:+UnlockExperimentalVMOptions -server
 *      -XX:CompileCommand=compileonly,*.TestJtreg::primitiveConTest_compiled
 *      -XX:CompileCommand=printcompilation,*.TestJtreg::primitiveConTest_compiled
 *      -XX:+PrintIdeal
 *      -XX:+TieredCompilation compiler.igvn.templated.TestJtreg
 */

package compiler.igvn.templated;

public class TestJtreg {
    public static void main(String[] vmFlags) {
        for (int i = 0; i < 10_000; i++) {
            primitiveConTest();
        }
    }

    public static void primitiveConTest() {
        int arg0 = 513;
        float arg1 = 3.9974846E-10f;
        float arg2 = 1.1754944E-38f;
        float arg3 = 1.1754944E-38f;
        long arg4 = 524282L;
        long arg5 = 2590086221109333669L;
        int arg6 = -16385;

        boolean v0 = primitiveConTest_compiled(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
        boolean v1 = primitiveConTest_reference(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
        if (v0 != v1) {
            StringBuilder sb = new StringBuilder();
            sb.append("wrong value: " + v0 + " vs " + v1);
            sb.append(" arg0: " + arg0);
            sb.append(" arg1: " + arg1);
            sb.append(" arg2: " + arg2);
            sb.append(" arg3: " + arg3);
            sb.append(" arg4: " + arg4);
            sb.append(" arg5: " + arg5);
            sb.append(" arg6: " + arg6);
            throw new RuntimeException("wrong value: " + sb.toString());
        }
    }

    public static boolean primitiveConTest_compiled(int arg0, float arg1, float arg2, float arg3, long arg4, long arg5, int arg6) {
        arg5 = (long)Math.min(Math.max(arg5, 8796093022205L), -3297683376527800628L);
        arg5 = (long)((arg5 & 1541862882599486797L) | -4294967288L);
        var val = (((!((arg0 == -16384)))?Float.min(arg1, (float)((char)40629)):(float)((true?(char)(Float.max(arg2, (-1.10794424E-7f * arg3))):Character.reverseBytes((char)((byte)((char)(arg4))))))) <= (float)(Long.compress(arg5, Integer.toUnsignedLong(arg6))));
        return val;
    }

    public static boolean primitiveConTest_reference(int arg0, float arg1, float arg2, float arg3, long arg4, long arg5, int arg6) {
        arg5 = (long)Math.min(Math.max(arg5, 8796093022205L), -3297683376527800628L);
        arg5 = (long)((arg5 & 1541862882599486797L) | -4294967288L);
        var val = (((!((arg0 == -16384)))?Float.min(arg1, (float)((char)40629)):(float)((true?(char)(Float.max(arg2, (-1.10794424E-7f * arg3))):Character.reverseBytes((char)((byte)((char)(arg4))))))) <= (float)(Long.compress(arg5, Integer.toUnsignedLong(arg6))));
        return val;
    }
}
