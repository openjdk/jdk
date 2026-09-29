/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.debugger;

import sun.jvm.hotspot.oops.Mark;
import sun.jvm.hotspot.runtime.VM;

/** <P> DebuggerBase is a recommended base class for debugger
    implementations. It can use a PageCache to cache data from the
    target process. Note that this class would not be suitable if the
    system were used to reflect upon itself; it would never be safe to
    store the value in an OopHandle in anything but an OopHandle.
    However, it provides a fair amount of code sharing to the current
    dbx and win32 implementations. </P>

    <P> NOTE that much of the code sharing is achieved by having this
    class implement many of the methods in the Win32Debugger and
    DbxDebugger interfaces. </P> */

public abstract class DebuggerBase implements Debugger {

  // May be set lazily, but must be set before calling any of the read
  // routines below
  protected MachineDescription machDesc;
  protected DebuggerUtilities utils;
  // Java primitive type sizes, set during bootstrapping. Do not call
  // any of the Java read routines until these are set up.
  protected long jbooleanSize;
  protected long jbyteSize;
  protected long jcharSize;
  protected long jdoubleSize;
  protected long jfloatSize;
  protected long jintSize;
  protected long jlongSize;
  protected long jshortSize;
  protected boolean javaPrimitiveTypesConfigured;
  // heap data.
  protected long oopSize;
  protected long heapOopSize;
  protected long narrowOopBase;  // heap base for compressed oops.
  protected int  narrowOopShift; // shift to decode compressed oops.
  // class metadata space
  protected long klassPtrSize;
  protected long narrowKlassBase;  // heap base for compressed klass ptrs.
  protected int  narrowKlassShift; // shift to decode compressed klass ptrs.
  // Should be initialized if desired by calling initCache()
  private PageCache cache;
  private long pageSize;

  // State for faster accessors that don't allocate memory on each read
  private boolean useFastAccessors;
  private boolean bigEndian;

  // Page-fetching functionality for LRU cache
  class Fetcher implements PageFetcher {
    public Page fetchPage(long pageBaseAddress, long numBytes) {
      // This assumes that if any byte is unmapped, that the entire
      // page is. The common case, however, is that the page is
      // mapped, so we always fetch the entire thing all at once to
      // avoid two round-trip communications per page fetch, even
      // though fetching of unmapped pages will be slow.
      ReadResult res = readBytesFromProcess(pageBaseAddress, numBytes);
      if (res.getData() == null) {
        return new Page(pageBaseAddress, numBytes);
      }
      return new Page(pageBaseAddress, res.getData());
    }
  }

  protected DebuggerBase() {
  }

  /** From the JVMDebugger interface. This is the only public method
      of this class. */
  public void configureJavaPrimitiveTypeSizes(long jbooleanSize,
                                              long jbyteSize,
                                              long jcharSize,
                                              long jdoubleSize,
                                              long jfloatSize,
                                              long jintSize,
                                              long jlongSize,
                                              long jshortSize) {
    this.jbooleanSize = jbooleanSize;
    this.jbyteSize = jbyteSize;
    this.jcharSize = jcharSize;
    this.jdoubleSize = jdoubleSize;
    this.jfloatSize = jfloatSize;
    this.jintSize = jintSize;
    this.jlongSize = jlongSize;
    this.jshortSize = jshortSize;

    if (jbooleanSize < 1) {
      throw new RuntimeException("jboolean size is too small");
    }

    if (jbyteSize < 1) {
      throw new RuntimeException("jbyte size is too small");
    }

    if (jcharSize < 2) {
      throw new RuntimeException("jchar size is too small");
    }

    if (jdoubleSize < 8) {
      throw new RuntimeException("jdouble size is too small");
    }

    if (jfloatSize < 4) {
      throw new RuntimeException("jfloat size is too small");
    }

    if (jintSize < 4) {
      throw new RuntimeException("jint size is too small");
    }

    if (jlongSize < 8) {
      throw new RuntimeException("jlong size is too small");
    }

    if (jshortSize < 2) {
      throw new RuntimeException("jshort size is too small");
    }

    if (jintSize != jfloatSize) {
      // If dataToJFloat were rewritten, this wouldn't be necessary
      throw new RuntimeException("jint size and jfloat size must be equal");
    }

    if (jlongSize != jdoubleSize) {
      // If dataToJDouble were rewritten, this wouldn't be necessary
      throw new RuntimeException("jlong size and jdouble size must be equal");
    }

    useFastAccessors =
      ((cache != null) &&
       (jbooleanSize == 1) &&
       (jbyteSize    == 1) &&
       (jcharSize    == 2) &&
       (jdoubleSize  == 8) &&
       (jfloatSize   == 4) &&
       (jintSize     == 4) &&
       (jlongSize    == 8) &&
       (jshortSize   == 2));

    javaPrimitiveTypesConfigured = true;
  }

  public void putHeapConst(long heapOopSize, long klassPtrSize, long narrowOopBase, int narrowOopShift,
                           long narrowKlassBase, int narrowKlassShift) {
    this.heapOopSize = heapOopSize;
    this.klassPtrSize = klassPtrSize;
    this.narrowOopBase = narrowOopBase;
    this.narrowOopShift = narrowOopShift;
    this.narrowKlassBase = narrowKlassBase;
    this.narrowKlassShift = narrowKlassShift;
  }

