package com.sword.scs;

import static sword.common.utils.EnvUtils.IS_WINDOWS;

import java.io.File;

public final class Constants {

	public static final String JDK_DIR_NAME = "jdk";
	public static final String REL_PATH_JDK_BIN = String.format("%s%cbin", JDK_DIR_NAME, File.separatorChar);
	public static final String REL_PATH_KEYTOOL = String.format("%s%ckeytool%s", REL_PATH_JDK_BIN, File.separatorChar, IS_WINDOWS ? ".exe" : "");
	public static final String REL_PATH_JAVA = String.format("%s%cjava%s", REL_PATH_JDK_BIN, File.separatorChar, IS_WINDOWS ? ".exe" : "");
	public static final String REL_PATH_JAVAW = String.format("%s%cjava%s", REL_PATH_JDK_BIN, File.separatorChar, IS_WINDOWS ? "w.exe" : "");//javaw does not exist on Linux
	public static final String REL_PATH_JPS = String.format("%s%cjps%s", REL_PATH_JDK_BIN, File.separatorChar, IS_WINDOWS ? ".exe" : "");
	public static final String REL_PATH_CACERTS = String.format("%s%cjre%clib%csecurity%ccacerts", JDK_DIR_NAME, File.separatorChar, File.separatorChar, File.separatorChar, File.separatorChar);

	public static final String SCS_WORK_DIR_NAME = "scs";
	public static final String SCS_OLD_WORK_DIR_NAME = "gsp";
	public static final String REL_PATH_SCS_CONF = String.format("%s%cconf", SCS_WORK_DIR_NAME, File.separatorChar);
	public static final String REL_PATH_SCS_OLD_CONF = String.format("%s%cconf", SCS_OLD_WORK_DIR_NAME, File.separatorChar);
	public static final String REL_PATH_SCS_CONNECTORS = String.format("%s%cconnectors", SCS_WORK_DIR_NAME, File.separatorChar);
	public static final String REL_PATH_SCS_BIN = String.format("%s%cbin", SCS_WORK_DIR_NAME, File.separatorChar);
	public static final String REL_PATH_SCS_TMP = String.format("%s%ctmp", SCS_WORK_DIR_NAME, File.separatorChar);
	
	public static final String REL_PATH_SCS_SVC_FILE = String.format("%s%cSvcDef.bin", REL_PATH_SCS_CONF, File.separatorChar);
	public static final String REL_PATH_SCS_OLD_SVC_FILE = String.format("%s%cSvcDef.bin", REL_PATH_SCS_OLD_CONF, File.separatorChar);
	public static final String REL_PATH_SCS_KEYSTORE = String.format("%s%ckeystore.jks", REL_PATH_SCS_CONF, File.separatorChar);
	public static final String REL_PATH_SCS_TRUSTSTORE = String.format("%s%ctruststore.jks", REL_PATH_SCS_CONF, File.separatorChar);
	public static final String REL_PATH_SCS_CONF_FILE = String.format("%s%cconfig.xml", REL_PATH_SCS_CONF, File.separatorChar);
	public static final String REL_PATH_SCS_JAAS_CONF = String.format("%s%cjaas.conf", REL_PATH_SCS_CONF, File.separatorChar);
	public static final String REL_PATH_SCS_KRB_CONF = String.format("%s%ckrb.conf", REL_PATH_SCS_CONF, File.separatorChar);
	public static final String REL_PATH_SCS_KRB_KEYTAB = String.format("%s%cscs.keytab", REL_PATH_SCS_CONF, File.separatorChar);

	public static final String ENVPATH_STORE_FILE = "EnvPath";

	public static final String SCS_SVC_BASENAME = "SwordGSP";

	public static final String STORES_PWD = "gLCWLIayGU2iEDcH";

	public static final String SERVER_KS_ENTRY_NAME = "tomcat";

	public static final String SAML_KS_ENTRY_NAME = "GspSaml";
	public static final String SAML_KEY_CN = "saml.gsp";

	public static final int SVC_INSTALLED = 0b001;
	public static final int SVC_RUNNING = 0b010;
	public static final int SVC_STOPPED = 0b100;

	public static final String DO_NOT_CHANGE = "do§notchange¤¤µ";

}
