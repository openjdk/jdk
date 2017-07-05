/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
package com.sun.org.apache.bcel.internal.generic;

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

import com.sun.org.apache.bcel.internal.Constants;
import com.sun.org.apache.bcel.internal.classfile.*;
import java.util.*;

/**
 * Template class for building up a method. This is done by defining exception
 * handlers, adding thrown exceptions, local variables and attributes, whereas
 * the `LocalVariableTable' and `LineNumberTable' attributes will be set
 * automatically for the code. Use stripAttributes() if you don't like this.
 *
 * While generating code it may be necessary to insert NOP operations. You can
 * use the `removeNOPs' method to get rid off them.
 * The resulting method object can be obtained via the `getMethod()' method.
 *
 * @author  <A HREF="mailto:markus.dahm@berlin.de">M. Dahm</A>
 * @author  <A HREF="http://www.vmeng.com/beard">Patrick C. Beard</A> [setMaxStack()]
 * @see     InstructionList
 * @see     Method
 */
public class MethodGen extends FieldGenOrMethodGen {
  private String          class_name;
  private Type[]          arg_types;
  private String[]        arg_names;
  private int             max_locals;
  private int             max_stack;
  private InstructionList il;
  private boolean         strip_attributes;

  private ArrayList       variable_vec    = new ArrayList();
  private ArrayList       line_number_vec = new ArrayList();
  private ArrayList       exception_vec   = new ArrayList();
  private ArrayList       throws_vec      = new ArrayList();
  private ArrayList       code_attrs_vec  = new ArrayList();

  /**
   * Declare method. If the method is non-static the constructor
   * automatically declares a local variable `$this' in slot 0. The
   * actual code is contained in the `il' parameter, which may further
   * manipulated by the user. But he must take care not to remove any
   * instruction (handles) that are still referenced from this object.
   *
   * For example one may not add a local variable and later remove the
   * instructions it refers to without causing havoc. It is safe
   * however if you remove that local variable, too.
   *
   * @param access_flags access qualifiers
   * @param return_type  method type
   * @param arg_types argument types
   * @param arg_names argument names (if this is null, default names will be provided
   * for them)
   * @param method_name name of method
   * @param class_name class name containing this method (may be null, if you don't care)
   * @param il instruction list associated with this method, may be null only for
   * abstract or native methods
   * @param cp constant pool
   */
  public MethodGen(int access_flags, Type return_type, Type[] arg_types,
                   String[] arg_names, String method_name, String class_name,
                   InstructionList il, ConstantPoolGen cp) {
    setAccessFlags(access_flags);
    setType(return_type);
    setArgumentTypes(arg_types);
    setArgumentNames(arg_names);
    setName(method_name);
    setClassName(class_name);
    setInstructionList(il);
    setConstantPool(cp);

    boolean abstract_ = isAbstract() || isNative();
    InstructionHandle start = null;
    InstructionHandle end   = null;

    if(!abstract_) {
      start = il.getStart();
      end   = il.getEnd();

      /* Add local variables, namely the implicit `this' and the arguments
       */
      if(!isStatic() && (class_name != null)) { // Instance method -> `this' is local var 0
        addLocalVariable("this", new ObjectType(class_name), start, end);
      }
    }

    if(arg_types != null) {
      int size = arg_types.length;

      for(int i=0; i < size; i++) {
        if(Type.VOID == arg_types[i]) {
          throw new ClassGenException("'void' is an illegal argument type for a method");
        }
      }

      if(arg_names != null) { // Names for variables provided?
        if(size != arg_names.length)
          throw new ClassGenException("Mismatch in argument array lengths: " +
                                      size + " vs. " + arg_names.length);
      } else { // Give them dummy names
        arg_names = new String[size];

        for(int i=0; i < size; i++)
          arg_names[i] = "arg" + i;

        setArgumentNames(arg_names);
      }

      if(!abstract_) {
        for(int i=0; i < size; i++) {
          addLocalVariable(arg_names[i], arg_types[i], start, end);
        }
      }
    }
  }

