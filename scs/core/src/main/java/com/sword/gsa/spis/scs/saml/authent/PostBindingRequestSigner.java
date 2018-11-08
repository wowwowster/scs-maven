package com.sword.gsa.spis.scs.saml.authent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sword.gsa.spis.scs.commons.SCSContext;
import com.sword.scs.Constants;

public final class PostBindingRequestSigner {

	private static final Logger LOG = Logger.getLogger(PostBindingRequestSigner.class);

	private final PrivateKeyEntry keyEntry;

	public PostBindingRequestSigner(final SCSContext ctx) {
		final File ksFile = ctx.tomcatRoot.resolve(Constants.REL_PATH_SCS_KEYSTORE).toFile();
		PrivateKeyEntry keyEntry = null;
		try (FileInputStream fis = new FileInputStream(ksFile)) {
			final KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(fis, Constants.STORES_PWD.toCharArray());
			keyEntry = (PrivateKeyEntry) ks.getEntry(Constants.SAML_KS_ENTRY_NAME, new KeyStore.PasswordProtection(Constants.STORES_PWD.toCharArray()));
		} catch (final Exception e) {
			e.printStackTrace();
			keyEntry = null;
		}
		this.keyEntry = keyEntry;
	}

	public byte[] signResponse(final String resp) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, FileNotFoundException, IOException, SAXException, ParserConfigurationException, MarshalException, XMLSignatureException, TransformerException {

		if (LOG.isDebugEnabled()) LOG.debug("Signing SAML response: " + resp);

		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setNamespaceAware(true);
		final Document doc = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(resp)));
		doc.getDocumentElement().setIdAttribute("ID", true);

		final XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
		final KeyInfoFactory kif = fac.getKeyInfoFactory();
		final List<Serializable> x509Content = new ArrayList<>();
		final X509Certificate cert = (X509Certificate) keyEntry.getCertificate();
		x509Content.add(cert.getSubjectX500Principal().getName());
		x509Content.add(cert);
		fac.newXMLSignature(
			fac.newSignedInfo(fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null), fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
				Collections.singletonList(fac.newReference("#sword", fac.newDigestMethod(DigestMethod.SHA1, null), Collections.singletonList(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null))),
				kif.newKeyInfo(Collections.singletonList(kif.newX509Data(x509Content)))).sign(new DOMSignContext(keyEntry.getPrivateKey(), doc.getDocumentElement()));
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final TransformerFactory tf = TransformerFactory.newInstance();
		final Transformer trans = tf.newTransformer();
		trans.transform(new DOMSource(doc), new StreamResult(baos));
		return baos.toByteArray();
	}
}
