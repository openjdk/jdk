/*
 * Copyright (c) 2000, 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;

public class CodeCache {
  private static AddressField       heapField;
  private static AddressField       scavengeRootNMethodsField;
  private static VirtualConstructor virtualConstructor;

  private CodeHeap heap;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("CodeCache");

    heapField = type.getAddressField("_heap");
    scavengeRootNMethodsField = type.getAddressField("_scavenge_root_nmethods");

    virtualConstructor = new VirtualConstructor(db);
    // Add mappings for all possible CodeBlob subclasses
    virtualConstructor.addMapping("BufferBlob", BufferBlob.class);
    virtualConstructor.addMapping("nmethod", NMethod.class);
    virtualConstructor.addMapping("RuntimeStub", RuntimeStub.class);
    virtualConstructor.addMapping("AdapterBlob", AdapterBlob.class);
    virtualConstructor.addMapping("MethodHandlesAdapterBlob", MethodHandlesAdapterBlob.class);
    virtualConstructor.addMapping("SafepointBlob", SafepointBlob.class);
    virtualConstructor.addMapping("DeoptimizationBlob", DeoptimizationBlob.class);
    if (VM.getVM().isServerCompiler()) {
      virtualConstructor.addMapping("ExceptionBlob", ExceptionBlob.class);
      virtualConstructor.addMapping("UncommonTrapBlob", UncommonTrapBlob.class);
    }
  }

  public CodeCache() {
    heap = (CodeHeap) VMObjectFactory.newObject(CodeHeap.class, heapField.getValue());
  }

  public NMethod scavengeRootMethods() {
    return (NMethod) VMObjectFactory.newObject(NMethod.class, scavengeRootNMethodsField.getValue());
  }

  public boolean contains(Address p) {
    return getHeap().contains(p);
  }

  /** When VM.getVM().isDebugging() returns true, this behaves like
      findBlobUnsafe */
  public CodeBlob findBlob(Address start) {
    CodeBlob result = findBlobUnsafe(start);
    if (result == null) return null;
    if (VM.getVM().isDebugging()) {
      return result;
    }
    // We could potientially look up non_entrant methods
    // NOTE: this is effectively a "guarantee", and is slightly different from the one in the VM
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(!(result.isZombie() || result.isLockedByVM()), "unsafe access to zombie method");
    }
    return result;
  }

  public CodeBlob findBlobUnsafe(Address start) {
    CodeBlob result = null;

    try {
      result = (CodeBlob) virtualConstructor.instantiateWrapperFor(getHeap().findStart(start));
    }
    catch (WrongTypeException wte) {
      Address cbAddr = null;
      try {
        cbAddr = getHeap().findStart(start);
      }
      catch (Exception findEx) {
        findEx.printStackTrace();
      }

      String message = "Couldn't deduce type of CodeBlob ";
      if (cbAddr != null) {
        message = message + "@" + cbAddr + " ";
      }
      message = message + "for PC=" + start;

      throw new RuntimeException(message, wte);
    }
    if (result == null) return null;
    if (Assert.ASSERTS_ENABLED) {
      // The HeapBlock that contains this blob is outside of the blob
      // but it shouldn't be an error to find a blob based on the
      // pointer to the HeapBlock.
      Assert.that(result.blobContains(start) || result.blobContains(start.addOffsetTo(8)),
                                                                    "found wrong CodeBlob");
    }
    return result;
  }

  public NMethod findNMethod(Address start) {
    CodeBlob cb = findBlob(start);
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(cb == null || cb.isNMethod(), "did not find an nmethod");
    }
    return (NMethod) cb;
  }

  public NMethod findNMethodUnsafe(Address start) {
    CodeBlob cb = findBlobUnsafe(start);
    if (Assert.ASSERTS_ENABLED) {
      Assert.that(cb == null || cb.isNMethod(), "did not find an nmethod");
    }
    return (NMethod) cb;
  }

  /** Routine for instantiating appropriately-typed wrapper for a
      CodeBlob. Used by CodeCache, Runtime1, etc. */
  public CodeBlob createCodeBlobWrapper(Address codeBlobAddr) {
    try {
      return (CodeBlob) virtualConstructor.instantiateWrapperFor(codeBlobAddr);
    }
    catch (Exception e) {
      String message = "Unable to deduce type of CodeBlob from address " + codeBlobAddr +
                       " (expected type nmethod, RuntimeStub, ";
      if (VM.getVM().isClientCompiler()) {
        message = message + " or ";
      }
      message = message + "SafepointBlob";
      if (VM.getVM().isServerCompiler()) {
        message = message + ", DeoptimizationBlob, or ExceptionBlob";
      }
      message = message + ")";
      throw new RuntimeException(message);
    }
  }

  public void iterate(CodeCacheVisitor visitor) {
    CodeHeap heap = getHeap();
    Address ptr = heap.begin();
    Address end = heap.end();

    visitor.prologue(ptr, end);
    CodeBlob lastBlob = null;
    while (ptr != null && ptr.lessThan(end)) {
      try {
        // Use findStart to get a pointer inside blob other findBlob asserts
        CodeBlob blob = findBlobUnsafe(heap.findStart(ptr));
        if (blob != null) {
          visitor.visit(blob);
          if (blob == lastBlob) {
            throw new InternalError("saw same blob twice");
          }
          lastBlob = blob;
        }
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
      Address next = heap.nextBlock(ptr);
      if (next != null && next.lessThan(ptr)) {
        throw new InternalError("pointer moved backwards");
      }
      ptr = next;
    }
    visitor.epilogue();
  }

  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  private CodeHeap getHeap() {
    return heap;
  }
}