  /**
   * Instantiate from existing method.
   *
   * @param m method
   * @param class_name class name containing this method
   * @param cp constant pool
   */
  public MethodGen(Method m, String class_name, ConstantPoolGen cp) {
    this(m.getAccessFlags(), Type.getReturnType(m.getSignature()),
         Type.getArgumentTypes(m.getSignature()), null /* may be overridden anyway */,
         m.getName(), class_name,
         ((m.getAccessFlags() & (Constants.ACC_ABSTRACT | Constants.ACC_NATIVE)) == 0)?
         new InstructionList(m.getCode().getCode()) : null,
         cp);

    Attribute[] attributes = m.getAttributes();
    for(int i=0; i < attributes.length; i++) {
      Attribute a = attributes[i];

      if(a instanceof Code) {
        Code c = (Code)a;
        setMaxStack(c.getMaxStack());
        setMaxLocals(c.getMaxLocals());

        CodeException[] ces = c.getExceptionTable();

        if(ces != null) {
          for(int j=0; j < ces.length; j++) {
            CodeException ce     = ces[j];
            int           type   = ce.getCatchType();
            ObjectType    c_type = null;

            if(type > 0) {
              String cen = m.getConstantPool().getConstantString(type, Constants.CONSTANT_Class);
              c_type = new ObjectType(cen);
            }

            int end_pc = ce.getEndPC();
            int length = m.getCode().getCode().length;

            InstructionHandle end;

            if(length == end_pc) { // May happen, because end_pc is exclusive
              end = il.getEnd();
            } else {
              end = il.findHandle(end_pc);
              end = end.getPrev(); // Make it inclusive
            }

            addExceptionHandler(il.findHandle(ce.getStartPC()), end,
                                il.findHandle(ce.getHandlerPC()), c_type);
          }
        }

        Attribute[] c_attributes = c.getAttributes();
        for(int j=0; j < c_attributes.length; j++) {
          a = c_attributes[j];

          if(a instanceof LineNumberTable) {
            LineNumber[] ln = ((LineNumberTable)a).getLineNumberTable();

            for(int k=0; k < ln.length; k++) {
              LineNumber l = ln[k];
              addLineNumber(il.findHandle(l.getStartPC()), l.getLineNumber());
            }
          } else if(a instanceof LocalVariableTable) {
            LocalVariable[] lv = ((LocalVariableTable)a).getLocalVariableTable();

            removeLocalVariables();

            for(int k=0; k < lv.length; k++) {
              LocalVariable     l     = lv[k];
              InstructionHandle start = il.findHandle(l.getStartPC());
              InstructionHandle end   = il.findHandle(l.getStartPC() + l.getLength());

              // Repair malformed handles
              if(null == start) {
                start = il.getStart();
              }

              if(null == end) {
                end = il.getEnd();
              }

              addLocalVariable(l.getName(), Type.getType(l.getSignature()),
                               l.getIndex(), start, end);
            }
          } else if (a instanceof LocalVariableTypeTable) {
             LocalVariable[] lv = ((LocalVariableTypeTable) a).getLocalVariableTypeTable();
             removeLocalVariables();
             for (int k = 0; k < lv.length; k++) {
                 LocalVariable l = lv[k];
                 InstructionHandle start = il.findHandle(l.getStartPC());
                 InstructionHandle end = il.findHandle(l.getStartPC() + l.getLength());
                 // Repair malformed handles
                 if (null == start) {
                     start = il.getStart();
                 }
                 if (null == end) {
                     end = il.getEnd();
                 }
                 addLocalVariable(l.getName(), Type.getType(l.getSignature()), l
                         .getIndex(), start, end);
              }
          } else
            addCodeAttribute(a);
        }
      } else if(a instanceof ExceptionTable) {
        String[] names = ((ExceptionTable)a).getExceptionNames();
        for(int j=0; j < names.length; j++)
          addException(names[j]);
      } else
        addAttribute(a);
    }
  }

