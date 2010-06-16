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

package sun.jvm.hotspot.debugger.dbx;

import java.io.*;
import java.net.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.dbx.sparc.*;
import sun.jvm.hotspot.debugger.dbx.x86.*;
import sun.jvm.hotspot.debugger.cdbg.CDebugger;
import sun.jvm.hotspot.utilities.*;

/** <P> An implementation of the JVMDebugger interface which sits on
    top of dbx and relies on the SA's dbx import module for
    communication with the debugger. </P>

    <P> <B>NOTE</B> that since we have the notion of fetching "Java
    primitive types" from the remote process (which might have
    different sizes than we expect) we have a bootstrapping
    problem. We need to know the sizes of these types before we can
    fetch them. The current implementation solves this problem by
    requiring that it be configured with these type sizes before they
    can be fetched. The readJ(Type) routines here will throw a
    RuntimeException if they are called before the debugger is
    configured with the Java primitive type sizes. </P>
*/

public class DbxDebuggerLocal extends DebuggerBase implements DbxDebugger {
  // These may be set by DbxDebuggerRemote
  protected boolean unalignedAccessesOkay;
  protected DbxThreadFactory threadFactory;

  private String dbxPathName;
  private String[] dbxSvcAgentDSOPathNames;
  private Process dbxProcess;
  private StreamMonitor dbxOutStreamMonitor;
  private StreamMonitor dbxErrStreamMonitor;
  private PrintWriter dbxOstr;
  private PrintWriter out;
  private InputLexer in;
  private Socket importModuleSocket;
  private static final int PORT = 21928;
  private static final int  LONG_TIMEOUT = 60000;
  private static final int  DBX_MODULE_NOT_FOUND      = 101;
  private static final int  DBX_MODULE_LOADED         = 102;

  //--------------------------------------------------------------------------------
  // Implementation of Debugger interface
  //

  /** <P> machDesc may be null if it couldn't be determined yet; i.e.,
      if we're on SPARC, we need to ask the remote process whether
      we're in 32- or 64-bit mode. </P>

      <P> useCache should be set to true if debugging is being done
      locally, and to false if the debugger is being created for the
      purpose of supporting remote debugging. </P> */
  public DbxDebuggerLocal(MachineDescription machDesc,
                          String dbxPathName,
                          String[] dbxSvcAgentDSOPathNames,
                          boolean useCache) {
    this.machDesc = machDesc;
    this.dbxPathName = dbxPathName;
    this.dbxSvcAgentDSOPathNames = dbxSvcAgentDSOPathNames;
    int cacheNumPages;
    int cachePageSize;
    if (PlatformInfo.getCPU().equals("sparc")) {
      cacheNumPages = parseCacheNumPagesProperty(2048);
      cachePageSize = 8192;
      threadFactory = new DbxSPARCThreadFactory(this);
    } else if (PlatformInfo.getCPU().equals("x86")) {
      cacheNumPages = 4096;
      cachePageSize = 4096;
      threadFactory = new DbxX86ThreadFactory(this);
      unalignedAccessesOkay = true;
    } else {
      throw new RuntimeException("Thread access for CPU architecture " + PlatformInfo.getCPU() + " not yet supported");
    }
    if (useCache) {
      // Cache portion of the remote process's address space.
      // Fetching data over the socket connection to dbx is relatively
      // slow. For now, this cache works best if it covers the entire
      // heap of the remote process. FIXME: at least should make this
      // tunable from the outside, i.e., via the UI. This is a 16 MB
      // cache divided on SPARC into 2048 8K pages and on x86 into
      // 4096 4K pages; the page size must be adjusted to be the OS's
      // page size. (FIXME: should pick this up from the debugger.)
      initCache(cachePageSize, cacheNumPages);
    }
  }

  /** Only called by DbxDebuggerRemote */
  protected DbxDebuggerLocal() {
  }

  /** FIXME: implement this with a Runtime.exec() of ps followed by
      parsing of its output */
  public boolean hasProcessList() throws DebuggerException {
    return false;
  }

