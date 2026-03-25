/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package runtime.valhalla.inlinetypes;

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;



import static jdk.test.lib.Asserts.*;

/*
 * @test id=default
 * @summary Plain array test for Inline Types
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @library /test/lib
 * @enablePreview
 * @compile --source 27 FlatArraysTest.java
 * @run main/othervm -XX:+UseArrayFlattening -XX:+UseFieldFlattening -XX:+UseAtomicValueFlattening -XX:+UseNullableValueFlattening runtime.valhalla.inlinetypes.FlatArraysTest
 */

/*
 * @test id=no-array-flattening
 * @summary Plain array test for Inline Types
 * @requires vm.flagless
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.misc
 * @library /test/lib
 * @enablePreview
 * @compile --source 27 FlatArraysTest.java
 * @run main/othervm -XX:-UseArrayFlattening -XX:+UseAtomicValueFlattening -XX:+UseNullableValueFlattening runtime.valhalla.inlinetypes.FlatArraysTest
 */

public class FlatArraysTest {
  static final int ARRAY_SIZE = 100;
  static final Unsafe UNSAFE = Unsafe.getUnsafe();
  static boolean UseArrayFlattening;

  static {
      RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
      List<String> arguments = runtimeMxBean.getInputArguments();
      UseArrayFlattening = !arguments.contains("-XX:-UseArrayFlattening");
      System.out.println("UseArrayFlattening: " + UseArrayFlattening);
  }

  @LooselyConsistentValue
  static value class SmallValue {
      byte b;
      short s;

      SmallValue() { b = 0 ;  s = 0; }
      SmallValue(byte b0, short s0) { b = b0; s = s0; }

      public static Object getTestValue() { return new SmallValue(Byte.MIN_VALUE, Short.MIN_VALUE); }

      public static boolean expectingFlatNullRestrictedArray() { return true; }
      public static boolean expectingFlatNullRestrictedAtomicArray() { return true; }
      public static boolean expectingFlatNullableAtomicArray() { return true; }
  }

  @LooselyConsistentValue
  static value class MediumValue {
      int x;
      int y;

      MediumValue() {
         x = 0;
         y = 0;
      }
      MediumValue(int x0, int y0) {
        x = x0;
        y = y0;
      }

      public static Object getTestValue() {
        return new MediumValue(Integer.MIN_VALUE, Integer.MIN_VALUE);
      }

      public static boolean expectingFlatNullRestrictedArray() { return true; }
      public static boolean expectingFlatNullRestrictedAtomicArray() { return true; }
      public static boolean expectingFlatNullableAtomicArray() { return false; }
  }

  @LooselyConsistentValue
  static value class BigValue {
      long x;
      long y;
      long z;

      BigValue() {
        x = 0;
        y = 0;
        z = 0;
      }
      BigValue(long x0, long y0, long z0) {
        x = x0;
        y = y0;
        z = z0;
      }

      public static Object getTestValue() {
        return new BigValue(Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);
      }

      public static boolean expectingFlatNullRestrictedArray() { return true; }
      public static boolean expectingFlatNullRestrictedAtomicArray() { return false; }
      public static boolean expectingFlatNullableAtomicArray() { return false; }
  }

  static void testNullFreeArray(Object[] array, Object value) {
    testErrorCases(array);
    assertNotNull(value, "Test needs a not null value");
    //   Test 1 : check initial element value is not null
    for (int i = 0 ; i < array.length; i++) {
      assertNotNull(array[i], "Initial value must not be null");
    }
    //   Test 2 : try to write null
    for (int i = 0 ; i < array.length; i++) {
      try {
        array[i] = null;
        throw new RuntimeException("Missing NullPointerException");
      } catch (NullPointerException e) { }
    }
    //   Test 3 : overwrite initial value with new value
    for (int i = 0 ; i < array.length; i++) {
      array[i] = value;
    }
    for (int i = 0 ; i < array.length; i++) {
      assertEquals(array[i], value);
    }
  }

