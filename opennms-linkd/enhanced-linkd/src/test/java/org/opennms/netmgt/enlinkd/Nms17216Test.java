/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.enlinkd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.test.OpenNMSJUnit4ClassRunner;
import org.opennms.core.test.db.annotations.JUnitTemporaryDatabase;
import org.opennms.core.test.snmp.annotations.JUnitSnmpAgent;
import org.opennms.core.test.snmp.annotations.JUnitSnmpAgents;
import org.opennms.core.utils.BeanUtils;
import org.opennms.netmgt.config.EnhancedLinkdConfig;
import org.opennms.netmgt.dao.NodeDao;
import org.opennms.netmgt.dao.TopologyDao;
import org.opennms.netmgt.linkd.Nms17216NetworkBuilder;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.topology.Element;
import org.opennms.netmgt.model.topology.ElementIdentifier;
import org.opennms.netmgt.model.topology.ElementIdentifier.ElementIdentifierType;
import org.opennms.netmgt.model.topology.EndPoint;
import org.opennms.netmgt.model.topology.Link;
import org.opennms.netmgt.model.topology.LldpElementIdentifier;
import org.opennms.netmgt.model.topology.LldpEndPoint;
import org.opennms.netmgt.model.topology.NodeElementIdentifier;
import org.opennms.test.JUnitConfigurationEnvironment;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@RunWith(OpenNMSJUnit4ClassRunner.class)
@ContextConfiguration(locations= {
        "classpath:/META-INF/opennms/applicationContext-soa.xml",
        "classpath:/META-INF/opennms/applicationContext-daemon.xml",
        "classpath:/META-INF/opennms/applicationContext-proxy-snmp.xml",
        "classpath:/META-INF/opennms/mockEventIpcManager.xml",
        "classpath:/META-INF/opennms/applicationContext-enhancedLinkdTest.xml"
})
@JUnitConfigurationEnvironment
@JUnitTemporaryDatabase
public class Nms17216Test extends Nms17216NetworkBuilder implements InitializingBean {

    @Autowired
    private EnhancedLinkd m_linkd;

    @Autowired
    private EnhancedLinkdConfig m_linkdConfig;

    @Autowired
    private NodeDao m_nodeDao;
    
    @Autowired
    private TopologyDao m_topologyDao;
        
    @Override
    public void afterPropertiesSet() throws Exception {
        BeanUtils.assertAutowiring(this);
    }

    @Before
    public void setUp() throws Exception {
        Properties p = new Properties();
//        p.setProperty("log4j.logger.org.hibernate.SQL", "WARN");
//        p.setProperty("log4j.logger.org.hibernate.cfg", "WARN");
//        p.setProperty("log4j.logger.org.hibernate.impl", "WARN");
//        p.setProperty("log4j.logger.org.hibernate.hql", "WARN");
        p.setProperty("log4j.logger.org.opennms.netmgt.linkd.snmp", "WARN");
        p.setProperty("log4j.logger.org.opennms.netmgt.snmp", "WARN");
        p.setProperty("log4j.logger.org.opennms.netmgt.filter", "WARN");
        p.setProperty("log4j.logger.org.hibernate", "WARN");
        p.setProperty("log4j.logger.org.springframework","WARN");
        p.setProperty("log4j.logger.com.mchange.v2.resourcepool", "WARN");
        MockLogAppender.setupLogging(p);
    }

    @After
    public void tearDown() throws Exception {
        for (final OnmsNode node : m_nodeDao.findAll()) {
            m_nodeDao.delete(node);
        }
        m_nodeDao.flush();
    }
    
