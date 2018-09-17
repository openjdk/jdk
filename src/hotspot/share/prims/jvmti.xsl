<?xml version="1.0"?> 
<!--
 Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

 This code is free software; you can redistribute it and/or modify it
 under the terms of the GNU General Public License version 2 only, as
 published by the Free Software Foundation.

 This code is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 version 2 for more details (a copy is included in the LICENSE file that
 accompanied this code).

 You should have received a copy of the GNU General Public License version
 2 along with this work; if not, write to the Free Software Foundation,
 Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.

 Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 or visit www.oracle.com if you need additional information or have any
 questions.
  
-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:import href="jvmtiLib.xsl"/>

<xsl:output method="html" indent="yes" 
  doctype-system="about:legacy-compat"/>

<xsl:param name="development"></xsl:param>

<xsl:template match="specification">
  <html lang="en">
  <head>
        <title>
          <xsl:value-of select="@label"/>
          <xsl:text> </xsl:text>
          <xsl:call-template name="showversion"/>
        </title>
        <style>
          .centered { text-align: center; }
          .leftAligned { text-align: left; }
          .rightAligned { text-align: right; }
          .bgLight { background-color: #EEEEFF; }
          .bgDark { background-color: #CCCCFF}
          th { background-color: #EEEEFF; }
          td.tableHeader {font-size: larger; text-align:center; }
          div.sep { height: 10px; }
          div.callbackCtnr { margin: 0 5%; }
          hr { border-width:0; color:gray; background-color:gray; }
          hr.thick { height:3px; }
          hr.thin { height:1px; }
          table.bordered { border: 1px solid gray; border-spacing: 0; border-collapse: separate; }
          table.bordered td, table.bordered th { padding: 3px; border: 1px solid black; }
          table.wide { width: 100%; }
        </style>
  </head>
  <body>
    <div class="centered" role="banner">
      <xsl:apply-templates select="title"/>
    </div>
    <ul role="navigation">
      <li>
        <a href="#SpecificationIntro"><b>Introduction</b></a>
        <ul>
          <xsl:for-each select="intro">
            <li>
              <a>
                <xsl:attribute name="href">#<xsl:value-of select="@id"/>
                </xsl:attribute>
                <b><xsl:value-of select="@label"/></b>
              </a>
            </li>
          </xsl:for-each>
        </ul>
      </li>
      <li>
        <a href="#FunctionSection"><b>Functions</b></a>
        <ul>
          <xsl:for-each select="functionsection/intro">
            <li>
              <a>
                <xsl:attribute name="href">#<xsl:value-of select="@id"/>
                </xsl:attribute>
                <b><xsl:value-of select="@label"/></b>
              </a>
            </li>
          </xsl:for-each>
          <li>
            <a href="#FunctionIndex"><b>Function Index</b></a>
            <ul>
              <xsl:for-each select="functionsection/category">
                <li>
                  <a>
                    <xsl:attribute name="href">#<xsl:value-of select="@id"/>
                    </xsl:attribute>
                    <b><xsl:value-of select="@label"/></b>
                  </a>
                </li>
              </xsl:for-each>
            </ul>
          </li>
          <li>
            <a href="#ErrorSection"><b>Error Codes</b></a>
          </li>
        </ul>
      </li>
      <li>
        <a href="#EventSection"><b>Events</b></a>
        <ul>
          <li>
            <a href="#EventIndex"><b>Event Index</b></a>
          </li>
        </ul>
      </li>
      <li>
        <a href="#DataSection"><b>Data Types</b></a>
        <ul>
          <xsl:for-each select="//basetypes">   
          <li>
            <a>
              <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
              <b>
                <xsl:value-of select="@label"/>
              </b>
            </a>
          </li>
          </xsl:for-each>
          <li>
            <a href="#StructureTypeDefinitions"><b>Structure Type Definitions</b></a>
          </li>
          <li>
            <a href="#FunctionTypeDefinitions"><b>Function Type Definitions</b></a>
          </li>
          <li>
            <a href="#EnumerationDefinitions"><b>Enumeration Definitions</b></a>
          </li>
          <li>
            <a href="#FunctionTable"><b>Function Table</b></a>
          </li>
        </ul>
      </li>
      <li>
        <a href="#ConstantIndex"><b>Constant Index</b></a>
      </li>
      <xsl:if test="$development = 'Show'">
        <li>
          <a href="#SpecificationIssues"><b>Issues</b></a>
          <ul>
            <xsl:for-each select="issuessection/intro">
              <li>
                <a>
                  <xsl:attribute name="href">#<xsl:value-of select="@id"/>
                  </xsl:attribute>
                  <b><xsl:value-of select="@label"/></b>
                </a>
              </li>
            </xsl:for-each>
          </ul>
        </li>
      </xsl:if>
      <li>
        <a href="#ChangeHistory"><b>Change History</b></a>
      </li>
    </ul>
    <!-- end table of contents, begin body -->
    <div role="main">
    <div class="sep"/>
    <hr class="thick"/>
    <div class="sep"/>
    <p id="SpecificationIntro"/>
      <xsl:apply-templates select="intro"/>
    <p id="FunctionSection"/>
      <xsl:apply-templates select="functionsection"/>
    <p id="ErrorSection"/>
      <xsl:apply-templates select="errorsection"/>
    <p id="DataSection"/>
      <xsl:apply-templates select="datasection"/>
    <p id="EventSection"/>
      <xsl:apply-templates select="eventsection"/>
    <p id="ConstantIndex"/>
      <div class="sep"/>
      <hr class="thick"/>
      <h2>
        Constant Index
      </h2>
      <blockquote>
        <xsl:apply-templates select="//constant" mode="index">
          <xsl:sort select="@id"/>
        </xsl:apply-templates>
      </blockquote>
    <xsl:if test="$development = 'Show'">
      <p id="SpecificationIssues"/>
      <div class="sep"/>
      <hr class="thick"/>
      <h2>
        <xsl:value-of select="issuessection/@label"/>
      </h2>
      <xsl:apply-templates select="issuessection/intro"/>
    </xsl:if>
    <p id="ChangeHistory"/>
      <xsl:apply-templates select="changehistory"/>
    </div>
    <xsl:apply-templates select="copyright"/>
  </body>
</html>
</xsl:template>

<xsl:template match="title">
    <h1>
      <xsl:apply-templates/>
    </h1>
    <h3>
      <xsl:value-of select="@subtitle"/>
      <xsl:text> </xsl:text>
      <xsl:call-template name="showbasicversion"/>
    </h3>
</xsl:template>

<xsl:template match="copyright">
  <p>
    <xsl:apply-templates/>
  </p>
</xsl:template>

<xsl:template match="functionsection">
  <div class="sep"/>
  <hr class="thick"/>
  <h2>
    <xsl:value-of select="@label"/>
  </h2>
  <xsl:apply-templates select="intro"/>
  <h3 id="FunctionIndex">Function Index</h3>
  <ul>
    <xsl:apply-templates select="category" mode="index"/>
  </ul>
  <xsl:apply-templates select="category" mode="body"/>
</xsl:template>

<xsl:template match="category" mode="index">
  <li>
    <a>
      <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
      <b>
        <xsl:value-of select="@label"/>
      </b>
    </a>
    <ul>
      <xsl:apply-templates select="function[count(@hide)=0]" mode="index"/>
    </ul>
  </li>
</xsl:template>

<xsl:template match="function|callback" mode="index">
  <li>
    <a>
      <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
      <xsl:apply-templates select="synopsis" mode="index"/>
    </a>
  </li>
</xsl:template>

<xsl:template match="synopsis" mode="index">
    <xsl:value-of select="."/>
</xsl:template>

<xsl:template match="category" mode="body">
  <p>
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
  </p>
  <hr class="thick"/>
  <h2 class="centered"><xsl:value-of select="@label"/></h2>
  <xsl:value-of select="@label"/> functions:
  <ul>
    <xsl:apply-templates select="function[count(@hide)=0]" mode="index"/>
  </ul>
  <xsl:variable name="calltypes" select="callback"/>
  <xsl:if test="count($calltypes)!=0">
    <xsl:value-of select="@label"/> function types:
    <ul>
      <xsl:apply-templates select="$calltypes" mode="index"/>
    </ul>
  </xsl:if>
  <xsl:variable name="cattypes" 
    select="(descendant::typedef|descendant::uniontypedef|descendant::capabilitiestypedef|descendant::constants[@kind='enum'])"/>
  <xsl:if test="count($cattypes)!=0">
    <xsl:value-of select="@label"/> types:
    <ul>
      <xsl:for-each select="$cattypes">
        <li>
          <a>
            <xsl:attribute name="href">
              <xsl:text>#</xsl:text>
              <xsl:value-of select="@id"/>
            </xsl:attribute>
            <code><xsl:value-of select="@id"/></code>
          </a>
          <xsl:text> - </xsl:text>
          <xsl:value-of select="@label"/>
        </li>
      </xsl:for-each>
    </ul>    
  </xsl:if>
  <xsl:variable name="catconst" 
    select="(descendant::constants[@kind!='enum'])"/>
  <xsl:if test="count($catconst)!=0">
    <xsl:value-of select="@label"/> flags and constants:
    <ul>
      <xsl:for-each select="$catconst">
        <li>
          <a>
            <xsl:attribute name="href">
              <xsl:text>#</xsl:text>
              <xsl:value-of select="@id"/>
            </xsl:attribute>
            <xsl:value-of select="@label"/>
          </a>
        </li>
      </xsl:for-each>
    </ul>    
  </xsl:if>
  <xsl:apply-templates select="intro|typedef|uniontypedef|capabilitiestypedef"/>
  <div class="sep"/>
  <xsl:apply-templates select="function[count(@hide)=0]|callback" mode="body"/>
</xsl:template>

<xsl:template match="function" mode="body">
  <hr class="thin">
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
    
  </hr>
  <xsl:apply-templates select="synopsis" mode="body"/>
  <blockquote>
    <xsl:apply-templates select="typedef" mode="code"/>
    <xsl:apply-templates select="descendant::constants[@kind='enum']" mode="signature"/>
    <pre>
      <xsl:text>jvmtiError
</xsl:text>
      <xsl:value-of select="@id"/>(jvmtiEnv* env<xsl:apply-templates select="parameters" mode="signature"/>)</pre>
  </blockquote>
  <xsl:apply-templates select="description"/>
  <xsl:apply-templates select="." mode="generalinfo"/>
  <xsl:apply-templates select="capabilities|eventcapabilities"/>
  <xsl:apply-templates select="typedef" mode="body"/>
  <xsl:apply-templates select="parameters" mode="body"/>
  <xsl:apply-templates select="." mode="errors"/>
</xsl:template>

<xsl:template match="function" mode="generalinfo">
  <table class="bordered wide">
     <tr class="bgLight">
      <td >
        <a href="#jvmtiPhase">Phase</a>
      </td>
      <td>
        <a href="#heapCallbacks">Callback Safe</a>
      </td>
      <td>
        <a href="#FunctionTable">Position</a>
      </td>
      <td>
        <a href="#ChangeHistory">Since</a>
      </td>
    </tr>
    <tr>
      <td >
        <xsl:apply-templates select="." mode="phaseinfo"/>
      </td>
      <td>
        <xsl:apply-templates select="." mode="callbacksafeinfo"/>
      </td>
      <td>
        <xsl:value-of select="@num"/>
      </td>
      <td>
        <xsl:value-of select="@since"/>
      </td>
    </tr>
  </table>
</xsl:template>

<xsl:template match="event" mode="generalinfo">
  <table class="bordered wide">
    <tr class="bgLight">
      <td >
        <a href="#jvmtiPhase">Phase</a>
      </td>
      <td>
        <a href="#jvmtiEvent">Event Type</a>
      </td>
      <td>
        <a href="#jvmtiEvent">Number</a>
      </td>
      <td>
        <a href="#enablingevents">Enabling</a>
      </td>
      <td>
        <a href="#ChangeHistory">Since</a>
      </td>
    </tr>
    <tr>
      <td >
        <xsl:apply-templates select="." mode="phaseinfo"/>
      </td>
      <td>
        <code><xsl:value-of select="@const"/></code>
      </td>
      <td>
        <xsl:value-of select="@num"/>
      </td>
      <td>
          <code><a href="#SetEventNotificationMode">SetEventNotificationMode</a>(JVMTI_ENABLE, 
          <xsl:value-of select="@const"/>, NULL)</code>
      </td>
      <td>
        <xsl:value-of select="@since"/>
      </td>
    </tr>
  </table>
</xsl:template>

<xsl:template match="function" mode="phaseinfo">
  may
  <xsl:choose>
    <xsl:when test="count(@phase) = 0 or @phase = 'live'">
      only be called during the live
    </xsl:when>
    <xsl:otherwise>
      <xsl:choose>
        <xsl:when test="@phase = 'onload'">
          only be called during the OnLoad or the live
        </xsl:when>
        <xsl:otherwise>
          <xsl:choose>
            <xsl:when test="@phase = 'any'">
              be called during any
            </xsl:when>
            <xsl:otherwise>
              <xsl:choose>
                <xsl:when test="@phase = 'start'">
                  only be called during the start or the live
                </xsl:when>
                <xsl:otherwise>
                  <xsl:choose>
                    <xsl:when test="@phase = 'onloadOnly'">
                      only be called during the OnLoad
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:message terminate="yes">
                        bad phase - <xsl:value-of select="@phase"/>
                      </xsl:message>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:otherwise>
            </xsl:choose>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:otherwise>
  </xsl:choose>
  phase
</xsl:template>


<xsl:template match="event" mode="phaseinfo">
  sent
  <xsl:choose>
    <xsl:when test="count(@phase) = 0 or @phase = 'live'">
      only during the live
    </xsl:when>
    <xsl:otherwise>
      <xsl:choose>
        <xsl:when test="@phase = 'any'">
          during the primordial, start or live
        </xsl:when>
        <xsl:otherwise>
          <xsl:choose>
            <xsl:when test="@phase = 'start'">
              during the start or live
            </xsl:when>
            <xsl:otherwise>
              <xsl:message terminate="yes">
                bad phase - <xsl:value-of select="@phase"/>
              </xsl:message>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:otherwise>
      </xsl:choose>
    </xsl:otherwise>
  </xsl:choose>
  phase
</xsl:template>


<xsl:template match="function" mode="callbacksafeinfo">
  <xsl:choose>
    <xsl:when test="contains(@callbacksafe,'safe')">
    This function may be called from the callbacks to the
    <a href="#Heap">Heap</a> iteration functions, or from the
    event handlers for the 
    <a href="#GarbageCollectionStart"><code>GarbageCollectionStart</code></a>,
    <a href="#GarbageCollectionFinish"><code>GarbageCollectionFinish</code></a>,
    and <a href="#ObjectFree"><code>ObjectFree</code></a> events.
    </xsl:when>
    <xsl:otherwise>
      No
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>


<xsl:template match="callback" mode="body">
  <hr class="thin">
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
  </hr>
  <xsl:apply-templates select="synopsis" mode="body"/>
  <div class="callbackCtnr">
  <blockquote>
    <pre>
      <xsl:text>typedef </xsl:text>
      <xsl:apply-templates select="child::*[position()=1]" mode="signature"/>
      <xsl:text> (JNICALL *</xsl:text>
      <xsl:value-of select="@id"/>
      <xsl:text>)
    (</xsl:text>
      <xsl:for-each select="parameters">
        <xsl:apply-templates select="param[position()=1]" mode="signature"/>
        <xsl:for-each select="param[position()>1]">
          <xsl:text>, 
     </xsl:text>
          <xsl:apply-templates select="." mode="signature"/>
        </xsl:for-each>
      </xsl:for-each>
      <xsl:text>);</xsl:text>
    </pre>
  </blockquote>
  <xsl:apply-templates select="description"/>
  <xsl:apply-templates select="parameters" mode="body"/>
  </div>
</xsl:template>

<xsl:template match="synopsis" mode="body">
  <h3><xsl:value-of select="."/></h3>
</xsl:template>

<xsl:template match="eventsection">
  <div class="sep"/>
  <hr class="thick"/>
  <h2>
    <xsl:value-of select="@label"/>
  </h2>
  <xsl:apply-templates select="intro"/>
  <blockquote>
  <pre>
  <xsl:text>
typedef struct {
</xsl:text>
  <xsl:call-template name="eventStruct">
    <xsl:with-param name="events" select="event"/>
    <xsl:with-param name="index" select="0"/>
    <xsl:with-param name="started" select="false"/>
    <xsl:with-param name="comment" select="'No'"/>
  </xsl:call-template>
  <xsl:text>} jvmtiEventCallbacks;
</xsl:text>
  </pre>
  </blockquote>
  <div class="sep"/>
  <hr class="thin"/>
  <h3 id="EventIndex">Event Index</h3>
  <ul>
    <xsl:apply-templates select="event" mode="index">
      <xsl:sort select="@label"/>
    </xsl:apply-templates>
  </ul>
  <xsl:apply-templates select="event" mode="body"/>
</xsl:template>

<xsl:template match="event" mode="index">
  <li>
    <a>
      <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
      <b>
        <xsl:value-of select="@label"/>
      </b>
    </a>
  </li>
</xsl:template>

<xsl:template match="event" mode="body">
  <p>
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
  </p>
  <hr class="thick"/>
  <h2><xsl:value-of select="@label"/></h2>
  <div class="sep"/>
  <blockquote>
    <xsl:apply-templates select="typedef" mode="code"/>
    <pre>
<xsl:text>void JNICALL
</xsl:text>
      <xsl:value-of select="@id"/>(jvmtiEnv *jvmti_env<xsl:apply-templates select="parameters" mode="signature"/>)</pre>
  </blockquote>
  <xsl:apply-templates select="description"/>
  <xsl:apply-templates select="." mode="generalinfo"/>
  <xsl:apply-templates select="typedef" mode="body"/>
  <xsl:apply-templates select="capabilities"/>
  <xsl:apply-templates select="parameters" mode="body"/>
</xsl:template>

<xsl:template match="capabilitiestypedef" mode="code">
  <blockquote>
    <pre>
      <xsl:apply-templates select="." mode="genstruct"/>
    </pre>
  </blockquote>
</xsl:template>

<xsl:template match="typedef" mode="code">
  <pre>
  <xsl:call-template name="gentypedef">
    <xsl:with-param name="tdef" select="."/>
  </xsl:call-template>
  </pre>
</xsl:template>

<xsl:template match="uniontypedef" mode="code">
  <pre>
  <xsl:call-template name="genuniontypedef">
    <xsl:with-param name="tdef" select="."/>
  </xsl:call-template>
  </pre>
</xsl:template>

<xsl:template match="capabilitiestypedef|typedef|uniontypedef" mode="description">
  <xsl:apply-templates select="description"/>
</xsl:template>

<xsl:template match="capabilitiestypedef|typedef|uniontypedef">
  <h4>
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
    <xsl:value-of select="@label"/>
  </h4>
  <xsl:apply-templates select="." mode="description"/>
  <blockquote>
    <xsl:apply-templates select="." mode="code"/>
    <xsl:apply-templates select="." mode="justbody"/>
  </blockquote>
</xsl:template>

<xsl:template match="constants" mode="signature">
  <pre>
  <xsl:apply-templates select="." mode="enum"/>
  </pre>
</xsl:template>

<xsl:template match="typedef|uniontypedef" mode="body">
  <p>
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
  </p>
  <xsl:apply-templates select="." mode="justbody"/>
</xsl:template>

<xsl:template match="typedef|uniontypedef" mode="justbody">
    <table class="bordered wide">
      <tr class="bgDark">
        <td colspan="3" class="tableHeader">
          <code><xsl:value-of select="@id"/></code> - <xsl:value-of select="@label"/>
        </td>
      </tr>
      <tr class="bgLight">
        <td>
          Field
        </td>
        <td>
          Type
        </td>
        <td>
          Description
        </td>
      </tr>
      <xsl:apply-templates select="field" mode="body"/>
    </table>
</xsl:template>

<xsl:template match="capabilitiestypedef" mode="body">
  <p>
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
  </p>
  <xsl:apply-templates select="." mode="justbody"/>
</xsl:template>

<xsl:template match="capabilitiestypedef" mode="justbody">
    <table class="bordered wide">
      <tr class="bgDark">
        <td colspan="3" class="tableHeader">
          <code><xsl:value-of select="@id"/></code> - <xsl:value-of select="@label"/>
        </td>
      </tr>
      <tr class="bgLight">
        <td colspan="3">
          All types are <code>unsigned int : 1</code>
        </td>
      </tr>
      <tr class="bgLight">
        <td>
          Field
        </td>
        <td>
          Description
        </td>
        <td>
          <a href="#ChangeHistory">Since</a>
        </td>
      </tr>
      <xsl:apply-templates select="capabilityfield" mode="body"/>
    </table>
</xsl:template>

<xsl:template match="typedef|uniontypedef|capabilitiestypedef|constants" mode="tableentry">
  <tr>
    <td>
      <a>
        <xsl:attribute name="href">
          <xsl:text>#</xsl:text>
          <xsl:value-of select="@id"/>
        </xsl:attribute>
        <code><xsl:value-of select="@id"/></code>
      </a>
    </td>
    <td>
      <xsl:value-of select="@label"/>
    </td>
  </tr>
</xsl:template>

<xsl:template match="field" mode="body">
  <tr>
    <td>
      <code>
        <xsl:attribute name="id">
          <xsl:value-of select="../@id"/>.<xsl:value-of select="@id"/>
        </xsl:attribute>
        <xsl:value-of select="@id"/>
      </code>
    </td>
    <td>
      <code>
        <xsl:apply-templates select="child::*[position()=1]" mode="link"/>
      </code>
    </td>
    <td>
      <xsl:apply-templates select="description" mode="brief"/>
    </td>
  </tr>
</xsl:template>

<xsl:template match="capabilityfield" mode="body">
  <tr>
    <td>
      <code>
        <xsl:choose>
          <xsl:when test="@disp1!=''">
            <xsl:value-of select="@disp1"/>
            <br></br>
            <xsl:value-of select="@disp2"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="@id"/>
          </xsl:otherwise>
        </xsl:choose>
      </code>
    </td>
    <td>
      <xsl:attribute name="id">
        <xsl:value-of select="../@id"/>.<xsl:value-of select="@id"/>
      </xsl:attribute>
      <xsl:apply-templates select="description" mode="brief"/>
    </td>
    <td>
      <xsl:value-of select="@since"/>
    </td>
  </tr>
</xsl:template>

<xsl:template match="callback" mode="tableentry">
  <tr>
    <td>
      <a>
        <xsl:attribute name="href">
          <xsl:text>#</xsl:text>
          <xsl:value-of select="@id"/>
        </xsl:attribute>
        <code>
          <xsl:value-of select="@id"/>
        </code>
      </a>
    </td>
    <td>
      <xsl:apply-templates select="synopsis" mode="index"/>
    </td>
  </tr>
</xsl:template>

<xsl:template match="constants">
  <blockquote>
    <table class="bordered">
      <xsl:attribute name="id">
        <xsl:value-of select="@id"/>
      </xsl:attribute>
      <tr class="bgDark">
        <td colspan="3" class="tableHeader">
            <xsl:value-of select="@label"/>
            <xsl:if test="@kind='enum'">
              <xsl:text> (</xsl:text>
              <code>
                <xsl:value-of select="@id"/>
              </code>
              <xsl:text>)</xsl:text>
            </xsl:if>
        </td>
      </tr>
      <tr class="bgLight">
        <td>
          Constant
        </td>
        <td>
          Value
        </td>
        <td>
          Description
        </td>
      </tr>
      <xsl:apply-templates select="constant" mode="body"/>
    </table>
  </blockquote>
</xsl:template>

<xsl:template match="constant" mode="index">
  <a>
    <xsl:attribute name="href">#<xsl:value-of select="@id"/>
    </xsl:attribute>
    <code>
      <xsl:value-of select="@id"/>
    </code>
  </a>
  <br/>
</xsl:template>

<xsl:template match="constant" mode="body">
  <tr>
    <td>
      <code>
        <xsl:attribute name="id">
          <xsl:value-of select="@id"/>
        </xsl:attribute>
        <xsl:value-of select="@id"/>
      </code>
    </td>
    <td class="rightAligned">
      <xsl:value-of select="@num"/>
    </td>
    <td>
      <xsl:apply-templates/>
    </td>
  </tr>
</xsl:template>

<xsl:template match="basetypes">
  <p>
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
  </p>
    <table class="bordered wide">
      <tr class="bgDark">
        <td colspan="2" class="tableHeader">
          <xsl:value-of select="@label"/>
        </td>
      </tr>
      <tr class="bgLight">
        <td>
          Type
        </td>
        <td>
          Description
        </td>
      </tr>
      <xsl:apply-templates select="basetype" mode="body"/>
    </table>
</xsl:template>

<xsl:template match="basetype" mode="body">
  <xsl:choose>
    <xsl:when test="count(definition)=0">
      <tr>
        <td>
          <code>
            <xsl:value-of select="@id"/>
          </code>
        </td>
        <td>
          <a>
            <xsl:attribute name="id">
              <xsl:choose>
                <xsl:when test="count(@name)=1">
                  <xsl:value-of select="@name"/>
                </xsl:when>
                <xsl:otherwise>
                  <xsl:value-of select="@id"/>
                </xsl:otherwise>
              </xsl:choose>
            </xsl:attribute>
          </a>
          <xsl:apply-templates select="description" mode="brief"/>
        </td>
      </tr>      
    </xsl:when>
    <xsl:otherwise>
      <tr>
        <td rowspan="2">
          <code>
            <xsl:value-of select="@id"/>
          </code>
        </td>
        <td>
          <a>
            <xsl:attribute name="id">
              <xsl:value-of select="@id"/>
            </xsl:attribute>
          </a>
          <xsl:apply-templates select="description" mode="brief"/>
        </td>
      </tr>      
      <tr>
        <td>
          <pre>
            <xsl:apply-templates select="definition"/>
          </pre>          
        </td>
      </tr>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template match="description">
  <xsl:apply-templates/>
  <div class="sep"/>
</xsl:template>

<xsl:template match="description" mode="brief">
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="fieldlink">
  <a>
    <xsl:attribute name="href">#<xsl:value-of select="@struct"/>.<xsl:value-of select="@id"/></xsl:attribute>
    <xsl:choose>
      <xsl:when test=".=''">
        <code>
          <xsl:value-of select="@id"/>
        </code>        
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates/>
      </xsl:otherwise>
    </xsl:choose>
  </a>
</xsl:template>

<xsl:template match="paramlink">
  <a>
    <xsl:attribute name="href">#<xsl:value-of select="ancestor::function/@id|ancestor::event/@id"/>.<xsl:value-of select="@id"/>
    </xsl:attribute>
    <xsl:choose>
      <xsl:when test=".=''">
        <code>
          <xsl:value-of select="@id"/>
        </code>        
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates/>
      </xsl:otherwise>
    </xsl:choose>
  </a>
</xsl:template>

<xsl:template match="eventlink|errorlink|typelink|datalink|functionlink">
  <a>
    <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
    <xsl:choose>
      <xsl:when test=".=''">
        <code>
          <xsl:value-of select="@id"/>
        </code>        
      </xsl:when>
      <xsl:otherwise>
        <xsl:apply-templates/>
      </xsl:otherwise>
    </xsl:choose>
  </a>
</xsl:template>

<xsl:template match="functionphaselist">
  <xsl:variable name="phase" select="@phase"/>
  <ul>
    <xsl:for-each select="/specification/functionsection/category/function[@phase=$phase and count(@hide)=0]">   
      <li>
        <a>
          <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
          <b>
            <xsl:value-of select="@id"/>
          </b>
        </a>
      </li>
    </xsl:for-each>
  </ul>
</xsl:template>

<xsl:template match="eventphaselist">
  <xsl:variable name="phase" select="@phase"/>
  <ul>
    <xsl:for-each select="//eventsection/event[@phase=$phase]">   
      <li>
        <a>
          <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
          <b>
            <xsl:value-of select="@id"/>
          </b>
        </a>
      </li>
    </xsl:for-each>
  </ul>
</xsl:template>

<xsl:template match="externallink">
  <a>
    <xsl:attribute name="href">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
    <xsl:value-of select="."/>
  </a>
</xsl:template>

<xsl:template match="vmspec">
  <cite>
    <xsl:text>The Java&#8482; Virtual Machine Specification</xsl:text>
    <xsl:if test="count(@chapter)=1">
      <xsl:text>, Chapter </xsl:text> 
      <xsl:value-of select="@chapter"/>
    </xsl:if>
  </cite>
</xsl:template>

<xsl:template match="internallink">
  <a>
    <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
    <xsl:apply-templates/>
  </a>
</xsl:template>

<xsl:template match="parameters" mode="body">
  <div class="sep"/>
  <table class="bordered wide">
    <tr class="bgDark">
      <td colspan="3" class="tableHeader">
        Parameters
      </td>
    </tr>
    <tr class="bgLight">
      <td>
        Name
      </td>
      <td>
        Type
      </td>
      <td>
        Description
      </td>
    </tr>
    <xsl:apply-templates select="param[count(jclass/@method)=0]" mode="body"/>
  </table>
</xsl:template>

<xsl:template match="param" mode="body">
  <tr>
    <td>
      <code>
        <xsl:attribute name="id">
          <xsl:value-of select="../../@id"/>.<xsl:value-of select="@id"/>
        </xsl:attribute>
        <xsl:value-of select="@id"/>
      </code>
    </td>
    <td>
      <code>
        <xsl:apply-templates select="child::*[position()=1]" mode="link"/>
      </code>
    </td>
    <td>
      <xsl:apply-templates select="description" mode="brief"/>
      <xsl:if test="count(ancestor::function)=1">
        <xsl:apply-templates select="child::*[position()=1]" mode="funcdescription"/>
      </xsl:if>
    </td>
  </tr>
</xsl:template>

<xsl:template match="capabilities">
  <div class="sep"/>
  <!--
  W3C Validator reports error if all cells has colspan==2.
  The workaround is to detect the case and set colspan = 1 for all cells
  which fills the entire row.
  -->
  <xsl:variable name="fullRowColspan">
    <xsl:choose>
      <xsl:when test="count(required)!=0 or count(capability)!=0">2</xsl:when>
      <xsl:otherwise>1</xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <table class="bordered wide">
    <tr class="bgDark">
      <td colspan="{$fullRowColspan}" class="tableHeader">
        Capabilities
      </td>
    </tr>
    <xsl:choose>
      <xsl:when test="count(required)=0">
        <tr>
          <td colspan="{$fullRowColspan}">
            <b>Required Functionality</b>
          </td>
        </tr>
      </xsl:when>
      <xsl:otherwise>
        <tr>
          <td colspan="2">
            <b>Optional Functionality:</b> might not be implemented for all
            virtual machines. 
            <xsl:choose>
              <xsl:when test="count(required)=1">
                The following capability 
              </xsl:when>
              <xsl:otherwise>
                One of the following capabilities
              </xsl:otherwise>
            </xsl:choose>
            (as returned by 
            <a href="#GetCapabilities"><code>GetCapabilities</code></a>)
            must be true to use this      
            <xsl:choose>
              <xsl:when test="ancestor::function">
                function.
              </xsl:when>
              <xsl:otherwise>
                event.
              </xsl:otherwise>
            </xsl:choose>
          </td>
        </tr>
        <tr class="bgLight">
          <td >
            Capability
          </td>
          <td>
            Effect
          </td>
        </tr>
        <xsl:apply-templates select="required"/>
      </xsl:otherwise>
    </xsl:choose>
    <xsl:if test="count(capability)!=0">
      <tr class="bgDark">
        <td colspan="{$fullRowColspan}" class="centered">
          Optional Features
        </td>
      </tr>
      <xsl:if test="count(required)=0">
        <tr class="bgLight">
          <td >
            Capability
          </td>
          <td>
            Effect
          </td>
        </tr>
      </xsl:if>
      <xsl:apply-templates select="capability"/>
    </xsl:if>
  </table>
</xsl:template>

<xsl:template match="eventcapabilities">
  <div class="sep"/>
  <table class="bordered wide">
    <tr class="bgDark">
      <td colspan="2" class="tableHeader">
        Capabilities
      </td>
    </tr>
    <tr>
      <td colspan="2">
        <b>Required Functionality</b>
      </td>
    </tr>
    <tr class="bgDark">
      <td colspan="2" class="centered">
        Event Enabling Capabilities
      </td>
    </tr>
    <tr class="bgLight">
      <td >
        Capability
      </td>
      <td>
        Events
      </td>
    </tr>
    <xsl:for-each select="//capabilityfield">
      <xsl:variable name="capa" select="@id"/>
      <xsl:variable name="events" select="//event[capabilities/required/@id=$capa]"/>
      <xsl:if test="count($events)">
        <tr>
          <td>
            <a>
              <xsl:attribute name="href">#jvmtiCapabilities.<xsl:value-of select="@id"/>
              </xsl:attribute>
              <code>
                <xsl:value-of select="@id"/>
              </code>
            </a>
          </td>
          <td>
            <xsl:for-each select="$events">
              <a>
                <xsl:attribute name="href">#<xsl:value-of select="@id"/>
                </xsl:attribute>
                <code>
                  <xsl:value-of select="@id"/>
                </code>
              </a>
              <br/>
            </xsl:for-each>
          </td>
        </tr>
      </xsl:if>
    </xsl:for-each>
  </table>
</xsl:template>

<xsl:template match="capability|required">
  <tr>
    <td>
      <a>
        <xsl:attribute name="href">#jvmtiCapabilities.<xsl:value-of select="@id"/>
        </xsl:attribute>
        <code>
          <xsl:value-of select="@id"/>
        </code>
      </a>
    </td>
    <td>
      <xsl:choose>
        <xsl:when test=".=''">
          <xsl:variable name="desiredID" select="@id"/>
          <xsl:for-each select="//capabilityfield[@id=$desiredID]">
            <xsl:apply-templates select="description" mode="brief"/>
          </xsl:for-each>
        </xsl:when>
        <xsl:otherwise>
          <xsl:apply-templates/>
        </xsl:otherwise>
      </xsl:choose>
    </td>
  </tr>
</xsl:template>

<xsl:template match="function" mode="errors">
  <xsl:variable name="haserrors">
    <xsl:apply-templates select="capabilities/required" mode="haserrors"/>
    <xsl:apply-templates select="errors/error" mode="haserrors"/>
    <xsl:apply-templates select="parameters/param" mode="haserrors"/>
  </xsl:variable>
  <div class="sep"/>
  <!--
  W3C Validator reports error if all cells has colspan==2.
  The workaround is to detect the case and set colspan = 1 for all cells
  which fills the entire row.
  -->
  <xsl:variable name="fullRowColspan">
    <xsl:choose>
      <xsl:when test="contains($haserrors,'yes')">2</xsl:when>
      <xsl:otherwise>1</xsl:otherwise>
    </xsl:choose>
  </xsl:variable>
  <table class="bordered wide">
    <tr class="bgDark">
      <td colspan="{$fullRowColspan}" class="tableHeader">
        Errors
      </td>
    </tr>
    <xsl:choose>
      <xsl:when test="contains($haserrors,'yes')">
        <tr>
          <td colspan="2">
            This function returns either a 
            <a href="#universal-error">universal error</a> 
            or one of the following errors
          </td>
        </tr>
        <tr class="bgLight">
          <td>
            Error
          </td>
          <td>
            Description
          </td>
        </tr>
        <xsl:apply-templates select="capabilities/required" mode="errors"/>
        <xsl:apply-templates select="errors/error"/>
        <xsl:apply-templates select="parameters/param" mode="errors"/>
      </xsl:when>
      <xsl:otherwise>
        <tr>
          <td colspan="{$fullRowColspan}">
            This function returns a 
            <a href="#universal-error">universal error</a>
          </td>
        </tr>
      </xsl:otherwise>
    </xsl:choose>
  </table>
</xsl:template>

<xsl:template match="required" mode="haserrors">
  yes
</xsl:template>

<xsl:template match="required" mode="errors">
  <tr>
    <td>
      <a href="#JVMTI_ERROR_MUST_POSSESS_CAPABILITY">
        <code>
          JVMTI_ERROR_MUST_POSSESS_CAPABILITY
        </code>
      </a>
    </td>
    <td>
      The environment does not possess the capability
      <a>
        <xsl:attribute name="href">#jvmtiCapabilities.<xsl:value-of select="@id"/></xsl:attribute>
        <code>
          <xsl:value-of select="@id"/>
        </code>        
      </a>.
      Use <a href="#AddCapabilities"><code>AddCapabilities</code></a>.
    </td>
  </tr>
</xsl:template>

<xsl:template match="param" mode="haserrors">
  <xsl:apply-templates mode="haserrors"/>
</xsl:template>

<xsl:template match="param" mode="errors">
  <xsl:apply-templates select="." mode="errors1"/>
  <xsl:apply-templates select="." mode="errors2"/>
</xsl:template>

<xsl:template match="param" mode="errors1">
  <xsl:variable name="haserrors">
    <xsl:apply-templates mode="haserrors"/>
  </xsl:variable>
  <xsl:if test="contains($haserrors,'yes')!=0">
    <xsl:variable name="erroridraw">
      <xsl:apply-templates mode="errorid"/>
    </xsl:variable>
    <xsl:variable name="errorid" select="normalize-space($erroridraw)"/>
    <tr>
      <td>
        <a>
          <xsl:attribute name="href">#<xsl:value-of select="$errorid"/></xsl:attribute>
          <code>
            <xsl:value-of select="$errorid"/>
          </code>
        </a>
      </td>
      <td>
        <xsl:apply-templates mode="errordesc">
          <xsl:with-param name="id" select="@id"/>
        </xsl:apply-templates>
      </td>
    </tr>
  </xsl:if>
</xsl:template>
 
<xsl:template match="param" mode="errors2">
  <xsl:variable name="haserrors2">
    <xsl:apply-templates mode="haserrors2"/>
  </xsl:variable>
  <xsl:if test="contains($haserrors2,'yes')!=0">
    <xsl:variable name="erroridraw2">
      <xsl:apply-templates mode="errorid2"/>
    </xsl:variable>
    <xsl:variable name="errorid2" select="normalize-space($erroridraw2)"/>
    <tr>
      <td>
        <a>
          <xsl:attribute name="href">#<xsl:value-of select="$errorid2"/></xsl:attribute>
          <code>
            <xsl:value-of select="$errorid2"/>
          </code>
        </a>
      </td>
      <td>
        <xsl:apply-templates mode="errordesc2">
          <xsl:with-param name="id" select="@id"/>
        </xsl:apply-templates>
      </td>
    </tr>
  </xsl:if>
</xsl:template>
 
<xsl:template match="description" mode="haserrors">
</xsl:template>

<xsl:template match="description" mode="errorid">
</xsl:template>

<xsl:template match="description" mode="errordesc">
</xsl:template>

<xsl:template match="jmethodID|jfieldID|jframeID|jrawMonitorID|jthread|jthreadGroup|jobject|enum|jlocation" mode="haserrors">
  yes
</xsl:template>

<xsl:template match="jclass" mode="haserrors">
  <xsl:if test="count(@method)=0">
    yes
  </xsl:if>
</xsl:template>

<xsl:template match="description|jclass|jfieldID|jrawMonitorID|
                    jthreadGroup|jobject|enum|jlocation|jvalue|jchar|jint|jlong|jfloat|jdouble|jboolean|
                    char|uchar|size_t|void|varargs|struct|
                    ptrtype|outptr|allocbuf|allocallocbuf|inptr|inbuf|outbuf|vmbuf|agentbuf" mode="haserrors2">
</xsl:template>

<xsl:template match="jmethodID" mode="haserrors2">
  <xsl:if test="count(@native)=1 and contains(@native,'error')">
    yes
  </xsl:if>
</xsl:template>

<xsl:template match="jthread" mode="haserrors2">
  <xsl:if test="count(@started)=0 or contains(@started,'yes') or @started=''">
    yes
  </xsl:if>
</xsl:template>

<xsl:template match="jframeID" mode="haserrors2">
    yes
</xsl:template>

<xsl:template match="description" mode="errorid2">
</xsl:template>

<xsl:template match="description" mode="errordesc2">
</xsl:template>

<xsl:template match="jmethodID" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_METHODID</xsl:text>
</xsl:template>

<xsl:template match="jmethodID" mode="errorid2">
    <xsl:text>JVMTI_ERROR_NATIVE_METHOD</xsl:text>
</xsl:template>

<xsl:template match="jmethodID" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a jmethodID.</xsl:text>
</xsl:template>

<xsl:template match="jmethodID" mode="errordesc2">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is a native method.</xsl:text>
</xsl:template>

<xsl:template match="jfieldID" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_FIELDID</xsl:text>
</xsl:template>

<xsl:template match="jfieldID" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a jfieldID.</xsl:text>
</xsl:template>

<xsl:template match="jframeID" mode="errorid">
  <xsl:text>JVMTI_ERROR_ILLEGAL_ARGUMENT</xsl:text>
</xsl:template>

<xsl:template match="jframeID" mode="errorid2">
  <xsl:text>JVMTI_ERROR_NO_MORE_FRAMES</xsl:text>
</xsl:template>

<xsl:template match="jframeID" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is less than zero.</xsl:text>
</xsl:template>

<xsl:template match="jframeID" mode="errordesc2">
  <xsl:param name="id"/>
  <xsl:text>There are no stack frames at the specified </xsl:text>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text>.</xsl:text>
</xsl:template>

<xsl:template match="jrawMonitorID" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_MONITOR</xsl:text>
</xsl:template>

<xsl:template match="jrawMonitorID" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a jrawMonitorID.</xsl:text>
</xsl:template>

<xsl:template match="jclass" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_CLASS</xsl:text>
</xsl:template>

<xsl:template match="jclass" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a class object or the class has been unloaded.</xsl:text>
</xsl:template>

<xsl:template match="jthread" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_THREAD</xsl:text>
</xsl:template>

<xsl:template match="jthread" mode="errorid2">
  <xsl:text>JVMTI_ERROR_THREAD_NOT_ALIVE</xsl:text>
</xsl:template>

<xsl:template match="jthread" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a thread object.</xsl:text>
</xsl:template>

<xsl:template match="jthread" mode="errordesc2">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not live (has not been started or is now dead).</xsl:text>
</xsl:template>

<xsl:template match="jthreadGroup" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_THREAD_GROUP</xsl:text>
</xsl:template>

<xsl:template match="jthreadGroup" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a thread group object.</xsl:text>
</xsl:template>

<xsl:template match="jobject" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_OBJECT</xsl:text>
</xsl:template>

<xsl:template match="jobject" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not an object.</xsl:text>
</xsl:template>

<xsl:template match="enum" mode="errorid">
  <xsl:choose>
    <xsl:when test=".='jvmtiEvent'">
      <xsl:text>JVMTI_ERROR_INVALID_EVENT_TYPE</xsl:text>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>JVMTI_ERROR_ILLEGAL_ARGUMENT</xsl:text>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template match="enum" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a </xsl:text>
  <xsl:value-of select="."/>
  <xsl:text>.</xsl:text>
</xsl:template>

<xsl:template match="jlocation" mode="errorid">
  <xsl:text>JVMTI_ERROR_INVALID_LOCATION</xsl:text>
</xsl:template>

<xsl:template match="jlocation" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is not a valid location.</xsl:text>
</xsl:template>

<xsl:template match="jint" mode="haserrors">
  <xsl:if test="count(@min)=1">
    yes
  </xsl:if>
</xsl:template>

<xsl:template match="jint" mode="errorid">
  <xsl:text>JVMTI_ERROR_ILLEGAL_ARGUMENT</xsl:text>
</xsl:template>

<xsl:template match="jint" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is less than </xsl:text>
  <code><xsl:value-of select="@min"/></code>
  <xsl:text>.</xsl:text>
</xsl:template>

<xsl:template match="jvalue|jchar|jlong|jfloat|jdouble|jboolean|char|uchar|size_t|void|varargs|struct" mode="haserrors">
</xsl:template>

<xsl:template match="jvalue|jchar|jlong|jfloat|jdouble|jboolean|char|uchar|size_t|void|varargs|struct" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:message terminate="yes">
    attempt to get error description for <xsl:apply-templates select="." mode="paramlink"/>
  </xsl:message>
</xsl:template>

<xsl:template match="ptrtype|outptr|allocbuf|allocallocbuf|inptr|inbuf|outbuf|vmbuf|agentbuf" mode="haserrors">
  <xsl:if test="count(nullok)=0">
    yes
  </xsl:if>
</xsl:template>

<xsl:template match="ptrtype|outptr|allocbuf|allocallocbuf|inptr|inbuf|outbuf|vmbuf|agentbuf" mode="errorid">
  <xsl:text>JVMTI_ERROR_NULL_POINTER</xsl:text>
</xsl:template>

<xsl:template match="ptrtype|outptr|allocbuf|allocallocbuf|inptr|inbuf|outbuf|vmbuf|agentbuf" mode="errordesc">
  <xsl:param name="id"/>
  <xsl:if test="count(nullok)=1">
    <xsl:message terminate="yes">
      attempt to get error description in null ok case for <xsl:apply-templates select="." mode="paramlink"/>
    </xsl:message>
  </xsl:if>
  <xsl:apply-templates select="." mode="paramlink"/>
  <xsl:text> is </xsl:text>
  <code>NULL</code>
  <xsl:text>.</xsl:text>
</xsl:template>

<xsl:template match="jmethodID|jfieldID|jframeID|jrawMonitorID|jint|jclass|jthread|jthreadGroup|jobject|enum|jlocation|ptrtype|outptr|allocbuf|allocallocbuf|inptr|inbuf|outbuf|vmbuf|agentbuf" mode="paramlink">
  <a>
    <xsl:attribute name="href">#<xsl:value-of select="ancestor::function/@id|ancestor::event/@id"/>.<xsl:value-of select="ancestor::param/@id"/>
    </xsl:attribute>
    <code>
      <xsl:value-of select="ancestor::param/@id"/>
    </code>        
  </a>
</xsl:template>

<xsl:template match="error" mode="haserrors">
  yes
</xsl:template>

<xsl:template match="error">
  <tr>
    <td>
      <a>
        <xsl:attribute name="href">#<xsl:value-of select="@id"/></xsl:attribute>
        <code>
          <xsl:value-of select="@id"/>
        </code>
      </a>
    </td>
    <td>
      <xsl:apply-templates/>
    </td>
  </tr>
</xsl:template>

<xsl:template match="errorsection">
  <div class="sep"/>
  <hr class="thick"/>
  <h2>
    Errors
  </h2>
  <div class="sep"/>
  <xsl:apply-templates select="intro"/>
  <div class="sep"/>
  <xsl:apply-templates select="errorcategory"/>
  <div class="sep"/>
</xsl:template>

<xsl:template match="datasection">
  <div class="sep"/>
  <hr class="thick"/>
  <h2>
    Data Types
  </h2>
  <div class="sep"/>
  <xsl:apply-templates select="intro"/>
  <xsl:apply-templates select="basetypes"/>
  <div class="sep"/>
  <table id="StructureTypeDefinitions" class="bordered wide">
    <tr class="bgDark">
      <td colspan="2" class="tableHeader">
        Structure Type Definitions
      </td>
    </tr>
    <tr class="bgLight">
      <td>
        Type
      </td>
      <td>
        Description
      </td>
    </tr>
    <xsl:apply-templates select="//typedef|//uniontypedef|//capabilitiestypedef" mode="tableentry">
      <xsl:sort select="@id"/>
    </xsl:apply-templates>
  </table>
  <div class="sep"/>
  <table id="FunctionTypeDefinitions" class="bordered wide">
    <tr class="bgDark">
      <td colspan="2" class="tableHeader">
        Function Type Definitions
      </td>
    </tr>
    <tr class="bgLight">
      <td>
        Type
      </td>
      <td>
        Description
      </td>
    </tr>
    <xsl:apply-templates select="//callback" mode="tableentry">
      <xsl:sort select="@id"/>
    </xsl:apply-templates>
  </table>
  <div class="sep"/>
  <table id="EnumerationDefinitions" class="bordered wide">
    <tr class="bgDark">
      <td colspan="2" class="tableHeader">
        Enumeration Definitions
      </td>
    </tr>
    <tr class="bgLight">
      <td>
        Type
      </td>
      <td>
        Description
      </td>
    </tr>
    <xsl:apply-templates select="//constants[@kind='enum']" mode="tableentry">
      <xsl:sort select="@id"/>
    </xsl:apply-templates>
  </table>
  <div class="sep"/>
  <table id="FunctionTable" class="bordered wide">
    <tr class="bgDark">
      <td colspan="3" class="tableHeader">
        Function Table Layout
      </td>
    </tr>
    <tr class="bgLight">
      <td>
        Position
      </td>
      <td>
        Function
      </td>
      <td>
        Declaration
      </td>
    </tr>
    <xsl:call-template name="funcStruct">
      <xsl:with-param name="funcs" select="//functionsection/category/function[count(@hide)=0]"/>
      <xsl:with-param name="index" select="1"/>
    </xsl:call-template>
  </table>
  <div class="sep"/>
</xsl:template>


<xsl:template name="funcStruct">
  <xsl:param name="funcs"/>
  <xsl:param name="index"/>
  <xsl:variable name="thisFunction" select="$funcs[@num=$index]"/>
  <tr>
    <td class="rightAligned">
      <xsl:number value="$index" format="  1"/>
    </td>
    <xsl:choose>
      <xsl:when test="count($thisFunction)=1">
        <td>
          <a>
            <xsl:attribute name="href">
              <xsl:text>#</xsl:text>
              <xsl:value-of select="$thisFunction/@id"/>
            </xsl:attribute>
            <xsl:value-of select="$thisFunction/synopsis"/>
          </a>
        </td>
        <td>
          <pre>
            <xsl:text>jvmtiError (JNICALL *</xsl:text>
            <xsl:value-of select="$thisFunction/@id"/>
            <xsl:text>) (jvmtiEnv* env</xsl:text>
            <xsl:apply-templates select="$thisFunction/parameters" mode="signature">
              <xsl:with-param name="comma">
                <xsl:text>,&#xA;                       </xsl:text>
              </xsl:with-param>
            </xsl:apply-templates>
            <xsl:text>);</xsl:text>
        </pre>
      </td>
    </xsl:when>
    <xsl:otherwise>
      <xsl:if test="count($thisFunction) != 0">
        <xsl:message terminate="yes">
          More than one function has index number <xsl:number value="$index" format="  1"/>.
      </xsl:message>
      </xsl:if>
      <td>
        <i>reserved</i>
      </td>
      <td>
        <pre>
          <xsl:text>void *reserved</xsl:text>        
          <xsl:value-of select="$index"/>
          <xsl:text>;</xsl:text>
        </pre>
      </td>
    </xsl:otherwise>
  </xsl:choose>
  </tr>
  <xsl:if test="count($funcs[@num &gt; $index]) &gt; 0">
    <xsl:call-template name="funcStruct">
      <xsl:with-param name="funcs" select="$funcs"/>
      <xsl:with-param name="index" select="1+$index"/>
    </xsl:call-template>
  </xsl:if>
</xsl:template>



<xsl:template match="errorcategory">
  <h3>
    <xsl:attribute name="id">
      <xsl:value-of select="@id"/>
    </xsl:attribute>
    <xsl:value-of select="@label"/>
  </h3>
  <xsl:apply-templates select="intro"/>
  <div class="sep"/>
  <dl>
    <xsl:apply-templates select="errorid"/>
  </dl>
  <div class="sep"/>
</xsl:template>

<xsl:template match="errorid">
  <dt>
    <code>
      <xsl:attribute name="id">
        <xsl:value-of select="@id"/>
      </xsl:attribute>
      <xsl:value-of select="@id"/> (<xsl:value-of select="@num"/>)
    </code>
  </dt>
  <dd>
    <xsl:apply-templates/>
    <div class="sep"/>
  </dd>
</xsl:template>

<xsl:template match="changehistory">
    <div class="sep"/>
    <hr class="thick"/>
    <h2>Change History</h2>
    Last update: <xsl:value-of select="@update"/><br/>
    Version: <xsl:call-template name="showversion"/>
    <div class="sep"/>
    <xsl:apply-templates select="intro"/>
    <div class="sep"/>
    <table class="bordered wide">
      <tr class="bgLight">
        <td>
          <b>Version</b><br/>
          <b>Date</b>
        </td>
        <td>
          <b>Changes</b>
        </td>
      </tr>
      <xsl:apply-templates select="change"/>
    </table>
</xsl:template>

<xsl:template match="change">
  <tr>
    <td>
      <xsl:if test="count(@version)">
        <b>
          <xsl:value-of select="@version"/>
        </b>
        <br/>
      </xsl:if>
      <xsl:value-of select="@date"/>
    </td>
    <td>
      <xsl:apply-templates/>
    </td>
  </tr>
</xsl:template>

<xsl:template match="intro">
  <xsl:if test="@id!=''">
    <xsl:choose>
      <xsl:when test="@label!=''">
        <h4>
          <xsl:attribute name="id">
            <xsl:value-of select="@id"/>
          </xsl:attribute>
          <xsl:value-of select="@label"/>
        </h4>
      </xsl:when>
      <xsl:otherwise>
        <a>
          <xsl:attribute name="name">
            <xsl:value-of select="@id"/>
          </xsl:attribute>
        </a>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:if>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="issue">
  <xsl:if test="$development = 'Show'">
    <p style="color: red">
    To be resolved:
      <xsl:apply-templates/>
    </p>
  </xsl:if>
</xsl:template>

<xsl:template match="rationale">
  <p style="color: purple">
  Rationale:
      <xsl:apply-templates/>
  </p>
</xsl:template>

<xsl:template match="todo">
  <xsl:if test="$development = 'Show'">
    <p style="color: green">
    To do:
      <xsl:apply-templates/>
    </p>
  </xsl:if>
</xsl:template>

<xsl:template match="elide">
</xsl:template>

<xsl:template match="b">
  <b>
  <xsl:apply-templates/>
  </b>
</xsl:template>

<xsl:template match="example">
  <blockquote>
    <pre>
      <xsl:apply-templates/>
    </pre>
  </blockquote>
</xsl:template>


<xsl:template match="table">
  <table class="bordered wide">
    <xsl:apply-templates/>
  </table>
</xsl:template>

<xsl:template match="tr">
  <tr>
    <xsl:apply-templates/>
  </tr>
</xsl:template>

<xsl:template match="td">
  <td>
    <xsl:if test="@class">
      <xsl:attribute name="class">
        <xsl:value-of select="@class"/>
      </xsl:attribute>
    </xsl:if>
    <xsl:apply-templates/>
  </td>
</xsl:template>

<xsl:template match="th">
  <th>
    <xsl:if test="@class">
      <xsl:attribute name="class">
        <xsl:value-of select="@class"/>
      </xsl:attribute>
    </xsl:if>
    <xsl:apply-templates/>
  </th>
</xsl:template>

<xsl:template match="dl">
  <dl>
    <xsl:apply-templates/>
  </dl>
</xsl:template>

<xsl:template match="dt">
  <dt>
    <xsl:apply-templates/>
  </dt>
</xsl:template>

<xsl:template match="dd">
  <dd>
    <xsl:apply-templates/>
  </dd>
</xsl:template>

<xsl:template match="blockquote">
  <blockquote>
    <xsl:apply-templates/>
  </blockquote>
</xsl:template>

<xsl:template match="p">
  <div class="sep"/>
</xsl:template>

<xsl:template match="br">
  <br>
    <xsl:apply-templates/>
  </br>
</xsl:template>

<xsl:template match="ul">
  <ul>
    <xsl:attribute name="style">list-style-type:<xsl:value-of select="@type"/></xsl:attribute>
    <xsl:apply-templates/>
  </ul>
</xsl:template>

<xsl:template match="li">
  <li>
    <xsl:apply-templates/>
  </li>
</xsl:template>

<xsl:template match="code">
  <code>
    <xsl:apply-templates/>
  </code>
</xsl:template>

<xsl:template match="tm">
  <xsl:apply-templates/>
  <sup style="font-size: xx-small">
    <xsl:text>TM</xsl:text>
  </sup>
  <xsl:text>&#032;</xsl:text>
</xsl:template>

<xsl:template match="b">
  <b>
    <xsl:apply-templates/>
  </b>
</xsl:template>

<xsl:template match="i">
  <i>
    <xsl:apply-templates/>
  </i>
</xsl:template>

<xsl:template match="space">
  <xsl:text>&#032;</xsl:text>
</xsl:template>

<xsl:template match="jvmti">
  <xsl:text>JVM</xsl:text><small style="font-size: xx-small">&#160;</small><xsl:text>TI</xsl:text>
</xsl:template>


</xsl:stylesheet>
