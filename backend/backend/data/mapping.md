# Dataset → Category Mapping

Each row in `corpus.csv` carries a `source` column so the methodology section
of the report can audit which dataset contributed which examples.

| Category | Source(s) | Filter | Notes |
|---|---|---|---|
| `urgent` | Disaster Tweets (`venetis/disaster_tweets`, fallbacks `Sachinkelenjaguri/Disaster_Tweets`, `VuduVations/disaster_tweets`) | `target == 1`, length 20–280 chars | Tweets about real emergencies. Short, time-sensitive. Closest public proxy for urgent personal messages — limitation discussed in methodology. |
| `work` | Enron emails (`SetFit/enron_spam` ham subset, fallback `snoop2head/enron_aeslc_emails`) | length 30–400 chars AND subject/body contains one of `meeting, deadline, report, project, proposal, invoice, EOD, contract, schedule, update, action item, status, review, deliverable, q1-q4, fy2024` | Filters down ~500k Enron mails to short, business-style messages. |
| `family_friends` | DailyDialog (`OpenRL/daily_dialog`, fallback `yangwang825/daily_dialog_plus_plus`) | Utterance length 25–300 chars. Neutral-emotion utterances downsampled to 20% to favour expressive lines. | All DailyDialog dialogues are by definition daily/personal conversation — the `topic` annotation isn't exposed in parquet-based mirrors after `datasets` 4.x dropped script support, so we use the whole corpus rather than filtering by topic. |
| `promotional` | YouTube Spam Collection (UCI, 5 video CSVs) + Enron spam-labelled rows | YouTube: `CLASS == 1` (promo comments). Enron: `label_text == "spam"` AND length 30–400. | Subscribe/check-out style. Distinct from outright SMS spam (different vocabulary). |
| `spam` | SMS Spam Collection (UCI) | `label == "spam"` | Classic SMS spam patterns: lottery, prize, txt to win. |
| `other` | SMS Spam ham subset + Enron ham (non-work keywords) | SMS: `label == "ham"` AND 40–200 chars. Enron: ham AND 60–250 chars AND no work keywords. | Short conversational text that doesn't cleanly fit the other five buckets. |

## Caps & balancing

`PER_CATEGORY_CAP` in `build.py` controls the target per-category row count.
Sources contribute in the order listed in `LOADERS`; rows are shuffled within
each source with a fixed seed before capping, so reruns are deterministic.

| Category | Cap |
|---|---|
| urgent | 800 |
| work | 1500 |
| family_friends | 1500 |
| promotional | 1000 |
| spam | 750 |
| other | 700 |

Total target: ~6,250 rows. Real counts depend on what each source returns after
filtering — the orchestrator logs the actual `kept / available` per category.

## Split

70/15/15 stratified by category. The **test set is frozen on first build**: re-running
`python -m backend.data.build` will refresh train+val but never overwrite `test.csv`,
so eval numbers stay comparable across model iterations. Pass `--reset-test` to override.

## Licenses

| Source | License | Redistribution |
|---|---|---|
| UCI SMS Spam Collection | CC BY 4.0 | Downloaded at build time, not redistributed in repo. |
| UCI YouTube Spam Collection | CC BY 4.0 | Same. |
| DailyDialog | Research-only (cite Li et al. 2017) | Cached locally; corpus.csv contains derived text only. |
| Enron Email | Public-domain Federal Energy Regulatory Commission release | Same. |
| Kaggle "Real or Not?" Disaster Tweets | Kaggle competition data, academic use permitted | Accessed via HuggingFace mirror. |

`build.py` writes everything under `data/` which is gitignored — the repo never
redistributes the source datasets, only the trained model artefacts.

## Known limitations (for the report)

1. **Disaster tweets ≠ urgent personal messages.** Tweets about real-world
   emergencies have a different distribution than urgent texts from a friend
   ("running late, are you free?"). The hand-labelled gold set (see
   `eval.py`) is the honest test of real-world Urgent classification.

2. **Enron is 25 years old.** Vocabulary skews toward late-1990s corporate
   communication. Should generalise to most Work patterns but not perfectly.

3. **DailyDialog utterances are scripted.** They're written for a research
   corpus, not real-world chat, so they lack typos, emoji, and abbreviation
   patterns of real messenger text.

These three biases are acknowledged in the methodology section and motivate
the per-user RAG layer: even if the base classifier has dataset bias, RAG
adapts to the user's actual conversational distribution at inference time.
