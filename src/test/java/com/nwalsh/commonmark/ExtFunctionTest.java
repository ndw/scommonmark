package com.nwalsh.commonmark;

import junit.framework.TestCase;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class ExtFunctionTest {
    private XsltCompiler xsltCompiler = null;
    private DocumentBuilder builder = null;

    @Before
    public void setUp() {
        Processor processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();
        config.registerExtensionFunction(new CommonMarkFunction());
        xsltCompiler = processor.newXsltCompiler();
        builder = processor.newDocumentBuilder();
    }

    @Test
    public void testCommonMark() {
        try {
            XdmNode node = applyStylesheet("src/test/resources/style.xsl", "src/test/resources/style.xsl");
            String text = node.getStringValue();
            Assert.assertFalse(text.contains("<del>more</del>"));
            Assert.assertTrue(text.contains("<em>bold"));
            Assert.assertTrue(text.contains("<pre><code>This"));
        } catch (SaxonApiException | FileNotFoundException sae) {
            sae.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testGfmExtensions() {
        try {
            XdmNode node = applyStylesheet("src/test/resources/gfm-tables.xsl", "src/test/resources/gfm-tables.xsl");
            String text = node.getStringValue();
            Assert.assertTrue(text.contains("<table>"));
            Assert.assertTrue(text.contains("<del>more</del>"));
            Assert.assertTrue(text.contains("<em>bold"));
            Assert.assertTrue(text.contains("<pre><code>This"));
        } catch (SaxonApiException | FileNotFoundException sae) {
            sae.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testXmlParser() {
        try {
            XdmNode node = applyStylesheet("src/test/resources/style-xml.xsl", "src/test/resources/style-xml.xsl");
            String text = node.toString();
            Assert.assertFalse(text.contains("<html xmlns"));
            Assert.assertTrue(text.contains("<div xmlns"));
            Assert.assertTrue(text.contains("<em>text</em>"));
        } catch (SaxonApiException | FileNotFoundException sae) {
            sae.printStackTrace();
            TestCase.fail();
        }
    }

    @Test
    public void testHtmlParser() {
        try {
            XdmNode node = applyStylesheet("src/test/resources/style-html.xsl", "src/test/resources/style-html.xsl");
            String text = node.toString();
            Assert.assertTrue(text.contains("<html xmlns"));
            Assert.assertFalse(text.contains("<div xmlns"));
            Assert.assertTrue(text.contains("<em>text</em>"));
        } catch (SaxonApiException | FileNotFoundException sae) {
            sae.printStackTrace();
            TestCase.fail();
        }
    }

    private XdmNode applyStylesheet(String stylesheet, String document) throws SaxonApiException, FileNotFoundException {
        File ssfile = new File(stylesheet);
        File inputfile = new File(document);
        XdmNode context = builder.build(inputfile);

        RawDestination result = new RawDestination();
        XsltExecutable xsltExec = xsltCompiler.compile(new SAXSource(new InputSource(new FileInputStream(ssfile))));
        XsltTransformer transformer = xsltExec.load();
        transformer.setDestination(result);
        transformer.setInitialContextNode(context);
        transformer.transform();
        XdmValue value = result.getXdmValue();

        if (value instanceof XdmNode) {
            return (XdmNode) value;
        } else {
            throw new RuntimeException("Value returned where node was expected");
        }
    }
}
