/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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
/*
 * COMPONENT_NAME: idl.parser
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl;

// NOTES:
// -D56351<daz> Update Prefix pragma directive to CORBA 2.3 (see spec.).
// -D57110<daz> Update ID pragma directive to CORBA 2.3 (see spec.).
// -D46094<daz> Prohibit exceptions from appearing wihtin structs, unions, exceptions.
// -D46094<daz> Prohibit attributes from appearing as operation parameter types,
//  operation return types, attribute types.
// -D59166<daz> Prohibit identifiers form colliding with IDL keywords.
// -F60858.1<daz> Add keyword collision warning.
// -F60942<daz> Prohibit operations from appearing as operation parameter types.
// -D62023<daz> Add deprecated keyword warning; cast char to byte for JDK 1.2 compatability.

import java.io.IOException;

/**
 *
 **/
class ParseException extends Exception
{
  /**
   * Constructor: print the supplied message to Standard.err
   * and create a new ParseException
   * @return a new ParseException.
   **/
  ParseException (String message)
  {
    super (message);
    System.err.println (message);
    detected = true;
  } // ctor

  /**
   * Constructor: print the supplied message to Standard.err, if it
   * is not a warning, and create a new ParseException.
   * @return a new ParseException.
   **/
  ParseException (String message, boolean onlyAWarning)
  {
   super (message);
   System.err.println (message);
   if (!onlyAWarning)
     detected = true;
  }

  static ParseException abstractValueBox (Scanner scanner)
  {
    return arg0 ("abstractValueBox", scanner);
  }

  static ParseException alreadyDeclared (Scanner scanner, String type)
  {
    return arg1 ("alreadyDeclared", scanner, type);
  }

  static ParseException declNotInSameFile( Scanner scanner, String type,
    String firstFile )
  {
    return arg2 ("declNotInSameFile", scanner, type, firstFile) ;
  }

  static ParseException alreadyDefaulted (Scanner scanner)
  {
    return arg0 ("alreadydefaulted", scanner);
  }

  static ParseException alreadyDerived (Scanner scanner, String derived, String iface)
  {
    return arg2 ("alreadyDerived", scanner, derived, iface);
  }

  static ParseException alreadyRaised (Scanner scanner, String exception)
  {
    return arg1 ("alreadyRaised", scanner, exception);
  }

  // <d60942>
  static ParseException attributeNotType (Scanner scanner, String attr)
  {
    return arg1 ("attributeNotType", scanner, attr);
  }

  static ParseException badAbstract (Scanner scanner, String name)
  {
    return arg1 ("badAbstract", scanner, name);
  }

  static ParseException badCustom (Scanner scanner)
  {
    return arg0 ("badCustom", scanner);
  }

  // <d57110>
  static ParseException badRepIDAlreadyAssigned (Scanner scanner, String entry)
  {
    return arg1 ("badRepIDAlreadyAssigned", scanner, entry);
  }

  // <d57110>
  static ParseException badRepIDForm (Scanner scanner, String entry)
  {
    return arg1 ("badRepIDForm", scanner, entry);
  }

  // <d56351>
  static ParseException badRepIDPrefix (Scanner scanner, String entry, String expected, String got)
  {
    return arg3 ("badRepIDPrefix", scanner, entry, expected, got);
  }

  static ParseException badState (Scanner scanner, String entry)
  {
    return arg1 ("badState", scanner, entry);
  }

  static ParseException branchLabel (Scanner scanner, String label)
  {
    return arg1 ("branchLabel", scanner, label);
  }

  static ParseException branchName (Scanner scanner, String name)
  {
    return arg1 ("branchName", scanner, name);
  }

  static ParseException duplicateInit (Scanner scanner)
  {
    return arg0 ("duplicateInit", scanner);
  }

  static ParseException duplicateState (Scanner scanner, String name)
  {
    return arg1 ("duplicateState", scanner, name);
  }

  static ParseException elseNoIf (Scanner scanner)
  {
    return arg0 ("elseNoIf", scanner);
  }

  static ParseException endNoIf (Scanner scanner)
  {
    return arg0 ("endNoIf", scanner);
  }

  static ParseException evaluationError (Scanner scanner, String problem)
  {
    return arg1 ("evaluation", scanner, problem);
  }

  static ParseException forwardEntry (Scanner scanner, String name)
  {
    return arg1 ("forwardEntry", scanner, name);
  }

