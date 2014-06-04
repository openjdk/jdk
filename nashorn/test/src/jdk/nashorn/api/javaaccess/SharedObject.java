/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.api.javaaccess;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

public class SharedObject {

    // Public fields
    public String                 publicString                  = "PublicString";
    public String[]               publicStringArray             = { "ArrayString[0]", "ArrayString[1]", "ArrayString[2]", "ArrayString[3]" };
    public Person                 publicObject                  = new Person(256);
    public Person[]               publicObjectArray             = { new Person(4), new Person(-422), new Person(14) };
    public boolean                publicBoolean                 = true;
    public boolean[]              publicBooleanArray            = { true, false, false, true };
    public Boolean                publicBooleanBox              = true;
    public long                   publicLong                    = 933333333333333333L;
    public long[]                 publicLongArray               = { 99012333333333L, -124355555L, 89777777777L };
    public Long                   publicLongBox                 = 9333333333L;
    public int                    publicInt                     = 2076543123;
    public int[]                  publicIntArray                = { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 };
    public Integer                publicIntBox                  = 20765123;
    public byte                   publicByte                    = -128;
    public byte[]                 publicByteArray               = { 1, 2, 4, 8, 16, 32, 64, 127, -128 };
    public Byte                   publicByteBox                 = 127;
    public short                  publicShort                   = 32000;
    public short[]                publicShortArray              = { 3240, 8900, -16789, 1, 12 };
    public Short                  publicShortBox                = Short.MIN_VALUE;
    public float                  publicFloat                   = 0.7f;
    public float[]                publicFloatArray              = { -32.01f, 89.3f, -1.3e8f, 3.1f };
    public Float                  publicFloatBox                = 1.377e4f;
    public double                 publicDouble                  = 1.34e20;
    public double[]               publicDoubleArray             = { 0.75e80, 8e-43, 1.000077, 0.123e10 };
    public Double                 publicDoubleBox               = 1.4e-19;
    public char                   publicChar                    = 'A';
    public char[]                 publicCharArray               = "Hello Nashorn".toCharArray();
    public Character              publicCharBox                 = 'B';
    // Public static fields
    public static String          publicStaticString            = "PublicStaticString";
    public static String[]        publicStaticStringArray       = { "StaticArrayString[0]", "StaticArrayString[1]", "StaticArrayString[2]", "StaticArrayString[3]" };
    public static Person          publicStaticObject            = new Person(512);
    public static Person[]        publicStaticObjectArray       = { new Person(40), new Person(-22), new Person(18) };
    public static boolean         publicStaticBoolean           = true;
    public static boolean[]       publicStaticBooleanArray      = { false, false, false, true };
    public static Boolean         publicStaticBooleanBox        = true;
    public static long            publicStaticLong              = 13333333333333333L;
    public static long[]          publicStaticLongArray         = { 19012333333333L, -224355555L, 39777777777L };
    public static Long            publicStaticLongBox           = 9333333334L;
    public static int             publicStaticInt               = 207654323;
    public static int[]           publicStaticIntArray          = { 5, 8, 13, 21, 34 };
    public static Integer         publicStaticIntBox            = 2075123;
    public static byte            publicStaticByte              = -12;
    public static byte[]          publicStaticByteArray         = { 16, 32, 64, 127, -128 };
    public static Byte            publicStaticByteBox           = 17;
    public static short           publicStaticShort             = 320;
    public static short[]         publicStaticShortArray        = { 1240, 900, -1789, 100, 12 };
    public static Short           publicStaticShortBox          = -16777;
    public static float           publicStaticFloat             = 7.7e8f;
    public static float[]         publicStaticFloatArray        = { -131.01f, 189.3f, -31.3e8f, 3.7f };
    public static Float           publicStaticFloatBox          = 1.37e4f;
    public static double          publicStaticDouble            = 1.341e20;
    public static double[]        publicStaticDoubleArray       = { 0.75e80, 0.123e10, 8e-43, 1.000077 };
    public static Double          publicStaticDoubleBox         = 1.41e-12;
    public static char            publicStaticChar              = 'C';
    public static char[]          publicStaticCharArray         = "Nashorn".toCharArray();
    public static Character       publicStaticCharBox           = 'D';
    // Public final fields
    public final String           publicFinalString             = "PublicFinalString";
    public final String[]         publicFinalStringArray        = { "FinalArrayString[0]", "FinalArrayString[1]", "FinalArrayString[2]", "FinalArrayString[3]" };
    public final Person           publicFinalObject             = new Person(1024);
    public final Person[]         publicFinalObjectArray        = { new Person(-900), new Person(1000), new Person(180) };
    public final boolean          publicFinalBoolean            = true;
    public final boolean[]        publicFinalBooleanArray       = { false, false, true, false };
    public final Boolean          publicFinalBooleanBox         = true;
    public final long             publicFinalLong               = 13353333333333333L;
    public final long[]           publicFinalLongArray          = { 1901733333333L, -2247355555L, 3977377777L };
    public final Long             publicFinalLongBox            = 9377333334L;
    public final int              publicFinalInt                = 20712023;
    public final int[]            publicFinalIntArray           = { 50, 80, 130, 210, 340 };
    public final Integer          publicFinalIntBox             = 207512301;
    public final byte             publicFinalByte               = -7;
    public final byte[]           publicFinalByteArray          = { 1, 3, 6, 17, -128 };
    public final Byte             publicFinalByteBox            = 19;
    public final short            publicFinalShort              = 31220;
    public final short[]          publicFinalShortArray         = { 12240, 9200, -17289, 1200, 12 };
    public final Short            publicFinalShortBox           = -26777;
    public final float            publicFinalFloat              = 7.72e8f;
    public final float[]          publicFinalFloatArray         = { -131.012f, 189.32f, -31.32e8f, 3.72f };
    public final Float            publicFinalFloatBox           = 1.372e4f;
    public final double           publicFinalDouble             = 1.3412e20;
    public final double[]         publicFinalDoubleArray        = { 0.725e80, 0.12e10, 8e-3, 1.00077 };
    public final Double           publicFinalDoubleBox          = 1.412e-12;
    public final char             publicFinalChar               = 'E';
    public final char[]           publicFinalCharArray          = "Nashorn hello".toCharArray();
    public final Character        publicFinalCharBox            = 'F';
    // Public static final fields
    public static final String    publicStaticFinalString       = "PublicStaticFinalString";
    public static final String[]  publicStaticFinalStringArray  = { "StaticFinalArrayString[0]", "StaticFinalArrayString[1]", "StaticFinalArrayString[2]", "StaticFinalArrayString[3]" };
    public static final Person    publicStaticFinalObject       = new Person(2048);
    public static final Person[]  publicStaticFinalObjectArray  = { new Person(-9), new Person(110), new Person(Integer.MAX_VALUE) };
    public static final boolean   publicStaticFinalBoolean      = true;
    public static final boolean[] publicStaticFinalBooleanArray = { false, true, false, false };
    public static final Boolean   publicStaticFinalBooleanBox   = true;
    public static final long      publicStaticFinalLong         = 8333333333333L;
    public static final long[]    publicStaticFinalLongArray    = { 19017383333L, -2247358L, 39773787L };
    public static final Long      publicStaticFinalLongBox      = 9377388334L;
    public static final int       publicStaticFinalInt          = 207182023;
    public static final int[]     publicStaticFinalIntArray     = { 1308, 210, 340 };
    public static final Integer   publicStaticFinalIntBox       = 2078301;
    public static final byte      publicStaticFinalByte         = -70;
    public static final byte[]    publicStaticFinalByteArray    = { 17, -128, 81 };
    public static final Byte      publicStaticFinalByteBox      = 91;
    public static final short     publicStaticFinalShort        = 8888;
    public static final short[]   publicStaticFinalShortArray   = { 8240, 9280, -1289, 120, 812 };
    public static final Short     publicStaticFinalShortBox     = -26;
    public static final float     publicStaticFinalFloat        = 0.72e8f;
    public static final float[]   publicStaticFinalFloatArray   = { -8131.012f, 9.32f, -138.32e8f, 0.72f };
    public static final Float     publicStaticFinalFloatBox     = 1.2e4f;
    public static final double    publicStaticFinalDouble       = 1.8e12;
    public static final double[]  publicStaticFinalDoubleArray  = { 8.725e80, 0.82e10, 18e-3, 1.08077 };
    public static final Double    publicStaticFinalDoubleBox    = 1.5612e-13;
    public static final char      publicStaticFinalChar         = 'K';
    public static final char[]    publicStaticFinalCharArray    = "StaticString".toCharArray();
    public static final Character publicStaticFinalCharBox      = 'L';

