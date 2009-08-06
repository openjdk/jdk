/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
package com.sun.tools.corba.se.logutil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.List;
import java.util.Queue;

public class MC {

  private static final String VERSION = "1.0";

  private static final List<String> SUN_EXCEPTION_GROUPS = Arrays.asList(new String[]
    { "SUNBASE", "ORBUTIL", "ACTIVATION", "NAMING", "INTERCEPTORS", "POA", "IOR", "UTIL" });

  private static final List<String> EXCEPTIONS = Arrays.asList(new String[]
    { "UNKNOWN", "BAD_PARAM", "NO_MEMORY", "IMP_LIMIT", "COMM_FAILURE", "INV_OBJREF", "NO_PERMISSION",
      "INTERNAL", "MARSHAL", "INITIALIZE", "NO_IMPLEMENT", "BAD_TYPECODE", "BAD_OPERATION", "NO_RESOURCES",
      "NO_RESPONSE", "PERSIST_STORE", "BAD_INV_ORDER", "TRANSIENT", "FREE_MEM", "INV_IDENT", "INV_FLAG",
      "INTF_REPOS", "BAD_CONTEXT", "OBJ_ADAPTER", "DATA_CONVERSION", "OBJECT_NOT_EXIST", "TRANSACTION_REQUIRED",
      "TRANSACTION_ROLLEDBACK", "INVALID_TRANSACTION", "INV_POLICY", "CODESET_INCOMPATIBLE", "REBIND",
      "TIMEOUT", "TRANSACTION_UNAVAILABLE", "BAD_QOS", "INVALID_ACTIVITY", "ACTIVITY_COMPLETED",
      "ACTIVITY_REQUIRED" });

  /**
   * Read the minor codes from the input file and
   * write out a resource file.
   *
   * @param inFile the file to read the codes from.
   * @param outDir the directory to write the resource file to.
   * @throws FileNotFoundException if the input file can not be found.
   * @throws IOException if an I/O error occurs.
   */
  private void makeResource(String inFile, String outDir)
  throws FileNotFoundException, IOException {
    writeResource(outDir, new Input(inFile));
  }

  /**
   * Create a new Java source file using the specified Scheme input file,
   * and writing the result to the given output directory.
   *
   * @param inFile the file to read the data from.
   * @param outDir the directory to write the Java class to.
   * @throws FileNotFoundException if the input file can not be found.
   * @throws IOException if an I/O error occurs.
   */
  private void makeClass(String inFile, String outDir)
  throws FileNotFoundException, IOException {
    writeClass(inFile, outDir, new Input(inFile));
  }

  /**
   * Writes out a Java source file using the data from the given
   * {@link Input} object.  The result is written to {@code outDir}.
   * The name of the input file is just used in the header of the
   * resulting source file.
   *
   * @param inFile the name of the file the data was read from.
   * @param outDir the directory to write the Java class to.
   * @param input the parsed input data.
   * @throws FileNotFoundException if the output file can't be written.
   */
  private void writeClass(String inFile, String outDir, Input input)
    throws FileNotFoundException {
    String packageName = input.getPackageName();
    String className = input.getClassName();
    String groupName = input.getGroupName();
    Queue<InputException> exceptions = input.getExceptions();
    FileOutputStream file = new FileOutputStream(outDir + File.separator + className + ".java");
    IndentingPrintWriter pw = new IndentingPrintWriter(file);

    writeClassHeader(inFile, groupName, pw);
    pw.printMsg("package @ ;", packageName);
    pw.println();
    pw.println("import java.util.logging.Logger ;");
    pw.println("import java.util.logging.Level ;");
    pw.println();
    pw.println("import org.omg.CORBA.OMGVMCID ;");
    pw.println( "import com.sun.corba.se.impl.util.SUNVMCID ;");
    pw.println( "import org.omg.CORBA.CompletionStatus ;");
    pw.println( "import org.omg.CORBA.SystemException ;");
    pw.println();
    pw.println( "import com.sun.corba.se.spi.orb.ORB ;");
    pw.println();
    pw.println( "import com.sun.corba.se.spi.logging.LogWrapperFactory;");
    pw.println();
    pw.println( "import com.sun.corba.se.spi.logging.LogWrapperBase;");
    pw.println();
    writeImports(exceptions, pw);
    pw.println();
    pw.indent();
    pw.printMsg("public class @ extends LogWrapperBase {", className);
    pw.println();
    pw.printMsg("public @( Logger logger )", className);
    pw.indent();
    pw.println( "{");
    pw.undent();
    pw.println( "super( logger ) ;");
    pw.println( "}");
    pw.println();
    pw.flush();
    writeFactoryMethod(className, groupName, pw);
    writeExceptions(groupName, exceptions, className, pw);
    pw.undent();
    pw.println( );
    pw.println( "}");
    pw.flush();
    pw.close();
  }

