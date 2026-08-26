package com.example.domain.model

enum class ContentType(val id: String) {
    URL("url"),
    TEXT("text"),
    EMAIL("email"),
    PHONE("phone"),
    SMS("sms"),
    WIFI("wifi"),
    VCARD("vcard"),
    LOCATION("location"),
    CALENDAR("calendar"),
    WHATSAPP("whatsapp"),
    TELEGRAM("telegram"),
    INSTAGRAM("instagram"),
    CRYPTO("crypto"),
    PAYPAL("paypal")
}

data class UrlContent(val url: String = "https://example.com")
data class TextContent(val text: String = "Hello, World!")
data class EmailContent(val to: String = "contact@example.com", val subject: String = "Inquiry", val body: String = "Hello QRaft team!")
data class PhoneContent(val phoneNumber: String = "+1234567890")
data class SmsContent(val phoneNumber: String = "+1234567890", val message: String = "Hello from QRaft!")
data class WifiContent(
    val ssid: String = "MyHomeWiFi",
    val password: String = "superSecret123",
    val encryption: String = "WPA", // WPA, WEP, nopass
    val hidden: Boolean = false
)
data class VCardContent(
    val firstName: String = "Alex",
    val lastName: String = "Morgan",
    val phone: String = "+1 (555) 019-2834",
    val email: String = "alex.morgan@design.io",
    val organization: String = "Creative Studio",
    val website: String = "https://alexmorgan.design"
)
data class LocationContent(
    val latitude: String = "37.7749",
    val longitude: String = "-122.4194",
    val address: String = "San Francisco, CA"
)
data class CalendarContent(
    val title: String = "Project Kickoff",
    val startDateTime: String = "2026-09-01T10:00:00",
    val endDateTime: String = "2026-09-01T11:30:00",
    val location: String = "Design Room & Video Call",
    val description: String = "Quarterly planning and kickoff"
)
data class WhatsAppContent(
    val phoneNumber: String = "+1234567890",
    val message: String = "Hi! I found your QR code on QRaft."
)
data class TelegramContent(
    val username: String = "username"
)
data class InstagramContent(
    val username: String = "instagram_user"
)
enum class CryptoCoin(val code: String, val title: String) {
    BTC("BTC", "Bitcoin"),
    ETH("ETH", "Ethereum"),
    USDT("USDT", "Tether (USDT)"),
    BNB("BNB", "BNB"),
    SOL("SOL", "Solana"),
    TRX("TRX", "TRON (TRX)"),
    DOGE("DOGE", "Dogecoin"),
    LTC("LTC", "Litecoin"),
    TON("TON", "Toncoin"),
    XRP("XRP", "Ripple (XRP)")
}

data class CryptoContent(
    val coin: CryptoCoin = CryptoCoin.BTC,
    val address: String = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
    val amount: String = ""
)
data class PayPalContent(
    val username: String = "mybusiness",
    val amount: String = "10.00",
    val currency: String = "USD"
)