    // Special vars
    public volatile boolean       volatileBoolean               = true;
    public transient boolean      transientBoolean              = true;

    // For methods testing
    public boolean                isAccessed                    = false;
    public volatile boolean       isFinished                    = false;

    private ScriptEngine engine;

    public ScriptEngine getEngine() {
        return engine;
    }

    public void setEngine(final ScriptEngine engine) {
        this.engine = engine;
    }

    public void voidMethod() {
        isAccessed = true;
    }

    public boolean booleanMethod(final boolean arg) {
        return !arg;
    }

    public Boolean booleanBoxingMethod(final Boolean arg) {
        return !arg.booleanValue();
    }

    public boolean[] booleanArrayMethod(final boolean arg[]) {
        final boolean[] res = new boolean[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = !arg[i];
        }
        return res;
    }

    public int intMethod(final int arg) {
        return arg + arg;
    }

    public Integer intBoxingMethod(final Integer arg) {
        return arg + arg;
    }

    public int[] intArrayMethod(final int arg[]) {
        final int[] res = new int[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = arg[i] * 2;
        }
        return res;
    }

    public long longMethod(final long arg) {
        return arg + arg;
    }

    public Long longBoxingMethod(final Long arg) {
        return arg + arg;
    }

    public long[] longArrayMethod(final long[] arg) {
        final long[] res = new long[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = arg[i] * 2;
        }
        return res;
    }

