package com.filestech.callhalt.platform.notification

/**
 * Whether an SMS reads as a validation code — the one a fake adviser has the victim read out (PROJET.md, § 4.3) — in
 * French or in English (docs/ARCHITECTURE.md, D-021). Read in memory and forgotten at once.
 *
 * Almost every SMS carries a number shaped like a code (an opt-out "STOP au 36111", a year, a postcode, a number to
 * call), and French says "code" for many things that are not one (a promo code, a delivery code, a door code, the
 * highway code). So the words decide, in two tiers (false-positive audit of 2026-11-02, 126 messages):
 * - a phrase only a validation code uses ("code de validation", "usage unique", "Certicode"…) makes it one;
 * - a bare "code" or "mot de passe" makes it one too, unless the message says what else it is (delivery, promo,
 *   door…) — and even then, a warning or a payment ("ne le communiquez jamais", "paiement") makes it one again: "never
 *   give it, even to a courier" is the fake-courier scam itself.
 *
 * A false code costs a STOP during a call; a missed one, the code read out: in doubt, it is a code. The corpus of
 * ValidationCodeTest says what was decided, message by message.
 *
 * The numbers that are never a code, the other kinds of code and the warnings or payments that make one a code again are
 * read in German, Italian and Spanish too (publication audit of 2026-11-07, M1 to M3): a German card notice or an
 * Italian key-box code was taken for a code. The codes themselves are recognised in French and English only (the
 * "code" and "PIN" words aside). Words with accents are bounded by "not after a letter" rather than by a word boundary:
 * the meaning of a word boundary next to a non-ASCII letter changed in JDK 19, so the tests would not speak for Android.
 */
fun isValidationCode(text: CharSequence): Boolean {
    val plain = NEVER_A_CODE.fold(text.toString()) { remaining, numbers -> numbers.replace(remaining, " ") }
    if (!CODE_NUMBER.containsMatchIn(plain)) return false
    val strong = STRONG_CODE_PHRASE.containsMatchIn(plain)
    val vetoed = !strong && OTHER_KIND_OF_CODE.containsMatchIn(plain) && !WARNING_OR_PAYMENT.containsMatchIn(plain)
    return (strong || CODE_WORD.containsMatchIn(plain)) && !vetoed
}

/**
 * Numbers that are never a validation code, blanked out before the code is looked for: the opt-out of a commercial
 * SMS, a written or dashed date, a year after "année", a number to call, a card's last digits, an IBAN or a bank code,
 * a reference after "n°" or "numéro", or right before its number after "No." or "Nr." ("Your PIN No. is 4821" is a code).
 */
private val NEVER_A_CODE = listOf(
    """\bstop\b[ \t\u00A0:]*(?:sms[ \t\u00A0:]*)?(?:au[ \t\u00A0:]*|to[ \t\u00A0:]*)?[0-9]{5}\b""",
    """\b(?:[0-9]{1,2}(?:er|\.)?[ \u00A0]+(?:de[ \u00A0]+)?)?(?:janvier|f[ée]vrier|mars|avril|mai|juin|juillet|ao[uû]t|septembre|""" +
        """octobre|novembre|d[ée]cembre|january|february|march|april|may|june|july|august|september|october|""" +
        """november|december|januar|februar|m[äa]rz|juni|juli|oktober|dezember|gennaio|febbraio|marzo|aprile|maggio|""" +
        """giugno|luglio|agosto|settembre|ottobre|dicembre|enero|febrero|abril|mayo|junio|julio|septiembre|octubre|""" +
        """noviembre|diciembre|janv|f[ée]vr?|avr|juil|sept?|oct|nov|d[ée]c|jan|feb|mar|apr|jun|jul|aug|dec)\.?""" +
        """[ \u00A0]+(?:de[ \u00A0]+)?[0-9]{4}\b""",
    """\b[0-9]{1,2}-[0-9]{1,2}-[0-9]{4}\b|\b[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}\b""",
    """\b(?:ann[ée]e|year)[ \u00A0]+[0-9]{4}\b""",
    """(?:appelez|contactez|composez|rappelez|joindre|call(?:[ \u00A0]+us)?(?:[ \u00A0]+(?:on|at))?|unter|""" +
        """chiama(?:ci)?(?:[ \u00A0]+al)?|llam[ae](?:nos)?(?:[ \u00A0]+al)?)[ \u00A0]+(?:le[ \u00A0]+|au[ \u00A0]+)?""" +
        """[0-9](?:[0-9]|[ \u00A0.](?=[0-9])){2,16}[0-9]""",
    """(?:se terminant par|finissant par|(?<!\p{L})ending(?: (?:in|with))?|(?<!\p{L})(?:karten)?endung|endet auf|""" +
        """termina(?:ta|da)?[ \u00A0]+(?:con|in|en)|[*Xx•]{2,})[ \u00A0]*[0-9]{4}\b""",
    """\b[A-Z]{2}[0-9]{2}(?:[ \u00A0]?[0-9A-Z]{4}){2,7}(?:[ \u00A0]?[0-9A-Z]{1,4})?\b|\bcode[ \u00A0]+(?:banque|guichet)[ :]*[0-9]{5}\b""",
    """\b(?:n°|nº|num[ée]ro)(?:[ \u00A0]+\p{L}+){0,2}[ \u00A0:]*[0-9]+""",
    """(?<!(?:pin|code|otp)[ \u00A0:-]{1,2})\b(?:nr|no)\.[ \u00A0:]*[0-9]+""",
).map { Regex(it, RegexOption.IGNORE_CASE) }

