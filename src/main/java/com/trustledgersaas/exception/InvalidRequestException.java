package com.trustledgersaas.exception;

/**
 * InvalidRequestException — Thrown when a request violates a business rule.
 *
 * Examples: trying to backdate a loan (loan date in the past), trying to add
 * a 101st customer on the Basic plan, trying to close an already-closed loan.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
