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

import java.util.*;
import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.generic.*;
import com.sun.org.apache.regexp.internal.*;

/**
 * InstructionFinder is a tool to search for given instructions patterns,
 * i.e., match sequences of instructions in an instruction list via
 * regular expressions. This can be used, e.g., in order to implement
 * a peep hole optimizer that looks for code patterns and replaces
 * them with faster equivalents.
 *
 * <p>This class internally uses the <a href="http://jakarta.apache.org/regexp/">
 * Regexp</a> package to search for regular expressions.
 *
 * A typical application would look like this:
<pre>
    InstructionFinder f   = new InstructionFinder(il);
    String            pat = "IfInstruction ICONST_0 GOTO ICONST_1 NOP (IFEQ|IFNE)";

    for(Iterator i = f.search(pat, constraint); i.hasNext(); ) {
      InstructionHandle[] match = (InstructionHandle[])i.next();
      ...
      il.delete(match[1], match[5]);
      ...
    }
</pre>
 * @author  <A HREF="http://www.berlin.de/~markus.dahm/">M. Dahm</A>
 * @see Instruction
 * @see InstructionList
 */
public class InstructionFinder {
  private static final int OFFSET     = 32767; // char + OFFSET is outside of LATIN-1
  private static final int NO_OPCODES = 256;   // Potential number, some are not used

  private static final HashMap map = new HashMap(); // Map<String,Pattern>

  private InstructionList     il;
  private String              il_string;    // instruction list as string
  private InstructionHandle[] handles;      // map instruction list to array

  /**
   * @param il instruction list to search for given patterns
   */
  public InstructionFinder(InstructionList il) {
    this.il = il;
    reread();
  }

  /**
   * Reread the instruction list, e.g., after you've altered the list upon a match.
   */
  public final void reread() {
    int    size  = il.getLength();
    char[] buf   = new char[size]; // Create a string with length equal to il length
    handles      = il.getInstructionHandles();

    // Map opcodes to characters
    for(int i=0; i < size; i++)
      buf[i] = makeChar(handles[i].getInstruction().getOpcode());

    il_string = new String(buf);
  }

  /**
   * Map symbolic instruction names like "getfield" to a single character.
   *
   * @param pattern instruction pattern in lower case
   * @return encoded string for a pattern such as "BranchInstruction".
   */
  private static final String mapName(String pattern) {
    String result = (String)map.get(pattern);

    if(result != null)
      return result;

    for(short i=0; i < NO_OPCODES; i++)
      if(pattern.equals(Constants.OPCODE_NAMES[i]))
        return "" + makeChar(i);

    throw new RuntimeException("Instruction unknown: " + pattern);
  }

  /**
   * Replace symbolic names of instructions with the appropiate character and remove
   * all white space from string. Meta characters such as +, * are ignored.
   *
   * @param pattern The pattern to compile
   * @return translated regular expression string
   */
  private static final String compilePattern(String pattern) {
    String       lower      = pattern.toLowerCase();
    StringBuffer buf        = new StringBuffer();
    int          size       = pattern.length();

    for(int i=0; i < size; i++) {
      char ch = lower.charAt(i);

      if(Character.isLetterOrDigit(ch)) {
        StringBuffer name = new StringBuffer();

        while((Character.isLetterOrDigit(ch) || ch == '_') && i < size) {
          name.append(ch);

          if(++i < size)
            ch = lower.charAt(i);
          else
            break;
        }

        i--;

        buf.append(mapName(name.toString()));
      } else if(!Character.isWhitespace(ch))
        buf.append(ch);
    }

    return buf.toString();
  }

  /**
   * @return the matched piece of code as an array of instruction (handles)
   */
  private InstructionHandle[] getMatch(int matched_from, int match_length) {
    InstructionHandle[] match = new InstructionHandle[match_length];
    System.arraycopy(handles, matched_from, match, 0, match_length);

    return match;
  }

