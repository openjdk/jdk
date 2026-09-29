<!-- ...................................................................... -->
<!-- XHTML Legacy Markup Module ........................................... -->
<!-- file: xhtml-legacy-1.mod

     This is an extension of XHTML, a reformulation of HTML as a modular XML application.
     Copyright 1998-2005 W3C (MIT, ERCIM, Keio), All Rights Reserved.
     Revision: $Id: xhtml-legacy-1.mod,v 4.1 2001/04/10 09:42:30 altheim Exp $ SMI

     This DTD module is identified by the PUBLIC and SYSTEM identifiers:

       PUBLIC "-//W3C//ELEMENTS XHTML Legacy Markup 1.0//EN"
       SYSTEM "http://www.w3.org/MarkUp/DTD/xhtml-legacy-1.mod"

     Revisions:
     (none)
     ....................................................................... -->

<!-- HTML Legacy Markup

        font, basefont, center, s, strike, u, dir, menu, isindex

          (plus additional datatypes and attributes)

     This optional module declares additional markup for simple
     presentation-related markup based on features found in the
     HTML 4 Transitional and Frameset DTDs. This relies on
     inclusion of the Legacy Redeclarations module. This module
     also declares the frames, inline frames and object modules.

     This is to allow XHTML 1.1 documents to be transformed for
     display on HTML browsers where CSS support is inconsistent
     or unavailable.
-->
<!-- Constructing a Legacy DTD

     To construct a DTD driver obtaining a close approximation
     of the HTML 4 Transitional and Frameset DTDs, declare the
     Legacy Redeclarations module as the pre-framework redeclaration
     parameter entity (%xhtml-prefw-redecl.mod;) and INCLUDE its
     conditional section:

        ...
        <!ENTITY % xhtml-prefw-redecl.module "INCLUDE" >
        <![%xhtml-prefw-redecl.module;[
        <!ENTITY % xhtml-prefw-redecl.mod
            PUBLIC "-//W3C//ELEMENTS XHTML Legacy Redeclarations 1.0//EN"
                   "xhtml-legacy-redecl-1.mod" >
        %xhtml-prefw-redecl.mod;]]>

     Such a DTD should be named with a variant FPI and redeclare
     the value of the %XHTML.version; parameter entity to that FPI:

         "-//Your Name Here//DTD XHTML Legacy 1.1//EN"

     IMPORTANT:  see also the notes included in the Legacy Redeclarations
     Module for information on how to construct a DTD using this module.
-->


<!-- Additional Element Types .................................... -->

<!-- font: Local Font Modifier  ........................ -->

<!ENTITY % font.element  "INCLUDE" >
<![%font.element;[
<!ENTITY % font.content
     "( #PCDATA | %Inline.mix; )*"
>
<!ENTITY % font.qname  "font" >
<!ELEMENT %font.qname;  %font.content; >
<!-- end of font.element -->]]>

<!ENTITY % font.attlist  "INCLUDE" >
<![%font.attlist;[
<!ATTLIST %font.qname;
      %Core.attrib;
      %I18n.attrib;
      size         CDATA                    #IMPLIED
      color        %Color.datatype;         #IMPLIED
      face         CDATA                    #IMPLIED
>
<!-- end of font.attlist -->]]>

<!-- basefont: Base Font Size  ......................... -->

<!ENTITY % basefont.element  "INCLUDE" >
<![%basefont.element;[
<!ENTITY % basefont.content "EMPTY" >
<!ENTITY % basefont.qname  "basefont" >
<!ELEMENT %basefont.qname;  %basefont.content; >
<!-- end of basefont.element -->]]>

<!ENTITY % basefont.attlist  "INCLUDE" >
<![%basefont.attlist;[
<!ATTLIST %basefont.qname;
      %id.attrib;
      size         CDATA                    #REQUIRED
      color        %Color.datatype;         #IMPLIED
      face         CDATA                    #IMPLIED
>
<!-- end of basefont.attlist -->]]>

<!-- center: Center Alignment  ......................... -->

<!ENTITY % center.element  "INCLUDE" >
<![%center.element;[
<!ENTITY % center.content
     "( #PCDATA | %Flow.mix; )*"
>
<!ENTITY % center.qname  "center" >
<!ELEMENT %center.qname;  %center.content; >
<!-- end of center.element -->]]>

<!ENTITY % center.attlist  "INCLUDE" >
<![%center.attlist;[
<!ATTLIST %center.qname;
      %Common.attrib;
>
<!-- end of center.attlist -->]]>

