package id.tub.dinas.ui

sealed interface Screen {
    data object Home : Screen
    data class Detail(val dinasId: String) : Screen
    data class Photo(val dinasId: String, val ownerId: String, val index: Int) : Screen
    data class Pdf(val path: String, val name: String) : Screen
    data object Settings : Screen
    data class Present(val dinasId: String) : Screen
}

sealed interface Editor {
    data class Titik(val id: String) : Editor
    data class Temuan(val id: String, val isNew: Boolean) : Editor
}

enum class DetailTab(val label: String) { PETA("Peta"), TEMUAN("Temuan"), RINGKASAN("Ringkasan"), TINDAK("Tindak Lanjut") }
