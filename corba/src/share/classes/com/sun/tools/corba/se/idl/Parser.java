/*
 * Copyright (c) 1999, 2009, Oracle and/or its affiliates. All rights reserved.
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
// -F46082.51<daz> Remove -stateful feature.
// -D52042<daz> Allow floating-point constants to be initialized with
//  integral as well as floating-point literals.  No other variations allowed.
// -D58058<daz> Set index array type to long rather than array element type.
// -D56351<daz> Update computation of RepositoryIDs to CORBA 2.3 (see spec.).
// -D57110<daz> Provide method() to set/clear ability for scoped names to
//  resolve to modules.  Allows rep. ids to be assigned to modules.
// -D46094<daz> Prohibit exceptions from appearing wihtin structs, unions, exceptions.
// -D46094<daz> Prohibit attributes from appearing as operation parameter types,
//  operation return types, attribute types.
// -D59067<daz> Prohibit nested value boxes.
// -D59166<daz> Prohibit collisions between keywords and non-escaped identifiers.
// -D59809<daz> At Pigeonhole(), add map short name of CORBA types to long name
//  (e.g., CORBA/StringValue --> org/omg/CORBA/StringValue), which allows fully-
//  qualified CORBA type names to resolve successfully.
// -F60858.1<daz> Support "-corba" option, level <= 2.2: issue warning for
//  keyowrd collisions;
// -D60942<daz> Prohibit operations from appearing within parameter types.
// -D61643<daz> Repair pigeonhole() to correctly filter bad RepIDs.
// -D62023<daz> Support -noWarn option; Issue warnings when tokens are
//  deprecated keywords or keywords in greater release version.
// -D61919<daz> Emit entries for modules originally opened in #include files
//  appearing at global scope and then reopened in the main IDL file.  Only
//  types appearing in the main IDL source will be emitted.

import java.io.EOFException;
import java.io.IOException;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.Vector;
import java.util.List ;
import java.util.ArrayList ;
import java.util.Iterator ;

import java.math.BigInteger;

import com.sun.tools.corba.se.idl.constExpr.*;

/**
 *
 **/
class Parser
{
  /**
   *
   **/
  Parser (Preprocessor preprocessor, Arguments arguments, Hashtable overrides,
      Hashtable symtab, SymtabFactory stFac, ExprFactory exprFac, String [] genKeywords)
  {
    this.arguments = arguments;
    noWarn         = arguments.noWarn; // <d62023>
    corbaLevel     = arguments.corbaLevel; // <f60858.1>
    paths          = arguments.includePaths;
    symbols        = arguments.definedSymbols;
    verbose        = arguments.verbose;
    emitAll        = arguments.emitAll;
    // <f46082.46.01>
    cppModule      = arguments.cppModule;
    // <f46082.51> Remove -stateful feature.
    //parseStateful  = arguments.parseStateful;
    overrideNames  = (overrides == null) ? new Hashtable () : overrides;
    symbolTable    = (symtab == null) ? new Hashtable () : symtab;
    keywords       = (genKeywords == null) ? new String [0] : genKeywords;
    stFactory      = stFac;
    exprFactory    = exprFac;
    currentModule  = topLevelModule = new ModuleEntry ();
    prep           = preprocessor;
    repIDStack.push (new IDLID ());
    addPrimEntries ();
  } // ctor

  /**
   *
   **/
  void parse (String file) throws IOException
  {
    IncludeEntry fileEntry = stFactory.includeEntry ();
    fileEntry.name ('"' + file + '"');
    try
    {
      // Set absolute file path
      fileEntry.absFilename (Util.getAbsolutePath (file, paths));
    }
    catch (IOException ioe)
    {}

    // <f46082.51> Remove -stateful feature.
    //scanner = new Scanner (fileEntry, keywords, verbose, parseStateful, emitAll);
    // <f60585.1> Support "-corba [level]" option.
    //scanner = new Scanner (fileEntry, keywords, verbose, emitAll);
    scanner = new Scanner (fileEntry, keywords, verbose, emitAll, corbaLevel,
        arguments.scannerDebugFlag );
    topLevelModule.sourceFile (fileEntry);

    // Prime the pump...
    // Match handles preprocessor directives, so use match to
    // call scanner.getToken just in case the first token is
    // such a directive.  But match depends on the token
    // already having a value, so fudge something.
    token = new Token (0);
    tokenHistory.insert (token); // Initialize look back buffer <26jul1997daz>.
    try
    {
      match (0);
      if (token.equals (Token.EOF))
        ParseException.nothing (file);
      else
        specification (topLevelModule);
    }
    catch (ParseException exception)  // Match MIGHT throw this
    {
      // It has already been reported, just end.
    }
    catch (EOFException exception)  // skipToSemicolon MIGHT throw this
    {
      // It has already been reported, just end.
    }
  } // parse

  /**
   *
   **/
  private void addPrimEntries ()
  {
    symbolTable.put ("short", stFactory.primitiveEntry ("short"));
    symbolTable.put ("long", stFactory.primitiveEntry ("long"));
    symbolTable.put ("long long", stFactory.primitiveEntry ("long long"));
    symbolTable.put ("unsigned short", stFactory.primitiveEntry ("unsigned short"));
    symbolTable.put ("unsigned long", stFactory.primitiveEntry ("unsigned long"));
    symbolTable.put ("unsigned long long", stFactory.primitiveEntry ("unsigned long long"));
    symbolTable.put ("char", stFactory.primitiveEntry ("char"));
    symbolTable.put ("wchar", stFactory.primitiveEntry ("wchar"));
    symbolTable.put ("float", stFactory.primitiveEntry ("float"));
    //Support fixed type: symbolTable.put ("fixed", stFactory.primitiveEntry ("fixed"));
    symbolTable.put ("double", stFactory.primitiveEntry ("double"));
    symbolTable.put ("boolean", stFactory.primitiveEntry ("boolean"));
    symbolTable.put ("octet", stFactory.primitiveEntry ("octet"));
    symbolTable.put ("any", stFactory.primitiveEntry ("any"));

    InterfaceEntry object = stFactory.interfaceEntry();
    object.name ("Object");
    symbolTable.put ("Object", object);

    ValueEntry valueBase = stFactory.valueEntry();
    valueBase.name ("ValueBase");
    symbolTable.put ("ValueBase", valueBase);

    // put these same entries in the lowercase symbol table
    lcSymbolTable.put ("short", stFactory.primitiveEntry ("short"));
    lcSymbolTable.put ("long", stFactory.primitiveEntry ("long"));
    lcSymbolTable.put ("long long", stFactory.primitiveEntry ("long long"));
    lcSymbolTable.put ("unsigned short", stFactory.primitiveEntry ("unsigned short"));
    lcSymbolTable.put ("unsigned long", stFactory.primitiveEntry ("unsigned long"));
    lcSymbolTable.put ("unsigned long long", stFactory.primitiveEntry ("unsigned long long"));
    lcSymbolTable.put ("char", stFactory.primitiveEntry ("char"));
    lcSymbolTable.put ("wchar", stFactory.primitiveEntry ("wchar"));
    lcSymbolTable.put ("float", stFactory.primitiveEntry ("float"));
    // Support fixed type: lcSymbolTable.put ("fixed", stFactory.primitiveEntry ("fixed"));
    lcSymbolTable.put ("double", stFactory.primitiveEntry ("double"));
    lcSymbolTable.put ("boolean", stFactory.primitiveEntry ("boolean"));
    lcSymbolTable.put ("octet", stFactory.primitiveEntry ("octet"));
    lcSymbolTable.put ("any", stFactory.primitiveEntry ("any"));
    lcSymbolTable.put ("object", object);
    lcSymbolTable.put ("valuebase", valueBase);
  } // addPrimEntries

  /**
   *
   **/
  private void specification (ModuleEntry entry) throws IOException
  {
    while (!token.equals (Token.EOF))
    {
      definition (entry);
      addToEmitList (entry);
    }
  } // specification

  // ModuleEntry is the topLevelModule; add its contained types to the emit list.
  /**
   *
   **/
  private void addToEmitList (ModuleEntry entry)
  {
    for (Enumeration e = entry.contained ().elements (); e.hasMoreElements();)
    {
      SymtabEntry emitEntry = (SymtabEntry)e.nextElement ();
      if (emitEntry.emit ())
      {
        emitList.addElement (emitEntry);

        // <d61919> I think the absence of the following statement was an
        // oversight.  If module X.Y.Z first appears in an include file, then is
        // reopened in the main IDL source, this statement guarantees that X.Y.Z
        // definitions within the main IDL source are emitted.
        ///---------------------------------------------------------------------
        // If any of this module's elements should be emitted, add
        // this module to the emit list.
        if (emitEntry instanceof ModuleEntry)
          checkContained ((ModuleEntry)emitEntry);
        if (emitEntry instanceof IncludeEntry)
        {
          includes.addElement (emitEntry.name ());
          includeEntries.addElement (emitEntry);
        }
      }
      else
        // If any of this module's elements should be emitted, add
        // this module to the emit list.
        if (emitEntry instanceof ModuleEntry)
          checkContained ((ModuleEntry)emitEntry);
    }
    entry.contained ().removeAllElements ();
  } // addToEmitList

  /**
   *
   **/
  private void checkContained (ModuleEntry entry)
  {
    // If any of this module's elements is to be emitted,
    // then add the module to the emit list.
    for (Enumeration e = entry.contained ().elements (); e.hasMoreElements ();)
    {
      SymtabEntry contained = (SymtabEntry)e.nextElement ();
      if (contained instanceof ModuleEntry)
        checkContained ((ModuleEntry)contained);
      if (contained.emit ())
      {
        if (!emitList.contains (entry))
          emitList.addElement (entry);
        entry.emit (true);
        break;
      }
    }
  } // checkContained

  /**
   *
   **/
  private void definition (ModuleEntry entry) throws IOException
  {
    try
    {
      switch (token.type)
      {
        case Token.Typedef:
        case Token.Struct:
        case Token.Union:
        case Token.Enum:
          typeDcl (entry);
          break;
        case Token.Const:
          constDcl (entry);
          break;
        case Token.Native:
          nativeDcl (entry);
          break;
        case Token.Exception:
          exceptDcl (entry);
          break;
        case Token.Interface:
          interfaceProd (entry, InterfaceEntry.NORMAL);
          break;
        case Token.Local:
          match( Token.Local ) ;
          if (token.type ==  Token.Interface)
              interfaceProd( entry, InterfaceEntry.LOCAL ) ;
          else
              throw ParseException.syntaxError( scanner, new int[] {
                  Token.Interface }, token.type ) ;
          break ;
        case Token.Module:
          module (entry);
          break;
        case Token.Abstract:
          match (Token.Abstract);
          if (token.type == Token.Interface)
            interfaceProd (entry, InterfaceEntry.ABSTRACT);
          else if (token.type == Token.Valuetype)
            valueProd (entry, true);
          else
            throw ParseException.syntaxError (scanner, new int[] {
                Token.Interface, Token.Valuetype }, token.type);
          break;
        case Token.Custom:
        case Token.Valuetype:
          valueProd (entry, false);
          break;
        default:
          throw ParseException.syntaxError (scanner, new int[] {
              Token.Typedef, Token.Struct,    Token.Union,     Token.Enum,
              Token.Const,   Token.Exception, Token.Interface, Token.Valuetype,
              Token.Module }, token.type);
      }
      match (Token.Semicolon);
    }
    catch (ParseException e)
    {
      skipToSemicolon ();
    }
  } // definition

  /**
   *
   **/
  private void module (ModuleEntry entry) throws IOException, ParseException
  {
    match (Token.Module);
    repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
    ModuleEntry newEntry = newModule (entry);
    ((IDLID)repIDStack.peek ()).appendToName (newEntry.name ());
    // comment must immediately precede "module" keyword
    newEntry.comment (tokenHistory.lookBack (1).comment);
    currentModule = newEntry;
    match (Token.Identifier);
    prep.openScope (newEntry);
    match (Token.LeftBrace);
    definition (newEntry);
    while (!token.equals (Token.EOF) && !token.equals (Token.RightBrace))
      definition (newEntry);
    prep.closeScope (newEntry);
    match (Token.RightBrace);
    currentModule = entry;
    repIDStack.pop ();
  } // module

  /**
   *
   **/
  private void interfaceProd (ModuleEntry entry, int interfaceType)
      throws IOException, ParseException
  {
    match (Token.Interface);
    String name = token.name;
    match (Token.Identifier);
    interface2 (entry, name, interfaceType);
  } // interfaceProd

  /**
   *
   **/
  private void interface2 (ModuleEntry module, String name, int interfaceType)
      throws IOException, ParseException
  {
    if (token.type == Token.Colon || token.type == Token.LeftBrace) {
        repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
        InterfaceEntry entry = stFactory.interfaceEntry (module,
            (IDLID)repIDStack.peek ());
        entry.sourceFile (scanner.fileEntry ());
        entry.name (name);
        entry.setInterfaceType(interfaceType);
        // Comment must immediately precede "[local | abstract] interface" keyword
        entry.comment (tokenHistory.lookBack (
            entry.getInterfaceType() == InterfaceEntry.NORMAL ? 2 : 3).comment);

        if (!ForwardEntry.replaceForwardDecl (entry))
            ParseException.badAbstract (scanner, entry.fullName ());
        pigeonhole (module, entry);
        ((IDLID)repIDStack.peek ()).appendToName (name);
        currentModule = entry;
        interfaceDcl (entry);
        currentModule = module;
        repIDStack.pop ();
    } else  { // This is a forward declaration
        ForwardEntry entry = stFactory.forwardEntry (module, (IDLID)repIDStack.peek ());
        entry.sourceFile (scanner.fileEntry ());
        entry.name (name);
        entry.setInterfaceType(interfaceType);
        // comment must immediately precede "interface" keyword.
        entry.comment (tokenHistory.lookBack (
            entry.getInterfaceType() == InterfaceEntry.NORMAL ? 2 : 3).comment);
        pigeonhole (module, entry);
    }
  } // interface2

  /**
   *
   **/
  private void interfaceDcl (InterfaceEntry entry) throws IOException, ParseException
  {
    if (token.type != Token.LeftBrace)
        inheritanceSpec (entry);
    else if (!entry.isAbstract ()) {
        SymtabEntry objectEntry = qualifiedEntry ("Object");
        SymtabEntry realOEntry  = typeOf (objectEntry);
        if (objectEntry == null)
            ;  // qualifiedEntry already generated an error message
        else if (!isInterface(realOEntry))
            ParseException.wrongType (scanner, overrideName ("Object"),
                "interface", objectEntry.typeName ());
        else
            entry.derivedFromAddElement (realOEntry, scanner);
    }

    prep.openScope (entry);
    match (Token.LeftBrace);
    while (token.type != Token.RightBrace)
        export (entry);
    prep.closeScope (entry);
    match (Token.RightBrace);
  } // interfaceDcl

