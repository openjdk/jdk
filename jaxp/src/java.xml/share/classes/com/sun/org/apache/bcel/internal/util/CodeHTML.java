/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.util;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache BCEL" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache BCEL", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import com.sun.org.apache.bcel.internal.classfile.*;
import java.io.*;
import java.util.BitSet;

/**
 * Convert code into HTML file.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 *
 */
final class CodeHTML implements com.sun.org.apache.bcel.internal.Constants {
  private String        class_name;     // name of current class
  private Method[]      methods;        // Methods to print
  private PrintWriter   file;           // file to write to
  private BitSet        goto_set;
  private ConstantPool  constant_pool;
  private ConstantHTML  constant_html;
  private static boolean wide=false;

  CodeHTML(String dir, String class_name,
           Method[] methods, ConstantPool constant_pool,
           ConstantHTML constant_html) throws IOException
  {
    this.class_name     = class_name;
    this.methods        = methods;
    this.constant_pool  = constant_pool;
    this.constant_html = constant_html;

    file = new PrintWriter(new FileOutputStream(dir + class_name + "_code.html"));
    file.println("<HTML><BODY BGCOLOR=\"#C0C0C0\">");

    for(int i=0; i < methods.length; i++)
      writeMethod(methods[i], i);

    file.println("</BODY></HTML>");
    file.close();
  }

