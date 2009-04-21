/*
 * Copyright 2002-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */


package sun.tools.javap;

import java.util.*;
import java.io.*;

import static sun.tools.javap.RuntimeConstants.*;

/**
 * Program to print information about class files
 *
 * @author  Sucheta Dambalkar
 */
public class JavapPrinter {
    JavapEnvironment env;
    ClassData cls;
    byte[] code;
    String lP= "";
    PrintWriter out;

    public JavapPrinter(InputStream cname, PrintWriter out, JavapEnvironment env){
        this.out = out;
        this.cls =  new ClassData(cname);
        this.env = env;
    }

    /**
     *  Entry point to print class file information.
     */
    public void print(){
        printclassHeader();
        printfields();
        printMethods();
        printend();
    }

    /**
     * Print a description of the class (not members).
     */
    public void printclassHeader(){
        String srcName="";
        if ((srcName = cls.getSourceName()) != "null") // requires debug info
            out.println("Compiled from " + javaclassname(srcName));

        if(cls.isInterface())   {
            // The only useful access modifier of an interface is
            // public; interfaces are always marked as abstract and
            // cannot be final.
            out.print((cls.isPublic()?"public ":"") +
                      "interface "+ javaclassname(cls.getClassName()));
        }
        else if(cls.isClass()) {
            String []accflags =  cls.getAccess();
            printAccess(accflags);
            out.print("class "+ javaclassname(cls.getClassName()));

            if(cls.getSuperClassName() != null){
                out.print(" extends " + javaclassname(cls.getSuperClassName()));
            }
        }

        String []interfacelist =  cls.getSuperInterfaces();
        if(interfacelist.length > 0){
            if(cls.isClass()) {
                out.print(" implements ");
            }
            else if(cls.isInterface()){
                out.print(" extends ");
            }

            for(int j = 0; j < interfacelist.length; j++){
                out.print(javaclassname(interfacelist[j]));

                if((j+1) < interfacelist.length) {
                    out.print(",");
                }
            }
        }

        // Print class attribute information.
        if((env.showallAttr) || (env.showVerbose)){
            printClassAttributes();
        }
        // Print verbose output.
        if(env.showVerbose){
            printverbosecls();
        }
        out.println("{");
    }

    /**
     * Print verbose output.
     */
    public void printverbosecls(){
        out.println("  minor version: "+cls.getMinor_version());
        out.println("  major version: "+cls.getMajor_version());
        out.println("  Constant pool:");
        printcp();
        env.showallAttr = true;
    }

    /**
     * Print class attribute information.
     */
    public void printClassAttributes(){
        out.println();
        AttrData[] clsattrs = cls.getAttributes();
        for(int i = 0; i < clsattrs.length; i++){
            String clsattrname = clsattrs[i].getAttrName();
            if(clsattrname.equals("SourceFile")){
                out.println("  SourceFile: "+ cls.getSourceName());
            }else if(clsattrname.equals("InnerClasses")){
                printInnerClasses();
            }else {
                printAttrData(clsattrs[i]);
            }
        }
    }

    /**
     * Print the fields
     */
    public void printfields(){
        FieldData[] fields = cls.getFields();
        for(int f = 0; f < fields.length; f++){
            String[] accflags = fields[f].getAccess();
            if(checkAccess(accflags)){
                if(!(env. showLineAndLocal || env.showDisassembled || env.showVerbose
                     ||  env.showInternalSigs || env.showallAttr)){
                    out.print("    ");
                }
                printAccess(accflags);
                out.println(fields[f].getType()+" " +fields[f].getName()+";");
                if (env.showInternalSigs) {
                    out.println("  Signature: " + (fields[f].getInternalSig()));
                }

                // print field attribute information.
                if (env.showallAttr){
                    printFieldAttributes(fields[f]);

                }
                if((env.showDisassembled) || (env.showLineAndLocal)){
                    out.println();
                }
            }
        }
    }


    /* print field attribute information. */
    public void printFieldAttributes(FieldData field){
        Vector<?> fieldattrs = field.getAttributes();
        for(int j = 0; j < fieldattrs.size(); j++){
            String fieldattrname = ((AttrData)fieldattrs.elementAt(j)).getAttrName();
            if(fieldattrname.equals("ConstantValue")){
                printConstantValue(field);
            }else if (fieldattrname.equals("Deprecated")){
                out.println("Deprecated: "+ field.isDeprecated());
            }else if (fieldattrname.equals("Synthetic")){
                out.println("  Synthetic: "+ field.isSynthetic());
            }else {
                printAttrData((AttrData)fieldattrs.elementAt(j));
            }
        }
        out.println();
    }