  /**
   * Adds a local variable to this method.
   *
   * @param name variable name
   * @param type variable type
   * @param slot the index of the local variable, if type is long or double, the next available
   * index is slot+2
   * @param start from where the variable is valid
   * @param end until where the variable is valid
   * @return new local variable object
   * @see LocalVariable
   */
  public LocalVariableGen addLocalVariable(String name, Type type, int slot,
                                           InstructionHandle start,
                                           InstructionHandle end) {
    byte t = type.getType();

    if(t != Constants.T_ADDRESS) {
      int  add = type.getSize();

      if(slot + add > max_locals)
        max_locals = slot + add;

      LocalVariableGen l = new LocalVariableGen(slot, name, type, start, end);
      int i;

      if((i = variable_vec.indexOf(l)) >= 0) // Overwrite if necessary
        variable_vec.set(i, l);
      else
        variable_vec.add(l);

      return l;
    } else {
      throw new IllegalArgumentException("Can not use " + type +
                                         " as type for local variable");

    }
  }

  /**
   * Adds a local variable to this method and assigns an index automatically.
   *
   * @param name variable name
   * @param type variable type
   * @param start from where the variable is valid, if this is null,
   * it is valid from the start
   * @param end until where the variable is valid, if this is null,
   * it is valid to the end
   * @return new local variable object
   * @see LocalVariable
   */
  public LocalVariableGen addLocalVariable(String name, Type type,
                                           InstructionHandle start,
                                           InstructionHandle end) {
    return addLocalVariable(name, type, max_locals, start, end);
  }

  /**
   * Remove a local variable, its slot will not be reused, if you do not use addLocalVariable
   * with an explicit index argument.
   */
  public void removeLocalVariable(LocalVariableGen l) {
    variable_vec.remove(l);
  }

  /**
   * Remove all local variables.
   */
  public void removeLocalVariables() {
    variable_vec.clear();
  }

  /**
   * Sort local variables by index
   */
  private static final void sort(LocalVariableGen[] vars, int l, int r) {
    int i = l, j = r;
    int m = vars[(l + r) / 2].getIndex();
    LocalVariableGen h;

    do {
      while(vars[i].getIndex() < m) i++;
      while(m < vars[j].getIndex()) j--;

      if(i <= j) {
        h=vars[i]; vars[i]=vars[j]; vars[j]=h; // Swap elements
        i++; j--;
      }
    } while(i <= j);

    if(l < j) sort(vars, l, j);
    if(i < r) sort(vars, i, r);
  }

  /*
   * If the range of the variable has not been set yet, it will be set to be valid from
   * the start to the end of the instruction list.
   *
   * @return array of declared local variables sorted by index
   */
  public LocalVariableGen[] getLocalVariables() {
    int                size = variable_vec.size();
    LocalVariableGen[] lg   = new LocalVariableGen[size];
    variable_vec.toArray(lg);

    for(int i=0; i < size; i++) {
      if(lg[i].getStart() == null)
        lg[i].setStart(il.getStart());

      if(lg[i].getEnd() == null)
        lg[i].setEnd(il.getEnd());
    }

    if(size > 1)
      sort(lg, 0, size - 1);

    return lg;
  }

  /**
   * @return `LocalVariableTable' attribute of all the local variables of this method.
   */
  public LocalVariableTable getLocalVariableTable(ConstantPoolGen cp) {
    LocalVariableGen[] lg   = getLocalVariables();
    int                size = lg.length;
    LocalVariable[]    lv   = new LocalVariable[size];

    for(int i=0; i < size; i++)
      lv[i] = lg[i].getLocalVariable(cp);

    return new LocalVariableTable(cp.addUtf8("LocalVariableTable"),
                                  2 + lv.length * 10, lv, cp.getConstantPool());
  }

  /**
   * Give an instruction a line number corresponding to the source code line.
   *
   * @param ih instruction to tag
   * @return new line number object
   * @see LineNumber
   */
  public LineNumberGen addLineNumber(InstructionHandle ih, int src_line) {
    LineNumberGen l = new LineNumberGen(ih, src_line);
    line_number_vec.add(l);
    return l;
  }

  /**
   * Remove a line number.
   */
  public void removeLineNumber(LineNumberGen l) {
    line_number_vec.remove(l);
  }

  /**
   * Remove all line numbers.
   */
  public void removeLineNumbers() {
    line_number_vec.clear();
  }