object ContentEncoder {
    fun encode(
        type: ContentType,
        url: UrlContent,
        text: TextContent,
        email: EmailContent,
        phone: PhoneContent,
        sms: SmsContent,
        wifi: WifiContent,
        vcard: VCardContent,
        location: LocationContent,
        calendar: CalendarContent,
        whatsapp: WhatsAppContent = WhatsAppContent(),
        telegram: TelegramContent = TelegramContent(),
        instagram: InstagramContent = InstagramContent(),
        crypto: CryptoContent = CryptoContent(),
        paypal: PayPalContent = PayPalContent()
    ): String {
        return when (type) {
            ContentType.URL -> {
                val u = url.url.trim()
                if (u.startsWith("http://", true) || u.startsWith("https://", true)) u else "https://$u"
            }
            ContentType.TEXT -> text.text
            ContentType.EMAIL -> {
                val encSub = java.net.URLEncoder.encode(email.subject, "UTF-8").replace("+", "%20")
                val encBody = java.net.URLEncoder.encode(email.body, "UTF-8").replace("+", "%20")
                "mailto:${email.to}?subject=$encSub&body=$encBody"
            }
            ContentType.PHONE -> "tel:${phone.phoneNumber.trim()}"
            ContentType.SMS -> {
                val encMsg = java.net.URLEncoder.encode(sms.message, "UTF-8").replace("+", "%20")
                "sms:${sms.phoneNumber}?body=$encMsg"
            }
            ContentType.WIFI -> {
                val enc = when (wifi.encryption.uppercase()) {
                    "WEP" -> "WEP"
                    "NONE", "NOPASS" -> "nopass"
                    else -> "WPA"
                }
                "WIFI:T:$enc;S:${wifi.ssid};P:${wifi.password};H:${wifi.hidden};;"
            }
            ContentType.VCARD -> {
                buildString {
                    appendLine("BEGIN:VCARD")
                    appendLine("VERSION:3.0")
                    appendLine("N:${vcard.lastName};${vcard.firstName};;;")
                    appendLine("FN:${vcard.firstName} ${vcard.lastName}".trim())
                    if (vcard.organization.isNotBlank()) appendLine("ORG:${vcard.organization}")
                    if (vcard.phone.isNotBlank()) appendLine("TEL;TYPE=CELL:${vcard.phone}")
                    if (vcard.email.isNotBlank()) appendLine("EMAIL:${vcard.email}")
                    if (vcard.website.isNotBlank()) appendLine("URL:${vcard.website}")
                    append("END:VCARD")
                }
            }
            ContentType.LOCATION -> {
                if (location.latitude.isNotBlank() && location.longitude.isNotBlank()) {
                    "geo:${location.latitude.trim()},${location.longitude.trim()}?q=${java.net.URLEncoder.encode(location.address.ifBlank { "${location.latitude},${location.longitude}" }, "UTF-8")}"
                } else {
                    "https://maps.google.com/?q=${java.net.URLEncoder.encode(location.address, "UTF-8")}"
                }
            }
            ContentType.CALENDAR -> {
                val cleanStart = calendar.startDateTime.replace("-", "").replace(":", "").replace(" ", "T")
                val cleanEnd = calendar.endDateTime.replace("-", "").replace(":", "").replace(" ", "T")
                buildString {
                    appendLine("BEGIN:VCALENDAR")
                    appendLine("VERSION:2.0")
                    appendLine("BEGIN:VEVENT")
                    appendLine("SUMMARY:${calendar.title}")
                    appendLine("DTSTART:$cleanStart")
                    appendLine("DTEND:$cleanEnd")
                    if (calendar.location.isNotBlank()) appendLine("LOCATION:${calendar.location}")
                    if (calendar.description.isNotBlank()) appendLine("DESCRIPTION:${calendar.description}")
                    appendLine("END:VEVENT")
                    append("END:VCALENDAR")
                }
            }
            ContentType.WHATSAPP -> {
                val cleanPhone = whatsapp.phoneNumber.filter { it.isDigit() || it == '+' }
                val encText = java.net.URLEncoder.encode(whatsapp.message, "UTF-8").replace("+", "%20")
                if (encText.isNotBlank()) {
                    "https://wa.me/$cleanPhone?text=$encText"
                } else {
                    "https://wa.me/$cleanPhone"
                }
            }
            ContentType.TELEGRAM -> {
                val cleanUser = telegram.username.trim().removePrefix("@").removePrefix("https://t.me/")
                "https://t.me/$cleanUser"
            }
            ContentType.INSTAGRAM -> {
                val cleanUser = instagram.username.trim().removePrefix("@").removePrefix("https://instagram.com/").removePrefix("https://www.instagram.com/")
                "https://instagram.com/$cleanUser"
            }
            ContentType.CRYPTO -> {
                val coin = crypto.coin.code
                val addr = crypto.address.trim()
                val amt = crypto.amount.trim()
                when (crypto.coin) {
                    CryptoCoin.BTC -> if (amt.isNotBlank()) "bitcoin:$addr?amount=$amt" else "bitcoin:$addr"
                    CryptoCoin.ETH -> if (amt.isNotBlank()) "ethereum:$addr?value=$amt" else "ethereum:$addr"
                    CryptoCoin.LTC -> if (amt.isNotBlank()) "litecoin:$addr?amount=$amt" else "litecoin:$addr"
                    CryptoCoin.DOGE -> if (amt.isNotBlank()) "dogecoin:$addr?amount=$amt" else "dogecoin:$addr"
                    CryptoCoin.SOL -> if (amt.isNotBlank()) "solana:$addr?amount=$amt" else "solana:$addr"
                    else -> "$coin:$addr"
                }
            }
            ContentType.PAYPAL -> {
                val cleanUser = paypal.username.trim().removePrefix("https://paypal.me/")
                if (paypal.amount.isNotBlank()) {
                    "https://paypal.me/$cleanUser/${paypal.amount.trim()}${paypal.currency.uppercase()}"
                } else {
                    "https://paypal.me/$cleanUser"
                }
            }
        }
    }
}