  static void testNullableArray(Object[] array, Object value) {
    testErrorCases(array);
    assertNotNull(value, "Test needs a not null value");
    //   Test 1 : check that initial element value is null
    System.gc();
    System.out.println("Test 1");
    for (int i = 0 ; i < array.length; i++) {
      assertNull(array[i], "Initial value should be null");
    }
    //   Test 2 : write new value to all elements
    System.gc();
    System.out.println("Test 2a");
    for (int i = 0 ; i < array.length; i++) {
      array[i] = value;
      assertEquals(array[i], value, "Value mismatch");
    }
    System.gc();
    System.out.println("Test 2b");
    for (int i = 0 ; i < array.length; i++) {
      assertEquals(array[i], value, "Value mismatch");
    }
    //   Test 3 : write null to all elements
    System.gc();
    System.out.println("Test 3a");
    for (int i = 0 ; i < array.length; i++) {
      array[i] = null;
    }
    System.gc();
    System.out.println("Test 3b");
    for (int i = 0 ; i < array.length; i++) {
      assertNull(array[i], "Value mismatch");
    }
    //   Test 4 : write alternate null / not null values
    System.gc();
    System.out.println("Test 4a");
    for (int i = 0 ; i < array.length; i++) {
      if (i%2 == 0) {
        array[i] = null;
      } else {
        array[i] = value;
      }
    }
    System.gc();
    System.out.println("Test 4b");
    for (int i = 0 ; i < array.length; i++) {
      if (i%2 == 0) {
        assertNull(array[i], "Value mismatch");
      } else {
        assertEquals(array[i], value, "Value mismatch");
      }
    }
  }

  static void testErrorCases(Object[] array) {
    try {
      Object o = array[-1];
      throw new RuntimeException("Missing IndexOutOfBoundsException");
    } catch(IndexOutOfBoundsException e) { }

    try {
      Object o = array[array.length];
      throw new RuntimeException("Missing IndexOutOfBoundsException");
    } catch(IndexOutOfBoundsException e) { }

    assertTrue(array.getClass().getComponentType() != String.class, "Must be for the test");
    assertTrue(array.length > 0, "Must be for the test");
    try {
      array[0] = new String("Bad");
      throw new RuntimeException("Missing ArrayStoreException");
    } catch (ArrayStoreException e) { }
  }

