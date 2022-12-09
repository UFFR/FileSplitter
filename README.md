# FileSplitter
A small Java program to split large files into several smaller ones, then put them back together. Creates a tiny file containing information to help merge the chunks and includes checksums to notify of corruption.

Use case is primarily to split large files to send to others, such as through email or Discord, where the recipient can then reconstitute the file.

Chunks can be any size >=1 KB (1024 bytes), defaulting to 10 MB (10485760 bytes). The `.sum` file is a binary file used to assist the merging part of the program. It contains the original size, the chunk size, number of chunks, and `SHA-256` checksums of the original file and every chunk. Any discrepancy between what it has saved and what is actually read and calculated is noted to the user.

The `.part` chunks themselves are simply that, exact, unmodified chunks of the original file. Thus, they can be merged by any other process without this program, if it suits the user. The `.sum` file contains the information the program requires.

## Usage
A common application is where you need to share a large file over some online service that has an upload cap, such as email or Discord.
Send each chunk as necessary along with the summary so the recipient can use them with this program to merge them back together. If the program detects a corrupted chunk, it will be noted to the receiving user, who should request that chunk to be resent.

### Examples
Here are some examples of use (parameter layout does not matter, only uses this structure for consistency).

Splitting an MP4 called `My Video.mp4` for Discord into a sub-directory `Split video` (this directory will be created if it does not already exist):

```
java -jar file_splitter.jar -p "~/Videos/My Video.mp4" -s 8:mb -e "~/Videos/Split video/"
```

Splitting an archive called `archive.tar.gz` for something that has a limit of 25 MB into the same directory as the source file (the suffix can be either case):

```
java -jar file_splitter.jar -p "~/Documents/archive.tar.gz" -s 25:MB
```

Splitting an oversized GIF called `why_we_cant_have_nice_things_like_apng.gif` for 1.44 MB floppy disks into a sub-directory `Split gif`:

```
java -jar file_splitter.jar -p "~/Pictures/why_we_cant_have_nice_things_like_apng.gif" -s 1440:KB -e "~/Pictures/Split gif/"
```

## Notes
The `-h` or `--help` parameter displays a simple help menu and version number. Report all bugs to the GitHub page.

## Libraries Used
- Apache Commons CLI - https://github.com/apache/commons-cli
- progressbar - https://github.com/ctongfei/progressbar