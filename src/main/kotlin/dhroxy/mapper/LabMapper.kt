package dhroxy.mapper

import dhroxy.model.LabsvarResponse
import dhroxy.model.Laboratorieresultat
import dhroxy.model.QuantitativeFindings
import dhroxy.model.Rekvisition
import org.hl7.fhir.r4.model.Annotation
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.CodeableConcept
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DateTimeType
import org.hl7.fhir.r4.model.Identifier
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Quantity
import org.hl7.fhir.r4.model.Reference
import org.hl7.fhir.r4.model.StringType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Date
import java.util.UUID

@Component
class LabMapper {
    private val log = LoggerFactory.getLogger(LabMapper::class.java)
    private val observationCategory =
        CodeableConcept().addCoding(
            Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                .setCode("laboratory")
                .setDisplay("Laboratory")
        )

    private fun cleanText(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return html
            .replace("<br/>", "\n", ignoreCase = true)
            .replace("<br />", "\n", ignoreCase = true)
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&#230;", "æ")
            .replace("&#248;", "ø")
            .replace("&#229;", "å")
            .replace("&#198;", "Æ")
            .replace("&#216;", "Ø")
            .replace("&#197;", "Å")
            .trim()
    }

    fun toObservationBundle(payload: LabsvarResponse?, requestUrl: String): Bundle {
        val svaroversigt = payload?.svaroversigt ?: return emptyBundle(requestUrl)
        val rekById = svaroversigt.rekvisitioner.associateBy { it.id }
        val bundle = Bundle().apply {
            type = Bundle.BundleType.SEARCHSET
            link = listOf(Bundle.BundleLinkComponent().apply {
                relation = "self"
                url = requestUrl
            })
        }

        svaroversigt.laboratorieresultater.forEach { result ->
            val rekvisition = rekById[result.rekvisitionsId]
            bundle.addEntry(
                Bundle.BundleEntryComponent().apply {
                    fullUrl = "urn:uuid:${UUID.randomUUID()}"
                    resource = mapObservation(result, rekvisition)
                }
            )
        }
        bundle.total = bundle.entry.size
        return bundle
    }