  static void testArrayCopy() {

    Object[] objArray = new Object[ARRAY_SIZE];
    for (int i = 0; i < ARRAY_SIZE; i++) {
      objArray[i] = SmallValue.getTestValue();
    }
    SmallValue[] nonAtomicArray = (SmallValue[])ValueClass.newNullRestrictedNonAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    SmallValue[] atomicArray = (SmallValue[])ValueClass.newNullRestrictedAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    SmallValue[] nullableArray = (SmallValue[])ValueClass.newNullableAtomicArray(SmallValue.class, ARRAY_SIZE);

    // obj -> non-atomic
    testArrayCopyInternal(objArray, nonAtomicArray);

    // obj -> atomic
    testArrayCopyInternal(objArray, atomicArray);

    // obj -> nullable
    testArrayCopyInternal(objArray, nullableArray);

    objArray[45] = null;
    // obj with null -> non-atomic   => NPE
    try {
      testArrayCopyInternal(objArray, nonAtomicArray);
      throw new RuntimeException("Missing NullPointerException");
    } catch (NullPointerException e) { }

    // obj with null -> atomic       => NPE
    try {
      testArrayCopyInternal(objArray, atomicArray);
      throw new RuntimeException("Missing NullPointerException");
    } catch (NullPointerException e) { }

    // obj with null -> nullable
    try {
      testArrayCopyInternal(objArray, nullableArray);
    } catch (NullPointerException e) {
      throw new RuntimeException("Unexpected NullPointerException");
    }

    objArray[45] = new String("bad");
    // obj with wrong type value -> non-atomic   => ASE
    try {
      testArrayCopyInternal(objArray, nonAtomicArray);
      throw new RuntimeException("Missing ArrayStoreException");
    } catch (ArrayStoreException e) { }

    // obj with wrong type value -> atomic       => ASE
    try {
      testArrayCopyInternal(objArray, atomicArray);
      throw new RuntimeException("Missing ArrayStoreException");
    } catch (ArrayStoreException e) { }

    // obj with wrong type value -> nullable     => ASE
    try {
      testArrayCopyInternal(objArray, nullableArray);
      throw new RuntimeException("Missing ArrayStoreException");
    } catch (ArrayStoreException e) { }

    // Reset all arrays
    objArray = new Object[ARRAY_SIZE];
    nonAtomicArray = (SmallValue[])ValueClass.newNullRestrictedNonAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    atomicArray = (SmallValue[])ValueClass.newNullRestrictedAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    nullableArray = (SmallValue[])ValueClass.newNullableAtomicArray(SmallValue.class, ARRAY_SIZE);

    // non-atomic -> obj
    testArrayCopyInternal(nonAtomicArray, objArray);

    // non-atomic -> non-atomic
    SmallValue[] nonAtomicArray2 = (SmallValue[])ValueClass.newNullRestrictedNonAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    testArrayCopyInternal(nonAtomicArray, nonAtomicArray2);

    // non-atomic -> non-atomic same array
    testArrayCopyInternal(nonAtomicArray, nonAtomicArray);

    // non-atomic -> atomic
    testArrayCopyInternal(nonAtomicArray, atomicArray);

    // non-atomic -> nullable
    testArrayCopyInternal(nonAtomicArray, nullableArray);

    // Reset all arrays
    objArray = new Object[ARRAY_SIZE];
    nonAtomicArray = (SmallValue[])ValueClass.newNullRestrictedNonAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    atomicArray = (SmallValue[])ValueClass.newNullRestrictedAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    nullableArray = (SmallValue[])ValueClass.newNullableAtomicArray(SmallValue.class, ARRAY_SIZE);

    for (int i = 0 ; i < ARRAY_SIZE; i++) {
      atomicArray[i] = (SmallValue)SmallValue.getTestValue();
    }

    // atomic -> obj
    testArrayCopyInternal(atomicArray, objArray);

    // atomic -> non-atomic
    testArrayCopyInternal(atomicArray, nonAtomicArray);

    // atomic -> atomic
    SmallValue[] atomicArray2 = (SmallValue[])ValueClass.newNullRestrictedAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    testArrayCopyInternal(atomicArray, atomicArray2);

    // atomic -> atomic same array
    testArrayCopyInternal(atomicArray, atomicArray);

    // atomic -> nullable
    testArrayCopyInternal(atomicArray, nullableArray);

    // Reset all arrays
    objArray = new Object[ARRAY_SIZE];
    nonAtomicArray = (SmallValue[])ValueClass.newNullRestrictedNonAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    atomicArray = (SmallValue[])ValueClass.newNullRestrictedAtomicArray(SmallValue.class, ARRAY_SIZE, new SmallValue());
    nullableArray = (SmallValue[])ValueClass.newNullableAtomicArray(SmallValue.class, ARRAY_SIZE);

    for (int i = 0 ; i < ARRAY_SIZE; i++) {
      nullableArray[i] = (SmallValue)SmallValue.getTestValue();
    }

    // nullable -> obj
    testArrayCopyInternal(nullableArray, objArray);

    // nullable -> non-atomic
    testArrayCopyInternal(nullableArray, nonAtomicArray);

    // nullable -> atomic
    testArrayCopyInternal(nullableArray, atomicArray);

    // nullable -> nullable
    SmallValue[] nullableArray2 = (SmallValue[])ValueClass.newNullableAtomicArray(SmallValue.class, ARRAY_SIZE);
    testArrayCopyInternal(nullableArray, nullableArray2);

    // nullable -> nullable same array
    testArrayCopyInternal(nullableArray, nullableArray);

    nullableArray[45] = null;

    // nullable with null -> obj
    testArrayCopyInternal(nullableArray, objArray);

    // nullable with null -> non-atomic  => NPE
    try {
      testArrayCopyInternal(nullableArray, nonAtomicArray);
      throw new RuntimeException("Missing NullPointerException");
    } catch (NullPointerException e) { }

    // nullable with null -> atomic      => NPE
    try {
      testArrayCopyInternal(nullableArray, atomicArray);
      throw new RuntimeException("Missing NullPointerException");
    } catch (NullPointerException e) { }

    // nullable with null -> nullable
    nullableArray2 = (SmallValue[])ValueClass.newNullableAtomicArray(SmallValue.class, ARRAY_SIZE);
    testArrayCopyInternal(nullableArray, nullableArray2);

    // nullable with null -> nullable same array
    testArrayCopyInternal(nullableArray, nullableArray);
  }