  // <f46082.40> Cannot forward value boxes.
  static ParseException forwardedValueBox (Scanner scanner, String name)
  {
    return arg1 ("forwardedValueBox", scanner, name);
  }

  static ParseException generic (Scanner scanner, String message)
  {
    return arg1 ("generic", scanner, message);
  }

  static ParseException illegalArray (Scanner scanner, String name)
  {
    return arg1 ("illegalArray", scanner, name);
  }

  static ParseException illegalException (Scanner scanner, String name)
  {
    return arg1 ("illegalException", scanner, name);
  }

  static ParseException invalidConst (Scanner scanner, String mustBe, String is)
  {
    return arg2 ("invalidConst1", scanner, mustBe, is);
  }

  static ParseException invalidConst (Scanner scanner, String type)
  {
    return arg1 ("invalidConst2", scanner, type);
  }

  // <d59166> Non-escaped identifiers that collide with keywords are illegal.
  static ParseException keywordCollision (Scanner scanner, String id)
  {
    return arg1 ("keywordCollision", scanner, id);
  }

  // <d62023> Warning for keywords that will be removed in a future version of IDL.
  static ParseException deprecatedKeywordWarning (Scanner scanner, String id)
  {
    return arg1Warning ("deprecatedKeywordWarning", scanner, id);
  }

  // <f60858.1> Warning for above error.
  static ParseException keywordCollisionWarning (Scanner scanner, String id)
  {
    return arg1Warning ("keywordCollisionWarning", scanner, id);
  }

  static ParseException methodClash (Scanner scanner, String interf, String method)
  {
    return arg2 ("methodClash", scanner, interf, method);
  }

  static ParseException moduleNotType (Scanner scanner, String module)
  {
    return arg1 ("moduleNotType", scanner, module);
  }

  // <d59067>
  static ParseException nestedValueBox (Scanner scanner)
  {
    return arg0 ("nestedValueBox", scanner);
  }

  static ParseException noDefault (Scanner scanner)
  {
    return arg0 ("noDefault", scanner);
  }

  static ParseException nonAbstractParent (Scanner scanner, String baseClass, String parentClass)
  {
    return arg2 ("nonAbstractParent", scanner, baseClass, parentClass);
  }

  static ParseException nonAbstractParent2 (Scanner scanner, String baseClass, String parentClass)
  {
    return arg2 ("nonAbstractParent2", scanner, baseClass, parentClass);
  }

  static ParseException nonAbstractParent3 (Scanner scanner, String baseClass, String parentClass)
  {
    return arg2 ("nonAbstractParent3", scanner, baseClass, parentClass);
  }

  static ParseException notANumber (Scanner scanner, String notNumber)
  {
    return arg1 ("notANumber", scanner, notNumber);
  }

  static ParseException nothing (String filename)
  {
    return new ParseException (Util.getMessage ("ParseException.nothing", filename));
  }

  static ParseException notPositiveInt (Scanner scanner, String notPosInt)
  {
    return arg1 ("notPosInt", scanner, notPosInt);
  }

  static ParseException oneway (Scanner scanner, String method)
  {
    return arg1 ("oneway", scanner, method);
  }

  // <d60942>
  static ParseException operationNotType (Scanner scanner, String op)
  {
    return arg1 ("operationNotType", scanner, op);
  }

  static ParseException outOfRange (Scanner scanner, String value, String type)
  {
    return arg2 ("outOfRange", scanner, value, type);
  }

  static ParseException recursive (Scanner scanner, String type, String name)
  {
    return arg2 ("recursive", scanner, type, name);
  }

  static ParseException selfInherit (Scanner scanner, String name)
  {
    return arg1 ("selfInherit", scanner, name);
  }

  static ParseException stringTooLong (Scanner scanner, String str, String max)
  {
    return arg2 ("stringTooLong", scanner, str, max);
  }

  static ParseException syntaxError (Scanner scanner, int expected, int got)
  {
    return arg2 ("syntax1", scanner, Token.toString (expected), Token.toString (got));
  }

  static ParseException syntaxError (Scanner scanner, String expected, String got)
  {
    return arg2 ("syntax1", scanner, expected, got);
  }

  static ParseException syntaxError (Scanner scanner, int[] expected, int got)
  {
    return syntaxError (scanner, expected, Token.toString (got));
  }

