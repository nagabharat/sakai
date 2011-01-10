/**
 * Copyright (c) 2008-2010 The Sakai Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.profile2.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.sakaiproject.profile2.dao.ProfileDao;
import org.sakaiproject.profile2.model.Person;
import org.sakaiproject.profile2.model.ProfilePrivacy;
import org.sakaiproject.profile2.model.ProfileStatus;
import org.sakaiproject.profile2.model.WallItem;
import org.sakaiproject.profile2.util.ProfileConstants;

/**
 * Implementation of ProfileWallLogic API for Profile2 wall.
 * 
 * @author d.b.robinson@lancaster.ac.uk
 */
public class ProfileWallLogicImpl implements ProfileWallLogic {
	
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(ProfileWallLogic.class);
		
	/**
	 * Creates a new instance of <code>ProfileWallLogicImpl</code>.
	 */
	public ProfileWallLogicImpl() {
		
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public void addEventToWalls(String event, String userUuid) {

		// get the connections of the creator of this content
		List<Person> connections = null;
		connections = connectionsLogic.getConnectionsForUser(userUuid);

		if (null == connections || 0 == connections.size()) {
			// there are therefore no walls to post event to
			return;
		}

		WallItem item = new WallItem();

		item.setCreatorUuid(userUuid);
		item.setType(ProfileConstants.WALL_ITEM_TYPE_EVENT);
		item.setDate(new Date());
		// this string is mapped to a localized resource string in GUI
		item.setText(event);
		
		for (Person connection : connections) {
			dao.addNewWallItemForUser(connection.getUuid(), item);
		}
	}
	
	/**
 	 * {@inheritDoc}
 	 */
	public List<WallItem> getWallItemsForUser(String userUuid, ProfilePrivacy privacy) {

		if (null == userUuid) {
			throw new IllegalArgumentException("must provide user id");
		}

		final String currentUserUuid = sakaiProxy.getCurrentUserId();
		if (null == currentUserUuid) {
			throw new SecurityException(
					"You must be logged in to make a request for a user's wall items.");
		}

		if (null == privacy) {
			return new ArrayList<WallItem>();
		}

		if (false == StringUtils.equals(userUuid, currentUserUuid)) {
			if (false == privacyLogic.isUserXWallVisibleByUserY(userUuid,
					privacy, currentUserUuid, connectionsLogic
							.isUserXFriendOfUserY(userUuid, currentUserUuid))) {
				return new ArrayList<WallItem>();
			}
		}

		List<WallItem> wallItems = dao.getWallItemsForUser(userUuid).getWallItems();
		
		// add in any connection statuses
		List<Person> connections = connectionsLogic
				.getConnectionsForUser(userUuid);

		if (null == connections || 0 == connections.size()) {
			return wallItems;
		}

		for (Person connection : connections) {

			ProfileStatus connectionStatus = statusLogic
					.getUserStatus(connection.getUuid());

			if (null == connectionStatus) {
				continue;
			}

			// status privacy check
			final boolean allowedStatus;
			// current user is always allowed to see status of connections
			if (true == StringUtils.equals(userUuid, currentUserUuid)) {
				allowedStatus = true;
			// don't allow friend-of-a-friend	
			} else {
				allowedStatus = privacyLogic.isUserXStatusVisibleByUserY(
						userUuid, privacy, connection.getUuid(),
						connectionsLogic.isUserXFriendOfUserY(userUuid,
								connection.getUuid()));
			}

			if (true == allowedStatus) {
				
				WallItem wallItem = new WallItem();
				wallItem.setType(ProfileConstants.WALL_ITEM_TYPE_STATUS);
				wallItem.setCreatorUuid(connection.getUuid());
				wallItem.setDate(connectionStatus.getDateAdded());
				wallItem.setText(connectionStatus.getMessage());

				wallItems.add(wallItem);
			}
		}

		// wall items are comparable and need to be in order
		Collections.sort(wallItems);
		
		// dao limits wall items but we also need to ensure any connection
		// status updates don't push the number of wall items over limit
		if (wallItems.size() > ProfileConstants.MAX_WALL_ITEMS_WITH_CONNECTION_STATUSES) {			
			return wallItems.subList(0, ProfileConstants.MAX_WALL_ITEMS_WITH_CONNECTION_STATUSES);
		} else {
			return wallItems;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public List<WallItem> getWallItemsForUser(String userUuid) {
		return getWallItemsForUser(userUuid, privacyLogic
				.getPrivacyRecordForUser(userUuid));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int getWallItemsCount(String userUuid) {
		return getWallItemsCount(userUuid, privacyLogic
				.getPrivacyRecordForUser(userUuid));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public int getWallItemsCount(String userUuid, ProfilePrivacy privacy) {

		final String currentUserUuid = sakaiProxy.getCurrentUserId();
		if (null == sakaiProxy.getCurrentUserId()) {
			throw new SecurityException(
					"You must be logged in to make a request for a user's wall items.");
		}

		if (null == privacy) {
			return 0;
		}

		if (false == StringUtils.equals(userUuid, currentUserUuid)) {

			if (false == privacyLogic.isUserXWallVisibleByUserY(userUuid,
					privacy, currentUserUuid, connectionsLogic
							.isUserXFriendOfUserY(userUuid, currentUserUuid))) {
				return 0;
			}
		}
				
		List<WallItem> wallItems = dao.getWallItemsForUser(userUuid).getWallItems();
		
		int count = wallItems.size();
		
		// connection statuses
		List<Person> connections = connectionsLogic
				.getConnectionsForUser(userUuid);
		
		if (null == connections || 0 == connections.size()) {
			return count;
		}
		
		for (Person connection : connections) {
			
			if (null != statusLogic.getUserStatus(connection.getUuid())) {
				
				// current user is always allowed to see status of connections
				if (true == StringUtils.equals(userUuid, currentUserUuid)) {
					count++;
				// don't allow friend-of-a-friend
				} else if (true == privacyLogic.isUserXStatusVisibleByUserY(
						userUuid, privacy, connection.getUuid(),
						connectionsLogic.isUserXFriendOfUserY(userUuid,
								connection.getUuid()))) {
					count++;
				}
			}
		}
				
		if (count > ProfileConstants.MAX_WALL_ITEMS_WITH_CONNECTION_STATUSES) {
			return ProfileConstants.MAX_WALL_ITEMS_WITH_CONNECTION_STATUSES;
		} else {
			return count;
		}
	}
		
	// internal components
	private ProfileDao dao;
	public void setDao(ProfileDao dao) {
		this.dao = dao;
	}
	
	private ProfilePrivacyLogic privacyLogic;
	public void setPrivacyLogic(ProfilePrivacyLogic privacyLogic) {
		this.privacyLogic = privacyLogic;
	}
	
	private ProfileConnectionsLogic connectionsLogic;
	public void setConnectionsLogic(
			ProfileConnectionsLogic connectionsLogic) {
		this.connectionsLogic = connectionsLogic;
	}
	
	private ProfileStatusLogic statusLogic;
	public void setStatusLogic(ProfileStatusLogic statusLogic) {
		this.statusLogic = statusLogic;
	}

	private SakaiProxy sakaiProxy;
	public void setSakaiProxy(SakaiProxy sakaiProxy) {
		this.sakaiProxy = sakaiProxy;
	}
		
}