    /**
     * Print the methods
     */
    public void printMethods(){
        MethodData[] methods = cls.getMethods();
        for(int m = 0; m < methods.length; m++){
            String[] accflags = methods[m].getAccess();
            if(checkAccess(accflags)){
                if(!(env. showLineAndLocal || env.showDisassembled || env.showVerbose
                     ||  env.showInternalSigs || env.showallAttr)){
                    out.print("    ");
                }
                printMethodSignature(methods[m], accflags);
                printExceptions(methods[m]);
                out.println(";");

                // Print internal signature of method.
                if (env.showInternalSigs){
                    out.println("  Signature: " + (methods[m].getInternalSig()));
                }

                //Print disassembled code.
                if(env.showDisassembled && ! env.showallAttr) {
                    printcodeSequence(methods[m]);
                    printExceptionTable(methods[m]);
                    out.println();
                }

                // Print line and local variable attribute information.
                if (env.showLineAndLocal) {
                    printLineNumTable(methods[m]);
                    printLocVarTable(methods[m]);
                    out.println();
                }

                // Print  method attribute information.
                if (env.showallAttr){
                    printMethodAttributes(methods[m]);
                }
            }
        }
    }

    /**
     * Print method signature.
     */
    public void printMethodSignature(MethodData method, String[] accflags){
        printAccess(accflags);

        if((method.getName()).equals("<init>")){
            out.print(javaclassname(cls.getClassName()));
            out.print(method.getParameters());
        }else if((method.getName()).equals("<clinit>")){
            out.print("{}");
        }else{
            out.print(method.getReturnType()+" ");
            out.print(method.getName());
            out.print(method.getParameters());
        }
    }

    /**
     * print method attribute information.
     */
    public void printMethodAttributes(MethodData method){
        Vector<?> methodattrs = method.getAttributes();
        Vector<?> codeattrs =  method.getCodeAttributes();
        for(int k = 0; k < methodattrs.size(); k++){
            String methodattrname = ((AttrData)methodattrs.elementAt(k)).getAttrName();
            if(methodattrname.equals("Code")){
                printcodeSequence(method);
                printExceptionTable(method);
                for(int c = 0; c < codeattrs.size(); c++){
                    String codeattrname = ((AttrData)codeattrs.elementAt(c)).getAttrName();
                    if(codeattrname.equals("LineNumberTable")){
                        printLineNumTable(method);
                    }else if(codeattrname.equals("LocalVariableTable")){
                        printLocVarTable(method);
                    }else if(codeattrname.equals("StackMapTable")) {
                        // Java SE JSR 202 stack map tables
                        printStackMapTable(method);
                    }else if(codeattrname.equals("StackMap")) {
                        // Java ME CLDC stack maps
                        printStackMap(method);
                    } else {
                        printAttrData((AttrData)codeattrs.elementAt(c));
                    }
                }
            }else if(methodattrname.equals("Exceptions")){
                out.println("  Exceptions: ");
                printExceptions(method);
            }else if (methodattrname.equals("Deprecated")){
                out.println("  Deprecated: "+ method.isDeprecated());
            }else if (methodattrname.equals("Synthetic")){
                out.println("  Synthetic: "+ method.isSynthetic());
            }else {
                printAttrData((AttrData)methodattrs.elementAt(k));
            }
        }
        out.println();
    }

    /**
     * Print exceptions.
     */
    public void printExceptions(MethodData method){
        int []exc_index_table = method.get_exc_index_table();
        if (exc_index_table != null) {
            if(!(env. showLineAndLocal || env.showDisassembled || env.showVerbose
                 ||  env.showInternalSigs || env.showallAttr)){
                out.print("    ");
            }
            out.print("   throws ");
            int k;
            int l = exc_index_table.length;

            for (k=0; k<l; k++) {
                out.print(javaclassname(cls.getClassName(exc_index_table[k])));
                if (k<l-1) out.print(", ");
            }
        }
    }

    /**
     * Print code sequence.
     */
    public void  printcodeSequence(MethodData method){
        code = method.getCode();
        if(code != null){
            out.println("  Code:");
            if(env.showVerbose){
                printVerboseHeader(method);
            }

            for (int pc=0; pc < code.length; ) {
                out.print("   "+pc+":\t");
                pc=pc+printInstr(pc);
                out.println();
            }
        }
    }

