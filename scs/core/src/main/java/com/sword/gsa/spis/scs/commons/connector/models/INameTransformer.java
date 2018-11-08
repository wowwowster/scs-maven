package com.sword.gsa.spis.scs.commons.connector.models;

public interface INameTransformer extends IConnector {

	public String transformName(String principalID) throws Exception;

}