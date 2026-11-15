package com.example.backend.task.query;

/** Supported orderings for task lists. Every ordering ends with a unique key so paging is stable. */
public enum TaskSort {
    /** Newest first. */
    NEWEST,
    /** Earliest due date first; tasks without one last. */
    DUE_DATE,
    /** High, medium, low; newest first within a priority. */
    PRIORITY
}