  static void testArrayCopyInternal(Object[] src, Object[] dst) {
    // When using this method for cases that should trigger a NPE or an ASE,
    // it is recommended to put the faulty value at index 45 in the src array
    assertTrue(src.length >= ARRAY_SIZE, "Must be for the test");
    assertTrue(dst.length >= ARRAY_SIZE, "Must be for the test");
    // Test 1 : good copy without indexes overlap
    System.arraycopy(src, 3, dst, 51, 40);
    for (int i = 0; i < 40; i++) {
      assertEquals(src[3+i], dst[51+i], "Mismatch after copying");
    }
    // Test 2 : good copy with indexes overlap
    System.arraycopy(src, 42, dst, 53, 45);
    if (src != dst) {  // Verification doesn't make sense if src and dst are the same
      for (int i = 0; i < 45; i++) {
        assertEquals(src[42+i], dst[53+i], "Mismatch after copying");
      }
    }
    // Test 3 : IOOB errors
    try {
      System.arraycopy(src, -1, dst, 3, 10);
      throw new RuntimeException("Missing IndexOutOfBoundsException");
    } catch(IndexOutOfBoundsException e) { }
    try {
      System.arraycopy(src, src.length - 5, dst, 3, 10);
      throw new RuntimeException("Missing IndexOutOfBoundsException");
    } catch(IndexOutOfBoundsException e) { }
    try {
      System.arraycopy(src, 10, dst, -1, 10);
      throw new RuntimeException("Missing IndexOutOfBoundsException");
    } catch(IndexOutOfBoundsException e) { }
    try {
      System.arraycopy(src, 10, dst, dst.length - 5, 10);
      throw new RuntimeException("Missing IndexOutOfBoundsException");
    } catch(IndexOutOfBoundsException e) { }
  }

  static void testArrayAccesses() throws NoSuchMethodException, InstantiationException,
  IllegalAccessException, InvocationTargetException {
    Class[] valueClasses = {SmallValue.class, MediumValue.class, BigValue.class};
    for (Class c: valueClasses) {
      System.out.println("Testing class " + c.getName());
      Method gtv = c.getMethod("getTestValue", null);
      Object o = gtv.invoke(null, null);
      assertNotNull(o);

      System.out.println("Regular reference array");
      Object[] array = (Object[])Array.newInstance(c, ARRAY_SIZE);
      Method ef = c.getMethod("expectingFlatNullableAtomicArray", null);
      boolean expectFlat = (Boolean) ef.invoke(null, null);
      assertTrue(ValueClass.isFlatArray(array) == (UseArrayFlattening && expectFlat));
      testNullableArray(array, o);

      System.out.println("NonAtomic NullRestricted array");
      array = ValueClass.newNullRestrictedNonAtomicArray(c, ARRAY_SIZE, c.newInstance());
      ef = c.getMethod("expectingFlatNullRestrictedArray", null);
      expectFlat = (Boolean)ef.invoke(null, null);
      assertTrue(ValueClass.isFlatArray(array) == (UseArrayFlattening && expectFlat));
      testNullFreeArray(array, o);

      System.out.println("NullRestricted Atomic array");
      array = ValueClass.newNullRestrictedAtomicArray(c, ARRAY_SIZE, c.newInstance());
      ef = c.getMethod("expectingFlatNullRestrictedAtomicArray", null);
      expectFlat = (Boolean)ef.invoke(null, null);
      assertTrue(ValueClass.isFlatArray(array) == (UseArrayFlattening && expectFlat));
      testNullFreeArray(array, o);

      System.out.println("Nullable Atomic array");
      array = ValueClass.newNullableAtomicArray(c, ARRAY_SIZE);
      ef = c.getMethod("expectingFlatNullableAtomicArray", null);
      expectFlat = (Boolean)ef.invoke(null, null);
      assertTrue(ValueClass.isFlatArray(array) == (UseArrayFlattening && expectFlat));
      testNullableArray(array, o);
    }
  }

  static value class AtomicValue {
    int i = 0;
  }

  static class FieldsHolder {
    @NullRestricted
    SmallValue sv;

    @NullRestricted
    AtomicValue av;

