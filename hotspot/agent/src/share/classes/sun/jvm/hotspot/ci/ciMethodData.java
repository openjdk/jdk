/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.ci;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.types.*;

public class ciMethodData extends ciMetadata {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("ciMethodData");
    origField = type.getAddressField("_orig");
    currentMileageField = new CIntField(type.getCIntegerField("_current_mileage"), 0);
    argReturnedField = new CIntField(type.getCIntegerField("_arg_returned"), 0);
    argStackField = new CIntField(type.getCIntegerField("_arg_stack"), 0);
    argLocalField = new CIntField(type.getCIntegerField("_arg_local"), 0);
    eflagsField = new CIntField(type.getCIntegerField("_eflags"), 0);
    hintDiField = new CIntField(type.getCIntegerField("_hint_di"), 0);
    currentMileageField = new CIntField(type.getCIntegerField("_current_mileage"), 0);
    dataField = type.getAddressField("_data");
    extraDataSizeField = new CIntField(type.getCIntegerField("_extra_data_size"), 0);
    dataSizeField = new CIntField(type.getCIntegerField("_data_size"), 0);
    stateField = new CIntField(type.getCIntegerField("_state"), 0);
    sizeofMethodDataOopDesc = (int)db.lookupType("MethodData").getSize();;
  }

  private static AddressField origField;
  private static CIntField currentMileageField;
  private static CIntField argReturnedField;
  private static CIntField argStackField;
  private static CIntField argLocalField;
  private static CIntField eflagsField;
  private static CIntField hintDiField;
  private static AddressField dataField;
  private static CIntField extraDataSizeField;
  private static CIntField dataSizeField;
  private static CIntField stateField;
  private static int sizeofMethodDataOopDesc;

  public ciMethodData(Address addr) {
    super(addr);
  }

  private byte[] fetchDataAt(Address base, long size) {
    byte[] result = new byte[(int)size];
    for (int i = 0; i < size; i++) {
      result[i] = base.getJByteAt(i);
    }
    return result;
  }

  public byte[] orig() {
    // fetch the orig MethodData data between header and dataSize
    Address base = getAddress().addOffsetTo(origField.getOffset());
    byte[] result = new byte[MethodData.sizeofMethodDataOopDesc];
    for (int i = 0; i < MethodData.sizeofMethodDataOopDesc; i++) {
      result[i] = base.getJByteAt(i);
    }
    return result;
  }

  public  long[] data() {
    // Read the data as an array of intptr_t elements
    Address base = dataField.getValue(getAddress());
    int elements = dataSize() / MethodData.cellSize;
    long[] result = new long[elements];
    for (int i = 0; i < elements; i++) {
      Address value = base.getAddressAt(i * MethodData.cellSize);
      if (value != null) {
        result[i] = value.minus(null);
      }
    }
    return result;
  }

  int dataSize() {
    return (int)dataSizeField.getValue(getAddress());
  }

  int state() {
    return (int)stateField.getValue(getAddress());
  }

  int currentMileage() {
    return (int)currentMileageField.getValue(getAddress());
  }

  boolean outOfBounds(int dataIndex) {
    return dataIndex >= dataSize();
  }

  ProfileData dataAt(int dataIndex) {
    if (outOfBounds(dataIndex)) {
      return null;
    }
    DataLayout dataLayout = new DataLayout(dataField.getValue(getAddress()), dataIndex);

    switch (dataLayout.tag()) {
    case DataLayout.noTag:
    default:
      throw new InternalError();
    case DataLayout.bitDataTag:
      return new BitData(dataLayout);
    case DataLayout.counterDataTag:
      return new CounterData(dataLayout);
    case DataLayout.jumpDataTag:
      return new JumpData(dataLayout);
    case DataLayout.receiverTypeDataTag:
      return new ciReceiverTypeData(dataLayout);
    case DataLayout.virtualCallDataTag:
      return new ciVirtualCallData(dataLayout);
    case DataLayout.retDataTag:
      return new RetData(dataLayout);
    case DataLayout.branchDataTag:
      return new BranchData(dataLayout);
    case DataLayout.multiBranchDataTag:
      return new MultiBranchData(dataLayout);
    }
  }

  int dpToDi(int dp) {
    return dp;
  }

  int firstDi() { return 0; }
  ProfileData firstData() { return dataAt(firstDi()); }
  ProfileData nextData(ProfileData current) {
    int currentIndex = dpToDi(current.dp());
    int nextIndex = currentIndex + current.sizeInBytes();
    return dataAt(nextIndex);
  }
  boolean isValid(ProfileData current) { return current != null; }

  public void printDataOn(PrintStream st) {
    ProfileData data = firstData();
    for ( ; isValid(data); data = nextData(data)) {
      st.print(dpToDi(data.dp()));
      st.print(" ");
      // st->fillTo(6);
      data.printDataOn(st);
    }
  }

  public void dumpReplayData(PrintStream out) {
    MethodData mdo = (MethodData)getMetadata();
    Method method = mdo.getMethod();
    Klass holder = method.getMethodHolder();
    out.print("ciMethodData " +
              holder.getName().asString() + " " +
              OopUtilities.escapeString(method.getName().asString()) + " " +
              method.getSignature().asString() + " " +
              state() + " " + currentMileage());
    byte[] orig = orig();
    out.print(" orig " + orig.length);
    for (int i = 0; i < orig.length; i++) {
      out.print(" " + (orig[i] & 0xff));
    }

    long[] data = data();
    out.print(" data " +  data.length);
    for (int i = 0; i < data.length; i++) {
      out.print(" 0x" + Long.toHexString(data[i]));
    }
    int count = 0;
    for (int round = 0; round < 2; round++) {
      if (round == 1) out.print(" oops " + count);
      ProfileData pdata = firstData();
      for ( ; isValid(pdata); pdata = nextData(pdata)) {
        if (pdata instanceof ciReceiverTypeData) {
          ciReceiverTypeData vdata = (ciReceiverTypeData)pdata;
          for (int i = 0; i < vdata.rowLimit(); i++) {
            ciKlass k = vdata.receiverAt(i);
            if (k != null) {
              if (round == 0) count++;
              else out.print(" " + ((vdata.dp() + vdata.cellOffset(vdata.receiverCellIndex(i))) / MethodData.cellSize) + " " + k.name());
            }
          }
        } else if (pdata instanceof ciVirtualCallData) {
          ciVirtualCallData vdata = (ciVirtualCallData)pdata;
          for (int i = 0; i < vdata.rowLimit(); i++) {
            ciKlass k = vdata.receiverAt(i);
            if (k != null) {
              if (round == 0) count++;
              else out.print(" " + ((vdata.dp() + vdata.cellOffset(vdata.receiverCellIndex(i))) / MethodData.cellSize + " " + k.name()));
            }
          }
        }
      }
    }
    out.println();
  }
}
