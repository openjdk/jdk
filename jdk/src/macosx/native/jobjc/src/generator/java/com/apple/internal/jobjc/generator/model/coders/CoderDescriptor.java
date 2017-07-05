/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */
package com.apple.internal.jobjc.generator.model.coders;

import com.apple.internal.jobjc.generator.model.types.JType.JStruct;
import com.apple.jobjc.Coder;
import com.apple.jobjc.Coder.IDCoder;
import com.apple.jobjc.Coder.NSClassCoder;
import com.apple.jobjc.Coder.PointerCoder;
import com.apple.jobjc.Coder.PrimitivePointerCoder;
import com.apple.jobjc.Coder.SELCoder;
import com.apple.jobjc.Coder.UnknownCoder;
import com.apple.jobjc.Coder.VoidCoder;

public class CoderDescriptor {
    public static final CoderDescriptor VOID_DESC = new CoderDescriptor(VoidCoder.INST, null, null);

    final Coder coder;
    private final String coderInstanceName;
    final String pushName;
    final String popName;

    public String mismatchMessage(){ return null; }

    public CoderDescriptor(final Coder coder, final String pushName, final String popName) {
        this.coder = coder;
        this.coderInstanceName = coder.getClass().getCanonicalName() + ".INST";
        if (coderInstanceName == null) throw new NullPointerException();

        this.pushName = pushName;
        this.popName = popName;
    }

    public CoderDescriptor(final String pushName, final String popName) {
        this.coder = null;
        this.coderInstanceName = null;
        this.pushName = pushName;
        this.popName = popName;
    }

    public CoderDescriptor(final Coder coder) { this(coder, "push", "pop"); }
    public CoderDescriptor(){ this("push", "pop"); }

    public String getPopStatementFor(final String contextName, final String returnValueType, final String returnValueName, final String transform) {
        return "final " + returnValueType + " " + returnValueName + " = " + "(" + returnValueType + ") " + (transform == null ? "" : transform)
        + "(" + getCoderInstanceName() + "." + popName + "(" + contextName + "));";
    }

    public final String getPushStatementFor(final String contextName, final String argumentName) {
        return getCoderInstanceName() + "." + pushName + "(" + contextName + ", " + argumentName + ");";
    }

    public String getPopAddrStatementFor(final String runtime, final String addr, final String returnValueType, final String returnValueName, final String transform) {
        return "final " + returnValueType + " " + returnValueName + " = " + "(" + returnValueType + ") " + (transform == null ? "" : transform)
        + "(" + getCoderInstanceName() + "." + popName + "(" + runtime + ", " + addr + "));";
    }

    public final String getPushAddrStatementFor(final String runtime, final String addr, final String argumentName) {
        return getCoderInstanceName() + "." + pushName + "(" + runtime + ", " + addr + ", " + argumentName + ");";
    }

    public String getCoderInstanceName() { return coderInstanceName; }
    public Coder getCoder() { return coder; }
    public String getDefaultReturnValue() { return "null"; }

    //
    // Specialized
    //

    static public class UnknownCoderDescriptor extends CoderDescriptor {
        public static final CoderDescriptor UNKNOWN_DESC = new UnknownCoderDescriptor();
        public UnknownCoderDescriptor() { super(UnknownCoder.INST); }
    }

    static public class PrimitivePointerCoderDescriptor extends CoderDescriptor {
        public static final PrimitivePointerCoderDescriptor POINTER_DESC = new PrimitivePointerCoderDescriptor();
        public PrimitivePointerCoderDescriptor() { super(PrimitivePointerCoder.INST, "push", "popPtr"); }
    }

    static public class PointerCoderDescriptor extends CoderDescriptor {
        public static final PointerCoderDescriptor INST = new PointerCoderDescriptor();
        public PointerCoderDescriptor() { super(PointerCoder.INST); }
    }

    static public class SELCoderDescriptor extends CoderDescriptor {
        public static final SELCoderDescriptor INST = new SELCoderDescriptor();
        public SELCoderDescriptor() { super(SELCoder.INST); }
    }

    static public class IDCoderDescriptor extends CoderDescriptor {
        public static final IDCoderDescriptor INST = new IDCoderDescriptor();
        public IDCoderDescriptor() { super(IDCoder.INST); }
    }

    static public class NSClassCoderDescriptor extends CoderDescriptor {
        public static final NSClassCoderDescriptor INST = new NSClassCoderDescriptor();
        public NSClassCoderDescriptor() { super(NSClassCoder.INST); }
    }

    static public class StructCoderDescriptor extends CoderDescriptor {
        public final JStruct jstruct;
        public StructCoderDescriptor(JStruct jst) { this.jstruct = jst; }
        @Override public String getCoderInstanceName(){ return jstruct.getJavaTypeName() + ".getStructCoder()"; }
    }
}