  /** May be called by subclasses if desired to initialize the page
      cache but may not be overridden */
  protected final void initCache(long pageSize, long maxNumPages) {
    cache = new PageCache(pageSize, maxNumPages, new Fetcher());
    this.pageSize = pageSize;
    if (machDesc != null) {
      bigEndian = machDesc.isBigEndian();
    }
  }

  /** May be called by subclasses if needed (if the machine
      description is not available at the time of cache
      initialization, as on Solaris) but may not be overridden */
  protected final void setBigEndian(boolean bigEndian) {
    this.bigEndian = bigEndian;
  }

  /** May be called by subclasses to clear out the cache but may not
      be overridden. For convenience, this can be called even if the
      cache has not been initialized. */
  protected final void clearCache() {
    if (cache != null) {
      cache.clear();
    }
  }

  /** May be called by subclasses to disable the cache (for example,
      when the target process has been resumed) but may not be
      overridden. For convenience, this can be called even if the
      cache has not been initialized. */
  protected final void disableCache() {
    if (cache != null) {
      cache.disable();
    }
  }

  /** May be called by subclasses to re-enable the cache (for example,
      when the target process has been suspended) but may not be
      overridden. For convenience, this can be called even if the
      cache has not been initialized. */
  protected final void enableCache() {
    if (cache != null) {
      cache.enable();
    }
  }

  /** May be called by subclasses directly but may not be overridden */
  protected final byte[] readBytes(long address, long numBytes)
    throws UnmappedAddressException, DebuggerException {
    if (cache != null) {
      return cache.getData(address, numBytes);
    } else {
      ReadResult res = readBytesFromProcess(address, numBytes);
      if (res.getData() != null) {
        return res.getData();
      }
      throw new UnmappedAddressException(res.getFailureAddress());
    }
  }

  /** If an address for a 64-bit value starts on the last 32-bit word of a
      page, then we can't use the page cache to read it because it will cause
      an ArrayIndexOutOfBoundsException when reading past the end of the page. */
  private boolean canUsePageCacheFor64bitRead(long address) {
    long pageMask = ~(pageSize - 1);
    if ((address & pageMask) != ((address + 4) & pageMask)) {
      // This address starts on the last 32-bit word of the page.
      // Cannot use the page cache in that case.
      return false;
    }
    return true;
  }