    private fun mapObservation(result: Laboratorieresultat, rekvisition: Rekvisition?): Observation {
        val observation = Observation()
        observation.id = "lab-${safeId(result.rekvisitionsId ?: result.proevenummerLaboratorie ?: UUID.randomUUID().toString())}"
        observation.identifier = buildList {
            result.rekvisitionsId?.let {
                add(Identifier().setSystem("https://www.sundhed.dk/labsvar/rekvisition").setValue(it))
            }
            result.proevenummerLaboratorie?.let {
                add(Identifier().setSystem("https://www.sundhed.dk/labsvar/proevenummer").setValue(it))
            }
        }
        observation.category = listOf(observationCategory)
        observation.status = mapStatus(result.resultatStatuskode, result.resultatStatus)

        val undersoegelse = result.undersoegelser.firstOrNull()
        val labData = extractLabDataFromQuantitativeFindings(undersoegelse?.quantitativeFindings)

        // Log HELE undersoegelse objektet for at se hvad der faktisk kommer fra sundhed.dk
        log.info("=== FULL Undersoegelse object ===")
        log.info("undersoegelse: {}", undersoegelse)
        log.info("undersoegelse?.undersoegelsesNavn: '{}'", undersoegelse?.undersoegelsesNavn)
        log.info("undersoegelse?.analyseKode: '{}'", undersoegelse?.analyseKode)
        log.info("undersoegelse?.materiale: '{}'", undersoegelse?.materiale)
        log.info("undersoegelse?.eksaminator: '{}'", undersoegelse?.eksaminator)
        log.info("Number of undersoegelser: {}", result.undersoegelser.size)

        // Log alle mulige navne kilder
        log.info("=== DisplayName sources ===")
        log.info("labData?.displayName: {}", labData?.displayName)
        log.info("result.resultattype: {}", result.resultattype)
        log.info("result.vaerditype: {}", result.vaerditype)
        log.info("result.analysetypeId: {}", result.analysetypeId)

        // Prioriter UndersoegelsesNavn (mest komplet, f.eks. "SARS-CoV-2 (RNA), Borger.")
        // derefter QuantitativeFindings Text kolonne, til sidst tekniske koder
        val displayName = undersoegelse?.undersoegelsesNavn?.takeIf { it.isNotBlank() }
            ?: labData?.displayName?.takeIf { it.isNotBlank() }
            ?: result.resultattype?.takeIf { it.isNotBlank() && !it.startsWith("Xrpt", ignoreCase = true) }
            ?: result.vaerditype?.takeIf { it.isNotBlank() && it != "Tekst" && it != "Numerisk" }
            ?: result.analysetypeId // Fald tilbage til teknisk kode som sidste udvej

        log.info("==> Final displayName: {}", displayName)

        // Hent analysekode - prioriter fra QuantitativeFindings, derefter undersoegelse, til sidst result
        val analyseKode = labData?.analyseKode
            ?: undersoegelse?.analyseKode?.takeIf { it.isNotBlank() }
            ?: result.analysetypeId

        observation.code = CodeableConcept().apply {
            text = displayName
            // Tilføj teknisk kode som coding
            analyseKode?.let { code ->
                addCoding(
                    Coding()
                        .setSystem("https://www.sundhed.dk/codes/labsvar")
                        .setCode(code)
                        .setDisplay(displayName)
                )
            }
        }

        // Sæt værdi - numerisk hvis muligt, ellers tekst
        if (labData?.value != null) {
            observation.setValue(Quantity().apply {
                value = labData.value
                labData.unit?.let { unit = it }
            })
        } else if (labData?.resultText != null) {
            // Tekst-resultat som "Ikke påvist", "Påvist" etc.
            observation.setValue(StringType(labData.resultText))
        } else {
            // Fald tilbage til andre tekst-felter
            val narrativeValue = cleanText(result.konklusionHtml)
                ?: cleanText(result.diagnoseHtml)
                ?: cleanText(result.mikroskopiHtml)
                ?: cleanText(result.makroskopiHtml)
                ?: result.vaerdi
            narrativeValue?.let { observation.setValue(StringType(it)) }
        }

        result.referenceIntervalTekst?.let {
            observation.referenceRange = listOf(
                Observation.ObservationReferenceRangeComponent().apply {
                    text = it
                }
            )
        }

        result.resultatdato?.let { observation.setEffective(parseDateType(it)) }
            ?: rekvisition?.proevetagningstidspunkt?.let { observation.setEffective(parseDateType(it)) }
        result.resultatdato?.let { observation.setIssued(parseDate(it)) }

        rekvisition?.rekvirentsOrganisation?.let {
            observation.setPerformer(
                listOf(
                    Reference().apply {
                        display = it
                    }
                )
            )
        } ?: undersoegelse?.eksaminator?.let {
            observation.setPerformer(listOf(Reference().apply { display = it }))
        }

        rekvisition?.let {
            val subjectRef = Reference()
            subjectRef.setIdentifier(
                Identifier()
                .setSystem("urn:dk:cpr")
                .setValue(it.patientCpr ?: hashId(observation.id))
            )
            subjectRef.display = it.patientNavn
            observation.setSubject(subjectRef)
        }

        result.analysevejledningLink?.let {
            observation.note = listOf(
                Annotation().apply {
                    text = "Analysevejledning: $it"
                }
            )
        }

        // Attach pathology-rich text as notes when present
        val extraNotes = listOfNotNull(
            analyseKode?.let { "Analysekode: $it" },
            cleanText(result.materialeHtml)?.let { "Materiale: $it" },
            cleanText(result.diagnoseHtml)?.let { "Diagnose: $it" },
            cleanText(result.konklusionHtml)?.let { "Konklusion: $it" },
            cleanText(result.mikroskopiHtml)?.let { "Mikroskopi: $it" },
            cleanText(result.makroskopiHtml)?.let { "Makroskopi: $it" },
            cleanText(result.kliniskeInformationerHtml)?.let { "Kliniske oplysninger: $it" }
        )
        if (extraNotes.isNotEmpty()) {
            observation.note = observation.note + extraNotes.map { txt ->
                Annotation().apply { text = txt }
            }
        }

        return observation
    }

    /**
     * Data class til at holde alle udtrukne felter fra QuantitativeFindings
     */
    data class ExtractedLabData(
        val displayName: String?,      // Fuldt læsbart navn (f.eks. "B—Erythrocytter; antalk.")
        val analyseKode: String?,      // Teknisk kode (f.eks. "NPU01960")
        val value: java.math.BigDecimal?,
        val unit: String?,
        val resultText: String?        // Tekst-resultat (f.eks. "Ikke påvist")
    )

