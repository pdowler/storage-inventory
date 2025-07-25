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
 *  : 5 $
 *
 ************************************************************************
 */

package org.opencadc.luskan;

import ca.nrc.cadc.auth.AuthMethod;
import ca.nrc.cadc.auth.AuthenticationUtil;
import ca.nrc.cadc.auth.IdentityManager;
import ca.nrc.cadc.auth.NotAuthenticatedException;
import ca.nrc.cadc.auth.X500PrincipalComparator;
import ca.nrc.cadc.cred.client.CredUtil;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.net.TransientException;
import ca.nrc.cadc.util.MultiValuedProperties;
import ca.nrc.cadc.uws.Job;
import ca.nrc.cadc.uws.server.JobPersistenceException;
import ca.nrc.cadc.uws.server.impl.PostgresJobPersistence;
import java.io.IOException;
import java.net.URI;
import java.security.AccessControlException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import org.apache.log4j.Logger;
import org.opencadc.gms.GroupURI;
import org.opencadc.gms.IvoaGroupClient;

public class AuthJobPersistence extends PostgresJobPersistence {

    private static final Logger log = Logger.getLogger(AuthJobPersistence.class);

    public AuthJobPersistence(IdentityManager im) {
        super(im);
    }

    @Override
    public Job put(Job job)
        throws JobPersistenceException, TransientException {
        checkPermission();
        return super.put(job);
    }

    /**
     *
     */
    private void checkPermission() {
        Subject s = AuthenticationUtil.getCurrentSubject();
        AuthMethod am = AuthenticationUtil.getAuthMethod(s);
        MultiValuedProperties props = LuskanConfig.getConfig();
        String allowAnon = props.getFirstPropertyValue(LuskanConfig.ALLOW_ANON);
        log.debug(LuskanConfig.ALLOW_ANON + "=" + allowAnon);
        if ("true".equals(allowAnon)) {
            return;
        }
        
        if (am == null || AuthMethod.ANON.equals(am)) {
            throw new NotAuthenticatedException("permission denied");
        }
        
        List<String> configUsers = props.getProperty(LuskanConfig.ALLOWED_USER_X509);
        Set<X500Principal> allowedUsers = new TreeSet<>(new X500PrincipalComparator());
        configUsers.forEach(u -> allowedUsers.add(new X500Principal(u)));
        for (X500Principal p : s.getPrincipals(X500Principal.class)) {
            if (allowedUsers.contains(p)) {
                // TODO: would be nice to be able to log this in the LogInfo object
                log.debug("allowedUserX509 query granted: " + p);
                return;
            }
        }

        List<String> configGroups = props.getProperty(LuskanConfig.ALLOWED_GROUP);
        Set<GroupURI> allowedGroups = new TreeSet<>();
        configGroups.forEach(group -> allowedGroups.add(new GroupURI(URI.create(group))));
                
        try {
            if (CredUtil.checkCredentials()) {
                IvoaGroupClient gms = new IvoaGroupClient();
                Set<GroupURI> mems = gms.getMemberships(allowedGroups);
                if (!mems.isEmpty()) {
                    StringBuilder sb = new StringBuilder("read: ");
                    for (GroupURI g : mems) {
                        sb.append(" ").append(g.getURI());
                    }
                    // TODO: would be nice to be able to log this in the LogInfo object
                    log.debug("GMS query granted: " + sb.toString());
                    return;
                }
                
            }
        } catch (CertificateException e) {
            throw new AccessControlException("read permission denied (invalid delegated client certificate)");
        } catch (ResourceNotFoundException ex) {
            throw new RuntimeException("CONFIG: " + ex.getMessage(), ex);
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException("OOPS: GMS client failed", ex);
        }
        throw new AccessControlException("permission denied");
    }

}
