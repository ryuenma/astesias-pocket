# Sync design rationale

The transfer/sync classes carry long KDoc blocks explaining WHY they are shaped
the way they are. That reasoning is real and worth keeping, but 40 lines of
prose above a class is not documentation - it is a design argument that belongs
somewhere it can be read once and referred to. Moved here 2026-10-11; each class
KDoc now carries the load-bearing rule and a pointer to this file.

## The local leg is never blocked on the network (TransferOutboxRepository)

The owner's transfer is written to `transactions` unconditionally. The outbox is
what makes that safe: the money has moved by the time any of this could run, so a
POST that fails, times out or never happens must not un-record it, delay it, or
report it as failed.

Queue rather than POST-inside-save, because the two orders fail differently. A
POST inside the save means she taps save on a train, the request times out, and
she now has either a recorded transfer reported as failed or an error dialog
about money that genuinely left her account - with no way to tell which, and
retrying risks the duplicate the whole design exists to prevent.

Every method returns a value describing what happened and never throws: the drain
runs in a coroutine nobody is watching.

## A publish must distinguish three outcomes (TransferSyncApi)

A failed GET is harmless - the cache keeps what it had. A failed POST is not: the
request may or may not have landed and the caller cannot tell. So a publish
cannot return `T?` that is null on any error; it must separate "the server
accepted this", "the server already had this" and "we do not know", because the
first two clear the queue row and the third must leave it queued. Collapsing all
three into null is how a transfer ends up permanently stuck, or published twice.

Separate from [DirectorySyncApi] because it needs the same failure tolerance but
different timing: the directory may do nothing when sync is unconfigured, this
must never THROW.

## Only record answers about transfers WE sent (TransferConfirmationRepository)

The server lets both profiles read every confirmation, so this device
necessarily sees the OWNER's own answer coming back in the same feed. Recording
it would tell her the partner accepted a transfer she had already accepted - a
false fact about money, sourced from a real row. Two checks close that: the
retained outbox row must be kind EVENT (this device sent it), and not
CONFIRMATION (an answer she gave about a transfer that arrived, which is
somebody else's).

Persisted rather than a snackbar because a background sync mostly completes while
nobody is looking, and a row survives the app being closed. Never writes to
`transactions` - the money already left her account when she saved it.

## Both directions of the directory, and why not transfers (DirectorySyncApi)

Reading and publishing live together because they are one resource: a device
reads every profile's accounts to populate the picker, and publishes its OWN so
the other device can do the same. Publishing only existed on the read side, which
is why the picker said "No other profiles yet" forever.

`publishAccount` returns a result type rather than `Unit?` because the server
holds a unique index on (profileId, localAccountId), so re-publishing an
unchanged account is refused - and that refusal is a SUCCESS. Collapsing "already
there" and "we do not know" into the same null is how a publish loop retries
forever or reports a published account that was never published.

A paired TRANSFER write is deliberately NOT here: it has to land atomically with
the local leg, and doing it from a network client is how you end up with money
moved on one phone and never on the other.
