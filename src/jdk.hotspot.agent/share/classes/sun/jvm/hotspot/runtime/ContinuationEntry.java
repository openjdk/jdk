/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2026, NTT DATA.
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

package sun.jvm.hotspot.runtime;

import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.aarch64.*;
import sun.jvm.hotspot.runtime.amd64.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.utilities.*;


public abstract class ContinuationEntry extends VMObject {
    private static long size;
    private static AddressField parentField;
    private static Address returnPC;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
        Type type = db.lookupType("ContinuationEntry");
        size = type.getSize();
        parentField = type.getAddressField("_parent");
        returnPC = type.getAddressField("_return_pc").getValue();
    }

    public static ContinuationEntry create(Address addr) {
        return switch (VM.getVM().getDebugger().getCPU()) {
            case "amd64"   -> VMObjectFactory.newObject(AMD64ContinuationEntry.class, addr);
            case "aarch64" -> VMObjectFactory.newObject(AARCH64ContinuationEntry.class, addr);
            default -> throw new UnsupportedPlatformException("Continuation is not yet implemented.");
        };
    }

    public ContinuationEntry(Address addr) {
        super(addr);
    }

    public ContinuationEntry getParent() {
        return VMObjectFactory.newObject(ContinuationEntry.class, parentField.getValue(addr));
    }

    public Address getEntryPC() {
        return returnPC;
    }

    public Address getEntrySP(){
        return this.getAddress();
    }

    public Address getEntryFP(){
        return this.getAddress().addOffsetTo(size);
    }

    public abstract Frame toFrame();

}
