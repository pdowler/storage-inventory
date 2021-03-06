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

import ca.nrc.cadc.net.PreconditionFailedException;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.Log4jInit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opencadc.inventory.Artifact;
import org.opencadc.inventory.storage.NewArtifact;
import org.opencadc.inventory.storage.StorageAdapter;
import org.opencadc.inventory.storage.StorageMetadata;
import static org.opencadc.inventory.storage.fs.OpaqueByteRangeTest.root;
import org.opencadc.inventory.storage.test.StorageAdapterBasicTest;

/**
 * Integration tests that interact with the file system. These tests require a file system
 * that supports posix extended attributes.
 * 
 * @author pdowler
 */
public class OpaqueFileSystemStorageAdapterTest extends StorageAdapterBasicTest {
    private static final Logger log = Logger.getLogger(OpaqueFileSystemStorageAdapterTest.class);

    static File root;
    static int depth = 2;
    
    static {
        Log4jInit.setLevel("org.opencadc.inventory.storage", Level.INFO);
        root = new File("build/tmp/opaque-int-tests");
        root.mkdir();
    }
    
    final OpaqueFileSystemStorageAdapter ofsAdapter;
            
    public OpaqueFileSystemStorageAdapterTest() { 
        super(new OpaqueFileSystemStorageAdapter(root, depth));
        this.ofsAdapter = (OpaqueFileSystemStorageAdapter) super.adapter;

        log.debug("    content path: " + ofsAdapter.contentPath);
        log.debug("transaction path: " + ofsAdapter.txnPath);
        Assert.assertTrue("testInit: contentPath", Files.exists(ofsAdapter.contentPath));
        Assert.assertTrue("testInit: txnPath", Files.exists(ofsAdapter.txnPath));
    }
    
    @Before
    public void cleanupBefore() throws IOException {
        log.info("cleanupBefore: delete all content from " + ofsAdapter.contentPath);
        if (Files.exists(ofsAdapter.contentPath)) {
            Files.walkFileTree(ofsAdapter.contentPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        log.info("cleanupBefore: delete all content from " + ofsAdapter.contentPath + " DONE");
    }
    
    
    // the code currently works correctly if you put files with different storageBucket depths
    // but it is probably a bad idea because you mix bucket directories and files and thus have
    // arbitrary sized directory listings to sort
    //@Test
    public void testMixedDepthIterator() {
        try {
            String dataString = "abcdefghijklmnopqrstuvwxyz";
            byte[] data = dataString.getBytes();
            MessageDigest md = MessageDigest.getInstance("MD5");
            String md5Val = HexUtil.toHex(md.digest(data));
            URI checksum = URI.create("md5:" + md5Val);
            log.info("expected md5sum: " + checksum);

            int num = 6;
            SortedSet<StorageMetadata> explist = new TreeSet<StorageMetadata>();
            
            // put files with standard test depth
            for (int i = 0; i < num; i++) {
                String suri = "test:FOO/bar" + i;
                URI uri = URI.create(suri);
                NewArtifact newArtifact = new NewArtifact(uri);
                newArtifact.contentChecksum = checksum;
                newArtifact.contentLength = (long) data.length;
                ByteArrayInputStream source = new ByteArrayInputStream(data);
                StorageMetadata meta = adapter.put(newArtifact, source);
                explist.add(meta);
                log.info("added file: " + meta.getStorageLocation());
            }
            
            // put files with larger depth
            File root = ofsAdapter.contentPath.getParent().toFile();
            StorageAdapter sap2 = new OpaqueFileSystemStorageAdapter(root, depth + 2); // opaque
           
            for (int i = num; i < 2 * num; i++) {
                String suri = "test:FOO/bar" + i;
                URI uri = URI.create(suri);
                NewArtifact newArtifact = new NewArtifact(uri);
                newArtifact.contentChecksum = checksum;
                newArtifact.contentLength = (long) data.length;
                ByteArrayInputStream source = new ByteArrayInputStream(data);
                StorageMetadata meta = sap2.put(newArtifact, source);
                explist.add(meta);
                log.info("added file: " + meta.getStorageLocation().storageBucket);
            }
            
            // iterate with standard depth adapter
            {
                Iterator<StorageMetadata> ai = adapter.iterator();
                Iterator<StorageMetadata> ei = explist.iterator();
                int count = 0;
                while (ai.hasNext()) {
                    count++;
                    StorageMetadata expected = ei.next();
                    StorageMetadata actual = ai.next();
                    log.info("adapter.iterator: " + actual);
                    Assert.assertEquals("order " + count, expected.getStorageLocation(), actual.getStorageLocation());
                    Assert.assertEquals("checksum", checksum, actual.getContentChecksum());
                    Assert.assertEquals("length", new Long(data.length), actual.getContentLength());
                    Assert.assertEquals("artifactURI",expected.artifactURI, actual.artifactURI);
                    
                }
                Assert.assertEquals("file count", explist.size(), count);
            }
            
            // iterator with larger depth adapter
            {
                Iterator<StorageMetadata> ai = sap2.iterator();
                Iterator<StorageMetadata> ei = explist.iterator();
                int count = 0;
                while (ai.hasNext()) {
                    count++;
                    StorageMetadata expected = ei.next();
                    StorageMetadata actual = ai.next();
                    log.info("sap2.iterator: " + actual);
                    Assert.assertEquals("order " + count, expected.getStorageLocation(), actual.getStorageLocation());
                    Assert.assertEquals("checksum", checksum, actual.getContentChecksum());
                    Assert.assertEquals("length", new Long(data.length), actual.getContentLength());
                    Assert.assertEquals("artifactURI",expected.artifactURI, actual.artifactURI);
                }
                Assert.assertEquals("sap2 file count", explist.size(), count);
            }        
            
            // iterate individual top-level buckets
            {
                int n = 0;
                for (int i = 0; i < Artifact.URI_BUCKET_CHARS.length(); i++) {
                    String bucketPrefix = Artifact.URI_BUCKET_CHARS.substring(i, i + 1);
                    log.info("iterator: " + bucketPrefix);
                    Iterator<StorageMetadata> bi = adapter.iterator(bucketPrefix);
                    while (bi.hasNext()) {
                        StorageMetadata sm = bi.next();
                        Assert.assertTrue("prefix match", sm.getStorageLocation().storageBucket.startsWith(bucketPrefix));
                        n++;
                    }
                }
                Assert.assertEquals("file count", explist.size(), n);
            }
            
            {
                int n = 0;
                for (int i = 0; i < Artifact.URI_BUCKET_CHARS.length(); i++) {
                    String bucketPrefix = Artifact.URI_BUCKET_CHARS.substring(i, i + 1);
                    log.info("sap2.iterator: " + bucketPrefix);
                    Iterator<StorageMetadata> bi = sap2.iterator(bucketPrefix);
                    while (bi.hasNext()) {
                        StorageMetadata sm = bi.next();
                        Assert.assertTrue("sap2 prefix match", sm.getStorageLocation().storageBucket.startsWith(bucketPrefix));
                        n++;
                    }
                }
                Assert.assertEquals("sap2 file count", explist.size(), n);
            }
            
        } catch (Exception unexpected) {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
}
