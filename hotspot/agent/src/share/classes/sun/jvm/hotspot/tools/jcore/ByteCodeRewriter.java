/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.tools.jcore;

import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.interpreter.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.runtime.*;

public class ByteCodeRewriter
{
    private Method method;
    private ConstantPool cpool;
    private ConstantPoolCache cpCache;
    private byte[] code;
    private Bytes  bytes;

    public static final boolean DEBUG = false;
    private static final int jintSize = 4;

    protected void debugMessage(String message) {
        System.out.println(message);
    }

    public ByteCodeRewriter(Method method, ConstantPool cpool, byte[] code) {
        this.method = method;
        this.cpool = cpool;
        this.cpCache = cpool.getCache();
        this.code = code;
        this.bytes = VM.getVM().getBytes();

    }

    protected short getConstantPoolIndex(int bci) {
       // get ConstantPool index from ConstantPoolCacheIndex at given bci
       short cpCacheIndex = method.getBytecodeShortArg(bci);
       if (cpCache == null) {
          return cpCacheIndex;
       } else {
          // change byte-ordering and go via cache
          return (short) cpCache.getEntryAt((int) (0xFFFF & bytes.swapShort(cpCacheIndex))).getConstantPoolIndex();
       }
    }

    static private void writeShort(byte[] buf, int index, short value) {
        buf[index] = (byte) ((value >> 8) & 0x00FF);
        buf[index + 1] = (byte) (value & 0x00FF);
    }

    public void rewrite() {
        int bytecode = Bytecodes._illegal;
        int hotspotcode = Bytecodes._illegal;
        int len = 0;

        for (int bci = 0; bci < code.length;) {
            hotspotcode = Bytecodes.codeAt(method, bci);
            bytecode = Bytecodes.javaCode(hotspotcode);

            if (Assert.ASSERTS_ENABLED) {
                int code_from_buffer = 0xFF & code[bci];
                Assert.that(code_from_buffer == hotspotcode
                          || code_from_buffer == Bytecodes._breakpoint,
                          "Unexpected bytecode found in method bytecode buffer!");
            }

            // update the code buffer hotspot specific bytecode with the jvm bytecode
            code[bci] = (byte) (0xFF & bytecode);

            short cpoolIndex = 0;
            switch (bytecode) {
                // bytecodes with ConstantPoolCache index
                case Bytecodes._getstatic:
                case Bytecodes._putstatic:
                case Bytecodes._getfield:
                case Bytecodes._putfield:
                case Bytecodes._invokevirtual:
                case Bytecodes._invokespecial:
                case Bytecodes._invokestatic:
                case Bytecodes._invokeinterface: {
                    cpoolIndex = getConstantPoolIndex(bci + 1);
                    writeShort(code, bci + 1, cpoolIndex);
                    break;
                }
            }

            len = Bytecodes.lengthFor(bytecode);
            if (len <= 0) len = Bytecodes.lengthAt(method, bci);

            if (DEBUG) {
                String operand = "";
                switch (len) {
                   case 2:
                        operand += code[bci + 1];
                        break;
                   case 3:
                        operand += (cpoolIndex != 0)? cpoolIndex :
                                            method.getBytecodeShortArg(bci + 1);
                        break;
                   case 5:
                        operand += method.getBytecodeIntArg(bci + 1);
                        break;
                }

                // the operand following # is not quite like javap output.
                // in particular, for goto & goto_w, the operand is PC relative
                // offset for jump. Javap adds relative offset with current PC
                // to give absolute bci to jump to.

                String message = "\t\t" + bci + " " + Bytecodes.name(bytecode);
                if (hotspotcode != bytecode)
                    message += " [" + Bytecodes.name(hotspotcode) + "]";
                if (operand != "")
                    message += " #" + operand;

                if (DEBUG) debugMessage(message);
            }

            bci += len;
        }
    }
}
