package com.sword.scs.utils;

import sword.common.crypto.aes.AESDecrypter;
import sword.common.crypto.aes.AESEncrypter;
import sword.common.security.PwdManager;
import sword.common.utils.HexUtils;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Scanner;

public class LicenseGenerator {

	private static final PwdManager PWD_MGR;
	
	static {
		PwdManager pm = null;
		try { pm = new PwdManager(); } catch (Exception e) { pm = null; }
		PWD_MGR = pm;
	}

	public static void main(String[] args) {
		try (Scanner s = new Scanner(System.in)) {
			System.out.println("Instance ID?");
			String lid = s.nextLine();
			System.out.println("License expiration date (yyyyMMdd)?");
			String dateStr = s.nextLine();
			generateLicense(lid, dateStr);
		}
	}
	
	 static void generateLicense(String encInstanceId, String expiryDate) {
		try {

			byte[] encInstanceIdBytes = HexUtils.toRawBytes(encInstanceId);
			byte[] instanceIdBytes = new AESDecrypter(LicMgr.AES_KEY, AESEncrypter.AES_128_KEY_SIZE).decrypt(encInstanceIdBytes);
			String instanceId = new String(instanceIdBytes, StandardCharsets.UTF_8);
			String instanceIdPart = PWD_MGR.encNoUI(instanceId);
			String datePart = PWD_MGR.encNoUI(LicMgr.SDF.format(new SimpleDateFormat("yyyyMMdd").parse(expiryDate)));
			
			final String license = String.format("%sg%s", instanceIdPart, datePart);
			System.out.println(license);
			
			
			System.out.println(LicMgr.SDF.parse(PWD_MGR.decNoUI(license.split("g")[1])));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static String generateLicenseString(String encInstanceId, String expiryDate) {
		try {

			byte[] encInstanceIdBytes = HexUtils.toRawBytes(encInstanceId);
			byte[] instanceIdBytes = new AESDecrypter(LicMgr.AES_KEY, AESEncrypter.AES_128_KEY_SIZE).decrypt(encInstanceIdBytes);
			String instanceId = new String(instanceIdBytes, StandardCharsets.UTF_8);
			String instanceIdPart = PWD_MGR.encNoUI(instanceId);
			String datePart = PWD_MGR.encNoUI(LicMgr.SDF.format(new SimpleDateFormat("yyyyMMdd").parse(expiryDate)));

			final String license = String.format("%sg%s", instanceIdPart, datePart);
			return license;

		} catch (Exception e) {
			e.printStackTrace();
			return "ERROR";
		}
	}

}
