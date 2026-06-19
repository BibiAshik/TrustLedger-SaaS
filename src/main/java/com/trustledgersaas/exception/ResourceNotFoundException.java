package com.trustledgersaas.exception;

/**
 * ResourceNotFoundException — Thrown when a requested entity is not found in the database.
 *
 * Examples: trying to view a shop that doesn't exist, trying to access a loan
 * with an invalid ID, trying to find a customer by a non-existent user ID.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