  /*
   * @return array of line numbers
   */
  public LineNumberGen[] getLineNumbers() {
    LineNumberGen[] lg = new LineNumberGen[line_number_vec.size()];
    line_number_vec.toArray(lg);
    return lg;
  }

  /**
   * @return `LineNumberTable' attribute of all the local variables of this method.
   */
  public LineNumberTable getLineNumberTable(ConstantPoolGen cp) {
    int          size = line_number_vec.size();
    LineNumber[] ln   = new LineNumber[size];

    try {
      for(int i=0; i < size; i++)
        ln[i] = ((LineNumberGen)line_number_vec.get(i)).getLineNumber();
    } catch(ArrayIndexOutOfBoundsException e) {} // Never occurs

    return new LineNumberTable(cp.addUtf8("LineNumberTable"),
                               2 + ln.length * 4, ln, cp.getConstantPool());
  }

  /**
   * Add an exception handler, i.e., specify region where a handler is active and an
   * instruction where the actual handling is done.
   *
   * @param start_pc Start of region (inclusive)
   * @param end_pc End of region (inclusive)
   * @param handler_pc Where handling is done
   * @param catch_type class type of handled exception or null if any
   * exception is handled
   * @return new exception handler object
   */
  public CodeExceptionGen addExceptionHandler(InstructionHandle start_pc,
                                              InstructionHandle end_pc,
                                              InstructionHandle handler_pc,
                                              ObjectType catch_type) {
    if((start_pc == null) || (end_pc == null) || (handler_pc == null))
      throw new ClassGenException("Exception handler target is null instruction");

    CodeExceptionGen c = new CodeExceptionGen(start_pc, end_pc,
                                              handler_pc, catch_type);
    exception_vec.add(c);
    return c;
  }

  /**
   * Remove an exception handler.
   */
  public void removeExceptionHandler(CodeExceptionGen c) {
    exception_vec.remove(c);
  }

  /**
   * Remove all line numbers.
   */
  public void removeExceptionHandlers() {
    exception_vec.clear();
  }

  /*
   * @return array of declared exception handlers
   */
  public CodeExceptionGen[] getExceptionHandlers() {
    CodeExceptionGen[] cg   = new CodeExceptionGen[exception_vec.size()];
    exception_vec.toArray(cg);
    return cg;
  }

  /**
   * @return code exceptions for `Code' attribute
   */
  private CodeException[] getCodeExceptions() {
    int             size  = exception_vec.size();
    CodeException[] c_exc = new CodeException[size];

    try {
      for(int i=0; i < size; i++) {
        CodeExceptionGen c = (CodeExceptionGen)exception_vec.get(i);
        c_exc[i] = c.getCodeException(cp);
      }
    } catch(ArrayIndexOutOfBoundsException e) {}

    return c_exc;
  }

  /**
   * Add an exception possibly thrown by this method.
   *
   * @param class_name (fully qualified) name of exception
   */
  public void addException(String class_name) {
    throws_vec.add(class_name);
  }

  /**
   * Remove an exception.
   */
  public void removeException(String c) {
    throws_vec.remove(c);
  }

  /**
   * Remove all exceptions.
   */
  public void removeExceptions() {
    throws_vec.clear();
  }

  /*
   * @return array of thrown exceptions
   */
  public String[] getExceptions() {
    String[] e = new String[throws_vec.size()];
    throws_vec.toArray(e);
    return e;
  }

  /**
   * @return `Exceptions' attribute of all the exceptions thrown by this method.
   */
  private ExceptionTable getExceptionTable(ConstantPoolGen cp) {
    int   size = throws_vec.size();
    int[] ex   = new int[size];

    try {
      for(int i=0; i < size; i++)
        ex[i] = cp.addClass((String)throws_vec.get(i));
    } catch(ArrayIndexOutOfBoundsException e) {}

    return new ExceptionTable(cp.addUtf8("Exceptions"),
                              2 + 2 * size, ex, cp.getConstantPool());
  }

