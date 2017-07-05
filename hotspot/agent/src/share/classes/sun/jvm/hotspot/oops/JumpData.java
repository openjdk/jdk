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

// JumpData
//
// A JumpData is used to access profiling information for a direct
// branch.  It is a counter, used for counting the number of branches,
// plus a data displacement, used for realigning the data pointer to
// the corresponding target bci.
public class JumpData extends ProfileData {
  static final int   takenOffSet = 0;
  static final int     displacementOffSet = 1;
  static final int     jumpCellCount = 2;

  public JumpData(DataLayout layout) {
    super(layout);
    //assert(layout.tag() == DataLayout.jumpDataTag ||
    //       layout.tag() == DataLayout.branchDataTag, "wrong type");
  }

  static int staticCellCount() {
    return jumpCellCount;
  }

  public int cellCount() {
    return staticCellCount();
  }

  // Direct accessor
  int taken() {
    return uintAt(takenOffSet);
  }

  int displacement() {
    return intAt(displacementOffSet);
  }

  // Code generation support
  static int takenOffset() {
    return cellOffset(takenOffSet);
  }

  static int displacementOffset() {
    return cellOffset(displacementOffSet);
  }

  public void printDataOn(PrintStream st) {
    printShared(st, "JumpData");
    st.println("taken(" + taken() + ") displacement(" + displacement() + ")");
  }
}
