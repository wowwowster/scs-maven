package com.sword.gsa.spis.scs.push.connector;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import sword.common.utils.StringUtils;

public final class URLManager {

	public static final String CONNECTORS_PATTERN = "regexp:https://connectors\\\\.sword-group\\\\.com/[A-Za-z0-9_\\\\-]+/doc\\\\?node=.+";
	public static final String CONNECTORS_HOST = "https://connectors.sword-group.com/%s/doc?node=%s";
	private static final String ALTERNATIVE_BASE_URL = System.getProperty("sword.indexer.AlternativeURL", "");

	// private static final String NO_VALUE = "null";
	// private static final Pattern PARAMETERS_NAMES_EXTRACT_RE = Pattern.compile("\\$\\{([^}]+?)(?:,([1-9]))??\\}");// Reluctant (non-Greedy)
	// private static final MessageFormat PARAMETERS_REPLACEMENT_RE = new MessageFormat("\\$\\'{'\\Q{0}\\E(?:,[1-9])?\\'}'");// Greedy
	//
	// private final Map<String, String> parameterValues = new HashMap<String, String>();
	// private final Map<String, Integer> parameterEncoding = new HashMap<String, Integer>();
	// private final String urlBase;
	// private final List<Constant> constants;
	//
	// public URLManager(String url, List<Constant> constants) {
	// urlBase = url;
	// this.constants = constants;
	// initMap();
	// }
	//
	// private void initMap() {
	// final Matcher m = PARAMETERS_NAMES_EXTRACT_RE.matcher(urlBase);
	// parameterValues.clear();
	// parameterEncoding.clear();
	// String pn, enc;
	// while (m.find()) {
	// pn = m.group(1);
	// enc = m.group(2);
	// parameterValues.put(pn, NO_VALUE);
	// parameterEncoding.put(pn, enc == null ? 0 : Integer.parseInt(enc));
	// }
	// for (final Constant c : constants)
	// addParameter(c.name, c.value);
	// }
	//
	// public void clear() {
	// initMap();
	// }
	//
	// public void addParameter(String paramName, String paramValue) {
	// if (parameterValues.containsKey(paramName)) parameterValues.put(paramName, paramValue);
	// }
	//
	// public boolean contains(String paramName) {
	// return parameterValues.containsKey(paramName);
	// }
	//
	// public String buildURL() {
	// final Iterator<String> it = parameterValues.keySet().iterator();
	// String _url = urlBase;
	// String paramName, paramValue;
	// int numEnc;
	// Matcher m;
	// StringBuffer sb;
	// while (it.hasNext()) {
	// paramName = it.next();
	// paramValue = parameterValues.get(paramName);
	// numEnc = parameterEncoding.get(paramName).intValue();
	// for (int i = 0; i < numEnc; i++)
	// try {
	// paramValue = URLEncoder.encode(paramValue, StandardCharsets.UTF_8.name());
	// } catch (final UnsupportedEncodingException e) {}
	// final Pattern replRe = Pattern.compile(PARAMETERS_REPLACEMENT_RE.format(new String[] {paramName}));
	// m = replRe.matcher(_url);
	// sb = new StringBuffer();
	// while (m.find())
	// m.appendReplacement(sb, Matcher.quoteReplacement(paramValue));
	// m.appendTail(sb);
	// _url = sb.toString();
	// }
	// return _url;
	// }

	public static String getSystemURL(final String dataSource, final String docId) throws UnsupportedEncodingException {
		final String un = StandardCharsets.UTF_8.name();
		
		final String did = URLEncoder.encode(docId, un);
		if (StringUtils.isNullOrEmpty(ALTERNATIVE_BASE_URL)) {
			final String ds = URLEncoder.encode(dataSource, un);
			return String.format(CONNECTORS_HOST, ds, did);
		} else {
			return ALTERNATIVE_BASE_URL + did;
		}
		
	}
}