  /**
   * Add an attribute to the code. Currently, the JVM knows about the
   * LineNumberTable, LocalVariableTable and StackMap attributes,
   * where the former two will be generated automatically and the
   * latter is used for the MIDP only. Other attributes will be
   * ignored by the JVM but do no harm.
   *
   * @param a attribute to be added
   */
  public void addCodeAttribute(Attribute a) { code_attrs_vec.add(a); }

  /**
   * Remove a code attribute.
   */
  public void removeCodeAttribute(Attribute a) { code_attrs_vec.remove(a); }

  /**
   * Remove all code attributes.
   */
  public void removeCodeAttributes() {
    code_attrs_vec.clear();
  }

  /**
   * @return all attributes of this method.
   */
  public Attribute[] getCodeAttributes() {
    Attribute[] attributes = new Attribute[code_attrs_vec.size()];
    code_attrs_vec.toArray(attributes);
    return attributes;
  }

  /**
   * Get method object. Never forget to call setMaxStack() or setMaxStack(max), respectively,
   * before calling this method (the same applies for max locals).
   *
   * @return method object
   */
  public Method getMethod() {
    String signature       = getSignature();
    int    name_index      = cp.addUtf8(name);
    int    signature_index = cp.addUtf8(signature);

    /* Also updates positions of instructions, i.e., their indices
     */
    byte[] byte_code = null;

    if(il != null)
      byte_code = il.getByteCode();

    LineNumberTable    lnt = null;
    LocalVariableTable lvt = null;

    /* Create LocalVariableTable and LineNumberTable attributes (for debuggers, e.g.)
     */
    if((variable_vec.size() > 0) && !strip_attributes)
      addCodeAttribute(lvt = getLocalVariableTable(cp));

    if((line_number_vec.size() > 0) && !strip_attributes)
      addCodeAttribute(lnt = getLineNumberTable(cp));

    Attribute[] code_attrs = getCodeAttributes();

    /* Each attribute causes 6 additional header bytes
     */
    int                attrs_len  = 0;
    for(int i=0; i < code_attrs.length; i++)
      attrs_len += (code_attrs[i].getLength() + 6);

    CodeException[] c_exc   = getCodeExceptions();
    int             exc_len = c_exc.length * 8; // Every entry takes 8 bytes

    Code code = null;

    if((il != null) && !isAbstract()) {
      // Remove any stale code attribute
      Attribute[] attributes = getAttributes();
      for(int i=0; i < attributes.length; i++) {
        Attribute a = attributes[i];

        if(a instanceof Code)
          removeAttribute(a);
      }

      code = new Code(cp.addUtf8("Code"),
                      8 + byte_code.length + // prologue byte code
                      2 + exc_len +          // exceptions
                      2 + attrs_len,         // attributes
                      max_stack, max_locals,
                      byte_code, c_exc,
                      code_attrs,
                      cp.getConstantPool());

      addAttribute(code);
    }

    ExceptionTable et = null;

    if(throws_vec.size() > 0)
      addAttribute(et = getExceptionTable(cp)); // Add `Exceptions' if there are "throws" clauses

    Method m = new Method(access_flags, name_index, signature_index,
                          getAttributes(), cp.getConstantPool());

    // Undo effects of adding attributes
    if(lvt != null)  removeCodeAttribute(lvt);
    if(lnt != null)  removeCodeAttribute(lnt);
    if(code != null) removeAttribute(code);
    if(et != null)   removeAttribute(et);

    return m;
  }

  /**
   * Remove all NOPs from the instruction list (if possible) and update every
   * object refering to them, i.e., branch instructions, local variables and
   * exception handlers.
   */
  public void removeNOPs() {
    if(il != null) {
      InstructionHandle next;
      /* Check branch instructions.
       */
      for(InstructionHandle ih = il.getStart(); ih != null; ih = next) {
        next = ih.next;

        if((next != null) && (ih.getInstruction() instanceof NOP)) {
          try {
            il.delete(ih);
          } catch(TargetLostException e) {
            InstructionHandle[] targets = e.getTargets();

            for(int i=0; i < targets.length; i++) {
              InstructionTargeter[] targeters = targets[i].getTargeters();

              for(int j=0; j < targeters.length; j++)
                targeters[j].updateTarget(targets[i], next);
            }
          }
        }
      }
    }
  }

