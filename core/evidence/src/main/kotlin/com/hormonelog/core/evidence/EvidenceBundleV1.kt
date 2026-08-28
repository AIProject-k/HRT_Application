package com.hormonelog.core.evidence

import com.hormonelog.core.domain.Route

/**
 * Packaged evidence bundle v1.
 *
 * Parameters are literature-derived *population* approximations for exploration —
 * NOT clinically validated and not for dosing decisions. The E2 3-compartment
 * rate constants and the patch parameters are taken from the open-source
 * estrannaise.js project (MIT, © 2025 alix); oral/sublingual and the TT PK/PD
 * constants are coarse fits to the cited abstracts and reviews. Gel is
 * deliberately omitted — label-only PK, postmenopausal population, high site
 * variability — so `supports(Route.GEL)` stays false.
 */
object EvidenceBundleV1 {

    private val sources = listOf(
        EvidenceSource(
            "estrannaise", "estrannaise.js — estradiol pharmacokinetics playground",
            "alix (WHSAH)", 2025, "github.com/WHSAH/estrannaise.js (MIT)",
            "3-compartment models MAP/MCMC-fit to published serum E2 curves", "open-source model, tertiary",
        ),
        EvidenceSource(
            "transfemsci_inj_meta", "An Informal Meta-Analysis of Estradiol Curves with Injectable Estradiol Preparations",
            "Transfeminine Science", 2022, "transfemscience.org/articles/injectable-e2-meta-analysis/",
            "~28 studies / 309 EV injections pooled (Oriowo 1980, Garza-Flores 1994, Göretzlehner 2002, Valle Alvarez 2011, Schug 2012 n=48@10mg); EV 5 mg Cmax ~295 pg/mL @ 2.1 d, t½ ~3.0 d, AUC 1886 pg·d/mL. Düsterberg 1982 (n=2) excluded as an outlier.", "narrative meta-analysis",
        ),
        EvidenceSource(
            "seifert2025_inj_mono", "Injectable Estradiol Monotherapy Effectively Suppresses Testosterone in Gender-Affirming Hormone Therapy",
            "Kaiser Permanente (S1530-891X(25)00945-0)", 2025, "PMID 40639470, Endocrine Practice",
            "357 patients; median E2 232 pg/mL (IQR 134–371) → median total T 17 ng/dL (IQR 10–33); 82.6% reach T < 50 ng/dL on injectable monotherapy; progestogen further lowers T, spironolactone does not.", "retrospective cohort",
        ),
        EvidenceSource(
            "doll2022", "Pharmacokinetics of Sublingual Versus Oral Estradiol in Transgender Women",
            "Doll et al.", 2022, "PMID 34781041, Endocrine Practice",
            "10 transgender women; 1 mg single dose; SL Cmax 144 pg/mL @ 1 h vs oral 35 pg/mL @ 8 h (LC-MS/MS); 0–8 h sampling", "primary crossover PK",
        ),
        EvidenceSource(
            "winston_mcpherson2025", "Estradiol Concentrations for Adequate Gender-Affirming Feminizing Therapy: A Systematic Review",
            "Winston-McPherson et al.", 2025, "LGBT Health / systematic review",
            "route/dose vs serum E2 and T; oral linear ~ −19 ng/dL T per 1 mg/d", "systematic review",
        ),
        EvidenceSource(
            "ucsf_guidelines", "UCSF Gender-Affirming Health Program — Feminizing hormone therapy",
            "Deutsch MB (ed.)", 2020, "transcare.ucsf.edu/guidelines/feminizing-hormone-therapy",
            "target E2 100–200 pg/mL, total T < 50 ng/dL", "clinical guideline",
        ),
        EvidenceSource(
            "wiki_cpa", "Pharmacology of cyproterone acetate",
            "Wikipedia contributors", 2025, "en.wikipedia.org/wiki/Pharmacology_of_cyproterone_acetate",
            "oral F ~88%, elimination t½ ~1.6–4.3 d; CPA alone suppresses T 50–70%, ~95% with estrogen; low doses near-maximal", "tertiary",
        ),
    )

    // ── E2 3-compartment rate constants [d, k1, k2, k3] (estrannaise) ──
    private data class C3(val d: Double, val k1: Double, val k2: Double, val k3: Double)
    private val injectable = mapOf(
        "EV" to C3(478.0, 0.236, 4.85, 1.24),
        "EC" to C3(246.0, 0.0825, 3.57, 0.669),
        "EEn" to C3(191.4, 0.119, 0.601, 0.402),
        "EB" to C3(1893.1, 0.67, 61.5, 4.34),
        "EUn" to C3(471.5, 0.01729, 6.528, 2.285),
    )
    private val patch = mapOf(
        "tw" to C3(16.792, 0.283, 5.592, 4.3),
        "ow" to C3(59.481, 0.107, 7.842, 5.193),
    )

