/*
 * Copyright (c) 1999, 2000, Oracle and/or its affiliates. All rights reserved.
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
// -D57110<daz> Allow ID pragma directive to be applied to modules and update
//  feature in accordance to CORBA 2.3.
// -D59165<daz> Enable escaped identifiers when processing pragmas.
// -f60858.1<daz> Support -corba option, level = 2.2: Accept identifiers that
//  collide with keywords, in letter but not case, and issue a warning.
// -d62023 <daz> support -noWarn option; suppress inappropriate warnings when
//  parsing IBM-specific pragmas (#meta <interface_name> abstract).

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;

import com.sun.tools.corba.se.idl.RepositoryID;

import com.sun.tools.corba.se.idl.constExpr.*;

/**
 * This class should be extended if new pragmas are desired.  If the
 * preprocessor encounters a pragma name which it doesn't recognize
 * (anything other than ID, prefix, or version), it calls the method
 * otherPragmas.  This is the only method which need be overridden.
 * The Preprocessor base class has a number of utility-like methods
 * which can be used by the overridden otherPragmas method.
 **/
public class Preprocessor
{
  /**
   * Public zero-argument constructor.
   **/
  Preprocessor ()
  {
  } // ctor

  /**
   *
   **/
  void init (Parser p)
  {
    parser  = p;
    symbols = p.symbols;
    macros  = p.macros;
  } // init

  /**
   *
   **/
  protected Object clone ()
  {
    return new Preprocessor ();
  } // clone

  /**
   *
   **/
  Token process (Token t) throws IOException, ParseException
  {
    token   = t;
    scanner = parser.scanner;
    // <f46082.40> Deactivate escaped identifier processing in Scanner while
    // preprocessing.
    //scanner.underscoreOK = true;
    scanner.escapedOK = false;
    try
    {
      switch (token.type)
      {
        case Token.Include:
          include ();
          break;
        case Token.If:
          ifClause ();
          break;
        case Token.Ifdef:
          ifdef (false);
          break;
        case Token.Ifndef:
          ifdef (true);
          break;
        case Token.Else:
          if (alreadyProcessedABranch.empty ())
            throw ParseException.elseNoIf (scanner);
          else if (((Boolean)alreadyProcessedABranch.peek ()).booleanValue ())
            skipToEndif ();
          else
          {
            alreadyProcessedABranch.pop ();
            alreadyProcessedABranch.push (new Boolean (true));
            token = scanner.getToken ();
          }
          break;
        case Token.Elif:
          elif ();
          break;
        case Token.Endif:
          if (alreadyProcessedABranch.empty ())
            throw ParseException.endNoIf (scanner);
          else
          {
            alreadyProcessedABranch.pop ();
            token = scanner.getToken ();
            break;
          }
        case Token.Define:
          define ();
          break;
        case Token.Undef:
          undefine ();
          break;
        case Token.Pragma:
          pragma ();
          break;
        case Token.Unknown:
          if (!parser.noWarn)
            ParseException.warning (scanner, Util.getMessage ("Preprocessor.unknown", token.name));
        case Token.Error:
        case Token.Line:
        case Token.Null:
          // ignore
        default:
          scanner.skipLineComment ();
          token = scanner.getToken ();
      }
    }
    catch (IOException e)
    {
      // <f46082.40> Underscore may now precede any identifier, so underscoreOK
      // is vestigal.  The Preprocessor must reset escapedOK so that Scanner
      // will process escaped identifiers according to specification.
      //scanner.underscoreOK = false;
      scanner.escapedOK = true;
      throw e;
    }
    catch (ParseException e)
    {
      // <f46082.40> See above.
      //scanner.underscoreOK = false;
      scanner.escapedOK = true;
      throw e;
    }
    // <f46082.40> See above.
    //scanner.underscoreOK = false;
    scanner.escapedOK = true;
    return token;
  } // process

  /**
   *
   **/
  private void include () throws IOException, ParseException
  {
    match (Token.Include);
    IncludeEntry include = parser.stFactory.includeEntry (parser.currentModule);
    include.sourceFile (scanner.fileEntry ());
    scanner.fileEntry ().addInclude (include);
    if (token.type == Token.StringLiteral)
      include2 (include);
    else if (token.type == Token.LessThan)
      include3 (include);
    else
    {
      int[] expected = {Token.StringLiteral, Token.LessThan};
      throw ParseException.syntaxError (scanner, expected, token.type);
    }
    if (parser.currentModule instanceof ModuleEntry)
      ((ModuleEntry)parser.currentModule).addContained (include);
    else if (parser.currentModule instanceof InterfaceEntry)
      ((InterfaceEntry)parser.currentModule).addContained (include);
  } // include

  /**
   *
   **/
  private void include2 (IncludeEntry include) throws IOException, ParseException
  {
    include.name ('"' + token.name + '"');
    include4 (include, token.name);
    match (Token.StringLiteral);
  } // include2

