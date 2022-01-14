/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2020.                            (c) 2020.
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

package org.opencadc.inventory.storage.fs;

import ca.nrc.cadc.util.Log4jInit;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opencadc.inventory.storage.InvalidConfigException;
import org.opencadc.inventory.storage.test.StorageAdapterBasicTest;

/**
 * Integration tests that interact with the file system. These tests require a file system
 * that supports posix extended attributes.
 * 
 * @author pdowler
 */
public class OpaqueStorageAdapterTest extends StorageAdapterBasicTest {
    private static final Logger log = Logger.getLogger(OpaqueStorageAdapterTest.class);

    static final int BUCKET_LEN = 2;
    static final File ROOT_DIR;
    
    static {
        Log4jInit.setLevel("org.opencadc.inventory.storage", Level.INFO);
        ROOT_DIR = new File("build/tmp/opaque-int-tests");
        ROOT_DIR.mkdir();
    }
    
    final OpaqueFileSystemStorageAdapter ofsAdapter;
            
    public OpaqueStorageAdapterTest() throws InvalidConfigException {
        super(new OpaqueFileSystemStorageAdapter(ROOT_DIR, BUCKET_LEN));
        this.ofsAdapter = (OpaqueFileSystemStorageAdapter) super.adapter;

        log.debug("    content path: " + ofsAdapter.contentPath);
        log.debug("transaction path: " + ofsAdapter.txnPath);
        Assert.assertTrue("testInit: contentPath", Files.exists(ofsAdapter.contentPath));
        Assert.assertTrue("testInit: txnPath", Files.exists(ofsAdapter.txnPath));
    }
    
    @Before
    public void cleanupBefore() throws IOException {
        log.info("cleanupBefore: " + ofsAdapter.contentPath.getParent());
        if (Files.exists(ofsAdapter.contentPath)) {
            Files.walkFileTree(ofsAdapter.contentPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!ofsAdapter.contentPath.equals(dir)) {
                        Files.delete(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        if (Files.exists(ofsAdapter.txnPath)) {
            Files.walkFileTree(ofsAdapter.txnPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        log.info("cleanupBefore: " + ofsAdapter.contentPath.getParent() + " DONE");
    }
    
    @Test
    public void testSetGetAttributes() {
        try {
            String txnID = UUID.randomUUID().toString();
            Path p = ofsAdapter.txnPath.resolve(txnID);
            OutputStream  ostream = Files.newOutputStream(p, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
            ostream.close();

            OpaqueFileSystemStorageAdapter.setFileAttribute(p, "foo", "bar");
            
            // attr set
            String val = OpaqueFileSystemStorageAdapter.getFileAttribute(p, "foo");
            Assert.assertEquals("bar", val);

            // attr not set
            val = OpaqueFileSystemStorageAdapter.getFileAttribute(p, "no-foo");
            Assert.assertNull("attr-not-set", val);
            
            // delete attr
            OpaqueFileSystemStorageAdapter.setFileAttribute(p, "foo", null);
            val = OpaqueFileSystemStorageAdapter.getFileAttribute(p, "foo");
            Assert.assertNull("deleted", val);
            
            // delete not-set attr
            OpaqueFileSystemStorageAdapter.setFileAttribute(p, "no-foo", null);
            val = OpaqueFileSystemStorageAdapter.getFileAttribute(p, "no-foo");
            Assert.assertNull("not-set-deleted", val);
            
        } catch (Exception unexpected) {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
