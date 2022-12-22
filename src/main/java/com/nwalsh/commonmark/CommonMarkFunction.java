package com.nwalsh.commonmark;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ErrorReporter;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.*;
import net.sf.saxon.value.SequenceType;
import nu.validator.htmlparser.common.XmlViolationPolicy;
import nu.validator.htmlparser.dom.HtmlDocumentBuilder;
import org.commonmark.Extension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.SourceLocator;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class CommonMarkFunction extends ExtensionFunctionDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nwalsh.com/xslt", "commonmark");

    private static final QName _escape_html = new QName("", "escape-html");
    private static final QName _sanitize_urls = new QName("", "sanitize-urls");
    private static final QName ext_extensions = new QName("ext", "http://nwalsh.com/xslt", "extensions");
    private static final QName ext_parser = new QName("ext", "http://nwalsh.com/xslt", "parser");

    HashMap<QName,List<String>> options = new HashMap<>();

    @Override
    public StructuredQName getFunctionQName() {
        return qName;
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 1;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.SINGLE_STRING, SequenceType.OPTIONAL_ITEM};
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_ITEM;
    }

    public ExtensionFunctionCall makeCallExpression() {
        return new CommonMarkFunction.PropertiesCall();
    }

    private class PropertiesCall extends ExtensionFunctionCall {
        public Sequence call(XPathContext xpathContext, Sequence[] sequences) throws XPathException {
            String markdown = sequences[0].head().getStringValue();

            if (sequences.length > 1) {
                Item item = sequences[1].head();
                if (item != null) {
                    if (item instanceof MapItem) {
                        options = parseMap((MapItem) item);
                    } else {
                        throw new IllegalArgumentException("ext:commonmark options parameter must be a map");
                    }
                }
            }

            boolean escape = getBooleanOption(_escape_html, false);
            boolean sanitize = getBooleanOption(_sanitize_urls, false);
            String useParser = getStringOption(ext_parser, "xml");

            if (!"xml".equals(useParser) && !"html".equals(useParser) && !"none".equals(useParser)) {
                throw new IllegalArgumentException("Parser option cannot be " + useParser);
            }

            ArrayList<Extension> extensions = new ArrayList<>();
            String className = null;
            try {
                if (options.containsKey(ext_extensions)) {
                    for (String klass : options.get(ext_extensions)) {
                        className = klass;
                        Class<?> clazz = Class.forName(klass);
                        Method create = clazz.getMethod("create");
                        extensions.add((Extension) create.invoke(null));
                    }
                }
            } catch (ClassNotFoundException|NoSuchMethodException|IllegalAccessException|InvocationTargetException ex) {
                throw new IllegalArgumentException("Failed to instantiate: " + className);
            }

            Parser parser = Parser.builder().extensions(extensions).build();
            Node document = parser.parse(markdown);
            HtmlRenderer.Builder builder = HtmlRenderer.builder();
            builder.escapeHtml(escape);
            builder.sanitizeUrls(sanitize);
            HtmlRenderer renderer = builder.extensions(extensions).build();
            String html = renderer.render(document);

            if ("none".equals(useParser)) {
                return new StringValue(html);
            } else {
                Processor processor = (Processor) xpathContext.getConfiguration().getProcessor();

                if ("xml".equals(useParser)) {
                    try {
                        // Suppress error output this time
                        XdmValue value = parseHtml(processor, html, new VoidReporter());
                        return value.getUnderlyingValue();
                    } catch (SaxonApiException ex) {
                        try {
                            XdmValue value = parseHtml(processor, "<div>" + html + "</div>", null);
                            return value.getUnderlyingValue();
                        } catch (SaxonApiException ex2) {
                            throw new IllegalArgumentException("Parsing CommonMark output as XML failed: " + ex2.getMessage());
                        }
                    }
                } else {
                    try {
                        HtmlDocumentBuilder htmlBuilder = new HtmlDocumentBuilder(XmlViolationPolicy.ALTER_INFOSET);
                        ByteArrayInputStream bais = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
                        Document dom = htmlBuilder.parse(bais);
                        DocumentBuilder documentBuilder = processor.newDocumentBuilder();
                        XdmNode doc = documentBuilder.build(new DOMSource(dom));
                        return doc.getUnderlyingValue();
                    } catch (SAXException| IOException|SaxonApiException ex) {
                        throw new IllegalArgumentException("Parsing CommonMark output as HTML failed: " + ex.getMessage());
                    }
                }
            }
        }
    }

    private XdmValue parseHtml(Processor processor, String html, ErrorReporter reporter) throws SaxonApiException {
        XsltCompiler xsltCompiler = processor.newXsltCompiler();
        RawDestination result = new RawDestination();

        InputStream xslstream = this.getClass().getResourceAsStream("/com/nwalsh/commonmark/xdm.xsl");
        HashMap<QName, XdmValue> params = new HashMap<>();
        params.put(new QName("", "html-string"), new XdmAtomicValue(html));

        XsltExecutable xsltExec = xsltCompiler.compile(new SAXSource(new InputSource(xslstream)));
        Xslt30Transformer transformer = xsltExec.load30();
        transformer.setStylesheetParameters(params);

        if (reporter != null) {
            transformer.setErrorReporter(reporter);
        }

        transformer.callTemplate(new QName("", "main"), result);
        return result.getXdmValue();
    }

    private boolean getBooleanOption(QName name, boolean defvalue) {
        String value = getStringOption(name, defvalue ? "true" : "false");
        if ("true".equals(value) || "false".equals(value)) {
            return "true".equals(value);
        }
        if ("1".equals(value) || "0".equals(value)) {
            return "1".equals(value);
        }
        if ("yes".equals(value) || "no".equals(value)) {
            return "yes".equals(value);
        }
        throw new IllegalArgumentException("Boolean option " + name + " cannot be " + value);
    }

    private String getStringOption(QName name, String defvalue) {
        if (options.containsKey(name) && options.get(name).size() > 0) {
            return options.get(name).get(0);
        }
        return defvalue;
    }

    private HashMap<QName, List<String>> parseMap(MapItem item) throws XPathException {
        HashMap<QName,List<String>> options = new HashMap<>();

        // The implementation of the keyValuePairs() method is incompatible between Saxon 10 and Saxon 11.
        // In order to avoid having to publish two versions of this class, we use reflection to
        // work it out at runtime. (Insert programmer barfing on his shoes emoji here.)
        try {
            Method keys = MapItem.class.getMethod("keys");
            Method get = MapItem.class.getMethod("get", AtomicValue.class);
            AtomicIterator aiter = (AtomicIterator) keys.invoke(item);
            AtomicValue next = aiter.next();
            while (next != null) {
                final QName key;
                if (next.getItemType() == BuiltInAtomicType.QNAME) {
                    QNameValue qkey = (QNameValue) next;
                    key = new QName(qkey.getPrefix(), qkey.getNamespaceURI(), qkey.getLocalName());
                } else {
                    throw new IllegalArgumentException("Option map keys must be QNames");
                }

                ArrayList<String> values = new ArrayList<>();
                Object rawValue = get.invoke(item, next);
                if (rawValue instanceof SequenceExtent) {
                    SequenceExtent seValue = (SequenceExtent) rawValue;
                    for (int pos = 0; pos < seValue.getLength(); pos++) {
                        AtomicValue value = (AtomicValue) seValue.itemAt(pos);
                        values.add(value.getStringValue());
                    }
                } else {
                    AtomicValue value = (AtomicValue) rawValue;
                    values.add(value.getStringValue());
                }
                options.put(key, values);
                next = aiter.next();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException("Failed to resolve MapItem with reflection");
        }

        return options;
    }

    public static class VoidReporter implements ErrorReporter {
        @Override
        public void report(XmlProcessingError xmlProcessingError) {
            // nop
        }
    }

}
