package io.siggi.transformfile.exception;

public class IncompatibleFileException extends TransformFileException {
    public final int foundVersion;
    public final int highestSupportedVersion;

    public IncompatibleFileException(int foundVersion, int highestSupportedVersion) {
        super("Max supported version is " + highestSupportedVersion + ", but found version " + foundVersion + ".");
        this.foundVersion = foundVersion;
        this.highestSupportedVersion = highestSupportedVersion;
    }
}
