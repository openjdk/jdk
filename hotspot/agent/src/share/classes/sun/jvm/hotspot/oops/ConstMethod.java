/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.code.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.interpreter.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class ConstMethod extends Oop {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  // anon-enum constants for _flags.
  private static int HAS_LINENUMBER_TABLE;
  private static int HAS_CHECKED_EXCEPTIONS;
  private static int HAS_LOCALVARIABLE_TABLE;

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type                  = db.lookupType("constMethodOopDesc");
    // Backpointer to non-const methodOop
    method                     = new OopField(type.getOopField("_method"), 0);
    // The exception handler table. 4-tuples of ints [start_pc, end_pc,
    // handler_pc, catch_type index] For methods with no exceptions the
    // table is pointing to Universe::the_empty_int_array
    exceptionTable             = new OopField(type.getOopField("_exception_table"), 0);
    constMethodSize            = new CIntField(type.getCIntegerField("_constMethod_size"), 0);
    flags                      = new ByteField(type.getJByteField("_flags"), 0);

    // enum constants for flags
    HAS_LINENUMBER_TABLE      = db.lookupIntConstant("constMethodOopDesc::_has_linenumber_table").intValue();
    HAS_CHECKED_EXCEPTIONS     = db.lookupIntConstant("constMethodOopDesc::_has_checked_exceptions").intValue();
    HAS_LOCALVARIABLE_TABLE   = db.lookupIntConstant("constMethodOopDesc::_has_localvariable_table").intValue();

    // Size of Java bytecodes allocated immediately after constMethodOop.
    codeSize                   = new CIntField(type.getCIntegerField("_code_size"), 0);
    nameIndex                  = new CIntField(type.getCIntegerField("_name_index"), 0);
    signatureIndex             = new CIntField(type.getCIntegerField("_signature_index"), 0);
    genericSignatureIndex      = new CIntField(type.getCIntegerField("_generic_signature_index"),0);

    // start of byte code
    bytecodeOffset = type.getSize();

    type                       = db.lookupType("CheckedExceptionElement");
    checkedExceptionElementSize = type.getSize();

    type                       = db.lookupType("LocalVariableTableElement");
    localVariableTableElementSize = type.getSize();
  }

  ConstMethod(OopHandle handle, ObjectHeap heap) {
    super(handle, heap);
  }

  // Fields
  private static OopField  method;
  private static OopField  exceptionTable;
  private static CIntField constMethodSize;
  private static ByteField flags;
  private static CIntField codeSize;
  private static CIntField nameIndex;
  private static CIntField signatureIndex;
  private static CIntField genericSignatureIndex;

  // start of bytecode
  private static long bytecodeOffset;

  private static long checkedExceptionElementSize;
  private static long localVariableTableElementSize;

  // Accessors for declared fields
  public Method getMethod() {
    return (Method) method.getValue(this);
  }

  public TypeArray getExceptionTable() {
    return (TypeArray) exceptionTable.getValue(this);
  }

  public long getConstMethodSize() {
    return constMethodSize.getValue(this);
  }

  public byte getFlags() {
    return flags.getValue(this);
  }

  public long getCodeSize() {
    return codeSize.getValue(this);
  }

  public long getNameIndex() {
    return nameIndex.getValue(this);
  }

  public long getSignatureIndex() {
    return signatureIndex.getValue(this);
  }

  public long getGenericSignatureIndex() {
    return genericSignatureIndex.getValue(this);
  }

  public Symbol getName() {
    return getMethod().getName();
  }

  public Symbol getSignature() {
    return getMethod().getSignature();
  }

  public Symbol getGenericSignature() {
    return getMethod().getGenericSignature();
  }

  // bytecode accessors

  /** Get a bytecode or breakpoint at the given bci */
  public int getBytecodeOrBPAt(int bci) {
    return getHandle().getJByteAt(bytecodeOffset + bci) & 0xFF;
  }

  public byte getBytecodeByteArg(int bci) {
    return (byte) getBytecodeOrBPAt(bci);
  }

  /** Fetches a 16-bit big-endian ("Java ordered") value from the
      bytecode stream */
  public short getBytecodeShortArg(int bci) {
    int hi = getBytecodeOrBPAt(bci);
    int lo = getBytecodeOrBPAt(bci + 1);
    return (short) ((hi << 8) | lo);
  }

  /** Fetches a 32-bit big-endian ("Java ordered") value from the
      bytecode stream */
  public int getBytecodeIntArg(int bci) {
    int b4 = getBytecodeOrBPAt(bci);
    int b3 = getBytecodeOrBPAt(bci + 1);
    int b2 = getBytecodeOrBPAt(bci + 2);
    int b1 = getBytecodeOrBPAt(bci + 3);

    return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
  }

  public byte[] getByteCode() {
     byte[] bc = new byte[ (int) getCodeSize() ];
     for( int i=0; i < bc.length; i++ )
     {
        long offs = bytecodeOffset + i;
        bc[i] = getHandle().getJByteAt( offs );
     }
     return bc;
  }

  public long getObjectSize() {
    return getConstMethodSize() * getHeap().getOopSize();
  }

  public void printValueOn(PrintStream tty) {
    tty.print("ConstMethod " + getName().asString() + getSignature().asString() + "@" + getHandle());
  }

  public void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
    if (doVMFields) {
      visitor.doOop(method, true);
      visitor.doOop(exceptionTable, true);
      visitor.doCInt(constMethodSize, true);
      visitor.doByte(flags, true);
      visitor.doCInt(codeSize, true);
      visitor.doCInt(nameIndex, true);
      visitor.doCInt(signatureIndex, true);
      visitor.doCInt(genericSignatureIndex, true);
      visitor.doCInt(codeSize, true);
    }
  }

  // Accessors

  public boolean hasLineNumberTable() {
    return (getFlags() & HAS_LINENUMBER_TABLE) != 0;
  }

  public int getLineNumberFromBCI(int bci) {
    if (!VM.getVM().isCore()) {
      if (bci == DebugInformationRecorder.SYNCHRONIZATION_ENTRY_BCI) bci = 0;
    }

    if (isNative()) {
      return -1;
    }

    if (Assert.ASSERTS_ENABLED) {
      Assert.that(bci == 0 || 0 <= bci && bci < getCodeSize(), "illegal bci");
    }
    int bestBCI  =  0;
    int bestLine = -1;
    if (hasLineNumberTable()) {
      // The line numbers are a short array of 2-tuples [start_pc, line_number].
      // Not necessarily sorted and not necessarily one-to-one.
      CompressedLineNumberReadStream stream =
        new CompressedLineNumberReadStream(getHandle(), (int) offsetOfCompressedLineNumberTable());
      while (stream.readPair()) {
        if (stream.bci() == bci) {
          // perfect match
          return stream.line();
        } else {
          // update best_bci/line
          if (stream.bci() < bci && stream.bci() >= bestBCI) {
            bestBCI  = stream.bci();
            bestLine = stream.line();
          }
        }
      }
    }
    return bestLine;
  }

  public LineNumberTableElement[] getLineNumberTable() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(hasLineNumberTable(),
                  "should only be called if table is present");
    }
    int len = getLineNumberTableLength();
    CompressedLineNumberReadStream stream =
      new CompressedLineNumberReadStream(getHandle(), (int) offsetOfCompressedLineNumberTable());
    LineNumberTableElement[] ret = new LineNumberTableElement[len];

    for (int idx = 0; idx < len; idx++) {
      stream.readPair();
      ret[idx] = new LineNumberTableElement(stream.bci(), stream.line());
    }
    return ret;
  }

  public boolean hasLocalVariableTable() {
    return (getFlags() & HAS_LOCALVARIABLE_TABLE) != 0;
  }

  public Symbol getLocalVariableName(int bci, int slot) {
    return getMethod().getLocalVariableName(bci, slot);
  }

  /** Should only be called if table is present */
  public LocalVariableTableElement[] getLocalVariableTable() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(hasLocalVariableTable(), "should only be called if table is present");
    }
    LocalVariableTableElement[] ret = new LocalVariableTableElement[getLocalVariableTableLength()];
    long offset = offsetOfLocalVariableTable();
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new LocalVariableTableElement(getHandle(), offset);
      offset += localVariableTableElementSize;
    }
    return ret;
  }

  public boolean hasCheckedExceptions() {
    return (getFlags() & HAS_CHECKED_EXCEPTIONS) != 0;
  }

  public CheckedExceptionElement[] getCheckedExceptions() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(hasCheckedExceptions(), "should only be called if table is present");
    }
    CheckedExceptionElement[] ret = new CheckedExceptionElement[getCheckedExceptionsLength()];
    long offset = offsetOfCheckedExceptions();
    for (int i = 0; i < ret.length; i++) {
      ret[i] = new CheckedExceptionElement(getHandle(), offset);
      offset += checkedExceptionElementSize;
    }
    return ret;
  }


  //---------------------------------------------------------------------------
  // Internals only below this point
  //

  private boolean isNative() {
    return getMethod().isNative();
  }

  // Offset of end of code
  private long offsetOfCodeEnd() {
    return bytecodeOffset + getCodeSize();
  }

  // Offset of start of compressed line number table (see methodOop.hpp)
  private long offsetOfCompressedLineNumberTable() {
    return offsetOfCodeEnd() + (isNative() ? 2 * VM.getVM().getAddressSize() : 0);
  }

  // Offset of last short in methodOop
  private long offsetOfLastU2Element() {
    return getObjectSize() - 2;
  }

  private long offsetOfCheckedExceptionsLength() {
    return offsetOfLastU2Element();
  }

  private int getCheckedExceptionsLength() {
    if (hasCheckedExceptions()) {
      return (int) getHandle().getCIntegerAt(offsetOfCheckedExceptionsLength(), 2, true);
    } else {
      return 0;
    }
  }

  // Offset of start of checked exceptions
  private long offsetOfCheckedExceptions() {
    long offset = offsetOfCheckedExceptionsLength();
    long length = getCheckedExceptionsLength();
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(length > 0, "should only be called if table is present");
    }
    offset -= length * checkedExceptionElementSize;
    return offset;
  }

  private int getLineNumberTableLength() {
    int len = 0;
    if (hasLineNumberTable()) {
      CompressedLineNumberReadStream stream =
        new CompressedLineNumberReadStream(getHandle(), (int) offsetOfCompressedLineNumberTable());
      while (stream.readPair()) {
        len += 1;
      }
    }
    return len;
  }

  private int getLocalVariableTableLength() {
    if (hasLocalVariableTable()) {
      return (int) getHandle().getCIntegerAt(offsetOfLocalVariableTableLength(), 2, true);
    } else {
      return 0;
    }
  }

  // Offset of local variable table length
  private long offsetOfLocalVariableTableLength() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(hasLocalVariableTable(), "should only be called if table is present");
    }
    if (hasCheckedExceptions()) {
      return offsetOfCheckedExceptions() - 2;
    } else {
      return offsetOfLastU2Element();
    }
  }

  private long offsetOfLocalVariableTable() {
    long offset = offsetOfLocalVariableTableLength();
    long length = getLocalVariableTableLength();
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(length > 0, "should only be called if table is present");
    }
    offset -= length * localVariableTableElementSize;
    return offset;
  }

}
