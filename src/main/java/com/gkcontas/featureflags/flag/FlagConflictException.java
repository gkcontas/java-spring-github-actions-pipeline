package com.gkcontas.featureflags.flag;

public class FlagConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FlagConflictException(String message) {
        super(message);
    }
}