    private fun c3Params(route: Route, ester: String, c: C3, evidence: Set<String>) = listOf(
        ModelParameter("$route.$ester.d", route, ester, "d", c.d, "pg/mL per mg", evidence),
        ModelParameter("$route.$ester.k1", route, ester, "k1", c.k1, "1/day", evidence),
        ModelParameter("$route.$ester.k2", route, ester, "k2", c.k2, "1/day", evidence),
        ModelParameter("$route.$ester.k3", route, ester, "k3", c.k3, "1/day", evidence),
    )

    private fun oneComp(route: Route, prefix: String, scale: Double, ka: Double, ke: Double, swallowed: Double?, evidence: Set<String>) =
        buildList {
            add(ModelParameter("$route.${prefix}Scale", route, null, "${prefix}Scale", scale, "pg/mL per mg", evidence))
            add(ModelParameter("$route.${prefix}Ka", route, null, "${prefix}Ka", ka, "1/day", evidence))
            add(ModelParameter("$route.${prefix}Ke", route, null, "${prefix}Ke", ke, "1/day", evidence))
            if (swallowed != null) {
                add(ModelParameter("$route.${prefix}Swallowed", route, null, "${prefix}Swallowed", swallowed, "fraction", evidence))
            }
        }

    private fun pd(name: String, value: Double, unit: String, evidence: Set<String>) =
        ModelParameter("pd.$name", null, "tt_pd", name, value, unit, evidence)

    private val parameters: List<ModelParameter> = buildList {
        val injEvidence = setOf("estrannaise", "transfemsci_inj_meta")
        injectable.forEach { (e, c) -> addAll(c3Params(Route.IM_INJECTION, e, c, injEvidence)) }
        // SC reuses the ester PK with wider uncertainty applied in the engine.
        injectable.forEach { (e, c) -> addAll(c3Params(Route.SC_INJECTION, e, c, injEvidence)) }
        val patchEvidence = setOf("estrannaise")
        patch.forEach { (v, c) -> addAll(c3Params(Route.PATCH, v, c, patchEvidence)) }

        val oralEvidence = setOf("doll2022", "winston_mcpherson2025")
        addAll(oneComp(Route.ORAL, "oral", scale = 98.6, ka = 3.5, ke = 1.28, swallowed = null, evidence = oralEvidence))
        addAll(oneComp(Route.SUBLINGUAL, "sl", scale = 1111.0, ka = 18.0, ke = 9.9, swallowed = 0.4, evidence = setOf("doll2022")))
        // sublingual also needs the swallowed fraction routed through the oral params:
        addAll(oneComp(Route.SUBLINGUAL, "oral", scale = 98.6, ka = 3.5, ke = 1.28, swallowed = null, evidence = oralEvidence))

        // ── TT PK/PD (non-route-scoped) ──
        // E2→T Hill calibrated to Seifert 2025: injectable monotherapy at E2 ~232 pg/mL → T ~17 ng/dL.
        val ttEv = setOf("ucsf_guidelines", "winston_mcpherson2025", "seifert2025_inj_mono")
        val cpaEv = setOf("wiki_cpa")
        add(pd("ttBaselineIntact", 600.0, "ng/dL", ttEv))
        add(pd("ttBaselinePostOrchi", 30.0, "ng/dL", ttEv))
        add(pd("ttFloor", 8.0, "ng/dL", ttEv))
        add(pd("ttIntervalFraction", 0.5, "fraction", ttEv))
        add(pd("e2EffectTauDays", 7.0, "day", ttEv))
        add(pd("e2SupprEmax", 0.97, "fraction", ttEv))
        add(pd("e2SupprEC50", 45.0, "pg/mL", ttEv))
        add(pd("e2SupprHill", 2.2, "", ttEv))
        add(pd("cpaF", 0.88, "fraction", cpaEv))
        add(pd("cpaKaPerDay", 6.0, "1/day", cpaEv))
        add(pd("cpaKePerDay", 0.365, "1/day", cpaEv))
        add(pd("cpaSupprEmax", 0.6, "fraction", cpaEv))
        add(pd("cpaSupprEC50", 8.0, "level", cpaEv))
    }

    val bundle: EvidenceBundle = EvidenceBundle(
        version = "v1",
        sources = sources.associateBy { it.id },
        parameters = parameters,
    )
}