  /**
   * Disassemble a stream of byte codes and return the
   * string representation.
   *
   * @param  stream data input stream
   * @return String representation of byte code
   */
  private final String codeToHTML(ByteSequence bytes, int method_number)
       throws IOException
  {
    short        opcode = (short)bytes.readUnsignedByte();
    StringBuffer buf;
    String       name, signature;
    int          default_offset=0, low, high;
    int          index, class_index, vindex, constant;
    int[]        jump_table;
    int          no_pad_bytes=0, offset;

    buf = new StringBuffer("<TT>" + OPCODE_NAMES[opcode] + "</TT></TD><TD>");

    /* Special case: Skip (0-3) padding bytes, i.e., the
     * following bytes are 4-byte-aligned
     */
    if((opcode == TABLESWITCH) || (opcode == LOOKUPSWITCH)) {
      int remainder = bytes.getIndex() % 4;
      no_pad_bytes  = (remainder == 0)? 0 : 4 - remainder;

      for(int i=0; i < no_pad_bytes; i++)
        bytes.readByte();

      // Both cases have a field default_offset in common
      default_offset = bytes.readInt();
    }

    switch(opcode) {
    case TABLESWITCH:
      low  = bytes.readInt();
      high = bytes.readInt();

      offset = bytes.getIndex() - 12 - no_pad_bytes - 1;
      default_offset += offset;

      buf.append("<TABLE BORDER=1><TR>");

      // Print switch indices in first row (and default)
      jump_table = new int[high - low + 1];
      for(int i=0; i < jump_table.length; i++) {
        jump_table[i] = offset + bytes.readInt();

        buf.append("<TH>" + (low + i) + "</TH>");
      }
      buf.append("<TH>default</TH></TR>\n<TR>");

      // Print target and default indices in second row
      for(int i=0; i < jump_table.length; i++)
        buf.append("<TD><A HREF=\"#code" + method_number + "@" +
                   jump_table[i] + "\">" + jump_table[i] + "</A></TD>");
      buf.append("<TD><A HREF=\"#code" + method_number + "@" +
                 default_offset + "\">" + default_offset + "</A></TD></TR>\n</TABLE>\n");

      break;

      /* Lookup switch has variable length arguments.
       */
    case LOOKUPSWITCH:
      int npairs = bytes.readInt();
      offset = bytes.getIndex() - 8 - no_pad_bytes - 1;
      jump_table = new int[npairs];
      default_offset += offset;

      buf.append("<TABLE BORDER=1><TR>");

      // Print switch indices in first row (and default)
      for(int i=0; i < npairs; i++) {
        int match = bytes.readInt();

        jump_table[i] = offset + bytes.readInt();
        buf.append("<TH>" + match + "</TH>");
      }
      buf.append("<TH>default</TH></TR>\n<TR>");

      // Print target and default indices in second row
      for(int i=0; i < npairs; i++)
        buf.append("<TD><A HREF=\"#code" + method_number + "@" +
                   jump_table[i] + "\">" + jump_table[i] + "</A></TD>");
      buf.append("<TD><A HREF=\"#code" + method_number + "@" +
                 default_offset + "\">" + default_offset + "</A></TD></TR>\n</TABLE>\n");
      break;

      /* Two address bytes + offset from start of byte stream form the
       * jump target.
       */
    case GOTO:      case IFEQ:      case IFGE:      case IFGT:
    case IFLE:      case IFLT:
    case IFNE:      case IFNONNULL: case IFNULL:    case IF_ACMPEQ:
    case IF_ACMPNE: case IF_ICMPEQ: case IF_ICMPGE: case IF_ICMPGT:
    case IF_ICMPLE: case IF_ICMPLT: case IF_ICMPNE: case JSR:

      index = (int)(bytes.getIndex() + bytes.readShort() - 1);

      buf.append("<A HREF=\"#code" + method_number + "@" + index + "\">" + index + "</A>");
      break;

      /* Same for 32-bit wide jumps
       */
    case GOTO_W: case JSR_W:
      int windex = bytes.getIndex() + bytes.readInt() - 1;
      buf.append("<A HREF=\"#code" + method_number + "@" + windex + "\">" +
                 windex + "</A>");
      break;

      /* Index byte references local variable (register)
       */
    case ALOAD:  case ASTORE: case DLOAD:  case DSTORE: case FLOAD:
    case FSTORE: case ILOAD:  case ISTORE: case LLOAD:  case LSTORE:
    case RET:
      if(wide) {
        vindex = bytes.readShort();
        wide=false; // Clear flag
      }
      else
        vindex = bytes.readUnsignedByte();

      buf.append("%" + vindex);
      break;

      /*
       * Remember wide byte which is used to form a 16-bit address in the
       * following instruction. Relies on that the method is called again with
       * the following opcode.
       */
    case WIDE:
      wide      = true;
      buf.append("(wide)");
      break;

      /* Array of basic type.
       */
    case NEWARRAY:
      buf.append("<FONT COLOR=\"#00FF00\">" + TYPE_NAMES[bytes.readByte()] + "</FONT>");
      break;

      /* Access object/class fields.
       */
    case GETFIELD: case GETSTATIC: case PUTFIELD: case PUTSTATIC:
      index = bytes.readShort();
      ConstantFieldref c1 = (ConstantFieldref)constant_pool.getConstant(index, CONSTANT_Fieldref);

      class_index = c1.getClassIndex();
      name = constant_pool.getConstantString(class_index, CONSTANT_Class);
      name = Utility.compactClassName(name, false);

      index = c1.getNameAndTypeIndex();
      String field_name = constant_pool.constantToString(index, CONSTANT_NameAndType);

      if(name.equals(class_name)) { // Local field
        buf.append("<A HREF=\"" + class_name + "_methods.html#field" + field_name +
                   "\" TARGET=Methods>" + field_name + "</A>\n");
      }
      else
        buf.append(constant_html.referenceConstant(class_index) + "." + field_name);

      break;

      /* Operands are references to classes in constant pool
       */
    case CHECKCAST: case INSTANCEOF: case NEW:
      index = bytes.readShort();
      buf.append(constant_html.referenceConstant(index));
      break;

      /* Operands are references to methods in constant pool
       */
    case INVOKESPECIAL: case INVOKESTATIC: case INVOKEVIRTUAL: case INVOKEINTERFACE:
      int m_index = bytes.readShort();
      String str;

      if(opcode == INVOKEINTERFACE) { // Special treatment needed
        int nargs    = bytes.readUnsignedByte(); // Redundant
        int reserved = bytes.readUnsignedByte(); // Reserved

        ConstantInterfaceMethodref c=(ConstantInterfaceMethodref)constant_pool.getConstant(m_index, CONSTANT_InterfaceMethodref);

        class_index = c.getClassIndex();
        str = constant_pool.constantToString(c);
        index = c.getNameAndTypeIndex();
      }
      else {
        ConstantMethodref c = (ConstantMethodref)constant_pool.getConstant(m_index, CONSTANT_Methodref);
        class_index = c.getClassIndex();

        str  = constant_pool.constantToString(c);
        index = c.getNameAndTypeIndex();
      }

      name = Class2HTML.referenceClass(class_index);
      str = Class2HTML.toHTML(constant_pool.constantToString(constant_pool.getConstant(index, CONSTANT_NameAndType)));

      // Get signature, i.e., types
      ConstantNameAndType c2 = (ConstantNameAndType)constant_pool.
        getConstant(index, CONSTANT_NameAndType);
      signature = constant_pool.constantToString(c2.getSignatureIndex(),
                                                 CONSTANT_Utf8);
      String[] args = Utility.methodSignatureArgumentTypes(signature, false);
      String   type = Utility.methodSignatureReturnType(signature, false);

      buf.append(name + ".<A HREF=\"" + class_name + "_cp.html#cp" + m_index +
                 "\" TARGET=ConstantPool>" + str + "</A>" + "(");

      // List arguments
      for(int i=0; i < args.length; i++) {
        buf.append(Class2HTML.referenceType(args[i]));

        if(i < args.length - 1)
          buf.append(", ");
      }
      // Attach return type
      buf.append("):" + Class2HTML.referenceType(type));

      break;

      /* Operands are references to items in constant pool
       */
    case LDC_W: case LDC2_W:
      index = bytes.readShort();

      buf.append("<A HREF=\"" + class_name + "_cp.html#cp" + index +
                 "\" TARGET=\"ConstantPool\">" +
                 Class2HTML.toHTML(constant_pool.constantToString(index,
                                                                  constant_pool.
                                                                  getConstant(index).getTag()))+
                 "</a>");
      break;

    case LDC:
      index = bytes.readUnsignedByte();
      buf.append("<A HREF=\"" + class_name + "_cp.html#cp" + index +
                 "\" TARGET=\"ConstantPool\">" +
                 Class2HTML.toHTML(constant_pool.constantToString(index,
                                                                  constant_pool.
                                                                  getConstant(index).getTag()))+
                 "</a>");
      break;

      /* Array of references.
       */
    case ANEWARRAY:
      index = bytes.readShort();

      buf.append(constant_html.referenceConstant(index));
      break;

      /* Multidimensional array of references.
       */
    case MULTIANEWARRAY:
      index = bytes.readShort();
      int dimensions = bytes.readByte();
      buf.append(constant_html.referenceConstant(index) + ":" + dimensions + "-dimensional");
      break;

      /* Increment local variable.
       */
    case IINC:
      if(wide) {
        vindex   = bytes.readShort();
        constant = bytes.readShort();
        wide     = false;
      }
      else {
        vindex   = bytes.readUnsignedByte();
        constant = bytes.readByte();
      }
      buf.append("%" + vindex + " " + constant);
      break;

    default:
      if(NO_OF_OPERANDS[opcode] > 0) {
        for(int i=0; i < TYPE_OF_OPERANDS[opcode].length; i++) {
          switch(TYPE_OF_OPERANDS[opcode][i]) {
          case T_BYTE:
            buf.append(bytes.readUnsignedByte());
            break;

          case T_SHORT: // Either branch or index
            buf.append(bytes.readShort());
            break;

          case T_INT:
            buf.append(bytes.readInt());
            break;

          default: // Never reached
            System.err.println("Unreachable default case reached!");
            System.exit(-1);
          }
          buf.append("&nbsp;");
        }
      }
    }

    buf.append("</TD>");
    return buf.toString();
  }