  /**
   * Writes out the header of a Java source file.
   *
   * @param inFile the input file the file was generated from.
   * @param groupName the group of exceptions the Java source file is for.
   * @param pw the print writer used to write the output.
   */
  private void writeClassHeader(String inFile, String groupName,
                                IndentingPrintWriter pw) {
    if (groupName.equals("OMG"))
      pw.println("// Log wrapper class for standard exceptions");
    else
      pw.printMsg("// Log wrapper class for Sun private system exceptions in group @",
                  groupName);
    pw.println("//");
    pw.printMsg("// Generated by MC.java version @, DO NOT EDIT BY HAND!", VERSION);
    pw.printMsg("// Generated from input file @ on @", inFile, new Date());
    pw.println();
  }

  /**
   * Write out the import list for the exceptions.
   *
   * @param groups the exceptions that were parsed.
   * @param pw the {@link IndentingPrintWriter} for writing to the file.
   */
  private void writeImports(Queue<InputException> exceptions,
                            IndentingPrintWriter pw) {
    if (exceptions == null)
      return;
    for (InputException e : exceptions)
      pw.println("import org.omg.CORBA." + e.getName() + " ;");
  }

  /**
   * Write out the factory method for this group of exceptions.
   *
   * @param className the name of the generated class.
   * @param groupName the name of this group of exceptions.
   * @param pw the {@link IndentingPrintWriter} for writing to the file.
   */
  private void writeFactoryMethod(String className, String groupName,
                                  IndentingPrintWriter pw) {
    pw.indent();
    pw.println( "private static LogWrapperFactory factory = new LogWrapperFactory() {");
    pw.println( "public LogWrapperBase create( Logger logger )" );
    pw.indent();
    pw.println( "{");
    pw.undent();
    pw.printMsg("return new @( logger ) ;", className);
    pw.undent();
    pw.println( "}" );
    pw.println( "} ;" );
    pw.println();
    pw.printMsg("public static @ get( ORB orb, String logDomain )", className);
    pw.indent();
    pw.println( "{");
    pw.indent();
    pw.printMsg( "@ wrapper = ", className);
    pw.indent();
    pw.printMsg( "(@) orb.getLogWrapper( logDomain, ", className);
    pw.undent();
    pw.undent();
    pw.printMsg( "\"@\", factory ) ;", groupName);
    pw.undent();
    pw.println( "return wrapper ;" );
    pw.println( "} " );
    pw.println();
    pw.printMsg( "public static @ get( String logDomain )", className);
    pw.indent();
    pw.println( "{");
    pw.indent();
    pw.printMsg( "@ wrapper = ", className);
    pw.indent();
    pw.printMsg( "(@) ORB.staticGetLogWrapper( logDomain, ", className);
    pw.undent();
    pw.undent();
    pw.printMsg( "\"@\", factory ) ;", groupName);
    pw.undent();
    pw.println( "return wrapper ;" );
    pw.println( "} " );
    pw.println();
  }

