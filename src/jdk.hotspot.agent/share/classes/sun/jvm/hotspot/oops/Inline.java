/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA.
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

import java.io.PrintStream;

import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.oops.ObjectHeap;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.types.WrongTypeException;


public class Inline extends Instance {

    private final InlineKlass klass;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) throws WrongTypeException {
        // TODO
        //Type type = db.lookupType("inlineOopDesc");
    }

    Inline(OopHandle handle, ObjectHeap heap, InlineKlass klass) {
        super(handle, heap);
        this.klass = klass;
    }

    Inline(OopHandle handle, ObjectHeap heap) {
        this(handle, heap, null);
    }

    @Override
    public boolean isInline() {
        return true;
    }

    public boolean isFlattened() {
        return klass != null;
    }

    @Override
    public Klass getKlass() {
        return isFlattened() ? klass : super.getKlass();
    }

    @Override
    public void iterateFields(OopVisitor visitor, boolean doVMFields) {
        if (isFlattened()) {
            ((InlineKlass)getKlass()).iterateNonStaticFields(visitor, this);
        } else {
            super.iterateFields(visitor, doVMFields);
        }
    }

    @Override
    public void printValueOn(PrintStream tty) {
        tty.print("Inlined object");
    }
}
