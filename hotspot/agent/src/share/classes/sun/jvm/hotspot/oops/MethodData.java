/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.oops;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

// A MethodData provides interpreter profiling information

public class MethodData extends Metadata {
  static int TypeProfileWidth = 2;
  static int BciProfileWidth = 2;
  static int CompileThreshold;

  static int Reason_many;                 // indicates presence of several reasons
  static int Reason_none;                 // indicates absence of a relevant deopt.
  static int Reason_LIMIT;
  static int Reason_RECORDED_LIMIT;       // some are not recorded per bc

  private static String[] trapReasonName;

  static String trapReasonName(int reason) {
    if (reason == Reason_many)  return "many";
    if (reason < Reason_LIMIT)
      return trapReasonName[reason];
    return "reason" + reason;
  }


  static int trapStateReason(int trapState) {
    // This assert provides the link between the width of DataLayout.trapBits
    // and the encoding of "recorded" reasons.  It ensures there are enough
    // bits to store all needed reasons in the per-BCI MDO profile.
    // assert(dsReasonMask >= reasonRecordedLimit, "enough bits");
    int recompileBit = (trapState & dsRecompileBit);
    trapState -= recompileBit;
    if (trapState == dsReasonMask) {
      return Reason_many;
    } else {
      // assert((int)reasonNone == 0, "state=0 => Reason_none");
      return trapState;
    }
  }


  static final int dsReasonMask   = DataLayout.trapMask >> 1;
  static final int dsRecompileBit = DataLayout.trapMask - dsReasonMask;

  static boolean trapStateIsRecompiled(int trapState) {
    return (trapState & dsRecompileBit) != 0;
  }

  static boolean reasonIsRecordedPerBytecode(int reason) {
    return reason > Reason_none && reason < Reason_RECORDED_LIMIT;
  }
  static int trapStateAddReason(int trapState, int reason) {
    // assert(reasonIsRecordedPerBytecode((DeoptReason)reason) || reason == reasonMany, "valid reason");
    int recompileBit = (trapState & dsRecompileBit);
    trapState -= recompileBit;
    if (trapState == dsReasonMask) {
      return trapState + recompileBit;     // already at state lattice bottom
    } else if (trapState == reason) {
      return trapState + recompileBit;     // the condition is already true
    } else if (trapState == 0) {
      return reason + recompileBit;          // no condition has yet been true
    } else {
      return dsReasonMask + recompileBit;  // fall to state lattice bottom
    }
  }
  static int trapStateSetRecompiled(int trapState, boolean z) {
    if (z)  return trapState |  dsRecompileBit;
    else    return trapState & ~dsRecompileBit;
  }

  static String formatTrapState(int trapState) {
    int reason      = trapStateReason(trapState);
    boolean     recompFlag = trapStateIsRecompiled(trapState);
    // Re-encode the state from its decoded components.
    int decodedState = 0;
    if (reasonIsRecordedPerBytecode(reason) || reason == Reason_many)
      decodedState = trapStateAddReason(decodedState, reason);
    if (recompFlag)
      decodedState = trapStateSetRecompiled(decodedState, recompFlag);
    // If the state re-encodes properly, format it symbolically.
    // Because this routine is used for debugging and diagnostics,
    // be robust even if the state is a strange value.
    if (decodedState != trapState) {
      // Random buggy state that doesn't decode??
      return "#" + trapState;
    } else {
      return trapReasonName(reason) + (recompFlag ? " recompiled" : "");
    }
  }



  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type      = db.lookupType("MethodData");
    baseOffset     = type.getSize();

    size           = new CIntField(type.getCIntegerField("_size"), 0);
    method         = new MetadataField(type.getAddressField("_method"), 0);

    VM.Flag[] flags = VM.getVM().getCommandLineFlags();
    for (int f = 0; f < flags.length; f++) {
      VM.Flag flag = flags[f];
      if (flag.getName().equals("TypeProfileWidth")) {
        TypeProfileWidth = (int)flag.getIntx();
      } else if (flag.getName().equals("BciProfileWidth")) {
        BciProfileWidth = (int)flag.getIntx();
      } else if (flag.getName().equals("CompileThreshold")) {
        CompileThreshold = (int)flag.getIntx();
      }
    }

    cellSize = (int)VM.getVM().getAddressSize();

    dataSize     = new CIntField(type.getCIntegerField("_data_size"), 0);
    data         = type.getAddressField("_data[0]");

    sizeofMethodDataOopDesc = (int)type.getSize();;

    Reason_many            = db.lookupIntConstant("Deoptimization::Reason_many").intValue();
    Reason_none            = db.lookupIntConstant("Deoptimization::Reason_none").intValue();
    Reason_LIMIT           = db.lookupIntConstant("Deoptimization::Reason_LIMIT").intValue();
    Reason_RECORDED_LIMIT  = db.lookupIntConstant("Deoptimization::Reason_RECORDED_LIMIT").intValue();

    trapReasonName = new String[Reason_LIMIT];