    /**
     * Print instructions.
     */
    public int printInstr(int pc){
        int opcode = getUbyte(pc);
        int opcode2;
        String mnem;
        switch (opcode) {
        case opc_nonpriv:
        case opc_priv:
            opcode2 = getUbyte(pc+1);
            mnem=Tables.opcName((opcode<<8)+opcode2);
            if (mnem==null)
                // assume all (even nonexistent) priv and nonpriv instructions
                // are 2 bytes long
                mnem=Tables.opcName(opcode)+" "+opcode2;
            out.print(mnem);
            return 2;
        case opc_wide: {
            opcode2 = getUbyte(pc+1);
            mnem=Tables.opcName((opcode<<8)+opcode2);
            if (mnem==null) {
                // nonexistent opcode - but we have to print something
                out.print("bytecode "+opcode);
                return 1;
            }
            out.print(mnem+" "+getUShort(pc+2));
            if (opcode2==opc_iinc) {
                out.print(", "+getShort(pc+4));
                return 6;
            }
            return 4;
        }
        }
        mnem=Tables.opcName(opcode);
        if (mnem==null) {
            // nonexistent opcode - but we have to print something
            out.print("bytecode "+opcode);
            return 1;
        }
        if (opcode>opc_jsr_w) {
            // pseudo opcodes should be printed as bytecodes
            out.print("bytecode "+opcode);
            return 1;
        }
        out.print(Tables.opcName(opcode));
        switch (opcode) {
        case opc_aload: case opc_astore:
        case opc_fload: case opc_fstore:
        case opc_iload: case opc_istore:
        case opc_lload: case opc_lstore:
        case opc_dload: case opc_dstore:
        case opc_ret:
            out.print("\t"+getUbyte(pc+1));
            return  2;
        case opc_iinc:
            out.print("\t"+getUbyte(pc+1)+", "+getbyte(pc+2));
            return  3;
        case opc_tableswitch:{
            int tb=align(pc+1);
            int default_skip = getInt(tb); /* default skip pamount */
            int low = getInt(tb+4);
            int high = getInt(tb+8);
            int count = high - low;
            out.print("{ //"+low+" to "+high);
            for (int i = 0; i <= count; i++)
                out.print( "\n\t\t" + (i+low) + ": "+lP+(pc+getInt(tb+12+4*i))+";");
            out.print("\n\t\tdefault: "+lP+(default_skip + pc) + " }");
            return tb-pc+16+count*4;
        }

        case opc_lookupswitch:{
            int tb=align(pc+1);
            int default_skip = getInt(tb);
            int npairs = getInt(tb+4);
            out.print("{ //"+npairs);
            for (int i = 1; i <= npairs; i++)
                out.print("\n\t\t"+getInt(tb+i*8)
                                 +": "+lP+(pc+getInt(tb+4+i*8))+";"
                                 );
            out.print("\n\t\tdefault: "+lP+(default_skip + pc) + " }");
            return tb-pc+(npairs+1)*8;
        }
        case opc_newarray:
            int type=getUbyte(pc+1);
            switch (type) {
            case T_BOOLEAN:out.print(" boolean");break;
            case T_BYTE:   out.print(" byte");   break;
            case T_CHAR:   out.print(" char");   break;
            case T_SHORT:  out.print(" short");  break;
            case T_INT:    out.print(" int");    break;
            case T_LONG:   out.print(" long");   break;
            case T_FLOAT:  out.print(" float");  break;
            case T_DOUBLE: out.print(" double"); break;
            case T_CLASS:  out.print(" class"); break;
            default:       out.print(" BOGUS TYPE:"+type);
            }
            return 2;

        case opc_anewarray: {
            int index =  getUShort(pc+1);
            out.print("\t#"+index+"; //");
            PrintConstant(index);
            return 3;
        }

        case opc_sipush:
            out.print("\t"+getShort(pc+1));
            return 3;

        case opc_bipush:
            out.print("\t"+getbyte(pc+1));
            return 2;

        case opc_ldc: {
            int index = getUbyte(pc+1);
            out.print("\t#"+index+"; //");
            PrintConstant(index);
            return 2;
        }

        case opc_ldc_w: case opc_ldc2_w:
        case opc_instanceof: case opc_checkcast:
        case opc_new:
        case opc_putstatic: case opc_getstatic:
        case opc_putfield: case opc_getfield:
        case opc_invokevirtual:
        case opc_invokespecial:
        case opc_invokestatic: {
            int index = getUShort(pc+1);
            out.print("\t#"+index+"; //");
            PrintConstant(index);
            return 3;
        }

        case opc_invokeinterface: {
            int index = getUShort(pc+1), nargs=getUbyte(pc+3);
            out.print("\t#"+index+",  "+nargs+"; //");
            PrintConstant(index);
            return 5;
        }

        case opc_multianewarray: {
            int index = getUShort(pc+1), dimensions=getUbyte(pc+3);
            out.print("\t#"+index+",  "+dimensions+"; //");
            PrintConstant(index);
            return 4;
        }
        case opc_jsr: case opc_goto:
        case opc_ifeq: case opc_ifge: case opc_ifgt:
        case opc_ifle: case opc_iflt: case opc_ifne:
        case opc_if_icmpeq: case opc_if_icmpne: case opc_if_icmpge:
        case opc_if_icmpgt: case opc_if_icmple: case opc_if_icmplt:
        case opc_if_acmpeq: case opc_if_acmpne:
        case opc_ifnull: case opc_ifnonnull:
            out.print("\t"+lP+(pc + getShort(pc+1)) );
            return 3;

        case opc_jsr_w:
        case opc_goto_w:
            out.print("\t"+lP+(pc + getInt(pc+1)));
            return 5;

        default:
            return 1;
        }
    }
    /**
     * Print code attribute details.
     */
    public void printVerboseHeader(MethodData method) {
        int argCount = method.getArgumentlength();
        if (!method.isStatic())
            ++argCount;  // for 'this'

        out.println("   Stack=" + method.getMaxStack()
                           + ", Locals=" + method.getMaxLocals()
                           + ", Args_size=" + argCount);

    }


