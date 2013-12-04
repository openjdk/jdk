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
<xsl:import href="xsl_util.xsl"/>
<xsl:output method="text" indent="no" omit-xml-declaration="yes"/>

<xsl:template match="/">
  <xsl:call-template name="file-header"/>

#ifndef TRACEFILES_JFRTYPES_HPP
#define TRACEFILES_JFRTYPES_HPP

#include "oops/symbol.hpp"
#include "trace/traceDataTypes.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"


enum JVMContentType {
  _not_a_content_type = (JVM_CONTENT_TYPES_START - 1),
  
<xsl:for-each select="trace/types/content_types/content_type[@jvm_type]">
  <xsl:value-of select="concat('  CONTENT_TYPE_', @jvm_type, ',',  $newline)"/>
</xsl:for-each>
  NUM_JVM_CONTENT_TYPES
};


enum JVMEventRelations {
  JVM_REL_NOT_AVAILABLE = 0,
  
<xsl:for-each select="trace/relation_decls/relation_decl">
  <xsl:value-of select="concat('  JVM_REL_', @id, ',', $newline)"/>
</xsl:for-each>
  NUM_EVENT_RELATIONS
};

/**
 * Create typedefs for the JRA types:
 *   typedef s8 TYPE_LONG;
 *   typedef s4 TYPE_INTEGER;
 *   typedef const char * TYPE_STRING;
 *   ...
 */
<xsl:for-each select="trace/types/primary_types/primary_type">
typedef <xsl:value-of select="@type"/>  TYPE_<xsl:value-of select="@symbol"/>;
</xsl:for-each>

#endif // JFRFILES_JFRTYPES_HPP
</xsl:template>

</xsl:stylesheet>