  /**
   * Writes out the exceptions themselves.
   *
   * @param groupName the name of this group of exceptions.
   * @param exceptions the exceptions to write out.
   * @param className the name of the generated class.
   * @param pw the {@link IndentingPrintWriter} for writing to the file.
   */
  private void writeExceptions(String groupName, Queue<InputException> exceptions,
                               String className, IndentingPrintWriter pw) {
    for (InputException e : exceptions) {
      pw.println("///////////////////////////////////////////////////////////");
      pw.printMsg("// @", e.getName());
      pw.println("///////////////////////////////////////////////////////////");
      pw.println();
      for (InputCode c : e.getCodes())
        writeMethods(groupName, e.getName(), c.getName(), c.getCode(),
                     c.getLogLevel(), className, StringUtil.countArgs(c.getMessage()), pw);
      pw.flush();
    }
  }

  /**
   * Writes out the methods for a particular error.
   *
   * @param groupName the name of this group of exceptions.
   * @param exceptionName the name of this particular exception.
   * @param errorName the name of this particular error.
   * @param code the minor code for this particular error.
   * @param ident the name of the error in mixed-case identifier form.
   * @param level the level at which to place log messages.
   * @param className the name of the class for this group of exceptions.
   * @param numParams the number of parameters the detail message takes.
   * @param pw the print writer for writing to the file.
   */
  private void writeMethods(String groupName, String exceptionName, String errorName,
                            int code, String level, String className, int numParams,
                            IndentingPrintWriter pw) {
    String ident = StringUtil.toMixedCase(errorName);
    pw.printMsg("public static final int @ = @ ;", errorName, getBase(groupName, code));
    pw.println();
    pw.flush();
    writeMethodStatusCause(groupName, exceptionName, errorName, ident, level,
                           numParams, className, pw);
    pw.println();
    pw.flush();
    writeMethodStatus(exceptionName, ident, numParams, pw);
    pw.println();
    pw.flush();
    writeMethodCause(exceptionName, ident, numParams, pw);
    pw.println();
    pw.flush();
    writeMethodNoArgs(exceptionName, ident, numParams, pw);
    pw.println();
    pw.flush();
  }

  /**
   * Writes out a method for an error that takes a
   * {@link org.omg.CORBA.CompletionStatus} and a cause.
   *
   * @param groupName the name of this group of exceptions.
   * @param exceptionName the name of this particular exception.
   * @param errorName the name of this particular error.
   * @param ident the name of the error in mixed-case identifier form.
   * @param logLevel the level at which to place log messages.
   * @param numParams the number of parameters the detail message takes.
   * @param className the name of the class for this group of exceptions.
   * @param pw the print writer for writing to the file.
   */
  private void writeMethodStatusCause(String groupName, String exceptionName,
                                      String errorName, String ident,
                                      String logLevel, int numParams,
                                      String className, IndentingPrintWriter pw) {
    pw.indent();
    pw.printMsg( "public @ @( CompletionStatus cs, Throwable t@) {", exceptionName,
                 ident, makeDeclArgs(true, numParams));
    pw.printMsg( "@ exc = new @( @, cs ) ;", exceptionName, exceptionName, errorName);
    pw.indent();
    pw.println( "if (t != null)" );
    pw.undent();
    pw.println( "exc.initCause( t ) ;" );
    pw.println();
    pw.indent();
    pw.printMsg( "if (logger.isLoggable( Level.@ )) {", logLevel);
    if (numParams > 0) {
      pw.printMsg( "Object[] parameters = new Object[@] ;", numParams);
      for (int a = 0; a < numParams; ++a)
        pw.printMsg("parameters[@] = arg@ ;", a, a);
    } else
      pw.println( "Object[] parameters = null ;");
    pw.indent();
    pw.printMsg( "doLog( Level.@, \"@.@\",", logLevel, groupName, ident);
    pw.undent();
    pw.undent();
    pw.printMsg( "parameters, @.class, exc ) ;", className);
    pw.println( "}");
    pw.println();

    pw.undent();
    pw.println( "return exc ;");
    pw.println( "}");
  }