  /**
   * Search for the given pattern in the instruction list. You can search for any valid
   * opcode via its symbolic name, e.g. "istore". You can also use a super class or
   * an interface name to match a whole set of instructions, e.g. "BranchInstruction" or
   * "LoadInstruction". "istore" is also an alias for all "istore_x" instructions. Additional
   * aliases are "if" for "ifxx", "if_icmp" for "if_icmpxx", "if_acmp" for "if_acmpxx".
   *
   * Consecutive instruction names must be separated by white space which will be removed
   * during the compilation of the pattern.
   *
   * For the rest the usual pattern matching rules for regular expressions apply.<P>
   * Example pattern:
   * <pre>
     search("BranchInstruction NOP ((IfInstruction|GOTO)+ ISTORE Instruction)*");
   * </pre>
   *
   * <p>If you alter the instruction list upon a match such that other
   * matching areas are affected, you should call reread() to update
   * the finder and call search() again, because the matches are cached.
   *
   * @param pattern the instruction pattern to search for, where case is ignored
   * @param from where to start the search in the instruction list
   * @param constraint optional CodeConstraint to check the found code pattern for
   * user-defined constraints
   * @return iterator of matches where e.nextElement() returns an array of instruction handles
   * describing the matched area
   */
  public final Iterator search(String pattern, InstructionHandle from,
                               CodeConstraint constraint)
  {
    String search = compilePattern(pattern);
    int  start    = -1;

    for(int i=0; i < handles.length; i++) {
      if(handles[i] == from) {
        start = i; // Where to start search from (index)
        break;
      }
    }

    if(start == -1)
      throw new ClassGenException("Instruction handle " + from +
                                  " not found in instruction list.");
    try {
      RE regex = new RE(search);
      ArrayList matches = new ArrayList();

      while(start < il_string.length() && regex.match(il_string, start)) {
        int startExpr = regex.getParenStart(0);
        int endExpr   = regex.getParenEnd(0);
        int lenExpr   = regex.getParenLength(0);

        InstructionHandle[] match = getMatch(startExpr, lenExpr);

        if((constraint == null) || constraint.checkCode(match))
          matches.add(match);
        start = endExpr;
      }

      return matches.iterator();
    } catch(RESyntaxException e) {
      System.err.println(e);
    }

    return null;
  }

  /**
   * Start search beginning from the start of the given instruction list.
   *
   * @param pattern the instruction pattern to search for, where case is ignored
   * @return iterator of matches where e.nextElement()
   * returns an array of instruction handles describing the matched
   * area
   */
  public final Iterator search(String pattern) {
    return search(pattern, il.getStart(), null);
  }

  /**
   * Start search beginning from `from'.
   *
   * @param pattern the instruction pattern to search for, where case is ignored
   * @param from where to start the search in the instruction list
   * @return  iterator of matches where e.nextElement() returns an array of instruction handles
   * describing the matched area
   */
  public final Iterator search(String pattern, InstructionHandle from) {
    return search(pattern, from, null);
  }

  /**
   * Start search beginning from the start of the given instruction list.
   * Check found matches with the constraint object.
   *
   * @param pattern the instruction pattern to search for, case is ignored
   * @param constraint constraints to be checked on matching code
   * @return instruction handle or `null' if the match failed
   */
  public final Iterator search(String pattern, CodeConstraint constraint) {
    return search(pattern, il.getStart(), constraint);
  }

  /**
   * Convert opcode number to char.
   */
  private static final char makeChar(short opcode) {
    return (char)(opcode + OFFSET);
  }

  /**
   * @return the inquired instruction list
   */
  public final InstructionList getInstructionList() { return il; }

  /**
   * Code patterns found may be checked using an additional
   * user-defined constraint object whether they really match the needed criterion.
   * I.e., check constraints that can not expressed with regular expressions.
   *
   */
  public interface CodeConstraint {
    /**
     * @param match array of instructions matching the requested pattern
     * @return true if the matched area is really useful
     */
    public boolean checkCode(InstructionHandle[] match);
  }

  // Initialize pattern map

