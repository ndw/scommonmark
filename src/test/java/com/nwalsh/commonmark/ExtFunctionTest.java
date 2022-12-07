package com.nwalsh.commonmark;

import junit.framework.TestCase;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.*;
import org.junit.Test;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class ExtFunctionTest extends TestCase {
    private Processor processor = null;
    private XsltCompiler xsltCompiler = null;
    private XPathCompiler xpathCompiler = null;
    private DocumentBuilder builder = null;

    @Override
    public void setUp() {
        processor = new Processor(false);
        Configuration config = processor.getUnderlyingConfiguration();
        config.registerExtensionFunction(new CommonMarkFunction());
        xsltCompiler = processor.newXsltCompiler();
        xpathCompiler = processor.newXPathCompiler();
        builder = processor.newDocumentBuilder();
    }

    @Test
    public void testExtension() {
        try {
            XdmNode node = applyStylesheet("src/test/resources/style.xsl", "src/test/resources/style.xsl");
            String text = node.getStringValue();
            assertTrue(text.contains("<em>bold"));
            assertTrue(text.contains("<pre><code>This"));
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