    /**
     * Print the exception table for this method code
     */
    void printExceptionTable(MethodData method){//throws IOException
        Vector<?> exception_table = method.getexception_table();
        if (exception_table.size() > 0) {
            out.println("  Exception table:");
            out.println("   from   to  target type");
            for (int idx = 0; idx < exception_table.size(); ++idx) {
                TrapData handler = (TrapData)exception_table.elementAt(idx);
                printFixedWidthInt(handler.start_pc, 6);
                printFixedWidthInt(handler.end_pc, 6);
                printFixedWidthInt(handler.handler_pc, 6);
                out.print("   ");
                int catch_cpx = handler.catch_cpx;
                if (catch_cpx == 0) {
                    out.println("any");
                }else {
                    out.print("Class ");
                    out.println(cls.getClassName(catch_cpx));
                    out.println("");
                }
            }
        }
    }

    /**
     * Print LineNumberTable attribute information.
     */
    public void printLineNumTable(MethodData method) {
        int numlines = method.getnumlines();
        Vector<?> lin_num_tb = method.getlin_num_tb();
        if( lin_num_tb.size() > 0){
            out.println("  LineNumberTable: ");
            for (int i=0; i<numlines; i++) {
                LineNumData linnumtb_entry=(LineNumData)lin_num_tb.elementAt(i);
                out.println("   line " + linnumtb_entry.line_number + ": "
                               + linnumtb_entry.start_pc);
            }
        }
        out.println();
    }

    /**
     * Print LocalVariableTable attribute information.
     */
    public void printLocVarTable(MethodData method){
        int siz = method.getloc_var_tbsize();
        if(siz > 0){
            out.println("  LocalVariableTable: ");
            out.print("   ");
            out.println("Start  Length  Slot  Name   Signature");
        }
        Vector<?> loc_var_tb = method.getloc_var_tb();

        for (int i=0; i<siz; i++) {
            LocVarData entry=(LocVarData)loc_var_tb.elementAt(i);

            out.println("   "+entry.start_pc+"      "+entry.length+"      "+
                               entry.slot+"    "+cls.StringValue(entry.name_cpx)  +
                               "       "+cls.StringValue(entry.sig_cpx));
        }
        out.println();
    }

    /**
     * Print StackMap attribute information.
     */
    public void printStackMap(MethodData method) {
        StackMapData[] stack_map_tb = method.getStackMap();
        int number_of_entries = stack_map_tb.length;
        if (number_of_entries > 0) {
            out.println("  StackMap: number_of_entries = " + number_of_entries);

            for (StackMapData frame : stack_map_tb) {
                frame.print(this);
            }
        }
       out.println();
    }