  public List getProcessList() throws DebuggerException {
    throw new DebuggerException("Not yet supported");
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized void attach(int processID) throws DebuggerException {
    try {
      launchProcess();
      dbxErrStreamMonitor.addTrigger("dbx: no process", 1);
      dbxErrStreamMonitor.addTrigger("dbx: Cannot open", 1);
      dbxErrStreamMonitor.addTrigger("dbx: Cannot find", DBX_MODULE_NOT_FOUND);
      dbxOstr = new PrintWriter(dbxProcess.getOutputStream(), true);
      dbxOstr.println("debug - " + processID);
      dbxOstr.println("kprint -u2 \\(ready\\)");
      boolean seen = dbxErrStreamMonitor.waitFor("(ready)", LONG_TIMEOUT);
      if (!seen) {
        detach();
        throw new DebuggerException("Timed out while connecting to process " + processID);
      }
      List retVals = dbxErrStreamMonitor.getTriggersSeen();
      if (retVals.contains(new Integer(1))) {
        detach();
        throw new DebuggerException("No such process " + processID);
      }

      // Throws DebuggerException upon failure
      importDbxModule();

      dbxOstr.println("svc_agent_run");

      connectToImportModule();

      // Set "fail fast" mode on process memory reads
      printlnToOutput("peek_fail_fast 1");
    }
    catch (IOException e) {
      detach();
      throw new DebuggerException("Error while connecting to dbx process", e);
    }
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized void attach(String executableName, String coreFileName) throws DebuggerException {
    try {
      launchProcess();
      // Missing executable
      dbxErrStreamMonitor.addTrigger("dbx: Cannot open", 1);
      // Missing core file
      dbxErrStreamMonitor.addTrigger("dbx: can't read", 2);
      // Corrupt executable
      dbxErrStreamMonitor.addTrigger("dbx: File", 3);
      // Corrupt core file
      dbxErrStreamMonitor.addTrigger("dbx: Unable to read", 4);
      // Mismatched core and executable
      dbxErrStreamMonitor.addTrigger("dbx: core object name", 5);
      // Missing loadobject
      dbxErrStreamMonitor.addTrigger("dbx: can't stat", 6);
      // Successful load of svc module
      dbxOstr = new PrintWriter(dbxProcess.getOutputStream(), true);
      dbxOstr.println("debug " + executableName + " " + coreFileName);
      dbxOstr.println("kprint -u2 \\(ready\\)");
      boolean seen = dbxErrStreamMonitor.waitFor("(ready)", LONG_TIMEOUT);
      if (!seen) {
        detach();
        throw new DebuggerException("Timed out while attaching to core file");
      }
      List retVals = dbxErrStreamMonitor.getTriggersSeen();
      if (retVals.size() > 0) {
        detach();

        if (retVals.contains(new Integer(1))) {
          throw new DebuggerException("Can not find executable \"" + executableName + "\"");
        } else if (retVals.contains(new Integer(2))) {
          throw new DebuggerException("Can not find core file \"" + coreFileName + "\"");
        } else if (retVals.contains(new Integer(3))) {
          throw new DebuggerException("Corrupt executable \"" + executableName + "\"");
        } else if (retVals.contains(new Integer(4))) {
          throw new DebuggerException("Corrupt core file \"" + coreFileName + "\"");
        } else if (retVals.contains(new Integer(5))) {
          throw new DebuggerException("Mismatched core file/executable \"" + coreFileName + "\"/\"" + executableName + "\"");
        } else {
          throw new DebuggerException("Couldn't find all loaded libraries for executable \"" + executableName + "\"");
        }
      }

      // Throws DebuggerException upon failure
      importDbxModule();

      dbxOstr.println("svc_agent_run");

      connectToImportModule();

      // Set "fail fast" mode on process memory reads
      printlnToOutput("peek_fail_fast 1");
    }
    catch (IOException e) {
      detach();
      throw new DebuggerException("Error while connecting to dbx process", e);
    }
  }

  /** From the Debugger interface via JVMDebugger */
  public synchronized boolean detach() {
    try {
      if (dbxProcess == null) {
        return false;
      }

      if (out != null && dbxOstr != null) {
        printlnToOutput("exit");
        dbxOstr.println("exit");

        // Wait briefly for the process to exit (FIXME: should make this
        // nicer)
        try {
          Thread.sleep(500);
        }
        catch (InterruptedException e) {
        }
      }

      shutdown();

      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  /** From the Debugger interface via JVMDebugger */
  public Address parseAddress(String addressString) throws NumberFormatException {
    long addr = utils.scanAddress(addressString);
    if (addr == 0) {
      return null;
    }
    return new DbxAddress(this, addr);
  }

  /** From the Debugger interface via JVMDebugger */
  public String getOS() {
    return PlatformInfo.getOS();
  }

  /** From the Debugger interface via JVMDebugger */
  public String getCPU() {
    return PlatformInfo.getCPU();
  }

  public boolean hasConsole() throws DebuggerException {
    return true;
  }

  public synchronized String consoleExecuteCommand(String cmd) throws DebuggerException {
    try {
      // A little tricky. We need to cause the dbx import module to
      // exit, then print our command on dbx's stdin along with a
      // command which will allow our StreamMonitors to
      // resynchronize. We need save the output from the StreamMonitors
      // along the way.
      printlnToOutput("exit");
      importModuleSocket.close();
      importModuleSocket = null;
      out = null;
      in = null;
      dbxOstr.println("kprint \\(ready\\)");
      dbxOstr.flush();
      dbxOutStreamMonitor.waitFor("(ready)", LONG_TIMEOUT);

      dbxOutStreamMonitor.startCapture();
      dbxErrStreamMonitor.startCapture();
      dbxOstr.println(cmd);
      dbxOstr.println("kprint \\(ready\\)");
      dbxOutStreamMonitor.waitFor("(ready)", LONG_TIMEOUT);
      String result = dbxOutStreamMonitor.stopCapture();
      String result2 = dbxErrStreamMonitor.stopCapture();
      result = result + result2;
      // Cut out the "(ready)" string
      StringBuffer outBuf = new StringBuffer(result.length());
      BufferedReader reader = new BufferedReader(new StringReader(result));
      // FIXME: bug in BufferedReader? readLine returns null when
      // ready() returns true.
      String line = null;
      do {
        line = reader.readLine();
        if ((line != null) && (!line.equals("(ready)"))) {
          outBuf.append(line);
          outBuf.append("\n");
        }
      } while (line != null);
      dbxOstr.println("svc_agent_run");
      dbxOstr.flush();

      connectToImportModule();

      return outBuf.toString();
    }
    catch (IOException e) {
      detach();
      throw new DebuggerException("Error while executing command on dbx console", e);
    }
  }

  public String getConsolePrompt() throws DebuggerException {
    return "(dbx) ";
  }

  public CDebugger getCDebugger() throws DebuggerException {
    return null;
  }

  /** From the SymbolLookup interface via Debugger and JVMDebugger */
  public synchronized Address lookup(String objectName, String symbol) {
    long addr = lookupInProcess(objectName, symbol);
    if (addr == 0) {
      return null;
    }
    return new DbxAddress(this, addr);
  }

  /** From the SymbolLookup interface via Debugger and JVMDebugger */
  public synchronized OopHandle lookupOop(String objectName, String symbol) {
    long addr = lookupInProcess(objectName, symbol);
    if (addr == 0) {
      return null;
    }
    return new DbxOopHandle(this, addr);
  }

  /** From the Debugger interface */
  public MachineDescription getMachineDescription() {
    return machDesc;
  }

  /** Internal routine supporting lazy setting of MachineDescription,
      since on SPARC we will need to query the remote process to ask
      it what its data model is (32- or 64-bit). NOTE that this is NOT
      present in the DbxDebugger interface because it should not be
      called across the wire (until we support attaching to multiple
      remote processes via RMI -- see the documentation for
      DbxDebuggerRemoteIntf.) */
  public void setMachineDescription(MachineDescription machDesc) {
    this.machDesc = machDesc;
    setBigEndian(machDesc.isBigEndian());
    utils = new DebuggerUtilities(machDesc.getAddressSize(), machDesc.isBigEndian());
  }

  /** Internal routine which queries the remote process about its data
      model -- i.e., size of addresses. Returns -1 upon error.
      Currently supported return values are 32 and 64. NOTE that this
      is NOT present in the DbxDebugger interface because it should
      not be called across the wire (until we support attaching to
      multiple remote processes via RMI -- see the documentation for
      DbxDebuggerRemoteIntf.) */
  public int getRemoteProcessAddressSize() {
    if (dbxProcess == null) {
      throw new RuntimeException("Not attached to remote process");
    }

    try {
      printlnToOutput("address_size");
      int i = in.parseInt();
      return i;
    }
    catch (IOException e) {
      return -1;
    }
  }

  //--------------------------------------------------------------------------------
  // Implementation of ThreadAccess interface
  //

  /** From the ThreadAccess interface via Debugger and JVMDebugger */
  public ThreadProxy getThreadForIdentifierAddress(Address addr) {
    return threadFactory.createThreadWrapper(addr);
  }

  public ThreadProxy getThreadForThreadId(long id) {
    return threadFactory.createThreadWrapper(id);
  }

  //----------------------------------------------------------------------
  // Overridden from DebuggerBase because we need to relax alignment
  // constraints on x86

  public long readJLong(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    // FIXME: allow this to be configurable. Undesirable to add a
    // dependency on the runtime package here, though, since this
    // package should be strictly underneath it.
    if (unalignedAccessesOkay) {
      utils.checkAlignment(address, jintSize);
    } else {
      utils.checkAlignment(address, jlongSize);
    }
    byte[] data = readBytes(address, jlongSize);
    return utils.dataToJLong(data, jlongSize);
  }

  //--------------------------------------------------------------------------------
  // Internal routines (for implementation of DbxAddress).
  // These must not be called until the MachineDescription has been set up.
  //

  /** From the DbxDebugger interface */
  public String addressValueToString(long address) {
    return utils.addressValueToString(address);
  }

  /** Need to override this to relax alignment checks on Solaris/x86. */
  public long readCInteger(long address, long numBytes, boolean isUnsigned)
    throws UnmappedAddressException, UnalignedAddressException {
    checkConfigured();
    if (!unalignedAccessesOkay) {
      utils.checkAlignment(address, numBytes);
    } else {
      // Only slightly relaxed semantics -- this is a hack, but is
      // necessary on Solaris/x86 where it seems the compiler is
      // putting some global 64-bit data on 32-bit boundaries
      if (numBytes == 8) {
        utils.checkAlignment(address, 4);
      } else {
        utils.checkAlignment(address, numBytes);
      }
    }
    byte[] data = readBytes(address, numBytes);
    return utils.dataToCInteger(data, isUnsigned);
  }

  /** From the DbxDebugger interface */
  public DbxAddress readAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    long value = readAddressValue(address);
    return (value == 0 ? null : new DbxAddress(this, value));
  }

  public DbxAddress readCompOopAddress(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    long value = readCompOopAddressValue(address);
    return (value == 0 ? null : new DbxAddress(this, value));
  }

  /** From the DbxDebugger interface */
  public DbxOopHandle readOopHandle(long address)
    throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
    long value = readAddressValue(address);
    return (value == 0 ? null : new DbxOopHandle(this, value));
  }
  public DbxOopHandle readCompOopHandle(long address)
    throws UnmappedAddressException, UnalignedAddressException, NotInHeapException {
    long value = readCompOopAddressValue(address);
    return (value == 0 ? null : new DbxOopHandle(this, value));
  }

  //--------------------------------------------------------------------------------
  // Thread context access. Can not be package private, but should
  // only be accessed by the architecture-specific subpackages.

  /** From the DbxDebugger interface. May have to redefine this later. */
  public synchronized long[] getThreadIntegerRegisterSet(int tid) {
    try {
      printlnToOutput("thr_gregs " + tid);
      int num = in.parseInt();
      long[] res = new long[num];
      for (int i = 0; i < num; i++) {
        res[i] = in.parseAddress();
      }
      return res;
    }
    catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  //--------------------------------------------------------------------------------
  // Address access. Can not be package private, but should only be
  // accessed by the architecture-specific subpackages.

  /** From the Debugger interface */
  public long getAddressValue(Address addr) {
    if (addr == null) return 0;
    return ((DbxAddress) addr).getValue();
  }

  /** From the DbxDebugger interface */
  public Address newAddress(long value) {
    if (value == 0) return null;
    return new DbxAddress(this, value);
  }

  //--------------------------------------------------------------------------------
  // Internals only below this point
  //

  private void launchProcess() throws IOException {
    dbxProcess = Runtime.getRuntime().exec(dbxPathName);
    //      dbxOutStreamMonitor = new StreamMonitor(dbxProcess.getInputStream());
    //      dbxErrStreamMonitor = new StreamMonitor(dbxProcess.getErrorStream());
    dbxOutStreamMonitor = new StreamMonitor(dbxProcess.getInputStream(), "dbx stdout", true);
    dbxErrStreamMonitor = new StreamMonitor(dbxProcess.getErrorStream(), "dbx stderr", true);
  }

  /** Requires that dbxErrStreamMonitor has a trigger on "dbx: Cannot
      find" with number DBX_MODULE_NOT_FOUND as well as one on "dbx:
      warning:" (plus the serviceability agent's dbx module path name,
      to avoid conflation with inability to load individual object
      files) with number DBX_MODULE_FAILED_TO_LOAD. The former
      indicates an absence of libsvc_agent_dbx.so, while the latter
      indicates that the module failed to load, specifically because
      the architecture was mismatched. (I don't see a way to detect
      from the dbx command prompt whether it's running the v8 or v9
      executbale, so we try to import both flavors of the import
      module; the "v8" file name convention doesn't actually include
      the v8 prefix, so this code should work for Intel as well.) */
  private void importDbxModule() throws DebuggerException {
    // Trigger for a successful load
    dbxOutStreamMonitor.addTrigger("Defining svc_agent_run", DBX_MODULE_LOADED);
    for (int i = 0; i < dbxSvcAgentDSOPathNames.length; i++) {
      dbxOstr.println("import " + dbxSvcAgentDSOPathNames[i]);
      dbxOstr.println("kprint -u2 \\(Ready\\)");
      boolean seen = dbxErrStreamMonitor.waitFor("(Ready)", LONG_TIMEOUT);
      if (!seen) {
        detach();
        throw new DebuggerException("Timed out while importing dbx module from file\n" + dbxSvcAgentDSOPathNames[i]);
      }
      List retVals = dbxErrStreamMonitor.getTriggersSeen();
      if (retVals.contains(new Integer(DBX_MODULE_NOT_FOUND))) {
        detach();
        throw new DebuggerException("Unable to find the Serviceability Agent's dbx import module at pathname \"" +
                                    dbxSvcAgentDSOPathNames[i] + "\"");
      } else {
        retVals = dbxOutStreamMonitor.getTriggersSeen();
        if (retVals.contains(new Integer(DBX_MODULE_LOADED))) {
          System.out.println("importDbxModule: imported " +  dbxSvcAgentDSOPathNames[i]);
          return;
        }
      }
    }

    // Failed to load all flavors
    detach();
    String errMsg = ("Unable to find a version of the Serviceability Agent's dbx import module\n" +
                     "matching the architecture of dbx at any of the following locations:");
    for (int i = 0; i < dbxSvcAgentDSOPathNames.length; i++) {
      errMsg = errMsg + "\n" + dbxSvcAgentDSOPathNames[i];
    }
    throw new DebuggerException(errMsg);
  }

  /** Terminate the debugger forcibly */
  private void shutdown() {

    if (dbxProcess != null) {
      // See whether the process has exited and, if not, terminate it
      // forcibly
      try {
        dbxProcess.exitValue();
      }
      catch (IllegalThreadStateException e) {
        dbxProcess.destroy();
      }
    }

    try {
      if (importModuleSocket != null) {
        importModuleSocket.close();
      }
    }
    catch (IOException e) {
    }

    // Release references to all objects
    clear();
    clearCache();
  }

  /** Looks up an address in the remote process's address space.
      Returns 0 if symbol not found or upon error. Package private to
      allow DbxDebuggerRemoteIntfImpl access. */
  synchronized long lookupInProcess(String objectName, String symbol) {
    try {
      printlnToOutput("lookup " + objectName + " " + symbol);
      return in.parseAddress();
    }
    catch (Exception e) {
      return 0;
    }
  }

  /** This reads bytes from the remote process. */
  public synchronized ReadResult readBytesFromProcess(long address, long numBytes)
    throws DebuggerException {
    if (numBytes < 0) {
      throw new DebuggerException("Can not read negative number (" + numBytes + ") of bytes from process");
    }
    try {
      String cmd = "peek " + utils.addressValueToString(address) + " " + numBytes;
      printlnToOutput(cmd);
      while (in.readByte() != 'B') {
      }
      byte res = in.readByte();
      if (res == 0) {
        System.err.println("Failing command: " + cmd);
        throw new DebuggerException("Read of remote process address space failed");
      }
      // NOTE: must read ALL of the data regardless of whether we need
      // to throw an UnmappedAddressException. Otherwise will corrupt
      // the input stream each time we have a failure. Not good. Do
      // not want to risk "flushing" the input stream in case a huge
      // read has a hangup in the middle and we leave data on the
      // stream.
      byte[] buf = new byte[(int) numBytes];
      boolean bailOut = false;
      long failureAddress = 0;
      int numReads = 0;
      while (numBytes > 0) {
        long len = in.readUnsignedInt();
        boolean isMapped = ((in.readByte() == 0) ? false : true);
        if (!isMapped) {
          if (!bailOut) {
            bailOut = true;
            failureAddress = address;
          }
        } else {
          // This won't work if we have unmapped regions, but if we do
          // then we're going to throw an exception anyway

          // NOTE: there is a factor of 20 speed difference between
          // these two ways of doing this read.
          in.readBytes(buf, 0, (int) len);
        }

        // Do NOT do this:
        //        for (int i = 0; i < (int) len; i++) {
        //          buf[i] = in.readByte();
        //        }

        numBytes -= len;
        address += len;
        ++numReads;
      }
      if (Assert.ASSERTS_ENABLED) {
        Assert.that(numBytes == 0, "Bug in debug server's implementation of peek: numBytesLeft == " +
                    numBytes + ", should be 0 (did " + numReads + " reads)");
      }
      if (bailOut) {
        return new ReadResult(failureAddress);
      }
      return new ReadResult(buf);
    }
    catch (IOException e) {
      throw new DebuggerException(e);
    }
  }

  public void writeBytesToProcess(long address, long numBytes, byte[] data)
    throws UnmappedAddressException, DebuggerException {
    // FIXME
    throw new DebuggerException("Unimplemented");
  }

  /** This provides DbxDebuggerRemoteIntfImpl access to readBytesFromProcess */
  ReadResult readBytesFromProcessInternal(long address, long numBytes)
    throws DebuggerException {
    return readBytesFromProcess(address, numBytes);
  }

  /** Convenience routine */
  private void printlnToOutput(String s) throws IOException {
    out.println(s);
    if (out.checkError()) {
      throw new IOException("Error occurred while writing to debug server");
    }
  }

  private void clear() {
    dbxProcess = null;
    dbxOstr = null;
    out = null;
    in = null;
    importModuleSocket = null;
  }

  /** Connects to the dbx import module, setting up out and in
      streams. Factored out to allow access to the dbx console. */
  private void connectToImportModule() throws IOException {
    // Try for 20 seconds to connect to dbx import module; time out
    // with failure if didn't succeed
    importModuleSocket = null;
    long endTime = System.currentTimeMillis() + LONG_TIMEOUT;

    while ((importModuleSocket == null) && (System.currentTimeMillis() < endTime)) {
      try {
        importModuleSocket = new Socket(InetAddress.getLocalHost(), PORT);
        importModuleSocket.setTcpNoDelay(true);
      }
      catch (IOException e) {
        // Swallow IO exceptions while attempting connection
        try {
          // Don't swamp the CPU
          Thread.sleep(1000);
        }
        catch (InterruptedException ex) {
        }
      }
    }

    if (importModuleSocket == null) {
      // Failed to connect because of timeout
      detach();
      throw new DebuggerException("Timed out while attempting to connect to remote dbx process");
    }

    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(importModuleSocket.getOutputStream(), "US-ASCII")), true);
    in = new InputLexer(new BufferedInputStream(importModuleSocket.getInputStream()));
  }
}
