/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2022.                            (c) 2022.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
************************************************************************
*/

package org.opencadc.vospace.db;

import ca.nrc.cadc.db.ConnectionConfig;
import ca.nrc.cadc.db.DBConfig;
import ca.nrc.cadc.db.DBUtil;
import ca.nrc.cadc.io.ResourceIterator;
import ca.nrc.cadc.util.Log4jInit;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opencadc.inventory.db.SQLGenerator;
import org.opencadc.inventory.db.TestUtil;
import org.opencadc.vospace.ContainerNode;
import org.opencadc.vospace.DataNode;
import org.opencadc.vospace.LinkNode;
import org.opencadc.vospace.Node;
import org.opencadc.vospace.NodeProperty;
import org.opencadc.vospace.VOS;

/**
 *
 * @author pdowler
 */
public class NodeDAOTest {
    private static final Logger log = Logger.getLogger(NodeDAOTest.class);

    static {
        Log4jInit.setLevel("org.opencadc.inventory", Level.INFO);
        Log4jInit.setLevel("org.opencadc.inventory.db", Level.DEBUG);
        Log4jInit.setLevel("ca.nrc.cadc.db", Level.INFO);
        Log4jInit.setLevel("org.opencadc.vospace", Level.INFO);
        Log4jInit.setLevel("org.opencadc.vospace.db", Level.DEBUG);
    }
    
    NodeDAO nodeDAO;
    
    public NodeDAOTest() throws Exception {
        try {
            DBConfig dbrc = new DBConfig();
            ConnectionConfig cc = dbrc.getConnectionConfig(TestUtil.SERVER, TestUtil.DATABASE);
            DBUtil.createJNDIDataSource("jdbc/ArtifactDAOTest", cc);

            Map<String,Object> config = new TreeMap<String,Object>();
            config.put(SQLGenerator.class.getName(), SQLGenerator.class);
            config.put("jndiDataSourceName", "jdbc/ArtifactDAOTest");
            config.put("database", TestUtil.DATABASE);
            config.put("schema", TestUtil.SCHEMA);
            config.put("vosSchema", TestUtil.VOS_SCHEMA);
            
            this.nodeDAO = new NodeDAO();
            nodeDAO.setConfig(config);
            
        } catch (Exception ex) {
            log.error("setup failed", ex);
            throw ex;
        }
    }
    
    @Before
    public void init_cleanup() throws Exception {
        log.info("init database...");
        InitDatabaseVOS init = new InitDatabaseVOS(nodeDAO.getDataSource(), TestUtil.DATABASE, TestUtil.VOS_SCHEMA);
        init.doInit();
        log.info("init database... OK");
        
        log.info("clearing old content...");
        SQLGenerator gen = nodeDAO.getSQLGenerator();
        DataSource ds = nodeDAO.getDataSource();
        String sql = "delete from " + gen.getTable(ContainerNode.class);
        log.info("pre-test cleanup: " + sql);
        ds.getConnection().createStatement().execute(sql);
        log.info("clearing old content... OK");
    }
    
    @Test
    public void testGetByID() {
        UUID id = UUID.randomUUID();
        Node a = nodeDAO.get(id);
        Assert.assertNull(a);
    }
    
    @Test
    public void testPutGetUpdateDeleteContainerNode() throws InterruptedException {
        UUID rootID = new UUID(0L, 0L);
        ContainerNode root = new ContainerNode(rootID, "root", false);
        
        // put
        ContainerNode orig = new ContainerNode("container-test", false);
        orig.parent = root;
        orig.ownerID = "the-owner";
        nodeDAO.put(orig);
        
        // get
        Node a = nodeDAO.get(orig.getID());
        Assert.assertNotNull(a);
        log.info("found: "  + a.getID() + " aka " + a);
        Assert.assertEquals(orig.getID(), a.getID());
        Assert.assertEquals(orig.getName(), a.getName());
        
        Assert.assertNull(a.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), a.getName());
        Assert.assertEquals(orig.ownerID, a.ownerID);
        Assert.assertEquals(orig.isPublic, a.isPublic);
        Assert.assertEquals(orig.isLocked, a.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, a.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, a.readWriteGroup);
        Assert.assertEquals(orig.properties, a.properties);
        
        Assert.assertTrue(a instanceof ContainerNode);
        ContainerNode c = (ContainerNode) a;
        Assert.assertEquals(orig.inheritPermissions, c.inheritPermissions);
        
        // these are set in put
        Assert.assertEquals(orig.getMetaChecksum(), a.getMetaChecksum());
        Assert.assertEquals(orig.getLastModified(), a.getLastModified());
        