    /**
     * Print StackMapTable attribute information.
     */
    public void printStackMapTable(MethodData method) {
        StackMapTableData[] stack_map_tb = method.getStackMapTable();
        int number_of_entries = stack_map_tb.length;
        if (number_of_entries > 0) {
            out.println("  StackMapTable: number_of_entries = " + number_of_entries);

            for (StackMapTableData frame : stack_map_tb) {
                frame.print(this);
            }
        }
        out.println();
    }

    void printMap(String name, int[] map) {
        out.print(name);
        for (int i=0; i<map.length; i++) {
            int fulltype = map[i];
            int type = fulltype & 0xFF;
            int argument = fulltype >> 8;
            switch (type) {
                case ITEM_Object:
                    out.print(" ");
                    PrintConstant(argument);
                    break;
                case ITEM_NewObject:
                    out.print(" " + Tables.mapTypeName(type));
                    out.print(" " + argument);
                    break;
                default:
                    out.print(" " + Tables.mapTypeName(type));
            }
            out.print( (i==(map.length-1)? ' ' : ','));
        }
        out.println("]");
    }

    /**
     * Print ConstantValue attribute information.
     */
    public void printConstantValue(FieldData field){
        out.print("  Constant value: ");
        int cpx = (field.getConstantValueIndex());
        byte tag=0;
        try {
            tag=cls.getTag(cpx);

        } catch (IndexOutOfBoundsException e) {
            out.print("Error:");
            return;
        }
        switch (tag) {
        case CONSTANT_METHOD:
        case CONSTANT_INTERFACEMETHOD:
        case CONSTANT_FIELD: {
            CPX2 x = cls.getCpoolEntry(cpx);
            if (x.cpx1 == cls.getthis_cpx()) {
                // don't print class part for local references
                cpx=x.cpx2;
            }
        }
        }
        out.print(cls.TagString(tag)+" "+ cls.StringValue(cpx));
    }

    /**
     * Print InnerClass attribute information.
     */
    public void printInnerClasses(){//throws ioexception

        InnerClassData[] innerClasses = cls.getInnerClasses();
        if(innerClasses != null){
            if(innerClasses.length > 0){
                out.print("  ");
                out.println("InnerClass: ");
                for(int i = 0 ; i < innerClasses.length; i++){
                    out.print("   ");
                    //access
                    String[] accflags = innerClasses[i].getAccess();
                    if(checkAccess(accflags)){
                        printAccess(accflags);
                        if (innerClasses[i].inner_name_index!=0) {
                            out.print("#"+innerClasses[i].inner_name_index+"= ");
                        }
                        out.print("#"+innerClasses[i].inner_class_info_index);
                        if (innerClasses[i].outer_class_info_index!=0) {
                            out.print(" of #"+innerClasses[i].outer_class_info_index);
                        }
                        out.print("; //");
                        if (innerClasses[i].inner_name_index!=0) {
                            out.print(cls.getName(innerClasses[i].inner_name_index)+"=");
                        }
                        PrintConstant(innerClasses[i].inner_class_info_index);
                        if (innerClasses[i].outer_class_info_index!=0) {
                            out.print(" of ");
                            PrintConstant(innerClasses[i].outer_class_info_index);
                        }
                        out.println();
                    }
                }

            }
        }
    }

    /**
     * Print constant pool information.
     */
    public void printcp(){
        int cpx = 1 ;

        while (cpx < cls.getCpoolCount()) {
            out.print("const #"+cpx+" = ");
            cpx+=PrintlnConstantEntry(cpx);
        }
        out.println();
    }

    /**
     * Print constant pool entry information.
     */
    @SuppressWarnings("fallthrough")
    public int PrintlnConstantEntry(int cpx) {
        int size=1;
        byte tag=0;
        try {
            tag=cls.getTag(cpx);
        } catch (IndexOutOfBoundsException e) {
            out.println("  <Incorrect CP index>");
            return 1;
        }
        out.print(cls.StringTag(cpx)+"\t");
        Object x=cls.getCpoolEntryobj(cpx);
        if (x==null) {
            switch (tag) {
            case CONSTANT_LONG:
            case CONSTANT_DOUBLE:
                size=2;
            }
            out.println("null;");
            return size;
        }
        String str=cls.StringValue(cpx);

        switch (tag) {
        case CONSTANT_CLASS:
        case CONSTANT_STRING:
            out.println("#"+(((CPX)x).cpx)+";\t//  "+str);
            break;
        case CONSTANT_FIELD:
        case CONSTANT_METHOD:
        case CONSTANT_INTERFACEMETHOD:
            out.println("#"+((CPX2)x).cpx1+".#"+((CPX2)x).cpx2+";\t//  "+str);
            break;
        case CONSTANT_NAMEANDTYPE:
            out.println("#"+((CPX2)x).cpx1+":#"+((CPX2)x).cpx2+";//  "+str);
            break;
        case CONSTANT_LONG:
        case CONSTANT_DOUBLE:
            size=2;
            // fall through
        default:
            out.println(str+";");
        }
        return size;
    }

