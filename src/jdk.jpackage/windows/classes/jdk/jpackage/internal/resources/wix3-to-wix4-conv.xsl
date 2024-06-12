<?xml version="1.0" encoding="utf-8"?>
<!--
/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
-->

<!--
  This stylesheet can be applied to Wix3 .wxl, .wxs, and .wsi source files.
-->
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:wix3loc="http://schemas.microsoft.com/wix/2006/localization"
                xmlns:wix3="http://schemas.microsoft.com/wix/2006/wi"
>
  <!-- Wix4 complains about xml declaration in input files. Turn it off -->
  <xsl:output method="xml" omit-xml-declaration="yes" indent="yes"/>

  <!--
    Remap xmlns="http://schemas.microsoft.com/wix/2006/localization"
    to xmlns="http://wixtoolset.org/schemas/v4/wxl"
  -->

  <xsl:template match="wix3loc:*">
    <xsl:element name="{local-name()}" namespace="http://wixtoolset.org/schemas/v4/wxl">
      <xsl:apply-templates select="@*|node()"/>
    </xsl:element>
  </xsl:template>

  <!--
    Remap xmlns="http://schemas.microsoft.com/wix/2006/localization"
    to xmlns="http://wixtoolset.org/schemas/v4/wxs"
  -->

  <xsl:template match="wix3:*">
    <xsl:element name="{local-name()}" namespace="http://wixtoolset.org/schemas/v4/wxs">
      <xsl:apply-templates select="@*|node()"/>
    </xsl:element>
  </xsl:template>


  <!--
    From <String Id="foo">Bar</String> to <String Id="foo" Value="Bar"/>
  -->
  <xsl:template match="wix3loc:WixLocalization/wix3loc:String">
    <xsl:element name="{local-name()}" namespace="http://wixtoolset.org/schemas/v4/wxl">
      <xsl:attribute name="Value">
        <xsl:value-of select="text()"/>
      </xsl:attribute>
      <xsl:apply-templates select="@*"/>
    </xsl:element>
  </xsl:template>


  <!--
  Wix3 Product (https://wixtoolset.org/docs/v3/xsd/wix/product/):
    Id
    Codepage
    Language
    Manufacturer
    Name
    UpgradeCode
    Version

  Wix3 Package (https://wixtoolset.org/docs/v3/xsd/wix/package/):
    AdminImage
    Comments
    Compressed
    Description
    Id
    InstallerVersion
    InstallPrivileges
    InstallScope
    Keywords
    Languages
    Manufacturer
    Platform
    Platforms
    ReadOnly
    ShortNames
    SummaryCodepage

  Wix4 Package (https://wixtoolset.org/docs/schema/wxs/package/):
    Codepage          <- Wix3:Product/@Codepage
    Compressed        <- Wix3:@Compressed
    InstallerVersion  <- Wix3:@InstallerVersion
    Language          <- Wix3:Product/@Language
    Manufacturer      <- Wix3:Product/@Manufacturer
    Name              <- Wix3:Product/@Name
    ProductCode       <- Wix3:Product/@Id
    Scope             <- Wix3:@InstallScope
    ShortNames        <- Wix3:@ShortNames
    UpgradeCode       <- Wix3:Product/@UpgradeCode
    UpgradeStrategy   <-
    Version           <- Wix3:Product/@Version

  Wix4 SummaryInformation (https://wixtoolset.org/docs/schema/wxs/summaryinformation/):
    Codepage          <- Wix3:Product/@Codepage
    Comments          <- Wix3:@Comments
    Description       <- Wix3:@Description
    Keywords          <- Wix3:@Keywords
    Manufacturer      <- Wix3:Product/@Manufacturer
  -->

  <xsl:template match="wix3:Product">
    <xsl:element name="Package" namespace="http://wixtoolset.org/schemas/v4/wxs">
      <xsl:apply-templates select="@Codepage|wix3:Package/@Compressed|wix3:Package/@InstallerVersion|@Language|@Manufacturer|@Name|@Id|wix3:Package/@InstallScope|wix3:Package/@ShortNames|@UpgradeCode|@Version"/>
      <xsl:if test="@Id">
        <xsl:attribute name="ProductCode">
          <xsl:value-of select="@Id"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:if test="wix3:Package/@InstallScope">
        <xsl:attribute name="Scope">
          <xsl:value-of select="wix3:Package/@InstallScope"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:element name="SummaryInformation" namespace="http://wixtoolset.org/schemas/v4/wxs">
        <xsl:apply-templates select="@Codepage|wix3:Package/@Comments|wix3:Package/@Description|wix3:Package/@Keywords|@Manufacturer"/>
      </xsl:element>
      <xsl:apply-templates select="node()"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="wix3:Package|wix3:Product/@Id|wix3:Package/@InstallScope"/>


  <xsl:template match="wix3:CustomAction/@BinaryKey">
    <xsl:attribute name="BinaryRef">
      <xsl:value-of select="."/>
    </xsl:attribute>
  </xsl:template>


  <xsl:template match="wix3:Custom|wix3:Publish">
    <xsl:element name="{local-name()}" namespace="http://wixtoolset.org/schemas/v4/wxs">
      <xsl:apply-templates select="@*"/>
      <xsl:if test="text()">
        <xsl:attribute name="Condition">
          <xsl:value-of select="text()"/>
        </xsl:attribute>
      </xsl:if>
      <xsl:apply-templates select="node()"/>
    </xsl:element>
  </xsl:template>

  <xsl:template match="wix3:Custom/text()|wix3:Publish/text()"/>


  <xsl:template match="wix3:Directory[@Id='TARGETDIR']"/>


  <!--
    Identity transform
  -->
  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
