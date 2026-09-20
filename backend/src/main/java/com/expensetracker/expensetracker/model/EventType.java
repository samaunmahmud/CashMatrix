package com.expensetracker.expensetracker.model;

/**
 * What a calendar entry represents. Payments and subscriptions usually carry
 * an amount; tasks are plain to-dos.
 */
public enum EventType {
    TASK,
    PAYMENT,
    SUBSCRIPTION
}
