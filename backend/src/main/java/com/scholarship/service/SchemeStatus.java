package com.scholarship.service;

/**
 * Application-window status of a scheme, derived only from verified dates and today's date.
 * A failed or missing fetch never produces CLOSED.
 */
public enum SchemeStatus {
    /** Verified deadline has not passed (and the window has opened, if an opening date is known). */
    OPEN,
    /** Verified deadline has passed. The next cycle's dates are not known. */
    CLOSED,
    /** Verified opening date is in the future. */
    UPCOMING,
    /** The record is verified but no verified application dates exist. */
    UNKNOWN,
    /** The record itself is not verified (pending review, archived, ...). */
    UNVERIFIED
}
