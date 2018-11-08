package com.sword.gsa.spis.scs.connectormanager;

import sword.connectors.commons.config.ConnectorSpec;

import com.sword.gsa.spis.scs.SCS;

public class ConnectorSPIConstants {

	public static final String CONNECTOR_MANAGER_NAME = "Google Search Appliance Connector Manager 3.2.x compatible";
	public static final String JAVA_VERSION = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");
	public static final String OS_VERSION = System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")";
	public static final String SCS_CONNECTOR_VERSION = ConnectorSPIConstants.getSCSVersion();
	public static final String SCS_PSEUDO_CONNECTOR_MGR_INFO = "<Info>" + CONNECTOR_MANAGER_NAME + " (" + SCS_CONNECTOR_VERSION + "); " + JAVA_VERSION + "; " + OS_VERSION + "</Info>";

	private static String getSCSVersion() {
		String a = null;
		try {
			ConnectorSpec cs = SCS.class.getAnnotation(ConnectorSpec.class);
			a = cs.version();
		} catch (final Exception e) {
			a = "UNKNOWN";
		}
		return a;
	}
}
