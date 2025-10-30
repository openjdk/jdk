/*
 * @test
 * @bug 8370459
 * @summary TODO: desc
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.igvn.templated.TestJtreg
 */

package compiler.igvn.templated;
// --- IMPORTS start ---
import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import jdk.test.lib.Utils;
import java.util.Random;
import compiler.lib.generators.*;
// --- IMPORTS end   ---
public class TestJtreg {
// --- CLASS_HOOK insertions start ---
// --- CLASS_HOOK insertions end   ---
    public static void main(String[] vmFlags) {
        TestFramework framework = new TestFramework(TestJtreg.class);
        framework.addFlags(vmFlags);
        framework.start();
    }
// --- LIST OF TESTS start ---
public static class LibraryRNG {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final RestrictableGenerator<Integer> GEN_BYTE = Generators.G.safeRestrict(Generators.G.ints(), Byte.MIN_VALUE, Byte.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_CHAR = Generators.G.safeRestrict(Generators.G.ints(), Character.MIN_VALUE, Character.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    private static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();
    private static final Generator<Float> GEN_FLOAT = Generators.G.floats();

    public static byte nextByte() {
        return GEN_BYTE.next().byteValue();
    }

    public static short nextShort() {
        return GEN_SHORT.next().shortValue();
    }

    public static char nextChar() {
        return (char)GEN_CHAR.next().intValue();
    }

    public static int nextInt() {
        return GEN_INT.next();
    }

    public static long nextLong() {
        return GEN_LONG.next();
    }

    public static float nextFloat() {
        return GEN_FLOAT.next();
    }

    public static double nextDouble() {
        return GEN_DOUBLE.next();
    }

    public static boolean nextBoolean() {
        return RANDOM.nextBoolean();
    }
}


@Test
public static void primitiveConTest() {
    // In each iteration, generate new random values for the method arguments.
int arg0 = LibraryRNG.nextInt();
float arg1 = LibraryRNG.nextFloat();
float arg2 = LibraryRNG.nextFloat();
float arg3 = LibraryRNG.nextFloat();
long arg4 = LibraryRNG.nextLong();
long arg5 = LibraryRNG.nextLong();
int arg6 = LibraryRNG.nextInt();
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

@DontInline
public static boolean primitiveConTest_compiled(int arg0, float arg1, float arg2, float arg3, long arg4, long arg5, int arg6) {
arg0 = constrain_arg0(arg0);
arg4 = constrain_arg4(arg4);
arg5 = constrain_arg5(arg5);
arg6 = constrain_arg6(arg6);
var val = (((!((arg0 == -16384)))?Float.min(arg1, (float)((char)40629)):(float)((true?(char)(Float.max(arg2, (-1.10794424E-7f * arg3))):Character.reverseBytes((char)((byte)((char)(arg4))))))) <= (float)(Long.compress(arg5, Integer.toUnsignedLong(arg6))));
return val;
}

@DontCompile
public static boolean primitiveConTest_reference(int arg0, float arg1, float arg2, float arg3, long arg4, long arg5, int arg6) {
arg0 = constrain_arg0(arg0);
arg4 = constrain_arg4(arg4);
arg5 = constrain_arg5(arg5);
arg6 = constrain_arg6(arg6);
var val = (((!((arg0 == -16384)))?Float.min(arg1, (float)((char)40629)):(float)((true?(char)(Float.max(arg2, (-1.10794424E-7f * arg3))):Character.reverseBytes((char)((byte)((char)(arg4))))))) <= (float)(Long.compress(arg5, Integer.toUnsignedLong(arg6))));
return val;
}

@ForceInline
public static int constrain_arg0(int v) {
v = (int)Math.min(Math.max(v, 524287), -65534);
v = (int)((v & 1073741823) | 1209351794);
return v;
}
@ForceInline
public static long constrain_arg4(long v) {
v = (long)((v & -45L) | 8704317956283722753L);
return v;
}
@ForceInline
public static long constrain_arg5(long v) {
v = (long)Math.min(Math.max(v, 8796093022205L), -3297683376527800628L);
v = (long)((v & 1541862882599486797L) | -4294967288L);
return v;
}
@ForceInline
public static int constrain_arg6(int v) {
v = (int)((v & 67108864) | -15);
return v;
}
}