  /**
   * Set maximum number of local variables.
   */
  public void   setMaxLocals(int m)  { max_locals = m; }
  public int    getMaxLocals()       { return max_locals; }

  /**
   * Set maximum stack size for this method.
   */
  public void   setMaxStack(int m)  { max_stack = m; }
  public int    getMaxStack()       { return max_stack; }

  /** @return class that contains this method
   */
  public String getClassName()                     { return class_name; }
  public void   setClassName(String class_name)    { this.class_name = class_name; }

  public void   setReturnType(Type return_type)    { setType(return_type); }
  public Type   getReturnType()                    { return getType(); }

  public void   setArgumentTypes(Type[] arg_types)  { this.arg_types = arg_types; }
  public Type[] getArgumentTypes()                  { return (Type[])arg_types.clone(); }
  public void   setArgumentType(int i, Type type)       { arg_types[i] = type; }
  public Type   getArgumentType(int i)                  { return arg_types[i]; }

  public void     setArgumentNames(String[] arg_names) { this.arg_names = arg_names; }
  public String[] getArgumentNames()                   { return (String[])arg_names.clone(); }
  public void     setArgumentName(int i, String name)     { arg_names[i] = name; }
  public String   getArgumentName(int i)                  { return arg_names[i]; }

  public InstructionList getInstructionList()                    { return il; }
  public void            setInstructionList(InstructionList il)  { this.il = il; }

  public String getSignature() {
    return Type.getMethodSignature(type, arg_types);
  }

  /**
   * Computes max. stack size by performing control flow analysis.
   */
  public void setMaxStack() {
    if(il != null)
      max_stack = getMaxStack(cp, il, getExceptionHandlers());
    else
      max_stack = 0;
  }

  /**
   * Compute maximum number of local variables.
   */
  public void setMaxLocals() {
    if(il != null) {
      int max = isStatic()? 0 : 1;

      if(arg_types != null)
        for(int i=0; i < arg_types.length; i++)
          max += arg_types[i].getSize();

      for(InstructionHandle ih = il.getStart(); ih != null; ih = ih.getNext()) {
        Instruction ins = ih.getInstruction();

        if((ins instanceof LocalVariableInstruction) ||
           (ins instanceof RET) || (ins instanceof IINC))
        {
          int index = ((IndexedInstruction)ins).getIndex() +
            ((TypedInstruction)ins).getType(cp).getSize();

          if(index > max)
            max = index;
        }
      }

      max_locals = max;
    } else
      max_locals = 0;
  }

  /** Do not/Do produce attributes code attributesLineNumberTable and
   * LocalVariableTable, like javac -O
   */
  public void stripAttributes(boolean flag) { strip_attributes = flag; }

  static final class BranchTarget {
    InstructionHandle target;
    int               stackDepth;

    BranchTarget(InstructionHandle target, int stackDepth) {
      this.target = target;
      this.stackDepth = stackDepth;
    }
  }

  static final class BranchStack {
    Stack     branchTargets  = new Stack();
    Hashtable visitedTargets = new Hashtable();

    public void push(InstructionHandle target, int stackDepth) {
      if(visited(target))
        return;

      branchTargets.push(visit(target, stackDepth));
    }

    public BranchTarget pop() {
      if(!branchTargets.empty()) {
        BranchTarget bt = (BranchTarget) branchTargets.pop();
        return bt;
      }

      return null;
    }

    private final BranchTarget visit(InstructionHandle target, int stackDepth) {
      BranchTarget bt = new BranchTarget(target, stackDepth);
      visitedTargets.put(target, bt);

      return bt;
    }

    private final boolean visited(InstructionHandle target) {
      return (visitedTargets.get(target) != null);
    }
  }

