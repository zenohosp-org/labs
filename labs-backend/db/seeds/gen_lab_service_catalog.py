#!/usr/bin/env python3
"""
Generate the lab_service_catalog master data resource from the raw LOINC release.

Output is a gzipped, header-less CSV committed at
  src/main/resources/db/data/lab_service_catalog.csv.gz
which the Flyway Java migration V21__load_lab_service_catalog streams into the
table via PostgreSQL COPY on boot. Gzipped (~2.7 MB) rather than a ~24 MB SQL
migration: 9x smaller in the repo/jar, and COPY loads it in ~1s.

The raw LOINC table (Loinc.csv, ~109k rows, licensed) is not committed. This
generator is the committed provenance — re-run it to regenerate the resource
from your local LOINC copy:

    python3 gen_lab_service_catalog.py \\
        ~/Downloads/loinc/Loinc_2.82/LoincTable/Loinc.csv \\
        ../src/main/resources/db/data/lab_service_catalog.csv.gz

Selection: ACTIVE + Laboratory class (CLASSTYPE=1) only — 60,009 rows in LOINC
2.82. Clinical observations, survey instruments, claims codes, and deprecated
terms are deliberately excluded; none is an orderable lab test.

Column order matches the COPY statement in V21 exactly:
  loinc_code, test_code, name, aliases, category, discipline, specimen_kind,
  default_unit, value_type, is_panel

Empty fields are written empty; V21's COPY uses NULL '' so they become NULL for
the nullable columns. Mapping (from the curated top-2000 seed, so master and the
per-hospital baseline classify identically) lands every value in lab_services'
closed vocabularies — a copied row already satisfies its CHECK constraints:
  test_code   <- LOINC_NUM ;  name <- LONG_COMMON_NAME(200) ;  aliases <- RELATEDNAMES2(500)
  category    <- CLASS      ;  discipline <- CLASS->PATHOLOGY|CYTOLOGY|HISTOPATHOLOGY
  specimen    <- SYSTEM     ;  value_type <- SCALE_TYP       ;  is_panel <- "PANEL" in CLASS
"""
import csv
import gzip
import sys

SCALE_TO_VALUE_TYPE = {
    "Qn": "NUMERIC", "OrdQn": "NUMERIC", "SemiQn": "NUMERIC",
    "Nom": "CODED", "Ord": "CODED",
    "Nar": "TEXT", "Doc": "TEXT", "Multi": "TEXT", "Set": "TEXT", "-": "TEXT",
}

SYSTEM_TO_SPECIMEN = {
    "Ser/Plas": "BLOOD", "Ser": "BLOOD", "Plas": "BLOOD", "Bld": "BLOOD", "RBC": "BLOOD",
    "Ser/Plas/Bld": "BLOOD", "PPP": "BLOOD", "PPP/Bld": "BLOOD", "BldC": "BLOOD",
    "BldCoV": "BLOOD", "BldA": "BLOOD", "BldV": "BLOOD", "PlasV": "BLOOD", "BldCo": "BLOOD",
    "Bld.dot": "BLOOD", "Ser/Plas.ultracentrifugate": "BLOOD", "BldCoA": "BLOOD",
    "BldMV": "BLOOD", "RBCCo": "BLOOD", "Retic": "BLOOD", "BldA+Inhl gas": "BLOOD",
    "Urine": "URINE", "Urine sed": "URINE",
    "Stool": "STOOL", "Meconium": "STOOL",
    "Tiss": "TISSUE", "Bone mar": "TISSUE", "Bone": "TISSUE",
    "CSF": "CSF",
    "Wound.shlw": "SWAB", "Wound.deep": "SWAB", "Wound": "SWAB", "Thrt": "SWAB",
    "Cvx/Vag": "SWAB", "Cvx": "SWAB", "Vag": "SWAB", "Nose": "SWAB", "Nph": "SWAB",
    "Genital": "SWAB", "Abscess": "SWAB", "Eye": "SWAB", "Anal": "SWAB",
    "Vag+Rectum": "SWAB", "Burn": "SWAB",
}


def trunc(x, n):
    # Collapse embedded newlines/tabs to spaces so no field can break the COPY
    # stream, then truncate to the column limit.
    return " ".join((x or "").split())[:n]


def main():
    if len(sys.argv) != 3:
        sys.exit(f"usage: {sys.argv[0]} <Loinc.csv> <out.csv.gz>")
    src, dst = sys.argv[1], sys.argv[2]
    n = 0
    with open(src, newline="", encoding="utf-8") as fh, \
         gzip.open(dst, "wt", newline="", encoding="utf-8") as out:
        w = csv.writer(out, lineterminator="\n")  # COPY CSV wants \n records
        for r in csv.DictReader(fh):
            if r.get("CLASSTYPE") != "1" or r.get("STATUS") != "ACTIVE":
                continue
            loinc = r["LOINC_NUM"]
            cls = r.get("CLASS", "") or ""
            disc = "CYTOLOGY" if cls.startswith("CYTO") else ("HISTOPATHOLOGY" if cls.startswith("PATH") else "PATHOLOGY")
            w.writerow([
                loinc, loinc,
                trunc(r.get("LONG_COMMON_NAME") or r.get("SHORTNAME") or loinc, 200),
                trunc(r.get("RELATEDNAMES2"), 500),
                trunc(cls, 50), disc,
                SYSTEM_TO_SPECIMEN.get((r.get("SYSTEM", "") or "").split("^")[0], "OTHER"),
                "",  # default_unit: LOINC example units are unreliable; leave blank
                SCALE_TO_VALUE_TYPE.get(r.get("SCALE_TYP", ""), "TEXT"),
                "true" if "PANEL" in cls else "false",
            ])
            n += 1
    print(f"wrote {n:,} rows -> {dst}")


if __name__ == "__main__":
    main()
