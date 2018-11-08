package com.sword.scs.utils;

import sword.common.crypto.aes.AESEncrypter;
import sword.common.security.PwdManager;
import sword.common.utils.HexUtils;
import sword.common.utils.dates.ThreadSafeDateFormat;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class LicMgr {
	
	static final ThreadSafeDateFormat SDF = new ThreadSafeDateFormat(new SimpleDateFormat("'::lic::'yyyyMMdd'::cil::'"));
	private static final String KEY_NAME_LIC = "lic";
	static final String AES_KEY = "je suis un clé pas vraiment secure mais bon c'est juste pour éviter la réplication à outrance";

	private static final PwdManager PWD_MGR;

	static {
		PwdManager pm = null;
		try {
			pm = new PwdManager();
		} catch (final Exception e) {
			pm = null;
		}
		PWD_MGR = pm;
	}

	public static String getInstanceID() throws BackingStoreException, IOException, GeneralSecurityException {
		final Preferences gd = getScsNode();
		String iId = gd.get(KEY_NAME_LIC, "");
		if (iId.length() > 8) return iId;
		else {
			iId = generateInstanceID();
			storeInstanceID(gd, iId);
			return iId;
		}
	}

	public static LicDate checkLicense(final String license) throws ParseException, BackingStoreException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException, NoSuchPaddingException, UnsupportedEncodingException {
		if (PWD_MGR == null) throw new IllegalStateException("Failed to initialize crypto libs");
		final String[] parts = license.split("g");
		final String instanceId = PWD_MGR.decNoUI(parts[0]);
		final String encInstanceId = HexUtils.toHexString(new AESEncrypter(AES_KEY, AESEncrypter.AES_128_KEY_SIZE).encrypt(instanceId.getBytes(StandardCharsets.UTF_8)));
		final Preferences gd = LicMgr.getScsNode();
		final String iId = gd.get(KEY_NAME_LIC, "");
		if (!encInstanceId.equals(iId)) return new LicDate("Invalid", false);
		final String licDateStr = PWD_MGR.decNoUI(parts[1]);
		final Date licDate = SDF.parse(licDateStr);
		if ("::lic::19810809::cil::".equals(licDateStr)) return LicDate.PERMANENT;
		if (new Date().after(licDate)) return new LicDate("Expired", false);
		return new LicDate(new SimpleDateFormat("EEE, d MMM yyyy").format(licDate), true);
	}

	private static String generateInstanceID() throws IOException, GeneralSecurityException { return HexUtils.toHexString(new AESEncrypter(AES_KEY, AESEncrypter.AES_128_KEY_SIZE).encrypt(new BigInteger(256, new SecureRandom()).toString(16).getBytes(StandardCharsets.UTF_8))); }

	private static void storeInstanceID(Preferences gd, String iId) {
		gd.put(KEY_NAME_LIC, iId);
	}

	private static Preferences getScsNode() throws BackingStoreException {
		Preferences pn = Preferences.systemRoot();
		final String[] nl = new String[] {"Sword", "gsp", "data"};
		for (final String s : nl) {
			Preferences cn;
			if (pn.nodeExists(s)) cn = pn.node(s);
			else {
				cn = pn.node(s);
				cn.flush();
			}
			pn = cn;
		}
		return pn;
	}
	
	public static class LicDate {
		
		public static final LicDate UNKNOWN_ERROR = new LicDate("Unknown error", false);
		public static final LicDate PERMANENT = new LicDate(null, true, false);
		
		public final String dateString;
		public final boolean isValid;
		public final boolean expires;
		
		public LicDate(String dateString, boolean isValid) {
			this(dateString, isValid, true);
		}

		private LicDate(String dateString, boolean isValid, boolean expires) {
			super();
			this.dateString = dateString;
			this.isValid = isValid;
			this.expires = expires;
		}
		
	}

}