<!-- s: Strike-Thru Text Style  ........................ -->

<!ENTITY % s.element  "INCLUDE" >
<![%s.element;[
<!ENTITY % s.content
     "( #PCDATA | %Inline.mix; )*"
>
<!ENTITY % s.qname  "s" >
<!ELEMENT %s.qname;  %s.content; >
<!-- end of s.element -->]]>

<!ENTITY % s.attlist  "INCLUDE" >
<![%s.attlist;[
<!ATTLIST %s.qname;
      %Common.attrib;
>
<!-- end of s.attlist -->]]>

<!-- strike: Strike-Thru Text Style  ....................-->

<!ENTITY % strike.element  "INCLUDE" >
<![%strike.element;[
<!ENTITY % strike.content
     "( #PCDATA | %Inline.mix; )*"
>
<!ENTITY % strike.qname  "strike" >
<!ELEMENT %strike.qname;  %strike.content; >
<!-- end of strike.element -->]]>

<!ENTITY % strike.attlist  "INCLUDE" >
<![%strike.attlist;[
<!ATTLIST %strike.qname;
      %Common.attrib;
>
<!-- end of strike.attlist -->]]>

<!-- u: Underline Text Style  ...........................-->

<!ENTITY % u.element  "INCLUDE" >
<![%u.element;[
<!ENTITY % u.content
     "( #PCDATA | %Inline.mix; )*"
>
<!ENTITY % u.qname  "u" >
<!ELEMENT %u.qname;  %u.content; >
<!-- end of u.element -->]]>

<!ENTITY % u.attlist  "INCLUDE" >
<![%u.attlist;[
<!ATTLIST %u.qname;
      %Common.attrib;
>
<!-- end of u.attlist -->]]>

<!-- dir: Directory List  .............................. -->

<!-- NOTE: the content model for <dir> in HTML 4 excluded %Block.mix;
-->
<!ENTITY % dir.element  "INCLUDE" >
<![%dir.element;[
<!ENTITY % dir.content
     "( %li.qname; )+"
>
<!ENTITY % dir.qname  "dir" >
<!ELEMENT %dir.qname;  %dir.content; >
<!-- end of dir.element -->]]>

<!ENTITY % dir.attlist  "INCLUDE" >
<![%dir.attlist;[
<!ATTLIST %dir.qname;
      %Common.attrib;
      compact      ( compact )              #IMPLIED
>
<!-- end of dir.attlist -->]]>

<!-- menu: Menu List  .................................. -->

<!-- NOTE: the content model for <menu> in HTML 4 excluded %Block.mix;
-->
<!ENTITY % menu.element  "INCLUDE" >
<![%menu.element;[
<!ENTITY % menu.content
     "( %li.qname; )+"
>
<!ENTITY % menu.qname  "menu" >
<!ELEMENT %menu.qname;  %menu.content; >
<!-- end of menu.element -->]]>

<!ENTITY % menu.attlist  "INCLUDE" >
<![%menu.attlist;[
<!ATTLIST %menu.qname;
      %Common.attrib;
      compact      ( compact )              #IMPLIED
>
<!-- end of menu.attlist -->]]>

<!-- isindex: Single-Line Prompt  ...................... -->

<!ENTITY % isindex.element  "INCLUDE" >
<![%isindex.element;[
<!ENTITY % isindex.content "EMPTY" >
<!ENTITY % isindex.qname  "isindex" >
<!ELEMENT %isindex.qname;  %isindex.content; >
<!-- end of isindex.element -->]]>

<!ENTITY % isindex.attlist  "INCLUDE" >
<![%isindex.attlist;[
<!ATTLIST %isindex.qname;
      %Core.attrib;
      %I18n.attrib;
      prompt       %Text.datatype;          #IMPLIED
>
<!-- end of isindex.attlist -->]]>


<!-- Additional Attributes ....................................... -->

<!-- Alignment attribute for Transitional use in HTML browsers
     (this functionality is generally well-supported in CSS,
     except within some contexts)
-->
<!ENTITY % align.attrib
     "align        ( left | center | right | justify ) #IMPLIED"
>

<!ATTLIST %applet.qname;
      align       ( top | middle | bottom | left | right ) #IMPLIED
      hspace      %Pixels.datatype;         #IMPLIED
      vspace      %Pixels.datatype;         #IMPLIED
>

