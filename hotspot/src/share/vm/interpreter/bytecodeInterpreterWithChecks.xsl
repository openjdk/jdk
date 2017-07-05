<?xml version="1.0"?> 
<!-- 
     Copyright 2006 Sun Microsystems, Inc.  All rights reserved.
     SUN PROPRIETARY/CONFIDENTIAL.  Use is subject to license terms.
-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:template match="processcode">
<xsl:text>
#define VM_JVMTI
#include "bytecodeInterpreter.cpp"
</xsl:text>
<xsl:text disable-output-escaping = "yes">

</xsl:text>

<xsl:output method="text" indent="no" omit-xml-declaration="yes"/>
</xsl:template>

</xsl:stylesheet>
