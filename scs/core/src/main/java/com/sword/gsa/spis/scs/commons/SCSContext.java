package com.sword.gsa.spis.scs.commons;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.sword.gsa.spis.scs.commons.connector.ConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.IConnectorContext;
import com.sword.gsa.spis.scs.commons.connector.models.AConnector;
import com.sword.scs.Constants;
import org.apache.log4j.Logger;

public final class SCSContext implements AutoCloseable {

	public final Path tomcatRoot;
	public final Path scsConfDir;
	public final Path scsConnectorsDir;
	public final List<ConnectorContext> installedConnectors = new ArrayList<>();
	public final Logger LOG = Logger.getLogger(SCSContext.class);

	public SCSContext(final String catalinaHome) {
		tomcatRoot = new File(catalinaHome).toPath();
		scsConfDir = tomcatRoot.resolve(Constants.REL_PATH_SCS_CONF);
		scsConnectorsDir = tomcatRoot.resolve(Constants.REL_PATH_SCS_CONNECTORS);
		findConnectors();
	}

	private void findConnectors() {
		final File[] connectorsHomes = scsConnectorsDir.toFile().listFiles();
		LOG.trace("Looking for connectors in " + scsConnectorsDir);
		for (final File connectorsHome : connectorsHomes) {
			LOG.trace("- " + connectorsHome);
			try {
				installedConnectors.add(new ConnectorContext(connectorsHome.toPath()));
			} catch (final Exception e) {
				LOG.warn("Skipping " + connectorsHome, e);
			}
		}
		Collections.sort(installedConnectors, new Comparator<ConnectorContext>() {
			@Override
			public int compare(ConnectorContext o1, ConnectorContext o2) { return o1.getSpec().name().compareTo(o2.getSpec().name()); }
		});
	}

	public IConnectorContext getConnectorCtx(final String className) {
		for (final ConnectorContext cc : installedConnectors)
			if (cc.getClassName().equals(className)) return cc;
		throw new IllegalArgumentException("Connector " + className + " is not installed");
	}

	public IConnectorContext getConnectorCtx(final AConnector c) {
		return getConnectorCtx(c.getClass().getName());
	}

	@Override
	public void close() throws IOException {
		IOException ioe = null;
		for (final ConnectorContext cc : installedConnectors) {
			try {
				cc.close();
			} catch (IOException ex) {
				ioe = ex;
			}
		}
		if (ioe != null) throw ioe;
	}

}