<!ATTLIST %body.qname;
      background   %URI.datatype;           #IMPLIED
      bgcolor      %Color.datatype;         #IMPLIED
      text         %Color.datatype;         #IMPLIED
      link         %Color.datatype;         #IMPLIED
      vlink        %Color.datatype;         #IMPLIED
      alink        %Color.datatype;         #IMPLIED
>

<!ATTLIST %br.qname;
      clear        ( left | all | right | none ) 'none'
>

<!ATTLIST %caption.qname;
      align        ( top | bottom | left | right ) #IMPLIED
>

<!ATTLIST %div.qname;
      %align.attrib;
>

<!ATTLIST %h1.qname;
      %align.attrib;
>

<!ATTLIST %h2.qname;
      %align.attrib;
>

<!ATTLIST %h3.qname;
      %align.attrib;
>

<!ATTLIST %h4.qname;
      %align.attrib;
>

<!ATTLIST %h5.qname;
      %align.attrib;
>

<!ATTLIST %h6.qname;
      %align.attrib;
>

<!ATTLIST %hr.qname;
      align        ( left | center | right ) #IMPLIED
      noshade      ( noshade )              #IMPLIED
      size         %Pixels.datatype;        #IMPLIED
      width        %Length.datatype;        #IMPLIED
>

<!ATTLIST %img.qname;
      align       ( top | middle | bottom | left | right ) #IMPLIED
      border      %Pixels.datatype;         #IMPLIED
      hspace      %Pixels.datatype;         #IMPLIED
      vspace      %Pixels.datatype;         #IMPLIED
>

<!ATTLIST %input.qname;
      align       ( top | middle | bottom | left | right ) #IMPLIED
>

<!ATTLIST %legend.qname;
      align        ( top | bottom | left | right ) #IMPLIED
>

<!ATTLIST %li.qname;
      type         CDATA                     #IMPLIED
      value        %Number.datatype;         #IMPLIED
>

<!ATTLIST %object.qname;
      align        ( top | middle | bottom | left | right ) #IMPLIED
      border       %Pixels.datatype;         #IMPLIED
      hspace       %Pixels.datatype;         #IMPLIED
      vspace       %Pixels.datatype;         #IMPLIED
>

<!ATTLIST %dl.qname;
      compact      ( compact )              #IMPLIED
>

<!ATTLIST %ol.qname;
      type         CDATA                    #IMPLIED
      compact      ( compact )              #IMPLIED
      start        %Number.datatype;        #IMPLIED
>

<!ATTLIST %p.qname;
      %align.attrib;
>

<!ATTLIST %pre.qname;
      width        %Length.datatype;        #IMPLIED
>

<!ATTLIST %script.qname;
      language     %ContentType.datatype;   #IMPLIED
>

<!ATTLIST %table.qname;
      align        ( left | center | right ) #IMPLIED
      bgcolor      %Color.datatype;         #IMPLIED
>

<!ATTLIST %tr.qname;
      bgcolor     %Color.datatype;          #IMPLIED
>

<!ATTLIST %th.qname;
      nowrap      ( nowrap )                #IMPLIED
      bgcolor     %Color.datatype;          #IMPLIED
      width       %Length.datatype;         #IMPLIED
      height      %Length.datatype;         #IMPLIED
>

<!ATTLIST %td.qname;
      nowrap      ( nowrap )                #IMPLIED
      bgcolor     %Color.datatype;          #IMPLIED
      width       %Length.datatype;         #IMPLIED
      height      %Length.datatype;         #IMPLIED
>

<!ATTLIST %ul.qname;
      type         CDATA                    #IMPLIED
      compact      ( compact )              #IMPLIED
>

<!-- Frames Module ............................................... -->
<!ENTITY % xhtml-frames.module "IGNORE" >
<![%xhtml-frames.module;[
<!ENTITY % xhtml-frames.mod
     PUBLIC "-//W3C//ELEMENTS XHTML Frames 1.0//EN"
            "xhtml-frames-1.mod" >
%xhtml-frames.mod;]]>

<!-- Inline Frames Module ........................................ -->
<!ENTITY % xhtml-iframe.module "INCLUDE" >
<![%xhtml-iframe.module;[
<!ATTLIST %iframe.qname;
      align        ( top | middle | bottom | left | right ) #IMPLIED
>
<!ENTITY % xhtml-iframe.mod
     PUBLIC "-//W3C//ELEMENTS XHTML Inline Frame Element 1.0//EN"
            "xhtml-iframe-1.mod" >
%xhtml-iframe.mod;]]>

<!-- end of xhtml-legacy-1.mod -->
