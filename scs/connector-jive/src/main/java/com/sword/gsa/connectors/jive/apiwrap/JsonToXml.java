package com.sword.gsa.connectors.jive.apiwrap;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import sword.common.utils.StringUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

@Deprecated
public class JsonToXml {

	public static final String NODE_NAME_ROOT = "json";
	public static final String NODE_NAME_OBJECT = "o";
	public static final String NODE_NAME_ARRAY = "a";
	public static final String NODE_NAME_PROPERTY = "p";

	public static final String ATTR_NAME_TYPE = "t";
	public static final String ATTR_NAME_NAME = "n";

	private final JsonParser jp;

	public JsonToXml(final JsonParser jp) {
		super();
		this.jp = jp;
	}

	public Document toXml() throws IOException, ParserConfigurationException {

		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Element root = doc.createElement(NODE_NAME_ROOT);
		doc.appendChild(root);

		JsonToken tok = null;
		while ((tok = jp.nextToken()) != null)
			processToken(jp, tok, doc, root);

		return doc;

	}

	public static void processObject(final JsonParser jp, final Document document, final Element node) throws JsonParseException, IOException {
		JsonToken tok = null;
		while ((tok = jp.nextToken()) != JsonToken.END_OBJECT)
			processToken(jp, tok, document, node);
	}

	public static void processArray(final JsonParser jp, final Document document, final Element node) throws JsonParseException, IOException {
		JsonToken tok = null;
		while ((tok = jp.nextToken()) != JsonToken.END_ARRAY)
			processToken(jp, tok, document, node);
	}

	public static void processToken(final JsonParser jp, final JsonToken tok, final Document document, final Element node) throws JsonParseException, IOException {

		if (tok == JsonToken.FIELD_NAME) return;
		else if (tok == JsonToken.START_ARRAY) {
			final Element an = document.createElement(NODE_NAME_ARRAY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) an.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			node.appendChild(an);
			processArray(jp, document, an);
		} else if (tok == JsonToken.START_OBJECT) {
			Element on = null;
			if (node.getNodeName().equals(NODE_NAME_ROOT) && StringUtils.isNullOrEmpty(jp.getCurrentName())) on = node;
			else {
				on = document.createElement(NODE_NAME_OBJECT);
				if (!NODE_NAME_ARRAY.equals(node.getNodeName())) on.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
				node.appendChild(on);
			}
			processObject(jp, document, on);
		} else if (tok == JsonToken.NOT_AVAILABLE) {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "NA");
			node.appendChild(pn);
		} else if (tok == JsonToken.VALUE_EMBEDDED_OBJECT) {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "embedded");
			node.appendChild(pn);
		} else if (tok == JsonToken.VALUE_FALSE || tok == JsonToken.VALUE_TRUE) {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "boolean");
			pn.setTextContent(Boolean.toString(jp.getBooleanValue()));
			node.appendChild(pn);
		} else if (tok == JsonToken.VALUE_NULL) {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "unknown");
			node.appendChild(pn);
		} else if (tok == JsonToken.VALUE_NUMBER_FLOAT) {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "float");
			pn.setTextContent(Float.toString(jp.getFloatValue()));
			node.appendChild(pn);
		} else if (tok == JsonToken.VALUE_NUMBER_INT) {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "integer");
			pn.setTextContent(Integer.toString(jp.getIntValue()));
			node.appendChild(pn);
		} else if (tok == JsonToken.VALUE_STRING) {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "string");
			pn.setTextContent(jp.getValueAsString());
			node.appendChild(pn);
		} else {
			final Element pn = document.createElement(NODE_NAME_PROPERTY);
			if (!NODE_NAME_ARRAY.equals(node.getNodeName())) pn.setAttribute(ATTR_NAME_NAME, jp.getCurrentName());
			pn.setAttribute(ATTR_NAME_TYPE, "error");
			pn.setTextContent(tok.toString());
			node.appendChild(pn);
		}

	}

}