    /**
     * Checks access of class, field or method.
     */
    public boolean checkAccess(String accflags[]){

        boolean ispublic = false;
        boolean isprotected = false;
        boolean isprivate = false;
        boolean ispackage = false;

        for(int i= 0; i < accflags.length; i++){
            if(accflags[i].equals("public")) ispublic = true;
            else if (accflags[i].equals("protected")) isprotected = true;
            else if (accflags[i].equals("private")) isprivate = true;
        }

        if(!(ispublic || isprotected || isprivate)) ispackage = true;

        if((env.showAccess == env.PUBLIC) && (isprotected || isprivate || ispackage)) return false;
        else if((env.showAccess == env.PROTECTED) && (isprivate || ispackage)) return false;
        else if((env.showAccess == env.PACKAGE) && (isprivate)) return false;
        else return true;
    }

    /**
     * Prints access of class, field or method.
     */
    public void printAccess(String []accflags){
        for(int j = 0; j < accflags.length; j++){
            out.print(accflags[j]+" ");
        }
    }

    /**
     * Print an integer so that it takes 'length' characters in
     * the output.  Temporary until formatting code is stable.
     */
    public void printFixedWidthInt(long x, int length) {
        CharArrayWriter baStream = new CharArrayWriter();
        PrintWriter pStream = new PrintWriter(baStream);
        pStream.print(x);
        String str = baStream.toString();
        for (int cnt = length - str.length(); cnt > 0; --cnt)
            out.print(' ');
        out.print(str);
    }

    protected int getbyte (int pc) {
        return code[pc];
    }

    protected int getUbyte (int pc) {
        return code[pc]&0xFF;
    }

    int getShort (int pc) {
        return (code[pc]<<8) | (code[pc+1]&0xFF);
    }

    int getUShort (int pc) {
        return ((code[pc]<<8) | (code[pc+1]&0xFF))&0xFFFF;
    }

    protected int getInt (int pc) {
        return (getShort(pc)<<16) | (getShort(pc+2)&0xFFFF);
    }

    /**
     * Print constant value at that index.
     */
    void PrintConstant(int cpx) {
        if (cpx==0) {
            out.print("#0");
            return;
        }
        byte tag=0;
        try {
            tag=cls.getTag(cpx);

        } catch (IndexOutOfBoundsException e) {
            out.print("#"+cpx);
            return;
        }
        switch (tag) {
        case CONSTANT_METHOD:
        case CONSTANT_INTERFACEMETHOD:
        case CONSTANT_FIELD: {
            // CPX2 x=(CPX2)(cpool[cpx]);
            CPX2 x = cls.getCpoolEntry(cpx);
            if (x.cpx1 == cls.getthis_cpx()) {
                // don't print class part for local references
                cpx=x.cpx2;
            }
        }
        }
        out.print(cls.TagString(tag)+" "+ cls.StringValue(cpx));
    }

    protected static int  align (int n) {
        return (n+3) & ~3 ;
    }

    public void printend(){
        out.println("}");
        out.println();
    }

    public String javaclassname(String name){
        return name.replace('/','.');
    }

    /**
     * Print attribute data in hex.
     */
    public void printAttrData(AttrData attr){
        byte []data = attr.getData();
        int i = 0;
        int j = 0;
        out.print("  "+attr.getAttrName()+": ");
        out.println("length = " + cls.toHex(attr.datalen));

        out.print("   ");


        while (i < data.length){
            String databytestring = cls.toHex(data[i]);
            if(databytestring.equals("0x")) out.print("00");
            else if(databytestring.substring(2).length() == 1){
                out.print("0"+databytestring.substring(2));
            } else{
                out.print(databytestring.substring(2));
            }

             j++;
            if(j == 16) {
                out.println();
                out.print("   ");
                j = 0;
            }
            else out.print(" ");
            i++;
        }
        out.println();
    }
}