  /**
   * Find all target addresses in code, so that they can be marked
   * with &lt;A NAME = ...&gt;. Target addresses are kept in an BitSet object.
   */
  private final void findGotos(ByteSequence bytes, Method method, Code code)
       throws IOException
  {
    int index;
    goto_set = new BitSet(bytes.available());
    int opcode;

    /* First get Code attribute from method and the exceptions handled
     * (try .. catch) in this method. We only need the line number here.
     */

    if(code != null) {
      CodeException[] ce  = code.getExceptionTable();
      int             len = ce.length;

      for(int i=0; i < len; i++) {
        goto_set.set(ce[i].getStartPC());
        goto_set.set(ce[i].getEndPC());
        goto_set.set(ce[i].getHandlerPC());
      }

      // Look for local variables and their range
      Attribute[] attributes = code.getAttributes();
      for(int i=0; i < attributes.length; i++) {
        if(attributes[i].getTag() == ATTR_LOCAL_VARIABLE_TABLE) {
          LocalVariable[] vars = ((LocalVariableTable)attributes[i]).getLocalVariableTable();

          for(int j=0; j < vars.length; j++) {
            int  start = vars[j].getStartPC();
            int  end   = (int)(start + vars[j].getLength());
            goto_set.set(start);
            goto_set.set(end);
          }
          break;
        }
      }
    }

    // Get target addresses from GOTO, JSR, TABLESWITCH, etc.
    for(int i=0; bytes.available() > 0; i++) {
      opcode = bytes.readUnsignedByte();
      //System.out.println(OPCODE_NAMES[opcode]);
      switch(opcode) {
      case TABLESWITCH: case LOOKUPSWITCH:
        //bytes.readByte(); // Skip already read byte

        int remainder = bytes.getIndex() % 4;
        int no_pad_bytes  = (remainder == 0)? 0 : 4 - remainder;
        int default_offset, offset;

        for(int j=0; j < no_pad_bytes; j++)
          bytes.readByte();

        // Both cases have a field default_offset in common
        default_offset = bytes.readInt();

        if(opcode == TABLESWITCH) {
          int low = bytes.readInt();
          int high = bytes.readInt();

          offset = bytes.getIndex() - 12 - no_pad_bytes - 1;
          default_offset += offset;
          goto_set.set(default_offset);

          for(int j=0; j < (high - low + 1); j++) {
            index = offset + bytes.readInt();
            goto_set.set(index);
          }
        }
        else { // LOOKUPSWITCH
          int npairs = bytes.readInt();

          offset = bytes.getIndex() - 8 - no_pad_bytes - 1;
          default_offset += offset;
          goto_set.set(default_offset);

          for(int j=0; j < npairs; j++) {
            int match = bytes.readInt();

            index = offset + bytes.readInt();
            goto_set.set(index);
          }
        }
        break;

      case GOTO:      case IFEQ:      case IFGE:      case IFGT:
      case IFLE:      case IFLT:
      case IFNE:      case IFNONNULL: case IFNULL:    case IF_ACMPEQ:
      case IF_ACMPNE: case IF_ICMPEQ: case IF_ICMPGE: case IF_ICMPGT:
      case IF_ICMPLE: case IF_ICMPLT: case IF_ICMPNE: case JSR:
        //bytes.readByte(); // Skip already read byte
        index = bytes.getIndex() + bytes.readShort() - 1;

        goto_set.set(index);
        break;

      case GOTO_W: case JSR_W:
        //bytes.readByte(); // Skip already read byte
        index = bytes.getIndex() + bytes.readInt() - 1;
        goto_set.set(index);
        break;

      default:
        bytes.unreadByte();
        codeToHTML(bytes, 0); // Ignore output
      }
    }
  }

