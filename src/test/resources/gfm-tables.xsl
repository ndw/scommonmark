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

| Col 1 | Col 2  |
| ----- | ------ |
| This  | is     | 
| a     | GitHub |
| table |        |

This is ~~more~~ _text_.</xsl:variable>
  <doc>
    <xsl:sequence
        select="ext:commonmark($markdown,
                map{
                  xs:QName('ext:parser'): 'none',
                  xs:QName('ext:extensions'):
                    ('org.commonmark.ext.gfm.tables.TablesExtension',
                     'org.commonmark.ext.gfm.strikethrough.StrikethroughExtension')})"/>
  </doc>
</xsl:template>

</xsl:stylesheet>
