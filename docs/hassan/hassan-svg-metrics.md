# Hassan — SVG import metrics

Source: [CI run 21](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45391572736). Baseline commit 4533afd; refactored commit 93a2051. Both measured in the same run with CK and PMD 7.17.0 (Java design rules).

| Metric (SVGInputFormat) | Before | After | Change |
|---|---:|---:|---:|
| LOC (CK) | 1963 | 1566 | -20.2% |
| WMC | 466 | 350 | -24.9% |
| CBO | 24 | 27 | +12.5% |
| RFC | 243 | 209 | -14.0% |
| LCOM | 1435 | 1971 | +37.4% |
| LCOM* | 0.90860224 | 0.927551 | increased |
| DIT | 1 | 1 | unchanged |
| NOC | 1 | 1 | unchanged |
| Methods | 62 | 70 | +8 |
| PMD design violations (file) | 124 | 67 | -46.0% |
| PMD CyclomaticComplexity violations | 17 | 13 | -4 |
| PMD CognitiveComplexity violations | 17 | 12 | -5 |
| PMD LawOfDemeter violations | 66 | 24 | -42 |
| PMD GodClass violations | 1 | 1 | unchanged |

Extracted SVGPathParser: LOC 368, WMC 90, CBO 3, RFC 24, LCOM 0, one method. The two top-level classes together have LOC 1934 and WMC 440, versus 1963 and 466 originally (reductions of 1.5% and 5.6%). These sums exclude separately reported nested types. The parser still has a complex method; extraction primarily separates responsibilities. Coupling and lack of cohesion increased in the importer, and the GodClass warning remains. PMD counts cover SVGInputFormat.java only, not the extracted parser; violation counts are not raw cyclomatic complexity scores. CK LOC differs from physical source line counts. No claim of full repository improvement is made.

Regression evidence: [run 20](https://github.com/shazilhamzah/JHotDraw-SRE-Project/actions/runs/45391062840) reports 80 SVG tests and 105 total executed tests, all passing, no skips. This was superseded by the final run 36 (128 tests passing); the TASKS.md coverage review is recorded in [hassan-svg-defects-and-coverage.md](hassan-svg-defects-and-coverage.md).
