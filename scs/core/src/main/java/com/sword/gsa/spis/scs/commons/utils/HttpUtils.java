package com.sword.gsa.spis.scs.commons.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;

import sword.common.utils.StringUtils;
import sword.common.utils.streams.StreamUtils;

public class HttpUtils {

	@SuppressWarnings("resource")
	public static String partToString(final Charset cs, final Part part) throws IOException {
		if (part == null) return null;
		final InputStream is = part.getInputStream();
		if (is == null) return null;
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		StreamUtils.transferBytes(is, baos);
		return new String(baos.toByteArray(), cs);
	}

	public static Charset getRequestCharset(final HttpServletRequest request) {
		final String ce = request.getCharacterEncoding();
		if (StringUtils.isNullOrEmpty(ce)) return StandardCharsets.UTF_8;
		else try {
			return Charset.forName(ce);
		} catch (final Exception e) {
			return StandardCharsets.UTF_8;
		}
	}

}
