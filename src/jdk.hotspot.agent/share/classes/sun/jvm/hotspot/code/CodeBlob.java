/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package sun.jvm.hotspot.code;

import sun.jvm.hotspot.compiler.ImmutableOopMap;
import sun.jvm.hotspot.compiler.ImmutableOopMapSet;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.oops.CIntField;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.JShortField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.utilities.Assert;
import sun.jvm.hotspot.utilities.CStringUtilities;

import java.io.PrintStream;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class CodeBlob extends VMObject {
  private static AddressField nameField;
  private static CIntegerField sizeField;
  private static CIntegerField kindField;
  private static CIntegerField relocationSizeField;
  private static CIntField     headerSizeField;
  private static CIntegerField contentOffsetField;
  private static CIntegerField codeOffsetField;
  private static CIntField     frameCompleteOffsetField;
  private static CIntegerField dataOffsetField;
  private static CIntegerField frameSizeField;
  private static AddressField  oopMapsField;
  private static AddressField  mutableDataField;
  private static CIntegerField mutableDataSizeField;
  private static CIntegerField callerMustGCArgumentsField;

  // Kinds of CodeBlobs that we need to know about.
  private static int NMethodKind;
  private static int RuntimeStubKind;
  private static int UpcallKind;

  private static Class[] wrapperClasses;

  public CodeBlob(Address addr) {
    super(addr);
  }

  private static void initialize(TypeDataBase db) {
    Type type = db.lookupType("CodeBlob");

    nameField                = type.getAddressField("_name");
    sizeField                = type.getCIntegerField("_size");
    kindField                = type.getCIntegerField("_kind");
    relocationSizeField      = type.getCIntegerField("_relocation_size");
    headerSizeField          = new CIntField(type.getCIntegerField("_header_size"), 0);
    contentOffsetField       = type.getCIntegerField("_content_offset");
    codeOffsetField          = type.getCIntegerField("_code_offset");
    frameCompleteOffsetField = new CIntField(type.getCIntegerField("_frame_complete_offset"), 0);
    dataOffsetField          = type.getCIntegerField("_data_offset");
    frameSizeField           = type.getCIntegerField("_frame_size");
    oopMapsField             = type.getAddressField("_oop_maps");
    callerMustGCArgumentsField = type.getCIntegerField("_caller_must_gc_arguments");
    mutableDataField         = type.getAddressField("_mutable_data");
    mutableDataSizeField     = type.getCIntegerField("_mutable_data_size");

    NMethodKind        = db.lookupIntConstant("CodeBlobKind::Nmethod").intValue();
    RuntimeStubKind    = db.lookupIntConstant("CodeBlobKind::RuntimeStub").intValue();
    UpcallKind         = db.lookupIntConstant("CodeBlobKind::Upcall").intValue();
  }

  static {
    VM.registerVMInitializedObserver(new Observer() {
      public void update(Observable o, Object data) {
        initialize(VM.getVM().getTypeDataBase());
      }
    });
  }

  public static Class<?> getClassFor(Address addr) {
      CodeBlob cb = new CodeBlob(addr);
      int kind = cb.getKind();
      if (kind == NMethodKind) {
          return NMethod.class;
      } else if (kind == UpcallKind) {
          return UpcallStub.class;
      } else {
          // All other CodeBlob kinds have no special functionality in SA and can be
          // represented by the generic CodeBlob class.
          return CodeBlob.class;
      }
  }

  public Address headerBegin()    { return getAddress(); }

  public Address headerEnd()      { return getAddress().addOffsetTo(getHeaderSize()); }

  public Address contentBegin()   { return headerBegin().addOffsetTo(getContentOffset()); }

  public Address contentEnd()     { return headerBegin().addOffsetTo(getDataOffset()); }

  public Address codeBegin()      { return headerBegin().addOffsetTo(getCodeOffset()); }

  public Address codeEnd()        { return headerBegin().addOffsetTo(getDataOffset()); }

  public Address dataBegin()      { return headerBegin().addOffsetTo(getDataOffset()); }

  public Address dataEnd()        { return headerBegin().addOffsetTo(getSize()); }

  // Offsets
  public int getContentOffset()   { return (int) contentOffsetField.getValue(addr); }

  public int getCodeOffset()      { return (int) codeOffsetField.getValue(addr); }

  public long getFrameCompleteOffset() { return frameCompleteOffsetField.getValue(addr); }

  public int getDataOffset()      { return (int) dataOffsetField.getValue(addr); }

  // Sizes
  public int getSize()            { return (int) sizeField.getValue(addr); }

  public int getHeaderSize()      { return (int) headerSizeField.getValue(addr); }


  // Mutable data
  public int getMutableDataSize()   { return (int) mutableDataSizeField.getValue(addr); }

  public Address mutableDataBegin() { return mutableDataField.getValue(addr); }

  public Address mutableDataEnd()   { return mutableDataBegin().addOffsetTo(getMutableDataSize());  }


  public long getFrameSizeWords() {
    return (int) frameSizeField.getValue(addr);
  }

  public String getName() {
    return CStringUtilities.getString(nameField.getValue(addr));
  }

  public int getKind() {
    return (int) kindField.getValue(addr);
  }

  /** OopMap for frame; can return null if none available */
  public ImmutableOopMapSet getOopMaps() {
    Address value = oopMapsField.getValue(addr);
    if (value == null) {
      return null;
    }
    return new ImmutableOopMapSet(value);
  }


  // Typing
  public boolean isNMethod()            { return getKind() == NMethodKind; }

  public boolean isRuntimeStub()        { return getKind() == RuntimeStubKind; }

  public boolean isUpcallStub()         { return getKind() == UpcallKind; }

  public boolean isJavaMethod()         { return false; }

  public boolean isNativeMethod()       { return false; }


  public NMethod asNMethodOrNull() {
    if (isNMethod()) return (NMethod)this;
    return null;
  }

  public int getContentSize()      { return (int) contentEnd().minus(contentBegin()); }

  public int getCodeSize()         { return (int) codeEnd()   .minus(codeBegin());    }

  public int getDataSize()         { return (int) dataEnd()   .minus(dataBegin());    }

  public int getRelocationSize()   { return (int) relocationSizeField.getValue(addr); }

  // Containment
  public boolean blobContains(Address addr)    { return headerBegin() .lessThanOrEqual(addr) && dataEnd()   .greaterThan(addr); }

  // FIXME: add relocationContains
  public boolean contentContains(Address addr) { return contentBegin().lessThanOrEqual(addr) && contentEnd().greaterThan(addr); }

  public boolean codeContains(Address addr)    { return codeBegin()   .lessThanOrEqual(addr) && codeEnd()   .greaterThan(addr); }

  public boolean dataContains(Address addr)    { return dataBegin()   .lessThanOrEqual(addr) && dataEnd()   .greaterThan(addr); }

  public boolean contains(Address addr)        { return contentContains(addr);                                                  }

  public boolean isFrameCompleteAt(Address a)  { return codeContains(a) && a.minus(codeBegin()) >= getFrameCompleteOffset(); }

  public ImmutableOopMap getOopMapForReturnAddress(Address returnAddress, boolean debugging) {
    Address pc = returnAddress;
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(getOopMaps() != null, "nope");
    }
    return getOopMaps().findMapAtOffset(pc.minus(codeBegin()), debugging);
  }

  /** NOTE: this returns a size in BYTES in this system! */
  public long getFrameSize() {
    return VM.getVM().getAddressSize() * getFrameSizeWords();
  }

  // Returns true, if the next frame is responsible for GC'ing oops passed as arguments
  public boolean callerMustGCArguments() {
    return callerMustGCArgumentsField.getValue(addr) != 0;
  }

  public void print() {
    printOn(System.out);
  }

  public void printOn(PrintStream tty) {
    tty.print(getName());
    printComponentsOn(tty);
  }

  protected void printComponentsOn(PrintStream tty) {
    tty.println(" content: [" + contentBegin() + ", " + contentEnd() + "), " +
                " code: [" + codeBegin() + ", " + codeEnd() + "), " +
                " data: [" + dataBegin() + ", " + dataEnd() + "), " +
                " frame size: " + getFrameSize());
  }
}
