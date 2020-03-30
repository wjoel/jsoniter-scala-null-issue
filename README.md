# jsoniter-scala-null-issue

UPDATE: This was reported and fixed three hours later (!) in https://github.com/plokhotnyuk/jsoniter-scala/issues/492
The fix is available in version 2.1.11

`jsoniter-scala` seems to have problems handling nulls correctly when
reading files using `fs2.io.file.readAll` using a chunk size which is
small relative to the file size.

For larger files the chunk size can not be large enough without quickly running
out of heap space.

Interestingly enough, the placement of the nulls seem to matter, and the
parsing succeeds when nulls are placed last in the objects.

The parsing succeeds if the destination type has fields matching the source,
regardless of the order of nulls in the source data and the order of the fields
in the destination type.

`circe-fs2` does not have any problems in these tests.

