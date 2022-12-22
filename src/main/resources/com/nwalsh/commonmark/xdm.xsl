<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="xs"
                version="3.0">

<xsl:param name="html-string" as="xs:string"/>

<xsl:output method="xml" indent="no"/>

<xsl:mode on-no-match="shallow-copy"/>

<xsl:template name="main">
  <xsl:apply-templates select="parse-xml($html-string)"/>
</xsl:template>

<xsl:template match="*">
  <xsl:choose>
    <xsl:when test="namespace-uri(.) = ''">
      <xsl:element name="{local-name(.)}" namespace="http://www.w3.org/1999/xhtml">
        <xsl:apply-templates/>
      </xsl:element>
    </xsl:when>
    <xsl:otherwise>
      <xsl:next-match/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

</xsl:stylesheet>