  static {
    map.put("arithmeticinstruction", "(irem|lrem|iand|ior|ineg|isub|lneg|fneg|fmul|ldiv|fadd|lxor|frem|idiv|land|ixor|ishr|fsub|lshl|fdiv|iadd|lor|dmul|lsub|ishl|imul|lmul|lushr|dneg|iushr|lshr|ddiv|drem|dadd|ladd|dsub)");
    map.put("invokeinstruction", "(invokevirtual|invokeinterface|invokestatic|invokespecial)");
    map.put("arrayinstruction", "(baload|aastore|saload|caload|fastore|lastore|iaload|castore|iastore|aaload|bastore|sastore|faload|laload|daload|dastore)");
    map.put("gotoinstruction", "(goto|goto_w)");
    map.put("conversioninstruction", "(d2l|l2d|i2s|d2i|l2i|i2b|l2f|d2f|f2i|i2d|i2l|f2d|i2c|f2l|i2f)");
    map.put("localvariableinstruction", "(fstore|iinc|lload|dstore|dload|iload|aload|astore|istore|fload|lstore)");
    map.put("loadinstruction", "(fload|dload|lload|iload|aload)");
    map.put("fieldinstruction", "(getfield|putstatic|getstatic|putfield)");
    map.put("cpinstruction", "(ldc2_w|invokeinterface|multianewarray|putstatic|instanceof|getstatic|checkcast|getfield|invokespecial|ldc_w|invokestatic|invokevirtual|putfield|ldc|new|anewarray)");
    map.put("stackinstruction", "(dup2|swap|dup2_x2|pop|pop2|dup|dup2_x1|dup_x2|dup_x1)");
    map.put("branchinstruction", "(ifle|if_acmpne|if_icmpeq|if_acmpeq|ifnonnull|goto_w|iflt|ifnull|if_icmpne|tableswitch|if_icmple|ifeq|if_icmplt|jsr_w|if_icmpgt|ifgt|jsr|goto|ifne|ifge|lookupswitch|if_icmpge)");
    map.put("returninstruction", "(lreturn|ireturn|freturn|dreturn|areturn|return)");
    map.put("storeinstruction", "(istore|fstore|dstore|astore|lstore)");
    map.put("select", "(tableswitch|lookupswitch)");
    map.put("ifinstruction", "(ifeq|ifgt|if_icmpne|if_icmpeq|ifge|ifnull|ifne|if_icmple|if_icmpge|if_acmpeq|if_icmplt|if_acmpne|ifnonnull|iflt|if_icmpgt|ifle)");
    map.put("jsrinstruction", "(jsr|jsr_w)");
    map.put("variablelengthinstruction", "(tableswitch|jsr|goto|lookupswitch)");
    map.put("unconditionalbranch", "(goto|jsr|jsr_w|athrow|goto_w)");
    map.put("constantpushinstruction", "(dconst|bipush|sipush|fconst|iconst|lconst)");
    map.put("typedinstruction", "(imul|lsub|aload|fload|lor|new|aaload|fcmpg|iand|iaload|lrem|idiv|d2l|isub|dcmpg|dastore|ret|f2d|f2i|drem|iinc|i2c|checkcast|frem|lreturn|astore|lushr|daload|dneg|fastore|istore|lshl|ldiv|lstore|areturn|ishr|ldc_w|invokeinterface|aastore|lxor|ishl|l2d|i2f|return|faload|sipush|iushr|caload|instanceof|invokespecial|putfield|fmul|ireturn|laload|d2f|lneg|ixor|i2l|fdiv|lastore|multianewarray|i2b|getstatic|i2d|putstatic|fcmpl|saload|ladd|irem|dload|jsr_w|dconst|dcmpl|fsub|freturn|ldc|aconst_null|castore|lmul|ldc2_w|dadd|iconst|f2l|ddiv|dstore|land|jsr|anewarray|dmul|bipush|dsub|sastore|d2i|i2s|lshr|iadd|l2i|lload|bastore|fstore|fneg|iload|fadd|baload|fconst|ior|ineg|dreturn|l2f|lconst|getfield|invokevirtual|invokestatic|iastore)");
    map.put("popinstruction", "(fstore|dstore|pop|pop2|astore|putstatic|istore|lstore)");
    map.put("allocationinstruction", "(multianewarray|new|anewarray|newarray)");
    map.put("indexedinstruction", "(lload|lstore|fload|ldc2_w|invokeinterface|multianewarray|astore|dload|putstatic|instanceof|getstatic|checkcast|getfield|invokespecial|dstore|istore|iinc|ldc_w|ret|fstore|invokestatic|iload|putfield|invokevirtual|ldc|new|aload|anewarray)");
    map.put("pushinstruction", "(dup|lload|dup2|bipush|fload|ldc2_w|sipush|lconst|fconst|dload|getstatic|ldc_w|aconst_null|dconst|iload|ldc|iconst|aload)");
    map.put("stackproducer", "(imul|lsub|aload|fload|lor|new|aaload|fcmpg|iand|iaload|lrem|idiv|d2l|isub|dcmpg|dup|f2d|f2i|drem|i2c|checkcast|frem|lushr|daload|dneg|lshl|ldiv|ishr|ldc_w|invokeinterface|lxor|ishl|l2d|i2f|faload|sipush|iushr|caload|instanceof|invokespecial|fmul|laload|d2f|lneg|ixor|i2l|fdiv|getstatic|i2b|swap|i2d|dup2|fcmpl|saload|ladd|irem|dload|jsr_w|dconst|dcmpl|fsub|ldc|arraylength|aconst_null|tableswitch|lmul|ldc2_w|iconst|dadd|f2l|ddiv|land|jsr|anewarray|dmul|bipush|dsub|d2i|newarray|i2s|lshr|iadd|lload|l2i|fneg|iload|fadd|baload|fconst|lookupswitch|ior|ineg|lconst|l2f|getfield|invokevirtual|invokestatic)");
    map.put("stackconsumer", "(imul|lsub|lor|iflt|fcmpg|if_icmpgt|iand|ifeq|if_icmplt|lrem|ifnonnull|idiv|d2l|isub|dcmpg|dastore|if_icmpeq|f2d|f2i|drem|i2c|checkcast|frem|lreturn|astore|lushr|pop2|monitorexit|dneg|fastore|istore|lshl|ldiv|lstore|areturn|if_icmpge|ishr|monitorenter|invokeinterface|aastore|lxor|ishl|l2d|i2f|return|iushr|instanceof|invokespecial|fmul|ireturn|d2f|lneg|ixor|pop|i2l|ifnull|fdiv|lastore|i2b|if_acmpeq|ifge|swap|i2d|putstatic|fcmpl|ladd|irem|dcmpl|fsub|freturn|ifgt|castore|lmul|dadd|f2l|ddiv|dstore|land|if_icmpne|if_acmpne|dmul|dsub|sastore|ifle|d2i|i2s|lshr|iadd|l2i|bastore|fstore|fneg|fadd|ior|ineg|ifne|dreturn|l2f|if_icmple|getfield|invokevirtual|invokestatic|iastore)");
    map.put("exceptionthrower", "(irem|lrem|laload|putstatic|baload|dastore|areturn|getstatic|ldiv|anewarray|iastore|castore|idiv|saload|lastore|fastore|putfield|lreturn|caload|getfield|return|aastore|freturn|newarray|instanceof|multianewarray|athrow|faload|iaload|aaload|dreturn|monitorenter|checkcast|bastore|arraylength|new|invokevirtual|sastore|ldc_w|ireturn|invokespecial|monitorexit|invokeinterface|ldc|invokestatic|daload)");
    map.put("loadclass", "(multianewarray|invokeinterface|instanceof|invokespecial|putfield|checkcast|putstatic|invokevirtual|new|getstatic|invokestatic|getfield|anewarray)");
    map.put("instructiontargeter", "(ifle|if_acmpne|if_icmpeq|if_acmpeq|ifnonnull|goto_w|iflt|ifnull|if_icmpne|tableswitch|if_icmple|ifeq|if_icmplt|jsr_w|if_icmpgt|ifgt|jsr|goto|ifne|ifge|lookupswitch|if_icmpge)");

    // Some aliases
    map.put("if_icmp", "(if_icmpne|if_icmpeq|if_icmple|if_icmpge|if_icmplt|if_icmpgt)");
    map.put("if_acmp", "(if_acmpeq|if_acmpne)");
    map.put("if", "(ifeq|ifne|iflt|ifge|ifgt|ifle)");

    // Precompile some aliases first
    map.put("iconst", precompile(Constants.ICONST_0, Constants.ICONST_5, Constants.ICONST_M1));
    map.put("lconst", new String(new char[] { '(', makeChar(Constants.LCONST_0), '|',
                                              makeChar(Constants.LCONST_1), ')' }));
    map.put("dconst", new String(new char[] { '(', makeChar(Constants.DCONST_0), '|',
                                              makeChar(Constants.DCONST_1), ')' }));
    map.put("fconst", new String(new char[] { '(', makeChar(Constants.FCONST_0), '|',
                                              makeChar(Constants.FCONST_1), ')' }));

    map.put("iload", precompile(Constants.ILOAD_0, Constants.ILOAD_3, Constants.ILOAD));
    map.put("dload", precompile(Constants.DLOAD_0, Constants.DLOAD_3, Constants.DLOAD));
    map.put("fload", precompile(Constants.FLOAD_0, Constants.FLOAD_3, Constants.FLOAD));
    map.put("aload", precompile(Constants.ALOAD_0, Constants.ALOAD_3, Constants.ALOAD));

    map.put("istore", precompile(Constants.ISTORE_0, Constants.ISTORE_3, Constants.ISTORE));
    map.put("dstore", precompile(Constants.DSTORE_0, Constants.DSTORE_3, Constants.DSTORE));
    map.put("fstore", precompile(Constants.FSTORE_0, Constants.FSTORE_3, Constants.FSTORE));
    map.put("astore", precompile(Constants.ASTORE_0, Constants.ASTORE_3, Constants.ASTORE));

    // Compile strings

    for(Iterator i = map.keySet().iterator(); i.hasNext(); ) {
      String key   = (String)i.next();
      String value = (String)map.get(key);

      char ch = value.charAt(1); // Omit already precompiled patterns
      if(ch < OFFSET) {
        map.put(key, compilePattern(value)); // precompile all patterns
      }
    }

    // Add instruction alias to match anything

    StringBuffer buf = new StringBuffer("(");

    for(short i=0; i < NO_OPCODES; i++) {
      if(Constants.NO_OF_OPERANDS[i] != Constants.UNDEFINED) { // Not an invalid opcode
        buf.append(makeChar(i));

        if(i < NO_OPCODES - 1)
          buf.append('|');
      }
    }
    buf.append(')');

    map.put("instruction", buf.toString());
  }

  private static String precompile(short from, short to, short extra) {
    StringBuffer buf = new StringBuffer("(");

    for(short i=from; i <= to; i++) {
      buf.append(makeChar(i));
      buf.append('|');
    }

    buf.append(makeChar(extra));
    buf.append(")");
    return buf.toString();
  }

  /*
   * Internal debugging routines.
   */
  private static final String pattern2string(String pattern) {
    return pattern2string(pattern, true);
  }

  private static final String pattern2string(String pattern, boolean make_string) {
    StringBuffer buf = new StringBuffer();

    for(int i=0; i < pattern.length(); i++) {
      char ch = pattern.charAt(i);

      if(ch >= OFFSET) {
        if(make_string)
          buf.append(Constants.OPCODE_NAMES[ch - OFFSET]);
        else
          buf.append((int)(ch - OFFSET));
      } else
        buf.append(ch);
    }

    return buf.toString();
  }
}