/**
 * Four to eight digits, or two groups of three ("482 913", "482-913": WhatsApp, Google), with no digit, letter, date,
 * time, amount or decimal glued to it. A colon before it is allowed ("Code:482913"), a full stop after it is the end of
 * a sentence. Spaces and digits are written out: Android's regex engine reads `\s` and `\d` as Unicode, the JVM's of
 * the tests as ASCII — the French non-breaking spaces would then pass one and not the other.
 */
private val CODE_NUMBER = Regex(
    """(?<![0-9/.,€$£A-Za-z])(?:[0-9]{3}[ \u00A0\u202F-][0-9]{3}|[0-9]{4,8})""" +
        """(?![0-9/:€$£%A-Za-z]|[.,][0-9]|[ \u00A0\u202F]?(?:€|%|eur(?:os?)?\b|h\b|min\b))""",
    RegexOption.IGNORE_CASE,
)

/** The phrases only a validation code uses. One of them makes the message a code, whatever else it says. */
private val STRONG_CODE_PHRASE = Regex(
    """\b(?:otp|passcode|3d[ -]?s(?:ecure)?|certicode|usage unique|one[ -]?time|""" +
        """(?:codes?|mot de passe)[ \u00A0]+(?:de[ \u00A0]+|d['’])?(?:validation|confirmation|v[ée]rification|""" +
        """s[ée]curit[ée]|authentification|connexion|activation|identification|signature|paiement)|""" +
        """(?:verification|security|login|sign[ -]?in|confirmation|authentication)[ \u00A0]+codes?|""" +
        """(?:saisissez|entrez|tapez|indiquez|renseignez|enter)[ \u00A0]+(?:le[ \u00A0]+)?(?=[0-9])|""" +
        """valider[ \u00A0]+(?:votre[ \u00A0]+|l['’]|cet(?:te)?[ \u00A0]+)?(?:achat|paiement|op[ée]ration|virement|transaction))""",
    RegexOption.IGNORE_CASE,
)

/** A bare code word: a code unless the message says it is another kind of code. */
private val CODE_WORD = Regex("""\b(?:codes?|mot de passe|password|pin)\b""", RegexOption.IGNORE_CASE)

/**
 * What else a "code" is: a promotion, a delivery, a door, a booking, the highway code, a QR code… Not a phone's or a
 * cash machine's keypad, a voicemail's "buzón", "Tor" without an article (a name, a browser) nor Italian's bare "chiavi"
 * (a bank's "chiavi di accesso"): a bank's code says them too.
 */
private val OTHER_KIND_OF_CODE = Regex(
    """\b(?:promo(?:tion(?:nel)?)?|r[ée]duc(?:tion)?|remise|parrain(?:age)?|cadeau|avantage|fid[ée]lit[ée]|soldes|""" +
        """offerte?s?|profitez|postal|livraison|livr[ée]e?s?|livreur|facteur|colis|locker|consigne|casier|relais|qr|""" +
        """barres?|portail|portes?(?!-)|immeuble|digicode|interphone|bo[iî]te|garage|parking|[ée]tage|b[aâ]timent|""" +
        """r[ée]sidence|cadenas|wi-?fi|route|r[ée]servation|booking|discount|coupon|voucher|referral|gift|delivery|""" +
        """parcel|courier|package|pickup|door|gate|zip|dress|lock[ -]?box|key[ -]?(?:safe|box)|""" +
        """(?<!atm[ \u00A0])(?<!phone[ \u00A0])(?<!phone['’]s[ \u00A0])keypad|alarm|haust[üu]r|t[üu]r|""" +
        """(?:das|dem|den|f[üu]rs|ans|am|zum|vom|ins)[ \u00A0]+tor|torcode|schl[üu]ssel(?:kast|box|safe|tresor|k[äa]st)\p{L}*|""" +
        """cassetta|portachiavi|llaves|buz[óo]n(?![ \u00A0]+de[ \u00A0]+voz))\b|""" +
        """\bcode[ \u00A0]*(?:client|de[ \u00A0]+suivi|d['’]entr[ée]e)\b""",
    RegexOption.IGNORE_CASE,
)

/** A warning against giving the code, or a payment: the message is a code again, whatever else it says. */
private val WARNING_OR_PAYMENT = Regex(
    """\b(?:jamais|never|anyone|no one|paiement|payment|virement|transfer|b[ée]n[ée]ficiaire|beneficiary|""" +
        """achat|purchase)\b|""" +
        // "Personne" warns ("à personne", "à aucune personne", "personne ne", "une personne se présentant comme…"), but
        // "une personne de votre choix peut retirer le colis" does not. "à" may come decomposed, "a" and its accent.
        """(?<!\p{L})(?:(?:[àa]\u0300?|avec)[ \u00A0]+(?:une[ \u00A0]+tierce[ \u00A0]+)?|aucune[ \u00A0]+(?:autre[ \u00A0]+)?)""" +
        """personne(?!\p{L})|(?<!\p{L})personne[ \u00A0]+(?:ne|n['’]|d['’]autre)(?!\p{L})|""" +
        """(?<!\p{L})personne[ \u00A0]+se[ \u00A0]+(?:pr[ée]sentant[ \u00A0]+comme|disant|faisant[ \u00A0]+passer)|""" +
        """(?:^|[.!?][ \u00A0]+)personne(?!\p{L})[^.!?\n]{0,40}?(?<!\p{L})ne(?!\p{L})|""" +
        // The same in German, Italian and Spanish. Not "nie" ("vergiss ihn nie") nor "mai" (French's May).
        """(?<!\p{L})(?:niemals|niemand(?:em|en)?|nessuno|nessun|nunca|nadie|zahlung\p{L}*|[üu]berweisung\p{L}*|""" +
        """pagamento|bonifico|pago|transferencia)(?!\p{L})""",
    RegexOption.IGNORE_CASE,
)
