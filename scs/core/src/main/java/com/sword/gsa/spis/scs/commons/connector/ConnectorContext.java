package com.sword.gsa.spis.scs.commons.connector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;

import org.apache.log4j.Logger;

import sword.connectors.commons.config.CP;
import sword.connectors.commons.config.ConnectorSpec;

import com.sword.gsa.spis.scs.commons.connector.models.IAuthNDelegator;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthNFormData;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthNHeaders;
import com.sword.gsa.spis.scs.commons.connector.models.IAuthorizer;
import com.sword.gsa.spis.scs.commons.connector.models.ICachableGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.IGroupRetriever;
import com.sword.gsa.spis.scs.commons.connector.models.Indexer;
import com.sword.gsa.spis.scs.commons.connector.models.INameTransformer;

public class ConnectorContext implements AutoCloseable, IConnectorContext {

	static final Logger LOG = Logger.getLogger(ConnectorContext.class);

	private static final String CONNECTOR_CLASSES_DIRNAME = "classes";
	private static final String CONNECTOR_LIB_DIRNAME = "lib";
	private static final String CONNECTOR_ENDORSED_DIRNAME = "endorsed";

	private static final String INDEXER_CONF_DIRNAME = "_conf";
	private static final String INDEXER_FEED_DIRNAME = "_feeds";
	private static final String INDEXER_DASHBOARD_DIRNAME = "_dashboard";
	private static final String INDEXER_LOCAL_DB_DIRNAME = "_local-db";
	private static final String INDEXER_WORK_DIRNAME = "_work";

	private static final String INDEXER_NEXT_START_FILENAME = "NextStart";
	private static final String INDEXER_CONF_FILENAME = "IndexerConf.xml";
	private static final String INDEXER_STOP_FILENAME = "stop";
	private static final String INDEXER_LOCK_FILENAME = "lock";

	private static final String GROUP_RETRIEVER_CACHE_DIRNAME = "_grcache";

	final Path homeDir;
	public final URLClassLoader classLoader;
	private final String className;
	private final ConnectorSpec spec;
	public final CP[] confParams;
	public final boolean isNameTransformer;
	public final boolean isGroupRetriever;
	public final boolean isCachableGroupRetriever;
	public final boolean isAuthorizer;
	public final boolean isAuthNHeaders;
	public final boolean isAuthNFormData;
	public final boolean isAuthNDelegator;
	public final boolean isIndexer;

