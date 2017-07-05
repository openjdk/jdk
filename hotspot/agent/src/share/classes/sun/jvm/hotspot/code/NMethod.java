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

package sun.jvm.hotspot.code;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class NMethod extends CodeBlob {
  private static long          pcDescSize;
  private static CIntegerField zombieInstructionSizeField;
  private static sun.jvm.hotspot.types.OopField methodField;
  /** != InvocationEntryBci if this nmethod is an on-stack replacement method */
  private static CIntegerField entryBCIField;
  /** To support simple linked-list chaining of nmethods */
  private static AddressField  osrLinkField;
  private static AddressField  scavengeRootLinkField;
  private static JByteField    scavengeRootStateField;

  /** Offsets for different nmethod parts */
  private static CIntegerField exceptionOffsetField;
  private static CIntegerField deoptOffsetField;
  private static CIntegerField origPCOffsetField;
  private static CIntegerField stubOffsetField;
  private static CIntegerField oopsOffsetField;
  private static CIntegerField scopesDataOffsetField;
  private static CIntegerField scopesPCsOffsetField;
  private static CIntegerField dependenciesOffsetField;
  private static CIntegerField handlerTableOffsetField;
  private static CIntegerField nulChkTableOffsetField;
  private static CIntegerField nmethodEndOffsetField;

  /** Offsets for entry points */
  /** Entry point with class check */
  private static AddressField  entryPointField;
  /** Entry point without class check */
  private static AddressField  verifiedEntryPointField;
  /** Entry point for on stack replacement */
  private static AddressField  osrEntryPointField;

  // FIXME: add access to flags (how?)

  /** NMethod Flushing lock (if non-zero, then the nmethod is not removed) */
  private static JIntField     lockCountField;

  /** not_entrant method removal. Each mark_sweep pass will update
      this mark to current sweep invocation count if it is seen on the
      stack.  An not_entrant method can be removed when there is no
      more activations, i.e., when the _stack_traversal_mark is less than
      current sweep traversal index. */
  private static CIntegerField stackTraversalMarkField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static void initialize(TypeDataBase db) {
    Type type = db.lookupType("nmethod");

    zombieInstructionSizeField  = type.getCIntegerField("_zombie_instruction_size");
    methodField                 = type.getOopField("_method");
    entryBCIField               = type.getCIntegerField("_entry_bci");
    osrLinkField                = type.getAddressField("_osr_link");
    scavengeRootLinkField       = type.getAddressField("_scavenge_root_link");
    scavengeRootStateField      = type.getJByteField("_scavenge_root_state");

    exceptionOffsetField        = type.getCIntegerField("_exception_offset");
    deoptOffsetField            = type.getCIntegerField("_deoptimize_offset");
    origPCOffsetField           = type.getCIntegerField("_orig_pc_offset");
    stubOffsetField             = type.getCIntegerField("_stub_offset");
    oopsOffsetField             = type.getCIntegerField("_oops_offset");
    scopesDataOffsetField       = type.getCIntegerField("_scopes_data_offset");
    scopesPCsOffsetField        = type.getCIntegerField("_scopes_pcs_offset");
    dependenciesOffsetField     = type.getCIntegerField("_dependencies_offset");
    handlerTableOffsetField     = type.getCIntegerField("_handler_table_offset");
    nulChkTableOffsetField      = type.getCIntegerField("_nul_chk_table_offset");
    nmethodEndOffsetField       = type.getCIntegerField("_nmethod_end_offset");
    entryPointField             = type.getAddressField("_entry_point");
    verifiedEntryPointField     = type.getAddressField("_verified_entry_point");
    osrEntryPointField          = type.getAddressField("_osr_entry_point");
    lockCountField              = type.getJIntField("_lock_count");
    stackTraversalMarkField     = type.getCIntegerField("_stack_traversal_mark");

    pcDescSize = db.lookupType("PcDesc").getSize();
  }

  public NMethod(Address addr) {
    super(addr);
  }


  // Accessors
  public Address getAddress() {
    return addr;
  }

  public Method getMethod() {
    return (Method) VM.getVM().getObjectHeap().newOop(methodField.getValue(addr));
  }

  // Type info
  public boolean isNMethod()      { return true;                    }
  public boolean isJavaMethod()   { return !getMethod().isNative(); }
  public boolean isNativeMethod() { return getMethod().isNative();  }
  public boolean isOSRMethod()    { return getEntryBCI() != VM.getVM().getInvocationEntryBCI(); }

  /** Boundaries for different parts */
  public Address constantsBegin()       { return instructionsBegin();                                }
  public Address constantsEnd()         { return getEntryPoint();                                    }
  public Address codeBegin()            { return getEntryPoint();                                    }
  public Address codeEnd()              { return headerBegin().addOffsetTo(getStubOffset());         }
  public Address exceptionBegin()       { return headerBegin().addOffsetTo(getExceptionOffset());    }
  public Address deoptBegin()           { return headerBegin().addOffsetTo(getDeoptOffset());        }
  public Address stubBegin()            { return headerBegin().addOffsetTo(getStubOffset());         }
  public Address stubEnd()              { return headerBegin().addOffsetTo(getOopsOffset());         }
  public Address oopsBegin()            { return headerBegin().addOffsetTo(getOopsOffset());         }
  public Address oopsEnd()              { return headerBegin().addOffsetTo(getScopesDataOffset());   }
  public Address scopesDataBegin()      { return headerBegin().addOffsetTo(getScopesDataOffset());   }
  public Address scopesDataEnd()        { return headerBegin().addOffsetTo(getScopesPCsOffset());    }
  public Address scopesPCsBegin()       { return headerBegin().addOffsetTo(getScopesPCsOffset());    }
  public Address scopesPCsEnd()         { return headerBegin().addOffsetTo(getDependenciesOffset()); }
  public Address dependenciesBegin()    { return headerBegin().addOffsetTo(getDependenciesOffset()); }
  public Address dependenciesEnd()      { return headerBegin().addOffsetTo(getHandlerTableOffset()); }
  public Address handlerTableBegin()    { return headerBegin().addOffsetTo(getHandlerTableOffset()); }
  public Address handlerTableEnd()      { return headerBegin().addOffsetTo(getNulChkTableOffset());  }
  public Address nulChkTableBegin()     { return headerBegin().addOffsetTo(getNulChkTableOffset());  }
  public Address nulChkTableEnd()       { return headerBegin().addOffsetTo(getNMethodEndOffset());   }

  public int constantsSize()            { return (int) constantsEnd()   .minus(constantsBegin());    }
  public int codeSize()                 { return (int) codeEnd()        .minus(codeBegin());         }
  public int stubSize()                 { return (int) stubEnd()        .minus(stubBegin());         }
  public int oopsSize()                 { return (int) oopsEnd()        .minus(oopsBegin());         }
  public int scopesDataSize()           { return (int) scopesDataEnd()  .minus(scopesDataBegin());   }
  public int scopesPCsSize()            { return (int) scopesPCsEnd()   .minus(scopesPCsBegin());    }
  public int dependenciesSize()         { return (int) dependenciesEnd().minus(dependenciesBegin()); }
  public int handlerTableSize()         { return (int) handlerTableEnd().minus(handlerTableBegin()); }
  public int nulChkTableSize()          { return (int) nulChkTableEnd() .minus(nulChkTableBegin());  }
  public int origPCOffset()             { return (int) origPCOffsetField.getValue(addr);             }

  public int totalSize() {
    return
      constantsSize()    +
      codeSize()         +
      stubSize()         +
      scopesDataSize()   +
      scopesPCsSize()    +
      dependenciesSize() +
      handlerTableSize() +
      nulChkTableSize();
  }

  public boolean constantsContains   (Address addr) { return constantsBegin()   .lessThanOrEqual(addr) && constantsEnd()   .greaterThan(addr); }
  public boolean codeContains        (Address addr) { return codeBegin()        .lessThanOrEqual(addr) && codeEnd()        .greaterThan(addr); }
  public boolean stubContains        (Address addr) { return stubBegin()        .lessThanOrEqual(addr) && stubEnd()        .greaterThan(addr); }
  public boolean oopsContains        (Address addr) { return oopsBegin()        .lessThanOrEqual(addr) && oopsEnd()        .greaterThan(addr); }
  public boolean scopesDataContains  (Address addr) { return scopesDataBegin()  .lessThanOrEqual(addr) && scopesDataEnd()  .greaterThan(addr); }
  public boolean scopesPCsContains   (Address addr) { return scopesPCsBegin()   .lessThanOrEqual(addr) && scopesPCsEnd()   .greaterThan(addr); }
  public boolean handlerTableContains(Address addr) { return handlerTableBegin().lessThanOrEqual(addr) && handlerTableEnd().greaterThan(addr); }
  public boolean nulChkTableContains (Address addr) { return nulChkTableBegin() .lessThanOrEqual(addr) && nulChkTableEnd() .greaterThan(addr); }

  /** Entry points */
  public Address getEntryPoint()         { return entryPointField.getValue(addr);         }
  public Address getVerifiedEntryPoint() { return verifiedEntryPointField.getValue(addr); }

  /** Support for oops in scopes and relocs. Note: index 0 is reserved for null. */
  public OopHandle getOopAt(int index) {
    if (index == 0) return null;
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(index > 0 && index <= oopsSize(), "must be a valid non-zero index");
    }
    return oopsBegin().getOopHandleAt((index - 1) * VM.getVM().getOopSize());
  }

  // FIXME: add interpreter_entry_point()
  // FIXME: add lazy_interpreter_entry_point() for C2

  // **********
  // * FIXME: * ADD ACCESS TO FLAGS!!!!
  // **********
  // public boolean isInUse();
  // public boolean isAlive();
  // public boolean isNotEntrant();
  // public boolean isZombie();

  // ********************************
  // * MAJOR FIXME: MAJOR HACK HERE *
  // ********************************
  public boolean isZombie() { return false; }

  // public boolean isUnloaded();
  // public boolean isYoung();
  // public boolean isOld();
  // public int     age();
  // public boolean isMarkedForDeoptimization();
  // public boolean isMarkedForUnloading();
  // public boolean isMarkedForReclamation();
  // public int     level();
  // public int     version();

  // FIXME: add mutators for above
  // FIXME: add exception cache access?

  /** On-stack replacement support */
  // FIXME: add mutators
  public int getOSREntryBCI() {
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(getEntryBCI() != VM.getVM().getInvocationEntryBCI(), "wrong kind of nmethod");
    }
    return getEntryBCI();
  }

  public NMethod getOSRLink() {
    return (NMethod) VMObjectFactory.newObject(NMethod.class, osrLinkField.getValue(addr));
  }

  public NMethod getScavengeRootLink() {
    return (NMethod) VMObjectFactory.newObject(NMethod.class, scavengeRootLinkField.getValue(addr));
  }

  public int getScavengeRootState() {
    return (int) scavengeRootStateField.getValue(addr);
  }


  /** Tells whether frames described by this nmethod can be
      deoptimized. Note: native wrappers cannot be deoptimized. */
  public boolean canBeDeoptimized() { return isJavaMethod(); }

  // FIXME: add inline cache support
  // FIXME: add flush()

  public boolean isLockedByVM() { return lockCountField.getValue(addr) > 0; }

  // FIXME: add mark_as_seen_on_stack
  // FIXME: add can_not_entrant_be_converted

  // FIXME: add GC support
  //  void follow_roots_or_mark_for_unloading(bool unloading_occurred, bool& marked_for_unloading);
  //  void follow_root_or_mark_for_unloading(oop* root, bool unloading_occurred, bool& marked_for_unloading);
  //  void preserve_callee_argument_oops(frame fr, const RegisterMap *reg_map, void f(oop*));
  //  void adjust_pointers();

  /** Finds a PCDesc with real-pc equal to "pc" */
  public PCDesc getPCDescAt(Address pc) {
    // FIXME: consider adding cache like the one down in the VM
    for (Address p = scopesPCsBegin(); p.lessThan(scopesPCsEnd()); p = p.addOffsetTo(pcDescSize)) {
      PCDesc pcDesc = new PCDesc(p);
      if (pcDesc.getRealPC(this).equals(pc)) {
        return pcDesc;
      }
    }
    return null;
  }

  /** ScopeDesc for an instruction */
  public ScopeDesc getScopeDescAt(Address pc) {
    PCDesc pd = getPCDescAt(pc);
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(pd != null, "scope must be present");
    }
    return new ScopeDesc(this, pd.getScopeDecodeOffset(), pd.getObjDecodeOffset(), pd.getReexecute());
  }

  /** This is only for use by the debugging system, and is only
      intended for use in the topmost frame, where we are not
      guaranteed to be at a PC for which we have a PCDesc. It finds
      the PCDesc with realPC closest to the current PC. */
  public PCDesc getPCDescNearDbg(Address pc) {
    PCDesc bestGuessPCDesc = null;
    long bestDistance = 0;
    for (Address p = scopesPCsBegin(); p.lessThan(scopesPCsEnd()); p = p.addOffsetTo(pcDescSize)) {
      PCDesc pcDesc = new PCDesc(p);
      // In case pc is null
      long distance = -pcDesc.getRealPC(this).minus(pc);
      if ((bestGuessPCDesc == null) ||
          ((distance >= 0) && (distance < bestDistance))) {
        bestGuessPCDesc = pcDesc;
        bestDistance    = distance;
      }
    }
    return bestGuessPCDesc;
  }

  /** This is only for use by the debugging system, and is only
      intended for use in the topmost frame, where we are not
      guaranteed to be at a PC for which we have a PCDesc. It finds
      the ScopeDesc closest to the current PC. NOTE that this may
      return NULL for compiled methods which don't have any
      ScopeDescs! */
  public ScopeDesc getScopeDescNearDbg(Address pc) {
    PCDesc pd = getPCDescNearDbg(pc);
    if (pd == null) return null;
    return new ScopeDesc(this, pd.getScopeDecodeOffset(), pd.getObjDecodeOffset(), pd.getReexecute());
  }

  public Map/*<Address, PCDesc>*/ getSafepoints() {
    Map safepoints = new HashMap(); // Map<Address, PCDesc>
    sun.jvm.hotspot.debugger.Address p = null;
    for (p = scopesPCsBegin(); p.lessThan(scopesPCsEnd());
         p = p.addOffsetTo(pcDescSize)) {
       PCDesc pcDesc = new PCDesc(p);
       sun.jvm.hotspot.debugger.Address pc = pcDesc.getRealPC(this);
       safepoints.put(pc, pcDesc);
    }
    return safepoints;
  }

  // FIXME: add getPCOffsetForBCI()
  // FIXME: add embeddedOopAt()
  // FIXME: add isDependentOn()
  // FIXME: add isPatchableAt()

  /** Support for code generation. Only here for proof-of-concept. */
  public static int getEntryPointOffset()            { return (int) entryPointField.getOffset();            }
  public static int getVerifiedEntryPointOffset()    { return (int) verifiedEntryPointField.getOffset();    }
  public static int getOSREntryPointOffset()         { return (int) osrEntryPointField.getOffset();         }
  public static int getEntryBCIOffset()              { return (int) entryBCIField.getOffset();              }
  /** NOTE: renamed from "method_offset_in_bytes" */
  public static int getMethodOffset()                { return (int) methodField.getOffset();                }

  public void print() {
    printOn(System.out);
  }

  protected void printComponentsOn(PrintStream tty) {
    // FIXME: add relocation information
    tty.println(" instructions: [" + instructionsBegin() + ", " + instructionsEnd() + "), " +
                " data: [" + dataBegin() + ", " + dataEnd() + "), " +
                " oops: [" + oopsBegin() + ", " + oopsEnd() + "), " +
                " frame size: " + getFrameSize());
  }

  public String toString() {
    Method method = getMethod();
    return "NMethod for " +
            method.getMethodHolder().getName().asString() + "." +
            method.getName().asString() + method.getSignature().asString() + "==>n" +
            super.toString();
  }

  public String flagsToString() {
    // FIXME need access to flags...
    return "";
  }

  public String getName() {
    Method method = getMethod();
    return "NMethod for " +
           method.getMethodHolder().getName().asString() + "." +
           method.getName().asString() +
           method.getSignature().asString();
  }

  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  private int getEntryBCI()           { return (int) entryBCIField          .getValue(addr); }
  private int getExceptionOffset()    { return (int) exceptionOffsetField   .getValue(addr); }
  private int getDeoptOffset()        { return (int) deoptOffsetField       .getValue(addr); }
  private int getStubOffset()         { return (int) stubOffsetField        .getValue(addr); }
  private int getOopsOffset()         { return (int) oopsOffsetField        .getValue(addr); }
  private int getScopesDataOffset()   { return (int) scopesDataOffsetField  .getValue(addr); }
  private int getScopesPCsOffset()    { return (int) scopesPCsOffsetField   .getValue(addr); }
  private int getDependenciesOffset() { return (int) dependenciesOffsetField.getValue(addr); }
  private int getHandlerTableOffset() { return (int) handlerTableOffsetField.getValue(addr); }
  private int getNulChkTableOffset()  { return (int) nulChkTableOffsetField .getValue(addr); }
  private int getNMethodEndOffset()   { return (int) nmethodEndOffsetField  .getValue(addr); }
}