        // update
        Thread.sleep(10L);
        orig.readOnlyGroup.add(URI.create("ivo://opencadc.org/gms?g1"));
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g3"));
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "123"));
        orig.isPublic = true;
        nodeDAO.put(orig);
        Node updated = nodeDAO.get(orig.getID());
        Assert.assertNotNull(updated);
        Assert.assertEquals(orig.getID(), updated.getID());
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertTrue(a.getLastModified().before(updated.getLastModified()));
        Assert.assertNotEquals(a.getMetaChecksum(), updated.getMetaChecksum());
        
        Assert.assertNull(updated.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertEquals(orig.ownerID, updated.ownerID);
        Assert.assertEquals(orig.isPublic, updated.isPublic);
        Assert.assertEquals(orig.isLocked, updated.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, updated.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, updated.readWriteGroup);
        Assert.assertEquals(orig.properties, updated.properties);
        
        Assert.assertTrue(updated instanceof ContainerNode);
        ContainerNode uc = (ContainerNode) updated;
        Assert.assertEquals(orig.inheritPermissions, uc.inheritPermissions);
        
        
        nodeDAO.delete(orig.getID());
        Node gone = nodeDAO.get(orig.getID());
        Assert.assertNull(gone);
    }
    
    @Test
    public void testPutGetUpdateDeleteContainerNodeMax() throws InterruptedException {
        UUID rootID = new UUID(0L, 0L);
        ContainerNode root = new ContainerNode(rootID, "root", false);
        
        // TODO: use get-by-path to find and remove the test node
        
        ContainerNode orig = new ContainerNode("container-test", false);
        orig.parent = root;
        orig.ownerID = "the-owner";
        orig.isPublic = true;
        orig.isLocked = false;
        orig.inheritPermissions = false;
        orig.readOnlyGroup.add(URI.create("ivo://opencadc.org/gms?g1"));
        orig.readOnlyGroup.add(URI.create("ivo://opencadc.org/gms?g2"));
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g3"));
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g4,g5"));
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g6-g7"));
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g6.g7"));
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g6_g7"));
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g6~g7"));
        
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "123"));
        orig.properties.add(new NodeProperty(URI.create("custom:prop"), "spaces in value"));
        orig.properties.add(new NodeProperty(URI.create("sketchy:a,b"), "comma in uri"));
        orig.properties.add(new NodeProperty(URI.create("sketchy:funny"), "value-with-{delims}"));
        nodeDAO.put(orig);
        
        Node a = nodeDAO.get(orig.getID());
        Assert.assertNotNull(a);
        log.info("found: "  + a.getID() + " aka " + a);
        Assert.assertEquals(orig.getID(), a.getID());
        Assert.assertEquals(orig.getName(), a.getName());
        
        Assert.assertNull(a.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), a.getName());
        Assert.assertEquals(orig.ownerID, a.ownerID);
        Assert.assertEquals(orig.isPublic, a.isPublic);
        Assert.assertEquals(orig.isLocked, a.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, a.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, a.readWriteGroup);
        Assert.assertEquals(orig.properties, a.properties);
        
        Assert.assertTrue(a instanceof ContainerNode);
        ContainerNode c = (ContainerNode) a;
        Assert.assertEquals(orig.inheritPermissions, c.inheritPermissions);
        
        // these are set in put
        Assert.assertEquals(orig.getMetaChecksum(), a.getMetaChecksum());
        Assert.assertEquals(orig.getLastModified(), a.getLastModified());
        
        // update
        Thread.sleep(10L);
        orig.isPublic = false;
        orig.isLocked = true;
        orig.readOnlyGroup.clear();
        orig.readOnlyGroup.add(URI.create("ivo://opencadc.org/gms?g1"));
        orig.readWriteGroup.clear();
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g3"));
        orig.properties.clear();
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "123"));
        orig.inheritPermissions = true;
        nodeDAO.put(orig);
        Node updated = nodeDAO.get(orig.getID());
        Assert.assertNotNull(updated);
        Assert.assertEquals(orig.getID(), updated.getID());
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertTrue(a.getLastModified().before(updated.getLastModified()));
        Assert.assertNotEquals(a.getMetaChecksum(), updated.getMetaChecksum());
        
        Assert.assertNull(updated.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertEquals(orig.ownerID, updated.ownerID);
        Assert.assertEquals(orig.isPublic, updated.isPublic);
        Assert.assertEquals(orig.isLocked, updated.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, updated.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, updated.readWriteGroup);
        Assert.assertEquals(orig.properties, updated.properties);
        
        Assert.assertTrue(updated instanceof ContainerNode);
        ContainerNode uc = (ContainerNode) updated;
        Assert.assertEquals(orig.inheritPermissions, uc.inheritPermissions);
        
        nodeDAO.delete(orig.getID());
        Node gone = nodeDAO.get(orig.getID());
        Assert.assertNull(gone);
    }
    
    @Test
    public void testPutGetUpdateDeleteDataNode() throws InterruptedException {
        UUID rootID = new UUID(0L, 0L);
        ContainerNode root = new ContainerNode(rootID, "root", false);
        
        DataNode orig = new DataNode("data-test", URI.create("cadc:vault/" + UUID.randomUUID()));
        orig.parent = root;
        orig.ownerID = "the-owner";
        orig.isPublic = true;
        orig.isLocked = false;
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_TYPE, "text/plain"));
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "this is the good stuff(tm)"));
        nodeDAO.put(orig);
        
        Node a = nodeDAO.get(orig.getID());
        Assert.assertNotNull(a);
        log.info("found: "  + a.getID() + " aka " + a);
        Assert.assertEquals(orig.getID(), a.getID());
        Assert.assertEquals(orig.getName(), a.getName());
        
        Assert.assertNull(a.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), a.getName());
        Assert.assertEquals(orig.ownerID, a.ownerID);
        Assert.assertEquals(orig.isPublic, a.isPublic);
        Assert.assertEquals(orig.isLocked, a.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, a.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, a.readWriteGroup);
        Assert.assertEquals(orig.properties, a.properties);
        
        Assert.assertTrue(a instanceof DataNode);
        DataNode dn = (DataNode) a;
        Assert.assertEquals(orig.getStorageID(), dn.getStorageID());
        
        // these are set in put
        Assert.assertEquals(orig.getMetaChecksum(), a.getMetaChecksum());
        Assert.assertEquals(orig.getLastModified(), a.getLastModified());
        
        // update
        Thread.sleep(10L);
        orig.isPublic = false;
        orig.isLocked = true;
        orig.readOnlyGroup.clear();
        orig.readOnlyGroup.add(URI.create("ivo://opencadc.org/gms?g1"));
        orig.readWriteGroup.clear();
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g3"));
        orig.properties.clear();
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "123"));
        // don't change storageID
        nodeDAO.put(orig);
        Node updated = nodeDAO.get(orig.getID());
        Assert.assertNotNull(updated);
        Assert.assertEquals(orig.getID(), updated.getID());
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertTrue(a.getLastModified().before(updated.getLastModified()));
        Assert.assertNotEquals(a.getMetaChecksum(), updated.getMetaChecksum());
        
        Assert.assertNull(updated.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertEquals(orig.ownerID, updated.ownerID);
        Assert.assertEquals(orig.isPublic, updated.isPublic);
        Assert.assertEquals(orig.isLocked, updated.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, updated.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, updated.readWriteGroup);
        Assert.assertEquals(orig.properties, updated.properties);
        
        
        Assert.assertTrue(a instanceof DataNode);
        DataNode udn = (DataNode) updated;
        Assert.assertEquals(orig.getStorageID(), udn.getStorageID());
        
        nodeDAO.delete(orig.getID());
        Node gone = nodeDAO.get(orig.getID());
        Assert.assertNull(gone);
    }
    
    @Test
    public void testPutGetUpdateDeleteLinkNode() throws InterruptedException {
        UUID rootID = new UUID(0L, 0L);
        ContainerNode root = new ContainerNode(rootID, "root", false);
        
        // TODO: use get-by-path to find and remove the test node
        
        LinkNode orig = new LinkNode("data-test", URI.create("vos://opencadc.org~srv/path/to/something"));
        orig.parent = root;
        orig.ownerID = "the-owner";
        orig.isPublic = true;
        orig.isLocked = false;
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_DESCRIPTION, "link to the good stuff(tm)"));
        nodeDAO.put(orig);
        
        Node a = nodeDAO.get(orig.getID());
        Assert.assertNotNull(a);
        log.info("found: "  + a.getID() + " aka " + a);
        Assert.assertEquals(orig.getID(), a.getID());
        Assert.assertEquals(orig.getName(), a.getName());
        
        Assert.assertNull(a.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), a.getName());
        Assert.assertEquals(orig.ownerID, a.ownerID);
        Assert.assertEquals(orig.isPublic, a.isPublic);
        Assert.assertEquals(orig.isLocked, a.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, a.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, a.readWriteGroup);
        Assert.assertEquals(orig.properties, a.properties);
        
        Assert.assertTrue(a instanceof LinkNode);
        LinkNode link = (LinkNode) a;
        Assert.assertEquals(orig.getTarget(), link.getTarget());
        
        // these are set in put
        Assert.assertEquals(orig.getMetaChecksum(), a.getMetaChecksum());
        Assert.assertEquals(orig.getLastModified(), a.getLastModified());
        
        // update
        Thread.sleep(10L);
        orig.isPublic = false;
        orig.isLocked = true;
        orig.readOnlyGroup.clear();
        orig.readOnlyGroup.add(URI.create("ivo://opencadc.org/gms?g1"));
        orig.readWriteGroup.clear();
        orig.readWriteGroup.add(URI.create("ivo://opencadc.org/gms?g3"));
        orig.properties.clear();
        orig.properties.add(new NodeProperty(VOS.PROPERTY_URI_CONTENTLENGTH, "123"));
        // don't change target
        nodeDAO.put(orig);
        Node updated = nodeDAO.get(orig.getID());
        Assert.assertNotNull(updated);
        Assert.assertEquals(orig.getID(), updated.getID());
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertTrue(a.getLastModified().before(updated.getLastModified()));
        Assert.assertNotEquals(a.getMetaChecksum(), updated.getMetaChecksum());
        
        Assert.assertNull(updated.parent); // get-node-by-id: comes pack without parent
        Assert.assertEquals(orig.getName(), updated.getName());
        Assert.assertEquals(orig.ownerID, updated.ownerID);
        Assert.assertEquals(orig.isPublic, updated.isPublic);
        Assert.assertEquals(orig.isLocked, updated.isLocked);
        Assert.assertEquals(orig.readOnlyGroup, updated.readOnlyGroup);
        Assert.assertEquals(orig.readWriteGroup, updated.readWriteGroup);
        Assert.assertEquals(orig.properties, updated.properties);
        
        Assert.assertTrue(updated instanceof LinkNode);
        LinkNode ulink = (LinkNode) updated;
        Assert.assertEquals(orig.getTarget(), ulink.getTarget());
        
        nodeDAO.delete(orig.getID());
        Node gone = nodeDAO.get(orig.getID());
        Assert.assertNull(gone);
    }
    
    @Test
    public void testPutGetDeleteContainerNodeChildren() throws IOException {
        UUID rootID = new UUID(0L, 0L);
        ContainerNode root = new ContainerNode(rootID, "root", false);
        
        ContainerNode orig = new ContainerNode("container-test", false);
        orig.parent = root;
        orig.ownerID = "the-owner";
        nodeDAO.put(orig);
        
        Node a = nodeDAO.get(orig.getID());
        Assert.assertNotNull(a);
        log.info("found: "  + a.getID() + " aka " + a);
        Assert.assertEquals(orig.getID(), a.getID());
        Assert.assertEquals(orig.getName(), a.getName());
        
        Assert.assertTrue(a instanceof ContainerNode);
        ContainerNode c = (ContainerNode) a;
        
        // these are set in put
        Assert.assertEquals(orig.getMetaChecksum(), a.getMetaChecksum());
        Assert.assertEquals(orig.getLastModified(), a.getLastModified());
        //ResourceIterator<Node> emptyIter = nodeDAO.childIterator(orig);
        //Assert.assertNotNull(emptyIter);
        //Assert.assertFalse(emptyIter.hasNext());
        //emptyIter.close();
        
        // add children
        ContainerNode cont = new ContainerNode("container1", false);
        cont.parent = c;
        cont.ownerID = c.ownerID;
        DataNode data = new DataNode("data1", URI.create("cadc:vault/" + UUID.randomUUID()));
        data.parent = c;
        data.ownerID = c.ownerID;
        LinkNode link = new LinkNode("link1", URI.create("cadc:ARCHIVE/data"));
        link.parent = c;
        link.ownerID = c.ownerID;
        log.info("put child: " + cont + " of " + cont.parent);
        nodeDAO.put(cont);
        log.info("put child: " + data + " of " + data.parent);
        nodeDAO.put(data);
        log.info("put child: " + link + " of " + link.parent);
        nodeDAO.put(link);
        
        ResourceIterator<Node> iter = nodeDAO.childIterator(orig);
        Assert.assertNotNull(iter);
        Assert.assertTrue(iter.hasNext());
        Node c1 = iter.next();
        Assert.assertTrue(iter.hasNext());
        Node c2 = iter.next();
        Assert.assertTrue(iter.hasNext());
        Node c3 = iter.next();
        
        // default order: alpha
        Assert.assertEquals(cont.getID(), c1.getID());
        Assert.assertEquals(cont.getName(), c1.getName());
        
        Assert.assertEquals(data.getID(), c2.getID());
        Assert.assertEquals(data.getName(), c2.getName());
        
        Assert.assertEquals(link.getID(), c3.getID());
        Assert.assertEquals(link.getName(), c3.getName());
        
        // depth first delete required?
        try {
            nodeDAO.delete(orig.getID());
            Assert.fail("expected IllegalStateException but successfully deleted non-empty container");
        } catch (IllegalStateException expected) {
            log.info("caught expected: " + expected);
        }
        
        nodeDAO.delete(cont.getID());
        nodeDAO.delete(data.getID());
        nodeDAO.delete(link.getID());
        nodeDAO.delete(orig.getID());
        Node gone = nodeDAO.get(orig.getID());
        Assert.assertNull(gone);
    }
    
    //@Test
    public void testPutGetDeleteNodeProperties() {
        log.info("TODO");
    }
}