	public ConnectorContext(final Path connectorHomeDir) throws IOException {
		super();
		homeDir = connectorHomeDir;

		final ClassLoader baseCl = this.getClass().getClassLoader();
		final Collection<URL> connectorLibs = new HashSet<>();
		final Path classesDir = connectorHomeDir.resolve(CONNECTOR_CLASSES_DIRNAME);
		connectorLibs.add(classesDir.toUri().toURL());
		final File libDir = connectorHomeDir.resolve(CONNECTOR_LIB_DIRNAME).toFile();
		if (libDir.exists()) for (final File f : libDir.listFiles())
			connectorLibs.add(f.toURI().toURL());
		classLoader = new URLClassLoader(connectorLibs.toArray(new URL[0]), baseCl);
		final ConnectorFinder cf = this.new ConnectorFinder(classesDir);
		try {
			Files.walkFileTree(classesDir, cf);
		} catch (Throwable e) {
			throw new IllegalArgumentException(connectorHomeDir + " is not a connector directory", e);
		}
		if (cf._className == null || cf.cs == null) throw new IllegalArgumentException(connectorHomeDir + " is not a connector directory");
		className = cf._className;
		spec = cf.cs;

		CP[] confParams;
		boolean isNameTransformer, isGroupRetriever, isCachableGroupRetriever, isAuthorizer, isAuthNHeaders, isAuthNFormData, isAuthNDelegator, isIndexer;
		try {
			final Class<?> clazz = classLoader.loadClass(className);
			final Field cpField = clazz.getDeclaredField(CP.CP_FIELD_NAME);
			confParams = (CP[]) cpField.get(null);
			isNameTransformer = INameTransformer.class.isAssignableFrom(clazz);
			isGroupRetriever = IGroupRetriever.class.isAssignableFrom(clazz);
			isCachableGroupRetriever = ICachableGroupRetriever.class.isAssignableFrom(clazz);
			isAuthorizer = IAuthorizer.class.isAssignableFrom(clazz);
			isAuthNHeaders = IAuthNHeaders.class.isAssignableFrom(clazz);
			isAuthNFormData = IAuthNFormData.class.isAssignableFrom(clazz);
			isAuthNDelegator = IAuthNDelegator.class.isAssignableFrom(clazz);
			isIndexer = Indexer.class.isAssignableFrom(clazz);
		} catch (final Throwable e) {
			throw new IllegalArgumentException(connectorHomeDir + " contains an unloadable connector class", e);
		}

		this.confParams = confParams;
		this.isNameTransformer = isNameTransformer;
		this.isGroupRetriever = isGroupRetriever;
		this.isCachableGroupRetriever = isCachableGroupRetriever;
		this.isAuthorizer = isAuthorizer;
		this.isAuthNHeaders = isAuthNHeaders;
		this.isAuthNFormData = isAuthNFormData;
		this.isAuthNDelegator = isAuthNDelegator;
		this.isIndexer = isIndexer;
	}

	
	@Override
	public Path getHomeDir() {
		return homeDir;
	}

	
	@Override
	public URLClassLoader getClassLoader() {
		return classLoader;
	}

	
	@Override
	public String getClassName() {
		return className;
	}

	
	@Override
	public ConnectorSpec getSpec() {
		return spec;
	}

	
	@Override
	public CP[] getConfParams() {
		return confParams;
	}

	
	@Override
	public boolean isNameTransformer() {
		return isNameTransformer;
	}

	
	@Override
	public boolean isGroupRetriever() {
		return isGroupRetriever;
	}

	
	@Override
	public boolean isCachableGroupRetriever() {
		return isCachableGroupRetriever;
	}

	
	@Override
	public boolean isAuthorizer() {
		return isAuthorizer;
	}

	
	@Override
	public boolean isAuthNHeaders() {
		return isAuthNHeaders;
	}

	
	@Override
	public boolean isAuthNFormData() {
		return isAuthNFormData;
	}

	
	@Override
	public boolean isAuthNDelegator() {
		return isAuthNDelegator;
	}

	
	@Override
	public boolean isIndexer() {
		return isIndexer;
	}

	@Override
	public Path getInstanceHomeDir(final String instanceId) {
		final Path instanceHomePath = homeDir.resolve(instanceId);
		final File instanceHomeFile = instanceHomePath.toFile();
		if (!instanceHomeFile.exists()) instanceHomeFile.mkdirs();
		return instanceHomePath;
	}

	@Override
	public Path getConnectorEndorsedDir() {
		return homeDir.resolve(CONNECTOR_LIB_DIRNAME).resolve(CONNECTOR_ENDORSED_DIRNAME);
	}

	@Override
	public Path getIndexerConfDir(final String instanceId) {
		return _getConnectorInstanceDir(instanceId, INDEXER_CONF_DIRNAME, true);
	}

	@Override
	public Path getIndexerFeedDir(final String instanceId) {
		return _getConnectorInstanceDir(instanceId, INDEXER_FEED_DIRNAME, true);
	}

	@Override
	public Path getIndexerDashboardDir(final String instanceId) {
		return _getConnectorInstanceDir(instanceId, INDEXER_DASHBOARD_DIRNAME, true);
	}

	@Override
	public Path getIndexerDBDir(final String instanceId) {
		return _getConnectorInstanceDir(instanceId, INDEXER_LOCAL_DB_DIRNAME, false);
	}