    public byte byteMethod(final byte arg) {
        return (byte)(arg + arg);
    }

    public Byte byteBoxingMethod(final Byte arg) {
        return (byte)(arg + arg);
    }

    public byte[] byteArrayMethod(final byte[] arg) {
        final byte[] res = new byte[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = (byte)(arg[i] * 2);
        }
        return res;
    }

    public char charMethod(final char arg) {
        return Character.toUpperCase(arg);
    }

    public Character charBoxingMethod(final Character arg) {
        return Character.toUpperCase(arg);
    }

    public char[] charArrayMethod(final char[] arg) {
        final char[] res = new char[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = Character.toUpperCase(arg[i]);
        }
        return res;
    }

    public short shortMethod(final short arg) {
        return (short)(arg + arg);
    }

    public Short shortBoxingMethod(final Short arg) {
        return (short)(arg + arg);
    }

    public short[] shortArrayMethod(final short[] arg) {
        final short[] res = new short[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = (short)(arg[i] * 2);
        }
        return res;
    }

    public float floatMethod(final float arg) {
        return arg + arg;
    }

    public Float floatBoxingMethod(final Float arg) {
        return arg + arg;
    }

    public float[] floatArrayMethod(final float[] arg) {
        final float[] res = new float[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = arg[i] * 2;
        }
        return res;
    }

    public double doubleMethod(final double arg) {
        return arg + arg;
    }

    public Double doubleBoxingMethod(final Double arg) {
        return arg + arg;
    }

    public double[] doubleArrayMethod(final double[] arg) {
        final double[] res = new double[arg.length];
        for (int i = 0; i < arg.length; i++) {
            res[i] = arg[i] * 2;
        }
        return res;
    }

    public String stringMethod(final String str) {
        return str + str;
    }

    public String[] stringArrayMethod(final String[] arr) {
        final int l = arr.length;
        final String[] res = new String[l];
        for (int i = 0; i < l; i++) {
            res[i] = arr[l - i - 1];
        }
        return res;
    }

    public Person[] objectArrayMethod(final Person[] arr) {
        final Person[] res = new Person[arr.length];
        for (int i = 0; i < arr.length; i++) {
            res[i] = new Person(i + 100);
        }
        return res;
    }