    /*
     * These are the links among the following nodes discovered using 
     * only the lldp protocol
     * switch1 Gi0/9 Gi0/10 Gi0/11 Gi0/12 ----> switch2 Gi0/1 Gi0/2 Gi0/3 Gi0/4
     * switch2 Gi0/19 Gi0/20              ----> switch3 Fa0/19 Fa0/20
     * 
     * here are the corresponding ifindex:
     * switch1 Gi0/9 --> 10109
     * switch1 Gi0/10 --> 10110
     * switch1 Gi0/11 --> 10111
     * switch1 Gi0/12 --> 10112
     * 
     * switch2 Gi0/1 --> 10101
     * switch2 Gi0/2 --> 10102
     * switch2 Gi0/3 --> 10103
     * switch2 Gi0/4 --> 10104
     * switch2 Gi0/19 --> 10119
     * switch2 Gi0/20 --> 10120
     * 
     * switch3 Fa0/19 -->  10019
     * switch3 Fa0/20 -->  10020
     * 
     * Here we add cdp discovery and all test lab devices
     * To the previuos links discovered by lldp
     * should be added the followings discovered with cdp:
     * switch3 Fa0/23 Fa0/24 ---> switch5 Fa0/1 Fa0/9
     * router1 Fa0/0 ----> switch1 Gi0/1
     * router2 Serial0/0/0 ----> router1 Serial0/0/0
     * router3 Serial0/0/1 ----> router2 Serial0/0/1
     * router4 GigabitEthernet0/1 ----> router3   GigabitEthernet0/0
     * switch4 FastEthernet0/1    ----> router3   GigabitEthernet0/1
     * 
     * here are the corresponding ifindex:
     * switch1 Gi0/1 -->  10101
     * 
     * switch3 Fa0/23 -->  10023
     * switch3 Fa0/24 -->  10024
     *
     * switch5 Fa0/1 -->  10001
     * switch5 Fa0/13 -->  10013
     * 
     * router1 Fa0/0 -->  7
     * router1 Serial0/0/0 --> 13
     * router1 Serial0/0/1 --> 14
     * 
     * router2 Serial0/0/0 --> 12
     * router2 Serial0/0/1 --> 13
     * 
     * router3 Serial0/0/1 --> 13
     * router3 GigabitEthernet0/0 --> 8
     * router3 GigabitEthernet0/1 --> 9
     * 
     * router4 GigabitEthernet0/1  --> 3
     * 
     * switch4 FastEthernet0/1 --> 10001
     * 
     */
    @Test
    @Ignore
    @JUnitSnmpAgents(value={
            @JUnitSnmpAgent(host=SWITCH1_IP, port=161, resource="classpath:linkd/nms17216/switch1-walk.txt"),
            @JUnitSnmpAgent(host=SWITCH2_IP, port=161, resource="classpath:linkd/nms17216/switch2-walk.txt"),
            @JUnitSnmpAgent(host=SWITCH3_IP, port=161, resource="classpath:linkd/nms17216/switch3-walk.txt"),
            @JUnitSnmpAgent(host=SWITCH4_IP, port=161, resource="classpath:linkd/nms17216/switch4-walk.txt"),
            @JUnitSnmpAgent(host=SWITCH5_IP, port=161, resource="classpath:linkd/nms17216/switch5-walk.txt"),
            @JUnitSnmpAgent(host=ROUTER1_IP, port=161, resource="classpath:linkd/nms17216/router1-walk.txt"),
            @JUnitSnmpAgent(host=ROUTER2_IP, port=161, resource="classpath:linkd/nms17216/router2-walk.txt"),
            @JUnitSnmpAgent(host=ROUTER3_IP, port=161, resource="classpath:linkd/nms17216/router3-walk.txt"),
            @JUnitSnmpAgent(host=ROUTER4_IP, port=161, resource="classpath:linkd/nms17216/router4-walk.txt")
    })
    public void testNetwork17216Links() throws Exception {
        
        m_nodeDao.save(getSwitch1());
        m_nodeDao.save(getSwitch2());
        m_nodeDao.save(getSwitch3());
        m_nodeDao.save(getSwitch4());
        m_nodeDao.save(getSwitch5());
        m_nodeDao.save(getRouter1());
        m_nodeDao.save(getRouter2());
        m_nodeDao.save(getRouter3());
        m_nodeDao.save(getRouter4());

        m_nodeDao.flush();

        final OnmsNode switch1 = m_nodeDao.findByForeignId("linkd", SWITCH1_NAME);
        final OnmsNode switch2 = m_nodeDao.findByForeignId("linkd", SWITCH2_NAME);
        final OnmsNode switch3 = m_nodeDao.findByForeignId("linkd", SWITCH3_NAME);
        final OnmsNode switch4 = m_nodeDao.findByForeignId("linkd", SWITCH4_NAME);
        final OnmsNode switch5 = m_nodeDao.findByForeignId("linkd", SWITCH5_NAME);
        final OnmsNode router1 = m_nodeDao.findByForeignId("linkd", ROUTER1_NAME);
        final OnmsNode router2 = m_nodeDao.findByForeignId("linkd", ROUTER2_NAME);
        final OnmsNode router3 = m_nodeDao.findByForeignId("linkd", ROUTER3_NAME);
        final OnmsNode router4 = m_nodeDao.findByForeignId("linkd", ROUTER4_NAME);
        
        assertTrue(m_linkd.scheduleNodeCollection(switch1.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(switch2.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(switch3.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(switch4.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(switch5.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(router1.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(router2.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(router3.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(router4.getId()));

        assertTrue(m_linkd.runSingleSnmpCollection(switch1.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(switch2.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(switch3.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(switch4.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(switch5.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(router1.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(router2.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(router3.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(router4.getId()));

        /*       
        assertEquals(0,m_dataLinkInterfaceDao.countAll());
        
        final Collection<LinkableNode> nodes = m_linkd.getLinkableNodesOnPackage("example1");

        assertEquals(9, nodes.size());
        
        for (LinkableNode node: nodes) {
            switch(node.getNodeId()) {
                case 1: assertEquals(5, node.getCdpInterfaces().size());
                assertEquals(SWITCH1_NAME, node.getCdpDeviceId());
                break;
                case 2: assertEquals(6, node.getCdpInterfaces().size());
                assertEquals(SWITCH2_NAME, node.getCdpDeviceId());
                break;
                case 3: assertEquals(4, node.getCdpInterfaces().size());
                assertEquals(SWITCH3_NAME, node.getCdpDeviceId());
                break;
                case 4: assertEquals(1, node.getCdpInterfaces().size());
                assertEquals(SWITCH4_NAME, node.getCdpDeviceId());
                break;
                case 5: assertEquals(2, node.getCdpInterfaces().size());
                assertEquals(SWITCH5_NAME, node.getCdpDeviceId());
                break;
                case 6: assertEquals(2, node.getCdpInterfaces().size());
                assertEquals(ROUTER1_NAME, node.getCdpDeviceId());
                break;
                case 7: assertEquals(2, node.getCdpInterfaces().size());
                assertEquals(ROUTER2_NAME, node.getCdpDeviceId());
                break;
                case 8: assertEquals(3, node.getCdpInterfaces().size());
                assertEquals(ROUTER3_NAME, node.getCdpDeviceId());
                break;
                case 9: assertEquals(1, node.getCdpInterfaces().size());
                assertEquals(ROUTER4_NAME, node.getCdpDeviceId());
                break;
                default: assertEquals(-1, node.getNodeId());
                break;
            }
        }        
        
        assertTrue(m_linkd.runSingleLinkDiscovery("example1"));

        assertEquals(13,m_dataLinkInterfaceDao.countAll());
        final List<DataLinkInterface> datalinkinterfaces = m_dataLinkInterfaceDao.findAll();

        int start=getStartPoint(datalinkinterfaces);

        for (final DataLinkInterface datalinkinterface: datalinkinterfaces) {
            Integer linkid = datalinkinterface.getId();
            if ( linkid == start) {
                // switch1 gi0/9 -> switch2 gi0/1 --lldp --cdp
                checkLink(switch2, switch1, 10101, 10109, datalinkinterface);
            } else if (linkid == start+1 ) {
                // switch1 gi0/10 -> switch2 gi0/2 --lldp --cdp
                checkLink(switch2, switch1, 10102, 10110, datalinkinterface);
            } else if (linkid == start+2) {
                // switch1 gi0/11 -> switch2 gi0/3 --lldp --cdp
                checkLink(switch2, switch1, 10103, 10111, datalinkinterface);
            } else if (linkid == start+3) {
                // switch1 gi0/12 -> switch2 gi0/4 --lldp --cdp
                checkLink(switch2, switch1, 10104, 10112, datalinkinterface);
            } else if (linkid == start+4) {
                // switch2 gi0/19 -> switch3 Fa0/19 --lldp --cdp
                checkLink(switch3, switch2, 10019, 10119, datalinkinterface);
            } else if (linkid == start+5) {
                // switch2 gi0/20 -> switch3 Fa0/20 --lldp --cdp
                checkLink(switch3, switch2, 10020, 10120, datalinkinterface);
            } else if (linkid == start+6) {
                // switch1 gi0/1 -> router1 Fa0/20 --cdp
                checkLink(router1, switch1, 7, 10101, datalinkinterface);
            } else if (linkid == start+7) {
                // switch3 Fa0/1 -> switch5 Fa0/23 --cdp
                checkLink(switch5, switch3, 10001, 10023, datalinkinterface);
            } else if (linkid == start+8) {
                // switch3 gi0/1 -> switch5 Fa0/20 --cdp
                checkLink(switch5, switch3, 10013, 10024, datalinkinterface);
            } else if (linkid == start+9) {
                //switch4 FastEthernet0/1    ----> router3   GigabitEthernet0/1
                checkLink(router3, switch4, 9, 10001, datalinkinterface);
            } else if (linkid == start+10) {
                checkLink(router2, router1, 12, 13, datalinkinterface);
            } else if (linkid == start+11) {
                checkLink(router3, router2, 13, 13, datalinkinterface);
            } else if (linkid == start+12) {
                checkLink(router4, router3, 3, 8, datalinkinterface);
            } else {
                // error
                checkLink(switch1,switch1,-1,-1,datalinkinterface);
            }      
        }*/
    }

    /*
     * These are the links among the following nodes discovered using 
     * only the lldp protocol
     * switch1 Gi0/9 Gi0/10 Gi0/11 Gi0/12 ----> switch2 Gi0/1 Gi0/2 Gi0/3 Gi0/4
     * switch2 Gi0/19 Gi0/20              ----> switch3 Fa0/19 Fa0/20
     * 
     * here are the corresponding ifindex:
     * switch1 Gi0/9 --> 10109
     * switch1 Gi0/10 --> 10110
     * switch1 Gi0/11 --> 10111
     * switch1 Gi0/12 --> 10112
     * 
     * switch2 Gi0/1 --> 10101
     * switch2 Gi0/2 --> 10102
     * switch2 Gi0/3 --> 10103
     * switch2 Gi0/4 --> 10104
     * switch2 Gi0/19 --> 10119
     * switch2 Gi0/20 --> 10120
     * 
     * switch3 Fa0/19 -->  10019
     * switch3 Fa0/20 -->  10020
     * 
     */
    @Test
    @JUnitSnmpAgents(value={
            @JUnitSnmpAgent(host=SWITCH1_IP, port=161, resource="classpath:linkd/nms17216/switch1-walk.txt"),
            @JUnitSnmpAgent(host=SWITCH2_IP, port=161, resource="classpath:linkd/nms17216/switch2-walk.txt"),
            @JUnitSnmpAgent(host=SWITCH3_IP, port=161, resource="classpath:linkd/nms17216/switch3-walk.txt")
    })
    public void testNetwork17216LldpLinks() throws Exception {
        m_nodeDao.save(getSwitch1());
        m_nodeDao.save(getSwitch2());
        m_nodeDao.save(getSwitch3());
        m_nodeDao.flush();

        m_linkdConfig.getConfiguration().setUseBridgeDiscovery(false);
        m_linkdConfig.getConfiguration().setUseCdpDiscovery(false);
        m_linkdConfig.getConfiguration().setUseOspfDiscovery(false);
        m_linkdConfig.getConfiguration().setUseLldpDiscovery(true);

        assertTrue(m_linkdConfig.useLldpDiscovery());
        assertTrue(!m_linkdConfig.useCdpDiscovery());
        assertTrue(!m_linkdConfig.useOspfDiscovery());
        assertTrue(!m_linkdConfig.useBridgeDiscovery());

        final OnmsNode switch1 = m_nodeDao.findByForeignId("linkd", SWITCH1_NAME);
        final OnmsNode switch2 = m_nodeDao.findByForeignId("linkd", SWITCH2_NAME);
        final OnmsNode switch3 = m_nodeDao.findByForeignId("linkd", SWITCH3_NAME);
        
        assertTrue(m_linkd.scheduleNodeCollection(switch1.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(switch2.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(switch3.getId()));
 
        final List<Element> topologyA = m_topologyDao.getTopology();
        List<EndPoint> endpoints = printTopology(topologyA);
        List<Link> links = printLink(topologyA);
        assertEquals(0,topologyA.size());
        assertEquals(0, endpoints.size());
        assertEquals(0, links.size());
        
        assertTrue(m_linkd.runSingleSnmpCollection(switch1.getId()));
        endpoints = printTopology(topologyA);
        links = printLink(topologyA);
        assertEquals(2,topologyA.size());
        assertEquals(8, endpoints.size());
        assertEquals(4, links.size());

        assertTrue(m_linkd.runSingleSnmpCollection(switch2.getId()));
        endpoints = printTopology(topologyA);
        links = printLink(topologyA);
        assertEquals(3,topologyA.size());
        assertEquals(12, endpoints.size());
        assertEquals(6, links.size());
       
        assertTrue(m_linkd.runSingleSnmpCollection(switch3.getId()));
        endpoints = printTopology(topologyA);
        links = printLink(topologyA);
        assertEquals(3,topologyA.size());
        assertEquals(12, endpoints.size());
        assertEquals(6, links.size());

    }

    @Test
    @Ignore
    @JUnitSnmpAgents(value={
            @JUnitSnmpAgent(host=SWITCH4_IP, port=161, resource="classpath:linkd/nms17216/switch4-walk.txt"),
            @JUnitSnmpAgent(host=ROUTER3_IP, port=161, resource="classpath:linkd/nms17216/router3-walk.txt")
    })
    public void testNetwork17216Switch4Router4CdpLinks() throws Exception {
        
        m_nodeDao.save(getSwitch4());
        m_nodeDao.save(getRouter3());

        m_nodeDao.flush();

        m_linkdConfig.getConfiguration().setUseBridgeDiscovery(false);
        m_linkdConfig.getConfiguration().setUseLldpDiscovery(false);
        m_linkdConfig.getConfiguration().setUseOspfDiscovery(false);
        m_linkdConfig.getConfiguration().setUseCdpDiscovery(true);

        
        final OnmsNode switch4 = m_nodeDao.findByForeignId("linkd", SWITCH4_NAME);
        final OnmsNode router3 = m_nodeDao.findByForeignId("linkd", ROUTER3_NAME);
        
        assertTrue(m_linkd.scheduleNodeCollection(switch4.getId()));
        assertTrue(m_linkd.scheduleNodeCollection(router3.getId()));

        assertTrue(m_linkd.runSingleSnmpCollection(switch4.getId()));
        assertTrue(m_linkd.runSingleSnmpCollection(router3.getId()));
       
//        assertEquals(0,m_dataLinkInterfaceDao.countAll());


        
        final Collection<LinkableNode> nodes = m_linkd.getLinkableNodes();

        assertEquals(2, nodes.size());
        
//        for (LinkableNode node: nodes) {
  //          assertEquals(1, node.getCdpInterfaces().size());
  //      }
        
 //       assertTrue(m_linkd.runSingleLinkDiscovery("example1"));

 //       assertEquals(1,m_dataLinkInterfaceDao.countAll());
 //       final List<DataLinkInterface> datalinkinterfaces = m_dataLinkInterfaceDao.findAll();
                
 //       for (final DataLinkInterface datalinkinterface: datalinkinterfaces) {

//                checkLink(router3, switch4, 9, 10001, datalinkinterface);
               
//        }
    }
    
    private List<EndPoint> printTopology(final List<Element> topology) {

    	List<EndPoint> endpoints = new ArrayList<EndPoint>();

    	int i=1;
        for (final Element e: topology) {
        	System.err.println("----------element "+i+"--------");
        	for (ElementIdentifier iden: e.getElementIdentifiers()) {
            	System.err.println("----------element identifier--------");
    			System.err.println("Identifier type: " + ElementIdentifierType.getTypeString(iden.getType().getIntCode()));
    			if (iden.getType().equals(ElementIdentifierType.ONMSNODE)) 
        			System.err.println("Identifier node: " + ((NodeElementIdentifier)iden).getNodeid());
    			else if (iden.getType().equals(ElementIdentifierType.LLDP))
    				System.err.println("Identifier lldp: " + ((LldpElementIdentifier)iden).getLldpChassisId());
        	}
        	for (EndPoint ep: e.getEndpoints()) {
            	System.err.println("----------endpoint identifier--------");
        		LldpEndPoint lldpep = (LldpEndPoint) ep;
        		System.err.println("Found Endpoint: " + lldpep.getLldpPortId());
        		if (!endpoints.contains(ep))
        			endpoints.add(ep);
        	}
        	i++;
        }
        return endpoints;
	
    }

    private List<Link> printLink(final List<Element> topology) {

    	List<Link> links = new ArrayList<Link>();

        for (final Element e: topology) {
        	for (EndPoint ep: e.getEndpoints()) {
        		if (ep.hasLink() && !links.contains(ep.getLink()))
        			links.add(ep.getLink());
        	}
        }
        return links;
	
    }

}
