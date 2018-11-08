package com.sword.gsa.spis.scs.saml;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.codec.binary.Base64;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import sword.common.crypto.hash.Digester;
import sword.common.crypto.hash.HashAlgorithm;
import sword.common.utils.StringUtils;
import sword.common.utils.enums.EnumFactory;
import sword.gsa.xmlfeeds.builder.acl.Group;

import com.sword.gsa.spis.scs.saml.authorization.DecisionQuery;

public class SAMLResponseBuilder {

	private static final long FIVE_MINUTE = TimeUnit.MINUTES.toMillis(5);

	private static final DateTimeFormatter DATE_FORMATER = org.opensaml.Configuration.getSAMLDateFormatter();

	public static byte[] buildAuthorizationResponse(final String statusCode, final List<DecisionQuery> queries, final String entityID) throws Exception {
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();

		final String refDate = DATE_FORMATER.print(new DateTime());

		final Element envelope = doc.createElement("soapenv:Envelope");
		envelope.setAttribute("xmlns:soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		final Element body = doc.createElement("soapenv:Body");

		if (SAMLSPIConstants.SAML_SC_SUCCESS.equals(statusCode)) {
			final int l = queries.size();
			DecisionQuery dq;
			for (int i = 0; i < l; i++) {
				dq = queries.get(i);

				final Element sResponse = spResponse(doc, refDate, "sword".concat(Integer.toString(i)));
				sResponse.appendChild(spStatusSuccess(doc));
				final Element sAssertion = sAssertion(doc, refDate, dq.getId());
				sAssertion.appendChild(sIssuer(doc, entityID));
				sAssertion.appendChild(sSubject(doc, dq.getOriginalSubject()));
				final Element sAuthDec = doc.createElement("saml:AuthzDecisionStatement");
				sAuthDec.setAttribute("Resource", dq.getResource());
				sAuthDec.setAttribute("Decision", EnumFactory.getLabel(dq.getAuthZDecision()));
				final Element sAuthAction = doc.createElement("saml:Action");
				sAuthAction.setAttribute("Namespace", "urn:oasis:names:tc:SAML:1.0:action:ghpp");
				sAuthAction.setTextContent(dq.getAction());
				sAuthDec.appendChild(sAuthAction);
				sAssertion.appendChild(sAuthDec);
				sResponse.appendChild(sAssertion);
				body.appendChild(sResponse);
			}
		} else {
			final Element sResponse = spResponse(doc, refDate, "sword0");
			final Element sStatus = doc.createElement("samlp:Status");
			final Element sStatusCode = doc.createElement("samlp:StatusCode");
			sStatusCode.setAttribute("Value", SAMLSPIConstants.SAML_SC_RESPONDER_FAILURE);
			sStatus.appendChild(sStatusCode);
			sResponse.appendChild(sStatus);
			body.appendChild(sResponse);
		}
		envelope.appendChild(body);
		doc.appendChild(envelope);

		return domToByteArray(doc, transformer);
	}

	public static String buildArtifactResponse(final String statusCode, final String verifiedIdentify, final String originalID, final String samlId, final Date issueInstant, final String consumer, final String secMngrissuer, final Collection<Group> groups, final long trustDuration, final String entityID) throws Exception {
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();

		final Element envelope = doc.createElement("soapenv:Envelope");
		envelope.setAttribute("xmlns:soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		final Element body = doc.createElement("soapenv:Body");

		final DateTimeFormatter dateFormater = org.opensaml.Configuration.getSAMLDateFormatter();
		final long curentMili = issueInstant.getTime();
		final String refDate = dateFormater.print(new DateTime(curentMili));
		final String notBefore = dateFormater.print(new DateTime(curentMili - FIVE_MINUTE));
		final String notAfterDate = dateFormater.print(new DateTime(curentMili + trustDuration));

		final Element sResponse = spResponse(doc, refDate, "sword");
		final Element sArtResponse = spArtifactResp(doc, originalID, refDate);
		final Element issuer = doc.createElement("Issuer");
		issuer.setTextContent(entityID);
		sArtResponse.appendChild(issuer);

		if (!SAMLSPIConstants.SAML_SC_SUCCESS.equals(statusCode)) {
			sArtResponse.appendChild(spStatusSuccess(doc));
			sArtResponse.appendChild(sResponse);
			sResponse.appendChild(spStatusFailure(doc));
		} else {
			sArtResponse.appendChild(spStatusSuccess(doc));
			sArtResponse.appendChild(sResponse);
			sResponse.appendChild(spStatusSuccess(doc));
			final Element sAssertion = sAssertion(doc, refDate, originalID);
			sResponse.appendChild(sAssertion);
			sAssertion.appendChild(sIssuer(doc, entityID));
			final Element subject = sSubject(doc, verifiedIdentify);
			subject.appendChild(sSubjConfirmation(doc, samlId, consumer, notAfterDate));
			sAssertion.appendChild(subject);
			sAssertion.appendChild(sConditions(doc, secMngrissuer, notBefore, notAfterDate));
			sAssertion.appendChild(sAuthNStatement(doc, refDate));
			addGroups(doc, groups, sAssertion);
		}

		body.appendChild(sArtResponse);
		envelope.appendChild(body);
		doc.appendChild(envelope);

		return domToString(doc, transformer);
	}

	public static String buildPostResponse(final String statusCode, final String verifiedIdentify, final String samlId, final Date issueInstant, final String consumer, final String secMngrissuer, final Collection<Group> groups, final long trustDuration, final String entityID) throws Exception {
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();

		final DateTimeFormatter dateFormater = org.opensaml.Configuration.getSAMLDateFormatter();
		final long curentMili = issueInstant.getTime();
		final String refDate = dateFormater.print(new DateTime(curentMili));
		final String notBefore = dateFormater.print(new DateTime(curentMili - FIVE_MINUTE));
		final String notAfterDate = dateFormater.print(new DateTime(curentMili + trustDuration));

		final Element sResponse = spResponse(doc, refDate, "sword");

		if (!SAMLSPIConstants.SAML_SC_SUCCESS.equals(statusCode)) {
			sResponse.setAttribute("Destination", consumer);
			sResponse.appendChild(sIssuer(doc, entityID));
			sResponse.appendChild(spStatusFailure(doc));
		} else {
			sResponse.setAttribute("Destination", consumer);
			sResponse.appendChild(sIssuer(doc, entityID));
			sResponse.appendChild(spStatusSuccess(doc));
			final Element sAssertion = sAssertion(doc, refDate, "assertionid");
			sAssertion.appendChild(sIssuer(doc, entityID));
			sResponse.appendChild(sAssertion);

			final Element subject = sSubject(doc, verifiedIdentify);
			subject.appendChild(sSubjConfirmation(doc, samlId, consumer, notAfterDate));
			sAssertion.appendChild(subject);

			sAssertion.appendChild(sConditions(doc, secMngrissuer, notBefore, notAfterDate));
			sAssertion.appendChild(sAuthNStatement(doc, refDate));
			addGroups(doc, groups, sAssertion);
		}

		doc.appendChild(sResponse);

		return domToString(doc, transformer);
	}

	/**
	 * <pre>
	 *  SAML_artifact := B64 (TypeCode EndpointIndex RemainingArtifact)
	 *  TypeCode      := Byte1Byte2
	 *  EndpointIndex := Byte1Byte2
	 *
	 *  Type 4 artifact:
	 *  TypeCode          := 0x0004
	 *  RemainingArtifact := SourceId MessageHandle
	 *
	 *  Where:
	 *  SourceId      := 20-byte_sequence
	 *  MessageHandle := 20-byte_sequence
	 *
	 *  SourceId      := Arbitrary sequence of bytes, although in practice, the SourceId is the SHA-1 hash of the issuer's entityID.
	 *  MessageHandle := Random sequence of bytes that references a SAML message that the artifact issuer is willing to produce on-demand.
	 * </pre>
	 */
	public static String generateType4Artifact(final String entityID) throws NoSuchAlgorithmException {
		final ByteBuffer bb = ByteBuffer.allocate(44);
		bb.put((byte) -0x01);
		bb.put((byte) 0x4);
		bb.put((byte) 0x5);
		bb.put((byte) 0x5);
		bb.put(twentyBytesLongSha1(entityID));
		final StringBuilder sb = new StringBuilder(Thread.currentThread().getName());
		sb.append(Long.toHexString(System.nanoTime()));
		while (sb.length() < 20)
			sb.append(Long.toHexString(System.nanoTime()));
		bb.put(sb.toString().getBytes(StandardCharsets.UTF_8), 0, 20);
		return Base64.encodeBase64String(bb.array());
	}

	private static byte[] twentyBytesLongSha1(final String entityID) throws NoSuchAlgorithmException {
		final Digester d = new Digester(HashAlgorithm.SHA_1);
		final byte[] val = d.digest(entityID.getBytes(StandardCharsets.UTF_8));
		return Arrays.copyOf(val, 20);
	}

	private static Element spArtifactResp(final Document doc, final String originalID, final String refDate) {
		final Element sArtResponse = doc.createElement("samlp:ArtifactResponse");
		sArtResponse.setAttribute("xmlns:samlp", SAMLSPIConstants.SAMLP_NS);
		sArtResponse.setAttribute("xmlns", SAMLSPIConstants.SAML_NS);
		sArtResponse.setAttribute("ID", "swordar");
		sArtResponse.setAttribute("Version", "2.0");
		sArtResponse.setAttribute("IssueInstant", refDate);
		sArtResponse.setAttribute("InResponseTo", originalID);
		return sArtResponse;
	}

	private static Element spResponse(final Document doc, final String refDate, final String id) {
		final Element sResponse = doc.createElement("samlp:Response");
		sResponse.setAttribute("xmlns:samlp", SAMLSPIConstants.SAMLP_NS);
		sResponse.setAttribute("xmlns:saml", SAMLSPIConstants.SAML_NS);
		sResponse.setAttribute("ID", id);
		sResponse.setAttribute("Version", "2.0");
		sResponse.setAttribute("IssueInstant", refDate);
		return sResponse;
	}

	private static Element sAssertion(final Document doc, final String refDate, final String id) {
		final Element sAssertion = doc.createElement("saml:Assertion");
		sAssertion.setAttribute("ID", id);
		sAssertion.setAttribute("Version", "2.0");
		sAssertion.setAttribute("IssueInstant", refDate);
		return sAssertion;
	}

	private static Element sSubject(final Document doc, final String verifiedIdentify) {
		final Element subject = doc.createElement("saml:Subject");
		final Element nameID = doc.createElement("saml:NameID");
		nameID.setTextContent(verifiedIdentify);
		subject.appendChild(nameID);
		return subject;
	}

	private static Element sSubjConfirmation(final Document doc, final String samlId, final String consumer, final String notAfterDate) {
		final Element sSubjConfirm = doc.createElement("saml:SubjectConfirmation");
		sSubjConfirm.setAttribute("Method", "urn:oasis:names:tc:SAML:2.0:cm:bearer");
		final Element sSubjConfirmData = doc.createElement("saml:SubjectConfirmationData");
		sSubjConfirmData.setAttribute("InResponseTo", samlId);
		sSubjConfirmData.setAttribute("Recipient", consumer);
		sSubjConfirmData.setAttribute("NotOnOrAfter", notAfterDate);
		sSubjConfirm.appendChild(sSubjConfirmData);
		return sSubjConfirm;
	}

	private static Element sIssuer(final Document doc, final String entityID) {
		final Element sIssuer = doc.createElement("saml:Issuer");
		sIssuer.setTextContent(entityID);
		return sIssuer;
	}

	private static Element sAuthNStatement(final Document doc, final String refDate) {
		final Element sAuthStatement = doc.createElement("saml:AuthnStatement");
		sAuthStatement.setAttribute("AuthnInstant", refDate);
		final Element sAuthCtx = doc.createElement("saml:AuthnContext");
		final Element sAuthCtxClass = doc.createElement("saml:AuthnContextClassRef");
		sAuthCtxClass.setTextContent("urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport");
		sAuthCtx.appendChild(sAuthCtxClass);
		sAuthStatement.appendChild(sAuthCtx);
		return sAuthStatement;
	}

	private static Element sConditions(final Document doc, final String secMngrissuer, final String refDate, final String notAfterDate) {
		final Element sCondition = doc.createElement("saml:Conditions");
		sCondition.setAttribute("NotBefore", refDate);
		sCondition.setAttribute("NotOnOrAfter", notAfterDate);
		final Element sAudienceRestr = doc.createElement("saml:AudienceRestriction");
		final Element sAudience = doc.createElement("saml:Audience");
		sAudience.setTextContent(secMngrissuer);
		sAudienceRestr.appendChild(sAudience);
		sCondition.appendChild(sAudienceRestr);
		return sCondition;
	}

	private static Element spStatusSuccess(final Document doc) {
		final Element sStatus = doc.createElement("samlp:Status");
		final Element sStatusCode = doc.createElement("samlp:StatusCode");
		sStatusCode.setAttribute("Value", SAMLSPIConstants.SAML_SC_SUCCESS);
		sStatus.appendChild(sStatusCode);
		return sStatus;
	}

	private static Element spStatusFailure(final Document doc) {
		final Element sStatus = doc.createElement("samlp:Status");
		final Element sStatusCode = doc.createElement("samlp:StatusCode");
		sStatusCode.setAttribute("Value", SAMLSPIConstants.SAML_SC_RESPONDER_FAILURE);
		sStatus.appendChild(sStatusCode);
		final Element sStatusCode2 = doc.createElement("samlp:StatusCode");
		sStatusCode2.setAttribute("Value", SAMLSPIConstants.SAML_SC_AUTHN_FAILURE);
		sStatusCode.appendChild(sStatusCode2);
		return sStatus;
	}

	private static void addGroups(final Document doc, final Collection<Group> groups, final Element parentElement) {
		Element sAttrStatement, sAttr;
		if (groups != null) {
			sAttrStatement = doc.createElement("saml:AttributeStatement");
			sAttr = doc.createElement("saml:Attribute");
			sAttr.setAttribute("Name", "member-of");
			Element attrVal;
			for (final Group group : groups) {
				attrVal = doc.createElement("saml:AttributeValue");
				if (StringUtils.isNullOrEmpty(group.namespace)) attrVal.setTextContent(group.principal);
				else attrVal.setTextContent(group.namespace + group.principal);
				sAttr.appendChild(attrVal);
			}
			sAttrStatement.appendChild(sAttr);
			parentElement.appendChild(sAttrStatement);
		}
	}

	private static byte[] domToByteArray(final Document doc, final Transformer transformer) throws TransformerException {
		final DOMSource domSource = new DOMSource(doc);
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.transform(domSource, new StreamResult(baos));
		return baos.toByteArray();
	}

	private static String domToString(final Document doc, final Transformer transformer) throws TransformerConfigurationException, TransformerException {
		final DOMSource domSource = new DOMSource(doc);
		final StringWriter writer = new StringWriter();
		final StreamResult result = new StreamResult(writer);
		transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		transformer.transform(domSource, result);
		return writer.toString();
	}
}