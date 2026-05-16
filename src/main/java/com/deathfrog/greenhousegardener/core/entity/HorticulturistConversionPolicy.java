package com.deathfrog.greenhousegardener.core.entity;

/**
 * Pure conversion-selection rules for the horticulturist worker.
 */
final class HorticulturistConversionPolicy
{
    private HorticulturistConversionPolicy()
    {
    }

    /**
     * Check whether conversion selection may consider a field on this colony day.
     *
     * @param convertedToday true when conversion already succeeded today
     * @param visitedToday true when the worker already visited the field today
     * @param conversionBlockedToday true when today's visit was a conversion block that may be retried
     * @return true when conversion scanning may continue to the live biome overlay check
     */
    static boolean mayAttemptConversionToday(
        final boolean convertedToday,
        final boolean visitedToday,
        final boolean conversionBlockedToday)
    {
        return !convertedToday && (!visitedToday || conversionBlockedToday);
    }
}