    AtomicValue nav;

    FieldsHolder() {
      sv = new SmallValue();
      av = new AtomicValue();
      nav = new AtomicValue();
      super();
    }
  }

  static void testSpecialArrayLayoutFromArray(Object[] array, boolean expectException) {
    int lk = UNSAFE.arrayLayout(array);
    boolean exception = false;
    try {
      Object[] newArray = UNSAFE.newSpecialArray(array.getClass().getComponentType(), 10, lk);
      int newLk = UNSAFE.arrayLayout(newArray);
      assertEquals(newLk, lk);
    } catch(IllegalArgumentException e) {
      e.printStackTrace();
      exception = true;
    }
    assertEquals(exception, expectException, "Exception not matching expectations");
  }

  static void testSpecialArrayFromFieldLayout(Class c, int layout, boolean expectException) {
    boolean exception = false;
    try {
      Object[] array = UNSAFE.newSpecialArray(c, 10, layout);
      int lk = UNSAFE.arrayLayout(array);
      assertEquals(lk, layout);
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    } catch (UnsupportedOperationException e) {
      e.printStackTrace();
      exception = true;
    }
    assertEquals(exception, expectException, "Exception not matching expectations");
  }

  static void testSpecialArrayCreation() {
    RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    List<String> jvmArgs = runtimeMXBean.getInputArguments();
    boolean arrayFlatteningEnabled = true;
    for (String s : jvmArgs) {
      if (s.compareTo("-XX:-UseArrayFlattening") == 0) arrayFlatteningEnabled = false;
    }

    // Test array creation from another array
    Object[] array0 = new SmallValue[10];
    testSpecialArrayLayoutFromArray(array0, !arrayFlatteningEnabled);
    if (arrayFlatteningEnabled) {
      Object[] array1 = ValueClass.newNullRestrictedNonAtomicArray(SmallValue.class, 10, new SmallValue());
      testSpecialArrayLayoutFromArray(array1, false);
      Object[] array2 = ValueClass.newNullRestrictedAtomicArray(SmallValue.class, 10, new SmallValue());
      testSpecialArrayLayoutFromArray(array2, false);
      Object[] array3 = ValueClass.newNullableAtomicArray(SmallValue.class, 10);
      testSpecialArrayLayoutFromArray(array3, false);
    }

    // Test array creation from a field layout
    try {
      Class c = FieldsHolder.class;
      Field f0 = c.getDeclaredField("sv");
      int layout0 = UNSAFE.fieldLayout(f0);
      testSpecialArrayFromFieldLayout(f0.getType(), layout0, !arrayFlatteningEnabled);
      Field f1 = c.getDeclaredField("av");
      int layout1 = UNSAFE.fieldLayout(f1);
      testSpecialArrayFromFieldLayout(f1.getType(), layout1, !arrayFlatteningEnabled);
      Field f2 = c.getDeclaredField("nav");
      int layout2 = UNSAFE.fieldLayout(f2);
      testSpecialArrayFromFieldLayout(f2.getType(), layout2, !arrayFlatteningEnabled);
    } catch(NoSuchFieldException e) {
      e.printStackTrace();
    }

    // Testing an invalid layout value
    boolean exception = false;
    try {
      UNSAFE.newSpecialArray(SmallValue.class, 10, 100);
    } catch(IllegalArgumentException e) {
      e.printStackTrace();
      exception = true;
    }
    assertEquals(exception, true, "Exception not received");
  }

    static value record Value4(short x, short y) {}

    public static void testReferenceArrayCreation() {
      Value4[] array0 = new Value4[1];
      assertTrue(ValueClass.isFlatArray(array0) == UseArrayFlattening);
      Value4[] array1 = (Value4[])ValueClass.newReferenceArray(Value4.class, 1);
      assertFalse(ValueClass.isFlatArray(array1));
      Value4[] array2 = new Value4[1];
      assertTrue(ValueClass.isFlatArray(array2) == UseArrayFlattening);
    }

    public static void main(String[] args) throws NoSuchMethodException, InstantiationException,
                                                IllegalAccessException, InvocationTargetException {
    testArrayAccesses();
    testArrayCopy();
    testSpecialArrayCreation();
    testReferenceArrayCreation();
  }

 }
