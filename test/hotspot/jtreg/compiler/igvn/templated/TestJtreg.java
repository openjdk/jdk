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
public static void primitiveConTest_3() {
    // In each iteration, generate new random values for the method arguments.
long arg0_3 = LibraryRNG.nextLong();
byte arg1_3 = LibraryRNG.nextByte();
long arg2_3 = LibraryRNG.nextLong();
long arg3_3 = LibraryRNG.nextLong();
float arg4_3 = LibraryRNG.nextFloat();
long arg5_3 = LibraryRNG.nextLong();
long arg6_3 = LibraryRNG.nextLong();
long arg7_3 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_3_compiled(arg0_3, arg1_3, arg2_3, arg3_3, arg4_3, arg5_3, arg6_3, arg7_3);
    Object v1 = primitiveConTest_3_reference(arg0_3, arg1_3, arg2_3, arg3_3, arg4_3, arg5_3, arg6_3, arg7_3);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_3_compiled(long arg0_3, byte arg1_3, long arg2_3, long arg3_3, float arg4_3, long arg5_3, long arg6_3, long arg7_3) {
arg0_3 = constrain_arg0_3(arg0_3);
arg1_3 = constrain_arg1_3(arg1_3);
arg2_3 = constrain_arg2_3(arg2_3);
arg3_3 = constrain_arg3_3(arg3_3);
arg4_3 = constrain_arg4_3(arg4_3);
arg5_3 = constrain_arg5_3(arg5_3);
arg6_3 = constrain_arg6_3(arg6_3);
arg7_3 = constrain_arg7_3(arg7_3);
try {
var val = (byte)((((long)(arg0_3) ^ Long.lowestOneBit(Long.divideUnsigned((Long.sum(Byte.toUnsignedLong(arg1_3), -7868897230043692615L) * (arg2_3 >>> arg3_3)), -5809268907828220395L))) / Long.expand(Double.doubleToLongBits((double)((long)(arg4_3))), (arg5_3 - (arg6_3 >> arg7_3)))));
return checksum_3(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_3_reference(long arg0_3, byte arg1_3, long arg2_3, long arg3_3, float arg4_3, long arg5_3, long arg6_3, long arg7_3) {
arg0_3 = constrain_arg0_3(arg0_3);
arg1_3 = constrain_arg1_3(arg1_3);
arg2_3 = constrain_arg2_3(arg2_3);
arg3_3 = constrain_arg3_3(arg3_3);
arg4_3 = constrain_arg4_3(arg4_3);
arg5_3 = constrain_arg5_3(arg5_3);
arg6_3 = constrain_arg6_3(arg6_3);
arg7_3 = constrain_arg7_3(arg7_3);
try {
var val = (byte)((((long)(arg0_3) ^ Long.lowestOneBit(Long.divideUnsigned((Long.sum(Byte.toUnsignedLong(arg1_3), -7868897230043692615L) * (arg2_3 >>> arg3_3)), -5809268907828220395L))) / Long.expand(Double.doubleToLongBits((double)((long)(arg4_3))), (arg5_3 - (arg6_3 >> arg7_3)))));
return checksum_3(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_3(long v) {
v = (long)Math.min(Math.max(v, -17592186044428L), 8919659595599012086L);
v = (long)((v & 1040L) | 2305843009213693957L);
return v;
}
@ForceInline
public static byte constrain_arg1_3(byte v) {
v = (byte)Math.min(Math.max(v, (byte)1), (byte)64);
v = (byte)((v & (byte)-2) | (byte)-8);
return v;
}
@ForceInline
public static long constrain_arg2_3(long v) {
v = (long)Math.min(Math.max(v, 128L), 5692215518377908751L);
return v;
}
@ForceInline
public static long constrain_arg3_3(long v) {
v = (long)((v & -7513866745013008680L) | -33554439L);
return v;
}
@ForceInline
public static float constrain_arg4_3(float v) {
return v;
}
@ForceInline
public static long constrain_arg5_3(long v) {
v = (long)((v & 4532184779400226356L) | -8687275759302390327L);
return v;
}
@ForceInline
public static long constrain_arg6_3(long v) {
v = (long)Math.min(Math.max(v, -274877906935L), -2161410295893502775L);
v = (long)((v & 137438953487L) | -16389L);
return v;
}
@ForceInline
public static long constrain_arg7_3(long v) {
return v;
}
@ForceInline
public static Object checksum_3(byte val) {
return new Object[] {val, (Integer.compareUnsigned(val, (byte)32) > 0), (Integer.compareUnsigned(val, (byte)-1) < 0), (Integer.compareUnsigned(val, (byte)4) < 0), (Integer.compareUnsigned(val, (byte)8) < 0), (Integer.compareUnsigned(val, (byte)-1) < 0), (val == (byte)-1), (Integer.compareUnsigned(val, (byte)2) > 0), (Integer.compareUnsigned(val, (byte)-32) >= 0), (val < (byte)32), (val & (byte)-32), (val & (byte)-1), (val != (byte)8), (val == (byte)-64), (Integer.compareUnsigned(val, (byte)-2) >= 0), (Integer.compareUnsigned(val, (byte)8) <= 0), (val <= (byte)2), (Integer.compareUnsigned(val, (byte)2) < 0), (val <= (byte)-128), (Integer.compareUnsigned(val, (byte)-2) <= 0), (val & (byte)32)};
}
@Test
public static void primitiveConTest_61() {
    // In each iteration, generate new random values for the method arguments.
    Object v0 = primitiveConTest_61_compiled();
    Object v1 = primitiveConTest_61_reference();
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_61_compiled() {
try {
var val = (byte)((double)(Long.compare(-2381817845593847035L, 35184372088824L)));
return checksum_61(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_61_reference() {
try {
var val = (byte)((double)(Long.compare(-2381817845593847035L, 35184372088824L)));
return checksum_61(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static Object checksum_61(byte val) {
return new Object[] {val, (Integer.compareUnsigned(val, (byte)-128) <= 0), (val >= (byte)-32), (val != (byte)-64), (val == (byte)8), (val < (byte)-128), (Integer.compareUnsigned(val, (byte)8) > 0), (Integer.compareUnsigned(val, (byte)-64) >= 0), (val != (byte)2), (val <= (byte)-128), (val == (byte)2), (val == (byte)16), (Integer.compareUnsigned(val, (byte)-16) >= 0), (val >= (byte)-8), (val >= (byte)4), (val > (byte)2), (val == (byte)32), (Integer.compareUnsigned(val, (byte)-64) < 0), (val >= (byte)4), (val > (byte)-32), (val <= (byte)-128)};
}
@Test
public static void primitiveConTest_87() {
    // In each iteration, generate new random values for the method arguments.
int arg0_87 = LibraryRNG.nextInt();
int arg1_87 = LibraryRNG.nextInt();
int arg2_87 = LibraryRNG.nextInt();
double arg3_87 = LibraryRNG.nextDouble();
byte arg4_87 = LibraryRNG.nextByte();
    Object v0 = primitiveConTest_87_compiled(arg0_87, arg1_87, arg2_87, arg3_87, arg4_87);
    Object v1 = primitiveConTest_87_reference(arg0_87, arg1_87, arg2_87, arg3_87, arg4_87);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_87_compiled(int arg0_87, int arg1_87, int arg2_87, double arg3_87, byte arg4_87) {
arg0_87 = constrain_arg0_87(arg0_87);
arg1_87 = constrain_arg1_87(arg1_87);
arg2_87 = constrain_arg2_87(arg2_87);
arg3_87 = constrain_arg3_87(arg3_87);
arg4_87 = constrain_arg4_87(arg4_87);
try {
var val = (byte)((short)(Character.reverseBytes((Double.isInfinite(((double)(Integer.rotateRight(Integer.compareUnsigned(arg0_87, arg1_87), arg2_87)) + (-(-1.4478747182395555E-25))))?(char)((short)((((Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY) >= arg3_87)?arg4_87:(byte)-16))):(char)((float)((short)-5897))))));
return checksum_87(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_87_reference(int arg0_87, int arg1_87, int arg2_87, double arg3_87, byte arg4_87) {
arg0_87 = constrain_arg0_87(arg0_87);
arg1_87 = constrain_arg1_87(arg1_87);
arg2_87 = constrain_arg2_87(arg2_87);
arg3_87 = constrain_arg3_87(arg3_87);
arg4_87 = constrain_arg4_87(arg4_87);
try {
var val = (byte)((short)(Character.reverseBytes((Double.isInfinite(((double)(Integer.rotateRight(Integer.compareUnsigned(arg0_87, arg1_87), arg2_87)) + (-(-1.4478747182395555E-25))))?(char)((short)((((Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY) >= arg3_87)?arg4_87:(byte)-16))):(char)((float)((short)-5897))))));
return checksum_87(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_87(int v) {
return v;
}
@ForceInline
public static int constrain_arg1_87(int v) {
return v;
}
@ForceInline
public static int constrain_arg2_87(int v) {
v = (int)Math.min(Math.max(v, -30), -8388607);
v = (int)((v & -1048575) | 2048);
return v;
}
@ForceInline
public static double constrain_arg3_87(double v) {
return v;
}
@ForceInline
public static byte constrain_arg4_87(byte v) {
v = (byte)Math.min(Math.max(v, (byte)-16), (byte)16);
return v;
}
@ForceInline
public static Object checksum_87(byte val) {
return new Object[] {val, (Integer.compareUnsigned(val, (byte)-8) < 0), (val > (byte)-16), (Integer.compareUnsigned(val, (byte)-64) <= 0), (Integer.compareUnsigned(val, (byte)2) <= 0), (Integer.compareUnsigned(val, (byte)-1) < 0), (val <= (byte)8), (val < (byte)-1), (Integer.compareUnsigned(val, (byte)32) > 0), (val > (byte)4), (Integer.compareUnsigned(val, (byte)16) <= 0), (val < (byte)-2), (Integer.compareUnsigned(val, (byte)-8) >= 0), (val == (byte)-8), (val >= (byte)16), (Integer.compareUnsigned(val, (byte)-32) <= 0), (val >= (byte)2), (Integer.compareUnsigned(val, (byte)16) > 0), (val > (byte)-1), (Integer.compareUnsigned(val, (byte)8) < 0), (Integer.compareUnsigned(val, (byte)-2) >= 0)};
}
@Test
public static void primitiveConTest_133() {
    // In each iteration, generate new random values for the method arguments.
    Object v0 = primitiveConTest_133_compiled();
    Object v1 = primitiveConTest_133_reference();
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_133_compiled() {
try {
var val = (byte)(Short.compare((short)(0.0f), (short)(1073741811L)));
return checksum_133(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_133_reference() {
try {
var val = (byte)(Short.compare((short)(0.0f), (short)(1073741811L)));
return checksum_133(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static Object checksum_133(byte val) {
return new Object[] {val, (val > (byte)8), (val & (byte)2), (Integer.compareUnsigned(val, (byte)-32) >= 0), (val != (byte)16), (val > (byte)2), (Integer.compareUnsigned(val, (byte)-4) < 0), (val != (byte)-16), (Integer.compareUnsigned(val, (byte)-16) <= 0), (val <= (byte)-64), (val <= (byte)-4), (val != (byte)-16), (Integer.compareUnsigned(val, (byte)-64) >= 0), (Integer.compareUnsigned(val, (byte)64) >= 0), (val >= (byte)-2), (Integer.compareUnsigned(val, (byte)-128) < 0), (Integer.compareUnsigned(val, (byte)2) > 0), (Integer.compareUnsigned(val, (byte)-128) >= 0), (val & (byte)64), (Integer.compareUnsigned(val, (byte)2) > 0), (val == (byte)32)};
}
@Test
public static void primitiveConTest_159() {
    // In each iteration, generate new random values for the method arguments.
float arg0_159 = LibraryRNG.nextFloat();
float arg1_159 = LibraryRNG.nextFloat();
long arg2_159 = LibraryRNG.nextLong();
long arg3_159 = LibraryRNG.nextLong();
long arg4_159 = LibraryRNG.nextLong();
long arg5_159 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_159_compiled(arg0_159, arg1_159, arg2_159, arg3_159, arg4_159, arg5_159);
    Object v1 = primitiveConTest_159_reference(arg0_159, arg1_159, arg2_159, arg3_159, arg4_159, arg5_159);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_159_compiled(float arg0_159, float arg1_159, long arg2_159, long arg3_159, long arg4_159, long arg5_159) {
arg0_159 = constrain_arg0_159(arg0_159);
arg1_159 = constrain_arg1_159(arg1_159);
arg2_159 = constrain_arg2_159(arg2_159);
arg3_159 = constrain_arg3_159(arg3_159);
arg4_159 = constrain_arg4_159(arg4_159);
arg5_159 = constrain_arg5_159(arg5_159);
try {
var val = (byte)(((arg0_159 != arg1_159)?Long.max((Long.highestOneBit(arg2_159) & arg3_159), (Long.min((long)((char)((arg4_159 - -7050702070126768984L))), (8306951580393372158L >>> -4848391625862243551L)) & (8039472942601765629L | arg5_159))):(long)((int)((short)((char)118)))));
return checksum_159(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_159_reference(float arg0_159, float arg1_159, long arg2_159, long arg3_159, long arg4_159, long arg5_159) {
arg0_159 = constrain_arg0_159(arg0_159);
arg1_159 = constrain_arg1_159(arg1_159);
arg2_159 = constrain_arg2_159(arg2_159);
arg3_159 = constrain_arg3_159(arg3_159);
arg4_159 = constrain_arg4_159(arg4_159);
arg5_159 = constrain_arg5_159(arg5_159);
try {
var val = (byte)(((arg0_159 != arg1_159)?Long.max((Long.highestOneBit(arg2_159) & arg3_159), (Long.min((long)((char)((arg4_159 - -7050702070126768984L))), (8306951580393372158L >>> -4848391625862243551L)) & (8039472942601765629L | arg5_159))):(long)((int)((short)((char)118)))));
return checksum_159(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_159(float v) {
return v;
}
@ForceInline
public static float constrain_arg1_159(float v) {
return v;
}
@ForceInline
public static long constrain_arg2_159(long v) {
v = (long)((v & 6674615035032087481L) | 769946663344204538L);
return v;
}
@ForceInline
public static long constrain_arg3_159(long v) {
v = (long)Math.min(Math.max(v, -9007199254740997L), 4826611904055959073L);
v = (long)((v & -7229383799957407328L) | -8388595L);
return v;
}
@ForceInline
public static long constrain_arg4_159(long v) {
v = (long)((v & -8198L) | -6380376589806722750L);
return v;
}
@ForceInline
public static long constrain_arg5_159(long v) {
v = (long)Math.min(Math.max(v, 2589377414385879386L), 7276413079692194669L);
v = (long)((v & -7068559298532351822L) | 6753931899362875930L);
return v;
}
@ForceInline
public static Object checksum_159(byte val) {
return new Object[] {val, (Integer.compareUnsigned(val, (byte)32) >= 0), (val >= (byte)-128), (Integer.compareUnsigned(val, (byte)16) >= 0), (Integer.compareUnsigned(val, (byte)-32) <= 0), (val == (byte)-32), (val != (byte)64), (Integer.compareUnsigned(val, (byte)-64) < 0), (Integer.compareUnsigned(val, (byte)-128) <= 0), (val != (byte)-4), (Integer.compareUnsigned(val, (byte)32) >= 0), (val > (byte)4), (val < (byte)4), (val & (byte)4), (val != (byte)-128), (val > (byte)-4), (Integer.compareUnsigned(val, (byte)64) > 0), (val != (byte)-4), (Integer.compareUnsigned(val, (byte)4) >= 0), (Integer.compareUnsigned(val, (byte)-32) < 0), (val >= (byte)8)};
}
@Test
public static void primitiveConTest_209() {
    // In each iteration, generate new random values for the method arguments.
float arg0_209 = LibraryRNG.nextFloat();
double arg1_209 = LibraryRNG.nextDouble();
int arg2_209 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_209_compiled(arg0_209, arg1_209, arg2_209);
    Object v1 = primitiveConTest_209_reference(arg0_209, arg1_209, arg2_209);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_209_compiled(float arg0_209, double arg1_209, int arg2_209) {
arg0_209 = constrain_arg0_209(arg0_209);
arg1_209 = constrain_arg1_209(arg1_209);
arg2_209 = constrain_arg2_209(arg2_209);
try {
var val = (byte)(Long.rotateRight((Boolean.logicalAnd(((float)(-16385) <= arg0_209), false)?(-2817156220506248400L >>> Double.doubleToLongBits((double)(arg1_209))):Double.doubleToLongBits((double)((byte)(Integer.max(arg2_209, -16384))))), Integer.lowestOneBit(Character.compare((char)((char)8196), (char)44523))));
return checksum_209(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_209_reference(float arg0_209, double arg1_209, int arg2_209) {
arg0_209 = constrain_arg0_209(arg0_209);
arg1_209 = constrain_arg1_209(arg1_209);
arg2_209 = constrain_arg2_209(arg2_209);
try {
var val = (byte)(Long.rotateRight((Boolean.logicalAnd(((float)(-16385) <= arg0_209), false)?(-2817156220506248400L >>> Double.doubleToLongBits((double)(arg1_209))):Double.doubleToLongBits((double)((byte)(Integer.max(arg2_209, -16384))))), Integer.lowestOneBit(Character.compare((char)((char)8196), (char)44523))));
return checksum_209(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_209(float v) {
return v;
}
@ForceInline
public static double constrain_arg1_209(double v) {
return v;
}
@ForceInline
public static int constrain_arg2_209(int v) {
return v;
}
@ForceInline
public static Object checksum_209(byte val) {
return new Object[] {val, (val == (byte)-64), (val < (byte)2), (Integer.compareUnsigned(val, (byte)2) < 0), (val != (byte)-128), (Integer.compareUnsigned(val, (byte)-64) > 0), (val > (byte)8), (val > (byte)64), (val == (byte)-16), (Integer.compareUnsigned(val, (byte)4) < 0), (val != (byte)64), (val >= (byte)-1), (Integer.compareUnsigned(val, (byte)16) <= 0), (val >= (byte)64), (val == (byte)2), (val & (byte)64), (val != (byte)-4), (val & (byte)-8), (val >= (byte)4), (Integer.compareUnsigned(val, (byte)-4) >= 0), (Integer.compareUnsigned(val, (byte)-4) > 0)};
}
@Test
public static void primitiveConTest_247() {
    // In each iteration, generate new random values for the method arguments.
byte arg0_247 = LibraryRNG.nextByte();
    Object v0 = primitiveConTest_247_compiled(arg0_247);
    Object v1 = primitiveConTest_247_reference(arg0_247);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_247_compiled(byte arg0_247) {
arg0_247 = constrain_arg0_247(arg0_247);
try {
var val = (byte)((((double)(arg0_247) / (double)(1.0f)) / (double)(709608823)));
return checksum_247(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_247_reference(byte arg0_247) {
arg0_247 = constrain_arg0_247(arg0_247);
try {
var val = (byte)((((double)(arg0_247) / (double)(1.0f)) / (double)(709608823)));
return checksum_247(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static byte constrain_arg0_247(byte v) {
v = (byte)((v & (byte)-1) | (byte)4);
return v;
}
@ForceInline
public static Object checksum_247(byte val) {
return new Object[] {val, (Integer.compareUnsigned(val, (byte)-2) >= 0), (Integer.compareUnsigned(val, (byte)-32) > 0), (val >= (byte)64), (Integer.compareUnsigned(val, (byte)-8) < 0), (val & (byte)-4), (val >= (byte)-2), (val <= (byte)-16), (val < (byte)8), (val & (byte)-16), (val & (byte)-8), (val < (byte)-64), (val == (byte)1), (val < (byte)-2), (Integer.compareUnsigned(val, (byte)-1) < 0), (val < (byte)32), (val >= (byte)64), (val > (byte)32), (val & (byte)-32), (val <= (byte)-2), (Integer.compareUnsigned(val, (byte)8) < 0)};
}
@Test
public static void primitiveConTest_277() {
    // In each iteration, generate new random values for the method arguments.
long arg0_277 = LibraryRNG.nextLong();
long arg1_277 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_277_compiled(arg0_277, arg1_277);
    Object v1 = primitiveConTest_277_reference(arg0_277, arg1_277);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_277_compiled(long arg0_277, long arg1_277) {
arg0_277 = constrain_arg0_277(arg0_277);
arg1_277 = constrain_arg1_277(arg1_277);
try {
var val = (byte)(Double.longBitsToDouble((true?arg0_277:(-(arg1_277)))));
return checksum_277(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_277_reference(long arg0_277, long arg1_277) {
arg0_277 = constrain_arg0_277(arg0_277);
arg1_277 = constrain_arg1_277(arg1_277);
try {
var val = (byte)(Double.longBitsToDouble((true?arg0_277:(-(arg1_277)))));
return checksum_277(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_277(long v) {
v = (long)((v & 65524L) | -4503599627370482L);
return v;
}
@ForceInline
public static long constrain_arg1_277(long v) {
v = (long)Math.min(Math.max(v, -3526099236703754568L), 281474976710666L);
v = (long)((v & 501L) | 9133680958661030247L);
return v;
}
@ForceInline
public static Object checksum_277(byte val) {
return new Object[] {val, (Integer.compareUnsigned(val, (byte)-4) <= 0), (val <= (byte)8), (Integer.compareUnsigned(val, (byte)4) < 0), (Integer.compareUnsigned(val, (byte)-128) < 0), (Integer.compareUnsigned(val, (byte)2) >= 0), (val < (byte)2), (Integer.compareUnsigned(val, (byte)64) < 0), (val != (byte)-64), (Integer.compareUnsigned(val, (byte)64) > 0), (val != (byte)-32), (Integer.compareUnsigned(val, (byte)4) > 0), (val == (byte)-2), (Integer.compareUnsigned(val, (byte)32) <= 0), (val <= (byte)-128), (val == (byte)4), (val != (byte)8), (Integer.compareUnsigned(val, (byte)4) < 0), (Integer.compareUnsigned(val, (byte)1) < 0), (Integer.compareUnsigned(val, (byte)64) < 0), (val == (byte)-8)};
}
@Test
public static void primitiveConTest_311() {
    // In each iteration, generate new random values for the method arguments.
float arg0_311 = LibraryRNG.nextFloat();
float arg1_311 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_311_compiled(arg0_311, arg1_311);
    Object v1 = primitiveConTest_311_reference(arg0_311, arg1_311);
// could fail - don't verify.
}

@DontInline
public static Object primitiveConTest_311_compiled(float arg0_311, float arg1_311) {
arg0_311 = constrain_arg0_311(arg0_311);
arg1_311 = constrain_arg1_311(arg1_311);
try {
var val = (byte)(Long.bitCount(Double.doubleToRawLongBits((-((double)((double)((double)(Float.max(arg0_311, arg1_311)))))))));
return checksum_311(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_311_reference(float arg0_311, float arg1_311) {
arg0_311 = constrain_arg0_311(arg0_311);
arg1_311 = constrain_arg1_311(arg1_311);
try {
var val = (byte)(Long.bitCount(Double.doubleToRawLongBits((-((double)((double)((double)(Float.max(arg0_311, arg1_311)))))))));
return checksum_311(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_311(float v) {
return v;
}
@ForceInline
public static float constrain_arg1_311(float v) {
return v;
}
@ForceInline
public static Object checksum_311(byte val) {
return new Object[] {val, (Integer.compareUnsigned(val, (byte)2) >= 0), (Integer.compareUnsigned(val, (byte)32) < 0), (val != (byte)16), (Integer.compareUnsigned(val, (byte)64) <= 0), (Integer.compareUnsigned(val, (byte)16) > 0), (Integer.compareUnsigned(val, (byte)-16) > 0), (Integer.compareUnsigned(val, (byte)16) >= 0), (Integer.compareUnsigned(val, (byte)-4) < 0), (Integer.compareUnsigned(val, (byte)4) > 0), (val <= (byte)4), (Integer.compareUnsigned(val, (byte)4) > 0), (val > (byte)-1), (val == (byte)-128), (Integer.compareUnsigned(val, (byte)-4) > 0), (val < (byte)64), (val < (byte)-64), (val == (byte)-2), (val <= (byte)16), (Integer.compareUnsigned(val, (byte)-128) >= 0), (val != (byte)-32)};
}
@Test
public static void primitiveConTest_345() {
    // In each iteration, generate new random values for the method arguments.
char arg0_345 = LibraryRNG.nextChar();
float arg1_345 = LibraryRNG.nextFloat();
short arg2_345 = LibraryRNG.nextShort();
float arg3_345 = LibraryRNG.nextFloat();
short arg4_345 = LibraryRNG.nextShort();
    Object v0 = primitiveConTest_345_compiled(arg0_345, arg1_345, arg2_345, arg3_345, arg4_345);
    Object v1 = primitiveConTest_345_reference(arg0_345, arg1_345, arg2_345, arg3_345, arg4_345);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_345_compiled(char arg0_345, float arg1_345, short arg2_345, float arg3_345, short arg4_345) {
arg0_345 = constrain_arg0_345(arg0_345);
arg1_345 = constrain_arg1_345(arg1_345);
arg2_345 = constrain_arg2_345(arg2_345);
arg3_345 = constrain_arg3_345(arg3_345);
arg4_345 = constrain_arg4_345(arg4_345);
try {
var val = ((((float)(arg0_345) + arg1_345) < (float)(Long.lowestOneBit(-7658518226402587934L)))?(byte)(arg2_345):(byte)(Float.min(Float.max(1.6749901E-22f, arg3_345), ((-2.4874683E-10f / (float)(-1.25849155E-29f)) * (float)(arg4_345)))));
return checksum_345(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_345_reference(char arg0_345, float arg1_345, short arg2_345, float arg3_345, short arg4_345) {
arg0_345 = constrain_arg0_345(arg0_345);
arg1_345 = constrain_arg1_345(arg1_345);
arg2_345 = constrain_arg2_345(arg2_345);
arg3_345 = constrain_arg3_345(arg3_345);
arg4_345 = constrain_arg4_345(arg4_345);
try {
var val = ((((float)(arg0_345) + arg1_345) < (float)(Long.lowestOneBit(-7658518226402587934L)))?(byte)(arg2_345):(byte)(Float.min(Float.max(1.6749901E-22f, arg3_345), ((-2.4874683E-10f / (float)(-1.25849155E-29f)) * (float)(arg4_345)))));
return checksum_345(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static char constrain_arg0_345(char v) {
v = (char)Math.min(Math.max(v, (char)51709), (char)64366);
v = (char)((v & (char)19466) | (char)32768);
return v;
}
@ForceInline
public static float constrain_arg1_345(float v) {
return v;
}
@ForceInline
public static short constrain_arg2_345(short v) {
v = (short)Math.min(Math.max(v, (short)2049), (short)1026);
return v;
}
@ForceInline
public static float constrain_arg3_345(float v) {
return v;
}
@ForceInline
public static short constrain_arg4_345(short v) {
return v;
}
@ForceInline
public static Object checksum_345(byte val) {
return new Object[] {val, (val & (byte)2), (val <= (byte)-16), (val < (byte)64), (val >= (byte)1), (Integer.compareUnsigned(val, (byte)-16) < 0), (val > (byte)32), (val <= (byte)2), (val != (byte)-128), (val & (byte)-2), (Integer.compareUnsigned(val, (byte)16) >= 0), (val >= (byte)32), (val < (byte)-8), (Integer.compareUnsigned(val, (byte)-2) >= 0), (Integer.compareUnsigned(val, (byte)64) <= 0), (Integer.compareUnsigned(val, (byte)64) >= 0), (val >= (byte)1), (Integer.compareUnsigned(val, (byte)-4) < 0), (val > (byte)-1), (val > (byte)2), (Integer.compareUnsigned(val, (byte)64) > 0)};
}
@Test
public static void primitiveConTest_391() {
    // In each iteration, generate new random values for the method arguments.
char arg0_391 = LibraryRNG.nextChar();
boolean arg1_391 = LibraryRNG.nextBoolean();
boolean arg2_391 = LibraryRNG.nextBoolean();
char arg3_391 = LibraryRNG.nextChar();
char arg4_391 = LibraryRNG.nextChar();
    Object v0 = primitiveConTest_391_compiled(arg0_391, arg1_391, arg2_391, arg3_391, arg4_391);
    Object v1 = primitiveConTest_391_reference(arg0_391, arg1_391, arg2_391, arg3_391, arg4_391);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_391_compiled(char arg0_391, boolean arg1_391, boolean arg2_391, char arg3_391, char arg4_391) {
arg0_391 = constrain_arg0_391(arg0_391);
arg1_391 = constrain_arg1_391(arg1_391);
arg2_391 = constrain_arg2_391(arg2_391);
arg3_391 = constrain_arg3_391(arg3_391);
arg4_391 = constrain_arg4_391(arg4_391);
try {
var val = (false?arg0_391:(arg1_391?(arg2_391?arg3_391:(char)4105):arg4_391));
return checksum_391(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_391_reference(char arg0_391, boolean arg1_391, boolean arg2_391, char arg3_391, char arg4_391) {
arg0_391 = constrain_arg0_391(arg0_391);
arg1_391 = constrain_arg1_391(arg1_391);
arg2_391 = constrain_arg2_391(arg2_391);
arg3_391 = constrain_arg3_391(arg3_391);
arg4_391 = constrain_arg4_391(arg4_391);
try {
var val = (false?arg0_391:(arg1_391?(arg2_391?arg3_391:(char)4105):arg4_391));
return checksum_391(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static char constrain_arg0_391(char v) {
v = (char)Math.min(Math.max(v, (char)25776), (char)32324);
v = (char)((v & (char)261) | (char)4100);
return v;
}
@ForceInline
public static boolean constrain_arg1_391(boolean v) {
return v;
}
@ForceInline
public static boolean constrain_arg2_391(boolean v) {
return v;
}
@ForceInline
public static char constrain_arg3_391(char v) {
return v;
}
@ForceInline
public static char constrain_arg4_391(char v) {
v = (char)Math.min(Math.max(v, (char)16368), (char)65525);
return v;
}
@ForceInline
public static Object checksum_391(char val) {
return new Object[] {val, (val >= (char)15798), (val < (char)4083), (val != (char)38396), (val != (char)19893), (val == (char)54374), (val != (char)4879), (val & (char)142), (val <= (char)2047), (val >= (char)20845), (val < (char)55891), (val != (char)14205), (val < (char)51795), (val <= (char)28483), (val < (char)58031), (val < (char)137), (val > (char)250), (val < (char)1027), (val > (char)27658), (val > (char)60344), (val < (char)33)};
}
@Test
public static void primitiveConTest_437() {
    // In each iteration, generate new random values for the method arguments.
double arg0_437 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_437_compiled(arg0_437);
    Object v1 = primitiveConTest_437_reference(arg0_437);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_437_compiled(double arg0_437) {
arg0_437 = constrain_arg0_437(arg0_437);
try {
var val = (char)(arg0_437);
return checksum_437(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_437_reference(double arg0_437) {
arg0_437 = constrain_arg0_437(arg0_437);
try {
var val = (char)(arg0_437);
return checksum_437(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static double constrain_arg0_437(double v) {
return v;
}
@ForceInline
public static Object checksum_437(char val) {
return new Object[] {val, (val > (char)48695), (val <= (char)55174), (val == (char)1038), (val & (char)39797), (val & (char)32779), (val >= (char)42735), (val != (char)13804), (val & (char)19716), (val > (char)38698), (val != (char)16396), (val & (char)118), (val == (char)15911), (val & (char)247), (val < (char)30007), (val & (char)25132), (val == (char)135), (val > (char)57497), (val != (char)65377), (val <= (char)51571), (val & (char)57892)};
}
@Test
public static void primitiveConTest_467() {
    // In each iteration, generate new random values for the method arguments.
long arg0_467 = LibraryRNG.nextLong();
float arg1_467 = LibraryRNG.nextFloat();
float arg2_467 = LibraryRNG.nextFloat();
float arg3_467 = LibraryRNG.nextFloat();
long arg4_467 = LibraryRNG.nextLong();
short arg5_467 = LibraryRNG.nextShort();
    Object v0 = primitiveConTest_467_compiled(arg0_467, arg1_467, arg2_467, arg3_467, arg4_467, arg5_467);
    Object v1 = primitiveConTest_467_reference(arg0_467, arg1_467, arg2_467, arg3_467, arg4_467, arg5_467);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_467_compiled(long arg0_467, float arg1_467, float arg2_467, float arg3_467, long arg4_467, short arg5_467) {
arg0_467 = constrain_arg0_467(arg0_467);
arg1_467 = constrain_arg1_467(arg1_467);
arg2_467 = constrain_arg2_467(arg2_467);
arg3_467 = constrain_arg3_467(arg3_467);
arg4_467 = constrain_arg4_467(arg4_467);
arg5_467 = constrain_arg5_467(arg5_467);
try {
var val = (char)(((((-5259922795001149457L >= arg0_467)?arg1_467:Float.min((-0.0f % arg2_467), arg3_467)) < 0.0f)?(short)(Long.sum((-(15L)), arg4_467)):(short)(arg5_467)));
return checksum_467(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_467_reference(long arg0_467, float arg1_467, float arg2_467, float arg3_467, long arg4_467, short arg5_467) {
arg0_467 = constrain_arg0_467(arg0_467);
arg1_467 = constrain_arg1_467(arg1_467);
arg2_467 = constrain_arg2_467(arg2_467);
arg3_467 = constrain_arg3_467(arg3_467);
arg4_467 = constrain_arg4_467(arg4_467);
arg5_467 = constrain_arg5_467(arg5_467);
try {
var val = (char)(((((-5259922795001149457L >= arg0_467)?arg1_467:Float.min((-0.0f % arg2_467), arg3_467)) < 0.0f)?(short)(Long.sum((-(15L)), arg4_467)):(short)(arg5_467)));
return checksum_467(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_467(long v) {
v = (long)((v & -6284693558782570924L) | 33554427L);
return v;
}
@ForceInline
public static float constrain_arg1_467(float v) {
return v;
}
@ForceInline
public static float constrain_arg2_467(float v) {
return v;
}
@ForceInline
public static float constrain_arg3_467(float v) {
return v;
}
@ForceInline
public static long constrain_arg4_467(long v) {
v = (long)((v & 2305843009213693964L) | 1125899906842620L);
return v;
}
@ForceInline
public static short constrain_arg5_467(short v) {
v = (short)((v & (short)14693) | (short)1026);
return v;
}
@ForceInline
public static Object checksum_467(char val) {
return new Object[] {val, (val == (char)1032), (val != (char)252), (val == (char)7856), (val > (char)6469), (val >= (char)2048), (val != (char)32756), (val <= (char)126), (val < (char)54879), (val != (char)8185), (val > (char)44345), (val <= (char)16388), (val >= (char)505), (val != (char)1033), (val & (char)127), (val < (char)65174), (val > (char)40155), (val == (char)41296), (val > (char)26020), (val < (char)32658), (val > (char)17950)};
}
@Test
public static void primitiveConTest_517() {
    // In each iteration, generate new random values for the method arguments.
long arg0_517 = LibraryRNG.nextLong();
long arg1_517 = LibraryRNG.nextLong();
long arg2_517 = LibraryRNG.nextLong();
long arg3_517 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_517_compiled(arg0_517, arg1_517, arg2_517, arg3_517);
    Object v1 = primitiveConTest_517_reference(arg0_517, arg1_517, arg2_517, arg3_517);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_517_compiled(long arg0_517, long arg1_517, long arg2_517, long arg3_517) {
arg0_517 = constrain_arg0_517(arg0_517);
arg1_517 = constrain_arg1_517(arg1_517);
arg2_517 = constrain_arg2_517(arg2_517);
arg3_517 = constrain_arg3_517(arg3_517);
try {
var val = (char)((char)(Long.max((long)(((-1073741828L >> Long.max(arg0_517, -137438953479L)) / arg1_517)), (-(Long.max(arg2_517, arg3_517))))));
return checksum_517(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_517_reference(long arg0_517, long arg1_517, long arg2_517, long arg3_517) {
arg0_517 = constrain_arg0_517(arg0_517);
arg1_517 = constrain_arg1_517(arg1_517);
arg2_517 = constrain_arg2_517(arg2_517);
arg3_517 = constrain_arg3_517(arg3_517);
try {
var val = (char)((char)(Long.max((long)(((-1073741828L >> Long.max(arg0_517, -137438953479L)) / arg1_517)), (-(Long.max(arg2_517, arg3_517))))));
return checksum_517(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_517(long v) {
v = (long)((v & -1152921504606846969L) | 4294967289L);
return v;
}
@ForceInline
public static long constrain_arg1_517(long v) {
v = (long)Math.min(Math.max(v, 8053994392708924253L), 18014398509481991L);
return v;
}
@ForceInline
public static long constrain_arg2_517(long v) {
return v;
}
@ForceInline
public static long constrain_arg3_517(long v) {
v = (long)Math.min(Math.max(v, -8321507834262366627L), -576460752303423486L);
return v;
}
@ForceInline
public static Object checksum_517(char val) {
return new Object[] {val, (val > (char)4085), (val >= (char)112), (val < (char)47617), (val == (char)5484), (val != (char)38599), (val <= (char)4084), (val & (char)6567), (val < (char)8198), (val & (char)8441), (val != (char)527), (val > (char)37675), (val & (char)25549), (val <= (char)42808), (val == (char)16395), (val >= (char)41059), (val <= (char)62712), (val & (char)68), (val <= (char)31), (val == (char)55768), (val == (char)2060)};
}
@Test
public static void primitiveConTest_559() {
    // In each iteration, generate new random values for the method arguments.
int arg0_559 = LibraryRNG.nextInt();
double arg1_559 = LibraryRNG.nextDouble();
double arg2_559 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_559_compiled(arg0_559, arg1_559, arg2_559);
    Object v1 = primitiveConTest_559_reference(arg0_559, arg1_559, arg2_559);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_559_compiled(int arg0_559, double arg1_559, double arg2_559) {
arg0_559 = constrain_arg0_559(arg0_559);
arg1_559 = constrain_arg1_559(arg1_559);
arg2_559 = constrain_arg2_559(arg2_559);
try {
var val = (char)((short)(Integer.compare(Integer.numberOfTrailingZeros(arg0_559), (Byte.compareUnsigned((byte)((char)(Float.NEGATIVE_INFINITY)), (byte)(8589934579L)) >> Integer.numberOfLeadingZeros(Boolean.compare((7891799378404855256L >= 3297315824337104661L), Double.isNaN(Double.sum(arg1_559, arg2_559))))))));
return checksum_559(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_559_reference(int arg0_559, double arg1_559, double arg2_559) {
arg0_559 = constrain_arg0_559(arg0_559);
arg1_559 = constrain_arg1_559(arg1_559);
arg2_559 = constrain_arg2_559(arg2_559);
try {
var val = (char)((short)(Integer.compare(Integer.numberOfTrailingZeros(arg0_559), (Byte.compareUnsigned((byte)((char)(Float.NEGATIVE_INFINITY)), (byte)(8589934579L)) >> Integer.numberOfLeadingZeros(Boolean.compare((7891799378404855256L >= 3297315824337104661L), Double.isNaN(Double.sum(arg1_559, arg2_559))))))));
return checksum_559(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_559(int v) {
v = (int)Math.min(Math.max(v, -15887671), 536870911);
return v;
}
@ForceInline
public static double constrain_arg1_559(double v) {
return v;
}
@ForceInline
public static double constrain_arg2_559(double v) {
return v;
}
@ForceInline
public static Object checksum_559(char val) {
return new Object[] {val, (val < (char)250), (val & (char)55309), (val > (char)7612), (val <= (char)65051), (val == (char)42022), (val & (char)5809), (val > (char)1010), (val == (char)2), (val > (char)17930), (val & (char)32760), (val & (char)511), (val <= (char)19152), (val >= (char)32755), (val >= (char)42), (val <= (char)40399), (val & (char)8511), (val < (char)32783), (val <= (char)36632), (val & (char)51223), (val > (char)1021)};
}
@Test
public static void primitiveConTest_597() {
    // In each iteration, generate new random values for the method arguments.
int arg0_597 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_597_compiled(arg0_597);
    Object v1 = primitiveConTest_597_reference(arg0_597);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_597_compiled(int arg0_597) {
arg0_597 = constrain_arg0_597(arg0_597);
try {
var val = Character.reverseBytes(Character.reverseBytes(Character.reverseBytes((char)(Short.reverseBytes((short)((-((float)(Long.remainderUnsigned(2127172027373322684L, (525L >> (long)((byte)(arg0_597)))))))))))));
return checksum_597(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_597_reference(int arg0_597) {
arg0_597 = constrain_arg0_597(arg0_597);
try {
var val = Character.reverseBytes(Character.reverseBytes(Character.reverseBytes((char)(Short.reverseBytes((short)((-((float)(Long.remainderUnsigned(2127172027373322684L, (525L >> (long)((byte)(arg0_597)))))))))))));
return checksum_597(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_597(int v) {
v = (int)((v & -66) | 5);
return v;
}
@ForceInline
public static Object checksum_597(char val) {
return new Object[] {val, (val >= (char)496), (val & (char)18), (val >= (char)513), (val > (char)76), (val < (char)53483), (val >= (char)250), (val != (char)51717), (val < (char)16394), (val != (char)8198), (val >= (char)3392), (val & (char)4096), (val & (char)65531), (val == (char)51264), (val < (char)266), (val > (char)2047), (val <= (char)46157), (val < (char)14736), (val < (char)43505), (val & (char)61574), (val != (char)25045)};
}
@Test
public static void primitiveConTest_627() {
    // In each iteration, generate new random values for the method arguments.
long arg0_627 = LibraryRNG.nextLong();
byte arg1_627 = LibraryRNG.nextByte();
boolean arg2_627 = LibraryRNG.nextBoolean();
double arg3_627 = LibraryRNG.nextDouble();
float arg4_627 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_627_compiled(arg0_627, arg1_627, arg2_627, arg3_627, arg4_627);
    Object v1 = primitiveConTest_627_reference(arg0_627, arg1_627, arg2_627, arg3_627, arg4_627);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_627_compiled(long arg0_627, byte arg1_627, boolean arg2_627, double arg3_627, float arg4_627) {
arg0_627 = constrain_arg0_627(arg0_627);
arg1_627 = constrain_arg1_627(arg1_627);
arg2_627 = constrain_arg2_627(arg2_627);
arg3_627 = constrain_arg3_627(arg3_627);
arg4_627 = constrain_arg4_627(arg4_627);
try {
var val = (char)((float)((byte)(((Integer.rotateLeft(Long.signum(arg0_627), Integer.sum(Byte.compare(arg1_627, (byte)(72057594037927947L)), Boolean.compare(false, arg2_627))) / (int)(Float.min((float)((char)(arg3_627)), arg4_627))) - 32768))));
return checksum_627(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_627_reference(long arg0_627, byte arg1_627, boolean arg2_627, double arg3_627, float arg4_627) {
arg0_627 = constrain_arg0_627(arg0_627);
arg1_627 = constrain_arg1_627(arg1_627);
arg2_627 = constrain_arg2_627(arg2_627);
arg3_627 = constrain_arg3_627(arg3_627);
arg4_627 = constrain_arg4_627(arg4_627);
try {
var val = (char)((float)((byte)(((Integer.rotateLeft(Long.signum(arg0_627), Integer.sum(Byte.compare(arg1_627, (byte)(72057594037927947L)), Boolean.compare(false, arg2_627))) / (int)(Float.min((float)((char)(arg3_627)), arg4_627))) - 32768))));
return checksum_627(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_627(long v) {
v = (long)Math.min(Math.max(v, -68719476728L), 1099511627774L);
v = (long)((v & -134217744L) | -30L);
return v;
}
@ForceInline
public static byte constrain_arg1_627(byte v) {
return v;
}
@ForceInline
public static boolean constrain_arg2_627(boolean v) {
return v;
}
@ForceInline
public static double constrain_arg3_627(double v) {
return v;
}
@ForceInline
public static float constrain_arg4_627(float v) {
return v;
}
@ForceInline
public static Object checksum_627(char val) {
return new Object[] {val, (val <= (char)7588), (val <= (char)37), (val & (char)65528), (val < (char)21), (val > (char)4087), (val >= (char)53695), (val > (char)18726), (val & (char)43816), (val != (char)77), (val == (char)34122), (val > (char)39445), (val <= (char)51670), (val != (char)16380), (val < (char)40207), (val & (char)118), (val <= (char)43197), (val & (char)248), (val < (char)65522), (val < (char)79), (val > (char)43507)};
}
@Test
public static void primitiveConTest_673() {
    // In each iteration, generate new random values for the method arguments.
float arg0_673 = LibraryRNG.nextFloat();
float arg1_673 = LibraryRNG.nextFloat();
float arg2_673 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_673_compiled(arg0_673, arg1_673, arg2_673);
    Object v1 = primitiveConTest_673_reference(arg0_673, arg1_673, arg2_673);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_673_compiled(float arg0_673, float arg1_673, float arg2_673) {
arg0_673 = constrain_arg0_673(arg0_673);
arg1_673 = constrain_arg1_673(arg1_673);
arg2_673 = constrain_arg2_673(arg2_673);
try {
var val = (Float.isFinite((((274877906943L > -36028797018963968L)?arg0_673:arg1_673) % arg2_673))?(char)((char)((false?17179869197L:Long.divideUnsigned(-9223372036854775796L, -4503599627370481L)))):(char)((double)((byte)((float)(Short.compareUnsigned((short)((byte)16), (short)16384))))));
return checksum_673(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_673_reference(float arg0_673, float arg1_673, float arg2_673) {
arg0_673 = constrain_arg0_673(arg0_673);
arg1_673 = constrain_arg1_673(arg1_673);
arg2_673 = constrain_arg2_673(arg2_673);
try {
var val = (Float.isFinite((((274877906943L > -36028797018963968L)?arg0_673:arg1_673) % arg2_673))?(char)((char)((false?17179869197L:Long.divideUnsigned(-9223372036854775796L, -4503599627370481L)))):(char)((double)((byte)((float)(Short.compareUnsigned((short)((byte)16), (short)16384))))));
return checksum_673(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_673(float v) {
return v;
}
@ForceInline
public static float constrain_arg1_673(float v) {
return v;
}
@ForceInline
public static float constrain_arg2_673(float v) {
return v;
}
@ForceInline
public static Object checksum_673(char val) {
return new Object[] {val, (val & (char)4093), (val >= (char)48371), (val >= (char)1009), (val > (char)72), (val & (char)263), (val != (char)21874), (val != (char)42530), (val != (char)38368), (val < (char)62083), (val != (char)2457), (val < (char)16368), (val >= (char)45575), (val != (char)34582), (val >= (char)4094), (val != (char)28801), (val == (char)32752), (val & (char)16369), (val >= (char)53439), (val < (char)32757), (val > (char)32752)};
}
@Test
public static void primitiveConTest_711() {
    // In each iteration, generate new random values for the method arguments.
double arg0_711 = LibraryRNG.nextDouble();
float arg1_711 = LibraryRNG.nextFloat();
boolean arg2_711 = LibraryRNG.nextBoolean();
double arg3_711 = LibraryRNG.nextDouble();
long arg4_711 = LibraryRNG.nextLong();
long arg5_711 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_711_compiled(arg0_711, arg1_711, arg2_711, arg3_711, arg4_711, arg5_711);
    Object v1 = primitiveConTest_711_reference(arg0_711, arg1_711, arg2_711, arg3_711, arg4_711, arg5_711);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_711_compiled(double arg0_711, float arg1_711, boolean arg2_711, double arg3_711, long arg4_711, long arg5_711) {
arg0_711 = constrain_arg0_711(arg0_711);
arg1_711 = constrain_arg1_711(arg1_711);
arg2_711 = constrain_arg2_711(arg2_711);
arg3_711 = constrain_arg3_711(arg3_711);
arg4_711 = constrain_arg4_711(arg4_711);
arg5_711 = constrain_arg5_711(arg5_711);
try {
var val = (Double.isNaN((double)(arg0_711))?(char)(arg1_711):(arg2_711?(char)(arg3_711):(char)(((arg4_711 % (long)((byte)-4)) & (-5701747359212868316L % arg5_711)))));
return checksum_711(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_711_reference(double arg0_711, float arg1_711, boolean arg2_711, double arg3_711, long arg4_711, long arg5_711) {
arg0_711 = constrain_arg0_711(arg0_711);
arg1_711 = constrain_arg1_711(arg1_711);
arg2_711 = constrain_arg2_711(arg2_711);
arg3_711 = constrain_arg3_711(arg3_711);
arg4_711 = constrain_arg4_711(arg4_711);
arg5_711 = constrain_arg5_711(arg5_711);
try {
var val = (Double.isNaN((double)(arg0_711))?(char)(arg1_711):(arg2_711?(char)(arg3_711):(char)(((arg4_711 % (long)((byte)-4)) & (-5701747359212868316L % arg5_711)))));
return checksum_711(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static double constrain_arg0_711(double v) {
return v;
}
@ForceInline
public static float constrain_arg1_711(float v) {
return v;
}
@ForceInline
public static boolean constrain_arg2_711(boolean v) {
return v;
}
@ForceInline
public static double constrain_arg3_711(double v) {
return v;
}
@ForceInline
public static long constrain_arg4_711(long v) {
v = (long)Math.min(Math.max(v, -6254230833330120916L), -8147589845835292434L);
return v;
}
@ForceInline
public static long constrain_arg5_711(long v) {
v = (long)Math.min(Math.max(v, 4398046511101L), 65542L);
v = (long)((v & -4264034815983950899L) | 4511284651126843739L);
return v;
}
@ForceInline
public static Object checksum_711(char val) {
return new Object[] {val, (val < (char)53251), (val & (char)60), (val < (char)45977), (val < (char)43226), (val != (char)41583), (val >= (char)516), (val >= (char)246), (val & (char)20320), (val <= (char)8206), (val > (char)37728), (val < (char)2041), (val == (char)58), (val == (char)1030), (val <= (char)11902), (val == (char)2055), (val <= (char)522), (val == (char)58166), (val == (char)27876), (val & (char)18363), (val & (char)8326)};
}
@Test
public static void primitiveConTest_761() {
    // In each iteration, generate new random values for the method arguments.
long arg0_761 = LibraryRNG.nextLong();
long arg1_761 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_761_compiled(arg0_761, arg1_761);
    Object v1 = primitiveConTest_761_reference(arg0_761, arg1_761);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_761_compiled(long arg0_761, long arg1_761) {
arg0_761 = constrain_arg0_761(arg0_761);
arg1_761 = constrain_arg1_761(arg1_761);
try {
var val = (char)(Integer.min((int)(Float.max((-1.0f / (-0.0f * -0.0f)), (float)((byte)2))), (Long.signum(Long.sum(arg0_761, -8796093022213L)) & Short.toUnsignedInt((short)((double)((byte)(arg1_761)))))));
return checksum_761(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_761_reference(long arg0_761, long arg1_761) {
arg0_761 = constrain_arg0_761(arg0_761);
arg1_761 = constrain_arg1_761(arg1_761);
try {
var val = (char)(Integer.min((int)(Float.max((-1.0f / (-0.0f * -0.0f)), (float)((byte)2))), (Long.signum(Long.sum(arg0_761, -8796093022213L)) & Short.toUnsignedInt((short)((double)((byte)(arg1_761)))))));
return checksum_761(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_761(long v) {
v = (long)((v & 3958739512663289728L) | -4294967299L);
return v;
}
@ForceInline
public static long constrain_arg1_761(long v) {
v = (long)((v & 1940216041692009010L) | 16777207L);
return v;
}
@ForceInline
public static Object checksum_761(char val) {
return new Object[] {val, (val & (char)6287), (val < (char)32780), (val & (char)4099), (val < (char)4111), (val <= (char)7726), (val < (char)3300), (val & (char)65484), (val == (char)23917), (val < (char)7), (val >= (char)20636), (val == (char)32780), (val >= (char)78), (val == (char)20854), (val < (char)116), (val < (char)253), (val & (char)246), (val != (char)58847), (val >= (char)4096), (val >= (char)52962), (val != (char)55138)};
}
@Test
public static void primitiveConTest_795() {
    // In each iteration, generate new random values for the method arguments.
double arg0_795 = LibraryRNG.nextDouble();
double arg1_795 = LibraryRNG.nextDouble();
int arg2_795 = LibraryRNG.nextInt();
float arg3_795 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_795_compiled(arg0_795, arg1_795, arg2_795, arg3_795);
    Object v1 = primitiveConTest_795_reference(arg0_795, arg1_795, arg2_795, arg3_795);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_795_compiled(double arg0_795, double arg1_795, int arg2_795, float arg3_795) {
arg0_795 = constrain_arg0_795(arg0_795);
arg1_795 = constrain_arg1_795(arg1_795);
arg2_795 = constrain_arg2_795(arg2_795);
arg3_795 = constrain_arg3_795(arg3_795);
try {
var val = ((arg0_795 <= (double)((short)(-4.9471692E-23f)))?Short.reverseBytes((short)(arg1_795)):Short.reverseBytes((short)((false?(byte)(Integer.expand(arg2_795, Boolean.compare(false, false))):(byte)(arg3_795)))));
return checksum_795(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_795_reference(double arg0_795, double arg1_795, int arg2_795, float arg3_795) {
arg0_795 = constrain_arg0_795(arg0_795);
arg1_795 = constrain_arg1_795(arg1_795);
arg2_795 = constrain_arg2_795(arg2_795);
arg3_795 = constrain_arg3_795(arg3_795);
try {
var val = ((arg0_795 <= (double)((short)(-4.9471692E-23f)))?Short.reverseBytes((short)(arg1_795)):Short.reverseBytes((short)((false?(byte)(Integer.expand(arg2_795, Boolean.compare(false, false))):(byte)(arg3_795)))));
return checksum_795(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static double constrain_arg0_795(double v) {
return v;
}
@ForceInline
public static double constrain_arg1_795(double v) {
return v;
}
@ForceInline
public static int constrain_arg2_795(int v) {
v = (int)Math.min(Math.max(v, -4097), 131072);
return v;
}
@ForceInline
public static float constrain_arg3_795(float v) {
return v;
}
@ForceInline
public static Object checksum_795(short val) {
return new Object[] {val, (Integer.compareUnsigned(val, (short)1026) > 0), (Integer.compareUnsigned(val, (short)-16) > 0), (val > (short)-28612), (Integer.compareUnsigned(val, (short)-14671) <= 0), (val != (short)-8392), (Integer.compareUnsigned(val, (short)12508) <= 0), (val == (short)4095), (val >= (short)9), (val > (short)2049), (val != (short)8), (Integer.compareUnsigned(val, (short)31) < 0), (val != (short)-127), (val != (short)-5), (Integer.compareUnsigned(val, (short)-26937) > 0), (Integer.compareUnsigned(val, (short)-21134) <= 0), (val > (short)-3644), (val & (short)8), (Integer.compareUnsigned(val, (short)-23394) > 0), (val < (short)-16382), (Integer.compareUnsigned(val, (short)-21068) > 0)};
}
@Test
public static void primitiveConTest_837() {
    // In each iteration, generate new random values for the method arguments.
byte arg0_837 = LibraryRNG.nextByte();
    Object v0 = primitiveConTest_837_compiled(arg0_837);
    Object v1 = primitiveConTest_837_reference(arg0_837);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_837_compiled(byte arg0_837) {
arg0_837 = constrain_arg0_837(arg0_837);
try {
var val = (short)((short)((~(Integer.rotateLeft(1317719396, Character.compare(((-9007199254740992L > -36028797018963980L)?((1.0 >= Double.longBitsToDouble(9221120237041090560L /* NaN */))?(char)((double)(1.7976931348623157E308)):(char)42435):(char)45), (char)(arg0_837)))))));
return checksum_837(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_837_reference(byte arg0_837) {
arg0_837 = constrain_arg0_837(arg0_837);
try {
var val = (short)((short)((~(Integer.rotateLeft(1317719396, Character.compare(((-9007199254740992L > -36028797018963980L)?((1.0 >= Double.longBitsToDouble(9221120237041090560L /* NaN */))?(char)((double)(1.7976931348623157E308)):(char)42435):(char)45), (char)(arg0_837)))))));
return checksum_837(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static byte constrain_arg0_837(byte v) {
v = (byte)Math.min(Math.max(v, (byte)-8), (byte)8);
return v;
}
@ForceInline
public static Object checksum_837(short val) {
return new Object[] {val, (val != (short)-1025), (val > (short)20059), (val > (short)-64), (val <= (short)4094), (val >= (short)8194), (val >= (short)20372), (Integer.compareUnsigned(val, (short)26914) > 0), (Integer.compareUnsigned(val, (short)255) >= 0), (val < (short)-254), (val != (short)-13847), (Integer.compareUnsigned(val, (short)-33) > 0), (val > (short)-29325), (Integer.compareUnsigned(val, (short)7) <= 0), (val & (short)9056), (val < (short)7619), (val < (short)32766), (Integer.compareUnsigned(val, (short)11963) > 0), (val <= (short)-2048), (val <= (short)-4098), (val != (short)64)};
}
@Test
public static void primitiveConTest_867() {
    // In each iteration, generate new random values for the method arguments.
double arg0_867 = LibraryRNG.nextDouble();
double arg1_867 = LibraryRNG.nextDouble();
double arg2_867 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_867_compiled(arg0_867, arg1_867, arg2_867);
    Object v1 = primitiveConTest_867_reference(arg0_867, arg1_867, arg2_867);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_867_compiled(double arg0_867, double arg1_867, double arg2_867) {
arg0_867 = constrain_arg0_867(arg0_867);
arg1_867 = constrain_arg1_867(arg1_867);
arg2_867 = constrain_arg2_867(arg2_867);
try {
var val = (short)((short)(((int)((byte)((long)(Double.sum(arg0_867, (arg1_867 + (arg2_867 - Double.NEGATIVE_INFINITY)))))) / (int)((char)70))));
return checksum_867(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_867_reference(double arg0_867, double arg1_867, double arg2_867) {
arg0_867 = constrain_arg0_867(arg0_867);
arg1_867 = constrain_arg1_867(arg1_867);
arg2_867 = constrain_arg2_867(arg2_867);
try {
var val = (short)((short)(((int)((byte)((long)(Double.sum(arg0_867, (arg1_867 + (arg2_867 - Double.NEGATIVE_INFINITY)))))) / (int)((char)70))));
return checksum_867(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static double constrain_arg0_867(double v) {
return v;
}
@ForceInline
public static double constrain_arg1_867(double v) {
return v;
}
@ForceInline
public static double constrain_arg2_867(double v) {
return v;
}
@ForceInline
public static Object checksum_867(short val) {
return new Object[] {val, (val > (short)0), (val != (short)-66), (val < (short)-2046), (val > (short)-4098), (Integer.compareUnsigned(val, (short)-126) <= 0), (Integer.compareUnsigned(val, (short)-1023) <= 0), (val <= (short)5), (val == (short)10), (Integer.compareUnsigned(val, (short)6) <= 0), (val == (short)-27579), (val != (short)-28326), (val <= (short)-511), (val < (short)16386), (val == (short)-511), (val < (short)4098), (val < (short)2050), (val == (short)1022), (val <= (short)22514), (val >= (short)-16), (val >= (short)-2047)};
}
@Test
public static void primitiveConTest_905() {
    // In each iteration, generate new random values for the method arguments.
long arg0_905 = LibraryRNG.nextLong();
double arg1_905 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_905_compiled(arg0_905, arg1_905);
    Object v1 = primitiveConTest_905_reference(arg0_905, arg1_905);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_905_compiled(long arg0_905, double arg1_905) {
arg0_905 = constrain_arg0_905(arg0_905);
arg1_905 = constrain_arg1_905(arg1_905);
try {
var val = Short.reverseBytes((short)((double)((Double.NEGATIVE_INFINITY + (Double.longBitsToDouble(arg0_905) * (Double.longBitsToDouble(9221120237041090560L /* NaN */) * arg1_905))))));
return checksum_905(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_905_reference(long arg0_905, double arg1_905) {
arg0_905 = constrain_arg0_905(arg0_905);
arg1_905 = constrain_arg1_905(arg1_905);
try {
var val = Short.reverseBytes((short)((double)((Double.NEGATIVE_INFINITY + (Double.longBitsToDouble(arg0_905) * (Double.longBitsToDouble(9221120237041090560L /* NaN */) * arg1_905))))));
return checksum_905(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_905(long v) {
v = (long)Math.min(Math.max(v, -8288558655632754035L), -5885932594730135480L);
return v;
}
@ForceInline
public static double constrain_arg1_905(double v) {
return v;
}
@ForceInline
public static Object checksum_905(short val) {
return new Object[] {val, (val > (short)63), (val > (short)8193), (val < (short)-18526), (Integer.compareUnsigned(val, (short)-14) < 0), (Integer.compareUnsigned(val, (short)5874) > 0), (val != (short)2048), (val == (short)34), (val > (short)-9172), (Integer.compareUnsigned(val, (short)16) <= 0), (val & (short)-16501), (val > (short)0), (val < (short)-129), (val != (short)-10), (val <= (short)7729), (val <= (short)16386), (Integer.compareUnsigned(val, (short)28840) >= 0), (val != (short)-258), (val < (short)-4), (val > (short)-4094), (val == (short)-17)};
}
@Test
public static void primitiveConTest_939() {
    // In each iteration, generate new random values for the method arguments.
int arg0_939 = LibraryRNG.nextInt();
float arg1_939 = LibraryRNG.nextFloat();
byte arg2_939 = LibraryRNG.nextByte();
    Object v0 = primitiveConTest_939_compiled(arg0_939, arg1_939, arg2_939);
    Object v1 = primitiveConTest_939_reference(arg0_939, arg1_939, arg2_939);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_939_compiled(int arg0_939, float arg1_939, byte arg2_939) {
arg0_939 = constrain_arg0_939(arg0_939);
arg1_939 = constrain_arg1_939(arg1_939);
arg2_939 = constrain_arg2_939(arg2_939);
try {
var val = (((int)((char)(-4611686018427387893L)) != Integer.divideUnsigned((int)((char)2960), (int)((byte)(arg0_939))))?(short)((-((Float.isNaN(arg1_939)?(Float.min(3.4028235E38f, 1.4E-45f) % Float.intBitsToFloat(-2050)):(float)(arg2_939))))):(short)-18);
return checksum_939(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_939_reference(int arg0_939, float arg1_939, byte arg2_939) {
arg0_939 = constrain_arg0_939(arg0_939);
arg1_939 = constrain_arg1_939(arg1_939);
arg2_939 = constrain_arg2_939(arg2_939);
try {
var val = (((int)((char)(-4611686018427387893L)) != Integer.divideUnsigned((int)((char)2960), (int)((byte)(arg0_939))))?(short)((-((Float.isNaN(arg1_939)?(Float.min(3.4028235E38f, 1.4E-45f) % Float.intBitsToFloat(-2050)):(float)(arg2_939))))):(short)-18);
return checksum_939(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_939(int v) {
return v;
}
@ForceInline
public static float constrain_arg1_939(float v) {
return v;
}
@ForceInline
public static byte constrain_arg2_939(byte v) {
return v;
}
@ForceInline
public static Object checksum_939(short val) {
return new Object[] {val, (Integer.compareUnsigned(val, (short)-18) > 0), (Integer.compareUnsigned(val, (short)-1022) > 0), (val & (short)18), (Integer.compareUnsigned(val, (short)-17) <= 0), (val < (short)-63), (Integer.compareUnsigned(val, (short)-255) < 0), (val & (short)-2047), (val > (short)709), (Integer.compareUnsigned(val, (short)33) < 0), (val <= (short)-16069), (val == (short)-4), (val < (short)-5), (val == (short)21518), (Integer.compareUnsigned(val, (short)-1025) < 0), (val < (short)-14570), (Integer.compareUnsigned(val, (short)-3589) <= 0), (val < (short)-16382), (val == (short)-15697), (val <= (short)28126), (Integer.compareUnsigned(val, (short)-63) > 0)};
}
@Test
public static void primitiveConTest_977() {
    // In each iteration, generate new random values for the method arguments.
int arg0_977 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_977_compiled(arg0_977);
    Object v1 = primitiveConTest_977_reference(arg0_977);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_977_compiled(int arg0_977) {
arg0_977 = constrain_arg0_977(arg0_977);
try {
var val = (short)((short)((((Float.POSITIVE_INFINITY % (float)((byte)-2)) == (float)((short)-2050))?(byte)(arg0_977):(byte)-32)));
return checksum_977(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_977_reference(int arg0_977) {
arg0_977 = constrain_arg0_977(arg0_977);
try {
var val = (short)((short)((((Float.POSITIVE_INFINITY % (float)((byte)-2)) == (float)((short)-2050))?(byte)(arg0_977):(byte)-32)));
return checksum_977(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_977(int v) {
v = (int)((v & 1035059234) | -16384);
return v;
}
@ForceInline
public static Object checksum_977(short val) {
return new Object[] {val, (Integer.compareUnsigned(val, (short)2702) <= 0), (val == (short)8190), (val == (short)511), (Integer.compareUnsigned(val, (short)-3771) < 0), (Integer.compareUnsigned(val, (short)-9) > 0), (val < (short)23787), (Integer.compareUnsigned(val, (short)-25195) < 0), (val >= (short)15307), (val > (short)8193), (Integer.compareUnsigned(val, (short)-32686) <= 0), (Integer.compareUnsigned(val, (short)-1835) > 0), (Integer.compareUnsigned(val, (short)-32767) <= 0), (Integer.compareUnsigned(val, (short)-15) >= 0), (val < (short)-513), (val != (short)-8194), (Integer.compareUnsigned(val, (short)-33) <= 0), (Integer.compareUnsigned(val, (short)-19041) > 0), (val != (short)-2049), (val & (short)4098), (val & (short)-23290)};
}
@Test
public static void primitiveConTest_1007() {
    // In each iteration, generate new random values for the method arguments.
boolean arg0_1007 = LibraryRNG.nextBoolean();
    Object v0 = primitiveConTest_1007_compiled(arg0_1007);
    Object v1 = primitiveConTest_1007_reference(arg0_1007);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1007_compiled(boolean arg0_1007) {
arg0_1007 = constrain_arg0_1007(arg0_1007);
try {
var val = (short)((byte)((char)((short)((long)(Integer.numberOfTrailingZeros((-((arg0_1007?Integer.bitCount(-16777214):Short.compare(Short.reverseBytes((short)13838), (short)255))))))))));
return checksum_1007(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1007_reference(boolean arg0_1007) {
arg0_1007 = constrain_arg0_1007(arg0_1007);
try {
var val = (short)((byte)((char)((short)((long)(Integer.numberOfTrailingZeros((-((arg0_1007?Integer.bitCount(-16777214):Short.compare(Short.reverseBytes((short)13838), (short)255))))))))));
return checksum_1007(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static boolean constrain_arg0_1007(boolean v) {
return v;
}
@ForceInline
public static Object checksum_1007(short val) {
return new Object[] {val, (Integer.compareUnsigned(val, (short)-3402) <= 0), (Integer.compareUnsigned(val, (short)10926) >= 0), (val & (short)32766), (val > (short)4503), (Integer.compareUnsigned(val, (short)14) <= 0), (val != (short)-4778), (val <= (short)-18), (Integer.compareUnsigned(val, (short)8194) < 0), (Integer.compareUnsigned(val, (short)-16384) >= 0), (val < (short)-8192), (val == (short)-16383), (Integer.compareUnsigned(val, (short)-27726) < 0), (Integer.compareUnsigned(val, (short)18899) > 0), (val == (short)-34), (val < (short)-1024), (Integer.compareUnsigned(val, (short)-2) >= 0), (val == (short)-127), (val != (short)32766), (val == (short)258), (val >= (short)-6305)};
}
@Test
public static void primitiveConTest_1037() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1037 = LibraryRNG.nextLong();
long arg1_1037 = LibraryRNG.nextLong();
long arg2_1037 = LibraryRNG.nextLong();
long arg3_1037 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1037_compiled(arg0_1037, arg1_1037, arg2_1037, arg3_1037);
    Object v1 = primitiveConTest_1037_reference(arg0_1037, arg1_1037, arg2_1037, arg3_1037);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1037_compiled(long arg0_1037, long arg1_1037, long arg2_1037, long arg3_1037) {
arg0_1037 = constrain_arg0_1037(arg0_1037);
arg1_1037 = constrain_arg1_1037(arg1_1037);
arg2_1037 = constrain_arg2_1037(arg2_1037);
arg3_1037 = constrain_arg3_1037(arg3_1037);
try {
var val = (short)(Long.numberOfTrailingZeros(((~(arg0_1037)) | (Long.lowestOneBit(arg1_1037) - Long.max((long)((byte)((byte)((long)((-(Double.longBitsToDouble(-5824885609220165866L))))))), Long.lowestOneBit(Long.rotateLeft((false?arg2_1037:arg3_1037), (Float.floatToIntBits(3.4028235E38f) % 16777218))))))));
return checksum_1037(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1037_reference(long arg0_1037, long arg1_1037, long arg2_1037, long arg3_1037) {
arg0_1037 = constrain_arg0_1037(arg0_1037);
arg1_1037 = constrain_arg1_1037(arg1_1037);
arg2_1037 = constrain_arg2_1037(arg2_1037);
arg3_1037 = constrain_arg3_1037(arg3_1037);
try {
var val = (short)(Long.numberOfTrailingZeros(((~(arg0_1037)) | (Long.lowestOneBit(arg1_1037) - Long.max((long)((byte)((byte)((long)((-(Double.longBitsToDouble(-5824885609220165866L))))))), Long.lowestOneBit(Long.rotateLeft((false?arg2_1037:arg3_1037), (Float.floatToIntBits(3.4028235E38f) % 16777218))))))));
return checksum_1037(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1037(long v) {
v = (long)Math.min(Math.max(v, 2251799813685263L), 576460752303423485L);
v = (long)((v & -7255208220839219660L) | -6000030823834957032L);
return v;
}
@ForceInline
public static long constrain_arg1_1037(long v) {
return v;
}
@ForceInline
public static long constrain_arg2_1037(long v) {
v = (long)((v & -549755813903L) | -1099511627790L);
return v;
}
@ForceInline
public static long constrain_arg3_1037(long v) {
v = (long)Math.min(Math.max(v, -4852968900054688579L), 3358003921919541047L);
v = (long)((v & -8859868340869142333L) | 4194315L);
return v;
}
@ForceInline
public static Object checksum_1037(short val) {
return new Object[] {val, (Integer.compareUnsigned(val, (short)-14) >= 0), (Integer.compareUnsigned(val, (short)-16383) > 0), (val > (short)15), (Integer.compareUnsigned(val, (short)-511) >= 0), (val < (short)33), (val < (short)5), (val < (short)-10616), (val & (short)26472), (Integer.compareUnsigned(val, (short)-30) > 0), (val == (short)-12658), (Integer.compareUnsigned(val, (short)24569) > 0), (val & (short)-31104), (Integer.compareUnsigned(val, (short)-12988) <= 0), (val < (short)16382), (val & (short)63), (val < (short)-9), (Integer.compareUnsigned(val, (short)-511) <= 0), (Integer.compareUnsigned(val, (short)16404) <= 0), (val <= (short)20187), (Integer.compareUnsigned(val, (short)-32768) > 0)};
}
@Test
public static void primitiveConTest_1079() {
    // In each iteration, generate new random values for the method arguments.
short arg0_1079 = LibraryRNG.nextShort();
short arg1_1079 = LibraryRNG.nextShort();
short arg2_1079 = LibraryRNG.nextShort();
float arg3_1079 = LibraryRNG.nextFloat();
float arg4_1079 = LibraryRNG.nextFloat();
float arg5_1079 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_1079_compiled(arg0_1079, arg1_1079, arg2_1079, arg3_1079, arg4_1079, arg5_1079);
    Object v1 = primitiveConTest_1079_reference(arg0_1079, arg1_1079, arg2_1079, arg3_1079, arg4_1079, arg5_1079);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1079_compiled(short arg0_1079, short arg1_1079, short arg2_1079, float arg3_1079, float arg4_1079, float arg5_1079) {
arg0_1079 = constrain_arg0_1079(arg0_1079);
arg1_1079 = constrain_arg1_1079(arg1_1079);
arg2_1079 = constrain_arg2_1079(arg2_1079);
arg3_1079 = constrain_arg3_1079(arg3_1079);
arg4_1079 = constrain_arg4_1079(arg4_1079);
arg5_1079 = constrain_arg5_1079(arg5_1079);
try {
var val = (Boolean.logicalXor(false, (Short.toUnsignedInt(arg0_1079) == (int)((float)(Short.reverseBytes(arg1_1079)))))?arg2_1079:(short)((1.1754944E-38f * ((arg3_1079 % arg4_1079) / arg5_1079))));
return checksum_1079(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1079_reference(short arg0_1079, short arg1_1079, short arg2_1079, float arg3_1079, float arg4_1079, float arg5_1079) {
arg0_1079 = constrain_arg0_1079(arg0_1079);
arg1_1079 = constrain_arg1_1079(arg1_1079);
arg2_1079 = constrain_arg2_1079(arg2_1079);
arg3_1079 = constrain_arg3_1079(arg3_1079);
arg4_1079 = constrain_arg4_1079(arg4_1079);
arg5_1079 = constrain_arg5_1079(arg5_1079);
try {
var val = (Boolean.logicalXor(false, (Short.toUnsignedInt(arg0_1079) == (int)((float)(Short.reverseBytes(arg1_1079)))))?arg2_1079:(short)((1.1754944E-38f * ((arg3_1079 % arg4_1079) / arg5_1079))));
return checksum_1079(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static short constrain_arg0_1079(short v) {
v = (short)Math.min(Math.max(v, (short)-1025), (short)2436);
return v;
}
@ForceInline
public static short constrain_arg1_1079(short v) {
v = (short)((v & (short)-21797) | (short)-7);
return v;
}
@ForceInline
public static short constrain_arg2_1079(short v) {
v = (short)Math.min(Math.max(v, (short)-4), (short)-15889);
return v;
}
@ForceInline
public static float constrain_arg3_1079(float v) {
return v;
}
@ForceInline
public static float constrain_arg4_1079(float v) {
return v;
}
@ForceInline
public static float constrain_arg5_1079(float v) {
return v;
}
@ForceInline
public static Object checksum_1079(short val) {
return new Object[] {val, (val <= (short)-33), (val == (short)-33), (val == (short)2709), (Integer.compareUnsigned(val, (short)-32) <= 0), (val != (short)16598), (val >= (short)258), (val < (short)1022), (Integer.compareUnsigned(val, (short)-3) <= 0), (Integer.compareUnsigned(val, (short)-5022) <= 0), (Integer.compareUnsigned(val, (short)-16) < 0), (Integer.compareUnsigned(val, (short)128) < 0), (Integer.compareUnsigned(val, (short)12901) >= 0), (val >= (short)16983), (val & (short)4), (val != (short)-20538), (Integer.compareUnsigned(val, (short)5) > 0), (val < (short)1), (Integer.compareUnsigned(val, (short)8368) > 0), (val != (short)31407), (Integer.compareUnsigned(val, (short)-2291) < 0)};
}
@Test
public static void primitiveConTest_1129() {
    // In each iteration, generate new random values for the method arguments.
double arg0_1129 = LibraryRNG.nextDouble();
char arg1_1129 = LibraryRNG.nextChar();
long arg2_1129 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1129_compiled(arg0_1129, arg1_1129, arg2_1129);
    Object v1 = primitiveConTest_1129_reference(arg0_1129, arg1_1129, arg2_1129);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1129_compiled(double arg0_1129, char arg1_1129, long arg2_1129) {
arg0_1129 = constrain_arg0_1129(arg0_1129);
arg1_1129 = constrain_arg1_1129(arg1_1129);
arg2_1129 = constrain_arg2_1129(arg2_1129);
try {
var val = (short)(Character.reverseBytes(((-0.0f <= Float.NEGATIVE_INFINITY)?(char)(((1.7976931348623157E308 < 0.0)?(char)(arg0_1129):arg1_1129)):(char)((double)((char)((char)(arg2_1129)))))));
return checksum_1129(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1129_reference(double arg0_1129, char arg1_1129, long arg2_1129) {
arg0_1129 = constrain_arg0_1129(arg0_1129);
arg1_1129 = constrain_arg1_1129(arg1_1129);
arg2_1129 = constrain_arg2_1129(arg2_1129);
try {
var val = (short)(Character.reverseBytes(((-0.0f <= Float.NEGATIVE_INFINITY)?(char)(((1.7976931348623157E308 < 0.0)?(char)(arg0_1129):arg1_1129)):(char)((double)((char)((char)(arg2_1129)))))));
return checksum_1129(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static double constrain_arg0_1129(double v) {
return v;
}
@ForceInline
public static char constrain_arg1_1129(char v) {
v = (char)Math.min(Math.max(v, (char)32), (char)57322);
v = (char)((v & (char)15473) | (char)513);
return v;
}
@ForceInline
public static long constrain_arg2_1129(long v) {
v = (long)((v & -2593393390151553198L) | -1735223102809001162L);
return v;
}
@ForceInline
public static Object checksum_1129(short val) {
return new Object[] {val, (Integer.compareUnsigned(val, (short)-129) <= 0), (val & (short)17), (Integer.compareUnsigned(val, (short)-4096) < 0), (Integer.compareUnsigned(val, (short)1022) < 0), (Integer.compareUnsigned(val, (short)-1026) <= 0), (Integer.compareUnsigned(val, (short)126) < 0), (Integer.compareUnsigned(val, (short)-2046) < 0), (val < (short)32767), (val > (short)-258), (Integer.compareUnsigned(val, (short)-23558) < 0), (Integer.compareUnsigned(val, (short)1023) >= 0), (Integer.compareUnsigned(val, (short)19454) < 0), (Integer.compareUnsigned(val, (short)-126) > 0), (Integer.compareUnsigned(val, (short)23902) < 0), (val != (short)8194), (val < (short)1760), (val == (short)510), (val >= (short)2053), (Integer.compareUnsigned(val, (short)16) < 0), (Integer.compareUnsigned(val, (short)63) > 0)};
}
@Test
public static void primitiveConTest_1167() {
    // In each iteration, generate new random values for the method arguments.
int arg0_1167 = LibraryRNG.nextInt();
long arg1_1167 = LibraryRNG.nextLong();
int arg2_1167 = LibraryRNG.nextInt();
int arg3_1167 = LibraryRNG.nextInt();
int arg4_1167 = LibraryRNG.nextInt();
int arg5_1167 = LibraryRNG.nextInt();
byte arg6_1167 = LibraryRNG.nextByte();
    Object v0 = primitiveConTest_1167_compiled(arg0_1167, arg1_1167, arg2_1167, arg3_1167, arg4_1167, arg5_1167, arg6_1167);
    Object v1 = primitiveConTest_1167_reference(arg0_1167, arg1_1167, arg2_1167, arg3_1167, arg4_1167, arg5_1167, arg6_1167);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1167_compiled(int arg0_1167, long arg1_1167, int arg2_1167, int arg3_1167, int arg4_1167, int arg5_1167, byte arg6_1167) {
arg0_1167 = constrain_arg0_1167(arg0_1167);
arg1_1167 = constrain_arg1_1167(arg1_1167);
arg2_1167 = constrain_arg2_1167(arg2_1167);
arg3_1167 = constrain_arg3_1167(arg3_1167);
arg4_1167 = constrain_arg4_1167(arg4_1167);
arg5_1167 = constrain_arg5_1167(arg5_1167);
arg6_1167 = constrain_arg6_1167(arg6_1167);
try {
var val = Integer.rotateRight(arg0_1167, Integer.sum(Integer.rotateRight(Long.numberOfTrailingZeros(arg1_1167), Integer.min(arg2_1167, arg3_1167)), ((arg4_1167 * arg5_1167) & Byte.compareUnsigned((byte)32, arg6_1167))));
return checksum_1167(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1167_reference(int arg0_1167, long arg1_1167, int arg2_1167, int arg3_1167, int arg4_1167, int arg5_1167, byte arg6_1167) {
arg0_1167 = constrain_arg0_1167(arg0_1167);
arg1_1167 = constrain_arg1_1167(arg1_1167);
arg2_1167 = constrain_arg2_1167(arg2_1167);
arg3_1167 = constrain_arg3_1167(arg3_1167);
arg4_1167 = constrain_arg4_1167(arg4_1167);
arg5_1167 = constrain_arg5_1167(arg5_1167);
arg6_1167 = constrain_arg6_1167(arg6_1167);
try {
var val = Integer.rotateRight(arg0_1167, Integer.sum(Integer.rotateRight(Long.numberOfTrailingZeros(arg1_1167), Integer.min(arg2_1167, arg3_1167)), ((arg4_1167 * arg5_1167) & Byte.compareUnsigned((byte)32, arg6_1167))));
return checksum_1167(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_1167(int v) {
v = (int)Math.min(Math.max(v, -1048575), 1800095966);
v = (int)((v & 131070) | -127);
return v;
}
@ForceInline
public static long constrain_arg1_1167(long v) {
v = (long)Math.min(Math.max(v, -2251799813685239L), -860664129956023214L);
v = (long)((v & -2251799813685232L) | -134217720L);
return v;
}
@ForceInline
public static int constrain_arg2_1167(int v) {
v = (int)Math.min(Math.max(v, 16777218), 197435578);
v = (int)((v & 3) | 1954442503);
return v;
}
@ForceInline
public static int constrain_arg3_1167(int v) {
v = (int)((v & 16383) | -357439775);
return v;
}
@ForceInline
public static int constrain_arg4_1167(int v) {
v = (int)Math.min(Math.max(v, 539167128), 8388607);
return v;
}
@ForceInline
public static int constrain_arg5_1167(int v) {
return v;
}
@ForceInline
public static byte constrain_arg6_1167(byte v) {
v = (byte)Math.min(Math.max(v, (byte)1), (byte)16);
return v;
}
@ForceInline
public static Object checksum_1167(int val) {
return new Object[] {val, (val < 1092604704), (Integer.compareUnsigned(val, -33554431) > 0), (val > 8193), (val <= -1438752595), (Integer.compareUnsigned(val, 194773638) <= 0), (val <= -692952212), (val == -2014182729), (val < -1), (val > -16382), (Integer.compareUnsigned(val, -65) <= 0), (val > -10), (Integer.compareUnsigned(val, -67108862) < 0), (Integer.compareUnsigned(val, -4) > 0), (val >= -1092688939), (val != 2000712092), (val & 268435455), (Integer.compareUnsigned(val, -715462947) > 0), (Integer.compareUnsigned(val, 131074) <= 0), (Integer.compareUnsigned(val, 33554430) > 0), (Integer.compareUnsigned(val, 536870914) < 0)};
}
@Test
public static void primitiveConTest_1221() {
    // In each iteration, generate new random values for the method arguments.
float arg0_1221 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_1221_compiled(arg0_1221);
    Object v1 = primitiveConTest_1221_reference(arg0_1221);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1221_compiled(float arg0_1221) {
arg0_1221 = constrain_arg0_1221(arg0_1221);
try {
var val = (int)(Long.signum((long)((char)((char)((byte)((byte)((short)((char)((short)((byte)(arg0_1221)))))))))));
return checksum_1221(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1221_reference(float arg0_1221) {
arg0_1221 = constrain_arg0_1221(arg0_1221);
try {
var val = (int)(Long.signum((long)((char)((char)((byte)((byte)((short)((char)((short)((byte)(arg0_1221)))))))))));
return checksum_1221(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_1221(float v) {
return v;
}
@ForceInline
public static Object checksum_1221(int val) {
return new Object[] {val, (val <= 30), (val > -7), (val >= -297400654), (val > -866003726), (val & -1412014295), (val >= -16384), (val != 16777218), (val <= -1025), (val != -927283827), (val > -8193), (val >= -1187721750), (val <= 1554444344), (val <= -34), (val > 5), (Integer.compareUnsigned(val, 1159064947) < 0), (Integer.compareUnsigned(val, 131073) < 0), (val < 1579120240), (Integer.compareUnsigned(val, -1101951894) > 0), (val != 258), (val > 1023)};
}
@Test
public static void primitiveConTest_1251() {
    // In each iteration, generate new random values for the method arguments.
float arg0_1251 = LibraryRNG.nextFloat();
float arg1_1251 = LibraryRNG.nextFloat();
long arg2_1251 = LibraryRNG.nextLong();
long arg3_1251 = LibraryRNG.nextLong();
double arg4_1251 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_1251_compiled(arg0_1251, arg1_1251, arg2_1251, arg3_1251, arg4_1251);
    Object v1 = primitiveConTest_1251_reference(arg0_1251, arg1_1251, arg2_1251, arg3_1251, arg4_1251);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1251_compiled(float arg0_1251, float arg1_1251, long arg2_1251, long arg3_1251, double arg4_1251) {
arg0_1251 = constrain_arg0_1251(arg0_1251);
arg1_1251 = constrain_arg1_1251(arg1_1251);
arg2_1251 = constrain_arg2_1251(arg2_1251);
arg3_1251 = constrain_arg3_1251(arg3_1251);
arg4_1251 = constrain_arg4_1251(arg4_1251);
try {
var val = Byte.toUnsignedInt((byte)(((-(Float.min(Float.max(Float.sum(arg0_1251, Float.float16ToFloat((short)-31)), arg1_1251), (float)((Long.reverseBytes(arg2_1251) % Long.sum(8897444233102110880L, arg3_1251)))))) - (float)(arg4_1251))));
return checksum_1251(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1251_reference(float arg0_1251, float arg1_1251, long arg2_1251, long arg3_1251, double arg4_1251) {
arg0_1251 = constrain_arg0_1251(arg0_1251);
arg1_1251 = constrain_arg1_1251(arg1_1251);
arg2_1251 = constrain_arg2_1251(arg2_1251);
arg3_1251 = constrain_arg3_1251(arg3_1251);
arg4_1251 = constrain_arg4_1251(arg4_1251);
try {
var val = Byte.toUnsignedInt((byte)(((-(Float.min(Float.max(Float.sum(arg0_1251, Float.float16ToFloat((short)-31)), arg1_1251), (float)((Long.reverseBytes(arg2_1251) % Long.sum(8897444233102110880L, arg3_1251)))))) - (float)(arg4_1251))));
return checksum_1251(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_1251(float v) {
return v;
}
@ForceInline
public static float constrain_arg1_1251(float v) {
return v;
}
@ForceInline
public static long constrain_arg2_1251(long v) {
v = (long)Math.min(Math.max(v, -1048563L), 3051721295166027582L);
v = (long)((v & 7864708616251854791L) | 536870923L);
return v;
}
@ForceInline
public static long constrain_arg3_1251(long v) {
v = (long)Math.min(Math.max(v, 116L), -2854404044095914016L);
v = (long)((v & -2097163L) | 274877906936L);
return v;
}
@ForceInline
public static double constrain_arg4_1251(double v) {
return v;
}
@ForceInline
public static Object checksum_1251(int val) {
return new Object[] {val, (val >= 1024), (val >= -402428328), (val >= 922832893), (val >= -2143886525), (val != -1058677938), (val >= -107918774), (val >= 2048), (val == 8388610), (Integer.compareUnsigned(val, -1048575) > 0), (val > -1478205213), (Integer.compareUnsigned(val, 17) > 0), (val == -65), (val == 1026), (val < -1969699180), (val <= -990446473), (Integer.compareUnsigned(val, -1870900995) > 0), (Integer.compareUnsigned(val, -63) > 0), (Integer.compareUnsigned(val, -1025) < 0), (val >= 535175992), (val >= -162549043)};
}
@Test
public static void primitiveConTest_1297() {
    // In each iteration, generate new random values for the method arguments.
byte arg0_1297 = LibraryRNG.nextByte();
int arg1_1297 = LibraryRNG.nextInt();
int arg2_1297 = LibraryRNG.nextInt();
int arg3_1297 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_1297_compiled(arg0_1297, arg1_1297, arg2_1297, arg3_1297);
    Object v1 = primitiveConTest_1297_reference(arg0_1297, arg1_1297, arg2_1297, arg3_1297);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1297_compiled(byte arg0_1297, int arg1_1297, int arg2_1297, int arg3_1297) {
arg0_1297 = constrain_arg0_1297(arg0_1297);
arg1_1297 = constrain_arg1_1297(arg1_1297);
arg2_1297 = constrain_arg2_1297(arg2_1297);
arg3_1297 = constrain_arg3_1297(arg3_1297);
try {
var val = Integer.divideUnsigned((~((int)((byte)(arg0_1297)))), ((arg1_1297 - (arg2_1297 >> -170425136)) + Integer.numberOfLeadingZeros(arg3_1297)));
return checksum_1297(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1297_reference(byte arg0_1297, int arg1_1297, int arg2_1297, int arg3_1297) {
arg0_1297 = constrain_arg0_1297(arg0_1297);
arg1_1297 = constrain_arg1_1297(arg1_1297);
arg2_1297 = constrain_arg2_1297(arg2_1297);
arg3_1297 = constrain_arg3_1297(arg3_1297);
try {
var val = Integer.divideUnsigned((~((int)((byte)(arg0_1297)))), ((arg1_1297 - (arg2_1297 >> -170425136)) + Integer.numberOfLeadingZeros(arg3_1297)));
return checksum_1297(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static byte constrain_arg0_1297(byte v) {
return v;
}
@ForceInline
public static int constrain_arg1_1297(int v) {
v = (int)Math.min(Math.max(v, 1), 511);
return v;
}
@ForceInline
public static int constrain_arg2_1297(int v) {
v = (int)Math.min(Math.max(v, -2082958265), -268435458);
return v;
}
@ForceInline
public static int constrain_arg3_1297(int v) {
v = (int)Math.min(Math.max(v, 536870910), -536870914);
return v;
}
@ForceInline
public static Object checksum_1297(int val) {
return new Object[] {val, (val <= 511), (val < 33554432), (Integer.compareUnsigned(val, -4096) <= 0), (val >= -4098), (val >= -8193), (val == 459103817), (Integer.compareUnsigned(val, -114571095) < 0), (Integer.compareUnsigned(val, 16777217) <= 0), (Integer.compareUnsigned(val, 1361651261) >= 0), (val > 127), (Integer.compareUnsigned(val, 4) >= 0), (val <= 34), (val != 62), (Integer.compareUnsigned(val, 16383) < 0), (val != 33), (val < 790402554), (Integer.compareUnsigned(val, -147273618) < 0), (val >= -129), (val & 2049), (val > -63)};
}
@Test
public static void primitiveConTest_1339() {
    // In each iteration, generate new random values for the method arguments.
int arg0_1339 = LibraryRNG.nextInt();
int arg1_1339 = LibraryRNG.nextInt();
long arg2_1339 = LibraryRNG.nextLong();
long arg3_1339 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1339_compiled(arg0_1339, arg1_1339, arg2_1339, arg3_1339);
    Object v1 = primitiveConTest_1339_reference(arg0_1339, arg1_1339, arg2_1339, arg3_1339);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1339_compiled(int arg0_1339, int arg1_1339, long arg2_1339, long arg3_1339) {
arg0_1339 = constrain_arg0_1339(arg0_1339);
arg1_1339 = constrain_arg1_1339(arg1_1339);
arg2_1339 = constrain_arg2_1339(arg2_1339);
arg3_1339 = constrain_arg3_1339(arg3_1339);
try {
var val = (Short.compare((short)((byte)(Byte.toUnsignedLong((byte)-64))), (short)((char)(Integer.reverse(arg0_1339)))) * Short.compareUnsigned((short)(arg1_1339), (short)((arg2_1339 ^ arg3_1339))));
return checksum_1339(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1339_reference(int arg0_1339, int arg1_1339, long arg2_1339, long arg3_1339) {
arg0_1339 = constrain_arg0_1339(arg0_1339);
arg1_1339 = constrain_arg1_1339(arg1_1339);
arg2_1339 = constrain_arg2_1339(arg2_1339);
arg3_1339 = constrain_arg3_1339(arg3_1339);
try {
var val = (Short.compare((short)((byte)(Byte.toUnsignedLong((byte)-64))), (short)((char)(Integer.reverse(arg0_1339)))) * Short.compareUnsigned((short)(arg1_1339), (short)((arg2_1339 ^ arg3_1339))));
return checksum_1339(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_1339(int v) {
return v;
}
@ForceInline
public static int constrain_arg1_1339(int v) {
v = (int)((v & -691651498) | -8388609);
return v;
}
@ForceInline
public static long constrain_arg2_1339(long v) {
v = (long)((v & -4222652069666712942L) | 4194320L);
return v;
}
@ForceInline
public static long constrain_arg3_1339(long v) {
v = (long)Math.min(Math.max(v, -268435458L), 4469412121816640279L);
v = (long)((v & 72057594037927928L) | 9223372036854775805L);
return v;
}
@ForceInline
public static Object checksum_1339(int val) {
return new Object[] {val, (val == 1365343544), (Integer.compareUnsigned(val, 1619704936) < 0), (val != 1), (Integer.compareUnsigned(val, 0) < 0), (Integer.compareUnsigned(val, -2126344901) < 0), (Integer.compareUnsigned(val, 67108863) > 0), (val >= -268435455), (val <= 1851172557), (Integer.compareUnsigned(val, 536870914) < 0), (Integer.compareUnsigned(val, 1718555185) > 0), (Integer.compareUnsigned(val, 2147483647) >= 0), (val == 134217729), (val < 2056267163), (val == -2097151), (val > 350258882), (val <= 32770), (val >= -524289), (Integer.compareUnsigned(val, 16777218) <= 0), (Integer.compareUnsigned(val, 62) < 0), (val != 1211017962)};
}
@Test
public static void primitiveConTest_1381() {
    // In each iteration, generate new random values for the method arguments.
boolean arg0_1381 = LibraryRNG.nextBoolean();
double arg1_1381 = LibraryRNG.nextDouble();
short arg2_1381 = LibraryRNG.nextShort();
    Object v0 = primitiveConTest_1381_compiled(arg0_1381, arg1_1381, arg2_1381);
    Object v1 = primitiveConTest_1381_reference(arg0_1381, arg1_1381, arg2_1381);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1381_compiled(boolean arg0_1381, double arg1_1381, short arg2_1381) {
arg0_1381 = constrain_arg0_1381(arg0_1381);
arg1_1381 = constrain_arg1_1381(arg1_1381);
arg2_1381 = constrain_arg2_1381(arg2_1381);
try {
var val = (arg0_1381?Integer.divideUnsigned(-4097, Short.compareUnsigned((short)(arg1_1381), arg2_1381)):Long.numberOfLeadingZeros((long)((-((-(Float.POSITIVE_INFINITY)))))));
return checksum_1381(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1381_reference(boolean arg0_1381, double arg1_1381, short arg2_1381) {
arg0_1381 = constrain_arg0_1381(arg0_1381);
arg1_1381 = constrain_arg1_1381(arg1_1381);
arg2_1381 = constrain_arg2_1381(arg2_1381);
try {
var val = (arg0_1381?Integer.divideUnsigned(-4097, Short.compareUnsigned((short)(arg1_1381), arg2_1381)):Long.numberOfLeadingZeros((long)((-((-(Float.POSITIVE_INFINITY)))))));
return checksum_1381(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static boolean constrain_arg0_1381(boolean v) {
return v;
}
@ForceInline
public static double constrain_arg1_1381(double v) {
return v;
}
@ForceInline
public static short constrain_arg2_1381(short v) {
return v;
}
@ForceInline
public static Object checksum_1381(int val) {
return new Object[] {val, (Integer.compareUnsigned(val, -9) < 0), (val == -1899870051), (val < 131072), (val >= 32770), (val <= 1836887739), (Integer.compareUnsigned(val, -1349163200) > 0), (val < 511), (val > 840402857), (Integer.compareUnsigned(val, 4095) < 0), (val != -4095), (val == -32), (val <= -4194304), (val == -1073741822), (val == 1025), (val <= -2097150), (Integer.compareUnsigned(val, -877767609) >= 0), (Integer.compareUnsigned(val, -512) < 0), (Integer.compareUnsigned(val, -67108866) < 0), (Integer.compareUnsigned(val, -8388607) < 0), (val == -8192)};
}
@Test
public static void primitiveConTest_1419() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1419 = LibraryRNG.nextLong();
long arg1_1419 = LibraryRNG.nextLong();
int arg2_1419 = LibraryRNG.nextInt();
int arg3_1419 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_1419_compiled(arg0_1419, arg1_1419, arg2_1419, arg3_1419);
    Object v1 = primitiveConTest_1419_reference(arg0_1419, arg1_1419, arg2_1419, arg3_1419);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1419_compiled(long arg0_1419, long arg1_1419, int arg2_1419, int arg3_1419) {
arg0_1419 = constrain_arg0_1419(arg0_1419);
arg1_1419 = constrain_arg1_1419(arg1_1419);
arg2_1419 = constrain_arg2_1419(arg2_1419);
arg3_1419 = constrain_arg3_1419(arg3_1419);
try {
var val = (Integer.compress(Long.compare((-(arg0_1419)), Long.highestOneBit((Long.expand((~(-281474976710661L)), Integer.toUnsignedLong(524290)) >> arg1_1419))), arg2_1419) << arg3_1419);
return checksum_1419(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1419_reference(long arg0_1419, long arg1_1419, int arg2_1419, int arg3_1419) {
arg0_1419 = constrain_arg0_1419(arg0_1419);
arg1_1419 = constrain_arg1_1419(arg1_1419);
arg2_1419 = constrain_arg2_1419(arg2_1419);
arg3_1419 = constrain_arg3_1419(arg3_1419);
try {
var val = (Integer.compress(Long.compare((-(arg0_1419)), Long.highestOneBit((Long.expand((~(-281474976710661L)), Integer.toUnsignedLong(524290)) >> arg1_1419))), arg2_1419) << arg3_1419);
return checksum_1419(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1419(long v) {
return v;
}
@ForceInline
public static long constrain_arg1_1419(long v) {
v = (long)((v & -17179869181L) | -7967177721503201590L);
return v;
}
@ForceInline
public static int constrain_arg2_1419(int v) {
return v;
}
@ForceInline
public static int constrain_arg3_1419(int v) {
v = (int)Math.min(Math.max(v, -433726642), 2048);
return v;
}
@ForceInline
public static Object checksum_1419(int val) {
return new Object[] {val, (val > -67108864), (Integer.compareUnsigned(val, -4194304) <= 0), (Integer.compareUnsigned(val, 2052741433) < 0), (val == 1165385137), (Integer.compareUnsigned(val, 585660788) >= 0), (val != -1277492414), (Integer.compareUnsigned(val, -1026) <= 0), (val <= -63), (val >= -2097150), (val >= -1246823366), (Integer.compareUnsigned(val, -16777217) >= 0), (val != -641893900), (val >= -1073741826), (val < -8191), (Integer.compareUnsigned(val, 466887578) > 0), (Integer.compareUnsigned(val, 268435456) <= 0), (val > 0), (Integer.compareUnsigned(val, -4194305) >= 0), (val == 8190), (val > 8388608)};
}
@Test
public static void primitiveConTest_1461() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1461 = LibraryRNG.nextLong();
long arg1_1461 = LibraryRNG.nextLong();
long arg2_1461 = LibraryRNG.nextLong();
long arg3_1461 = LibraryRNG.nextLong();
float arg4_1461 = LibraryRNG.nextFloat();
int arg5_1461 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_1461_compiled(arg0_1461, arg1_1461, arg2_1461, arg3_1461, arg4_1461, arg5_1461);
    Object v1 = primitiveConTest_1461_reference(arg0_1461, arg1_1461, arg2_1461, arg3_1461, arg4_1461, arg5_1461);
// could fail - don't verify.
}

@DontInline
public static Object primitiveConTest_1461_compiled(long arg0_1461, long arg1_1461, long arg2_1461, long arg3_1461, float arg4_1461, int arg5_1461) {
arg0_1461 = constrain_arg0_1461(arg0_1461);
arg1_1461 = constrain_arg1_1461(arg1_1461);
arg2_1461 = constrain_arg2_1461(arg2_1461);
arg3_1461 = constrain_arg3_1461(arg3_1461);
arg4_1461 = constrain_arg4_1461(arg4_1461);
arg5_1461 = constrain_arg5_1461(arg5_1461);
try {
var val = Double.compare(Double.longBitsToDouble(((-((arg0_1461 >> arg1_1461))) << Long.divideUnsigned(arg2_1461, arg3_1461))), Double.min((Double.longBitsToDouble(Double.doubleToRawLongBits(-0.0)) * Double.sum(Double.sum(((double)((double)(arg4_1461)) * 1.416042956940828E-169), 2.2250738585072014E-308), -1.3329782285438013E9)), (double)((-0.0f % Float.intBitsToFloat(arg5_1461)))));
return checksum_1461(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1461_reference(long arg0_1461, long arg1_1461, long arg2_1461, long arg3_1461, float arg4_1461, int arg5_1461) {
arg0_1461 = constrain_arg0_1461(arg0_1461);
arg1_1461 = constrain_arg1_1461(arg1_1461);
arg2_1461 = constrain_arg2_1461(arg2_1461);
arg3_1461 = constrain_arg3_1461(arg3_1461);
arg4_1461 = constrain_arg4_1461(arg4_1461);
arg5_1461 = constrain_arg5_1461(arg5_1461);
try {
var val = Double.compare(Double.longBitsToDouble(((-((arg0_1461 >> arg1_1461))) << Long.divideUnsigned(arg2_1461, arg3_1461))), Double.min((Double.longBitsToDouble(Double.doubleToRawLongBits(-0.0)) * Double.sum(Double.sum(((double)((double)(arg4_1461)) * 1.416042956940828E-169), 2.2250738585072014E-308), -1.3329782285438013E9)), (double)((-0.0f % Float.intBitsToFloat(arg5_1461)))));
return checksum_1461(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1461(long v) {
v = (long)Math.min(Math.max(v, 4097L), 2251799813685252L);
return v;
}
@ForceInline
public static long constrain_arg1_1461(long v) {
v = (long)Math.min(Math.max(v, 508L), 18014398509481975L);
v = (long)((v & -1009L) | 7782518239889245987L);
return v;
}
@ForceInline
public static long constrain_arg2_1461(long v) {
return v;
}
@ForceInline
public static long constrain_arg3_1461(long v) {
v = (long)((v & -4678468255563773926L) | -549755813874L);
return v;
}
@ForceInline
public static float constrain_arg4_1461(float v) {
return v;
}
@ForceInline
public static int constrain_arg5_1461(int v) {
v = (int)Math.min(Math.max(v, 262144), -8388608);
v = (int)((v & -16777215) | 1766269539);
return v;
}
@ForceInline
public static Object checksum_1461(int val) {
return new Object[] {val, (Integer.compareUnsigned(val, -1408780164) > 0), (val <= 131070), (Integer.compareUnsigned(val, 34) >= 0), (val & 5), (val >= 258), (val != 2022895932), (val & 632242164), (Integer.compareUnsigned(val, -262144) >= 0), (val >= -511), (Integer.compareUnsigned(val, 367022045) > 0), (val != -126), (Integer.compareUnsigned(val, -91661508) <= 0), (val <= 465379291), (val >= 2097153), (val < 921549444), (val != -33554431), (val > -1073741823), (Integer.compareUnsigned(val, 32766) <= 0), (Integer.compareUnsigned(val, 1578589912) < 0), (val <= -16777214)};
}
@Test
public static void primitiveConTest_1511() {
    // In each iteration, generate new random values for the method arguments.
int arg0_1511 = LibraryRNG.nextInt();
int arg1_1511 = LibraryRNG.nextInt();
int arg2_1511 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_1511_compiled(arg0_1511, arg1_1511, arg2_1511);
    Object v1 = primitiveConTest_1511_reference(arg0_1511, arg1_1511, arg2_1511);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1511_compiled(int arg0_1511, int arg1_1511, int arg2_1511) {
arg0_1511 = constrain_arg0_1511(arg0_1511);
arg1_1511 = constrain_arg1_1511(arg1_1511);
arg2_1511 = constrain_arg2_1511(arg2_1511);
try {
var val = ((((int)(1.7889043101437885E245) >>> arg0_1511) * arg1_1511) - (Integer.rotateLeft(3, 1114045952) - arg2_1511));
return checksum_1511(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1511_reference(int arg0_1511, int arg1_1511, int arg2_1511) {
arg0_1511 = constrain_arg0_1511(arg0_1511);
arg1_1511 = constrain_arg1_1511(arg1_1511);
arg2_1511 = constrain_arg2_1511(arg2_1511);
try {
var val = ((((int)(1.7889043101437885E245) >>> arg0_1511) * arg1_1511) - (Integer.rotateLeft(3, 1114045952) - arg2_1511));
return checksum_1511(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_1511(int v) {
v = (int)Math.min(Math.max(v, 1302647359), -1024);
v = (int)((v & -7) | 8192);
return v;
}
@ForceInline
public static int constrain_arg1_1511(int v) {
v = (int)((v & -63) | -131072);
return v;
}
@ForceInline
public static int constrain_arg2_1511(int v) {
v = (int)((v & 33554432) | -131070);
return v;
}
@ForceInline
public static Object checksum_1511(int val) {
return new Object[] {val, (Integer.compareUnsigned(val, -33554431) >= 0), (val != -2139360987), (val > -32767), (val < 1376158205), (val >= 258), (val & -1115004402), (Integer.compareUnsigned(val, -62) >= 0), (Integer.compareUnsigned(val, -1883291860) <= 0), (Integer.compareUnsigned(val, 67108862) > 0), (Integer.compareUnsigned(val, -32766) <= 0), (Integer.compareUnsigned(val, -985970159) < 0), (val != 268435457), (Integer.compareUnsigned(val, -4194305) < 0), (Integer.compareUnsigned(val, 32770) <= 0), (val <= 3), (val > -536870914), (val & 1018546084), (val <= 1295881120), (Integer.compareUnsigned(val, -262146) > 0), (Integer.compareUnsigned(val, 1708162909) >= 0)};
}
@Test
public static void primitiveConTest_1549() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1549 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1549_compiled(arg0_1549);
    Object v1 = primitiveConTest_1549_reference(arg0_1549);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1549_compiled(long arg0_1549) {
arg0_1549 = constrain_arg0_1549(arg0_1549);
try {
var val = Integer.numberOfLeadingZeros(Short.compare(((Integer.rotateRight(Long.signum(arg0_1549), -1024) > -726124633)?(short)(Float.NEGATIVE_INFINITY):(short)-3834), Short.reverseBytes((short)((byte)(15L)))));
return checksum_1549(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1549_reference(long arg0_1549) {
arg0_1549 = constrain_arg0_1549(arg0_1549);
try {
var val = Integer.numberOfLeadingZeros(Short.compare(((Integer.rotateRight(Long.signum(arg0_1549), -1024) > -726124633)?(short)(Float.NEGATIVE_INFINITY):(short)-3834), Short.reverseBytes((short)((byte)(15L)))));
return checksum_1549(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1549(long v) {
v = (long)((v & -281474976710651L) | -8934581540295849462L);
return v;
}
@ForceInline
public static Object checksum_1549(int val) {
return new Object[] {val, (val != 8388608), (val != -1476152825), (Integer.compareUnsigned(val, -1022) >= 0), (Integer.compareUnsigned(val, 262144) < 0), (Integer.compareUnsigned(val, -536870912) >= 0), (val > -8388610), (val & 536870914), (val > 127), (Integer.compareUnsigned(val, -134217729) > 0), (Integer.compareUnsigned(val, -1430852868) < 0), (val >= -67108864), (Integer.compareUnsigned(val, -2047) < 0), (val & 4194305), (val > -130), (val & -16), (val > -612101718), (val >= 4098), (val <= -1073741825), (Integer.compareUnsigned(val, -32) < 0), (val >= -4098)};
}
@Test
public static void primitiveConTest_1579() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1579 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1579_compiled(arg0_1579);
    Object v1 = primitiveConTest_1579_reference(arg0_1579);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1579_compiled(long arg0_1579) {
arg0_1579 = constrain_arg0_1579(arg0_1579);
try {
var val = (-((8197L - arg0_1579)));
return checksum_1579(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1579_reference(long arg0_1579) {
arg0_1579 = constrain_arg0_1579(arg0_1579);
try {
var val = (-((8197L - arg0_1579)));
return checksum_1579(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1579(long v) {
return v;
}
@ForceInline
public static Object checksum_1579(long val) {
return new Object[] {val, (val != -8210615503310670829L), (Long.compareUnsigned(val, 6823075263402888964L) < 0), (val <= 2319946056706991899L), (Long.compareUnsigned(val, 5712601925582292523L) >= 0), (Long.compareUnsigned(val, -549755813891L) <= 0), (Long.compareUnsigned(val, 1866118044351482733L) >= 0), (Long.compareUnsigned(val, -8796093022218L) <= 0), (Long.compareUnsigned(val, -2317361762651687311L) <= 0), (Long.compareUnsigned(val, 3854653249051209505L) >= 0), (val > -9223372036854775800L), (val != -5789714583302804748L), (val < 1844739580690379949L), (Long.compareUnsigned(val, -2907841383731543937L) >= 0), (val < 72057594037927930L), (Long.compareUnsigned(val, 8893673872714120740L) < 0), (Long.compareUnsigned(val, -6447548933557580525L) >= 0), (Long.compareUnsigned(val, 4956635488350352472L) > 0), (Long.compareUnsigned(val, -18014398509481982L) > 0), (val >= 288230376151711758L), (Long.compareUnsigned(val, 16373L) >= 0)};
}
@Test
public static void primitiveConTest_1609() {
    // In each iteration, generate new random values for the method arguments.
int arg0_1609 = LibraryRNG.nextInt();
float arg1_1609 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_1609_compiled(arg0_1609, arg1_1609);
    Object v1 = primitiveConTest_1609_reference(arg0_1609, arg1_1609);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1609_compiled(int arg0_1609, float arg1_1609) {
arg0_1609 = constrain_arg0_1609(arg0_1609);
arg1_1609 = constrain_arg1_1609(arg1_1609);
try {
var val = Integer.toUnsignedLong((arg0_1609 + Float.floatToIntBits(arg1_1609)));
return checksum_1609(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1609_reference(int arg0_1609, float arg1_1609) {
arg0_1609 = constrain_arg0_1609(arg0_1609);
arg1_1609 = constrain_arg1_1609(arg1_1609);
try {
var val = Integer.toUnsignedLong((arg0_1609 + Float.floatToIntBits(arg1_1609)));
return checksum_1609(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_1609(int v) {
v = (int)Math.min(Math.max(v, -1073741822), -670106283);
v = (int)((v & 0) | -31);
return v;
}
@ForceInline
public static float constrain_arg1_1609(float v) {
return v;
}
@ForceInline
public static Object checksum_1609(long val) {
return new Object[] {val, (val >= -9007199254740999L), (Long.compareUnsigned(val, -281474976710672L) < 0), (val != 6691754335956144137L), (val & 16777209L), (val < 2077721963804001501L), (val & -4503599627370506L), (Long.compareUnsigned(val, 2766184248163190195L) >= 0), (Long.compareUnsigned(val, 1579262437386058610L) <= 0), (val <= 4919990430376808310L), (Long.compareUnsigned(val, 8569260064013538587L) >= 0), (val < 4503599627370504L), (val != 536870911L), (Long.compareUnsigned(val, -3375311154419517491L) <= 0), (val < 6374295646927941621L), (val <= -36028797018963964L), (val >= 918580001566322496L), (val <= 8796418371603637975L), (val >= 1099511627789L), (val <= 63L), (Long.compareUnsigned(val, -1823640524045125141L) < 0)};
}
@Test
public static void primitiveConTest_1643() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1643 = LibraryRNG.nextLong();
boolean arg1_1643 = LibraryRNG.nextBoolean();
int arg2_1643 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_1643_compiled(arg0_1643, arg1_1643, arg2_1643);
    Object v1 = primitiveConTest_1643_reference(arg0_1643, arg1_1643, arg2_1643);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1643_compiled(long arg0_1643, boolean arg1_1643, int arg2_1643) {
arg0_1643 = constrain_arg0_1643(arg0_1643);
arg1_1643 = constrain_arg1_1643(arg1_1643);
arg2_1643 = constrain_arg2_1643(arg2_1643);
try {
var val = (long)(Long.rotateRight(arg0_1643, Integer.compareUnsigned(Boolean.compare(arg1_1643, false), arg2_1643)));
return checksum_1643(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1643_reference(long arg0_1643, boolean arg1_1643, int arg2_1643) {
arg0_1643 = constrain_arg0_1643(arg0_1643);
arg1_1643 = constrain_arg1_1643(arg1_1643);
arg2_1643 = constrain_arg2_1643(arg2_1643);
try {
var val = (long)(Long.rotateRight(arg0_1643, Integer.compareUnsigned(Boolean.compare(arg1_1643, false), arg2_1643)));
return checksum_1643(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1643(long v) {
v = (long)((v & -2147483643L) | -1749230651618435689L);
return v;
}
@ForceInline
public static boolean constrain_arg1_1643(boolean v) {
return v;
}
@ForceInline
public static int constrain_arg2_1643(int v) {
return v;
}
@ForceInline
public static Object checksum_1643(long val) {
return new Object[] {val, (val & 367563711979530664L), (val & 524293L), (Long.compareUnsigned(val, -9139328618982982361L) < 0), (val != 446189711599451882L), (Long.compareUnsigned(val, -1507765444589614137L) < 0), (val & 17592186044403L), (val != 2166417306653091411L), (val <= -4885680717835132725L), (val == -140737488355337L), (val < 3623557162622979792L), (Long.compareUnsigned(val, 2305843009213693968L) >= 0), (Long.compareUnsigned(val, -2147483634L) > 0), (val == -6079492118383707128L), (Long.compareUnsigned(val, 5782457584311705519L) <= 0), (val >= -2059L), (Long.compareUnsigned(val, 1125899906842634L) >= 0), (Long.compareUnsigned(val, -198049993761730535L) < 0), (val > -2990539403760626626L), (Long.compareUnsigned(val, -3114028120815911L) >= 0), (val < -16379L)};
}
@Test
public static void primitiveConTest_1681() {
    // In each iteration, generate new random values for the method arguments.
    Object v0 = primitiveConTest_1681_compiled();
    Object v1 = primitiveConTest_1681_reference();
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1681_compiled() {
try {
var val = (long)((byte)((double)(-1.7924037E-37f)));
return checksum_1681(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1681_reference() {
try {
var val = (long)((byte)((double)(-1.7924037E-37f)));
return checksum_1681(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static Object checksum_1681(long val) {
return new Object[] {val, (val == 262145L), (Long.compareUnsigned(val, 4095L) >= 0), (val == 4611686018427387914L), (val >= -281474976710672L), (val > -9007199254741008L), (Long.compareUnsigned(val, 16777204L) <= 0), (val <= 7841412493645911054L), (val & -7771517104972380953L), (val != 18014398509481994L), (val != 7936252630027215823L), (val == 4503599627370492L), (val >= 4611686018427387912L), (val <= -217247499277014825L), (val & 5492898048354748799L), (val > -7640366122612926983L), (val >= 5767712031777194074L), (Long.compareUnsigned(val, -3200040355775939891L) >= 0), (Long.compareUnsigned(val, -1080637931120846391L) <= 0), (Long.compareUnsigned(val, 4398046511090L) <= 0), (val > 7810613584900086109L)};
}
@Test
public static void primitiveConTest_1707() {
    // In each iteration, generate new random values for the method arguments.
float arg0_1707 = LibraryRNG.nextFloat();
double arg1_1707 = LibraryRNG.nextDouble();
long arg2_1707 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1707_compiled(arg0_1707, arg1_1707, arg2_1707);
    Object v1 = primitiveConTest_1707_reference(arg0_1707, arg1_1707, arg2_1707);
// could fail - don't verify.
}

@DontInline
public static Object primitiveConTest_1707_compiled(float arg0_1707, double arg1_1707, long arg2_1707) {
arg0_1707 = constrain_arg0_1707(arg0_1707);
arg1_1707 = constrain_arg1_1707(arg1_1707);
arg2_1707 = constrain_arg2_1707(arg2_1707);
try {
var val = (long)(((Character.compare((char)37788, (char)((-((float)(Double.NEGATIVE_INFINITY))))) & Short.compare((short)(arg0_1707), (short)(288230376151711756L))) ^ Long.compareUnsigned(Byte.toUnsignedLong((byte)2), (long)((Double.doubleToRawLongBits((-(arg1_1707))) & Long.highestOneBit(arg2_1707))))));
return checksum_1707(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1707_reference(float arg0_1707, double arg1_1707, long arg2_1707) {
arg0_1707 = constrain_arg0_1707(arg0_1707);
arg1_1707 = constrain_arg1_1707(arg1_1707);
arg2_1707 = constrain_arg2_1707(arg2_1707);
try {
var val = (long)(((Character.compare((char)37788, (char)((-((float)(Double.NEGATIVE_INFINITY))))) & Short.compare((short)(arg0_1707), (short)(288230376151711756L))) ^ Long.compareUnsigned(Byte.toUnsignedLong((byte)2), (long)((Double.doubleToRawLongBits((-(arg1_1707))) & Long.highestOneBit(arg2_1707))))));
return checksum_1707(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_1707(float v) {
return v;
}
@ForceInline
public static double constrain_arg1_1707(double v) {
return v;
}
@ForceInline
public static long constrain_arg2_1707(long v) {
v = (long)((v & -2035L) | 4194289L);
return v;
}
@ForceInline
public static Object checksum_1707(long val) {
return new Object[] {val, (val < 70368744177651L), (val >= -3607106062202643904L), (Long.compareUnsigned(val, 5561662957041962027L) >= 0), (val >= 39L), (Long.compareUnsigned(val, -3240039200798766986L) < 0), (val & -1598749801333649356L), (val == 34L), (Long.compareUnsigned(val, -18014398509481978L) <= 0), (Long.compareUnsigned(val, 1621503505141143542L) > 0), (val != -3997784128286817416L), (Long.compareUnsigned(val, -9223372036854775805L) <= 0), (val < 6110035604410436892L), (val == 1152921504606846977L), (val < -2215412444508734631L), (val > 3877094589933212800L), (Long.compareUnsigned(val, -1847491455515717898L) < 0), (Long.compareUnsigned(val, 524297L) < 0), (Long.compareUnsigned(val, 3906933030707872656L) > 0), (val <= -1894978006047931842L), (Long.compareUnsigned(val, -2522215421851685242L) < 0)};
}
@Test
public static void primitiveConTest_1745() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1745 = LibraryRNG.nextLong();
long arg1_1745 = LibraryRNG.nextLong();
long arg2_1745 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1745_compiled(arg0_1745, arg1_1745, arg2_1745);
    Object v1 = primitiveConTest_1745_reference(arg0_1745, arg1_1745, arg2_1745);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1745_compiled(long arg0_1745, long arg1_1745, long arg2_1745) {
arg0_1745 = constrain_arg0_1745(arg0_1745);
arg1_1745 = constrain_arg1_1745(arg1_1745);
arg2_1745 = constrain_arg2_1745(arg2_1745);
try {
var val = (Double.doubleToLongBits((double)(Long.highestOneBit(arg0_1745))) << Long.reverse(Long.expand((-576460752303423484L >>> arg1_1745), (arg2_1745 ^ 4499087691624735581L))));
return checksum_1745(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1745_reference(long arg0_1745, long arg1_1745, long arg2_1745) {
arg0_1745 = constrain_arg0_1745(arg0_1745);
arg1_1745 = constrain_arg1_1745(arg1_1745);
arg2_1745 = constrain_arg2_1745(arg2_1745);
try {
var val = (Double.doubleToLongBits((double)(Long.highestOneBit(arg0_1745))) << Long.reverse(Long.expand((-576460752303423484L >>> arg1_1745), (arg2_1745 ^ 4499087691624735581L))));
return checksum_1745(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1745(long v) {
return v;
}
@ForceInline
public static long constrain_arg1_1745(long v) {
v = (long)Math.min(Math.max(v, -1664492243032154008L), 8864400926546316738L);
return v;
}
@ForceInline
public static long constrain_arg2_1745(long v) {
v = (long)((v & 67108859L) | -1527862840781194564L);
return v;
}
@ForceInline
public static Object checksum_1745(long val) {
return new Object[] {val, (val == 692111030194575392L), (val <= 4012051893603937024L), (Long.compareUnsigned(val, 140737488355318L) <= 0), (Long.compareUnsigned(val, -32772L) >= 0), (val != 6905823162480108984L), (val > -6392024859202337756L), (Long.compareUnsigned(val, -7685070217685559896L) <= 0), (val != -3039097156405294977L), (val != -6575735174559352053L), (val != -4312490888880673731L), (Long.compareUnsigned(val, -35184372088837L) > 0), (val != -3028511512864240404L), (val != 4749740948557388549L), (Long.compareUnsigned(val, -2039L) <= 0), (val <= -4726038733816304335L), (Long.compareUnsigned(val, 576460752303423480L) <= 0), (val <= -1570247913406774425L), (Long.compareUnsigned(val, -8388610L) <= 0), (val & -4554646821755332572L), (val > 1270894887755258476L)};
}
@Test
public static void primitiveConTest_1783() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1783 = LibraryRNG.nextLong();
long arg1_1783 = LibraryRNG.nextLong();
long arg2_1783 = LibraryRNG.nextLong();
short arg3_1783 = LibraryRNG.nextShort();
long arg4_1783 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_1783_compiled(arg0_1783, arg1_1783, arg2_1783, arg3_1783, arg4_1783);
    Object v1 = primitiveConTest_1783_reference(arg0_1783, arg1_1783, arg2_1783, arg3_1783, arg4_1783);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1783_compiled(long arg0_1783, long arg1_1783, long arg2_1783, short arg3_1783, long arg4_1783) {
arg0_1783 = constrain_arg0_1783(arg0_1783);
arg1_1783 = constrain_arg1_1783(arg1_1783);
arg2_1783 = constrain_arg2_1783(arg2_1783);
arg3_1783 = constrain_arg3_1783(arg3_1783);
arg4_1783 = constrain_arg4_1783(arg4_1783);
try {
var val = (long)((short)((Long.reverseBytes(Long.remainderUnsigned((false?8304074378872920703L:arg0_1783), arg1_1783)) >> (~(((arg2_1783 * (long)((char)(arg3_1783))) | arg4_1783))))));
return checksum_1783(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1783_reference(long arg0_1783, long arg1_1783, long arg2_1783, short arg3_1783, long arg4_1783) {
arg0_1783 = constrain_arg0_1783(arg0_1783);
arg1_1783 = constrain_arg1_1783(arg1_1783);
arg2_1783 = constrain_arg2_1783(arg2_1783);
arg3_1783 = constrain_arg3_1783(arg3_1783);
arg4_1783 = constrain_arg4_1783(arg4_1783);
try {
var val = (long)((short)((Long.reverseBytes(Long.remainderUnsigned((false?8304074378872920703L:arg0_1783), arg1_1783)) >> (~(((arg2_1783 * (long)((char)(arg3_1783))) | arg4_1783))))));
return checksum_1783(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1783(long v) {
v = (long)Math.min(Math.max(v, 2983133618580879589L), -131079L);
v = (long)((v & 8840499805246101579L) | 5875976398860085763L);
return v;
}
@ForceInline
public static long constrain_arg1_1783(long v) {
v = (long)((v & -4946490231273929173L) | 268435471L);
return v;
}
@ForceInline
public static long constrain_arg2_1783(long v) {
v = (long)Math.min(Math.max(v, -68719476747L), -2526940348374253520L);
return v;
}
@ForceInline
public static short constrain_arg3_1783(short v) {
v = (short)Math.min(Math.max(v, (short)15926), (short)-22812);
return v;
}
@ForceInline
public static long constrain_arg4_1783(long v) {
return v;
}
@ForceInline
public static Object checksum_1783(long val) {
return new Object[] {val, (Long.compareUnsigned(val, 519L) < 0), (val != -549755813879L), (Long.compareUnsigned(val, 2305843009213693946L) >= 0), (Long.compareUnsigned(val, -3774945386531796452L) < 0), (val < 4294967307L), (val == -3175308444372140453L), (val == 56L), (val > -6869947098602978748L), (Long.compareUnsigned(val, 2147483659L) > 0), (val >= -2199023255567L), (Long.compareUnsigned(val, -225591020484598214L) >= 0), (val < 4503599627370491L), (Long.compareUnsigned(val, -4294967298L) > 0), (val != 1424727120610253877L), (val >= 1073741827L), (Long.compareUnsigned(val, -7370746883945405848L) > 0), (Long.compareUnsigned(val, -4503599627370489L) <= 0), (val < 7427575364737396166L), (val != 6680531444603622669L), (Long.compareUnsigned(val, 35184372088847L) < 0)};
}
@Test
public static void primitiveConTest_1829() {
    // In each iteration, generate new random values for the method arguments.
long arg0_1829 = LibraryRNG.nextLong();
int arg1_1829 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_1829_compiled(arg0_1829, arg1_1829);
    Object v1 = primitiveConTest_1829_reference(arg0_1829, arg1_1829);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1829_compiled(long arg0_1829, int arg1_1829) {
arg0_1829 = constrain_arg0_1829(arg0_1829);
arg1_1829 = constrain_arg1_1829(arg1_1829);
try {
var val = Long.rotateRight((long)((-0.0 + (double)(Long.remainderUnsigned(2080381074715230705L, arg0_1829)))), (arg1_1829 % 65));
return checksum_1829(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1829_reference(long arg0_1829, int arg1_1829) {
arg0_1829 = constrain_arg0_1829(arg0_1829);
arg1_1829 = constrain_arg1_1829(arg1_1829);
try {
var val = Long.rotateRight((long)((-0.0 + (double)(Long.remainderUnsigned(2080381074715230705L, arg0_1829)))), (arg1_1829 % 65));
return checksum_1829(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_1829(long v) {
return v;
}
@ForceInline
public static int constrain_arg1_1829(int v) {
return v;
}
@ForceInline
public static Object checksum_1829(long val) {
return new Object[] {val, (val < 7109123131266356188L), (val == -5137218073614457633L), (val & -4355797358288398503L), (val > 524285L), (Long.compareUnsigned(val, -101410889062557264L) < 0), (Long.compareUnsigned(val, -1048563L) >= 0), (val <= 4189896516386569669L), (val & -3749525794315611941L), (val == 2447941654733997895L), (val >= -5367547945051084089L), (val >= 8050698268828049019L), (val < 2370413522296565379L), (val < 68719476731L), (val & -7909303795150296334L), (Long.compareUnsigned(val, 1363414862869154906L) >= 0), (val < -16777232L), (val >= 1152921504606846986L), (Long.compareUnsigned(val, 36028797018963962L) > 0), (Long.compareUnsigned(val, -274877906929L) <= 0), (val == 8371233424243877869L)};
}
@Test
public static void primitiveConTest_1863() {
    // In each iteration, generate new random values for the method arguments.
float arg0_1863 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_1863_compiled(arg0_1863);
    Object v1 = primitiveConTest_1863_reference(arg0_1863);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1863_compiled(float arg0_1863) {
arg0_1863 = constrain_arg0_1863(arg0_1863);
try {
var val = (long)(Integer.compare((Long.compare(4503599627370492L, Long.highestOneBit(Long.max(37494083506142921L, 33554441L))) + (-((131070 - Float.floatToIntBits((0.0f % arg0_1863)))))), 1048575));
return checksum_1863(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1863_reference(float arg0_1863) {
arg0_1863 = constrain_arg0_1863(arg0_1863);
try {
var val = (long)(Integer.compare((Long.compare(4503599627370492L, Long.highestOneBit(Long.max(37494083506142921L, 33554441L))) + (-((131070 - Float.floatToIntBits((0.0f % arg0_1863)))))), 1048575));
return checksum_1863(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_1863(float v) {
return v;
}
@ForceInline
public static Object checksum_1863(long val) {
return new Object[] {val, (Long.compareUnsigned(val, -6330943930845520122L) < 0), (val >= 131074L), (Long.compareUnsigned(val, 513L) > 0), (Long.compareUnsigned(val, 8047021222279905970L) < 0), (Long.compareUnsigned(val, 2707353901756175318L) <= 0), (val > 9135937495321525518L), (val > 1125899906842622L), (val < 8404336848742379202L), (Long.compareUnsigned(val, -1510878445068526233L) < 0), (Long.compareUnsigned(val, -576460752303423480L) > 0), (val == -8376927533922586996L), (Long.compareUnsigned(val, -6478647779184085104L) > 0), (val <= 8596552728703034873L), (val >= -9131578890119518070L), (Long.compareUnsigned(val, -4503599627370507L) > 0), (val & 33554416L), (val != -268435446L), (val < -35184372088842L), (Long.compareUnsigned(val, 5938068643310903601L) < 0), (Long.compareUnsigned(val, 7543859025103737196L) > 0)};
}
@Test
public static void primitiveConTest_1893() {
    // In each iteration, generate new random values for the method arguments.
float arg0_1893 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_1893_compiled(arg0_1893);
    Object v1 = primitiveConTest_1893_reference(arg0_1893);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1893_compiled(float arg0_1893) {
arg0_1893 = constrain_arg0_1893(arg0_1893);
try {
var val = (long)((char)((double)(((arg0_1893 * (Float.intBitsToFloat((int)((byte)-128)) + 1.1754944E-38f)) - ((float)((~(-1861001582))) - (float)((float)((float)(246154541))))))));
return checksum_1893(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1893_reference(float arg0_1893) {
arg0_1893 = constrain_arg0_1893(arg0_1893);
try {
var val = (long)((char)((double)(((arg0_1893 * (Float.intBitsToFloat((int)((byte)-128)) + 1.1754944E-38f)) - ((float)((~(-1861001582))) - (float)((float)((float)(246154541))))))));
return checksum_1893(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_1893(float v) {
return v;
}
@ForceInline
public static Object checksum_1893(long val) {
return new Object[] {val, (val < 217650814752931236L), (Long.compareUnsigned(val, -43L) >= 0), (Long.compareUnsigned(val, -2251799813685255L) > 0), (Long.compareUnsigned(val, -3426521075255065713L) <= 0), (Long.compareUnsigned(val, -1611975747159164746L) < 0), (Long.compareUnsigned(val, -3L) <= 0), (Long.compareUnsigned(val, 268435442L) >= 0), (Long.compareUnsigned(val, -1000385211343038664L) <= 0), (Long.compareUnsigned(val, -268435443L) >= 0), (val <= 6019255537252304902L), (val < -5179319123923290124L), (val >= -134217732L), (Long.compareUnsigned(val, 536870920L) <= 0), (val > 1872804434548651519L), (val >= -9046600331795429877L), (Long.compareUnsigned(val, 8952541922876665907L) > 0), (val < -288230376151711751L), (val & -5495052408480035876L), (Long.compareUnsigned(val, 6872791918008981260L) >= 0), (val > 281474976710667L)};
}
@Test
public static void primitiveConTest_1923() {
    // In each iteration, generate new random values for the method arguments.
boolean arg0_1923 = LibraryRNG.nextBoolean();
float arg1_1923 = LibraryRNG.nextFloat();
float arg2_1923 = LibraryRNG.nextFloat();
float arg3_1923 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_1923_compiled(arg0_1923, arg1_1923, arg2_1923, arg3_1923);
    Object v1 = primitiveConTest_1923_reference(arg0_1923, arg1_1923, arg2_1923, arg3_1923);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1923_compiled(boolean arg0_1923, float arg1_1923, float arg2_1923, float arg3_1923) {
arg0_1923 = constrain_arg0_1923(arg0_1923);
arg1_1923 = constrain_arg1_1923(arg1_1923);
arg2_1923 = constrain_arg2_1923(arg2_1923);
arg3_1923 = constrain_arg3_1923(arg3_1923);
try {
var val = Float.min((arg0_1923?arg1_1923:arg2_1923), arg3_1923);
return checksum_1923(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1923_reference(boolean arg0_1923, float arg1_1923, float arg2_1923, float arg3_1923) {
arg0_1923 = constrain_arg0_1923(arg0_1923);
arg1_1923 = constrain_arg1_1923(arg1_1923);
arg2_1923 = constrain_arg2_1923(arg2_1923);
arg3_1923 = constrain_arg3_1923(arg3_1923);
try {
var val = Float.min((arg0_1923?arg1_1923:arg2_1923), arg3_1923);
return checksum_1923(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static boolean constrain_arg0_1923(boolean v) {
return v;
}
@ForceInline
public static float constrain_arg1_1923(float v) {
return v;
}
@ForceInline
public static float constrain_arg2_1923(float v) {
return v;
}
@ForceInline
public static float constrain_arg3_1923(float v) {
return v;
}
@ForceInline
public static Object checksum_1923(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_1945() {
    // In each iteration, generate new random values for the method arguments.
float arg0_1945 = LibraryRNG.nextFloat();
float arg1_1945 = LibraryRNG.nextFloat();
int arg2_1945 = LibraryRNG.nextInt();
int arg3_1945 = LibraryRNG.nextInt();
int arg4_1945 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_1945_compiled(arg0_1945, arg1_1945, arg2_1945, arg3_1945, arg4_1945);
    Object v1 = primitiveConTest_1945_reference(arg0_1945, arg1_1945, arg2_1945, arg3_1945, arg4_1945);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1945_compiled(float arg0_1945, float arg1_1945, int arg2_1945, int arg3_1945, int arg4_1945) {
arg0_1945 = constrain_arg0_1945(arg0_1945);
arg1_1945 = constrain_arg1_1945(arg1_1945);
arg2_1945 = constrain_arg2_1945(arg2_1945);
arg3_1945 = constrain_arg3_1945(arg3_1945);
arg4_1945 = constrain_arg4_1945(arg4_1945);
try {
var val = (Float.max((Float.min(arg0_1945, arg1_1945) * 1.0f), (float)(Integer.rotateLeft(arg2_1945, (Integer.compareUnsigned(arg3_1945, arg4_1945) / -990250464)))) + 1.5992931E12f);
return checksum_1945(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1945_reference(float arg0_1945, float arg1_1945, int arg2_1945, int arg3_1945, int arg4_1945) {
arg0_1945 = constrain_arg0_1945(arg0_1945);
arg1_1945 = constrain_arg1_1945(arg1_1945);
arg2_1945 = constrain_arg2_1945(arg2_1945);
arg3_1945 = constrain_arg3_1945(arg3_1945);
arg4_1945 = constrain_arg4_1945(arg4_1945);
try {
var val = (Float.max((Float.min(arg0_1945, arg1_1945) * 1.0f), (float)(Integer.rotateLeft(arg2_1945, (Integer.compareUnsigned(arg3_1945, arg4_1945) / -990250464)))) + 1.5992931E12f);
return checksum_1945(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_1945(float v) {
return v;
}
@ForceInline
public static float constrain_arg1_1945(float v) {
return v;
}
@ForceInline
public static int constrain_arg2_1945(int v) {
return v;
}
@ForceInline
public static int constrain_arg3_1945(int v) {
v = (int)Math.min(Math.max(v, 536870914), 503522615);
return v;
}
@ForceInline
public static int constrain_arg4_1945(int v) {
v = (int)Math.min(Math.max(v, 428182224), 772908959);
v = (int)((v & 255) | -386640224);
return v;
}
@ForceInline
public static Object checksum_1945(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_1971() {
    // In each iteration, generate new random values for the method arguments.
float arg0_1971 = LibraryRNG.nextFloat();
boolean arg1_1971 = LibraryRNG.nextBoolean();
float arg2_1971 = LibraryRNG.nextFloat();
float arg3_1971 = LibraryRNG.nextFloat();
float arg4_1971 = LibraryRNG.nextFloat();
float arg5_1971 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_1971_compiled(arg0_1971, arg1_1971, arg2_1971, arg3_1971, arg4_1971, arg5_1971);
    Object v1 = primitiveConTest_1971_reference(arg0_1971, arg1_1971, arg2_1971, arg3_1971, arg4_1971, arg5_1971);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_1971_compiled(float arg0_1971, boolean arg1_1971, float arg2_1971, float arg3_1971, float arg4_1971, float arg5_1971) {
arg0_1971 = constrain_arg0_1971(arg0_1971);
arg1_1971 = constrain_arg1_1971(arg1_1971);
arg2_1971 = constrain_arg2_1971(arg2_1971);
arg3_1971 = constrain_arg3_1971(arg3_1971);
arg4_1971 = constrain_arg4_1971(arg4_1971);
arg5_1971 = constrain_arg5_1971(arg5_1971);
try {
var val = (Float.max(Float.NEGATIVE_INFINITY, (float)((char)5062)) + Float.max(Float.float16ToFloat((short)33), Float.sum(arg0_1971, (arg1_1971?(((-((arg2_1971 + Float.intBitsToFloat(2143289344 /* NaN */)))) < arg3_1971)?arg4_1971:Float.max(((arg5_1971 % -2.4740498E14f) / -7.095718E36f), -1.0f)):(float)(-6.562014E-21f)))));
return checksum_1971(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_1971_reference(float arg0_1971, boolean arg1_1971, float arg2_1971, float arg3_1971, float arg4_1971, float arg5_1971) {
arg0_1971 = constrain_arg0_1971(arg0_1971);
arg1_1971 = constrain_arg1_1971(arg1_1971);
arg2_1971 = constrain_arg2_1971(arg2_1971);
arg3_1971 = constrain_arg3_1971(arg3_1971);
arg4_1971 = constrain_arg4_1971(arg4_1971);
arg5_1971 = constrain_arg5_1971(arg5_1971);
try {
var val = (Float.max(Float.NEGATIVE_INFINITY, (float)((char)5062)) + Float.max(Float.float16ToFloat((short)33), Float.sum(arg0_1971, (arg1_1971?(((-((arg2_1971 + Float.intBitsToFloat(2143289344 /* NaN */)))) < arg3_1971)?arg4_1971:Float.max(((arg5_1971 % -2.4740498E14f) / -7.095718E36f), -1.0f)):(float)(-6.562014E-21f)))));
return checksum_1971(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_1971(float v) {
return v;
}
@ForceInline
public static boolean constrain_arg1_1971(boolean v) {
return v;
}
@ForceInline
public static float constrain_arg2_1971(float v) {
return v;
}
@ForceInline
public static float constrain_arg3_1971(float v) {
return v;
}
@ForceInline
public static float constrain_arg4_1971(float v) {
return v;
}
@ForceInline
public static float constrain_arg5_1971(float v) {
return v;
}
@ForceInline
public static Object checksum_1971(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2001() {
    // In each iteration, generate new random values for the method arguments.
float arg0_2001 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_2001_compiled(arg0_2001);
    Object v1 = primitiveConTest_2001_reference(arg0_2001);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2001_compiled(float arg0_2001) {
arg0_2001 = constrain_arg0_2001(arg0_2001);
try {
var val = (arg0_2001 / 1.4E-45f);
return checksum_2001(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2001_reference(float arg0_2001) {
arg0_2001 = constrain_arg0_2001(arg0_2001);
try {
var val = (arg0_2001 / 1.4E-45f);
return checksum_2001(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_2001(float v) {
return v;
}
@ForceInline
public static Object checksum_2001(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2011() {
    // In each iteration, generate new random values for the method arguments.
int arg0_2011 = LibraryRNG.nextInt();
int arg1_2011 = LibraryRNG.nextInt();
int arg2_2011 = LibraryRNG.nextInt();
double arg3_2011 = LibraryRNG.nextDouble();
double arg4_2011 = LibraryRNG.nextDouble();
double arg5_2011 = LibraryRNG.nextDouble();
double arg6_2011 = LibraryRNG.nextDouble();
int arg7_2011 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_2011_compiled(arg0_2011, arg1_2011, arg2_2011, arg3_2011, arg4_2011, arg5_2011, arg6_2011, arg7_2011);
    Object v1 = primitiveConTest_2011_reference(arg0_2011, arg1_2011, arg2_2011, arg3_2011, arg4_2011, arg5_2011, arg6_2011, arg7_2011);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2011_compiled(int arg0_2011, int arg1_2011, int arg2_2011, double arg3_2011, double arg4_2011, double arg5_2011, double arg6_2011, int arg7_2011) {
arg0_2011 = constrain_arg0_2011(arg0_2011);
arg1_2011 = constrain_arg1_2011(arg1_2011);
arg2_2011 = constrain_arg2_2011(arg2_2011);
arg3_2011 = constrain_arg3_2011(arg3_2011);
arg4_2011 = constrain_arg4_2011(arg4_2011);
arg5_2011 = constrain_arg5_2011(arg5_2011);
arg6_2011 = constrain_arg6_2011(arg6_2011);
arg7_2011 = constrain_arg7_2011(arg7_2011);
try {
var val = ((float)((byte)((Integer.compareUnsigned(arg0_2011, 0) ^ (true?511:-16777216)))) * (float)((float)(Short.toUnsignedLong((short)(Integer.compareUnsigned(Boolean.compare((arg1_2011 >= arg2_2011), ((arg3_2011 < 1.7976931348623157E308) ^ ((arg4_2011 - arg5_2011) != arg6_2011))), Integer.signum(arg7_2011)))))));
return checksum_2011(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2011_reference(int arg0_2011, int arg1_2011, int arg2_2011, double arg3_2011, double arg4_2011, double arg5_2011, double arg6_2011, int arg7_2011) {
arg0_2011 = constrain_arg0_2011(arg0_2011);
arg1_2011 = constrain_arg1_2011(arg1_2011);
arg2_2011 = constrain_arg2_2011(arg2_2011);
arg3_2011 = constrain_arg3_2011(arg3_2011);
arg4_2011 = constrain_arg4_2011(arg4_2011);
arg5_2011 = constrain_arg5_2011(arg5_2011);
arg6_2011 = constrain_arg6_2011(arg6_2011);
arg7_2011 = constrain_arg7_2011(arg7_2011);
try {
var val = ((float)((byte)((Integer.compareUnsigned(arg0_2011, 0) ^ (true?511:-16777216)))) * (float)((float)(Short.toUnsignedLong((short)(Integer.compareUnsigned(Boolean.compare((arg1_2011 >= arg2_2011), ((arg3_2011 < 1.7976931348623157E308) ^ ((arg4_2011 - arg5_2011) != arg6_2011))), Integer.signum(arg7_2011)))))));
return checksum_2011(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_2011(int v) {
v = (int)((v & -173773046) | -1656934380);
return v;
}
@ForceInline
public static int constrain_arg1_2011(int v) {
v = (int)((v & -33554433) | -32);
return v;
}
@ForceInline
public static int constrain_arg2_2011(int v) {
v = (int)Math.min(Math.max(v, -256), 510);
v = (int)((v & -3) | 1428730819);
return v;
}
@ForceInline
public static double constrain_arg3_2011(double v) {
return v;
}
@ForceInline
public static double constrain_arg4_2011(double v) {
return v;
}
@ForceInline
public static double constrain_arg5_2011(double v) {
return v;
}
@ForceInline
public static double constrain_arg6_2011(double v) {
return v;
}
@ForceInline
public static int constrain_arg7_2011(int v) {
v = (int)Math.min(Math.max(v, -386376329), 2047);
return v;
}
@ForceInline
public static Object checksum_2011(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2049() {
    // In each iteration, generate new random values for the method arguments.
long arg0_2049 = LibraryRNG.nextLong();
long arg1_2049 = LibraryRNG.nextLong();
int arg2_2049 = LibraryRNG.nextInt();
short arg3_2049 = LibraryRNG.nextShort();
    Object v0 = primitiveConTest_2049_compiled(arg0_2049, arg1_2049, arg2_2049, arg3_2049);
    Object v1 = primitiveConTest_2049_reference(arg0_2049, arg1_2049, arg2_2049, arg3_2049);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2049_compiled(long arg0_2049, long arg1_2049, int arg2_2049, short arg3_2049) {
arg0_2049 = constrain_arg0_2049(arg0_2049);
arg1_2049 = constrain_arg1_2049(arg1_2049);
arg2_2049 = constrain_arg2_2049(arg2_2049);
arg3_2049 = constrain_arg3_2049(arg3_2049);
try {
var val = Float.min((-(Float.intBitsToFloat(Long.bitCount((((false?arg0_2049:-9071933015129692095L) >> (arg1_2049 >>> -4530378795821892009L)) + -1677370570116576377L))))), Float.float16ToFloat(((arg2_2049 >= Integer.max(33554434, -2097151))?(short)((short)-255):(short)((byte)(arg3_2049)))));
return checksum_2049(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2049_reference(long arg0_2049, long arg1_2049, int arg2_2049, short arg3_2049) {
arg0_2049 = constrain_arg0_2049(arg0_2049);
arg1_2049 = constrain_arg1_2049(arg1_2049);
arg2_2049 = constrain_arg2_2049(arg2_2049);
arg3_2049 = constrain_arg3_2049(arg3_2049);
try {
var val = Float.min((-(Float.intBitsToFloat(Long.bitCount((((false?arg0_2049:-9071933015129692095L) >> (arg1_2049 >>> -4530378795821892009L)) + -1677370570116576377L))))), Float.float16ToFloat(((arg2_2049 >= Integer.max(33554434, -2097151))?(short)((short)-255):(short)((byte)(arg3_2049)))));
return checksum_2049(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_2049(long v) {
v = (long)((v & -2305843009213693963L) | -346425255253529321L);
return v;
}
@ForceInline
public static long constrain_arg1_2049(long v) {
v = (long)Math.min(Math.max(v, 536870918L), -4611686018427387904L);
return v;
}
@ForceInline
public static int constrain_arg2_2049(int v) {
v = (int)Math.min(Math.max(v, -65538), -1011105594);
v = (int)((v & -630178381) | 4194302);
return v;
}
@ForceInline
public static short constrain_arg3_2049(short v) {
v = (short)Math.min(Math.max(v, (short)24784), (short)-16385);
v = (short)((v & (short)258) | (short)15);
return v;
}
@ForceInline
public static Object checksum_2049(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2071() {
    // In each iteration, generate new random values for the method arguments.
long arg0_2071 = LibraryRNG.nextLong();
long arg1_2071 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_2071_compiled(arg0_2071, arg1_2071);
    Object v1 = primitiveConTest_2071_reference(arg0_2071, arg1_2071);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2071_compiled(long arg0_2071, long arg1_2071) {
arg0_2071 = constrain_arg0_2071(arg0_2071);
arg1_2071 = constrain_arg1_2071(arg1_2071);
try {
var val = (float)((char)((char)(Long.compare(arg0_2071, arg1_2071))));
return checksum_2071(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2071_reference(long arg0_2071, long arg1_2071) {
arg0_2071 = constrain_arg0_2071(arg0_2071);
arg1_2071 = constrain_arg1_2071(arg1_2071);
try {
var val = (float)((char)((char)(Long.compare(arg0_2071, arg1_2071))));
return checksum_2071(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_2071(long v) {
v = (long)Math.min(Math.max(v, -3031773110814790794L), 134217733L);
return v;
}
@ForceInline
public static long constrain_arg1_2071(long v) {
v = (long)((v & -70368744177676L) | 6611627330305002193L);
return v;
}
@ForceInline
public static Object checksum_2071(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2085() {
    // In each iteration, generate new random values for the method arguments.
float arg0_2085 = LibraryRNG.nextFloat();
int arg1_2085 = LibraryRNG.nextInt();
int arg2_2085 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_2085_compiled(arg0_2085, arg1_2085, arg2_2085);
    Object v1 = primitiveConTest_2085_reference(arg0_2085, arg1_2085, arg2_2085);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2085_compiled(float arg0_2085, int arg1_2085, int arg2_2085) {
arg0_2085 = constrain_arg0_2085(arg0_2085);
arg1_2085 = constrain_arg1_2085(arg1_2085);
arg2_2085 = constrain_arg2_2085(arg2_2085);
try {
var val = (((float)((char)506) - (-8.061509E-30f + (float)(arg0_2085))) + (float)(Integer.remainderUnsigned(((-18 | Long.numberOfLeadingZeros((long)((char)(Long.sum(Short.toUnsignedLong((short)(-1.0)), 134217742L))))) + ((Byte.toUnsignedInt((byte)-2) / arg1_2085) << 128)), Integer.sum(-16384, arg2_2085))));
return checksum_2085(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2085_reference(float arg0_2085, int arg1_2085, int arg2_2085) {
arg0_2085 = constrain_arg0_2085(arg0_2085);
arg1_2085 = constrain_arg1_2085(arg1_2085);
arg2_2085 = constrain_arg2_2085(arg2_2085);
try {
var val = (((float)((char)506) - (-8.061509E-30f + (float)(arg0_2085))) + (float)(Integer.remainderUnsigned(((-18 | Long.numberOfLeadingZeros((long)((char)(Long.sum(Short.toUnsignedLong((short)(-1.0)), 134217742L))))) + ((Byte.toUnsignedInt((byte)-2) / arg1_2085) << 128)), Integer.sum(-16384, arg2_2085))));
return checksum_2085(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_2085(float v) {
return v;
}
@ForceInline
public static int constrain_arg1_2085(int v) {
return v;
}
@ForceInline
public static int constrain_arg2_2085(int v) {
v = (int)Math.min(Math.max(v, 16384), -1048578);
return v;
}
@ForceInline
public static Object checksum_2085(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2103() {
    // In each iteration, generate new random values for the method arguments.
double arg0_2103 = LibraryRNG.nextDouble();
long arg1_2103 = LibraryRNG.nextLong();
double arg2_2103 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_2103_compiled(arg0_2103, arg1_2103, arg2_2103);
    Object v1 = primitiveConTest_2103_reference(arg0_2103, arg1_2103, arg2_2103);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2103_compiled(double arg0_2103, long arg1_2103, double arg2_2103) {
arg0_2103 = constrain_arg0_2103(arg0_2103);
arg1_2103 = constrain_arg1_2103(arg1_2103);
arg2_2103 = constrain_arg2_2103(arg2_2103);
try {
var val = (float)((short)((double)(Double.min(Double.sum(Double.max((arg0_2103 % -1.866390716695701E215), Double.max(-0.0, (double)(arg1_2103))), (double)((char)(Short.reverseBytes((short)-17)))), arg2_2103))));
return checksum_2103(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2103_reference(double arg0_2103, long arg1_2103, double arg2_2103) {
arg0_2103 = constrain_arg0_2103(arg0_2103);
arg1_2103 = constrain_arg1_2103(arg1_2103);
arg2_2103 = constrain_arg2_2103(arg2_2103);
try {
var val = (float)((short)((double)(Double.min(Double.sum(Double.max((arg0_2103 % -1.866390716695701E215), Double.max(-0.0, (double)(arg1_2103))), (double)((char)(Short.reverseBytes((short)-17)))), arg2_2103))));
return checksum_2103(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static double constrain_arg0_2103(double v) {
return v;
}
@ForceInline
public static long constrain_arg1_2103(long v) {
v = (long)Math.min(Math.max(v, 35184372088837L), 1011L);
return v;
}
@ForceInline
public static double constrain_arg2_2103(double v) {
return v;
}
@ForceInline
public static Object checksum_2103(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2121() {
    // In each iteration, generate new random values for the method arguments.
float arg0_2121 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_2121_compiled(arg0_2121);
    Object v1 = primitiveConTest_2121_reference(arg0_2121);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2121_compiled(float arg0_2121) {
arg0_2121 = constrain_arg0_2121(arg0_2121);
try {
var val = (1.1754944E-38f - arg0_2121);
return checksum_2121(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2121_reference(float arg0_2121) {
arg0_2121 = constrain_arg0_2121(arg0_2121);
try {
var val = (1.1754944E-38f - arg0_2121);
return checksum_2121(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_2121(float v) {
return v;
}
@ForceInline
public static Object checksum_2121(float val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2131() {
    // In each iteration, generate new random values for the method arguments.
float arg0_2131 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_2131_compiled(arg0_2131);
    Object v1 = primitiveConTest_2131_reference(arg0_2131);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2131_compiled(float arg0_2131) {
arg0_2131 = constrain_arg0_2131(arg0_2131);
try {
var val = (double)(Byte.compare((byte)(Float.intBitsToFloat(2143289344 /* NaN */)), (byte)((byte)((short)((double)(arg0_2131))))));
return checksum_2131(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2131_reference(float arg0_2131) {
arg0_2131 = constrain_arg0_2131(arg0_2131);
try {
var val = (double)(Byte.compare((byte)(Float.intBitsToFloat(2143289344 /* NaN */)), (byte)((byte)((short)((double)(arg0_2131))))));
return checksum_2131(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_2131(float v) {
return v;
}
@ForceInline
public static Object checksum_2131(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2141() {
    // In each iteration, generate new random values for the method arguments.
float arg0_2141 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_2141_compiled(arg0_2141);
    Object v1 = primitiveConTest_2141_reference(arg0_2141);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2141_compiled(float arg0_2141) {
arg0_2141 = constrain_arg0_2141(arg0_2141);
try {
var val = (double)((float)((double)(arg0_2141)));
return checksum_2141(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2141_reference(float arg0_2141) {
arg0_2141 = constrain_arg0_2141(arg0_2141);
try {
var val = (double)((float)((double)(arg0_2141)));
return checksum_2141(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_2141(float v) {
return v;
}
@ForceInline
public static Object checksum_2141(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2151() {
    // In each iteration, generate new random values for the method arguments.
boolean arg0_2151 = LibraryRNG.nextBoolean();
short arg1_2151 = LibraryRNG.nextShort();
double arg2_2151 = LibraryRNG.nextDouble();
long arg3_2151 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_2151_compiled(arg0_2151, arg1_2151, arg2_2151, arg3_2151);
    Object v1 = primitiveConTest_2151_reference(arg0_2151, arg1_2151, arg2_2151, arg3_2151);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2151_compiled(boolean arg0_2151, short arg1_2151, double arg2_2151, long arg3_2151) {
arg0_2151 = constrain_arg0_2151(arg0_2151);
arg1_2151 = constrain_arg1_2151(arg1_2151);
arg2_2151 = constrain_arg2_2151(arg2_2151);
arg3_2151 = constrain_arg3_2151(arg3_2151);
try {
var val = ((double)((double)((char)((double)((arg0_2151?arg1_2151:(short)((long)((double)(arg2_2151)))))))) / (double)(Long.reverseBytes(arg3_2151)));
return checksum_2151(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2151_reference(boolean arg0_2151, short arg1_2151, double arg2_2151, long arg3_2151) {
arg0_2151 = constrain_arg0_2151(arg0_2151);
arg1_2151 = constrain_arg1_2151(arg1_2151);
arg2_2151 = constrain_arg2_2151(arg2_2151);
arg3_2151 = constrain_arg3_2151(arg3_2151);
try {
var val = ((double)((double)((char)((double)((arg0_2151?arg1_2151:(short)((long)((double)(arg2_2151)))))))) / (double)(Long.reverseBytes(arg3_2151)));
return checksum_2151(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static boolean constrain_arg0_2151(boolean v) {
return v;
}
@ForceInline
public static short constrain_arg1_2151(short v) {
v = (short)Math.min(Math.max(v, (short)14), (short)-11631);
return v;
}
@ForceInline
public static double constrain_arg2_2151(double v) {
return v;
}
@ForceInline
public static long constrain_arg3_2151(long v) {
v = (long)((v & -8184L) | 2147483632L);
return v;
}
@ForceInline
public static Object checksum_2151(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2173() {
    // In each iteration, generate new random values for the method arguments.
int arg0_2173 = LibraryRNG.nextInt();
int arg1_2173 = LibraryRNG.nextInt();
double arg2_2173 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_2173_compiled(arg0_2173, arg1_2173, arg2_2173);
    Object v1 = primitiveConTest_2173_reference(arg0_2173, arg1_2173, arg2_2173);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2173_compiled(int arg0_2173, int arg1_2173, double arg2_2173) {
arg0_2173 = constrain_arg0_2173(arg0_2173);
arg1_2173 = constrain_arg1_2173(arg1_2173);
arg2_2173 = constrain_arg2_2173(arg2_2173);
try {
var val = (double)(Double.min((double)((arg0_2173 >>> (-16385 - arg1_2173))), (arg2_2173 * (double)((short)-16384))));
return checksum_2173(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2173_reference(int arg0_2173, int arg1_2173, double arg2_2173) {
arg0_2173 = constrain_arg0_2173(arg0_2173);
arg1_2173 = constrain_arg1_2173(arg1_2173);
arg2_2173 = constrain_arg2_2173(arg2_2173);
try {
var val = (double)(Double.min((double)((arg0_2173 >>> (-16385 - arg1_2173))), (arg2_2173 * (double)((short)-16384))));
return checksum_2173(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_2173(int v) {
v = (int)Math.min(Math.max(v, -1693766558), -1048574);
v = (int)((v & 1992131904) | 1073741823);
return v;
}
@ForceInline
public static int constrain_arg1_2173(int v) {
v = (int)((v & 67108863) | -814393363);
return v;
}
@ForceInline
public static double constrain_arg2_2173(double v) {
return v;
}
@ForceInline
public static Object checksum_2173(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2191() {
    // In each iteration, generate new random values for the method arguments.
    Object v0 = primitiveConTest_2191_compiled();
    Object v1 = primitiveConTest_2191_reference();
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2191_compiled() {
try {
var val = (double)(-249L);
return checksum_2191(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2191_reference() {
try {
var val = (double)(-249L);
return checksum_2191(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static Object checksum_2191(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2197() {
    // In each iteration, generate new random values for the method arguments.
boolean arg0_2197 = LibraryRNG.nextBoolean();
double arg1_2197 = LibraryRNG.nextDouble();
double arg2_2197 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_2197_compiled(arg0_2197, arg1_2197, arg2_2197);
    Object v1 = primitiveConTest_2197_reference(arg0_2197, arg1_2197, arg2_2197);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2197_compiled(boolean arg0_2197, double arg1_2197, double arg2_2197) {
arg0_2197 = constrain_arg0_2197(arg0_2197);
arg1_2197 = constrain_arg1_2197(arg1_2197);
arg2_2197 = constrain_arg2_2197(arg2_2197);
try {
var val = ((double)((char)51730) / ((arg0_2197?Double.min((1.7976931348623157E308 / -1.0), arg1_2197):arg2_2197) % (double)(4.9E-324)));
return checksum_2197(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2197_reference(boolean arg0_2197, double arg1_2197, double arg2_2197) {
arg0_2197 = constrain_arg0_2197(arg0_2197);
arg1_2197 = constrain_arg1_2197(arg1_2197);
arg2_2197 = constrain_arg2_2197(arg2_2197);
try {
var val = ((double)((char)51730) / ((arg0_2197?Double.min((1.7976931348623157E308 / -1.0), arg1_2197):arg2_2197) % (double)(4.9E-324)));
return checksum_2197(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static boolean constrain_arg0_2197(boolean v) {
return v;
}
@ForceInline
public static double constrain_arg1_2197(double v) {
return v;
}
@ForceInline
public static double constrain_arg2_2197(double v) {
return v;
}
@ForceInline
public static Object checksum_2197(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2215() {
    // In each iteration, generate new random values for the method arguments.
int arg0_2215 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_2215_compiled(arg0_2215);
    Object v1 = primitiveConTest_2215_reference(arg0_2215);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2215_compiled(int arg0_2215) {
arg0_2215 = constrain_arg0_2215(arg0_2215);
try {
var val = (double)((char)((byte)((arg0_2215 / Integer.bitCount(-1022)))));
return checksum_2215(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2215_reference(int arg0_2215) {
arg0_2215 = constrain_arg0_2215(arg0_2215);
try {
var val = (double)((char)((byte)((arg0_2215 / Integer.bitCount(-1022)))));
return checksum_2215(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_2215(int v) {
return v;
}
@ForceInline
public static Object checksum_2215(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2225() {
    // In each iteration, generate new random values for the method arguments.
int arg0_2225 = LibraryRNG.nextInt();
char arg1_2225 = LibraryRNG.nextChar();
double arg2_2225 = LibraryRNG.nextDouble();
double arg3_2225 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_2225_compiled(arg0_2225, arg1_2225, arg2_2225, arg3_2225);
    Object v1 = primitiveConTest_2225_reference(arg0_2225, arg1_2225, arg2_2225, arg3_2225);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2225_compiled(int arg0_2225, char arg1_2225, double arg2_2225, double arg3_2225) {
arg0_2225 = constrain_arg0_2225(arg0_2225);
arg1_2225 = constrain_arg1_2225(arg1_2225);
arg2_2225 = constrain_arg2_2225(arg2_2225);
arg3_2225 = constrain_arg3_2225(arg3_2225);
try {
var val = ((double)((float)(arg0_2225)) % (double)(Double.sum((double)(Float.float16ToFloat((short)(arg1_2225))), (double)((byte)((float)((arg2_2225 + arg3_2225)))))));
return checksum_2225(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2225_reference(int arg0_2225, char arg1_2225, double arg2_2225, double arg3_2225) {
arg0_2225 = constrain_arg0_2225(arg0_2225);
arg1_2225 = constrain_arg1_2225(arg1_2225);
arg2_2225 = constrain_arg2_2225(arg2_2225);
arg3_2225 = constrain_arg3_2225(arg3_2225);
try {
var val = ((double)((float)(arg0_2225)) % (double)(Double.sum((double)(Float.float16ToFloat((short)(arg1_2225))), (double)((byte)((float)((arg2_2225 + arg3_2225)))))));
return checksum_2225(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_2225(int v) {
return v;
}
@ForceInline
public static char constrain_arg1_2225(char v) {
v = (char)Math.min(Math.max(v, (char)19074), (char)60562);
return v;
}
@ForceInline
public static double constrain_arg2_2225(double v) {
return v;
}
@ForceInline
public static double constrain_arg3_2225(double v) {
return v;
}
@ForceInline
public static Object checksum_2225(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2247() {
    // In each iteration, generate new random values for the method arguments.
short arg0_2247 = LibraryRNG.nextShort();
    Object v0 = primitiveConTest_2247_compiled(arg0_2247);
    Object v1 = primitiveConTest_2247_reference(arg0_2247);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2247_compiled(short arg0_2247) {
arg0_2247 = constrain_arg0_2247(arg0_2247);
try {
var val = (double)((float)(Long.compress(Long.min((34359738374L >> (2305680666642447701L | 1099511627771L)), 9007199254741005L), Short.toUnsignedLong(arg0_2247))));
return checksum_2247(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2247_reference(short arg0_2247) {
arg0_2247 = constrain_arg0_2247(arg0_2247);
try {
var val = (double)((float)(Long.compress(Long.min((34359738374L >> (2305680666642447701L | 1099511627771L)), 9007199254741005L), Short.toUnsignedLong(arg0_2247))));
return checksum_2247(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static short constrain_arg0_2247(short v) {
v = (short)Math.min(Math.max(v, (short)66), (short)25525);
v = (short)((v & (short)-1720) | (short)-7);
return v;
}
@ForceInline
public static Object checksum_2247(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2257() {
    // In each iteration, generate new random values for the method arguments.
long arg0_2257 = LibraryRNG.nextLong();
boolean arg1_2257 = LibraryRNG.nextBoolean();
long arg2_2257 = LibraryRNG.nextLong();
double arg3_2257 = LibraryRNG.nextDouble();
int arg4_2257 = LibraryRNG.nextInt();
double arg5_2257 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_2257_compiled(arg0_2257, arg1_2257, arg2_2257, arg3_2257, arg4_2257, arg5_2257);
    Object v1 = primitiveConTest_2257_reference(arg0_2257, arg1_2257, arg2_2257, arg3_2257, arg4_2257, arg5_2257);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2257_compiled(long arg0_2257, boolean arg1_2257, long arg2_2257, double arg3_2257, int arg4_2257, double arg5_2257) {
arg0_2257 = constrain_arg0_2257(arg0_2257);
arg1_2257 = constrain_arg1_2257(arg1_2257);
arg2_2257 = constrain_arg2_2257(arg2_2257);
arg3_2257 = constrain_arg3_2257(arg3_2257);
arg4_2257 = constrain_arg4_2257(arg4_2257);
arg5_2257 = constrain_arg5_2257(arg5_2257);
try {
var val = (((4.9E-324 * (double)(arg0_2257)) % Double.min((double)(72057594037927932L), (((arg1_2257 ^ false)?Double.longBitsToDouble(arg2_2257):Double.max(Double.NEGATIVE_INFINITY, (-7.525560337778962E-177 % arg3_2257))) % Double.longBitsToDouble((72057594037927937L | (35184372088820L / 4865486852653417322L)))))) + ((double)(arg4_2257) % arg5_2257));
return checksum_2257(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2257_reference(long arg0_2257, boolean arg1_2257, long arg2_2257, double arg3_2257, int arg4_2257, double arg5_2257) {
arg0_2257 = constrain_arg0_2257(arg0_2257);
arg1_2257 = constrain_arg1_2257(arg1_2257);
arg2_2257 = constrain_arg2_2257(arg2_2257);
arg3_2257 = constrain_arg3_2257(arg3_2257);
arg4_2257 = constrain_arg4_2257(arg4_2257);
arg5_2257 = constrain_arg5_2257(arg5_2257);
try {
var val = (((4.9E-324 * (double)(arg0_2257)) % Double.min((double)(72057594037927932L), (((arg1_2257 ^ false)?Double.longBitsToDouble(arg2_2257):Double.max(Double.NEGATIVE_INFINITY, (-7.525560337778962E-177 % arg3_2257))) % Double.longBitsToDouble((72057594037927937L | (35184372088820L / 4865486852653417322L)))))) + ((double)(arg4_2257) % arg5_2257));
return checksum_2257(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_2257(long v) {
v = (long)Math.min(Math.max(v, 4433627397723127615L), 79L);
return v;
}
@ForceInline
public static boolean constrain_arg1_2257(boolean v) {
return v;
}
@ForceInline
public static long constrain_arg2_2257(long v) {
v = (long)((v & -1249298528280236727L) | 7033572945960392827L);
return v;
}
@ForceInline
public static double constrain_arg3_2257(double v) {
return v;
}
@ForceInline
public static int constrain_arg4_2257(int v) {
v = (int)((v & -524286) | 449520009);
return v;
}
@ForceInline
public static double constrain_arg5_2257(double v) {
return v;
}
@ForceInline
public static Object checksum_2257(double val) {
return new Object[] {val};
}
@Test
public static void primitiveConTest_2287() {
    // In each iteration, generate new random values for the method arguments.
double arg0_2287 = LibraryRNG.nextDouble();
boolean arg1_2287 = LibraryRNG.nextBoolean();
long arg2_2287 = LibraryRNG.nextLong();
long arg3_2287 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_2287_compiled(arg0_2287, arg1_2287, arg2_2287, arg3_2287);
    Object v1 = primitiveConTest_2287_reference(arg0_2287, arg1_2287, arg2_2287, arg3_2287);
// could fail - don't verify.
}

@DontInline
public static Object primitiveConTest_2287_compiled(double arg0_2287, boolean arg1_2287, long arg2_2287, long arg3_2287) {
arg0_2287 = constrain_arg0_2287(arg0_2287);
arg1_2287 = constrain_arg1_2287(arg1_2287);
arg2_2287 = constrain_arg2_2287(arg2_2287);
arg3_2287 = constrain_arg3_2287(arg3_2287);
try {
var val = (Boolean.logicalAnd(Double.isFinite(arg0_2287), arg1_2287) ^ (((long)((short)66) + Long.max(arg2_2287, arg3_2287)) >= Double.doubleToRawLongBits((double)((1644045039445458214L * -4748359324114721511L)))));
return checksum_2287(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2287_reference(double arg0_2287, boolean arg1_2287, long arg2_2287, long arg3_2287) {
arg0_2287 = constrain_arg0_2287(arg0_2287);
arg1_2287 = constrain_arg1_2287(arg1_2287);
arg2_2287 = constrain_arg2_2287(arg2_2287);
arg3_2287 = constrain_arg3_2287(arg3_2287);
try {
var val = (Boolean.logicalAnd(Double.isFinite(arg0_2287), arg1_2287) ^ (((long)((short)66) + Long.max(arg2_2287, arg3_2287)) >= Double.doubleToRawLongBits((double)((1644045039445458214L * -4748359324114721511L)))));
return checksum_2287(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static double constrain_arg0_2287(double v) {
return v;
}
@ForceInline
public static boolean constrain_arg1_2287(boolean v) {
return v;
}
@ForceInline
public static long constrain_arg2_2287(long v) {
v = (long)Math.min(Math.max(v, 8884701735985551979L), 17179869177L);
v = (long)((v & 4503599627370481L) | -1099511627775L);
return v;
}
@ForceInline
public static long constrain_arg3_2287(long v) {
v = (long)((v & -1902354829850456370L) | 549755813883L);
return v;
}
@ForceInline
public static Object checksum_2287(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2309() {
    // In each iteration, generate new random values for the method arguments.
boolean arg0_2309 = LibraryRNG.nextBoolean();
long arg1_2309 = LibraryRNG.nextLong();
long arg2_2309 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_2309_compiled(arg0_2309, arg1_2309, arg2_2309);
    Object v1 = primitiveConTest_2309_reference(arg0_2309, arg1_2309, arg2_2309);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2309_compiled(boolean arg0_2309, long arg1_2309, long arg2_2309) {
arg0_2309 = constrain_arg0_2309(arg0_2309);
arg1_2309 = constrain_arg1_2309(arg1_2309);
arg2_2309 = constrain_arg2_2309(arg2_2309);
try {
var val = ((arg0_2309?Long.divideUnsigned(288230376151711759L, arg1_2309):arg2_2309) > 4718032541324204305L);
return checksum_2309(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2309_reference(boolean arg0_2309, long arg1_2309, long arg2_2309) {
arg0_2309 = constrain_arg0_2309(arg0_2309);
arg1_2309 = constrain_arg1_2309(arg1_2309);
arg2_2309 = constrain_arg2_2309(arg2_2309);
try {
var val = ((arg0_2309?Long.divideUnsigned(288230376151711759L, arg1_2309):arg2_2309) > 4718032541324204305L);
return checksum_2309(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static boolean constrain_arg0_2309(boolean v) {
return v;
}
@ForceInline
public static long constrain_arg1_2309(long v) {
v = (long)Math.min(Math.max(v, -1125899906842612L), -525L);
return v;
}
@ForceInline
public static long constrain_arg2_2309(long v) {
v = (long)((v & -4611686018427387888L) | 7186268731298375280L);
return v;
}
@ForceInline
public static Object checksum_2309(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2327() {
    // In each iteration, generate new random values for the method arguments.
long arg0_2327 = LibraryRNG.nextLong();
long arg1_2327 = LibraryRNG.nextLong();
double arg2_2327 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_2327_compiled(arg0_2327, arg1_2327, arg2_2327);
    Object v1 = primitiveConTest_2327_reference(arg0_2327, arg1_2327, arg2_2327);
// could fail - don't verify.
}

@DontInline
public static Object primitiveConTest_2327_compiled(long arg0_2327, long arg1_2327, double arg2_2327) {
arg0_2327 = constrain_arg0_2327(arg0_2327);
arg1_2327 = constrain_arg1_2327(arg1_2327);
arg2_2327 = constrain_arg2_2327(arg2_2327);
try {
var val = ((Long.reverse((7379280883853304887L - Long.min(-5639435563511939348L, arg0_2327))) % Double.doubleToRawLongBits(-1.0)) == (-((Long.max(arg1_2327, -2048L) & Short.toUnsignedLong((short)(arg2_2327))))));
return checksum_2327(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2327_reference(long arg0_2327, long arg1_2327, double arg2_2327) {
arg0_2327 = constrain_arg0_2327(arg0_2327);
arg1_2327 = constrain_arg1_2327(arg1_2327);
arg2_2327 = constrain_arg2_2327(arg2_2327);
try {
var val = ((Long.reverse((7379280883853304887L - Long.min(-5639435563511939348L, arg0_2327))) % Double.doubleToRawLongBits(-1.0)) == (-((Long.max(arg1_2327, -2048L) & Short.toUnsignedLong((short)(arg2_2327))))));
return checksum_2327(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_2327(long v) {
v = (long)Math.min(Math.max(v, -5820160021632391645L), -6852919424080079157L);
return v;
}
@ForceInline
public static long constrain_arg1_2327(long v) {
v = (long)((v & 3304234327488634213L) | 8633712972849261195L);
return v;
}
@ForceInline
public static double constrain_arg2_2327(double v) {
return v;
}
@ForceInline
public static Object checksum_2327(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2345() {
    // In each iteration, generate new random values for the method arguments.
long arg0_2345 = LibraryRNG.nextLong();
float arg1_2345 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_2345_compiled(arg0_2345, arg1_2345);
    Object v1 = primitiveConTest_2345_reference(arg0_2345, arg1_2345);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2345_compiled(long arg0_2345, float arg1_2345) {
arg0_2345 = constrain_arg0_2345(arg0_2345);
arg1_2345 = constrain_arg1_2345(arg1_2345);
try {
var val = (((float)(arg0_2345) * -1.574099E-15f) >= arg1_2345);
return checksum_2345(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2345_reference(long arg0_2345, float arg1_2345) {
arg0_2345 = constrain_arg0_2345(arg0_2345);
arg1_2345 = constrain_arg1_2345(arg1_2345);
try {
var val = (((float)(arg0_2345) * -1.574099E-15f) >= arg1_2345);
return checksum_2345(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_2345(long v) {
v = (long)Math.min(Math.max(v, 2399839452752939185L), -65522L);
return v;
}
@ForceInline
public static float constrain_arg1_2345(float v) {
return v;
}
@ForceInline
public static Object checksum_2345(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2359() {
    // In each iteration, generate new random values for the method arguments.
byte arg0_2359 = LibraryRNG.nextByte();
    Object v0 = primitiveConTest_2359_compiled(arg0_2359);
    Object v1 = primitiveConTest_2359_reference(arg0_2359);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2359_compiled(byte arg0_2359) {
arg0_2359 = constrain_arg0_2359(arg0_2359);
try {
var val = Float.isInfinite(Float.float16ToFloat((short)((char)((char)((Float.max(-8.475572E15f, (float)(arg0_2359)) - -0.0f))))));
return checksum_2359(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2359_reference(byte arg0_2359) {
arg0_2359 = constrain_arg0_2359(arg0_2359);
try {
var val = Float.isInfinite(Float.float16ToFloat((short)((char)((char)((Float.max(-8.475572E15f, (float)(arg0_2359)) - -0.0f))))));
return checksum_2359(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static byte constrain_arg0_2359(byte v) {
v = (byte)((v & (byte)2) | (byte)-128);
return v;
}
@ForceInline
public static Object checksum_2359(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2369() {
    // In each iteration, generate new random values for the method arguments.
int arg0_2369 = LibraryRNG.nextInt();
int arg1_2369 = LibraryRNG.nextInt();
double arg2_2369 = LibraryRNG.nextDouble();
double arg3_2369 = LibraryRNG.nextDouble();
double arg4_2369 = LibraryRNG.nextDouble();
double arg5_2369 = LibraryRNG.nextDouble();
    Object v0 = primitiveConTest_2369_compiled(arg0_2369, arg1_2369, arg2_2369, arg3_2369, arg4_2369, arg5_2369);
    Object v1 = primitiveConTest_2369_reference(arg0_2369, arg1_2369, arg2_2369, arg3_2369, arg4_2369, arg5_2369);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2369_compiled(int arg0_2369, int arg1_2369, double arg2_2369, double arg3_2369, double arg4_2369, double arg5_2369) {
arg0_2369 = constrain_arg0_2369(arg0_2369);
arg1_2369 = constrain_arg1_2369(arg1_2369);
arg2_2369 = constrain_arg2_2369(arg2_2369);
arg3_2369 = constrain_arg3_2369(arg3_2369);
arg4_2369 = constrain_arg4_2369(arg4_2369);
arg5_2369 = constrain_arg5_2369(arg5_2369);
try {
var val = (Integer.bitCount(arg0_2369) == Integer.compareUnsigned((Integer.numberOfLeadingZeros(arg1_2369) ^ Double.compare((arg2_2369 % arg3_2369), (arg4_2369 % -3.4494292099160036E245))), (int)(Double.sum(3.6762760294494553E9, arg5_2369))));
return checksum_2369(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2369_reference(int arg0_2369, int arg1_2369, double arg2_2369, double arg3_2369, double arg4_2369, double arg5_2369) {
arg0_2369 = constrain_arg0_2369(arg0_2369);
arg1_2369 = constrain_arg1_2369(arg1_2369);
arg2_2369 = constrain_arg2_2369(arg2_2369);
arg3_2369 = constrain_arg3_2369(arg3_2369);
arg4_2369 = constrain_arg4_2369(arg4_2369);
arg5_2369 = constrain_arg5_2369(arg5_2369);
try {
var val = (Integer.bitCount(arg0_2369) == Integer.compareUnsigned((Integer.numberOfLeadingZeros(arg1_2369) ^ Double.compare((arg2_2369 % arg3_2369), (arg4_2369 % -3.4494292099160036E245))), (int)(Double.sum(3.6762760294494553E9, arg5_2369))));
return checksum_2369(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_2369(int v) {
return v;
}
@ForceInline
public static int constrain_arg1_2369(int v) {
v = (int)Math.min(Math.max(v, -254), -65537);
return v;
}
@ForceInline
public static double constrain_arg2_2369(double v) {
return v;
}
@ForceInline
public static double constrain_arg3_2369(double v) {
return v;
}
@ForceInline
public static double constrain_arg4_2369(double v) {
return v;
}
@ForceInline
public static double constrain_arg5_2369(double v) {
return v;
}
@ForceInline
public static Object checksum_2369(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2399() {
    // In each iteration, generate new random values for the method arguments.
int arg0_2399 = LibraryRNG.nextInt();
float arg1_2399 = LibraryRNG.nextFloat();
float arg2_2399 = LibraryRNG.nextFloat();
float arg3_2399 = LibraryRNG.nextFloat();
long arg4_2399 = LibraryRNG.nextLong();
long arg5_2399 = LibraryRNG.nextLong();
int arg6_2399 = LibraryRNG.nextInt();
    Object v0 = primitiveConTest_2399_compiled(arg0_2399, arg1_2399, arg2_2399, arg3_2399, arg4_2399, arg5_2399, arg6_2399);
    Object v1 = primitiveConTest_2399_reference(arg0_2399, arg1_2399, arg2_2399, arg3_2399, arg4_2399, arg5_2399, arg6_2399);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2399_compiled(int arg0_2399, float arg1_2399, float arg2_2399, float arg3_2399, long arg4_2399, long arg5_2399, int arg6_2399) {
arg0_2399 = constrain_arg0_2399(arg0_2399);
arg1_2399 = constrain_arg1_2399(arg1_2399);
arg2_2399 = constrain_arg2_2399(arg2_2399);
arg3_2399 = constrain_arg3_2399(arg3_2399);
arg4_2399 = constrain_arg4_2399(arg4_2399);
arg5_2399 = constrain_arg5_2399(arg5_2399);
arg6_2399 = constrain_arg6_2399(arg6_2399);
try {
var val = (((!((arg0_2399 == -16384)))?Float.min(arg1_2399, (float)((char)40629)):(float)((true?(char)(Float.max(arg2_2399, (-1.10794424E-7f * arg3_2399))):Character.reverseBytes((char)((byte)((char)(arg4_2399))))))) <= (float)(Long.compress(arg5_2399, Integer.toUnsignedLong(arg6_2399))));
return checksum_2399(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2399_reference(int arg0_2399, float arg1_2399, float arg2_2399, float arg3_2399, long arg4_2399, long arg5_2399, int arg6_2399) {
arg0_2399 = constrain_arg0_2399(arg0_2399);
arg1_2399 = constrain_arg1_2399(arg1_2399);
arg2_2399 = constrain_arg2_2399(arg2_2399);
arg3_2399 = constrain_arg3_2399(arg3_2399);
arg4_2399 = constrain_arg4_2399(arg4_2399);
arg5_2399 = constrain_arg5_2399(arg5_2399);
arg6_2399 = constrain_arg6_2399(arg6_2399);
try {
var val = (((!((arg0_2399 == -16384)))?Float.min(arg1_2399, (float)((char)40629)):(float)((true?(char)(Float.max(arg2_2399, (-1.10794424E-7f * arg3_2399))):Character.reverseBytes((char)((byte)((char)(arg4_2399))))))) <= (float)(Long.compress(arg5_2399, Integer.toUnsignedLong(arg6_2399))));
return checksum_2399(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static int constrain_arg0_2399(int v) {
v = (int)Math.min(Math.max(v, 524287), -65534);
v = (int)((v & 1073741823) | 1209351794);
return v;
}
@ForceInline
public static float constrain_arg1_2399(float v) {
return v;
}
@ForceInline
public static float constrain_arg2_2399(float v) {
return v;
}
@ForceInline
public static float constrain_arg3_2399(float v) {
return v;
}
@ForceInline
public static long constrain_arg4_2399(long v) {
v = (long)((v & -45L) | 8704317956283722753L);
return v;
}
@ForceInline
public static long constrain_arg5_2399(long v) {
v = (long)Math.min(Math.max(v, 8796093022205L), -3297683376527800628L);
v = (long)((v & 1541862882599486797L) | -4294967288L);
return v;
}
@ForceInline
public static int constrain_arg6_2399(int v) {
v = (int)((v & 67108864) | -15);
return v;
}
@ForceInline
public static Object checksum_2399(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2433() {
    // In each iteration, generate new random values for the method arguments.
float arg0_2433 = LibraryRNG.nextFloat();
boolean arg1_2433 = LibraryRNG.nextBoolean();
float arg2_2433 = LibraryRNG.nextFloat();
float arg3_2433 = LibraryRNG.nextFloat();
    Object v0 = primitiveConTest_2433_compiled(arg0_2433, arg1_2433, arg2_2433, arg3_2433);
    Object v1 = primitiveConTest_2433_reference(arg0_2433, arg1_2433, arg2_2433, arg3_2433);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2433_compiled(float arg0_2433, boolean arg1_2433, float arg2_2433, float arg3_2433) {
arg0_2433 = constrain_arg0_2433(arg0_2433);
arg1_2433 = constrain_arg1_2433(arg1_2433);
arg2_2433 = constrain_arg2_2433(arg2_2433);
arg3_2433 = constrain_arg3_2433(arg3_2433);
try {
var val = Double.isInfinite(((double)(((-(arg0_2433)) / (arg1_2433?arg2_2433:(-0.0f * arg3_2433)))) % (double)((0.0f % (float)(8.9393046E-7f)))));
return checksum_2433(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2433_reference(float arg0_2433, boolean arg1_2433, float arg2_2433, float arg3_2433) {
arg0_2433 = constrain_arg0_2433(arg0_2433);
arg1_2433 = constrain_arg1_2433(arg1_2433);
arg2_2433 = constrain_arg2_2433(arg2_2433);
arg3_2433 = constrain_arg3_2433(arg3_2433);
try {
var val = Double.isInfinite(((double)(((-(arg0_2433)) / (arg1_2433?arg2_2433:(-0.0f * arg3_2433)))) % (double)((0.0f % (float)(8.9393046E-7f)))));
return checksum_2433(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static float constrain_arg0_2433(float v) {
return v;
}
@ForceInline
public static boolean constrain_arg1_2433(boolean v) {
return v;
}
@ForceInline
public static float constrain_arg2_2433(float v) {
return v;
}
@ForceInline
public static float constrain_arg3_2433(float v) {
return v;
}
@ForceInline
public static Object checksum_2433(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2455() {
    // In each iteration, generate new random values for the method arguments.
long arg0_2455 = LibraryRNG.nextLong();
long arg1_2455 = LibraryRNG.nextLong();
boolean arg2_2455 = LibraryRNG.nextBoolean();
double arg3_2455 = LibraryRNG.nextDouble();
float arg4_2455 = LibraryRNG.nextFloat();
long arg5_2455 = LibraryRNG.nextLong();
double arg6_2455 = LibraryRNG.nextDouble();
long arg7_2455 = LibraryRNG.nextLong();
long arg8_2455 = LibraryRNG.nextLong();
long arg9_2455 = LibraryRNG.nextLong();
    Object v0 = primitiveConTest_2455_compiled(arg0_2455, arg1_2455, arg2_2455, arg3_2455, arg4_2455, arg5_2455, arg6_2455, arg7_2455, arg8_2455, arg9_2455);
    Object v1 = primitiveConTest_2455_reference(arg0_2455, arg1_2455, arg2_2455, arg3_2455, arg4_2455, arg5_2455, arg6_2455, arg7_2455, arg8_2455, arg9_2455);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2455_compiled(long arg0_2455, long arg1_2455, boolean arg2_2455, double arg3_2455, float arg4_2455, long arg5_2455, double arg6_2455, long arg7_2455, long arg8_2455, long arg9_2455) {
arg0_2455 = constrain_arg0_2455(arg0_2455);
arg1_2455 = constrain_arg1_2455(arg1_2455);
arg2_2455 = constrain_arg2_2455(arg2_2455);
arg3_2455 = constrain_arg3_2455(arg3_2455);
arg4_2455 = constrain_arg4_2455(arg4_2455);
arg5_2455 = constrain_arg5_2455(arg5_2455);
arg6_2455 = constrain_arg6_2455(arg6_2455);
arg7_2455 = constrain_arg7_2455(arg7_2455);
arg8_2455 = constrain_arg8_2455(arg8_2455);
arg9_2455 = constrain_arg9_2455(arg9_2455);
try {
var val = (Long.reverseBytes((arg0_2455 / arg1_2455)) < (((Float.float16ToFloat((arg2_2455?(short)(arg3_2455):(short)-10)) > (Float.max(arg4_2455, 2.494124f) - Float.max(-6.4237903E-15f, -267.56754f)))?arg5_2455:Long.max((4456812545025198969L - (long)(arg6_2455)), arg7_2455)) + Long.min(Long.min(-262147L, arg8_2455), arg9_2455)));
return checksum_2455(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2455_reference(long arg0_2455, long arg1_2455, boolean arg2_2455, double arg3_2455, float arg4_2455, long arg5_2455, double arg6_2455, long arg7_2455, long arg8_2455, long arg9_2455) {
arg0_2455 = constrain_arg0_2455(arg0_2455);
arg1_2455 = constrain_arg1_2455(arg1_2455);
arg2_2455 = constrain_arg2_2455(arg2_2455);
arg3_2455 = constrain_arg3_2455(arg3_2455);
arg4_2455 = constrain_arg4_2455(arg4_2455);
arg5_2455 = constrain_arg5_2455(arg5_2455);
arg6_2455 = constrain_arg6_2455(arg6_2455);
arg7_2455 = constrain_arg7_2455(arg7_2455);
arg8_2455 = constrain_arg8_2455(arg8_2455);
arg9_2455 = constrain_arg9_2455(arg9_2455);
try {
var val = (Long.reverseBytes((arg0_2455 / arg1_2455)) < (((Float.float16ToFloat((arg2_2455?(short)(arg3_2455):(short)-10)) > (Float.max(arg4_2455, 2.494124f) - Float.max(-6.4237903E-15f, -267.56754f)))?arg5_2455:Long.max((4456812545025198969L - (long)(arg6_2455)), arg7_2455)) + Long.min(Long.min(-262147L, arg8_2455), arg9_2455)));
return checksum_2455(val);
} catch (ArithmeticException e) { return e;
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_2455(long v) {
v = (long)Math.min(Math.max(v, 2322994150570490433L), -16383L);
return v;
}
@ForceInline
public static long constrain_arg1_2455(long v) {
v = (long)Math.min(Math.max(v, 268435447L), -7744281215694879847L);
return v;
}
@ForceInline
public static boolean constrain_arg2_2455(boolean v) {
return v;
}
@ForceInline
public static double constrain_arg3_2455(double v) {
return v;
}
@ForceInline
public static float constrain_arg4_2455(float v) {
return v;
}
@ForceInline
public static long constrain_arg5_2455(long v) {
v = (long)Math.min(Math.max(v, -4294967291L), -131065L);
return v;
}
@ForceInline
public static double constrain_arg6_2455(double v) {
return v;
}
@ForceInline
public static long constrain_arg7_2455(long v) {
v = (long)Math.min(Math.max(v, 5280625050016499894L), 1073741835L);
v = (long)((v & -5421135196662501356L) | -4771549579014441607L);
return v;
}
@ForceInline
public static long constrain_arg8_2455(long v) {
return v;
}
@ForceInline
public static long constrain_arg9_2455(long v) {
v = (long)((v & 1517518270977861942L) | -16380L);
return v;
}
@ForceInline
public static Object checksum_2455(boolean val) {
return new Object[] {val, val == true, val == false};
}
@Test
public static void primitiveConTest_2501() {
    // In each iteration, generate new random values for the method arguments.
long arg0_2501 = LibraryRNG.nextLong();
char arg1_2501 = LibraryRNG.nextChar();
long arg2_2501 = LibraryRNG.nextLong();
long arg3_2501 = LibraryRNG.nextLong();
boolean arg4_2501 = LibraryRNG.nextBoolean();
    Object v0 = primitiveConTest_2501_compiled(arg0_2501, arg1_2501, arg2_2501, arg3_2501, arg4_2501);
    Object v1 = primitiveConTest_2501_reference(arg0_2501, arg1_2501, arg2_2501, arg3_2501, arg4_2501);
Verify.checkEQ(v0, v1);
}

@DontInline
public static Object primitiveConTest_2501_compiled(long arg0_2501, char arg1_2501, long arg2_2501, long arg3_2501, boolean arg4_2501) {
arg0_2501 = constrain_arg0_2501(arg0_2501);
arg1_2501 = constrain_arg1_2501(arg1_2501);
arg2_2501 = constrain_arg2_2501(arg2_2501);
arg3_2501 = constrain_arg3_2501(arg3_2501);
arg4_2501 = constrain_arg4_2501(arg4_2501);
try {
var val = Boolean.logicalAnd(Boolean.logicalAnd(((float)((long)(arg0_2501)) > (float)(arg1_2501)), (Long.lowestOneBit(arg2_2501) <= arg3_2501)), arg4_2501);
return checksum_2501(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@DontCompile
public static Object primitiveConTest_2501_reference(long arg0_2501, char arg1_2501, long arg2_2501, long arg3_2501, boolean arg4_2501) {
arg0_2501 = constrain_arg0_2501(arg0_2501);
arg1_2501 = constrain_arg1_2501(arg1_2501);
arg2_2501 = constrain_arg2_2501(arg2_2501);
arg3_2501 = constrain_arg3_2501(arg3_2501);
arg4_2501 = constrain_arg4_2501(arg4_2501);
try {
var val = Boolean.logicalAnd(Boolean.logicalAnd(((float)((long)(arg0_2501)) > (float)(arg1_2501)), (Long.lowestOneBit(arg2_2501) <= arg3_2501)), arg4_2501);
return checksum_2501(val);
} finally {
    // Just so that javac is happy if there are no exceptions to catch.
}
}

@ForceInline
public static long constrain_arg0_2501(long v) {
return v;
}
@ForceInline
public static char constrain_arg1_2501(char v) {
v = (char)((v & (char)75) | (char)32752);
return v;
}
@ForceInline
public static long constrain_arg2_2501(long v) {
return v;
}
@ForceInline
public static long constrain_arg3_2501(long v) {
v = (long)Math.min(Math.max(v, 2208934098988872211L), 65528L);
v = (long)((v & 288230376151711739L) | -137438953457L);
return v;
}
@ForceInline
public static boolean constrain_arg4_2501(boolean v) {
return v;
}
@ForceInline
public static Object checksum_2501(boolean val) {
return new Object[] {val, val == true, val == false};
}
// --- LIST OF TESTS end   ---
}
