# vault quota design/algorithms

The definitive source of content-length (file size) of a DataNode comes from the
`inventory.Artifact` table and is not known until a PUT to storage is completed.
In the case of a `vault` service co-located with a single storage site (`minoc`),
the new Artifact is visible in the database as soon as the PUT to `minoc` is
completed. In the case of a `vault` service co-located with a global SI, the new 
Artifact is visible in the database once it is synced from the site of the PUT to 
the global database by `fenwick` (or worst case: `ratik`).

## NOTE
This design was for supporting propagation of space used up the tree so that
allocation space used was dynamically updated as content was modified. While the
algorithm for doing that is nominally viable, the algorithm to validate and repair
incorrect container node sizes in a live system is excessively complex and probably
impossible to implement in practice (deadlocks, excessive database load and query
processing, etc).

**This design will not be completed and implemented** and is retained here for future 
reference.

## TODO
The design below only takes into account incremental propagation of space used 
by stored files. It is not complete/verified until we also come up with a validation
algorithm that can detect and fix discrepancies in a live `vault`.

## operations that effect node size
The following operations effect node size:
* delete node removed the node and applies a negative delta to parent
* recursive delete will need to update twice as many nodes in small transactions
* move node will applies a negative delta to previous parent and positive delta to new parent
* copy applies a positive delta to new parent
* transfer negotiation needs to check allocationNode quota vs size to allow a put to proceed

This has to be done entirely inside the NodePersistence implementation; that should be feasible
since the argument of NodePersistence methods is the previously retrieved node with the full parent
tree intact. It's not clear which of these will require changes in cadc-vos-server, but if they do 
it will need to be possible for them to be optional and/or gracefully not do anything.

In any case, any solution with container node delta(s) is inherently multi-threaded because 
user requests can modify them.

## DataNode size algorithm:
This is an event watcher that gets Artifact events (after a PUT) and intiates the
propagation of sizes (space used).
```
track progress using HarvestState (source: `db:{bucket range}`, name: TBD)
incremental query for new artifacts in lastModified order
for each new Artifact:
  query for DataNode (storageID = artifact.uri)
  if Artifact.contentLength != Node.size:
    start txn
    lock parent
    lock datanode
    compute delta
    apply delta to parent.delta
    set dataNode.size
    update HarvestState
    commit txn
```
Optimization: The above sequence does the first step of propagation from DataNode to 
parent ContainerNode so the maximum work can be done in parallel using bucket ranges 
(smaller than 0-f). It also means the propagation below only has to consider 
ContainerNode.delta since DataNode(s) never have a delta.

## ContainerNode size propagation algorithm:
```
query for ContainerNode with non-zero delta
for each ContainerNode:
  start txn
  lock parent
  lock containernode
  re-check delta
  apply delta to parent.delta
  apply delta containernode.size, set containernode.delta=0
  commit txn
```
The above sequence finds candidate propagations, locks (order: parent-child), 
and applies the propagation. This moves the outstanding delta up the tree one level. If the
sequence acts on multiple child containers before the parent, the delta(s) naturally
_merge_ and fewer delta propagations occur in the upper part of the tree.

The most generic implementation is to iterate over container nodes:
```
Iterator<ContainerNode> iter = nodeDAO.containerIterator(boolean nonZeroDelta);
```
It would be optimal to do propagations from the bottom upwards in order to "merge" them,
but it doesn't seem practical to forcibly accomplish that ordering. Container size propagation 
will be implemented as a single sequence (thread). We could add something to the vospace.Node 
table to support subdividing work and enable multiple threads, but there is nothing there right 
now and it might not be necessary.

## validation

### DataNode vs Artifact discrepancies
These can be validated in parallel by multiple threads, subdivide work by bucket if we add
DataNode.storageBucket (== Artifact.uriBucket).

```
discrepancy: Artifact exists but DataNode does not
explanation: DataNode created, transfer negotiated, DataNode removed, transfer executed
evidence: DeletedNodeEvent exists
action: remove artifact, create DeletedArtifactEvent

discrepancy: Artifact exists but DataNode does not
explanation: DataNode created, Artifact put, DataNode deleted, Artifact delete failed
evidence: only possible with singlePool==false
action: remove artifact, create DeletedArtifactEvent

discrepancy: DataNode exists but Artifact does not 
explanation: DataNode created, Artifact never (successfully) put (normal)
evidence: DataNode.nodeSize == 0 or null
action: none

discrepancy: DataNode exists but Artifact does not
explanation: deleted or lost Artifact
evidence: DataNode.nodeSize != 0 (deleted vs lost: DeletedArtifactEvent exists)
action: lock nodes, fix dataNode and propagate delta to parent

discrepancy: DataNode.nodeSize != Artifact.contentLength
explanation: artifact written (if DataNode.size > 0: replaced)
action: lock nodes, fix DataNode and propagate delta to parent
```
Required lock order: child-parent or parent-child OK.

The most generic implementation is a merge join of two iterators (see ratik, tantar):
```
Iterator<Artifact> aiter = artifactDAO.iterator(vaultNamespace, bucket);  // uriBucket,uri order
Iterator<DataNode> niter = nodeDAO.iterator(bucket);      // storageBucket,storageID order 
```

### ContainerNode vs child nodes discrepancies
These can be validated in 
```
discrepancy 1: container size != sum(child size)
explanation: un-propagated delta from put or delete
evidence: sum(child delta) != 0
action: none

discrepancy 1: container size != sum(child size)
explanation: bug
evidence: sum(child delta) == 0
action: fix?? container size, set container.delta
```
Required lock order: locks the parent of a parent-children relationship so propagations are blocked,
then do the aggregate query (select sum(child size), sum(child delta) where parentID=?)
but child state is still not stable (delete child node, move child out, copy/move node in, 
sync child nodes from remote) so all of these would have to lock in the same order to avoid deadlock.
I don't see any way to avoid deadlocks when user requests can lock multiple nodes.

Recursive delete, container size propagation, datanode validation, and container validation can will 
all potentially modify child delta(s).

The most generic implementation is to iterate over container nodes:
```
Iterator<ContainerNode> iter = nodeDAO.containerIterator(false); // order not relevant
```

## database changes required
note: all field and column names TBD
* add `transient Long bytesUsed` to ContainerNode and DataNode
* add `transient long delta` field to ContainerNode
* add `bytesUsed` to the `vospace.Node` table
* add `delta` to the `vospace.Node` table
* add `storageBucket` to DataNode?? TBD
* add `storageBucket` to `vospace.Node` table

## cadc-inventory-db API required immediately
* incremental sync query/iterator: ArtifactDAO.iterator(Namespace ns, String uriBucketPrefix, Date minLastModified, boolean ordered)
  order by lastModified if set
* lookup DataNode by storageID: NodeDAO.getDataNode(URI storageID)
* indices to support new queries

## cadc-inventory-db API required later
* validate-by-bucket: use ArtifactDAO.iterator(String uriBucketPrefix, boolean ordered, Namespace ns)
* validate-by-bucket: NodeDAO.dataNodeIterator(String storageBucketPrefix, boolean ordered)
* incremental and validate containers: NodeDAO.containerIterator(boolean nonZeroDelta)
* indices to support new queries

