/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

// ArrayData
//
// A ArrayData is a base class for accessing profiling data which does
// not have a statically known size.  It consists of an array length
// and an array start.
abstract class ArrayData extends ProfileData {

  static final int arrayLenOffSet = 0;
  static final int arrayStartOffSet = 1;

  int arrayUintAt(int index) {
    int aindex = index + arrayStartOffSet;
    return uintAt(aindex);
  }
  int arrayIntAt(int index) {
    int aindex = index + arrayStartOffSet;
    return intAt(aindex);
  }
  Oop arrayOopAt(int index) {
    int aindex = index + arrayStartOffSet;
    return oopAt(aindex);
  }

  // Code generation support for subclasses.
  static int arrayElementOffset(int index) {
    return cellOffset(arrayStartOffSet + index);
  }

  ArrayData(DataLayout layout) {
    super(layout);
  }

  static int staticCellCount() {
    return -1;
  }

  int arrayLen() {
    return intAt(arrayLenOffSet);
  }

  public int cellCount() {
    return arrayLen() + 1;
  }

  // Code generation support
  static int arrayLenOffset() {
    return cellOffset(arrayLenOffSet);
  }
  static int arrayStartOffset() {
    return cellOffset(arrayStartOffSet);
  }

}