  static ParseException syntaxError (Scanner scanner, int[] expected, String got)
  {
    String tokenList = "";
    for (int i = 0; i < expected.length; ++i)
      tokenList += " `" + Token.toString (expected[i]) + "'";
    return arg2 ("syntax2", scanner, tokenList, got);
  }

  static ParseException unclosedComment (String filename)
  {
    return new ParseException (Util.getMessage ("ParseException.unclosed", filename));
  }

  static ParseException undeclaredType (Scanner scanner, String undeclaredType)
  {
    return arg1 ("undeclaredType", scanner, undeclaredType);
  }

  static ParseException warning (Scanner scanner, String message)
  {
    scannerInfo (scanner);
    String[] parameters = { filename, Integer.toString (lineNumber), message, line, pointer };
    return new ParseException (Util.getMessage ("ParseException.warning", parameters), true);
  }

  static ParseException wrongType (Scanner scanner, String name, String mustBe, String is)
  {
    scannerInfo (scanner);
    String[] parameters = {filename, Integer.toString (lineNumber), name, is, mustBe, line, pointer};
    return new ParseException (Util.getMessage ("ParseException.wrongType", parameters));
  }

  static ParseException wrongExprType (Scanner scanner, String mustBe, String is)
  {
    scannerInfo (scanner);
    String[] parameters = {filename, Integer.toString (lineNumber),
      is, mustBe, line, pointer};
    return new ParseException (Util.getMessage ("ParseException.constExprType",
      parameters));
  }

  static ParseException illegalForwardInheritance( Scanner scanner, String declName,
    String baseName )
  {
    scannerInfo( scanner ) ;
    String[] parameters = { filename, Integer.toString(lineNumber),
        declName, baseName, line, pointer } ;
    return new ParseException (Util.getMessage(
        "ParseException.forwardInheritance", parameters ) ) ;
  }

  static ParseException illegalIncompleteTypeReference( Scanner scanner,
    String declName )
  {
    scannerInfo( scanner ) ;
    String[] parameters = { filename, Integer.toString(lineNumber),
        declName, line, pointer } ;
    return new ParseException (Util.getMessage(
        "ParseException.illegalIncompleteTypeReference", parameters ) ) ;
  }

  private static void scannerInfo (Scanner scanner)
  {
    filename   = scanner.filename ();
    line       = scanner.lastTokenLine ();
    lineNumber = scanner.lastTokenLineNumber ();
    int pos    = scanner.lastTokenLinePosition ();
    pointer    = "^";
    if (pos > 1)
    {
      byte[] bytes = new byte[ pos - 1 ];
      for (int i = 0; i < pos - 1; ++i)
        bytes[i] = (byte)' ';  // <d62023>
      pointer = new String (bytes) + pointer;
    }
  }

  private static ParseException arg0 (String msgId, Scanner scanner)
  {
    scannerInfo (scanner);
    String[] parameters = {filename, Integer.toString (lineNumber), line, pointer};
    return new ParseException (Util.getMessage ("ParseException." + msgId, parameters));
  }

  private static ParseException arg1 (String msgId, Scanner scanner, String arg1)
  {
    scannerInfo (scanner);
    String[] parameters = {filename, Integer.toString (lineNumber), arg1, line, pointer};
    return new ParseException (Util.getMessage ("ParseException." + msgId, parameters));
  }

  // <f60858.1>
  private static ParseException arg1Warning (String msgId, Scanner scanner, String arg1)
  {
    scannerInfo (scanner);
    String[] parameters = {filename, Integer.toString (lineNumber), arg1, line, pointer};
    return new ParseException (Util.getMessage ("ParseException." + msgId, parameters), true);
  }

  private static ParseException arg2 (String msgId, Scanner scanner, String arg1, String arg2)
  {
    scannerInfo (scanner);
    String[] parameters = {filename, Integer.toString (lineNumber), arg1, arg2, line, pointer};
    return new ParseException (Util.getMessage ("ParseException." + msgId, parameters));
  }

  private static ParseException arg3 (String msgId, Scanner scanner, String arg1, String arg2, String arg3)
  {
    scannerInfo (scanner);
    String[] parameters = {filename, Integer.toString (lineNumber), arg1, arg2, arg3, line, pointer};
    return new ParseException (Util.getMessage ("ParseException." + msgId, parameters));
  }

  private static String filename  = "";
  private static String line      = "";
  private static int   lineNumber = 0;
  private static String pointer   = "^";

  static boolean detected = false;
} // class ParseException
