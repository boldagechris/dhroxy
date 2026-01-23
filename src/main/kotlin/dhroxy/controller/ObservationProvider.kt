package dhroxy.controller

import ca.uhn.fhir.rest.annotation.OptionalParam
import ca.uhn.fhir.rest.annotation.Search
import ca.uhn.fhir.rest.api.server.IBundleProvider
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.param.TokenAndListParam
import ca.uhn.fhir.rest.param.TokenOrListParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.SimpleBundleProvider
import ca.uhn.fhir.rest.server.servlet.ServletRequestDetails
import dhroxy.service.MedicationOverviewService
import dhroxy.service.ObservationService
import org.hl7.fhir.r4.model.Observation
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import kotlinx.coroutines.runBlocking

@Component
class ObservationProvider(
    private val observationService: ObservationService
) : IResourceProvider {

    override fun getResourceType(): Class<Observation> = Observation::class.java

    @Search
    fun search(
        @OptionalParam(name = Observation.SP_DATE) date: DateRangeParam?,
        @OptionalParam(name = Observation.SP_CATEGORY) category: TokenAndListParam?,
        details: ServletRequestDetails
    ): IBundleProvider {
        val categoryValue = extractCategory(category, details)
        var (fra, til) = extractDateRange(date, details.parameters["date"])

        // sundhed.dk requires both fra and til to be specified
        // If til is missing, default to today
        if (til == null && fra != null) {
            til = java.time.LocalDate.now().toString()
        }

        // Add timestamps for sundhed.dk API compatibility
        val fraWithTime = fra?.let { addTimeToDate(it, "T00:00:00") }
        val tilWithTime = til?.let { addTimeToDate(it, "T23:59:59") }
        val omraade = mapCategoryToOmraade(categoryValue)
        val headers = toHttpHeaders(details)
        val bundle = runBlocking {
            observationService.search(headers, fraWithTime, tilWithTime, omraade, requestUrl(details))
        }
        return SimpleBundleProvider(bundle.entry.mapNotNull { it.resource as? Observation })
    }

    private fun extractCategory(category: TokenAndListParam?, details: ServletRequestDetails): String? {
        val token = collectTokens(category).firstOrNull()
        val v = token?.value
        return if (!v.isNullOrBlank()) v else details.parameters["category"]?.firstOrNull()
    }

    private fun extractDateRange(
        dateRange: DateRangeParam?,
        rawParams: Array<String>?
    ): Pair<String?, String?> {
        val values = when {
            dateRange != null -> {
                val lowers = dateRange.lowerBound?.let { lb ->
                    (lb.prefix?.value?.lowercase().orEmpty()) + (lb.valueAsString ?: "")
                }
                val uppers = dateRange.upperBound?.let { ub ->
                    (ub.prefix?.value?.lowercase().orEmpty()) + (ub.valueAsString ?: "")
                }
                listOfNotNull(lowers, uppers).toTypedArray()
            }
            else -> rawParams
        }
        return extractDateRange(values)
    }

    private fun extractDateRange(dateParams: Array<String>?): Pair<String?, String?> {
        var fra: String? = null
        var til: String? = null
        dateParams.orEmpty().forEach { value ->
            when {
                value.startsWith("ge") -> fra = value.removePrefix("ge")
                value.startsWith("gt") -> fra = value.removePrefix("gt")
                value.startsWith("le") -> til = value.removePrefix("le")
                value.startsWith("lt") -> til = value.removePrefix("lt")
                value.startsWith("eq") -> {
                    fra = value.removePrefix("eq")
                    til = value.removePrefix("eq")
                }
                fra == null -> fra = value
                til == null -> til = value
            }
        }
        return fra to til
    }

    private fun isMedicationCategory(category: String?): Boolean {
        if (category.isNullOrBlank()) return false
        val normalized = category.lowercase()
        return normalized.contains("medication") || normalized.contains("therapy")
    }

    private fun mapCategoryToOmraade(category: String?): String {
        if (category.isNullOrBlank()) return "Alle"
        val normalized = category.lowercase()
        return when {
            normalized.contains("mikro") -> "Mikrobiologi"
            normalized.contains("patologi") -> "Patologi"
            normalized.contains("klinisk") || normalized.contains("biokemi") -> "KliniskBiokemi"
            else -> "Alle"
        }
    }

    private fun toHttpHeaders(details: ServletRequestDetails): HttpHeaders =
        HttpHeaders().apply {
            details.headers?.forEach { entry: Map.Entry<String, MutableList<String>> ->
                addAll(entry.key, entry.value)
            }
        }

    private fun requestUrl(details: ServletRequestDetails): String =
        details.servletRequest?.let { req ->
            buildString {
                append(req.requestURL.toString())
                req.queryString?.let { append("?").append(it) }
            }
        } ?: (details.requestPath ?: "")

    private fun collectTokens(param: TokenAndListParam?): List<TokenParam> {
        val result = mutableListOf<TokenParam>()
        val raw = param?.valuesAsQueryTokens
        if (raw is Iterable<*>) {
            raw.forEach { item ->
                when (item) {
                    is TokenParam -> result.add(item)
                    is TokenOrListParam -> {
                        val innerRaw = item.valuesAsQueryTokens
                        if (innerRaw is Iterable<*>) {
                            innerRaw.forEach { inner ->
                                when (inner) {
                                    is TokenParam -> result.add(inner)
                                    is Iterable<*> -> inner.filterIsInstance<TokenParam>().forEach(result::add)
                                }
                            }
                        }
                    }
                    is Iterable<*> -> item.filterIsInstance<TokenParam>().forEach(result::add)
                }
            }
        }
        return result
    }

    private fun addTimeToDate(date: String, time: String): String {
        // If date already contains time, return as-is
        if (date.contains("T")) return date
        // Otherwise append the time component
        return date + time
    }
}
