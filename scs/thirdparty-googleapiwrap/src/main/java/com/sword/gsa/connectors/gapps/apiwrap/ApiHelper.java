package com.sword.gsa.connectors.gapps.apiwrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.log4j.Logger;

import sword.common.utils.StringUtils;
import sword.common.utils.files.MimeType;
import sword.common.utils.streams.StreamUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpIOExceptionHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpUnsuccessfulResponseHandler;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ArrayMap;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.groupssettings.Groupssettings;
import com.google.api.services.plus.Plus;
import com.google.api.services.plusDomains.PlusDomains;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeRequestInitializer;

public class ApiHelper implements AutoCloseable {

	private static final Random RND = new Random();
	public static final Logger LOG = Logger.getLogger(ApiHelper.class);

	private final JsonFactory jsonFactory = new JacksonFactory();
	private final HttpTransport httpTransport;

	private final String svcAccountEmail;
	private final java.io.File privKeyFile;

	public ApiHelper(final String svcAccountEmail, final java.io.File privKeyFile) {
		this(svcAccountEmail, privKeyFile, TimeUnit.SECONDS.toMillis(40));
	}

	public ApiHelper(final String svcAccountEmail, final java.io.File privKeyFile, final long httpClientTimeout) {
		this.svcAccountEmail = svcAccountEmail;
		this.privKeyFile = privKeyFile;
		httpTransport = new ApacheHttpTransport.Builder().build();
		final HttpParams httpParams = ((ApacheHttpTransport) httpTransport).getHttpClient().getParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, (int) (2 * httpClientTimeout));
		HttpConnectionParams.setSoTimeout(httpParams, (int) httpClientTimeout);
	}

	public GoogleCredential getCredential(final String delegatedUserEmail, final Collection<String> scopes) throws GeneralSecurityException, IOException {
		return new GoogleCredential.Builder().addRefreshListener(new CredentialRefresshLogger()).setTransport(httpTransport).setJsonFactory(jsonFactory).setServiceAccountId(svcAccountEmail).setServiceAccountPrivateKeyFromP12File(privKeyFile).setServiceAccountScopes(scopes)
			.setServiceAccountUser(delegatedUserEmail).build();
	}

	public Directory getDirectoryService(final String applicationName, final String delegatedUserEmail, final Collection<String> scopes) throws GeneralSecurityException, IOException {
		final GoogleCredential gc = getCredential(delegatedUserEmail, scopes);
		final Directory service = new Directory.Builder(httpTransport, jsonFactory, null).setApplicationName(applicationName).setHttpRequestInitializer(gc).build();
		return service;
	}

	public Drive getDriveService(final String applicationName, final String delegatedUserEmail, final Collection<String> scopes) throws GeneralSecurityException, IOException {
		final GoogleCredential gc = getCredential(delegatedUserEmail, scopes);
		final Drive service = new Drive.Builder(httpTransport, jsonFactory, null).setApplicationName(applicationName).setHttpRequestInitializer(gc).build();
		return service;
	}

	public Plus getGPlusService(final String applicationName, final String delegatedUserEmail, final Collection<String> scopes) throws GeneralSecurityException, IOException {
		final GoogleCredential gc = getCredential(delegatedUserEmail, scopes);
		final Plus service = new Plus.Builder(httpTransport, jsonFactory, null).setApplicationName(applicationName).setHttpRequestInitializer(gc).build();
		return service;
	}

	public PlusDomains getGPlusDomainsService(final String applicationName, final String delegatedUserEmail, final Collection<String> scopes) throws GeneralSecurityException, IOException {
		final GoogleCredential gc = getCredential(delegatedUserEmail, scopes);
		final PlusDomains service = new PlusDomains.Builder(httpTransport, jsonFactory, null).setApplicationName(applicationName).setHttpRequestInitializer(gc).build();
		return service;
	}

	public Groupssettings getGroupsSettingsService(final String applicationName, final String delegatedUserEmail, final Collection<String> scopes) throws GeneralSecurityException, IOException {
		final GoogleCredential gc = getCredential(delegatedUserEmail, scopes);
		final Groupssettings service = new Groupssettings.Builder(httpTransport, jsonFactory, null).setApplicationName(applicationName).setHttpRequestInitializer(gc).build();
		return service;
	}
	
	public YouTube getYouTubeService(final String applicationName, final String clientId, final String clientSecret, final String accessToken, final String refreshToken, final Collection<String> scopes, final Long httpClientTimeout) {
		final GoogleCredential gc = getCredential(clientId, clientSecret, accessToken, refreshToken, scopes, httpClientTimeout);
		final YouTube service = new YouTube.Builder(httpTransport, jsonFactory, gc).setApplicationName(applicationName).build();
		return service;
	}
	
	public YouTube getYouTubeService(final String applicationName, final String apiKey) {
		final YouTube service = new YouTube.Builder(httpTransport, jsonFactory, null).setYouTubeRequestInitializer(new YouTubeRequestInitializer(apiKey)).setApplicationName(applicationName).build();
		return service;
	}

	@Override
	public void close() throws Exception {
		httpTransport.shutdown();
	}

	public static GoogleCredential getCredential(final String clientId, final String clientSecret, final String accessToken, final String refreshToken, final long httpClientTimeout) {
		
		final HttpTransport httpTransport = new ApacheHttpTransport.Builder().build();
		final HttpParams httpParams = ((ApacheHttpTransport) httpTransport).getHttpClient().getParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, (int) (2 * httpClientTimeout));
		HttpConnectionParams.setSoTimeout(httpParams, (int) httpClientTimeout);
		
		final JsonFactory jsonFactory = new JacksonFactory();
		
		return new GoogleCredential.Builder()
		.setTransport(httpTransport)
		.setJsonFactory(jsonFactory)
		.setClientSecrets(clientId, clientSecret)
		.addRefreshListener(new CredentialRefresshLogger())
		.build()
		.setAccessToken(accessToken)
		.setExpiresInSeconds(3600L)
		.setRefreshToken(refreshToken);
	}
	
	public static GoogleCredential getCredential(final String clientId, final String clientSecret, final String accessToken, final String refreshToken, final Collection<String> scopes, final long httpClientTimeout) {
		
		final HttpTransport httpTransport = new ApacheHttpTransport.Builder().build();
		final HttpParams httpParams = ((ApacheHttpTransport) httpTransport).getHttpClient().getParams();
		HttpConnectionParams.setConnectionTimeout(httpParams, (int) (2 * httpClientTimeout));
		HttpConnectionParams.setSoTimeout(httpParams, (int) httpClientTimeout);
		
		final JsonFactory jsonFactory = new JacksonFactory();
		
		return new GoogleCredential.Builder()
		.setTransport(httpTransport)
		.setJsonFactory(jsonFactory)
		.setClientSecrets(clientId, clientSecret)
		.addRefreshListener(new CredentialRefresshLogger())
		.build().createScoped(scopes)
		.setAccessToken(accessToken)
		.setExpiresInSeconds(3600L)
		.setRefreshToken(refreshToken);
	}

	public static <T> T executeWithRetry(final AbstractGoogleClientRequest<T> req, final int maxRetry) throws Exception {
		int i = 0;
		Exception lastEx = null;
		while (i < maxRetry) {
			i++;
			try {
				return req.execute();
			} catch (final GoogleJsonResponseException e) {
				lastEx = e;
				final String m = e.getMessage();
				final GoogleJsonError gje = e.getDetails();
				final int errorCodeType = gje.getCode() / 10;
				if (errorCodeType == 50 || isGoogleRateError(gje)) {
					LOG.warn(req.getClass().getSimpleName() + " #" + i + " failed - retrying: " + m);
					Thread.sleep(getSleepPeriod(i));
				} else throw e;
			} catch (final IOException e) {
				lastEx = e;
				final String m = e.getMessage();
				LOG.warn(String.format("%s #%d failed (%s) - retrying: %s", req.getClass().getSimpleName(), i, e.getClass().getSimpleName(), m));
				Thread.sleep(getSleepPeriod(i));
			} catch (final Exception e) {
				lastEx = e;
				final String m = e.getMessage();
				if (!StringUtils.isNullOrEmpty(m) && m.contains("403 User Rate Limit Exceeded")) {
					LOG.warn(req.getClass().getSimpleName() + " #" + i + " failed (User Rate Limit Exceeded) - retrying: " + m);
					Thread.sleep(getSleepPeriod(i));
				} else throw e;
			}
		}
		LOG.error("Maximum number of retries reached - rethrowing");
		throw lastEx;
	}

	static int getSleepPeriod(final int i) {
		return (1 << i) * 10_000 + RND.nextInt(3000);
	}

	private static boolean isGoogleRateError(final GoogleJsonError e) {// Source: exponential backoff example from https://developers.google.com/drive/handle-errors
		try {
			if (e.getCode() == 403) {
				final List<ErrorInfo> errorList = e.getErrors();
				if (!(errorList == null || errorList.isEmpty())) {
					final ErrorInfo firstError = errorList.get(0);
					if (firstError != null) {
						final String reason = firstError.getReason();
						return "rateLimitExceeded".equals(reason) || "userRateLimitExceeded".equals(reason) || "quotaExceeded".equals(reason);
					}
				}
			}
			return false;
		} catch (final Exception ex) {
			return false;
		}
	}

	public static InputStream downloadFile(final Drive service, final File file) throws IOException, InterruptedException {
		return downloadFile(service, getDownloadUrl(file));
	}

	public static InputStream downloadFile(final Drive service, final String url) throws IOException, InterruptedException {
		try {
			if (StringUtils.isNullOrEmpty(url)) throw new NullPointerException("File URL");
			final RetryDownload rd = new RetryDownload(4);
			final HttpResponse resp = service.getRequestFactory().buildGetRequest(new GenericUrl(url)).setIOExceptionHandler(rd).setUnsuccessfulResponseHandler(rd).execute();
			final int sc = resp.getStatusCode();
			final InputStream is = resp.getContent();
			if (sc == 200) return is;
			else {
				final ByteArrayOutputStream os = new ByteArrayOutputStream();
				StreamUtils.transferBytes(is, os);
				throw new IOException("Obtained an invalid status code: " + sc + " - content: " + new String(os.toByteArray()));
			}
		} catch (IOException ioe) {
			final Throwable cause = ioe.getCause();
			if (cause instanceof InterruptedException) throw (InterruptedException) cause;
			else throw ioe;
		}
	}

	public static String getDownloadUrl(final File file) {
		return getDownloadInfo(file)[0];
	}

	public static String getMime(final File file) {
		return getDownloadInfo(file)[1];
	}

	private static String[] getDownloadInfo(final File file) {
		if (file == null) throw new NullPointerException("File");

		String u = file.getDownloadUrl();
		String mt = file.getMimeType();

		if (StringUtils.isNullOrEmpty(u)) {
			final Map<String, String> els = file.getExportLinks();
			if (els == null) {
				u = file.getAlternateLink();
			} else {
				if (MimeType.GA_DOC.mime.equals(mt)) {
					mt = MimeType.WORD_X.mime;
				} else if (MimeType.GA_DRAWING.mime.equals(mt)) {
					mt = MimeType.PNG.mime;
				} else if (MimeType.GA_FORM.mime.equals(mt)) {
					mt = MimeType.CSV.mime;
				} else if (MimeType.GA_PRES.mime.equals(mt)) {
					mt = MimeType.POWERPOINT_X.mime;
				} else if (MimeType.GA_SPREADSHEET.mime.equals(mt)) {
					mt = MimeType.EXCEL_X.mime;
				}

				if (els.containsKey(mt)) {
					u = els.get(mt);
				} else {
					u = file.getAlternateLink();
					mt = file.getMimeType();
				}

			}
		}
		return new String[] {u, mt};
	}
	
	public static <T> List<T> buildTypedList(Object untypedObjectList, Class<T> destType) throws InstantiationException, IllegalAccessException {
		if (untypedObjectList == null) return null;
		else if (untypedObjectList instanceof List) {
			List<T> result = new ArrayList<>();
			for (Object untypedEmail: (List<?>) untypedObjectList) {
				if (untypedEmail != null) {
					if (untypedEmail instanceof ArrayMap) {
						result.add(buildSimpleObject((ArrayMap<?, ?>)untypedEmail, destType));
					} else if (destType.isInstance(untypedEmail)) {
						result.add(destType.cast(untypedEmail));
					} else throw new IllegalArgumentException("List item type not supported: " + untypedEmail.getClass().getName());
				}
			}
			return result;
		} else throw new IllegalArgumentException("Object is not a List");
	}
	
	public static <T> T buildSimpleObject(ArrayMap<?, ?> map, Class<T> destClass) throws InstantiationException, IllegalAccessException {
		T obj = destClass.newInstance();
		Field[] af = destClass.getDeclaredFields();
		for (Field f: af) {
			if (f.getAnnotation(com.google.api.client.util.Key.class) != null) {
				f.setAccessible(true);
				f.set(obj, map.get(f.getName()));
				f.setAccessible(false);
			}
		}
		return obj;
	}

	private static class RetryDownload implements HttpIOExceptionHandler, HttpUnsuccessfulResponseHandler {

		private final int maxRetries;
		private int retryCount = 0;

		public RetryDownload(final int maxRetries) {
			this.maxRetries = maxRetries;
		}

		@Override
		public boolean handleResponse(final HttpRequest request, final HttpResponse response, final boolean supportsRetry) throws IOException {
			try {
				return waitAndRetry(supportsRetry);
			} catch (InterruptedException ie) {
				throw new IOException(ie);
			}
		}

		@Override
		public boolean handleIOException(final HttpRequest request, final boolean supportsRetry) throws IOException {
			try {
				return waitAndRetry(supportsRetry);
			} catch (InterruptedException ie) {
				throw new IOException(ie);
			}
		}

		private synchronized boolean waitAndRetry(final boolean supportsRetry) throws InterruptedException {
			retryCount++;
			if (retryCount <= maxRetries) {
				LOG.warn("DownloadRequest #" + retryCount + " failed - retrying (" + supportsRetry + ")");
				Thread.sleep(getSleepPeriod(retryCount));
				return true;
			} else {
				LOG.error("Maximum number of retries reached - aborting");
				return false;
			}
		}

	}


}
