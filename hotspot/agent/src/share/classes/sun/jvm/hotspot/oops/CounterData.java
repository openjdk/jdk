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

// CounterData
//
// A CounterData corresponds to a simple counter.
public class CounterData extends BitData {

  static final int countOff = 0;
  static final int counterCellCount = 1;

  public CounterData(DataLayout layout) {
    super(layout);
  }

  static int staticCellCount() {
    return counterCellCount;
  }

  public int cellCount() {
    return staticCellCount();
  }

  // Direct accessor
  int count() {
    return uintAt(countOff);
  }

  // Code generation support
  static int countOffset() {
    return cellOffset(countOff);
  }
  static int counterDataSize() {
    return cellOffset(counterCellCount);
  }

  public void printDataOn(PrintStream st) {
    printShared(st, "CounterData");
    st.println("count(" + count() + ")");
  }
}
