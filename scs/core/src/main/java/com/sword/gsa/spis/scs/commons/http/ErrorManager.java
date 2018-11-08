package com.sword.gsa.spis.scs.commons.http;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import sword.common.utils.StringUtils;

public class ErrorManager {

	public static void processError(final HttpServletRequest req, final HttpServletResponse resp, final int statusCode, final String errorMessage, final Throwable t) throws IOException {
		resp.setStatus(statusCode);
		resp.setContentType("text/plain");
		resp.setCharacterEncoding(StandardCharsets.UTF_8.name());

		try (final BufferedOutputStream bos = new BufferedOutputStream(resp.getOutputStream())) {
			bos.write("Error: ".getBytes(StandardCharsets.UTF_8));
			bos.write((StringUtils.isNullOrEmpty(errorMessage) ? "Unknown error" : errorMessage).getBytes(StandardCharsets.UTF_8));
			bos.write("\n".getBytes(StandardCharsets.UTF_8));

			if (t != null) {
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				try (PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8.name())) {
					t.printStackTrace(ps);
				}
				bos.write(baos.toByteArray());
			}
		}
	}

	// public static byte[] buildErrorPageBytes(HttpServletRequest req, String errorMessage, Throwable t) {
	//
	// StringBuilder message = new StringBuilder();
	// if (t != null) {
	// message.append(ERROR_HEAD_LINE_MF.format(new String[]{t.getClass().getName()+": "+t.getMessage()}));
	// if (t.getCause()!=null) message.append(ERROR_MESS_LINE_MF.format(new String[]{"Caused by: "+t.getCause().getClass().getName()+": "+t.getCause().getMessage()}));
	// StackTraceElement[] aste = t.getStackTrace();
	// for (StackTraceElement ste : aste) {
	// message.append(ERROR_MESS_LINE_MF.format(new String[]{ste.getClassName()+"."+ste.getMethodName()+":"+ste.getLineNumber()}));
	// }
	// }
	// return ERROR_PAGE_MF.format(new String[]{errorMessage, message.toString()}).getBytes("ISO-8859-1");
	// }

	public static void processError(final HttpServletRequest req, final HttpServletResponse resp, final int statusCode, final String errorMessage) throws IOException {
		processError(req, resp, statusCode, errorMessage, null);
	}

}