  public boolean readJBoolean(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jbooleanSize);
    if (useFastAccessors) {
      return (cache.getByte(address) != 0);
    } else {
      byte[] data = readBytes(address, jbooleanSize);
      return utils.dataToJBoolean(data, jbooleanSize);
    }
  }

  public byte readJByte(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jbyteSize);
    if (useFastAccessors) {
      return cache.getByte(address);
    } else {
      byte[] data = readBytes(address, jbyteSize);
      return utils.dataToJByte(data, jbyteSize);
    }
  }

  public char readJChar(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jcharSize);
    if (useFastAccessors) {
      return cache.getChar(address, bigEndian);
    } else {
      byte[] data = readBytes(address, jcharSize);
      return (char) utils.dataToJChar(data, jcharSize);
    }
  }

  public double readJDouble(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jdoubleSize);
    if (useFastAccessors && canUsePageCacheFor64bitRead(address)) {
      return cache.getDouble(address, bigEndian);
    } else {
      byte[] data = readBytes(address, jdoubleSize);
      return utils.dataToJDouble(data, jdoubleSize);
    }
  }

  public float readJFloat(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jfloatSize);
    if (useFastAccessors) {
      return cache.getFloat(address, bigEndian);
    } else {
      byte[] data = readBytes(address, jfloatSize);
      return utils.dataToJFloat(data, jfloatSize);
    }
  }

  public int readJInt(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jintSize);
    if (useFastAccessors) {
      return cache.getInt(address, bigEndian);
    } else {
      byte[] data = readBytes(address, jintSize);
      return utils.dataToJInt(data, jintSize);
    }
  }

  public long readJLong(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jlongSize);
    if (useFastAccessors && canUsePageCacheFor64bitRead(address)) {
      return cache.getLong(address, bigEndian);
    } else {
      byte[] data = readBytes(address, jlongSize);
      return utils.dataToJLong(data, jlongSize);
    }
  }

  public short readJShort(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    checkJavaConfigured();
    utils.checkAlignment(address, jshortSize);
    if (useFastAccessors) {
      return cache.getShort(address, bigEndian);
    } else {
      byte[] data = readBytes(address, jshortSize);
      return utils.dataToJShort(data, jshortSize);
    }
  }

  public long readCInteger(long address, long numBytes, boolean isUnsigned)
    throws UnmappedAddressException, UnalignedAddressException {
    checkConfigured();
    utils.checkAlignment(address, numBytes);
    if (useFastAccessors && (numBytes != 8 || canUsePageCacheFor64bitRead(address))) {
      if (isUnsigned) {
        switch((int) numBytes) {
        case 1: return cache.getByte(address) & 0xFF;
        case 2: return cache.getShort(address, bigEndian) & 0xFFFF;
        case 4: return cache.getInt(address, bigEndian) & 0xFFFFFFFFL;
        case 8: return cache.getLong(address, bigEndian);
        default: {
          byte[] data = readBytes(address, numBytes);
          return utils.dataToCInteger(data, isUnsigned);
        }
        }
      } else {
        switch((int) numBytes) {
        case 1: return cache.getByte(address);
        case 2: return cache.getShort(address, bigEndian);
        case 4: return cache.getInt(address, bigEndian);
        case 8: return cache.getLong(address, bigEndian);
        default: {
          byte[] data = readBytes(address, numBytes);
          return utils.dataToCInteger(data, isUnsigned);
        }
        }
      }
    } else {
      byte[] data = readBytes(address, numBytes);
      return utils.dataToCInteger(data, isUnsigned);
    }
  }

  protected long readAddressValue(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    return readCInteger(address, machDesc.getAddressSize(), true);
  }

  protected long readCompOopAddressValue(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    long value = readCInteger(address, getHeapOopSize(), true);
    if (value != 0) {
      // See oop.inline.hpp decode_heap_oop
      value = (long)(narrowOopBase + (long)(value << narrowOopShift));
    }
    return value;
  }

  protected long readCompKlassAddressValue(long address)
    throws UnmappedAddressException, UnalignedAddressException {
    long value;
    if (VM.getVM().isCompactObjectHeadersEnabled()) {
      // With compact headers, the compressed Klass* is currently read from the mark
      // word. We need to load the whole mark, and shift the upper parts.
      value = readCInteger(address, machDesc.getAddressSize(), true);
      value = value >>> Mark.getKlassShift();
    } else {
      value = readCInteger(address, getKlassPtrSize(), true);
    }
    if (value != 0) {
      value = (long)(narrowKlassBase + (long)(value << narrowKlassShift));
    }
    return value;
  }

  /** Can be called by subclasses but can not be overridden */
  protected final void checkConfigured() {
    if (machDesc == null) {
      throw new RuntimeException("MachineDescription must have been set by this point");
    }
    if (utils == null) {
      throw new RuntimeException("DebuggerUtilities must have been set by this point");
    }
  }

  /** Can be called by subclasses but can not be overridden */
  protected final void checkJavaConfigured() {
    checkConfigured();

    if (!javaPrimitiveTypesConfigured) {
      throw new RuntimeException("Java primitive type sizes have not yet been configured");
    }
  }

  /** Possibly override page cache size with user-specified property */
  protected int parseCacheNumPagesProperty(int defaultNum) {
    String cacheNumPagesString = System.getProperty("cacheNumPages");
    if (cacheNumPagesString != null) {
      try {
        return Integer.parseInt(cacheNumPagesString);
      } catch (Exception e) {
        System.err.println("Error parsing cacheNumPages property:");
        e.printStackTrace();
      }
    }
    return defaultNum;
  }

  /** Interim solution for allowing subclasses to write bytes to
      process until we make that functionality available in the basic
      Address interface */
  protected void invalidatePageCache(long startAddress, long numBytes) {
    cache.clear(startAddress, numBytes);
  }

  @Override
  public String findSymbol(String symbol) {
    Address addr = lookup(null, symbol);
    if (addr == null && getOS().equals("win32")) {
      // On win32 symbols are prefixed with the dll name. Do the user
      // a favor and see if this is a symbol in jvm.dll or java.dll.
      addr = lookup(null, "jvm!" + symbol);
      if (addr == null) {
        addr = lookup(null, "java!" + symbol);
      }
    }
    if (addr == null) {
      return null;
    }
    var builder = new StringBuilder(addr.toString());
    var cdbg = getCDebugger();
    var loadObject = cdbg.loadObjectContainingPC(addr);
    // Print the shared library path and the offset of the symbol
    if (loadObject != null) {
      builder.append(": ").append(loadObject.getName());
      long diff = addr.minus(loadObject.getBase());
      if (diff != 0L) {
        builder.append(" + 0x").append(Long.toHexString(diff));
      }
    }
    return builder.toString();
  }

  public long getJBooleanSize() {
    return jbooleanSize;
  }

  public long getJByteSize() {
    return jbyteSize;
  }

  public long getJCharSize() {
    return jcharSize;
  }

  public long getJDoubleSize() {
    return jdoubleSize;
  }

  public long getJFloatSize() {
    return jfloatSize;
  }

  public long getJIntSize() {
    return jintSize;
  }

  public long getJLongSize() {
    return jlongSize;
  }

  public long getJShortSize() {
    return jshortSize;
  }

  public long getHeapOopSize() {
    return heapOopSize;
  }

  public long getNarrowOopBase() {
    return narrowOopBase;
  }
  public int getNarrowOopShift() {
    return narrowOopShift;
  }

  public long getKlassPtrSize() {
    return klassPtrSize;
  }

  public long getNarrowKlassBase() {
    return narrowKlassBase;
  }
  public int getNarrowKlassShift() {
    return narrowKlassShift;
  }
}
