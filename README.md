# TransformFile

TransformFile compares files and creates a patch file that transforms original files into a destination file.

The patch file can have multiple input files, but only one output file. If you want multiple output files, you may want to use a tar file as the single output file, and pipe the output of TransformFile into `tar x`.