    public Person objectMethod(final Person t) {
        t.id *= 2;
        return t;
    }

    public int twoParamMethod(final long l, final double d) {
        return (int)(l + d);
    }

    public int threeParamMethod(final short s, final long l, final char c) {
        return (int)(s + l + c);
    }

    public Person[] twoObjectParamMethod(final Person arg1, final Person arg2) {
        return new Person[] { arg2, arg1 };
    }

    public Person[] threeObjectParamMethod(final Person arg1, final Person arg2, final Person arg3) {
        return new Person[] { arg3, arg2, arg1 };
    }

    public Person[] eightObjectParamMethod(final Person arg1, final Person arg2, final Person arg3, final Person arg4, final Person arg5, final Person arg6, final Person arg7, final Person arg8) {
        return new Person[] { arg8, arg7, arg6, arg5, arg4, arg3, arg2, arg1 };
    }

    public Person[] nineObjectParamMethod(final Person arg1, final Person arg2, final Person arg3, final Person arg4, final Person arg5, final Person arg6, final Person arg7, final Person arg8, final Person arg9) {
        return new Person[] { arg9, arg8, arg7, arg6, arg5, arg4, arg3, arg2, arg1 };
    }

    public Person[] methodObjectEllipsis(final Person... args) {
        final int l = args.length;
        final Person[] res = new Person[l];
        for (int i = 0; i < l; i++) {
            res[i] = args[l - i - 1];
        }
        return res;
    }

    public Person[] methodPrimitiveEllipsis(final int... args) {
        final int l = args.length;
        final Person[] res = new Person[l];
        for (int i = 0; i < l; i++) {
            res[i] = new Person(args[i]);
        }
        return res;
    }

    public Object[] methodMixedEllipsis(final Object... args) {
        return args;
    }

    public Object[] methodObjectWithEllipsis(final String arg, final int... args) {
        final Object[] res = new Object[args.length + 1];
        res[0] = arg;
        for (int i = 0; i < args.length; i++) {
            res[i + 1] = args[i];
        }
        return res;
    }

    public Object[] methodPrimitiveWithEllipsis(final int arg, final long... args) {
        final Object[] res = new Object[args.length + 1];
        res[0] = arg;
        for (int i = 0; i < args.length; i++) {
            res[i + 1] = args[i];
        }
        return res;
    }

    public Object[] methodMixedWithEllipsis(final String arg1, final int arg2, final Object... args) {
        final Object[] res = new Object[args.length + 2];
        res[0] = arg1;
        res[1] = arg2;
        System.arraycopy(args, 0, res, 2, args.length);
        return res;
    }

    public void methodStartsThread() {
        isFinished = false;

        final Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    isFinished = true;
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        t.start();
    }

    public String overloadedMethodDoubleVSint(final int arg) {
        return "int";
    }

    public String overloadedMethodDoubleVSint(final double arg) {
        return "double";
    }

    public int overloadedMethod(final int arg) {
        return arg*2;
    }

    public int overloadedMethod(final String arg) {
        return arg.length();
    }

    public int overloadedMethod(final boolean arg) {
        return (arg) ? 1 : 0;
    }

    public int overloadedMethod(final Person arg) {
        return arg.id*2;
    }

    public int firstLevelMethodInt(final int arg) throws ScriptException, NoSuchMethodException {
        return (int) ((Invocable)engine).invokeFunction("secondLevelMethodInt", arg);
    }

    public int thirdLevelMethodInt(final int arg) {
        return arg*5;
    }

    public int firstLevelMethodInteger(final Integer arg) throws ScriptException, NoSuchMethodException {
        return (int) ((Invocable)engine).invokeFunction("secondLevelMethodInteger", arg);
    }

    public int thirdLevelMethodInteger(final Integer arg) {
        return arg*10;
    }

    public Person firstLevelMethodObject(final Person p) throws ScriptException, NoSuchMethodException {
        return (Person) ((Invocable)engine).invokeFunction("secondLevelMethodObject", p);
    }

    public Person thirdLevelMethodObject(final Person p) {
        p.id *= 10;
        return p;
    }

}
