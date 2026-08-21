package com.motorcycle.comparison.entity;

/**
 * Commercial segment of a motorcycle. Used as a primary filter facet on the
 * catalogue endpoint, so it is stored as a string for readability in the DB.
 */
public enum Category {
    SPORT,
    NAKED,
    TOURING,
    ADVENTURE,
    CRUISER,
    SCOOTER,
    OFF_ROAD,
    SUPERMOTO,
    ELECTRIC
}
