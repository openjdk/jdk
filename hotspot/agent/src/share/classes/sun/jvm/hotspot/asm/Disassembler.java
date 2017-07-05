/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.asm;

import java.io.PrintStream;
import java.util.Observer;
import java.util.Observable;
import sun.jvm.hotspot.code.CodeBlob;
import sun.jvm.hotspot.code.NMethod;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;

public class Disassembler {
   private static String options = "";
   private static long decode_function;

   protected long startPc;
   protected byte[] code;
   private CodeBlob blob;
   private NMethod nmethod;

   public static void decode(InstructionVisitor visitor, CodeBlob blob) {
      decode(visitor, blob, blob.codeBegin(), blob.codeEnd());
   }

   public static void decode(InstructionVisitor visitor, CodeBlob blob, Address begin, Address end) {
      int codeSize = (int)end.minus(begin);
      long startPc = VM.getAddressValue(begin);
      byte[] code = new byte[codeSize];
      for (int i = 0; i < code.length; i++)
         code[i] = begin.getJByteAt(i);
      Disassembler dis = new Disassembler(startPc, code);
      dis.decode(visitor);
   }

   private Disassembler(long startPc, byte[] code) {
      this.startPc = startPc;
      this.code = code;

      // Lazily load hsdis
      if (decode_function == 0) {
         StringBuilder path = new StringBuilder(System.getProperty("java.home"));
         String sep = System.getProperty("file.separator");
         String os = System.getProperty("os.name");
         String libname = "hsdis";
         String arch = System.getProperty("os.arch");
         if (os.lastIndexOf("Windows", 0) != -1) {
            if (arch.equals("x86")) {
               libname +=  "-i386";
            } else if (arch.equals("amd64")) {
               libname +=  "-amd64";
            } else {
               libname +=  "-" + arch;
            }
            path.append(sep + "bin" + sep);
            libname += ".dll";
         } else if (os.lastIndexOf("SunOS", 0) != -1) {
            if (arch.equals("x86") || arch.equals("i386")) {
               path.append(sep + "lib" + sep + "i386" + sep);
               libname +=  "-i386" + ".so";
            } else if (arch.equals("amd64")) {
               path.append(sep + "lib" + sep + "amd64" + sep);
               libname +=  "-amd64" + ".so";
            } else {
               path.append(sep + "lib" + sep + arch + sep);
               libname +=  "-" + arch + ".so";
            }
         } else if (os.lastIndexOf("Linux", 0) != -1) {
            if (arch.equals("x86") || arch.equals("i386")) {
               path.append(sep + "lib" + sep + "i386" + sep);
               libname += "-i386.so";
            } else if (arch.equals("amd64") || arch.equals("x86_64")) {
               path.append(sep + "lib" + sep + "amd64" + sep);
               libname +=  "-amd64.so";
            } else {
               path.append(sep + "lib" + sep + arch + sep);
               libname +=  "-" + arch + ".so";
            }
         } else if (os.lastIndexOf("Mac OS X", 0) != -1) {
            path.append(sep + "lib" + sep);
            libname += "-amd64" + ".dylib";       // x86_64 => amd64
         } else {
            path.append(sep + "lib" + sep + "arch" + sep);
            libname +=  "-" + arch + ".so";
         }
         decode_function = load_library(path.toString(), libname);
      }
   }

   private static native long load_library(String installed_jrepath, String hsdis_library_name);

   private native void decode(InstructionVisitor visitor, long pc, byte[] code,
                              String options, long decode_function);

   private void decode(InstructionVisitor visitor) {
      visitor.prologue();
      decode(visitor, startPc, code, options, decode_function);
      visitor.epilogue();
   }

   private boolean match(String event, String tag) {
      if (!event.startsWith(tag))
         return false;
      int taglen = tag.length();
      if (taglen == event.length()) return true;
      char delim = event.charAt(taglen);
      return delim == ' ' || delim == '/' || delim == '=';
   }

   // This is called from the native code to process various markers
   // in the dissassembly.
   private long handleEvent(InstructionVisitor visitor, String event, long arg) {
      if (match(event, "insn")) {
         try {
            visitor.beginInstruction(arg);
         } catch (Throwable e) {
            e.printStackTrace();
         }
      } else if (match(event, "/insn")) {
         try {
            visitor.endInstruction(arg);
         } catch (Throwable e) {
            e.printStackTrace();
         }
      } else if (match(event, "addr")) {
         if (arg != 0) {
            visitor.printAddress(arg);
         }
         return arg;
      } else if (match(event, "mach")) {
         // output().printf("[Disassembling for mach='%s']\n", arg);
      } else {
         // ignore unrecognized markup
      }
      return 0;
   }

   // This called from the native code to perform printing
   private  void rawPrint(InstructionVisitor visitor, String s) {
      visitor.print(s);
   }
}