  /**
   * Computes stack usage of an instruction list by performing control flow analysis.
   *
   * @return maximum stack depth used by method
   */
  public static int getMaxStack(ConstantPoolGen cp, InstructionList il, CodeExceptionGen[] et) {
    BranchStack branchTargets = new BranchStack();

    /* Initially, populate the branch stack with the exception
     * handlers, because these aren't (necessarily) branched to
     * explicitly. in each case, the stack will have depth 1,
     * containing the exception object.
     */
    for (int i = 0; i < et.length; i++) {
      InstructionHandle handler_pc = et[i].getHandlerPC();
      if (handler_pc != null)
        branchTargets.push(handler_pc, 1);
    }

    int               stackDepth = 0, maxStackDepth = 0;
    InstructionHandle ih         = il.getStart();

    while(ih != null) {
      Instruction instruction = ih.getInstruction();
      short opcode = instruction.getOpcode();
      int delta = instruction.produceStack(cp) - instruction.consumeStack(cp);

      stackDepth += delta;
      if(stackDepth > maxStackDepth)
        maxStackDepth = stackDepth;

      // choose the next instruction based on whether current is a branch.
      if(instruction instanceof BranchInstruction) {
        BranchInstruction branch = (BranchInstruction) instruction;
        if(instruction instanceof Select) {
          // explore all of the select's targets. the default target is handled below.
          Select select = (Select) branch;
          InstructionHandle[] targets = select.getTargets();
          for (int i = 0; i < targets.length; i++)
            branchTargets.push(targets[i], stackDepth);
          // nothing to fall through to.
          ih = null;
        } else if(!(branch instanceof IfInstruction)) {
          // if an instruction that comes back to following PC,
          // push next instruction, with stack depth reduced by 1.
          if(opcode == Constants.JSR || opcode == Constants.JSR_W)
            branchTargets.push(ih.getNext(), stackDepth - 1);
          ih = null;
        }
        // for all branches, the target of the branch is pushed on the branch stack.
        // conditional branches have a fall through case, selects don't, and
        // jsr/jsr_w return to the next instruction.
        branchTargets.push(branch.getTarget(), stackDepth);
      } else {
        // check for instructions that terminate the method.
        if(opcode == Constants.ATHROW || opcode == Constants.RET ||
           (opcode >= Constants.IRETURN && opcode <= Constants.RETURN))
          ih = null;
      }
      // normal case, go to the next instruction.
      if(ih != null)
        ih = ih.getNext();
      // if we have no more instructions, see if there are any deferred branches to explore.
      if(ih == null) {
        BranchTarget bt = branchTargets.pop();
        if (bt != null) {
          ih = bt.target;
          stackDepth = bt.stackDepth;
        }
      }
    }

    return maxStackDepth;
  }

  private ArrayList observers;

  /** Add observer for this object.
   */
  public void addObserver(MethodObserver o) {
    if(observers == null)
      observers = new ArrayList();

    observers.add(o);
  }

  /** Remove observer for this object.
   */
  public void removeObserver(MethodObserver o) {
    if(observers != null)
      observers.remove(o);
  }

  /** Call notify() method on all observers. This method is not called
   * automatically whenever the state has changed, but has to be
   * called by the user after he has finished editing the object.
   */
  public void update() {
    if(observers != null)
      for(Iterator e = observers.iterator(); e.hasNext(); )
        ((MethodObserver)e.next()).notify(this);
  }

  /**
   * Return string representation close to declaration format,
   * `public static void _main(String[]) throws IOException', e.g.
   *
   * @return String representation of the method.
   */
  public final String toString() {
    String access    = Utility.accessToString(access_flags);
    String signature = Type.getMethodSignature(type, arg_types);

    signature = Utility.methodSignatureToString(signature, name, access,
                                                true, getLocalVariableTable(cp));

    StringBuffer buf = new StringBuffer(signature);

    if(throws_vec.size() > 0) {
      for(Iterator e = throws_vec.iterator(); e.hasNext(); )
        buf.append("\n\t\tthrows " + e.next());
    }

    return buf.toString();
  }

  /** @return deep copy of this method
   */
  public MethodGen copy(String class_name, ConstantPoolGen cp) {
    Method    m  = ((MethodGen)clone()).getMethod();
    MethodGen mg = new MethodGen(m, class_name, this.cp);

    if(this.cp != cp) {
      mg.setConstantPool(cp);
      mg.getInstructionList().replaceConstantPool(this.cp, cp);
    }

    return mg;
  }
}
