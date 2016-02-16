<?xml version="1.0" encoding="utf-8"?>
<!--
 Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

#ifndef TRACEFILES_TRACEEVENTCLASSES_HPP
#define TRACEFILES_TRACEEVENTCLASSES_HPP

// On purpose outside the INCLUDE_TRACE
// Some parts of traceEvent.hpp are used outside of
// INCLUDE_TRACE

#include "tracefiles/traceTypes.hpp"
#include "trace/traceEvent.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"
#if INCLUDE_TRACE
#include "trace/traceStream.hpp"
#include "utilities/ostream.hpp"

  <xsl:apply-templates select="trace/events/struct" mode="trace"/>
  <xsl:apply-templates select="trace/events/event" mode="trace"/>

#else // !INCLUDE_TRACE

class TraceEvent {
public:
  TraceEvent() {}
  void set_starttime(const Ticks&amp; time) {}
  void set_endtime(const Ticks&amp; time) {}
  bool should_commit() const { return false; }
  static bool is_enabled() { return false; }
  void commit() const {}
};

  <xsl:apply-templates select="trace/events/struct" mode="empty"/>
  <xsl:apply-templates select="trace/events/event" mode="empty"/>

#endif // INCLUDE_TRACE
#endif // TRACEFILES_TRACEEVENTCLASSES_HPP
</xsl:template>

<xsl:template match="struct" mode="trace">
struct TraceStruct<xsl:value-of select="@id"/>
{
private:
<xsl:apply-templates select="value" mode="write-fields"/>
public:
<xsl:apply-templates select="value" mode="write-setters"/>

  void writeStruct(TraceStream&amp; ts) {
<xsl:apply-templates select="value" mode="write-data"/>
  }
};

</xsl:template>

<xsl:template match="struct" mode="empty">
struct TraceStruct<xsl:value-of select="@id"/> 
{
public:
<xsl:apply-templates select="value" mode="write-empty-setters"/>
};
</xsl:template>


<xsl:template match="event" mode="empty">
  <xsl:value-of select="concat('class Event', @id, ' : public TraceEvent')"/>
{
 public:
<xsl:value-of select="concat('  Event', @id, '(bool ignore=true) {}')"/>
<xsl:text>
</xsl:text>

<xsl:apply-templates select="value|structvalue|transition_value|relation" mode="write-empty-setters"/>
};

</xsl:template>


<xsl:template match="event" mode="trace">
  <xsl:value-of select="concat('class Event', @id, ' : public TraceEvent&lt;Event', @id, '&gt;')"/>
{
 public:
  static const bool hasThread = <xsl:value-of select="@has_thread"/>;
  static const bool hasStackTrace = <xsl:value-of select="@has_stacktrace"/>;
  static const bool isInstant = <xsl:value-of select="@is_instant"/>;
  static const bool isRequestable = <xsl:value-of select="@is_requestable"/>;
  static const TraceEventId eventId = <xsl:value-of select="concat('Trace', @id, 'Event')"/>;

 private:
<xsl:apply-templates select="value|structvalue|transition_value|relation" mode="write-fields"/>

  void writeEventContent(void) {
    TraceStream ts(*tty);
    ts.print("<xsl:value-of select="@label"/>: [");
<xsl:apply-templates select="value|structvalue" mode="write-data"/>
    ts.print("]\n");
  }

 public:
<xsl:apply-templates select="value|structvalue|transition_value|relation" mode="write-setters"/>

  bool should_write(void) {
    return true;
  }
<xsl:text>

</xsl:text>
  <xsl:value-of select="concat('  Event', @id, '(EventStartTime timing=TIMED) : TraceEvent&lt;Event', @id, '&gt;(timing) {}', $newline)"/>
  void writeEvent(void) {
    if (UseLockedTracing) {
      ttyLocker lock;
      writeEventContent();
    } else {
      writeEventContent();
    }
  }
};

</xsl:template>

<xsl:template match="value|transition_value|relation" mode="write-empty-setters">
  <xsl:param name="cls"/>
  <xsl:variable name="type" select="@type"/>
  <xsl:variable name="wt" select="//primary_type[@symbol=$type]/@type"/>
  <xsl:value-of select="concat('  void set_', @field, '(', $wt, ' value) { }')"/>
  <xsl:if test="position() != last()">
    <xsl:text>
