package com.sword.gsa.spis.scs.commons.connector.models;

import java.util.Map;

public interface IConnector {
	
	public String getUniqueId();

	public String getNamespace();

	public String getNameTransformer();

	public Map<String, String> getCpMap();

}