  /**
   *
   **/
  private void include3 (IncludeEntry include) throws IOException, ParseException
  {
    if (token.type != Token.LessThan)
      // match will throw an exception
      match (Token.LessThan);
    else
    {
      try
      {
        String includeFile = getUntil ('>');
        token = scanner.getToken ();
        include.name ('<' + includeFile + '>');
        include4 (include, includeFile);
        match (Token.GreaterThan);
      }
      catch (IOException e)
      {
        throw ParseException.syntaxError (scanner, ">", "EOF");
      }
    }
  } // include3

  /**
   *
   **/
  private void include4 (IncludeEntry include, String filename) throws IOException, ParseException
  {
    try
    {
      // If the #include is at the global scope, it is treated as
      // an import statement.  If it is within some other scope, it
      // is treated as a normal #include.
      boolean includeIsImport = parser.currentModule == parser.topLevelModule;
      //daz
      include.absFilename (Util.getAbsolutePath (filename, parser.paths));
      scanner.scanIncludedFile (include, getFilename (filename), includeIsImport);
    }
    catch (IOException e)
    {
      ParseException.generic (scanner, e.toString ());
    }
  } // include4

  /**
   *
   **/
  private void define () throws IOException, ParseException
  {
    match (Token.Define);
    if (token.equals (Token.Identifier))
    {
      String symbol = scanner.getStringToEOL ();
      symbols.put (token.name, symbol.trim ());
      match (Token.Identifier);
    }
    else if (token.equals (Token.MacroIdentifier))
    {
      symbols.put (token.name, '(' + scanner.getStringToEOL () . trim ());
      macros.addElement (token.name);
      match (Token.MacroIdentifier);
    }
    else
      throw ParseException.syntaxError (scanner, Token.Identifier, token.type);
  } // define

  /**
   *
   **/
  private void undefine () throws IOException, ParseException
  {
    match (Token.Undef);
    if (token.equals (Token.Identifier))
    {
      symbols.remove (token.name);
      macros.removeElement (token.name);
      match (Token.Identifier);
    }
    else
      throw ParseException.syntaxError (scanner, Token.Identifier, token.type);
  } // undefine

  /**
   *
   **/
  private void ifClause () throws IOException, ParseException
  {
    match (Token.If);
    constExpr ();
  } // ifClause

  /**
   *
   **/
  private void constExpr () throws IOException, ParseException
  {
    SymtabEntry dummyEntry = new SymtabEntry (parser.currentModule);
    dummyEntry.container (parser.currentModule);
    parser.parsingConditionalExpr = true;
    Expression boolExpr = booleanConstExpr (dummyEntry);
    parser.parsingConditionalExpr = false;
    boolean expr;
    if (boolExpr.value () instanceof Boolean)
      expr = ((Boolean)boolExpr.value ()).booleanValue ();
    else
      expr = ((Number)boolExpr.value ()).longValue () != 0;
    alreadyProcessedABranch.push (new Boolean (expr));
    if (!expr)
      skipToEndiforElse ();
  } // constExpr

  /**
   *
   **/
  Expression booleanConstExpr (SymtabEntry entry) throws IOException, ParseException
  {
    Expression expr = orExpr (null, entry);
    try
    {
      expr.evaluate ();
    }
    catch (EvaluationException e)
    {
      ParseException.evaluationError (scanner, e.toString ());
    }
    return expr;
  } // booleanConstExpr

  /**
   *
   **/
  private Expression orExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
      e = andExpr (null, entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (andExpr (null, entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.DoubleBar))
    {
      match (token.type);
      BooleanOr or = parser.exprFactory.booleanOr (e, null);
      or.rep (e.rep () + " || ");
      return orExpr (or, entry);
    }
    else
      return e;
  } // orExpr

  /**
   *
   **/
  private Expression andExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
      e = notExpr (entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (notExpr (entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.DoubleAmpersand))
    {
      match (token.type);
      BooleanAnd and = parser.exprFactory.booleanAnd (e, null);
      and.rep (e.rep () + " && ");
      return andExpr (and, entry);
    }
    else
      return e;
  } // andExpr

  /**
   *
   **/
  private Expression notExpr (/*boolean alreadySawExclamation, */SymtabEntry entry) throws IOException, ParseException
  {
    Expression e;
    if (token.equals (Token.Exclamation))
    {
      match (Token.Exclamation);
      e = parser.exprFactory.booleanNot (definedExpr (entry));
      e.rep ("!" + ((BooleanNot)e).operand ().rep ());
    }
    else
      e = definedExpr (entry);
    return e;
  } // notExpr

  /**
   *
   **/
  private Expression definedExpr (SymtabEntry entry) throws IOException, ParseException
  {
    if (token.equals (Token.Identifier) && token.name.equals ("defined"))
      match (Token.Identifier);
    return equalityExpr (null, entry);
  } // definedExpr