	@Override
	public Path getIndexerWorkDir(final String instanceId) {
		return _getConnectorInstanceDir(instanceId, INDEXER_WORK_DIRNAME, true);
	}

	@Override
	public Path getIndexerNextStartFile(final String instanceId) {
		final Path oldLocation = getInstanceHomeDir(instanceId).resolve(INDEXER_NEXT_START_FILENAME);
		final Path newLocation = _getConnectorInstanceDir(instanceId, INDEXER_WORK_DIRNAME, true).resolve(INDEXER_NEXT_START_FILENAME);
		if (oldLocation.toFile().exists()) try {
			Files.move(oldLocation, newLocation, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException e) {}// ignore
		return newLocation;
	}

	@Override
	public Path getIndexerConfFile(final String instanceId) throws IOException {
		final Path oldLocation = getInstanceHomeDir(instanceId).resolve(INDEXER_CONF_FILENAME);
		final Path newLocation = getIndexerConfDir(instanceId).resolve(INDEXER_CONF_FILENAME);
		if (oldLocation.toFile().exists()) Files.move(oldLocation, newLocation, StandardCopyOption.REPLACE_EXISTING);
		return newLocation;
	}

	@Override
	public Path getIndexerStopFile(final String instanceId) {
		final Path oldLocation = getInstanceHomeDir(instanceId).resolve(INDEXER_STOP_FILENAME);
		final File oldLocationFile = oldLocation.toFile();
		if (oldLocationFile.exists() && !oldLocationFile.delete()) oldLocationFile.deleteOnExit();
		return _getConnectorInstanceDir(instanceId, INDEXER_WORK_DIRNAME, true).resolve(INDEXER_STOP_FILENAME);
	}

	@Override
	public Path getIndexerLockFile(final String instanceId) {
		final Path oldLocation = getInstanceHomeDir(instanceId).resolve(INDEXER_LOCK_FILENAME);
		final File oldLocationFile = oldLocation.toFile();
		if (oldLocationFile.exists() && !oldLocationFile.delete()) oldLocationFile.deleteOnExit();
		return _getConnectorInstanceDir(instanceId, INDEXER_WORK_DIRNAME, true).resolve(INDEXER_LOCK_FILENAME);
	}

	@Override
	public Path getGroupRetrieverCacheDir(final String instanceId) {
		return _getConnectorInstanceDir(instanceId, GROUP_RETRIEVER_CACHE_DIRNAME, true);
	}

	private Path _getConnectorInstanceDir(final String instanceId, final String dirname, final boolean createIfNonExistent) {
		final Path indexerDirPath = getInstanceHomeDir(instanceId).resolve(dirname);
		if (createIfNonExistent) {
			final File indexerDirFile = indexerDirPath.toFile();
			if (!indexerDirFile.exists()) indexerDirFile.mkdirs();
		}
		return indexerDirPath;
	}

	@Override
	public void close() throws IOException {
		classLoader.close();
	}

	private class ConnectorFinder implements FileVisitor<Path> {

		private final Path rootClasses;
		String _className = null;
		ConnectorSpec cs = null;

		public ConnectorFinder(final Path rootClasses) {
			this.rootClasses = rootClasses;
		}

		@Override
		public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
			String className = file.toString();
			if (className.endsWith(".class")) {
				className = rootClasses.relativize(file).toString().replaceFirst(".class$", "").replace('\\', '.').replace('/', '.');
				try {
					final Class<?> clazz = classLoader.loadClass(className);
					final ConnectorSpec cs = clazz.getAnnotation(ConnectorSpec.class);
					if (cs != null) {
						_className = clazz.getName();
						this.cs = cs;
						return FileVisitResult.TERMINATE;
					}
				} catch (ClassNotFoundException | VerifyError e) {
					LOG.error("Invalid class: " + file, e);
				}
			}
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
			return FileVisitResult.CONTINUE;
		}

	}

}
