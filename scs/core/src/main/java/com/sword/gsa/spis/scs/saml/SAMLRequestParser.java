package com.sword.gsa.spis.scs.saml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.impl.builder.StAXSOAPModelBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sword.gsa.spis.scs.saml.authent.ArtifactRequest;
import com.sword.gsa.spis.scs.saml.authorization.DecisionQuery;

public class SAMLRequestParser {

	private static final Pattern ARTIFACT_EXTRACT = Pattern.compile("SAMLart=([^\\&]+)");

	private final Charset enc;

	public SAMLRequestParser(final Charset encoding) {
		enc = encoding;
	}

	public List<DecisionQuery> extractQueriesFromRequest(final HttpServletRequest request) throws IOException, XMLStreamException {

		final List<DecisionQuery> queries = new ArrayList<>();

		try (InputStream is = request.getInputStream()) {
			final XMLInputFactory xif = XMLInputFactory.newInstance();
			final XMLStreamReader reader = xif.createXMLStreamReader(is, enc.name());
			final StAXSOAPModelBuilder builder = new StAXSOAPModelBuilder(reader);

			QName qName = new QName(SAMLSPIConstants.SAMLP_NS, "AuthzDecisionQuery");

			final Iterator<?> iter = builder.getSOAPEnvelope().getBody().getChildrenWithName(qName);

			while (iter.hasNext()) {

				final OMElement authzDecisionQuery = (OMElement) iter.next();

				qName = new QName(null, "ID");
				final OMAttribute id = authzDecisionQuery.getAttribute(qName);
				qName = new QName(null, "Resource");
				final OMAttribute resource = authzDecisionQuery.getAttribute(qName);
				resource.toString();
				qName = new QName(SAMLSPIConstants.SAML_NS, "Subject");
				final OMElement subject = authzDecisionQuery.getFirstChildWithName(qName);
				qName = new QName(SAMLSPIConstants.SAML_NS, "NameID");
				final OMElement nameId = subject.getFirstChildWithName(qName);
				qName = new QName(SAMLSPIConstants.SAML_NS, "Action");
				final OMElement action = authzDecisionQuery.getFirstChildWithName(qName);
				qName = new QName(null, "Namespace");
				final OMAttribute actionNamespace = action.getAttribute(qName);

				queries.add(new DecisionQuery(id.getAttributeValue().trim(), resource.getAttributeValue().trim(), nameId.getText().trim(), action.getText().trim(), actionNamespace.getAttributeValue().trim()));
			}

			return queries;
		}
	}

	public ArtifactRequest extractArtifactFromRequest(final HttpServletRequest request) throws IOException, XMLStreamException {
		try (final InputStream is = request.getInputStream()) {
			final XMLInputFactory xif = XMLInputFactory.newInstance();
			final XMLStreamReader reader = xif.createXMLStreamReader(is, enc.name());
			final StAXSOAPModelBuilder builder = new StAXSOAPModelBuilder(reader);

			QName qName = new QName(SAMLSPIConstants.SAMLP_NS, "ArtifactResolve");
			final OMElement ar = builder.getSOAPEnvelope().getBody().getFirstChildWithName(qName);

			qName = new QName(SAMLSPIConstants.SAMLP_NS, "Artifact");
			final OMElement a = ar.getFirstChildWithName(qName);

			return new ArtifactRequest(a.getText().trim(), ar.getAttribute(new QName(null, "IssueInstant")).getAttributeValue(), ar.getAttribute(new QName(null, "ID")).getAttributeValue());
		}
	}

	public Element getDocFromString(final String xml) throws SAXException, IOException, ParserConfigurationException {
		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(enc))).getDocumentElement();
	}

	public static Element getFirstTagWithName(final String tagName, final Element rootElement) {
		return getFirstTagWithName("*", tagName, rootElement);
	}

	public static Element getFirstTagWithName(final String namespaceURI, final String tagName, final Element rootElement) {
		if (tagName.equals(rootElement.getNodeName())) return rootElement;
		final NodeList nl = rootElement.getElementsByTagNameNS(namespaceURI, tagName);
		final int l = nl.getLength();
		for (int i = 0; i < l; i++)
			if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) return (Element) nl.item(i);
		return null;
	}

	public static String extractArtifactFromReferrer(final String referrerValue) throws UnsupportedEncodingException {
		if (referrerValue == null) return null;
		final Matcher m = ARTIFACT_EXTRACT.matcher(referrerValue);
		if (m.find()) return URLDecoder.decode(m.group(1), "UTF-8");
		else return null;
	}

	public static Date parseIssueInstantString(final String issueInstant) throws ParseException {
		return SAMLSPIConstants.SAML_DF.parse(issueInstant);
	}

}
