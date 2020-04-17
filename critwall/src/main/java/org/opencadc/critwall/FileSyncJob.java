/* ************************************************************************ *******************  CANADIAN ASTRONOMY DATA CENTRE  ******************* **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  ************** * *  (c) 2020.                            (c) 2020. *  Government of Canada                 Gouvernement du Canada *  National Research Council            Conseil national de recherches *  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6 *  All rights reserved                  Tous droits réservés * *  NRC disclaims any warranties,        Le CNRC dénie toute garantie *  expressed, implied, or               énoncée, implicite ou légale, *  statutory, of any kind with          de quelque nature que ce *  respect to the software,             soit, concernant le logiciel, *  including without limitation         y compris sans restriction *  any warranty of merchantability      toute garantie de valeur *  or fitness for a particular          marchande ou de pertinence *  purpose. NRC shall not be            pour un usage particulier. *  liable in any event for any          Le CNRC ne pourra en aucun cas *  damages, whether direct or           être tenu responsable de tout *  indirect, special or general,        dommage, direct ou indirect, *  consequential or incidental,         particulier ou général, *  arising from the use of the          accessoire ou fortuit, résultant *  software.  Neither the name          de l'utilisation du logiciel. Ni *  of the National Research             le nom du Conseil National de *  Council of Canada nor the            Recherches du Canada ni les noms *  names of its contributors may        de ses  participants ne peuvent *  be used to endorse or promote        être utilisés pour approuver ou *  products derived from this           promouvoir les produits dérivés *  software without specific prior      de ce logiciel sans autorisation *  written permission.                  préalable et particulière *                                       par écrit. * *  This file is part of the             Ce fichier fait partie du projet *  OpenCADC project.                    OpenCADC. * *  OpenCADC is free software:           OpenCADC est un logiciel libre ; *  you can redistribute it and/or       vous pouvez le redistribuer ou le *  modify it under the terms of         modifier suivant les termes de *  the GNU Affero General Public        la “GNU Affero General Public *  License as published by the          License” telle que publiée *  Free Software Foundation,            par la Free Software Foundation *  either version 3 of the              : soit la version 3 de cette *  License, or (at your option)         licence, soit (à votre gré) *  any later version.                   toute version ultérieure. * *  OpenCADC is distributed in the       OpenCADC est distribué *  hope that it will be useful,         dans l’espoir qu’il vous *  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE *  without even the implied             GARANTIE : sans même la garantie *  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ *  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF *  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence *  General Public License for           Générale Publique GNU Affero *  more details.                        pour plus de détails. * *  You should have received             Vous devriez avoir reçu une *  a copy of the GNU Affero             copie de la Licence Générale *  General Public License along         Publique GNU Affero avec *  with OpenCADC.  If not, see          OpenCADC ; si ce n’est *  <http://www.gnu.org/licenses/>.      pas le cas, consultez : *                                       <http://www.gnu.org/licenses/>. * ************************************************************************ */package org.opencadc.critwall;import ca.nrc.cadc.auth.AuthMethod;import ca.nrc.cadc.net.FileContent;import ca.nrc.cadc.net.HttpGet;import ca.nrc.cadc.net.HttpPost;import ca.nrc.cadc.net.TransientException;import ca.nrc.cadc.reg.Standards;import ca.nrc.cadc.reg.client.RegistryClient;import ca.nrc.cadc.vos.Direction;import ca.nrc.cadc.vos.Protocol;import ca.nrc.cadc.vos.Transfer;import ca.nrc.cadc.vos.TransferReader;import ca.nrc.cadc.vos.TransferWriter;import ca.nrc.cadc.vos.VOS;import java.io.ByteArrayOutputStream;import java.io.FileNotFoundException;import java.net.URI;import java.net.URL;import java.util.ArrayList;import java.util.List;import org.apache.log4j.Logger;import org.opencadc.inventory.Artifact;import org.opencadc.inventory.InventoryUtil;import org.opencadc.inventory.db.ArtifactDAO;import org.opencadc.inventory.storage.NewArtifact;import org.opencadc.inventory.storage.StorageAdapter;import org.opencadc.inventory.storage.StorageMetadata;public class FileSyncJob implements Runnable {    private static final Logger log = Logger.getLogger(FileSyncJob.class);    private final ArtifactDAO artifactDAO;    private final URI artifactID;    private final URI resourceID;    private final StorageAdapter storageAdapter;    public FileSyncJob(URI artifactID, URI resourceID, StorageAdapter storageAdapter, ArtifactDAO artifactDAO) {        InventoryUtil.assertNotNull(FileSyncJob.class, "artifactID", artifactID);        InventoryUtil.assertNotNull(FileSyncJob.class, "resourceID", resourceID);        InventoryUtil.assertNotNull(FileSyncJob.class, "storageAdapter", storageAdapter);        InventoryUtil.assertNotNull(FileSyncJob.class, "artifactDAO", artifactDAO);        this.artifactID = artifactID;        this.resourceID = resourceID;        this.storageAdapter = storageAdapter;        this.artifactDAO = artifactDAO;    }    @Override    public void run() {        RegistryClient regClient = new RegistryClient();        log.debug("resource id: " + this.resourceID);        URL certURL = regClient.getServiceURL(this.resourceID, Standards.VOSPACE_SYNC_21, AuthMethod.CERT);        log.debug("certURL: " + certURL);        try {            // Ask for all protocols available back, and the server will            // give you URLs for the ones it allows.            List<Protocol> protocolList = new ArrayList<Protocol>();            Protocol httpsCert = new Protocol(VOS.PROTOCOL_HTTPS_GET);            log.debug("protocols: " + httpsCert);            protocolList.add(httpsCert);            Transfer transfer = new Transfer(artifactID, Direction.pullFromVoSpace, protocolList);            transfer.version = VOS.VOSPACE_21;            TransferWriter writer = new TransferWriter();            ByteArrayOutputStream out = new ByteArrayOutputStream();            writer.write(transfer, out);            FileContent content = new FileContent(out.toByteArray(), "text/xml");            log.debug("xml file content to be posted: " + transfer);            log.debug("artifact path: " + artifactID.getPath());            HttpPost post = new HttpPost(certURL, content, true);            post.prepare();            log.debug("post prepare done");            // TODO: error handling is likely very different than this...            if (post.getThrowable() != null && post.getThrowable() instanceof FileNotFoundException) {                throw (FileNotFoundException) post.getThrowable();            }            TransferReader reader = new TransferReader();            Transfer t = reader.read(post.getInputStream(), null);            List<String> urlList = t.getAllEndpoints();            log.debug("endpoints returned: " + urlList);            // get the URL list from t, then call HttpGet to get the item            int retryCount = 0;            boolean found = false;            for (int i = 0; i < urlList.size(); i++) {                URL u = new URL(urlList.get(i));                log.debug("trying this url: " + u);                while (retryCount < 2 && found == false) {                    ByteArrayOutputStream dest = new ByteArrayOutputStream();                    HttpGet get = new HttpGet(u, dest);                    get.prepare();                    Artifact curArtifact = artifactDAO.get(artifactID);                    // Check to see if the get succeeds. If so, exit                    if (get.getThrowable() == null) {                        NewArtifact a = new NewArtifact(artifactID);                        StorageMetadata storageMeta = storageAdapter.put(a, get.getInputStream());                        log.debug("get & put for url succeeded.");                        log.debug("storage meta returned: " + storageMeta.getStorageLocation());                        log.debug(storageMeta.getContentChecksum());                        log.debug(storageMeta.contentLastModified);                        log.debug(storageMeta.getContentLength());                        // Update curArtifact with new storage location                        curArtifact.storageLocation = storageMeta.getStorageLocation();                        artifactDAO.put(curArtifact, true);                        log.debug("updated artifact with udpated storage location");                        found = true;                        break;                    } else {                        Throwable th = get.getThrowable();                        if (th instanceof TransientException) {                            // try again                            retryCount++;                            log.debug("retrying for " + retryCount + " time.");                            continue;                        } else {                            // TODO: probably should be something different                            throw new RuntimeException("unable to get artifactID: " + artifactID, th);                        }                    }                }            }        } catch (Exception e) {            // TODO: need a better error catch here.            log.debug("error on try: " + e);            throw new RuntimeException("error on try: ", e);        }    }}