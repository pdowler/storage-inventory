/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2023.                            (c) 2023.
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

package org.opencadc.inventory;

import ca.nrc.cadc.date.DateUtil;
import ca.nrc.cadc.util.FileUtil;
import ca.nrc.cadc.util.Log4jInit;
import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.net.URI;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class EntityTest {
    private static final Logger log = Logger.getLogger(EntityTest.class);

    static {
        Log4jInit.setLevel("org.opencadc.inventory", Level.INFO);
        Log4jInit.setLevel("org.opencadc.persist", Level.INFO);
        //org.opencadc.persist.Entity.MCS_DEBUG = true;
    }
    
    public EntityTest() { 
    }
    
    /*
    @Test
    public void testTemplate() {
        try {
            
        } catch (Exception unexpected) {
            log.error("unexpected exception", unexpected);
            Assert.fail("unexpected exception: " + unexpected);
        }
    }
    */
    
    @Test
    public void testArtifact() {
        URI uri = URI.create("cadc:FOO/bar");
        URI contentChecksum = URI.create("md5:d41d8cd98f00b204e9800998ecf8427e");
        Date contentLastModified = new Date();
        Long contentLength = 1024L;
        
        try {
            Artifact ok = new Artifact(uri, contentChecksum, contentLastModified, contentLength);
            log.info("created: " + ok);
            Assert.assertEquals(uri, ok.getURI());
            Assert.assertEquals(contentChecksum, ok.getContentChecksum());
            Assert.assertEquals(contentLastModified, ok.getContentLastModified());
            Assert.assertEquals(contentLength, ok.getContentLength());
            Assert.assertNotNull(ok.getBucket());
            
            UUID id = UUID.randomUUID();
            Artifact recon = new Artifact(id, uri, contentChecksum, contentLastModified, contentLength);
            log.info("created: " + recon);
            Assert.assertEquals(id, recon.getID());
            Assert.assertEquals(uri, recon.getURI());
            Assert.assertEquals(contentChecksum, recon.getContentChecksum());
            Assert.assertEquals(contentLastModified, recon.getContentLastModified());
            Assert.assertEquals(contentLength, recon.getContentLength());
            Assert.assertNotNull(recon.getBucket());
            
            try {
                Artifact invalid = new Artifact(null, contentChecksum, contentLastModified, contentLength);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
            try {
                Artifact invalid = new Artifact(uri, null, contentLastModified, contentLength);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
            try {
                Artifact invalid = new Artifact(uri, contentChecksum, null, contentLength);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
            try {
                Artifact invalid = new Artifact(uri, contentChecksum, contentLastModified, null);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
            try {
                Artifact invalid = new Artifact(uri, contentChecksum, contentLastModified, -1L);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
            // invalid URI
            try {
                URI u2 = URI.create("cadc:/foo/bar"); // absolute path
                Artifact invalid = new Artifact(u2, contentChecksum, contentLastModified, -1L);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
    
    @Test
    public void testArtifactTransientState() {
        URI uri = URI.create("cadc:FOO/bar");
        URI contentChecksum = URI.create("md5:d41d8cd98f00b204e9800998ecf8427e");
        Date contentLastModified = new Date();
        Long contentLength = 1024L;
        
        try {
            Artifact ok = new Artifact(uri, contentChecksum, contentLastModified, contentLength);
            log.info("created: " + ok);
            
            URI mcs1 = ok.computeMetaChecksum(MessageDigest.getInstance("MD5"));
            
            // first verify checksum changes by changing non-transient state
            ok.contentType = "text/plain";
            URI mcs2 = ok.computeMetaChecksum(MessageDigest.getInstance("MD5"));
            Assert.assertNotEquals(mcs1, mcs2);
            
            //ok.storageLocation = new StorageLocation(ok.getID(), URI.create("ceph:" + UUID.randomUUID()));
            ok.storageLocation = new StorageLocation(URI.create("ceph:" + UUID.randomUUID()));
            URI mcs3 = ok.computeMetaChecksum(MessageDigest.getInstance("MD5"));
            Assert.assertEquals(mcs2, mcs3);
            
            //ok.siteLocations.add(new SiteLocation(ok.getID(), UUID.randomUUID()));
            ok.siteLocations.add(new SiteLocation(UUID.randomUUID()));
            URI mcs4 = ok.computeMetaChecksum(MessageDigest.getInstance("MD5"));
            Assert.assertEquals(mcs2, mcs4);
            
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
    
    @Test
    public void testStableArtifactChecksum() {
        URI uri = URI.create("cadc:FOO/bar");
        URI contentChecksum = URI.create("md5:d41d8cd98f00b204e9800998ecf8427e");
        Date contentLastModified = new Date();
        Long contentLength = 1024L;
        
        try {
            
            if (false) {
                // generate a sample artifact from current code
                Artifact cur = new Artifact(uri, contentChecksum, contentLastModified, contentLength);
                cur.contentEncoding = "gzip";
                cur.contentType = "text/plain";
                log.info("created: " + cur);
                final URI mcs1 = cur.computeMetaChecksum(MessageDigest.getInstance("MD5"));

                StringBuilder sb = new StringBuilder();
                sb.append("uri\tcontentChecksum\tcontentLastModified\tcontentLength\tcontentEncoding\tcontentType\tid\tmetaChecksum\n");
                sb.append(toTSV(cur, mcs1));

                File out = new File("build/tmp/sample-artifact.tsv");
                PrintWriter w = new PrintWriter(out);
                w.println(sb.toString());
                w.close();
                log.info("new sample artifact: " + out.getPath());
            }
            
            // check that meta checksum of previous samples is stable
            final DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
            for (String fname : new String[] {"sample-artifact.tsv", "sample-iris.tsv" }) {
                File in = FileUtil.getFileFromResource(fname, EntityTest.class);
                log.info("checking: " + in.getPath());

                LineNumberReader r = new LineNumberReader(new FileReader(in));
                String line = r.readLine();
                log.info("header: " + line);
                line = r.readLine();
                String[] ss = line.split("[\t]");
                log.info("IN:\n" + line);

                URI suri = URI.create(ss[0]);
                URI ccs = URI.create(ss[1]);
                Date clm = df.parse(ss[2]);
                Long clen = Long.parseLong(ss[3]);
                String cenc = ss[4];
                String ctype = ss[5];
                UUID id = UUID.fromString(ss[6]);
                Artifact actual = new Artifact(id, suri, ccs, clm, clen);
                actual.contentEncoding = cenc;
                actual.contentType = ctype;
                URI metaChecksum = URI.create(ss[7]);

                URI recomp = actual.computeMetaChecksum(MessageDigest.getInstance("MD5"));
                log.info("RE:\n" + toTSV(actual, recomp));

                Assert.assertEquals(in.getName(), metaChecksum, recomp);
            }
            
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
    
    private String toTSV(Artifact cur, URI mcs) {
        DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        StringBuilder sb = new StringBuilder();
        sb.append(cur.getURI().toASCIIString()).append("\t");
        sb.append(cur.getContentChecksum().toASCIIString()).append("\t");
        sb.append(df.format(cur.getContentLastModified())).append("\t");
        sb.append(cur.getContentLength()).append("\t");
        sb.append(cur.contentEncoding).append("\t");
        sb.append(cur.contentType).append("\t");
        sb.append(cur.getID().toString()).append("\t");
        sb.append(mcs.toASCIIString());
        return sb.toString();
    }
    
    @Test
    public void testDeletedArtifactEvent() {
        try {
            DeletedArtifactEvent ok = new DeletedArtifactEvent(UUID.randomUUID());
            log.info("created: " + ok);
            
            try {
                DeletedArtifactEvent invalid = new DeletedArtifactEvent(null);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
    
    @Test
    public void testStorageLocationEvent() {
        try {
            StorageLocationEvent ok = new StorageLocationEvent(UUID.randomUUID());
            log.info("created: " + ok);
            
            try {
                StorageLocationEvent invalid = new StorageLocationEvent(null);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
    
    @Test
    public void testDeletedStorageLocationEvent() {
        try {
            DeletedStorageLocationEvent ok = new DeletedStorageLocationEvent(UUID.randomUUID());
            log.info("created: " + ok);
            
            try {
                DeletedStorageLocationEvent invalid = new DeletedStorageLocationEvent(null);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
    
    @Test
    public void testStorageSite() {
        final URI resourceID = URI.create("ivo://example.net/foo");
        final String name = "foo";
        
        final URI resourceID2 = URI.create("ivo://example.net/bar");
        final String name2 = "flibble";
                
        try {
            StorageSite ok = new StorageSite(resourceID, name, true, true);
            log.info("created: " + ok);
            Assert.assertEquals(resourceID, ok.getResourceID());
            Assert.assertEquals(name, ok.getName());

            UUID id = UUID.randomUUID();
            StorageSite recon = new StorageSite(id, resourceID, name, true, true);
            log.info("created: " + recon);
            Assert.assertEquals(id, recon.getID());
            Assert.assertEquals(resourceID, recon.getResourceID());
            Assert.assertEquals(name, recon.getName());

            // rename
            StorageSite ren = new StorageSite(resourceID, name, true, true);
            log.info("created: " + ren);
            ren.setResourceID(resourceID2);
            Assert.assertEquals(resourceID2, ren.getResourceID());
            Assert.assertEquals(name, ren.getName());
            ren.setName(name2);
            Assert.assertEquals(resourceID2, ren.getResourceID());
            Assert.assertEquals(name2, ren.getName());
            
            recon.setResourceID(resourceID);
            try {
                StorageSite invalid = new StorageSite(null, name, true, true);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
            try {
                StorageSite invalid = new StorageSite(resourceID, null, true, true);
                Assert.fail("created: " + invalid);
            } catch (IllegalArgumentException expected) {
                log.info("expected: " + expected);
            }
            
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
    
    @Test
    public void testStableMetaChecksum() {
        final DateFormat df = DateUtil.getDateFormat(DateUtil.IVOA_DATE_FORMAT, DateUtil.UTC);
        try {
            // values pulled from global luskan query
            final URI expectedMetaChecksum = URI.create("md5:ba0af5277852ab5aea7184fdac90ca9c");
            
            final UUID id = UUID.fromString("61d482fe-cd4c-475f-be93-8f9d16fd1edf");
            final URI uri = URI.create("cadc:IRIS/I001B3H0.fits");
            final URI contentChecksum = URI.create("md5:2ada853a8ae135e16504aeba4e47489e");
            final Long contentLength = 1008000L;
            final Date contentLastModified = df.parse("2006-07-25T16:15:19.000");
            final String contentType = "application/fits";
            
            Artifact a = new Artifact(id, uri, contentChecksum, contentLastModified, contentLength);
            a.contentType = contentType;
            
            URI mcs = a.computeMetaChecksum(MessageDigest.getInstance("MD5"));
            log.info("expected: " + expectedMetaChecksum);
            log.info("  actual: " + mcs);
            Assert.assertEquals(expectedMetaChecksum, mcs);
        } catch (Exception ex) {
            log.error("unexpected exception", ex);
            Assert.fail("unexpected exception: " + ex);
        }
    }
}
