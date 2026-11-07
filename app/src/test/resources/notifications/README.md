# Notification regression corpus

`device-notifications.json` contains manually anonymized reconstructions of the
notification notes inspected in the user's app: settlement notices, campaigns,
bill-payment acknowledgements, Google Wallet payments, and an incoming transfer.
The statement-arrival example comes from the user's description and is marked
`user_report_reconstruction`; the other examples are marked
`device_note_reconstruction`.

These are **not raw notification exports**. The app often saves only a merchant
as a transaction note, and some saved notes were truncated. Merchant-only notes
are excluded because their original notification cannot be recovered. Examples
with an incomplete ending are marked `textMayBeTruncated` and remain incomplete
in the replay, rather than inventing missing text.

Amounts, merchant names, card suffixes, dates, times, branch names, card products,
and personalized bank references have been replaced with fictitious values.
Source row IDs, account numbers, device identifiers, capture timestamps, and raw
database copies are not included. Generic financial wording, punctuation,
currency formatting, and the public Google Wallet package identifier are kept
so that parser behavior is exercised realistically.

`NotificationCorpusTest` replays every case. A null `expectedAmount` means the
notification must produce neither a transaction nor an unparsed payment entry.
Positive examples verify the exact transaction amount and direction, plus the
merchant where it is recoverable. `NotificationAmountExtractorTest` separately
adds synthetic boundary cases with missing merchants/cards and multiple monetary
fields; those generated examples are not presented as device observations.