  /**
   *
   **/
  private void export (InterfaceEntry entry) throws IOException
  {
    try
    {
      switch (token.type)
      {
        case Token.Typedef:
        case Token.Struct:
        case Token.Union:
        case Token.Enum:
          typeDcl (entry);
          break;
        case Token.Const:
          constDcl (entry);
          break;
        case Token.Native:
          nativeDcl (entry);
          break;
        case Token.Exception:
          exceptDcl (entry);
          break;
        case Token.Readonly:
        case Token.Attribute:
          attrDcl (entry);
          break;
        case Token.Oneway:
        case Token.Float:
        case Token.Double:
        case Token.Long:
        case Token.Short:
        case Token.Unsigned:
        case Token.Char:
        case Token.Wchar:
        case Token.Boolean:
        case Token.Octet:
        case Token.Any:
        case Token.String:
        case Token.Wstring:
        case Token.Identifier:
        case Token.Object:
        // <f46082.40> Value base type.
        case Token.ValueBase:
        case Token.DoubleColon:
        case Token.Void:
          opDcl (entry);
          break;
        // <f46082.51> Remove -stateful feature.
        //case Token.State:       if (parseStateful) {
        //                          stateDef (entry);
        //                          break; }
        default:
          throw ParseException.syntaxError(scanner, new int [] {
              Token.Typedef,  Token.Struct,      Token.Union,        Token.Enum,
              Token.Const,    Token.Exception,   Token.Readonly,     Token.Attribute,
              Token.Oneway,   Token.Float,       Token.Double,       Token.Long,
              Token.Short,    Token.Unsigned,    Token.Char,         Token.Wchar,
              Token.Boolean,  Token.Octet,       Token.Any,          Token.String,
              Token.Wstring,  Token.Identifier,  Token.DoubleColon,  Token.Void,
              Token.ValueBase }, token.type);
      }
      match (Token.Semicolon);
    }
    catch (ParseException exception)
    {
      skipToSemicolon ();
    }
  } // export

  private void inheritanceSpec (InterfaceEntry entry) throws IOException, ParseException
  {
    for (match (Token.Colon); ; match (Token.Comma)) {
        SymtabEntry parent = scopedName (entry.container (),
            stFactory.interfaceEntry ());
        SymtabEntry realParent = typeOf (parent);

        if (isInterfaceOnly (realParent)) {
            boolean isInterface = (realParent instanceof InterfaceEntry);
            if (entry.derivedFrom ().contains (realParent))
                ParseException.alreadyDerived (scanner, realParent.fullName (), entry.fullName ());
            else if (!entry.isAbstract () ||
                (((InterfaceType)realParent).getInterfaceType() == InterfaceType.ABSTRACT))
                entry.derivedFromAddElement (realParent, scanner);
            else
                ParseException.nonAbstractParent (scanner, entry.fullName (), parent.fullName ());
        } else if (isForward( realParent )) {
            ParseException.illegalForwardInheritance( scanner,
                entry.fullName(), parent.fullName() ) ;
        } else
            ParseException.wrongType (scanner, parent.fullName (), "interface", entryName (parent));

        if ((parent instanceof InterfaceEntry) && (((InterfaceEntry)parent).state () != null))
            if (entry.state () == null)
                entry.initState ();
            else
                throw ParseException.badState (scanner, entry.fullName ());

        if (token.type != Token.Comma)
            break;
    }
  } // inheritanceSpec

  // <57110> Member _moduleIsLegalType may be set by any feature to allow
  // method scopedName() and any of its helper methods -- qualifiedName(),
  // partlyQualifiedName(), and unqualifiedName() -- to return a ModuleEntry
  // rather than a parse error in the event a name resolves to a module.  The
  // flag must be cleared (set to false) to resume normal parsing behavior.
  //
  // Currently, this is used only when preprocessing the ID pragma directive.

  private boolean _isModuleLegalType = false;

  /**
   *
   **/
  public boolean isModuleLegalType ()
  {
    return _isModuleLegalType;
  }; // moduleIsLegaType

  /**
   *
   **/
  public void isModuleLegalType (boolean b)
  {
    _isModuleLegalType = b;
  }; // moduleIsLegalType

  /**
   *
   **/
  SymtabEntry scopedName (SymtabEntry container,
    SymtabEntry expected) throws IOException, ParseException
  {
    return scopedName( container, expected, true ) ;
  }

