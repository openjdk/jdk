/*
 * Copyright (c) 2000, 2004, Oracle and/or its affiliates. All rights reserved.
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

// A Method represents a Java method

public class Method extends Oop {
  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
    Type type                  = db.lookupType("methodOopDesc");
    constMethod                = new OopField(type.getOopField("_constMethod"), 0);
    constants                  = new OopField(type.getOopField("_constants"), 0);
    methodSize                 = new CIntField(type.getCIntegerField("_method_size"), 0);
    maxStack                   = new CIntField(type.getCIntegerField("_max_stack"), 0);
    maxLocals                  = new CIntField(type.getCIntegerField("_max_locals"), 0);
    sizeOfParameters           = new CIntField(type.getCIntegerField("_size_of_parameters"), 0);
    accessFlags                = new CIntField(type.getCIntegerField("_access_flags"), 0);
    code                       = type.getAddressField("_code");
    vtableIndex                = new CIntField(type.getCIntegerField("_vtable_index"), 0);
    if (!VM.getVM().isCore()) {
      invocationCounter        = new CIntField(type.getCIntegerField("_invocation_counter"), 0);
    }
    bytecodeOffset = type.getSize();

    /*
    interpreterEntry           = type.getAddressField("_interpreter_entry");
    fromCompiledCodeEntryPoint = type.getAddressField("_from_compiled_code_entry_point");

    */
    objectInitializerName = null;
    classInitializerName = null;
  }

  Method(OopHandle handle, ObjectHeap heap) {
    super(handle, heap);
  }

  public boolean isMethod()            { return true; }

  // Fields
  private static OopField  constMethod;
  private static OopField  constants;
  private static CIntField methodSize;
  private static CIntField maxStack;
  private static CIntField maxLocals;
  private static CIntField sizeOfParameters;
  private static CIntField accessFlags;
  private static CIntField vtableIndex;
  private static CIntField invocationCounter;
  private static long      bytecodeOffset;

  private static AddressField       code;

  // constant method names - <init>, <clinit>
  // Initialized lazily to avoid initialization ordering dependencies between Method and SymbolTable
  private static Symbol objectInitializerName;
  private static Symbol classInitializerName;
  private static Symbol objectInitializerName() {
    if (objectInitializerName == null) {
      objectInitializerName = VM.getVM().getSymbolTable().probe("<init>");
    }
    return objectInitializerName;
  }
  private static Symbol classInitializerName() {
    if (classInitializerName == null) {
      classInitializerName = VM.getVM().getSymbolTable().probe("<clinit>");
    }
    return classInitializerName;
  }


  /*
  private static AddressCField       interpreterEntry;
  private static AddressCField       fromCompiledCodeEntryPoint;
  */

  // Accessors for declared fields
  public ConstMethod  getConstMethod()                { return (ConstMethod)  constMethod.getValue(this);       }
  public ConstantPool getConstants()                  { return (ConstantPool) constants.getValue(this);         }
  public TypeArray    getExceptionTable()             { return getConstMethod().getExceptionTable();            }
  /** WARNING: this is in words, not useful in this system; use getObjectSize() instead */
  public long         getMethodSize()                 { return                methodSize.getValue(this);        }
  public long         getMaxStack()                   { return                maxStack.getValue(this);          }
  public long         getMaxLocals()                  { return                maxLocals.getValue(this);         }
  public long         getSizeOfParameters()           { return                sizeOfParameters.getValue(this);  }
  public long         getNameIndex()                  { return                getConstMethod().getNameIndex();  }
  public long         getSignatureIndex()             { return            getConstMethod().getSignatureIndex(); }
  public long         getGenericSignatureIndex()      { return     getConstMethod().getGenericSignatureIndex(); }
  public long         getAccessFlags()                { return                accessFlags.getValue(this);       }
  public long         getCodeSize()                   { return                getConstMethod().getCodeSize();   }
  public long         getVtableIndex()                { return                vtableIndex.getValue(this);       }
  public long         getInvocationCounter()          {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(!VM.getVM().isCore(), "must not be used in core build");
    }
    return invocationCounter.getValue(this);
  }

  // get associated compiled native method, if available, else return null.
  public NMethod getNativeMethod() {
    Address addr = code.getValue(getHandle());
    return (NMethod) VMObjectFactory.newObject(NMethod.class, addr);
  }

  // Convenience routine
  public AccessFlags getAccessFlagsObj() {
    return new AccessFlags(getAccessFlags());
  }

  /** Get a bytecode or breakpoint at the given bci */
  public int getBytecodeOrBPAt(int bci) {
    return getConstMethod().getBytecodeOrBPAt(bci);
  }

  /** Fetch the original non-breakpoint bytecode at the specified
      bci. It is required that there is currently a bytecode at this
      bci. */
  public int getOrigBytecodeAt(int bci) {
    BreakpointInfo bp = ((InstanceKlass) getMethodHolder()).getBreakpoints();
    for (; bp != null; bp = bp.getNext()) {
      if (bp.match(this, bci)) {
        return bp.getOrigBytecode();
      }
    }
    System.err.println("Requested bci " + bci);
    for (; bp != null; bp = bp.getNext()) {
      System.err.println("Breakpoint at bci " + bp.getBCI() + ", bytecode " +
                         bp.getOrigBytecode());
    }
    Assert.that(false, "Should not reach here");
    return -1; // not reached
  }

  public byte getBytecodeByteArg(int bci) {
    return getConstMethod().getBytecodeByteArg(bci);
  }

  /** Fetches a 16-bit big-endian ("Java ordered") value from the
      bytecode stream */
  public short getBytecodeShortArg(int bci) {
    return getConstMethod().getBytecodeShortArg(bci);
  }

  /** Fetches a 32-bit big-endian ("Java ordered") value from the
      bytecode stream */
  public int getBytecodeIntArg(int bci) {
    return getConstMethod().getBytecodeIntArg(bci);
  }

  public byte[] getByteCode() {
    return getConstMethod().getByteCode();
  }

  /*
  public Address      getCode()                       { return codeField.getValue(this); }
  public Address      getInterpreterEntry()           { return interpreterEntryField.getValue(this); }
  public Address      getFromCompiledCodeEntryPoint() { return fromCompiledCodeEntryPointField.getValue(this); }
  */
  // Accessors
  public Symbol  getName()          { return (Symbol) getConstants().getObjAt(getNameIndex());         }
  public Symbol  getSignature()     { return (Symbol) getConstants().getObjAt(getSignatureIndex());    }
  public Symbol  getGenericSignature() {
     long index = getGenericSignatureIndex();
     return (index != 0L) ? (Symbol) getConstants().getObjAt(index) : null;
  }

  // Method holder (the Klass holding this method)
  public Klass   getMethodHolder()  { return getConstants().getPoolHolder();                           }

  // Access flags
  public boolean isPublic()         { return getAccessFlagsObj().isPublic();                           }
  public boolean isPrivate()        { return getAccessFlagsObj().isPrivate();                          }
  public boolean isProtected()      { return getAccessFlagsObj().isProtected();                        }
  public boolean isPackagePrivate() { AccessFlags af = getAccessFlagsObj();
                                      return (!af.isPublic() && !af.isPrivate() && !af.isProtected()); }
  public boolean isStatic()         { return getAccessFlagsObj().isStatic();                           }
  public boolean isFinal()          { return getAccessFlagsObj().isFinal();                            }
  public boolean isSynchronized()   { return getAccessFlagsObj().isSynchronized();                     }
  public boolean isBridge()         { return getAccessFlagsObj().isBridge();                           }
  public boolean isVarArgs()        { return getAccessFlagsObj().isVarArgs();                          }
  public boolean isNative()         { return getAccessFlagsObj().isNative();                           }
  public boolean isAbstract()       { return getAccessFlagsObj().isAbstract();                         }
  public boolean isStrict()         { return getAccessFlagsObj().isStrict();                           }
  public boolean isSynthetic()      { return getAccessFlagsObj().isSynthetic();                        }

  public boolean isConstructor() {
     return (!isStatic()) && getName().equals(objectInitializerName());
  }

  public boolean isStaticInitializer() {
     return isStatic() && getName().equals(classInitializerName());
  }

  public boolean isObsolete() {
     return getAccessFlagsObj().isObsolete();
  }

  public OopMapCacheEntry getMaskFor(int bci) {
    OopMapCacheEntry entry = new OopMapCacheEntry();
    entry.fill(this, bci);
    return entry;
  }

  public long getObjectSize() {
    return getMethodSize() * getHeap().getOopSize();
  }

  public void printValueOn(PrintStream tty) {
    tty.print("Method " + getName().asString() + getSignature().asString() + "@" + getHandle());
  }

  public void iterateFields(OopVisitor visitor, boolean doVMFields) {
    super.iterateFields(visitor, doVMFields);
    if (doVMFields) {
      visitor.doOop(constMethod, true);
      visitor.doOop(constants, true);
      visitor.doCInt(methodSize, true);
      visitor.doCInt(maxStack, true);
      visitor.doCInt(maxLocals, true);
      visitor.doCInt(sizeOfParameters, true);
      visitor.doCInt(accessFlags, true);
    }
  }

  public boolean hasLineNumberTable() {
    return getConstMethod().hasLineNumberTable();
  }

  public int getLineNumberFromBCI(int bci) {
    return getConstMethod().getLineNumberFromBCI(bci);
  }

  public LineNumberTableElement[] getLineNumberTable() {
    return getConstMethod().getLineNumberTable();
  }

  public boolean hasLocalVariableTable() {
    return getConstMethod().hasLocalVariableTable();
  }

  /** Should only be called if table is present */
  public LocalVariableTableElement[] getLocalVariableTable() {
    return getConstMethod().getLocalVariableTable();
  }

  public Symbol getLocalVariableName(int bci, int slot) {
    if (! hasLocalVariableTable()) {
       return null;
    }

    LocalVariableTableElement[] locals = getLocalVariableTable();
    for (int l = 0; l < locals.length; l++) {
       LocalVariableTableElement local = locals[l];
       if ((bci >= local.getStartBCI()) &&
          (bci < (local.getStartBCI() + local.getLength())) &&
          slot == local.getSlot()) {
          return getConstants().getSymbolAt(local.getNameCPIndex());
       }
    }

    return null;
  }

  public boolean hasCheckedExceptions() {
    return getConstMethod().hasCheckedExceptions();
  }

  /** Should only be called if table is present */
  public CheckedExceptionElement[] getCheckedExceptions() {
    return getConstMethod().getCheckedExceptions();
  }

  /** Returns name and signature in external form for debugging
      purposes */
  public String externalNameAndSignature() {
    final StringBuffer buf = new StringBuffer();
    buf.append(getMethodHolder().getName().asString());
    buf.append(".");
    buf.append(getName().asString());
    buf.append("(");
    new SignatureConverter(getSignature(), buf).iterateParameters();
    buf.append(")");
    return buf.toString().replace('/', '.');
  }
}