    // Find Deopt reasons
    Iterator i = db.getIntConstants();
    String prefix = "Deoptimization::Reason_";
    while (i.hasNext()) {
      String name = (String)i.next();
      if (name.startsWith(prefix)) {
        // Strip prefix
        if (!name.endsWith("Reason_many") &&
            !name.endsWith("Reason_LIMIT") &&
            !name.endsWith("Reason_RECORDED_LIMIT")) {
          String trimmed = name.substring(prefix.length());
          int value = db.lookupIntConstant(name).intValue();
          if (trapReasonName[value] != null) {
            throw new InternalError("duplicate reasons: " + trapReasonName[value] + " " + trimmed);
          }
          trapReasonName[value] = trimmed;
        }
      }
    }
    for (int index = 0; index < trapReasonName.length; index++) {
      if (trapReasonName[index] == null) {
        throw new InternalError("missing reason for " + index);
      }
    }
  }

  public MethodData(Address addr) {
    super(addr);
  }

  public boolean isMethodData()        { return true; }

  private static long baseOffset;
  private static CIntField size;
  private static MetadataField  method;
  private static CIntField dataSize;
  private static AddressField data;

  public static int sizeofMethodDataOopDesc;
  public static int cellSize;

  public Method getMethod() {
    return (Method) method.getValue(this);
  }

  public void printValueOn(PrintStream tty) {
    Method m = getMethod();
    tty.print("MethodData for " + m.getName().asString() + m.getSignature().asString());
  }

  public void iterateFields(MetadataVisitor visitor) {
    super.iterateFields(visitor);
    visitor.doMetadata(method, true);
      visitor.doCInt(size, true);
    }

  int dataSize() {
    if (dataSize == null) {
      return 0;
    } else {
      return (int)dataSize.getValue(getAddress());
    }
  }

  boolean outOfBounds(int dataIndex) {
    return dataIndex >= dataSize();
  }

  ProfileData dataAt(int dataIndex) {
    if (outOfBounds(dataIndex)) {
      return null;
    }
    DataLayout dataLayout = new DataLayout(this, dataIndex + (int)data.getOffset());

    switch (dataLayout.tag()) {
    case DataLayout.noTag:
    default:
      throw new InternalError(dataIndex + " " + dataSize() + " " + dataLayout.tag());
    case DataLayout.bitDataTag:
      return new BitData(dataLayout);
    case DataLayout.counterDataTag:
      return new CounterData(dataLayout);
    case DataLayout.jumpDataTag:
      return new JumpData(dataLayout);
    case DataLayout.receiverTypeDataTag:
      return new ReceiverTypeData(dataLayout);
    case DataLayout.virtualCallDataTag:
      return new VirtualCallData(dataLayout);
    case DataLayout.retDataTag:
      return new RetData(dataLayout);
    case DataLayout.branchDataTag:
      return new BranchData(dataLayout);
    case DataLayout.multiBranchDataTag:
      return new MultiBranchData(dataLayout);
    }
  }

  int dpToDi(int dp) {
    // this in an offset from the base of the MDO, so convert to offset into _data
    return dp - (int)data.getOffset();
  }

  int firstDi() { return 0; }
  public ProfileData firstData() { return dataAt(firstDi()); }
  public ProfileData nextData(ProfileData current) {
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

  private byte[] fetchDataAt(Address base, long offset, long size) {
    byte[] result = new byte[(int)size];
    for (int i = 0; i < size; i++) {
      result[i] = base.getJByteAt(offset + i);
    }
    return result;
  }

  public byte[] orig() {
    // fetch the orig MethodData data between header and dataSize
    return fetchDataAt(getAddress(), 0, sizeofMethodDataOopDesc);
  }

  public long[] data() {
    // Read the data as an array of intptr_t elements
    Address base = getAddress();
    long offset = data.getOffset();
    int elements = dataSize() / cellSize;
    long[] result = new long[elements];
    for (int i = 0; i < elements; i++) {
      Address value = base.getAddressAt(offset + i * MethodData.cellSize);
      if (value != null) {
        result[i] = value.minus(null);
      }
    }
    return result;
  }

  // Get a measure of how much mileage the method has on it.
  int mileageOf(Method method) {
    long mileage = 0;
    int iic = method.interpreterInvocationCount();
    if (mileage < iic)  mileage = iic;

    long ic = method.getInvocationCount();
    long bc = method.getBackedgeCount();

    long icval = ic >> 3;
    if ((ic & 4) != 0) icval += CompileThreshold;
    if (mileage < icval)  mileage = icval;
    long bcval = bc >> 3;
    if ((bc & 4) != 0) bcval += CompileThreshold;
    if (mileage < bcval)  mileage = bcval;
    return (int)mileage;
  }

  public int currentMileage() {
    return 20000;
  }

  public void dumpReplayData(PrintStream out) {
    Method method = getMethod();
    Klass holder = method.getMethodHolder();
    out.print("ciMethodData " +
              holder.getName().asString() + " " +
              OopUtilities.escapeString(method.getName().asString()) + " " +
              method.getSignature().asString() + " " +
              "2" + " " +
              currentMileage());
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
        if (pdata instanceof ReceiverTypeData) {
          ReceiverTypeData vdata = (ReceiverTypeData)pdata;
          for (int i = 0; i < vdata.rowLimit(); i++) {
            Klass k = vdata.receiver(i);
            if (k != null) {
              if (round == 0) count++;
              else out.print(" " +
                             (dpToDi(vdata.dp() +
                              vdata.cellOffset(vdata.receiverCellIndex(i))) / cellSize) + " " +
                             k.getName().asString());
            }
          }
        } else if (pdata instanceof VirtualCallData) {
          VirtualCallData vdata = (VirtualCallData)pdata;
          for (int i = 0; i < vdata.rowLimit(); i++) {
            Klass k = vdata.receiver(i);
            if (k != null) {
              if (round == 0) count++;
              else out.print(" " +
                             (dpToDi(vdata.dp() +
                              vdata.cellOffset(vdata.receiverCellIndex(i))) / cellSize) + " " +
                             k.getName().asString());
            }
          }
        }
      }
    }
    out.println();
  }
}
