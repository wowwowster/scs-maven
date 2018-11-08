package com.sword.gsa.connectors.gapps.youtube;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import sword.common.utils.StringUtils;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.gdata.client.CoreErrorDomain;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.RedirectRequiredException;
import com.google.gdata.util.ResourceNotFoundException;
import com.google.gdata.util.ServiceException;
import com.sword.gsa.connectors.gapps.apiwrap.ApiHelper;

public final class RetryableAction {

	private static final long[] WAITS = new long[] {TimeUnit.SECONDS.toMillis(3), TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(30), TimeUnit.MINUTES.toMillis(5)};
	private static final int MAX_WAIT_INDEX = WAITS.length - 1;

	public static <T> T executeWithRetry(final Callable<T> action, final int maxRetry, final GoogleCredential gc) throws Exception {
		int attempt = 0;
		final Object wait = new Object();
		Throwable ex = null;
		while (attempt < maxRetry)
			try {
				return action.call();
			} catch (final ResourceNotFoundException rnfe) {
				throw rnfe;
			} catch (final AuthenticationException ae) {
				if ("Unauthorized".equals(ae.getMessage())) throw ae;
				else {
					attempt++;
					ApiHelper.LOG.info("Request failed due to an AuthenticationException (attempt #" + attempt + ") - trying to refresh token");
					if (RetryableAction.refreshToken(maxRetry, gc)) ApiHelper.LOG.info("Token successfully refreshed");
					else {
						ApiHelper.LOG.info("Failed to refresh token");
						throw ae;
					}
				}
			} catch (final RedirectRequiredException rre) {
				throw rre;
			} catch (final ServiceException se) {
				attempt++;
				if (StringUtils.npeProofEquals(se.getCodeName(), CoreErrorDomain.ERR.invalidRedirectedToUrl.getCodeName())) throw se;
				else {
					ApiHelper.LOG.info("Request failed - retrying (attempt #" + attempt + "): ", se);
					ex = se;
					final int retryInd = Math.min(attempt, MAX_WAIT_INDEX);
					synchronized (wait) {
						wait.wait(WAITS[retryInd]);
					}
				}
			} catch (final Throwable t) {
				attempt++;
				ApiHelper.LOG.info("Request failed - retrying (attempt #" + attempt + "): ", t);
				ex = t;
				final int retryInd = Math.min(attempt, MAX_WAIT_INDEX);
				synchronized (wait) {
					wait.wait(WAITS[retryInd]);
				}
			}
		throw new Exception("Maximmum number of retries reached: ", ex);
	}

	public static boolean refreshToken(final int maxRetry, final GoogleCredential gc) throws Exception {
		int attempt = 0;
		final Object wait = new Object();
		Throwable ex = null;
		while (attempt < maxRetry)
			try {
				return gc.refreshToken();
			} catch (final Throwable t) {
				ApiHelper.LOG.info("Token refresh failed - retrying (attempt #" + attempt + "): " + t.getMessage());
				ApiHelper.LOG.trace(t);
				ex = t;
				final int retryInd = Math.min(attempt, MAX_WAIT_INDEX);
				attempt++;
				synchronized (wait) {
					wait.wait(WAITS[retryInd]);
				}
			}
		throw new Exception("Maximmum number of retries reached: ", ex);
	}

}
