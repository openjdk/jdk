/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.tools.soql;

import java.io.*;
import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.tools.*;
import sun.jvm.hotspot.utilities.*;
import sun.jvm.hotspot.utilities.soql.*;

/**
  This is command line SOQL (Simple Object Query Language) interpreter.
*/

public class SOQL extends Tool {
   public static void main(String[] args) {
      SOQL soql = new SOQL();
      soql.execute(args);
   }

   public SOQL() {
      super();
   }

   public SOQL(JVMDebugger d) {
      super(d);
   }

   protected SOQLEngine soqlEngine;
   protected BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
   protected PrintStream out       = System.out;
   static protected String prompt       = "soql> ";
   static protected String secondPrompt = "> ";

   public void run() {
      soqlEngine = SOQLEngine.getEngine();
      while (true) {
         try {
            out.print(prompt);
            String line = in.readLine();
            if (line == null) {
               return;
            }
            StringTokenizer st = new StringTokenizer(line);
            if (st.hasMoreTokens()) {
               String cmd = st.nextToken();
               if (cmd.equals("select")) {
                  handleSelect(line);
               } else if (cmd.equals("classes")) {
                  handleClasses(line);
               } else if (cmd.equals("class")) {
                  handleClass(line);
               } else if (cmd.equals("object")) {
                  handleObject(line);
               } else if (cmd.equals("quit")) {
                  out.println("Bye!");
                  return;
               } else if (cmd.equals("")) {
                  // do nothing ...
               } else {
                  handleUnknown(line);
               }
            }
         } catch (IOException e) {
            e.printStackTrace();
            return;
         }
      }
   }

   protected void handleSelect(String query) {
      StringBuffer buf = new StringBuffer(query);
      String tmp = null;
      while (true) {
         out.print(secondPrompt);
         try {
            tmp = in.readLine();
         } catch (IOException ioe) {
            break;
         }
         if (tmp.equals("") || tmp.equals("go"))
            break;
         buf.append('\n');
         buf.append(tmp);
      }
      query = buf.toString();

      try {
         soqlEngine.executeQuery(query,
                           new ObjectVisitor() {
                              public void visit(Object o) {
                                 if (o != null && o instanceof JSJavaObject) {
                                     String oopAddr = ((JSJavaObject)o).getOop().getHandle().toString();
                                     out.println(oopAddr);
                                 } else {
                                     out.println((o == null)? "null" : o.toString());
                                 }
                              }
                           });
      } catch (SOQLException se) {
         se.printStackTrace();
      }
   }

   protected void handleClasses(String line) {
      // just list all InstanceKlasses
      InstanceKlass[] klasses = SystemDictionaryHelper.getAllInstanceKlasses();
      for (int i = 0; i < klasses.length; i++) {
         out.print(klasses[i].getName().asString().replace('/', '.'));
         out.print(" @");
         out.println(klasses[i].getAddress());
      }
   }

   protected void handleClass(String line) {
      StringTokenizer st = new StringTokenizer(line);
      st.nextToken(); // ignore "class"
      if (st.hasMoreTokens()) {
         String className = st.nextToken();
         InstanceKlass klass = SystemDictionaryHelper.findInstanceKlass(className);
         if (klass == null) {
            out.println("class " + className + " not found");
         } else {
            // klass.iterate(new OopPrinter(out), true);

            // base class
            InstanceKlass base = (InstanceKlass) klass.getSuper();
            if (base != null) {
               out.println("super");
               out.print("\t");
               out.println(base.getName().asString().replace('/', '.'));
            }

            // list immediate fields only
            U2Array fields = klass.getFields();
            int numFields = (int) fields.length();
            ConstantPool cp = klass.getConstants();
            out.println("fields");
            if (numFields != 0) {
              for (int f = 0; f < numFields; f++){
                 Symbol f_name = klass.getFieldName(f);
                 Symbol f_sig  = klass.getFieldSignature(f);
                 StringBuffer sigBuf = new StringBuffer();
                 new SignatureConverter(f_sig, sigBuf).dispatchField();
                 out.print('\t');
                 out.print(sigBuf.toString().replace('/', '.'));
                 out.print(' ');
                 out.println(f_name.asString());
               }
            } else {
               out.println("\tno fields in this class");
            }
         }
      } else {
         out.println("usage: class <name of the class>");
      }
   }

   protected Oop getOopAtAddress(Address addr) {
      OopHandle oopHandle = addr.addOffsetToAsOopHandle(0);
      return VM.getVM().getObjectHeap().newOop(oopHandle);
   }

   protected void handleObject(String line) {
      StringTokenizer st = new StringTokenizer(line);
      st.nextToken(); // ignore "object"
      if (st.hasMoreTokens()) {
         String addrStr = st.nextToken();
         Address addr = null;
         Debugger dbg = VM.getVM().getDebugger();
         try {
            addr = dbg.parseAddress(addrStr);
         } catch (Exception e) {
            out.println("invalid address : " + e.getMessage());
            return;
         }

         Oop oop = null;
         try {
            oop = getOopAtAddress(addr);
         } catch (Exception e) {
            out.println("invalid object : " + e.getMessage());
         }

         if (oop != null) {
            oop.iterate(new OopPrinter(out), true);
         } else {
            out.println("null object!");
         }
      } else {
         out.println("usage: object <address>");
      }
   }

   protected void handleUnknown(String line) {
      out.println("Unknown command!");
   }
}
