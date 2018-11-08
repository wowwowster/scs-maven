package com.sword.gsa.spis.scs.commons.acl.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import sword.gsa.xmlfeeds.builder.acl.Group;
import sword.gsa.xmlfeeds.builder.acl.Principal;
import sword.gsa.xmlfeeds.builder.acl.User;

public abstract class FeedableGroupCache extends GroupCache implements Serializable {
	
	private static final long serialVersionUID = 1L;

	public abstract Iterable<Group> getGroups();

	/**
	 * @return
	 * The list of all users
	 * @throws UsersAsGroupsAlreadyResolved
	 * see class Javadoc comment
	 */
	public abstract Iterable<User> getUsers() throws UsersAsGroupsAlreadyResolved;

	public Collection<Group> getUserGroups(User u) {
		
		Collection<Group> userGroups = new HashSet<>();
		
		Collection<Principal> alreadyCheckedPrincipals = new HashSet<>();//Avoids infinite loops due to cycles in group memberships

		List<Group> allGroups = new ArrayList<>();
		for (Group g: getGroups()) allGroups.add(g);
		
		List<Principal> currentPrincipalsToCheck = new ArrayList<>();
		currentPrincipalsToCheck.add(u);

		List<Principal> newPrincipalsToCheck = new ArrayList<>();
		
		//Iterative approach to avoid StackOverflowException when number of nested groups is too high
		while (!currentPrincipalsToCheck.isEmpty()) {
			
			newPrincipalsToCheck.clear();
			
			alreadyCheckedPrincipals.addAll(currentPrincipalsToCheck);
			
			for (Group g: allGroups) {
				for (Principal p: currentPrincipalsToCheck) {
					if (g.hasMember(p)) {
						userGroups.add(g);
						if (!alreadyCheckedPrincipals.contains(g)) {
							newPrincipalsToCheck.add(g);
						}
					}
				}
			}
			
			currentPrincipalsToCheck.clear();
			currentPrincipalsToCheck.addAll(newPrincipalsToCheck);
			
		}
		userGroups.add(new Group(u.principal, u.namespace));
		return userGroups;
		
	}

}
