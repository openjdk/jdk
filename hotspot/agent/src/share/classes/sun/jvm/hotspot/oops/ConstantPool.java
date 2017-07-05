/*
 * Copyright (c) 2000, 2010, Oracle and/or its affiliates. All rights reserved.
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

// A ConstantPool is an oop containing class constants
// as described in the class file

public class ConstantPool extends Oop implements ClassConstants {
  // Used for debugging this code
  private static final boolean DEBUG = false;

  protected void debugMessage(String message) {
    System.out.println(message);
  }

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type   = db.lookupType("constantPoolOopDesc");
    tags        = new OopField(type.getOopField("_tags"), 0);
    cache       = new OopField(type.getOopField("_cache"), 0);
    poolHolder  = new OopField(type.getOopField("_pool_holder"), 0);
    length      = new CIntField(type.getCIntegerField("_length"), 0);
    headerSize  = type.getSize();
    elementSize = 0;
  }

  ConstantPool(OopHandle handle, ObjectHeap heap) {
    super(handle, heap);
  }

  public boolean isConstantPool()      { return true; }

  private static OopField tags;
  private static OopField cache;
  private static OopField poolHolder;
  private static CIntField length; // number of elements in oop

  private static long headerSize;
  private static long elementSize;

  public TypeArray         getTags()       { return (TypeArray)         tags.getValue(this); }
  public ConstantPoolCache getCache()      { return (ConstantPoolCache) cache.getValue(this); }
  public Klass             getPoolHolder() { return (Klass)             poolHolder.getValue(this); }
  public int               getLength()     { return (int)length.getValue(this); }

  private long getElementSize() {
    if (elementSize !=0 ) {
      return elementSize;
    } else {
      elementSize = VM.getVM().getOopSize();
    }
    return elementSize;
  }

  private long indexOffset(long index) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(index > 0 && index < getLength(),  "invalid cp index " + index + " " + getLength());
    }
    return (index * getElementSize()) + headerSize;
  }

  public ConstantTag getTagAt(long index) {
    return new ConstantTag(getTags().getByteAt((int) index));
  }

  public Oop getObjAt(long index){
    return getHeap().newOop(getHandle().getOopHandleAt(indexOffset(index)));
  }

  public Symbol getSymbolAt(long index) {
    return (Symbol) getObjAt(index);
  }

  public int getIntAt(long index){
    return getHandle().getJIntAt(indexOffset(index));
  }

  public float getFloatAt(long index){
    return getHandle().getJFloatAt(indexOffset(index));
  }

  public long getLongAt(long index) {
    int oneHalf = getHandle().getJIntAt(indexOffset(index + 1));
    int otherHalf   = getHandle().getJIntAt(indexOffset(index));
    // buildLongFromIntsPD accepts higher address value, lower address value
    // in that order.
    return VM.getVM().buildLongFromIntsPD(oneHalf, otherHalf);
  }

  public double getDoubleAt(long index) {
    return Double.longBitsToDouble(getLongAt(index));
  }

  public int getFieldOrMethodAt(int which) {
    if (DEBUG) {
      System.err.print("ConstantPool.getFieldOrMethodAt(" + which + "): new index = ");
    }
    int i = -1;
    ConstantPoolCache cache = getCache();
    if (cache == null) {
      i = which;
    } else {
      // change byte-ordering and go via cache
      i = cache.getEntryAt(0xFFFF & VM.getVM().getBytes().swapShort((short) which)).getConstantPoolIndex();
    }
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(getTagAt(i).isFieldOrMethod(), "Corrupted constant pool");
    }
    if (DEBUG) {
      System.err.println(i);
    }
    int res = getIntAt(i);
    if (DEBUG) {
      System.err.println("ConstantPool.getFieldOrMethodAt(" + i + "): result = " + res);
    }
    return res;
  }

  public int[] getNameAndTypeAt(int which) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(getTagAt(which).isNameAndType(), "Corrupted constant pool");
    }
    int i = getIntAt(which);
    if (DEBUG) {
      System.err.println("ConstantPool.getNameAndTypeAt(" + which + "): result = " + i);
    }
    return new int[] { extractLowShortFromInt(i), extractHighShortFromInt(i) };
  }

  public Symbol getNameRefAt(int which) {
    int nameIndex = getNameAndTypeAt(getNameAndTypeRefIndexAt(which))[0];
    return getSymbolAt(nameIndex);
  }

  public Symbol getSignatureRefAt(int which) {
    int sigIndex = getNameAndTypeAt(getNameAndTypeRefIndexAt(which))[1];
    return getSymbolAt(sigIndex);
  }

  // returns null, if not resolved.
  public Klass getKlassRefAt(int which) {
    if( ! getTagAt(which).isKlass()) return null;
    return (Klass) getObjAt(which);
  }

  // returns null, if not resolved.
  public InstanceKlass getFieldOrMethodKlassRefAt(int which) {
    int refIndex = getFieldOrMethodAt(which);
    int klassIndex = extractLowShortFromInt(refIndex);
    return (InstanceKlass) getKlassRefAt(klassIndex);
  }

  // returns null, if not resolved.
  public Method getMethodRefAt(int which) {
    InstanceKlass klass = getFieldOrMethodKlassRefAt(which);
    if (klass == null) return null;
    Symbol name = getNameRefAt(which);
    Symbol sig  = getSignatureRefAt(which);
    return klass.findMethod(name, sig);
  }

  // returns null, if not resolved.
  public Field getFieldRefAt(int which) {
    InstanceKlass klass = getFieldOrMethodKlassRefAt(which);
    if (klass == null) return null;
    Symbol name = getNameRefAt(which);
    Symbol sig  = getSignatureRefAt(which);
    return klass.findField(name, sig);
  }

  public int getNameAndTypeRefIndexAt(int index) {
    int refIndex = getFieldOrMethodAt(index);
    if (DEBUG) {
      System.err.println("ConstantPool.getNameAndTypeRefIndexAt(" + index + "): refIndex = " + refIndex);
    }
    int i = extractHighShortFromInt(refIndex);
    if (DEBUG) {
      System.err.println("ConstantPool.getNameAndTypeRefIndexAt(" + index + "): result = " + i);
    }
    return i;
  }

  /** Lookup for entries consisting of (name_index, signature_index) */
  public int getNameRefIndexAt(int index) {
    int[] refIndex = getNameAndTypeAt(index);
    if (DEBUG) {
      System.err.println("ConstantPool.getNameRefIndexAt(" + index + "): refIndex = " + refIndex[0]+"/"+refIndex[1]);
    }
    int i = refIndex[0];
    if (DEBUG) {
      System.err.println("ConstantPool.getNameRefIndexAt(" + index + "): result = " + i);
    }
    return i;
  }

  /** Lookup for entries consisting of (name_index, signature_index) */
  public int getSignatureRefIndexAt(int index) {
    int[] refIndex = getNameAndTypeAt(index);
    if (DEBUG) {
      System.err.println("ConstantPool.getSignatureRefIndexAt(" + index + "): refIndex = " + refIndex[0]+"/"+refIndex[1]);
    }
    int i = refIndex[1];
    if (DEBUG) {
      System.err.println("ConstantPool.getSignatureRefIndexAt(" + index + "): result = " + i);
    }
    return i;
  }

  /** Lookup for MethodHandle entries. */
  public int getMethodHandleIndexAt(int i) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(getTagAt(i).isMethodHandle(), "Corrupted constant pool");
    }
    int res = extractHighShortFromInt(getIntAt(i));
    if (DEBUG) {
      System.err.println("ConstantPool.getMethodHandleIndexAt(" + i + "): result = " + res);
    }
    return res;
  }

  /** Lookup for MethodHandle entries. */
  public int getMethodHandleRefKindAt(int i) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(getTagAt(i).isMethodHandle(), "Corrupted constant pool");
    }
    int res = extractLowShortFromInt(getIntAt(i));
    if (DEBUG) {
      System.err.println("ConstantPool.getMethodHandleRefKindAt(" + i + "): result = " + res);
    }
    return res;
  }

  /** Lookup for MethodType entries. */
  public int getMethodTypeIndexAt(int i) {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(getTagAt(i).isMethodType(), "Corrupted constant pool");
    }
    int res = getIntAt(i);
    if (DEBUG) {
      System.err.println("ConstantPool.getMethodHandleTypeAt(" + i + "): result = " + res);
    }
    return res;
  }

  final private static String[] nameForTag = new String[] {
  };

  private String nameForTag(int tag) {
    switch (tag) {
    case JVM_CONSTANT_Utf8:               return "JVM_CONSTANT_Utf8";
    case JVM_CONSTANT_Unicode:            return "JVM_CONSTANT_Unicode";
    case JVM_CONSTANT_Integer:            return "JVM_CONSTANT_Integer";
    case JVM_CONSTANT_Float:              return "JVM_CONSTANT_Float";
    case JVM_CONSTANT_Long:               return "JVM_CONSTANT_Long";
    case JVM_CONSTANT_Double:             return "JVM_CONSTANT_Double";
    case JVM_CONSTANT_Class:              return "JVM_CONSTANT_Class";
    case JVM_CONSTANT_String:             return "JVM_CONSTANT_String";
    case JVM_CONSTANT_Fieldref:           return "JVM_CONSTANT_Fieldref";
    case JVM_CONSTANT_Methodref:          return "JVM_CONSTANT_Methodref";
    case JVM_CONSTANT_InterfaceMethodref: return "JVM_CONSTANT_InterfaceMethodref";
    case JVM_CONSTANT_NameAndType:        return "JVM_CONSTANT_NameAndType";
    case JVM_CONSTANT_MethodHandle:       return "JVM_CONSTANT_MethodHandle";
    case JVM_CONSTANT_MethodType:         return "JVM_CONSTANT_MethodType";
    case JVM_CONSTANT_Invalid:            return "JVM_CONSTANT_Invalid";
    case JVM_CONSTANT_UnresolvedClass:    return "JVM_CONSTANT_UnresolvedClass";
    case JVM_CONSTANT_UnresolvedClassInError:    return "JVM_CONSTANT_UnresolvedClassInError";
    case JVM_CONSTANT_ClassIndex:         return "JVM_CONSTANT_ClassIndex";
    case JVM_CONSTANT_UnresolvedString:   return "JVM_CONSTANT_UnresolvedString";
    case JVM_CONSTANT_StringIndex:        return "JVM_CONSTANT_StringIndex";
    }
    throw new InternalError("Unknown tag: " + tag);
  }

  public void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
    if (doVMFields) {
      visitor.doOop(tags, true);
      visitor.doOop(cache, true);
      visitor.doOop(poolHolder, true);

      final int length = (int) getLength();
      // zero'th pool entry is always invalid. ignore it.
      for (int index = 1; index < length; index++) {
        int ctag = (int) getTags().getByteAt((int) index);
        switch (ctag) {
        case JVM_CONSTANT_ClassIndex:
        case JVM_CONSTANT_StringIndex:
        case JVM_CONSTANT_Integer:
          visitor.doInt(new IntField(new NamedFieldIdentifier(nameForTag(ctag)), indexOffset(index), true), true);
          break;

        case JVM_CONSTANT_Float:
          visitor.doFloat(new FloatField(new NamedFieldIdentifier(nameForTag(ctag)), indexOffset(index), true), true);
          break;

        case JVM_CONSTANT_Long:
          visitor.doLong(new LongField(new NamedFieldIdentifier(nameForTag(ctag)), indexOffset(index), true), true);
          // long entries occupy two slots
          index++;
          break;

        case JVM_CONSTANT_Double:
          visitor.doDouble(new DoubleField(new NamedFieldIdentifier(nameForTag(ctag)), indexOffset(index), true), true);
          // double entries occupy two slots
          index++;
          break;

        case JVM_CONSTANT_UnresolvedClassInError:
        case JVM_CONSTANT_UnresolvedClass:
        case JVM_CONSTANT_Class:
        case JVM_CONSTANT_UnresolvedString:
        case JVM_CONSTANT_Utf8:
          visitor.doOop(new OopField(new NamedFieldIdentifier(nameForTag(ctag)), indexOffset(index), true), true);
          break;

        case JVM_CONSTANT_Fieldref:
        case JVM_CONSTANT_Methodref:
        case JVM_CONSTANT_InterfaceMethodref:
        case JVM_CONSTANT_NameAndType:
        case JVM_CONSTANT_MethodHandle:
        case JVM_CONSTANT_MethodType:
          visitor.doInt(new IntField(new NamedFieldIdentifier(nameForTag(ctag)), indexOffset(index), true), true);
          break;
        }
      }
    }
    /*
    int length = getLength();
    for (int index = 0; index < length; index++) {
      long offset = baseOffset + (index + typeDataBase.getOopSize());
      visitor.doOop(new IndexableField(index, offset, false), getObjAt(index));
    }
    */
  }

  public void writeBytes(OutputStream os) throws IOException {
          // Map between any modified UTF-8 and it's constant pool index.
          Map utf8ToIndex = new HashMap();
      DataOutputStream dos = new DataOutputStream(os);
      TypeArray tags = getTags();
      int len = (int)getLength();
      int ci = 0; // constant pool index

      // collect all modified UTF-8 Strings from Constant Pool

      for (ci = 1; ci < len; ci++) {
          byte cpConstType = tags.getByteAt(ci);
          if(cpConstType == JVM_CONSTANT_Utf8) {
              Symbol sym = getSymbolAt(ci);
              utf8ToIndex.put(sym.asString(), new Short((short) ci));
          }
          else if(cpConstType == JVM_CONSTANT_Long ||
                  cpConstType == JVM_CONSTANT_Double) {
              ci++;
          }
      }


      for(ci = 1; ci < len; ci++) {
          int cpConstType = (int)tags.getByteAt(ci);
          // write cp_info
          // write constant type
          switch(cpConstType) {
              case JVM_CONSTANT_Utf8: {
                  dos.writeByte(cpConstType);
                  Symbol sym = getSymbolAt(ci);
                  dos.writeShort((short)sym.getLength());
                  dos.write(sym.asByteArray());
                  if (DEBUG) debugMessage("CP[" + ci + "] = modified UTF-8 " + sym.asString());
                  break;
              }

              case JVM_CONSTANT_Unicode:
                  throw new IllegalArgumentException("Unicode constant!");

              case JVM_CONSTANT_Integer:
                  dos.writeByte(cpConstType);
                  dos.writeInt(getIntAt(ci));
                  if (DEBUG) debugMessage("CP[" + ci + "] = int " + getIntAt(ci));
                  break;

              case JVM_CONSTANT_Float:
                  dos.writeByte(cpConstType);
                  dos.writeFloat(getFloatAt(ci));
                  if (DEBUG) debugMessage("CP[" + ci + "] = float " + getFloatAt(ci));
                  break;

              case JVM_CONSTANT_Long: {
                  dos.writeByte(cpConstType);
                  long l = getLongAt(ci);
                  // long entries occupy two pool entries
                  ci++;
                  dos.writeLong(l);
                  break;
              }

              case JVM_CONSTANT_Double:
                  dos.writeByte(cpConstType);
                  dos.writeDouble(getDoubleAt(ci));
                  // double entries occupy two pool entries
                  ci++;
                  break;

              case JVM_CONSTANT_Class: {
                  dos.writeByte(cpConstType);
                  // Klass already resolved. ConstantPool constains klassOop.
                  Klass refKls = (Klass) getObjAt(ci);
                  String klassName = refKls.getName().asString();
                  Short s = (Short) utf8ToIndex.get(klassName);
                  dos.writeShort(s.shortValue());
                  if (DEBUG) debugMessage("CP[" + ci  + "] = class " + s);
                  break;
              }

              // case JVM_CONSTANT_ClassIndex:
              case JVM_CONSTANT_UnresolvedClassInError:
              case JVM_CONSTANT_UnresolvedClass: {
                  dos.writeByte(JVM_CONSTANT_Class);
                  String klassName = getSymbolAt(ci).asString();
                  Short s = (Short) utf8ToIndex.get(klassName);
                  dos.writeShort(s.shortValue());
                  if (DEBUG) debugMessage("CP[" + ci + "] = class " + s);
                  break;
              }

              case JVM_CONSTANT_String: {
                  dos.writeByte(cpConstType);
                  String str = OopUtilities.stringOopToString(getObjAt(ci));
                  Short s = (Short) utf8ToIndex.get(str);
                  dos.writeShort(s.shortValue());
                  if (DEBUG) debugMessage("CP[" + ci + "] = string " + s);
                  break;
              }

                  // case JVM_CONSTANT_StringIndex:
              case JVM_CONSTANT_UnresolvedString: {
                  dos.writeByte(JVM_CONSTANT_String);
                  String val = getSymbolAt(ci).asString();

                  Short s = (Short) utf8ToIndex.get(val);
                  dos.writeShort(s.shortValue());
                  if (DEBUG) debugMessage("CP[" + ci + "] = string " + s);
                  break;
              }

              // all external, internal method/field references
              case JVM_CONSTANT_Fieldref:
              case JVM_CONSTANT_Methodref:
              case JVM_CONSTANT_InterfaceMethodref: {
                  dos.writeByte(cpConstType);
                  int value = getIntAt(ci);
                  short klassIndex = (short) extractLowShortFromInt(value);
                  short nameAndTypeIndex = (short) extractHighShortFromInt(value);
                  dos.writeShort(klassIndex);
                  dos.writeShort(nameAndTypeIndex);
                  if (DEBUG) debugMessage("CP[" + ci + "] = ref klass = " +
                                          klassIndex + ", N&T = " + nameAndTypeIndex);
                  break;
              }

              case JVM_CONSTANT_NameAndType: {
                  dos.writeByte(cpConstType);
                  int value = getIntAt(ci);
                  short nameIndex = (short) extractLowShortFromInt(value);
                  short signatureIndex = (short) extractHighShortFromInt(value);
                  dos.writeShort(nameIndex);
                  dos.writeShort(signatureIndex);
                  if (DEBUG) debugMessage("CP[" + ci + "] = N&T name = " + nameIndex
                                          + ", type = " + signatureIndex);
                  break;
              }

              case JVM_CONSTANT_MethodHandle: {
                  dos.writeByte(cpConstType);
                  int value = getIntAt(ci);
                  short nameIndex = (short) extractLowShortFromInt(value);
                  short signatureIndex = (short) extractHighShortFromInt(value);
                  dos.writeShort(nameIndex);
                  dos.writeShort(signatureIndex);
                  if (DEBUG) debugMessage("CP[" + ci + "] = N&T name = " + nameIndex
                                          + ", type = " + signatureIndex);
                  break;
              }
              default:
                  throw new InternalError("unknown tag: " + cpConstType);
          } // switch
      }
      dos.flush();
      return;
  }

  public void printValueOn(PrintStream tty) {
    tty.print("ConstantPool for " + getPoolHolder().getName().asString());
  }

  public long getObjectSize() {
    return alignObjectSize(headerSize + (getLength() * getElementSize()));
  }

  //----------------------------------------------------------------------
  // Internals only below this point
  //

  private static int extractHighShortFromInt(int val) {
    // must stay in sync with constantPoolOopDesc::name_and_type_at_put, method_at_put, etc.
    return (val >> 16) & 0xFFFF;
  }

  private static int extractLowShortFromInt(int val) {
    // must stay in sync with constantPoolOopDesc::name_and_type_at_put, method_at_put, etc.
    return val & 0xFFFF;
  }
}
