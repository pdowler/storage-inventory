/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2021.                            (c) 2021.
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

package org.opencadc.raven;

import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.rest.InlineContentException;
import ca.nrc.cadc.rest.InlineContentHandler;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.opencadc.inventory.InventoryUtil;
import org.opencadc.inventory.transfer.ProtocolsGenerator;
import org.opencadc.vospace.VOS;
import org.opencadc.vospace.transfer.Direction;
import org.opencadc.vospace.transfer.Transfer;
import org.opencadc.vospace.transfer.TransferReader;
import org.opencadc.vospace.transfer.TransferWriter;

/**
 * Given a transfer request object return a transfer response object with all
 * available endpoints to the target artifact.
 *
 * @author majorb
 */
public class PostAction extends ArtifactAction {

    
    private static final Logger log = Logger.getLogger(PostAction.class);

    // immutable state set in constructor
    private final List<URI> readGrantServices = new ArrayList<>();
    private final List<URI> writeGrantServices = new ArrayList<>();

    private static final String INLINE_CONTENT_TAG = "inputstream";
    private static final String CONTENT_TYPE = "text/xml";

    private final List<URI> avoid = new ArrayList<>();

    /**
     * Default, no-arg constructor.
     */
    public PostAction() {
        super();
    }

    @Override
    void parseRequest() throws Exception {
        TransferReader reader = new TransferReader();
        InputStream in = (InputStream) syncInput.getContent(INLINE_CONTENT_TAG);
        if (in == null) {
            return;
        }
        transfer = reader.read(in, null);

        log.debug("transfer request: " + transfer);
        if (Direction.pullFromVoSpace.equals(transfer.getDirection())) {
            avoid.addAll(RavenInitAction.getGetAvoid(RavenInitAction.getConfig()));
        } else if (Direction.pushToVoSpace.equals(transfer.getDirection())) {
            avoid.addAll(RavenInitAction.getPutAvoid(RavenInitAction.getConfig()));
        } else {
            throw new IllegalArgumentException("direction not supported: " + transfer.getDirection());
        }
        List<URI> targets = transfer.getTargets();
        if (targets.isEmpty() || targets.size() > 1) {
            throw new IllegalArgumentException("expected 1 target URI, found: " + targets.size());
        }
        artifactURI = targets.get(0);
        InventoryUtil.validateArtifactURI(PostAction.class, artifactURI);
    }

    /**
     * Return the input stream.
     * @return The Object representing the input stream.
     */
    @Override
    protected InlineContentHandler getInlineContentHandler() {
        return new InlineContentHandler() {
            public InlineContentHandler.Content accept(String name, String contentType, InputStream inputStream)
                    throws InlineContentException, IOException, ResourceNotFoundException {
                if (!CONTENT_TYPE.equals(contentType)) {
                    throw new IllegalArgumentException("expecting text/xml input document");
                }
                Content content = new Content();
                content.name = INLINE_CONTENT_TAG;
                content.value = inputStream;
                return content;
            }
        };
    }


    /**
     * Perform transfer negotiation.
     */
    @Override
    public void doAction() throws Exception {
        initAndAuthorize();

        ProtocolsGenerator pg = new ProtocolsGenerator(this.artifactDAO, this.siteAvailabilities, this.siteRules);
        pg.tokenGen = this.tokenGen;
        pg.user = this.user;
        pg.preventNotFound = this.preventNotFound;
        pg.storageResolver = this.storageResolver;
        pg.siteAvoid.addAll(avoid);
        Transfer ret = new Transfer(artifactURI, transfer.getDirection());
        // TODO: change from pg.getProtocols(transfer) to pg.getResolvedTransfer(transfer)??
        ret.getProtocols().addAll(pg.getProtocols(transfer));
        ret.version = VOS.VOSPACE_21;
                        
        TransferWriter transferWriter = new TransferWriter();
        transferWriter.write(ret, syncOutput.getOutputStream());
    }


}
