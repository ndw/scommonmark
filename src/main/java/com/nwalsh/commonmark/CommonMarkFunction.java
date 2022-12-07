package com.nwalsh.commonmark;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

public class CommonMarkFunction extends ExtensionFunctionDefinition {
    private static final StructuredQName qName =
            new StructuredQName("", "http://nwalsh.com/xslt", "commonmark");

    private static final QName _escape_html = new QName("", "escape-html");
    private static final QName _sanitize_urls = new QName("", "sanitize-urls");

    HashMap<QName,String> options = new HashMap<>();

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
        return SequenceType.SINGLE_STRING;
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

            Parser parser = Parser.builder().build();
            Node document = parser.parse(markdown);
            HtmlRenderer.Builder builder = HtmlRenderer.builder();
            builder.escapeHtml(escape);
            builder.sanitizeUrls(sanitize);
            HtmlRenderer renderer = builder.build();
            String html = renderer.render(document);
            return new StringValue(html);
        }
    }

    private boolean getBooleanOption(QName name, boolean defvalue) {
        if (options.containsKey(name)) {
            String value = options.get(name);
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
        return defvalue;
    }

    private HashMap<QName,String> parseMap(MapItem item) throws XPathException {
        HashMap<QName,String> options = new HashMap<>();

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

                AtomicValue value = (AtomicValue) get.invoke(item, next);
                options.put(key, value.getStringValue());
                next = aiter.next();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException("Failed to resolve MapItem with reflection");
        }

        return options;
    }
}