  /**
   * Write a single method with the byte code associated with it.
   */
  private void writeMethod(Method method, int method_number)
       throws IOException
  {
    // Get raw signature
    String       signature = method.getSignature();
    // Get array of strings containing the argument types
    String[]     args      = Utility.methodSignatureArgumentTypes(signature, false);
    // Get return type string
    String       type      = Utility.methodSignatureReturnType(signature, false);
    // Get method name
    String       name      = method.getName();
    String       html_name = Class2HTML.toHTML(name);
    // Get method's access flags
    String       access    = Utility.accessToString(method.getAccessFlags());
    access = Utility.replace(access, " ", "&nbsp;");
    // Get the method's attributes, the Code Attribute in particular
    Attribute[]  attributes= method.getAttributes();

    file.print("<P><B><FONT COLOR=\"#FF0000\">" + access + "</FONT>&nbsp;" +
               "<A NAME=method" + method_number + ">" + Class2HTML.referenceType(type) +
               "</A>&nbsp<A HREF=\"" + class_name + "_methods.html#method" + method_number +
               "\" TARGET=Methods>" + html_name + "</A>(");

    for(int i=0; i < args.length; i++) {
      file.print(Class2HTML.referenceType(args[i]));
      if(i < args.length - 1)
        file.print(",&nbsp;");
    }

    file.println(")</B></P>");

    Code c=null;
    byte[] code=null;

    if(attributes.length > 0) {
      file.print("<H4>Attributes</H4><UL>\n");
      for(int i=0; i < attributes.length; i++) {
        byte tag = attributes[i].getTag();

        if(tag != ATTR_UNKNOWN)
          file.print("<LI><A HREF=\"" + class_name + "_attributes.html#method" + method_number + "@" + i +
                     "\" TARGET=Attributes>" + ATTRIBUTE_NAMES[tag] + "</A></LI>\n");
        else
          file.print("<LI>" + attributes[i] + "</LI>");

        if(tag == ATTR_CODE) {
          c = (Code)attributes[i];
          Attribute[] attributes2 = c.getAttributes();
          code                                                          = c.getCode();

          file.print("<UL>");
          for(int j=0; j < attributes2.length; j++) {
            tag = attributes2[j].getTag();
            file.print("<LI><A HREF=\"" + class_name + "_attributes.html#" +
                       "method" + method_number + "@" + i + "@" + j + "\" TARGET=Attributes>" +
                       ATTRIBUTE_NAMES[tag] + "</A></LI>\n");

          }
          file.print("</UL>");
        }
      }
      file.println("</UL>");
    }

    if(code != null) { // No code, an abstract method, e.g.
      //System.out.println(name + "\n" + Utility.codeToString(code, constant_pool, 0, -1));

      // Print the byte code
      ByteSequence stream = new ByteSequence(code);
      stream.mark(stream.available());
      findGotos(stream, method, c);
      stream.reset();

      file.println("<TABLE BORDER=0><TR><TH ALIGN=LEFT>Byte<BR>offset</TH>" +
                   "<TH ALIGN=LEFT>Instruction</TH><TH ALIGN=LEFT>Argument</TH>");

      for(int i=0; stream.available() > 0; i++) {
        int offset = stream.getIndex();
        String str = codeToHTML(stream, method_number);
        String anchor = "";

        /* Set an anchor mark if this line is targetted by a goto, jsr, etc.
         * Defining an anchor for every line is very inefficient!
         */
        if(goto_set.get(offset))
          anchor = "<A NAME=code" + method_number + "@" + offset +  "></A>";

        String anchor2;
        if(stream.getIndex() == code.length) // last loop
          anchor2 = "<A NAME=code" + method_number + "@" + code.length + ">" + offset + "</A>";
        else
          anchor2 = "" + offset;

        file.println("<TR VALIGN=TOP><TD>" + anchor2 + "</TD><TD>" + anchor + str + "</TR>");
      }

      // Mark last line, may be targetted from Attributes window
      file.println("<TR><TD> </A></TD></TR>");
      file.println("</TABLE>");
    }

  }
}