</xsl:text>
  </xsl:if>
</xsl:template>

<xsl:template match="structvalue" mode="write-empty-setters">
  <xsl:param name="cls"/>
  <xsl:value-of select="concat('  void set_', @field, '(const TraceStruct', @type, '&amp; value) { }')"/>
  <xsl:if test="position() != last()">
    <xsl:text>
</xsl:text>
  </xsl:if>
</xsl:template>

<xsl:template match="value[@type='TICKS']" mode="write-setters">
#if INCLUDE_TRACE
<xsl:value-of select="concat('  void set_', @field, '(const Ticks&amp; time) { _', @field, ' = time; }')"/>
#else
<xsl:value-of select="concat('  void set_', @field, '(const Ticks&amp; ignore) {}')"/>
#endif
</xsl:template>

<xsl:template match="value[@type='TICKSPAN']" mode="write-setters">
#if INCLUDE_TRACE
  <xsl:value-of select="concat('  void set_', @field, '(const Tickspan&amp; time) { _', @field, ' = time; }')"/>
#else
  <xsl:value-of select="concat('  void set_', @field, '(const Tickspan&amp; ignore) {}')"/>
#endif
</xsl:template>


<xsl:template match="value" mode="write-fields">
  <xsl:variable name="type" select="@type"/>
  <xsl:variable name="wt" select="//primary_type[@symbol=$type]/@type"/>
  <xsl:value-of select="concat('  ', $wt, ' _', @field, ';')"/>
  <xsl:if test="position() != last()">
    <xsl:text> 
</xsl:text>
  </xsl:if>
</xsl:template>

<xsl:template match="structvalue" mode="write-fields">
  <xsl:value-of select="concat('  TraceStruct', @type, ' _', @field, ';')"/>
  <xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="value|transition_value|relation" mode="write-setters">
  <xsl:param name="cls"/>
  <xsl:variable name="type" select="@type"/>
  <xsl:variable name="wt" select="//primary_type[@symbol=$type]/@type"/>
  <xsl:value-of select="concat('  void set_', @field, '(', $wt, ' value) { this->_', @field, ' = value; }')"/>
  <xsl:if test="position() != last()">
    <xsl:text>
</xsl:text>
  </xsl:if>
</xsl:template>

<xsl:template match="structvalue" mode="write-setters">
  <xsl:param name="cls"/>
  <xsl:value-of select="concat('  void set_', @field, '(const TraceStruct', @type, '&amp; value) { this->_', @field, ' = value; }')"/>
  <xsl:if test="position() != last()">
    <xsl:text>
</xsl:text>
  </xsl:if>
</xsl:template>

<xsl:template match="value" mode="write-data">
  <xsl:variable name="type" select="@type"/>
  <xsl:variable name="wt" select="//primary_type[@symbol=$type]/@writetype"/>
  <xsl:choose>
    <xsl:when test="@type='TICKSPAN'">
      <xsl:value-of select="concat('    ts.print_val(&quot;', @label, '&quot;, _', @field, '.value());')"/>
    </xsl:when>
    <xsl:when test="@type='TICKS'">
      <xsl:value-of select="concat('    ts.print_val(&quot;', @label, '&quot;, _', @field, '.value());')"/>
    </xsl:when>
    <xsl:otherwise>
      <xsl:value-of select="concat('    ts.print_val(&quot;', @label, '&quot;, _', @field, ');')"/>
    </xsl:otherwise>
  </xsl:choose>
  <xsl:if test="position() != last()">
    <xsl:text>
    ts.print(", ");
</xsl:text>
  </xsl:if>
</xsl:template>

<xsl:template match="structvalue" mode="write-data">
  <xsl:value-of select="concat('    _', @field, '.writeStruct(ts);')"/>
  <xsl:if test="position() != last()">
    <xsl:text>
    ts.print(", ");
</xsl:text>
  </xsl:if>
</xsl:template>

</xsl:stylesheet>
