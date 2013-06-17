<?xml version="1.0" encoding="utf-8"?>
<!--
 Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

<!-- utilities used when generating code -->

<xsl:variable name="newline">
  <xsl:text>&#xA;</xsl:text>
</xsl:variable>

<xsl:variable name="indent1">
  <xsl:text>&#xA;  </xsl:text>
</xsl:variable>

<xsl:variable name="indent2">
  <xsl:text>&#xA;    </xsl:text>
</xsl:variable>

<xsl:variable name="indent3">
  <xsl:text>&#xA;      </xsl:text>
</xsl:variable>

<xsl:variable name="indent4">
  <xsl:text>&#xA;        </xsl:text>
</xsl:variable>

<xsl:variable name="quote">
  <xsl:text>"</xsl:text>
</xsl:variable>

<xsl:template name="file-header">
  <xsl:text>/* AUTOMATICALLY GENERATED FILE - DO NOT EDIT */</xsl:text>
</xsl:template>

<xsl:template name="string-replace-all">
  <xsl:param name="text" />
  <xsl:param name="replace" />
  <xsl:param name="by" />
  <xsl:choose>
    <xsl:when test="contains($text, $replace)">
      <xsl:value-of select="substring-before($text,$replace)" />
      <xsl:value-of select="$by" />
      <xsl:call-template name="string-replace-all">
        <xsl:with-param name="text" select="substring-after($text,$replace)" />
        <xsl:with-param name="replace" select="$replace" />
        <xsl:with-param name="by" select="$by" />
      </xsl:call-template>
    </xsl:when>
    <xsl:otherwise>
      <xsl:value-of select="$text" />
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>


</xsl:stylesheet>
