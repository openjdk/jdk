/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @bug 8277451
 * @summary Test exception thrown due to bad receiver and bad value on
 *          Field with and without setAccessible(true)
 * @run junit/othervm --enable-final-field-mutation=ALL-UNNAMED NegativeTest
 */

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class NegativeTest {
    static class Fields {
        public static int si;
        public static char sc;
        public static byte sb;
        public static short ss;
        public static long sl;
        public static double sd;
        public static float sf;
        public static boolean sz;
        public static String so;

        public static final int sfi = 10;
        public static final char sfc = 'a';
        public static final byte sfb = 1;
        public static final short sfs = 2;
        public static final long sfl = 1000L;
        public static final double sfd = 1.0;
        public static final float sff = 2.0f;
        public static final boolean sfz = true;
        public static final String sfo = "abc";

        public int i;
        public char c;
        public byte b;
        public short s;
        public long l;
        public double d;
        public float f;
        public boolean z;
        public String o;

        public final int fi = 10;
        public final char fc = 'a';
        public final byte fb = 1;
        public final short fs = 2;
        public final long fl = 1000L;
        public final double fd = 1.0;
        public final float ff = 2.0f;
        public final boolean fz = true;
        public final String fo = "abc";
    }

    static final Field i_field = field("i", false);
    static final Field c_field = field("c", false);
    static final Field b_field = field("b", false);
    static final Field s_field = field("s", false);
    static final Field l_field = field("l", false);
    static final Field d_field = field("d", false);
    static final Field f_field = field("f", false);
    static final Field z_field = field("z", false);
    static final Field o_field = field("o", false);
    static final Field fi_field = field("fi", false);
    static final Field fc_field = field("fc", false);
    static final Field fb_field = field("fb", false);
    static final Field fs_field = field("fs", false);
    static final Field fl_field = field("fl", false);
    static final Field fd_field = field("fd", false);
    static final Field ff_field = field("ff", false);
    static final Field fz_field = field("fz", false);
    static final Field fo_field = field("fo", false);

    static final Field override_i_field = field("i", true);
    static final Field override_c_field = field("c", true);
    static final Field override_b_field = field("b", true);
    static final Field override_s_field = field("s", true);
    static final Field override_l_field = field("l", true);
    static final Field override_d_field = field("d", true);
    static final Field override_f_field = field("f", true);
    static final Field override_z_field = field("z", true);
    static final Field override_o_field = field("o", true);
    static final Field override_fi_field = field("fi", true);
    static final Field override_fc_field = field("fc", true);
    static final Field override_fb_field = field("fb", true);
    static final Field override_fs_field = field("fs", true);
    static final Field override_fl_field = field("fl", true);
    static final Field override_fd_field = field("fd", true);
    static final Field override_ff_field = field("ff", true);
    static final Field override_fz_field = field("fz", true);
    static final Field override_fo_field = field("fo", true);

    static final Field si_field = field("si", false);
    static final Field sc_field = field("sc", false);
    static final Field sb_field = field("sb", false);
    static final Field ss_field = field("ss", false);
    static final Field sl_field = field("sl", false);
    static final Field sd_field = field("sd", false);
    static final Field sf_field = field("sf", false);
    static final Field sz_field = field("sz", false);
    static final Field so_field = field("so", false);
    static final Field sfi_field = field("sfi", false);
    static final Field sfc_field = field("sfc", false);
    static final Field sfb_field = field("sfb", false);
    static final Field sfs_field = field("sfs", false);
    static final Field sfl_field = field("sfl", false);
    static final Field sfd_field = field("sfd", false);
    static final Field sff_field = field("sff", false);
    static final Field sfz_field = field("sfz", false);
    static final Field sfo_field = field("sfo", false);

    static final Field override_si_field = field("si", true);
    static final Field override_sc_field = field("sc", true);
    static final Field override_sb_field = field("sb", true);
    static final Field override_ss_field = field("ss", true);
    static final Field override_sl_field = field("sl", true);
    static final Field override_sd_field = field("sd", true);
    static final Field override_sf_field = field("sf", true);
    static final Field override_sz_field = field("sz", true);
    static final Field override_so_field = field("so", true);
    static final Field override_sfi_field = field("sfi", true);
    static final Field override_sfc_field = field("sfc", true);
    static final Field override_sfb_field = field("sfb", true);
    static final Field override_sfs_field = field("sfs", true);
    static final Field override_sfl_field = field("sfl", true);
    static final Field override_sfd_field = field("sfd", true);
    static final Field override_sff_field = field("sff", true);
    static final Field override_sfz_field = field("sfz", true);
    static final Field override_sfo_field = field("sfo", true);

    private static Field field(String name, boolean suppressAccessCheck) {
        try {
            Field f = Fields.class.getDeclaredField(name);
            if (suppressAccessCheck) {
                f.setAccessible(true);
            }
            return f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object[][] instanceFields() {
        return new Object[][]{
                new Object[]{i_field},
                new Object[]{c_field},
                new Object[]{b_field},
                new Object[]{s_field},
                new Object[]{l_field},
                new Object[]{d_field},
                new Object[]{f_field},
                new Object[]{z_field},
                new Object[]{o_field},
                new Object[]{override_i_field},
                new Object[]{override_c_field},
                new Object[]{override_b_field},
                new Object[]{override_s_field},
                new Object[]{override_l_field},
                new Object[]{override_d_field},
                new Object[]{override_f_field},
                new Object[]{override_z_field},
                new Object[]{override_o_field},
                // final instance fields
                new Object[]{fi_field},
                new Object[]{fc_field},
                new Object[]{fb_field},
                new Object[]{fs_field},
                new Object[]{fl_field},
                new Object[]{fd_field},
                new Object[]{ff_field},
                new Object[]{fz_field},
                new Object[]{fo_field},
                new Object[]{override_fi_field},
                new Object[]{override_fc_field},
                new Object[]{override_fb_field},
                new Object[]{override_fs_field},
                new Object[]{override_fl_field},
                new Object[]{override_fd_field},
                new Object[]{override_ff_field},
                new Object[]{override_fz_field},
                new Object[]{override_fo_field},
        };
    }
    private static Fields INSTANCE = new Fields();

    /*
     * Test Field::get on a good receiver, a bad receiver and null.
     *
     * IllegalArgumentException is thrown if the receiver is of
     * a bad type.  NullPointerException is thrown if the receiver is null.
     */
    @ParameterizedTest
    @MethodSource("instanceFields")
    public void testReceiver(Field f) throws ReflectiveOperationException {
        f.get(INSTANCE);     // good receiver

        testBadReceiver(f);
        testNullReceiver(f);
    }

    /*
     * IllegalArgumentException should be thrown for bad receiver type
     */
    private void testBadReceiver(Field f) throws ReflectiveOperationException {
        assertFalse(Modifier.isStatic(f.getModifiers()));  // instance field
        Object badObj = new NegativeTest();
        assertThrows(IllegalArgumentException.class, () -> f.get(badObj));
        Class<?> fType = f.getType();
        if (fType.isPrimitive()) {
            assertThrows(IllegalArgumentException.class, () -> {
                switch (fType.descriptorString()) {
                    case "B" -> f.getByte(badObj);
                    case "C" -> f.getChar(badObj);
                    case "D" -> f.getDouble(badObj);
                    case "F" -> f.getFloat(badObj);
                    case "I" -> f.getInt(badObj);
                    case "J" -> f.getLong(badObj);
                    case "S" -> f.getShort(badObj);
                    case "Z" -> f.getBoolean(badObj);
                }
            });
        }
    }

    /*
     * NullPointerException should be thrown for null receiver
     */
    private void testNullReceiver(Field f) throws ReflectiveOperationException {
        assertFalse(Modifier.isStatic(f.getModifiers()));  // instance field
        assertThrows(NullPointerException.class, () -> f.get(null));

        Class<?> fType = f.getType();
        if (fType.isPrimitive()) {
            assertThrows(NullPointerException.class, () -> {
                switch (fType.descriptorString()) {
                    case "B" -> f.getByte(null);
                    case "C" -> f.getChar(null);
                    case "D" -> f.getDouble(null);
                    case "F" -> f.getFloat(null);
                    case "I" -> f.getInt(null);
                    case "J" -> f.getLong(null);
                    case "S" -> f.getShort(null);
                    case "Z" -> f.getBoolean(null);
                }
            });
        }
    }

    private static Object[][] writeableFields() {
        Fields obj = new Fields();
        return new Object[][]{
                // instance fields with and without setAccessible(true)
                new Object[]{i_field, obj, Integer.valueOf(10)},
                new Object[]{c_field, obj, Character.valueOf('c')},
                new Object[]{b_field, obj, Byte.valueOf((byte)1)},
                new Object[]{s_field, obj, Short.valueOf((short)2)},
                new Object[]{l_field, obj, Long.valueOf(1000)},
                new Object[]{d_field, obj, Double.valueOf(1.2)},
                new Object[]{f_field, obj, Float.valueOf(2.5f)},
                new Object[]{z_field, obj, Boolean.valueOf(true)},
                new Object[]{o_field, obj, "good-value"},
                new Object[]{override_i_field, obj, Integer.valueOf(10)},
                new Object[]{override_c_field, obj, Character.valueOf('c')},
                new Object[]{override_b_field, obj, Byte.valueOf((byte)1)},
                new Object[]{override_s_field, obj, Short.valueOf((short)2)},
                new Object[]{override_l_field, obj, Long.valueOf(1000)},
                new Object[]{override_d_field, obj, Double.valueOf(1.2)},
                new Object[]{override_f_field, obj, Float.valueOf(2.5f)},
                new Object[]{override_z_field, obj, Boolean.valueOf(true)},
                new Object[]{override_o_field, obj, "good-value"},
                // instance final fields with setAccessible(true)
                new Object[]{override_fi_field, obj, Integer.valueOf(10)},
                new Object[]{override_fc_field, obj, Character.valueOf('c')},
                new Object[]{override_fb_field, obj, Byte.valueOf((byte)1)},
                new Object[]{override_fs_field, obj, Short.valueOf((short)2)},
                new Object[]{override_fl_field, obj, Long.valueOf(1000)},
                new Object[]{override_fd_field, obj, Double.valueOf(1.2)},
                new Object[]{override_ff_field, obj, Float.valueOf(2.5f)},
                new Object[]{override_fz_field, obj, Boolean.valueOf(true)},
                new Object[]{override_fo_field, obj, "good-value"},
                // static fields with and without setAccessible(true)
                new Object[]{si_field, null, Integer.valueOf(10)},
                new Object[]{sc_field, null, Character.valueOf('c')},
                new Object[]{sb_field, null, Byte.valueOf((byte)1)},
                new Object[]{ss_field, null, Short.valueOf((short)2)},
                new Object[]{sl_field, null, Long.valueOf(1000)},
                new Object[]{sd_field, null, Double.valueOf(1.2)},
                new Object[]{sf_field, null, Float.valueOf(2.5f)},
                new Object[]{sz_field, null, Boolean.valueOf(true)},
                new Object[]{so_field, null, "good-value"},
                new Object[]{override_si_field, null, Integer.valueOf(10)},
                new Object[]{override_sc_field, null, Character.valueOf('c')},
                new Object[]{override_sb_field, null, Byte.valueOf((byte)1)},
                new Object[]{override_ss_field, null, Short.valueOf((short)2)},
                new Object[]{override_sl_field, null, Long.valueOf(1000)},
                new Object[]{override_sd_field, null, Double.valueOf(1.2)},
                new Object[]{override_sf_field, null, Float.valueOf(2.5f)},
                new Object[]{override_sz_field, null, Boolean.valueOf(true)},
                new Object[]{override_so_field, null, "good-value"},
        };
    }

    /*
     * Test Field::set with a good and bad value.
     * Test setting to null if the field type is primitive.
     *
     * IllegalArgumentException is thrown if the value is of a bad type or null.
     * NullPointerException is thrown if the receiver of an instance field is null.
     * The receiver is checked
     */
    @ParameterizedTest
    @MethodSource("writeableFields")
    public void testSetValue(Field f, Object obj, Object value) throws IllegalAccessException {
        f.set(obj, value);
        Class<?> fType = f.getType();
        if (fType.isPrimitive()) {
            switch (fType.descriptorString()) {
                case "B" -> f.setByte(obj, ((Byte) value).byteValue());
                case "C" -> f.setChar(obj, ((Character) value).charValue());
                case "D" -> f.setDouble(obj, ((Double) value).doubleValue());
                case "F" -> f.setFloat(obj, ((Float) value).floatValue());
                case "I" -> f.setInt(obj, ((Integer) value).intValue());
                case "J" -> f.setLong(obj, ((Long) value).longValue());
                case "S" -> f.setShort(obj, ((Short) value).shortValue());
                case "Z" -> f.setBoolean(obj, ((Boolean) value).booleanValue());
            }

            // test null value only if it's primitive type
            assertThrows(IllegalArgumentException.class, () -> f.set(obj, null));
        }

        Object badValue = new NegativeTest();
        assertThrows(IllegalArgumentException.class, () -> f.set(obj, badValue));
    }

    private static Object[][] readOnlyFinalFields() {
        Object obj = INSTANCE;
        return new Object[][]{
                // instance final fields
                new Object[]{fi_field, obj, Integer.valueOf(10)},
                new Object[]{fc_field, obj, Character.valueOf('c')},
                new Object[]{fb_field, obj, Byte.valueOf((byte)1)},
                new Object[]{fs_field, obj, Short.valueOf((short)2)},
                new Object[]{fl_field, obj, Long.valueOf(1000)},
                new Object[]{fd_field, obj, Double.valueOf(1.2)},
                new Object[]{ff_field, obj, Float.valueOf(2.5f)},
                new Object[]{fz_field, obj, Boolean.valueOf(true)},
                new Object[]{fo_field, obj, "good-value"},
                // static final fields
                new Object[]{sfi_field, null, Integer.valueOf(10)},
                new Object[]{sfc_field, null, Character.valueOf('c')},
                new Object[]{sfb_field, null, Byte.valueOf((byte)1)},
                new Object[]{sfs_field, null, Short.valueOf((short)2)},
                new Object[]{sfl_field, null, Long.valueOf(1000)},
                new Object[]{sfd_field, null, Double.valueOf(1.2)},
                new Object[]{sff_field, null, Float.valueOf(2.5f)},
                new Object[]{sfz_field, null, Boolean.valueOf(true)},
                new Object[]{sfo_field, null, "good-value"},
                new Object[]{override_sfi_field, null, Integer.valueOf(10)},
                new Object[]{override_sfc_field, null, Character.valueOf('c')},
                new Object[]{override_sfb_field, null, Byte.valueOf((byte)1)},
                new Object[]{override_sfs_field, null, Short.valueOf((short)2)},
                new Object[]{override_sfl_field, null, Long.valueOf(1000)},
                new Object[]{override_sfd_field, null, Double.valueOf(1.2)},
                new Object[]{override_sff_field, null, Float.valueOf(2.5f)},
                new Object[]{override_sfz_field, null, Boolean.valueOf(true)},
                new Object[]{override_sfo_field, null, "good-value"},
        };
    }

    /*
     * Test Field::set on a read-only final field.
     * IllegalAccessException is thrown regardless of whether the value
     * is of a bad type or not.
     */
    @ParameterizedTest
    @MethodSource("readOnlyFinalFields")
    public void testSetValueOnFinalField(Field f, Object obj, Object value) {
        assertTrue(Modifier.isFinal(f.getModifiers()));
        assertThrows(IllegalAccessException.class, () -> f.set(obj, value));

        Class<?> fType = f.getType();
        if (fType.isPrimitive()) {
            assertThrows(IllegalAccessException.class, () -> {
                switch (fType.descriptorString()) {
                    case "B" -> f.setByte(obj, ((Byte)value).byteValue());
                    case "C" -> f.setChar(obj, ((Character)value).charValue());
                    case "D" -> f.setDouble(obj, ((Double)value).doubleValue());
                    case "F" -> f.setFloat(obj, ((Float)value).floatValue());
                    case "I" -> f.setInt(obj, ((Integer)value).intValue());
                    case "J" -> f.setLong(obj, ((Long)value).longValue());
                    case "S" -> f.setShort(obj, ((Short)value).shortValue());
                    case "Z" -> f.setBoolean(obj, ((Boolean)value).booleanValue());
                }
            });

            // test null value only if it's primitive type
            assertThrows(IllegalAccessException.class, () -> f.set(obj, null));
        }

        Object badValue = new NegativeTest();
        assertThrows(IllegalAccessException.class, () -> f.set(obj, badValue));
    }

    private static Object[][] finalInstanceFields() {
        return new Object[][]{
                new Object[]{fi_field, Integer.valueOf(10)},
                new Object[]{fc_field, Character.valueOf('c')},
                new Object[]{fb_field, Byte.valueOf((byte) 1)},
                new Object[]{fs_field, Short.valueOf((short) 2)},
                new Object[]{fl_field, Long.valueOf(1000)},
                new Object[]{fd_field, Double.valueOf(1.2)},
                new Object[]{ff_field, Float.valueOf(2.5f)},
                new Object[]{fz_field, Boolean.valueOf(true)},
                new Object[]{fo_field, "good-value"},
        };
    }

    /*
     * Test Field::set on a final instance field with either a bad receiver
     * or null.  IllegalArgumentException is thrown if the receiver is of
     * a bad type.  NullPointerException is thrown if the receiver is null.
     * The receiver is checked before the access check is performed and
     * also before the value is checked.
     */
    @ParameterizedTest
    @MethodSource("finalInstanceFields")
    public void testReceiverOnFinalField(Field f, Object value) {
        assertTrue(Modifier.isFinal(f.getModifiers()));
        Object badReceiver = new NegativeTest();
        // set the field with a bad receiver with a good value
        assertThrows(IllegalArgumentException.class, () -> f.set(badReceiver, value));

        // set the field with a bad receiver with a bad value
        Object badValue = new NegativeTest();
        assertThrows(IllegalArgumentException.class, () -> f.set(badReceiver, badValue));

        // set the field with a null receiver with a good value
        assertThrows(NullPointerException.class, () -> f.set(null, value));
        // set the field with a null receiver with a bad value
        assertThrows(NullPointerException.class, () -> f.set(null, badValue));

        Class<?> fType = f.getType();
        if (fType.isPrimitive()) {
            // test bad receiver
            assertThrows(IllegalArgumentException.class, () -> {
                switch (fType.descriptorString()) {
                    case "B" -> f.setByte(badReceiver, ((Byte) value).byteValue());
                    case "C" -> f.setChar(badReceiver, ((Character) value).charValue());
                    case "D" -> f.setDouble(badReceiver, ((Double) value).doubleValue());
                    case "F" -> f.setFloat(badReceiver, ((Float) value).floatValue());
                    case "I" -> f.setInt(badReceiver, ((Integer) value).intValue());
                    case "J" -> f.setLong(badReceiver, ((Long) value).longValue());
                    case "S" -> f.setShort(badReceiver, ((Short) value).shortValue());
                    case "Z" -> f.setBoolean(badReceiver, ((Boolean) value).booleanValue());
                }
            });
            // test null receiver
            assertThrows(NullPointerException.class, () -> {
                switch (fType.descriptorString()) {
                    case "B" -> f.setByte(null, ((Byte) value).byteValue());
                    case "C" -> f.setChar(null, ((Character) value).charValue());
                    case "D" -> f.setDouble(null, ((Double) value).doubleValue());
                    case "F" -> f.setFloat(null, ((Float) value).floatValue());
                    case "I" -> f.setInt(null, ((Integer) value).intValue());
                    case "J" -> f.setLong(null, ((Long) value).longValue());
                    case "S" -> f.setShort(null, ((Short) value).shortValue());
                    case "Z" -> f.setBoolean(null, ((Boolean) value).booleanValue());
                }
            });
        }
    }
}
