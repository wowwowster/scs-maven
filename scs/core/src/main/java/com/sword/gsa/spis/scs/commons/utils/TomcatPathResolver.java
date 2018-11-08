package com.sword.gsa.spis.scs.commons.utils;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TomcatPathResolver {

	private static final Pattern ENV = Pattern.compile("\\$\\{([^\\}]+)\\}");

	public static boolean isValidFile(final String filePath) {
		if (new File(filePath).exists()) return true;
		else if (filePath.indexOf("${") != -1) {// Replace ${xxx} by corresponding system property then check file existence again
			Matcher m = ENV.matcher(filePath);
			final StringBuffer sb = new StringBuffer();
			while (m.find())
				m = m.appendReplacement(sb, Matcher.quoteReplacement(System.getProperty(m.group(1), "not_found")));
			m.appendTail(sb);
			return new File(sb.toString()).exists();
		}
		return false;
	}

	public static String getFilePath(final String filePath) {
		File f = new File(filePath);
		if (f.exists()) return f.getAbsolutePath();
		else if (filePath.indexOf("${") != -1) {// Replace ${xxx} by corresponding system property then check file existence again
			Matcher m = ENV.matcher(filePath);
			final StringBuffer sb = new StringBuffer();
			while (m.find())
				m = m.appendReplacement(sb, Matcher.quoteReplacement(System.getProperty(m.group(1), "not_found")));
			m.appendTail(sb);
			f = new File(sb.toString());
			return f.getAbsolutePath();
		}
		return null;
	}

}