  /**
   * Writes out a method for an error that takes a
   * {@link org.omg.CORBA.CompletionStatus}.
   *
   * @param exceptionName the name of this particular exception.
   * @param ident the name of the error in mixed-case identifier form.
   * @param numParams the number of parameters the detail message takes.
   * @param pw the print writer for writing to the file.
   */
  private void writeMethodStatus(String exceptionName, String ident,
                                 int numParams, IndentingPrintWriter pw) {
    pw.indent();
    pw.printMsg("public @ @( CompletionStatus cs@) {", exceptionName,
                ident, makeDeclArgs(true, numParams));
    pw.undent();
    pw.printMsg("return @( cs, null@ ) ;", ident, makeCallArgs(true, numParams));
    pw.println("}");
  }

  /**
   * Writes out a method for an error that takes a cause.
   *
   * @param exceptionName the name of this particular exception.
   * @param ident the name of the error in mixed-case identifier form.
   * @param numParams the number of parameters the detail message takes.
   * @param pw the print writer for writing to the file.
   */
  private void writeMethodCause(String exceptionName, String ident,
                                int numParams, IndentingPrintWriter pw) {
    pw.indent();
    pw.printMsg("public @ @( Throwable t@) {", exceptionName, ident,
                makeDeclArgs(true, numParams));
    pw.undent();
    pw.printMsg("return @( CompletionStatus.COMPLETED_NO, t@ ) ;", ident,
                makeCallArgs(true, numParams));
    pw.println("}");
  }

  /**
   * Writes out a method for an error that takes no arguments.
   *
   * @param exceptionName the name of this particular exception.
   * @param ident the name of the error in mixed-case identifier form.
   * @param numParams the number of parameters the detail message takes.
   * @param pw the print writer for writing to the file.
   */
  private void writeMethodNoArgs(String exceptionName, String ident,
                                 int numParams, IndentingPrintWriter pw) {

    pw.indent();
    pw.printMsg("public @ @( @) {", exceptionName, ident,
                makeDeclArgs(false, numParams));
    pw.undent();
    pw.printMsg("return @( CompletionStatus.COMPLETED_NO, null@ ) ;",
                ident, makeCallArgs(true, numParams));
    pw.println("}");
  }

  /**
   * Returns a list of comma-separated arguments with type declarations.
   *
   * @param leadingComma true if the list should start with a comma.
   * @param numArgs the number of arguments to generate.
   * @return the generated string.
   */
  private String makeDeclArgs(boolean leadingComma, int numArgs) {
    return makeArgString("Object arg", leadingComma, numArgs);
  }

  /**
   * Returns a list of comma-separated arguments without type declarations.
   *
   * @param leadingComma true if the list should start with a comma.
   * @param numArgs the number of arguments to generate.
   * @return the generated string.
   */
  private String makeCallArgs(boolean leadingComma, int numArgs) {
    return makeArgString("arg", leadingComma, numArgs);
  }

  /**
   * Returns a list of comma-separated arguments.
   *
   * @param prefixString the string with which to prefix each argument.
   * @param leadingComma true if the list should start with a comma.
   * @param numArgs the number of arguments to generate.
   * @return the generated string.
   */
  private String makeArgString(String prefixString, boolean leadingComma,
                               int numArgs) {
    if (numArgs == 0)
      return " ";
    if (numArgs == 1) {
      if (leadingComma)
        return ", " + prefixString + (numArgs - 1);
      else
        return " " + prefixString + (numArgs - 1);
    }
    return makeArgString(prefixString, leadingComma, numArgs - 1) +
      ", " + prefixString + (numArgs - 1);
  }

