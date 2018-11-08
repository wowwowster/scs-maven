package com.sword.gsa.spis.scs.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import sword.common.utils.StringUtils;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.sword.gsa.spis.scs.commons.SCSContextListener;
import com.sword.gsa.spis.scs.commons.config.SCSConfiguration;
import com.sword.gsa.spis.scs.commons.http.SCSSecureServlet;
import com.sword.scs.utils.LicMgr.LicDate;

@MultipartConfig
public class SCSPublicConfigUI extends HttpServlet {

	private static final long serialVersionUID = 1L;

	protected static final Logger LOG = Logger.getLogger(SCSPublicConfigUI.class);

	protected LicDate licDate = null;
	protected SCSConfiguration conf = null;

	@Override
	public void init(final ServletConfig config) throws ServletException {
		super.init(config);
		final ServletContext sc = config.getServletContext();
		synchronized (sc) {
			licDate = (LicDate) sc.getAttribute(SCSContextListener.LIC_PARAM_NAME);
			if (licDate==null) licDate = LicDate.UNKNOWN_ERROR;
			conf = (SCSConfiguration) sc.getAttribute(SCSContextListener.CONF_PARAM_NAME);
		}
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		try {
			final String pi = req.getPathInfo();
			if (StringUtils.isNullOrEmpty(pi)) SCSConfigUI.jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request(0): " + req.getRequestURL());
			else if ("/scs/freshness".equals(pi)) getScsConfigFreshness(resp, getServletContext(), conf);
			else if ("/scs/license".equals(pi)) getScsLicenseStatus(resp, getServletContext(), licDate);
			else SCSConfigUI.jsonError(resp, HttpServletResponse.SC_BAD_REQUEST, "Unknown request: " + req.getRequestURL());
		} catch (final Exception e) {
			SCSConfigUI.jsonError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unhandled exception", e);
		}
	}

	private static void getScsConfigFreshness(final HttpServletResponse resp, final ServletContext sc, final SCSConfiguration conf) throws IOException, JsonGenerationException {

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();
			
			final String webFilePath = sc.getRealPath("/WEB-INF/web.xml");
			final File webFile = new File(webFilePath);

			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Files.copy(webFile.toPath(), baos);

			String contents = new String(baos.toByteArray(), StandardCharsets.UTF_8);
			
			Matcher m = Pattern.compile("<!-- Updated on (.+?) -->").matcher(contents);
			long lastModified = webFile.lastModified();
			if (m.find()) {
				try {
					lastModified = SCSSecureServlet.CONF_FILE_DATES.parse(m.group(1)).getTime();
				} catch (ParseException e) {
					LOG.error("Failed to fetch web.xml modification date", e);
				}
			}
			
			jg.writeBooleanField("IsFresh", conf.creationTime > lastModified);
			jg.writeEndObject();
		}

	}

	private static void getScsLicenseStatus(final HttpServletResponse resp, final ServletContext sc, final LicDate licDate) throws IOException, JsonGenerationException {

		resp.setStatus(HttpServletResponse.SC_OK);
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		try (OutputStream os = resp.getOutputStream(); JsonGenerator jg = new JsonFactory().createGenerator(os, JsonEncoding.UTF8)) {
			jg.setPrettyPrinter(new DefaultPrettyPrinter());
			jg.writeStartObject();
			
			jg.writeBooleanField("IsValid", licDate.isValid);
			jg.writeBooleanField("Expires", licDate.expires);
			jg.writeStringField("Message", licDate.dateString);
			
			jg.writeEndObject();
		}

	}

}
