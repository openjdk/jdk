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

// BranchData
//
// A BranchData is used to access profiling data for a two-way branch.
// It consists of taken and notTaken counts as well as a data displacement
// for the taken case.
public class BranchData extends JumpData {

  static final int notTakenOffSet = jumpCellCount;
  static final int branchCellCount = notTakenOffSet + 1;

  public BranchData(DataLayout layout) {
    super(layout);
    //assert(layout.tag() == DataLayout.branchDataTag, "wrong type");
  }

  static int staticCellCount() {
    return branchCellCount;
  }

  public int cellCount() {
    return staticCellCount();
  }

  // Direct accessor
  int notTaken() {
    return uintAt(notTakenOffSet);
  }

  // Code generation support
  static int notTakenOffset() {
    return cellOffset(notTakenOffSet);
  }
  static int branchDataSize() {
    return cellOffset(branchCellCount);
  }

  public void printDataOn(PrintStream st) {
    printShared(st, "BranchData");
    st.println("taken(" + taken() + ") displacement(" + displacement() + ")");
    tab(st);
    st.println("not taken(" + notTaken() + ")");
  }
}
