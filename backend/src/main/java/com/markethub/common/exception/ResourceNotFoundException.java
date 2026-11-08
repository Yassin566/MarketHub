package com.markethub.common.exception;

/** Raised when a resource is absent or is not visible within the caller's tenant. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
