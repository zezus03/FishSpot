package com.example.fishspot.feature.spots

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.floor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp

// "Sakrij tačnu lokaciju": u javnom delu ulova je samo centar kvadrata od oko 3 km, a tačna lokacija
// je u MestaZaPecanje/{id}/privatno/lokacija, koju po pravilima baze čita samo autor.
// Svi ulovi sa istog mesta padaju u isti kvadrat, pa se iz više ulova ne može "ukrstiti" tačna tačka.
object SkrivenaLokacija {
    // ~3 km (stepen geografske širine je ~111 km, a dužine ~78 km na 45° severno)
    private const val KORAK_LAT = 0.027
    private const val KORAK_LNG = 0.038
    // Krug koji pokriva ceo kvadrat oko centra
    const val RADIJUS_M = 2300.0

    fun priblizno(lat: Double, lng: Double): LatLng =
        LatLng((floor(lat / KORAK_LAT) + 0.5) * KORAK_LAT, (floor(lng / KORAK_LNG) + 0.5) * KORAK_LNG)

    fun privatnoRef(spotId: String): DocumentReference =
        FirebaseFirestore.getInstance().collection("MestaZaPecanje").document(spotId)
            .collection("privatno").document("lokacija")

    // Tačna lokacija sopstvenog ulova (drugima pravila ne dozvoljavaju čitanje)
    suspend fun tacna(spotId: String): LatLng? = try {
        val d = privatnoRef(spotId).get().await()
        val lat = d.getDouble("lat"); val lng = d.getDouble("lng")
        if (lat != null && lng != null) LatLng(lat, lng) else null
    } catch (_: Exception) {
        null
    }
}

// Prekidač kod dodavanja i izmene ulova
@androidx.compose.runtime.Composable
fun PrekidacSakrijLokaciju(sakrij: Boolean, onPromena: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                "Sakrij tačnu lokaciju", color = com.example.fishspot.core.theme.FishBoje.TekstPrimarni,
                fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            androidx.compose.material3.Text(
                if (sakrij) "Drugi vide samo krug od oko 3 km. Tačno mesto vidiš samo ti."
                else "Drugi vide tačno mesto ulova na mapi.",
                color = com.example.fishspot.core.theme.FishBoje.TekstPriguseni, fontSize = 12.sp
            )
        }
        androidx.compose.material3.Switch(
            checked = sakrij, onCheckedChange = onPromena,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = com.example.fishspot.core.theme.FishBoje.Pozadina,
                checkedTrackColor = com.example.fishspot.core.theme.FishBoje.Akcenat
            )
        )
    }
}