  /**
   *
   **/
  private Expression equalityExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
    {
      parser.token = token; // Since parser to parse, give it this token
      e = parser.constExp (entry);
      token = parser.token; // Since parser last parsed, get its token
    }
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      parser.token = token; // Since parser to parse, give it this token
      Expression constExpr = parser.constExp (entry);
      token = parser.token; // Since parser last parsed, get its token
      b.right (constExpr);
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.DoubleEqual))
    {
      match (token.type);
      Equal eq = parser.exprFactory.equal (e, null);
      eq.rep (e.rep () + " == ");
      return equalityExpr (eq, entry);
    }
    else if (token.equals (Token.NotEqual))
    {
      match (token.type);
      NotEqual n = parser.exprFactory.notEqual (e, null);
      n.rep (e.rep () + " != ");
      return equalityExpr (n, entry);
    }
    else if (token.equals (Token.GreaterThan))
    {
      match (token.type);
      GreaterThan g = parser.exprFactory.greaterThan (e, null);
      g.rep (e.rep () + " > ");
      return equalityExpr (g, entry);
    }
    else if (token.equals (Token.GreaterEqual))
    {
      match (token.type);
      GreaterEqual g = parser.exprFactory.greaterEqual (e, null);
      g.rep (e.rep () + " >= ");
      return equalityExpr (g, entry);
    }
    else if (token.equals (Token.LessThan))
    {
      match (token.type);
      LessThan l = parser.exprFactory.lessThan (e, null);
      l.rep (e.rep () + " < ");
      return equalityExpr (l, entry);
    }
    else if (token.equals (Token.LessEqual))
    {
      match (token.type);
      LessEqual l = parser.exprFactory.lessEqual (e, null);
      l.rep (e.rep () + " <= ");
      return equalityExpr (l, entry);
    }
    else
      return e;
  } // equalityExpr

  /**
   *
   **/
  Expression primaryExpr (SymtabEntry entry) throws IOException, ParseException
  {
    Expression primary = null;
    switch (token.type)
    {
      case Token.Identifier:
        // If an identifier gets this far, it means that no
        // preprocessor variable was defined with that name.
        // Generate a FALSE boolean expr.
        //daz        primary = parser.exprFactory.terminal ("0", new Long (0));
        primary = parser.exprFactory.terminal ("0", BigInteger.valueOf (0));
        token = scanner.getToken ();
        break;
      case Token.BooleanLiteral:
      case Token.CharacterLiteral:
      case Token.IntegerLiteral:
      case Token.FloatingPointLiteral:
      case Token.StringLiteral:
        //daz        primary = parser.literal ();
        primary = parser.literal (entry);
        token = parser.token;
        break;
      case Token.LeftParen:
        match (Token.LeftParen);
        primary = booleanConstExpr (entry);
        match (Token.RightParen);
        primary.rep ('(' + primary.rep () + ')');
        break;
      default:
        int[] expected = {Token.Literal, Token.LeftParen};
        throw ParseException.syntaxError (scanner, expected, token.type);
    }
    return primary;
  } // primaryExpr

  /**
   *
   **/
  private void ifDefine (boolean inParens, boolean not) throws IOException, ParseException
  {
    if (token.equals (Token.Identifier))
      if ((not && symbols.containsKey (token.name)) || (!not && !symbols.containsKey (token.name)))
      {
        alreadyProcessedABranch.push (new Boolean (false));
        skipToEndiforElse ();
      }
      else
      {
        alreadyProcessedABranch.push (new Boolean (true));
        match (Token.Identifier);
        if (inParens)
          match (Token.RightParen);
      }
    else
      throw ParseException.syntaxError (scanner, Token.Identifier, token.type);
  } // ifDefine

  /**
   *
   **/
  private void ifdef (boolean not) throws IOException, ParseException
  {
    if (not)
      match (Token.Ifndef);
    else
      match (Token.Ifdef);
    if (token.equals (Token.Identifier))
      if ((not && symbols.containsKey (token.name)) || (!not && !symbols.containsKey (token.name)))
      {
        alreadyProcessedABranch.push (new Boolean (false));
        skipToEndiforElse ();
      }
      else
      {
        alreadyProcessedABranch.push (new Boolean (true));
        match (Token.Identifier);
      }
    else
      throw ParseException.syntaxError (scanner, Token.Identifier, token.type);
  } // ifdef

  /**
   *
   **/
  private void elif () throws IOException, ParseException
  {
    if (alreadyProcessedABranch.empty ())
      throw ParseException.elseNoIf (scanner);
    else if (((Boolean)alreadyProcessedABranch.peek ()).booleanValue ())
      skipToEndif ();
    else
    {
      match (Token.Elif);
      constExpr ();
    }
  } // elif

  /**
   *
   **/
  private void skipToEndiforElse () throws IOException, ParseException
  {
    while (!token.equals (Token.Endif) && !token.equals (Token.Else) && !token.equals (Token.Elif))
    {
      if (token.equals (Token.Ifdef) || token.equals (Token.Ifndef))
      {
        alreadyProcessedABranch.push (new Boolean (true));
        skipToEndif ();
      }
      else
        token = scanner.skipUntil ('#');
    }
    process (token);
  } // skipToEndiforElse

  /**
   *
   **/
  private void skipToEndif () throws IOException, ParseException
  {
    while (!token.equals (Token.Endif))
    {
      token = scanner.skipUntil ('#');
      if (token.equals (Token.Ifdef) || token.equals (Token.Ifndef))
      {
        alreadyProcessedABranch.push (new Boolean (true));
        skipToEndif ();
      }
    }
    alreadyProcessedABranch.pop ();
    match (Token.Endif);
  } // skipToEndif

  ///////////////
  // For Pragma

  /**
   *
   **/
  private void pragma () throws IOException, ParseException
  {
    match (Token.Pragma);
    String pragmaType = token.name;

    // <d59165> Enable escaped identifiers while processing pragma internals.
    // Don't enable until scanning pragma name!
    scanner.escapedOK = true;
    match (Token.Identifier);

    // Add pragma entry to container
    PragmaEntry pragmaEntry = parser.stFactory.pragmaEntry (parser.currentModule);
    pragmaEntry.name (pragmaType);
    pragmaEntry.sourceFile (scanner.fileEntry ());
    pragmaEntry.data (scanner.currentLine ());
    if (parser.currentModule instanceof ModuleEntry)
      ((ModuleEntry)parser.currentModule).addContained (pragmaEntry);
    else if (parser.currentModule instanceof InterfaceEntry)
      ((InterfaceEntry)parser.currentModule).addContained (pragmaEntry);

    // If the token was an identifier, then pragmaType WILL be non-null.
    if (pragmaType.equals ("ID"))
      idPragma ();
    else if (pragmaType.equals ("prefix"))
      prefixPragma ();
    else if (pragmaType.equals ("version"))
      versionPragma ();

    // we are adding extensions to the Sun's idlj compiler to
    // handle correct code generation for local Objects, where
    // the OMG is taking a long time to formalize stuff.  Good
    // example of this is poa.idl.  Two proprietory pragmas
    // sun_local and sun_localservant are defined.  sun_local
    // generates only Holder and Helper classes, where read
    // and write methods throw marshal exceptions.  sun_localservant
    // is to generate Helper, Holder, and only Skel with _invoke
    // throwing an exception, since it does not make sense for
    // local objects.

    else if (pragmaType.equals ("sun_local"))
      localPragma();
    else if (pragmaType.equals ("sun_localservant"))
      localServantPragma();
    else
    {
      otherPragmas (pragmaType, tokenToString ());
      token = scanner.getToken ();
    }

    scanner.escapedOK = false; // <d59165> Disable escaped identifiers.
  } // pragma

  // <d57110> Pragma ID can be appiled to modules and it is an error to
  // name a type in more than one ID pragma directive.

  private Vector PragmaIDs = new Vector ();

  private void localPragma () throws IOException, ParseException
  {
    // Before I can use a parser method, I must make sure it has the current token.
    parser.token = token;
    // this makes sense only for interfaces, if specified for modules,
    // parser should throw an error
    SymtabEntry anErrorOccurred = new SymtabEntry ();
    SymtabEntry entry = parser.scopedName (parser.currentModule, anErrorOccurred);
    // Was the indicated type found in the symbol table?
    if (entry == anErrorOccurred)
    {
        System.out.println("Error occured ");
      // Don't have to generate an error, scopedName already has.
      scanner.skipLineComment ();
      token = scanner.getToken ();
    }
    else
    {
      // by this time we have already parsed the ModuleName and the
      // pragma type, therefore setInterfaceType
      if (entry instanceof InterfaceEntry) {
          InterfaceEntry ent = (InterfaceEntry) entry;
          ent.setInterfaceType (InterfaceEntry.LOCAL_SIGNATURE_ONLY);
      }
      token = parser.token;
      String string = token.name;
      match (Token.StringLiteral);
      // for non-interfaces it doesn't make sense, so just ignore it
    }
  } // localPragma

  private void localServantPragma () throws IOException, ParseException
  {
    // Before I can use a parser method, I must make sure it has the current token.
    parser.token = token;
    // this makes sense only for interfaces, if specified for modules,
    // parser should throw an error
    SymtabEntry anErrorOccurred = new SymtabEntry ();
    SymtabEntry entry = parser.scopedName (parser.currentModule, anErrorOccurred);

    // Was the indicated type found in the symbol table?
    if (entry == anErrorOccurred)
    {
      // Don't have to generate an error, scopedName already has.
      scanner.skipLineComment ();
      token = scanner.getToken ();
        System.out.println("Error occured ");
    }
    else
    {
      // by this time we have already parsed the ModuleName and the
      // pragma type, therefore setInterfaceType
      if (entry instanceof InterfaceEntry) {
          InterfaceEntry ent = (InterfaceEntry) entry;
          ent.setInterfaceType (InterfaceEntry.LOCALSERVANT);
      }
      token = parser.token;
      String string = token.name;
      match (Token.StringLiteral);
      // for non-interfaces it doesn't make sense, so just ignore it
    }
  } // localServantPragma


  /**
   *
   **/
  private void idPragma () throws IOException, ParseException
  {
    // Before I can use a parser method, I must make sure it has the current token.
    parser.token = token;

    // <d57110> This flag will relax the restriction that the scopedNamed
    // in this ID pragma directive cannot resolve to a module.
    parser.isModuleLegalType (true);
    SymtabEntry anErrorOccurred = new SymtabEntry ();
    SymtabEntry entry = parser.scopedName (parser.currentModule, anErrorOccurred);
    parser.isModuleLegalType (false);  // <57110>

    // Was the indicated type found in the symbol table?
    if (entry == anErrorOccurred)
    {
      // Don't have to generate an error, scopedName already has.
      scanner.skipLineComment ();
      token = scanner.getToken ();
    }
    // <d57110>
    //else if (PragmaIDs.contains (entry))
    //{
    //  ParseException.badRepIDAlreadyAssigned (scanner, entry.name ());
    //  scanner.skipLineComment ();
    //  token = scanner.getToken ();
    //}
    else
    {
      token = parser.token;
      String string = token.name;
      // Do not match token until after raise exceptions, otherwise
      // incorrect messages will be emitted!
      if (PragmaIDs.contains (entry)) // <d57110>
      {
        ParseException.badRepIDAlreadyAssigned (scanner, entry.name ());
      }
      else if (!RepositoryID.hasValidForm (string)) // <d57110>
      {
        ParseException.badRepIDForm (scanner, string);
      }
      else
      {
        entry.repositoryID (new RepositoryID (string));
        PragmaIDs.addElement (entry); // <d57110>
      }
      match (Token.StringLiteral);
    }
  } // idPragma

  /**
   *
   **/
  private void prefixPragma () throws IOException, ParseException
  {
    String string = token.name;
    match (Token.StringLiteral);
    ((IDLID)parser.repIDStack.peek ()).prefix (string);
    ((IDLID)parser.repIDStack.peek ()).name ("");
  } // prefixPragma

  /**
   *
   **/
  private void versionPragma () throws IOException, ParseException
  {
    // Before I can use a parser method, I must make sure it has the current token.
    parser.token = token;
    // This flag will relax the restriction that the scopedNamed
    // in this Version pragma directive cannot resolve to a module.
    parser.isModuleLegalType (true);
    SymtabEntry anErrorOccurred = new SymtabEntry ();
    SymtabEntry entry = parser.scopedName (parser.currentModule, anErrorOccurred);
    // reset the flag to original value
    parser.isModuleLegalType (false);
    if (entry == anErrorOccurred)
    {
      // Don't have to generate an error, scopedName already has.
      scanner.skipLineComment ();
      token = scanner.getToken ();
    }
    else
    {
      token = parser.token;
      String string = token.name;
      match (Token.FloatingPointLiteral);
      if (entry.repositoryID () instanceof IDLID)
        ((IDLID)entry.repositoryID ()).version (string);
    }
  } // versionPragma

  private Vector pragmaHandlers = new Vector ();

  /**
   *
   **/
  void registerPragma (PragmaHandler handler)
  {
    pragmaHandlers.addElement (handler);
  } // registerPragma

  /**
   *
   **/
  private void otherPragmas (String pragmaType, String currentToken) throws IOException
  {
    for (int i = pragmaHandlers.size () - 1; i >= 0; --i)
    {
      PragmaHandler handler = (PragmaHandler)pragmaHandlers.elementAt (i);
      if (handler.process (pragmaType, currentToken))
                break;
    }
  } // otherPragmas

  /*
   * These protected methods are used by extenders, by the code
   * which implements otherPragma.
   */

  /**
   * Get the current token.
   **/
  String currentToken ()
  {
    return tokenToString ();
  } // currentToken

  /**
   * This method, given an entry name, returns the entry with that name.
   * It can take fully or partially qualified names and returns the
   * appropriate entry defined within the current scope.  If no entry
   * exists, null is returned.
   **/
  SymtabEntry getEntryForName (String string)
  {
    boolean partialScope = false;
    boolean globalScope  = false;

    // Change all ::'s to /'s
    if (string.startsWith ("::"))
    {
      globalScope = true;
      string = string.substring (2);
    }
    int index = string.indexOf ("::");
    while (index >= 0)
    {
      partialScope = true;
      string = string.substring (0, index) + '/' + string.substring (index + 2);
      index = string.indexOf ("::");
    }

    // Get the entry for that string
    SymtabEntry entry = null;
    if (globalScope)
      entry = parser.recursiveQualifiedEntry (string);
    else if (partialScope)
      entry = parser.recursivePQEntry (string, parser.currentModule);
    else
      entry = parser.unqualifiedEntryWMod (string, parser.currentModule);
    return entry;
  } // getEntryForName

  /**
   * This method returns a string of all of the characters from the
   * input file from the current position up to, but not including,
   * the end-of-line character(s).
   **/
  String getStringToEOL () throws IOException
  {
    return scanner.getStringToEOL ();
  } // getStringToEOL

  /**
   * This method returns a string of all of the characters from the
   * input file from the current position up to, but not including,
   * the given character.  It encapsulates parenthesis and quoted strings,
   * meaning it does not stop if the given character is found within
   * parentheses or quotes.  For instance, given the input of
   * `start(inside)end', getUntil ('n') will return "start(inside)e"
   **/
  String getUntil (char c) throws IOException
  {
    return scanner.getUntil (c);
  } // getUntil

  private boolean lastWasMacroID = false;

  /**
   *
   **/
  private String tokenToString ()
  {
    if (token.equals (Token.MacroIdentifier))
    {
      lastWasMacroID = true;
      return token.name;
    }
    else if (token.equals (Token.Identifier))
      return token.name;
    else
      return token.toString ();
  } // tokenToString

  /**
   * This method returns the next token String from the input file.
   **/
  String nextToken () throws IOException
  {
    if (lastWasMacroID)
    {
      lastWasMacroID = false;
      return "(";
    }
    else
    {
      token = scanner.getToken ();
      return tokenToString ();
    }
  } // nextToken

  /**
   * This method assumes that the current token marks the beginning
   * of a scoped name.  It then parses the subsequent identifier and
   * double colon tokens, builds the scoped name, and finds the symbol
   * table entry with that name.
   **/
  SymtabEntry scopedName () throws IOException
  {
    boolean     globalScope  = false;
    boolean     partialScope = false;
    String      name         = null;
    SymtabEntry entry        = null;
    try
    {
      if (token.equals (Token.DoubleColon))
        globalScope = true;
      else
      {
        if (token.equals (Token.Object))
        {
          name = "Object";
          match (Token.Object);
        }
        else if (token.type == Token.ValueBase)
        {
          name = "ValueBase";
          match (Token.ValueBase);
        }
        else
        {
          name = token.name;
          match (Token.Identifier);
        }
      }
      while (token.equals (Token.DoubleColon))
      {
        match (Token.DoubleColon);
        partialScope = true;
        if (name != null)
          name = name + '/' + token.name;
        else
          name = token.name;
        match (Token.Identifier);
      }
      if (globalScope)
        entry = parser.recursiveQualifiedEntry (name);
      else if (partialScope)
        entry = parser.recursivePQEntry (name, parser.currentModule);
      else
        entry = parser.unqualifiedEntryWMod (name, parser.currentModule);
    }
    catch (ParseException e)
    {
      entry = null;
    }
    return entry;
  } // scopedName

  /**
   * Skip to the end of the line.
   **/
  void skipToEOL () throws IOException
  {
    scanner.skipLineComment ();
  } // skipToEOL

  /**
   * This method skips the data in the input file until the specified
   * character is encountered, then it returns the next token.
   **/
  String skipUntil (char c) throws IOException
  {
    if (!(lastWasMacroID && c == '('))
      token = scanner.skipUntil (c);
    return tokenToString ();
  } // skipUntil

  /**
   * This method displays a Parser Exception complete with line number
   * and position information with the given message string.
   **/
  void parseException (String message)
  {
    // <d62023> Suppress warnings
    if (!parser.noWarn)
      ParseException.warning (scanner, message);
  } // parseException

  // For Pragma
  ///////////////
  // For macro expansion

  /**
   *
   **/
  String expandMacro (String macroDef, Token t) throws IOException, ParseException
  {
    token = t;
    // Get the parameter values from the macro 'call'
    Vector parmValues = getParmValues ();

    // Get the parameter names from the macro definition
    // NOTE:  a newline character is appended here so that when
    // getStringToEOL is called, it stops scanning at the end
    // of this string.
    scanner.scanString (macroDef + '\n');
    Vector parmNames = new Vector ();
    macro (parmNames);

    if (parmValues.size () < parmNames.size ())
      throw ParseException.syntaxError (scanner, Token.Comma, Token.RightParen);
    else if (parmValues.size () > parmNames.size ())
      throw ParseException.syntaxError (scanner, Token.RightParen, Token.Comma);

    macroDef = scanner.getStringToEOL ();
    for (int i = 0; i < parmNames.size (); ++i)
      macroDef = replaceAll (macroDef, (String)parmNames.elementAt (i), (String)parmValues.elementAt (i));
    return removeDoublePound (macroDef);
  } // expandMacro

  // This method is only used by the macro expansion methods.
  /**
   *
   **/
  private void miniMatch (int type) throws ParseException
  {
    // A normal production would now execute:
    // match (type);
    // But match reads the next token.  I don't want to do that now.
    // Just make sure the current token is a 'type'.
    if (!token.equals (type))
      throw ParseException.syntaxError (scanner, type, token.type);
  } // miniMatch

  /**
   *
   **/
  private Vector getParmValues () throws IOException, ParseException
  {
    Vector values = new Vector ();
    if (token.equals (Token.Identifier))
    {
      match (Token.Identifier);
      miniMatch (Token.LeftParen);
    }
    else if (!token.equals (Token.MacroIdentifier))
      throw ParseException.syntaxError (scanner, Token.Identifier, token.type);

    if (!token.equals (Token.RightParen))
    {
      values.addElement (scanner.getUntil (',', ')').trim ());
      token = scanner.getToken ();
      macroParmValues (values);
    }
    return values;
  } // getParmValues

  /**
   *
   **/
  private void macroParmValues (Vector values) throws IOException, ParseException
  {
    while (!token.equals (Token.RightParen))
    {
      miniMatch (Token.Comma);
      values.addElement (scanner.getUntil (',', ')').trim ());
      token = scanner.getToken ();
    }
  } // macroParmValues

  /**
   *
   **/
  private void macro (Vector parmNames) throws IOException, ParseException
  {
    match (token.type);
    match (Token.LeftParen);
    macroParms (parmNames);
    miniMatch (Token.RightParen);
  } // macro

  /**
   *
   **/
  private void macroParms (Vector parmNames) throws IOException, ParseException
  {
    if (!token.equals (Token.RightParen))
    {
      parmNames.addElement (token.name);
      match (Token.Identifier);
      macroParms2 (parmNames);
    }
  } // macroParms

  /**
   *
   **/
  private void macroParms2 (Vector parmNames) throws IOException, ParseException
  {
    while (!token.equals (Token.RightParen))
    {
      match (Token.Comma);
      parmNames.addElement (token.name);
      match (Token.Identifier);
    }
  } // macroParms2

  /**
   *
   **/
  private String replaceAll (String string, String from, String to)
  {
    int index = 0;
    while (index != -1)
    {
      index = string.indexOf (from, index);
      if (index != -1)
      {
        if (!embedded (string, index, index + from.length ()))
          if (index > 0 && string.charAt(index) == '#')
            string = string.substring (0, index) + '"' + to + '"' + string.substring (index + from.length ());
          else
            string = string.substring (0, index) + to + string.substring (index + from.length ());
        index += to.length ();
      }
    }
    return string;
  } // replaceAll

  /**
   *
   **/
  private boolean embedded (String string, int index, int endIndex)
  {
    // Don't replace if found substring is not an independent id.
    // For example, don't replace "thither".indexOf ("it", 0)
    boolean ret    = false;
    char    preCh  = index == 0 ? ' ' : string.charAt (index - 1);
    char    postCh = endIndex >= string.length () - 1 ? ' ' : string.charAt (endIndex);
    if ((preCh >= 'a' && preCh <= 'z') || (preCh >= 'A' && preCh <= 'Z'))
      ret = true;
    else if ((postCh >= 'a' && postCh <= 'z') || (postCh >= 'A' && postCh <= 'Z') || (postCh >= '0' && postCh <= '9') || postCh == '_')
      ret = true;
    else
      ret = inQuotes (string, index);
    return ret;
  } // embedded

  /**
   *
   **/
  private boolean inQuotes (String string, int index)
  {
    int quoteCount = 0;
    for (int i = 0; i < index; ++i)
      if (string.charAt (i) == '"') ++quoteCount;
    // If there are an odd number of quotes before this region,
    // then this region is within quotes
    return quoteCount % 2 != 0;
  } // inQuotes

  /**
   * Remove any occurrences of ##.
   **/
  private String removeDoublePound (String string)
  {
    int index = 0;
    while (index != -1)
    {
      index = string.indexOf ("##", index);
      if (index != -1)
      {
        int startSkip = index - 1;
        int stopSkip  = index + 2;
        if (startSkip < 0)
          startSkip = 0;
        if (stopSkip >= string.length ())
          stopSkip = string.length () - 1;
        while (startSkip > 0 &&
               (string.charAt (startSkip) == ' ' ||
               string.charAt (startSkip) == '\t'))
          --startSkip;
        while (stopSkip < string.length () - 1 &&
               (string.charAt (stopSkip) == ' ' ||
               string.charAt (stopSkip) == '\t'))
          ++stopSkip;
        string = string.substring (0, startSkip + 1) + string.substring (stopSkip);
      }
    }
    return string;
  } // removeDoublePound

  // For macro expansion
  ///////////////

  /**
   *
   **/
  private String getFilename (String name) throws FileNotFoundException
  {
    String fullName = null;
    File file = new File (name);
    if (file.canRead ())
      fullName = name;
    else
    {
      Enumeration pathList = parser.paths.elements ();
      while (!file.canRead () && pathList.hasMoreElements ())
      {
        fullName = (String)pathList.nextElement () + File.separatorChar + name;
        file = new File (fullName);
      }
      if (!file.canRead ())
        throw new FileNotFoundException (name);
    }
    return fullName;
  } // getFilename

  /**
   *
   **/
  private void match (int type) throws IOException, ParseException
  {
    if (!token.equals (type))
      throw ParseException.syntaxError (scanner, type, token.type);
    token = scanner.getToken ();

    // <d62023> Added for convenience, but commented-out because there is
    // no reason to issue warnings for tokens scanned during preprocessing.
    // See issueTokenWarnings().
    //issueTokenWarnings ();

    //System.out.println ("Preprocessor.match token = " + token.type);
    //if (token.equals (Token.Identifier) || token.equals (Token.MacroIdentifier))
    //  System.out.println ("Preprocessor.match token name = " + token.name);

    // If the token is a defined thingy, scan the defined string
    // instead of the input stream for a while.
    if (token.equals (Token.Identifier) || token.equals (Token.MacroIdentifier))
    {
      String string = (String)symbols.get (token.name);
      if (string != null && !string.equals (""))
        // If this is a macro, parse the macro
        if (macros.contains (token.name))
        {
          scanner.scanString (expandMacro (string, token));
          token = scanner.getToken ();
        }
        // else this is just a normal define
        else
        {
          scanner.scanString (string);
          token = scanner.getToken ();
        }
    }
  } // match

  // <d62023>
  /**
   * Issue warnings about tokens scanned during preprocessing.
   **/
  private void issueTokenWarnings ()
  {
    if (parser.noWarn)
      return;

    // There are no keywords defined for preprocessing (only directives), so:
    //
    // 1.) Do not issue warnings for identifiers known to be keywords in
    //     another level of IDL.
    // 2.) Do not issue warnings for identifiers that collide with keywords
    //     in letter, but not case.
    // 3.) Do not issue warnings for deprecated keywords.
    //
    // Should we warn when a macro identifier replaces a keyword?  Hmmm.

    // Deprecated directives?  None to date.
    //if (token.isDirective () && token.isDeprecated ())
    //  ParseException.warning (scanner, Util.getMesage ("Deprecated.directive", token.name));
  } // issueTokenWarnings

  /**
   * This method is called when the parser encounters a left curly brace.
   * An extender of PragmaHandler may find scope information useful.
   * For example, the prefix pragma takes effect as soon as it is
   * encountered and stays in effect until the current scope is closed.
   * If a similar pragma extension is desired, then the openScope and
   * closeScope methods are available for overriding.
   * @param entry the symbol table entry whose scope has just been opened.
   *  Be aware that, since the scope has just been entered, this entry is
   *  incomplete at this point.
   **/
  void openScope (SymtabEntry entry)
  {
    for (int i = pragmaHandlers.size () - 1; i >= 0; --i)
    {
      PragmaHandler handler = (PragmaHandler)pragmaHandlers.elementAt (i);
      handler.openScope (entry);
    }
  } // openScope

  /**
   * This method is called when the parser encounters a right curly brace.
   * An extender of PragmaHandler may find scope information useful.
   * For example, the prefix pragma takes effect as soon as it is
   * encountered and stays in effect until the current scope is closed.
   * If a similar pragma extension is desired, then the openScope and
   * closeScope methods are available for overriding.
   * @param entry the symbol table entry whose scope has just been closed.
   **/
  void closeScope (SymtabEntry entry)
  {
    for (int i = pragmaHandlers.size () - 1; i >= 0; --i)
    {
      PragmaHandler handler = (PragmaHandler)pragmaHandlers.elementAt (i);
      handler.closeScope (entry);
    }
  } // closeScope

  private Parser    parser;
  private Scanner   scanner;
  private Hashtable symbols;
  private Vector    macros;

  // The logic associated with this stack is scattered above.
  // A concise map of the logic is:
  // case #if false, #ifdef false, #ifndef true
  //   push (false);
  //   skipToEndifOrElse ();
  // case #if true, #ifdef true, #ifndef false
  //   push (true);
  // case #elif <conditional>
  //   if (top == true)
  //     skipToEndif ();
  //   else if (conditional == true)
  //     pop ();
  //     push (true);
  //   else if (conditional == false)
  //     skipToEndifOrElse ();
  // case #else
  //   if (top == true)
  //     skipToEndif ();
  //   else
  //     pop ();
  //     push (true);
  // case #endif
  //   pop ();
  private        Stack  alreadyProcessedABranch = new Stack ();
                 Token  token;

  private static String indent = "";
}
