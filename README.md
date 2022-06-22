
**About flal**

flal – “FLac Audio Library” – is a tiny tool used to convert convert flac audio libraries into mp3 or m4a files. It will iterate over a given set of files and either convert these into single output files or, depending on the metadata of the source flac files, create a single large output file from multiple input files, for example from an audio book.

All this is a conversion from an older ruby script and currently in experimental status.


**External binaries**

The following external programs are required:

- The `flac` binary from [xiph.org's original flac implementation](https://xiph.org/flac/documentation_tools_flac.html). While flal uses jaudiotagger for reading and writing tags, `flac` is required for decoding of flac files.

The following binaries are optional:

- The `fdkaac` commandline encoder frontend for `libfdk-aac` from [nu774/fdkaac](https://github.com/nu774/fdkaac) for encoding AAC audio files. This is required for AAC output into `.m4a` files.


---

This package includes software written by other authors:

jaudiotagger from [http://www.jthink.net/jaudiotagger/](http://www.jthink.net/jaudiotagger/), which is licensed under the LGPL.