  /**
   * Returns the {@link String} containing the calculation of the
   * error code.
   *
   * @param groupName the group of exception to which the code belongs.
   * @param code the minor code number representing the exception within the group.
   * @return the unique error code.
   */
  private String getBase(String groupName, int code) {
    if (groupName.equals("OMG"))
      return "OMGVMCID.value + " + code;
    else
      return "SUNVMCID.value + " + (code + getSunBaseNumber(groupName));
  }

  /**
   * Returns the base number for Sun-specific exceptions.
   *
   * @return the base number.
   */
  private int getSunBaseNumber(String groupName) {
    return 200 * SUN_EXCEPTION_GROUPS.indexOf(groupName);
  }

  /**
   * Writes out a resource file using the data from the given
   * {@link Input} object.  The result is written to {@code outDir}.
   *
   * @param outDir the directory to write the Java class to.
   * @param input the parsed input data.
   * @throws FileNotFoundException if the output file can't be written.
   */
  private void writeResource(String outDir, Input input)
    throws FileNotFoundException {
    FileOutputStream file = new FileOutputStream(outDir + File.separator +
                                                 input.getClassName() + ".resource");
    IndentingPrintWriter pw = new IndentingPrintWriter(file);
    String groupName = input.getGroupName();
    for (InputException e : input.getExceptions()) {
      String exName = e.getName();
      for (InputCode c : e.getCodes()) {
        String ident = StringUtil.toMixedCase(c.getName());
        pw.printMsg("@.@=\"@: (@) @\"", groupName, ident,
                    getMessageID(groupName, exName, c.getCode()), exName, c.getMessage());
      }
      pw.flush();
    }
    pw.close();
  }

  /**
   * Returns the message ID corresponding to the given group name,
   * exception name and error code.
   *
   * @param groupName the name of the group of exceptions.
   * @param exception the name of the particular exception.
   * @param code an error code from the given exception.
   * @return the message ID.
   */
  private String getMessageID(String groupName, String exceptionName, int code) {
    if (groupName.equals("OMG"))
      return getStandardMessageID(exceptionName, code);
    else
      return getSunMessageID(groupName, exceptionName, code);
  }

  /**
   * Returns the standard (OMG) message ID corresponding to the given
   * exception name and error code.
   *
   * @param exceptionName the name of the particular exception.
   * @param code an error code from the given exception.
   * @return the message ID.
   */
  private String getStandardMessageID(String exceptionName, int code) {
    return new Formatter().format("IOP%s0%04d", getExceptionID(exceptionName),
                                  code).toString();
  }

  /**
   * Returns the Sun message ID corresponding to the given group name,
   * exception name and error code.
   *
   * @param groupName the name of the group of exceptions.
   * @param exceptionName the name of the particular exception.
   * @param code an error code from the given exception.
   * @return the message ID.
   */
  private String getSunMessageID(String groupName, String exceptionName, int code) {
    return new Formatter().format("IOP%s1%04d", getExceptionID(exceptionName),
                                  getSunBaseNumber(groupName) + code).toString();
  }

  /**
   * Returns the exception ID corresponding to the given exception name.
   *
   * @param exceptionName the name of the particular exception.
   * @return the message ID.
   */
  private String getExceptionID(String exceptionName) {
    return new Formatter().format("%03d", EXCEPTIONS.indexOf(exceptionName)).toString();
  }

  /**
   * Entry point for running the generator from the command
   * line.  Users can specify either "make-class" or "make-resource"
   * as the first argument to generate the specified type of file.
   *
   * @param args the command-line arguments.
   * @throws FileNotFoundException if the input file can not be found.
   * @throws IOException if an I/O error occurs.
   */
  public static void main(String[] args)
    throws FileNotFoundException, IOException
  {
    if (args.length < 3)
      {
        System.err.println("(make-class|make-resource) <input file> <output dir>");
        System.exit(-1);
      }
    if (args[0].equals("make-class"))
      new MC().makeClass(args[1], args[2]);
    else if (args[0].equals("make-resource"))
      new MC().makeResource(args[1], args[2]);
    else
      System.err.println("Invalid command: " + args[0]);
  }

}
