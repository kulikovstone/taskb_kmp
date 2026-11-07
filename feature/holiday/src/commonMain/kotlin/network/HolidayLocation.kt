package com.mkulikov.taskb.feature.holiday.network

import com.mkulikov.taskb.feature.settings.api.model.AppLocation

internal data class HolidayLocation(val countryCode: String, val region: String?) {
    val key: String
        get() = countryCode + (region?.let { region -> ":$region" } ?: "")
}

internal fun holidayLocation(location: AppLocation): HolidayLocation {
    val country = location.countryCode?.uppercase()?.takeIf { countryCode ->
        countryCode.length == 2 && countryCode.all { char -> char in 'A'..'Z' }
    }
        ?: throw HolidayLocationException(
            message = "Choose your location again in Settings to load public holidays."
        )
    val names = regionsByCountry[country] ?: return HolidayLocation(countryCode = country, region = null)
    val region = names[location.regionName?.trim()?.lowercase()]
        ?: throw HolidayLocationException(
            message = "Public holidays need a supported region. Choose another location in Settings."
        )
    return HolidayLocation(countryCode = country, region = region)
}

// Exact English subdivision names/aliases from ISO 3166-2. Enrico's region codes are
// documented at https://kayaposoft.com/enrico/json/v2.0/docs.php. An unknown name
// must remain unsupported instead of borrowing a nearby jurisdiction's holidays.
private fun regions(values: String): Map<String, String> {
    return buildMap {
        values.split('|').forEach { entry ->
            val (code, names) = entry.split('=', limit = 2)
            names.split('/').forEach { name -> put(key = name.lowercase(), value = code) }
            put(key = code, value = code)
        }
    }
}

private val regionsByCountry = mapOf(
    "US" to
        regions(
            values =
                ("al=Alabama|ak=Alaska|az=Arizona|ar=Arkansas|ca=California|co=Colorado|" +
                    "ct=Connecticut|de=Delaware|dc=District of Columbia/Washington, D.C.|fl=Florida|" +
                    "ga=Georgia|hi=Hawaii|id=Idaho|il=Illinois|in=Indiana|ia=Iowa|ks=Kansas|" +
                    "ky=Kentucky|la=Louisiana|me=Maine|md=Maryland|ma=Massachusetts|mi=Michigan|" +
                    "mn=Minnesota|ms=Mississippi|mo=Missouri|mt=Montana|ne=Nebraska|nv=Nevada|nh=New " +
                    "Hampshire|nj=New Jersey|nm=New Mexico|ny=New York|nc=North Carolina|nd=North " +
                    "Dakota|oh=Ohio|ok=Oklahoma|or=Oregon|pa=Pennsylvania|ri=Rhode Island|sc=South " +
                    "Carolina|sd=South Dakota|tn=Tennessee|tx=Texas|ut=Utah|vt=Vermont|va=Virginia|" +
                    "wa=Washington|wv=West Virginia|wi=Wisconsin|wy=Wyoming")
        ),
    "DE" to
        regions(
            values =
                ("bw=Baden-Württemberg/Baden-Wurttemberg|by=Bavaria/Bayern|be=Berlin|" +
                    "bb=Brandenburg|hb=Bremen|hh=Hamburg|he=Hesse/Hessen|mv=Mecklenburg-Vorpommern|" +
                    "ni=Lower Saxony/Niedersachsen|nw=North Rhine-Westphalia/Nordrhein-Westfalen|" +
                    "rp=Rhineland-Palatinate/Rheinland-Pfalz|sl=Saarland|sn=Saxony/Sachsen|" +
                    "st=Saxony-Anhalt/Sachsen-Anhalt|sh=Schleswig-Holstein|th=Thuringia/Thüringen")
        ),
    "GB" to regions(values = "eng=England|nir=Northern Ireland|sct=Scotland|wls=Wales"),
    "CA" to
        regions(
            values =
                ("ab=Alberta|bc=British Columbia|mb=Manitoba|nb=New Brunswick|nl=Newfoundland and " +
                    "Labrador|nt=Northwest Territories|ns=Nova Scotia|nu=Nunavut|on=Ontario|" +
                    "pe=Prince Edward Island|qc=Quebec/Québec|sk=Saskatchewan|yt=Yukon")
        ),
    "AU" to
        regions(
            values =
                ("act=Australian Capital Territory|nsw=New South Wales|nt=Northern Territory|" +
                    "qld=Queensland|sa=South Australia|tas=Tasmania|vic=Victoria|wa=Western Australia")
        ),
    "NZ" to
        regions(
            values =
                ("auk=Auckland|bop=Bay of Plenty|can=Canterbury|gis=Gisborne|hkb=Hawke's " +
                    "Bay/Hawke’s Bay|mbh=Marlborough|mwt=Manawatu-Wanganui/Manawatu-Whanganui|" +
                    "nsn=Nelson|ntl=Northland|ota=Otago|stl=Southland|tas=Tasman|tki=Taranaki|" +
                    "wko=Waikato|wgn=Wellington|wtc=West Coast|cit=Chatham Islands")
        ),
    "BE" to
        regions(
            values =
                ("bru=Brussels/Brussels Capital/Brussels Capital Region|vlg=Flanders/Flemish " +
                    "Region|wal=Wallonia/Walloon Region")
        ),
    "SK" to
        regions(
            values =
                ("bc=Banská Bystrica/Banska Bystrica|bl=Bratislava|ki=Košice/Kosice|ni=Nitra|" +
                    "pv=Prešov/Presov|ta=Trnava|tc=Trenčín/Trencin|zi=Žilina/Zilina")
        ),
    "ES" to
        regions(
            values =
                ("an=Andalusia/Andalucía|ar=Aragon/Aragón|as=Asturias|cn=Canary Islands|" +
                    "cb=Cantabria|cl=Castille and León/Castile and León/Castille and Leon/Castile " +
                    "and Leon|cm=Castille-La Mancha/Castile-La Mancha|ct=Catalonia|ce=Ceuta|" +
                    "ex=Extremadura|ga=Galicia|ib=Balearic Islands|ri=La Rioja|md=Madrid|ml=Melilla|" +
                    "mc=Murcia|nc=Navarre/Navarra|pv=Basque Country|vc=Valencia/Valencian Community")
        ),
    "CH" to
        regions(
            values =
                ("ag=Aargau|ai=Appenzell Innerrhoden|ar=Appenzell Ausserrhoden|" +
                    "bl=Basel-Landschaft/Basel-Land|bs=Basel-City/Basel-Stadt|be=Bern/Berne|" +
                    "fr=Fribourg/Freiburg|ge=Geneva/Genève|gl=Glarus|gr=Grisons/Graubünden|ju=Jura|" +
                    "lu=Lucerne/Luzern|ne=Neuchâtel/Neuchatel|nw=Nidwalden|ow=Obwalden|sg=Saint " +
                    "Gallen/St. Gallen/Sankt Gallen|sh=Schaffhausen|sz=Schwyz|so=Solothurn|" +
                    "tg=Thurgau|ti=Ticino|ur=Uri|vs=Valais/Wallis|vd=Vaud|zg=Zug|zh=Zurich/Zürich")
        ),
)
