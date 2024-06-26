
create table <schema>.Node (
    -- require a special root ID value but prevent bugs
    parentID uuid not null,
    name varchar(512) not null,
    nodeType char(1) not null,

    ownerID varchar(256) not null,
    isPublic boolean,
    isLocked boolean,
    readOnlyGroups text[],
    readWriteGroups text[],

    -- store misc props in a 2D array
    properties text[][],

    -- ContainerNode
    inheritPermissions boolean,
    
    -- DataNode
    busy boolean,
    bytesUsed bigint,
    -- Artifact.uri and Artifact.uriBucket
    storageID varchar(512),
    storageBucket varchar(5),

    -- LinkNode
    target text,

    lastModified timestamp not null,
    metaChecksum varchar(136) not null,
    id uuid not null primary key
);

-- usage: vault path navigation
create unique index node_parent_child on <schema>.Node(parentID,name);

-- usage: Node metadata-sync
create index node_lastmodified on <schema>.Node(lastModified);

-- usage: vault incremental Artifact to Node for bytesUsed
-- usage: vault Node vs Artifact validation
create unique index node_storageID on <schema>.Node(storageID);