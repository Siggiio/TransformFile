package io.siggi.transformfile.exception;

public class TransformFileException extends Exception {
    public TransformFileException() {
        super();
    }

    public TransformFileException(String message) {
        super(message);
    }

    public TransformFileException(Throwable cause) {
        super(cause);
    }

    public TransformFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
