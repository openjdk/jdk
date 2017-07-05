/*
 * Copyright (c) 2001, 2003, Oracle and/or its affiliates. All rights reserved.
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
package sun.jvm.hotspot.debugger.posix;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.debugger.cdbg.*;
import sun.jvm.hotspot.debugger.posix.elf.*;
import sun.jvm.hotspot.utilities.memo.*;

/** Provides a simple wrapper around the ELF library which handles
    relocation. */
public abstract class DSO implements LoadObject {

    private MemoizedObject file;  // contains ELFFile
    private String         filename;
    private Address        addr;
    private long           size;
    private IsDSO          dso = new IsDSO();

    class IsDSO extends MemoizedBoolean {
        protected boolean computeValue() {
           return getFile().getHeader().getFileType() == ELFHeader.FT_DYN;
        }
    };

    class ELFFileByName extends MemoizedObject {
        protected Object computeValue() {
           return ELFFileParser.getParser().parse(DSO.this.filename);
        }
    };

    class ELFFileByAddress extends MemoizedObject {
        protected Object computeValue() {
           return ELFFileParser.getParser().parse(new AddressDataSource(DSO.this.addr));
        }
    };

    public DSO(String filename, long size, Address relocation) throws ELFException {
        this.filename = filename;
        this.size = size;
        this.addr = relocation;
        this.file = new ELFFileByName();
    }

    public DSO(long size, Address relocation) throws ELFException {
        this.addr = relocation;
        this.size = size;
        this.file = new ELFFileByAddress();
    }

    public String getName() {
        return filename;
    }

    public Address getBase() {
        return addr;
    }

    /** if this .so is unloaded and re-loaded in the same process at a different
        base, change the base by calling this to avoid re-parsing the ELF. */
    public void setBase(Address newBase) {
        addr = newBase;
        if (filename == null) {
            // ELFFile was created by address. we have to re-parse it.
            file = new ELFFileByAddress();
            dso  = new IsDSO();
        }
    }

    public long getSize() {
        return size;
    }

    public CDebugInfoDataBase getDebugInfoDataBase() throws DebuggerException {
        // FIXME: after stabs parser
        return null;
    }

    public BlockSym debugInfoForPC(Address pc) throws DebuggerException  {
        // FIXME: after stabs parser
        return null;
    }

    public ClosestSymbol closestSymbolToPC(Address pcAsAddr) throws DebuggerException {
        boolean dso = isDSO();
        long offset = dso? pcAsAddr.minus(addr) : getAddressValue(pcAsAddr);
        ELFSymbol sym = getFile().getHeader().getELFSymbol(offset);
        return (sym != null)? createClosestSymbol(sym.getName(), offset - sym.getValue()) : null;
    }

    public LineNumberInfo lineNumberForPC(Address pc) throws DebuggerException {
        // FIXME: after stabs parser
        return null;
    }

    /** return true if file is a .so */
    public boolean isDSO() {
        return dso.getValue();
    }

    /** Look up a symbol; returns absolute address or null if symbol was
        not found. */
    public Address lookupSymbol(String symbol) throws ELFException {
        ELFSymbol sym = getFile().getHeader().getELFSymbol(symbol);
        if (sym == null) {
           return null;
        }

        long value = sym.getValue();
        if (isDSO()) {
           return addr.addOffsetTo(value);
        } else {
           return newAddress(value);
        }
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof DSO)) {
           return false;
        }
        DSO other = (DSO)o;
        return getBase().equals(other.getBase());
    }

    public int hashCode() {
        return getBase().hashCode();
    }

    protected ELFFile getFile() {
        return (ELFFile) file.getValue();
    }

    protected abstract Address newAddress(long addr);
    protected abstract long getAddressValue(Address addr);

    protected ClosestSymbol createClosestSymbol(String name, long diff) {
        return new ClosestSymbol(name, diff);
    }
}
