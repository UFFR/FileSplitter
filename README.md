# FileSplitter
A small Java program to split large files into several smaller ones, then put them back together. Creates a tiny file containing information to help merge the chunks and includes checksums to notify of corruption.

Use case is primarily to split large files to send to others, such as through email or Discord, where the recipient can then reconsitute the file.

Chunks can be any size >=1 KB. The `.sum` file is a binary file used to tell the merging part of the program. It contains the original size, the chunk sizes, number of chunks, and `SHA-256` checksums of the original file and every chunk. Any discrepancy between what it has saved and what is actually read and calculated is noted to the user.
