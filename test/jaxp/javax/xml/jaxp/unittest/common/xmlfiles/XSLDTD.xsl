<?xml version="1.1" encoding="UTF-8" standalone="no"?>
<!DOCTYPE HTMLlat1 SYSTEM "XSLDTD.dtd">
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: copy20 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.3 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test copy-of a string constant containing character entity -->

<xsl:output method="xml" encoding="UTF-8"/>
<!-- With this output encoding, should get two bytes (xC3,xA6) for the &aelig -->

<xsl:template match="/">
  <out>
    <xsl:copy-of select="'abcd&aelig;fgh'"/>
  </out>
</xsl:template>

</xsl:stylesheet>