    /**
     * Udtræk alle relevante felter fra QuantitativeFindings data array.
     *
     * Sundhed.dk's QuantitativeFindings.Data array struktur (typisk):
     * - data[0] = headers/kolonnenavne
     * - data[1] = værdier
     *
     * Kolonner (baseret på sundhed.dk format):
     * 0: Teknisk ID/kode (f.eks. "iupac_DNK35312")
     * 1: Fuldt analysenavn (f.eks. "B—Erythrocytter; antalk.")
     * 2: Analysekode display (f.eks. "NPU01960")
     * ...
     * 9: Værdi (numerisk eller "Ikke påvist")
     * 10: Enhed
     */
    private fun extractLabDataFromQuantitativeFindings(qf: QuantitativeFindings?): ExtractedLabData? {
        val data = qf?.data ?: return null
        if (data.size < 2) return null

        // Log headers og data for debugging
        val headers = data[0]
        val row = data[1]
        log.info("=== QuantitativeFindings DEBUG ===")
        log.info("Headers (${headers.size} columns): {}", headers.mapIndexed { i, h -> "$i: $h" })
        log.info("Data row (${row.size} columns): {}", row.mapIndexed { i, v -> "$i: $v" })

        if (row.isEmpty()) return null

        // Baseret på sundhed.dk struktur:
        // Kolonne 4 = "Text" indeholder det læsbare navn (f.eks. "SARS-CoV-2")
        // Kolonne 1 = "Code" indeholder teknisk kode (f.eks. "DNK35312")
        val displayName = row.getOrNull(4)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        log.info("Extracted displayName from col 4 (Text): {}", displayName)

        // Kolonne 1 = "Code" indeholder analysekoden (f.eks. "DNK35312" eller "NPU01960")
        val analyseKode = row.getOrNull(1)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        log.info("Extracted analyseKode from col 1 (Code): {}", analyseKode)

        // Kolonne 9 indeholder værdien
        val valueStr = row.getOrNull(9)?.toString()?.trim().orEmpty()
        val isTextResult = valueStr.equals("Ikke påvist", ignoreCase = true) ||
                          valueStr.equals("Påvist", ignoreCase = true) ||
                          valueStr.equals("Negativ", ignoreCase = true) ||
                          valueStr.equals("Positiv", ignoreCase = true)

        val numericValue = if (!isTextResult) valueStr.toBigDecimalOrNull() else null
        val resultText = if (isTextResult || numericValue == null && valueStr.isNotBlank()) valueStr else null
        log.info("Extracted value: numeric={}, text={}", numericValue, resultText)

        // Kolonne 10 indeholder enheden
        val unit = row.getOrNull(10)?.toString()?.trim()?.takeIf { it.isNotBlank() }

        return ExtractedLabData(
            displayName = displayName,
            analyseKode = analyseKode,
            value = numericValue,
            unit = unit,
            resultText = resultText
        )
    }

    private fun extractNumericValue(qf: QuantitativeFindings?): Quantity? {
        val labData = extractLabDataFromQuantitativeFindings(qf) ?: return null
        val value = labData.value ?: return null

        return Quantity().apply {
            this.value = value
            labData.unit?.let { this.unit = it }
        }
    }

    /**
     * Udtræk læsbart analysenavn fra QuantitativeFindings
     */
    private fun extractDisplayNameFromQuantitativeFindings(qf: QuantitativeFindings?): String? {
        return extractLabDataFromQuantitativeFindings(qf)?.displayName
    }

    /**
     * Udtræk analysekode fra QuantitativeFindings
     */
    private fun extractAnalyseKodeFromQuantitativeFindings(qf: QuantitativeFindings?): String? {
        return extractLabDataFromQuantitativeFindings(qf)?.analyseKode
    }

    /**
     * Udtræk tekst-resultat (f.eks. "Ikke påvist") fra QuantitativeFindings
     */
    private fun extractResultTextFromQuantitativeFindings(qf: QuantitativeFindings?): String? {
        return extractLabDataFromQuantitativeFindings(qf)?.resultText
    }

    private fun mapStatus(statusCode: String?, statusText: String?): Observation.ObservationStatus {
        return when (statusCode ?: statusText) {
            "SvarEndeligt", "KompletSvar" -> Observation.ObservationStatus.FINAL
            "Foreloebigt" -> Observation.ObservationStatus.PRELIMINARY
            "Annulleret" -> Observation.ObservationStatus.CANCELLED
            else -> Observation.ObservationStatus.UNKNOWN
        }
    }

    private fun parseDateType(dateTime: String): DateTimeType =
        DateTimeType(Date.from(OffsetDateTime.parse(dateTime).toInstant()))

    private fun parseDate(dateTime: String): Date =
        Date.from(OffsetDateTime.parse(dateTime).toInstant())

    private fun safeId(raw: String): String =
        raw.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .take(64)

    private fun hashId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun emptyBundle(requestUrl: String): Bundle =
        Bundle().apply {
            type = Bundle.BundleType.SEARCHSET
            total = 0
            link = listOf(Bundle.BundleLinkComponent().apply {
                relation = "self"
                url = requestUrl
            })
        }
}
