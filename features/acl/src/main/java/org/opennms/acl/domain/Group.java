/*******************************************************************************
 * This file is part of the OpenNMS(R) Application.
 *
 * Copyright (C) 2009 Massimiliano Dess&igrave; (desmax74@yahoo.it)
 * Copyright (C) 2009 The OpenNMS Group, Inc.
 * All rights reserved.
 *
 * This program was developed and is maintained by Rocco RIONERO
 * ("the author") and is subject to dual-copyright according to
 * the terms set in "The OpenNMS Project Contributor Agreement".
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc.:
 *
 *      51 Franklin Street
 *      5th Floor
 *      Boston, MA 02110-1301
 *      USA
 *
 * For more information contact:
 *
 *      OpenNMS Licensing <license@opennms.org>
 *      http://www.opennms.org/
 *      http://www.opennms.com/
 *
 *******************************************************************************/

package org.opennms.acl.domain;

import java.util.List;

import org.opennms.acl.model.GroupDTO;
import org.opennms.acl.model.GroupView;
import org.opennms.acl.model.Pager;
import org.opennms.acl.service.AuthorityService;
import org.opennms.acl.service.GroupService;

/**
 * This entity is a ACL group.
 * 
 * @author Massimiliano Dess&igrave; (desmax74@yahoo.it)
 * @since jdk 1.5.0
 */
public class Group {

    public Group(GroupDTO group, AuthorityService authorityService, GroupService groupService) {
        super();
        this.group = group;
        this.authorityService = authorityService;
        this.groupService = groupService;
        this.group.setEmptyUsers(groupService.hasUsers(group.getId()));
    }

    public List<?> getAuthorities() {
        return authorityService.getGroupAuthorities(group.getId());
    }

    /**
     * Return a list of all items manageable by authorities
     * 
     * @return all items
     */
    public List<?> getAllGroups() {
        return groupService.getGroups();
    }

    /**
     * Return a paginated list of anemic group
     * 
     * @param pager
     * @return
     */
    public List<GroupDTO> getGroups(Pager pager) {
        return groupService.getGroups(pager);
    }

    /**
     * Return a read only Group
     * 
     * @return authority
     */
    public GroupView getGroupView() {
        return group;
    }

    /**
     * @return hasAuthorities
     */
    public boolean hasAuthorities() {
        return group.getAuthorities().size() > 0;
    }

    /**
     * @return hasUsers
     */
    public boolean hasUser() {
        return group.getEmptyUsers();
    }

    /**
     * Save the internal state of the Group
     */
    public void save() {
        groupService.save(group);
    }

    /**
     * Overwrite the authorities assigned to this Group
     */
    public void setNewAuthorities(List<?> items) {
        group.setAuthorities(items);
    }

    /**
     * Remove this Group
     */
    public boolean remove() {
        return groupService.removeGroup(group.getId());
    }

    /**
     * Return the human readable description of this Group
     * 
     * @return description
     */
    public String getName() {
        return group.getName();
    }

    /**
     * Group unique identifier
     * 
     * @return group's identifier
     */
    public Integer getId() {
        return group.getId();
    }

    public List<?> getFreeAuthorities() {
        return authorityService.getFreeAuthoritiesForGroup();
    }

    private GroupDTO group;
    private AuthorityService authorityService;
    private GroupService groupService;
}
