package com.timeflux.domain.model

/** One cell in the year-overview heatmap: count of entries for a given month and module. */
data class YearGridRow(
    val yearMonth: String,   // "YYYY-MM"
    val moduleType: String,  // ModuleType.id
    val count: Long,
)
