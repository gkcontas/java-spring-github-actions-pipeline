package com.gkcontas.featureflags.flag;

public class FlagNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FlagNotFoundException(String key) {
        super("Feature flag '" + key + "' does not exist");
    }
}