  SymtabEntry scopedName (SymtabEntry container, SymtabEntry expected,
    boolean mustBeReferencable ) throws IOException, ParseException
  {
    boolean globalScope  = false;
    boolean partialScope = false;
    String  name         = null;
    if (token.type == Token.DoubleColon)
      globalScope = true;
    else
    {
      if (token.type == Token.Object)
      {
        name = "Object";
        match (Token.Object);
      }
      else if (token.type == Token.ValueBase) // <f46082.40>
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
    while (token.type == Token.DoubleColon)
    {
      match (Token.DoubleColon);
      partialScope = true;
      if (name != null)
        name += '/' + token.name;
      else name = token.name;
        match (Token.Identifier);
    }
    SymtabEntry entry = null;
    if (globalScope)
      entry = qualifiedEntry (name);
    else if (partialScope)
      entry = partlyQualifiedEntry (name, container);
    else
      entry = unqualifiedEntry (name, container);

    if (entry == null)
      // Make the entry the expected entry.  The generators will
      // not be called now, since a semantic exception ocurred, but
      // the parse has to finish and something valid has to be
      // returned.
      (entry = expected).name (name);
    else if (!entry.isReferencable() && mustBeReferencable)
      throw ParseException.illegalIncompleteTypeReference( scanner, name ) ;

    return entry;
  } // scopedName

  private void valueProd (ModuleEntry entry, boolean isAbstract) throws IOException, ParseException
  {
    boolean isCustom = (token.type == Token.Custom);
    if (isCustom)
      match (Token.Custom);
    match (Token.Valuetype);
    String name = token.name;
    match (Token.Identifier);

    switch (token.type)
    {
      case Token.LeftBrace:
      case Token.Colon:
      case Token.Supports:
        value2 (entry, name, isAbstract, isCustom);
        return;
      case Token.Semicolon:
        if (isCustom)
          break;
        valueForwardDcl (entry, name, isAbstract);
        return;
    }
    if (isCustom)
      throw ParseException.badCustom (scanner);
    if (isAbstract)
      throw ParseException.abstractValueBox (scanner);
    valueBox (entry, name);
  }  // valueProd

  /**
   *
   **/
  private void value2 (ModuleEntry module, String name, boolean isAbstract,
      boolean isCustom) throws IOException, ParseException
  {
    repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
    // The 'actual' repository ID will be calculated at the end of the
    // parsing phase, since it is based on the entire contents of the
    // declaration, and needs to have all forward references resolved:
    ValueEntry entry = stFactory.valueEntry (module, (IDLID)repIDStack.peek ());
    entry.sourceFile (scanner.fileEntry ());
    entry.name (name);
    entry.setInterfaceType (isAbstract ? InterfaceType.ABSTRACT : InterfaceType.NORMAL);
    entry.setCustom (isCustom);
    // Comment must immediately precede "[abstract | custom] value" keyword
    entry.comment (tokenHistory.lookBack ((isAbstract || isCustom) ? 3 : 2).comment);
    // If this value has been forward declared, there are probably
    // other values which derive from a ForwardValueEntry.  Replace
    // those ForwardValueEntry's with this ValueEntry:
    if (!ForwardEntry.replaceForwardDecl (entry))
      ParseException.badAbstract (scanner, entry.fullName ());
    pigeonhole (module, entry);
    ((IDLID)repIDStack.peek ()).appendToName (name);
    currentModule = entry;
    valueDcl (entry);
    entry.tagMethods ();
    currentModule = module;
    repIDStack.pop ();
  } // value2

  /**
   *
   **/
  private void valueDcl (ValueEntry entry) throws IOException, ParseException
  {
    if (token.type == Token.Colon)
      valueInheritanceSpec (entry);
    else if (!entry.isAbstract ())
    {
      SymtabEntry objectEntry = qualifiedEntry ("ValueBase");
      SymtabEntry realOEntry  = typeOf (objectEntry);
      if (objectEntry == null)
        ; // qualifiedEntry already generated an error message
      else if (!isValue (realOEntry))
        ParseException.wrongType (scanner, overrideName ("ValueBase"), "value", objectEntry.typeName ());
      else
        entry.derivedFromAddElement (realOEntry, false, scanner);
    }
    if (token.type == Token.Supports)
      valueSupportsSpec (entry);
    prep.openScope (entry);
    match (Token.LeftBrace);
    while (token.type != Token.RightBrace)
    {
      valueElement (entry);
    }
    prep.closeScope (entry);
    match (Token.RightBrace);
  } // valueDcl

  /**
   *
   **/
  private void valueInheritanceSpec (ValueEntry entry) throws IOException, ParseException
  {
    match (Token.Colon);
    boolean isTruncatable = (token.type == Token.Truncatable);
    if (isTruncatable)
        match (Token.Truncatable);
    for (; ; match (Token.Comma), isTruncatable = false) {
        SymtabEntry parent = scopedName (entry.container (),
            stFactory.valueEntry ());
        SymtabEntry realParent = typeOf (parent);
        if (isValue (realParent) && !(realParent instanceof ValueBoxEntry))
            entry.derivedFromAddElement (realParent, isTruncatable,
                scanner);
        else if (isForward(realParent))
            ParseException.illegalForwardInheritance( scanner,
                entry.fullName(), parent.fullName() ) ;
        else
            ParseException.wrongType (scanner,
                parent.fullName (), "value", entryName (parent));
        if (token.type != Token.Comma)
            break;
    }
  } // valueInheritanceSpec

  /**
   *
   **/
  private void valueSupportsSpec (ValueEntry entry) throws IOException, ParseException
  {
    match (Token.Supports) ;
    for (; ; match( Token.Comma ) ) {
        SymtabEntry parent = scopedName (entry.container (), stFactory.interfaceEntry ());
        SymtabEntry realParent = typeOf (parent);
        if (isInterface(realParent))
            entry.derivedFromAddElement (realParent, scanner);
        else
            ParseException.wrongType (scanner, parent.fullName (), "interface",
                entryName (parent));

        if (token.type != Token.Comma)
            break;
    }
  }  // valueSupportsSpec

  private void valueElement (ValueEntry entry) throws IOException, ParseException
  {
    if (entry.isAbstract ())
      export (entry);
    else
      switch (token.type)
      {
        case Token.Private:
        case Token.Public:
          valueStateMember (entry);
          break;
        case Token.Init:
        case Token.Factory:  // <d62023> "factory" supplants "init" in 2.4RTF
          initDcl (entry);
          break;
        case Token.Typedef:
        case Token.Struct:
        case Token.Union:
        case Token.Enum:
        case Token.Const:
        case Token.Native:
        case Token.Exception:
        case Token.Readonly:
        case Token.Attribute:
        case Token.Oneway:
        case Token.Float:
        case Token.Double:
        case Token.Long:
        case Token.Short:
        case Token.Unsigned:
        case Token.Char:
        case Token.Wchar:
        case Token.Boolean:
        case Token.Octet:
        case Token.Any:
        case Token.String:
        case Token.Wstring:
        case Token.Identifier:
        case Token.Object:
        case Token.ValueBase:
        case Token.DoubleColon:
        case Token.Void:
          export (entry);
          break;
        default:
          throw ParseException.syntaxError(scanner, new int[] {
              Token.Private,  Token.Public,      Token.Init,         Token.ValueBase,
              Token.Typedef,  Token.Struct,      Token.Union,        Token.Enum,
              Token.Const,    Token.Exception,   Token.Readonly,     Token.Attribute,
              Token.Oneway,   Token.Float,       Token.Double,       Token.Long,
              Token.Short,    Token.Unsigned,    Token.Char,         Token.Wchar,
              Token.Boolean,  Token.Octet,       Token.Any,          Token.String,
              Token.Wstring,  Token.Identifier,  Token.DoubleColon,  Token.Void },
              token.type);
      }  // switch
  }  // valueElement

  // <f46082.40>
  /**
   *
   **/
  private void valueStateMember (ValueEntry entry) throws IOException, ParseException
  {
    TypedefEntry typedefEntry =
        stFactory.typedefEntry (entry, (IDLID)repIDStack.peek ());
    typedefEntry.sourceFile (scanner.fileEntry ());
    // comment must immediately precede "public", "private" keywords
    typedefEntry.comment (token.comment);
    boolean isPublic = (token.type == Token.Public);
    if (isPublic)
      match (Token.Public);
    else
      match (Token.Private);
    // <f46082.40> Add constructed types declared "inline" to the contained
    // vector of this value entry.
    boolean isConstTypeSpec =
        (token.type == Token.Struct || token.type == Token.Union || token.type == Token.Enum);
    // <f46082.40> Make typedefEntry anonymous.  If this line is removed,
    // the entry will be named incorrectly.  See <d50618>.
    typedefEntry.name ("");
    typedefEntry.type (typeSpec (typedefEntry));
    addDeclarators (entry, typedefEntry, isPublic);
    // <f46082.40>
    if (isConstTypeSpec)
      entry.addContained (typedefEntry);
    match (Token.Semicolon);
  }  // valueStateMember


  private void addDeclarators (ValueEntry entry, TypedefEntry typedefEntry,
      boolean isPublic) throws IOException, ParseException
  {
    int modifier = isPublic ? InterfaceState.Public : InterfaceState.Private;
    try
    {
      Vector typedefList = new Vector ();
      declarators (typedefEntry, typedefList);
      for (Enumeration e = typedefList.elements (); e.hasMoreElements ();)
        entry.addStateElement (
            new InterfaceState (modifier, (TypedefEntry)e.nextElement ()), scanner);
    }
    catch (ParseException exception)
    {
      skipToSemicolon ();
    }
  } // addDeclarators

  /**
   *
   **/
  private void initDcl (ValueEntry entry) throws IOException, ParseException
  {
    MethodEntry method = stFactory.methodEntry (entry, (IDLID)repIDStack.peek ());
    method.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede "init" keyword:
    method.comment (token.comment);
    repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
    ((IDLID)repIDStack.peek ()).appendToName (token.name);

    // <d62023> In 2.3 prelim, <init_dcl> ::= "init" "(" ...
    if (token.type == Token.Init)
    {
      method.name ("init");
      match (Token.Init);
      match (Token.LeftParen);
    }
    else // <d62023> In 2.4rtf, <init_dcl> ::= "factory" <Indentifier> "(" ...
    {
      match (Token.Factory);
      method.name (token.name);
      if (token.type == Token.MacroIdentifier)
        match (Token.MacroIdentifier);  // "(" already consumed.
      else
      {
        match (Token.Identifier);
        match (Token.LeftParen);
      }
    }

    if (token.type != Token.RightParen)
      for (;;)
      {
        initParamDcl (method);
        if (token.type == Token.RightParen)
          break;
        match (Token.Comma);
      }
    entry.initializersAddElement (method, scanner);
    match (Token.RightParen);
    match (Token.Semicolon);
    repIDStack.pop ();
  } // initDcl

  /**
   *
   **/
  private void initParamDcl (MethodEntry entry) throws IOException, ParseException
  {
    ParameterEntry parmEntry = stFactory.parameterEntry (entry, (IDLID)repIDStack.peek ());
    parmEntry.sourceFile (scanner.fileEntry());
    // Comment must immediately precede parameter attribute
    parmEntry.comment (token.comment);
    match (Token.In);
    parmEntry.passType (ParameterEntry.In);
    parmEntry.type (paramTypeSpec (entry));
    parmEntry.name (token.name);
    match (Token.Identifier);
    if (isntInList (entry.parameters (), parmEntry.name ()))
      entry.addParameter (parmEntry);
  } // initParamDcl

  /**
   *
   **/
  private void valueBox (ModuleEntry module, String name) throws IOException, ParseException
  {
    repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
    // Note: The 'actual' repository ID will be calculated at the end of
    // the parsing phase, since it is based on the entire contents of the
    // declaration, and needs to have all forward references resolved:
    ValueEntry entry = stFactory.valueBoxEntry (module, (IDLID)repIDStack.peek ());
    entry.sourceFile (scanner.fileEntry ());
    entry.name (name);
    // comment must immediately precede "value" keyword
    entry.comment (tokenHistory.lookBack (2).comment);
    // <f46082.40> Value boxes may not be forwarded.
    // If this value has been forward declared, there are probably
    // other values which derive from a ForwardValueEntry.
    // Replace those ForwardValueEntry's with this ValueEntry:
    //if (!ForwardValueEntry.replaceForwardDecl (entry))
    //   ParseException.badAbstract (scanner, entry.fullName());
    SymtabEntry valueForward = (SymtabEntry)Parser.symbolTable.get (entry.fullName ());
    if (valueForward != null && valueForward instanceof ForwardEntry)
      ParseException.forwardedValueBox (scanner, entry.fullName ());
    pigeonhole (module, entry);
    ((IDLID)repIDStack.peek ()).appendToName (name);
    currentModule = entry;
    TypedefEntry typedefEntry = stFactory.typedefEntry (entry, (IDLID)repIDStack.peek ());
    typedefEntry.sourceFile (scanner.fileEntry ());
    typedefEntry.comment (token.comment);
    // <d50237> Workaround to place typedefEntry in the _contained vector of
    // this value box entry ONLY when <type_spec> is a constructed type declared
    // at this point (i.e., not an identifier that resolves to a constructed
    // type), so that emitters may generate bindings for it. <daz>
    boolean isConstTypeSpec =
        token.type == Token.Struct || token.type == Token.Union || token.type == Token.Enum;
    // <d50618> Make typedefEntry anonymous.  If this line is removed, the
    // entry will be named incorrectly.
    typedefEntry.name ("");
    typedefEntry.type (typeSpec (typedefEntry));
    // <d59067> Value boxes cannot be nested.
    if (typedefEntry.type () instanceof ValueBoxEntry)
      ParseException.nestedValueBox (scanner);
    //typedefEntry.name ("");
    entry.addStateElement (new InterfaceState (InterfaceState.Public, typedefEntry), scanner);
    if (isConstTypeSpec)
      entry.addContained (typedefEntry);
    currentModule = module;
    repIDStack.pop ();
  } // valueBox

  /**
   *
   **/
  private void valueForwardDcl (ModuleEntry module, String name, boolean isAbstract)
      throws IOException, ParseException
  {
    ForwardValueEntry entry = stFactory.forwardValueEntry (module, (IDLID)repIDStack.peek ());
    entry.sourceFile (scanner.fileEntry ());
    entry.name (name);
    entry.setInterfaceType(isAbstract ? InterfaceType.ABSTRACT : InterfaceType.NORMAL );
    // Comment must immediately precede "[abstract] value" keyword[s]
    entry.comment (tokenHistory.lookBack (isAbstract? 3 : 2).comment);
    pigeonhole (module, entry);
  } // valueForwardDcl

  private void nativeDcl (SymtabEntry entry) throws IOException, ParseException
  {
    match (Token.Native);
    NativeEntry nativeEntry = stFactory.nativeEntry (entry, (IDLID)repIDStack.peek ());
    nativeEntry.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede "native" keyword
    nativeEntry.comment (tokenHistory.lookBack (1).comment);
    nativeEntry.name (token.name);
    match (Token.Identifier);
    pigeonhole (entry, nativeEntry);
  } // nativeDcl
  /**
   *
   **/
  private void constDcl (SymtabEntry entry) throws IOException, ParseException
  {
    match (Token.Const);
    ConstEntry constEntry = stFactory.constEntry (entry, (IDLID)repIDStack.peek ());
    constEntry.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede "const" keyword
    constEntry.comment (tokenHistory.lookBack (1).comment);
    constType (constEntry);
    constEntry.name (token.name);
    match (Token.Identifier);
    match (Token.Equal);
    constEntry.value (constExp (constEntry));
    verifyConstType (constEntry.value (), typeOf (constEntry.type ()));
    pigeonhole (entry, constEntry);
  } // constDcl

  /**
   *
   **/
  private void constType (SymtabEntry entry) throws IOException, ParseException
  {
    switch (token.type)
    {
      case Token.Octet:
        entry.type( octetType()) ;
        break ;
      case Token.Long:
      case Token.Short:
      case Token.Unsigned:
        entry.type (integerType (entry));
        break;
      case Token.Char:
      case Token.Wchar:
        entry.type (charType ());
        break;
      case Token.Boolean:
        entry.type (booleanType ());
        break;
      case Token.Float:
      case Token.Double:
        entry.type (floatingPtType ());
        break;
      case Token.String:
      case Token.Wstring:
        entry.type (stringType (entry));
        break;
      case Token.Identifier:
      case Token.DoubleColon:
        entry.type (scopedName (entry.container (), stFactory.primitiveEntry ()));
        if (hasArrayInfo (entry.type ()))
          ParseException.illegalArray (scanner, "const");
        SymtabEntry entryType = typeOf (entry.type ());
        if (!((entryType instanceof PrimitiveEntry) || (entryType instanceof StringEntry)))
        {
          ParseException.wrongType(scanner, entry.fullName (), "primitive or string", entryName (entry.type ()));
          entry.type (qualifiedEntry ("long"));
        }
        else if (entryType instanceof PrimitiveEntry)
        {
          String any = overrideName ("any");
          if (entryType.name().equals (any))
          {
            ParseException.wrongType (scanner, entry.fullName (), "primitive or string (except " + any + ')', any);
            entry.type (qualifiedEntry ("long"));
          }
        }
        break;
      default:
        throw ParseException.syntaxError (scanner, new int [] {
                      Token.Long,   Token.Short,   Token.Unsigned, Token.Char,
                      Token.Wchar,  Token.Boolean, Token.Float,    Token.Double,
                      Token.String, Token.Wstring, Token.Identifier,
                      Token.DoubleColon }, token.type);
    }
  } // constType

  /**
   *
   **/
  private boolean hasArrayInfo (SymtabEntry entry)
  {
    while (entry instanceof TypedefEntry)
    {
      if (((TypedefEntry)entry).arrayInfo ().size () != 0)
        return true;
      entry = entry.type ();
    }
  return false;
  } // hasArrayInfo

  /**
   *
   **/
  public static String overrideName (String string)
  {
    String name = (String)overrideNames.get (string);
    return (name == null) ? string : name;
  } // overrideName

  // If entry is boolean, expression value must be boolean
  // If entry is float/double, expression value must be float/double
  // If entry is integral, expression value must be integral
  // If entry is string, expression value must be string

  /**
   *
   **/
  private void verifyConstType (Expression e, SymtabEntry t)
  {
    Object value = e.value ();
    if (value instanceof BigInteger)
      verifyIntegral ((Number)value, t);
    else if (value instanceof String)
      verifyString (e, t);
    else if (value instanceof Boolean)
      verifyBoolean (t);
    else if (value instanceof Character)
      verifyCharacter (e, t);
    else if (value instanceof Float || value instanceof Double)
      verifyFloat((Number)value, t);
    else if (value instanceof ConstEntry)
      verifyConstType (((ConstEntry)value).value (), t);
    else
      ParseException.wrongExprType (scanner, t.fullName (),
          (value == null) ? "" : value.toString ());
  } // verifyConstType

  private static final int MAX_SHORT  = 32767;
  private static final int MIN_SHORT  = -32768;
  private static final int MAX_USHORT = 65535;

  /**
   *
   **/
  private void verifyIntegral (Number n, SymtabEntry t)
  {
    boolean outOfRange = false;
    //KEEP: Useful for debugging com.sun.tools.corba.se.idl.constExpr package
    //System.out.println ("verifyIntegral, n = " + n.toString ());

    if (t == qualifiedEntry( "octet" )) {
        if ((n.longValue() > 255) || (n.longValue() < 0))
            outOfRange = true ;
    } else if (t == qualifiedEntry ("long")) {
        if (n.longValue () > Integer.MAX_VALUE || n.longValue() < Integer.MIN_VALUE)
            outOfRange = true;
    } else if (t == qualifiedEntry ("short")) {
        if (n.intValue () > Short.MAX_VALUE || n.intValue () < Short.MIN_VALUE)
            outOfRange = true;
    } else if (t == qualifiedEntry ("unsigned long")) {
        if (n.longValue () > (long)Integer.MAX_VALUE*2+1 || n.longValue() < 0)
            outOfRange = true;
    } else if (t == qualifiedEntry ("unsigned short")) {
        if (n.intValue () > (int) Short.MAX_VALUE*2+1 || n.intValue () < 0)
            outOfRange = true;
    } else if (t == qualifiedEntry ("long long")) {
        // BigInteger required because value being compared may exceed
        // java.lang.Long.MAX_VALUE/MIN_VALUE:
        BigInteger llMax = BigInteger.valueOf (Long.MAX_VALUE);
        BigInteger llMin = BigInteger.valueOf (Long.MIN_VALUE);
        if (((BigInteger)n).compareTo (llMax) > 0 ||
            ((BigInteger)n).compareTo (llMin) < 0)
            outOfRange = true;
    } else if (t == qualifiedEntry ("unsigned long long")) {
        BigInteger ullMax = BigInteger.valueOf (Long.MAX_VALUE).
            multiply (BigInteger.valueOf (2)).
            add (BigInteger.valueOf (1));
        BigInteger ullMin = BigInteger.valueOf (0);
        if (((BigInteger)n).compareTo (ullMax) > 0 ||
            ((BigInteger)n).compareTo (ullMin) < 0)
            outOfRange = true;
    } else {
        String got = null;
        // THIS MUST BE CHANGED; BIGINTEGER IS ALWAYS THE CONTAINER
        /*
        if (n instanceof Short)
          got = "short";
        else if (n instanceof Integer)
          got = "long";
        else
          got = "long long";
        */
        got = "long";
        ParseException.wrongExprType (scanner, t.fullName (), got);
    }

    if (outOfRange)
        ParseException.outOfRange (scanner, n.toString (), t.fullName ());
  } // verifyIntegral

  /**
   *
   **/
  private void verifyString (Expression e, SymtabEntry t)
  {
    String string = (String)(e.value()) ;
    if (!(t instanceof StringEntry)) {
        ParseException.wrongExprType (scanner, t.fullName (), e.type() );
    } else if (((StringEntry)t).maxSize () != null) {
        Expression maxExp = ((StringEntry)t).maxSize ();
        try {
            Number max = (Number)maxExp.value ();
            if (string.length () > max.intValue ())
                ParseException.stringTooLong (scanner, string, max.toString ());
        } catch (Exception exception) {
            // If the above statement is not valid and throws an
            // exception, then an error occurred and was reported
            // earlier.  Move on.
        }
    }

    if (!e.type().equals( t.name())) {
        // cannot mix strings and wide strings
        ParseException.wrongExprType (scanner, t.name(), e.type() ) ;
    }
  } // verifyString

  /**
   *
   **/
  private void verifyBoolean (SymtabEntry t)
  {
    if (!t.name ().equals (overrideName ("boolean")))
      ParseException.wrongExprType(scanner, t.name(), "boolean");
  } // verifyBoolean

  /**
   *
   **/
  private void verifyCharacter (Expression e, SymtabEntry t)
  {
    // Bug fix 4382578:  Can't compile a wchar literal.
    // Allow a Character to be either a char or a wchar.
    if (!t.name ().equals (overrideName ("char")) &&
        !t.name ().equals (overrideName ("wchar")) ||
        !t.name().equals(e.type()) )
        ParseException.wrongExprType (scanner, t.fullName(), e.type() ) ;
  } // verifyCharacter

  /**
   *
   **/
  private void verifyFloat (Number f, SymtabEntry t)
  {
    // <d52042> Added range checking for floats.
    //if (!(t.name ().equals (overrideName ("float")) ||
    //    t.name ().equals (overrideName ("double"))))
    //  ParseException.wrongExprType (scanner,
    //      t.fullName (), (f instanceof Float) ? "float" : "double");
    //KEEP: Useful for debugging com.sun.tools.corba.se.idl.constExpr package
    //System.out.println ("verifyFloat, f = " + f.toString ());
    boolean outOfRange = false;
    if (t.name ().equals (overrideName ("float")))
    {
      double absVal = (f.doubleValue () < 0.0) ?
          f.doubleValue () * -1.0 : f.doubleValue ();
      if ((absVal != 0.0) &&
          (absVal > Float.MAX_VALUE || absVal < Float.MIN_VALUE))
        outOfRange = true;
    }
    else if (t.name ().equals (overrideName ("double")))
    {
      // Cannot check range of double until BigDecimal is the basis
      // of all floating-point types.  Currently, it is Double.  The
      // parser will fail when instantiating a Double with an exception.
    }
    else
    {
      ParseException.wrongExprType (scanner, t.fullName (),
        (f instanceof Float) ? "float" : "double");
    }
    if (outOfRange)
      ParseException.outOfRange (scanner, f.toString (), t.fullName ());
  } // verifyFloat

  /**
   *
   **/
  Expression constExp (SymtabEntry entry) throws IOException, ParseException
  {
    // Parse the expression.
    Expression expr = orExpr (null, entry);

    // Set its target type.
    if (expr.type() == null)
      expr.type (entry.typeName ());
    // Compute its value and <d53042> coerce it to the target type.
    try
    {
      expr.evaluate ();

      // <d54042> Coerces integral value to Double if an integer literal
      // was used to initialize a floating-point constant expression.
      if (expr instanceof Terminal &&
          expr.value () instanceof BigInteger &&
          (overrideName (expr.type ()).equals ("float") ||
               overrideName (expr.type ()).indexOf ("double") >= 0))
      {
        expr.value (new Double (((BigInteger)expr.value ()).doubleValue ()));
      }
    }
    catch (EvaluationException exception)
    {
      ParseException.evaluationError (scanner, exception.toString ());
    }
    return expr;
  } // constExp

  /**
   *
   **/
  private Expression orExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
      e = xorExpr (null, entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (xorExpr (null, entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.Bar))
    {
      match (token.type);
      Or or = exprFactory.or (e, null);
      or.type (entry.typeName ());
      or.rep (e.rep () + " | ");
      return orExpr (or, entry);
    }
    return e;
  } // orExpr

  /**
   *
   **/
  private Expression xorExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
      e = andExpr (null, entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (andExpr (null, entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.Carat))
    {
      match (token.type);
      Xor xor = exprFactory.xor (e, null);
      xor.rep (e.rep () + " ^ ");
      xor.type (entry.typeName ());
      return xorExpr (xor, entry);
    }
    return e;
  } // xorExpr

  /**
   *
   **/
  private Expression andExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
      e = shiftExpr (null, entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (shiftExpr (null, entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.Ampersand))
    {
      match (token.type);
      And and = exprFactory.and (e, null);
      and.rep(e.rep () + " & ");
      and.type (entry.typeName ());
      return andExpr (and, entry);
    }
    return e;
  } // andExpr

  /**
   *
   **/
  private Expression shiftExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
      e = addExpr (null, entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (addExpr (null, entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.ShiftLeft))
    {
      match (token.type);
      ShiftLeft sl = exprFactory.shiftLeft (e, null);
      sl.type (entry.typeName ());
      sl.rep (e.rep () + " << ");
      return shiftExpr (sl, entry);
    }
    if (token.equals (Token.ShiftRight))
    {
      match (token.type);
      ShiftRight sr = exprFactory.shiftRight (e, null);
      sr.type (entry.typeName ());
      sr.rep (e.rep () + " >> ");
      return shiftExpr (sr, entry);
    }
    return e;
  } // shiftExpr

  /**
   *
   **/
  private Expression addExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
      e = multExpr (null, entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (multExpr (null, entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.Plus))
    {
      match (token.type);
      Plus p = exprFactory.plus (e, null);
      p.type (entry.typeName ());
      p.rep (e.rep () + " + ");
      return addExpr (p, entry);
    }
    if (token.equals (Token.Minus))
    {
      match (token.type);
      Minus m = exprFactory.minus (e, null);
      m.type (entry.typeName ());
      m.rep (e.rep () + " - ");
      return addExpr (m, entry);
    }
    return e;
  } // addExpr

  /**
   *
   **/
  private Expression multExpr (Expression e, SymtabEntry entry) throws IOException, ParseException
  {
    if (e == null)
    e = unaryExpr (entry);
    else
    {
      BinaryExpr b = (BinaryExpr)e;
      b.right (unaryExpr (entry));
      e.rep (e.rep () + b.right ().rep ());
    }
    if (token.equals (Token.Star))
    {
      match (token.type);
      Times t = exprFactory.times (e, null);
      t.type (entry.typeName ());
      t.rep (e.rep () + " * ");
      return multExpr (t, entry);
    }
    if (token.equals (Token.Slash))
    {
      match (token.type);
      Divide d = exprFactory.divide (e, null);
      d.type (entry.typeName ());
      d.rep (e.rep () + " / ");
      return multExpr (d, entry);
    }
    if (token.equals (Token.Percent))
    {
      match (token.type);
      Modulo m = exprFactory.modulo (e, null);
      m.type (entry.typeName ());
      m.rep (e.rep () + " % ");
      return multExpr (m, entry);
    }
    return e;
  } // multExpr

  /**
   *
   **/
  private Expression unaryExpr (SymtabEntry entry) throws IOException, ParseException
  {
    if (token.equals (Token.Plus))
    {
      match (token.type);
      Expression e   = primaryExpr (entry);
      Positive   pos = exprFactory.positive (e);
      pos.type (entry.typeName());
      pos.rep ('+' + e.rep());
      return pos;
    }
    if (token.equals (Token.Minus))
    {
      match (token.type);
      Expression e   = primaryExpr (entry);
      Negative   neg = exprFactory.negative (e);
      neg.type (entry.typeName());
      neg.rep ('-' + e.rep());
      return neg;
    }
    if (token.equals (Token.Tilde))
    {
      match (token.type);
      Expression e   = primaryExpr (entry);
      Not        not = exprFactory.not (e);
      not.type (entry.typeName());
      not.rep ('~' + e.rep());
      return not;
    }
    return primaryExpr (entry);
  } // unaryExpr

  /**
   *
   **/
  private Expression primaryExpr (SymtabEntry entry)
      throws IOException, ParseException
  {
    Expression primary = null;
    if (parsingConditionalExpr)
    {
      prep.token = token; // Give current token to preprocessor
      primary    = prep.primaryExpr (entry);
      token      = prep.token; // Get the current token from preprocessor
    }
    else
      switch (token.type)
      {
        case Token.Identifier:
        case Token.DoubleColon:
          ConstEntry expectedC = stFactory.constEntry ();
          expectedC.value (exprFactory.terminal ("1", BigInteger.valueOf (1)));
          SymtabEntry ref = scopedName (entry.container (), expectedC);
          if (!(ref instanceof ConstEntry))
          {
            ParseException.invalidConst (scanner, ref.fullName ());
            // An error occurred.  Just give it some bogus value. <daz>
            //primary = exprFactory.terminal ("1", new Long (1));
            primary = exprFactory.terminal ("1", BigInteger.valueOf (1));
          }
          else
            primary = exprFactory.terminal ((ConstEntry)ref);
          break;
        case Token.BooleanLiteral:
        case Token.CharacterLiteral:
        case Token.IntegerLiteral:
        case Token.FloatingPointLiteral:
        case Token.StringLiteral:
          primary = literal (entry);
          break;
        case Token.LeftParen:
          match (Token.LeftParen);
          primary = constExp (entry);
          match (Token.RightParen);
          primary.rep ('(' + primary.rep () + ')');
          break;
        default:
          throw ParseException.syntaxError (scanner, new int [] {
              Token.Identifier, Token.DoubleColon, Token.Literal, Token.LeftParen},
              token.type);
      }
    return primary;
  } // primaryExpr

  /**
   *
   **/
  Expression literal (SymtabEntry entry) throws IOException, ParseException
  {
    String     string  = token.name;
    Expression literal = null;
    switch (token.type)
    {
      case Token.IntegerLiteral:
        match (Token.IntegerLiteral);
        try
        {
          literal = exprFactory.terminal (string, parseString (string));
          literal.type (entry.typeName ());
        }
        catch (NumberFormatException exception)
        {
          ParseException.notANumber (scanner, string);
          literal = exprFactory.terminal ("0", BigInteger.valueOf (0));
        }
        break;
      case Token.CharacterLiteral:
        boolean isWide = token.isWide();
        match (Token.CharacterLiteral);
        literal = exprFactory.terminal ("'" + string.substring (1) + "'",
            new Character (string.charAt (0)), isWide );
        break;
      case Token.FloatingPointLiteral:
        match (Token.FloatingPointLiteral);
        try
        {
          literal = exprFactory.terminal (string, new Double (string));
          literal.type (entry.typeName ());
        }
        catch (NumberFormatException e)
        {
          ParseException.notANumber (scanner, string);
        }
        break;
      case Token.BooleanLiteral:
        literal = booleanLiteral ();
        break;
      case Token.StringLiteral:
        literal = stringLiteral ();
        break;
      default:
        throw ParseException.syntaxError (scanner, Token.Literal,token.type);
    }
    return literal;
  } // literal

  /**
   *
   **/
  private BigInteger parseString (String string) throws NumberFormatException
  {
    int radix = 10;
    if (string.length() > 1)
      if (string.charAt (0) == '0')
        if (string.charAt (1) == 'x' || string.charAt (1) == 'X')
        {
          string = string.substring (2);
          radix = 16;
        }
        else
          radix = 8;
    return new BigInteger (string, radix);
  } // parseString

  /**
   *
   **/
  private Terminal booleanLiteral () throws IOException, ParseException
  {
    Boolean bool = null;
    if (token.name.equals ("TRUE"))
      bool = new Boolean (true);
    else if (token.name.equals ("FALSE"))
      bool = new Boolean (false);
    else
    {
      ParseException.invalidConst (scanner, token.name);
      bool = new Boolean (false);
    }
    String name = token.name;
    match (Token.BooleanLiteral);
    return exprFactory.terminal (name, bool);
  } // booleanLiteral

  /**
   *
   **/
  private Expression stringLiteral () throws IOException, ParseException
  {
    // If string literals appear together, concatenate them.  Ie:
    // "Twas " "brillig " "and " "the " "slithy " "toves"
    // becomes
    // "Twas brillig and the slithy toves"
    boolean isWide = token.isWide() ;
    String literal = "";
    do
    {
      literal += token.name;
      match (Token.StringLiteral);
    } while (token.equals (Token.StringLiteral));
    Expression stringExpr = exprFactory.terminal (literal, isWide );
    stringExpr.rep ('"' + literal + '"');
    return stringExpr;
  } // stringLiteral

  /**
   *
   **/
  private Expression positiveIntConst (SymtabEntry entry) throws IOException, ParseException
  {
    Expression e     = constExp (entry);
    Object     value = e.value ();
    while (value instanceof ConstEntry)
      value = ((ConstEntry)value).value ().value ();
    if (!(value instanceof Number) || value instanceof Float || value instanceof Double)
    {
      ParseException.notPositiveInt (scanner, e.rep ());
      //e = exprFactory.terminal ("1", new Long (1));
      e = exprFactory.terminal ("1", BigInteger.valueOf (1));
    }
    //else if (((Number)value).longValue () <= 0) {
    //   ParseException.notPositiveInt (scanner, value.toString ());
    //   e = exprFactory.terminal ("1", new Long (1)); }
    else if (((BigInteger)value).compareTo (BigInteger.valueOf (0)) <= 0)
    {
      ParseException.notPositiveInt (scanner, value.toString ());
      //e = exprFactory.terminal ("1", new Long (1)); <daz>
      e = exprFactory.terminal ("1", BigInteger.valueOf (1));
    }
    return e;
  } // positiveIntConst

  /**
   *
   **/
  private SymtabEntry typeDcl (SymtabEntry entry) throws IOException, ParseException
  {
    switch (token.type)
    {
      case Token.Typedef:
        match (Token.Typedef);
        return typeDeclarator (entry);
      case Token.Struct:
        return structType (entry);
      case Token.Union:
        return unionType (entry);
      case Token.Enum:
        return enumType (entry);
      default:
        throw ParseException.syntaxError (scanner, new int [] {
            Token.Typedef, Token.Struct, Token.Union, Token.Enum}, token.type);
    }
  } // typeDcl

  /**
   *
   **/
  private TypedefEntry typeDeclarator (SymtabEntry entry) throws IOException, ParseException
  {
    TypedefEntry typedefEntry = stFactory.typedefEntry (entry, (IDLID)repIDStack.peek ());
    typedefEntry.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede "typedef" keyword
    typedefEntry.comment (tokenHistory.lookBack (1).comment);
    typedefEntry.type (typeSpec (entry));
    Vector typedefList = new Vector ();
    declarators (typedefEntry, typedefList);
    for (Enumeration e = typedefList.elements(); e.hasMoreElements();)
      pigeonhole (entry, (SymtabEntry)e.nextElement ());
    return typedefEntry;
  } // typeDeclarator

  /**
   *
   **/
  private SymtabEntry typeSpec (SymtabEntry entry) throws IOException, ParseException
  {
    return ((token.type == Token.Struct) ||
            (token.type == Token.Union)  ||
            (token.type == Token.Enum))
        ? constrTypeSpec (entry)
        : simpleTypeSpec (entry, true);
  } // typeSpec

  /**
   *
   **/
  private SymtabEntry simpleTypeSpec (SymtabEntry entry,
    boolean mustBeReferencable ) throws IOException, ParseException
  {
    // <f46082.40>
    //if ((token.type == Token.Identifier)  ||
    //    (token.type == Token.DoubleColon) ||
    //    (token.type == Token.Object)) {
    if ((token.type == Token.Identifier)  ||
        (token.type == Token.DoubleColon) ||
        (token.type == Token.Object)      ||
        (token.type == Token.ValueBase))
    {
      SymtabEntry container = ((entry instanceof InterfaceEntry) ||
                               (entry instanceof ModuleEntry)    ||
                               (entry instanceof StructEntry)    ||
                               (entry instanceof UnionEntry))
          ? entry
          : entry.container ();
      return scopedName (container, stFactory.primitiveEntry (),
        mustBeReferencable);
    }
    return ((token.type == Token.Sequence) ||
            (token.type == Token.String)   ||
            (token.type == Token.Wstring))
        ? templateTypeSpec (entry)
        : baseTypeSpec (entry);
  } // simpleTypeSpec

  /**
   *
   **/
  private SymtabEntry baseTypeSpec (SymtabEntry entry) throws IOException, ParseException
  {
    switch (token.type)
    {
      case Token.Float:
      case Token.Double:
        return floatingPtType ();
      case Token.Long:
      case Token.Short:
     case Token.Unsigned:
        return integerType (entry);
      case Token.Char:
      case Token.Wchar:
        return charType ();
      case Token.Boolean:
        return booleanType ();
     case Token.Octet:
        return octetType ();
      case Token.Any:
        return anyType ();
      // NOTE: Object and ValueBase are <base_type_spec>s, but both
      // are processed at simpleTypeSpec(), not here.  parmTypeSpec()
      // directly checks for these types.  Could make baseTypeSpec() do
      // the same
      default:
        throw ParseException.syntaxError (scanner, new int [] {
            Token.Float,    Token.Double, Token.Long,  Token.Short,
            Token.Unsigned, Token.Char,   Token.Wchar, Token.Boolean,
            Token.Octet,    Token.Any}, token.type);
    }
  } // baseTypeSpec

  /**
   *
   **/
  private SymtabEntry templateTypeSpec (SymtabEntry entry) throws IOException, ParseException
  {
    switch (token.type)
    {
      case Token.Sequence:
        return sequenceType (entry);
      case Token.String:
      case Token.Wstring:
        return stringType (entry);
    }
    throw ParseException.syntaxError (scanner, new int [] {Token.Sequence, Token.String, Token.Wstring}, token.type);
  } // templateTypeSpec

  /**
   *
   **/
  private SymtabEntry constrTypeSpec (SymtabEntry entry) throws IOException, ParseException
  {
    switch (token.type)
    {
      case Token.Struct:
        return structType (entry);
      case Token.Union:
        return unionType (entry);
      case Token.Enum:
        return enumType (entry);
    }
    throw ParseException.syntaxError (scanner, new int [] {Token.Struct, Token.Union, Token.Enum}, token.type);
  } // constrTypeSpec

  /**
   *
   **/
  private void declarators (TypedefEntry entry, Vector list) throws IOException, ParseException
  {
    for (; ; match (Token.Comma))
    {
      TypedefEntry newEntry = (TypedefEntry)entry.clone ();
      declarator (newEntry);
      if (isntInList (list, newEntry.name ()))
        list.addElement (newEntry);
      if (token.type != Token.Comma)
        break;
    }
  } // declarators

  /**
   *
   **/
  private void declarator (TypedefEntry entry) throws IOException, ParseException
  {
    entry.name (token.name);
    // If the declarator is commented then override the comment cloned from the parent
    // entry. <08aug1997daz>
    if (!token.comment.text ().equals (""))
      entry.comment (token.comment);
    match (Token.Identifier);
    while (token.type == Token.LeftBracket)
      fixedArraySize (entry);
  } // declarator

  /**
   *
   **/
  private PrimitiveEntry floatingPtType () throws IOException, ParseException
  {
    String name = "double";
    if (token.type == Token.Float)
    {
      match (Token.Float);
      name = "float";
    }
    else if (token.type == Token.Double)
      match (Token.Double);
    else
    {
      int [] expected = {Token.Float, Token.Double};
      ParseException.syntaxError (scanner, new int [] {Token.Float, Token.Double }, token.type);
    }
    PrimitiveEntry ret = null;
    try
    {
      ret = (PrimitiveEntry)qualifiedEntry (name);
    }
    catch (ClassCastException exception)
    {
      ParseException.undeclaredType (scanner, name);
    }
    return ret;
  } // floatingPtType

  /**
   *
   **/
  private PrimitiveEntry integerType (SymtabEntry entry) throws IOException, ParseException
  {
    String name = "";
    if (token.type == Token.Unsigned)
    {
      match (Token.Unsigned);
      name = "unsigned ";
    }
    name += signedInt();
    PrimitiveEntry ret = null;
    try
    {
      ret = (PrimitiveEntry) qualifiedEntry (name);
    }
    catch (ClassCastException exception)
    {
      ParseException.undeclaredType (scanner, name);
    }
    return ret;
  } // integerType

  /**
   *
   **/
  private String signedInt () throws IOException, ParseException
  {
    String ret = "long";
    if (token.type == Token.Long)
    {
      match (Token.Long);
      // <signedInt'> ::= "long" | e
      if (token.type == Token.Long)
      {
        ret = "long long";
        match (Token.Long);
      }
    }
    else if (token.type == Token.Short)
    {
      ret = "short";
      match (Token.Short);
    }
    else
      ParseException.syntaxError (scanner, new int [] {Token.Long, Token.Short}, token.type);
    return ret;
  } // signedInt

  /**
   *
   **/
  private PrimitiveEntry charType () throws IOException, ParseException
  {
    String tokenName;
    if (token.type == Token.Char)
    {
      match (Token.Char);
      tokenName = "char";
    }
    else
    {
      match (Token.Wchar);
      tokenName = "wchar";
    }
    PrimitiveEntry ret = null;
    try
    {
      ret = (PrimitiveEntry) qualifiedEntry (tokenName);
    }
    catch (ClassCastException exception)
    {
      ParseException.undeclaredType (scanner, overrideName (tokenName));
    }
    return ret;
  } // charType

  /**
   *
   **/
  private PrimitiveEntry booleanType () throws IOException, ParseException
  {
    PrimitiveEntry ret = null;
    match (Token.Boolean);
    try
    {
      ret = (PrimitiveEntry) qualifiedEntry ("boolean");
    }
    catch (ClassCastException exception)
    {
      ParseException.undeclaredType (scanner, overrideName ("boolean"));
    }
    return ret;
  } // booleanType

  /**
   *
   **/
  private PrimitiveEntry octetType () throws IOException, ParseException
  {
    PrimitiveEntry ret = null;
    match (Token.Octet);
    try
    {
      ret = (PrimitiveEntry) qualifiedEntry ("octet");
    }
    catch (ClassCastException exception)
    {
      ParseException.undeclaredType (scanner, overrideName ("octet"));
    }
    return ret;
  } // octetType

  /**
   *
   **/
  private SymtabEntry anyType () throws IOException, ParseException
  {
    match (Token.Any);
    try
    {
      return qualifiedEntry ("any");
    }
    catch (ClassCastException exception)
    {
      ParseException.undeclaredType (scanner, overrideName ("any"));
      return null;
    }
  } // anyType

  /**
   *
   **/
  private StructEntry structType (SymtabEntry entry) throws IOException,
    ParseException
  {
    match (Token.Struct);
    String name = token.name;
    match (Token.Identifier);
    StructEntry structEntry = null ;

    if (token.type == Token.LeftBrace) {
      repIDStack.push(((IDLID)repIDStack.peek ()).clone ()) ;
      structEntry = makeStructEntry( name, entry, false ) ;
      ((IDLID)repIDStack.peek ()).appendToName (name);
      prep.openScope (structEntry);
      match (Token.LeftBrace) ;
      member (structEntry) ;
      memberList2 (structEntry) ;
      prep.closeScope (structEntry);
      match (Token.RightBrace) ;
      repIDStack.pop() ;
    } else if (token.equals( Token.Semicolon )) {
      structEntry = makeStructEntry( name, entry, true ) ;
    } else {
      throw ParseException.syntaxError (scanner,
        new int[] { Token.Semicolon, Token.LeftBrace }, token.type);
    }
    return structEntry;
  } // structType

  private StructEntry makeStructEntry( String name, SymtabEntry entry,
    boolean isForward )
  {
    StructEntry structEntry = stFactory.structEntry (entry,
      (IDLID)repIDStack.peek () );
    structEntry.isReferencable( !isForward ) ;
    structEntry.sourceFile (scanner.fileEntry ());
    structEntry.name (name);
    // Comment must immediately preceed "struct" keyword
    structEntry.comment (tokenHistory.lookBack (1).comment);
    pigeonhole( entry, structEntry ) ;
    return structEntry ;
  }

  /**
   *
   **/
  private void memberList2 (StructEntry entry) throws IOException
  {
    while (token.type != Token.RightBrace)
      member (entry);
  } // memberList2

  /**
   *
   **/
  private void member (StructEntry entry) throws IOException
  {
    TypedefEntry newEntry = stFactory.typedefEntry(entry, (IDLID)repIDStack.peek());
    newEntry.sourceFile (scanner.fileEntry ());
    // comment must immediately precede <type_spec> lexeme
    newEntry.comment (token.comment);
    try
    {
      newEntry.type (typeSpec (entry));
      if (newEntry.type () == entry)
        throw ParseException.recursive (scanner, entry.fullName (),
            (token.name == null) ? "" : token.name);
      // <d46094> Exception cannot appear within a struct, union, or exception
      if (typeOf (newEntry) instanceof ExceptionEntry)
        throw ParseException.illegalException (scanner, entryName (entry));
      declarators (newEntry, entry.members ());
      match (Token.Semicolon);
    }
    catch (ParseException exception)
    {
      skipToSemicolon ();
    }
  } // member

  /**
   *
   **/
  private final boolean isConstTypeSpec (Token t)
  {
    return (t.type == Token.Struct || t.type == Token.Union || t.type == Token.Enum);
  } // isConstTypeSpec

  /**
   *
   **/
  private UnionEntry unionType (SymtabEntry entry) throws IOException, ParseException
  {
    match (Token.Union) ;
    String name = token.name ;
    match (Token.Identifier) ;
    UnionEntry unionEntry = null ;

    if (token.type == Token.Switch) {
      repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
      unionEntry = makeUnionEntry( name, entry, false ) ;
      ((IDLID)repIDStack.peek ()).appendToName (name);
      match (Token.Switch);
      match (Token.LeftParen);
      unionEntry.type (switchTypeSpec (unionEntry));
      match (Token.RightParen);
      prep.openScope (unionEntry);
      match (Token.LeftBrace);
      switchBody (unionEntry);
      verifyUnion (unionEntry);
      prep.closeScope (unionEntry);
      match (Token.RightBrace);
      repIDStack.pop ();
    } else if (token.equals( Token.Semicolon )) {
      unionEntry = makeUnionEntry( name, entry, true ) ;
    } else {
      throw ParseException.syntaxError (scanner,
        new int[] { Token.Semicolon, Token.Switch }, token.type);
    }

    return unionEntry ;
  } // unionType

  private UnionEntry makeUnionEntry( String name, SymtabEntry entry,
    boolean isForward )
  {
    UnionEntry unionEntry = stFactory.unionEntry (entry,
      (IDLID)repIDStack.peek () );
    unionEntry.isReferencable( !isForward ) ;
    unionEntry.sourceFile (scanner.fileEntry ());
    unionEntry.name (name);
    // Comment must immediately preceed "union" keyword
    unionEntry.comment (tokenHistory.lookBack (1).comment);
    pigeonhole( entry, unionEntry ) ;
    return unionEntry ;
  }

  /**
   *
   **/
  private void verifyUnion (UnionEntry u)
  {
    if (u.typeName ().equals (overrideName ("boolean")))
    {
      if (caseCount (u) > 2)
        ParseException.noDefault (scanner);
    }
    else if (u.type () instanceof EnumEntry)
    {
      if (caseCount (u) > ((EnumEntry)u.type ()).elements ().size ())
        ParseException.noDefault (scanner);
    }
  } // verifyUnion

  /**
   *
   **/
  private long caseCount (UnionEntry u)
  {
    long cases = 0;
    Enumeration branches = u.branches ().elements ();
    while (branches.hasMoreElements ())
    {
      UnionBranch branch = (UnionBranch)branches.nextElement ();
      cases += branch.labels.size ();
      if (branch.isDefault)
        ++cases;
    }
    return cases;
  } // caseCount

  /**
   *
   **/
  private SymtabEntry switchTypeSpec (UnionEntry entry) throws IOException, ParseException
  {
    SymtabEntry ret = null;
    switch (token.type)
    {
       case Token.Long:
       case Token.Short:
       case Token.Unsigned:
         return integerType (entry);
       case Token.Char:
       case Token.Wchar:
         return charType();
       case Token.Boolean:
         return booleanType();
       case Token.Enum:
         return enumType (entry);
       case Token.Identifier:
       case Token.DoubleColon:
         ret = scopedName (entry, stFactory.primitiveEntry ());
         if (hasArrayInfo (entry.type ()))
           ParseException.illegalArray (scanner, "switch");
         SymtabEntry retType = typeOf (ret);
         if (!(retType instanceof EnumEntry || retType instanceof PrimitiveEntry))
           ParseException.wrongType (scanner, ret.fullName (),
               "long, unsigned long, short, unsigned short, char, boolean, enum",
               entryName (ret.type ()));
         else if (ret instanceof PrimitiveEntry)
         {
           SymtabEntry octet = qualifiedEntry ("octet");
           SymtabEntry flt   = qualifiedEntry ("float");
           SymtabEntry dbl   = qualifiedEntry ("double");
           if (retType == octet || retType == flt || retType == dbl)
             ParseException.wrongType (scanner, ret.fullName(),
                 "long, unsigned long, short, unsigned short, char, boolean, enum",
                 entryName(ret.type ()));
         }
         break;
       default:
         throw ParseException.syntaxError (scanner, new int [] {
             Token.Long,    Token.Short, Token.Unsigned, Token.Char,
             Token.Boolean, Token.Enum,  Token.Identifier,
             Token.DoubleColon }, token.type);
    }
    return ret;
  } // switchTypeSpec

  // This is only used by the union methods
  UnionBranch defaultBranch = null;

  /**
   *
   **/
  private void switchBody (UnionEntry entry) throws IOException, ParseException
  {
    caseProd (entry);
    while (!token.equals (Token.RightBrace))
      caseProd (entry);
    entry.defaultBranch ((defaultBranch == null) ? null : defaultBranch.typedef);
    defaultBranch = null;
  } // switchBody

  /**
   *
   **/
  private void caseProd (UnionEntry entry) throws IOException, ParseException
  {
    UnionBranch branch = new UnionBranch ();
    entry.addBranch (branch);
    caseLabel (entry, branch);
    while (token.equals (Token.Case) || token.equals (Token.Default))
      caseLabel (entry, branch);
    elementSpec (entry, branch);
    match (Token.Semicolon);
  } // caseProd

  /**
   *
   **/
  private void caseLabel (UnionEntry entry, UnionBranch branch) throws IOException, ParseException
  {
    if (token.type == Token.Case)
    {
      match (Token.Case);
      ConstEntry tmpEntry = stFactory.constEntry (entry, (IDLID)repIDStack.peek ());
      tmpEntry.sourceFile (scanner.fileEntry ());
      tmpEntry.type (entry);

      Expression  label;
      SymtabEntry type = typeOf (entry.type ());
      if (type instanceof EnumEntry)
        label = matchEnum ((EnumEntry)type);
      else
      {
        label = constExp (tmpEntry);
        verifyConstType (label, type);
      }
      if (entry.has (label))
        ParseException.branchLabel (scanner, label.rep ());
      branch.labels.addElement (label);
      match (Token.Colon);
    }
    else if (token.type == Token.Default)
    {
      match (Token.Default);
      match (Token.Colon);
      if (entry.defaultBranch () != null)
        ParseException.alreadyDefaulted (scanner);
      branch.isDefault = true;
      defaultBranch    = branch;
    }
    else
      throw ParseException.syntaxError (scanner, new int [] { Token.Case, Token.Default }, token.type);
  } // caselabel

  /**
   *
   **/
  private Expression matchEnum (EnumEntry entry) throws IOException, ParseException
  {
    // Get the symbol table entry for the case label based on the
    // scope of the EnumEntry, NOT the UnionEntry (the union could be
    // in a different scope than the enum).  Given
    // module M { enum E {A, B, C, D}; };
    // a case label for A could be one of the following:
    // case A:
    // case M::A:
    // case ::M::A:
    SymtabEntry label = scopedName (entry.container(), new SymtabEntry ());
    return exprFactory.terminal (label.name (), false);
  } // matchEnum

  /**
   *
   **/
  private void elementSpec (UnionEntry entry, UnionBranch branch) throws IOException, ParseException
  {
    TypedefEntry typedef = stFactory.typedefEntry (entry, (IDLID)repIDStack.peek ());
    typedef.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede <type_spec> lexeme
    typedef.comment (token.comment);
    typedef.type (typeSpec (entry));
    if (typedef.type () == entry)
      throw ParseException.recursive (scanner, entry.fullName (), (token.name == null)? "" : token.name);
    // <d46094> Exception cannot appear within a struct, union, or exception
    if (typeOf (typedef) instanceof ExceptionEntry)
      throw ParseException.illegalException (scanner, entryName (entry));
    declarator (typedef);
    branch.typedef = typedef;
    // Ensure a branch with the same name doesn't already exist.
    if (entry.has (typedef))
      ParseException.branchName (scanner, typedef.name ());
  } // elementSpec

  /**
   *
   **/
  private EnumEntry enumType (SymtabEntry entry) throws IOException, ParseException
  {
    match (Token.Enum);
    EnumEntry enumEntry = newEnumEntry (entry);
    // comment must immediately precede "enum" keyword
    enumEntry.comment (tokenHistory.lookBack (1).comment);
    enumEntry.name (token.name);
    match (Token.Identifier);
    prep.openScope (enumEntry);
    match (Token.LeftBrace);
    if (isntInStringList (enumEntry.elements (), token.name))
    {
      enumEntry.addElement (token.name);
      SymtabEntry element = new SymtabEntry (entry, (IDLID)repIDStack.peek ());
      // if block taken from EnumEntry ctor
      if (element.module ().equals (""))
        element.module (element.name ());
      else if (!element.name ().equals (""))
        element.module (element.module () + "/" + element.name ());
      element.name (token.name);
      // <d50237> Place the SymtabEntry representing this enumeration
      // contant into the SymtabEntry defining its scope (e.g., InterfaceEntry,
      // ValueEntry, etc.) rather than the SymtabEntry passed in, which
      // may not define the contant's scope (e.g., TypedefEntry).
      //pigeonhole (entry, element); } <daz>
      pigeonhole (enumEntry.container (), element);
    }
    match (Token.Identifier);
    enumType2 (enumEntry);
    prep.closeScope (enumEntry);
    match (Token.RightBrace);
    return enumEntry;
  } // enumType

  /**
   *
   **/
  private void enumType2 (EnumEntry entry) throws IOException, ParseException
  {
    while (token.type == Token.Comma)
    {
      match (Token.Comma);
      String name = token.name;
      match (Token.Identifier);
      if (isntInStringList (entry.elements (), name))
      {
        entry.addElement (name);
        SymtabEntry element = new SymtabEntry (entry.container (), (IDLID)repIDStack.peek ());
        // if block taken from EnumEntry ctor:
        if (element.module ().equals (""))
          element.module (element.name ());
        else if (!element.name().equals (""))
          element.module (element.module () + "/" + element.name ());
        element.name (name);
        pigeonhole (entry.container  (), element);
      }
    }
  } // enumType2

  /**
   *
   **/
  private SequenceEntry sequenceType (SymtabEntry entry) throws IOException, ParseException
  {
    match (Token.Sequence);
    match (Token.LessThan);

    SequenceEntry newEntry = newSequenceEntry (entry);
    SymtabEntry tsentry = simpleTypeSpec (newEntry, false );
    newEntry.type (tsentry);
    if (!tsentry.isReferencable()) {
        // This is a sequence type that is referencing an
        // incomplete forward declaration of a struct or
        // union.  Save the sequence in a list for later
        // backpatching.
        try {
            List fwdTypes = (List)tsentry.dynamicVariable( ftlKey ) ;
            if (fwdTypes == null) {
                fwdTypes = new ArrayList() ;
                tsentry.dynamicVariable( ftlKey, fwdTypes ) ;
            }
            fwdTypes.add( newEntry ) ;
        } catch (NoSuchFieldException exc) {
            throw new IllegalStateException() ;
        }
    }

    if (token.type == Token.Comma)
    {
      match (Token.Comma);
      ConstEntry tmpEntry = stFactory.constEntry (newEntry, (IDLID)repIDStack.peek ());
      tmpEntry.sourceFile (scanner.fileEntry ());
      tmpEntry.type (qualifiedEntry ("long"));
      newEntry.maxSize (positiveIntConst (tmpEntry));
      verifyConstType (newEntry.maxSize(), qualifiedEntry ("long"));
    }
    match (Token.GreaterThan);
    return newEntry;
  } // sequenceType

  /**
   *
   **/
  private StringEntry stringType (SymtabEntry entry) throws IOException, ParseException
  {
    StringEntry string = stFactory.stringEntry ();
    if (token.type == Token.String)
    {
      string.name (overrideName ("string"));
      match (Token.String);
    }
    else
    {
      string.name (overrideName ("wstring"));
      match (Token.Wstring);
    }
    string.maxSize (stringType2 (entry));
    return string;
  } // stringType

  /**
   *
   **/
  private Expression stringType2 (SymtabEntry entry) throws IOException, ParseException
  {
    if (token.type == Token.LessThan)
    {
      match (Token.LessThan);

      // START IBM.11417 failure in the IDL compiler
      //Expression maxSize = positiveIntConst (entry);   IBM.11417

      ConstEntry tmpEntry = stFactory.constEntry (entry, (IDLID)repIDStack.peek
());
      tmpEntry.sourceFile (scanner.fileEntry ());
      tmpEntry.type (qualifiedEntry ("long"));
      Expression maxSize = positiveIntConst (tmpEntry);

      // END IBM.11417

      verifyConstType (maxSize, qualifiedEntry ("long"));
      match (Token.GreaterThan);
      return maxSize;
    }
    return null;
  } // stringType2

  /**
   *
   **/
  private void fixedArraySize (TypedefEntry entry) throws IOException, ParseException
  {
    match (Token.LeftBracket);
    ConstEntry tmpEntry = stFactory.constEntry (entry, (IDLID)repIDStack.peek ());
    tmpEntry.sourceFile (scanner.fileEntry ());
    // <d58058> Set type of tmpExpr to "long", which is the array index type.
    // Previously, this type was erroneously set to the array element type.
    //tmpEntry.type (entry.type ());
    tmpEntry.type (qualifiedEntry ("long"));
    Expression expr = positiveIntConst (tmpEntry);
    entry.addArrayInfo (expr);
    verifyConstType (expr, qualifiedEntry ("long"));
    match (Token.RightBracket);
  } // fixedArraySize

  /**
   *
   **/
  private void attrDcl (InterfaceEntry entry) throws IOException, ParseException
  {
    AttributeEntry attribute = stFactory.attributeEntry (entry, (IDLID)repIDStack.peek ());
    attribute.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede the "attribute" keyword.  Save the
    // comment preceding the declaration for use below.
    attribute.comment (token.comment);
    Comment dclComment = attribute.comment ();

    if (token.type == Token.Readonly)
    {
      match (Token.Readonly);
      attribute.readOnly (true);
    }
    match (Token.Attribute);
    attribute.type (paramTypeSpec (attribute));
    attribute.name (token.name);
    // Override declaration comment if attribute identifier is commented
    if (!token.comment.text ().equals (""))
      attribute.comment (token.comment);
    entry.methodsAddElement (attribute, scanner);
    pigeonholeMethod (entry, attribute);
    // Declaration comment was overriden:
    if (!token.comment.text ().equals (""))
    {
      // Create a temporary attribute with declaration comment so cloning in
      // attrdcl2() can use declaration comment as default.
      AttributeEntry attributeClone = (AttributeEntry) attribute.clone ();
      attributeClone.comment (dclComment);

      match (Token.Identifier);
      attrDcl2 (entry, attributeClone);
    }
    else
    {
      match (Token.Identifier);
      attrDcl2 (entry, attribute);
    }
    //match (Token.Identifier);
    //attrDcl2 (entry, attribute);
  } // attrDcl

  /**
   *
   **/
  private void attrDcl2 (InterfaceEntry entry, AttributeEntry clone)
          throws IOException, ParseException
  {
    while (token.type == Token.Comma)
    {
      match (Token.Comma);
      AttributeEntry attribute = (AttributeEntry)clone.clone ();
      attribute.name (token.name);
      // Override the declaration comment (i.e., that preceding the
      // "attribute" keyword) if the attribute identifier is commented.
      if (!token.comment.text ().equals (""))
        attribute.comment (token.comment);
      entry.methodsAddElement (attribute, scanner);
      pigeonholeMethod (entry, attribute);
      match (Token.Identifier);
    }
  } // attrDcl2

  /**
   *
   **/
  private void exceptDcl (SymtabEntry entry) throws IOException, ParseException
  {
    match (Token.Exception);
    repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
    ExceptionEntry exceptEntry = stFactory.exceptionEntry (entry, (IDLID)repIDStack.peek ());
    ((IDLID)repIDStack.peek ()).appendToName (token.name);
    exceptEntry.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede "exception" keyword
    exceptEntry.comment (tokenHistory.lookBack (1).comment);
    exceptEntry.name (token.name);
    match (Token.Identifier);
    pigeonhole (entry, exceptEntry);
    if (token.equals (Token.LeftBrace))
    {
      prep.openScope (exceptEntry);
      match (Token.LeftBrace);
      memberList2 (exceptEntry);
      prep.closeScope (exceptEntry);
      match (Token.RightBrace);
      repIDStack.pop ();
    }
    else
      throw ParseException.syntaxError (scanner, Token.LeftBrace,token.type);
  } // exceptDcl

  /**
   *
   **/
  private void opDcl (InterfaceEntry entry) throws IOException, ParseException
  {
    MethodEntry method = stFactory.methodEntry (entry, (IDLID)repIDStack.peek ());
    method.sourceFile (scanner.fileEntry ());
    // Comment must immediately precede "oneway" keyword or <return_type_spec>
    method.comment (token.comment);
    if (token.type == Token.Oneway)
    {
      match (Token.Oneway);
      method.oneway (true);
    }
    method.type (opTypeSpec (method));
    repIDStack.push (((IDLID)repIDStack.peek ()).clone ());
    ((IDLID)repIDStack.peek ()).appendToName (token.name);
    method.name (token.name);
    entry.methodsAddElement (method, scanner);
    pigeonholeMethod (entry, method);
    opDcl2 (method);
    if (method.oneway ())
      checkIfOpLegalForOneway (method);
    repIDStack.pop ();
  } // opDcl

  /**
   *
   **/
  private void checkIfOpLegalForOneway (MethodEntry method)
  {
    boolean notLegal = false;
    if ((method.type() != null) ||
         (method.exceptions().size() != 0)) notLegal = true;
    else
    {
      for (Enumeration e = method.parameters().elements(); e.hasMoreElements();)
      {
        if (((ParameterEntry)e.nextElement ()).passType () != ParameterEntry.In)
        {
          notLegal = true;
          break;
        }
      }
    }
    if (notLegal)
      ParseException.oneway (scanner, method.name ());
  } // checkifOpLegalForOneway

  /**
   *
   **/
  private void opDcl2 (MethodEntry method) throws IOException, ParseException
  {
    if (token.equals (Token.MacroIdentifier))
    {
      match (Token.MacroIdentifier);
      parameterDcls2 (method);
    }
    else
    {
      match (Token.Identifier);
      parameterDcls (method);
     }
    opDcl3 (method);
  } // opDcl2

  /**
   *
   **/
  private void opDcl3 (MethodEntry entry) throws IOException, ParseException
  {
    if (token.type != Token.Semicolon)
    {
      if (!token.equals (Token.Raises) && !token.equals (Token.Context))
        throw ParseException.syntaxError (scanner, new int [] {
            Token.Raises, Token.Context, Token.Semicolon }, token.type);
      if (token.type == Token.Raises)
        raisesExpr (entry);
      if (token.type == Token.Context)
        contextExpr (entry);
    }
  } // opDcl3

  /**
   *
   **/
  private SymtabEntry opTypeSpec (SymtabEntry entry) throws IOException, ParseException
  {
    SymtabEntry ret = null;
    if (token.type == Token.Void)
      match (Token.Void);
    else
      ret = paramTypeSpec (entry);
    return ret;
  } // opTypeSpec

  /**
   *
   **/
  private void parameterDcls (MethodEntry entry) throws IOException, ParseException
  {
    match (Token.LeftParen);
    parameterDcls2 (entry);
  } // parameterDcls

  /**
   *
   **/
  private void parameterDcls2 (MethodEntry entry) throws IOException, ParseException
  {
    if (token.type == Token.RightParen)
      match (Token.RightParen);
    else
    {
      paramDcl (entry);
      while (token.type == Token.Comma)
      {
        match (Token.Comma);
        paramDcl (entry);
      }
      match (Token.RightParen);
    }
  } // paraneterDcls2

  /**
   *
   **/
  private void paramDcl (MethodEntry entry) throws IOException, ParseException
  {
    ParameterEntry parmEntry = stFactory.parameterEntry (entry, (IDLID)repIDStack.peek ());
    parmEntry.sourceFile (scanner.fileEntry ());
    // Comment must immeiately precede the parameter attribute
    parmEntry.comment (token.comment);
    paramAttribute (parmEntry);
    parmEntry.type (paramTypeSpec (entry));
    parmEntry.name (token.name);
    match (Token.Identifier);
    if (isntInList (entry.parameters (), parmEntry.name ()))
      entry.addParameter (parmEntry);
  } // paramDcl

  /**
   *
   **/
  private void paramAttribute (ParameterEntry entry) throws IOException, ParseException
  {
    if (token.type == Token.In)
    {
      entry.passType (ParameterEntry.In);
      match (Token.In);
    }
    else if (token.type == Token.Out)
    {
      entry.passType (ParameterEntry.Out);
      match (Token.Out);
    }
    else if (token.type == Token.Inout)
    {
      entry.passType (ParameterEntry.Inout);
      match (Token.Inout);
    }
    else
      throw ParseException.syntaxError (scanner, new int [] {
          Token.In, Token.Out, Token.Inout }, token.type);
  } // paramAttribute

  /**
   *
   **/
  private void raisesExpr (MethodEntry entry) throws IOException, ParseException
  {
    match (Token.Raises);
    match (Token.LeftParen);
    // Comment must immediately precede <scoped_name> for exception
    Comment tempComment = token.comment;
    SymtabEntry exception = scopedName(entry.container (), stFactory.exceptionEntry ());
    if (typeOf (exception) instanceof ExceptionEntry)
    {
      // Comment must immediately precede <scoped_name> for exception
      exception.comment (tempComment);
      if (isntInList (entry.exceptions (), exception))
        entry.exceptionsAddElement ((ExceptionEntry) exception);
    }
    else
      ParseException.wrongType (scanner, exception.fullName(),
          "exception", entryName (exception.type ()));
    raisesExpr2 (entry);
    match (Token.RightParen);
  } // raisesExpr

  /**
   *
   **/
  private void raisesExpr2 (MethodEntry entry) throws IOException, ParseException
  {
    while (token.type == Token.Comma)
    {
      match (Token.Comma);
      // Comment must immediately precede <scoped_name> of exception
      Comment tempComment = token.comment;
      SymtabEntry exception = scopedName (entry.container (), stFactory.exceptionEntry ());
      if (typeOf (exception) instanceof ExceptionEntry)
      {
        // Comment must immediately precede <scoped_name> of exception
        exception.comment (tempComment);
        if (isntInList (entry.exceptions (), exception))
          entry.addException ((ExceptionEntry)exception);
      }
      else
        ParseException.wrongType (scanner, exception.fullName (),
            "exception", entryName (exception.type ()));
    }
  } // raisesExpr2

  /**
   *
   **/
  private void contextExpr (MethodEntry entry) throws IOException, ParseException
  {
    match (Token.Context);
    match (Token.LeftParen);
    String stringLit = (String)stringLiteral ().value ();
    if (isntInStringList (entry.contexts (), stringLit))
      entry.addContext (stringLit);
    contextExpr2 (entry);
    match (Token.RightParen);
  } // contextExpr


  private void contextExpr2 (MethodEntry entry) throws IOException, ParseException
  {
    while (token.type == Token.Comma)
    {
      match (Token.Comma);
      String stringLit = (String)stringLiteral ().value ();
      if (isntInStringList (entry.contexts (), stringLit))
        entry.addContext (stringLit);
    }
  } // contextExpr2

  /**
   *
   **/
  private SymtabEntry paramTypeSpec (SymtabEntry entry) throws IOException, ParseException
  {
    SymtabEntry ret = null;
    switch (token.type)
    {
      case Token.Float:
      case Token.Double:
      case Token.Long:
      case Token.Short:
      case Token.Unsigned:
      case Token.Char:
      case Token.Wchar:
      case Token.Boolean:
      case Token.Octet:
      case Token.Any:
        return baseTypeSpec (entry);
      case Token.String:
      case Token.Wstring:
        return stringType (entry);
      case Token.Identifier:
      case Token.Object:
      // <f46082.40>
      case Token.ValueBase:
      case Token.DoubleColon:
        ret = scopedName (entry.container (), stFactory.primitiveEntry ());
        if (typeOf (ret) instanceof AttributeEntry)
          //ParseException.attributeParamType (scanner);
          ParseException.attributeNotType (scanner, ret.name ());
        else // <d60942>
          if (typeOf (ret) instanceof MethodEntry)
            ParseException.operationNotType (scanner, ret.name ());

        //if (!(returnType instanceof PrimitiveEntry ||
        //     returnType instanceof StringEntry))
        //ParseException.wrongType (scanner, ret.fullName(),
        //                          "primitive or string", entryName (ret.type()));
        break;
      default:
        throw ParseException.syntaxError (scanner, new int [] {
            Token.Float,      Token.Double,      Token.Long,    Token.Short,
            Token.Unsigned,   Token.Char,        Token.Wchar,   Token.Boolean,
            Token.Octet,      Token.Any,         Token.String,  Token.Wstring,
            Token.Identifier, Token.DoubleColon, Token.ValueBase }, token.type);
    }
    return ret;
  } // paramTypeSpec

  /**
   *
   **/
  private void match (int type) throws IOException, ParseException
  {
    ParseException exception = null;
    if (!token.equals (type))
    {
      exception = ParseException.syntaxError (scanner, type, token.type);
      // Missing a semicolon is a common error.  If a semicolon was expected,
      // assume it exists and keep the current token (don't get the next one).
      // BEWARE!!! THIS HAS THE POTENTIAL FOR AN INFINITE LOOP!
      if (type == Token.Semicolon)
        return;
    }
    // <f46082.40> Unecessary due to new valueElement() algorithm.
    //if (!tokenStack.empty())
    //{
    //  token = (Token)tokenStack.pop ();
    //  return;
    //}

    // Fetch the next token.
    token = scanner.getToken ();

    // <d62023> Issue warnings about tokens.
    issueTokenWarnings ();

    // Maintain history of most recent tokens.
    tokenHistory.insert (token);

    // <d59166> Identifiers that collide with keywords are illegal.  Note
    // that escaped identifers never collide!
    /*
    if (token.collidesWithKeyword ())
    {
      // <f60858.1> Issue a warning only
      if (corbaLevel <= 2.2f)
        ParseException.keywordCollisionWarning (scanner, token.name);
      else
        exception = ParseException.keywordCollision (scanner, token.name);
    }
    */

    while (token.isDirective ())
      token = prep.process (token);

    // If the token is a defined thingy, scan the defined string
    // instead of the input stream for a while.
    if (token.equals (Token.Identifier) || token.equals (Token.MacroIdentifier))
    {
      String string = (String)symbols.get (token.name);
      if (string != null && !string.equals (""))
      {
        // If this is a macro, parse the macro
        if (macros.contains (token.name))
        {
          scanner.scanString (prep.expandMacro (string, token));
          match (token.type);
        }
        else // This is just a normal define.
        {
          scanner.scanString (string);
          match (token.type);
        }
      }
    }
    if (exception != null)
      throw exception;
  } // match

  // <d62023>
  /**
   * Issue warnings according to attributes of current Token.
   **/
  private void issueTokenWarnings ()
  {
    if (noWarn)
      return;

    if ((token.equals (Token.Identifier) || token.equals (Token.MacroIdentifier))
        && !token.isEscaped ())
    {
      // Identifier collision with keyword in another release.
      // Identifier collision with keyword in letter, but not in case.
      if (token.collidesWithKeyword ())
        ParseException.warning (scanner, Util.getMessage ("Migration.keywordCollision", token.name));
    }
    // Deprecated keyword.
    if (token.isKeyword () && token.isDeprecated ())
      ParseException.warning (scanner, Util.getMessage ("Deprecated.keyword", token.toString ()));
  } // issueTokenWarnings

  /**
   *
   **/
  private ModuleEntry newModule (ModuleEntry oldEntry)
  {
    ModuleEntry entry = stFactory.moduleEntry (oldEntry, (IDLID)repIDStack.peek ());
    entry.sourceFile (scanner.fileEntry ());
    entry.name (token.name);
    // If this named module already exists, just reopen it.
    /* <46082.46.01> if cppModule, always create new module entry */
    SymtabEntry prevEntry = (SymtabEntry) symbolTable.get (entry.fullName ());
    if (!cppModule && prevEntry != null && prevEntry instanceof ModuleEntry)
    {
      // A module has been reopened, return that ModuleEntry.
      entry = (ModuleEntry) prevEntry;
      if (oldEntry == topLevelModule)
      {
        // Do a little checking:
        if (!entry.emit ())
          // The entry module is being reopened to put new stuff into it.
          // The module itself is not marked as "emit", but the new stuff
          // may be, so put the module on the emitList (add it to topLevelModule).
          addToContainer (oldEntry, entry);
        else if (!oldEntry.contained().contains (entry))
          // <d50767> The entry module being reopened is to be emitted, but
          // will not be placed on the emitList! I.E., it was not added to
          // topLevelModule.  Occurs when a generator manually inserts
          // ModuleEntrys into the symbol table (e.g., org; see preParse()
          // in ...idl.toJava.Compile). <daz>
          addToContainer (oldEntry, entry);
      }
    }
    else
      pigeonhole (oldEntry, entry);
    return entry;
  } // newModule

  /**
   *
   **/
  private EnumEntry newEnumEntry (SymtabEntry oldEntry)
  {
    EnumEntry entry = stFactory.enumEntry (oldEntry, (IDLID)repIDStack.peek ());
    entry.sourceFile (scanner.fileEntry ());
    entry.name (token.name);
    pigeonhole (oldEntry, entry);
    return entry;
  } // newEnumEntry

  /**
   *
   **/
  private SequenceEntry newSequenceEntry (SymtabEntry oldEntry)
  {
    SequenceEntry entry = stFactory.sequenceEntry (oldEntry, (IDLID)repIDStack.peek ());
     entry.sourceFile (scanner.fileEntry ());
     entry.name ("");
     pigeonhole (oldEntry, entry);
     return entry;
  } // newSequenceEntry

    private void updateSymbolTable( String fullName, SymtabEntry entry, boolean lcCheck )
    {
        // Check for case-insensitive collision (IDL error).
        String lcFullName = fullName.toLowerCase();
        if (lcCheck)
            if (lcSymbolTable.get (lcFullName) != null) {
                ParseException.alreadyDeclared (scanner, fullName);
            }
        symbolTable.put (fullName, entry);
        lcSymbolTable.put (lcFullName, entry);
        // <d59809> Allow fully-qualified CORBA types to be resolved by mapping
        // short name (e.g., CORBA/StringValue) to long name, actual name.
        String omgPrefix = "org/omg/CORBA" ;
        if (fullName.startsWith (omgPrefix)) {
            overrideNames.put (
                "CORBA" + fullName.substring (omgPrefix.length()), fullName);
        }
    }

    private void pigeonhole (SymtabEntry container, SymtabEntry entry)
    {
        if (entry.name().equals (""))
            entry.name (unknownNamePrefix + ++sequence);

        // If this object is not in the overrides list, then it is
        // ok to put it in the table (if it IS in the overrides list,
        // it is already in the table under a different name).
        String fullName = entry.fullName();
        if (overrideNames.get (fullName) == null) {
            addToContainer (container, entry);

            // It is an error is this name already exists in the symbol
            // table, unless this is a redefinition of a forward decl.
            // Re-opening a module is also legal, but not handled here.
            SymtabEntry oldEntry = (SymtabEntry) symbolTable.get (fullName);

            if (oldEntry == null) {
                updateSymbolTable( fullName, entry, true ) ;
            } else if (oldEntry instanceof ForwardEntry &&
                entry instanceof InterfaceEntry) {

                String repIDPrefix = ((IDLID)entry.repositoryID ()).prefix ();
                String oldRepIDPrefix = ((IDLID)oldEntry.repositoryID ()).prefix ();
                if (repIDPrefix.equals (oldRepIDPrefix)) {
                    updateSymbolTable( fullName, entry, false ) ;
                } else {
                    ParseException.badRepIDPrefix (scanner, fullName,
                        oldRepIDPrefix, repIDPrefix);
                }
            } else if (entry instanceof ForwardEntry &&
                       (oldEntry instanceof InterfaceEntry ||
                        oldEntry instanceof ForwardEntry)) {
                if (oldEntry instanceof ForwardEntry &&
                    entry.repositoryID () instanceof IDLID &&
                    oldEntry.repositoryID () instanceof IDLID) {

                    String repIDPrefix =
                        ((IDLID)entry.repositoryID ()).prefix ();
                    String oldRepIDPrefix =
                        ((IDLID)oldEntry.repositoryID ()).prefix ();

                    if (!(repIDPrefix.equals (oldRepIDPrefix))) {
                        // Disallow multiple ForwardEntry's having same Repository
                        // ID prefixes (CORBA 2.3).
                        ParseException.badRepIDPrefix (scanner, fullName,
                            oldRepIDPrefix, repIDPrefix);
                    }
                }
            } else if (cppModule && entry instanceof ModuleEntry &&
                oldEntry instanceof ModuleEntry) {
                // Allow multiple ModuleEntrys when user submits
                // the -cppModule flag.
            } else if (fullName.startsWith ("org/omg/CORBA") ||
                fullName.startsWith ("CORBA")) {
                // Ignore CORBA PIDL types entered at preParse() by generator.
            } else if (isForwardable( oldEntry, entry )) {
                // Both oldEntry and entry are structs or unions.
                // Legality depends on isReferencable on the two entries:
                // oldEntry     Entry
                //      T           T       ERROR alreadyDeclared
                //      T           F       legal fwd decl
                //      F           T       if defined in same file legal,
                //                          otherwise ERROR
                //      F           F       legal fwd decl
                if (oldEntry.isReferencable() && entry.isReferencable())
                    ParseException.alreadyDeclared (scanner, fullName);

                if (entry.isReferencable()) {
                    String firstFile =
                        oldEntry.sourceFile().absFilename() ;
                    String defFile =
                        entry.sourceFile().absFilename() ;
                    if (!firstFile.equals( defFile ))
                        ParseException.declNotInSameFile( scanner,
                            fullName, firstFile ) ;
                    else {
                        updateSymbolTable( fullName, entry, false ) ;

                        List oldRefList ;

                        try {
                            oldRefList = (List)oldEntry.dynamicVariable(
                                ftlKey ) ;
                        } catch (NoSuchFieldException exc) {
                            throw new IllegalStateException() ;
                        }

                        if (oldRefList != null) {
                            // Update entries in backpatch list
                            Iterator iter = oldRefList.iterator() ;
                            while (iter.hasNext()) {
                                SymtabEntry elem = (SymtabEntry)iter.next() ;
                                elem.type( entry ) ;
                            }
                        }
                    }
                }
            } else {
                ParseException.alreadyDeclared (scanner, fullName);
            }
        }
    } // pigeonhole

    private boolean isForwardable( SymtabEntry oldEntry,
        SymtabEntry entry )
    {
        return ((oldEntry instanceof StructEntry) &&
            (entry instanceof StructEntry)) ||
           ((oldEntry instanceof UnionEntry) &&
            (entry instanceof UnionEntry)) ;
    }

  // pigeonhole checks to see if this entry is already in the symbol
  // table and generates an error if it is.  Methods must be checked
  // not only against the symbol table but also against their
  // interface's parent's methods.  This is done in InterfaceEntry.
  // verifyMethod, so no checking need be done here.

  /**
   *
   **/
  private void pigeonholeMethod (InterfaceEntry container, MethodEntry entry)
  {
    if (entry.name ().equals (""))
       entry.name (unknownNamePrefix + ++sequence);

    // If this object is not in the overrides list, then it is
    // ok to put it in the table (if it IS in the overrides list,
    // it is already in the table under a different name).
    String fullName = entry.fullName ();
    if (overrideNames.get (fullName) == null)
    {
      addToContainer (container, entry);
      String lcFullName = fullName.toLowerCase ();
      symbolTable.put (fullName, entry);
      lcSymbolTable.put (lcFullName, entry);
      // <d59809> Allow fully-qualified CORBA types to be resolved by mapping
      // short name (e.g., CORBA/StringValue) to long name, actual name.
      if (fullName.startsWith ("org/omg/CORBA"))
        overrideNames.put ("CORBA" + fullName.substring (13), fullName);
    }
  } // pigeonholeMethod

  /**
   *
   **/
  private void addToContainer (SymtabEntry container, SymtabEntry contained)
  {
    if (container instanceof ModuleEntry)
      ((ModuleEntry)container).addContained (contained);
    else if (container instanceof InterfaceEntry)
      ((InterfaceEntry)container).addContained (contained);
    else if (container instanceof StructEntry)
      ((StructEntry)container).addContained (contained);
    else if (container instanceof UnionEntry)
      ((UnionEntry)container).addContained (contained);
    else if (container instanceof SequenceEntry)
      ((SequenceEntry)container).addContained (contained);
  } // addToContainer

  // NOTE: qualifiedEntry/partlyQualifiedEntry/unqualifiedEntry and
  // their court could probably use some performance improvements,
  // but I'm scared to touch anything.  It's the most complex bit of
  // code in this parser.

  // The qualified named type is searched for in the following order:
  // 1.  OverrideNames
  // 2.  Global scope
  // 3.  Inheritance scope (if container is an interface)
  // A qualified name is one which begins with :: or is assumed to be
  // in the global scope (like long, short, etc).

  /**
   *
   **/
  SymtabEntry qualifiedEntry (String typeName)
  {
    SymtabEntry type = recursiveQualifiedEntry (typeName);
    if (type == null)
      // Then it's not anywhere, report the error.
      ParseException.undeclaredType (scanner, typeName);

    // <d57110> Relax this retriction when parsing ID pragma directive, e.g..
    //else if (type instanceof ModuleEntry) {
    else if (type instanceof ModuleEntry && !_isModuleLegalType)
    {
      // Module's are not valid types.
      ParseException.moduleNotType (scanner, typeName);
      type = null;
    }
    return type;
  } // qualifiedEntry

  /**
   *
   **/
  SymtabEntry recursiveQualifiedEntry (String typeName)
  {
    SymtabEntry type = null;
    if (typeName != null && !typeName.equals ("void"))
    {
      int index = typeName.lastIndexOf ('/');
      if (index >= 0)
      {
        // Figure out if the container of this thing exists, converting any typedefs to interfaces if necessary.
        type = recursiveQualifiedEntry (typeName.substring (0, index));
        if (type == null)
          return null;
        else if (type instanceof TypedefEntry)
          typeName = typeOf (type).fullName () + typeName.substring (index);
      }

      // If we got this far, a container exists, start over looking
      // for the thing itself (this is the meat of the method):
      type = searchOverrideNames (typeName);
      if (type == null)
        type = (SymtabEntry) symbolTable.get (typeName); // search global scope:
      if (type == null)
        type = searchGlobalInheritanceScope (typeName);
    }
    return type;
  } // recursiveQualifiedEntry

  // A partially qualified name is of the form <scope>::<name>.
  // First the scope is defined (meaning it is fully qualified);
  // Then the name is searched for in the scope.

  /**
   *
   **/
  SymtabEntry partlyQualifiedEntry (String typeName, SymtabEntry container)
  {
    // This is the simple logic of this method:
    // type = searchModuleScope (typeName.substring (0, typeName.lastIndexOf ('/')), container);
    // type = qualifiedEntry (type.fullName () + typeName.substring (typeName.lastIndexOf ('/')));
    // But searchModuleScope only finds the first module that fits.
    // The name might not be in that module but in one further out
    // in the module scope.  Should others be searched?
    SymtabEntry type = null;
    if (typeName != null)
    {
      int index = typeName.lastIndexOf ('/');

      // Figure out if the container of this thing exists, converting any
      // typedefs to interfaces if necessary:
      type = recursivePQEntry (typeName.substring (0, index), container);
      if (type instanceof TypedefEntry)
        typeName = typeOf (type).fullName () + typeName.substring (index);

      // If we got this far, a container exists, start over looking
      // for the thing itself.

      if (container != null)
        type = searchModuleScope (typeName.substring (0, typeName.lastIndexOf ('/')), container);
      if (type == null)
        type = qualifiedEntry (typeName);
      else
        type = qualifiedEntry (type.fullName () + typeName.substring (typeName.lastIndexOf ('/')));
    }
    return type;
  } // partlyQualifiedEntry

  // partlyQualifiedEntry and recursivePQEntry are almost identical.
  // They are different because when the recursive one is looking for
  // the existence of containers, the error check for a module type
  // must not occur (this check is done in qualifiedEntry).  Only
  // when the full partly qualified name is being processed must this
  // check be performed.

  /**
   *
   **/
  SymtabEntry recursivePQEntry (String typeName, SymtabEntry container)
  {
    SymtabEntry type = null;
    if (typeName != null)
    {
      int index = typeName.lastIndexOf ('/');
      if (index < 0)
        type = searchModuleScope (typeName, container);
      else
      {
        // Figure out if the container of this thing exists, converting any
        // typedefs to interfaces if necessary:
        type = recursivePQEntry (typeName.substring (0, index), container);
        if (type == null)
          return null;
        else if (type instanceof TypedefEntry)
          typeName = typeOf (type).fullName () + typeName.substring (index);

        // If we got this far, a container exists, start over, looking
        // for the thing itself (This is the meat of the method):
        if (container != null)
          type = searchModuleScope (typeName.substring (0, typeName.lastIndexOf ('/')), container);
          if (type == null)
            recursiveQualifiedEntry (typeName);
          else
            type = recursiveQualifiedEntry (type.fullName () + typeName.substring (typeName.lastIndexOf ('/')));
      }
    }
    return type;
  } // recursivePQEntry

  // The named type is searched for in the following order:
  // 1.  Local scope
  // 2.  Inheritance scope
  // 3.  OverrideNames
  // 4.  Module scope

  /**
   *
   **/
  SymtabEntry unqualifiedEntry (String typeName, SymtabEntry container)
  {
    SymtabEntry type = unqualifiedEntryWMod (typeName, container);

    // <d57110> Relax this retriction in special cases, e.g., when
    // parsing a ID pragma directive.
    //if (type instanceof ModuleEntry) {
    if (type instanceof ModuleEntry && !_isModuleLegalType)
    {
      // Module's are not valid types:
      ParseException.moduleNotType (scanner, typeName);
      type = null;
    }
    return type;
  } // unqualifiedEntry

  /**
   *
   **/
  SymtabEntry unqualifiedEntryWMod (String typeName, SymtabEntry container)
  {
    SymtabEntry type = null;
    if ((typeName != null) && !typeName.equals ("void"))
    {
      // Search local scope:
      type = (SymtabEntry)symbolTable.get (container.fullName () + '/' + typeName);
      if (type == null)
        type = searchLocalInheritanceScope (typeName, container);
      if (type == null)
        type = searchOverrideNames (typeName);
      if ((type == null) && (container != null))
        type = searchModuleScope (typeName, container);
      if (type == null)
        type = searchParentInheritanceScope (typeName, container);
    }
    if (type == null)
      // Then it's not anywhere, report the error:
      ParseException.undeclaredType (scanner, typeName);
    return type;
  } // unqualifiedEntryWMod

  /**
   * Walks up the enclosing scopes until it finds an interface type. Then,
   * searches up that interface inheritance tree for the type definition.
   *
   * @param name type name to be searched for.
   * @param ptype parent type entry.
   **/
  SymtabEntry searchParentInheritanceScope(String name, SymtabEntry ptype) {

    String cname = ptype.fullName();

    while ((ptype != null) && !(cname.equals ("")) &&
           !(ptype instanceof InterfaceEntry)) {
        int index = cname.lastIndexOf ('/');
        if (index < 0) {
            cname = "";
        } else {
            cname = cname.substring (0, index);
            ptype = (SymtabEntry) symbolTable.get(cname);
        }
    }

    if ((ptype == null) || !(ptype instanceof InterfaceEntry)) {
        return null; // could not find an enclosing interface type - give up.
    }

    // check if the enclosing interface supports the type definition.
    String fullName = ptype.fullName () + '/' + name;
    SymtabEntry type = (SymtabEntry) symbolTable.get (fullName);
    if (type != null) {
        return type; // found type definition.
    }

    // search up the interface inheritance tree.
    return searchLocalInheritanceScope(name, ptype);
  }

  /**
   *
   **/
  SymtabEntry searchGlobalInheritanceScope (String name)
  {
    // See if the container of this named object is an interface:
    int         index = name.lastIndexOf ('/');
    SymtabEntry entry = null;
    if (index >= 0)
    {
      String containerName = name.substring (0, index);
      entry = (SymtabEntry)symbolTable.get (containerName);
      entry = (entry instanceof InterfaceEntry)
          // It's an interface, now look in its inheritance scope:
          ? searchLocalInheritanceScope (name.substring (index + 1), entry)
          : null;
    }
    return entry;
  } // searchGlobalInheritanceScope

  /**
   *
   **/
  SymtabEntry searchLocalInheritanceScope (String name, SymtabEntry container)
  {
    return (container instanceof InterfaceEntry)
        ? searchDerivedFrom (name, (InterfaceEntry) container)
        : null;
  } // searchLocalInheritanceScope

  /**
   *
   **/
  SymtabEntry searchOverrideNames (String name)
  {
    String overrideName = (String)overrideNames.get (name);
    return (overrideName != null)
        ? (SymtabEntry)symbolTable.get (overrideName)
        : null;
  } // searchOverrideNames

  /**
   *
   **/
  SymtabEntry searchModuleScope (String name, SymtabEntry container)
  {
    String      module   = container.fullName ();
    String      fullName = module + '/' + name;
    SymtabEntry type     = (SymtabEntry)symbolTable.get (fullName);
    while ((type == null) && !module.equals (""))
    {
      int index = module.lastIndexOf ('/');
      if (index < 0)
        module = "";
      else
      {
        module   = module.substring (0, index);
        fullName = module + '/' + name;
        type     = (SymtabEntry)symbolTable.get (fullName);
      }
    }
    return (type == null) ? (SymtabEntry)symbolTable.get (name) : type;
  } // searchModuleScope

  /**
   *
   **/
  SymtabEntry searchDerivedFrom (String name, InterfaceEntry i)
  {
    for (Enumeration e = i.derivedFrom ().elements (); e.hasMoreElements ();)
    {
      SymtabEntry tmp = (SymtabEntry)e.nextElement ();
      if (tmp instanceof InterfaceEntry)
      {
        InterfaceEntry parent = (InterfaceEntry)tmp;
        String fullName = parent.fullName () + '/' + name;
        SymtabEntry type = (SymtabEntry)symbolTable.get (fullName);
        if (type != null)
          return type;
        type = searchDerivedFrom (name, parent);
        if (type != null)
          return type;
      }
      // else it is a ForwardEntry and nothing can be done at this point.
    }
    return null;
  } // searchDerivedFrom

  /**
   *
   **/
  String entryName (SymtabEntry entry)
  {
    if (entry instanceof AttributeEntry)
      return "attribute";
    if (entry instanceof ConstEntry)
      return "constant";
    if (entry instanceof EnumEntry)
      return "enumeration";
    if (entry instanceof ExceptionEntry)
      return "exception";
    if (entry instanceof ValueBoxEntry)
      return "value box";
    if (entry instanceof ForwardValueEntry || entry instanceof ValueEntry)
      return "value";
    if (entry instanceof ForwardEntry || entry instanceof InterfaceEntry)
      return "interface";
    if (entry instanceof MethodEntry)
      return "method";
    if (entry instanceof ModuleEntry)
      return "module";
    if (entry instanceof ParameterEntry)
      return "parameter";
    if (entry instanceof PrimitiveEntry)
      return "primitive";
    if (entry instanceof SequenceEntry)
      return "sequence";
    if (entry instanceof StringEntry)
      return "string";
    if (entry instanceof StructEntry)
      return "struct";
    if (entry instanceof TypedefEntry)
      return "typedef";
    if (entry instanceof UnionEntry)
      return "union";
    return "void";
  } // entryName

  /**
   *
   **/
  private boolean isInterface (SymtabEntry entry)
  {
    return entry instanceof InterfaceEntry || (entry instanceof ForwardEntry
        && !(entry instanceof ForwardValueEntry)) ;
  }

  private boolean isValue (SymtabEntry entry)
  {
    return entry instanceof ValueEntry ; // || entry instanceof ForwardValueEntry;
  }

  private boolean isInterfaceOnly (SymtabEntry entry)
  {
    return entry instanceof InterfaceEntry ;
  }

  private boolean isForward(SymtabEntry entry)
  {
      return entry instanceof ForwardEntry ;
  }

  // list must be a vector of Strings.
  /**
   *
   **/
  private boolean isntInStringList (Vector list, String name)
  {
    boolean isnt = true;
    Enumeration e = list.elements ();
    while (e.hasMoreElements ())
      if (name.equals ((String)e.nextElement ()))
      {
        ParseException.alreadyDeclared (scanner, name);
        isnt = false;
        break;
      }
    return isnt;
  } // isntInStringList

  // list must be a vector of SymtabEntry's.
  /**
   *
   **/
  private boolean isntInList (Vector list, String name)
  {
    boolean isnt = true;
    for (Enumeration e = list.elements (); e.hasMoreElements ();)
      if (name.equals (((SymtabEntry)e.nextElement ()).name ()))
      {
        ParseException.alreadyDeclared (scanner, name);
        isnt = false;
        break;
      }
    return isnt;
  } // isntInList

  // list must be a vector of SymtabEntry's.
  /**
   *
   **/
  private boolean isntInList (Vector list, SymtabEntry entry)
  {
    boolean isnt = true;
    for (Enumeration e = list.elements (); e.hasMoreElements ();)
    {
      SymtabEntry eEntry = (SymtabEntry)e.nextElement ();
      if (entry == eEntry)  // && entry.fullName().equals (eEntry.fullName()))
      {
        ParseException.alreadyDeclared (scanner, entry.fullName ());
        isnt = false;
        break;
      }
     }
     return isnt;
  } // isntInList

  /**
   *
   **/
  public static SymtabEntry typeOf (SymtabEntry entry)
  {
    while (entry instanceof TypedefEntry)
      entry = entry.type ();
    return entry;
  } // typeOf

  /**
   *
   **/
  void forwardEntryCheck ()
  {
    for (Enumeration e = symbolTable.elements (); e.hasMoreElements ();)
    {
      SymtabEntry entry = (SymtabEntry)e.nextElement ();
      if (entry instanceof ForwardEntry)
        ParseException.forwardEntry (scanner, entry.fullName ());
    }
  } // forwardEntryCheck

  // <46082.03> Revert to "IDL:"-style (i.e., regular) repository ID.
  /*
  void updateRepositoryIds () {
     for (Enumeration e = symbolTable.elements(); e.hasMoreElements();) {
         SymtabEntry entry = (SymtabEntry) e.nextElement();
         if (entry instanceof ValueEntry)
           ((ValueEntry) entry).calcRepId();
     }
  } // updateRepositoryIds
  */

  ////////////////////
  // Error Handling Methods

  // A syntax error occurred.  Skip until a semicolon is encountered.
  // Ignore semicolons within {...} blocks
  /**
   *
   **/
  private void skipToSemicolon () throws IOException
  {
    while (!token.equals (Token.EOF) && !token.equals (Token.Semicolon))
    {
      if (token.equals (Token.LeftBrace))
        skipToRightBrace();
      try
      {
        match (token.type);
      }
      catch (ParseException exception)
      {
        // The error has already been reported...
      }
    }
    if (token.equals (Token.EOF))
      throw new EOFException ();
    try
    {
      match (Token.Semicolon);
    }
    catch (Exception exception)
    {
    }
  } // skipToSemicolon

  /**
   *
   **/
  private void skipToRightBrace () throws IOException
  {
    boolean firstTime = true;
    while (!token.equals (Token.EOF) && !token.equals (Token.RightBrace))
    {
      if (firstTime)
        firstTime = false;
      else if (token.equals (Token.LeftBrace))
        skipToRightBrace ();
      try
      {
        match (token.type);
      }
      catch (ParseException exception)
      {
        // The error has already been reported...
      }
    }
    if (token.equals (Token.EOF))
      throw new EOFException();
  } // skipToRightBrace

  // Error Handling Methods
  ////////////////////

  // <d56351> In CORBA 2.3, an IDL file provides a new scope for Repository IDs.
  // The following methods provide a means for other classes in the framework
  // to manage this scoping (see Preprocessor and Scanner).

  // public static int nPush = 0;
  // public static int nPop = 0;

  /**
   *
   **/
  public static void enteringInclude ()
  {
    repIDStack.push (new IDLID ());
  } // enteringInclude

  /**
   *
   **/
  public static void exitingInclude ()
  {
    repIDStack.pop ();
  } // exitingInclude


  public static final String unknownNamePrefix = "uN__";

       static Hashtable   symbolTable;
              Hashtable   lcSymbolTable  = new Hashtable ();
       static Hashtable   overrideNames;
              Vector      emitList       = new Vector ();
              boolean     emitAll;
  // <f46082.46.01>
              boolean     cppModule;
  // <d62023>
              boolean     noWarn;
              Scanner     scanner;
  // <f46082.40> No longer necessary due to new valueElement() algorithm.
  //          Stack       tokenStack     = new Stack();
              Hashtable   symbols;
              Vector      macros         = new Vector ();
              Vector      paths;

  // Only needed for the pragma directive
              SymtabEntry currentModule  = null;

  // <d56351> Static field necessary to allow Scanner access to enterind/exiting
  // Include() methods. Must reset in Compile class, too!
  //          Stack       repIDStack     = new Stack ();
       static Stack       repIDStack     = new Stack ();

  // Dynamic variable key used for forward type lists.
  // A struct or union X entry may have this attached,
  // which always contains a List<SymtabEntry>.
  // The elements are entries E such that E.type() == X.
  // This list must be resolved in pigeonhole when the
  // type is finally defined.  This is similar to
  // ForwardEntry.replaceForwardDecl.
  private static int ftlKey = SymtabEntry.getVariableKey() ;

              int         sequence       = 0;
              Vector      includes;
              Vector      includeEntries;

  // Only needed in primaryExpr.  Set in Preprocessor.booleanConstExpr.
              boolean     parsingConditionalExpr = false;

              Token         token;
              ModuleEntry   topLevelModule;
  private     Preprocessor  prep;
  private     boolean       verbose;
              SymtabFactory stFactory;
              ExprFactory   exprFactory;
  private     String[]      keywords;
  // <f46082.51> Remove -stateful feature.
  //private     boolean       parseStateful = false;

  // Circular buffer containing most recent tokens, including the current token.
  private     TokenBuffer tokenHistory = new TokenBuffer ();
  protected   float       corbaLevel; // <f60858.1>
  private     Arguments   arguments;

} // class Parser
