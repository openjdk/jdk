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
<xsl:output method="text" indent="no" omit-xml-declaration="yes"/>
<xsl:import href="xsl_util.xsl"/>

<xsl:template match="/">
  <xsl:call-template name="file-header"/>

#ifndef TRACEFILES_JFREVENTIDS_HPP
#define TRACEFILES_JFREVENTIDS_HPP

#include "utilities/macros.hpp"

#if INCLUDE_TRACE

#include "trace/traceDataTypes.hpp"

/**
 * Enum of the event types in the JVM
 */
enum TraceEventId {
  _traceeventbase = (NUM_RESERVED_EVENTS-1), // Make sure we start at right index.
  
  // Events -> enum entry
<xsl:for-each select="trace/events/event">
  <xsl:value-of select="concat('  Trace', @id, 'Event,', $newline)"/>
</xsl:for-each>
  MaxTraceEventId
};

/**
 * Struct types in the JVM
 */
enum TraceStructId {
<xsl:for-each select="trace/types/content_types/*">
  <xsl:value-of select="concat('  Trace', @id, 'Struct,', $newline)"/>
</xsl:for-each>
<xsl:for-each select="trace/events/*">
  <xsl:value-of select="concat('  Trace', @id, 'Struct,', $newline)"/>
</xsl:for-each>
  MaxTraceStructId
};

typedef enum TraceEventId  TraceEventId;
typedef enum TraceStructId TraceStructId;

#endif
#endif
</xsl:template>

</xsl:stylesheet>
