<?xml version="1.0" encoding="utf-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ext="http://nwalsh.com/xslt"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                exclude-result-prefixes="#all"
                version="3.0">

<xsl:output method="xml" encoding="utf-8" indent="no"/>

<xsl:mode on-no-match="shallow-copy"/>

<xsl:template match="/">
  <xsl:variable name="markdown" as="xs:string">This is *bold*.
```
This is a pre.
```
This is ~~more~~ _text_.</xsl:variable>

  <doc>
    <xdm>
      <xsl:sequence select="ext:commonmark($markdown,
                            map {xs:QName('ext:parser'): 'html'})"/>
    </xdm>
  </doc>
</xsl:template>

</xsl:stylesheet>
