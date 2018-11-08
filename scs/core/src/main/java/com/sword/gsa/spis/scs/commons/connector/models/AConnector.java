package com.sword.gsa.spis.scs.commons.connector.models;

import java.util.Map;

public abstract class AConnector implements IConnector {

	public final String uniqueId;
	public final String namespace;
	public final String nameTransformer;
	public final Map<String, String> cpMap;

	public AConnector(final String uniqueId, final Map<String, String> configurationParameters, final String namespace, final String nameTransformer) {
		super();
		this.uniqueId = uniqueId;
		this.namespace = namespace;
		this.nameTransformer = nameTransformer;
		cpMap = configurationParameters;
	}
	
	@Override
	public final String getUniqueId() {
		return uniqueId;
	}
	
	@Override
	public final String getNamespace() {
		return namespace;
	}
	
	@Override
	public final String getNameTransformer() {
		return nameTransformer;
	}
	
	@Override
	public final Map<String, String> getCpMap() {
		return cpMap;
	}

}
