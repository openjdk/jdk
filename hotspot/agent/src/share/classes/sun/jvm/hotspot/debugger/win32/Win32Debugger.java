/*
 * Copyright (c) 2000, 2008, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger.win32;

import java.util.List;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;

/** An extension of the JVMDebugger interface with a few additions to
    support 32-bit vs. 64-bit debugging as well as features required
    by the architecture-specific subpackages. */

public interface Win32Debugger extends JVMDebugger {
  public String       addressValueToString(long address) throws DebuggerException;
  public boolean      readJBoolean(long address) throws DebuggerException;
  public byte         readJByte(long address) throws DebuggerException;
  public char         readJChar(long address) throws DebuggerException;
  public double       readJDouble(long address) throws DebuggerException;
  public float        readJFloat(long address) throws DebuggerException;
  public int          readJInt(long address) throws DebuggerException;
  public long         readJLong(long address) throws DebuggerException;
  public short        readJShort(long address) throws DebuggerException;
  public long         readCInteger(long address, long numBytes, boolean isUnsigned)
    throws DebuggerException;
  public Win32Address readAddress(long address) throws DebuggerException;
  public Win32Address readCompOopAddress(long address) throws DebuggerException;
  public Win32OopHandle readOopHandle(long address) throws DebuggerException;
  public Win32OopHandle readCompOopHandle(long address) throws DebuggerException;
  public void         writeJBoolean(long address, boolean value) throws DebuggerException;
  public void         writeJByte(long address, byte value) throws DebuggerException;
  public void         writeJChar(long address, char value) throws DebuggerException;
  public void         writeJDouble(long address, double value) throws DebuggerException;
  public void         writeJFloat(long address, float value) throws DebuggerException;
  public void         writeJInt(long address, int value) throws DebuggerException;
  public void         writeJLong(long address, long value) throws DebuggerException;
  public void         writeJShort(long address, short value) throws DebuggerException;
  public void         writeCInteger(long address, long numBytes, long value) throws DebuggerException;
  public void         writeAddress(long address, Win32Address value) throws DebuggerException;
  public void         writeOopHandle(long address, Win32OopHandle value) throws DebuggerException;

  // On Windows the int is actually the value of a HANDLE which
  // currently must be read from the target process; that is, the
  // target process must maintain its own thread list, each element of
  // which holds a HANDLE to its underlying OS thread. FIXME: should
  // add access to the OS-level thread list, but there are too many
  // limitations imposed by Windows to usefully do so; see
  // src/os/win32/agent/README-commands.txt, command "duphandle".
  //
  // The returned array of register contents is guaranteed to be in
  // the same order as in the DbxDebugger for Solaris/x86; that is,
  // the indices match those in debugger/x86/X86ThreadContext.java.
  public long[]       getThreadIntegerRegisterSet(int threadHandleValue,
                                                  boolean mustDuplicateHandle) throws DebuggerException;
  // Implmentation of setContext
  public void         setThreadIntegerRegisterSet(int threadHandleValue,
                                                  boolean mustDuplicateHandle,
                                                  long[] contents) throws DebuggerException;

  public Address      newAddress(long value) throws DebuggerException;

  // Routine supporting the ThreadProxy implementation, in particular
  // the ability to get a thread ID from a thread handle via
  // examination of the Thread Information Block. Fetch the LDT entry
  // for a given selector.
  public Win32LDTEntry getThreadSelectorEntry(int threadHandleValue,
                                              boolean mustDuplicateHandle,
                                              int selector) throws DebuggerException;

  // Support for the CDebugger interface. Retrieves the thread list of
  // the target process as a List of ThreadProxy objects.
  public List/*<ThreadProxy>*/ getThreadList() throws DebuggerException;

  // Support for the CDebugger interface. Retrieves a List of the
  // loadobjects in the target process.
  public List/*<LoadObject>*/ getLoadObjectList() throws DebuggerException;

  // Support for the ProcessControl interface
  public void writeBytesToProcess(long startAddress, long numBytes, byte[] data) throws UnmappedAddressException, DebuggerException;
  public void suspend() throws DebuggerException;
  public void resume() throws DebuggerException;
  public boolean isSuspended() throws DebuggerException;
  public void setBreakpoint(Address addr) throws DebuggerException;
  public void clearBreakpoint(Address addr) throws DebuggerException;
  public boolean isBreakpointSet(Address addr) throws DebuggerException;
  // FIXME: do not want to expose complicated data structures (like
  // the DebugEvent) in this interface due to serialization issues
  public DebugEvent debugEventPoll() throws DebuggerException;
  public void debugEventContinue() throws DebuggerException;

  // NOTE: this interface implicitly contains the following methods:
  // From the Debugger interface via JVMDebugger
  //   public void attach(int processID) throws DebuggerException;
  //   public void attach(String executableName, String coreFileName) throws DebuggerException;
  //   public boolean detach();
  //   public Address parseAddress(String addressString) throws NumberFormatException;
  //   public long getAddressValue(Address addr) throws DebuggerException;
  //   public String getOS();
  //   public String getCPU();
  // From the SymbolLookup interface via Debugger and JVMDebugger
  //   public Address lookup(String objectName, String symbol);
  //   public OopHandle lookupOop(String objectName, String symbol);
  // From the JVMDebugger interface
  //   public void configureJavaPrimitiveTypeSizes(long jbooleanSize,
  //                                               long jbyteSize,
  //                                               long jcharSize,
  //                                               long jdoubleSize,
  //                                               long jfloatSize,
  //                                               long jintSize,
  //                                               long jlongSize,
  //                                               long jshortSize);
  // From the ThreadAccess interface via Debugger and JVMDebugger
  //   public ThreadProxy getThreadForIdentifierAddress(Address addr);
}
