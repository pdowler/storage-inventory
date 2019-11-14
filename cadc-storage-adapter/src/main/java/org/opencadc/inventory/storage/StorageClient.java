/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2019.                            (c) 2019.
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
 *  $Revision: 4 $
 *
 ************************************************************************
 */

package org.opencadc.inventory.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.opencadc.inventory.Artifact;
import org.opencadc.inventory.StorageLocation;

import ca.nrc.cadc.net.InputStreamWrapper;
import ca.nrc.cadc.net.OutputStreamWrapper;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.net.TransientException;

/**
 * Provides access to storage.  
 *
 * @author majorb
 *
 */
public class StorageClient {
    
    private static Logger log = Logger.getLogger(StorageClient.class);
    
    private StorageAdapter adapter;

    public StorageClient() {
        adapter = getStorageAdapter();
    }
    
    public Artifact get(URI storageID, OutputStream out) throws ResourceNotFoundException, TransientException {
        InputStreamWrapper handler = new InputStreamWrapper() {
            public void read(InputStream in) throws IOException {
                ioLoop(out, in);
            }
        };
        return adapter.get(storageID, handler);
    }

    public Artifact meta(URI storageID) throws ResourceNotFoundException, TransientException {
        return adapter.meta(storageID);
    }

    public StorageLocation put(Artifact artifact, InputStream in) throws StreamCorruptedException, TransientException {
        OutputStreamWrapper wrapper = new OutputStreamWrapper() {
            public void write(OutputStream out) throws IOException {
                ioLoop(out, in);
            }
        };
        return adapter.put(artifact, wrapper);
    }

    public StorageLocation replace(Artifact artifact, InputStream in, StorageLocation replaceID) throws ResourceNotFoundException, StreamCorruptedException, TransientException {
        OutputStreamWrapper wrapper = new OutputStreamWrapper() {
            public void write(OutputStream out) throws IOException {
                ioLoop(out, in);
            }
        };
        return adapter.replace(artifact, wrapper, replaceID);
    }

    public void delete(URI storageID) throws ResourceNotFoundException, TransientException {
        adapter.delete(storageID);
    }

    public Iterator<StorageLocation> iterator() throws TransientException {
        return adapter.iterator();
    }
    
    private void ioLoop(OutputStream out, InputStream in) {
        // TODO: Write 2 threaded io loop--one thread reading
        // and one thread writing.
    }
    
    /**
     * Load the configured storage adapter or the default one if none
     * are found.
     * 
     * @return The storage adapter to be used by this client.
     */
    private StorageAdapter getStorageAdapter() {
        Class c = null;
        
        // Try to load the adapter based on a classname in a system property
        String cname = System.getProperty(StorageAdapter.class.getName());
        if (cname != null) {
            try {
                c = Class.forName(cname);
            } catch (Throwable t) {
                String msg = "Failed to find storage adapter " + cname;
                log.error(msg, t);
                throw new IllegalStateException(msg, t);
            }
        }
        
        // Try to load the adapter based on the default name
        cname = StorageAdapter.class.getName() + "Impl";
        try {
            c = Class.forName(cname);
            try {
                Constructor con = c.getConstructor();
                Object o = con.newInstance();
                StorageAdapter ret = (StorageAdapter) o;
                log.debug("Loaded Storage adapter: " + cname);
                return ret;
            } catch (Throwable t) {
                log.error("Failed to load storage adapter: " + cname, t);
            }
        } catch (Throwable t) {
            String msg = "Failed to find storage adapter " + cname +
                "  Using default implementation.";
            log.debug(msg, t);
        }
        
        StorageAdapter ret = new FileSystemStorageAdapter();
        log.debug("Loaded default storage adapter: " + ret);
        return ret;
        
    }

}
