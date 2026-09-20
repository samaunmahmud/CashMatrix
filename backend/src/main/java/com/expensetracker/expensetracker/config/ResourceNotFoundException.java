package com.expensetracker.expensetracker.config;

/**
 * Thrown when a record doesn't exist or belongs to someone else. Both cases
 * look identical to the caller so ids can't be probed to find other users' data.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
